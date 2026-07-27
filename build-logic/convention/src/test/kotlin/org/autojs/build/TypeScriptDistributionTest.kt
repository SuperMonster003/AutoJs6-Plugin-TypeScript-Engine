package org.autojs.build

import groovy.json.JsonSlurper
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
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

class TypeScriptDistributionTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `generation is canonical and independently verifiable`() {
        val tarball = createTarball("canonical.tgz", validEntries())
        val lock = createLock(tarball)
        val first = temporaryFolder.newFolder("first")
        val second = temporaryFolder.newFolder("second")

        TypeScriptDistribution.generate(lock, tarball, first)
        TypeScriptDistribution.generate(lock, tarball, second)
        TypeScriptDistribution.verify(lock, tarball, first)
        TypeScriptDistribution.verify(lock, tarball, second)

        val firstFiles = snapshot(first)
        val secondFiles = snapshot(second)
        assertEquals(firstFiles.keys, secondFiles.keys)
        firstFiles.forEach { (path, bytes) ->
            assertArrayEquals(path, bytes, secondFiles.getValue(path))
        }
        assertEquals(
            setOf(
                "LICENSE.txt",
                "ThirdPartyNoticeText.txt",
                "distribution.json",
                "lib/lib.d.ts",
                "lib/lib.es2024.d.ts",
                "lib/typescript.d.ts",
                "lib/typescript.js",
            ),
            firstFiles.keys,
        )

        val manifestBytes = firstFiles.getValue("distribution.json")
        val manifestText = manifestBytes.toString(StandardCharsets.UTF_8)
        assertTrue(manifestText.endsWith("\n"))
        assertFalse(manifestText.contains('\r'))
        assertFalse(manifestText.contains(first.absolutePath))
        @Suppress("UNCHECKED_CAST")
        val manifest = JsonSlurper().parseText(manifestText) as Map<String, Any?>
        assertEquals(1, manifest["schemaVersion"])
        assertTrue((manifest["fingerprint"] as String).matches(LOWER_SHA256))

        @Suppress("UNCHECKED_CAST")
        val components = manifest["components"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val libraries = components["standardLibraries"] as Map<String, Any?>
        assertEquals(2, libraries["fileCount"])
        assertEquals("lib/lib*.d.ts", libraries["pattern"])
    }

    @Test
    fun `unsafe paths anywhere in the archive are rejected`() {
        val unsafePaths = listOf(
            "../escape",
            "/absolute",
            "C:/drive",
            "package/../escape",
            "package/./escape",
            "package//escape",
            "outside/file.txt",
        )

        unsafePaths.forEachIndexed { index, unsafePath ->
            val tarball = createTarball(
                "unsafe-$index.tgz",
                validEntries() + ArchiveSpec.file(unsafePath, "unselected"),
            )
            val lock = createLock(tarball, "unsafe-$index.json")
            val output = File(temporaryFolder.root, "unsafe-$index-output")

            assertThrows(TypeScriptDistributionException::class.java) {
                TypeScriptDistribution.generate(lock, tarball, output)
            }
            assertFalse(output.exists())
        }
    }

    @Test
    fun `duplicate paths and archive links are rejected`() {
        val cases = listOf(
            validEntries() + ArchiveSpec.file(
                "package/lib/typescript.js",
                "duplicate",
            ),
            validEntries() + ArchiveSpec.file(
                "package/lib/TypeScript.js",
                "case-fold collision",
            ),
            validEntries() + ArchiveSpec.symbolicLink(
                "package/unselected-symbolic-link",
                "lib/typescript.js",
            ),
            validEntries() + ArchiveSpec.hardLink(
                "package/unselected-hard-link",
                "package/lib/typescript.js",
            ),
        )

        cases.forEachIndexed { index, entries ->
            val tarball = createTarball("unsafe-type-$index.tgz", entries)
            val lock = createLock(tarball, "unsafe-type-$index.json")

            assertThrows(TypeScriptDistributionException::class.java) {
                TypeScriptDistribution.generate(
                    lock,
                    tarball,
                    File(temporaryFolder.root, "unsafe-type-$index-output"),
                )
            }
        }
    }

    @Test
    fun `extended tar metadata and corrupt headers are rejected before extraction`() {
        val cases = listOf(
            validEntries() + ArchiveSpec.paxMetadata(
                "package/PaxHeaders/typescript",
                "30 path=package/lib/ignored.js\n",
            ),
            validEntries() + ArchiveSpec.gnuLongName(
                "package/GNU-LONG-NAME",
                "package/lib/ignored.js\u0000",
            ),
        )

        cases.forEachIndexed { index, entries ->
            val tarball = createTarball("metadata-$index.tgz", entries)
            val lock = createLock(tarball, "metadata-$index.json")
            assertThrows(TypeScriptDistributionException::class.java) {
                TypeScriptDistribution.generate(
                    lock,
                    tarball,
                    File(temporaryFolder.root, "metadata-$index-output"),
                )
            }
        }

        val validTarball = createTarball("checksum-source.tgz", validEntries())
        val corruptTarball = rewriteExpandedTarball(
            name = "checksum-corrupt.tgz",
            source = validTarball,
        ) { expanded ->
            expanded[0] = (expanded[0].toInt() xor 1).toByte()
            expanded
        }
        val corruptLock = createLock(corruptTarball, "checksum-corrupt.json")
        assertThrows(TypeScriptDistributionException::class.java) {
            TypeScriptDistribution.generate(
                corruptLock,
                corruptTarball,
                File(temporaryFolder.root, "checksum-corrupt-output"),
            )
        }
    }

    @Test
    fun `concatenated gzip and data after tar eof are rejected`() {
        val first = createTarball("first-member.tgz", validEntries())
        val second = createTarball("second-member.tgz", validEntries())
        val concatenated = File(temporaryFolder.root, "concatenated.tgz").apply {
            writeBytes(first.readBytes() + second.readBytes())
        }
        val concatenatedLock = createLock(concatenated, "concatenated.json")
        assertThrows(TypeScriptDistributionException::class.java) {
            TypeScriptDistribution.generate(
                concatenatedLock,
                concatenated,
                File(temporaryFolder.root, "concatenated-output"),
            )
        }

        val trailingTarData = rewriteExpandedTarball(
            name = "trailing-tar-data.tgz",
            source = first,
        ) { expanded ->
            expanded[expanded.lastIndex] = 1
            expanded
        }
        val trailingTarLock = createLock(trailingTarData, "trailing-tar-data.json")
        assertThrows(TypeScriptDistributionException::class.java) {
            TypeScriptDistribution.generate(
                trailingTarLock,
                trailingTarData,
                File(temporaryFolder.root, "trailing-tar-data-output"),
            )
        }

        val trailingCompressedData =
            File(temporaryFolder.root, "trailing-compressed-data.tgz").apply {
                writeBytes(first.readBytes() + "not-a-gzip-member".toByteArray())
            }
        val trailingCompressedLock =
            createLock(trailingCompressedData, "trailing-compressed-data.json")
        assertThrows(TypeScriptDistributionException::class.java) {
            TypeScriptDistribution.generate(
                trailingCompressedLock,
                trailingCompressedData,
                File(temporaryFolder.root, "trailing-compressed-data-output"),
            )
        }
    }

    @Test
    fun `archive entry and expansion limits cover unselected files`() {
        val tarball = createTarball(
            "limits.tgz",
            validEntries() + ArchiveSpec.file(
                "package/unselected-large.bin",
                "0123456789",
            ),
        )
        val lock = createLock(tarball, "limits.json")
        val output = File(temporaryFolder.root, "limits-output")

        assertThrows(TypeScriptDistributionException::class.java) {
            TypeScriptDistribution.generate(
                lock,
                tarball,
                output,
                TypeScriptDistributionLimits(
                    maximumTarballBytes = tarball.length() + 1,
                    maximumArchiveEntries = 256,
                    maximumArchiveFileBytes = 9,
                    maximumArchiveBytes = 1_024,
                ),
            )
        }
        assertThrows(TypeScriptDistributionException::class.java) {
            TypeScriptDistribution.generate(
                lock,
                tarball,
                output,
                TypeScriptDistributionLimits(
                    maximumTarballBytes = tarball.length() + 1,
                    maximumArchiveEntries = validEntries().size,
                    maximumArchiveFileBytes = 1_024,
                    maximumArchiveBytes = 1_024,
                ),
            )
        }
    }

    @Test
    fun `all three pinned digests are required and verified`() {
        val tarball = createTarball("digests.tgz", validEntries())
        val validLock = createLock(tarball, "digests-valid.json")
        val validText = validLock.readText()
        val mutations = listOf(
            Regex("\"sha1\":\"[0-9a-f]+\"") to
                "\"sha1\":\"${"0".repeat(40)}\"",
            Regex("\"sha256\":\"[0-9a-f]+\"") to
                "\"sha256\":\"${"0".repeat(64)}\"",
            Regex("\"integrity\":\"sha512-[^\"]+\"") to
                "\"integrity\":\"sha512-${Base64.getEncoder().encodeToString(ByteArray(64))}\"",
        )

        mutations.forEachIndexed { index, (pattern, replacement) ->
            val mutated = File(temporaryFolder.root, "digests-mutated-$index.json")
            mutated.writeText(validText.replace(pattern, replacement))
            assertThrows(TypeScriptDistributionException::class.java) {
                TypeScriptDistribution.verifyTarball(mutated, tarball)
            }
        }

        val missingSha256 = File(temporaryFolder.root, "digests-missing-sha256.json")
        missingSha256.writeText(
            validText.replace(Regex(",\"sha256\":\"[0-9a-f]+\""), ""),
        )
        assertThrows(TypeScriptDistributionException::class.java) {
            TypeScriptDistribution.verifyTarball(missingSha256, tarball)
        }
    }

    @Test
    fun `verification rejects tampering extra files and a noncanonical manifest`() {
        val tarball = createTarball("tamper.tgz", validEntries())
        val lock = createLock(tarball, "tamper.json")
        val output = File(temporaryFolder.root, "tamper-output")

        TypeScriptDistribution.generate(lock, tarball, output)
        File(output, "lib/typescript.js").appendText("tampered")
        assertThrows(TypeScriptDistributionException::class.java) {
            TypeScriptDistribution.verify(lock, tarball, output)
        }

        TypeScriptDistribution.generate(lock, tarball, output)
        File(output, "extra.txt").writeText("extra")
        assertThrows(TypeScriptDistributionException::class.java) {
            TypeScriptDistribution.verify(lock, tarball, output)
        }

        TypeScriptDistribution.generate(lock, tarball, output)
        File(output, TypeScriptDistribution.MANIFEST_FILE_NAME).appendText(" ")
        assertThrows(TypeScriptDistributionException::class.java) {
            TypeScriptDistribution.verify(lock, tarball, output)
        }
    }

    @Test
    fun `missing required files are rejected`() {
        val tarball = createTarball(
            "missing.tgz",
            validEntries().filterNot { it.name == "package/lib/typescript.d.ts" },
        )
        val lock = createLock(tarball, "missing.json")

        assertThrows(TypeScriptDistributionException::class.java) {
            TypeScriptDistribution.generate(
                lock,
                tarball,
                File(temporaryFolder.root, "missing-output"),
            )
        }
    }

    private fun validEntries(): List<ArchiveSpec> = listOf(
        ArchiveSpec.file(
            "package/lib/typescript.js",
            "var ts = { version: \"6.0.3\" };",
        ),
        ArchiveSpec.file(
            "package/lib/typescript.d.ts",
            "export const version: string;",
        ),
        ArchiveSpec.file("package/lib/lib.d.ts", "/// <reference no-default-lib=\"true\"/>"),
        ArchiveSpec.file("package/lib/lib.es2024.d.ts", "interface Symbol {}"),
        ArchiveSpec.file("package/LICENSE.txt", "Apache License"),
        ArchiveSpec.file("package/ThirdPartyNoticeText.txt", "Third-party notices"),
        ArchiveSpec.file("package/README.md", "not selected"),
    )

    private fun createTarball(
        name: String,
        entries: List<ArchiveSpec>,
    ): File {
        val tarball = File(temporaryFolder.root, name)
        Files.newOutputStream(tarball.toPath()).use { fileOutput ->
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

                            EntryType.HARD_LINK -> TarArchiveEntry(
                                spec.name,
                                TarConstants.LF_LINK,
                                true,
                            ).apply {
                                linkName = requireNotNull(spec.linkName)
                                size = 0L
                            }

                            EntryType.PAX_METADATA -> TarArchiveEntry(
                                spec.name,
                                TarConstants.LF_PAX_EXTENDED_HEADER_LC,
                                true,
                            ).apply {
                                size = spec.data.size.toLong()
                            }

                            EntryType.GNU_LONG_NAME -> TarArchiveEntry(
                                spec.name,
                                TarConstants.LF_GNUTYPE_LONGNAME,
                                true,
                            ).apply {
                                size = spec.data.size.toLong()
                            }
                        }
                        entry.setModTime(0L)
                        tar.putArchiveEntry(entry)
                        if (
                            spec.type == EntryType.FILE ||
                            spec.type == EntryType.PAX_METADATA ||
                            spec.type == EntryType.GNU_LONG_NAME
                        ) {
                            tar.write(spec.data)
                        }
                        tar.closeArchiveEntry()
                    }
                }
            }
        }
        return tarball
    }

    private fun rewriteExpandedTarball(
        name: String,
        source: File,
        transform: (ByteArray) -> ByteArray,
    ): File {
        val expanded = Files.newInputStream(source.toPath()).use { sourceInput ->
            GzipCompressorInputStream.builder()
                .setInputStream(sourceInput)
                .get()
                .use { gzipInput -> gzipInput.readBytes() }
        }
        return File(temporaryFolder.root, name).apply {
            Files.newOutputStream(toPath()).use { targetOutput ->
                GzipCompressorOutputStream(targetOutput).use { gzipOutput ->
                    gzipOutput.write(transform(expanded))
                }
            }
        }
    }

    private fun createLock(
        tarball: File,
        name: String = "compiler-distribution.lock.json",
    ): File {
        val bytes = tarball.readBytes()
        val sha1 = digest("SHA-1", bytes)
        val sha256 = digest("SHA-256", bytes)
        val integrity = "sha512-${Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-512").digest(bytes),
        )}"
        return File(temporaryFolder.root, name).apply {
            writeText(
                """{"declarationEntry":"package/lib/typescript.d.ts","integrity":"$integrity","licenseEntry":"package/LICENSE.txt","package":"typescript","runtimeEntry":"package/lib/typescript.js","schemaVersion":2,"sha1":"$sha1","sha256":"$sha256","source":"https://registry.npmjs.org/typescript/-/typescript-6.0.3.tgz","standardLibraryGlob":"package/lib/lib*.d.ts","thirdPartyNoticeEntry":"package/ThirdPartyNoticeText.txt","version":"6.0.3"}""",
            )
        }
    }

    private fun snapshot(root: File): Map<String, ByteArray> =
        root.walkTopDown()
            .filter(File::isFile)
            .associate { file ->
                file.relativeTo(root).invariantSeparatorsPath to file.readBytes()
            }
            .toSortedMap()

    private fun digest(algorithm: String, bytes: ByteArray): String =
        MessageDigest.getInstance(algorithm)
            .digest(bytes)
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }

    private enum class EntryType {
        FILE,
        GNU_LONG_NAME,
        SYMBOLIC_LINK,
        HARD_LINK,
        PAX_METADATA,
    }

    private data class ArchiveSpec(
        val name: String,
        val data: ByteArray,
        val type: EntryType,
        val linkName: String? = null,
    ) {
        companion object {
            fun file(name: String, contents: String) = ArchiveSpec(
                name = name,
                data = contents.toByteArray(StandardCharsets.UTF_8),
                type = EntryType.FILE,
            )

            fun symbolicLink(name: String, target: String) = ArchiveSpec(
                name = name,
                data = ByteArray(0),
                type = EntryType.SYMBOLIC_LINK,
                linkName = target,
            )

            fun hardLink(name: String, target: String) = ArchiveSpec(
                name = name,
                data = ByteArray(0),
                type = EntryType.HARD_LINK,
                linkName = target,
            )

            fun paxMetadata(name: String, contents: String) = ArchiveSpec(
                name = name,
                data = contents.toByteArray(StandardCharsets.UTF_8),
                type = EntryType.PAX_METADATA,
            )

            fun gnuLongName(name: String, contents: String) = ArchiveSpec(
                name = name,
                data = contents.toByteArray(StandardCharsets.UTF_8),
                type = EntryType.GNU_LONG_NAME,
            )
        }
    }

    companion object {
        private val LOWER_SHA256 = Regex("[0-9a-f]{64}")
    }
}
