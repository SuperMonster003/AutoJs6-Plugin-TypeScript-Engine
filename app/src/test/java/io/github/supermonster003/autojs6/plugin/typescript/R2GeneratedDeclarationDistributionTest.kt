package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R2GeneratedDeclarationDistributionTest {

    @Test
    fun generatedDistributionLoadsThroughTheRealRuntimeAuthorityForBothProfiles() {
        val declarationRoot = requiredDirectory(DECLARATION_DISTRIBUTION_PROPERTY)
        val typeScriptRoot = requiredDirectory(TYPESCRIPT_DISTRIBUTION_PROPERTY)
        val compilerManifest = parseManifest(typeScriptRoot.resolve(MANIFEST_FILE))
        val common = loadCommonDeclarations(typeScriptRoot, compilerManifest)
        val compilerFingerprint = compilerManifest.requireString("fingerprint")

        assertEquals(108, common.files.size)
        assertEquals(3_784_758L, common.files.sumOf(R2TrustedDeclarationFile::size))
        assertEquals(
            "6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f",
            common.fingerprint,
        )

        val declarationManifestBytes = Files.readAllBytes(declarationRoot.resolve(MANIFEST_FILE))
        val declarationManifest = R2StrictJson.parseObject(declarationManifestBytes)
        assertEquals(2, declarationManifest.requireLong("schemaVersion").toInt())
        assertEquals(
            mapOf(
                "package" to "autojs6-plugin-typescript-engine-declarations",
                "version" to BuildConfig.VERSION_NAME,
            ),
            declarationManifest.requireObject("release"),
        )
        val nodeSource = declarationManifest.requireObject("sources").requireObject("node")
        assertEquals("plugin-bundled-declaration-layer", nodeSource.requireString("kind"))
        assertEquals(BuildConfig.VERSION_NAME, nodeSource.requireString("releaseVersion"))
        assertFalse(nodeSource.containsKey("commit"))
        assertFalse(nodeSource.containsKey("hostBuild"))
        assertFalse(nodeSource.containsKey("repositoryIdentity"))
        val packagedFiles = readPackagedFiles(declarationRoot)
        val rhino = R2DeclarationDistributionLoader.loadForTesting(
            profile = R2DeclarationProfile.RHINO,
            common = common,
            manifestBytes = declarationManifestBytes,
            packagedFiles = packagedFiles,
            compilerDistributionFingerprint = compilerFingerprint,
        )
        val node = R2DeclarationDistributionLoader.loadForTesting(
            profile = R2DeclarationProfile.NODE,
            common = common,
            manifestBytes = declarationManifestBytes,
            packagedFiles = packagedFiles,
            compilerDistributionFingerprint = compilerFingerprint,
        )

        val layers = declarationManifest.requireObject("layers")
        val profiles = declarationManifest.requireObject("profiles")
        assertProfile(
            declarations = rhino,
            expectedProfile = R2DeclarationProfile.RHINO,
            expectedFileCount = 161,
            expectedEngineFileCount = 53,
            expectedProfileFingerprint = profiles.requireObject("rhino")
                .requireString("fingerprint"),
            expectedEngineFingerprint = layers.requireObject("rhino")
                .requireString("fingerprint"),
        )
        assertProfile(
            declarations = node,
            expectedProfile = R2DeclarationProfile.NODE,
            expectedFileCount = 167,
            expectedEngineFileCount = 59,
            expectedProfileFingerprint = profiles.requireObject("node")
                .requireString("fingerprint"),
            expectedEngineFingerprint = layers.requireObject("node")
                .requireString("fingerprint"),
        )

        assertEquals(listOf("/declarations/rhino/index.d.ts"), rhino.compilerRootNames)
        assertEquals(listOf("/declarations/node/index.d.ts"), node.compilerRootNames)
        assertTrue(rhino.filesByArchivePath.containsKey("declarations/rhino/index.d.ts"))
        assertTrue(node.filesByArchivePath.containsKey("declarations/node/index.d.ts"))
        assertTrue(node.filesByArchivePath.containsKey("declarations/node/input_observer.d.ts"))
        assertFalse(
            rhino.files.any { file -> file.archivePath.startsWith("declarations/node/") },
        )
        assertFalse(
            node.files.any { file -> file.archivePath.startsWith("declarations/rhino/") },
        )
        assertNotEquals(rhino.profileFingerprint, node.profileFingerprint)

        val releaseIdentity =
            "\"release\":{\"package\":" +
                "\"autojs6-plugin-typescript-engine-declarations\"," +
                "\"version\":\"${BuildConfig.VERSION_NAME}\"}"
        val tamperedManifest = declarationManifestBytes.toString(Charsets.UTF_8)
            .replace(
                releaseIdentity,
                releaseIdentity.replace(BuildConfig.VERSION_NAME, "0.0.0"),
            )
            .toByteArray(Charsets.UTF_8)
        assertThrows(IllegalArgumentException::class.java) {
            R2DeclarationDistributionLoader.loadForTesting(
                profile = R2DeclarationProfile.RHINO,
                common = common,
                manifestBytes = tamperedManifest,
                packagedFiles = packagedFiles,
                compilerDistributionFingerprint = compilerFingerprint,
            )
        }
    }

    private fun assertProfile(
        declarations: R2TrustedDeclarations,
        expectedProfile: R2DeclarationProfile,
        expectedFileCount: Int,
        expectedEngineFileCount: Int,
        expectedProfileFingerprint: String,
        expectedEngineFingerprint: String,
    ) {
        val enginePrefix = "declarations/${expectedProfile.manifestValue}/"
        assertEquals(expectedProfile, declarations.profile)
        assertEquals(expectedFileCount, declarations.files.size)
        assertEquals(expectedFileCount, declarations.filesByArchivePath.size)
        assertEquals(
            expectedEngineFileCount,
            declarations.files.count { file -> file.archivePath.startsWith(enginePrefix) },
        )
        assertEquals(expectedProfileFingerprint, declarations.profileFingerprint)
        assertEquals(
            linkedMapOf(
                R2DeclarationLayer.COMMON.manifestValue to declarations.fingerprint,
                expectedProfile.engineLayer.manifestValue to expectedEngineFingerprint,
            ),
            declarations.layerFingerprints,
        )
    }

    private fun loadCommonDeclarations(
        root: Path,
        manifest: Map<String, Any?>,
    ): R2TrustedDeclarations {
        val packageIdentity = manifest.requireObject("package")
        require(packageIdentity.requireString("name") == "typescript")
        require(packageIdentity.requireString("version") == "6.0.3")
        val standardLibraries = manifest.requireObject("components")
            .requireObject("standardLibraries")
        val expectedFileCount = standardLibraries.requireLong("fileCount").toInt()
        val expectedTotalBytes = standardLibraries.requireLong("totalBytes")
        val expectedFingerprint = standardLibraries.requireString("fingerprint")
        val files = manifest.requireList("files").mapNotNull { rawDescriptor ->
            val descriptor = rawDescriptor.requireObjectValue("TypeScript distribution file")
            val relativePath = descriptor.requireString("path")
            if (!isStandardLibraryPath(relativePath)) return@mapNotNull null
            val expectedSize = descriptor.requireLong("size")
            val expectedSha256 = descriptor.requireString("sha256")
            val bytes = Files.readAllBytes(root.resolve(relativePath).normalize())
            try {
                require(bytes.size.toLong() == expectedSize) {
                    "Generated TypeScript declaration size drifted: $relativePath."
                }
                require(R2Digests.equal(expectedSha256, R2Digests.sha256(bytes))) {
                    "Generated TypeScript declaration SHA-256 drifted: $relativePath."
                }
                R2TrustedDeclarationFile(
                    archivePath = "declarations/${relativePath.removePrefix("lib/")}",
                    fingerprintPath = relativePath,
                    size = expectedSize,
                    sha256 = expectedSha256,
                    text = R2Utf8.decode(bytes, "Generated TypeScript declaration $relativePath"),
                )
            } finally {
                bytes.fill(0)
            }
        }
        require(files.size == expectedFileCount)
        require(files.sumOf(R2TrustedDeclarationFile::size) == expectedTotalBytes)
        return R2TrustedDeclarations.create(expectedFingerprint, files)
    }

    private fun readPackagedFiles(root: Path): Map<String, ByteArray> {
        val relativePaths = ArrayList<String>()
        Files.walk(root).use { paths ->
            paths.forEach { candidate ->
                if (candidate == root) return@forEach
                require(!Files.isSymbolicLink(candidate)) {
                    "Generated declaration distribution contains a symbolic link: $candidate."
                }
                if (Files.isRegularFile(candidate)) {
                    relativePaths += root.relativize(candidate).joinToString("/") { segment ->
                        segment.toString()
                    }
                } else {
                    require(Files.isDirectory(candidate)) {
                        "Generated declaration distribution contains a special file: $candidate."
                    }
                }
            }
        }
        val result = LinkedHashMap<String, ByteArray>()
        relativePaths.sorted().forEach { relativePath ->
            if (relativePath != MANIFEST_FILE) {
                result[relativePath] = Files.readAllBytes(root.resolve(relativePath))
            }
        }
        return result
    }

    private fun requiredDirectory(propertyName: String): Path {
        val value = requireNotNull(System.getProperty(propertyName)) {
            "Required JVM test property is missing: $propertyName."
        }
        require(value.isNotBlank()) { "Required JVM test property is blank: $propertyName." }
        val path = Path.of(value).toAbsolutePath().normalize()
        require(Files.isDirectory(path)) {
            "Required JVM test directory does not exist: $propertyName=$path."
        }
        return path
    }

    private fun parseManifest(path: Path): Map<String, Any?> {
        require(Files.isRegularFile(path)) { "Generated manifest does not exist: $path." }
        return R2StrictJson.parseObject(Files.readAllBytes(path))
    }

    private fun isStandardLibraryPath(path: String): Boolean =
        path.startsWith("lib/lib") &&
            path.endsWith(".d.ts") &&
            '/' !in path.removePrefix("lib/")

    private fun Map<String, Any?>.requireObject(key: String): Map<String, Any?> =
        this[key].requireObjectValue("Manifest field $key")

    private fun Any?.requireObjectValue(label: String): Map<String, Any?> {
        val value = this as? Map<*, *>
            ?: throw IllegalArgumentException("$label must be an object.")
        @Suppress("UNCHECKED_CAST")
        return value as Map<String, Any?>
    }

    private fun Map<String, Any?>.requireList(key: String): List<Any?> =
        this[key] as? List<*>
            ?: throw IllegalArgumentException("Manifest field $key must be an array.")

    private fun Map<String, Any?>.requireString(key: String): String =
        this[key] as? String
            ?: throw IllegalArgumentException("Manifest field $key must be a string.")

    private fun Map<String, Any?>.requireLong(key: String): Long {
        val value = this[key] as? R2StrictJson.NumberToken
            ?: throw IllegalArgumentException("Manifest field $key must be an integer.")
        return value.source.toLongOrNull()
            ?: throw IllegalArgumentException("Manifest field $key exceeds Long.")
    }

    private companion object {
        const val DECLARATION_DISTRIBUTION_PROPERTY =
            "r2.generatedDeclarationDistributionDir"
        const val TYPESCRIPT_DISTRIBUTION_PROPERTY =
            "r2.generatedTypeScriptDistributionDir"
        const val MANIFEST_FILE = "distribution.json"
    }
}
