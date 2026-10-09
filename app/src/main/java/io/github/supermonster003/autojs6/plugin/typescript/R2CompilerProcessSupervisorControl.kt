package io.github.supermonster003.autojs6.plugin.typescript

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Process
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keys used only by DEBUG instrumentation to turn on and observe the production owner path.
 *
 * The live control plane itself uses the private typed AIDL interfaces in this application
 * module. None of these keys are part of the public compiler contract.
 */
internal object R2CompilerProcessSupervisorControlProtocol {
    const val KEY_ENABLE_AUTOMATIC_TERMINATION_FOR_TESTING =
        "__autojs6_r2_enable_production_supervisor_for_testing"
    const val KEY_AUTOMATIC_TERMINATION_ENABLED =
        "__autojs6_r2_production_supervisor_automatic_termination_enabled"
    const val KEY_REGISTRATION_READY =
        "__autojs6_r2_production_supervisor_registration_ready"
    const val KEY_REGISTRY_CONNECTED =
        "__autojs6_r2_production_supervisor_registry_connected"
    const val KEY_SET_RECONNECT_PAUSED_FOR_TESTING =
        "__autojs6_r2_production_supervisor_set_reconnect_paused_for_testing"
    const val KEY_RECONNECT_PAUSED =
        "__autojs6_r2_production_supervisor_reconnect_paused"
    const val KEY_REGISTERED_SUPERVISOR_PID =
        "__autojs6_r2_production_supervisor_registered_pid"
    const val KEY_REGISTERED_SUPERVISOR_START_TIME_TICKS =
        "__autojs6_r2_production_supervisor_registered_start_time_ticks"
    const val KEY_RETIRED_CALLBACK_COMMAND_FOR_TESTING =
        "__autojs6_r2_production_supervisor_retired_callback_command_for_testing"
    const val COMMAND_ARM_RETIRED_CALLBACKS = "arm"
    const val COMMAND_REPLAY_RETIRED_CALLBACKS = "replay"
    const val COMMAND_RESET_RETIRED_CALLBACKS = "reset"
    const val KEY_CONNECTION_GENERATION =
        "__autojs6_r2_production_supervisor_connection_generation"
    const val KEY_RETIRED_CALLBACK_REPLAY_ARMED =
        "__autojs6_r2_production_supervisor_retired_callback_replay_armed"
    const val KEY_RETIRED_CALLBACKS_REPLAYED =
        "__autojs6_r2_production_supervisor_retired_callbacks_replayed"
    const val KEY_RETIRED_CALLBACK_STATE_PRESERVED =
        "__autojs6_r2_production_supervisor_retired_callback_state_preserved"
    const val KEY_RETIRED_CONNECTION_GENERATION =
        "__autojs6_r2_production_supervisor_retired_connection_generation"
    const val KEY_REPLAY_TARGET_CONNECTION_GENERATION =
        "__autojs6_r2_production_supervisor_replay_target_connection_generation"
    const val KEY_RETIRED_CALLBACK_DELIVERY_COUNT =
        "__autojs6_r2_production_supervisor_retired_callback_delivery_count"
}

/**
 * Non-exported live-epoch owner in :typescript_supervisor.
 *
 * Registration is established before production admission is allowed. The owner pins an exact
 * compiler-process callback Binder and is the only process allowed to request the existing
 * exact-self-termination primitive after process-wide admission becomes poisoned.
 */
internal class R2CompilerProcessSupervisorService : Service() {

    private val registryBinder =
        object : IR2CompilerProcessSupervisorRegistry.Stub() {
            override fun registerEpoch(
                compilerBootId: String?,
                compilerUid: Int,
                compilerPid: Int,
                compilerStartTimeTicks: Long,
                compilerProcessName: String?,
                compilerNonce: String?,
                control: IR2CompilerProcessEpochControl?,
            ): String? {
                val identity = identityOrNull(
                    bootId = compilerBootId,
                    uid = compilerUid,
                    pid = compilerPid,
                    startTimeTicks = compilerStartTimeTicks,
                    processName = compilerProcessName,
                    nonce = compilerNonce,
                ) ?: return null
                val endpoint = control ?: return null
                val endpointBinder = endpoint.asBinder()
                val supervisorIdentity =
                    currentSupervisorIdentityOrNull()
                        ?: return null
                if (
                    !callerMatchesCompiler(
                        identity = identity,
                        supervisorBootId = supervisorIdentity.bootId,
                    ) ||
                    !hasExactRemoteDescriptor(
                        endpointBinder,
                        IR2CompilerProcessEpochControl.DESCRIPTOR,
                    )
                ) {
                    return null
                }

                val duplicate = synchronized(registryLock) {
                    val current = registration
                    if (
                        current != null &&
                        current.identity.matchesExact(identity) &&
                        current.controlBinder === endpointBinder &&
                        current.controlBinder.isBinderAlive
                    ) {
                        current
                    } else {
                        /*
                         * An alive callback remains authoritative for its registered epoch.
                         * Only Binder death permits replacement.
                         */
                        if (
                            registrationAttemptInFlight ||
                            current?.controlBinder?.isBinderAlive == true
                        ) {
                            return null
                        }
                        removeRegistrationLocked(current)
                        registrationAttemptInFlight = true
                        null
                    }
                }
                if (duplicate != null) {
                    val challenged = runCatching {
                        duplicate.control.establishSupervisorRegistration(
                            duplicate.token,
                            supervisorIdentity.bootId,
                            supervisorIdentity.uid,
                            Process.myPid(),
                            supervisorIdentity.startTimeTicks,
                            supervisorIdentity.processName,
                        )
                    }.getOrDefault(false)
                    return synchronized(registryLock) {
                        duplicate.token.takeIf {
                            challenged &&
                                registration === duplicate &&
                                duplicate.controlBinder.isBinderAlive
                        }
                    }
                }

                val token = newToken()
                val deathRecipient = IBinder.DeathRecipient {
                    removeRegistrationForEndpoint(endpointBinder)
                }
                val linked = runCatching {
                    endpointBinder.linkToDeath(deathRecipient, 0)
                }.isSuccess
                if (!linked || !endpointBinder.isBinderAlive) {
                    synchronized(registryLock) {
                        registrationAttemptInFlight = false
                    }
                    return null
                }
                val challenged = runCatching {
                    endpoint.establishSupervisorRegistration(
                        token,
                        supervisorIdentity.bootId,
                        supervisorIdentity.uid,
                        Process.myPid(),
                        supervisorIdentity.startTimeTicks,
                        supervisorIdentity.processName,
                    )
                }.getOrDefault(false)
                if (!challenged || !endpointBinder.isBinderAlive) {
                    runCatching {
                        endpointBinder.unlinkToDeath(deathRecipient, 0)
                    }
                    synchronized(registryLock) {
                        registrationAttemptInFlight = false
                    }
                    return null
                }

                val installed = synchronized(registryLock) {
                    if (
                        !registrationAttemptInFlight ||
                        registration != null ||
                        !endpointBinder.isBinderAlive
                    ) {
                        registrationAttemptInFlight = false
                        false
                    } else {
                        registration = Registration(
                            identity = identity,
                            token = token,
                            control = endpoint,
                            controlBinder = endpointBinder,
                            deathRecipient = deathRecipient,
                        )
                        registrationAttemptInFlight = false
                        true
                    }
                }
                if (!installed) {
                    runCatching {
                        endpointBinder.unlinkToDeath(deathRecipient, 0)
                    }
                    return null
                }
                return token
            }

            override fun notifyPoisonedEpoch(
                registrationToken: String?,
                compilerBootId: String?,
                compilerUid: Int,
                compilerPid: Int,
                compilerStartTimeTicks: Long,
                compilerProcessName: String?,
                compilerNonce: String?,
            ): Boolean {
                val token = registrationToken ?: return false
                val identity = identityOrNull(
                    bootId = compilerBootId,
                    uid = compilerUid,
                    pid = compilerPid,
                    startTimeTicks = compilerStartTimeTicks,
                    processName = compilerProcessName,
                    nonce = compilerNonce,
                ) ?: return false
                val supervisorIdentity =
                    currentSupervisorIdentityOrNull()
                        ?: return false
                if (
                    !callerMatchesCompiler(
                        identity = identity,
                        supervisorBootId = supervisorIdentity.bootId,
                    )
                ) {
                    return false
                }
                val selected = synchronized(registryLock) {
                    val current = registration
                    if (
                        current == null ||
                        !current.identity.matchesExact(identity) ||
                        !secureEquals(current.token, token) ||
                        !current.controlBinder.isBinderAlive
                    ) {
                        null
                    } else if (current.terminationInFlight) {
                        return true
                    } else {
                        current.terminationInFlight = true
                        current
                    }
                } ?: return false

                val started = runCatching {
                    Thread(
                        {
                            superviseTermination(selected)
                        },
                        "typescript-r2-production-supervisor",
                    ).apply {
                        isDaemon = true
                        start()
                    }
                }.isSuccess
                if (!started) {
                    synchronized(registryLock) {
                        if (registration === selected) {
                            selected.terminationInFlight = false
                        }
                    }
                }
                return started
            }
        }

    override fun onBind(intent: Intent?): IBinder = registryBinder

    override fun onDestroy() {
        /*
         * Registry ownership is process-wide, not Service-instance-wide. A framework Service
         * recreation in the same :typescript_supervisor PID must not discard a live registration.
         */
        super.onDestroy()
    }

    private fun superviseTermination(selected: Registration) {
        val completed = runCatching {
            R2CompilerProcessSupervisor(this).terminatePoisonedEpoch(
                rawBinder = selected.controlBinder,
                oldIdentity = selected.identity,
                endpointDescriptor = IR2CompilerProcessEpochControl.DESCRIPTOR,
            ) { commandDeadline ->
                selected.control.terminatePoisonedEpoch(
                    selected.token,
                    selected.identity.bootId,
                    selected.identity.uid,
                    selected.identity.pid,
                    selected.identity.startTimeTicks,
                    selected.identity.processName,
                    selected.identity.nonce,
                    commandDeadline,
                )
            }
        }.isSuccess
        if (!completed) {
            synchronized(registryLock) {
                if (
                    registration === selected &&
                    selected.controlBinder.isBinderAlive
                ) {
                    selected.terminationInFlight = false
                }
            }
        }
    }

    private fun callerMatchesCompiler(
        identity: R2CompilerProcessEpochIdentity,
        supervisorBootId: String,
    ): Boolean =
        R2CompilerProcessSupervisorOnlineIdentityPolicy
            .compilerCallerMatches(
                identity = identity,
                callingUid = Binder.getCallingUid(),
                callingPid = Binder.getCallingPid(),
                applicationUid = applicationInfo.uid,
                supervisorBootId = supervisorBootId,
                expectedCompilerProcessName = COMPILER_PROCESS_NAME,
            )

    private fun currentSupervisorIdentityOrNull(): R2ObservedCompilerProcess? =
        R2CompilerProcessProcfs()
            .capture(Process.myPid())
            ?.takeIf { observed ->
                observed.uid == applicationInfo.uid &&
                    observed.processName == SUPERVISOR_PROCESS_NAME
            }

    private class Registration(
        val identity: R2CompilerProcessEpochIdentity,
        val token: String,
        val control: IR2CompilerProcessEpochControl,
        val controlBinder: IBinder,
        val deathRecipient: IBinder.DeathRecipient,
        var terminationInFlight: Boolean = false,
    )

    private companion object {
        val registryLock = Any()
        val random = SecureRandom()
        var registration: Registration? = null
        var registrationAttemptInFlight = false
        const val REGISTRATION_TOKEN_BYTES = 32
        val COMPILER_PROCESS_NAME =
            "${BuildConfig.APPLICATION_ID}:typescript_compiler"
        val SUPERVISOR_PROCESS_NAME =
            "${BuildConfig.APPLICATION_ID}:typescript_supervisor"

        fun removeRegistrationForEndpoint(endpoint: IBinder) {
            synchronized(registryLock) {
                registration?.takeIf { it.controlBinder === endpoint }?.let {
                    removeRegistrationLocked(it)
                }
            }
        }

        fun removeRegistrationLocked(candidate: Registration?) {
            if (candidate == null) return
            if (registration === candidate) {
                registration = null
            }
            runCatching {
                candidate.controlBinder.unlinkToDeath(candidate.deathRecipient, 0)
            }
        }

        fun newToken(): String {
            val bytes = ByteArray(REGISTRATION_TOKEN_BYTES)
            random.nextBytes(bytes)
            return try {
                bytes.toLowerHex()
            } finally {
                bytes.fill(0)
            }
        }
    }
}

/**
 * Process-lifetime compiler-side registration client.
 *
 * The application context keeps the explicit binding across Service recreation in the compiler
 * process. A poison that wins before registration completes is remembered and delivered after the
 * exact registration handshake succeeds.
 */
internal object R2CompilerProcessSupervisorClient {

    private val lock = Any()
    private val registrationInFlight = AtomicBoolean(false)
    private val notificationInFlight = AtomicBoolean(false)
    private val bindingRetryScheduled = AtomicBoolean(false)
    private val registrationRetryScheduled = AtomicBoolean(false)
    private val notificationRetryScheduled = AtomicBoolean(false)
    private var appContext: Context? = null
    private var nextConnectionGeneration = 0L
    private var connectionGeneration = 0L
    private var activeConnection: GenerationServiceConnection? = null
    private var registry: IR2CompilerProcessSupervisorRegistry? = null
    private var registryDeath: RegistryDeath? = null
    private var registrationToken: String? = null
    private var supervisorIdentity: R2ObservedCompilerProcess? = null
    private var supervisorPid: Int? = null
    private var pendingHandshake: SupervisorHandshake? = null
    private var poisonObserved = false
    private var automaticTerminationEnabled = !BuildConfig.DEBUG
    private var reconnectPausedForTesting = false
    private var retiredCallbackArmForTesting: RetiredCallbackArm? = null
    private var retiredCallbacksForTesting: RetiredCallbacks? = null
    private var retiredCallbackReplayForTesting:
        R2RetiredCallbackReplayTelemetry? = null

    private val control =
        object : IR2CompilerProcessEpochControl.Stub() {
            override fun establishSupervisorRegistration(
                token: String?,
                supervisorBootId: String?,
                supervisorUid: Int,
                supervisorPid: Int,
                supervisorStartTimeTicks: Long,
                supervisorProcessName: String?,
            ): Boolean {
                val acceptedToken =
                    token?.takeIf(::isCanonicalToken) ?: return false
                val expectedName =
                    "${BuildConfig.APPLICATION_ID}:typescript_supervisor"
                val compilerIdentity = R2CompilerProcessEpoch.identity()
                val observed =
                    R2CompilerProcessSupervisorOnlineIdentityPolicy
                        .supervisorIdentityOrNull(
                            supervisorBootId = supervisorBootId,
                            supervisorUid = supervisorUid,
                            supervisorPid = supervisorPid,
                            supervisorStartTimeTicks =
                                supervisorStartTimeTicks,
                            supervisorProcessName = supervisorProcessName,
                            callingUid = Binder.getCallingUid(),
                            callingPid = Binder.getCallingPid(),
                            compilerUid = Process.myUid(),
                            compilerBootId = compilerIdentity.bootId,
                            expectedSupervisorProcessName = expectedName,
                        )
                        ?: return false
                synchronized(lock) {
                    if (
                        !registrationInFlight.get() ||
                        reconnectPausedForTesting
                    ) {
                        return false
                    }
                    val activeToken = registrationToken
                    val activeIdentity = supervisorIdentity
                    val activePid =
                        R2CompilerProcessSupervisorClient.supervisorPid
                    if (
                        activeToken != null &&
                        (
                            !secureEquals(activeToken, acceptedToken) ||
                                activeIdentity != observed ||
                                activePid != supervisorPid
                            )
                    ) {
                        return false
                    }
                    pendingHandshake = SupervisorHandshake(
                        token = acceptedToken,
                        identity = observed,
                        pid = supervisorPid,
                    )
                }
                return true
            }

            override fun terminatePoisonedEpoch(
                token: String?,
                compilerBootId: String?,
                compilerUid: Int,
                compilerPid: Int,
                compilerStartTimeTicks: Long,
                compilerProcessName: String?,
                compilerNonce: String?,
                commandDeadlineElapsedRealtime: Long,
            ): Boolean {
                val expectedSupervisor = synchronized(lock) {
                    val currentToken = registrationToken
                    val currentSupervisor = supervisorIdentity
                    val currentSupervisorPid = supervisorPid
                    if (
                        token == null ||
                        currentToken == null ||
                        currentSupervisor == null ||
                        currentSupervisorPid == null ||
                        !secureEquals(currentToken, token)
                    ) {
                        null
                    } else {
                        currentSupervisorPid to currentSupervisor
                    }
                } ?: return false
                if (
                    Binder.getCallingUid() != expectedSupervisor.second.uid ||
                    Binder.getCallingPid() != expectedSupervisor.first
                ) {
                    return false
                }
                val requestedIdentity = identityOrNull(
                    bootId = compilerBootId,
                    uid = compilerUid,
                    pid = compilerPid,
                    startTimeTicks = compilerStartTimeTicks,
                    processName = compilerProcessName,
                    nonce = compilerNonce,
                ) ?: return false
                val currentIdentity = R2CompilerProcessEpoch.identity()
                if (
                    !currentIdentity.matchesExact(requestedIdentity) ||
                    !R2ProcessAdmissionAuthority.requiresExternalSupervision() ||
                    !R2CompilerProcessEpoch.hasUsableCommandDeadline(
                        commandDeadlineElapsedRealtime,
                    )
                ) {
                    return false
                }
                return R2CompilerProcessEpoch.scheduleExactSelfTermination(
                    context = requireNotNull(appContext),
                    expectedIdentity = requestedIdentity,
                    commandDeadline = commandDeadlineElapsedRealtime,
                )
            }
        }

    fun start(context: Context) {
        synchronized(lock) {
            if (appContext == null) {
                appContext = context.applicationContext
            }
        }
        bindIfNeeded()
    }

    fun isReady(): Boolean = synchronized(lock) {
        registry?.asBinder()?.isBinderAlive == true &&
            registrationToken != null &&
            supervisorIdentity != null &&
            supervisorPid != null
    }

    fun isAutomaticTerminationEnabled(): Boolean = synchronized(lock) {
        automaticTerminationEnabled
    }

    fun telemetryForTesting(): R2CompilerProcessSupervisorClientTelemetry =
        synchronized(lock) {
            val currentRegistry = registry
            val currentIdentity = supervisorIdentity
            val currentPid = supervisorPid
            val callbackReplay = retiredCallbackReplayForTesting
            val registryConnected =
                currentRegistry?.asBinder()?.isBinderAlive == true
            val ready =
                registryConnected &&
                    registrationToken != null &&
                    currentIdentity != null &&
                    currentPid != null
            R2CompilerProcessSupervisorClientTelemetry(
                registryConnected = registryConnected,
                registrationReady = ready,
                automaticTerminationEnabled = automaticTerminationEnabled,
                reconnectPaused = reconnectPausedForTesting,
                registeredSupervisorPid = currentPid.takeIf { ready },
                registeredSupervisorStartTimeTicks =
                    currentIdentity?.startTimeTicks.takeIf { ready },
                connectionGeneration = activeConnection?.generation,
                retiredCallbackReplayArmed =
                    retiredCallbackArmForTesting != null ||
                        retiredCallbacksForTesting != null,
                retiredCallbacksReplayed = callbackReplay?.replayed,
                retiredCallbackStatePreserved =
                    callbackReplay?.statePreserved,
                retiredConnectionGeneration =
                    callbackReplay?.retiredGeneration,
                replayTargetConnectionGeneration =
                    callbackReplay?.currentGeneration,
                retiredCallbackDeliveryCount =
                    callbackReplay?.deliveryCount,
            )
        }

    fun enableAutomaticTerminationForTesting() {
        check(BuildConfig.DEBUG)
        synchronized(lock) {
            automaticTerminationEnabled = true
        }
        if (R2ProcessAdmissionAuthority.requiresExternalSupervision()) {
            onProcessPoisoned()
        }
    }

    fun setReconnectPausedForTesting(paused: Boolean) {
        check(BuildConfig.DEBUG)
        val action = synchronized(lock) {
            reconnectPausedForTesting = paused
            if (paused) {
                ReconnectAction()
            } else {
                val currentRegistry = registry
                val registration = if (
                    currentRegistry != null &&
                    currentRegistry.asBinder().isBinderAlive &&
                    registrationToken == null
                ) {
                    RegistrySnapshot(
                        registry = currentRegistry,
                        generation = connectionGeneration,
                    )
                } else {
                    null
                }
                ReconnectAction(
                    bind = activeConnection == null && appContext != null,
                    registration = registration,
                )
            }
        }
        action.registration?.let { snapshot ->
            register(snapshot.registry, snapshot.generation)
        }
        if (action.bind) {
            bindIfNeeded()
        }
    }

    fun executeRetiredCallbackCommandForTesting(command: String) {
        check(BuildConfig.DEBUG)
        when (command) {
            R2CompilerProcessSupervisorControlProtocol
                .COMMAND_ARM_RETIRED_CALLBACKS ->
                armRetiredCallbacksForTesting()
            R2CompilerProcessSupervisorControlProtocol
                .COMMAND_REPLAY_RETIRED_CALLBACKS ->
                replayRetiredCallbacksForTesting()
            R2CompilerProcessSupervisorControlProtocol
                .COMMAND_RESET_RETIRED_CALLBACKS ->
                resetRetiredCallbacksForTesting()
            else -> error("Unknown retired callback command")
        }
    }

    fun onProcessPoisoned() {
        val enabled = synchronized(lock) {
            poisonObserved = true
            automaticTerminationEnabled
        }
        if (enabled) {
            dispatchPoisonNotification()
        }
    }

    private fun armRetiredCallbacksForTesting() {
        synchronized(lock) {
            retiredCallbackArmForTesting = null
            retiredCallbacksForTesting = null
            retiredCallbackReplayForTesting = null

            val connection = activeConnection ?: return
            val currentRegistry = registry ?: return
            val currentBinder = currentRegistry.asBinder()
            val currentDeath = registryDeath ?: return
            if (
                connectionGeneration != connection.generation ||
                currentDeath.generation != connection.generation ||
                currentDeath.binder !== currentBinder ||
                !currentBinder.isBinderAlive ||
                registrationToken == null ||
                supervisorIdentity == null ||
                supervisorPid == null
            ) {
                return
            }
            retiredCallbackArmForTesting = RetiredCallbackArm(
                connection = connection,
                generation = connection.generation,
                registryBinder = currentBinder,
            )
        }
    }

    private fun replayRetiredCallbacksForTesting() {
        val snapshot = synchronized(lock) {
            val retired = retiredCallbacksForTesting
            val currentConnection = activeConnection
            val currentRegistry = registry
            val currentBinder = currentRegistry?.asBinder()
            val currentDeath = registryDeath
            val currentToken = registrationToken
            val currentIdentity = supervisorIdentity
            val currentPid = supervisorPid
            val currentGeneration = currentConnection?.generation
            if (
                retired == null ||
                currentConnection == null ||
                currentBinder == null ||
                currentDeath == null ||
                currentToken == null ||
                currentIdentity == null ||
                currentPid == null ||
                currentGeneration == null ||
                currentConnection === retired.connection ||
                connectionGeneration != currentGeneration ||
                currentGeneration <= retired.connection.generation ||
                currentDeath.generation != currentGeneration ||
                currentDeath.binder !== currentBinder ||
                !currentBinder.isBinderAlive ||
                retired.registryDeath.generation !=
                    retired.connection.generation
            ) {
                retiredCallbackReplayForTesting =
                    R2RetiredCallbackReplayTelemetry(
                        replayed = false,
                        statePreserved = false,
                        retiredGeneration =
                            retired?.connection?.generation,
                        currentGeneration = currentGeneration,
                        deliveryCount = 0,
                    )
                return
            }
            retiredCallbacksForTesting = null
            RetiredCallbackReplaySnapshot(
                context = requireNotNull(appContext),
                retired = retired,
                currentConnection = currentConnection,
                currentGeneration = currentGeneration,
                currentRegistryBinder = currentBinder,
                currentRegistryDeath = currentDeath,
                currentRegistrationToken = currentToken,
                currentSupervisorIdentity = currentIdentity,
                currentSupervisorPid = currentPid,
            )
        }

        val serviceName = ComponentName(
            snapshot.context,
            R2CompilerProcessSupervisorService::class.java,
        )
        var statePreserved = true
        var deliveryCount = 0

        fun replay(callback: () -> Unit) {
            deliveryCount += 1
            val delivered = runCatching(callback).isSuccess
            val preservedAfterDelivery =
                currentReplayTargetMatches(snapshot)
            statePreserved =
                statePreserved &&
                    delivered &&
                    preservedAfterDelivery
        }

        repeat(2) {
            replay {
                snapshot.retired.connection
                    .onServiceDisconnected(serviceName)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            repeat(2) {
                replay {
                    snapshot.retired.connection.onBindingDied(serviceName)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            repeat(2) {
                replay {
                    snapshot.retired.connection.onNullBinding(serviceName)
                }
            }
        }
        repeat(2) {
            replay {
                snapshot.retired.registryDeath.recipient.binderDied()
            }
        }

        synchronized(lock) {
            statePreserved =
                statePreserved &&
                    currentReplayTargetMatchesLocked(snapshot)
            retiredCallbackReplayForTesting =
                R2RetiredCallbackReplayTelemetry(
                    replayed = true,
                    statePreserved = statePreserved,
                    retiredGeneration =
                        snapshot.retired.connection.generation,
                    currentGeneration = snapshot.currentGeneration,
                    deliveryCount = deliveryCount,
                )
        }
    }

    private fun resetRetiredCallbacksForTesting() {
        synchronized(lock) {
            retiredCallbackArmForTesting = null
            retiredCallbacksForTesting = null
            retiredCallbackReplayForTesting = null
        }
    }

    private fun currentReplayTargetMatches(
        snapshot: RetiredCallbackReplaySnapshot,
    ): Boolean = synchronized(lock) {
        currentReplayTargetMatchesLocked(snapshot)
    }

    private fun currentReplayTargetMatchesLocked(
        snapshot: RetiredCallbackReplaySnapshot,
    ): Boolean {
        val currentRegistryBinder = registry?.asBinder()
        val currentDeath = registryDeath
        val currentToken = registrationToken
        return activeConnection === snapshot.currentConnection &&
            connectionGeneration == snapshot.currentGeneration &&
            currentRegistryBinder === snapshot.currentRegistryBinder &&
            currentRegistryBinder.isBinderAlive &&
            currentDeath === snapshot.currentRegistryDeath &&
            currentDeath.generation == snapshot.currentGeneration &&
            currentDeath.binder === currentRegistryBinder &&
            currentToken != null &&
            secureEquals(
                snapshot.currentRegistrationToken,
                currentToken,
            ) &&
            supervisorIdentity == snapshot.currentSupervisorIdentity &&
            supervisorPid == snapshot.currentSupervisorPid
    }

    private fun bindIfNeeded() {
        val attempt = synchronized(lock) {
            if (activeConnection != null) return
            val current = appContext ?: return
            val generation = Math.incrementExact(nextConnectionGeneration)
            nextConnectionGeneration = generation
            connectionGeneration = generation
            val connection = GenerationServiceConnection(generation)
            activeConnection = connection
            BindingAttempt(
                context = current,
                connection = connection,
            )
        }
        val started = runCatching {
            attempt.context.bindService(
                Intent(
                    attempt.context,
                    R2CompilerProcessSupervisorService::class.java,
                ),
                attempt.connection,
                Context.BIND_AUTO_CREATE,
            )
        }.getOrDefault(false)
        if (!started) {
            restartBinding(attempt.connection)
        }
    }

    private fun onServiceConnected(
        source: GenerationServiceConnection,
        name: ComponentName,
        service: IBinder,
    ) {
        val context = synchronized(lock) {
            appContext.takeIf {
                activeConnection === source &&
                    connectionGeneration == source.generation
            }
        } ?: return
        val expected = ComponentName(
            context,
            R2CompilerProcessSupervisorService::class.java,
        )
        if (
            name != expected ||
            !hasExactRemoteDescriptor(
                service,
                IR2CompilerProcessSupervisorRegistry.DESCRIPTOR,
            )
        ) {
            restartBinding(source)
            return
        }
        val remote =
            IR2CompilerProcessSupervisorRegistry.Stub.asInterface(service)
        val deathRecipient = IBinder.DeathRecipient {
            restartBinding(
                expectedConnection = source,
                expectedBinder = service,
            )
        }
        if (
            runCatching {
                service.linkToDeath(deathRecipient, 0)
            }.isFailure ||
            !service.isBinderAlive
        ) {
            restartBinding(source)
            return
        }
        var installed = false
        val oldDeath = synchronized(lock) {
            if (
                activeConnection !== source ||
                connectionGeneration != source.generation ||
                !service.isBinderAlive
            ) {
                null
            } else {
                val old = registryDeath
                registry = remote
                registryDeath = RegistryDeath(
                    binder = service,
                    recipient = deathRecipient,
                    generation = source.generation,
                )
                clearActiveRegistrationLocked()
                installed = true
                old
            }
        }
        if (installed) {
            unlinkRegistryDeath(oldDeath)
            val registrationPaused = synchronized(lock) {
                reconnectPausedForTesting
            }
            if (!registrationPaused) {
                register(remote, source.generation)
            }
        } else {
            runCatching {
                service.unlinkToDeath(deathRecipient, 0)
            }
            restartBinding(source)
        }
    }

    private fun restartBinding(
        expectedConnection: GenerationServiceConnection,
        expectedBinder: IBinder? = null,
    ) {
        val restart = synchronized(lock) {
            if (activeConnection !== expectedConnection) return
            if (
                expectedBinder != null &&
                registry?.asBinder() !== expectedBinder
            ) {
                return
            }
            val current = appContext ?: return
            val death = registryDeath
            if (BuildConfig.DEBUG) {
                val arm = retiredCallbackArmForTesting
                if (arm?.connection === expectedConnection) {
                    retiredCallbacksForTesting = if (
                        arm.generation == expectedConnection.generation &&
                        death != null &&
                        death.generation == arm.generation &&
                        death.binder === arm.registryBinder
                    ) {
                        RetiredCallbacks(
                            connection = expectedConnection,
                            registryDeath = death,
                        )
                    } else {
                        null
                    }
                    retiredCallbackArmForTesting = null
                }
            }
            activeConnection = null
            registry = null
            registryDeath = null
            clearActiveRegistrationLocked()
            RestartBinding(
                context = current,
                connection = expectedConnection,
                registryDeath = death,
            )
        }
        unlinkRegistryDeath(restart.registryDeath)
        runCatching {
            restart.context.unbindService(restart.connection)
        }
        scheduleBindingRetry()
    }

    private fun scheduleBindingRetry() {
        if (!bindingRetryScheduled.compareAndSet(false, true)) return
        runCatching {
            Thread(
                {
                    runCatching {
                        Thread.sleep(RETRY_DELAY_MILLIS)
                    }
                    bindingRetryScheduled.set(false)
                    bindIfNeeded()
                },
                "typescript-r2-supervisor-bind",
            ).apply {
                isDaemon = true
                start()
            }
        }.onFailure {
            bindingRetryScheduled.set(false)
        }
    }

    private fun register(
        remote: IR2CompilerProcessSupervisorRegistry,
        generation: Long,
    ) {
        if (
            !isCurrentRegistry(remote, generation) ||
            synchronized(lock) { reconnectPausedForTesting }
        ) {
            return
        }
        if (!registrationInFlight.compareAndSet(false, true)) {
            scheduleRegistrationRetry()
            return
        }
        val started = runCatching {
            Thread(
                {
                    var accepted = false
                    try {
                        val identity = R2CompilerProcessEpoch.identity()
                        val token = if (
                            isCurrentRegistry(remote, generation) &&
                            synchronized(lock) {
                                !reconnectPausedForTesting
                            }
                        ) {
                            runCatching {
                                remote.registerEpoch(
                                    identity.bootId,
                                    identity.uid,
                                    identity.pid,
                                    identity.startTimeTicks,
                                    identity.processName,
                                    identity.nonce,
                                    control,
                                )
                            }.getOrNull()
                        } else {
                            null
                        }
                        accepted = synchronized(lock) {
                            val handshake = pendingHandshake
                            if (
                                connectionGeneration != generation ||
                                registry?.asBinder() !== remote.asBinder() ||
                                reconnectPausedForTesting ||
                                token == null ||
                                handshake == null ||
                                !isCanonicalToken(token) ||
                                !secureEquals(handshake.token, token)
                            ) {
                                if (
                                    handshake != null &&
                                    token != null &&
                                    secureEquals(handshake.token, token)
                                ) {
                                    pendingHandshake = null
                                }
                                false
                            } else {
                                registrationToken = token
                                supervisorIdentity = handshake.identity
                                supervisorPid = handshake.pid
                                pendingHandshake = null
                                true
                            }
                        }
                    } finally {
                        registrationInFlight.set(false)
                    }
                    if (accepted) {
                        R2CompilerBootTelemetry.supervisorReady()
                        dispatchPoisonNotification()
                    } else {
                        scheduleRegistrationRetry()
                    }
                },
                "typescript-r2-supervisor-register",
            ).apply {
                isDaemon = true
                start()
            }
        }.isSuccess
        if (!started) {
            registrationInFlight.set(false)
            scheduleRegistrationRetry()
        }
    }

    private fun scheduleRegistrationRetry() {
        if (!registrationRetryScheduled.compareAndSet(false, true)) return
        runCatching {
            Thread(
                {
                    runCatching {
                        Thread.sleep(RETRY_DELAY_MILLIS)
                    }
                    registrationRetryScheduled.set(false)
                    val snapshot = synchronized(lock) {
                        val current = registry
                        if (
                            current == null ||
                            !current.asBinder().isBinderAlive ||
                            registrationToken != null ||
                            reconnectPausedForTesting
                        ) {
                            null
                        } else {
                            RegistrySnapshot(
                                registry = current,
                                generation = connectionGeneration,
                            )
                        }
                    }
                    if (snapshot != null) {
                        register(snapshot.registry, snapshot.generation)
                    }
                },
                "typescript-r2-supervisor-register-retry",
            ).apply {
                isDaemon = true
                start()
            }
        }.onFailure {
            registrationRetryScheduled.set(false)
        }
    }

    private fun dispatchPoisonNotification() {
        val snapshot = synchronized(lock) {
            if (!poisonObserved || !automaticTerminationEnabled) return
            val currentRegistry = registry ?: return
            val token = registrationToken ?: return
            if (!currentRegistry.asBinder().isBinderAlive) return
            NotificationSnapshot(
                registry = currentRegistry,
                token = token,
                identity = R2CompilerProcessEpoch.identity(),
            )
        }
        if (!notificationInFlight.compareAndSet(false, true)) return
        val started = runCatching {
            Thread(
                {
                    try {
                        runCatching {
                            snapshot.registry.notifyPoisonedEpoch(
                                snapshot.token,
                                snapshot.identity.bootId,
                                snapshot.identity.uid,
                                snapshot.identity.pid,
                                snapshot.identity.startTimeTicks,
                                snapshot.identity.processName,
                                snapshot.identity.nonce,
                            )
                        }
                    } finally {
                        notificationInFlight.set(false)
                        /*
                         * Poison is process-monotonic. Always retry against the then-current
                         * generation: an old transaction must never consume the only dispatch
                         * opportunity of a replacement supervisor connection.
                         */
                        scheduleNotificationRetry()
                    }
                },
                "typescript-r2-supervisor-notify",
            ).apply {
                isDaemon = true
                start()
            }
        }.isSuccess
        if (!started) {
            notificationInFlight.set(false)
            scheduleNotificationRetry()
        }
    }

    private fun scheduleNotificationRetry() {
        if (!notificationRetryScheduled.compareAndSet(false, true)) return
        runCatching {
            Thread(
                {
                    runCatching {
                        Thread.sleep(RETRY_DELAY_MILLIS)
                    }
                    notificationRetryScheduled.set(false)
                    dispatchPoisonNotification()
                },
                "typescript-r2-supervisor-notify-retry",
            ).apply {
                isDaemon = true
                start()
            }
        }.onFailure {
            notificationRetryScheduled.set(false)
        }
    }

    private fun isCurrentRegistry(
        remote: IR2CompilerProcessSupervisorRegistry,
        generation: Long,
    ): Boolean = synchronized(lock) {
        connectionGeneration == generation &&
            activeConnection?.generation == generation &&
            registry?.asBinder() === remote.asBinder() &&
            remote.asBinder().isBinderAlive
    }

    private fun clearActiveRegistrationLocked() {
        registrationToken = null
        supervisorIdentity = null
        supervisorPid = null
        pendingHandshake = null
    }

    private fun unlinkRegistryDeath(death: RegistryDeath?) {
        if (death == null) return
        runCatching {
            death.binder.unlinkToDeath(death.recipient, 0)
        }
    }

    private data class NotificationSnapshot(
        val registry: IR2CompilerProcessSupervisorRegistry,
        val token: String,
        val identity: R2CompilerProcessEpochIdentity,
    )

    private data class RegistrySnapshot(
        val registry: IR2CompilerProcessSupervisorRegistry,
        val generation: Long,
    )

    private data class ReconnectAction(
        val bind: Boolean = false,
        val registration: RegistrySnapshot? = null,
    )

    private data class SupervisorHandshake(
        val token: String,
        val identity: R2ObservedCompilerProcess,
        val pid: Int,
    )

    private data class RegistryDeath(
        val binder: IBinder,
        val recipient: IBinder.DeathRecipient,
        val generation: Long,
    )

    private data class BindingAttempt(
        val context: Context,
        val connection: GenerationServiceConnection,
    )

    private data class RestartBinding(
        val context: Context,
        val connection: GenerationServiceConnection,
        val registryDeath: RegistryDeath?,
    )

    private data class RetiredCallbackArm(
        val connection: GenerationServiceConnection,
        val generation: Long,
        val registryBinder: IBinder,
    )

    private data class RetiredCallbacks(
        val connection: GenerationServiceConnection,
        val registryDeath: RegistryDeath,
    )

    private data class RetiredCallbackReplaySnapshot(
        val context: Context,
        val retired: RetiredCallbacks,
        val currentConnection: GenerationServiceConnection,
        val currentGeneration: Long,
        val currentRegistryBinder: IBinder,
        val currentRegistryDeath: RegistryDeath,
        val currentRegistrationToken: String,
        val currentSupervisorIdentity: R2ObservedCompilerProcess,
        val currentSupervisorPid: Int,
    )

    private class GenerationServiceConnection(
        val generation: Long,
    ) : ServiceConnection {

        override fun onServiceConnected(
            name: ComponentName,
            service: IBinder,
        ) {
            R2CompilerProcessSupervisorClient.onServiceConnected(
                source = this,
                name = name,
                service = service,
            )
        }

        override fun onServiceDisconnected(name: ComponentName) {
            R2CompilerProcessSupervisorClient.restartBinding(this)
        }

        override fun onBindingDied(name: ComponentName) {
            R2CompilerProcessSupervisorClient.restartBinding(this)
        }

        override fun onNullBinding(name: ComponentName) {
            R2CompilerProcessSupervisorClient.restartBinding(this)
        }
    }

    private const val RETRY_DELAY_MILLIS = 250L
}

internal data class R2CompilerProcessSupervisorClientTelemetry(
    val registryConnected: Boolean,
    val registrationReady: Boolean,
    val automaticTerminationEnabled: Boolean,
    val reconnectPaused: Boolean,
    val registeredSupervisorPid: Int?,
    val registeredSupervisorStartTimeTicks: Long?,
    val connectionGeneration: Long?,
    val retiredCallbackReplayArmed: Boolean,
    val retiredCallbacksReplayed: Boolean?,
    val retiredCallbackStatePreserved: Boolean?,
    val retiredConnectionGeneration: Long?,
    val replayTargetConnectionGeneration: Long?,
    val retiredCallbackDeliveryCount: Int?,
)

private data class R2RetiredCallbackReplayTelemetry(
    val replayed: Boolean,
    val statePreserved: Boolean,
    val retiredGeneration: Long?,
    val currentGeneration: Long?,
    val deliveryCount: Int,
)

internal object R2CompilerProcessSupervisorOnlineIdentityPolicy {

    fun compilerCallerMatches(
        identity: R2CompilerProcessEpochIdentity,
        callingUid: Int,
        callingPid: Int,
        applicationUid: Int,
        supervisorBootId: String,
        expectedCompilerProcessName: String,
    ): Boolean =
        callingUid == applicationUid &&
            callingPid == identity.pid &&
            identity.uid == applicationUid &&
            identity.processName == expectedCompilerProcessName &&
            identity.bootId == supervisorBootId

    fun supervisorIdentityOrNull(
        supervisorBootId: String?,
        supervisorUid: Int,
        supervisorPid: Int,
        supervisorStartTimeTicks: Long,
        supervisorProcessName: String?,
        callingUid: Int,
        callingPid: Int,
        compilerUid: Int,
        compilerBootId: String,
        expectedSupervisorProcessName: String,
    ): R2ObservedCompilerProcess? {
        if (
            callingUid != compilerUid ||
            callingPid != supervisorPid ||
            supervisorUid != compilerUid ||
            supervisorPid <= 0 ||
            supervisorStartTimeTicks <= 0L ||
            supervisorProcessName != expectedSupervisorProcessName ||
            supervisorBootId != compilerBootId
        ) {
            return null
        }
        return R2ObservedCompilerProcess(
            bootId = compilerBootId,
            uid = supervisorUid,
            startTimeTicks = supervisorStartTimeTicks,
            processName = expectedSupervisorProcessName,
        )
    }
}

private fun identityOrNull(
    bootId: String?,
    uid: Int,
    pid: Int,
    startTimeTicks: Long,
    processName: String?,
    nonce: String?,
): R2CompilerProcessEpochIdentity? = runCatching {
    R2CompilerProcessEpochIdentity(
        bootId = requireNotNull(bootId),
        uid = uid,
        pid = pid,
        startTimeTicks = startTimeTicks,
        processName = requireNotNull(processName),
        nonce = requireNotNull(nonce),
    )
}.getOrNull()

private fun isCanonicalToken(token: String): Boolean =
    token.length == REGISTRATION_TOKEN_CHARS &&
        token.all { character ->
            character in '0'..'9' || character in 'a'..'f'
        }

private fun hasExactRemoteDescriptor(
    binder: IBinder,
    expectedDescriptor: String,
): Boolean =
    binder.isBinderAlive &&
        binder.queryLocalInterface(expectedDescriptor) == null &&
        runCatching {
            binder.interfaceDescriptor
        }.getOrNull() == expectedDescriptor

private fun secureEquals(expected: String, actual: String): Boolean {
    val expectedBytes = expected.toByteArray(Charsets.US_ASCII)
    val actualBytes = actual.toByteArray(Charsets.US_ASCII)
    return try {
        MessageDigest.isEqual(expectedBytes, actualBytes)
    } finally {
        expectedBytes.fill(0)
        actualBytes.fill(0)
    }
}

private fun ByteArray.toLowerHex(): String = buildString(size * 2) {
    this@toLowerHex.forEach { value ->
        val unsigned = value.toInt() and 0xff
        append(HEX[unsigned ushr 4])
        append(HEX[unsigned and 0x0f])
    }
}

private const val REGISTRATION_TOKEN_CHARS = 64
private const val HEX = "0123456789abcdef"
