package io.github.supermonster003.autojs6.plugin.typescript

import java.util.concurrent.CountDownLatch

/**
 * Process-lifetime memo for immutable, build-verified values that are expensive to load.
 *
 * The first successful load for a key is kept for the rest of the process. Concurrent callers
 * for a key that is still loading wait for that single load instead of starting their own, so a
 * boot-time preload and the first Binder request share one pass over the packaged assets.
 * Failures are never cached: every waiter of a failed load sees the failure, and the next call
 * retries from scratch. Values must be immutable because they are shared between the service
 * Binder threads, the WebView host and the compilation coordinator.
 */
internal class R2ProcessLoadCache<K : Any, V : Any>(
    private val loader: (K) -> V,
) {

    private val lock = Any()
    private val values = HashMap<K, V>()
    private val inFlight = HashMap<K, InFlightLoad<V>>()

    fun get(key: K): V {
        val flight: InFlightLoad<V>
        val owner: Boolean
        synchronized(lock) {
            values[key]?.let { return it }
            val existing = inFlight[key]
            if (existing != null) {
                flight = existing
                owner = false
            } else {
                flight = InFlightLoad()
                inFlight[key] = flight
                owner = true
            }
        }
        if (!owner) {
            return flight.await().getOrThrow()
        }
        val outcome = runCatching { loader(key) }
        synchronized(lock) {
            outcome.getOrNull()?.let { value -> values[key] = value }
            if (inFlight[key] === flight) {
                inFlight.remove(key)
            }
        }
        flight.complete(outcome)
        return outcome.getOrThrow()
    }

    fun peek(key: K): V? = synchronized(lock) { values[key] }

    /** Test-only reset so JVM tests can observe a fresh load without a new process. */
    internal fun clearForTesting() = synchronized(lock) {
        values.clear()
        inFlight.clear()
    }

    private class InFlightLoad<V> {

        private val latch = CountDownLatch(1)

        @Volatile
        private var outcome: Result<V>? = null

        fun complete(result: Result<V>) {
            outcome = result
            latch.countDown()
        }

        fun await(): Result<V> {
            latch.await()
            return requireNotNull(outcome)
        }
    }
}
