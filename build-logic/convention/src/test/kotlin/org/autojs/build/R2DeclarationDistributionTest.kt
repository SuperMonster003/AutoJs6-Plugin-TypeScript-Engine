package org.autojs.build

import groovy.json.JsonSlurper
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.util.TreeMap

class R2DeclarationDistributionTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `generation is deterministic and verification rejects output drift`() {
        val fixture = fixture("canonical", validArchiveEntries())
        val first = temporaryFolder.newFolder("first-output")
        val second = temporaryFolder.newFolder("second-output")

        generate(fixture, first)
        generate(fixture, second)
        verify(fixture, first)
        verify(fixture, second)
        val firstSnapshot = snapshot(first)
        val secondSnapshot = snapshot(second)
        assertEquals(firstSnapshot.keys, secondSnapshot.keys)
        firstSnapshot.forEach { (path, bytes) ->
            assertArrayEquals(path, bytes, secondSnapshot.getValue(path))
        }

        val manifestBytes = firstSnapshot.getValue("distribution.json")
        val manifestText = manifestBytes.toString(StandardCharsets.UTF_8)
        assertTrue(manifestText.endsWith("\n"))
        assertFalse(manifestText.contains('\r'))
        @Suppress("UNCHECKED_CAST")
        val manifest = JsonSlurper().parseText(manifestText) as Map<String, Any?>
        assertEquals(
            setOf("files", "fingerprint", "generator", "layers", "profiles", "schemaVersion", "sources"),
            manifest.keys,
        )
        @Suppress("UNCHECKED_CAST")
        val layers = manifest["layers"] as Map<String, Map<String, Any?>>
        assertEquals(108, (layers.getValue("common").getValue("fileCount") as Number).toInt())
        assertEquals(59, (layers.getValue("node").getValue("fileCount") as Number).toInt())
        @Suppress("UNCHECKED_CAST")
        val nodeFiles = layers.getValue("node").getValue("files") as List<String>
        assertTrue("layers/node/input_observer.d.ts" in nodeFiles)
        val projection = firstSnapshot.getValue("layers/rhino/index.d.ts")
            .toString(StandardCharsets.UTF_8)
        listOf("HEIGHT", "WIDTH", "\$toast", "sleep", "toast", "toastLog").forEach {
            assertTrue("projection must contain $it", it in projection)
        }
        assertFalse(projection.contains("canary", ignoreCase = true))

        File(first, "layers/rhino/index.d.ts").appendText("tamper")
        assertThrows(R2DeclarationDistributionException::class.java) {
            verify(fixture, first)
        }
        generate(fixture, first)
        File(first, "layers/node/input_observer.d.ts").delete()
        assertThrows(R2DeclarationDistributionException::class.java) {
            verify(fixture, first)
        }
        generate(fixture, first)
        File(first, "extra.txt").writeText("extra")
        assertThrows(R2DeclarationDistributionException::class.java) {
            verify(fixture, first)
        }
    }

    @Test
    fun `source hash archive link and case collisions fail closed`() {
        val valid = fixture("source-tamper", validArchiveEntries())
        File(valid.nodeSource, "module-00.d.ts").appendText("tamper")
        assertThrows(R2DeclarationDistributionException::class.java) {
            generate(valid, temporaryFolder.newFolder("source-tamper-output"))
        }

        val linkFixture = fixture(
            "archive-link",
            validArchiveEntries() + ArchiveSpec.symbolicLink(
                "package/unselected-link",
                "declarations/autojs6/aj6-int-init.d.ts",
            ),
        )
        assertThrows(R2DeclarationDistributionException::class.java) {
            generate(linkFixture, temporaryFolder.newFolder("archive-link-output"))
        }

        val collisionFixture = fixture(
            "archive-collision",
            validArchiveEntries() +
                ArchiveSpec.file("package/Unselected.txt", "a") +
                ArchiveSpec.file("package/unselected.txt", "b"),
        )
        assertThrows(R2DeclarationDistributionException::class.java) {
            generate(collisionFixture, temporaryFolder.newFolder("archive-collision-output"))
        }
    }

    private fun fixture(name: String, archiveEntries: List<ArchiveSpec>): Fixture {
        val root = temporaryFolder.newFolder(name)
        val tarball = createTarball(File(root, "rhino.tgz"), archiveEntries)
        val nodeSource = File(root, "node").apply { mkdirs() }
        createNodeSources(nodeSource)
        val typeScript = File(root, "typescript").apply { mkdirs() }
        File(typeScript, "distribution.json").writeText(
            """{"components":{"standardLibraries":{"fileCount":108,"fingerprint":"${R2DeclarationDistribution.COMMON_STANDARD_LIBRARIES_FINGERPRINT}","totalBytes":3784758}},"fingerprint":"${R2DeclarationDistribution.COMMON_DISTRIBUTION_FINGERPRINT}","package":{"name":"typescript","version":"6.0.3"},"schemaVersion":1}""",
            Charsets.UTF_8,
        )
        val lock = File(root, "r2-declarations.lock.json")
        createLock(lock, tarball, nodeSource, archiveEntries)
        return Fixture(lock, tarball, nodeSource, typeScript)
    }

    private fun generate(fixture: Fixture, output: File) {
        R2DeclarationDistribution.generate(
            fixture.lock,
            fixture.tarball,
            fixture.nodeSource,
            fixture.typeScriptDistribution,
            output,
        )
    }

    private fun verify(fixture: Fixture, output: File) {
        R2DeclarationDistribution.verify(
            fixture.lock,
            fixture.tarball,
            fixture.nodeSource,
            fixture.typeScriptDistribution,
            output,
        )
    }

    private fun createNodeSources(root: File) {
        File(root, "LICENSE").writeText("Mozilla Public License Version 2.0\n")
        File(root, "index.d.ts").writeText("export {};\n")
        File(root, "input_observer.d.ts").writeText("export interface InputObserver {}\n")
        repeat(57) { index ->
            File(root, "module-${index.toString().padStart(2, '0')}.d.ts")
                .writeText("export interface Module$index {}\n")
        }
        File(root, "package.json").writeText(
            """{"name":"@autojs6/node-types","private":true,"types":"index.d.ts","version":"1.0.0"}
""",
        )
    }

    private fun createLock(
        target: File,
        tarball: File,
        nodeSource: File,
        archiveEntries: List<ArchiveSpec>,
    ) {
        val selected = archiveEntries
            .filter { it.type == EntryType.FILE && it.name in RHINO_PATHS }
            .associateBy(ArchiveSpec::name)
        val tarballBytes = tarball.readBytes()
        val nodeFiles = nodeSource.listFiles()!!.filter(File::isFile).map { file ->
            mapOf(
                "gitBlobSha1" to gitBlobSha1(file.readBytes()),
                "path" to file.name,
                "sha256" to digest("SHA-256", file.readBytes()),
                "size" to file.length(),
            )
        }.sortedBy { it.getValue("path") as String }
        val rhinoFiles = RHINO_PATHS.map { path ->
            val bytes = selected.getValue(path).data
            mapOf(
                "path" to path,
                "sha256" to digest("SHA-256", bytes),
                "size" to bytes.size.toLong(),
            )
        }
        val lock = mapOf(
            "schemaVersion" to 1L,
            "common" to mapOf(
                "package" to "typescript",
                "version" to "6.0.3",
                "distributionFingerprint" to R2DeclarationDistribution.COMMON_DISTRIBUTION_FINGERPRINT,
                "standardLibrariesFingerprint" to R2DeclarationDistribution.COMMON_STANDARD_LIBRARIES_FINGERPRINT,
                "declarationFileCount" to 108L,
                "declarationTotalBytes" to 3_784_758L,
            ),
            "rhino" to mapOf(
                "package" to "@sm003/autojs6-dts",
                "version" to "2.1.3",
                "source" to "https://registry.npmjs.org/@sm003/autojs6-dts/-/autojs6-dts-2.1.3.tgz",
                "tarballBytes" to tarballBytes.size.toLong(),
                "sha1" to digest("SHA-1", tarballBytes),
                "sha256" to digest("SHA-256", tarballBytes),
                "integrity" to "sha512-${Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(tarballBytes))}",
                "conversion" to "autojs6-rhino-surface-v1",
                "license" to "MPL-2.0",
                "licenseEntry" to "package/LICENSE",
                "files" to rhinoFiles,
            ),
            "node" to mapOf(
                "repositoryIdentity" to "AutoJs6-Private",
                "commit" to "1d74c57aee7d4f6e300acedee395da6110fc8954",
                "hostBuild" to 5253L,
                "sourceAvailability" to "vendored-private-host-snapshot",
                "sourceDirectory" to "r2-declaration-sources/node",
                "license" to "MPL-2.0",
                "licenseFile" to "LICENSE",
                "packageFile" to "package.json",
                "files" to nodeFiles,
            ),
        )
        target.writeBytes(CanonicalJson.encode(lock))
    }

    private fun validArchiveEntries(): List<ArchiveSpec> = listOf(
        ArchiveSpec.file("package/LICENSE", "Mozilla Public License Version 2.0\n"),
        ArchiveSpec.file(
            "package/declarations/autojs6/aj6-int-globals.d.ts",
            """declare let HEIGHT: number;
declare let WIDTH: number;
declare function sleep(millis: number): void;
declare function sleep(millisMin: number, millisMax: number): void;
declare function sleep(millis: number, bounds: string): void;
declare function toastLog(msg: string | any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;
""",
        ),
        ArchiveSpec.file(
            "package/declarations/autojs6/aj6-int-init.d.ts",
            "declare let toast: Internal.Toast;\ndeclare let \$toast: Internal.Toast;\n",
        ),
        ArchiveSpec.file(
            "package/declarations/autojs6/aj6-int-toast.d.ts",
            """declare namespace Internal {
  interface Toast {
    (text: string | any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;
    dismissAll(): void;
  }
}
declare namespace Toast {
  type IsLong = 'long' | 'l' | 'short' | 's' | boolean;
  type IsForcible = 'forcible' | 'f' | boolean;
}
""",
        ),
        ArchiveSpec.file("package/unselected.txt", "not selected"),
    )

    private fun createTarball(target: File, entries: List<ArchiveSpec>): File {
        Files.newOutputStream(target.toPath()).use { fileOutput ->
            GzipCompressorOutputStream(fileOutput).use { gzipOutput ->
                TarArchiveOutputStream(gzipOutput, StandardCharsets.UTF_8.name()).use { tar ->
                    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_ERROR)
                    tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_ERROR)
                    entries.forEach { spec ->
                        val entry = when (spec.type) {
                            EntryType.FILE -> TarArchiveEntry(spec.name, true).apply {
                                size = spec.data.size.toLong()
                            }
                            EntryType.SYMBOLIC_LINK -> TarArchiveEntry(
                                spec.name,
                                TarConstants.LF_SYMLINK,
                                true,
                            ).apply {
                                linkName = requireNotNull(spec.linkName)
                                size = 0L
                            }
                        }
                        entry.setModTime(0L)
                        tar.putArchiveEntry(entry)
                        if (spec.type == EntryType.FILE) tar.write(spec.data)
                        tar.closeArchiveEntry()
                    }
                }
            }
        }
        return target
    }

    private fun snapshot(root: File): Map<String, ByteArray> = root.walkTopDown()
        .filter(File::isFile)
        .associate { it.relativeTo(root).invariantSeparatorsPath to it.readBytes() }
        .toSortedMap()

    private fun digest(algorithm: String, bytes: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(bytes).toLowerHex()

    private fun gitBlobSha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${bytes.size}\u0000".toByteArray(StandardCharsets.US_ASCII))
        digest.update(bytes)
        return digest.digest().toLowerHex()
    }

    private fun ByteArray.toLowerHex(): String =
        joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }

    private data class Fixture(
        val lock: File,
        val tarball: File,
        val nodeSource: File,
        val typeScriptDistribution: File,
    )

    private enum class EntryType { FILE, SYMBOLIC_LINK }

    private data class ArchiveSpec(
        val name: String,
        val data: ByteArray,
        val type: EntryType,
        val linkName: String? = null,
    ) {
        companion object {
            fun file(name: String, contents: String) = ArchiveSpec(
                name,
                contents.toByteArray(StandardCharsets.UTF_8),
                EntryType.FILE,
            )

            fun symbolicLink(name: String, target: String) = ArchiveSpec(
                name,
                ByteArray(0),
                EntryType.SYMBOLIC_LINK,
                target,
            )
        }
    }

    private object CanonicalJson {
        fun encode(value: Any?): ByteArray =
            (encodeValue(value) + "\n").toByteArray(StandardCharsets.UTF_8)

        private fun encodeValue(value: Any?): String = when (value) {
            null -> "null"
            is Boolean, is Number -> value.toString()
            is String -> buildString {
                append('"')
                value.forEach { character ->
                    when (character) {
                        '"' -> append("\\\"")
                        '\\' -> append("\\\\")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(character)
                    }
                }
                append('"')
            }
            is List<*> -> value.joinToString(
                separator = ",",
                prefix = "[",
                postfix = "]",
            ) { encodeValue(it) }
            is Map<*, *> -> value.entries
                .map { (key, item) -> key as String to item }
                .sortedBy { it.first }
                .joinToString(separator = ",", prefix = "{", postfix = "}") { (key, item) ->
                    "${encodeValue(key)}:${encodeValue(item)}"
                }
            else -> error("unsupported JSON type: ${value::class.java.name}")
        }
    }

    companion object {
        private val RHINO_PATHS = listOf(
            "package/LICENSE",
            "package/declarations/autojs6/aj6-int-globals.d.ts",
            "package/declarations/autojs6/aj6-int-init.d.ts",
            "package/declarations/autojs6/aj6-int-toast.d.ts",
        )
    }
}
