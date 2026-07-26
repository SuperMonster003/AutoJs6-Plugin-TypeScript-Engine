package io.github.supermonster003.autojs6.plugin.typescript

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

class TypeScriptCompilerPluginService : Service() {

    private val binder = object : ITypeScriptCompilerPlugin.Stub() {
        override fun getContractVersion() = TypeScriptCompilerContract.VERSION

        override fun getContractHash() = TypeScriptCompilerContract.HASH

        override fun getCompilerInfo() = TypeScriptPluginMetadata.compilerInfo()

        override fun prewarm(options: Bundle?): Bundle =
            TypeScriptPluginMetadata.compilerInfo().apply {
                val decision = R0PrewarmGate.reject(
                    R0PrewarmAdmission(
                        options = options.toPrewarmEnvelopeOrNull(),
                        primitiveMetadataOnly = options.hasPrimitiveMetadataOnly(),
                        metadataWithinBudget = options.isWithinMetadataBudget(),
                    ),
                )
                putString(TypeScriptCompilerContract.KEY_ERROR_CODE, decision.errorCode)
                putString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE, decision.errorMessage)
            }

        override fun startCompilation(
            request: Bundle?,
            inputArchive: ParcelFileDescriptor?,
            outputArchive: ParcelFileDescriptor?,
            callback: ITypeScriptCompilationCallback?,
        ): Bundle = try {
            R0CompilationGate.reject(
                R0CompilationAdmission(
                    request = request.toEnvelopeOrNull(),
                    primitiveMetadataOnly = request.hasPrimitiveMetadataOnly(),
                    metadataWithinBudget = request.isWithinMetadataBudget(),
                    inputArchiveValid = inputArchive.isValid(),
                    outputArchiveValid = outputArchive.isValid(),
                    callbackValid = callback?.asBinder()?.isBinderAlive == true,
                ),
            ).toBundle()
        } finally {
            inputArchive.closeQuietly()
            outputArchive.closeQuietly()
        }

        override fun cancelCompilation(cancellation: Bundle?): Boolean = false
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun Bundle?.toPrewarmEnvelopeOrNull(): R0PrewarmEnvelope? = runCatching {
        this ?: return null
        R0PrewarmEnvelope(
            contractVersion = getInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, -1),
            contractHash = getString(TypeScriptCompilerContract.KEY_CONTRACT_HASH).orEmpty(),
            timeoutMs = getLong(TypeScriptCompilerContract.KEY_TIMEOUT_MS, -1L),
        )
    }.getOrNull()

    private fun Bundle?.toEnvelopeOrNull(): R0CompilationEnvelope? = runCatching {
        this ?: return null
        R0CompilationEnvelope(
            contractVersion = getInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, -1),
            contractHash = getString(TypeScriptCompilerContract.KEY_CONTRACT_HASH).orEmpty(),
            requestId = getString(TypeScriptCompilerContract.KEY_REQUEST_ID).orEmpty(),
            projectId = getString(TypeScriptCompilerContract.KEY_PROJECT_ID).orEmpty(),
            entrySourceUri = getString(TypeScriptCompilerContract.KEY_ENTRY_SOURCE_URI).orEmpty(),
            targetProfile = getString(TypeScriptCompilerContract.KEY_TARGET_PROFILE).orEmpty(),
            compilationMode = getString(TypeScriptCompilerContract.KEY_COMPILATION_MODE).orEmpty(),
            compilerOptionsJson = getString(
                TypeScriptCompilerContract.KEY_COMPILER_OPTIONS_JSON,
                "{}",
            ),
            inputArchiveTransportVersion = getInt(
                TypeScriptCompilerContract.KEY_INPUT_ARCHIVE_TRANSPORT_VERSION,
                -1,
            ),
            outputArchiveTransportVersion = getInt(
                TypeScriptCompilerContract.KEY_OUTPUT_ARCHIVE_TRANSPORT_VERSION,
                -1,
            ),
            timeoutMs = getLong(TypeScriptCompilerContract.KEY_TIMEOUT_MS, -1L),
        )
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun Bundle?.hasPrimitiveMetadataOnly(): Boolean = runCatching {
        this != null &&
            !hasFileDescriptors() &&
            keySet().size <= TypeScriptCompilerContract.MAX_METADATA_KEYS &&
            keySet().all { key ->
                when (val value = get(key)) {
                    null,
                    is Boolean,
                    is Byte,
                    is Char,
                    is Double,
                    is Float,
                    is Int,
                    is Long,
                    is Short,
                    is String,
                    -> true
                    is BooleanArray -> value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is ByteArray -> value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is CharArray -> value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is DoubleArray -> value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is FloatArray -> value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is IntArray -> value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is LongArray -> value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is ShortArray -> value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is Array<*> ->
                        value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS &&
                            value.all { it == null || it is String }
                    else -> false
                }
            }
    }.getOrDefault(false)

    private fun Bundle?.isWithinMetadataBudget(): Boolean = runCatching {
        this ?: return false
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(this)
            parcel.dataSize() <= TypeScriptCompilerContract.MAX_METADATA_BYTES
        } finally {
            parcel.recycle()
        }
    }.getOrDefault(false)

    private fun R0CompilationDecision.toBundle() = Bundle().apply {
        putInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, TypeScriptCompilerContract.VERSION)
        putString(TypeScriptCompilerContract.KEY_CONTRACT_HASH, TypeScriptCompilerContract.HASH)
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        putBoolean(TypeScriptCompilerContract.KEY_ACCEPTED, accepted)
        putString(TypeScriptCompilerContract.KEY_ERROR_CODE, errorCode)
        putString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE, errorMessage)
    }

    private fun ParcelFileDescriptor?.isValid() = runCatching {
        this != null && fileDescriptor.valid()
    }.getOrDefault(false)

    private fun ParcelFileDescriptor?.closeQuietly() {
        runCatching { this?.close() }
    }
}
