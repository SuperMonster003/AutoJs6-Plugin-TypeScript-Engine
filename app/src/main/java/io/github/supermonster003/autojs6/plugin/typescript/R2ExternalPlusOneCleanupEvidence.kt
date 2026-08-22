package io.github.supermonster003.autojs6.plugin.typescript

import android.os.Bundle
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Private, non-shipping observation protocol for the external plus-one cleanup fixture.
 *
 * No value in this protocol grants compilation authority. The service exposes these keys only
 * after the exact TEST_ONLY evidence caller has been attested, and normal release builds compile
 * every call site behind [BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE].
 */
internal object R2ExternalPlusOneCleanupEvidenceProtocol {

    const val KEY_ENABLED =
        "__autojs6_r2_external_plus_one_cleanup_enabled"

    const val KEY_PRE_ACK_AVAILABLE =
        "__autojs6_r2_external_plus_one_cleanup_pre_ack_available"
    const val KEY_PRE_ACK_REQUEST_ID =
        "__autojs6_r2_external_plus_one_cleanup_pre_ack_request_id"
    const val KEY_PRE_ACK_JOB_TOKEN_SHA256 =
        "__autojs6_r2_external_plus_one_cleanup_pre_ack_job_token_sha256"
    const val KEY_PRE_ACK_INPUT_TARGET_SHA256 =
        "__autojs6_r2_external_plus_one_cleanup_pre_ack_input_target_sha256"
    const val KEY_PRE_ACK_OUTPUT_TARGET_SHA256 =
        "__autojs6_r2_external_plus_one_cleanup_pre_ack_output_target_sha256"
    const val KEY_PRE_ACK_INPUT_EXACT_TARGET_FD_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_pre_ack_input_exact_target_fd_count"
    const val KEY_PRE_ACK_OUTPUT_EXACT_TARGET_FD_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_pre_ack_output_exact_target_fd_count"
    const val KEY_PRE_ACK_ACTIVE_LEASE =
        "__autojs6_r2_external_plus_one_cleanup_pre_ack_active_lease"
    const val KEY_PRE_ACK_PROCESS_PID =
        "__autojs6_r2_external_plus_one_cleanup_pre_ack_process_pid"
    const val KEY_PRE_ACK_PROCESS_START_TIME_TICKS =
        "__autojs6_r2_external_plus_one_cleanup_pre_ack_process_start_time_ticks"
    const val KEY_PRE_ACK_PROCESS_NAME =
        "__autojs6_r2_external_plus_one_cleanup_pre_ack_process_name"
    const val KEY_PRE_ACK_PROCESS_EPOCH_SHA256 =
        "__autojs6_r2_external_plus_one_cleanup_pre_ack_process_epoch_sha256"

    const val KEY_RECEIPT_AVAILABLE =
        "__autojs6_r2_external_plus_one_cleanup_receipt_available"
    const val KEY_RECEIPT_REQUEST_ID =
        "__autojs6_r2_external_plus_one_cleanup_receipt_request_id"
    const val KEY_RECEIPT_JOB_TOKEN_SHA256 =
        "__autojs6_r2_external_plus_one_cleanup_receipt_job_token_sha256"
    const val KEY_RECEIPT_INPUT_TARGET_SHA256 =
        "__autojs6_r2_external_plus_one_cleanup_receipt_input_target_sha256"
    const val KEY_RECEIPT_OUTPUT_TARGET_SHA256 =
        "__autojs6_r2_external_plus_one_cleanup_receipt_output_target_sha256"
    const val KEY_RECEIPT_CREATED_BEFORE_CALLBACK =
        "__autojs6_r2_external_plus_one_cleanup_receipt_created_before_callback"
    const val KEY_RECEIPT_SNAPSHOT_AVAILABLE =
        "__autojs6_r2_external_plus_one_cleanup_receipt_snapshot_available"
    const val KEY_RECEIPT_ACTIVE_LEASE_RELEASED =
        "__autojs6_r2_external_plus_one_cleanup_receipt_active_lease_released"
    const val KEY_RECEIPT_INPUT_EXACT_TARGET_FD_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_receipt_input_exact_target_fd_count"
    const val KEY_RECEIPT_OUTPUT_EXACT_TARGET_FD_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_receipt_output_exact_target_fd_count"
    const val KEY_RECEIPT_PRIVATE_INPUT_NAMED_FILE_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_receipt_private_input_named_file_count"
    const val KEY_RECEIPT_PRIVATE_INPUT_FD_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_receipt_private_input_fd_count"
    const val KEY_RECEIPT_PROJECT_SPOOL_NAMED_FILE_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_receipt_project_spool_named_file_count"
    const val KEY_RECEIPT_PROJECT_SPOOL_FD_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_receipt_project_spool_fd_count"
    const val KEY_RECEIPT_PRIVATE_OUTPUT_NAMED_FILE_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_receipt_private_output_named_file_count"
    const val KEY_RECEIPT_PRIVATE_OUTPUT_FD_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_receipt_private_output_fd_count"
    const val KEY_RECEIPT_CURSOR_STATE =
        "__autojs6_r2_external_plus_one_cleanup_receipt_cursor_state"
    const val KEY_RECEIPT_CURSOR_OPENED_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_receipt_cursor_opened_count"
    const val KEY_RECEIPT_CURSOR_CLOSED_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_receipt_cursor_closed_count"
    const val KEY_RECEIPT_CURSOR_RELEASED =
        "__autojs6_r2_external_plus_one_cleanup_receipt_cursor_released"
    const val KEY_RECEIPT_PROCESS_PID =
        "__autojs6_r2_external_plus_one_cleanup_receipt_process_pid"
    const val KEY_RECEIPT_PROCESS_START_TIME_TICKS =
        "__autojs6_r2_external_plus_one_cleanup_receipt_process_start_time_ticks"
    const val KEY_RECEIPT_PROCESS_NAME =
        "__autojs6_r2_external_plus_one_cleanup_receipt_process_name"
    const val KEY_RECEIPT_PROCESS_EPOCH_SHA256 =
        "__autojs6_r2_external_plus_one_cleanup_receipt_process_epoch_sha256"

    const val KEY_CURRENT_AVAILABLE =
        "__autojs6_r2_external_plus_one_cleanup_current_available"
    const val KEY_CURRENT_REQUEST_ID =
        "__autojs6_r2_external_plus_one_cleanup_current_request_id"
    const val KEY_CURRENT_JOB_TOKEN_SHA256 =
        "__autojs6_r2_external_plus_one_cleanup_current_job_token_sha256"
    const val KEY_CURRENT_INPUT_TARGET_SHA256 =
        "__autojs6_r2_external_plus_one_cleanup_current_input_target_sha256"
    const val KEY_CURRENT_OUTPUT_TARGET_SHA256 =
        "__autojs6_r2_external_plus_one_cleanup_current_output_target_sha256"
    const val KEY_CURRENT_ACTIVE_LEASE_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_current_active_lease_count"
    const val KEY_CURRENT_INPUT_EXACT_TARGET_FD_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_current_input_exact_target_fd_count"
    const val KEY_CURRENT_OUTPUT_EXACT_TARGET_FD_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_current_output_exact_target_fd_count"
    const val KEY_CURRENT_PRIVATE_INPUT_NAMED_FILE_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_current_private_input_named_file_count"
    const val KEY_CURRENT_PRIVATE_INPUT_FD_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_current_private_input_fd_count"
    const val KEY_CURRENT_PROJECT_SPOOL_NAMED_FILE_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_current_project_spool_named_file_count"
    const val KEY_CURRENT_PROJECT_SPOOL_FD_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_current_project_spool_fd_count"
    const val KEY_CURRENT_PRIVATE_OUTPUT_NAMED_FILE_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_current_private_output_named_file_count"
    const val KEY_CURRENT_PRIVATE_OUTPUT_FD_COUNT =
        "__autojs6_r2_external_plus_one_cleanup_current_private_output_fd_count"
    const val KEY_CURRENT_PROCESS_PID =
        "__autojs6_r2_external_plus_one_cleanup_current_process_pid"
    const val KEY_CURRENT_PROCESS_START_TIME_TICKS =
        "__autojs6_r2_external_plus_one_cleanup_current_process_start_time_ticks"
    const val KEY_CURRENT_PROCESS_NAME =
        "__autojs6_r2_external_plus_one_cleanup_current_process_name"
    const val KEY_CURRENT_PROCESS_EPOCH_SHA256 =
        "__autojs6_r2_external_plus_one_cleanup_current_process_epoch_sha256"

    const val CURSOR_STATE_NOT_CREATED = "NOT_CREATED"
    const val CURSOR_STATE_RELEASED = "RELEASED"
    const val CURSOR_STATE_UNPROVEN = "UNPROVEN"
}

internal interface R2ExternalPlusOneCleanupEvidenceOperations {

    fun readLink(path: String): String = Os.readlink(path)

    fun listFdNames(): Array<String>? = File(PROC_SELF_FD).list()

    fun listChildren(directory: File): Array<File>? = directory.listFiles()

    fun isRegularFile(file: File): Boolean = file.isFile

    companion object {
        const val PROC_SELF_FD = "/proc/self/fd"
    }
}

private object R2SystemExternalPlusOneCleanupEvidenceOperations :
    R2ExternalPlusOneCleanupEvidenceOperations

internal data class R2ExternalPlusOneCleanupSafeProcessIdentity(
    val pid: Int,
    val startTimeTicks: Long,
    val processName: String,
    val epochSha256: String,
) {

    companion object {
        fun capture(): R2ExternalPlusOneCleanupSafeProcessIdentity {
            val identity = R2CompilerProcessEpoch.identity()
            /*
             * The boot ID and nonce are termination authority and never leave this method. The
             * domain-separated digest is safe for same-epoch equality only and is infeasible to
             * invert because the process nonce contributes 256 random bits.
             */
            val epochSha256 = R2Digests.sha256(
                listOf(
                    "autojs6-r2-external-plus-one-cleanup-epoch-v1",
                    identity.bootId,
                    identity.uid.toString(),
                    identity.pid.toString(),
                    identity.startTimeTicks.toString(),
                    identity.processName,
                    identity.nonce,
                ).joinToString(separator = "\u0000")
                    .toByteArray(StandardCharsets.UTF_8),
            )
            return R2ExternalPlusOneCleanupSafeProcessIdentity(
                pid = identity.pid,
                startTimeTicks = identity.startTimeTicks,
                processName = identity.processName,
                epochSha256 = epochSha256,
            )
        }
    }
}

internal data class R2ExternalPlusOneCleanupDescriptorTargets(
    internal val inputTarget: String?,
    internal val outputTarget: String?,
) {

    val inputTargetSha256: String?
        get() = inputTarget?.sha256Utf8()

    val outputTargetSha256: String?
        get() = outputTarget?.sha256Utf8()
}

internal data class R2ExternalPlusOneCleanupPreAcknowledgement(
    val requestId: String,
    val jobTokenSha256: String,
    val descriptorTargets: R2ExternalPlusOneCleanupDescriptorTargets,
    val descriptorSnapshotAvailable: Boolean,
    val inputExactTargetFdCount: Int,
    val outputExactTargetFdCount: Int,
    val activeLease: Boolean,
    val processIdentity: R2ExternalPlusOneCleanupSafeProcessIdentity,
) {

    val exactPostTransactionOwnershipProven: Boolean
        get() =
            descriptorSnapshotAvailable &&
                inputExactTargetFdCount == 1 &&
                outputExactTargetFdCount == 1 &&
                activeLease
}

internal data class R2ExternalPlusOneCleanupResourceSnapshot(
    val available: Boolean,
    val inputExactTargetFdCount: Int,
    val outputExactTargetFdCount: Int,
    val privateInputNamedFileCount: Int,
    val privateInputFdCount: Int,
    val projectSpoolNamedFileCount: Int,
    val projectSpoolFdCount: Int,
    val privateOutputNamedFileCount: Int,
    val privateOutputFdCount: Int,
)

internal data class R2ExternalPlusOneCleanupTerminalReceipt(
    val requestId: String,
    val jobTokenSha256: String,
    val descriptorTargets: R2ExternalPlusOneCleanupDescriptorTargets,
    val createdBeforeCallback: Boolean,
    val activeLeaseReleased: Boolean,
    val resources: R2ExternalPlusOneCleanupResourceSnapshot,
    val cursor: R2HostProjectCursorCleanupEvidence,
    val processIdentity: R2ExternalPlusOneCleanupSafeProcessIdentity,
) {

    val available: Boolean
        get() =
            activeLeaseReleased &&
                resources.available &&
                cursor.cleanupProven
}

internal data class R2ExternalPlusOneCleanupCurrentSnapshot(
    val requestId: String,
    val jobTokenSha256: String,
    val descriptorTargets: R2ExternalPlusOneCleanupDescriptorTargets,
    val activeLeaseCount: Int,
    val resources: R2ExternalPlusOneCleanupResourceSnapshot,
    val processIdentity: R2ExternalPlusOneCleanupSafeProcessIdentity,
) {

    val available: Boolean
        get() = resources.available
}

internal data class R2ExternalPlusOneCleanupEvidenceState(
    val preAcknowledgement: R2ExternalPlusOneCleanupPreAcknowledgement?,
    val terminalReceipt: R2ExternalPlusOneCleanupTerminalReceipt?,
    val currentSnapshot: R2ExternalPlusOneCleanupCurrentSnapshot?,
) {

    fun putInto(bundle: Bundle) {
        bundle.putBoolean(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_ENABLED,
            true,
        )
        putPreAcknowledgement(bundle, preAcknowledgement)
        putTerminalReceipt(bundle, terminalReceipt)
        putCurrentSnapshot(bundle, currentSnapshot)
    }

    private fun putPreAcknowledgement(
        bundle: Bundle,
        evidence: R2ExternalPlusOneCleanupPreAcknowledgement?,
    ) {
        bundle.putBoolean(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_PRE_ACK_AVAILABLE,
            evidence?.descriptorSnapshotAvailable == true,
        )
        evidence ?: return
        bundle.putString(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_PRE_ACK_REQUEST_ID,
            evidence.requestId,
        )
        bundle.putString(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_PRE_ACK_JOB_TOKEN_SHA256,
            evidence.jobTokenSha256,
        )
        evidence.descriptorTargets.inputTargetSha256?.let { digest ->
            bundle.putString(
                R2ExternalPlusOneCleanupEvidenceProtocol.KEY_PRE_ACK_INPUT_TARGET_SHA256,
                digest,
            )
        }
        evidence.descriptorTargets.outputTargetSha256?.let { digest ->
            bundle.putString(
                R2ExternalPlusOneCleanupEvidenceProtocol.KEY_PRE_ACK_OUTPUT_TARGET_SHA256,
                digest,
            )
        }
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol
                .KEY_PRE_ACK_INPUT_EXACT_TARGET_FD_COUNT,
            evidence.inputExactTargetFdCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol
                .KEY_PRE_ACK_OUTPUT_EXACT_TARGET_FD_COUNT,
            evidence.outputExactTargetFdCount,
        )
        bundle.putBoolean(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_PRE_ACK_ACTIVE_LEASE,
            evidence.activeLease,
        )
        putPreAckIdentity(bundle, evidence.processIdentity)
    }

    private fun putTerminalReceipt(
        bundle: Bundle,
        receipt: R2ExternalPlusOneCleanupTerminalReceipt?,
    ) {
        bundle.putBoolean(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_AVAILABLE,
            receipt?.available == true,
        )
        receipt ?: return
        bundle.putString(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_REQUEST_ID,
            receipt.requestId,
        )
        bundle.putString(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_JOB_TOKEN_SHA256,
            receipt.jobTokenSha256,
        )
        receipt.descriptorTargets.inputTargetSha256?.let { digest ->
            bundle.putString(
                R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_INPUT_TARGET_SHA256,
                digest,
            )
        }
        receipt.descriptorTargets.outputTargetSha256?.let { digest ->
            bundle.putString(
                R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_OUTPUT_TARGET_SHA256,
                digest,
            )
        }
        bundle.putBoolean(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_CREATED_BEFORE_CALLBACK,
            receipt.createdBeforeCallback,
        )
        bundle.putBoolean(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_SNAPSHOT_AVAILABLE,
            receipt.resources.available,
        )
        bundle.putBoolean(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_ACTIVE_LEASE_RELEASED,
            receipt.activeLeaseReleased,
        )
        putReceiptResources(bundle, receipt.resources)
        bundle.putString(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_CURSOR_STATE,
            receipt.cursor.state,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_CURSOR_OPENED_COUNT,
            receipt.cursor.openedCursorCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_CURSOR_CLOSED_COUNT,
            receipt.cursor.closedCursorCount,
        )
        bundle.putBoolean(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_CURSOR_RELEASED,
            receipt.cursor.released,
        )
        putReceiptIdentity(bundle, receipt.processIdentity)
    }

    private fun putCurrentSnapshot(
        bundle: Bundle,
        snapshot: R2ExternalPlusOneCleanupCurrentSnapshot?,
    ) {
        bundle.putBoolean(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_CURRENT_AVAILABLE,
            snapshot?.available == true,
        )
        snapshot ?: return
        bundle.putString(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_CURRENT_REQUEST_ID,
            snapshot.requestId,
        )
        bundle.putString(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_CURRENT_JOB_TOKEN_SHA256,
            snapshot.jobTokenSha256,
        )
        snapshot.descriptorTargets.inputTargetSha256?.let { digest ->
            bundle.putString(
                R2ExternalPlusOneCleanupEvidenceProtocol.KEY_CURRENT_INPUT_TARGET_SHA256,
                digest,
            )
        }
        snapshot.descriptorTargets.outputTargetSha256?.let { digest ->
            bundle.putString(
                R2ExternalPlusOneCleanupEvidenceProtocol.KEY_CURRENT_OUTPUT_TARGET_SHA256,
                digest,
            )
        }
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_CURRENT_ACTIVE_LEASE_COUNT,
            snapshot.activeLeaseCount,
        )
        putCurrentResources(bundle, snapshot.resources)
        putCurrentIdentity(bundle, snapshot.processIdentity)
    }

    private fun putReceiptResources(
        bundle: Bundle,
        resources: R2ExternalPlusOneCleanupResourceSnapshot,
    ) {
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol
                .KEY_RECEIPT_INPUT_EXACT_TARGET_FD_COUNT,
            resources.inputExactTargetFdCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol
                .KEY_RECEIPT_OUTPUT_EXACT_TARGET_FD_COUNT,
            resources.outputExactTargetFdCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol
                .KEY_RECEIPT_PRIVATE_INPUT_NAMED_FILE_COUNT,
            resources.privateInputNamedFileCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_PRIVATE_INPUT_FD_COUNT,
            resources.privateInputFdCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol
                .KEY_RECEIPT_PROJECT_SPOOL_NAMED_FILE_COUNT,
            resources.projectSpoolNamedFileCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_PROJECT_SPOOL_FD_COUNT,
            resources.projectSpoolFdCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol
                .KEY_RECEIPT_PRIVATE_OUTPUT_NAMED_FILE_COUNT,
            resources.privateOutputNamedFileCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_PRIVATE_OUTPUT_FD_COUNT,
            resources.privateOutputFdCount,
        )
    }

    private fun putCurrentResources(
        bundle: Bundle,
        resources: R2ExternalPlusOneCleanupResourceSnapshot,
    ) {
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol
                .KEY_CURRENT_INPUT_EXACT_TARGET_FD_COUNT,
            resources.inputExactTargetFdCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol
                .KEY_CURRENT_OUTPUT_EXACT_TARGET_FD_COUNT,
            resources.outputExactTargetFdCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol
                .KEY_CURRENT_PRIVATE_INPUT_NAMED_FILE_COUNT,
            resources.privateInputNamedFileCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_CURRENT_PRIVATE_INPUT_FD_COUNT,
            resources.privateInputFdCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol
                .KEY_CURRENT_PROJECT_SPOOL_NAMED_FILE_COUNT,
            resources.projectSpoolNamedFileCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_CURRENT_PROJECT_SPOOL_FD_COUNT,
            resources.projectSpoolFdCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol
                .KEY_CURRENT_PRIVATE_OUTPUT_NAMED_FILE_COUNT,
            resources.privateOutputNamedFileCount,
        )
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_CURRENT_PRIVATE_OUTPUT_FD_COUNT,
            resources.privateOutputFdCount,
        )
    }

    private fun putPreAckIdentity(
        bundle: Bundle,
        identity: R2ExternalPlusOneCleanupSafeProcessIdentity,
    ) {
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_PRE_ACK_PROCESS_PID,
            identity.pid,
        )
        bundle.putLong(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_PRE_ACK_PROCESS_START_TIME_TICKS,
            identity.startTimeTicks,
        )
        bundle.putString(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_PRE_ACK_PROCESS_NAME,
            identity.processName,
        )
        bundle.putString(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_PRE_ACK_PROCESS_EPOCH_SHA256,
            identity.epochSha256,
        )
    }

    private fun putReceiptIdentity(
        bundle: Bundle,
        identity: R2ExternalPlusOneCleanupSafeProcessIdentity,
    ) {
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_PROCESS_PID,
            identity.pid,
        )
        bundle.putLong(
            R2ExternalPlusOneCleanupEvidenceProtocol
                .KEY_RECEIPT_PROCESS_START_TIME_TICKS,
            identity.startTimeTicks,
        )
        bundle.putString(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_PROCESS_NAME,
            identity.processName,
        )
        bundle.putString(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_RECEIPT_PROCESS_EPOCH_SHA256,
            identity.epochSha256,
        )
    }

    private fun putCurrentIdentity(
        bundle: Bundle,
        identity: R2ExternalPlusOneCleanupSafeProcessIdentity,
    ) {
        bundle.putInt(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_CURRENT_PROCESS_PID,
            identity.pid,
        )
        bundle.putLong(
            R2ExternalPlusOneCleanupEvidenceProtocol
                .KEY_CURRENT_PROCESS_START_TIME_TICKS,
            identity.startTimeTicks,
        )
        bundle.putString(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_CURRENT_PROCESS_NAME,
            identity.processName,
        )
        bundle.putString(
            R2ExternalPlusOneCleanupEvidenceProtocol.KEY_CURRENT_PROCESS_EPOCH_SHA256,
            identity.epochSha256,
        )
    }
}

internal object R2ExternalPlusOneCleanupEvidence {

    fun capturePreAcknowledgement(
        requestId: String,
        jobToken: String,
        inputFd: Int,
        outputFd: Int,
        activeLease: Boolean,
        operations: R2ExternalPlusOneCleanupEvidenceOperations =
            R2SystemExternalPlusOneCleanupEvidenceOperations,
    ): R2ExternalPlusOneCleanupPreAcknowledgement {
        val targets = R2ExternalPlusOneCleanupDescriptorTargets(
            inputTarget = readExactDescriptorTarget(inputFd, operations),
            outputTarget = readExactDescriptorTarget(outputFd, operations),
        )
        val fdSnapshot = enumerateFdTargets(operations)
        return R2ExternalPlusOneCleanupPreAcknowledgement(
            requestId = requestId,
            jobTokenSha256 = jobToken.sha256Utf8(),
            descriptorTargets = targets,
            descriptorSnapshotAvailable =
                targets.inputTarget != null &&
                    targets.outputTarget != null &&
                    fdSnapshot.available,
            inputExactTargetFdCount =
                fdSnapshot.countExactTarget(targets.inputTarget),
            outputExactTargetFdCount =
                fdSnapshot.countExactTarget(targets.outputTarget),
            activeLease = activeLease,
            processIdentity =
                R2ExternalPlusOneCleanupSafeProcessIdentity.capture(),
        )
    }

    fun captureResourceSnapshot(
        descriptorTargets: R2ExternalPlusOneCleanupDescriptorTargets,
        inputStagingDirectory: File,
        outputStagingDirectory: File,
        operations: R2ExternalPlusOneCleanupEvidenceOperations =
            R2SystemExternalPlusOneCleanupEvidenceOperations,
    ): R2ExternalPlusOneCleanupResourceSnapshot {
        val fdSnapshot = enumerateFdTargets(operations)
        val namedSnapshot = enumerateNamedTransients(
            inputStagingDirectory = inputStagingDirectory,
            outputStagingDirectory = outputStagingDirectory,
            operations = operations,
        )
        return R2ExternalPlusOneCleanupResourceSnapshot(
            available =
                descriptorTargets.inputTarget != null &&
                    descriptorTargets.outputTarget != null &&
                    fdSnapshot.available &&
                    namedSnapshot.available,
            inputExactTargetFdCount =
                fdSnapshot.countExactTarget(descriptorTargets.inputTarget),
            outputExactTargetFdCount =
                fdSnapshot.countExactTarget(descriptorTargets.outputTarget),
            privateInputNamedFileCount =
                namedSnapshot.count(R2PrivateTransientKind.PRIVATE_INPUT),
            privateInputFdCount =
                fdSnapshot.countTransient(R2PrivateTransientKind.PRIVATE_INPUT),
            projectSpoolNamedFileCount =
                namedSnapshot.count(R2PrivateTransientKind.PROJECT_SPOOL),
            projectSpoolFdCount =
                fdSnapshot.countTransient(R2PrivateTransientKind.PROJECT_SPOOL),
            privateOutputNamedFileCount =
                namedSnapshot.count(R2PrivateTransientKind.PRIVATE_OUTPUT),
            privateOutputFdCount =
                fdSnapshot.countTransient(R2PrivateTransientKind.PRIVATE_OUTPUT),
        )
    }

    fun captureTerminalReceiptBeforeCallback(
        preAcknowledgement: R2ExternalPlusOneCleanupPreAcknowledgement,
        activeLeaseReleased: Boolean,
        cursor: R2HostProjectCursorCleanupEvidence,
        inputStagingDirectory: File,
        outputStagingDirectory: File,
        operations: R2ExternalPlusOneCleanupEvidenceOperations =
            R2SystemExternalPlusOneCleanupEvidenceOperations,
    ) = R2ExternalPlusOneCleanupTerminalReceipt(
        requestId = preAcknowledgement.requestId,
        jobTokenSha256 = preAcknowledgement.jobTokenSha256,
        descriptorTargets = preAcknowledgement.descriptorTargets,
        createdBeforeCallback = true,
        activeLeaseReleased = activeLeaseReleased,
        resources = captureResourceSnapshot(
            descriptorTargets = preAcknowledgement.descriptorTargets,
            inputStagingDirectory = inputStagingDirectory,
            outputStagingDirectory = outputStagingDirectory,
            operations = operations,
        ),
        cursor = cursor,
        processIdentity = R2ExternalPlusOneCleanupSafeProcessIdentity.capture(),
    )

    fun captureCurrentSnapshot(
        preAcknowledgement: R2ExternalPlusOneCleanupPreAcknowledgement,
        activeLeaseCount: Int,
        inputStagingDirectory: File,
        outputStagingDirectory: File,
        operations: R2ExternalPlusOneCleanupEvidenceOperations =
            R2SystemExternalPlusOneCleanupEvidenceOperations,
    ) = R2ExternalPlusOneCleanupCurrentSnapshot(
        requestId = preAcknowledgement.requestId,
        jobTokenSha256 = preAcknowledgement.jobTokenSha256,
        descriptorTargets = preAcknowledgement.descriptorTargets,
        activeLeaseCount = activeLeaseCount,
        resources = captureResourceSnapshot(
            descriptorTargets = preAcknowledgement.descriptorTargets,
            inputStagingDirectory = inputStagingDirectory,
            outputStagingDirectory = outputStagingDirectory,
            operations = operations,
        ),
        processIdentity = R2ExternalPlusOneCleanupSafeProcessIdentity.capture(),
    )

    private fun readExactDescriptorTarget(
        fd: Int,
        operations: R2ExternalPlusOneCleanupEvidenceOperations,
    ): String? {
        if (fd < 0) return null
        return runCatching {
            operations.readLink(
                "${R2ExternalPlusOneCleanupEvidenceOperations.PROC_SELF_FD}/$fd",
            ).withoutDeletedSuffix()
        }.getOrNull()
    }

    private fun enumerateFdTargets(
        operations: R2ExternalPlusOneCleanupEvidenceOperations,
    ): R2ExternalPlusOneCleanupFdEnumeration {
        val names = try {
            operations.listFdNames()
        } catch (_: Throwable) {
            return R2ExternalPlusOneCleanupFdEnumeration.unavailable()
        } ?: return R2ExternalPlusOneCleanupFdEnumeration.unavailable()
        val targets = ArrayList<String>(names.size)
        for (name in names) {
            if (name.isEmpty() || name.any { character -> !character.isDigit() }) continue
            val target = try {
                operations.readLink(
                    "${R2ExternalPlusOneCleanupEvidenceOperations.PROC_SELF_FD}/$name",
                )
            } catch (failure: ErrnoException) {
                if (failure.errno == OsConstants.ENOENT) {
                    // The descriptor closed between list() and readlink(); it is not live.
                    continue
                }
                return R2ExternalPlusOneCleanupFdEnumeration.unavailable()
            } catch (_: Throwable) {
                return R2ExternalPlusOneCleanupFdEnumeration.unavailable()
            }
            targets += target.withoutDeletedSuffix()
        }
        return R2ExternalPlusOneCleanupFdEnumeration(
            available = true,
            targets = targets,
        )
    }

    private fun enumerateNamedTransients(
        inputStagingDirectory: File,
        outputStagingDirectory: File,
        operations: R2ExternalPlusOneCleanupEvidenceOperations,
    ): R2ExternalPlusOneCleanupNamedEnumeration {
        val counts = mutableMapOf<R2PrivateTransientKind, Int>()
        for (directory in listOf(inputStagingDirectory, outputStagingDirectory)) {
            val children = try {
                operations.listChildren(directory)
            } catch (_: Throwable) {
                return R2ExternalPlusOneCleanupNamedEnumeration.unavailable()
            } ?: return R2ExternalPlusOneCleanupNamedEnumeration.unavailable()
            for (child in children) {
                val kind = R2PrivateTransientOrphanScavenger.recognizedKind(
                    directoryName = directory.name,
                    childName = child.name,
                ) ?: continue
                val regular = try {
                    operations.isRegularFile(child)
                } catch (_: Throwable) {
                    return R2ExternalPlusOneCleanupNamedEnumeration.unavailable()
                }
                if (regular) {
                    counts[kind] = Math.addExact(counts[kind] ?: 0, 1)
                }
            }
        }
        return R2ExternalPlusOneCleanupNamedEnumeration(
            available = true,
            counts = counts,
        )
    }
}

private data class R2ExternalPlusOneCleanupFdEnumeration(
    val available: Boolean,
    val targets: List<String>,
) {

    fun countExactTarget(target: String?): Int =
        if (!available || target == null) {
            UNAVAILABLE_COUNT
        } else {
            targets.count { candidate -> candidate == target }
        }

    fun countTransient(kind: R2PrivateTransientKind): Int =
        if (!available) {
            UNAVAILABLE_COUNT
        } else {
            targets.count { target ->
                val path = File(target)
                R2PrivateTransientOrphanScavenger.recognizedKind(
                    directoryName = path.parentFile?.name.orEmpty(),
                    childName = path.name,
                ) == kind
            }
        }

    companion object {
        fun unavailable() = R2ExternalPlusOneCleanupFdEnumeration(
            available = false,
            targets = emptyList(),
        )
    }
}

private data class R2ExternalPlusOneCleanupNamedEnumeration(
    val available: Boolean,
    val counts: Map<R2PrivateTransientKind, Int>,
) {

    fun count(kind: R2PrivateTransientKind): Int =
        if (available) counts[kind] ?: 0 else UNAVAILABLE_COUNT

    companion object {
        fun unavailable() = R2ExternalPlusOneCleanupNamedEnumeration(
            available = false,
            counts = emptyMap(),
        )
    }
}

private fun String.withoutDeletedSuffix(): String =
    removeSuffix(DELETED_FD_TARGET_SUFFIX)

private fun String.sha256Utf8(): String =
    R2Digests.sha256(toByteArray(StandardCharsets.UTF_8))

private const val DELETED_FD_TARGET_SUFFIX = " (deleted)"
private const val UNAVAILABLE_COUNT = -1
