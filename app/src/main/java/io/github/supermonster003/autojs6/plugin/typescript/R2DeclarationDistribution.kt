package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Strict runtime verifier for the generated C8 declaration distribution. */
internal object R2DeclarationDistributionLoader {

    fun load(
        context: Context,
        profile: R2DeclarationProfile,
        common: R2TrustedDeclarations,
    ): R2TrustedDeclarations {
        val source = AndroidDeclarationAssetSource(context)
        val compilerIdentity = readCompilerDistributionIdentity(context)
        return load(
            source = source,
            profile = profile,
            common = common,
            compilerDistributionFingerprint = compilerIdentity.distributionFingerprint,
            standardLibrariesFingerprint = compilerIdentity.standardLibrariesFingerprint,
        )
    }

    internal fun loadForTesting(
        profile: R2DeclarationProfile,
        common: R2TrustedDeclarations,
        manifestBytes: ByteArray,
        packagedFiles: Map<String, ByteArray>,
        compilerDistributionFingerprint: String,
    ): R2TrustedDeclarations {
        val files = LinkedHashMap<String, ByteArray>(packagedFiles.size + 1)
        files[MANIFEST_PATH] = manifestBytes.copyOf()
        packagedFiles.forEach { (path, bytes) -> files[path] = bytes.copyOf() }
        return load(
            source = MapDeclarationAssetSource(files),
            profile = profile,
            common = common,
            compilerDistributionFingerprint = compilerDistributionFingerprint,
            standardLibrariesFingerprint = common.fingerprint,
        )
    }

    internal fun validateRhinoSourceDescriptorForTesting(bytes: ByteArray) {
        RhinoDeclarationSource.parse(R2StrictJson.parseObject(bytes))
    }

    internal fun validateReleaseDescriptorForTesting(bytes: ByteArray) {
        DeclarationRelease.parse(R2StrictJson.parseObject(bytes))
    }

    internal fun validateNodeSourceDescriptorForTesting(bytes: ByteArray) {
        NodeDeclarationSource.parse(
            R2StrictJson.parseObject(bytes),
            DeclarationRelease.parse(
                mapOf(
                    "package" to EXPECTED_RELEASE_PACKAGE,
                    "version" to BuildConfig.VERSION_NAME,
                ),
            ),
        )
    }

    internal fun validateCommonSourceAndBindingForTesting(
        sourceBytes: ByteArray,
        bindingBytes: ByteArray,
        common: R2TrustedDeclarations,
        compilerDistributionFingerprint: String,
    ) {
        val source = CommonDeclarationSource.parse(
            value = R2StrictJson.parseObject(sourceBytes),
            common = common,
            compilerDistributionFingerprint = compilerDistributionFingerprint,
        )
        validateCommonBinding(bindingBytes, source, common)
    }

    internal fun validatePackagedPathsForTesting(paths: List<String>) {
        requireStrictPathInventory(paths, "packaged declaration test paths")
    }

    private fun load(
        source: DeclarationAssetSource,
        profile: R2DeclarationProfile,
        common: R2TrustedDeclarations,
        compilerDistributionFingerprint: String,
        standardLibrariesFingerprint: String,
    ): R2TrustedDeclarations {
        require(common.profile == null && common.compilerRootNames.isEmpty()) {
            "The C8 common authority must be the standalone TypeScript standard-library set."
        }
        require(R2Digests.equal(common.fingerprint, standardLibrariesFingerprint)) {
            "The loaded TypeScript standard libraries do not match their distribution metadata."
        }
        val manifestBytes = source.read(MANIFEST_PATH, MAXIMUM_MANIFEST_BYTES)
        val distribution = try {
            R2DeclarationDistribution.parse(
                bytes = manifestBytes,
                common = common,
                compilerDistributionFingerprint = compilerDistributionFingerprint,
            )
        } finally {
            manifestBytes.fill(0)
        }

        val expectedPaths = distribution.files.mapTo(LinkedHashSet()) { file -> file.path }
        expectedPaths += MANIFEST_PATH
        val actualPaths = source.paths()
        require(actualPaths == expectedPaths) {
            "Packaged declaration distribution is not exactly covered by its manifest; " +
                "missing=${expectedPaths - actualPaths}, extra=${actualPaths - expectedPaths}."
        }

        val selectedLayer = distribution.layers.getValue(profile.engineLayer.manifestValue)
        val selectedPaths = selectedLayer.files.toHashSet()
        val selectedFiles = ArrayList<R2TrustedDeclarationFile>(selectedPaths.size)
        var bindingVerified = false
        distribution.files.forEach { descriptor ->
            val bytes = source.read(descriptor.path, descriptor.size)
            try {
                require(bytes.size.toLong() == descriptor.size) {
                    "Packaged declaration asset size mismatch: ${descriptor.path}."
                }
                require(R2Digests.equal(descriptor.sha256, R2Digests.sha256(bytes))) {
                    "Packaged declaration asset SHA-256 mismatch: ${descriptor.path}."
                }
                when {
                    descriptor.path == distribution.sources.common.bindingFile -> {
                        validateCommonBinding(
                            bytes = bytes,
                            source = distribution.sources.common,
                            common = common,
                        )
                        bindingVerified = true
                    }
                    descriptor.path in selectedPaths -> {
                        val archivePath = "declarations/" +
                            descriptor.path.removePrefix(DECLARATION_LAYER_ROOT)
                        selectedFiles += R2TrustedDeclarationFile(
                            archivePath = archivePath,
                            fingerprintPath = descriptor.path,
                            size = descriptor.size,
                            sha256 = descriptor.sha256,
                            text = R2Utf8.decode(
                                bytes,
                                "Packaged declaration ${descriptor.path}",
                            ),
                        )
                    }
                }
            } finally {
                bytes.fill(0)
            }
        }
        require(bindingVerified) { "The common declaration binding was not verified." }
        require(selectedFiles.map(R2TrustedDeclarationFile::fingerprintPath) == selectedLayer.files) {
            "The selected declaration layer was not materialized exactly."
        }

        val selectedProfile = distribution.profiles.getValue(profile.manifestValue)
        return R2TrustedDeclarations.createProfile(
            profile = profile,
            commonFingerprint = common.fingerprint,
            commonFiles = common.files,
            engineFingerprint = selectedLayer.fingerprint,
            engineFiles = selectedFiles,
            engineEntryPoint = requireNotNull(selectedLayer.entryPoint),
            profileFingerprint = selectedProfile.fingerprint,
        )
    }

    private fun readCompilerDistributionIdentity(context: Context): CompilerDistributionIdentity {
        val path =
            "typescript/${BuildConfig.TYPESCRIPT_VERSION}/distribution.json"
        val bytes = context.assets.open(path).use { input ->
            readBounded(input, MAXIMUM_COMPILER_MANIFEST_BYTES)
        }
        try {
            val root = R2StrictJson.parseObject(bytes)
            val packageInfo = root.requireObject("package")
            require(packageInfo.requireString("name") == "typescript")
            require(packageInfo.requireString("version") == BuildConfig.TYPESCRIPT_VERSION)
            val components = root.requireObject("components")
            val libraries = components.requireObject("standardLibraries")
            return CompilerDistributionIdentity(
                distributionFingerprint = root.requireLowerSha256("fingerprint"),
                standardLibrariesFingerprint = libraries.requireLowerSha256("fingerprint"),
            )
        } finally {
            bytes.fill(0)
        }
    }

    private fun validateCommonBinding(
        bytes: ByteArray,
        source: CommonDeclarationSource,
        common: R2TrustedDeclarations,
    ) {
        val binding = R2StrictJson.parseObject(bytes)
        binding.requireExactKeys(COMMON_BINDING_KEYS, "Common declaration binding")
        require(binding.requireInt("schemaVersion") == 1)
        require(binding.requireString("package") == source.packageName)
        require(binding.requireString("version") == source.version)
        require(binding.requireString("distributionAssetRoot") == source.distributionAssetRoot)
        require(binding.requireString("distributionManifest") == source.distributionManifest)
        require(
            R2Digests.equal(
                binding.requireLowerSha256("distributionFingerprint"),
                source.distributionFingerprint,
            ),
        )
        require(
            R2Digests.equal(
                binding.requireLowerSha256("standardLibrariesFingerprint"),
                source.standardLibrariesFingerprint,
            ),
        )
        require(binding.requireInt("fileCount") == common.files.size)
        require(binding.requireLong("totalBytes") == common.files.sumOf { file -> file.size })
    }

    private interface DeclarationAssetSource {
        fun read(path: String, maximumBytes: Long): ByteArray
        fun paths(): Set<String>
    }

    private class AndroidDeclarationAssetSource(
        private val context: Context,
    ) : DeclarationAssetSource {

        override fun read(path: String, maximumBytes: Long): ByteArray =
            context.assets.open("$ASSET_ROOT/$path").use { input ->
                readBounded(input, maximumBytes)
            }

        override fun paths(): Set<String> {
            val result = LinkedHashSet<String>()
            fun visit(relativeDirectory: String) {
                val assetDirectory = if (relativeDirectory.isEmpty()) {
                    ASSET_ROOT
                } else {
                    "$ASSET_ROOT/$relativeDirectory"
                }
                val children = context.assets.list(assetDirectory)
                    ?.sorted()
                    .orEmpty()
                children.forEach { child ->
                    val relativePath = if (relativeDirectory.isEmpty()) {
                        child
                    } else {
                        "$relativeDirectory/$child"
                    }
                    val descendants = context.assets.list("$ASSET_ROOT/$relativePath")
                        ?.sorted()
                        .orEmpty()
                    if (descendants.isEmpty()) {
                        result += relativePath
                    } else {
                        visit(relativePath)
                    }
                }
            }
            visit("")
            return result
        }
    }

    private class MapDeclarationAssetSource(
        files: Map<String, ByteArray>,
    ) : DeclarationAssetSource {
        private val files = files.mapValues { (_, bytes) -> bytes.copyOf() }

        override fun read(path: String, maximumBytes: Long): ByteArray {
            val bytes = requireNotNull(files[path]) { "Missing declaration test asset: $path." }
            require(bytes.size.toLong() <= maximumBytes) {
                "Declaration test asset exceeds its declared bound: $path."
            }
            return bytes.copyOf()
        }

        override fun paths(): Set<String> = LinkedHashSet(files.keys)
    }

    private fun readBounded(input: InputStream, maximumBytes: Long): ByteArray {
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
                require(byteCount < maximumBytes) { "Packaged asset exceeds its byte budget." }
                output.write(singleByte)
                byteCount++
                continue
            }
            require(byteCount <= maximumBytes - read) {
                "Packaged asset exceeds its byte budget."
            }
            output.write(buffer, 0, read)
            byteCount += read
        }
        return output.toByteArray()
    }

    private data class CompilerDistributionIdentity(
        val distributionFingerprint: String,
        val standardLibrariesFingerprint: String,
    )

    private const val EXPECTED_RELEASE_PACKAGE =
        "autojs6-plugin-typescript-engine-declarations"
    private const val ASSET_ROOT = "r2-declarations/v2"
    private const val MANIFEST_PATH = "distribution.json"
    private const val DECLARATION_LAYER_ROOT = "layers/"
    private const val MAXIMUM_MANIFEST_BYTES = 256L * 1024L
    private const val MAXIMUM_COMPILER_MANIFEST_BYTES = 256L * 1024L
    private val COMMON_BINDING_KEYS = setOf(
        "schemaVersion",
        "package",
        "version",
        "distributionAssetRoot",
        "distributionManifest",
        "distributionFingerprint",
        "standardLibrariesFingerprint",
        "fileCount",
        "totalBytes",
    )
}

private data class R2DeclarationDistribution(
    val generator: DeclarationGenerator,
    val release: DeclarationRelease,
    val sources: DeclarationSources,
    val layers: Map<String, DeclarationLayerDescriptor>,
    val profiles: Map<String, DeclarationProfileDescriptor>,
    val files: List<PackagedDeclarationFile>,
    val fingerprint: String,
) {
    companion object {
        fun parse(
            bytes: ByteArray,
            common: R2TrustedDeclarations,
            compilerDistributionFingerprint: String,
        ): R2DeclarationDistribution {
            require(bytes.size.toLong() <= MAXIMUM_MANIFEST_BYTES)
            val root = R2StrictJson.parseObject(
                bytes = bytes,
                maximumDepth = MAXIMUM_JSON_DEPTH,
                maximumNodes = MAXIMUM_JSON_NODES,
            )
            root.requireExactKeys(TOP_LEVEL_KEYS, "Declaration distribution")
            require(root.requireInt("schemaVersion") == 2)
            val generator = DeclarationGenerator.parse(root.requireObject("generator"))
            val release = DeclarationRelease.parse(root.requireObject("release"))
            val sources = DeclarationSources.parse(
                root.requireObject("sources"),
                common,
                compilerDistributionFingerprint,
                release,
            )
            val files = parseFiles(root.requireList("files"))
            val layers = parseLayers(root.requireObject("layers"), sources, files, common)
            val profiles = parseProfiles(root.requireObject("profiles"), layers)
            val fingerprint = root.requireLowerSha256("fingerprint")
            val calculated = calculateDistributionFingerprint(
                generator = generator,
                release = release,
                sources = sources,
                layers = layers,
                profiles = profiles,
                files = files,
            )
            require(R2Digests.equal(fingerprint, calculated)) {
                "Declaration distribution does not match its fingerprint."
            }
            requireExactPackagedTopology(sources, layers, files)
            return R2DeclarationDistribution(
                generator = generator,
                release = release,
                sources = sources,
                layers = layers,
                profiles = profiles,
                files = files,
                fingerprint = fingerprint,
            )
        }

        private fun parseFiles(values: List<Any?>): List<PackagedDeclarationFile> {
            require(values.size <= MAXIMUM_PACKAGED_FILES)
            val result = ArrayList<PackagedDeclarationFile>(values.size)
            var totalBytes = 0L
            values.forEach { value ->
                val objectValue = value as? Map<*, *>
                    ?: throw IllegalArgumentException("Declaration file record must be an object.")
                @Suppress("UNCHECKED_CAST")
                val descriptor = PackagedDeclarationFile.parse(objectValue as Map<String, Any?>)
                require(descriptor.size <= MAXIMUM_PACKAGED_FILE_BYTES)
                require(totalBytes <= MAXIMUM_PACKAGED_TOTAL_BYTES - descriptor.size)
                totalBytes += descriptor.size
                result += descriptor
            }
            require(result.map(PackagedDeclarationFile::path) ==
                result.map(PackagedDeclarationFile::path).sorted()) {
                "Declaration distribution files must be strictly path-sorted."
            }
            requireStrictPathInventory(result.map(PackagedDeclarationFile::path), "packaged files")
            return result
        }

        private fun parseLayers(
            values: Map<String, Any?>,
            sources: DeclarationSources,
            files: List<PackagedDeclarationFile>,
            common: R2TrustedDeclarations,
        ): Map<String, DeclarationLayerDescriptor> {
            values.requireExactKeys(LAYER_IDS, "Declaration layers")
            val packaged = files.associateBy(PackagedDeclarationFile::path)
            val result = linkedMapOf<String, DeclarationLayerDescriptor>()
            LAYER_IDS.sorted().forEach { layerId ->
                val layerValue = values[layerId] as? Map<*, *>
                    ?: throw IllegalArgumentException("Declaration layer $layerId must be an object.")
                @Suppress("UNCHECKED_CAST")
                result[layerId] = DeclarationLayerDescriptor.parse(
                    id = layerId,
                    value = layerValue as Map<String, Any?>,
                    packaged = packaged,
                    common = common,
                )
            }
            require(result.values.map(DeclarationLayerDescriptor::sourceId).toSet() == sources.ids) {
                "Declaration layers do not bind the exact source identities."
            }
            return result
        }

        private fun parseProfiles(
            values: Map<String, Any?>,
            layers: Map<String, DeclarationLayerDescriptor>,
        ): Map<String, DeclarationProfileDescriptor> {
            values.requireExactKeys(PROFILE_IDS, "Declaration profiles")
            return PROFILE_IDS.sorted().associateWithTo(LinkedHashMap()) { profileId ->
                val profileValue = values[profileId] as? Map<*, *>
                    ?: throw IllegalArgumentException(
                        "Declaration profile $profileId must be an object.",
                    )
                @Suppress("UNCHECKED_CAST")
                DeclarationProfileDescriptor.parse(
                    id = profileId,
                    value = profileValue as Map<String, Any?>,
                    layers = layers,
                )
            }
        }

        private fun calculateDistributionFingerprint(
            generator: DeclarationGenerator,
            release: DeclarationRelease,
            sources: DeclarationSources,
            layers: Map<String, DeclarationLayerDescriptor>,
            profiles: Map<String, DeclarationProfileDescriptor>,
            files: List<PackagedDeclarationFile>,
        ): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(DISTRIBUTION_FINGERPRINT_DOMAIN.toByteArray(StandardCharsets.US_ASCII))
            digest.update(
                "generator\u0000${generator.fingerprint}\n"
                    .toByteArray(StandardCharsets.US_ASCII),
            )
            val releaseFingerprint = R2Digests.sha256(
                canonicalJson(release.raw).toByteArray(StandardCharsets.UTF_8),
            )
            digest.update(
                "release\u0000$releaseFingerprint\n"
                    .toByteArray(StandardCharsets.US_ASCII),
            )
            sources.raw.toSortedMap().forEach { (id, descriptor) ->
                val canonical = canonicalJson(descriptor)
                val sourceFingerprint = R2Digests.sha256(
                    canonical.toByteArray(StandardCharsets.UTF_8),
                )
                digest.update(
                    "source\u0000$id\u0000$sourceFingerprint\n"
                        .toByteArray(StandardCharsets.US_ASCII),
                )
            }
            layers.toSortedMap().forEach { (id, layer) ->
                digest.update(
                    "layer\u0000$id\u0000${layer.fingerprint}\n"
                        .toByteArray(StandardCharsets.US_ASCII),
                )
            }
            profiles.toSortedMap().forEach { (id, profile) ->
                digest.update(
                    "profile\u0000$id\u0000${profile.fingerprint}\n"
                        .toByteArray(StandardCharsets.US_ASCII),
                )
            }
            files.forEach { file ->
                digest.update("file\u0000".toByteArray(StandardCharsets.US_ASCII))
                digest.update(file.path.toByteArray(StandardCharsets.UTF_8))
                digest.update(0)
                digest.update(file.size.toString().toByteArray(StandardCharsets.US_ASCII))
                digest.update(0)
                digest.update(file.sha256.toByteArray(StandardCharsets.US_ASCII))
                digest.update('\n'.code.toByte())
            }
            return digest.digest().toLowerHex()
        }

        private fun requireExactPackagedTopology(
            sources: DeclarationSources,
            layers: Map<String, DeclarationLayerDescriptor>,
            files: List<PackagedDeclarationFile>,
        ) {
            val expected = LinkedHashSet<String>()
            expected += sources.common.bindingFile
            expected += sources.rhino.licenseFile
            expected += sources.rhino.noticeFile
            expected += sources.node.licenseFile
            expected += sources.node.noticeFile
            expected += NODE_PACKAGE_JSON
            layers.values.forEach { layer -> expected.addAll(layer.files) }
            val actual = files.mapTo(LinkedHashSet()) { file -> file.path }
            require(actual == expected) {
                "Declaration distribution contains unclassified packaged files; " +
                    "missing=${expected - actual}, extra=${actual - expected}."
            }
        }

        private const val MAXIMUM_MANIFEST_BYTES = 256L * 1024L
        private const val MAXIMUM_JSON_DEPTH = 16
        private const val MAXIMUM_JSON_NODES = 16 * 1024
        private const val MAXIMUM_PACKAGED_FILES = 512
        private const val MAXIMUM_PACKAGED_FILE_BYTES = 8L * 1024L * 1024L
        private const val MAXIMUM_PACKAGED_TOTAL_BYTES = 16L * 1024L * 1024L
        private const val NODE_PACKAGE_JSON = "layers/node/package.json"
        private const val DISTRIBUTION_FINGERPRINT_DOMAIN =
            "autojs6.typescript.declarations.distribution.v2\n"
        private val TOP_LEVEL_KEYS = setOf(
            "schemaVersion",
            "generator",
            "release",
            "sources",
            "layers",
            "profiles",
            "files",
            "fingerprint",
        )
        private val LAYER_IDS = setOf("common", "rhino", "node")
        private val PROFILE_IDS = setOf("rhino", "node")
    }
}

private data class DeclarationGenerator(
    val id: String,
    val version: Int,
    val fingerprint: String,
) {
    companion object {
        fun parse(value: Map<String, Any?>): DeclarationGenerator {
            value.requireExactKeys(KEYS, "Declaration generator")
            val id = value.requireString("id")
            val version = value.requireInt("version")
            val fingerprint = value.requireLowerSha256("fingerprint")
            require(id == EXPECTED_ID && version == EXPECTED_VERSION)
            val canonical = buildString {
                append(FINGERPRINT_DOMAIN)
                append("id\u0000")
                append(id)
                append('\n')
                append("version\u0000")
                append(version)
                append('\n')
            }
            require(
                R2Digests.equal(
                    fingerprint,
                    R2Digests.sha256(canonical.toByteArray(StandardCharsets.UTF_8)),
                ),
            ) { "Declaration generator does not match its fingerprint." }
            return DeclarationGenerator(id, version, fingerprint)
        }

        private const val EXPECTED_ID = "autojs6-plugin-typescript-engine"
        private const val EXPECTED_VERSION = 5
        private const val FINGERPRINT_DOMAIN =
            "autojs6.typescript.declarations.generator.v1\n"
        private val KEYS = setOf("id", "version", "fingerprint")
    }
}

private data class DeclarationRelease(
    val packageName: String,
    val version: String,
    val raw: Map<String, Any?>,
) {
    companion object {
        fun parse(value: Map<String, Any?>): DeclarationRelease {
            value.requireExactKeys(KEYS, "Declaration release")
            val packageName = value.requireString("package")
            val version = value.requireString("version")
            require(packageName == EXPECTED_PACKAGE)
            require(version == BuildConfig.VERSION_NAME) {
                "Declaration release $version does not match plugin ${BuildConfig.VERSION_NAME}."
            }
            return DeclarationRelease(packageName, version, LinkedHashMap(value))
        }

        private const val EXPECTED_PACKAGE =
            "autojs6-plugin-typescript-engine-declarations"
        private val KEYS = setOf("package", "version")
    }
}

private data class DeclarationSources(
    val common: CommonDeclarationSource,
    val rhino: RhinoDeclarationSource,
    val node: NodeDeclarationSource,
    val raw: Map<String, Map<String, Any?>>,
) {
    val ids: Set<String> = raw.keys

    companion object {
        fun parse(
            value: Map<String, Any?>,
            common: R2TrustedDeclarations,
            compilerDistributionFingerprint: String,
            release: DeclarationRelease,
        ): DeclarationSources {
            value.requireExactKeys(SOURCE_IDS, "Declaration sources")
            val raw = SOURCE_IDS.sorted().associateWithTo(LinkedHashMap()) { id ->
                val descriptor = value[id] as? Map<*, *>
                    ?: throw IllegalArgumentException("Declaration source $id must be an object.")
                @Suppress("UNCHECKED_CAST")
                descriptor as Map<String, Any?>
            }
            return DeclarationSources(
                common = CommonDeclarationSource.parse(
                    raw.getValue("common"),
                    common,
                    compilerDistributionFingerprint,
                ),
                rhino = RhinoDeclarationSource.parse(raw.getValue("rhino")),
                node = NodeDeclarationSource.parse(raw.getValue("node"), release),
                raw = raw,
            )
        }

        private val SOURCE_IDS = setOf("common", "rhino", "node")
    }
}

private data class CommonDeclarationSource(
    val packageName: String,
    val version: String,
    val distributionAssetRoot: String,
    val distributionManifest: String,
    val bindingFile: String,
    val distributionFingerprint: String,
    val standardLibrariesFingerprint: String,
    val fileCount: Int,
    val totalBytes: Long,
) {
    companion object {
        fun parse(
            value: Map<String, Any?>,
            common: R2TrustedDeclarations,
            compilerDistributionFingerprint: String,
        ): CommonDeclarationSource {
            value.requireExactKeys(KEYS, "Common declaration source")
            require(value.requireString("kind") == "typescript-standard-libraries")
            val packageName = value.requireString("package")
            val version = value.requireString("version")
            val distributionAssetRoot = value.requireString("distributionAssetRoot")
            val distributionManifest = value.requireString("distributionManifest")
            val bindingFile = value.requireString("bindingFile")
            val distributionFingerprint = value.requireLowerSha256("distributionFingerprint")
            val standardLibrariesFingerprint =
                value.requireLowerSha256("standardLibrariesFingerprint")
            val fileCount = value.requireInt("fileCount")
            val totalBytes = value.requireLong("totalBytes")
            require(packageName == "typescript")
            require(version == BuildConfig.TYPESCRIPT_VERSION)
            require(distributionAssetRoot == "typescript/${BuildConfig.TYPESCRIPT_VERSION}")
            require(distributionManifest == "$distributionAssetRoot/distribution.json")
            require(bindingFile == "layers/common/binding.json")
            require(R2Digests.equal(distributionFingerprint, compilerDistributionFingerprint))
            require(R2Digests.equal(standardLibrariesFingerprint, common.fingerprint))
            require(fileCount == common.files.size)
            require(totalBytes == common.files.sumOf { file -> file.size })
            return CommonDeclarationSource(
                packageName,
                version,
                distributionAssetRoot,
                distributionManifest,
                bindingFile,
                distributionFingerprint,
                standardLibrariesFingerprint,
                fileCount,
                totalBytes,
            )
        }

        private val KEYS = setOf(
            "kind",
            "package",
            "version",
            "distributionAssetRoot",
            "distributionManifest",
            "bindingFile",
            "distributionFingerprint",
            "standardLibrariesFingerprint",
            "fileCount",
            "totalBytes",
        )
    }
}

private data class RhinoDeclarationSource(
    val source: String,
    val sha1: String,
    val sha256: String,
    val integrity: String,
    val licenseFile: String,
    val noticeFile: String,
) {
    companion object {
        fun parse(value: Map<String, Any?>): RhinoDeclarationSource {
            value.requireExactKeys(KEYS, "Rhino declaration source")
            require(value.requireString("kind") == "npm-tarball-projection-with-plugin-supplement")
            require(value.requireString("package") == "@sm003/autojs6-dts")
            require(value.requireString("version") == "2.1.3")
            val source = value.requireString("source")
            val sha1 = value.requireString("sha1")
            val sha256 = value.requireLowerSha256("sha256")
            val integrity = value.requireString("integrity")
            require(value.requireString("conversion") == "autojs6-rhino-modules-v2")
            require(value.requireString("license") == "MPL-2.0")
            val licenseFile = value.requireString("licenseFile")
            val noticeFile = value.requireString("noticeFile")
            require(source == EXPECTED_SOURCE)
            require(sha1 == EXPECTED_SHA1)
            require(sha256 == EXPECTED_SHA256)
            require(integrity == EXPECTED_INTEGRITY)
            require(licenseFile == "licenses/rhino/MPL-2.0.txt")
            require(noticeFile == "notices/rhino-source.txt")
            return RhinoDeclarationSource(
                source,
                sha1,
                sha256,
                integrity,
                licenseFile,
                noticeFile,
            )
        }

        private const val EXPECTED_SOURCE =
            "https://registry.npmjs.org/@sm003/autojs6-dts/-/autojs6-dts-2.1.3.tgz"
        private const val EXPECTED_SHA1 = "20be1a50a9d029b150c99939c9f307dfebdf5abf"
        private const val EXPECTED_SHA256 =
            "ecc90a666c4383060d712a478752f9273534605add0f921e1537187f4386983c"
        private const val EXPECTED_INTEGRITY =
            "sha512-0k5z1il4at5C4uIDOAjFZmg6yTk8qsooahniDlc1flKOLl2AN6xTJELyddb+" +
                "yv4HRrTXRpCruhSz4pY40aLTjQ=="
        private val KEYS = setOf(
            "kind",
            "package",
            "version",
            "source",
            "sha1",
            "sha256",
            "integrity",
            "conversion",
            "license",
            "licenseFile",
            "noticeFile",
        )
    }
}

private data class NodeDeclarationSource(
    val licenseFile: String,
    val noticeFile: String,
) {
    companion object {
        fun parse(
            value: Map<String, Any?>,
            release: DeclarationRelease,
        ): NodeDeclarationSource {
            value.requireExactKeys(KEYS, "Node declaration source")
            require(value.requireString("kind") == "plugin-bundled-declaration-layer")
            require(value.requireString("releasePackage") == release.packageName)
            require(value.requireString("releaseVersion") == release.version)
            require(value.requireString("license") == "MPL-2.0")
            val licenseFile = value.requireString("licenseFile")
            val noticeFile = value.requireString("noticeFile")
            require(licenseFile == "licenses/node/MPL-2.0.txt")
            require(noticeFile == "notices/node-source.txt")
            return NodeDeclarationSource(licenseFile, noticeFile)
        }

        private val KEYS = setOf(
            "kind",
            "releasePackage",
            "releaseVersion",
            "license",
            "licenseFile",
            "noticeFile",
        )
    }
}

private data class PackagedDeclarationFile(
    val path: String,
    val size: Long,
    val sha256: String,
) {
    companion object {
        fun parse(value: Map<String, Any?>): PackagedDeclarationFile {
            value.requireExactKeys(KEYS, "Packaged declaration file")
            val path = value.requireString("path")
            validateRelativeAssetPath(path, "Packaged declaration file")
            require(path != "distribution.json")
            return PackagedDeclarationFile(
                path = path,
                size = value.requireLong("size"),
                sha256 = value.requireLowerSha256("sha256"),
            )
        }

        private val KEYS = setOf("path", "size", "sha256")
    }
}

private data class DeclarationLayerDescriptor(
    val id: String,
    val sourceId: String,
    val entryPoint: String?,
    val fileCount: Int,
    val totalBytes: Long,
    val fingerprint: String,
    val files: List<String>,
) {
    companion object {
        fun parse(
            id: String,
            value: Map<String, Any?>,
            packaged: Map<String, PackagedDeclarationFile>,
            common: R2TrustedDeclarations,
        ): DeclarationLayerDescriptor {
            value.requireExactKeys(KEYS, "Declaration layer $id")
            val sourceId = value.requireString("sourceId")
            val entryPointValue = value["entryPoint"]
            val entryPoint = when (entryPointValue) {
                null -> null
                is String -> entryPointValue
                else -> throw IllegalArgumentException(
                    "Declaration layer $id entry point must be a string or null.",
                )
            }
            val fileCount = value.requireInt("fileCount")
            val totalBytes = value.requireLong("totalBytes")
            val fingerprint = value.requireLowerSha256("fingerprint")
            val files = value.requireList("files").map { item ->
                item as? String
                    ?: throw IllegalArgumentException("Declaration layer file must be a string.")
            }
            require(files == files.sorted()) { "Declaration layer $id files are not sorted." }
            requireStrictPathInventory(files, "declaration layer $id")
            require(sourceId == id)

            if (id == R2DeclarationLayer.COMMON.manifestValue) {
                require(entryPoint == null)
                require(files.isEmpty())
                require(fileCount == common.files.size)
                require(totalBytes == common.files.sumOf { file -> file.size })
                require(R2Digests.equal(fingerprint, common.fingerprint))
            } else {
                val expectedEntryPoint = "layers/$id/index.d.ts"
                require(entryPoint == expectedEntryPoint)
                require(entryPoint in files)
                require(files.all { path ->
                    path.startsWith("layers/$id/") && path.endsWith(".d.ts")
                })
                val descriptors = files.map { path ->
                    requireNotNull(packaged[path]) {
                        "Declaration layer $id references an unmanifested file: $path."
                    }
                }
                require(fileCount == descriptors.size)
                require(totalBytes == descriptors.sumOf(PackagedDeclarationFile::size))
                val layer = R2DeclarationLayer.entries.single { it.manifestValue == id }
                val calculated = R2TrustedDeclarations.layerFingerprint(
                    layer,
                    descriptors.map { descriptor ->
                        R2TrustedDeclarationFile(
                            archivePath = "declarations/" +
                                descriptor.path.removePrefix("layers/"),
                            fingerprintPath = descriptor.path,
                            size = descriptor.size,
                            sha256 = descriptor.sha256,
                            text = "",
                        )
                    },
                )
                require(R2Digests.equal(fingerprint, calculated)) {
                    "Declaration layer $id does not match its fingerprint."
                }
                require(
                    (id == "rhino" && fileCount == 53) ||
                        (id == "node" && fileCount == 59),
                ) { "Declaration layer $id has an unexpected frozen file count." }
            }
            return DeclarationLayerDescriptor(
                id,
                sourceId,
                entryPoint,
                fileCount,
                totalBytes,
                fingerprint,
                files,
            )
        }

        private val KEYS = setOf(
            "sourceId",
            "entryPoint",
            "fileCount",
            "totalBytes",
            "fingerprint",
            "files",
        )
    }
}

private data class DeclarationProfileDescriptor(
    val id: String,
    val layers: List<String>,
    val fingerprint: String,
) {
    companion object {
        fun parse(
            id: String,
            value: Map<String, Any?>,
            layers: Map<String, DeclarationLayerDescriptor>,
        ): DeclarationProfileDescriptor {
            value.requireExactKeys(KEYS, "Declaration profile $id")
            val layerIds = value.requireList("layers").map { item ->
                item as? String
                    ?: throw IllegalArgumentException("Declaration profile layer must be a string.")
            }
            require(layerIds == listOf("common", id)) {
                "Declaration profile $id does not select its exact ordered layers."
            }
            val profile = R2DeclarationProfile.entries.single { it.manifestValue == id }
            val layerFingerprints = linkedMapOf<String, String>()
            layerIds.forEach { layerId ->
                layerFingerprints[layerId] = requireNotNull(layers[layerId]).fingerprint
            }
            val entryPoint = requireNotNull(layers.getValue(id).entryPoint)
            val fingerprint = value.requireLowerSha256("fingerprint")
            val calculated = R2TrustedDeclarations.calculateProfileFingerprint(
                profile,
                entryPoint,
                layerFingerprints,
            )
            require(R2Digests.equal(fingerprint, calculated)) {
                "Declaration profile $id does not match its fingerprint."
            }
            return DeclarationProfileDescriptor(id, layerIds, fingerprint)
        }

        private val KEYS = setOf("layers", "fingerprint")
    }
}

private fun Map<String, Any?>.requireExactKeys(expected: Set<String>, label: String) {
    require(keys == expected) {
        "$label keys are not exact; missing=${expected - keys}, extra=${keys - expected}."
    }
}

private fun Map<String, Any?>.requireObject(key: String): Map<String, Any?> {
    val value = this[key] as? Map<*, *>
        ?: throw IllegalArgumentException("Declaration field $key must be an object.")
    @Suppress("UNCHECKED_CAST")
    return value as Map<String, Any?>
}

private fun Map<String, Any?>.requireList(key: String): List<Any?> =
    this[key] as? List<*>
        ?: throw IllegalArgumentException("Declaration field $key must be an array.")

private fun Map<String, Any?>.requireString(key: String): String =
    (this[key] as? String)
        ?.also { value -> require(value.isNotEmpty()) { "Declaration field $key is empty." } }
        ?: throw IllegalArgumentException("Declaration field $key must be a string.")

private fun Map<String, Any?>.requireLong(key: String): Long {
    val token = this[key] as? R2StrictJson.NumberToken
        ?: throw IllegalArgumentException("Declaration field $key must be an integer.")
    require(token.source.matches(NON_NEGATIVE_INTEGER))
    return token.source.toLongOrNull()
        ?: throw IllegalArgumentException("Declaration field $key exceeds Long.")
}

private fun Map<String, Any?>.requireInt(key: String): Int {
    val value = requireLong(key)
    require(value <= Int.MAX_VALUE.toLong())
    return value.toInt()
}

private fun Map<String, Any?>.requireLowerSha256(key: String): String =
    requireString(key).also { value ->
        R2Digests.requireLowerSha256(value, "Declaration field $key")
    }

private fun validateRelativeAssetPath(path: String, label: String) {
    require(path.isNotEmpty() && path.length <= 4_096)
    require(path.all { character -> character.code in ' '.code..'~'.code })
    require(!path.startsWith('/') && '\\' !in path && !path.hasAsciiDrivePrefix())
    val segments = path.split('/')
    require(segments.size <= 64)
    require(segments.all { segment -> segment.isNotEmpty() && segment != "." && segment != ".." })
}

private fun requireStrictPathInventory(paths: List<String>, label: String) {
    val exact = HashSet<String>(paths.size)
    val folded = HashMap<String, String>(paths.size)
    paths.forEach { path ->
        validateRelativeAssetPath(path, label)
        require(exact.add(path)) { "$label contains duplicate path $path." }
        val previous = folded.putIfAbsent(path.asciiCaseFold(), path)
        require(previous == null) { "$label paths collide: $previous / $path." }
    }
}

private fun canonicalJson(value: Any?): String = when (value) {
    null -> "null"
    is String -> buildString { appendJsonString(value) }
    is Boolean -> value.toString()
    is R2StrictJson.NumberToken -> value.source
    is List<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",") {
        canonicalJson(it)
    }
    is Map<*, *> -> value.entries
        .map { entry ->
            val key = entry.key as? String
                ?: throw IllegalArgumentException("Canonical JSON object key must be a string.")
            key to entry.value
        }
        .sortedBy { it.first }
        .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, item) ->
            buildString {
                appendJsonString(key)
                append(':')
                append(canonicalJson(item))
            }
        }
    else -> throw IllegalArgumentException("Unsupported canonical JSON value.")
}

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    value.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000c' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) {
                append("\\u")
                append(character.code.toString(16).padStart(4, '0'))
            } else {
                append(character)
            }
        }
    }
    append('"')
}

private fun String.hasAsciiDrivePrefix(): Boolean =
    length >= 2 &&
        (this[0] in 'A'..'Z' || this[0] in 'a'..'z') &&
        this[1] == ':'

private fun String.asciiCaseFold(): String = buildString(length) {
    this@asciiCaseFold.forEach { character ->
        append(if (character in 'A'..'Z') (character.code + 32).toChar() else character)
    }
}

private fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}

private val NON_NEGATIVE_INTEGER = Regex("0|[1-9][0-9]*")
