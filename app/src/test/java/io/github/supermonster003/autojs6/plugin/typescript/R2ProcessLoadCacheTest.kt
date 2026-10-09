package io.github.supermonster003.autojs6.plugin.typescript

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R2ProcessLoadCacheTest {

    @Test
    fun loadsEachKeyOnceAndSharesTheInstance() {
        val loads = AtomicInteger()
        val cache = R2ProcessLoadCache<String, List<Int>> { key ->
            loads.incrementAndGet()
            listOf(key.length)
        }

        val first = cache.get("rhino")
        val second = cache.get("rhino")
        val other = cache.get("node")

        assertSame(first, second)
        assertEquals(listOf(5), first)
        assertEquals(listOf(4), other)
        assertEquals(2, loads.get())
        assertSame(first, cache.peek("rhino"))
        assertNull(cache.peek("missing"))
    }

    @Test
    fun neverCachesFailuresAndRetriesTheNextRequest() {
        val attempts = AtomicInteger()
        val cache = R2ProcessLoadCache<String, String> { key ->
            if (attempts.incrementAndGet() == 1) {
                throw IllegalStateException("asset unavailable")
            }
            key.uppercase()
        }

        assertThrows(IllegalStateException::class.java) { cache.get("rhino") }
        assertNull(cache.peek("rhino"))
        assertEquals("RHINO", cache.get("rhino"))
        assertEquals(2, attempts.get())
        assertEquals("RHINO", cache.get("rhino"))
        assertEquals(2, attempts.get())
    }

    @Test
    fun concurrentFirstLoadsShareOneLoadAndOneInstance() {
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val loads = AtomicInteger()
        val cache = R2ProcessLoadCache<String, Any> { _ ->
            loads.incrementAndGet()
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            Any()
        }
        val executor = Executors.newFixedThreadPool(4)
        try {
            val first = executor.submit<Any> { cache.get("common") }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val others = (1..3).map { executor.submit<Any> { cache.get("common") } }
            release.countDown()
            val results = (listOf(first) + others).map { future -> future.get(5, TimeUnit.SECONDS) }
            assertTrue(results.all { result -> result === results.first() })
            assertSame(results.first(), cache.get("common"))
            assertEquals(1, loads.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun waitersOfAFailedLoadSeeTheFailureAndTheNextCallRetries() {
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val attempts = AtomicInteger()
        val cache = R2ProcessLoadCache<String, String> { key ->
            if (attempts.incrementAndGet() == 1) {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                throw IllegalStateException("first load failed")
            }
            key.uppercase()
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val owner = executor.submit<String> { cache.get("rhino") }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val waiter = executor.submit<String> { cache.get("rhino") }
            release.countDown()
            val ownerError = assertThrows(java.util.concurrent.ExecutionException::class.java) {
                owner.get(5, TimeUnit.SECONDS)
            }
            assertTrue(ownerError.cause is IllegalStateException)
            // The waiter either joined the failed in-flight load (and sees its failure) or arrived
            // after the owner gave up (and performed the successful retry itself).
            val waiterOutcome = runCatching { waiter.get(5, TimeUnit.SECONDS) }
            waiterOutcome.fold(
                onSuccess = { value ->
                    assertEquals("RHINO", value)
                    assertEquals(2, attempts.get())
                },
                onFailure = { error ->
                    assertTrue(error is java.util.concurrent.ExecutionException)
                    assertTrue(error.cause is IllegalStateException)
                    assertEquals(1, attempts.get())
                },
            )
            assertEquals("RHINO", cache.get("rhino"))
            assertEquals(2, attempts.get())
            assertEquals("RHINO", cache.get("rhino"))
            assertEquals(2, attempts.get())
        } finally {
            executor.shutdownNow()
        }
    }
}
