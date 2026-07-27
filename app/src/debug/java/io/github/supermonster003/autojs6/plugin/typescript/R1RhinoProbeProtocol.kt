package io.github.supermonster003.autojs6.plugin.typescript

/**
 * Debug-only, primitive-Bundle Messenger contract for the R1 Rhino feasibility probe.
 *
 * This is deliberately not part of the public compiler API. A probe result can only report
 * unsupported; it cannot select a compiler backend or make Rhino fallback-eligible.
 */
object R1RhinoProbeProtocol {

    const val MESSAGE_PROBE = 1
    const val MESSAGE_CANCEL = 2
    const val MESSAGE_STARTED = 3
    const val MESSAGE_RESULT = 4

    const val MODE_PARSE = "parse"
    const val MODE_UNCOOPERATIVE_CANCELLATION = "uncooperative_cancellation"

    const val PHASE_ADMISSION = "admission"
    const val PHASE_RHINO_CLASS_LOAD = "rhino_class_load"
    const val PHASE_CONTEXT_CONFIGURATION = "context_configuration"
    const val PHASE_COMPILER_ASSET_DIGEST = "compiler_asset_digest"
    const val PHASE_COMPILER_PARSE = "compiler_parse"
    const val PHASE_UNEXPECTED_SUCCESS = "unexpected_success"
    const val PHASE_CANCELLED = "cancelled"

    const val KEY_REQUEST_ID = "requestId"
    const val KEY_MODE = "mode"
    const val KEY_SUPPORTED = "supported"
    const val KEY_FALLBACK_ELIGIBLE = "fallbackEligible"
    const val KEY_PHASE = "phase"
    const val KEY_PID = "pid"
    const val KEY_ARTIFACT_NAME = "artifactName"
    const val KEY_ARTIFACT_SHA256 = "artifactSha256"
    const val KEY_RHINO_SOURCE_COMMIT = "rhinoSourceCommit"
    const val KEY_AUTOJS6_CONSUMER_COMMIT = "autoJs6ConsumerCommit"
    const val KEY_TYPESCRIPT_VERSION = "typeScriptVersion"
    const val KEY_COMPILER_ASSET_SHA256 = "compilerAssetSha256"
    const val KEY_DISTRIBUTION_FINGERPRINT = "distributionFingerprint"
    const val KEY_DECLARATIONS_FINGERPRINT = "declarationsFingerprint"
    const val KEY_LANGUAGE_VERSION = "languageVersion"
    const val KEY_OPTIMIZATION_LEVEL = "optimizationLevel"
    const val KEY_SECURITY_PROFILE = "securityProfile"
    const val KEY_ERROR_CLASS = "errorClass"
    const val KEY_ERROR_MESSAGE = "errorMessage"
    const val KEY_ERROR_SOURCE = "errorSource"
    const val KEY_ERROR_LINE = "errorLine"
    const val KEY_ERROR_COLUMN = "errorColumn"
    const val KEY_ERROR_LINE_SOURCE = "errorLineSource"
    const val KEY_ELAPSED_MS = "elapsedMs"
    const val KEY_BASELINE_PSS_KIB = "baselinePssKiB"
    const val KEY_FINAL_PSS_KIB = "finalPssKiB"
    const val KEY_CANCEL_REQUESTED = "cancelRequested"

    const val EXPECTED_LANGUAGE_VERSION_ES6 = 200
    const val INTERPRETED_OPTIMIZATION_LEVEL = -1
    const val SECURITY_PROFILE_SAFE_DENY_ALL_CLASSES =
        "safe-standard-objects+deny-all-class-shutter"
}
