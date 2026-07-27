package io.github.supermonster003.autojs6.plugin.typescript

import android.os.Bundle
import android.os.Parcel
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

internal data class BinderMetadataValidation(
    val primitiveMetadataOnly: Boolean,
    val metadataWithinBudget: Boolean,
)

internal object BinderMetadataValidator {

    fun validate(metadata: Bundle?): BinderMetadataValidation =
        BinderMetadataValidation(
            primitiveMetadataOnly = metadata.hasPrimitiveMetadataOnly(),
            metadataWithinBudget = metadata.isWithinMetadataBudget(),
        )

    @Suppress("DEPRECATION")
    private fun Bundle?.hasPrimitiveMetadataOnly(): Boolean = runCatching {
        this != null &&
            !hasFileDescriptors() &&
            keySet().size <= TypeScriptCompilerContract.MAX_METADATA_KEYS &&
            keySet().all { key ->
                when (val value = get(key)) {
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
                    null -> false
                    is BooleanArray ->
                        value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is ByteArray ->
                        value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is CharArray ->
                        value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is DoubleArray ->
                        value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is FloatArray ->
                        value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is IntArray ->
                        value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is LongArray ->
                        value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is ShortArray ->
                        value.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
                    is Array<*> ->
                        value.javaClass.componentType == String::class.java &&
                            value.size <=
                            TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS
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
}
