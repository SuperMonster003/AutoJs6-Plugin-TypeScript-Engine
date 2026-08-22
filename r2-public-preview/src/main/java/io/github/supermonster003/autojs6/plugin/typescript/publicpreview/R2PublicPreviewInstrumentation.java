package io.github.supermonster003.autojs6.plugin.typescript.publicpreview;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback;
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin;
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract;
import org.autojs.plugin.typescript.api.TypeScriptPluginCapabilityKeys;
import org.autojs.plugin.typescript.api.TypeScriptR2Contract;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * External-UID evidence for the additive public R2 preview Bundle shape.
 *
 * <p>This is a standalone application APK. Its instrumentation targets this APK itself, then binds
 * the exported compiler service in the real plugin package under a distinct application UID.
 */
public final class R2PublicPreviewInstrumentation extends Instrumentation {

    private static final String LOG_TAG = "R2PublicPreview";
    private static final String ARG_EXTERNAL_CAPACITY_EVIDENCE =
            "externalCapacityEvidence";
    private static final String ARG_EXTERNAL_PLUS_ONE_CLEANUP_EVIDENCE =
            "externalPlusOneCleanupEvidence";
    private static final String ARG_EXTERNAL_NATURAL_EMIT_TIMEOUT_EVIDENCE =
            "externalNaturalEmitTimeoutEvidence";
    private static final String
            ARG_EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_EVIDENCE =
                    "externalNaturalSemanticTimeoutEvidence";
    private static final String
            ARG_EXTERNAL_NATURAL_PARSE_TIMEOUT_EVIDENCE =
                    "externalNaturalParseTimeoutEvidence";
    private static final String
            ARG_SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_EVIDENCE =
                    "shippingReleaseNaturalParseTimeoutEvidence";
    private static final String EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY =
            "__autojs6_r2_external_capacity_evidence_scope";
    private static final String STORED_CONTAINER_EXACT_64_SCOPE =
            "stored-container-exact-64-v1";
    private static final String DEFLATED_EXPANDED_EXACT_64_SCOPE =
            "deflated-expanded-exact-64-v1";
    private static final String CLEANUP_TELEMETRY_PREFIX =
            "__autojs6_r2_external_plus_one_cleanup_";
    private static final String CLEANUP_KEY_ENABLED =
            CLEANUP_TELEMETRY_PREFIX + "enabled";
    private static final String CLEANUP_KEY_PRE_ACK_AVAILABLE =
            CLEANUP_TELEMETRY_PREFIX + "pre_ack_available";
    private static final String CLEANUP_KEY_PRE_ACK_REQUEST_ID =
            CLEANUP_TELEMETRY_PREFIX + "pre_ack_request_id";
    private static final String CLEANUP_KEY_PRE_ACK_JOB_TOKEN_SHA256 =
            CLEANUP_TELEMETRY_PREFIX + "pre_ack_job_token_sha256";
    private static final String CLEANUP_KEY_PRE_ACK_INPUT_TARGET_SHA256 =
            CLEANUP_TELEMETRY_PREFIX + "pre_ack_input_target_sha256";
    private static final String CLEANUP_KEY_PRE_ACK_OUTPUT_TARGET_SHA256 =
            CLEANUP_TELEMETRY_PREFIX + "pre_ack_output_target_sha256";
    private static final String
            CLEANUP_KEY_PRE_ACK_INPUT_EXACT_TARGET_FD_COUNT =
                    CLEANUP_TELEMETRY_PREFIX
                            + "pre_ack_input_exact_target_fd_count";
    private static final String
            CLEANUP_KEY_PRE_ACK_OUTPUT_EXACT_TARGET_FD_COUNT =
                    CLEANUP_TELEMETRY_PREFIX
                            + "pre_ack_output_exact_target_fd_count";
    private static final String CLEANUP_KEY_PRE_ACK_ACTIVE_LEASE =
            CLEANUP_TELEMETRY_PREFIX + "pre_ack_active_lease";
    private static final String CLEANUP_KEY_PRE_ACK_PROCESS_PID =
            CLEANUP_TELEMETRY_PREFIX + "pre_ack_process_pid";
    private static final String CLEANUP_KEY_PRE_ACK_PROCESS_START_TIME_TICKS =
            CLEANUP_TELEMETRY_PREFIX + "pre_ack_process_start_time_ticks";
    private static final String CLEANUP_KEY_PRE_ACK_PROCESS_NAME =
            CLEANUP_TELEMETRY_PREFIX + "pre_ack_process_name";
    private static final String CLEANUP_KEY_PRE_ACK_PROCESS_EPOCH_SHA256 =
            CLEANUP_TELEMETRY_PREFIX + "pre_ack_process_epoch_sha256";
    private static final String CLEANUP_KEY_RECEIPT_AVAILABLE =
            CLEANUP_TELEMETRY_PREFIX + "receipt_available";
    private static final String CLEANUP_KEY_RECEIPT_REQUEST_ID =
            CLEANUP_TELEMETRY_PREFIX + "receipt_request_id";
    private static final String CLEANUP_KEY_RECEIPT_JOB_TOKEN_SHA256 =
            CLEANUP_TELEMETRY_PREFIX + "receipt_job_token_sha256";
    private static final String CLEANUP_KEY_RECEIPT_INPUT_TARGET_SHA256 =
            CLEANUP_TELEMETRY_PREFIX + "receipt_input_target_sha256";
    private static final String CLEANUP_KEY_RECEIPT_OUTPUT_TARGET_SHA256 =
            CLEANUP_TELEMETRY_PREFIX + "receipt_output_target_sha256";
    private static final String CLEANUP_KEY_RECEIPT_SNAPSHOT_AVAILABLE =
            CLEANUP_TELEMETRY_PREFIX + "receipt_snapshot_available";
    private static final String CLEANUP_KEY_RECEIPT_ACTIVE_LEASE_RELEASED =
            CLEANUP_TELEMETRY_PREFIX + "receipt_active_lease_released";
    private static final String
            CLEANUP_KEY_RECEIPT_INPUT_EXACT_TARGET_FD_COUNT =
                    CLEANUP_TELEMETRY_PREFIX
                            + "receipt_input_exact_target_fd_count";
    private static final String
            CLEANUP_KEY_RECEIPT_OUTPUT_EXACT_TARGET_FD_COUNT =
                    CLEANUP_TELEMETRY_PREFIX
                            + "receipt_output_exact_target_fd_count";
    private static final String
            CLEANUP_KEY_RECEIPT_PRIVATE_INPUT_NAMED_FILE_COUNT =
                    CLEANUP_TELEMETRY_PREFIX
                            + "receipt_private_input_named_file_count";
    private static final String CLEANUP_KEY_RECEIPT_PRIVATE_INPUT_FD_COUNT =
            CLEANUP_TELEMETRY_PREFIX + "receipt_private_input_fd_count";
    private static final String
            CLEANUP_KEY_RECEIPT_PROJECT_SPOOL_NAMED_FILE_COUNT =
                    CLEANUP_TELEMETRY_PREFIX
                            + "receipt_project_spool_named_file_count";
    private static final String CLEANUP_KEY_RECEIPT_PROJECT_SPOOL_FD_COUNT =
            CLEANUP_TELEMETRY_PREFIX + "receipt_project_spool_fd_count";
    private static final String
            CLEANUP_KEY_RECEIPT_PRIVATE_OUTPUT_NAMED_FILE_COUNT =
                    CLEANUP_TELEMETRY_PREFIX
                            + "receipt_private_output_named_file_count";
    private static final String CLEANUP_KEY_RECEIPT_PRIVATE_OUTPUT_FD_COUNT =
            CLEANUP_TELEMETRY_PREFIX + "receipt_private_output_fd_count";
    private static final String CLEANUP_KEY_RECEIPT_CURSOR_STATE =
            CLEANUP_TELEMETRY_PREFIX + "receipt_cursor_state";
    private static final String CLEANUP_KEY_RECEIPT_CURSOR_OPENED_COUNT =
            CLEANUP_TELEMETRY_PREFIX + "receipt_cursor_opened_count";
    private static final String CLEANUP_KEY_RECEIPT_CURSOR_CLOSED_COUNT =
            CLEANUP_TELEMETRY_PREFIX + "receipt_cursor_closed_count";
    private static final String CLEANUP_KEY_RECEIPT_CURSOR_RELEASED =
            CLEANUP_TELEMETRY_PREFIX + "receipt_cursor_released";
    private static final String CLEANUP_KEY_RECEIPT_CREATED_BEFORE_CALLBACK =
            CLEANUP_TELEMETRY_PREFIX + "receipt_created_before_callback";
    private static final String CLEANUP_KEY_RECEIPT_PROCESS_PID =
            CLEANUP_TELEMETRY_PREFIX + "receipt_process_pid";
    private static final String CLEANUP_KEY_RECEIPT_PROCESS_START_TIME_TICKS =
            CLEANUP_TELEMETRY_PREFIX + "receipt_process_start_time_ticks";
    private static final String CLEANUP_KEY_RECEIPT_PROCESS_NAME =
            CLEANUP_TELEMETRY_PREFIX + "receipt_process_name";
    private static final String CLEANUP_KEY_RECEIPT_PROCESS_EPOCH_SHA256 =
            CLEANUP_TELEMETRY_PREFIX + "receipt_process_epoch_sha256";
    private static final String CLEANUP_KEY_CURRENT_AVAILABLE =
            CLEANUP_TELEMETRY_PREFIX + "current_available";
    private static final String CLEANUP_KEY_CURRENT_REQUEST_ID =
            CLEANUP_TELEMETRY_PREFIX + "current_request_id";
    private static final String CLEANUP_KEY_CURRENT_JOB_TOKEN_SHA256 =
            CLEANUP_TELEMETRY_PREFIX + "current_job_token_sha256";
    private static final String CLEANUP_KEY_CURRENT_INPUT_TARGET_SHA256 =
            CLEANUP_TELEMETRY_PREFIX + "current_input_target_sha256";
    private static final String CLEANUP_KEY_CURRENT_OUTPUT_TARGET_SHA256 =
            CLEANUP_TELEMETRY_PREFIX + "current_output_target_sha256";
    private static final String CLEANUP_KEY_CURRENT_ACTIVE_LEASE_COUNT =
            CLEANUP_TELEMETRY_PREFIX + "current_active_lease_count";
    private static final String
            CLEANUP_KEY_CURRENT_INPUT_EXACT_TARGET_FD_COUNT =
                    CLEANUP_TELEMETRY_PREFIX
                            + "current_input_exact_target_fd_count";
    private static final String
            CLEANUP_KEY_CURRENT_OUTPUT_EXACT_TARGET_FD_COUNT =
                    CLEANUP_TELEMETRY_PREFIX
                            + "current_output_exact_target_fd_count";
    private static final String
            CLEANUP_KEY_CURRENT_PRIVATE_INPUT_NAMED_FILE_COUNT =
                    CLEANUP_TELEMETRY_PREFIX
                            + "current_private_input_named_file_count";
    private static final String CLEANUP_KEY_CURRENT_PRIVATE_INPUT_FD_COUNT =
            CLEANUP_TELEMETRY_PREFIX + "current_private_input_fd_count";
    private static final String
            CLEANUP_KEY_CURRENT_PROJECT_SPOOL_NAMED_FILE_COUNT =
                    CLEANUP_TELEMETRY_PREFIX
                            + "current_project_spool_named_file_count";
    private static final String CLEANUP_KEY_CURRENT_PROJECT_SPOOL_FD_COUNT =
            CLEANUP_TELEMETRY_PREFIX + "current_project_spool_fd_count";
    private static final String
            CLEANUP_KEY_CURRENT_PRIVATE_OUTPUT_NAMED_FILE_COUNT =
                    CLEANUP_TELEMETRY_PREFIX
                            + "current_private_output_named_file_count";
    private static final String CLEANUP_KEY_CURRENT_PRIVATE_OUTPUT_FD_COUNT =
            CLEANUP_TELEMETRY_PREFIX + "current_private_output_fd_count";
    private static final String CLEANUP_KEY_CURRENT_PROCESS_PID =
            CLEANUP_TELEMETRY_PREFIX + "current_process_pid";
    private static final String CLEANUP_KEY_CURRENT_PROCESS_START_TIME_TICKS =
            CLEANUP_TELEMETRY_PREFIX + "current_process_start_time_ticks";
    private static final String CLEANUP_KEY_CURRENT_PROCESS_NAME =
            CLEANUP_TELEMETRY_PREFIX + "current_process_name";
    private static final String CLEANUP_KEY_CURRENT_PROCESS_EPOCH_SHA256 =
            CLEANUP_TELEMETRY_PREFIX + "current_process_epoch_sha256";
    private static final String NATURAL_PHASE_TIMEOUT_PREFIX =
            "__autojs6_r2_external_natural_phase_timeout_";
    private static final String NATURAL_PHASE_KEY_ENABLED =
            NATURAL_PHASE_TIMEOUT_PREFIX + "enabled";
    private static final String NATURAL_PHASE_KEY_RECEIPT_AVAILABLE =
            NATURAL_PHASE_TIMEOUT_PREFIX + "receipt_available";
    private static final String NATURAL_PHASE_KEY_REQUEST_ID =
            NATURAL_PHASE_TIMEOUT_PREFIX + "request_id";
    private static final String NATURAL_PHASE_KEY_HOST_REQUEST_ID =
            NATURAL_PHASE_TIMEOUT_PREFIX + "host_request_id";
    private static final String NATURAL_PHASE_KEY_OUTCOME =
            NATURAL_PHASE_TIMEOUT_PREFIX + "outcome";
    private static final String NATURAL_PHASE_KEY_CREATED_BEFORE_CALLBACK =
            NATURAL_PHASE_TIMEOUT_PREFIX + "created_before_callback";
    private static final String NATURAL_PHASE_KEY_PROJECT_START_NANOS =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "project_start_nanos_since_request_start";
    private static final String NATURAL_PHASE_KEY_PHASE_VALUES =
            NATURAL_PHASE_TIMEOUT_PREFIX + "phase_values";
    private static final String NATURAL_PHASE_KEY_SEQUENCE_TWO_NANOS =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "phase_sequence_two_nanos_since_request_start";
    private static final String NATURAL_PHASE_KEY_COMPLETED_NANOS =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "phase_completed_nanos_since_request_start";
    private static final String NATURAL_PHASE_KEY_TERMINATION_AVAILABLE =
            NATURAL_PHASE_TIMEOUT_PREFIX + "termination_available";
    private static final String NATURAL_PHASE_KEY_STOP_CAUSE =
            NATURAL_PHASE_TIMEOUT_PREFIX + "termination_stop_cause";
    private static final String NATURAL_PHASE_KEY_HOST_STOP_PHASE =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "termination_phase_at_host_stop_selection";
    private static final String NATURAL_PHASE_KEY_ENTERED_PHASES =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "termination_entered_phases_at_host_stop_selection";
    private static final String NATURAL_PHASE_KEY_COMPLETED_PHASES =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "termination_completed_phases_at_host_stop_selection";
    private static final String NATURAL_PHASE_KEY_PAGE_TERMINATION_PHASE =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "termination_phase_at_page_termination";
    private static final String NATURAL_PHASE_KEY_DEADLINE_NANOS =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "termination_deadline_nanos_since_request_start";
    private static final String NATURAL_PHASE_KEY_STOP_SELECTED_NANOS =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "termination_stop_selected_nanos_since_request_start";
    private static final String NATURAL_PHASE_KEY_DEADLINE_OVERSHOOT_NANOS =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "termination_deadline_overshoot_nanos";
    private static final String NATURAL_PHASE_KEY_SESSION_GENERATION =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "termination_session_generation";
    private static final String NATURAL_PHASE_KEY_WORKER_GENERATION =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "termination_worker_generation";
    private static final String NATURAL_PHASE_KEY_PROGRESS_SEQUENCE =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "termination_progress_sequence";
    private static final String NATURAL_PHASE_KEY_PROGRESS_COMPLETED =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "termination_progress_completed_units";
    private static final String NATURAL_PHASE_KEY_PROGRESS_TOTAL =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "termination_progress_total_units";
    private static final String NATURAL_PHASE_KEY_TERMINATED_WORKER =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "termination_terminated_worker_generation";
    private static final String NATURAL_PHASE_KEY_REPLACEMENT_WORKER =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "termination_replacement_worker_generation";
    private static final String NATURAL_PHASE_KEY_INPUT_SENT_FRAMES =
            NATURAL_PHASE_TIMEOUT_PREFIX + "input_sent_frame_count";
    private static final String NATURAL_PHASE_KEY_INPUT_MAXIMUM_FRAME_BYTES =
            NATURAL_PHASE_TIMEOUT_PREFIX
                    + "input_maximum_encoded_frame_bytes";
    private static final String NATURAL_PHASE_KEY_INPUT_ACCEPTED_BYTES =
            NATURAL_PHASE_TIMEOUT_PREFIX + "input_accepted_payload_bytes";
    private static final String NATURAL_PHASE_KEY_INPUT_TOTAL_BYTES =
            NATURAL_PHASE_TIMEOUT_PREFIX + "input_total_payload_bytes";
    private static final String NATURAL_PHASE_KEY_INPUT_FILE_INDEX =
            NATURAL_PHASE_TIMEOUT_PREFIX + "input_file_index";
    private static final String NATURAL_PHASE_KEY_INPUT_FILE_OFFSET =
            NATURAL_PHASE_TIMEOUT_PREFIX + "input_file_offset";
    private static final String NATURAL_PHASE_KEY_INPUT_STAGE =
            NATURAL_PHASE_TIMEOUT_PREFIX + "input_stage";
    private static final String NATURAL_PHASE_KEY_INPUT_COMPLETE =
            NATURAL_PHASE_TIMEOUT_PREFIX + "input_complete";
    private static final String NATURAL_PHASE_KEY_INPUT_OPENED_CURSORS =
            NATURAL_PHASE_TIMEOUT_PREFIX + "input_opened_cursor_count";
    private static final String NATURAL_PHASE_KEY_INPUT_CLOSED_CURSORS =
            NATURAL_PHASE_TIMEOUT_PREFIX + "input_closed_cursor_count";
    private static final String NATURAL_PHASE_KEY_INPUT_ACTIVE_CURSOR =
            NATURAL_PHASE_TIMEOUT_PREFIX + "input_active_cursor";
    private static final String NATURAL_PHASE_KEY_INPUT_RELEASED =
            NATURAL_PHASE_TIMEOUT_PREFIX + "input_released";
    private static final String ACTION_COMPILER =
            "org.autojs.plugin.typescript.COMPILER";
    private static final String CATEGORY_TYPESCRIPT = "typescript";
    private static final String CURRENT_PACKAGE =
            "io.github.supermonster003.autojs6.plugin.typescript";
    private static final String FIXTURE_PACKAGE =
            "io.github.supermonster003.autojs6.plugin.typescript.publicpreview";
    private static final String PLUGIN_PERMISSION =
            "org.autojs.permission.PLUGIN";
    private static final String INVALID_PROJECT_ID =
            "r2-public-preview-project";
    private static final String INVALID_ENTRY_SOURCE_URI =
            "autojs://source/r2-public-preview/main.ts";

    private static final byte[] INVALID_ZIP_BYTES =
            "public-r2-preview-intentionally-not-a-zip"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] OUTPUT_SENTINEL =
            "public-r2-preview-output-sentinel"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] ABSENT_MANIFEST_BYTES =
            "public-r2-preview-absent-manifest"
                    .getBytes(StandardCharsets.UTF_8);

    private static final long ADMISSION_WAIT_MS = 30_000L;
    private static final long BIND_WAIT_MS = 5_000L;
    private static final long CALLBACK_WAIT_MS = 135_000L;
    private static final long CALLBACK_QUIET_MS = 1_000L;
    private static final long RETRY_DELAY_MS = 250L;
    private static final long PRE_ACK_TELEMETRY_WAIT_MS = 80L;
    private static final long PRE_ACK_TELEMETRY_SAMPLE_DELAY_MS = 10L;
    private static final long CLEANUP_SAMPLE_DELAY_MS = 50L;
    private static final int REQUIRED_CONSECUTIVE_CLEANUP_SAMPLES = 5;
    private static final int NATURAL_EMIT_PRECONDITION_RUNS = 2;
    private static final int NATURAL_EMIT_BASELINE_RUNS = 3;
    private static final int NATURAL_EMIT_FAULT_ATTEMPTS = 1;
    private static final long NATURAL_EMIT_MINIMUM_MARGIN_MS = 100L;
    private static final long NATURAL_EMIT_MAXIMUM_OVERSHOOT_MS = 5_000L;
    private static final int NATURAL_SEMANTIC_PRECONDITION_RUNS = 2;
    private static final int NATURAL_SEMANTIC_BASELINE_RUNS = 3;
    private static final int NATURAL_SEMANTIC_FAULT_ATTEMPTS = 1;
    private static final long NATURAL_SEMANTIC_MINIMUM_MARGIN_MS = 100L;
    private static final long NATURAL_SEMANTIC_MAXIMUM_OVERSHOOT_MS = 5_000L;
    private static final int NATURAL_PARSE_PRECONDITION_RUNS = 2;
    private static final int NATURAL_PARSE_BASELINE_RUNS = 3;
    private static final int NATURAL_PARSE_FAULT_ATTEMPTS = 1;
    private static final long NATURAL_PARSE_MINIMUM_MARGIN_MS = 100L;
    private static final long NATURAL_PARSE_MAXIMUM_OVERSHOOT_MS = 5_000L;
    private static final long REQUEST_TIMEOUT_MS =
            TypeScriptCompilerContract.MAX_TIMEOUT_MS;
    private static final int MAXIMUM_OUTPUT_MANIFEST_BYTES = 1024 * 1024;
    private static final String OUTPUT_MANIFEST_SCHEMA =
            TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA;
    private static final String DIAGNOSTICS_SCHEMA =
            TypeScriptR2Contract.DIAGNOSTICS_SCHEMA;
    private static final int OUTPUT_REVISION =
            TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION;
    private static final int DIAGNOSTICS_REVISION =
            TypeScriptR2Contract.DIAGNOSTICS_SCHEMA_REVISION;
    private static final String COMPILER_API = "createIncrementalProgram";
    private static final String EFFECTIVE_OPTIONS_JSON =
            "{\"allowJs\":false,\"checkJs\":false,\"ignoreDeprecations\":\"6.0\","
                    + "\"incremental\":false,\"inlineSourceMap\":false,"
                    + "\"inlineSources\":false,\"module\":1,\"moduleResolution\":2,"
                    + "\"newLine\":1,\"noEmitOnError\":true,\"noLib\":false,"
                    + "\"outDir\":\"/outputs\",\"rootDir\":\"/sources\","
                    + "\"skipLibCheck\":false,\"sourceMap\":true,\"strict\":true,"
                    + "\"target\":5,\"types\":[]}";
    private boolean externalCapacityEvidenceRequested;
    private boolean externalPlusOneCleanupEvidenceRequested;
    private boolean externalNaturalEmitTimeoutEvidenceRequested;
    private boolean externalNaturalSemanticTimeoutEvidenceRequested;
    private boolean externalNaturalParseTimeoutEvidenceRequested;
    private boolean shippingReleaseNaturalParseTimeoutEvidenceRequested;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        externalCapacityEvidenceRequested =
                arguments != null
                        && Boolean.parseBoolean(
                                arguments.getString(
                                        ARG_EXTERNAL_CAPACITY_EVIDENCE,
                                        "false"));
        externalPlusOneCleanupEvidenceRequested =
                arguments != null
                        && Boolean.parseBoolean(
                                arguments.getString(
                                        ARG_EXTERNAL_PLUS_ONE_CLEANUP_EVIDENCE,
                                        "false"));
        externalNaturalEmitTimeoutEvidenceRequested =
                arguments != null
                        && Boolean.parseBoolean(
                                arguments.getString(
                                        ARG_EXTERNAL_NATURAL_EMIT_TIMEOUT_EVIDENCE,
                                        "false"));
        externalNaturalSemanticTimeoutEvidenceRequested =
                arguments != null
                        && Boolean.parseBoolean(
                                arguments.getString(
                                        ARG_EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_EVIDENCE,
                                        "false"));
        externalNaturalParseTimeoutEvidenceRequested =
                arguments != null
                        && Boolean.parseBoolean(
                                arguments.getString(
                                        ARG_EXTERNAL_NATURAL_PARSE_TIMEOUT_EVIDENCE,
                                        "false"));
        shippingReleaseNaturalParseTimeoutEvidenceRequested =
                arguments != null
                        && Boolean.parseBoolean(
                                arguments.getString(
                                        ARG_SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_EVIDENCE,
                                        "false"));
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            if (shippingReleaseNaturalParseTimeoutEvidenceRequested) {
                require(
                        !externalCapacityEvidenceRequested
                                && !externalPlusOneCleanupEvidenceRequested
                                && !externalNaturalEmitTimeoutEvidenceRequested
                                && !externalNaturalSemanticTimeoutEvidenceRequested
                                && !externalNaturalParseTimeoutEvidenceRequested,
                        "Shipping Release natural parse timeout must run in its exclusive "
                                + "ordinary public-preview mode.");
                runShippingReleaseNaturalParseTimeoutEvidence();
            } else if (externalNaturalParseTimeoutEvidenceRequested) {
                require(
                        !externalCapacityEvidenceRequested
                                && !externalPlusOneCleanupEvidenceRequested
                                && !externalNaturalEmitTimeoutEvidenceRequested
                                && !externalNaturalSemanticTimeoutEvidenceRequested,
                        "External natural parse timeout evidence must run in its exclusive "
                                + "ordinary public-preview mode.");
                runExternalNaturalParseTimeoutEvidence();
            } else if (externalNaturalSemanticTimeoutEvidenceRequested) {
                require(
                        !externalCapacityEvidenceRequested
                                && !externalPlusOneCleanupEvidenceRequested
                                && !externalNaturalEmitTimeoutEvidenceRequested
                                && !externalNaturalParseTimeoutEvidenceRequested,
                        "External natural semantic timeout evidence must run in its exclusive "
                                + "ordinary public-preview mode.");
                runExternalNaturalSemanticTimeoutEvidence();
            } else if (externalNaturalEmitTimeoutEvidenceRequested) {
                require(
                        !externalCapacityEvidenceRequested
                                && !externalPlusOneCleanupEvidenceRequested
                                && !externalNaturalSemanticTimeoutEvidenceRequested
                                && !externalNaturalParseTimeoutEvidenceRequested,
                        "External natural emit timeout evidence must run in its exclusive "
                                + "ordinary public-preview mode.");
                runExternalNaturalEmitTimeoutEvidence();
            } else if (externalPlusOneCleanupEvidenceRequested) {
                runExternalPlusOneCleanupEvidence(
                        externalCapacityEvidenceRequested);
            } else {
                runPublicPreview(externalCapacityEvidenceRequested);
            }
            result.putString(
                    "stream",
                    shippingReleaseNaturalParseTimeoutEvidenceRequested
                            ? "Normal/shipping Release external-UID Binder natural parse "
                                    + "timeout evidence passed: bounded public phase/work "
                                    + "progress, two successful preconditions, three strict "
                                    + "request-start baselines, one parse fault, empty output, "
                                    + "and same-Binder successor recovery.\n"
                            : externalNaturalParseTimeoutEvidenceRequested
                            ? "External-UID Binder natural parse timeout evidence passed: "
                                    + "two successful preconditions, three successful "
                                    + "request-start baselines, one fixed-deadline parse "
                                    + "fault, pre-callback phase and cleanup receipts, five "
                                    + "all-zero samples, and same-Binder/process successor "
                                    + "recovery.\n"
                            : externalNaturalSemanticTimeoutEvidenceRequested
                            ? "External-UID Binder natural semantic timeout evidence passed: "
                                    + "two successful preconditions, three successful "
                                    + "request-start baselines, one fixed-deadline semantic "
                                    + "fault, pre-callback phase and cleanup receipts, five "
                                    + "all-zero samples, and same-Binder/process successor "
                                    + "recovery.\n"
                            : externalNaturalEmitTimeoutEvidenceRequested
                            ? "External-UID Binder natural emit timeout evidence passed: "
                                    + "two successful preconditions, three successful "
                                    + "request-start baselines, one fixed-deadline emit fault, "
                                    + "pre-callback phase and cleanup receipts, five all-zero "
                                    + "samples, and same-Binder/process successor recovery.\n"
                            : externalPlusOneCleanupEvidenceRequested
                            ? "External-UID external plus-one cleanup evidence passed: "
                                    + "pre-ACK server FD ownership, callback-before-receipt "
                                    + "correlation, five consecutive all-zero cleanup samples, "
                                    + "stable Binder/process epoch, and ordinary inclusive "
                                    + "successor recovery"
                                    + (externalCapacityEvidenceRequested
                                            ? " for production-fence, STORED-container, and "
                                                    + "DEFLATED-expanded plus-one rejection"
                                            : " for production-fence plus-one rejection")
                                    + ".\n"
                            : "External-UID public R2 preview passed: same signer, accepted ticket, "
                                    + "wrong-token ACK rejection, post-return public ACK, invalid-ZIP "
                                    + "empty-output failure, fence-inclusive emitted output, and "
                                    + "fence-plus-one asynchronous quota failure"
                                    + (externalCapacityEvidenceRequested
                                            ? ", four exact-64-MiB capacity boundaries, and a "
                                                    + "post-capacity production-fence successor"
                                            : "")
                                    + ".\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            result.putString("shortMsg", error.toString());
            result.putString("stream", trace.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    @SuppressWarnings("deprecation")
    private void runPublicPreview(boolean runExternalCapacityEvidence)
            throws Exception {
        Context context = getTargetContext();
        require(
                FIXTURE_PACKAGE.equals(context.getPackageName()),
                "Instrumentation did not target the standalone fixture package.");

        PackageManager packageManager = context.getPackageManager();
        ApplicationInfo currentApplication =
                packageManager.getApplicationInfo(CURRENT_PACKAGE, 0);
        require(
                Process.myUid() != currentApplication.uid,
                "Fixture unexpectedly shares the compiler plugin UID.");
        Log.i(
                LOG_TAG,
                "event=identity fixtureUid="
                        + Process.myUid()
                        + " pluginUid="
                        + currentApplication.uid);
        require(
                packageManager.checkSignatures(FIXTURE_PACKAGE, CURRENT_PACKAGE)
                        == PackageManager.SIGNATURE_MATCH,
                "Fixture and compiler plugin do not have the same signer.");
        PermissionInfo pluginPermission =
                packageManager.getPermissionInfo(PLUGIN_PERMISSION, 0);
        require(
                (pluginPermission.protectionLevel
                                & PermissionInfo.PROTECTION_MASK_BASE)
                        == PermissionInfo.PROTECTION_SIGNATURE,
                "Compiler service permission is not signature-protected.");
        require(
                packageManager.checkSignatures(
                                pluginPermission.packageName, CURRENT_PACKAGE)
                        == PackageManager.SIGNATURE_MATCH,
                "Compiler service permission owner does not share the plugin signer.");

        Intent discovery =
                new Intent(ACTION_COMPILER)
                        .addCategory(CATEGORY_TYPESCRIPT)
                        .setPackage(CURRENT_PACKAGE);
        List<ResolveInfo> matches =
                packageManager.queryIntentServices(discovery, 0);
        require(matches.size() == 1, "Expected exactly one current compiler service.");
        ServiceInfo serviceInfo = matches.get(0).serviceInfo;
        require(serviceInfo.exported, "Current compiler service is not exported.");
        require(
                PLUGIN_PERMISSION.equals(serviceInfo.permission),
                "Current compiler service permission drifted.");
        require(
                serviceInfo.applicationInfo.uid == currentApplication.uid,
                "Resolved compiler service belongs to an unexpected UID.");
        ComponentName component =
                new ComponentName(serviceInfo.packageName, serviceInfo.name);

        CompilerMetadata metadata =
                runInvalidArchiveCase(context, discovery, component);
        Context pluginContext =
                context.createPackageContext(CURRENT_PACKAGE, 0);
        R2FenceBoundedProjectFixture.CompilerAuthority authority =
                metadata.toFixtureAuthority();
        runFenceInclusiveCase(
                context,
                pluginContext,
                discovery,
                component,
                authority);
        runFencePlusOneCase(
                context,
                pluginContext,
                discovery,
                component,
                authority);
        if (runExternalCapacityEvidence) {
            runExternalCapacityBoundaryCase(
                    context,
                    discovery,
                    component,
                    authority,
                    R2ExternalCapacityProjectFixture.Boundary
                            .STORED_CONTAINER_INCLUSIVE);
            runExternalCapacityBoundaryCase(
                    context,
                    discovery,
                    component,
                    authority,
                    R2ExternalCapacityProjectFixture.Boundary
                            .STORED_CONTAINER_PLUS_ONE);
            runExternalCapacityBoundaryCase(
                    context,
                    discovery,
                    component,
                    authority,
                    R2ExternalCapacityProjectFixture.Boundary
                            .DEFLATED_UNCOMPRESSED_INCLUSIVE);
            runExternalCapacityBoundaryCase(
                    context,
                    discovery,
                    component,
                    authority,
                    R2ExternalCapacityProjectFixture.Boundary
                            .DEFLATED_UNCOMPRESSED_PLUS_ONE);
            runFenceInclusiveCase(
                    context,
                    pluginContext,
                    discovery,
                    component,
                    authority);
        }
    }

    @SuppressWarnings("deprecation")
    private void runExternalNaturalEmitTimeoutEvidence()
            throws Exception {
        Context context = getTargetContext();
        require(
                FIXTURE_PACKAGE.equals(context.getPackageName()),
                "Instrumentation did not target the standalone fixture package.");

        PackageManager packageManager = context.getPackageManager();
        ApplicationInfo currentApplication =
                packageManager.getApplicationInfo(CURRENT_PACKAGE, 0);
        require(
                Process.myUid() != currentApplication.uid,
                "Fixture unexpectedly shares the compiler plugin UID.");
        require(
                packageManager.checkSignatures(FIXTURE_PACKAGE, CURRENT_PACKAGE)
                        == PackageManager.SIGNATURE_MATCH,
                "Fixture and compiler plugin do not have the same signer.");
        require(
                (context.getApplicationInfo().flags
                                & ApplicationInfo.FLAG_TEST_ONLY)
                        != 0,
                "External natural emit fixture is not TEST_ONLY.");
        require(
                (currentApplication.flags & ApplicationInfo.FLAG_TEST_ONLY)
                        != 0,
                "External natural emit compiler evidence build is not TEST_ONLY.");
        PermissionInfo pluginPermission =
                packageManager.getPermissionInfo(PLUGIN_PERMISSION, 0);
        require(
                (pluginPermission.protectionLevel
                                & PermissionInfo.PROTECTION_MASK_BASE)
                        == PermissionInfo.PROTECTION_SIGNATURE,
                "Compiler service permission is not signature-protected.");
        require(
                packageManager.checkSignatures(
                                pluginPermission.packageName, CURRENT_PACKAGE)
                        == PackageManager.SIGNATURE_MATCH,
                "Compiler service permission owner does not share the plugin signer.");

        Intent discovery =
                new Intent(ACTION_COMPILER)
                        .addCategory(CATEGORY_TYPESCRIPT)
                        .setPackage(CURRENT_PACKAGE);
        List<ResolveInfo> matches =
                packageManager.queryIntentServices(discovery, 0);
        require(matches.size() == 1, "Expected exactly one current compiler service.");
        ServiceInfo serviceInfo = matches.get(0).serviceInfo;
        require(serviceInfo.exported, "Current compiler service is not exported.");
        require(
                PLUGIN_PERMISSION.equals(serviceInfo.permission),
                "Current compiler service permission drifted.");
        ComponentName component =
                new ComponentName(serviceInfo.packageName, serviceInfo.name);
        Context pluginContext =
                context.createPackageContext(CURRENT_PACKAGE, 0);

        try (CleanupEvidenceSession session =
                awaitCleanupEvidenceSession(
                        context,
                        discovery,
                        component,
                        true)) {
            runExternalNaturalEmitTimeoutMatrix(
                    context,
                    pluginContext,
                    session);
            requireLiveSameBinder(session);
        }
    }

    private static void runExternalNaturalEmitTimeoutMatrix(
            Context context,
            Context pluginContext,
            CleanupEvidenceSession session)
            throws Exception {
        require(pluginContext != null, "Natural emit plugin context is null.");
        require(
                NATURAL_EMIT_PRECONDITION_RUNS == 2
                        && NATURAL_EMIT_BASELINE_RUNS == 3
                        && NATURAL_EMIT_FAULT_ATTEMPTS == 1,
                "Natural emit evidence run authority drifted.");
        List<NaturalEmitTiming> baselineTimings = new ArrayList<>();
        for (int run = 1; run <= NATURAL_EMIT_PRECONDITION_RUNS; run++) {
            runExternalNaturalEmitSuccessfulRun(
                    context,
                    session,
                    false,
                    "external-natural-emit-precondition-" + run,
                    "EXTERNAL_NATURAL_EMIT_TIMEOUT_PRECONDITION_PASS",
                    run);
        }
        for (int run = 1; run <= NATURAL_EMIT_BASELINE_RUNS; run++) {
            baselineTimings.add(
                    runExternalNaturalEmitSuccessfulRun(
                            context,
                            session,
                            false,
                            "external-natural-emit-baseline-" + run,
                            "EXTERNAL_NATURAL_EMIT_TIMEOUT_BASELINE_PASS",
                            run));
        }

        NaturalEmitCalibration calibration =
                calibrateExternalNaturalEmitDeadline(baselineTimings);
        runExternalNaturalEmitFault(
                context,
                session,
                calibration);
        runExternalNaturalEmitSuccessfulRun(
                context,
                session,
                true,
                "external-natural-emit-successor",
                "EXTERNAL_NATURAL_EMIT_TIMEOUT_SUCCESSOR_PASS",
                1);
        requireLiveSameBinder(session);
        Log.i(
                LOG_TAG,
                "EXTERNAL_NATURAL_EMIT_TIMEOUT_MATRIX_PASS"
                        + " preconditionRuns="
                        + NATURAL_EMIT_PRECONDITION_RUNS
                        + " baselineRuns="
                        + NATURAL_EMIT_BASELINE_RUNS
                        + " faultAttempts="
                        + NATURAL_EMIT_FAULT_ATTEMPTS
                        + " timeoutMs="
                        + calibration.timeoutMillis
                        + " roots="
                        + R2ExternalNaturalEmitTimeoutProjectFixture.ROOT_COUNT
                        + " files="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .NATURAL_TOTAL_FILE_COUNT
                        + " bytes="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .NATURAL_TOTAL_BYTES
                        + " tokens="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .NATURAL_TOTAL_TOKENS
                        + " ordinaryAdmission=true expandedWorkerCapacity=false"
                        + " requestStartAbsoluteDeadline=true sameBinder=true"
                        + " sameProcessEpoch=true zeroSamples="
                        + REQUIRED_CONSECUTIVE_CLEANUP_SAMPLES);
    }

    private static NaturalEmitTiming runExternalNaturalEmitSuccessfulRun(
            Context context,
            CleanupEvidenceSession session,
            boolean successor,
            String requestLabel,
            String event,
            int run)
            throws Exception {
        String requestId = newRequestId(requestLabel);
        R2FenceBoundedProjectFixture.CompilerAuthority authority =
                session.metadata.toFixtureAuthority();
        try (R2ExternalNaturalEmitTimeoutProjectFixture fixture =
                successor
                        ? R2ExternalNaturalEmitTimeoutProjectFixture
                                .createSuccessor(
                                        context, requestId, authority)
                        : R2ExternalNaturalEmitTimeoutProjectFixture.create(
                                context, requestId, authority);
                ParcelFileDescriptor input =
                        ParcelFileDescriptor.open(
                                fixture.input,
                                ParcelFileDescriptor.MODE_READ_ONLY);
                ParcelFileDescriptor output =
                        ParcelFileDescriptor.open(
                                fixture.output,
                                ParcelFileDescriptor.MODE_READ_WRITE)) {
            RecordingCallback callback = new RecordingCallback();
            Admission admission =
                    admitNaturalEmitOnce(
                            session,
                            input,
                            output,
                            callback,
                            requestId,
                            fixture.projectId,
                            fixture.entrySourceUri,
                            fixture.inputArchiveSha256,
                            fixture.requestManifestSha256,
                            TypeScriptCompilerContract.MAX_TIMEOUT_MS);
            CleanupCheckpoint checkpoint =
                    assertPreAcknowledgementCleanupTelemetry(
                            session,
                            admission,
                            input,
                            output);
            acknowledgeAdmission(admission);
            Bundle terminal = callback.awaitTerminal(CALLBACK_WAIT_MS);
            SystemClock.sleep(CALLBACK_QUIET_MS);
            callback.assertExactlyOneCompletion();
            assertTerminalRequestId(terminal, requestId);
            require(
                    terminal.getBoolean(
                            TypeScriptCompilerContract.KEY_SUCCEEDED, false),
                    "Natural emit successful run failed: outcome="
                            + terminal.getString(
                                    TypeScriptCompilerContract.KEY_OUTCOME)
                            + " errorCode="
                            + terminal.getString(
                                    TypeScriptCompilerContract.KEY_ERROR_CODE)
                            + " errorMessage="
                            + terminal.getString(
                                    TypeScriptCompilerContract.KEY_ERROR_MESSAGE)
                            + " elapsedMs="
                            + terminal.getLong(
                                    TypeScriptCompilerContract.KEY_ELAPSED_MS,
                                    -1L)
                            + ".");
            require(
                    TypeScriptCompilerContract.OUTCOME_EMITTED.equals(
                            terminal.getString(
                                    TypeScriptCompilerContract.KEY_OUTCOME)),
                    "Natural emit successful run did not emit.");
            require(
                    terminal.getInt(
                                    TypeScriptCompilerContract
                                            .KEY_DIAGNOSTIC_COUNT,
                                    -1)
                            == 0,
                    "Natural emit successful run returned diagnostics.");
            require(
                    terminal.getInt(
                                    TypeScriptCompilerContract
                                            .KEY_EMITTED_MODULE_COUNT,
                                    -1)
                            == fixture.rootPaths.size(),
                    "Natural emit successful run module count drifted.");
            Os.fsync(output.getFileDescriptor());
            verifyExternalNaturalEmitOutput(
                    fixture,
                    authority,
                    terminal);

            if (successor) {
                CleanupReceipt cleanupReceipt =
                        awaitCleanupEvidence(
                                session,
                                checkpoint,
                                TypeScriptCompilerContract.OUTCOME_EMITTED,
                                null,
                                false);
                require(
                        cleanupReceipt != null
                                && cleanupReceipt.cursorOpenedCount
                                        == R2ExternalNaturalEmitTimeoutProjectFixture
                                                .SUCCESSOR_TOTAL_FILE_COUNT
                                && cleanupReceipt.cursorClosedCount
                                        == cleanupReceipt.cursorOpenedCount,
                        "Natural emit successor cleanup cursor authority drifted.");
                Log.i(
                        LOG_TAG,
                        event
                                + " run="
                                + run
                                + " requestId="
                                + requestId
                                + " files="
                                + cleanupReceipt.cursorOpenedCount
                                + " outputBytes="
                                + fixture.expectedOutputAuthority.totalBytes
                                + " outputSha256="
                                + fixture.expectedOutputAuthority.aggregateSha256
                                + " phaseReceiptRequired=false sameBinder=true"
                                + " sameProcessEpoch=true");
                return null;
            }

            NaturalPhaseReceipt phaseReceipt =
                    requireNaturalPhaseReceipt(
                            session, requestId, "success");
            assertSuccessfulNaturalPhaseReceipt(
                    phaseReceipt, fixture);
            CleanupReceipt cleanupReceipt =
                    awaitCleanupEvidence(
                            session,
                            checkpoint,
                            TypeScriptCompilerContract.OUTCOME_EMITTED,
                            null,
                            false);
            require(
                    cleanupReceipt != null
                            && cleanupReceipt.cursorOpenedCount
                                    == R2ExternalNaturalEmitTimeoutProjectFixture
                                            .NATURAL_TOTAL_FILE_COUNT
                            && cleanupReceipt.cursorClosedCount
                                    == cleanupReceipt.cursorOpenedCount,
                    "Natural emit successful cleanup cursor authority drifted.");
            NaturalEmitTiming timing = phaseReceipt.emitTiming();
            Log.i(
                    LOG_TAG,
                    event
                            + " run="
                            + run
                            + " requestId="
                            + requestId
                            + " hostRequestId="
                            + phaseReceipt.hostRequestId
                            + " projectStartMs="
                            + TimeUnit.NANOSECONDS.toMillis(
                                    phaseReceipt
                                            .projectStartNanosSinceRequestStart)
                            + " emitSequenceTwoMs="
                            + TimeUnit.NANOSECONDS.toMillis(
                                    timing.sequenceTwoNanosSinceRequestStart)
                            + " emitCompletedMs="
                            + TimeUnit.NANOSECONDS.toMillis(
                                    timing.completedNanosSinceRequestStart)
                            + " files="
                            + phaseReceipt.input.openedCursorCount
                            + " frames="
                            + phaseReceipt.input.sentFrameCount
                            + " outputBytes="
                            + fixture.expectedOutputAuthority.totalBytes
                            + " outputSha256="
                            + fixture.expectedOutputAuthority.aggregateSha256);
            return timing;
        }
    }

    private static NaturalEmitCalibration
            calibrateExternalNaturalEmitDeadline(
                    List<NaturalEmitTiming> baselineTimings) {
        require(
                baselineTimings.size() == NATURAL_EMIT_BASELINE_RUNS,
                "Natural emit calibration did not contain three baselines.");
        long minimumTimeoutMillis = 0L;
        long maximumTimeoutMillis = Long.MAX_VALUE;
        for (NaturalEmitTiming timing : baselineTimings) {
            long sampleMinimum =
                    TimeUnit.NANOSECONDS.toMillis(
                                    timing.sequenceTwoNanosSinceRequestStart)
                            + 1L;
            long sampleMaximum =
                    (timing.completedNanosSinceRequestStart - 1L)
                            / TimeUnit.MILLISECONDS.toNanos(1L);
            if (sampleMinimum > minimumTimeoutMillis) {
                minimumTimeoutMillis = sampleMinimum;
            }
            if (sampleMaximum < maximumTimeoutMillis) {
                maximumTimeoutMillis = sampleMaximum;
            }
        }
        require(
                maximumTimeoutMillis - minimumTimeoutMillis
                        >= NATURAL_EMIT_MINIMUM_MARGIN_MS * 2L,
                "Natural emit baselines have no strict two-sided deadline window: "
                        + minimumTimeoutMillis
                        + ".."
                        + maximumTimeoutMillis
                        + " ms.");
        long timeoutMillis =
                minimumTimeoutMillis
                        + (maximumTimeoutMillis - minimumTimeoutMillis) / 2L;
        require(
                timeoutMillis
                        >= TypeScriptR2Contract
                                .MIN_PUBLIC_COMPILATION_TIMEOUT_MS,
                "Natural emit strict midpoint is below the public timeout minimum; "
                        + "faultAttempts=0.");
        require(
                timeoutMillis - minimumTimeoutMillis
                                >= NATURAL_EMIT_MINIMUM_MARGIN_MS
                        && maximumTimeoutMillis - timeoutMillis
                                >= NATURAL_EMIT_MINIMUM_MARGIN_MS,
                "Natural emit strict midpoint lost a two-sided margin.");
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        for (NaturalEmitTiming timing : baselineTimings) {
            require(
                    timing.sequenceTwoNanosSinceRequestStart
                                    < timeoutNanos
                            && timeoutNanos
                                    < timing.completedNanosSinceRequestStart,
                    "Natural emit midpoint is outside a baseline interval.");
        }
        Log.i(
                LOG_TAG,
                "EXTERNAL_NATURAL_EMIT_TIMEOUT_WINDOW_PASS"
                        + " lowerMs="
                        + minimumTimeoutMillis
                        + " upperMs="
                        + maximumTimeoutMillis
                        + " timeoutMs="
                        + timeoutMillis
                        + " leftMarginMs="
                        + (timeoutMillis - minimumTimeoutMillis)
                        + " rightMarginMs="
                        + (maximumTimeoutMillis - timeoutMillis)
                        + " publicMinimumMs="
                        + TypeScriptR2Contract
                                .MIN_PUBLIC_COMPILATION_TIMEOUT_MS
                        + " strictIntersection=true faultAttempts=0");
        return new NaturalEmitCalibration(
                minimumTimeoutMillis,
                maximumTimeoutMillis,
                timeoutMillis);
    }

    private static void runExternalNaturalEmitFault(
            Context context,
            CleanupEvidenceSession session,
            NaturalEmitCalibration calibration)
            throws Exception {
        require(
                NATURAL_EMIT_FAULT_ATTEMPTS == 1,
                "Natural emit fault must be attempted exactly once.");
        String requestId =
                newRequestId("external-natural-emit-timeout-fault");
        R2FenceBoundedProjectFixture.CompilerAuthority authority =
                session.metadata.toFixtureAuthority();
        try (R2ExternalNaturalEmitTimeoutProjectFixture fixture =
                R2ExternalNaturalEmitTimeoutProjectFixture.create(
                        context, requestId, authority);
                ParcelFileDescriptor input =
                        ParcelFileDescriptor.open(
                                fixture.input,
                                ParcelFileDescriptor.MODE_READ_ONLY);
                ParcelFileDescriptor output =
                        ParcelFileDescriptor.open(
                                fixture.output,
                                ParcelFileDescriptor.MODE_READ_WRITE)) {
            RecordingCallback callback = new RecordingCallback();
            long wallStartedAtNanos = System.nanoTime();
            Admission admission =
                    admitNaturalEmitOnce(
                            session,
                            input,
                            output,
                            callback,
                            requestId,
                            fixture.projectId,
                            fixture.entrySourceUri,
                            fixture.inputArchiveSha256,
                            fixture.requestManifestSha256,
                            calibration.timeoutMillis);
            CleanupCheckpoint checkpoint =
                    assertPreAcknowledgementCleanupTelemetry(
                            session,
                            admission,
                            input,
                            output);
            acknowledgeAdmission(admission);
            Bundle terminal = callback.awaitTerminal(CALLBACK_WAIT_MS);
            long wallElapsedMillis =
                    TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - wallStartedAtNanos);
            SystemClock.sleep(CALLBACK_QUIET_MS);
            callback.assertExactlyOneCompletion();
            assertTerminalRequestId(terminal, requestId);
            require(
                    !terminal.getBoolean(
                            TypeScriptCompilerContract.KEY_SUCCEEDED, true),
                    "Natural emit fault unexpectedly succeeded.");
            require(
                    TypeScriptCompilerContract.OUTCOME_TIMED_OUT.equals(
                            terminal.getString(
                                    TypeScriptCompilerContract.KEY_OUTCOME)),
                    "Natural emit fault did not time out.");
            require(
                    TypeScriptCompilerContract.ERROR_TIMEOUT.equals(
                            terminal.getString(
                                    TypeScriptCompilerContract
                                            .KEY_ERROR_CODE)),
                    "Natural emit fault returned the wrong error.");
            require(
                    wallElapsedMillis >= calibration.timeoutMillis,
                    "Natural emit fault preceded its request-start deadline.");
            assertNoCompilationPayload(
                    terminal, "External natural emit timeout");
            assertSyncedEmptyOutput(
                    output,
                    fixture.output,
                    "External natural emit timeout");

            NaturalPhaseReceipt phaseReceipt =
                    requireNaturalPhaseReceipt(
                            session, requestId, "timed_out");
            assertTimedOutNaturalPhaseReceipt(
                    phaseReceipt,
                    fixture,
                    calibration);
            CleanupReceipt cleanupReceipt =
                    awaitCleanupEvidence(
                            session,
                            checkpoint,
                            TypeScriptCompilerContract.OUTCOME_TIMED_OUT,
                            TypeScriptCompilerContract.ERROR_TIMEOUT,
                            false);
            require(
                    cleanupReceipt != null
                            && cleanupReceipt.cursorOpenedCount
                                    == R2ExternalNaturalEmitTimeoutProjectFixture
                                            .NATURAL_TOTAL_FILE_COUNT
                            && cleanupReceipt.cursorClosedCount
                                    == cleanupReceipt.cursorOpenedCount,
                    "Natural emit fault cleanup cursor authority drifted.");
            NaturalPhaseTermination termination =
                    phaseReceipt.termination;
            Log.i(
                    LOG_TAG,
                    "EXTERNAL_NATURAL_EMIT_TIMEOUT_FAULT_PASS"
                            + " faultAttempts=1 requestId="
                            + requestId
                            + " hostRequestId="
                            + phaseReceipt.hostRequestId
                            + " timeoutMs="
                            + calibration.timeoutMillis
                            + " wallElapsedMs="
                            + wallElapsedMillis
                            + " phase=emit progressSequence="
                            + termination.progressSequence
                            + " completedUnits="
                            + termination.progressCompletedUnits
                            + " totalUnits="
                            + termination.progressTotalUnits
                            + " deadlineOvershootMs="
                            + TimeUnit.NANOSECONDS.toMillis(
                                    termination.deadlineOvershootNanos)
                            + " openedCursors="
                            + phaseReceipt.input.openedCursorCount
                            + " closedCursors="
                            + phaseReceipt.input.closedCursorCount
                            + " terminatedWorkerGeneration="
                            + termination.terminatedWorkerGeneration
                            + " replacementWorkerGeneration="
                            + termination.replacementWorkerGeneration
                            + " outputBytes="
                            + fixture.output.length()
                            + " callbackBeforeReceiptRead=true"
                            + " receiptCreatedBeforeCallback=true");
        }
    }

    private static NaturalPhaseReceipt requireNaturalPhaseReceipt(
            CleanupEvidenceSession session,
            String requestId,
            String expectedOutcome)
            throws Exception {
        requireLiveSameBinder(session);
        ExternalNaturalPhaseTimeoutTelemetry telemetry =
                ExternalNaturalPhaseTimeoutTelemetry.from(
                        session.bound.compiler.getCompilerInfo());
        NaturalPhaseReceipt receipt = telemetry.receipt;
        require(receipt != null, "Natural phase receipt was unavailable after callback.");
        require(
                requestId.equals(receipt.requestId),
                "Natural phase receipt Binder request ID did not match.");
        require(
                receipt.hostRequestId != null
                        && !receipt.hostRequestId.isEmpty()
                        && !receipt.hostRequestId.equals(requestId),
                "Natural phase receipt did not bridge a private host request ID.");
        require(
                expectedOutcome.equals(receipt.outcome),
                "Natural phase receipt outcome did not match terminal.");
        return receipt;
    }

    private static void assertSuccessfulNaturalPhaseReceipt(
            NaturalPhaseReceipt receipt,
            R2ExternalNaturalEmitTimeoutProjectFixture fixture) {
        require(
                receipt.termination == null,
                "Successful natural phase receipt contained termination.");
        require(
                Arrays.equals(
                        receipt.phases,
                        new String[] {"parse", "semantic", "emit"}),
                "Successful natural phase receipt lost compiler phases.");
        assertNaturalPhaseInput(receipt.input, fixture);
    }

    private static void assertTimedOutNaturalPhaseReceipt(
            NaturalPhaseReceipt receipt,
            R2ExternalNaturalEmitTimeoutProjectFixture fixture,
            NaturalEmitCalibration calibration) {
        require(
                Arrays.equals(
                        receipt.phases,
                        new String[] {"parse", "semantic"}),
                "Timed-out natural phase receipt completed emit.");
        assertNaturalPhaseInput(receipt.input, fixture);
        NaturalPhaseTermination termination = receipt.termination;
        require(termination != null, "Timed-out natural phase receipt lost termination.");
        require(
                "TIMED_OUT".equals(termination.stopCause)
                        && "emit".equals(termination.hostStopPhase)
                        && "emit".equals(termination.pageTerminationPhase),
                "Natural emit termination phase/cause drifted.");
        require(
                Arrays.equals(
                                termination.enteredPhases,
                                new String[] {
                                    "ingress", "parse", "semantic", "emit"
                                })
                        && Arrays.equals(
                                termination.completedPhases,
                                new String[] {
                                    "ingress", "parse", "semantic"
                                }),
                "Natural emit termination phase histories drifted.");
        require(
                termination.progressSequence == 2
                        && termination.progressCompletedUnits
                                == (R2ExternalNaturalEmitTimeoutProjectFixture
                                                .NATURAL_OUTPUT_AUTHORITY.fileCount
                                        - 1)
                                        / 4
                                        + 1
                        && termination.progressTotalUnits
                                == R2ExternalNaturalEmitTimeoutProjectFixture
                                        .NATURAL_OUTPUT_AUTHORITY.fileCount,
                "Natural emit sequence-two termination progress drifted.");
        require(
                termination.workerGeneration
                                == termination.terminatedWorkerGeneration
                        && termination.replacementWorkerGeneration
                                == termination.terminatedWorkerGeneration + 1,
                "Natural emit exact Worker replacement drifted.");
        require(
                termination.sessionGeneration > 0,
                "Natural emit termination session generation is invalid.");
        long requestedDeadlineNanos =
                TimeUnit.MILLISECONDS.toNanos(calibration.timeoutMillis);
        long deadlineDifferenceNanos =
                Math.abs(
                        termination.deadlineNanosSinceRequestStart
                                - requestedDeadlineNanos);
        require(
                deadlineDifferenceNanos
                        <= TimeUnit.MILLISECONDS.toNanos(250L),
                "Natural emit host deadline drifted from Binder request start.");
        require(
                termination.deadlineOvershootNanos
                        <= TimeUnit.MILLISECONDS.toNanos(
                                NATURAL_EMIT_MAXIMUM_OVERSHOOT_MS),
                "Natural emit deadline selection overshoot was unbounded.");
    }

    private static void assertNaturalPhaseInput(
            NaturalPhaseInputTelemetry input,
            R2ExternalNaturalEmitTimeoutProjectFixture fixture) {
        int expectedFiles;
        int expectedFrames;
        long expectedBytes;
        if (fixture.isNaturalEmitWorkload()) {
            expectedFiles =
                    R2ExternalNaturalEmitTimeoutProjectFixture
                            .NATURAL_TOTAL_FILE_COUNT;
            expectedFrames =
                    R2ExternalNaturalEmitTimeoutProjectFixture
                            .NATURAL_INPUT_TRANSPORT_FRAME_COUNT;
            expectedBytes =
                    R2ExternalNaturalEmitTimeoutProjectFixture
                            .NATURAL_TOTAL_BYTES;
        } else if (fixture.isNaturalSemanticWorkload()) {
            expectedFiles =
                    R2ExternalNaturalEmitTimeoutProjectFixture
                            .SEMANTIC_TOTAL_FILE_COUNT;
            expectedFrames =
                    R2ExternalNaturalEmitTimeoutProjectFixture
                            .SEMANTIC_INPUT_TRANSPORT_FRAME_COUNT;
            expectedBytes =
                    R2ExternalNaturalEmitTimeoutProjectFixture
                            .SEMANTIC_TOTAL_BYTES;
        } else if (fixture.isNaturalParseWorkload()) {
            expectedFiles =
                    R2ExternalNaturalEmitTimeoutProjectFixture
                            .PARSE_TOTAL_FILE_COUNT;
            expectedFrames =
                    R2ExternalNaturalEmitTimeoutProjectFixture
                            .PARSE_INPUT_TRANSPORT_FRAME_COUNT;
            expectedBytes =
                    R2ExternalNaturalEmitTimeoutProjectFixture
                            .PARSE_TOTAL_BYTES;
        } else {
            expectedFiles =
                    R2ExternalNaturalEmitTimeoutProjectFixture
                            .SUCCESSOR_TOTAL_FILE_COUNT;
            expectedFrames =
                    R2ExternalNaturalEmitTimeoutProjectFixture
                            .SUCCESSOR_INPUT_TRANSPORT_FRAME_COUNT;
            expectedBytes =
                    R2ExternalNaturalEmitTimeoutProjectFixture
                            .SUCCESSOR_TOTAL_BYTES;
        }
        require(
                input.sentFrameCount == expectedFrames
                        && input.acceptedPayloadBytes == expectedBytes
                        && input.totalPayloadBytes == expectedBytes,
                "Natural phase input byte/frame authority drifted.");
        require(
                input.complete
                        && input.openedCursorCount == expectedFiles
                        && input.closedCursorCount == expectedFiles
                        && !input.activeCursor
                        && input.released,
                "Natural phase input cursor cleanup is unproven.");
    }

    @SuppressWarnings("deprecation")
    private void runExternalNaturalSemanticTimeoutEvidence()
            throws Exception {
        Context context = getTargetContext();
        require(
                FIXTURE_PACKAGE.equals(context.getPackageName()),
                "Instrumentation did not target the standalone fixture package.");

        PackageManager packageManager = context.getPackageManager();
        ApplicationInfo currentApplication =
                packageManager.getApplicationInfo(CURRENT_PACKAGE, 0);
        require(
                Process.myUid() != currentApplication.uid,
                "Fixture unexpectedly shares the compiler plugin UID.");
        require(
                packageManager.checkSignatures(FIXTURE_PACKAGE, CURRENT_PACKAGE)
                        == PackageManager.SIGNATURE_MATCH,
                "Fixture and compiler plugin do not have the same signer.");
        require(
                (context.getApplicationInfo().flags
                                & ApplicationInfo.FLAG_TEST_ONLY)
                        != 0,
                "External natural semantic fixture is not TEST_ONLY.");
        require(
                (currentApplication.flags & ApplicationInfo.FLAG_TEST_ONLY)
                        != 0,
                "External natural semantic compiler evidence build is not TEST_ONLY.");
        PermissionInfo pluginPermission =
                packageManager.getPermissionInfo(PLUGIN_PERMISSION, 0);
        require(
                (pluginPermission.protectionLevel
                                & PermissionInfo.PROTECTION_MASK_BASE)
                        == PermissionInfo.PROTECTION_SIGNATURE,
                "Compiler service permission is not signature-protected.");
        require(
                packageManager.checkSignatures(
                                pluginPermission.packageName, CURRENT_PACKAGE)
                        == PackageManager.SIGNATURE_MATCH,
                "Compiler service permission owner does not share the plugin signer.");

        Intent discovery =
                new Intent(ACTION_COMPILER)
                        .addCategory(CATEGORY_TYPESCRIPT)
                        .setPackage(CURRENT_PACKAGE);
        List<ResolveInfo> matches =
                packageManager.queryIntentServices(discovery, 0);
        require(matches.size() == 1, "Expected exactly one current compiler service.");
        ServiceInfo serviceInfo = matches.get(0).serviceInfo;
        require(serviceInfo.exported, "Current compiler service is not exported.");
        require(
                PLUGIN_PERMISSION.equals(serviceInfo.permission),
                "Current compiler service permission drifted.");
        ComponentName component =
                new ComponentName(serviceInfo.packageName, serviceInfo.name);
        Context pluginContext =
                context.createPackageContext(CURRENT_PACKAGE, 0);

        try (CleanupEvidenceSession session =
                awaitCleanupEvidenceSession(
                        context,
                        discovery,
                        component,
                        true)) {
            runExternalNaturalSemanticTimeoutMatrix(
                    context,
                    pluginContext,
                    session);
            requireLiveSameBinder(session);
        }
    }

    private static void runExternalNaturalSemanticTimeoutMatrix(
            Context context,
            Context pluginContext,
            CleanupEvidenceSession session)
            throws Exception {
        require(pluginContext != null, "Natural semantic plugin context is null.");
        require(
                NATURAL_SEMANTIC_PRECONDITION_RUNS == 2
                        && NATURAL_SEMANTIC_BASELINE_RUNS == 3
                        && NATURAL_SEMANTIC_FAULT_ATTEMPTS == 1,
                "Natural semantic evidence run authority drifted.");
        List<NaturalSemanticTiming> baselineTimings = new ArrayList<>();
        for (int run = 1; run <= NATURAL_SEMANTIC_PRECONDITION_RUNS; run++) {
            runExternalNaturalSemanticSuccessfulRun(
                    context,
                    session,
                    false,
                    "external-natural-semantic-precondition-" + run,
                    "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_PRECONDITION_PASS",
                    run);
        }
        for (int run = 1; run <= NATURAL_SEMANTIC_BASELINE_RUNS; run++) {
            baselineTimings.add(
                    runExternalNaturalSemanticSuccessfulRun(
                            context,
                            session,
                            false,
                            "external-natural-semantic-baseline-" + run,
                            "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_BASELINE_PASS",
                            run));
        }

        NaturalSemanticCalibration calibration =
                calibrateExternalNaturalSemanticDeadline(baselineTimings);
        runExternalNaturalSemanticFault(
                context,
                session,
                calibration);
        runExternalNaturalSemanticSuccessfulRun(
                context,
                session,
                true,
                "external-natural-semantic-successor",
                "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_SUCCESSOR_PASS",
                1);
        requireLiveSameBinder(session);
        Log.i(
                LOG_TAG,
                "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_MATRIX_PASS"
                        + " preconditionRuns="
                        + NATURAL_SEMANTIC_PRECONDITION_RUNS
                        + " baselineRuns="
                        + NATURAL_SEMANTIC_BASELINE_RUNS
                        + " faultAttempts="
                        + NATURAL_SEMANTIC_FAULT_ATTEMPTS
                        + " timeoutMs="
                        + calibration.timeoutMillis
                        + " roots="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .SEMANTIC_ROOT_COUNT
                        + " files="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .SEMANTIC_TOTAL_FILE_COUNT
                        + " bytes="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .SEMANTIC_TOTAL_BYTES
                        + " tokens="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .SEMANTIC_TOTAL_TOKENS
                        + " ordinaryAdmission=true expandedWorkerCapacity=false"
                        + " requestStartAbsoluteDeadline=true sameBinder=true"
                        + " sameProcessEpoch=true zeroSamples="
                        + REQUIRED_CONSECUTIVE_CLEANUP_SAMPLES);
    }

    private static NaturalSemanticTiming
            runExternalNaturalSemanticSuccessfulRun(
                    Context context,
                    CleanupEvidenceSession session,
                    boolean successor,
                    String requestLabel,
                    String event,
                    int run)
                    throws Exception {
        String requestId = newRequestId(requestLabel);
        R2FenceBoundedProjectFixture.CompilerAuthority authority =
                session.metadata.toFixtureAuthority();
        try (R2ExternalNaturalEmitTimeoutProjectFixture fixture =
                successor
                        ? R2ExternalNaturalEmitTimeoutProjectFixture
                                .createSuccessor(
                                        context, requestId, authority)
                        : R2ExternalNaturalEmitTimeoutProjectFixture
                                .createSemantic(
                                        context, requestId, authority);
                ParcelFileDescriptor input =
                        ParcelFileDescriptor.open(
                                fixture.input,
                                ParcelFileDescriptor.MODE_READ_ONLY);
                ParcelFileDescriptor output =
                        ParcelFileDescriptor.open(
                                fixture.output,
                                ParcelFileDescriptor.MODE_READ_WRITE)) {
            RecordingCallback callback = new RecordingCallback();
            Admission admission =
                    admitNaturalSemanticOnce(
                            session,
                            input,
                            output,
                            callback,
                            requestId,
                            fixture.projectId,
                            fixture.entrySourceUri,
                            fixture.inputArchiveSha256,
                            fixture.requestManifestSha256,
                            TypeScriptCompilerContract.MAX_TIMEOUT_MS);
            CleanupCheckpoint checkpoint =
                    assertPreAcknowledgementCleanupTelemetry(
                            session,
                            admission,
                            input,
                            output);
            acknowledgeAdmission(admission);
            Bundle terminal = callback.awaitTerminal(CALLBACK_WAIT_MS);
            SystemClock.sleep(CALLBACK_QUIET_MS);
            callback.assertExactlyOneCompletion();
            assertTerminalRequestId(terminal, requestId);
            require(
                    terminal.getBoolean(
                            TypeScriptCompilerContract.KEY_SUCCEEDED, false),
                    "Natural semantic successful run failed: outcome="
                            + terminal.getString(
                                    TypeScriptCompilerContract.KEY_OUTCOME)
                            + " errorCode="
                            + terminal.getString(
                                    TypeScriptCompilerContract.KEY_ERROR_CODE)
                            + " errorMessage="
                            + terminal.getString(
                                    TypeScriptCompilerContract.KEY_ERROR_MESSAGE)
                            + " elapsedMs="
                            + terminal.getLong(
                                    TypeScriptCompilerContract.KEY_ELAPSED_MS,
                                    -1L)
                            + ".");
            require(
                    TypeScriptCompilerContract.OUTCOME_EMITTED.equals(
                            terminal.getString(
                                    TypeScriptCompilerContract.KEY_OUTCOME)),
                    "Natural semantic successful run did not emit.");
            require(
                    terminal.getInt(
                                    TypeScriptCompilerContract
                                            .KEY_DIAGNOSTIC_COUNT,
                                    -1)
                            == 0,
                    "Natural semantic successful run returned diagnostics.");
            require(
                    terminal.getInt(
                                    TypeScriptCompilerContract
                                            .KEY_EMITTED_MODULE_COUNT,
                                    -1)
                            == fixture.rootPaths.size(),
                    "Natural semantic successful run module count drifted.");
            Os.fsync(output.getFileDescriptor());
            verifyExternalNaturalEmitOutput(
                    fixture,
                    authority,
                    terminal);

            if (successor) {
                CleanupReceipt cleanupReceipt =
                        awaitCleanupEvidence(
                                session,
                                checkpoint,
                                TypeScriptCompilerContract.OUTCOME_EMITTED,
                                null,
                                false);
                require(
                        cleanupReceipt != null
                                && cleanupReceipt.cursorOpenedCount
                                        == R2ExternalNaturalEmitTimeoutProjectFixture
                                                .SUCCESSOR_TOTAL_FILE_COUNT
                                && cleanupReceipt.cursorClosedCount
                                        == cleanupReceipt.cursorOpenedCount,
                        "Natural semantic successor cleanup cursor authority drifted.");
                Log.i(
                        LOG_TAG,
                        event
                                + " run="
                                + run
                                + " requestId="
                                + requestId
                                + " files="
                                + cleanupReceipt.cursorOpenedCount
                                + " outputBytes="
                                + fixture.expectedOutputAuthority.totalBytes
                                + " outputSha256="
                                + fixture.expectedOutputAuthority.aggregateSha256
                                + " phaseReceiptRequired=false sameBinder=true"
                                + " sameProcessEpoch=true");
                return null;
            }

            NaturalPhaseReceipt phaseReceipt =
                    requireNaturalPhaseReceipt(
                            session, requestId, "success");
            assertSuccessfulNaturalPhaseReceipt(
                    phaseReceipt, fixture);
            CleanupReceipt cleanupReceipt =
                    awaitCleanupEvidence(
                            session,
                            checkpoint,
                            TypeScriptCompilerContract.OUTCOME_EMITTED,
                            null,
                            false);
            require(
                    cleanupReceipt != null
                            && cleanupReceipt.cursorOpenedCount
                                    == R2ExternalNaturalEmitTimeoutProjectFixture
                                            .SEMANTIC_TOTAL_FILE_COUNT
                            && cleanupReceipt.cursorClosedCount
                                    == cleanupReceipt.cursorOpenedCount,
                    "Natural semantic successful cleanup cursor authority drifted.");
            NaturalSemanticTiming timing = phaseReceipt.semanticTiming();
            Log.i(
                    LOG_TAG,
                    event
                            + " run="
                            + run
                            + " requestId="
                            + requestId
                            + " hostRequestId="
                            + phaseReceipt.hostRequestId
                            + " projectStartMs="
                            + TimeUnit.NANOSECONDS.toMillis(
                                    phaseReceipt
                                            .projectStartNanosSinceRequestStart)
                            + " semanticSequenceTwoMs="
                            + TimeUnit.NANOSECONDS.toMillis(
                                    timing.sequenceTwoNanosSinceRequestStart)
                            + " semanticCompletedMs="
                            + TimeUnit.NANOSECONDS.toMillis(
                                    timing.completedNanosSinceRequestStart)
                            + " files="
                            + phaseReceipt.input.openedCursorCount
                            + " frames="
                            + phaseReceipt.input.sentFrameCount
                            + " outputBytes="
                            + fixture.expectedOutputAuthority.totalBytes
                            + " outputSha256="
                            + fixture.expectedOutputAuthority.aggregateSha256);
            return timing;
        }
    }

    private static NaturalSemanticCalibration
            calibrateExternalNaturalSemanticDeadline(
                    List<NaturalSemanticTiming> baselineTimings) {
        require(
                baselineTimings.size() == NATURAL_SEMANTIC_BASELINE_RUNS,
                "Natural semantic calibration did not contain three baselines.");
        long minimumTimeoutMillis = 0L;
        long maximumTimeoutMillis = Long.MAX_VALUE;
        for (NaturalSemanticTiming timing : baselineTimings) {
            long sampleMinimum =
                    TimeUnit.NANOSECONDS.toMillis(
                                    timing.sequenceTwoNanosSinceRequestStart)
                            + 1L;
            long sampleMaximum =
                    (timing.completedNanosSinceRequestStart - 1L)
                            / TimeUnit.MILLISECONDS.toNanos(1L);
            if (sampleMinimum > minimumTimeoutMillis) {
                minimumTimeoutMillis = sampleMinimum;
            }
            if (sampleMaximum < maximumTimeoutMillis) {
                maximumTimeoutMillis = sampleMaximum;
            }
        }
        require(
                maximumTimeoutMillis - minimumTimeoutMillis
                        >= NATURAL_SEMANTIC_MINIMUM_MARGIN_MS * 2L,
                "Natural semantic baselines have no strict two-sided deadline window: "
                        + minimumTimeoutMillis
                        + ".."
                        + maximumTimeoutMillis
                        + " ms; faultAttempts=0.");
        long timeoutMillis =
                minimumTimeoutMillis
                        + (maximumTimeoutMillis - minimumTimeoutMillis) / 2L;
        require(
                timeoutMillis
                        >= TypeScriptR2Contract
                                .MIN_PUBLIC_COMPILATION_TIMEOUT_MS,
                "Natural semantic strict midpoint is below the public timeout minimum; "
                        + "faultAttempts=0.");
        require(
                timeoutMillis - minimumTimeoutMillis
                                >= NATURAL_SEMANTIC_MINIMUM_MARGIN_MS
                        && maximumTimeoutMillis - timeoutMillis
                                >= NATURAL_SEMANTIC_MINIMUM_MARGIN_MS,
                "Natural semantic strict midpoint lost a two-sided margin.");
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        for (NaturalSemanticTiming timing : baselineTimings) {
            require(
                    timing.sequenceTwoNanosSinceRequestStart
                                    < timeoutNanos
                            && timeoutNanos
                                    < timing.completedNanosSinceRequestStart,
                    "Natural semantic midpoint is outside a baseline interval.");
        }
        Log.i(
                LOG_TAG,
                "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_WINDOW_PASS"
                        + " lowerMs="
                        + minimumTimeoutMillis
                        + " upperMs="
                        + maximumTimeoutMillis
                        + " timeoutMs="
                        + timeoutMillis
                        + " leftMarginMs="
                        + (timeoutMillis - minimumTimeoutMillis)
                        + " rightMarginMs="
                        + (maximumTimeoutMillis - timeoutMillis)
                        + " publicMinimumMs="
                        + TypeScriptR2Contract
                                .MIN_PUBLIC_COMPILATION_TIMEOUT_MS
                        + " strictIntersection=true faultAttempts=0");
        return new NaturalSemanticCalibration(
                minimumTimeoutMillis,
                maximumTimeoutMillis,
                timeoutMillis);
    }

    private static void runExternalNaturalSemanticFault(
            Context context,
            CleanupEvidenceSession session,
            NaturalSemanticCalibration calibration)
            throws Exception {
        require(
                NATURAL_SEMANTIC_FAULT_ATTEMPTS == 1,
                "Natural semantic fault must be attempted exactly once.");
        String requestId =
                newRequestId("external-natural-semantic-timeout-fault");
        R2FenceBoundedProjectFixture.CompilerAuthority authority =
                session.metadata.toFixtureAuthority();
        try (R2ExternalNaturalEmitTimeoutProjectFixture fixture =
                R2ExternalNaturalEmitTimeoutProjectFixture.createSemantic(
                        context, requestId, authority);
                ParcelFileDescriptor input =
                        ParcelFileDescriptor.open(
                                fixture.input,
                                ParcelFileDescriptor.MODE_READ_ONLY);
                ParcelFileDescriptor output =
                        ParcelFileDescriptor.open(
                                fixture.output,
                                ParcelFileDescriptor.MODE_READ_WRITE)) {
            RecordingCallback callback = new RecordingCallback();
            long wallStartedAtNanos = System.nanoTime();
            Admission admission =
                    admitNaturalSemanticOnce(
                            session,
                            input,
                            output,
                            callback,
                            requestId,
                            fixture.projectId,
                            fixture.entrySourceUri,
                            fixture.inputArchiveSha256,
                            fixture.requestManifestSha256,
                            calibration.timeoutMillis);
            CleanupCheckpoint checkpoint =
                    assertPreAcknowledgementCleanupTelemetry(
                            session,
                            admission,
                            input,
                            output);
            acknowledgeAdmission(admission);
            Bundle terminal = callback.awaitTerminal(CALLBACK_WAIT_MS);
            long wallElapsedMillis =
                    TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - wallStartedAtNanos);
            SystemClock.sleep(CALLBACK_QUIET_MS);
            callback.assertExactlyOneCompletion();
            assertTerminalRequestId(terminal, requestId);
            require(
                    !terminal.getBoolean(
                            TypeScriptCompilerContract.KEY_SUCCEEDED, true),
                    "Natural semantic fault unexpectedly succeeded.");
            require(
                    TypeScriptCompilerContract.OUTCOME_TIMED_OUT.equals(
                            terminal.getString(
                                    TypeScriptCompilerContract.KEY_OUTCOME)),
                    "Natural semantic fault did not time out.");
            require(
                    TypeScriptCompilerContract.ERROR_TIMEOUT.equals(
                            terminal.getString(
                                    TypeScriptCompilerContract
                                            .KEY_ERROR_CODE)),
                    "Natural semantic fault returned the wrong error.");
            require(
                    wallElapsedMillis >= calibration.timeoutMillis,
                    "Natural semantic fault preceded its request-start deadline.");
            assertNoCompilationPayload(
                    terminal, "External natural semantic timeout");
            assertSyncedEmptyOutput(
                    output,
                    fixture.output,
                    "External natural semantic timeout");

            NaturalPhaseReceipt phaseReceipt =
                    requireNaturalPhaseReceipt(
                            session, requestId, "timed_out");
            assertTimedOutNaturalSemanticPhaseReceipt(
                    phaseReceipt,
                    fixture,
                    calibration);
            CleanupReceipt cleanupReceipt =
                    awaitCleanupEvidence(
                            session,
                            checkpoint,
                            TypeScriptCompilerContract.OUTCOME_TIMED_OUT,
                            TypeScriptCompilerContract.ERROR_TIMEOUT,
                            false);
            require(
                    cleanupReceipt != null
                            && cleanupReceipt.cursorOpenedCount
                                    == R2ExternalNaturalEmitTimeoutProjectFixture
                                            .SEMANTIC_TOTAL_FILE_COUNT
                            && cleanupReceipt.cursorClosedCount
                                    == cleanupReceipt.cursorOpenedCount,
                    "Natural semantic fault cleanup cursor authority drifted.");
            NaturalPhaseTermination termination = phaseReceipt.termination;
            Log.i(
                    LOG_TAG,
                    "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_FAULT_PASS"
                            + " faultAttempts=1 requestId="
                            + requestId
                            + " hostRequestId="
                            + phaseReceipt.hostRequestId
                            + " timeoutMs="
                            + calibration.timeoutMillis
                            + " wallElapsedMs="
                            + wallElapsedMillis
                            + " phase=semantic progressSequence="
                            + termination.progressSequence
                            + " completedUnits="
                            + termination.progressCompletedUnits
                            + " totalUnits="
                            + termination.progressTotalUnits
                            + " deadlineOvershootMs="
                            + TimeUnit.NANOSECONDS.toMillis(
                                    termination.deadlineOvershootNanos)
                            + " openedCursors="
                            + phaseReceipt.input.openedCursorCount
                            + " closedCursors="
                            + phaseReceipt.input.closedCursorCount
                            + " terminatedWorkerGeneration="
                            + termination.terminatedWorkerGeneration
                            + " replacementWorkerGeneration="
                            + termination.replacementWorkerGeneration
                            + " outputBytes="
                            + fixture.output.length()
                            + " callbackBeforeReceiptRead=true"
                            + " receiptCreatedBeforeCallback=true");
        }
    }

    private static void assertTimedOutNaturalSemanticPhaseReceipt(
            NaturalPhaseReceipt receipt,
            R2ExternalNaturalEmitTimeoutProjectFixture fixture,
            NaturalSemanticCalibration calibration) {
        require(
                fixture.isNaturalSemanticWorkload(),
                "Natural semantic termination used the wrong fixture workload.");
        require(
                Arrays.equals(
                        receipt.phases,
                        new String[] {"parse"}),
                "Timed-out natural phase receipt completed semantic.");
        assertNaturalPhaseInput(receipt.input, fixture);
        NaturalPhaseTermination termination = receipt.termination;
        require(termination != null, "Timed-out natural phase receipt lost termination.");
        require(
                "TIMED_OUT".equals(termination.stopCause)
                        && "semantic".equals(termination.hostStopPhase)
                        && "semantic".equals(termination.pageTerminationPhase),
                "Natural semantic termination phase/cause drifted.");
        require(
                Arrays.equals(
                                termination.enteredPhases,
                                new String[] {
                                    "ingress", "parse", "semantic"
                                })
                        && Arrays.equals(
                                termination.completedPhases,
                                new String[] {"ingress", "parse"}),
                "Natural semantic termination phase histories drifted.");
        require(
                termination.progressSequence == 2
                        && termination.progressCompletedUnits
                                == (R2ExternalNaturalEmitTimeoutProjectFixture
                                                .SEMANTIC_ROOT_COUNT
                                        - 1)
                                        / 4
                                        + 1
                        && termination.progressTotalUnits
                                == R2ExternalNaturalEmitTimeoutProjectFixture
                                        .SEMANTIC_ROOT_COUNT,
                "Natural semantic sequence-two termination progress drifted.");
        require(
                termination.workerGeneration
                                == termination.terminatedWorkerGeneration
                        && termination.replacementWorkerGeneration
                                == termination.terminatedWorkerGeneration + 1,
                "Natural semantic exact Worker replacement drifted.");
        require(
                termination.sessionGeneration > 0,
                "Natural semantic termination session generation is invalid.");
        long requestedDeadlineNanos =
                TimeUnit.MILLISECONDS.toNanos(calibration.timeoutMillis);
        long deadlineDifferenceNanos =
                Math.abs(
                        termination.deadlineNanosSinceRequestStart
                                - requestedDeadlineNanos);
        require(
                deadlineDifferenceNanos
                        <= TimeUnit.MILLISECONDS.toNanos(250L),
                "Natural semantic host deadline drifted from Binder request start.");
        require(
                termination.deadlineOvershootNanos
                        <= TimeUnit.MILLISECONDS.toNanos(
                                NATURAL_SEMANTIC_MAXIMUM_OVERSHOOT_MS),
                "Natural semantic deadline selection overshoot was unbounded.");
    }

    /**
     * One irreversible public admission attempt used by every natural semantic run.
     */
    private static Admission admitNaturalSemanticOnce(
            CleanupEvidenceSession session,
            ParcelFileDescriptor input,
            ParcelFileDescriptor output,
            RecordingCallback callback,
            String requestId,
            String projectId,
            String entrySourceUri,
            String inputArchiveSha256,
            String requestManifestSha256,
            long timeoutMs)
            throws Exception {
        requireLiveSameBinder(session);
        require(
                timeoutMs
                        >= TypeScriptR2Contract
                                .MIN_PUBLIC_COMPILATION_TIMEOUT_MS,
                "Natural semantic timeout is below the public minimum.");
        require(
                timeoutMs <= TypeScriptCompilerContract.MAX_TIMEOUT_MS,
                "Natural semantic timeout is above the public maximum.");
        ITypeScriptCompilerPlugin compiler = session.bound.compiler;
        CompilerMetadata metadata =
                CompilerMetadata.from(compiler.getCompilerInfo());
        require(
                metadata != null,
                "Same-Binder natural semantic compiler metadata became unavailable.");
        metadata.requireMatches(session.metadata.toFixtureAuthority());
        Bundle ticket =
                compiler.startCompilation(
                        publicRequest(
                                requestId,
                                projectId,
                                entrySourceUri,
                                metadata,
                                inputArchiveSha256,
                                requestManifestSha256,
                                null,
                                timeoutMs),
                        input,
                        output,
                        callback);
        require(ticket != null, "Compiler returned a null natural semantic ticket.");
        require(
                requestId.equals(
                        ticket.getString(
                                TypeScriptCompilerContract.KEY_REQUEST_ID)),
                "Natural semantic admission ticket lost the request ID.");
        require(
                ticket.getBoolean(
                        TypeScriptCompilerContract.KEY_ACCEPTED, false),
                "Natural semantic request was rejected without retry: "
                        + ticket.getString(
                                TypeScriptCompilerContract.KEY_ERROR_CODE)
                        + ": "
                        + ticket.getString(
                                TypeScriptCompilerContract.KEY_ERROR_MESSAGE));
        return new Admission(
                session.bound,
                compiler,
                ticket,
                requestId,
                metadata);
    }

    @SuppressWarnings("deprecation")
    private void runShippingReleaseNaturalParseTimeoutEvidence()
            throws Exception {
        Context context = getTargetContext();
        require(
                FIXTURE_PACKAGE.equals(context.getPackageName()),
                "Instrumentation did not target the standalone fixture package.");

        PackageManager packageManager = context.getPackageManager();
        ApplicationInfo currentApplication =
                packageManager.getApplicationInfo(CURRENT_PACKAGE, 0);
        require(
                Process.myUid() != currentApplication.uid,
                "Fixture unexpectedly shares the compiler plugin UID.");
        require(
                packageManager.checkSignatures(FIXTURE_PACKAGE, CURRENT_PACKAGE)
                        == PackageManager.SIGNATURE_MATCH,
                "Fixture and compiler plugin do not have the same signer.");
        require(
                (context.getApplicationInfo().flags
                                & ApplicationInfo.FLAG_TEST_ONLY)
                        != 0,
                "Shipping Release natural parse fixture is not TEST_ONLY.");
        require(
                (currentApplication.flags & ApplicationInfo.FLAG_TEST_ONLY)
                        == 0,
                "Natural parse target is a TEST_ONLY evidence build, not normal Release.");
        require(
                (currentApplication.flags & ApplicationInfo.FLAG_DEBUGGABLE)
                        == 0,
                "Natural parse target is debuggable, not normal Release.");
        PermissionInfo pluginPermission =
                packageManager.getPermissionInfo(PLUGIN_PERMISSION, 0);
        require(
                (pluginPermission.protectionLevel
                                & PermissionInfo.PROTECTION_MASK_BASE)
                        == PermissionInfo.PROTECTION_SIGNATURE,
                "Compiler service permission is not signature-protected.");
        require(
                packageManager.checkSignatures(
                                pluginPermission.packageName, CURRENT_PACKAGE)
                        == PackageManager.SIGNATURE_MATCH,
                "Compiler service permission owner does not share the plugin signer.");

        Intent discovery =
                new Intent(ACTION_COMPILER)
                        .addCategory(CATEGORY_TYPESCRIPT)
                        .setPackage(CURRENT_PACKAGE);
        List<ResolveInfo> matches =
                packageManager.queryIntentServices(discovery, 0);
        require(matches.size() == 1, "Expected exactly one current compiler service.");
        ServiceInfo serviceInfo = matches.get(0).serviceInfo;
        require(serviceInfo.exported, "Current compiler service is not exported.");
        require(
                PLUGIN_PERMISSION.equals(serviceInfo.permission),
                "Current compiler service permission drifted.");
        require(
                serviceInfo.applicationInfo.uid == currentApplication.uid,
                "Resolved compiler service belongs to an unexpected UID.");
        ComponentName component =
                new ComponentName(serviceInfo.packageName, serviceInfo.name);

        try (CleanupEvidenceSession session =
                awaitCleanupEvidenceSession(
                        context,
                        discovery,
                        component,
                        false)) {
            require(
                    !session.telemetryRequired && session.identity == null,
                    "Normal Release session unexpectedly enabled private telemetry.");
            Bundle compilerInfo = session.bound.compiler.getCompilerInfo();
            for (String key : compilerInfo.keySet()) {
                require(
                        !key.startsWith(NATURAL_PHASE_TIMEOUT_PREFIX)
                                && !key.startsWith(CLEANUP_TELEMETRY_PREFIX),
                        "Normal Release exposed a private evidence key: " + key);
            }
            runShippingReleaseNaturalParseTimeoutMatrix(context, session);
            requireLiveSameBinder(session);
        }
    }

    private static void runShippingReleaseNaturalParseTimeoutMatrix(
            Context context, CleanupEvidenceSession session) throws Exception {
        require(
                NATURAL_PARSE_PRECONDITION_RUNS == 2
                        && NATURAL_PARSE_BASELINE_RUNS == 3
                        && NATURAL_PARSE_FAULT_ATTEMPTS == 1,
                "Shipping Release natural parse run authority drifted.");
        List<NaturalParseTiming> baselineTimings = new ArrayList<>();
        for (int run = 1; run <= NATURAL_PARSE_PRECONDITION_RUNS; run++) {
            runShippingReleaseNaturalParseSuccessfulRun(
                    context,
                    session,
                    false,
                    "shipping-release-natural-parse-precondition-" + run,
                    "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_PRECONDITION_PASS",
                    run);
        }
        for (int run = 1; run <= NATURAL_PARSE_BASELINE_RUNS; run++) {
            baselineTimings.add(
                    runShippingReleaseNaturalParseSuccessfulRun(
                            context,
                            session,
                            false,
                            "shipping-release-natural-parse-baseline-" + run,
                            "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_BASELINE_PASS",
                            run));
        }
        NaturalParseCalibration calibration =
                calibrateNaturalParseDeadline(
                        baselineTimings,
                        "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_WINDOW_PASS");
        runShippingReleaseNaturalParseFault(context, session, calibration);
        runShippingReleaseNaturalParseSuccessfulRun(
                context,
                session,
                true,
                "shipping-release-natural-parse-successor",
                "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_SUCCESSOR_PASS",
                1);
        requireLiveSameBinder(session);
        Log.i(
                LOG_TAG,
                "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_MATRIX_PASS"
                        + " preconditionRuns="
                        + NATURAL_PARSE_PRECONDITION_RUNS
                        + " baselineRuns="
                        + NATURAL_PARSE_BASELINE_RUNS
                        + " faultAttempts="
                        + NATURAL_PARSE_FAULT_ATTEMPTS
                        + " timeoutMs="
                        + calibration.timeoutMillis
                        + " files="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .PARSE_TOTAL_FILE_COUNT
                        + " bytes="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .PARSE_TOTAL_BYTES
                        + " tokens="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .PARSE_TOTAL_TOKENS
                        + " ordinaryAdmission=true publicProgress=true"
                        + " privateReceipt=false pluginTestOnly=false"
                        + " pluginDebuggable=false sameBinder=true");
    }

    private static NaturalParseTiming
            runShippingReleaseNaturalParseSuccessfulRun(
                    Context context,
                    CleanupEvidenceSession session,
                    boolean successor,
                    String requestLabel,
                    String event,
                    int run)
                    throws Exception {
        String requestId = newRequestId(requestLabel);
        R2FenceBoundedProjectFixture.CompilerAuthority authority =
                session.metadata.toFixtureAuthority();
        try (R2ExternalNaturalEmitTimeoutProjectFixture fixture =
                successor
                        ? R2ExternalNaturalEmitTimeoutProjectFixture
                                .createSuccessor(context, requestId, authority)
                        : R2ExternalNaturalEmitTimeoutProjectFixture
                                .createParse(context, requestId, authority);
                ParcelFileDescriptor input =
                        ParcelFileDescriptor.open(
                                fixture.input,
                                ParcelFileDescriptor.MODE_READ_ONLY);
                ParcelFileDescriptor output =
                        ParcelFileDescriptor.open(
                                fixture.output,
                                ParcelFileDescriptor.MODE_READ_WRITE)) {
            RecordingCallback callback = new RecordingCallback();
            Admission admission =
                    admitNaturalParseOnce(
                            session,
                            input,
                            output,
                            callback,
                            requestId,
                            fixture.projectId,
                            fixture.entrySourceUri,
                            fixture.inputArchiveSha256,
                            fixture.requestManifestSha256,
                            TypeScriptCompilerContract.MAX_TIMEOUT_MS);
            CleanupCheckpoint checkpoint =
                    assertPreAcknowledgementCleanupTelemetry(
                            session, admission, input, output);
            acknowledgeAdmission(admission);
            Bundle terminal = callback.awaitTerminal(CALLBACK_WAIT_MS);
            SystemClock.sleep(CALLBACK_QUIET_MS);
            callback.assertExactlyOneCompletion();
            callback.assertTerminalFollowsAllProgress();
            assertTerminalRequestId(terminal, requestId);
            require(
                    terminal.getBoolean(
                            TypeScriptCompilerContract.KEY_SUCCEEDED, false),
                    "Shipping Release natural parse success run failed: "
                            + terminal.getString(
                                    TypeScriptCompilerContract.KEY_ERROR_CODE)
                            + ": "
                            + terminal.getString(
                                    TypeScriptCompilerContract.KEY_ERROR_MESSAGE));
            require(
                    TypeScriptCompilerContract.OUTCOME_EMITTED.equals(
                            terminal.getString(
                                    TypeScriptCompilerContract.KEY_OUTCOME)),
                    "Shipping Release natural parse success did not emit.");
            require(
                    terminal.getInt(
                                    TypeScriptCompilerContract
                                            .KEY_DIAGNOSTIC_COUNT,
                                    -1)
                            == 0
                            && terminal.getInt(
                                            TypeScriptCompilerContract
                                                    .KEY_EMITTED_MODULE_COUNT,
                                            -1)
                                    == fixture.rootPaths.size(),
                    "Shipping Release natural parse output counters drifted.");
            Os.fsync(output.getFileDescriptor());
            verifyExternalNaturalEmitOutput(fixture, authority, terminal);
            List<R2PublicProgress.Event> publicProgress =
                    R2PublicProgress.parse(
                            callback.progressSnapshot(), requestId);
            R2PublicProgress.ParseWindow window =
                    R2PublicProgress.requireSuccessfulNaturalParse(
                            publicProgress,
                            fixture.rootPaths.size(),
                            fixture.expectedOutputAuthority.fileCount,
                            successor);
            awaitCleanupEvidence(
                    session,
                    checkpoint,
                    TypeScriptCompilerContract.OUTCOME_EMITTED,
                    null,
                    false);
            if (successor) {
                Log.i(
                        LOG_TAG,
                        event
                                + " run="
                                + run
                                + " requestId="
                                + requestId
                                + " progressEvents="
                                + publicProgress.size()
                                + " files="
                                + R2ExternalNaturalEmitTimeoutProjectFixture
                                        .SUCCESSOR_TOTAL_FILE_COUNT
                                + " outputBytes="
                                + fixture.expectedOutputAuthority.totalBytes
                                + " outputSha256="
                                + fixture.expectedOutputAuthority.aggregateSha256
                                + " sameBinder=true");
                return null;
            }
            require(window != null, "Natural parse public timing window is null.");
            Log.i(
                    LOG_TAG,
                    event
                            + " run="
                            + run
                            + " requestId="
                            + requestId
                            + " parseSequenceTwoMs="
                            + TimeUnit.NANOSECONDS.toMillis(
                                    window.sequenceTwoElapsedNanos)
                            + " parseCompletedMs="
                            + TimeUnit.NANOSECONDS.toMillis(
                                    window.completedElapsedNanos)
                            + " progressEvents="
                            + publicProgress.size()
                            + " outputBytes="
                            + fixture.expectedOutputAuthority.totalBytes
                            + " outputSha256="
                            + fixture.expectedOutputAuthority.aggregateSha256);
            return new NaturalParseTiming(
                    window.sequenceTwoElapsedNanos,
                    window.completedElapsedNanos);
        }
    }

    private static void runShippingReleaseNaturalParseFault(
            Context context,
            CleanupEvidenceSession session,
            NaturalParseCalibration calibration)
            throws Exception {
        require(
                NATURAL_PARSE_FAULT_ATTEMPTS == 1,
                "Shipping Release natural parse fault must run exactly once.");
        String requestId =
                newRequestId("shipping-release-natural-parse-timeout-fault");
        R2FenceBoundedProjectFixture.CompilerAuthority authority =
                session.metadata.toFixtureAuthority();
        try (R2ExternalNaturalEmitTimeoutProjectFixture fixture =
                R2ExternalNaturalEmitTimeoutProjectFixture.createParse(
                        context, requestId, authority);
                ParcelFileDescriptor input =
                        ParcelFileDescriptor.open(
                                fixture.input,
                                ParcelFileDescriptor.MODE_READ_ONLY);
                ParcelFileDescriptor output =
                        ParcelFileDescriptor.open(
                                fixture.output,
                                ParcelFileDescriptor.MODE_READ_WRITE)) {
            RecordingCallback callback = new RecordingCallback();
            long wallStartedAtNanos = System.nanoTime();
            Admission admission =
                    admitNaturalParseOnce(
                            session,
                            input,
                            output,
                            callback,
                            requestId,
                            fixture.projectId,
                            fixture.entrySourceUri,
                            fixture.inputArchiveSha256,
                            fixture.requestManifestSha256,
                            calibration.timeoutMillis);
            CleanupCheckpoint checkpoint =
                    assertPreAcknowledgementCleanupTelemetry(
                            session, admission, input, output);
            acknowledgeAdmission(admission);
            Bundle terminal = callback.awaitTerminal(CALLBACK_WAIT_MS);
            long wallElapsedMillis =
                    TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - wallStartedAtNanos);
            SystemClock.sleep(CALLBACK_QUIET_MS);
            callback.assertExactlyOneCompletion();
            callback.assertTerminalFollowsAllProgress();
            assertTerminalRequestId(terminal, requestId);
            require(
                    !terminal.getBoolean(
                            TypeScriptCompilerContract.KEY_SUCCEEDED, true)
                            && TypeScriptCompilerContract.OUTCOME_TIMED_OUT.equals(
                                    terminal.getString(
                                            TypeScriptCompilerContract.KEY_OUTCOME))
                            && TypeScriptCompilerContract.ERROR_TIMEOUT.equals(
                                    terminal.getString(
                                            TypeScriptCompilerContract
                                                    .KEY_ERROR_CODE)),
                    "Shipping Release natural parse fault did not return timeout.");
            long terminalElapsedMillis =
                    terminal.getLong(
                            TypeScriptCompilerContract.KEY_ELAPSED_MS, -1L);
            require(
                    wallElapsedMillis >= calibration.timeoutMillis
                            && terminalElapsedMillis >= calibration.timeoutMillis
                            && terminalElapsedMillis - calibration.timeoutMillis
                                    <= NATURAL_PARSE_MAXIMUM_OVERSHOOT_MS,
                    "Shipping Release natural parse timeout boundary drifted.");
            assertNoCompilationPayload(
                    terminal, "Shipping Release natural parse timeout");
            assertSyncedEmptyOutput(
                    output,
                    fixture.output,
                    "Shipping Release natural parse timeout");
            List<R2PublicProgress.Event> publicProgress =
                    R2PublicProgress.parse(
                            callback.progressSnapshot(), requestId);
            R2PublicProgress.Event sequenceTwo =
                    R2PublicProgress.requireTimedOutNaturalParse(
                            publicProgress,
                            R2ExternalNaturalEmitTimeoutProjectFixture
                                    .PARSE_ROOT_COUNT);
            require(
                    sequenceTwo.elapsedNanos
                            < TimeUnit.MILLISECONDS.toNanos(
                                    calibration.timeoutMillis),
                    "Shipping Release parse sequence two did not precede the deadline.");
            awaitCleanupEvidence(
                    session,
                    checkpoint,
                    TypeScriptCompilerContract.OUTCOME_TIMED_OUT,
                    TypeScriptCompilerContract.ERROR_TIMEOUT,
                    false);
            requireLiveSameBinder(session);
            Log.i(
                    LOG_TAG,
                    "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_FAULT_PASS"
                            + " faultAttempts=1 requestId="
                            + requestId
                            + " timeoutMs="
                            + calibration.timeoutMillis
                            + " wallElapsedMs="
                            + wallElapsedMillis
                            + " terminalElapsedMs="
                            + terminalElapsedMillis
                            + " phase=parse progressSequence="
                            + sequenceTwo.sequence
                            + " completedUnits="
                            + sequenceTwo.completedUnits
                            + " totalUnits="
                            + sequenceTwo.totalUnits
                            + " progressElapsedMs="
                            + TimeUnit.NANOSECONDS.toMillis(
                                    sequenceTwo.elapsedNanos)
                            + " progressEvents="
                            + publicProgress.size()
                            + " outputBytes="
                            + fixture.output.length()
                            + " privateReceipt=false sameBinder=true");
        }
    }

    @SuppressWarnings("deprecation")
    private void runExternalNaturalParseTimeoutEvidence()
            throws Exception {
        Context context = getTargetContext();
        require(
                FIXTURE_PACKAGE.equals(context.getPackageName()),
                "Instrumentation did not target the standalone fixture package.");

        PackageManager packageManager = context.getPackageManager();
        ApplicationInfo currentApplication =
                packageManager.getApplicationInfo(CURRENT_PACKAGE, 0);
        require(
                Process.myUid() != currentApplication.uid,
                "Fixture unexpectedly shares the compiler plugin UID.");
        require(
                packageManager.checkSignatures(FIXTURE_PACKAGE, CURRENT_PACKAGE)
                        == PackageManager.SIGNATURE_MATCH,
                "Fixture and compiler plugin do not have the same signer.");
        require(
                (context.getApplicationInfo().flags
                                & ApplicationInfo.FLAG_TEST_ONLY)
                        != 0,
                "External natural parse fixture is not TEST_ONLY.");
        require(
                (currentApplication.flags & ApplicationInfo.FLAG_TEST_ONLY)
                        != 0,
                "External natural parse compiler evidence build is not TEST_ONLY.");
        PermissionInfo pluginPermission =
                packageManager.getPermissionInfo(PLUGIN_PERMISSION, 0);
        require(
                (pluginPermission.protectionLevel
                                & PermissionInfo.PROTECTION_MASK_BASE)
                        == PermissionInfo.PROTECTION_SIGNATURE,
                "Compiler service permission is not signature-protected.");
        require(
                packageManager.checkSignatures(
                                pluginPermission.packageName, CURRENT_PACKAGE)
                        == PackageManager.SIGNATURE_MATCH,
                "Compiler service permission owner does not share the plugin signer.");

        Intent discovery =
                new Intent(ACTION_COMPILER)
                        .addCategory(CATEGORY_TYPESCRIPT)
                        .setPackage(CURRENT_PACKAGE);
        List<ResolveInfo> matches =
                packageManager.queryIntentServices(discovery, 0);
        require(matches.size() == 1, "Expected exactly one current compiler service.");
        ServiceInfo serviceInfo = matches.get(0).serviceInfo;
        require(serviceInfo.exported, "Current compiler service is not exported.");
        require(
                PLUGIN_PERMISSION.equals(serviceInfo.permission),
                "Current compiler service permission drifted.");
        ComponentName component =
                new ComponentName(serviceInfo.packageName, serviceInfo.name);
        Context pluginContext =
                context.createPackageContext(CURRENT_PACKAGE, 0);

        try (CleanupEvidenceSession session =
                awaitCleanupEvidenceSession(
                        context,
                        discovery,
                        component,
                        true)) {
            runExternalNaturalParseTimeoutMatrix(
                    context,
                    pluginContext,
                    session);
            requireLiveSameBinder(session);
        }
    }

    private static void runExternalNaturalParseTimeoutMatrix(
            Context context,
            Context pluginContext,
            CleanupEvidenceSession session)
            throws Exception {
        require(pluginContext != null, "Natural parse plugin context is null.");
        require(
                NATURAL_PARSE_PRECONDITION_RUNS == 2
                        && NATURAL_PARSE_BASELINE_RUNS == 3
                        && NATURAL_PARSE_FAULT_ATTEMPTS == 1,
                "Natural parse evidence run authority drifted.");
        require(
                PRE_ACK_TELEMETRY_WAIT_MS
                        < NATURAL_PARSE_MINIMUM_MARGIN_MS,
                "Natural parse pre-ACK sampling bound escaped the strict "
                        + "calibration margin.");
        List<NaturalParseTiming> baselineTimings = new ArrayList<>();
        for (int run = 1; run <= NATURAL_PARSE_PRECONDITION_RUNS; run++) {
            runExternalNaturalParseSuccessfulRun(
                    context,
                    session,
                    false,
                    "external-natural-parse-precondition-" + run,
                    "EXTERNAL_NATURAL_PARSE_TIMEOUT_PRECONDITION_PASS",
                    run);
        }
        for (int run = 1; run <= NATURAL_PARSE_BASELINE_RUNS; run++) {
            baselineTimings.add(
                    runExternalNaturalParseSuccessfulRun(
                            context,
                            session,
                            false,
                            "external-natural-parse-baseline-" + run,
                            "EXTERNAL_NATURAL_PARSE_TIMEOUT_BASELINE_PASS",
                            run));
        }

        NaturalParseCalibration calibration =
                calibrateNaturalParseDeadline(
                        baselineTimings,
                        "EXTERNAL_NATURAL_PARSE_TIMEOUT_WINDOW_PASS");
        runExternalNaturalParseFault(
                context,
                session,
                calibration);
        runExternalNaturalParseSuccessfulRun(
                context,
                session,
                true,
                "external-natural-parse-successor",
                "EXTERNAL_NATURAL_PARSE_TIMEOUT_SUCCESSOR_PASS",
                1);
        requireLiveSameBinder(session);
        Log.i(
                LOG_TAG,
                "EXTERNAL_NATURAL_PARSE_TIMEOUT_MATRIX_PASS"
                        + " preconditionRuns="
                        + NATURAL_PARSE_PRECONDITION_RUNS
                        + " baselineRuns="
                        + NATURAL_PARSE_BASELINE_RUNS
                        + " faultAttempts="
                        + NATURAL_PARSE_FAULT_ATTEMPTS
                        + " timeoutMs="
                        + calibration.timeoutMillis
                        + " roots="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .PARSE_ROOT_COUNT
                        + " files="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .PARSE_TOTAL_FILE_COUNT
                        + " bytes="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .PARSE_TOTAL_BYTES
                        + " tokens="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .PARSE_TOTAL_TOKENS
                        + " preSequenceImports="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .PARSE_IMPORT_ROOT_COUNT
                                * R2ExternalNaturalEmitTimeoutProjectFixture
                                        .PARSE_IMPORTS_PER_ROOT
                        + " postSequenceImports="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .PARSE_POST_SEQUENCE_IMPORT_COUNT
                        + " postSequenceRoot="
                        + R2ExternalNaturalEmitTimeoutProjectFixture
                                .PARSE_POST_SEQUENCE_IMPORT_ROOT_INDEX
                        + " ordinaryAdmission=true expandedWorkerCapacity=false"
                        + " requestStartAbsoluteDeadline=true sameBinder=true"
                        + " sameProcessEpoch=true zeroSamples="
                        + REQUIRED_CONSECUTIVE_CLEANUP_SAMPLES);
    }

    private static NaturalParseTiming
            runExternalNaturalParseSuccessfulRun(
                    Context context,
                    CleanupEvidenceSession session,
                    boolean successor,
                    String requestLabel,
                    String event,
                    int run)
                    throws Exception {
        String requestId = newRequestId(requestLabel);
        R2FenceBoundedProjectFixture.CompilerAuthority authority =
                session.metadata.toFixtureAuthority();
        try (R2ExternalNaturalEmitTimeoutProjectFixture fixture =
                successor
                        ? R2ExternalNaturalEmitTimeoutProjectFixture
                                .createSuccessor(
                                        context, requestId, authority)
                        : R2ExternalNaturalEmitTimeoutProjectFixture
                                .createParse(
                                        context, requestId, authority);
                ParcelFileDescriptor input =
                        ParcelFileDescriptor.open(
                                fixture.input,
                                ParcelFileDescriptor.MODE_READ_ONLY);
                ParcelFileDescriptor output =
                        ParcelFileDescriptor.open(
                                fixture.output,
                                ParcelFileDescriptor.MODE_READ_WRITE)) {
            RecordingCallback callback = new RecordingCallback();
            Admission admission =
                    admitNaturalParseOnce(
                            session,
                            input,
                            output,
                            callback,
                            requestId,
                            fixture.projectId,
                            fixture.entrySourceUri,
                            fixture.inputArchiveSha256,
                            fixture.requestManifestSha256,
                            TypeScriptCompilerContract.MAX_TIMEOUT_MS);
            CleanupCheckpoint checkpoint =
                    assertPreAcknowledgementCleanupTelemetry(
                            session,
                            admission,
                            input,
                            output);
            acknowledgeAdmission(admission);
            Bundle terminal = callback.awaitTerminal(CALLBACK_WAIT_MS);
            SystemClock.sleep(CALLBACK_QUIET_MS);
            callback.assertExactlyOneCompletion();
            assertTerminalRequestId(terminal, requestId);
            require(
                    terminal.getBoolean(
                            TypeScriptCompilerContract.KEY_SUCCEEDED, false),
                    "Natural parse successful run failed: outcome="
                            + terminal.getString(
                                    TypeScriptCompilerContract.KEY_OUTCOME)
                            + " errorCode="
                            + terminal.getString(
                                    TypeScriptCompilerContract.KEY_ERROR_CODE)
                            + " errorMessage="
                            + terminal.getString(
                                    TypeScriptCompilerContract.KEY_ERROR_MESSAGE)
                            + " elapsedMs="
                            + terminal.getLong(
                                    TypeScriptCompilerContract.KEY_ELAPSED_MS,
                                    -1L)
                            + ".");
            require(
                    TypeScriptCompilerContract.OUTCOME_EMITTED.equals(
                            terminal.getString(
                                    TypeScriptCompilerContract.KEY_OUTCOME)),
                    "Natural parse successful run did not emit.");
            require(
                    terminal.getInt(
                                    TypeScriptCompilerContract
                                            .KEY_DIAGNOSTIC_COUNT,
                                    -1)
                            == 0,
                    "Natural parse successful run returned diagnostics.");
            require(
                    terminal.getInt(
                                    TypeScriptCompilerContract
                                            .KEY_EMITTED_MODULE_COUNT,
                                    -1)
                            == fixture.rootPaths.size(),
                    "Natural parse successful run module count drifted.");
            Os.fsync(output.getFileDescriptor());
            verifyExternalNaturalEmitOutput(
                    fixture,
                    authority,
                    terminal);

            if (successor) {
                CleanupReceipt cleanupReceipt =
                        awaitCleanupEvidence(
                                session,
                                checkpoint,
                                TypeScriptCompilerContract.OUTCOME_EMITTED,
                                null,
                                false);
                require(
                        cleanupReceipt != null
                                && cleanupReceipt.cursorOpenedCount
                                        == R2ExternalNaturalEmitTimeoutProjectFixture
                                                .SUCCESSOR_TOTAL_FILE_COUNT
                                && cleanupReceipt.cursorClosedCount
                                        == cleanupReceipt.cursorOpenedCount,
                        "Natural parse successor cleanup cursor authority drifted.");
                Log.i(
                        LOG_TAG,
                        event
                                + " run="
                                + run
                                + " requestId="
                                + requestId
                                + " files="
                                + cleanupReceipt.cursorOpenedCount
                                + " outputBytes="
                                + fixture.expectedOutputAuthority.totalBytes
                                + " outputSha256="
                                + fixture.expectedOutputAuthority.aggregateSha256
                                + " phaseReceiptRequired=false sameBinder=true"
                                + " sameProcessEpoch=true");
                return null;
            }

            NaturalPhaseReceipt phaseReceipt =
                    requireNaturalPhaseReceipt(
                            session, requestId, "success");
            assertSuccessfulNaturalPhaseReceipt(
                    phaseReceipt, fixture);
            CleanupReceipt cleanupReceipt =
                    awaitCleanupEvidence(
                            session,
                            checkpoint,
                            TypeScriptCompilerContract.OUTCOME_EMITTED,
                            null,
                            false);
            require(
                    cleanupReceipt != null
                            && cleanupReceipt.cursorOpenedCount
                                    == R2ExternalNaturalEmitTimeoutProjectFixture
                                            .PARSE_TOTAL_FILE_COUNT
                            && cleanupReceipt.cursorClosedCount
                                    == cleanupReceipt.cursorOpenedCount,
                    "Natural parse successful cleanup cursor authority drifted.");
            NaturalParseTiming timing = phaseReceipt.parseTiming();
            Log.i(
                    LOG_TAG,
                    event
                            + " run="
                            + run
                            + " requestId="
                            + requestId
                            + " hostRequestId="
                            + phaseReceipt.hostRequestId
                            + " projectStartMs="
                            + TimeUnit.NANOSECONDS.toMillis(
                                    phaseReceipt
                                            .projectStartNanosSinceRequestStart)
                            + " parseSequenceTwoMs="
                            + TimeUnit.NANOSECONDS.toMillis(
                                    timing.sequenceTwoNanosSinceRequestStart)
                            + " parseCompletedMs="
                            + TimeUnit.NANOSECONDS.toMillis(
                                    timing.completedNanosSinceRequestStart)
                            + " files="
                            + phaseReceipt.input.openedCursorCount
                            + " frames="
                            + phaseReceipt.input.sentFrameCount
                            + " outputBytes="
                            + fixture.expectedOutputAuthority.totalBytes
                            + " outputSha256="
                            + fixture.expectedOutputAuthority.aggregateSha256);
            return timing;
        }
    }

    private static NaturalParseCalibration calibrateNaturalParseDeadline(
            List<NaturalParseTiming> baselineTimings, String passEvent) {
        require(
                baselineTimings.size() == NATURAL_PARSE_BASELINE_RUNS,
                "Natural parse calibration did not contain three baselines.");
        long minimumTimeoutMillis = 0L;
        long maximumTimeoutMillis = Long.MAX_VALUE;
        for (NaturalParseTiming timing : baselineTimings) {
            long sampleMinimum =
                    TimeUnit.NANOSECONDS.toMillis(
                                    timing.sequenceTwoNanosSinceRequestStart)
                            + 1L;
            long sampleMaximum =
                    (timing.completedNanosSinceRequestStart - 1L)
                            / TimeUnit.MILLISECONDS.toNanos(1L);
            if (sampleMinimum > minimumTimeoutMillis) {
                minimumTimeoutMillis = sampleMinimum;
            }
            if (sampleMaximum < maximumTimeoutMillis) {
                maximumTimeoutMillis = sampleMaximum;
            }
        }
        require(
                maximumTimeoutMillis - minimumTimeoutMillis
                        >= NATURAL_PARSE_MINIMUM_MARGIN_MS * 2L,
                "Natural parse baselines have no strict two-sided deadline window: "
                        + minimumTimeoutMillis
                        + ".."
                        + maximumTimeoutMillis
                        + " ms; faultAttempts=0.");
        long timeoutMillis =
                minimumTimeoutMillis
                        + (maximumTimeoutMillis - minimumTimeoutMillis) / 2L;
        require(
                timeoutMillis
                        >= TypeScriptR2Contract
                                .MIN_PUBLIC_COMPILATION_TIMEOUT_MS,
                "Natural parse strict midpoint is below the public timeout minimum; "
                        + "faultAttempts=0.");
        require(
                timeoutMillis - minimumTimeoutMillis
                                >= NATURAL_PARSE_MINIMUM_MARGIN_MS
                        && maximumTimeoutMillis - timeoutMillis
                                >= NATURAL_PARSE_MINIMUM_MARGIN_MS,
                "Natural parse strict midpoint lost a two-sided margin.");
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        for (NaturalParseTiming timing : baselineTimings) {
            require(
                    timing.sequenceTwoNanosSinceRequestStart
                                    < timeoutNanos
                            && timeoutNanos
                                    < timing.completedNanosSinceRequestStart,
                    "Natural parse midpoint is outside a baseline interval.");
        }
        Log.i(
                LOG_TAG,
                passEvent
                        + " lowerMs="
                        + minimumTimeoutMillis
                        + " upperMs="
                        + maximumTimeoutMillis
                        + " timeoutMs="
                        + timeoutMillis
                        + " leftMarginMs="
                        + (timeoutMillis - minimumTimeoutMillis)
                        + " rightMarginMs="
                        + (maximumTimeoutMillis - timeoutMillis)
                        + " publicMinimumMs="
                        + TypeScriptR2Contract
                                .MIN_PUBLIC_COMPILATION_TIMEOUT_MS
                        + " strictIntersection=true faultAttempts=0");
        return new NaturalParseCalibration(
                minimumTimeoutMillis,
                maximumTimeoutMillis,
                timeoutMillis);
    }

    private static void runExternalNaturalParseFault(
            Context context,
            CleanupEvidenceSession session,
            NaturalParseCalibration calibration)
            throws Exception {
        require(
                NATURAL_PARSE_FAULT_ATTEMPTS == 1,
                "Natural parse fault must be attempted exactly once.");
        String requestId =
                newRequestId("external-natural-parse-timeout-fault");
        R2FenceBoundedProjectFixture.CompilerAuthority authority =
                session.metadata.toFixtureAuthority();
        try (R2ExternalNaturalEmitTimeoutProjectFixture fixture =
                R2ExternalNaturalEmitTimeoutProjectFixture.createParse(
                        context, requestId, authority);
                ParcelFileDescriptor input =
                        ParcelFileDescriptor.open(
                                fixture.input,
                                ParcelFileDescriptor.MODE_READ_ONLY);
                ParcelFileDescriptor output =
                        ParcelFileDescriptor.open(
                                fixture.output,
                                ParcelFileDescriptor.MODE_READ_WRITE)) {
            RecordingCallback callback = new RecordingCallback();
            long wallStartedAtNanos = System.nanoTime();
            Admission admission =
                    admitNaturalParseOnce(
                            session,
                            input,
                            output,
                            callback,
                            requestId,
                            fixture.projectId,
                            fixture.entrySourceUri,
                            fixture.inputArchiveSha256,
                            fixture.requestManifestSha256,
                            calibration.timeoutMillis);
            CleanupCheckpoint checkpoint =
                    assertPreAcknowledgementCleanupTelemetry(
                            session,
                            admission,
                            input,
                            output);
            acknowledgeAdmission(admission);
            Bundle terminal = callback.awaitTerminal(CALLBACK_WAIT_MS);
            long wallElapsedMillis =
                    TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - wallStartedAtNanos);
            SystemClock.sleep(CALLBACK_QUIET_MS);
            callback.assertExactlyOneCompletion();
            assertTerminalRequestId(terminal, requestId);
            require(
                    !terminal.getBoolean(
                            TypeScriptCompilerContract.KEY_SUCCEEDED, true),
                    "Natural parse fault unexpectedly succeeded.");
            require(
                    TypeScriptCompilerContract.OUTCOME_TIMED_OUT.equals(
                            terminal.getString(
                                    TypeScriptCompilerContract.KEY_OUTCOME)),
                    "Natural parse fault did not time out.");
            require(
                    TypeScriptCompilerContract.ERROR_TIMEOUT.equals(
                            terminal.getString(
                                    TypeScriptCompilerContract
                                            .KEY_ERROR_CODE)),
                    "Natural parse fault returned the wrong error.");
            require(
                    wallElapsedMillis >= calibration.timeoutMillis,
                    "Natural parse fault preceded its request-start deadline.");
            assertNoCompilationPayload(
                    terminal, "External natural parse timeout");
            assertSyncedEmptyOutput(
                    output,
                    fixture.output,
                    "External natural parse timeout");

            NaturalPhaseReceipt phaseReceipt =
                    requireNaturalPhaseReceipt(
                            session, requestId, "timed_out");
            assertTimedOutNaturalParsePhaseReceipt(
                    phaseReceipt,
                    fixture,
                    calibration);
            CleanupReceipt cleanupReceipt =
                    awaitCleanupEvidence(
                            session,
                            checkpoint,
                            TypeScriptCompilerContract.OUTCOME_TIMED_OUT,
                            TypeScriptCompilerContract.ERROR_TIMEOUT,
                            false);
            require(
                    cleanupReceipt != null
                            && cleanupReceipt.cursorOpenedCount
                                    == R2ExternalNaturalEmitTimeoutProjectFixture
                                            .PARSE_TOTAL_FILE_COUNT
                            && cleanupReceipt.cursorClosedCount
                                    == cleanupReceipt.cursorOpenedCount,
                    "Natural parse fault cleanup cursor authority drifted.");
            NaturalPhaseTermination termination = phaseReceipt.termination;
            Log.i(
                    LOG_TAG,
                    "EXTERNAL_NATURAL_PARSE_TIMEOUT_FAULT_PASS"
                            + " faultAttempts=1 requestId="
                            + requestId
                            + " hostRequestId="
                            + phaseReceipt.hostRequestId
                            + " timeoutMs="
                            + calibration.timeoutMillis
                            + " wallElapsedMs="
                            + wallElapsedMillis
                            + " phase=parse progressSequence="
                            + termination.progressSequence
                            + " completedUnits="
                            + termination.progressCompletedUnits
                            + " totalUnits="
                            + termination.progressTotalUnits
                            + " deadlineOvershootMs="
                            + TimeUnit.NANOSECONDS.toMillis(
                                    termination.deadlineOvershootNanos)
                            + " openedCursors="
                            + phaseReceipt.input.openedCursorCount
                            + " closedCursors="
                            + phaseReceipt.input.closedCursorCount
                            + " terminatedWorkerGeneration="
                            + termination.terminatedWorkerGeneration
                            + " replacementWorkerGeneration="
                            + termination.replacementWorkerGeneration
                            + " outputBytes="
                            + fixture.output.length()
                            + " callbackBeforeReceiptRead=true"
                            + " receiptCreatedBeforeCallback=true");
        }
    }

    private static void assertTimedOutNaturalParsePhaseReceipt(
            NaturalPhaseReceipt receipt,
            R2ExternalNaturalEmitTimeoutProjectFixture fixture,
            NaturalParseCalibration calibration) {
        require(
                fixture.isNaturalParseWorkload(),
                "Natural parse termination used the wrong fixture workload.");
        require(
                Arrays.equals(
                        receipt.phases,
                        new String[0]),
                "Timed-out natural phase receipt completed parse.");
        assertNaturalPhaseInput(receipt.input, fixture);
        NaturalPhaseTermination termination = receipt.termination;
        require(termination != null, "Timed-out natural phase receipt lost termination.");
        require(
                "TIMED_OUT".equals(termination.stopCause)
                        && "parse".equals(termination.hostStopPhase)
                        && "parse".equals(termination.pageTerminationPhase),
                "Natural parse termination phase/cause drifted.");
        require(
                Arrays.equals(
                                termination.enteredPhases,
                                new String[] {"ingress", "parse"})
                        && Arrays.equals(
                                termination.completedPhases,
                                new String[] {"ingress"}),
                "Natural parse termination phase histories drifted.");
        require(
                termination.progressSequence == 2
                        && termination.progressCompletedUnits
                                == (R2ExternalNaturalEmitTimeoutProjectFixture
                                                .PARSE_ROOT_COUNT
                                        - 1)
                                        / 4
                                        + 1
                        && termination.progressTotalUnits
                                == R2ExternalNaturalEmitTimeoutProjectFixture
                                        .PARSE_ROOT_COUNT,
                "Natural parse sequence-two termination progress drifted.");
        require(
                termination.workerGeneration
                                == termination.terminatedWorkerGeneration
                        && termination.terminatedWorkerGeneration > 0
                        && termination.replacementWorkerGeneration
                                == termination.terminatedWorkerGeneration + 1,
                "Natural parse exact Worker replacement drifted.");
        require(
                termination.sessionGeneration > 0,
                "Natural parse termination session generation is invalid.");
        long requestedDeadlineNanos =
                TimeUnit.MILLISECONDS.toNanos(calibration.timeoutMillis);
        long deadlineDifferenceNanos =
                Math.abs(
                        termination.deadlineNanosSinceRequestStart
                                - requestedDeadlineNanos);
        require(
                deadlineDifferenceNanos
                        <= TimeUnit.MILLISECONDS.toNanos(250L),
                "Natural parse host deadline drifted from Binder request start.");
        require(
                termination.deadlineOvershootNanos
                        <= TimeUnit.MILLISECONDS.toNanos(
                                NATURAL_PARSE_MAXIMUM_OVERSHOOT_MS),
                "Natural parse deadline selection overshoot was unbounded.");
    }

    /**
     * One irreversible public admission attempt used by every natural parse run.
     */
    private static Admission admitNaturalParseOnce(
            CleanupEvidenceSession session,
            ParcelFileDescriptor input,
            ParcelFileDescriptor output,
            RecordingCallback callback,
            String requestId,
            String projectId,
            String entrySourceUri,
            String inputArchiveSha256,
            String requestManifestSha256,
            long timeoutMs)
            throws Exception {
        requireLiveSameBinder(session);
        require(
                timeoutMs
                        >= TypeScriptR2Contract
                                .MIN_PUBLIC_COMPILATION_TIMEOUT_MS,
                "Natural parse timeout is below the public minimum.");
        require(
                timeoutMs <= TypeScriptCompilerContract.MAX_TIMEOUT_MS,
                "Natural parse timeout is above the public maximum.");
        ITypeScriptCompilerPlugin compiler = session.bound.compiler;
        CompilerMetadata metadata =
                CompilerMetadata.from(compiler.getCompilerInfo());
        require(
                metadata != null,
                "Same-Binder natural parse compiler metadata became unavailable.");
        metadata.requireMatches(session.metadata.toFixtureAuthority());
        Bundle ticket =
                compiler.startCompilation(
                        publicRequest(
                                requestId,
                                projectId,
                                entrySourceUri,
                                metadata,
                                inputArchiveSha256,
                                requestManifestSha256,
                                null,
                                timeoutMs),
                        input,
                        output,
                        callback);
        require(ticket != null, "Compiler returned a null natural parse ticket.");
        require(
                requestId.equals(
                        ticket.getString(
                                TypeScriptCompilerContract.KEY_REQUEST_ID)),
                "Natural parse admission ticket lost the request ID.");
        require(
                ticket.getBoolean(
                        TypeScriptCompilerContract.KEY_ACCEPTED, false),
                "Natural parse request was rejected without retry: "
                        + ticket.getString(
                                TypeScriptCompilerContract.KEY_ERROR_CODE)
                        + ": "
                        + ticket.getString(
                                TypeScriptCompilerContract.KEY_ERROR_MESSAGE));
        return new Admission(
                session.bound,
                compiler,
                ticket,
                requestId,
                metadata);
    }


    private void runExternalPlusOneCleanupEvidence(
            boolean runExternalCapacityEvidence) throws Exception {
        Context context = getTargetContext();
        require(
                FIXTURE_PACKAGE.equals(context.getPackageName()),
                "Instrumentation did not target the standalone fixture package.");

        PackageManager packageManager = context.getPackageManager();
        ApplicationInfo currentApplication =
                packageManager.getApplicationInfo(CURRENT_PACKAGE, 0);
        require(
                Process.myUid() != currentApplication.uid,
                "Fixture unexpectedly shares the compiler plugin UID.");
        require(
                packageManager.checkSignatures(FIXTURE_PACKAGE, CURRENT_PACKAGE)
                        == PackageManager.SIGNATURE_MATCH,
                "Fixture and compiler plugin do not have the same signer.");
        PermissionInfo pluginPermission =
                packageManager.getPermissionInfo(PLUGIN_PERMISSION, 0);
        require(
                (pluginPermission.protectionLevel
                                & PermissionInfo.PROTECTION_MASK_BASE)
                        == PermissionInfo.PROTECTION_SIGNATURE,
                "Compiler service permission is not signature-protected.");
        require(
                packageManager.checkSignatures(
                                pluginPermission.packageName, CURRENT_PACKAGE)
                        == PackageManager.SIGNATURE_MATCH,
                "Compiler service permission owner does not share the plugin signer.");

        Intent discovery =
                new Intent(ACTION_COMPILER)
                        .addCategory(CATEGORY_TYPESCRIPT)
                        .setPackage(CURRENT_PACKAGE);
        List<ResolveInfo> matches =
                packageManager.queryIntentServices(discovery, 0);
        require(matches.size() == 1, "Expected exactly one current compiler service.");
        ServiceInfo serviceInfo = matches.get(0).serviceInfo;
        require(serviceInfo.exported, "Current compiler service is not exported.");
        require(
                PLUGIN_PERMISSION.equals(serviceInfo.permission),
                "Current compiler service permission drifted.");
        require(
                serviceInfo.applicationInfo.uid == currentApplication.uid,
                "Resolved compiler service belongs to an unexpected UID.");
        ComponentName component =
                new ComponentName(serviceInfo.packageName, serviceInfo.name);
        Context pluginContext =
                context.createPackageContext(CURRENT_PACKAGE, 0);

        try (CleanupEvidenceSession session =
                awaitCleanupEvidenceSession(
                        context,
                        discovery,
                        component,
                        runExternalCapacityEvidence)) {
            runFencePlusOneCleanupCase(
                    context,
                    pluginContext,
                    session,
                    "fence-plus-one-cleanup");
            runFenceInclusiveCleanupSuccessor(
                    context,
                    pluginContext,
                    session,
                    "fence-plus-one-cleanup");
            if (runExternalCapacityEvidence) {
                runExternalCapacityPlusOneCleanupCase(
                        context,
                        session,
                        R2ExternalCapacityProjectFixture.Boundary
                                .STORED_CONTAINER_PLUS_ONE);
                runFenceInclusiveCleanupSuccessor(
                        context,
                        pluginContext,
                        session,
                        "stored-container-plus-one-cleanup");
                runExternalCapacityPlusOneCleanupCase(
                        context,
                        session,
                        R2ExternalCapacityProjectFixture.Boundary
                                .DEFLATED_UNCOMPRESSED_PLUS_ONE);
                runFenceInclusiveCleanupSuccessor(
                        context,
                        pluginContext,
                        session,
                        "deflated-expanded-plus-one-cleanup");
            }
            requireLiveSameBinder(session);
            Log.i(
                    LOG_TAG,
                    "event=external-plus-one-cleanup-pass"
                            + " capacityEvidence="
                            + runExternalCapacityEvidence
                            + " telemetryRequired="
                            + session.telemetryRequired
                            + (session.identity == null
                                    ? ""
                                    : " processPid="
                                            + session.identity.pid
                                            + " processStartTicks="
                                            + session.identity.startTicks
                                            + " processName="
                                            + session.identity.processName)
                            + " zeroSamples="
                            + (session.telemetryRequired
                                    ? REQUIRED_CONSECUTIVE_CLEANUP_SAMPLES
                                    : 0));
        }
    }

    private static CleanupEvidenceSession awaitCleanupEvidenceSession(
            Context context,
            Intent discovery,
            ComponentName component,
            boolean requireTelemetry)
            throws Exception {
        long deadline = SystemClock.elapsedRealtime() + ADMISSION_WAIT_MS;
        String lastTransient = "compiler metadata was not yet available";
        while (SystemClock.elapsedRealtime() < deadline) {
            BoundCompiler bound = null;
            boolean retained = false;
            try {
                bound = bind(context, discovery, component, deadline);
                require(
                        bound.compiler.getContractVersion()
                                == TypeScriptCompilerContract.VERSION,
                        "Compiler contract version mismatch.");
                require(
                        TypeScriptCompilerContract.HASH.equals(
                                bound.compiler.getContractHash()),
                        "Compiler contract hash mismatch.");
                while (SystemClock.elapsedRealtime() < deadline
                        && bound.rawBinder.isBinderAlive()
                        && bound.rawBinder.pingBinder()) {
                    Bundle info = bound.compiler.getCompilerInfo();
                    CompilerMetadata metadata = CompilerMetadata.from(info);
                    boolean telemetryEnabled =
                            !requireTelemetry
                                    || cleanupTelemetryEnabled(info);
                    if (metadata != null && telemetryEnabled) {
                        retained = true;
                        return new CleanupEvidenceSession(
                                bound,
                                metadata,
                                requireTelemetry,
                                null);
                    }
                    lastTransient =
                            metadata == null
                                    ? "compilerInfo fingerprints were not ready"
                                    : "cleanup telemetry was not enabled";
                    long remaining =
                            deadline - SystemClock.elapsedRealtime();
                    if (remaining > 0L) {
                        SystemClock.sleep(
                                Math.min(RETRY_DELAY_MS, remaining));
                    }
                }
                lastTransient = "compiler Binder stopped responding";
            } catch (RetryableBindingException | RemoteException error) {
                lastTransient = error.toString();
            } finally {
                if (bound != null && !retained) {
                    bound.close();
                }
            }
        }
        throw new AssertionError(
                "Timed out waiting for cleanup-evidence compiler readiness: "
                        + lastTransient);
    }

    private static void runFencePlusOneCleanupCase(
            Context context,
            Context pluginContext,
            CleanupEvidenceSession session,
            String label)
            throws Exception {
        long startedAt = SystemClock.elapsedRealtime();
        String requestId = newRequestId(label);
        try (R2FenceBoundedProjectFixture fixture =
                R2FenceBoundedProjectFixture.create(
                        context,
                        pluginContext,
                        requestId,
                        session.metadata.toFixtureAuthority(),
                        true)) {
            require(
                    fixture.callerSourceBytes == 4_603_851L,
                    "Plus-one caller source boundary drifted.");
            File outputFile = null;
            Admission admission = null;
            boolean terminalReceived = false;
            RecordingCallback callback = new RecordingCallback();
            try {
                outputFile =
                        File.createTempFile(
                                "r2-public-preview-fence-plus-one-cleanup-output-",
                                ".archive",
                                context.getFilesDir());
                writeAndSync(outputFile, OUTPUT_SENTINEL);
                try (ParcelFileDescriptor input =
                                ParcelFileDescriptor.open(
                                        fixture.input,
                                        ParcelFileDescriptor.MODE_READ_ONLY);
                        ParcelFileDescriptor output =
                                ParcelFileDescriptor.open(
                                        outputFile,
                                        ParcelFileDescriptor.MODE_READ_WRITE)) {
                    admission =
                            admitOnBoundCompiler(
                                    session,
                                    input,
                                    output,
                                    callback,
                                    requestId,
                                    R2FenceBoundedProjectFixture.PROJECT_ID,
                                    R2FenceBoundedProjectFixture.ENTRY_SOURCE_URI,
                                    fixture.inputArchiveSha256,
                                    fixture.requestManifestSha256);
                    CleanupCheckpoint checkpoint =
                            assertPreAcknowledgementCleanupTelemetry(
                                    session,
                                    admission,
                                    input,
                                    output);
                    acknowledgeAdmission(admission);
                    Bundle terminal =
                            callback.awaitTerminal(CALLBACK_WAIT_MS);
                    terminalReceived = true;
                    SystemClock.sleep(CALLBACK_QUIET_MS);
                    callback.assertExactlyOneCompletion();
                    assertTerminalRequestId(terminal, requestId);
                    require(
                            !terminal.getBoolean(
                                    TypeScriptCompilerContract.KEY_SUCCEEDED,
                                    true),
                            "Fence-plus-one cleanup project unexpectedly succeeded.");
                    require(
                            TypeScriptCompilerContract.OUTCOME_FAILED.equals(
                                    terminal.getString(
                                            TypeScriptCompilerContract.KEY_OUTCOME)),
                            "Fence-plus-one cleanup project did not fail.");
                    require(
                            TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA.equals(
                                    terminal.getString(
                                            TypeScriptCompilerContract
                                                    .KEY_ERROR_CODE)),
                            "Fence-plus-one cleanup project did not reach quota.");
                    assertNoCompilationPayload(
                            terminal, "Fence-plus-one cleanup project");
                    assertSyncedEmptyOutput(
                            output,
                            outputFile,
                            "Fence-plus-one cleanup terminal");
                    awaitCleanupEvidence(
                            session,
                            checkpoint,
                            TypeScriptCompilerContract.OUTCOME_FAILED,
                            TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                            true);
                    logCleanupEvidence(
                            requestId,
                            "production-fence-plus-one",
                            TypeScriptCompilerContract.OUTCOME_FAILED,
                            fixture.callerSourceBytes,
                            outputFile.length(),
                            startedAt,
                            session);
                } finally {
                    if (admission != null && !terminalReceived) {
                        admission.bestEffortCancel();
                    }
                }
            } finally {
                deleteFixtureFiles(
                        new File[] {outputFile},
                        new String[] {
                            "fence-plus-one cleanup output fixture"
                        });
            }
        }
    }

    private static boolean cleanupTelemetryEnabled(Bundle info) {
        require(
                info != null,
                "Cleanup evidence compilerInfo was null.");
        require(
                info.containsKey(CLEANUP_KEY_ENABLED),
                "Cleanup evidence compilerInfo omitted its enabled marker.");
        return info.getBoolean(CLEANUP_KEY_ENABLED, false);
    }

    private static void runExternalCapacityPlusOneCleanupCase(
            Context context,
            CleanupEvidenceSession session,
            R2ExternalCapacityProjectFixture.Boundary boundary)
            throws Exception {
        require(
                boundary.isPlusOne(),
                "Cleanup runner accepted a non-plus-one external capacity boundary.");
        long startedAt = SystemClock.elapsedRealtime();
        String requestId =
                newRequestId(
                        "external-capacity-"
                                + boundary.name()
                                + "-cleanup");
        try (R2ExternalCapacityProjectFixture fixture =
                R2ExternalCapacityProjectFixture.create(
                        context,
                        requestId,
                        boundary,
                        session.metadata.toFixtureAuthority())) {
            if (boundary.compression
                    == R2ExternalCapacityProjectFixture.Compression.STORED) {
                require(
                        fixture.containerBytes
                                == TypeScriptCompilerContract
                                                .MAX_INPUT_ARCHIVE_BYTES
                                        + 1L,
                        "STORED plus-one cleanup boundary drifted.");
            } else {
                require(
                        fixture.uncompressedBytes
                                == TypeScriptCompilerContract.MAX_INPUT_BYTES
                                        + 1L,
                        "DEFLATED plus-one cleanup boundary drifted.");
                require(
                        fixture.containerBytes
                                <= TypeScriptCompilerContract
                                        .MAX_INPUT_ARCHIVE_BYTES,
                        "DEFLATED plus-one cleanup fixture exceeds the container limit.");
            }

            File outputFile = null;
            Admission admission = null;
            boolean terminalReceived = false;
            RecordingCallback callback = new RecordingCallback();
            try {
                outputFile =
                        File.createTempFile(
                                "r2-public-preview-external-plus-one-cleanup-output-",
                                ".archive",
                                context.getFilesDir());
                writeAndSync(outputFile, OUTPUT_SENTINEL);
                try (ParcelFileDescriptor input =
                                ParcelFileDescriptor.open(
                                        fixture.input,
                                        ParcelFileDescriptor.MODE_READ_ONLY);
                        ParcelFileDescriptor output =
                                ParcelFileDescriptor.open(
                                        outputFile,
                                        ParcelFileDescriptor.MODE_READ_WRITE)) {
                    admission =
                            admitOnBoundCompiler(
                                    session,
                                    input,
                                    output,
                                    callback,
                                    requestId,
                                    fixture.projectId,
                                    fixture.entrySourceUri,
                                    fixture.inputArchiveSha256,
                                    fixture.requestManifestSha256);
                    CleanupCheckpoint checkpoint =
                            assertPreAcknowledgementCleanupTelemetry(
                                    session,
                                    admission,
                                    input,
                                    output);
                    acknowledgeAdmission(admission);
                    Bundle terminal =
                            callback.awaitTerminal(CALLBACK_WAIT_MS);
                    terminalReceived = true;
                    SystemClock.sleep(CALLBACK_QUIET_MS);
                    callback.assertExactlyOneCompletion();
                    assertTerminalRequestId(terminal, requestId);
                    require(
                            !terminal.getBoolean(
                                    TypeScriptCompilerContract.KEY_SUCCEEDED,
                                    true),
                            "External capacity plus-one cleanup unexpectedly succeeded.");
                    require(
                            TypeScriptCompilerContract.OUTCOME_FAILED.equals(
                                    terminal.getString(
                                            TypeScriptCompilerContract.KEY_OUTCOME)),
                            "External capacity plus-one cleanup did not fail.");
                    require(
                            TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA.equals(
                                    terminal.getString(
                                            TypeScriptCompilerContract
                                                    .KEY_ERROR_CODE)),
                            "External capacity plus-one cleanup did not reach quota.");
                    assertNoCompilationPayload(
                            terminal,
                            "External capacity plus-one cleanup "
                                    + boundary.name());
                    assertSyncedEmptyOutput(
                            output,
                            outputFile,
                            "External capacity plus-one cleanup terminal");
                    awaitCleanupEvidence(
                            session,
                            checkpoint,
                            TypeScriptCompilerContract.OUTCOME_FAILED,
                            TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                            true);
                    logCleanupEvidence(
                            requestId,
                            boundary.name(),
                            TypeScriptCompilerContract.OUTCOME_FAILED,
                            boundary.compression
                                            == R2ExternalCapacityProjectFixture
                                                    .Compression.STORED
                                    ? fixture.containerBytes
                                    : fixture.uncompressedBytes,
                            outputFile.length(),
                            startedAt,
                            session);
                } finally {
                    if (admission != null && !terminalReceived) {
                        admission.bestEffortCancel();
                    }
                }
            } finally {
                deleteFixtureFiles(
                        new File[] {outputFile},
                        new String[] {
                            "external plus-one cleanup output fixture"
                        });
            }
        }
    }

    private static void runFenceInclusiveCleanupSuccessor(
            Context context,
            Context pluginContext,
            CleanupEvidenceSession session,
            String predecessorLabel)
            throws Exception {
        long startedAt = SystemClock.elapsedRealtime();
        String requestId =
                newRequestId(
                        "fence-inclusive-successor-"
                                + predecessorLabel);
        R2FenceBoundedProjectFixture.CompilerAuthority authority =
                session.metadata.toFixtureAuthority();
        try (R2FenceBoundedProjectFixture fixture =
                R2FenceBoundedProjectFixture.create(
                        context,
                        pluginContext,
                        requestId,
                        authority,
                        false)) {
            require(
                    fixture.callerSourceBytes == 4_603_850L,
                    "Inclusive cleanup successor boundary drifted.");
            File outputFile = null;
            Admission admission = null;
            boolean terminalReceived = false;
            RecordingCallback callback = new RecordingCallback();
            try {
                outputFile =
                        File.createTempFile(
                                "r2-public-preview-fence-successor-output-",
                                ".archive",
                                context.getFilesDir());
                writeAndSync(outputFile, OUTPUT_SENTINEL);
                try (ParcelFileDescriptor input =
                                ParcelFileDescriptor.open(
                                        fixture.input,
                                        ParcelFileDescriptor.MODE_READ_ONLY);
                        ParcelFileDescriptor output =
                                ParcelFileDescriptor.open(
                                        outputFile,
                                        ParcelFileDescriptor.MODE_READ_WRITE)) {
                    admission =
                            admitOnBoundCompiler(
                                    session,
                                    input,
                                    output,
                                    callback,
                                    requestId,
                                    R2FenceBoundedProjectFixture.PROJECT_ID,
                                    R2FenceBoundedProjectFixture.ENTRY_SOURCE_URI,
                                    fixture.inputArchiveSha256,
                                    fixture.requestManifestSha256);
                    CleanupCheckpoint checkpoint =
                            assertPreAcknowledgementCleanupTelemetry(
                                    session,
                                    admission,
                                    input,
                                    output);
                    acknowledgeAdmission(admission);
                    Bundle terminal =
                            callback.awaitTerminal(CALLBACK_WAIT_MS);
                    terminalReceived = true;
                    SystemClock.sleep(CALLBACK_QUIET_MS);
                    callback.assertExactlyOneCompletion();
                    assertTerminalRequestId(terminal, requestId);
                    require(
                            terminal.getBoolean(
                                    TypeScriptCompilerContract.KEY_SUCCEEDED,
                                    false),
                            "Fence-inclusive cleanup successor did not succeed.");
                    require(
                            TypeScriptCompilerContract.OUTCOME_EMITTED.equals(
                                    terminal.getString(
                                            TypeScriptCompilerContract.KEY_OUTCOME)),
                            "Fence-inclusive cleanup successor did not emit.");
                    require(
                            terminal.getInt(
                                            TypeScriptCompilerContract
                                                    .KEY_DIAGNOSTIC_COUNT,
                                            -1)
                                    == 0,
                            "Fence-inclusive cleanup successor returned diagnostics.");
                    require(
                            terminal.getInt(
                                            TypeScriptCompilerContract
                                                    .KEY_EMITTED_MODULE_COUNT,
                                            -1)
                                    == 1,
                            "Fence-inclusive cleanup successor emitted an unexpected module count.");
                    Os.fsync(output.getFileDescriptor());
                    require(
                            outputFile.length() > 0L,
                            "Fence-inclusive cleanup successor left empty output.");
                    verifyEmittedOutput(
                            outputFile, fixture, authority, terminal);
                    awaitCleanupEvidence(
                            session,
                            checkpoint,
                            TypeScriptCompilerContract.OUTCOME_EMITTED,
                            null,
                            false);
                    logCleanupEvidence(
                            requestId,
                            "ordinary-inclusive-successor",
                            TypeScriptCompilerContract.OUTCOME_EMITTED,
                            fixture.callerSourceBytes,
                            outputFile.length(),
                            startedAt,
                            session);
                } finally {
                    if (admission != null && !terminalReceived) {
                        admission.bestEffortCancel();
                    }
                }
            } finally {
                deleteFixtureFiles(
                        new File[] {outputFile},
                        new String[] {
                            "fence-inclusive cleanup successor output fixture"
                        });
            }
        }
    }

    private static CompilerMetadata runInvalidArchiveCase(
            Context context, Intent discovery, ComponentName component)
            throws Exception {
        long startedAt = SystemClock.elapsedRealtime();
        File inputFile = null;
        File outputFile = null;
        RecordingCallback callback = new RecordingCallback();
        Admission admission = null;
        boolean terminalReceived = false;
        String requestId = newRequestId("invalid-zip");
        try {
            inputFile =
                    File.createTempFile(
                            "r2-public-preview-input-",
                            ".archive",
                            context.getFilesDir());
            outputFile =
                    File.createTempFile(
                            "r2-public-preview-output-",
                            ".archive",
                            context.getFilesDir());
            writeAndSync(inputFile, INVALID_ZIP_BYTES);
            writeAndSync(outputFile, OUTPUT_SENTINEL);
            try (ParcelFileDescriptor input =
                            ParcelFileDescriptor.open(
                                    inputFile,
                                    ParcelFileDescriptor.MODE_READ_ONLY);
                    ParcelFileDescriptor output =
                            ParcelFileDescriptor.open(
                                    outputFile,
                                    ParcelFileDescriptor.MODE_READ_WRITE)) {
                admission =
                        awaitAdmission(
                                context,
                                discovery,
                                component,
                                input,
                                output,
                                callback,
                                requestId,
                                INVALID_PROJECT_ID,
                                INVALID_ENTRY_SOURCE_URI,
                                sha256(INVALID_ZIP_BYTES),
                                sha256(ABSENT_MANIFEST_BYTES),
                                null);
                CompilerMetadata metadata = admission.metadata;
                acknowledgeAdmission(admission);

                Bundle terminal = callback.awaitTerminal(CALLBACK_WAIT_MS);
                terminalReceived = true;
                SystemClock.sleep(CALLBACK_QUIET_MS);
                callback.assertExactlyOneCompletion();
                assertTerminalRequestId(terminal, requestId);
                require(
                        !terminal.getBoolean(
                                TypeScriptCompilerContract.KEY_SUCCEEDED, true),
                        "Invalid ZIP unexpectedly succeeded.");
                require(
                        TypeScriptCompilerContract.OUTCOME_FAILED.equals(
                                terminal.getString(
                                        TypeScriptCompilerContract.KEY_OUTCOME)),
                        "Invalid ZIP did not produce a failed terminal outcome.");
                require(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_FORMAT.equals(
                                terminal.getString(
                                        TypeScriptCompilerContract.KEY_ERROR_CODE)),
                        "Correctly hashed invalid ZIP did not reach archive-format validation.");
                assertNoCompilationPayload(terminal, "Invalid ZIP");
                assertSyncedEmptyOutput(
                        output, outputFile, "Invalid ZIP terminal");
                logCaseEvidence(
                        requestId,
                        TypeScriptCompilerContract.OUTCOME_FAILED,
                        INVALID_ZIP_BYTES.length,
                        outputFile.length(),
                        startedAt);
                return metadata;
            } finally {
                closeAdmission(admission, terminalReceived);
            }
        } finally {
            deleteFixtureFiles(
                    new File[] {inputFile, outputFile},
                    new String[] {
                        "invalid-ZIP input fixture",
                        "invalid-ZIP output fixture"
                    });
        }
    }

    private static void runFenceInclusiveCase(
            Context context,
            Context pluginContext,
            Intent discovery,
            ComponentName component,
            R2FenceBoundedProjectFixture.CompilerAuthority authority)
            throws Exception {
        long startedAt = SystemClock.elapsedRealtime();
        String requestId = newRequestId("fence-inclusive");
        try (R2FenceBoundedProjectFixture fixture =
                R2FenceBoundedProjectFixture.create(
                        context,
                        pluginContext,
                        requestId,
                        authority,
                        false)) {
            require(
                    fixture.callerSourceBytes == 4_603_850L,
                    "Inclusive caller source boundary drifted.");
            File outputFile = null;
            RecordingCallback callback = new RecordingCallback();
            Admission admission = null;
            boolean terminalReceived = false;
            try {
                outputFile =
                        File.createTempFile(
                                "r2-public-preview-fence-inclusive-output-",
                                ".archive",
                                context.getFilesDir());
                writeAndSync(outputFile, OUTPUT_SENTINEL);
                try (ParcelFileDescriptor input =
                                ParcelFileDescriptor.open(
                                        fixture.input,
                                        ParcelFileDescriptor.MODE_READ_ONLY);
                        ParcelFileDescriptor output =
                                ParcelFileDescriptor.open(
                                        outputFile,
                                        ParcelFileDescriptor.MODE_READ_WRITE)) {
                    admission =
                            awaitAdmission(
                                    context,
                                    discovery,
                                    component,
                                    input,
                                    output,
                                    callback,
                                    requestId,
                                    R2FenceBoundedProjectFixture.PROJECT_ID,
                                    R2FenceBoundedProjectFixture.ENTRY_SOURCE_URI,
                                    fixture.inputArchiveSha256,
                                    fixture.requestManifestSha256,
                                    authority);
                    acknowledgeAdmission(admission);
                    Bundle terminal =
                            callback.awaitTerminal(CALLBACK_WAIT_MS);
                    terminalReceived = true;
                    SystemClock.sleep(CALLBACK_QUIET_MS);
                    callback.assertExactlyOneCompletion();
                    assertTerminalRequestId(terminal, requestId);
                    require(
                            terminal.getBoolean(
                                    TypeScriptCompilerContract.KEY_SUCCEEDED,
                                    false),
                            "Fence-inclusive project did not succeed.");
                    require(
                            TypeScriptCompilerContract.OUTCOME_EMITTED.equals(
                                    terminal.getString(
                                            TypeScriptCompilerContract.KEY_OUTCOME)),
                            "Fence-inclusive project did not emit.");
                    require(
                            terminal.getInt(
                                            TypeScriptCompilerContract
                                                    .KEY_DIAGNOSTIC_COUNT,
                                            -1)
                                    == 0,
                            "Fence-inclusive project returned diagnostics.");
                    require(
                            terminal.getInt(
                                            TypeScriptCompilerContract
                                                    .KEY_EMITTED_MODULE_COUNT,
                                            -1)
                                    == 1,
                            "Fence-inclusive project emitted an unexpected module count.");
                    Os.fsync(output.getFileDescriptor());
                    require(
                            outputFile.length() > 0L,
                            "Fence-inclusive project left empty output.");
                    verifyEmittedOutput(
                            outputFile, fixture, authority, terminal);
                    logCaseEvidence(
                            requestId,
                            TypeScriptCompilerContract.OUTCOME_EMITTED,
                            fixture.callerSourceBytes,
                            outputFile.length(),
                            startedAt);
                } finally {
                    closeAdmission(admission, terminalReceived);
                }
            } finally {
                deleteFixtureFiles(
                        new File[] {outputFile},
                        new String[] {"fence-inclusive output fixture"});
            }
        }
    }

    private static void runFencePlusOneCase(
            Context context,
            Context pluginContext,
            Intent discovery,
            ComponentName component,
            R2FenceBoundedProjectFixture.CompilerAuthority authority)
            throws Exception {
        long startedAt = SystemClock.elapsedRealtime();
        String requestId = newRequestId("fence-plus-one");
        try (R2FenceBoundedProjectFixture fixture =
                R2FenceBoundedProjectFixture.create(
                        context,
                        pluginContext,
                        requestId,
                        authority,
                        true)) {
            require(
                    fixture.callerSourceBytes == 4_603_851L,
                    "Plus-one caller source boundary drifted.");
            File outputFile = null;
            RecordingCallback callback = new RecordingCallback();
            Admission admission = null;
            boolean terminalReceived = false;
            try {
                outputFile =
                        File.createTempFile(
                                "r2-public-preview-fence-plus-one-output-",
                                ".archive",
                                context.getFilesDir());
                writeAndSync(outputFile, OUTPUT_SENTINEL);
                try (ParcelFileDescriptor input =
                                ParcelFileDescriptor.open(
                                        fixture.input,
                                        ParcelFileDescriptor.MODE_READ_ONLY);
                        ParcelFileDescriptor output =
                                ParcelFileDescriptor.open(
                                        outputFile,
                                        ParcelFileDescriptor.MODE_READ_WRITE)) {
                    admission =
                            awaitAdmission(
                                    context,
                                    discovery,
                                    component,
                                    input,
                                    output,
                                    callback,
                                    requestId,
                                    R2FenceBoundedProjectFixture.PROJECT_ID,
                                    R2FenceBoundedProjectFixture.ENTRY_SOURCE_URI,
                                    fixture.inputArchiveSha256,
                                    fixture.requestManifestSha256,
                                    authority);
                    acknowledgeAdmission(admission);
                    Bundle terminal =
                            callback.awaitTerminal(CALLBACK_WAIT_MS);
                    terminalReceived = true;
                    SystemClock.sleep(CALLBACK_QUIET_MS);
                    callback.assertExactlyOneCompletion();
                    assertTerminalRequestId(terminal, requestId);
                    require(
                            !terminal.getBoolean(
                                    TypeScriptCompilerContract.KEY_SUCCEEDED,
                                    true),
                            "Fence-plus-one project unexpectedly succeeded.");
                    require(
                            TypeScriptCompilerContract.OUTCOME_FAILED.equals(
                                    terminal.getString(
                                            TypeScriptCompilerContract.KEY_OUTCOME)),
                            "Fence-plus-one project did not fail.");
                    require(
                            TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA.equals(
                                    terminal.getString(
                                            TypeScriptCompilerContract
                                                    .KEY_ERROR_CODE)),
                            "Fence-plus-one project did not reach the Worker quota fence.");
                    assertNoCompilationPayload(
                            terminal, "Fence-plus-one project");
                    assertSyncedEmptyOutput(
                            output,
                            outputFile,
                            "Fence-plus-one terminal");
                    logCaseEvidence(
                            requestId,
                            TypeScriptCompilerContract.OUTCOME_FAILED,
                            fixture.callerSourceBytes,
                            outputFile.length(),
                            startedAt);
                } finally {
                    closeAdmission(admission, terminalReceived);
                }
            } finally {
                deleteFixtureFiles(
                        new File[] {outputFile},
                        new String[] {"fence-plus-one output fixture"});
            }
        }
    }

    private static void runExternalCapacityBoundaryCase(
            Context context,
            Intent discovery,
            ComponentName component,
            R2FenceBoundedProjectFixture.CompilerAuthority authority,
            R2ExternalCapacityProjectFixture.Boundary boundary)
            throws Exception {
        long startedAt = SystemClock.elapsedRealtime();
        String requestId =
                newRequestId("external-capacity-" + boundary.name());
        try (R2ExternalCapacityProjectFixture fixture =
                R2ExternalCapacityProjectFixture.create(
                        context, requestId, boundary, authority)) {
            long expectedBoundary =
                    TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES
                            + boundary.quotaDeltaBytes;
            if (boundary.compression
                    == R2ExternalCapacityProjectFixture.Compression.STORED) {
                require(
                        fixture.containerBytes == expectedBoundary,
                        "STORED external capacity container boundary drifted.");
            } else {
                require(
                        fixture.uncompressedBytes
                                == TypeScriptCompilerContract.MAX_INPUT_BYTES
                                        + boundary.quotaDeltaBytes,
                        "DEFLATED external capacity expanded boundary drifted.");
                require(
                        fixture.containerBytes
                                <= TypeScriptCompilerContract
                                        .MAX_INPUT_ARCHIVE_BYTES,
                        "DEFLATED external capacity fixture exceeds the container limit.");
            }
            require(
                    fixture.sourceDescriptors.size()
                            <= TypeScriptCompilerContract.MAX_INPUT_FILES,
                    "External capacity source inventory exceeds the contract.");
            for (R2ExternalCapacityProjectFixture.SourceDescriptor descriptor :
                    fixture.sourceDescriptors) {
                require(
                        descriptor.size
                                <= TypeScriptCompilerContract
                                        .MAX_SINGLE_FILE_BYTES,
                        "External capacity source exceeds the single-file limit: "
                                + descriptor.path);
            }

            String evidenceScope = null;
            if (!boundary.isPlusOne()) {
                evidenceScope =
                        boundary.compression
                                        == R2ExternalCapacityProjectFixture
                                                .Compression.STORED
                                ? STORED_CONTAINER_EXACT_64_SCOPE
                                : DEFLATED_EXPANDED_EXACT_64_SCOPE;
            }

            File outputFile = null;
            RecordingCallback callback = new RecordingCallback();
            Admission admission = null;
            boolean terminalReceived = false;
            try {
                outputFile =
                        File.createTempFile(
                                "r2-public-preview-external-capacity-output-",
                                ".archive",
                                context.getFilesDir());
                writeAndSync(outputFile, OUTPUT_SENTINEL);
                try (ParcelFileDescriptor input =
                                ParcelFileDescriptor.open(
                                        fixture.input,
                                        ParcelFileDescriptor.MODE_READ_ONLY);
                        ParcelFileDescriptor output =
                                ParcelFileDescriptor.open(
                                        outputFile,
                                        ParcelFileDescriptor.MODE_READ_WRITE)) {
                    admission =
                            awaitAdmission(
                                    context,
                                    discovery,
                                    component,
                                    input,
                                    output,
                                    callback,
                                    requestId,
                                    fixture.projectId,
                                    fixture.entrySourceUri,
                                    fixture.inputArchiveSha256,
                                    fixture.requestManifestSha256,
                                    authority,
                                    evidenceScope);
                    acknowledgeAdmission(admission);
                    Bundle terminal =
                            callback.awaitTerminal(CALLBACK_WAIT_MS);
                    terminalReceived = true;
                    SystemClock.sleep(CALLBACK_QUIET_MS);
                    callback.assertExactlyOneCompletion();
                    assertTerminalRequestId(terminal, requestId);

                    if (boundary.isPlusOne()) {
                        require(
                                !terminal.getBoolean(
                                        TypeScriptCompilerContract
                                                .KEY_SUCCEEDED,
                                        true),
                                "External capacity plus-one unexpectedly succeeded.");
                        require(
                                TypeScriptCompilerContract.OUTCOME_FAILED.equals(
                                        terminal.getString(
                                                TypeScriptCompilerContract
                                                        .KEY_OUTCOME)),
                                "External capacity plus-one did not fail.");
                        require(
                                TypeScriptCompilerContract
                                        .ERROR_ARCHIVE_QUOTA
                                        .equals(
                                                terminal.getString(
                                                        TypeScriptCompilerContract
                                                                .KEY_ERROR_CODE)),
                                "External capacity plus-one did not reach archive quota.");
                        assertNoCompilationPayload(
                                terminal,
                                "External capacity " + boundary.name());
                        assertSyncedEmptyOutput(
                                output,
                                outputFile,
                                "External capacity plus-one terminal");
                    } else {
                        require(
                                terminal.getBoolean(
                                        TypeScriptCompilerContract
                                                .KEY_SUCCEEDED,
                                        false),
                                "External capacity inclusive project did not succeed.");
                        require(
                                TypeScriptCompilerContract.OUTCOME_EMITTED.equals(
                                        terminal.getString(
                                                TypeScriptCompilerContract
                                                        .KEY_OUTCOME)),
                                "External capacity inclusive project did not emit.");
                        require(
                                terminal.getInt(
                                                TypeScriptCompilerContract
                                                        .KEY_DIAGNOSTIC_COUNT,
                                                -1)
                                        == 0,
                                "External capacity project returned diagnostics.");
                        require(
                                terminal.getInt(
                                                TypeScriptCompilerContract
                                                        .KEY_EMITTED_MODULE_COUNT,
                                                -1)
                                        == 1,
                                "External capacity project emitted an unexpected module count.");
                        Os.fsync(output.getFileDescriptor());
                        require(
                                outputFile.length() > 0L,
                                "External capacity project left empty output.");
                        verifyEmittedOutput(
                                outputFile, fixture, authority, terminal);
                    }
                    logCapacityEvidence(
                            fixture,
                            terminal.getString(
                                    TypeScriptCompilerContract.KEY_OUTCOME),
                            outputFile.length(),
                            startedAt);
                } finally {
                    closeAdmission(admission, terminalReceived);
                }
            } finally {
                deleteFixtureFiles(
                        new File[] {outputFile},
                        new String[] {
                            "external capacity output fixture"
                        });
            }
        }
    }

    private static Admission admitOnBoundCompiler(
            CleanupEvidenceSession session,
            ParcelFileDescriptor input,
            ParcelFileDescriptor output,
            RecordingCallback callback,
            String requestId,
            String projectId,
            String entrySourceUri,
            String inputArchiveSha256,
            String requestManifestSha256)
            throws Exception {
        ITypeScriptCompilerPlugin compiler = session.bound.compiler;
        long deadline = SystemClock.elapsedRealtime() + ADMISSION_WAIT_MS;
        String lastTransient = "compiler supervisor was not yet ready";
        while (SystemClock.elapsedRealtime() < deadline) {
            requireLiveSameBinder(session);
            CompilerMetadata metadata =
                    CompilerMetadata.from(compiler.getCompilerInfo());
            require(
                    metadata != null,
                    "Same-Binder compiler metadata became unavailable.");
            metadata.requireMatches(session.metadata.toFixtureAuthority());

            // Dedicated cleanup cases intentionally use the ordinary production path:
            // externalCapacityEvidenceScope = null.
            Bundle ticket =
                    compiler.startCompilation(
                            publicRequest(
                                    requestId,
                                    projectId,
                                    entrySourceUri,
                                    metadata,
                                    inputArchiveSha256,
                                    requestManifestSha256,
                                    null),
                            input,
                            output,
                            callback);
            require(ticket != null, "Compiler returned a null cleanup admission ticket.");
            require(
                    requestId.equals(
                            ticket.getString(
                                    TypeScriptCompilerContract.KEY_REQUEST_ID)),
                    "Cleanup admission ticket lost the request ID.");
            if (ticket.getBoolean(
                    TypeScriptCompilerContract.KEY_ACCEPTED, false)) {
                return new Admission(
                        session.bound,
                        compiler,
                        ticket,
                        requestId,
                        metadata);
            }
            String errorCode =
                    ticket.getString(TypeScriptCompilerContract.KEY_ERROR_CODE);
            if (!TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE.equals(
                    errorCode)
                    && !TypeScriptCompilerContract.ERROR_BUSY.equals(errorCode)) {
                throw new AssertionError(
                        "Same-Binder cleanup request was rejected: "
                                + errorCode
                                + ": "
                                + ticket.getString(
                                        TypeScriptCompilerContract
                                                .KEY_ERROR_MESSAGE));
            }
            lastTransient =
                    errorCode
                            + ": "
                            + ticket.getString(
                                    TypeScriptCompilerContract.KEY_ERROR_MESSAGE);
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining > 0L) {
                SystemClock.sleep(Math.min(RETRY_DELAY_MS, remaining));
            }
        }
        throw new AssertionError(
                "Timed out waiting for same-Binder cleanup admission: "
                        + lastTransient);
    }

    /**
     * One irreversible public admission attempt used by every natural emit run.
     *
     * The session is already ready and the preceding cleanup receipt is all-zero, so an
     * unavailable/busy response is evidence failure rather than authority to retry the request.
     */
    private static Admission admitNaturalEmitOnce(
            CleanupEvidenceSession session,
            ParcelFileDescriptor input,
            ParcelFileDescriptor output,
            RecordingCallback callback,
            String requestId,
            String projectId,
            String entrySourceUri,
            String inputArchiveSha256,
            String requestManifestSha256,
            long timeoutMs)
            throws Exception {
        requireLiveSameBinder(session);
        require(
                timeoutMs
                        >= TypeScriptR2Contract
                                .MIN_PUBLIC_COMPILATION_TIMEOUT_MS,
                "Natural emit timeout is below the public minimum.");
        require(
                timeoutMs <= TypeScriptCompilerContract.MAX_TIMEOUT_MS,
                "Natural emit timeout is above the public maximum.");
        ITypeScriptCompilerPlugin compiler = session.bound.compiler;
        CompilerMetadata metadata =
                CompilerMetadata.from(compiler.getCompilerInfo());
        require(
                metadata != null,
                "Same-Binder natural emit compiler metadata became unavailable.");
        metadata.requireMatches(session.metadata.toFixtureAuthority());
        Bundle ticket =
                compiler.startCompilation(
                        publicRequest(
                                requestId,
                                projectId,
                                entrySourceUri,
                                metadata,
                                inputArchiveSha256,
                                requestManifestSha256,
                                null,
                                timeoutMs),
                        input,
                        output,
                        callback);
        require(ticket != null, "Compiler returned a null natural emit ticket.");
        require(
                requestId.equals(
                        ticket.getString(
                                TypeScriptCompilerContract.KEY_REQUEST_ID)),
                "Natural emit admission ticket lost the request ID.");
        require(
                ticket.getBoolean(
                        TypeScriptCompilerContract.KEY_ACCEPTED, false),
                "Natural emit request was rejected without retry: "
                        + ticket.getString(
                                TypeScriptCompilerContract.KEY_ERROR_CODE)
                        + ": "
                        + ticket.getString(
                                TypeScriptCompilerContract.KEY_ERROR_MESSAGE));
        return new Admission(
                session.bound,
                compiler,
                ticket,
                requestId,
                metadata);
    }

    private static CleanupCheckpoint
            assertPreAcknowledgementCleanupTelemetry(
                    CleanupEvidenceSession session,
                    Admission admission,
                    ParcelFileDescriptor input,
                    ParcelFileDescriptor output)
                    throws Exception {
        requireLiveSameBinder(session);
        String jobToken =
                admission.ticket.getString(
                        TypeScriptCompilerContract.KEY_JOB_TOKEN);
        require(
                jobToken != null && !jobToken.isEmpty(),
                "Cleanup admission did not return a job token.");
        String jobTokenSha256 =
                sha256(jobToken.getBytes(StandardCharsets.UTF_8));
        if (!session.telemetryRequired) {
            return new CleanupCheckpoint(
                    admission.requestId,
                    jobTokenSha256,
                    null,
                    null);
        }

        long telemetryStartedAt = SystemClock.elapsedRealtime();
        long telemetryDeadline =
                telemetryStartedAt + PRE_ACK_TELEMETRY_WAIT_MS;
        Bundle compilerInfo;
        ExternalPlusOneCleanupTelemetry telemetry = null;
        int telemetrySamples = 0;
        do {
            telemetrySamples++;
            compilerInfo = admission.compiler.getCompilerInfo();
            require(
                    compilerInfo.getBoolean(
                            NATURAL_PHASE_KEY_ENABLED, false),
                    "External natural phase telemetry was not enabled.");
            require(
                    !compilerInfo.getBoolean(
                            NATURAL_PHASE_KEY_RECEIPT_AVAILABLE, false),
                    "Natural phase receipt for the active request existed before "
                            + "acknowledgement.");
            if (compilerInfo.getBoolean(
                            CLEANUP_KEY_PRE_ACK_AVAILABLE, false)
                    && compilerInfo.getBoolean(
                            CLEANUP_KEY_CURRENT_AVAILABLE, false)) {
                telemetry =
                        ExternalPlusOneCleanupTelemetry.from(compilerInfo);
                break;
            }
            long remaining =
                    telemetryDeadline - SystemClock.elapsedRealtime();
            if (remaining > 0L) {
                SystemClock.sleep(
                        Math.min(
                                PRE_ACK_TELEMETRY_SAMPLE_DELAY_MS,
                                remaining));
            }
        } while (SystemClock.elapsedRealtime() < telemetryDeadline);
        require(
                telemetry != null,
                "Cleanup pre-ACK telemetry did not converge to stable exact "
                        + "post-transaction ownership within "
                        + PRE_ACK_TELEMETRY_WAIT_MS
                        + " ms; samples="
                        + telemetrySamples
                        + ".");
        long telemetryWaitElapsedMs =
                SystemClock.elapsedRealtime() - telemetryStartedAt;
        require(
                telemetry.preAcknowledgement != null,
                "Cleanup pre-ACK telemetry was unavailable.");
        CleanupPreAcknowledgement preAcknowledgement =
                telemetry.preAcknowledgement;
        require(
                admission.requestId.equals(preAcknowledgement.requestId),
                "Cleanup pre-ACK telemetry request ID did not match admission.");
        require(
                jobTokenSha256.equals(
                        preAcknowledgement.jobTokenSha256),
                "Cleanup pre-ACK telemetry token digest did not match admission.");
        require(
                descriptorTargetSha256(input).equals(
                        preAcknowledgement.inputTargetSha256),
                "Cleanup pre-ACK input target digest did not match the caller descriptor.");
        require(
                descriptorTargetSha256(output).equals(
                        preAcknowledgement.outputTargetSha256),
                "Cleanup pre-ACK output target digest did not match the caller descriptor.");
        require(
                preAcknowledgement.activeLease,
                "Cleanup pre-ACK telemetry did not retain the active lease.");
        require(
                preAcknowledgement.inputExactTargetFdCount == 1,
                "Cleanup pre-ACK telemetry did not retain exactly one input target FD.");
        require(
                preAcknowledgement.outputExactTargetFdCount == 1,
                "Cleanup pre-ACK telemetry did not retain exactly one output target FD.");
        require(
                telemetry.current.activeLeaseCount == 1,
                "Cleanup pre-ACK current telemetry did not report one active lease.");
        require(
                telemetry.current.inputExactTargetFdCount == 1,
                "Cleanup pre-ACK current telemetry lost the input target FD.");
        require(
                telemetry.current.outputExactTargetFdCount == 1,
                "Cleanup pre-ACK current telemetry lost the output target FD.");
        require(
                admission.requestId.equals(telemetry.current.requestId)
                        && jobTokenSha256.equals(
                                telemetry.current.jobTokenSha256),
                "Cleanup pre-ACK current telemetry correlation did not match admission.");
        require(
                preAcknowledgement.inputTargetSha256.equals(
                                telemetry.current.inputTargetSha256)
                        && preAcknowledgement.outputTargetSha256.equals(
                                telemetry.current.outputTargetSha256),
                "Cleanup pre-ACK current telemetry target digests drifted.");
        if (session.identity == null) {
            session.identity = preAcknowledgement.identity;
        }
        requireSameProcessIdentity(
                session.identity,
                preAcknowledgement.identity,
                "pre-ACK");
        requireSameProcessIdentity(
                session.identity,
                telemetry.currentIdentity,
                "pre-ACK current");
        require(
                telemetry.receipt == null
                        || !admission.requestId.equals(
                                telemetry.receipt.requestId)
                        || !jobTokenSha256.equals(
                                telemetry.receipt.jobTokenSha256),
                "Cleanup receipt for the active request existed before acknowledgement.");
        Log.i(
                LOG_TAG,
                "event=external-plus-one-cleanup-pre-ack"
                        + " requestId="
                        + admission.requestId
                        + " activeLeaseCount="
                        + telemetry.current.activeLeaseCount
                        + " inputFdCount="
                        + preAcknowledgement.inputExactTargetFdCount
                        + " outputFdCount="
                        + preAcknowledgement.outputExactTargetFdCount
                        + " telemetrySamples="
                        + telemetrySamples
                        + " telemetryWaitElapsedMs="
                        + telemetryWaitElapsedMs);
        return new CleanupCheckpoint(
                admission.requestId,
                jobTokenSha256,
                preAcknowledgement.inputTargetSha256,
                preAcknowledgement.outputTargetSha256);
    }

    private static CleanupReceipt awaitCleanupEvidence(
            CleanupEvidenceSession session,
            CleanupCheckpoint checkpoint,
            String terminalOutcome,
            String terminalErrorCode,
            boolean cursorMustRemainUncreated)
            throws Exception {
        require(
                terminalOutcome != null && !terminalOutcome.isEmpty(),
                "Cleanup evidence requires a terminal outcome.");
        if (!session.telemetryRequired) {
            requireLiveSameBinder(session);
            Log.i(
                    LOG_TAG,
                    "event=external-plus-one-cleanup-black-box"
                            + " requestId="
                            + checkpoint.requestId
                            + " outcome="
                            + terminalOutcome
                            + " errorCode="
                            + terminalErrorCode
                            + " binderAlive=true");
            return null;
        }

        long deadline = SystemClock.elapsedRealtime() + ADMISSION_WAIT_MS;
        int consecutiveAllZeroSamples = 0;
        String lastState = "cleanup receipt was unavailable";
        while (SystemClock.elapsedRealtime() < deadline) {
            requireLiveSameBinder(session);
            ExternalPlusOneCleanupTelemetry telemetry =
                    ExternalPlusOneCleanupTelemetry.from(
                            session.bound.compiler.getCompilerInfo());
            requireSameProcessIdentity(
                    session.identity,
                    telemetry.currentIdentity,
                    "post-callback current");
            if (telemetry.receipt == null) {
                consecutiveAllZeroSamples = 0;
                lastState = "cleanup receipt was unavailable";
            } else if (!checkpoint.requestId.equals(
                            telemetry.receipt.requestId)
                    || !checkpoint.jobTokenSha256.equals(
                            telemetry.receipt.jobTokenSha256)) {
                consecutiveAllZeroSamples = 0;
                lastState = "cleanup receipt correlation did not match";
            } else {
                assertCleanupReceipt(
                        session,
                        telemetry.receipt,
                        checkpoint,
                        cursorMustRemainUncreated);
                if (checkpoint.matches(telemetry.current)
                        && telemetry.currentIsAllZero()) {
                    consecutiveAllZeroSamples++;
                    if (consecutiveAllZeroSamples
                            >= REQUIRED_CONSECUTIVE_CLEANUP_SAMPLES) {
                        Log.i(
                                LOG_TAG,
                                "event=external-plus-one-cleanup-receipt"
                                        + " requestId="
                                        + checkpoint.requestId
                                        + " outcome="
                                        + terminalOutcome
                                        + " errorCode="
                                        + terminalErrorCode
                                        + " callbackBeforeReceiptRead=true"
                                        + " receiptCreatedBeforeCallback=true"
                                        + " zeroSamples="
                                        + consecutiveAllZeroSamples);
                        return telemetry.receipt;
                    }
                    lastState =
                            "only "
                                    + consecutiveAllZeroSamples
                                    + " consecutive all-zero samples were observed";
                } else {
                    consecutiveAllZeroSamples = 0;
                    lastState =
                            "current cleanup counters were not all zero";
                }
            }
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining > 0L) {
                SystemClock.sleep(
                        Math.min(CLEANUP_SAMPLE_DELAY_MS, remaining));
            }
        }
        throw new AssertionError(
                "Timed out waiting for five consecutive all-zero cleanup samples for "
                        + checkpoint.requestId
                        + ": "
                        + lastState);
    }

    private static void assertCleanupReceipt(
            CleanupEvidenceSession session,
            CleanupReceipt receipt,
            CleanupCheckpoint checkpoint,
            boolean cursorMustRemainUncreated) {
        require(
                receipt.snapshotAvailable,
                "Cleanup receipt did not contain a pre-callback snapshot.");
        require(
                receipt.activeLeaseReleased,
                "Cleanup receipt did not prove active-lease release.");
        require(
                receipt.createdBeforeCallback,
                "Cleanup receipt was not created before terminal callback publication.");
        require(
                checkpoint.inputTargetSha256.equals(
                                receipt.inputTargetSha256)
                        && checkpoint.outputTargetSha256.equals(
                                receipt.outputTargetSha256),
                "Cleanup receipt target digests did not match pre-ACK evidence.");
        require(
                receipt.inputExactTargetFdCount == 0,
                "Cleanup receipt retained an input target FD.");
        require(
                receipt.outputExactTargetFdCount == 0,
                "Cleanup receipt retained an output target FD.");
        require(
                receipt.privateInputNamedFileCount == 0
                        && receipt.privateInputFdCount == 0,
                "Cleanup receipt retained private input staging state.");
        require(
                receipt.projectSpoolNamedFileCount == 0
                        && receipt.projectSpoolFdCount == 0,
                "Cleanup receipt retained project spool state.");
        require(
                receipt.privateOutputNamedFileCount == 0
                        && receipt.privateOutputFdCount == 0,
                "Cleanup receipt retained private output staging state.");
        require(
                receipt.cursorReleased,
                "Cleanup receipt did not prove cursor release.");
        if (cursorMustRemainUncreated) {
            require(
                    "NOT_CREATED".equals(receipt.cursorState),
                    "Quota rejection unexpectedly created a project cursor.");
            require(
                    receipt.cursorOpenedCount == 0
                            && receipt.cursorClosedCount == 0,
                    "Quota rejection reported cursor open/close activity.");
        } else {
            require(
                    "RELEASED".equals(receipt.cursorState),
                    "Successful successor cursor was not released.");
            require(
                    receipt.cursorOpenedCount >= 1
                            && receipt.cursorOpenedCount
                                    == receipt.cursorClosedCount,
                    "Successful successor cursor open/close counts were unbalanced.");
        }
        requireSameProcessIdentity(
                session.identity, receipt.identity, "cleanup receipt");
    }

    private static void requireLiveSameBinder(
            CleanupEvidenceSession session) {
        require(
                !session.bound.closed
                        && session.bound.rawBinder.isBinderAlive()
                        && session.bound.rawBinder.pingBinder(),
                "Dedicated cleanup runner lost its original compiler Binder identity.");
    }

    private static void requireSameProcessIdentity(
            CleanupProcessIdentity expected,
            CleanupProcessIdentity actual,
            String label) {
        require(expected != null, "Missing baseline cleanup process identity.");
        require(actual != null, "Missing " + label + " cleanup process identity.");
        require(
                expected.sameAs(actual),
                "Cleanup process identity changed at "
                        + label
                        + ": expected "
                        + expected
                        + ", observed "
                        + actual
                        + ".");
    }

    private static String descriptorTargetSha256(
            ParcelFileDescriptor descriptor) throws Exception {
        String target =
                Os.readlink(
                        "/proc/self/fd/" + descriptor.getFd());
        String deletedSuffix = " (deleted)";
        if (target.endsWith(deletedSuffix)) {
            target =
                    target.substring(
                            0, target.length() - deletedSuffix.length());
        }
        return sha256(target.getBytes(StandardCharsets.UTF_8));
    }

    private static void logCleanupEvidence(
            String requestId,
            String boundary,
            String outcome,
            long boundaryBytes,
            long outputBytes,
            long startedAt,
            CleanupEvidenceSession session) {
        Log.i(
                LOG_TAG,
                "event=external-plus-one-cleanup-case"
                        + " requestId="
                        + requestId
                        + " boundary="
                        + boundary
                        + " outcome="
                        + outcome
                        + " boundaryBytes="
                        + boundaryBytes
                        + " outputBytes="
                        + outputBytes
                        + " telemetryRequired="
                        + session.telemetryRequired
                        + " sameBinder=true"
                        + " elapsedMs="
                        + (SystemClock.elapsedRealtime() - startedAt));
    }

    private static Admission awaitAdmission(
            Context context,
            Intent discovery,
            ComponentName component,
            ParcelFileDescriptor input,
            ParcelFileDescriptor output,
            RecordingCallback callback,
            String requestId,
            String projectId,
            String entrySourceUri,
            String inputArchiveSha256,
            String requestManifestSha256,
            R2FenceBoundedProjectFixture.CompilerAuthority requiredAuthority)
            throws Exception {
        return awaitAdmission(
                context,
                discovery,
                component,
                input,
                output,
                callback,
                requestId,
                projectId,
                entrySourceUri,
                inputArchiveSha256,
                requestManifestSha256,
                requiredAuthority,
                null);
    }

    private static Admission awaitAdmission(
            Context context,
            Intent discovery,
            ComponentName component,
            ParcelFileDescriptor input,
            ParcelFileDescriptor output,
            RecordingCallback callback,
            String requestId,
            String projectId,
            String entrySourceUri,
            String inputArchiveSha256,
            String requestManifestSha256,
            R2FenceBoundedProjectFixture.CompilerAuthority requiredAuthority,
            String externalCapacityEvidenceScope)
            throws Exception {
        long deadline = SystemClock.elapsedRealtime() + ADMISSION_WAIT_MS;
        String lastTransient = "compiler metadata was not yet available";
        int attempt = 0;
        while (SystemClock.elapsedRealtime() < deadline) {
            BoundCompiler bound = null;
            boolean retained = false;
            try {
                bound = bind(context, discovery, component, deadline);
                ITypeScriptCompilerPlugin compiler = bound.compiler;
                require(
                        compiler.getContractVersion()
                                == TypeScriptCompilerContract.VERSION,
                        "Compiler contract version mismatch.");
                require(
                        TypeScriptCompilerContract.HASH.equals(
                                compiler.getContractHash()),
                        "Compiler contract hash mismatch.");

                CompilerMetadata metadata =
                        CompilerMetadata.from(compiler.getCompilerInfo());
                if (metadata == null) {
                    lastTransient = "compilerInfo fingerprints were not ready";
                } else {
                    if (requiredAuthority != null) {
                        metadata.requireMatches(requiredAuthority);
                    }
                    Bundle ticket =
                            compiler.startCompilation(
                                    publicRequest(
                                            requestId,
                                            projectId,
                                            entrySourceUri,
                                             metadata,
                                             inputArchiveSha256,
                                             requestManifestSha256,
                                             externalCapacityEvidenceScope),
                                    input,
                                    output,
                                    callback);
                    require(ticket != null, "Compiler returned a null admission ticket.");
                    require(
                            requestId.equals(
                                    ticket.getString(
                                            TypeScriptCompilerContract.KEY_REQUEST_ID)),
                            "Admission ticket lost the request ID.");
                    if (ticket.getBoolean(
                            TypeScriptCompilerContract.KEY_ACCEPTED, false)) {
                        retained = true;
                        return new Admission(
                                bound,
                                compiler,
                                ticket,
                                requestId,
                                metadata);
                    }
                    String errorCode =
                            ticket.getString(
                                    TypeScriptCompilerContract.KEY_ERROR_CODE);
                    if (!TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE.equals(
                                    errorCode)
                            && !TypeScriptCompilerContract.ERROR_BUSY.equals(errorCode)) {
                        throw new AssertionError(
                                "Public R2 request was rejected: "
                                        + errorCode
                                        + ": "
                                        + ticket.getString(
                                                TypeScriptCompilerContract
                                                        .KEY_ERROR_MESSAGE));
                    }
                    lastTransient = "admission returned " + errorCode;
                }
            } catch (RetryableBindingException | RemoteException error) {
                lastTransient = error.toString();
            } finally {
                if (bound != null && !retained) {
                    bound.close();
                }
            }
            attempt++;
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining > 0L) {
                SystemClock.sleep(Math.min(RETRY_DELAY_MS, remaining));
            }
        }
        throw new AssertionError(
                "Timed out waiting for compiler supervisor readiness/rebind: "
                        + lastTransient);
    }

    private static BoundCompiler bind(
            Context context,
            Intent discovery,
            ComponentName component,
            long deadline)
            throws RetryableBindingException {
        AwaitingConnection connection = new AwaitingConnection();
        boolean bound =
                context.bindService(
                        new Intent(discovery).setComponent(component),
                        connection,
                        Context.BIND_AUTO_CREATE);
        if (!bound) {
            throw new RetryableBindingException(
                    "bindService returned false for the compiler service.");
        }
        try {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0L) {
                throw new RetryableBindingException(
                        "Admission deadline elapsed during bind.");
            }
            IBinder rawBinder =
                    connection.awaitBinder(Math.min(BIND_WAIT_MS, remaining));
            if (rawBinder == null || !rawBinder.pingBinder()) {
                throw new RetryableBindingException(
                        "Compiler service returned no live Binder.");
            }
            require(
                    ITypeScriptCompilerPlugin.DESCRIPTOR.equals(
                            rawBinder.getInterfaceDescriptor()),
                    "Compiler Binder descriptor drifted.");
            require(
                    rawBinder.queryLocalInterface(
                                    ITypeScriptCompilerPlugin.DESCRIPTOR)
                            == null,
                    "Fixture did not cross into the compiler service process.");
            ITypeScriptCompilerPlugin compiler =
                    ITypeScriptCompilerPlugin.Stub.asInterface(rawBinder);
            require(compiler != null, "Compiler AIDL proxy is null.");
            return new BoundCompiler(
                    context, connection, rawBinder, compiler);
        } catch (RetryableBindingException error) {
            context.unbindService(connection);
            throw error;
        } catch (RemoteException error) {
            context.unbindService(connection);
            throw new RetryableBindingException(error.toString());
        } catch (RuntimeException | Error error) {
            context.unbindService(connection);
            throw error;
        }
    }

    private static Bundle publicRequest(
            String requestId,
            String projectId,
            String entrySourceUri,
            CompilerMetadata metadata,
            String inputArchiveSha256,
            String requestManifestSha256,
            String externalCapacityEvidenceScope) {
        return publicRequest(
                requestId,
                projectId,
                entrySourceUri,
                metadata,
                inputArchiveSha256,
                requestManifestSha256,
                externalCapacityEvidenceScope,
                REQUEST_TIMEOUT_MS);
    }

    private static Bundle publicRequest(
            String requestId,
            String projectId,
            String entrySourceUri,
            CompilerMetadata metadata,
            String inputArchiveSha256,
            String requestManifestSha256,
            String externalCapacityEvidenceScope,
            long timeoutMs) {
        Bundle request = new Bundle();
        request.putInt(
                TypeScriptCompilerContract.KEY_CONTRACT_VERSION,
                TypeScriptCompilerContract.VERSION);
        request.putString(
                TypeScriptCompilerContract.KEY_CONTRACT_HASH,
                TypeScriptCompilerContract.HASH);
        request.putInt(
                TypeScriptCompilerContract.KEY_INPUT_ARCHIVE_TRANSPORT_VERSION,
                TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION);
        request.putInt(
                TypeScriptCompilerContract.KEY_OUTPUT_ARCHIVE_TRANSPORT_VERSION,
                TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION);
        request.putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId);
        request.putString(
                TypeScriptCompilerContract.KEY_PROJECT_ID,
                projectId);
        request.putString(
                TypeScriptCompilerContract.KEY_ENTRY_SOURCE_URI,
                entrySourceUri);
        request.putString(
                TypeScriptCompilerContract.KEY_TARGET_PROFILE,
                TypeScriptCompilerContract.PROFILE_RHINO);
        request.putString(
                TypeScriptCompilerContract.KEY_COMPILATION_MODE,
                TypeScriptCompilerContract.MODE_DEVELOPMENT);
        request.putString(
                TypeScriptCompilerContract.KEY_COMPILER_OPTIONS_JSON, "{}");
        request.putLong(
                TypeScriptCompilerContract.KEY_TIMEOUT_MS, timeoutMs);
        request.putInt(
                TypeScriptCompilerContract.KEY_PROFILE_SCHEMA_REVISION,
                metadata.profileSchemaRevision);
        request.putInt(
                TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
                metadata.inputArchiveSchemaRevision);
        request.putInt(
                TypeScriptR2Contract.KEY_PROFILE_REVISION,
                metadata.profileRevision);
        request.putInt(
                TypeScriptR2Contract.KEY_CONFIG_SCHEMA_REVISION,
                metadata.configSchemaRevision);
        request.putString(
                TypeScriptR2Contract.KEY_CONFIG_KIND,
                metadata.configKind);
        request.putInt(
                TypeScriptCompilerContract.KEY_RUNTIME_CAPABILITY_SCHEMA_REVISION,
                metadata.runtimeCapabilitySchemaRevision);
        request.putString(
                TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SHA256,
                inputArchiveSha256);
        request.putString(
                TypeScriptR2Contract.KEY_REQUEST_MANIFEST_SHA256,
                requestManifestSha256);
        request.putString(
                TypeScriptCompilerContract.KEY_COMPILER_DISTRIBUTION_FINGERPRINT,
                metadata.distributionFingerprint);
        request.putString(
                TypeScriptCompilerContract.KEY_DECLARATIONS_FINGERPRINT,
                metadata.declarationsFingerprint);
        request.putString(
                TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
                metadata.declarationProfileFingerprint);
        request.putString(
                TypeScriptCompilerContract.KEY_RUNTIME_CAPABILITIES_FINGERPRINT,
                metadata.runtimeCapabilitiesFingerprint);
        if (externalCapacityEvidenceScope != null) {
            request.putString(
                    EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY,
                    externalCapacityEvidenceScope);
        }
        return request;
    }

    private static String newRequestId(String label) {
        String requestId =
                "r2-public-preview-"
                        + label
                        + "-"
                        + SystemClock.elapsedRealtime();
        require(
                requestId.length()
                        <= TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS,
                "Generated request ID exceeds the contract limit.");
        return requestId;
    }

    private static void acknowledgeAdmission(Admission admission)
            throws RemoteException {
        Bundle ticket = admission.ticket;
        String requestId = admission.requestId;
        String jobToken =
                ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN);
        require(
                ticket.getBoolean(
                        TypeScriptR2Contract.KEY_ACKNOWLEDGEMENT_REQUIRED,
                        false),
                "Accepted ticket did not require the post-return acknowledgement.");
        require(
                ticket.getLong(
                                TypeScriptR2Contract
                                        .KEY_ACKNOWLEDGEMENT_TIMEOUT_MS,
                                -1L)
                        == TypeScriptR2Contract
                                .MAX_ACKNOWLEDGEMENT_TIMEOUT_MS,
                "Accepted ticket returned an invalid acknowledgement timeout.");
        require(
                jobToken != null
                        && !jobToken.isEmpty()
                        && jobToken.length()
                                <= TypeScriptCompilerContract
                                        .MAX_JOB_TOKEN_CHARS,
                "Accepted ticket returned an invalid job token.");
        require(
                !admission.compiler.cancelCompilation(
                        acknowledgement(
                                requestId, wrongToken(jobToken))),
                "Wrong-token public acknowledgement was accepted.");
        require(
                admission.compiler.cancelCompilation(
                        acknowledgement(requestId, jobToken)),
                "Exact public acknowledgement was rejected.");
    }

    private static void assertTerminalRequestId(
            Bundle terminal, String requestId) {
        require(
                requestId.equals(
                        terminal.getString(
                                TypeScriptCompilerContract.KEY_REQUEST_ID)),
                "Terminal callback lost the accepted request ID.");
    }

    private static void assertNoCompilationPayload(
            Bundle terminal, String label) {
        require(
                terminal.getInt(
                                TypeScriptCompilerContract
                                        .KEY_DIAGNOSTIC_COUNT,
                                -1)
                        == 0,
                label + " returned diagnostics.");
        require(
                terminal.getInt(
                                TypeScriptCompilerContract
                                        .KEY_EMITTED_MODULE_COUNT,
                                -1)
                        == 0,
                label + " returned emitted modules.");
        require(
                !terminal.containsKey(
                        TypeScriptCompilerContract
                                .KEY_OUTPUT_MANIFEST_SHA256),
                label + " returned an output manifest digest.");
        require(
                !terminal.containsKey(
                        TypeScriptR2Contract
                                .KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION),
                label + " returned an output archive schema revision.");
        require(
                !terminal.containsKey(
                        TypeScriptR2Contract
                                .KEY_DIAGNOSTICS_SCHEMA_REVISION),
                label + " returned a diagnostics schema revision.");
    }

    private static void assertSyncedEmptyOutput(
            ParcelFileDescriptor output, File outputFile, String label)
            throws Exception {
        Os.fsync(output.getFileDescriptor());
        Os.lseek(
                output.getFileDescriptor(), 0L, OsConstants.SEEK_SET);
        byte[] probe = new byte[1];
        require(
                Os.read(
                                output.getFileDescriptor(),
                                probe,
                                0,
                                probe.length)
                        == 0,
                label + " left bytes in the output descriptor.");
        require(
                outputFile.length() == 0L,
                label + " output file was not empty.");
    }

    private static void verifyExternalNaturalEmitOutput(
            R2ExternalNaturalEmitTimeoutProjectFixture fixture,
            R2FenceBoundedProjectFixture.CompilerAuthority authority,
            Bundle terminal)
            throws Exception {
        Set<String> expected = new HashSet<>();
        expected.add("compilation-manifest.json");
        expected.add("diagnostics.json");
        for (String rootPath : fixture.rootPaths) {
            String stem =
                    rootPath
                            .substring("sources/".length())
                            .substring(
                                    0,
                                    rootPath.length()
                                            - "sources/".length()
                                            - ".ts".length());
            expected.add("modules/" + stem + ".js");
            expected.add("maps/" + stem + ".js.map");
        }

        try (ZipFile output = new ZipFile(fixture.output)) {
            Set<String> actual = new HashSet<>();
            Enumeration<? extends ZipEntry> entries = output.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                require(
                        actual.add(entry.getName()),
                        "Natural emit output contains a duplicate entry.");
                require(
                        !entry.isDirectory()
                                && entry.getMethod() == ZipEntry.STORED
                                && entry.getSize() == entry.getCompressedSize(),
                        "Natural emit output is not canonical STORED ZIP32.");
            }
            require(
                    expected.equals(actual),
                    "Natural emit output inventory differs: " + actual);

            byte[] diagnosticsBytes =
                    readZipEntryBounded(
                            output,
                            "diagnostics.json",
                            (int) TypeScriptR2Contract.MAX_DIAGNOSTICS_DOCUMENT_BYTES);
            JSONObject diagnostics =
                    new JSONObject(
                            new String(
                                    diagnosticsBytes,
                                    StandardCharsets.UTF_8));
            requireJsonString(diagnostics, "schema", DIAGNOSTICS_SCHEMA);
            require(
                    diagnostics.getInt("revision") == DIAGNOSTICS_REVISION
                            && diagnostics.getInt("diagnosticsQuotaRevision")
                                    == TypeScriptR2Contract.DIAGNOSTICS_QUOTA_REVISION
                            && diagnostics.getInt("diagnosticCount") == 0
                            && diagnostics.getInt("relatedInformationCount") == 0
                            && diagnostics
                                            .getJSONArray("diagnostics")
                                            .length()
                                    == 0,
                    "Natural emit diagnostics authority drifted.");
            requireJsonString(
                    diagnostics, "requestId", fixture.requestId);
            requireJsonString(
                    diagnostics, "projectId", fixture.projectId);
            requireJsonString(
                    diagnostics,
                    "positionEncoding",
                    TypeScriptR2Contract.DIAGNOSTICS_POSITION_ENCODING);
            require(
                    diagnostics.getInt("lineBase")
                                    == TypeScriptR2Contract.DIAGNOSTICS_LINE_BASE
                            && diagnostics.getInt("columnBase")
                                    == TypeScriptR2Contract.DIAGNOSTICS_COLUMN_BASE,
                    "Natural emit diagnostics range authority drifted.");
            require(
                    diagnostics.getInt("profileRevision")
                                    == authority.profileRevision
                            && diagnostics.getInt("configSchemaRevision")
                                    == authority.configSchemaRevision,
                    "Natural emit diagnostics profile authority drifted.");

            Map<String, OutputPayload> packagedPayloads =
                    new LinkedHashMap<>();
            packagedPayloads.put(
                    "diagnostics.json",
                    new OutputPayload(
                            "diagnostics.json",
                            "diagnostics",
                            diagnosticsBytes,
                            new String[0]));
            Map<String, byte[]> workerOutputs = new LinkedHashMap<>();
            long javascriptBytes = 0L;
            long sourceMapBytes = 0L;
            for (String rootPath : fixture.rootPaths) {
                String stem =
                        rootPath
                                .substring("sources/".length())
                                .substring(
                                        0,
                                        rootPath.length()
                                            - "sources/".length()
                                            - ".ts".length());
                String modulePath = "modules/" + stem + ".js";
                String mapPath = "maps/" + stem + ".js.map";
                byte[] moduleBytes =
                        readZipEntryBounded(
                                output,
                                modulePath,
                                (int) TypeScriptCompilerContract
                                        .MAX_SINGLE_JAVASCRIPT_BYTES);
                byte[] mapBytes =
                        readZipEntryBounded(
                                output,
                                mapPath,
                                (int) TypeScriptCompilerContract
                                        .MAX_SINGLE_SOURCE_MAP_BYTES);
                byte[] rawModuleBytes =
                        recoverRawWorkerModuleBytes(moduleBytes, stem);
                javascriptBytes =
                        Math.addExact(
                                javascriptBytes,
                                rawModuleBytes.length);
                sourceMapBytes =
                        Math.addExact(sourceMapBytes, mapBytes.length);
                workerOutputs.put(
                        "/outputs/" + stem + ".js", rawModuleBytes);
                workerOutputs.put("/outputs/" + stem + ".js.map", mapBytes);
                packagedPayloads.put(
                        mapPath,
                        new OutputPayload(
                                mapPath,
                                "source_map",
                                mapBytes,
                                new String[] {rootPath}));
                packagedPayloads.put(
                        modulePath,
                        new OutputPayload(
                                modulePath,
                                "javascript",
                                moduleBytes,
                                new String[] {rootPath}));
                JSONObject sourceMap =
                        new JSONObject(
                                new String(
                                        mapBytes,
                                        StandardCharsets.UTF_8));
                require(
                        sourceMap.getInt("version") == 3
                                && !sourceMap.getString("mappings").isEmpty(),
                        "Natural emit source map authority drifted.");
            }
            byte[] entryModule =
                    workerOutputs.get(
                            "/outputs/"
                                    + externalSourceStem(
                                            fixture.entryPath)
                                    + ".js");
            require(
                    entryModule != null
                            && new String(
                                            entryModule,
                                            StandardCharsets.UTF_8)
                                    .contains(fixture.exportedIdentifier),
                    "Natural emit entry module lost its export.");

            MessageDigest aggregate =
                    MessageDigest.getInstance("SHA-256");
            List<String> workerPaths =
                    new ArrayList<>(workerOutputs.keySet());
            Collections.sort(workerPaths);
            for (String workerPath : workerPaths) {
                aggregate.update(
                        workerPath.getBytes(StandardCharsets.UTF_8));
                aggregate.update((byte) 0);
                aggregate.update(workerOutputs.get(workerPath));
            }
            R2ExternalNaturalEmitTimeoutProjectFixture.OutputAuthority
                    outputAuthority = fixture.expectedOutputAuthority;
            String aggregateSha256 = lowerHex(aggregate.digest());
            require(
                    workerOutputs.size() == outputAuthority.fileCount
                            && javascriptBytes
                                    == outputAuthority.javascriptBytes
                            && sourceMapBytes
                                    == outputAuthority.sourceMapBytes
                            && javascriptBytes + sourceMapBytes
                                    == outputAuthority.totalBytes
                            && aggregateSha256.equals(
                                    outputAuthority.aggregateSha256),
                    "Natural emit TS 6.0.3 output authority drifted: files="
                            + workerOutputs.size()
                            + " javascriptBytes="
                            + javascriptBytes
                            + " sourceMapBytes="
                            + sourceMapBytes
                            + " totalBytes="
                            + (javascriptBytes + sourceMapBytes)
                            + " aggregateSha256="
                            + aggregateSha256
                            + ".");

            byte[] manifestBytes =
                    readZipEntryBounded(
                            output,
                            "compilation-manifest.json",
                            MAXIMUM_OUTPUT_MANIFEST_BYTES);
            require(
                    sha256(manifestBytes)
                            .equals(
                                    terminal.getString(
                                            TypeScriptCompilerContract
                                                    .KEY_OUTPUT_MANIFEST_SHA256)),
                    "Natural emit terminal manifest digest drifted.");
            JSONObject manifest =
                    new JSONObject(
                            new String(
                                    manifestBytes,
                                    StandardCharsets.UTF_8));
            requireJsonString(
                    manifest, "schema", OUTPUT_MANIFEST_SCHEMA);
            require(
                    manifest.getInt("revision") == OUTPUT_REVISION,
                    "Natural emit output manifest revision drifted.");
            require(
                    terminal.getInt(
                                    TypeScriptR2Contract
                                            .KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION,
                                    -1)
                            == OUTPUT_REVISION,
                    "Natural emit terminal output schema revision drifted.");
            require(
                    terminal.getInt(
                                    TypeScriptR2Contract
                                            .KEY_DIAGNOSTICS_SCHEMA_REVISION,
                                    -1)
                            == DIAGNOSTICS_REVISION,
                    "Natural emit terminal diagnostics schema revision drifted.");
            requireJsonString(
                    manifest, "requestId", fixture.requestId);
            requireJsonString(
                    manifest, "projectId", fixture.projectId);
            requireJsonString(
                    manifest,
                    "entrySourceUri",
                    fixture.entrySourceUri);
            requireJsonString(
                    manifest,
                    "entryModulePath",
                    "modules/"
                            + externalSourceStem(fixture.entryPath)
                            + ".js");
            requireJsonString(
                    manifest,
                    "targetProfile",
                    TypeScriptCompilerContract.PROFILE_RHINO);
            requireJsonString(
                    manifest,
                    "compilationMode",
                    TypeScriptCompilerContract.MODE_DEVELOPMENT);
            requireJsonString(manifest, "compilerApi", COMPILER_API);
            requireJsonString(manifest, "compilerPackage", "typescript");
            requireJsonString(
                    manifest,
                    "compilerVersion",
                    authority.compilerVersion);
            requireJsonString(
                    manifest,
                    "compilerDistributionFingerprint",
                    authority.compilerDistributionFingerprint);
            requireJsonString(
                    manifest,
                    "declarationsFingerprint",
                    authority.declarationsFingerprint);
            requireJsonString(
                    manifest,
                    "runtimeCapabilitiesFingerprint",
                    authority.runtimeCapabilitiesFingerprint);
            requireJsonString(
                    manifest,
                    "inputArchiveSha256",
                    fixture.inputArchiveSha256);
            requireJsonString(
                    manifest,
                    "requestManifestSha256",
                    fixture.requestManifestSha256);
            requireJsonString(
                    manifest,
                    "outcome",
                    TypeScriptCompilerContract.OUTCOME_EMITTED);
            require(
                    manifest.getInt("contractVersion")
                                    == TypeScriptCompilerContract.VERSION
                            && TypeScriptCompilerContract.HASH.equals(
                                    manifest.getString("contractHash"))
                            && manifest.getInt(
                                            "outputArchiveTransportVersion")
                                    == TypeScriptCompilerContract
                                            .OUTPUT_ARCHIVE_TRANSPORT_VERSION,
                    "Natural emit output contract authority drifted.");
            require(
                    manifest.getInt("profileSchemaRevision")
                                    == TypeScriptCompilerContract
                                            .PROFILE_SCHEMA_REVISION
                            && manifest.getInt("profileRevision")
                                    == authority.profileRevision
                            && manifest.getInt("configSchemaRevision")
                                    == authority.configSchemaRevision
                            && "none".equals(
                                    manifest.getString("configKind"))
                            && manifest.getInt(
                                            "runtimeCapabilitySchemaRevision")
                                    == TypeScriptCompilerContract
                                            .RUNTIME_CAPABILITY_SCHEMA_REVISION,
                    "Natural emit output profile authority drifted.");
            require(
                    !manifest.getBoolean("emitSkipped")
                            && manifest.getInt("diagnosticCount") == 0
                            && manifest.getInt("emittedModuleCount")
                                    == fixture.rootPaths.size(),
                    "Natural emit output terminal accounting drifted.");
            requireJsonString(
                    manifest,
                    "diagnosticsPath",
                    "diagnostics.json");
            verifyEffectiveOptions(manifest);
            verifyOutputManifestFiles(
                    manifest.getJSONArray("files"),
                    packagedPayloads);
            requireJsonString(
                    manifest,
                    "artifactInventoryFingerprint",
                    outputInventoryFingerprint(packagedPayloads, false));
            requireJsonString(
                    manifest,
                    "moduleInventoryFingerprint",
                    outputInventoryFingerprint(packagedPayloads, true));
        }
    }

    private static String externalSourceStem(String sourcePath) {
        require(
                sourcePath.startsWith("sources/")
                        && sourcePath.endsWith(".ts"),
                "External source path is not canonical TypeScript.");
        return sourcePath.substring(
                "sources/".length(),
                sourcePath.length() - ".ts".length());
    }

    private static byte[] recoverRawWorkerModuleBytes(
            byte[] packagedBytes, String stem) {
        String packaged = new String(packagedBytes, StandardCharsets.UTF_8);
        require(
                Arrays.equals(
                        packagedBytes,
                        packaged.getBytes(StandardCharsets.UTF_8)),
                "Packaged JavaScript is not canonical UTF-8.");
        int parentDepth = 1;
        for (int index = 0; index < stem.length(); index++) {
            if (stem.charAt(index) == '/') {
                parentDepth++;
            }
        }
        StringBuilder packagedDirectiveBuilder =
                new StringBuilder("//# sourceMappingURL=");
        for (int index = 0; index < parentDepth; index++) {
            packagedDirectiveBuilder.append("../");
        }
        String packagedDirective =
                packagedDirectiveBuilder
                        .append("maps/")
                        .append(stem)
                        .append(".js.map")
                        .toString();
        String workerDirective =
                "//# sourceMappingURL="
                        + stem.substring(stem.lastIndexOf('/') + 1)
                        + ".js.map";
        boolean terminalLf = packaged.endsWith("\n");
        String withoutTerminalLf =
                terminalLf
                        ? packaged.substring(0, packaged.length() - 1)
                        : packaged;
        int directiveStart =
                withoutTerminalLf.length() - packagedDirective.length();
        require(
                packaged.indexOf('\r') < 0
                        && directiveStart >= 0
                        && withoutTerminalLf.endsWith(packagedDirective)
                        && (directiveStart == 0
                                || withoutTerminalLf.charAt(
                                                directiveStart - 1)
                                        == '\n'),
                "Packaged JavaScript Source Map reference differs.");
        String raw =
                withoutTerminalLf.substring(0, directiveStart)
                        + workerDirective
                        + (terminalLf ? "\n" : "");
        byte[] rawBytes = raw.getBytes(StandardCharsets.UTF_8);
        require(
                packagedBytes.length - rawBytes.length
                        == packagedDirective.length()
                                - workerDirective.length(),
                "Worker JavaScript reconstruction differs.");
        return rawBytes;
    }

    private static void verifyEmittedOutput(
            File outputFile,
            R2FenceBoundedProjectFixture fixture,
            R2FenceBoundedProjectFixture.CompilerAuthority authority,
            Bundle terminal)
            throws Exception {
        verifyEmittedOutput(
                outputFile,
                fixture.requestId,
                R2FenceBoundedProjectFixture.PROJECT_ID,
                R2FenceBoundedProjectFixture.ENTRY_SOURCE_URI,
                R2FenceBoundedProjectFixture.SOURCE_PATH,
                "fenceBounded",
                fixture.inputArchiveSha256,
                fixture.requestManifestSha256,
                authority,
                terminal);
    }

    private static void verifyEmittedOutput(
            File outputFile,
            R2ExternalCapacityProjectFixture fixture,
            R2FenceBoundedProjectFixture.CompilerAuthority authority,
            Bundle terminal)
            throws Exception {
        verifyEmittedOutput(
                outputFile,
                fixture.requestId,
                fixture.projectId,
                fixture.entrySourceUri,
                R2ExternalCapacityProjectFixture.ENTRY_PATH,
                R2ExternalCapacityProjectFixture.EXPORTED_IDENTIFIER,
                fixture.inputArchiveSha256,
                fixture.requestManifestSha256,
                authority,
                terminal);
    }

    private static void verifyEmittedOutput(
            File outputFile,
            String requestId,
            String projectId,
            String entrySourceUri,
            String sourcePath,
            String exportedIdentifier,
            String inputArchiveSha256,
            String requestManifestSha256,
            R2FenceBoundedProjectFixture.CompilerAuthority authority,
            Bundle terminal)
            throws Exception {
        Set<String> expected =
                new HashSet<>(
                        Arrays.asList(
                                "compilation-manifest.json",
                                "diagnostics.json",
                                "modules/main.js",
                                "maps/main.js.map"));
        try (ZipFile output = new ZipFile(outputFile)) {
            Set<String> actual = new HashSet<>();
            Enumeration<? extends ZipEntry> entries = output.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                require(
                        actual.add(entry.getName()),
                        "Emitted output contains a duplicate ZIP entry.");
                require(
                        !entry.isDirectory()
                                && entry.getMethod() == ZipEntry.STORED
                                && entry.getSize() == entry.getCompressedSize(),
                        "Emitted output entry is not a regular STORED ZIP32 payload: "
                                + entry.getName());
            }
            require(
                    expected.equals(actual),
                    "Emitted output inventory differs: " + actual);

            byte[] diagnosticsBytes =
                    readZipEntryBounded(
                            output,
                            "diagnostics.json",
                            (int) TypeScriptR2Contract.MAX_DIAGNOSTICS_DOCUMENT_BYTES);
            byte[] sourceMapBytes =
                    readZipEntryBounded(
                            output,
                            "maps/main.js.map",
                            (int) TypeScriptCompilerContract
                                    .MAX_SINGLE_SOURCE_MAP_BYTES);
            byte[] moduleBytes =
                    readZipEntryBounded(
                            output,
                            "modules/main.js",
                            (int) TypeScriptCompilerContract
                                    .MAX_SINGLE_JAVASCRIPT_BYTES);

            JSONObject diagnostics =
                    new JSONObject(
                            new String(
                                    diagnosticsBytes,
                                    StandardCharsets.UTF_8));
            requireJsonString(
                    diagnostics, "schema", DIAGNOSTICS_SCHEMA);
            require(
                    diagnostics.getInt("revision") == DIAGNOSTICS_REVISION,
                    "Diagnostics revision differs.");
            require(
                    diagnostics.getInt("diagnosticsQuotaRevision")
                                    == TypeScriptR2Contract.DIAGNOSTICS_QUOTA_REVISION
                            && diagnostics.getInt("diagnosticCount") == 0
                            && diagnostics.getInt("relatedInformationCount") == 0,
                    "Diagnostics quota accounting differs.");
            requireJsonString(
                    diagnostics, "requestId", requestId);
            requireJsonString(
                    diagnostics, "projectId", projectId);
            requireJsonString(
                    diagnostics,
                    "positionEncoding",
                    TypeScriptR2Contract.DIAGNOSTICS_POSITION_ENCODING);
            require(
                    diagnostics.getInt("lineBase")
                                    == TypeScriptR2Contract.DIAGNOSTICS_LINE_BASE
                            && diagnostics.getInt("columnBase")
                                    == TypeScriptR2Contract.DIAGNOSTICS_COLUMN_BASE,
                    "Diagnostics range authority differs.");
            requireJsonString(
                    diagnostics,
                    "targetProfile",
                    TypeScriptCompilerContract.PROFILE_RHINO);
            require(
                    diagnostics.getInt("profileSchemaRevision")
                            == TypeScriptCompilerContract
                                    .PROFILE_SCHEMA_REVISION,
                    "Diagnostics profile schema differs.");
            require(
                    diagnostics.getInt("profileRevision")
                            == authority.profileRevision,
                    "Diagnostics profile revision differs.");
            require(
                    diagnostics.getInt("configSchemaRevision")
                            == authority.configSchemaRevision,
                    "Diagnostics config schema differs.");
            requireJsonString(diagnostics, "configKind", "none");
            require(
                    diagnostics.getJSONArray("diagnostics").length() == 0,
                    "Diagnostics payload is not empty.");

            JSONObject sourceMap =
                    new JSONObject(
                            new String(
                                    sourceMapBytes,
                                    StandardCharsets.UTF_8));
            require(
                    sourceMap.getInt("version") == 3,
                    "Emitted source map version differs.");
            requireJsonString(sourceMap, "file", "main.js");
            requireJsonString(sourceMap, "sourceRoot", "");
            requireJsonArrayStrings(
                    sourceMap.getJSONArray("sources"),
                    Arrays.asList("../" + sourcePath),
                    "source map sources");
            require(
                    sourceMap.getJSONArray("names").length() == 0,
                    "Emitted source map names inventory differs.");
            require(
                    !sourceMap.getString("mappings").isEmpty(),
                    "Emitted source map mappings are empty.");

            String moduleText =
                    new String(moduleBytes, StandardCharsets.UTF_8);
            require(
                    moduleText.contains(exportedIdentifier),
                    "Emitted JavaScript lost the accepted export.");
            String moduleWithoutFinalLf =
                    moduleText.endsWith("\n")
                            ? moduleText.substring(0, moduleText.length() - 1)
                            : moduleText;
            require(
                    moduleText.indexOf('\r') < 0
                            && moduleWithoutFinalLf.endsWith(
                                    "//# sourceMappingURL=../maps/main.js.map"),
                    "Emitted JavaScript sourceMappingURL differs.");

            Map<String, OutputPayload> payloads = new LinkedHashMap<>();
            payloads.put(
                    "diagnostics.json",
                    new OutputPayload(
                            "diagnostics.json",
                            "diagnostics",
                            diagnosticsBytes,
                            new String[0]));
            payloads.put(
                    "maps/main.js.map",
                    new OutputPayload(
                            "maps/main.js.map",
                            "source_map",
                            sourceMapBytes,
                            new String[] {
                                sourcePath
                            }));
            payloads.put(
                    "modules/main.js",
                    new OutputPayload(
                            "modules/main.js",
                            "javascript",
                            moduleBytes,
                            new String[] {
                                sourcePath
                            }));

            ZipEntry manifestEntry =
                    output.getEntry("compilation-manifest.json");
            require(
                    manifestEntry != null
                            && manifestEntry.getSize() > 0L
                            && manifestEntry.getSize()
                                    <= MAXIMUM_OUTPUT_MANIFEST_BYTES,
                    "Emitted compilation manifest has an invalid size.");
            byte[] manifestBytes;
            try (InputStream input =
                    output.getInputStream(manifestEntry)) {
                manifestBytes =
                        readAllBounded(
                                input, MAXIMUM_OUTPUT_MANIFEST_BYTES);
            }
            String manifestSha256 = sha256(manifestBytes);
            require(
                    manifestSha256.equals(
                            terminal.getString(
                                    TypeScriptCompilerContract
                                            .KEY_OUTPUT_MANIFEST_SHA256)),
                    "Terminal output-manifest SHA-256 differs.");
            JSONObject manifest =
                    new JSONObject(
                            new String(
                                    manifestBytes,
                                    StandardCharsets.UTF_8));
            requireJsonString(
                    manifest, "schema", OUTPUT_MANIFEST_SCHEMA);
            require(
                    manifest.getInt("revision") == OUTPUT_REVISION,
                    "Output manifest revision differs.");
            require(
                    terminal.getInt(
                                    TypeScriptR2Contract
                                            .KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION,
                                    -1)
                            == OUTPUT_REVISION,
                    "Terminal output archive schema revision differs.");
            require(
                    terminal.getInt(
                                    TypeScriptR2Contract
                                            .KEY_DIAGNOSTICS_SCHEMA_REVISION,
                                    -1)
                            == DIAGNOSTICS_REVISION,
                    "Terminal diagnostics schema revision differs.");
            requireJsonString(
                    manifest,
                    "requestId",
                    requestId);
            requireJsonString(
                    manifest,
                    "projectId",
                    projectId);
            requireJsonString(
                    manifest,
                    "entrySourceUri",
                    entrySourceUri);
            requireJsonString(
                    manifest,
                    "entryModulePath",
                    "modules/main.js");
            requireJsonString(
                    manifest,
                    "targetProfile",
                    TypeScriptCompilerContract.PROFILE_RHINO);
            requireJsonString(
                    manifest,
                    "compilationMode",
                    TypeScriptCompilerContract.MODE_DEVELOPMENT);
            requireJsonString(
                    manifest, "compilerApi", COMPILER_API);
            requireJsonString(
                    manifest,
                    "compilerPackage",
                    "typescript");
            requireJsonString(
                    manifest,
                    "compilerVersion",
                    authority.compilerVersion);
            requireJsonString(
                    manifest,
                    "compilerDistributionFingerprint",
                    authority.compilerDistributionFingerprint);
            requireJsonString(
                    manifest,
                    "declarationsFingerprint",
                    authority.declarationsFingerprint);
            requireJsonString(
                    manifest,
                    "runtimeCapabilitiesFingerprint",
                    authority.runtimeCapabilitiesFingerprint);
            requireJsonString(
                    manifest,
                    "inputArchiveSha256",
                    inputArchiveSha256);
            requireJsonString(
                    manifest,
                    "requestManifestSha256",
                    requestManifestSha256);
            requireJsonString(
                    manifest,
                    "outcome",
                    TypeScriptCompilerContract.OUTCOME_EMITTED);
            require(
                    manifest.getInt("contractVersion")
                            == TypeScriptCompilerContract.VERSION,
                    "Output manifest contract version differs.");
            requireJsonString(
                    manifest,
                    "contractHash",
                    TypeScriptCompilerContract.HASH);
            require(
                    manifest.getInt("outputArchiveTransportVersion")
                            == TypeScriptCompilerContract
                                    .OUTPUT_ARCHIVE_TRANSPORT_VERSION,
                    "Output manifest transport version differs.");
            require(
                    manifest.getInt("profileSchemaRevision")
                            == TypeScriptCompilerContract
                                    .PROFILE_SCHEMA_REVISION,
                    "Output manifest profile schema differs.");
            require(
                    manifest.getInt("profileRevision")
                            == authority.profileRevision,
                    "Output manifest profile revision differs.");
            require(
                    manifest.getInt("configSchemaRevision")
                            == authority.configSchemaRevision,
                    "Output manifest config schema differs.");
            requireJsonString(
                    manifest, "configKind", "none");
            require(
                    manifest.getInt(
                                    "runtimeCapabilitySchemaRevision")
                            == TypeScriptCompilerContract
                                    .RUNTIME_CAPABILITY_SCHEMA_REVISION,
                    "Output manifest runtime schema differs.");
            require(
                    !manifest.getBoolean("emitSkipped"),
                    "Output manifest says emit was skipped.");
            require(
                    manifest.getInt("diagnosticCount") == 0,
                    "Output manifest diagnostic count differs.");
            require(
                    manifest.getInt("emittedModuleCount") == 1,
                    "Output manifest module count differs.");
            requireJsonString(
                    manifest,
                    "diagnosticsPath",
                    "diagnostics.json");
            verifyEffectiveOptions(manifest);
            verifyOutputManifestFiles(
                    manifest.getJSONArray("files"), payloads);
            requireJsonString(
                    manifest,
                    "artifactInventoryFingerprint",
                    outputInventoryFingerprint(payloads, false));
            requireJsonString(
                    manifest,
                    "moduleInventoryFingerprint",
                    outputInventoryFingerprint(payloads, true));
        }
    }

    private static byte[] readZipEntryBounded(
            ZipFile archive, String path, int maximumBytes)
            throws Exception {
        ZipEntry entry = archive.getEntry(path);
        require(entry != null, "Emitted output entry is missing: " + path);
        require(
                entry.getSize() > 0L && entry.getSize() <= maximumBytes,
                "Emitted output entry has an invalid size: " + path);
        byte[] bytes;
        try (InputStream input = archive.getInputStream(entry)) {
            bytes = readAllBounded(input, maximumBytes);
        }
        require(
                entry.getSize() == bytes.length,
                "Emitted output entry size changed while reading: " + path);
        return bytes;
    }

    private static void verifyEffectiveOptions(JSONObject manifest)
            throws Exception {
        JSONObject options = manifest.getJSONObject("effectiveOptions");
        require(
                jsonKeys(options)
                        .equals(
                                new HashSet<>(
                                        Arrays.asList(
                                                "allowJs",
                                                "checkJs",
                                                "ignoreDeprecations",
                                                "incremental",
                                                "inlineSourceMap",
                                                "inlineSources",
                                                "module",
                                                "moduleResolution",
                                                "newLine",
                                                "noEmitOnError",
                                                "noLib",
                                                "outDir",
                                                "rootDir",
                                                "skipLibCheck",
                                                "sourceMap",
                                                "strict",
                                                "target",
                                                "types"))),
                "Output manifest effective-options inventory differs.");
        require(!options.getBoolean("allowJs"), "allowJs authority differs.");
        require(!options.getBoolean("checkJs"), "checkJs authority differs.");
        requireJsonString(options, "ignoreDeprecations", "6.0");
        require(!options.getBoolean("incremental"), "incremental authority differs.");
        require(!options.getBoolean("inlineSourceMap"), "inlineSourceMap authority differs.");
        require(!options.getBoolean("inlineSources"), "inlineSources authority differs.");
        require(options.getInt("module") == 1, "module authority differs.");
        require(options.getInt("moduleResolution") == 2, "moduleResolution authority differs.");
        require(options.getInt("newLine") == 1, "newLine authority differs.");
        require(options.getBoolean("noEmitOnError"), "noEmitOnError authority differs.");
        require(!options.getBoolean("noLib"), "noLib authority differs.");
        requireJsonString(options, "outDir", "/outputs");
        requireJsonString(options, "rootDir", "/sources");
        require(!options.getBoolean("skipLibCheck"), "skipLibCheck authority differs.");
        require(options.getBoolean("sourceMap"), "sourceMap authority differs.");
        require(options.getBoolean("strict"), "strict authority differs.");
        require(options.getInt("target") == 5, "target authority differs.");
        require(
                options.getJSONArray("types").length() == 0,
                "types authority differs.");
        requireJsonString(
                manifest,
                "effectiveOptionsFingerprint",
                sha256(EFFECTIVE_OPTIONS_JSON.getBytes(StandardCharsets.US_ASCII)));
    }

    private static void verifyOutputManifestFiles(
            JSONArray files, Map<String, OutputPayload> payloads)
            throws Exception {
        require(
                files.length() == payloads.size(),
                "Output manifest file count differs.");
        List<String> sortedPaths =
                new ArrayList<>(payloads.keySet());
        Collections.sort(sortedPaths);
        int index = 0;
        for (String path : sortedPaths) {
            OutputPayload payload = payloads.get(path);
            JSONObject descriptor = files.getJSONObject(index++);
            require(
                    jsonKeys(descriptor)
                            .equals(
                                    new HashSet<>(
                                            Arrays.asList(
                                                    "path",
                                                    "kind",
                                                    "size",
                                                    "sha256",
                                                    "sourcePaths"))),
                    "Output manifest file descriptor shape differs: "
                            + payload.path);
            requireJsonString(descriptor, "path", payload.path);
            requireJsonString(descriptor, "kind", payload.kind);
            require(
                    descriptor.getLong("size") == payload.bytes.length,
                    "Output manifest file size differs: " + payload.path);
            requireJsonString(
                    descriptor,
                    "sha256",
                    sha256(payload.bytes));
            requireJsonArrayStrings(
                    descriptor.getJSONArray("sourcePaths"),
                    Arrays.asList(payload.sourcePaths),
                    "sourcePaths for " + payload.path);
        }
    }

    private static String outputInventoryFingerprint(
            Map<String, OutputPayload> payloads, boolean modulesOnly) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception error) {
            throw new AssertionError("SHA-256 is unavailable.", error);
        }
        digest.update(
                (modulesOnly
                                ? "autojs6.typescript.output-module-inventory.v1\n"
                                : "autojs6.typescript.output-artifact-inventory.v1\n")
                        .getBytes(StandardCharsets.US_ASCII));
        List<String> sortedPaths = new ArrayList<>();
        for (Map.Entry<String, OutputPayload> entry : payloads.entrySet()) {
            if (!modulesOnly || "javascript".equals(entry.getValue().kind)) {
                sortedPaths.add(entry.getKey());
            }
        }
        Collections.sort(sortedPaths);
        for (String path : sortedPaths) {
            OutputPayload payload = payloads.get(path);
            digest.update(payload.kind.getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(payload.path.getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(
                    Long.toString(payload.bytes.length)
                            .getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(
                    sha256(payload.bytes)
                            .getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(
                    Integer.toString(payload.sourcePaths.length)
                            .getBytes(StandardCharsets.US_ASCII));
            for (String sourcePath : payload.sourcePaths) {
                digest.update((byte) 0);
                digest.update(
                        sourcePath.getBytes(StandardCharsets.US_ASCII));
            }
            digest.update((byte) '\n');
        }
        return lowerHex(digest.digest());
    }

    private static Set<String> jsonKeys(JSONObject object) {
        Set<String> keys = new HashSet<>();
        java.util.Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) {
            keys.add(iterator.next());
        }
        return keys;
    }

    private static void requireJsonArrayStrings(
            JSONArray array, List<String> expected, String label)
            throws Exception {
        require(
                array.length() == expected.size(),
                "JSON array size differs for " + label + ".");
        for (int index = 0; index < expected.size(); index++) {
            require(
                    expected.get(index).equals(array.getString(index)),
                    "JSON array authority differs for " + label + ".");
        }
    }

    private static void requireJsonString(
            JSONObject object, String key, String expected)
            throws Exception {
        require(
                expected.equals(object.getString(key)),
                "JSON authority differs for " + key + ".");
    }

    private static byte[] readAllBounded(
            InputStream input, int maximumBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total = Math.addExact(total, read);
            require(
                    total <= maximumBytes,
                    "Bounded output exceeds its limit.");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void closeAdmission(
            Admission admission, boolean terminalReceived) {
        if (admission == null) {
            return;
        }
        if (!terminalReceived) {
            admission.bestEffortCancel();
        }
        admission.close();
    }

    private static void logCaseEvidence(
            String requestId,
            String outcome,
            long callerBytes,
            long outputBytes,
            long startedAt) {
        Log.i(
                LOG_TAG,
                "event=case"
                        + " requestId="
                        + requestId
                        + " outcome="
                        + outcome
                        + " callerBytes="
                        + callerBytes
                        + " outputBytes="
                        + outputBytes
                        + " elapsedMs="
                        + (SystemClock.elapsedRealtime() - startedAt));
    }

    private static void logCapacityEvidence(
            R2ExternalCapacityProjectFixture fixture,
            String outcome,
            long outputBytes,
            long startedAt) {
        Log.i(
                LOG_TAG,
                "event=capacity"
                        + " requestId="
                        + fixture.requestId
                        + " boundary="
                        + fixture.boundary.name()
                        + " outcome="
                        + outcome
                        + " containerBytes="
                        + fixture.containerBytes
                        + " uncompressedBytes="
                        + fixture.uncompressedBytes
                        + " compressedPayloadBytes="
                        + fixture.compressedPayloadBytes
                        + " manifestBytes="
                        + fixture.manifestBytes
                        + " sourceBytes="
                        + fixture.sourceBytes
                        + " quotaAxis="
                        + fixture.productionQuotaAxis.name()
                        + " outputBytes="
                        + outputBytes
                        + " elapsedMs="
                        + (SystemClock.elapsedRealtime() - startedAt));
    }

    private static void deleteFixtureFiles(
            File[] files, String[] labels) {
        require(
                files.length == labels.length,
                "Fixture cleanup metadata differs.");
        AssertionError cleanupFailure = null;
        for (int index = 0; index < files.length; index++) {
            File file = files[index];
            if (file == null) {
                continue;
            }
            try {
                deleteExactly(file, labels[index]);
            } catch (AssertionError error) {
                if (cleanupFailure == null) {
                    cleanupFailure = error;
                } else {
                    cleanupFailure.addSuppressed(error);
                }
            }
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    private static void deleteExactly(File file, String label) {
        require(
                !file.exists() || file.delete(),
                "Could not delete " + label + ".");
        require(!file.exists(), "Deleted " + label + " still exists.");
    }

    private static Bundle acknowledgement(String requestId, String jobToken) {
        Bundle acknowledgement = new Bundle();
        acknowledgement.putInt(
                TypeScriptCompilerContract.KEY_CONTRACT_VERSION,
                TypeScriptCompilerContract.VERSION);
        acknowledgement.putString(
                TypeScriptCompilerContract.KEY_CONTRACT_HASH,
                TypeScriptCompilerContract.HASH);
        acknowledgement.putString(
                TypeScriptCompilerContract.KEY_REQUEST_ID, requestId);
        acknowledgement.putString(
                TypeScriptCompilerContract.KEY_JOB_TOKEN, jobToken);
        acknowledgement.putString(
                TypeScriptR2Contract.KEY_CANCELLATION_OPERATION,
                TypeScriptR2Contract.CANCELLATION_OPERATION_ACKNOWLEDGE);
        return acknowledgement;
    }

    private static Bundle cancellation(String requestId, String jobToken) {
        Bundle cancellation = acknowledgement(requestId, jobToken);
        cancellation.putString(
                TypeScriptR2Contract.KEY_CANCELLATION_OPERATION,
                TypeScriptR2Contract.CANCELLATION_OPERATION_CANCEL);
        return cancellation;
    }

    private static String wrongToken(String jobToken) {
        char first = jobToken.charAt(0);
        char replacement = first == '0' ? '1' : '0';
        return replacement + jobToken.substring(1);
    }

    private static void writeAndSync(File file, byte[] bytes) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
            output.getFD().sync();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return lowerHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception error) {
            throw new AssertionError("SHA-256 is unavailable.", error);
        }
    }

    private static String lowerHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }

    private static boolean isLowerSha256(String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class OutputPayload {

        final String path;
        final String kind;
        final byte[] bytes;
        final String[] sourcePaths;

        OutputPayload(
                String path,
                String kind,
                byte[] bytes,
                String[] sourcePaths) {
            this.path = path;
            this.kind = kind;
            this.bytes = bytes;
            this.sourcePaths = sourcePaths;
        }
    }

    private static final class CleanupEvidenceSession
            implements AutoCloseable {

        final BoundCompiler bound;
        final CompilerMetadata metadata;
        final boolean telemetryRequired;
        CleanupProcessIdentity identity;

        CleanupEvidenceSession(
                BoundCompiler bound,
                CompilerMetadata metadata,
                boolean telemetryRequired,
                CleanupProcessIdentity identity) {
            this.bound = bound;
            this.metadata = metadata;
            this.telemetryRequired = telemetryRequired;
            this.identity = identity;
            require(
                    telemetryRequired || identity == null,
                    "Black-box cleanup session unexpectedly carried private telemetry.");
        }

        @Override
        public void close() {
            bound.close();
        }
    }

    private static final class CleanupCheckpoint {

        final String requestId;
        final String jobTokenSha256;
        final String inputTargetSha256;
        final String outputTargetSha256;

        CleanupCheckpoint(
                String requestId,
                String jobTokenSha256,
                String inputTargetSha256,
                String outputTargetSha256) {
            this.requestId = requestId;
            this.jobTokenSha256 = jobTokenSha256;
            this.inputTargetSha256 = inputTargetSha256;
            this.outputTargetSha256 = outputTargetSha256;
        }

        boolean matches(CleanupCurrent current) {
            return requestId.equals(current.requestId)
                    && jobTokenSha256.equals(current.jobTokenSha256)
                    && inputTargetSha256.equals(
                            current.inputTargetSha256)
                    && outputTargetSha256.equals(
                            current.outputTargetSha256);
        }
    }

    private static final class ExternalPlusOneCleanupTelemetry {

        final CleanupPreAcknowledgement preAcknowledgement;
        final CleanupReceipt receipt;
        final CleanupCurrent current;
        final CleanupProcessIdentity currentIdentity;

        ExternalPlusOneCleanupTelemetry(
                CleanupPreAcknowledgement preAcknowledgement,
                CleanupReceipt receipt,
                CleanupCurrent current) {
            this.preAcknowledgement = preAcknowledgement;
            this.receipt = receipt;
            this.current = current;
            this.currentIdentity = current.identity;
        }

        static ExternalPlusOneCleanupTelemetry from(Bundle info) {
            require(info != null, "Cleanup telemetry compilerInfo was null.");
            require(
                    requiredBoolean(info, CLEANUP_KEY_ENABLED),
                    "External plus-one cleanup telemetry was not enabled.");

            CleanupPreAcknowledgement preAcknowledgement = null;
            if (requiredBoolean(
                    info, CLEANUP_KEY_PRE_ACK_AVAILABLE)) {
                String requestId =
                        requiredString(
                                info, CLEANUP_KEY_PRE_ACK_REQUEST_ID);
                String tokenSha256 =
                        requiredSha256(
                                info,
                                CLEANUP_KEY_PRE_ACK_JOB_TOKEN_SHA256);
                String inputTargetSha256 =
                        requiredSha256(
                                info,
                                CLEANUP_KEY_PRE_ACK_INPUT_TARGET_SHA256);
                String outputTargetSha256 =
                        requiredSha256(
                                info,
                                CLEANUP_KEY_PRE_ACK_OUTPUT_TARGET_SHA256);
                int inputFdCount =
                        requiredNonNegativeInt(
                                info,
                                CLEANUP_KEY_PRE_ACK_INPUT_EXACT_TARGET_FD_COUNT);
                int outputFdCount =
                        requiredNonNegativeInt(
                                info,
                                CLEANUP_KEY_PRE_ACK_OUTPUT_EXACT_TARGET_FD_COUNT);
                boolean activeLease =
                        requiredBoolean(
                                info, CLEANUP_KEY_PRE_ACK_ACTIVE_LEASE);
                CleanupProcessIdentity identity =
                        processIdentity(
                                info,
                                CLEANUP_KEY_PRE_ACK_PROCESS_PID,
                                CLEANUP_KEY_PRE_ACK_PROCESS_START_TIME_TICKS,
                                CLEANUP_KEY_PRE_ACK_PROCESS_NAME,
                                CLEANUP_KEY_PRE_ACK_PROCESS_EPOCH_SHA256);
                preAcknowledgement =
                        new CleanupPreAcknowledgement(
                                requestId,
                                tokenSha256,
                                inputTargetSha256,
                                outputTargetSha256,
                                inputFdCount,
                                outputFdCount,
                                activeLease,
                                identity);
            }

            CleanupReceipt receipt = null;
            if (requiredBoolean(
                    info, CLEANUP_KEY_RECEIPT_AVAILABLE)) {
                receipt =
                        new CleanupReceipt(
                                requiredString(
                                        info,
                                        CLEANUP_KEY_RECEIPT_REQUEST_ID),
                                requiredSha256(
                                        info,
                                        CLEANUP_KEY_RECEIPT_JOB_TOKEN_SHA256),
                                requiredSha256(
                                        info,
                                        CLEANUP_KEY_RECEIPT_INPUT_TARGET_SHA256),
                                requiredSha256(
                                        info,
                                        CLEANUP_KEY_RECEIPT_OUTPUT_TARGET_SHA256),
                                requiredBoolean(
                                        info,
                                        CLEANUP_KEY_RECEIPT_SNAPSHOT_AVAILABLE),
                                requiredBoolean(
                                        info,
                                        CLEANUP_KEY_RECEIPT_ACTIVE_LEASE_RELEASED),
                                requiredNonNegativeInt(
                                        info,
                                        CLEANUP_KEY_RECEIPT_INPUT_EXACT_TARGET_FD_COUNT),
                                requiredNonNegativeInt(
                                        info,
                                        CLEANUP_KEY_RECEIPT_OUTPUT_EXACT_TARGET_FD_COUNT),
                                requiredNonNegativeInt(
                                        info,
                                        CLEANUP_KEY_RECEIPT_PRIVATE_INPUT_NAMED_FILE_COUNT),
                                requiredNonNegativeInt(
                                        info,
                                        CLEANUP_KEY_RECEIPT_PRIVATE_INPUT_FD_COUNT),
                                requiredNonNegativeInt(
                                        info,
                                        CLEANUP_KEY_RECEIPT_PROJECT_SPOOL_NAMED_FILE_COUNT),
                                requiredNonNegativeInt(
                                        info,
                                        CLEANUP_KEY_RECEIPT_PROJECT_SPOOL_FD_COUNT),
                                requiredNonNegativeInt(
                                        info,
                                        CLEANUP_KEY_RECEIPT_PRIVATE_OUTPUT_NAMED_FILE_COUNT),
                                requiredNonNegativeInt(
                                        info,
                                        CLEANUP_KEY_RECEIPT_PRIVATE_OUTPUT_FD_COUNT),
                                requiredString(
                                        info,
                                        CLEANUP_KEY_RECEIPT_CURSOR_STATE),
                                requiredNonNegativeInt(
                                        info,
                                        CLEANUP_KEY_RECEIPT_CURSOR_OPENED_COUNT),
                                requiredNonNegativeInt(
                                        info,
                                        CLEANUP_KEY_RECEIPT_CURSOR_CLOSED_COUNT),
                                requiredBoolean(
                                        info,
                                        CLEANUP_KEY_RECEIPT_CURSOR_RELEASED),
                                requiredBoolean(
                                        info,
                                        CLEANUP_KEY_RECEIPT_CREATED_BEFORE_CALLBACK),
                                processIdentity(
                                        info,
                                        CLEANUP_KEY_RECEIPT_PROCESS_PID,
                                        CLEANUP_KEY_RECEIPT_PROCESS_START_TIME_TICKS,
                                        CLEANUP_KEY_RECEIPT_PROCESS_NAME,
                                        CLEANUP_KEY_RECEIPT_PROCESS_EPOCH_SHA256));
            }

            require(
                    requiredBoolean(
                            info, CLEANUP_KEY_CURRENT_AVAILABLE),
                    "Current cleanup telemetry was unavailable.");
            CleanupCurrent current =
                    new CleanupCurrent(
                            requiredString(
                                    info,
                                    CLEANUP_KEY_CURRENT_REQUEST_ID),
                            requiredSha256(
                                    info,
                                    CLEANUP_KEY_CURRENT_JOB_TOKEN_SHA256),
                            requiredSha256(
                                    info,
                                    CLEANUP_KEY_CURRENT_INPUT_TARGET_SHA256),
                            requiredSha256(
                                    info,
                                    CLEANUP_KEY_CURRENT_OUTPUT_TARGET_SHA256),
                            requiredNonNegativeInt(
                                    info,
                                    CLEANUP_KEY_CURRENT_ACTIVE_LEASE_COUNT),
                            requiredNonNegativeInt(
                                    info,
                                    CLEANUP_KEY_CURRENT_INPUT_EXACT_TARGET_FD_COUNT),
                            requiredNonNegativeInt(
                                    info,
                                    CLEANUP_KEY_CURRENT_OUTPUT_EXACT_TARGET_FD_COUNT),
                            requiredNonNegativeInt(
                                    info,
                                    CLEANUP_KEY_CURRENT_PRIVATE_INPUT_NAMED_FILE_COUNT),
                            requiredNonNegativeInt(
                                    info,
                                    CLEANUP_KEY_CURRENT_PRIVATE_INPUT_FD_COUNT),
                            requiredNonNegativeInt(
                                    info,
                                    CLEANUP_KEY_CURRENT_PROJECT_SPOOL_NAMED_FILE_COUNT),
                            requiredNonNegativeInt(
                                    info,
                                    CLEANUP_KEY_CURRENT_PROJECT_SPOOL_FD_COUNT),
                            requiredNonNegativeInt(
                                    info,
                                    CLEANUP_KEY_CURRENT_PRIVATE_OUTPUT_NAMED_FILE_COUNT),
                            requiredNonNegativeInt(
                                    info,
                                    CLEANUP_KEY_CURRENT_PRIVATE_OUTPUT_FD_COUNT),
                            processIdentity(
                                    info,
                                    CLEANUP_KEY_CURRENT_PROCESS_PID,
                                    CLEANUP_KEY_CURRENT_PROCESS_START_TIME_TICKS,
                                    CLEANUP_KEY_CURRENT_PROCESS_NAME,
                                    CLEANUP_KEY_CURRENT_PROCESS_EPOCH_SHA256));
            return new ExternalPlusOneCleanupTelemetry(
                    preAcknowledgement, receipt, current);
        }

        boolean currentIsAllZero() {
            return current.activeLeaseCount == 0
                    && current.inputExactTargetFdCount == 0
                    && current.outputExactTargetFdCount == 0
                    && current.privateInputNamedFileCount == 0
                    && current.privateInputFdCount == 0
                    && current.projectSpoolNamedFileCount == 0
                    && current.projectSpoolFdCount == 0
                    && current.privateOutputNamedFileCount == 0
                    && current.privateOutputFdCount == 0;
        }

        private static CleanupProcessIdentity processIdentity(
                Bundle info,
                String pidKey,
                String startTimeKey,
                String processNameKey,
                String epochSha256Key) {
            int pid = requiredNonNegativeInt(info, pidKey);
            require(pid > 0, "Cleanup telemetry process PID was invalid.");
            require(
                    info.containsKey(startTimeKey),
                    "Cleanup telemetry omitted " + startTimeKey + ".");
            long startTimeTicks = info.getLong(startTimeKey, -1L);
            require(
                    startTimeTicks > 0L,
                    "Cleanup telemetry process start time was invalid.");
            String processName = requiredString(info, processNameKey);
            require(
                    (CURRENT_PACKAGE + ":typescript_compiler")
                            .equals(processName),
                    "Cleanup telemetry process name was unexpected.");
            return new CleanupProcessIdentity(
                    pid,
                    startTimeTicks,
                    processName,
                    requiredSha256(info, epochSha256Key));
        }

        private static boolean requiredBoolean(
                Bundle info, String key) {
            require(
                    info.containsKey(key),
                    "Cleanup telemetry omitted " + key + ".");
            return info.getBoolean(key, false);
        }

        private static int requiredNonNegativeInt(
                Bundle info, String key) {
            require(
                    info.containsKey(key),
                    "Cleanup telemetry omitted " + key + ".");
            int value = info.getInt(key, -1);
            require(
                    value >= 0,
                    "Cleanup telemetry returned a negative count for "
                            + key
                            + ".");
            return value;
        }

        private static String requiredString(
                Bundle info, String key) {
            require(
                    info.containsKey(key),
                    "Cleanup telemetry omitted " + key + ".");
            String value = info.getString(key);
            require(
                    value != null && !value.isEmpty(),
                    "Cleanup telemetry returned an empty value for "
                            + key
                            + ".");
            return value;
        }

        private static String requiredSha256(
                Bundle info, String key) {
            String value = requiredString(info, key);
            require(
                    isLowerSha256(value),
                    "Cleanup telemetry returned a malformed SHA-256 for "
                            + key
                            + ".");
            return value;
        }
    }

    private static final class ExternalNaturalPhaseTimeoutTelemetry {

        final NaturalPhaseReceipt receipt;

        ExternalNaturalPhaseTimeoutTelemetry(NaturalPhaseReceipt receipt) {
            this.receipt = receipt;
        }

        static ExternalNaturalPhaseTimeoutTelemetry from(Bundle info) {
            require(info != null, "Natural phase telemetry compilerInfo was null.");
            require(
                    phaseRequiredBoolean(info, NATURAL_PHASE_KEY_ENABLED),
                    "External natural phase telemetry was not enabled.");
            if (!phaseRequiredBoolean(
                    info, NATURAL_PHASE_KEY_RECEIPT_AVAILABLE)) {
                return new ExternalNaturalPhaseTimeoutTelemetry(null);
            }

            String[] phases =
                    phaseRequiredStringArray(
                            info, NATURAL_PHASE_KEY_PHASE_VALUES);
            long[] sequenceTwo =
                    phaseRequiredLongArray(
                            info, NATURAL_PHASE_KEY_SEQUENCE_TWO_NANOS);
            long[] completed =
                    phaseRequiredLongArray(
                            info, NATURAL_PHASE_KEY_COMPLETED_NANOS);
            require(
                    phases.length == sequenceTwo.length
                            && phases.length == completed.length,
                    "Natural phase timing arrays have different lengths.");
            for (int index = 0; index < phases.length; index++) {
                require(
                        isCompilerPhase(phases[index]),
                        "Natural phase receipt contains an unsupported phase.");
                require(
                        sequenceTwo[index] > 0L
                                && completed[index] > sequenceTwo[index],
                        "Natural phase receipt timing is invalid.");
                if (index > 0) {
                    require(
                            phaseOrdinal(phases[index - 1])
                                    < phaseOrdinal(phases[index]),
                            "Natural phase receipt timings are out of order.");
                }
            }

            boolean terminationAvailable =
                    phaseRequiredBoolean(
                            info,
                            NATURAL_PHASE_KEY_TERMINATION_AVAILABLE);
            NaturalPhaseTermination termination =
                    terminationAvailable
                            ? new NaturalPhaseTermination(
                                    phaseRequiredString(
                                            info,
                                            NATURAL_PHASE_KEY_STOP_CAUSE),
                                    phaseRequiredString(
                                            info,
                                            NATURAL_PHASE_KEY_HOST_STOP_PHASE),
                                    phaseRequiredStringArray(
                                            info,
                                            NATURAL_PHASE_KEY_ENTERED_PHASES),
                                    phaseRequiredStringArray(
                                            info,
                                            NATURAL_PHASE_KEY_COMPLETED_PHASES),
                                    phaseRequiredString(
                                            info,
                                            NATURAL_PHASE_KEY_PAGE_TERMINATION_PHASE),
                                    phaseRequiredLong(
                                            info,
                                            NATURAL_PHASE_KEY_DEADLINE_NANOS),
                                    phaseRequiredLong(
                                            info,
                                            NATURAL_PHASE_KEY_STOP_SELECTED_NANOS),
                                    phaseRequiredLong(
                                            info,
                                            NATURAL_PHASE_KEY_DEADLINE_OVERSHOOT_NANOS),
                                    phaseRequiredInt(
                                            info,
                                            NATURAL_PHASE_KEY_SESSION_GENERATION),
                                    phaseRequiredInt(
                                            info,
                                            NATURAL_PHASE_KEY_WORKER_GENERATION),
                                    phaseRequiredInt(
                                            info,
                                            NATURAL_PHASE_KEY_PROGRESS_SEQUENCE),
                                    phaseRequiredInt(
                                            info,
                                            NATURAL_PHASE_KEY_PROGRESS_COMPLETED),
                                    phaseRequiredInt(
                                            info,
                                            NATURAL_PHASE_KEY_PROGRESS_TOTAL),
                                    phaseRequiredInt(
                                            info,
                                            NATURAL_PHASE_KEY_TERMINATED_WORKER),
                                    phaseRequiredInt(
                                            info,
                                            NATURAL_PHASE_KEY_REPLACEMENT_WORKER))
                            : null;
            NaturalPhaseInputTelemetry input =
                    new NaturalPhaseInputTelemetry(
                            phaseRequiredInt(
                                    info,
                                    NATURAL_PHASE_KEY_INPUT_SENT_FRAMES),
                            phaseRequiredInt(
                                    info,
                                    NATURAL_PHASE_KEY_INPUT_MAXIMUM_FRAME_BYTES),
                            phaseRequiredLong(
                                    info,
                                    NATURAL_PHASE_KEY_INPUT_ACCEPTED_BYTES),
                            phaseRequiredLong(
                                    info,
                                    NATURAL_PHASE_KEY_INPUT_TOTAL_BYTES),
                            phaseRequiredInt(
                                    info,
                                    NATURAL_PHASE_KEY_INPUT_FILE_INDEX),
                            phaseRequiredLong(
                                    info,
                                    NATURAL_PHASE_KEY_INPUT_FILE_OFFSET),
                            phaseRequiredString(
                                    info,
                                    NATURAL_PHASE_KEY_INPUT_STAGE),
                            phaseRequiredBoolean(
                                    info,
                                    NATURAL_PHASE_KEY_INPUT_COMPLETE),
                            phaseRequiredInt(
                                    info,
                                    NATURAL_PHASE_KEY_INPUT_OPENED_CURSORS),
                            phaseRequiredInt(
                                    info,
                                    NATURAL_PHASE_KEY_INPUT_CLOSED_CURSORS),
                            phaseRequiredBoolean(
                                    info,
                                    NATURAL_PHASE_KEY_INPUT_ACTIVE_CURSOR),
                            phaseRequiredBoolean(
                                    info,
                                    NATURAL_PHASE_KEY_INPUT_RELEASED));
            return new ExternalNaturalPhaseTimeoutTelemetry(
                    new NaturalPhaseReceipt(
                            phaseRequiredString(
                                    info, NATURAL_PHASE_KEY_REQUEST_ID),
                            phaseRequiredString(
                                    info, NATURAL_PHASE_KEY_HOST_REQUEST_ID),
                            phaseRequiredString(
                                    info, NATURAL_PHASE_KEY_OUTCOME),
                            phaseRequiredBoolean(
                                    info,
                                    NATURAL_PHASE_KEY_CREATED_BEFORE_CALLBACK),
                            phaseRequiredLong(
                                    info,
                                    NATURAL_PHASE_KEY_PROJECT_START_NANOS),
                            phases,
                            sequenceTwo,
                            completed,
                            termination,
                            input));
        }

        private static boolean isCompilerPhase(String phase) {
            return "parse".equals(phase)
                    || "semantic".equals(phase)
                    || "emit".equals(phase);
        }

        private static int phaseOrdinal(String phase) {
            if ("parse".equals(phase)) {
                return 1;
            }
            if ("semantic".equals(phase)) {
                return 2;
            }
            if ("emit".equals(phase)) {
                return 3;
            }
            throw new AssertionError("Unsupported compiler phase: " + phase);
        }

        private static boolean phaseRequiredBoolean(
                Bundle info, String key) {
            require(
                    info.containsKey(key),
                    "Natural phase telemetry omitted " + key + ".");
            return info.getBoolean(key, false);
        }

        private static int phaseRequiredInt(Bundle info, String key) {
            require(
                    info.containsKey(key),
                    "Natural phase telemetry omitted " + key + ".");
            int value = info.getInt(key, -1);
            require(value >= 0, "Natural phase telemetry " + key + " is invalid.");
            return value;
        }

        private static long phaseRequiredLong(Bundle info, String key) {
            require(
                    info.containsKey(key),
                    "Natural phase telemetry omitted " + key + ".");
            long value = info.getLong(key, -1L);
            require(value >= 0L, "Natural phase telemetry " + key + " is invalid.");
            return value;
        }

        private static String phaseRequiredString(
                Bundle info, String key) {
            require(
                    info.containsKey(key),
                    "Natural phase telemetry omitted " + key + ".");
            String value = info.getString(key);
            require(
                    value != null && !value.isEmpty(),
                    "Natural phase telemetry " + key + " is invalid.");
            return value;
        }

        private static String[] phaseRequiredStringArray(
                Bundle info, String key) {
            require(
                    info.containsKey(key),
                    "Natural phase telemetry omitted " + key + ".");
            String[] value = info.getStringArray(key);
            require(
                    value != null,
                    "Natural phase telemetry " + key + " is invalid.");
            return value;
        }

        private static long[] phaseRequiredLongArray(
                Bundle info, String key) {
            require(
                    info.containsKey(key),
                    "Natural phase telemetry omitted " + key + ".");
            long[] value = info.getLongArray(key);
            require(
                    value != null,
                    "Natural phase telemetry " + key + " is invalid.");
            return value;
        }
    }

    private static final class NaturalPhaseReceipt {

        final String requestId;
        final String hostRequestId;
        final String outcome;
        final boolean createdBeforeCallback;
        final long projectStartNanosSinceRequestStart;
        final String[] phases;
        final long[] sequenceTwoNanosSinceRequestStart;
        final long[] completedNanosSinceRequestStart;
        final NaturalPhaseTermination termination;
        final NaturalPhaseInputTelemetry input;

        NaturalPhaseReceipt(
                String requestId,
                String hostRequestId,
                String outcome,
                boolean createdBeforeCallback,
                long projectStartNanosSinceRequestStart,
                String[] phases,
                long[] sequenceTwoNanosSinceRequestStart,
                long[] completedNanosSinceRequestStart,
                NaturalPhaseTermination termination,
                NaturalPhaseInputTelemetry input) {
            this.requestId = requestId;
            this.hostRequestId = hostRequestId;
            this.outcome = outcome;
            this.createdBeforeCallback = createdBeforeCallback;
            this.projectStartNanosSinceRequestStart =
                    projectStartNanosSinceRequestStart;
            this.phases = phases;
            this.sequenceTwoNanosSinceRequestStart =
                    sequenceTwoNanosSinceRequestStart;
            this.completedNanosSinceRequestStart =
                    completedNanosSinceRequestStart;
            this.termination = termination;
            this.input = input;
            require(createdBeforeCallback, "Natural phase receipt followed callback.");
            require(
                    projectStartNanosSinceRequestStart >= 0L,
                    "Natural phase project start preceded request start.");
        }

        NaturalEmitTiming emitTiming() {
            for (int index = 0; index < phases.length; index++) {
                if ("emit".equals(phases[index])) {
                    return new NaturalEmitTiming(
                            sequenceTwoNanosSinceRequestStart[index],
                            completedNanosSinceRequestStart[index]);
                }
            }
            throw new AssertionError("Natural phase receipt omitted emit timing.");
        }

        NaturalSemanticTiming semanticTiming() {
            for (int index = 0; index < phases.length; index++) {
                if ("semantic".equals(phases[index])) {
                    return new NaturalSemanticTiming(
                            sequenceTwoNanosSinceRequestStart[index],
                            completedNanosSinceRequestStart[index]);
                }
            }
            throw new AssertionError("Natural phase receipt omitted semantic timing.");
        }

        NaturalParseTiming parseTiming() {
            for (int index = 0; index < phases.length; index++) {
                if ("parse".equals(phases[index])) {
                    return new NaturalParseTiming(
                            sequenceTwoNanosSinceRequestStart[index],
                            completedNanosSinceRequestStart[index]);
                }
            }
            throw new AssertionError("Natural phase receipt omitted parse timing.");
        }
    }

    private static final class NaturalPhaseTermination {

        final String stopCause;
        final String hostStopPhase;
        final String[] enteredPhases;
        final String[] completedPhases;
        final String pageTerminationPhase;
        final long deadlineNanosSinceRequestStart;
        final long stopSelectedNanosSinceRequestStart;
        final long deadlineOvershootNanos;
        final int sessionGeneration;
        final int workerGeneration;
        final int progressSequence;
        final int progressCompletedUnits;
        final int progressTotalUnits;
        final int terminatedWorkerGeneration;
        final int replacementWorkerGeneration;

        NaturalPhaseTermination(
                String stopCause,
                String hostStopPhase,
                String[] enteredPhases,
                String[] completedPhases,
                String pageTerminationPhase,
                long deadlineNanosSinceRequestStart,
                long stopSelectedNanosSinceRequestStart,
                long deadlineOvershootNanos,
                int sessionGeneration,
                int workerGeneration,
                int progressSequence,
                int progressCompletedUnits,
                int progressTotalUnits,
                int terminatedWorkerGeneration,
                int replacementWorkerGeneration) {
            this.stopCause = stopCause;
            this.hostStopPhase = hostStopPhase;
            this.enteredPhases = enteredPhases;
            this.completedPhases = completedPhases;
            this.pageTerminationPhase = pageTerminationPhase;
            this.deadlineNanosSinceRequestStart =
                    deadlineNanosSinceRequestStart;
            this.stopSelectedNanosSinceRequestStart =
                    stopSelectedNanosSinceRequestStart;
            this.deadlineOvershootNanos = deadlineOvershootNanos;
            this.sessionGeneration = sessionGeneration;
            this.workerGeneration = workerGeneration;
            this.progressSequence = progressSequence;
            this.progressCompletedUnits = progressCompletedUnits;
            this.progressTotalUnits = progressTotalUnits;
            this.terminatedWorkerGeneration = terminatedWorkerGeneration;
            this.replacementWorkerGeneration = replacementWorkerGeneration;
            require(
                    stopSelectedNanosSinceRequestStart
                                    - deadlineNanosSinceRequestStart
                            == deadlineOvershootNanos,
                    "Natural phase deadline overshoot is inconsistent.");
        }
    }

    private static final class NaturalPhaseInputTelemetry {

        final int sentFrameCount;
        final int maximumEncodedFrameBytes;
        final long acceptedPayloadBytes;
        final long totalPayloadBytes;
        final int fileIndex;
        final long fileOffset;
        final String stage;
        final boolean complete;
        final int openedCursorCount;
        final int closedCursorCount;
        final boolean activeCursor;
        final boolean released;

        NaturalPhaseInputTelemetry(
                int sentFrameCount,
                int maximumEncodedFrameBytes,
                long acceptedPayloadBytes,
                long totalPayloadBytes,
                int fileIndex,
                long fileOffset,
                String stage,
                boolean complete,
                int openedCursorCount,
                int closedCursorCount,
                boolean activeCursor,
                boolean released) {
            this.sentFrameCount = sentFrameCount;
            this.maximumEncodedFrameBytes = maximumEncodedFrameBytes;
            this.acceptedPayloadBytes = acceptedPayloadBytes;
            this.totalPayloadBytes = totalPayloadBytes;
            this.fileIndex = fileIndex;
            this.fileOffset = fileOffset;
            this.stage = stage;
            this.complete = complete;
            this.openedCursorCount = openedCursorCount;
            this.closedCursorCount = closedCursorCount;
            this.activeCursor = activeCursor;
            this.released = released;
        }
    }

    private static final class NaturalEmitTiming {

        final long sequenceTwoNanosSinceRequestStart;
        final long completedNanosSinceRequestStart;

        NaturalEmitTiming(
                long sequenceTwoNanosSinceRequestStart,
                long completedNanosSinceRequestStart) {
            this.sequenceTwoNanosSinceRequestStart =
                    sequenceTwoNanosSinceRequestStart;
            this.completedNanosSinceRequestStart =
                    completedNanosSinceRequestStart;
        }
    }

    private static final class NaturalEmitCalibration {

        final long minimumTimeoutMillis;
        final long maximumTimeoutMillis;
        final long timeoutMillis;

        NaturalEmitCalibration(
                long minimumTimeoutMillis,
                long maximumTimeoutMillis,
                long timeoutMillis) {
            this.minimumTimeoutMillis = minimumTimeoutMillis;
            this.maximumTimeoutMillis = maximumTimeoutMillis;
            this.timeoutMillis = timeoutMillis;
        }
    }

    private static final class NaturalSemanticTiming {

        final long sequenceTwoNanosSinceRequestStart;
        final long completedNanosSinceRequestStart;

        NaturalSemanticTiming(
                long sequenceTwoNanosSinceRequestStart,
                long completedNanosSinceRequestStart) {
            this.sequenceTwoNanosSinceRequestStart =
                    sequenceTwoNanosSinceRequestStart;
            this.completedNanosSinceRequestStart =
                    completedNanosSinceRequestStart;
        }
    }

    private static final class NaturalSemanticCalibration {

        final long minimumTimeoutMillis;
        final long maximumTimeoutMillis;
        final long timeoutMillis;

        NaturalSemanticCalibration(
                long minimumTimeoutMillis,
                long maximumTimeoutMillis,
                long timeoutMillis) {
            this.minimumTimeoutMillis = minimumTimeoutMillis;
            this.maximumTimeoutMillis = maximumTimeoutMillis;
            this.timeoutMillis = timeoutMillis;
        }
    }

    private static final class NaturalParseTiming {

        final long sequenceTwoNanosSinceRequestStart;
        final long completedNanosSinceRequestStart;

        NaturalParseTiming(
                long sequenceTwoNanosSinceRequestStart,
                long completedNanosSinceRequestStart) {
            this.sequenceTwoNanosSinceRequestStart =
                    sequenceTwoNanosSinceRequestStart;
            this.completedNanosSinceRequestStart =
                    completedNanosSinceRequestStart;
        }
    }

    private static final class NaturalParseCalibration {

        final long minimumTimeoutMillis;
        final long maximumTimeoutMillis;
        final long timeoutMillis;

        NaturalParseCalibration(
                long minimumTimeoutMillis,
                long maximumTimeoutMillis,
                long timeoutMillis) {
            this.minimumTimeoutMillis = minimumTimeoutMillis;
            this.maximumTimeoutMillis = maximumTimeoutMillis;
            this.timeoutMillis = timeoutMillis;
        }
    }

    private static final class CleanupPreAcknowledgement {

        final String requestId;
        final String jobTokenSha256;
        final String inputTargetSha256;
        final String outputTargetSha256;
        final int inputExactTargetFdCount;
        final int outputExactTargetFdCount;
        final boolean activeLease;
        final CleanupProcessIdentity identity;

        CleanupPreAcknowledgement(
                String requestId,
                String jobTokenSha256,
                String inputTargetSha256,
                String outputTargetSha256,
                int inputExactTargetFdCount,
                int outputExactTargetFdCount,
                boolean activeLease,
                CleanupProcessIdentity identity) {
            this.requestId = requestId;
            this.jobTokenSha256 = jobTokenSha256;
            this.inputTargetSha256 = inputTargetSha256;
            this.outputTargetSha256 = outputTargetSha256;
            this.inputExactTargetFdCount = inputExactTargetFdCount;
            this.outputExactTargetFdCount = outputExactTargetFdCount;
            this.activeLease = activeLease;
            this.identity = identity;
        }
    }

    private static final class CleanupReceipt {

        final String requestId;
        final String jobTokenSha256;
        final String inputTargetSha256;
        final String outputTargetSha256;
        final boolean snapshotAvailable;
        final boolean activeLeaseReleased;
        final int inputExactTargetFdCount;
        final int outputExactTargetFdCount;
        final int privateInputNamedFileCount;
        final int privateInputFdCount;
        final int projectSpoolNamedFileCount;
        final int projectSpoolFdCount;
        final int privateOutputNamedFileCount;
        final int privateOutputFdCount;
        final String cursorState;
        final int cursorOpenedCount;
        final int cursorClosedCount;
        final boolean cursorReleased;
        final boolean createdBeforeCallback;
        final CleanupProcessIdentity identity;

        CleanupReceipt(
                String requestId,
                String jobTokenSha256,
                String inputTargetSha256,
                String outputTargetSha256,
                boolean snapshotAvailable,
                boolean activeLeaseReleased,
                int inputExactTargetFdCount,
                int outputExactTargetFdCount,
                int privateInputNamedFileCount,
                int privateInputFdCount,
                int projectSpoolNamedFileCount,
                int projectSpoolFdCount,
                int privateOutputNamedFileCount,
                int privateOutputFdCount,
                String cursorState,
                int cursorOpenedCount,
                int cursorClosedCount,
                boolean cursorReleased,
                boolean createdBeforeCallback,
                CleanupProcessIdentity identity) {
            this.requestId = requestId;
            this.jobTokenSha256 = jobTokenSha256;
            this.inputTargetSha256 = inputTargetSha256;
            this.outputTargetSha256 = outputTargetSha256;
            this.snapshotAvailable = snapshotAvailable;
            this.activeLeaseReleased = activeLeaseReleased;
            this.inputExactTargetFdCount = inputExactTargetFdCount;
            this.outputExactTargetFdCount = outputExactTargetFdCount;
            this.privateInputNamedFileCount = privateInputNamedFileCount;
            this.privateInputFdCount = privateInputFdCount;
            this.projectSpoolNamedFileCount = projectSpoolNamedFileCount;
            this.projectSpoolFdCount = projectSpoolFdCount;
            this.privateOutputNamedFileCount = privateOutputNamedFileCount;
            this.privateOutputFdCount = privateOutputFdCount;
            this.cursorState = cursorState;
            this.cursorOpenedCount = cursorOpenedCount;
            this.cursorClosedCount = cursorClosedCount;
            this.cursorReleased = cursorReleased;
            this.createdBeforeCallback = createdBeforeCallback;
            this.identity = identity;
        }
    }

    private static final class CleanupCurrent {

        final String requestId;
        final String jobTokenSha256;
        final String inputTargetSha256;
        final String outputTargetSha256;
        final int activeLeaseCount;
        final int inputExactTargetFdCount;
        final int outputExactTargetFdCount;
        final int privateInputNamedFileCount;
        final int privateInputFdCount;
        final int projectSpoolNamedFileCount;
        final int projectSpoolFdCount;
        final int privateOutputNamedFileCount;
        final int privateOutputFdCount;
        final CleanupProcessIdentity identity;

        CleanupCurrent(
                String requestId,
                String jobTokenSha256,
                String inputTargetSha256,
                String outputTargetSha256,
                int activeLeaseCount,
                int inputExactTargetFdCount,
                int outputExactTargetFdCount,
                int privateInputNamedFileCount,
                int privateInputFdCount,
                int projectSpoolNamedFileCount,
                int projectSpoolFdCount,
                int privateOutputNamedFileCount,
                int privateOutputFdCount,
                CleanupProcessIdentity identity) {
            this.requestId = requestId;
            this.jobTokenSha256 = jobTokenSha256;
            this.inputTargetSha256 = inputTargetSha256;
            this.outputTargetSha256 = outputTargetSha256;
            this.activeLeaseCount = activeLeaseCount;
            this.inputExactTargetFdCount = inputExactTargetFdCount;
            this.outputExactTargetFdCount = outputExactTargetFdCount;
            this.privateInputNamedFileCount = privateInputNamedFileCount;
            this.privateInputFdCount = privateInputFdCount;
            this.projectSpoolNamedFileCount = projectSpoolNamedFileCount;
            this.projectSpoolFdCount = projectSpoolFdCount;
            this.privateOutputNamedFileCount = privateOutputNamedFileCount;
            this.privateOutputFdCount = privateOutputFdCount;
            this.identity = identity;
        }
    }

    private static final class CleanupProcessIdentity {

        final int pid;
        final long startTicks;
        final String processName;
        final String epochSha256;

        CleanupProcessIdentity(
                int pid,
                long startTicks,
                String processName,
                String epochSha256) {
            this.pid = pid;
            this.startTicks = startTicks;
            this.processName = processName;
            this.epochSha256 = epochSha256;
        }

        boolean sameAs(CleanupProcessIdentity other) {
            return other != null
                    && pid == other.pid
                    && startTicks == other.startTicks
                    && processName.equals(other.processName)
                    && epochSha256.equals(other.epochSha256);
        }

        @Override
        public String toString() {
            return "("
                    + pid
                    + ","
                    + startTicks
                    + ","
                    + processName
                    + ","
                    + epochSha256
                    + ")";
        }
    }

    private static final class CompilerMetadata {

        final int inputArchiveSchemaRevision;
        final int profileSchemaRevision;
        final int profileRevision;
        final int configSchemaRevision;
        final String configKind;
        final int runtimeCapabilitySchemaRevision;
        final String compilerVersion;
        final String distributionFingerprint;
        final String declarationsFingerprint;
        final String declarationProfileFingerprint;
        final String runtimeCapabilitiesFingerprint;

        CompilerMetadata(
                int inputArchiveSchemaRevision,
                int profileSchemaRevision,
                int profileRevision,
                int configSchemaRevision,
                String configKind,
                int runtimeCapabilitySchemaRevision,
                String compilerVersion,
                String distributionFingerprint,
                String declarationsFingerprint,
                String declarationProfileFingerprint,
                String runtimeCapabilitiesFingerprint) {
            this.inputArchiveSchemaRevision = inputArchiveSchemaRevision;
            this.profileSchemaRevision = profileSchemaRevision;
            this.profileRevision = profileRevision;
            this.configSchemaRevision = configSchemaRevision;
            this.configKind = configKind;
            this.runtimeCapabilitySchemaRevision =
                    runtimeCapabilitySchemaRevision;
            this.compilerVersion = compilerVersion;
            this.distributionFingerprint = distributionFingerprint;
            this.declarationsFingerprint = declarationsFingerprint;
            this.declarationProfileFingerprint =
                    declarationProfileFingerprint;
            this.runtimeCapabilitiesFingerprint = runtimeCapabilitiesFingerprint;
        }

        static CompilerMetadata from(Bundle info) {
            require(info != null, "Compiler returned null compilerInfo.");
            require(
                    info.getInt(
                                    TypeScriptCompilerContract.KEY_CONTRACT_VERSION,
                                    -1)
                            == TypeScriptCompilerContract.VERSION,
                    "compilerInfo contract version mismatch.");
            require(
                    TypeScriptCompilerContract.HASH.equals(
                            info.getString(
                                    TypeScriptCompilerContract.KEY_CONTRACT_HASH)),
                    "compilerInfo contract hash mismatch.");
            requireFalseCapability(
                    info,
                    TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE,
                    "compilerAvailable");
            requireFalseCapability(
                    info,
                    TypeScriptPluginCapabilityKeys.PROJECT_COMPILATION,
                    "projectCompilation");
            requireFalseCapability(
                    info,
                    TypeScriptPluginCapabilityKeys.SEMANTIC_DIAGNOSTICS,
                    "semanticDiagnostics");
            requireFalseCapability(
                    info,
                    TypeScriptPluginCapabilityKeys.SOURCE_MAP_V3,
                    "sourceMapV3");
            requireFalseCapability(
                    info,
                    TypeScriptPluginCapabilityKeys.NODE_RUNTIME_REQUIRED,
                    "nodeRuntimeRequired");
            String distribution =
                    info.getString(
                            TypeScriptCompilerContract
                                    .KEY_COMPILER_DISTRIBUTION_FINGERPRINT);
            String compilerVersion =
                    info.getString(
                            TypeScriptPluginCapabilityKeys.COMPILER_VERSION);
            String declarations =
                    info.getString(
                            TypeScriptCompilerContract
                                    .KEY_DECLARATIONS_FINGERPRINT);
            String declarationProfile =
                    info.getString(
                            TypeScriptR2Contract
                                    .KEY_DECLARATION_PROFILE_FINGERPRINT);
            String runtime =
                    info.getString(
                            TypeScriptCompilerContract
                                    .KEY_RUNTIME_CAPABILITIES_FINGERPRINT);
            if (compilerVersion == null
                    || compilerVersion.isEmpty()
                    || distribution == null
                    || declarations == null
                    || declarationProfile == null
                    || runtime == null) {
                return null;
            }
            require(
                    isLowerSha256(distribution),
                    "compilerInfo distribution fingerprint is malformed.");
            require(
                    isLowerSha256(declarations),
                    "compilerInfo declarations fingerprint is malformed.");
            require(
                    isLowerSha256(declarationProfile),
                    "compilerInfo declaration profile fingerprint is malformed.");
            require(
                    isLowerSha256(runtime),
                    "compilerInfo runtime fingerprint is malformed.");
            int profileSchema =
                    info.getInt(
                            TypeScriptPluginCapabilityKeys
                                    .PROFILE_SCHEMA_REVISION,
                            -1);
            int inputArchiveSchemaRevision =
                    info.getInt(
                            TypeScriptR2Contract
                                    .KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
                            -1);
            int outputArchiveSchemaRevision =
                    info.getInt(
                            TypeScriptR2Contract
                                    .KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION,
                            -1);
            int diagnosticsSchemaRevision =
                    info.getInt(
                            TypeScriptR2Contract
                                    .KEY_DIAGNOSTICS_SCHEMA_REVISION,
                            -1);
            int profileRevision =
                    info.getInt(TypeScriptR2Contract.KEY_PROFILE_REVISION, -1);
            int configSchemaRevision =
                    info.getInt(
                            TypeScriptR2Contract.KEY_CONFIG_SCHEMA_REVISION,
                            -1);
            String configKind =
                    info.getString(TypeScriptR2Contract.KEY_CONFIG_KIND);
            int runtimeSchema =
                    info.getInt(
                            TypeScriptPluginCapabilityKeys
                                    .RUNTIME_CAPABILITY_SCHEMA_REVISION,
                            -1);
            require(
                    inputArchiveSchemaRevision
                            == TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
                    "compilerInfo input archive schema revision mismatch.");
            require(
                    outputArchiveSchemaRevision
                            == TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION,
                    "compilerInfo output archive schema revision mismatch.");
            require(
                    diagnosticsSchemaRevision
                            == TypeScriptR2Contract.DIAGNOSTICS_SCHEMA_REVISION,
                    "compilerInfo diagnostics schema revision mismatch.");
            require(
                    profileSchema
                            == TypeScriptCompilerContract
                                    .PROFILE_SCHEMA_REVISION,
                    "compilerInfo profile schema revision mismatch.");
            require(
                    runtimeSchema
                            == TypeScriptCompilerContract
                                    .RUNTIME_CAPABILITY_SCHEMA_REVISION,
                    "compilerInfo runtime schema revision mismatch.");
            require(profileRevision > 0, "compilerInfo profile revision is missing.");
            require(
                    configSchemaRevision > 0,
                    "compilerInfo config schema revision is missing.");
            require(
                    "none".equals(configKind),
                    "compilerInfo config kind is unsupported.");
            return new CompilerMetadata(
                    inputArchiveSchemaRevision,
                    profileSchema,
                    profileRevision,
                    configSchemaRevision,
                    configKind,
                    runtimeSchema,
                    compilerVersion,
                    distribution,
                    declarations,
                    declarationProfile,
                    runtime);
        }

        R2FenceBoundedProjectFixture.CompilerAuthority toFixtureAuthority() {
            return new R2FenceBoundedProjectFixture.CompilerAuthority(
                    profileRevision,
                    configSchemaRevision,
                    compilerVersion,
                    distributionFingerprint,
                    declarationsFingerprint,
                    declarationProfileFingerprint,
                    runtimeCapabilitiesFingerprint);
        }

        void requireMatches(
                R2FenceBoundedProjectFixture.CompilerAuthority authority) {
            require(
                    profileRevision == authority.profileRevision,
                    "Admission profile revision changed after fixture generation.");
            require(
                    configSchemaRevision == authority.configSchemaRevision,
                    "Admission config schema changed after fixture generation.");
            require(
                    compilerVersion.equals(authority.compilerVersion),
                    "Admission compiler version changed after fixture generation.");
            require(
                    distributionFingerprint.equals(
                            authority.compilerDistributionFingerprint),
                    "Admission compiler distribution changed after fixture generation.");
            require(
                    declarationsFingerprint.equals(
                            authority.declarationsFingerprint),
                    "Admission declarations changed after fixture generation.");
            require(
                    declarationProfileFingerprint.equals(
                            authority.declarationProfileFingerprint),
                    "Admission declaration profile changed after fixture generation.");
            require(
                    runtimeCapabilitiesFingerprint.equals(
                            authority.runtimeCapabilitiesFingerprint),
                    "Admission runtime capabilities changed after fixture generation.");
        }

        private static void requireFalseCapability(
                Bundle info, String key, String label) {
            require(
                    info.containsKey(key) && !info.getBoolean(key, true),
                    "compilerInfo " + label + " preview capability must remain false.");
        }
    }

    private static final class Admission implements AutoCloseable {

        final BoundCompiler bound;
        final ITypeScriptCompilerPlugin compiler;
        final Bundle ticket;
        final String requestId;
        final CompilerMetadata metadata;

        Admission(
                BoundCompiler bound,
                ITypeScriptCompilerPlugin compiler,
                Bundle ticket,
                String requestId,
                CompilerMetadata metadata) {
            this.bound = bound;
            this.compiler = compiler;
            this.ticket = ticket;
            this.requestId = requestId;
            this.metadata = metadata;
        }

        void bestEffortCancel() {
            String token =
                    ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN);
            if (token == null || token.isEmpty()) {
                return;
            }
            try {
                compiler.cancelCompilation(cancellation(requestId, token));
            } catch (RemoteException ignored) {
                // The assertion that led here remains the primary failure.
            }
        }

        @Override
        public void close() {
            bound.close();
        }
    }

    private static final class BoundCompiler implements AutoCloseable {

        final Context context;
        final AwaitingConnection connection;
        final IBinder rawBinder;
        final ITypeScriptCompilerPlugin compiler;
        boolean closed;

        BoundCompiler(
                Context context,
                AwaitingConnection connection,
                IBinder rawBinder,
                ITypeScriptCompilerPlugin compiler) {
            this.context = context;
            this.connection = connection;
            this.rawBinder = rawBinder;
            this.compiler = compiler;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            context.unbindService(connection);
        }
    }

    private static final class AwaitingConnection implements ServiceConnection {

        private final CountDownLatch connected = new CountDownLatch(1);
        private volatile IBinder binder;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = service;
            connected.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            binder = null;
        }

        @Override
        public void onNullBinding(ComponentName name) {
            connected.countDown();
        }

        IBinder awaitBinder(long timeoutMs) throws RetryableBindingException {
            try {
                if (!connected.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                    throw new RetryableBindingException(
                            "Timed out waiting for compiler service connection.");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new RetryableBindingException(
                        "Interrupted while waiting for compiler service.");
            }
            return binder;
        }
    }

    private static final class RecordingCallback
            extends ITypeScriptCompilationCallback.Stub {

        private final CountDownLatch terminalSignal = new CountDownLatch(1);
        private final AtomicInteger completionCount = new AtomicInteger();
        private final AtomicReference<Bundle> firstTerminal =
                new AtomicReference<>();
        private final List<Bundle> progressEvents =
                Collections.synchronizedList(new ArrayList<>());
        private final List<String> callbackOrder =
                Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onProgress(Bundle progress) {
            progressEvents.add(progress == null ? null : new Bundle(progress));
            callbackOrder.add("progress");
        }

        @Override
        public void onCompleted(Bundle result) {
            int count = completionCount.incrementAndGet();
            if (count == 1) {
                firstTerminal.set(result == null ? null : new Bundle(result));
            }
            callbackOrder.add("terminal");
            terminalSignal.countDown();
        }

        Bundle awaitTerminal(long timeoutMs) throws InterruptedException {
            require(
                    terminalSignal.await(timeoutMs, TimeUnit.MILLISECONDS),
                    "Timed out waiting for the public R2 terminal callback.");
            Bundle result = firstTerminal.get();
            require(result != null, "Public R2 terminal callback was null.");
            return result;
        }

        void assertExactlyOneCompletion() {
            require(
                    completionCount.get() == 1,
                    "Expected exactly one public R2 terminal callback, observed "
                            + completionCount.get()
                            + ".");
        }

        List<Bundle> progressSnapshot() {
            synchronized (progressEvents) {
                List<Bundle> snapshot = new ArrayList<>(progressEvents.size());
                for (Bundle progress : progressEvents) {
                    snapshot.add(progress == null ? null : new Bundle(progress));
                }
                return Collections.unmodifiableList(snapshot);
            }
        }

        void assertTerminalFollowsAllProgress() {
            synchronized (callbackOrder) {
                require(
                        !callbackOrder.isEmpty()
                                && "terminal".equals(
                                        callbackOrder.get(
                                                callbackOrder.size() - 1)),
                        "Public terminal callback did not follow every progress callback: "
                                + callbackOrder);
                require(
                        Collections.frequency(callbackOrder, "terminal") == 1,
                        "Public callback order contained more than one terminal event.");
            }
        }
    }

    private static final class RetryableBindingException extends Exception {

        RetryableBindingException(String message) {
            super(message);
        }
    }
}
