package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import java.nio.charset.StandardCharsets
import org.json.JSONObject

data class CompilerDistributionMetadata(
    val version: String,
    val distributionFingerprint: String,
    val declarationsFingerprint: String,
    val generatorFingerprint: String,
) {

    companion object {

        fun load(context: Context): CompilerDistributionMetadata {
            val assetPath =
                "typescript/${BuildConfig.TYPESCRIPT_VERSION}/distribution.json"
            val manifestText = context.assets.open(assetPath).use { input ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            }
            check(manifestText.toByteArray(StandardCharsets.UTF_8).size <= MAX_MANIFEST_BYTES) {
                "Compiler distribution manifest exceeds the runtime metadata budget."
            }

            val manifest = JSONObject(manifestText)
            check(manifest.getInt("schemaVersion") == 1)
            val packageInfo = manifest.getJSONObject("package")
            check(packageInfo.getString("name") == "typescript")
            val version = packageInfo.getString("version")
            check(version == BuildConfig.TYPESCRIPT_VERSION) {
                "Compiler distribution version $version does not match " +
                    BuildConfig.TYPESCRIPT_VERSION
            }

            val generator = manifest.getJSONObject("generator")
            check(generator.getString("id") == "org.autojs.build.typescript-distribution")
            check(generator.getInt("revision") == 1)

            val components = manifest.getJSONObject("components")
            val compiler = components.getJSONObject("compiler")
            check(compiler.getString("path") == "lib/typescript.js")
            val standardLibraries = components.getJSONObject("standardLibraries")
            check(standardLibraries.getInt("fileCount") == 108)

            return CompilerDistributionMetadata(
                version = version,
                distributionFingerprint = manifest.requireFingerprint("fingerprint"),
                declarationsFingerprint =
                    standardLibraries.requireFingerprint("fingerprint"),
                generatorFingerprint = generator.requireFingerprint("fingerprint"),
            )
        }

        private fun JSONObject.requireFingerprint(key: String): String =
            getString(key).also { value ->
                check(value.matches(LOWER_SHA256)) {
                    "Compiler distribution field $key is not a lowercase SHA-256."
                }
            }

        private const val MAX_MANIFEST_BYTES = 256 * 1024
        private val LOWER_SHA256 = Regex("[0-9a-f]{64}")
    }
}
