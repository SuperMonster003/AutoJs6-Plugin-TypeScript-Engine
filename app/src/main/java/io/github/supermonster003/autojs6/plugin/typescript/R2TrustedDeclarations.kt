package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import org.json.JSONObject

internal data class R2TrustedDeclarationFile(
    val archivePath: String,
    val fingerprintPath: String,
    val size: Long,
    val sha256: String,
    val text: String,
)

internal enum class R2DeclarationLayer(
    val manifestValue: String,
) {
    COMMON("common"),
    RHINO("rhino"),
    NODE("node"),
}

internal enum class R2DeclarationProfile(
    val manifestValue: String,
    val engineLayer: R2DeclarationLayer,
) {
    RHINO("rhino", R2DeclarationLayer.RHINO),
    NODE("node", R2DeclarationLayer.NODE),
}

/**
 * Plugin-authoritative declaration inventory.
 *
 * Caller archives cannot populate the reserved /declarations VFS root. These texts are loaded
 * from the signed, build-verified compiler distribution and their actual runtime bytes are checked
 * before this object is created.
 */
internal class R2TrustedDeclarations private constructor(
    /** Compatibility identity of the TypeScript standard-library component. */
    val fingerprint: String,
    val profile: R2DeclarationProfile?,
    val profileFingerprint: String,
    layerFingerprints: Map<String, String>,
    compilerRootNames: List<String>,
    files: List<R2TrustedDeclarationFile>,
) {

    val layerFingerprints: Map<String, String> =
        Collections.unmodifiableMap(LinkedHashMap(layerFingerprints))
    val compilerRootNames: List<String> =
        Collections.unmodifiableList(ArrayList(compilerRootNames))
    val files: List<R2TrustedDeclarationFile> =
        Collections.unmodifiableList(ArrayList(files))
    val filesByArchivePath: Map<String, R2TrustedDeclarationFile> =
        Collections.unmodifiableMap(
            LinkedHashMap<String, R2TrustedDeclarationFile>(files.size).apply {
                files.forEach { file -> put(file.archivePath, file) }
            },
        )

    companion object {

        /**
         * @param verifyTexts re-encodes every text and compares it with the declared size and
         * SHA-256. Loaders that just hashed the exact packaged bytes and decoded them with the
         * strict UTF-8 decoder pass `false`; every other caller keeps the default.
         */
        fun create(
            fingerprint: String,
            files: List<R2TrustedDeclarationFile>,
            verifyTexts: Boolean = true,
        ): R2TrustedDeclarations {
            R2Digests.requireLowerSha256(fingerprint, "Declarations fingerprint")
            require(files.isNotEmpty()) {
                "Trusted declarations inventory must not be empty."
            }
            val copiedFiles = validateAndCopyLayerFiles(
                R2DeclarationLayer.COMMON,
                files,
                verifyTexts,
            )

            val calculated = fingerprint(copiedFiles)
            require(R2Digests.equal(fingerprint, calculated)) {
                "Trusted declaration inventory does not match its fingerprint."
            }
            return R2TrustedDeclarations(
                fingerprint = fingerprint,
                profile = null,
                profileFingerprint = fingerprint,
                layerFingerprints = linkedMapOf(
                    R2DeclarationLayer.COMMON.manifestValue to fingerprint,
                ),
                compilerRootNames = emptyList(),
                files = copiedFiles,
            )
        }

        internal fun createProfile(
            profile: R2DeclarationProfile,
            commonFingerprint: String,
            commonFiles: List<R2TrustedDeclarationFile>,
            engineFingerprint: String,
            engineFiles: List<R2TrustedDeclarationFile>,
            engineEntryPoint: String,
            profileFingerprint: String,
            verifyTexts: Boolean = true,
        ): R2TrustedDeclarations {
            R2Digests.requireLowerSha256(
                commonFingerprint,
                "Common declaration fingerprint",
            )
            R2Digests.requireLowerSha256(
                engineFingerprint,
                "${profile.manifestValue} declaration fingerprint",
            )
            R2Digests.requireLowerSha256(
                profileFingerprint,
                "${profile.manifestValue} declaration profile fingerprint",
            )
            require(commonFiles.isNotEmpty()) {
                "Common trusted declarations inventory must not be empty."
            }
            require(engineFiles.isNotEmpty()) {
                "${profile.manifestValue} trusted declarations inventory must not be empty."
            }

            val copiedCommon = validateAndCopyLayerFiles(
                R2DeclarationLayer.COMMON,
                commonFiles,
                verifyTexts,
            )
            require(R2Digests.equal(commonFingerprint, fingerprint(copiedCommon))) {
                "Common trusted declaration inventory does not match its fingerprint."
            }
            val copiedEngine = validateAndCopyLayerFiles(
                profile.engineLayer,
                engineFiles,
                verifyTexts,
            )
            require(
                R2Digests.equal(
                    engineFingerprint,
                    layerFingerprint(profile.engineLayer, copiedEngine),
                ),
            ) {
                "${profile.manifestValue} trusted declaration inventory does not match " +
                    "its layer fingerprint."
            }
            validateCanonicalRelativePath(
                engineEntryPoint,
                "${profile.manifestValue} declaration entry point",
            )
            require(engineEntryPoint in copiedEngine.map(R2TrustedDeclarationFile::fingerprintPath)) {
                "${profile.manifestValue} declaration entry point is not in its layer."
            }
            val expectedEntryPrefix =
                "$DECLARATION_LAYER_ROOT${profile.engineLayer.manifestValue}/"
            require(
                engineEntryPoint.startsWith(expectedEntryPrefix) &&
                    engineEntryPoint.endsWith(DECLARATION_SUFFIX),
            ) {
                "${profile.manifestValue} declaration entry point is outside its layer."
            }

            val layerFingerprints = linkedMapOf(
                R2DeclarationLayer.COMMON.manifestValue to commonFingerprint,
                profile.engineLayer.manifestValue to engineFingerprint,
            )
            require(
                R2Digests.equal(
                    profileFingerprint,
                    calculateProfileFingerprint(profile, engineEntryPoint, layerFingerprints),
                ),
            ) {
                "${profile.manifestValue} declaration profile does not match its fingerprint."
            }
            val selectedFiles = ArrayList<R2TrustedDeclarationFile>(
                copiedCommon.size + copiedEngine.size,
            ).apply {
                addAll(copiedCommon)
                addAll(copiedEngine)
            }
            requireNoCrossLayerCollisions(selectedFiles)
            val compilerRoot = "/$ARCHIVE_ROOT" +
                engineEntryPoint.removePrefix(DECLARATION_LAYER_ROOT)

            return R2TrustedDeclarations(
                fingerprint = commonFingerprint,
                profile = profile,
                profileFingerprint = profileFingerprint,
                layerFingerprints = layerFingerprints,
                compilerRootNames = listOf(compilerRoot),
                files = selectedFiles,
            )
        }

        /**
         * Process-wide memo of the verified TypeScript standard-library inventory.
         *
         * The inventory is immutable and derived only from signed APK assets, so one verified
         * load serves the WebView host, the service capability bundle and every compilation job
         * for the rest of the process (startup-latency L1-1). Failures are not cached.
         */
        private val standardLibrariesCache = R2ProcessLoadCache<Unit, R2TrustedDeclarations> {
            loadTypeScriptStandardLibrariesUncached(requireNotNull(cacheContext))
        }

        private val profileCache = R2ProcessLoadCache<R2ProjectProfile, R2TrustedDeclarations> {
            profile ->
            loadForProfileUncached(requireNotNull(cacheContext), profile)
        }

        @Volatile
        private var cacheContext: Context? = null

        fun loadTypeScriptStandardLibraries(context: Context): R2TrustedDeclarations {
            cacheContext = context.applicationContext ?: context
            return standardLibrariesCache.get(Unit)
        }

        private fun loadTypeScriptStandardLibrariesUncached(
            context: Context,
        ): R2TrustedDeclarations {
            val assetRoot = "typescript/${BuildConfig.TYPESCRIPT_VERSION}"
            val manifestBytes = context.assets.open("$assetRoot/distribution.json").use { input ->
                readBounded(input, MAXIMUM_DISTRIBUTION_MANIFEST_BYTES)
            }
            check(manifestBytes.size.toLong() <= MAXIMUM_DISTRIBUTION_MANIFEST_BYTES) {
                "Compiler distribution manifest exceeds the runtime budget."
            }
            val manifest = JSONObject(
                R2Utf8.decode(manifestBytes, "Compiler distribution manifest"),
            )
            check(manifest.getInt("schemaVersion") == 1)
            val packageInfo = manifest.getJSONObject("package")
            check(packageInfo.getString("name") == "typescript")
            check(packageInfo.getString("version") == BuildConfig.TYPESCRIPT_VERSION)

            val component = manifest
                .getJSONObject("components")
                .getJSONObject("standardLibraries")
            val expectedFingerprint = component.getString("fingerprint")
            val expectedFileCount = component.getInt("fileCount")
            val expectedTotalBytes = component.getLong("totalBytes")
            check(component.getString("pattern") == "lib/lib*.d.ts")

            val files = ArrayList<R2TrustedDeclarationFile>(expectedFileCount)
            val manifestFiles = manifest.getJSONArray("files")
            for (index in 0 until manifestFiles.length()) {
                val file = manifestFiles.getJSONObject(index)
                val distributionPath = file.getString("path")
                if (!isTypeScriptStandardLibraryPath(distributionPath)) continue
                val expectedSize = file.getLong("size")
                val expectedSha256 = file.getString("sha256")
                val declarationBytes =
                    context.assets.open("$assetRoot/$distributionPath").use { input ->
                        readBounded(input, expectedSize)
                    }
                check(declarationBytes.size.toLong() == expectedSize) {
                    "Packaged declaration size mismatch: $distributionPath."
                }
                check(R2Digests.equal(expectedSha256, R2Digests.sha256(declarationBytes))) {
                    "Packaged declaration SHA-256 mismatch: $distributionPath."
                }
                files += R2TrustedDeclarationFile(
                    archivePath =
                        "$ARCHIVE_ROOT${distributionPath.removePrefix(DISTRIBUTION_LIB_ROOT)}",
                    fingerprintPath = distributionPath,
                    size = expectedSize,
                    sha256 = expectedSha256,
                    text = R2Utf8.decode(
                        declarationBytes,
                        "Packaged declaration $distributionPath",
                    ),
                )
            }
            check(files.size == expectedFileCount) {
                "Compiler distribution declaration count does not match its component."
            }
            check(files.sumOf(R2TrustedDeclarationFile::size) == expectedTotalBytes) {
                "Compiler distribution declaration bytes do not match its component."
            }

            // Every byte above was hashed against the distribution manifest and decoded with the
            // strict UTF-8 decoder, so the inventory does not need a second encode-and-hash pass.
            return create(expectedFingerprint, files, verifyTexts = false)
        }

        fun loadForProfile(
            context: Context,
            profile: R2ProjectProfile,
        ): R2TrustedDeclarations {
            cacheContext = context.applicationContext ?: context
            return profileCache.get(profile)
        }

        private fun loadForProfileUncached(
            context: Context,
            profile: R2ProjectProfile,
        ): R2TrustedDeclarations = when (profile) {
            R2ProjectProfile.PROVISIONAL_RHINO ->
                R2DeclarationDistributionLoader.load(
                    context = context,
                    profile = R2DeclarationProfile.RHINO,
                    common = loadTypeScriptStandardLibraries(context),
                )
            R2ProjectProfile.PROVISIONAL_NODE_NEXT ->
                R2DeclarationDistributionLoader.load(
                    context = context,
                    profile = R2DeclarationProfile.NODE,
                    common = loadTypeScriptStandardLibraries(context),
                )
        }

        /** Direct declaration-profile selector retained for focused distribution verification. */
        internal fun loadDeclarationProfileForTesting(
            context: Context,
            profile: R2DeclarationProfile,
        ): R2TrustedDeclarations = R2DeclarationDistributionLoader.load(
            context = context,
            profile = profile,
            common = loadTypeScriptStandardLibraries(context),
        )

        internal fun fingerprint(files: List<R2TrustedDeclarationFile>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            files.sortedBy(R2TrustedDeclarationFile::fingerprintPath).forEach { file ->
                digest.update(file.fingerprintPath.toByteArray(StandardCharsets.UTF_8))
                digest.update(0)
                digest.update(file.size.toString().toByteArray(StandardCharsets.US_ASCII))
                digest.update(0)
                digest.update(file.sha256.toByteArray(StandardCharsets.US_ASCII))
                digest.update('\n'.code.toByte())
            }
            return digest.digest().joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        }

        internal fun layerFingerprint(
            layer: R2DeclarationLayer,
            files: List<R2TrustedDeclarationFile>,
        ): String {
            require(layer != R2DeclarationLayer.COMMON) {
                "The common layer keeps the TypeScript standard-library component fingerprint."
            }
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(DECLARATION_LAYER_FINGERPRINT_DOMAIN.toByteArray(StandardCharsets.US_ASCII))
            digest.update("layer\u0000${layer.manifestValue}\n".toByteArray(StandardCharsets.US_ASCII))
            files.sortedBy(R2TrustedDeclarationFile::fingerprintPath).forEach { file ->
                digest.update("file\u0000".toByteArray(StandardCharsets.US_ASCII))
                digest.update(file.fingerprintPath.toByteArray(StandardCharsets.UTF_8))
                digest.update(0)
                digest.update(file.size.toString().toByteArray(StandardCharsets.US_ASCII))
                digest.update(0)
                digest.update(file.sha256.toByteArray(StandardCharsets.US_ASCII))
                digest.update('\n'.code.toByte())
            }
            return digest.digest().toLowerHex()
        }

        internal fun calculateProfileFingerprint(
            profile: R2DeclarationProfile,
            engineEntryPoint: String,
            layerFingerprints: Map<String, String>,
        ): String {
            val expectedLayerOrder = listOf(
                R2DeclarationLayer.COMMON.manifestValue,
                profile.engineLayer.manifestValue,
            )
            require(layerFingerprints.keys.toList() == expectedLayerOrder) {
                "${profile.manifestValue} declaration layers are not exact and ordered."
            }
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(
                DECLARATION_PROFILE_FINGERPRINT_DOMAIN.toByteArray(StandardCharsets.US_ASCII),
            )
            digest.update(
                "profile\u0000${profile.manifestValue}\n".toByteArray(StandardCharsets.US_ASCII),
            )
            expectedLayerOrder.forEach { layerId ->
                val fingerprint = requireNotNull(layerFingerprints[layerId])
                R2Digests.requireLowerSha256(
                    fingerprint,
                    "$layerId declaration layer fingerprint",
                )
                digest.update(
                    "layer\u0000$layerId\u0000$fingerprint\n".toByteArray(StandardCharsets.US_ASCII),
                )
            }
            digest.update(
                "entryPoint\u0000$engineEntryPoint\n".toByteArray(StandardCharsets.UTF_8),
            )
            return digest.digest().toLowerHex()
        }

        private fun validateAndCopyLayerFiles(
            layer: R2DeclarationLayer,
            files: List<R2TrustedDeclarationFile>,
            verifyTexts: Boolean,
        ): ArrayList<R2TrustedDeclarationFile> {
            val archivePaths = HashMap<String, String>(files.size)
            val fingerprintPaths = HashMap<String, String>(files.size)
            val copiedFiles = ArrayList<R2TrustedDeclarationFile>(files.size)
            files.forEach { file ->
                validateCanonicalRelativePath(file.archivePath, "Declaration archive path")
                require(file.archivePath.startsWith(ARCHIVE_ROOT)) {
                    "Trusted declaration must be below $ARCHIVE_ROOT: ${file.archivePath}."
                }
                require(file.archivePath.endsWith(DECLARATION_SUFFIX)) {
                    "Trusted declaration path must end with $DECLARATION_SUFFIX."
                }
                validateCanonicalRelativePath(file.fingerprintPath, "Declaration fingerprint path")
                val expectedArchivePath = when (layer) {
                    R2DeclarationLayer.COMMON -> {
                        require(file.fingerprintPath.startsWith(DISTRIBUTION_LIB_ROOT)) {
                            "Common declaration fingerprint path must be below " +
                                "$DISTRIBUTION_LIB_ROOT."
                        }
                        "$ARCHIVE_ROOT" +
                            file.fingerprintPath.removePrefix(DISTRIBUTION_LIB_ROOT)
                    }
                    R2DeclarationLayer.RHINO,
                    R2DeclarationLayer.NODE,
                    -> {
                        val expectedLayerRoot =
                            "$DECLARATION_LAYER_ROOT${layer.manifestValue}/"
                        require(file.fingerprintPath.startsWith(expectedLayerRoot)) {
                            "${layer.manifestValue} declaration fingerprint path must be below " +
                                "$expectedLayerRoot."
                        }
                        "$ARCHIVE_ROOT" +
                            file.fingerprintPath.removePrefix(DECLARATION_LAYER_ROOT)
                    }
                }
                require(file.fingerprintPath.endsWith(DECLARATION_SUFFIX)) {
                    "Trusted declaration fingerprint path must end with $DECLARATION_SUFFIX."
                }
                require(file.archivePath == expectedArchivePath) {
                    "Trusted declaration VFS path is not derived from its fingerprint path."
                }
                require(file.size >= 0L) {
                    "Trusted declaration size must be non-negative."
                }
                R2Digests.requireLowerSha256(
                    file.sha256,
                    "Trusted declaration SHA-256",
                )
                if (verifyTexts) {
                    val declarationBytes =
                        R2Utf8.encode(file.text, "Trusted declaration ${file.archivePath}")
                    try {
                        require(declarationBytes.size.toLong() == file.size) {
                            "Trusted declaration text size does not match: ${file.archivePath}."
                        }
                        require(
                            R2Digests.equal(file.sha256, R2Digests.sha256(declarationBytes)),
                        ) {
                            "Trusted declaration text SHA-256 does not match: " +
                                "${file.archivePath}."
                        }
                    } finally {
                        declarationBytes.fill(0)
                    }
                }

                val collisionKey = file.archivePath.asciiCaseFold()
                val previous = archivePaths.putIfAbsent(collisionKey, file.archivePath)
                require(previous == null) {
                    "Trusted declaration paths collide: $previous / ${file.archivePath}."
                }
                val fingerprintCollisionKey = file.fingerprintPath.asciiCaseFold()
                val previousFingerprintPath = fingerprintPaths.putIfAbsent(
                    fingerprintCollisionKey,
                    file.fingerprintPath,
                )
                require(previousFingerprintPath == null) {
                    "Trusted declaration fingerprint paths collide: " +
                        "$previousFingerprintPath / ${file.fingerprintPath}."
                }
                copiedFiles += file.copy()
            }
            return copiedFiles
        }

        private fun requireNoCrossLayerCollisions(files: List<R2TrustedDeclarationFile>) {
            val archivePaths = HashMap<String, String>(files.size)
            val fingerprintPaths = HashMap<String, String>(files.size)
            files.forEach { file ->
                val archiveCollision = archivePaths.putIfAbsent(
                    file.archivePath.asciiCaseFold(),
                    file.archivePath,
                )
                require(archiveCollision == null) {
                    "Selected declaration layers collide: $archiveCollision / ${file.archivePath}."
                }
                val fingerprintCollision = fingerprintPaths.putIfAbsent(
                    file.fingerprintPath.asciiCaseFold(),
                    file.fingerprintPath,
                )
                require(fingerprintCollision == null) {
                    "Selected declaration layer assets collide: " +
                        "$fingerprintCollision / ${file.fingerprintPath}."
                }
            }
        }

        private fun isTypeScriptStandardLibraryPath(path: String): Boolean =
            path.startsWith("${DISTRIBUTION_LIB_ROOT}lib") &&
                path.endsWith(DECLARATION_SUFFIX) &&
                '/' !in path.removePrefix(DISTRIBUTION_LIB_ROOT)

        private fun readBounded(
            input: InputStream,
            maximumBytes: Long,
        ): ByteArray {
            require(maximumBytes in 0L..Int.MAX_VALUE.toLong())
            val output = ByteArrayOutputStream(minOf(maximumBytes, 16L * 1024L).toInt())
            val buffer = ByteArray(16 * 1024)
            var byteCount = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) {
                    val singleByte = input.read()
                    if (singleByte < 0) break
                    check(byteCount < maximumBytes) {
                        "Packaged asset exceeds its byte budget."
                    }
                    output.write(singleByte)
                    byteCount++
                    continue
                }
                check(byteCount <= maximumBytes - read) {
                    "Packaged asset exceeds its byte budget."
                }
                output.write(buffer, 0, read)
                byteCount += read
            }
            return output.toByteArray()
        }

        private fun validateCanonicalRelativePath(
            path: String,
            label: String,
        ) {
            require(path.isNotEmpty() && path.length <= MAXIMUM_PATH_CHARS) {
                "$label is empty or too long."
            }
            require(path.all { character -> character.code in PRINTABLE_ASCII }) {
                "$label must contain only printable ASCII: $path."
            }
            require(
                !path.startsWith('/') &&
                    !path.hasAsciiDrivePrefix() &&
                    '\\' !in path,
            ) {
                "$label must be a canonical relative path: $path."
            }
            val segments = path.split('/')
            require(
                segments.size <= MAXIMUM_PATH_DEPTH &&
                    segments.all { segment ->
                        segment.isNotEmpty() && segment != "." && segment != ".."
                    },
            ) {
                "$label must be a canonical relative path: $path."
            }
        }

        private fun String.asciiCaseFold(): String = buildString(length) {
            this@asciiCaseFold.forEach { character ->
                append(
                    if (character in 'A'..'Z') {
                        (character.code + ASCII_CASE_OFFSET).toChar()
                    } else {
                        character
                    },
                )
            }
        }

        private fun String.hasAsciiDrivePrefix(): Boolean =
            length >= 2 &&
                this[0].let { character ->
                    character in 'A'..'Z' || character in 'a'..'z'
                } &&
                this[1] == ':'

        private const val ARCHIVE_ROOT = "declarations/"
        private const val DISTRIBUTION_LIB_ROOT = "lib/"
        private const val DECLARATION_LAYER_ROOT = "layers/"
        private const val DECLARATION_SUFFIX = ".d.ts"
        private const val DECLARATION_LAYER_FINGERPRINT_DOMAIN =
            "autojs6.typescript.declarations.layer.v1\n"
        private const val DECLARATION_PROFILE_FINGERPRINT_DOMAIN =
            "autojs6.typescript.declarations.profile.v1\n"
        private const val MAXIMUM_DISTRIBUTION_MANIFEST_BYTES = 256L * 1024L
        private const val MAXIMUM_PATH_CHARS = 4_096
        private const val MAXIMUM_PATH_DEPTH = 64
        private const val ASCII_CASE_OFFSET = 'a'.code - 'A'.code
        private val PRINTABLE_ASCII = ' '.code..'~'.code

        private fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
