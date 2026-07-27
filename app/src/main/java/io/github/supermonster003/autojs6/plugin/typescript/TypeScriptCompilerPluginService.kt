package io.github.supermonster003.autojs6.plugin.typescript

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

class TypeScriptCompilerPluginService : Service() {

    private lateinit var compilerHost: TypeScriptWebViewCompilerHost

    private val binder = object : ITypeScriptCompilerPlugin.Stub() {
        override fun onTransact(
            code: Int,
            data: Parcel,
            reply: Parcel?,
            flags: Int,
        ): Boolean {
            val originalPosition = data.dataPosition()
            val containsForbiddenFileDescriptors = try {
                when (code) {
                    TRANSACTION_PREWARM,
                    TRANSACTION_START_COMPILATION,
                    TRANSACTION_CANCEL_COMPILATION,
                    -> {
                        data.enforceInterface(ITypeScriptCompilerPlugin.DESCRIPTOR)
                        when (code) {
                            TRANSACTION_START_COMPILATION ->
                                data.firstTypedBundleContainsFileDescriptors()
                            else -> data.hasFileDescriptors()
                        }
                    }
                    else -> false
                }
            } finally {
                data.setDataPosition(originalPosition)
            }
            if (!containsForbiddenFileDescriptors) {
                return super.onTransact(code, data, reply, flags)
            }

            val response = requireNotNull(reply) {
                "Synchronous compiler transaction has no reply Parcel."
            }
            return when (code) {
                TRANSACTION_PREWARM -> {
                    response.writeBundleResult(
                        invalidMetadataPrewarmResult(),
                    )
                    true
                }
                TRANSACTION_START_COMPILATION -> {
                    response.writeBundleResult(
                        R0CompilationDecision(
                            requestId = "",
                            accepted = false,
                            errorCode = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                            errorMessage =
                                "Compilation request contains forbidden file descriptors.",
                        ).toBundle(),
                    )
                    true
                }
                TRANSACTION_CANCEL_COMPILATION -> {
                    response.writeNoException()
                    response.writeInt(0)
                    true
                }
                else -> error("Unexpected compiler transaction code: $code")
            }
        }

        override fun getContractVersion() = TypeScriptCompilerContract.VERSION

        override fun getContractHash() = TypeScriptCompilerContract.HASH

        override fun getCompilerInfo() =
            TypeScriptPluginMetadata.compilerInfo(compilerHost.snapshot())

        override fun prewarm(options: Bundle?): Bundle {
            val envelope = options.toPrewarmEnvelopeOrNull()
            val metadata = BinderMetadataValidator.validate(options)
            options.discardIfInvalid(metadata)
            val rejection = CompilerPrewarmGate.validate(
                CompilerPrewarmAdmission(
                    options = envelope,
                    primitiveMetadataOnly = metadata.primitiveMetadataOnly,
                    metadataWithinBudget = metadata.metadataWithinBudget,
                ),
            )
            if (rejection != null) {
                return TypeScriptPluginMetadata.compilerInfo(compilerHost.snapshot()).apply {
                    putString(
                        TypeScriptCompilerContract.KEY_ERROR_CODE,
                        rejection.errorCode,
                    )
                    putString(
                        TypeScriptCompilerContract.KEY_ERROR_MESSAGE,
                        rejection.errorMessage,
                    )
                }
            }

            val outcome = compilerHost.probe(requireNotNull(envelope).timeoutMs)
            return TypeScriptPluginMetadata.compilerInfo(compilerHost.snapshot()).apply {
                if (!outcome.succeeded) {
                    putString(
                        TypeScriptCompilerContract.KEY_ERROR_CODE,
                        outcome.errorCode,
                    )
                    putString(
                        TypeScriptCompilerContract.KEY_ERROR_MESSAGE,
                        outcome.errorMessage,
                    )
                }
            }
        }

        override fun startCompilation(
            request: Bundle?,
            inputArchive: ParcelFileDescriptor?,
            outputArchive: ParcelFileDescriptor?,
            callback: ITypeScriptCompilationCallback?,
        ): Bundle = try {
            val envelope = request.toEnvelopeOrNull()
            val metadata = BinderMetadataValidator.validate(request)
            request.discardIfInvalid(metadata)
            val archiveDescriptors = ArchiveFileDescriptorValidator.validate(
                inputArchive = inputArchive,
                outputArchive = outputArchive,
            )
            R0CompilationGate.reject(
                R0CompilationAdmission(
                    request = envelope,
                    primitiveMetadataOnly = metadata.primitiveMetadataOnly,
                    metadataWithinBudget = metadata.metadataWithinBudget,
                    inputArchiveValid = archiveDescriptors.inputArchiveValid,
                    outputArchiveValid = archiveDescriptors.outputArchiveValid,
                    callbackValid = callback?.asBinder()?.isBinderAlive == true,
                    backingFilesDistinct = archiveDescriptors.backingFilesDistinct,
                ),
            ).toBundle()
        } finally {
            inputArchive.closeQuietly()
            outputArchive.closeQuietly()
        }

        override fun cancelCompilation(cancellation: Bundle?): Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        TypeScriptWebViewCompilerHost.prepareCompilerProcess()
        compilerHost = TypeScriptWebViewCompilerHost(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        if (::compilerHost.isInitialized) {
            compilerHost.close()
        }
        super.onDestroy()
    }

    private fun invalidMetadataPrewarmResult() =
        TypeScriptPluginMetadata.compilerInfo(compilerHost.snapshot()).apply {
            putString(
                TypeScriptCompilerContract.KEY_ERROR_CODE,
                TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
            )
            putString(
                TypeScriptCompilerContract.KEY_ERROR_MESSAGE,
                "Prewarm options contain forbidden file descriptors.",
            )
        }

    private fun Parcel.firstTypedBundleContainsFileDescriptors(): Boolean {
        val present = readInt()
        if (present == 0) return false
        check(present == 1) {
            "Compiler transaction has an invalid Bundle presence marker."
        }

        val bundleStart = dataPosition()
        val payloadLength = readInt()
        check(payloadLength >= 0 && payloadLength % Int.SIZE_BYTES == 0) {
            "Compiler transaction has an invalid Bundle payload length."
        }
        val bundleLength = if (payloadLength == 0) {
            Int.SIZE_BYTES
        } else {
            Math.addExact(payloadLength, Int.SIZE_BYTES * 2)
        }
        check(bundleStart <= dataSize() - bundleLength) {
            "Compiler transaction has a truncated Bundle payload."
        }

        val bundleParcel = Parcel.obtain()
        return try {
            bundleParcel.appendFrom(this, bundleStart, bundleLength)
            bundleParcel.hasFileDescriptors()
        } finally {
            bundleParcel.recycle()
        }
    }

    private fun Parcel.writeBundleResult(result: Bundle) {
        writeNoException()
        writeInt(1)
        result.writeToParcel(this, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
    }

    private fun Bundle?.toPrewarmEnvelopeOrNull(): CompilerPrewarmEnvelope? = runCatching {
        this ?: return null
        CompilerPrewarmEnvelope(
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

    private fun R0CompilationDecision.toBundle() = Bundle().apply {
        putInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, TypeScriptCompilerContract.VERSION)
        putString(TypeScriptCompilerContract.KEY_CONTRACT_HASH, TypeScriptCompilerContract.HASH)
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        putBoolean(TypeScriptCompilerContract.KEY_ACCEPTED, accepted)
        putString(TypeScriptCompilerContract.KEY_ERROR_CODE, errorCode)
        putString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE, errorMessage)
    }

    private fun Bundle?.discardIfInvalid(validation: BinderMetadataValidation) {
        if (
            validation.primitiveMetadataOnly &&
            validation.metadataWithinBudget
        ) {
            return
        }
        runCatching { this?.clear() }
    }

    private fun ParcelFileDescriptor?.closeQuietly() {
        runCatching { this?.close() }
    }

    private companion object {
        const val TRANSACTION_PREWARM = IBinder.FIRST_CALL_TRANSACTION + 3
        const val TRANSACTION_START_COMPILATION = IBinder.FIRST_CALL_TRANSACTION + 4
        const val TRANSACTION_CANCEL_COMPILATION = IBinder.FIRST_CALL_TRANSACTION + 5
    }
}
