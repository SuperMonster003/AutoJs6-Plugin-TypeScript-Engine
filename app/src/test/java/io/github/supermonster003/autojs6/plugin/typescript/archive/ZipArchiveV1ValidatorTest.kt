package io.github.supermonster003.autojs6.plugin.typescript.archive

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ZipArchiveV1ValidatorTest {

    @Test
    fun validTextGoldensCoverStoredDeflatedAsciiUtf8DescriptorCommentsAndEmptyArchive() {
        val fixtures = goldenSections("valid-golden.hex")
        assertEquals(
            setOf(
                "stored-ascii-comments",
                "deflated-ascii-utf8-signed-descriptor",
                "deflated-repetition-at-exact-100-to-1-bound",
                "empty",
            ),
            fixtures.keys,
        )

        val stored = ZipArchiveV1Validator.validate(
            fixtures.getValue("stored-ascii-comments"),
            TEST_LIMITS,
        )
        assertEquals(1, stored.entries.size)
        assertEquals("src/main.ts", stored.entries.single().path)
        assertEquals(0, stored.entries.single().compressionMethod)
        assertEquals(11L, stored.compressedBytes)
        assertEquals(11L, stored.uncompressedBytes)
        assertEquals(136L, stored.metadataBytes)
        assertEquals(stored.containerBytes, stored.metadataBytes + stored.compressedBytes)

        val descriptor = ZipArchiveV1Validator.validate(
            fixtures.getValue("deflated-ascii-utf8-signed-descriptor"),
            TEST_LIMITS,
        )
        assertEquals("src/main.ts", descriptor.entries.single().path)
        assertEquals(8, descriptor.entries.single().compressionMethod)
        assertEquals(19L, descriptor.uncompressedBytes)
        assertEquals(136L, descriptor.metadataBytes)
        assertEquals(descriptor.containerBytes, descriptor.metadataBytes + descriptor.compressedBytes)

        val repetition = ZipArchiveV1Validator.validate(
            fixtures.getValue("deflated-repetition-at-exact-100-to-1-bound"),
            TEST_LIMITS,
        )
        assertEquals(1_200L, repetition.uncompressedBytes)
        assertEquals(12L, repetition.compressedBytes)
        assertEquals(122L, repetition.metadataBytes)

        val empty = ZipArchiveV1Validator.validate(
            fixtures.getValue("empty"),
            TEST_LIMITS,
        )
        assertTrue(empty.entries.isEmpty())
        assertEquals(22L, empty.containerBytes)
        assertEquals(22L, empty.metadataBytes)
    }

    @Test
    fun invalidTextGoldensFreezeFormatIntegrityPathAndQuotaIndependentRejections() {
        val fixtures = goldenSections("invalid-golden.hex")
        val expected = linkedMapOf(
            "encrypted" to ZipArchiveV1FailureKind.FORMAT,
            "non-ascii-without-utf8-flag" to ZipArchiveV1FailureKind.PATH,
            "non-ascii-with-utf8-flag" to ZipArchiveV1FailureKind.PATH,
            "parent-traversal" to ZipArchiveV1FailureKind.PATH,
            "casefold-collision" to ZipArchiveV1FailureKind.PATH,
            "unix-symlink" to ZipArchiveV1FailureKind.PATH,
            "dos-volume-label" to ZipArchiveV1FailureKind.PATH,
            "dos-directory-attribute" to ZipArchiveV1FailureKind.PATH,
            "dos-reserved-type-bit" to ZipArchiveV1FailureKind.PATH,
            "local-central-name-mismatch" to ZipArchiveV1FailureKind.INTEGRITY,
            "crc-mismatch" to ZipArchiveV1FailureKind.INTEGRITY,
            "zip64-sentinel" to ZipArchiveV1FailureKind.FORMAT,
            "trailing-byte" to ZipArchiveV1FailureKind.FORMAT,
            "preamble" to ZipArchiveV1FailureKind.INTEGRITY,
            "extra-field" to ZipArchiveV1FailureKind.FORMAT,
            "unsigned-data-descriptor" to ZipArchiveV1FailureKind.FORMAT,
            "unsupported-method" to ZipArchiveV1FailureKind.FORMAT,
        )
        assertEquals(expected.keys, fixtures.keys)
        expected.forEach { (name, kind) ->
            assertRejected(kind, name) {
                ZipArchiveV1Validator.validate(fixtures.getValue(name), TEST_LIMITS)
            }
        }
    }

    @Test
    fun textGoldenFilesMatchTheirFrozenCanonicalSha256Manifest() {
        val expected = linkedMapOf(
            "valid-golden.hex" to
                "9dd0594ef28cd49d0903095b15b912142f7909b1ec8fffe2018052efd6c7af4f",
            "invalid-golden.hex" to
                "513334e8a499743f6df53e4b155579192a572c677cfaedbaecd714047c7337ef",
        )
        val manifest = fixtureText("fixtures.sha256")
            .lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
            .associate { line ->
                val fields = line.split(Regex("\\s+"), limit = 2)
                require(fields.size == 2) { "Malformed fixture SHA-256 manifest line: $line" }
                fields[1] to fields[0]
            }

        assertEquals(expected, manifest)
        expected.forEach { (fileName, digest) ->
            assertEquals(
                fileName,
                digest,
                canonicalFixtureSha256(fileName),
            )
        }
    }

    @Test
    fun fileOverloadUsesTheSameRawValidator() {
        val bytes = goldenSections("valid-golden.hex").getValue("stored-ascii-comments")
        val file = File.createTempFile("archive-v1-", ".zip")
        try {
            file.writeBytes(bytes)
            assertEquals(
                ZipArchiveV1Validator.validate(bytes, TEST_LIMITS),
                ZipArchiveV1Validator.validate(file, TEST_LIMITS),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun immutableFileSnapshotIsDetachedFromLaterSourceRewrite() {
        val validFixtures = goldenSections("valid-golden.hex")
        val original = validFixtures.getValue("stored-ascii-comments")
        val replacement = validFixtures.getValue("empty")
        val file = File.createTempFile("archive-v1-snapshot-", ".zip")
        try {
            file.writeBytes(original)
            val snapshot = ZipArchiveV1Validator.readImmutableFileSnapshot(
                file = file,
                maximumContainerBytes = TEST_LIMITS.maximumContainerBytes,
            )
            file.writeBytes(replacement)

            assertEquals(
                listOf("src/main.ts"),
                ZipArchiveV1Validator.validate(snapshot, TEST_LIMITS)
                    .entries
                    .map(ZipArchiveV1Entry::path),
            )
            assertTrue(
                ZipArchiveV1Validator.validate(file, TEST_LIMITS).entries.isEmpty(),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun printableAsciiPathsAndAsciiOnlyCaseCollisionsAreEnforced() {
        val printableAscii = storedArchive(
            RawEntry("AZ az-09_~.ts".toByteArray(Charsets.US_ASCII), flags = FLAG_UTF8),
        )
        assertEquals(
            "AZ az-09_~.ts",
            ZipArchiveV1Validator.validate(printableAscii, TEST_LIMITS).entries.single().path,
        )

        val nonAsciiWithUtf8Flag = storedArchive(
            RawEntry(
                "é.ts".toByteArray(Charsets.UTF_8),
                flags = FLAG_UTF8,
            ),
        )
        assertRejected(ZipArchiveV1FailureKind.PATH, "non-ASCII with UTF-8 flag") {
            ZipArchiveV1Validator.validate(nonAsciiWithUtf8Flag, TEST_LIMITS)
        }

        val nonAsciiWithoutUtf8Flag = storedArchive(
            RawEntry("é.ts".toByteArray(Charsets.UTF_8)),
        )
        assertRejected(ZipArchiveV1FailureKind.PATH, "non-ASCII without UTF-8 flag") {
            ZipArchiveV1Validator.validate(nonAsciiWithoutUtf8Flag, TEST_LIMITS)
        }

        val asciiCaseCollision = storedArchive(
            RawEntry("SRC/Main.TS".toByteArray(Charsets.US_ASCII)),
            RawEntry("src/main.ts".toByteArray(Charsets.US_ASCII)),
        )
        assertRejected(ZipArchiveV1FailureKind.PATH, "ASCII case collision") {
            ZipArchiveV1Validator.validate(asciiCaseCollision, TEST_LIMITS)
        }
    }

    @Test
    fun canonicalRelativePathRulesRejectEveryAliasClass() {
        val invalidPaths = listOf(
            "",
            "/absolute.ts",
            "C:/main.ts",
            "src\\main.ts",
            "src//main.ts",
            "src/./main.ts",
            "src/../main.ts",
            "src/\u0000main.ts",
            "directory/",
        )
        invalidPaths.forEach { path ->
            val archive = storedArchive(
                RawEntry(path.toByteArray(Charsets.UTF_8)),
            )
            assertRejected(ZipArchiveV1FailureKind.PATH, "path=$path") {
                ZipArchiveV1Validator.validate(archive, TEST_LIMITS)
            }
        }
    }

    @Test
    fun onlyRegularFatOrUnixEntriesAreAccepted() {
        val fatRegular = storedArchive(
            RawEntry(
                rawName = "fat-regular.ts".toByteArray(),
                externalAttributes = DOS_ALLOWED_REGULAR_ATTRIBUTES,
            ),
        )
        assertEquals(
            "fat-regular.ts",
            ZipArchiveV1Validator.validate(fatRegular, TEST_LIMITS).entries.single().path,
        )

        val unixRegular = storedArchive(
            RawEntry(
                rawName = "regular.ts".toByteArray(),
                creatorSystem = CREATOR_UNIX,
                externalAttributes = UNIX_REGULAR_0644,
            ),
        )
        assertEquals(
            "regular.ts",
            ZipArchiveV1Validator.validate(unixRegular, TEST_LIMITS).entries.single().path,
        )

        val unixDevice = storedArchive(
            RawEntry(
                rawName = "device".toByteArray(),
                creatorSystem = CREATOR_UNIX,
                externalAttributes = UNIX_CHARACTER_DEVICE_0600,
            ),
        )
        assertRejected(ZipArchiveV1FailureKind.PATH, "Unix device") {
            ZipArchiveV1Validator.validate(unixDevice, TEST_LIMITS)
        }

        val dosDirectory = storedArchive(
            RawEntry(
                rawName = "directory".toByteArray(),
                externalAttributes = DOS_DIRECTORY_ATTRIBUTE,
            ),
        )
        assertRejected(ZipArchiveV1FailureKind.PATH, "DOS directory") {
            ZipArchiveV1Validator.validate(dosDirectory, TEST_LIMITS)
        }

        listOf(DOS_VOLUME_LABEL_ATTRIBUTE, DOS_RESERVED_DEVICE_ATTRIBUTE, DOS_RESERVED_HIGH_BIT)
            .forEach { attribute ->
                val nonRegularDosEntry = storedArchive(
                    RawEntry(
                        rawName = "non-regular-dos-entry".toByteArray(),
                        externalAttributes = attribute,
                    ),
                )
                assertRejected(
                    ZipArchiveV1FailureKind.PATH,
                    "DOS non-regular/reserved attribute=$attribute",
                ) {
                    ZipArchiveV1Validator.validate(nonRegularDosEntry, TEST_LIMITS)
                }
            }

        val unsupportedCreator = storedArchive(
            RawEntry(
                rawName = "foreign.ts".toByteArray(),
                creatorSystem = 7,
            ),
        )
        assertRejected(ZipArchiveV1FailureKind.FORMAT, "creator system") {
            ZipArchiveV1Validator.validate(unsupportedCreator, TEST_LIMITS)
        }
    }

    @Test
    fun onlyFrozenFlagsAndSingleVolumeRecordsAreAccepted() {
        val unsupportedFlag = storedArchive(
            RawEntry("main.ts".toByteArray(), flags = 1 shl 2),
        )
        assertRejected(ZipArchiveV1FailureKind.FORMAT, "unsupported flag") {
            ZipArchiveV1Validator.validate(unsupportedFlag, TEST_LIMITS)
        }

        val localCentralFlagMismatch = storedArchive(
            RawEntry("main.ts".toByteArray()),
        ).copyOf().also { archive ->
            val central = archive.indexOfSignature(CENTRAL_DIRECTORY_SIGNATURE)
            archive.putU16(central + 8, FLAG_UTF8)
        }
        assertRejected(ZipArchiveV1FailureKind.INTEGRITY, "flag mismatch") {
            ZipArchiveV1Validator.validate(localCentralFlagMismatch, TEST_LIMITS)
        }

        val multiDisk = storedArchive(
            RawEntry("main.ts".toByteArray()),
        ).copyOf().also { archive ->
            val eocd = archive.indexOfSignature(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
            archive.putU16(eocd + 4, 1)
        }
        assertRejected(ZipArchiveV1FailureKind.FORMAT, "multi-disk") {
            ZipArchiveV1Validator.validate(multiDisk, TEST_LIMITS)
        }
    }

    @Test
    fun productionInputAndOutputQuotasMatchEveryFrozenV1Constant() {
        val input = ZipArchiveV1Limits.INPUT
        assertEquals(64L * MEBIBYTE, input.maximumContainerBytes)
        assertEquals(4_096, input.maximumFiles)
        assertEquals(64L * MEBIBYTE, input.maximumTotalUncompressedBytes)
        assertEquals(8L * MEBIBYTE, input.maximumSingleFileBytes)
        assertEquals(1_024, input.maximumEntryNameBytes)
        assertEquals(64, input.maximumPathDepth)
        assertEquals(8L * MEBIBYTE, input.maximumMetadataBytes)
        assertEquals(100, input.maximumCompressionRatio)

        val output = ZipArchiveV1Limits.OUTPUT
        assertEquals(128L * MEBIBYTE, output.maximumContainerBytes)
        assertEquals(8_192, output.maximumFiles)
        assertEquals(256L * MEBIBYTE, output.maximumTotalUncompressedBytes)
        assertEquals(256L * MEBIBYTE, output.maximumSingleFileBytes)
        assertEquals(1_024, output.maximumEntryNameBytes)
        assertEquals(64, output.maximumPathDepth)
        assertEquals(8L * MEBIBYTE, output.maximumMetadataBytes)
        assertEquals(100, output.maximumCompressionRatio)

        assertEquals(
            TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES,
            input.maximumContainerBytes,
        )
        assertEquals(TypeScriptCompilerContract.MAX_INPUT_FILES, input.maximumFiles)
        assertEquals(
            TypeScriptCompilerContract.MAX_INPUT_BYTES,
            input.maximumTotalUncompressedBytes,
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES,
            input.maximumSingleFileBytes,
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_OUTPUT_ARCHIVE_BYTES,
            output.maximumContainerBytes,
        )
        assertEquals(TypeScriptCompilerContract.MAX_OUTPUT_FILES, output.maximumFiles)
        assertEquals(
            TypeScriptCompilerContract.MAX_OUTPUT_BYTES,
            output.maximumTotalUncompressedBytes,
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES,
            output.maximumEntryNameBytes,
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH,
            output.maximumPathDepth,
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_ZIP_METADATA_BYTES,
            output.maximumMetadataBytes,
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_ARCHIVE_COMPRESSION_RATIO,
            output.maximumCompressionRatio,
        )
    }

    @Test
    fun everyBasicQuotaHasAnInclusiveBoundaryAndARejectingBoundary() {
        val bytes = goldenSections("valid-golden.hex").getValue("stored-ascii-comments")
        val baseline = ZipArchiveV1Validator.validate(bytes, TEST_LIMITS)

        assertEquals(
            baseline,
            ZipArchiveV1Validator.validate(
                bytes,
                TEST_LIMITS.copy(maximumContainerBytes = bytes.size.toLong()),
            ),
        )
        assertRejected(ZipArchiveV1FailureKind.QUOTA, "container bytes") {
            ZipArchiveV1Validator.validate(
                bytes,
                TEST_LIMITS.copy(maximumContainerBytes = bytes.size - 1L),
            )
        }

        ZipArchiveV1Validator.validate(
            bytes,
            TEST_LIMITS.copy(maximumFiles = 1),
        )
        assertRejected(ZipArchiveV1FailureKind.QUOTA, "file count") {
            ZipArchiveV1Validator.validate(
                bytes,
                TEST_LIMITS.copy(maximumFiles = 0),
            )
        }

        ZipArchiveV1Validator.validate(
            bytes,
            TEST_LIMITS.copy(maximumSingleFileBytes = 11L),
        )
        assertRejected(ZipArchiveV1FailureKind.QUOTA, "single file") {
            ZipArchiveV1Validator.validate(
                bytes,
                TEST_LIMITS.copy(maximumSingleFileBytes = 10L),
            )
        }

        ZipArchiveV1Validator.validate(
            bytes,
            TEST_LIMITS.copy(maximumTotalUncompressedBytes = 11L),
        )
        assertRejected(ZipArchiveV1FailureKind.QUOTA, "total bytes") {
            ZipArchiveV1Validator.validate(
                bytes,
                TEST_LIMITS.copy(maximumTotalUncompressedBytes = 10L),
            )
        }

        ZipArchiveV1Validator.validate(
            bytes,
            TEST_LIMITS.copy(maximumEntryNameBytes = 11),
        )
        assertRejected(ZipArchiveV1FailureKind.QUOTA, "entry name bytes") {
            ZipArchiveV1Validator.validate(
                bytes,
                TEST_LIMITS.copy(maximumEntryNameBytes = 10),
            )
        }

        ZipArchiveV1Validator.validate(
            bytes,
            TEST_LIMITS.copy(maximumPathDepth = 2),
        )
        assertRejected(ZipArchiveV1FailureKind.QUOTA, "path depth") {
            ZipArchiveV1Validator.validate(
                bytes,
                TEST_LIMITS.copy(maximumPathDepth = 1),
            )
        }

        ZipArchiveV1Validator.validate(
            bytes,
            TEST_LIMITS.copy(maximumMetadataBytes = baseline.metadataBytes),
        )
        assertRejected(ZipArchiveV1FailureKind.QUOTA, "metadata bytes") {
            ZipArchiveV1Validator.validate(
                bytes,
                TEST_LIMITS.copy(maximumMetadataBytes = baseline.metadataBytes - 1L),
            )
        }
    }

    @Test
    fun compressionRatioUsesActualIntegerBytesWithoutFloatingPointRounding() {
        val bytes = goldenSections("valid-golden.hex")
            .getValue("deflated-repetition-at-exact-100-to-1-bound")
        ZipArchiveV1Validator.validate(
            bytes,
            TEST_LIMITS.copy(maximumCompressionRatio = 100),
        )
        assertRejected(ZipArchiveV1FailureKind.QUOTA, "99:1 ratio") {
            ZipArchiveV1Validator.validate(
                bytes,
                TEST_LIMITS.copy(maximumCompressionRatio = 99),
            )
        }
    }

    @Test
    fun actualStreamingRatioCannotBeHiddenByForgedDeclaredSize() {
        val forged = goldenSections("valid-golden.hex")
            .getValue("deflated-repetition-at-exact-100-to-1-bound")
            .copyOf()
            .also { archive ->
                val local = archive.indexOfSignature(LOCAL_FILE_HEADER_SIGNATURE)
                val central = archive.indexOfSignature(CENTRAL_DIRECTORY_SIGNATURE)
                archive.putU32(local + 22, 10L)
                archive.putU32(central + 24, 10L)
            }
        assertRejected(ZipArchiveV1FailureKind.QUOTA, "actual streaming ratio") {
            ZipArchiveV1Validator.validate(
                forged,
                TEST_LIMITS.copy(maximumCompressionRatio = 99),
            )
        }
    }

    @Test
    fun declaredStoredSizeAndActualPayloadMustAgree() {
        val forged = storedArchive(
            RawEntry("main.ts".toByteArray(), data = "payload".toByteArray()),
        ).copyOf().also { archive ->
            val local = archive.indexOfSignature(LOCAL_FILE_HEADER_SIGNATURE)
            val central = archive.indexOfSignature(CENTRAL_DIRECTORY_SIGNATURE)
            archive.putU32(local + 22, 8L)
            archive.putU32(central + 24, 8L)
        }
        assertRejected(ZipArchiveV1FailureKind.INTEGRITY, "forged STORED size") {
            ZipArchiveV1Validator.validate(forged, TEST_LIMITS)
        }
    }

    private fun goldenSections(fileName: String): LinkedHashMap<String, ByteArray> {
        val text = fixtureText(fileName)
        val sections = linkedMapOf<String, StringBuilder>()
        var current: StringBuilder? = null
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith('[') && line.endsWith(']') -> {
                    val name = line.substring(1, line.length - 1)
                    check(name.isNotBlank() && name !in sections)
                    current = StringBuilder().also { sections[name] = it }
                }

                else -> requireNotNull(current) {
                    "Hex bytes appear before a fixture section in $fileName."
                }.append(line)
            }
        }
        return LinkedHashMap(
            sections.mapValues { (name, hexBuilder) ->
                val hex = hexBuilder.toString()
                require(hex.length % 2 == 0) { "Odd hex length for $name." }
                ByteArray(hex.length / 2) { index ->
                    hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
                }
            },
        )
    }

    private fun fixtureText(fileName: String): String =
        requireNotNull(
            javaClass.getResourceAsStream("/archive-v1/$fileName"),
        ) {
            "Missing golden fixture $fileName."
        }.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }

    private fun canonicalFixtureSha256(fileName: String): String {
        val canonicalBytes = fixtureText(fileName)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(canonicalBytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun assertRejected(
        expectedKind: ZipArchiveV1FailureKind,
        label: String,
        block: () -> Unit,
    ) {
        try {
            block()
            fail("$label unexpectedly passed ZIP v1 validation.")
        } catch (failure: ZipArchiveV1Exception) {
            assertEquals(label, expectedKind, failure.kind)
        }
    }

    private fun storedArchive(
        vararg entries: RawEntry,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val localOffsets = ArrayList<Int>(entries.size)
        entries.forEach { entry ->
            localOffsets += output.size()
            val crc = CRC32().apply { update(entry.data) }.value
            output.putU32(LOCAL_FILE_HEADER_SIGNATURE)
            output.putU16(VERSION_STORED)
            output.putU16(entry.flags)
            output.putU16(0)
            output.putU16(0)
            output.putU16(0)
            output.putU32(crc)
            output.putU32(entry.data.size.toLong())
            output.putU32(entry.data.size.toLong())
            output.putU16(entry.rawName.size)
            output.putU16(0)
            output.write(entry.rawName)
            output.write(entry.data)
        }

        val centralOffset = output.size()
        entries.forEachIndexed { index, entry ->
            val crc = CRC32().apply { update(entry.data) }.value
            output.putU32(CENTRAL_DIRECTORY_SIGNATURE)
            output.putU16((entry.creatorSystem shl 8) or VERSION_DEFLATED)
            output.putU16(VERSION_STORED)
            output.putU16(entry.flags)
            output.putU16(0)
            output.putU16(0)
            output.putU16(0)
            output.putU32(crc)
            output.putU32(entry.data.size.toLong())
            output.putU32(entry.data.size.toLong())
            output.putU16(entry.rawName.size)
            output.putU16(0)
            output.putU16(0)
            output.putU16(0)
            output.putU16(0)
            output.putU32(entry.externalAttributes)
            output.putU32(localOffsets[index].toLong())
            output.write(entry.rawName)
        }
        val centralSize = output.size() - centralOffset
        output.putU32(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
        output.putU16(0)
        output.putU16(0)
        output.putU16(entries.size)
        output.putU16(entries.size)
        output.putU32(centralSize.toLong())
        output.putU32(centralOffset.toLong())
        output.putU16(0)
        return output.toByteArray()
    }

    private fun ByteArray.indexOfSignature(signature: Long): Int {
        for (index in 0..size - 4) {
            if (
                (
                    (this[index].toLong() and 0xffL) or
                        ((this[index + 1].toLong() and 0xffL) shl 8) or
                        ((this[index + 2].toLong() and 0xffL) shl 16) or
                        ((this[index + 3].toLong() and 0xffL) shl 24)
                    ) == signature
            ) {
                return index
            }
        }
        error("Signature ${signature.toString(16)} not found.")
    }

    private fun ByteArray.putU16(
        offset: Int,
        value: Int,
    ) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.putU32(
        offset: Int,
        value: Long,
    ) {
        repeat(4) { index ->
            this[offset + index] = (value ushr (index * 8)).toByte()
        }
    }

    private fun ByteArrayOutputStream.putU16(value: Int) {
        write(value and 0xff)
        write(value ushr 8 and 0xff)
    }

    private fun ByteArrayOutputStream.putU32(value: Long) {
        repeat(4) { index ->
            write((value ushr (index * 8)).toInt() and 0xff)
        }
    }

    private data class RawEntry(
        val rawName: ByteArray,
        val data: ByteArray = "x".toByteArray(),
        val flags: Int = 0,
        val creatorSystem: Int = 0,
        val externalAttributes: Long = 0L,
    )

    private companion object {
        val TEST_LIMITS = ZipArchiveV1Limits(
            maximumContainerBytes = 1024L * 1024L,
            maximumFiles = 100,
            maximumTotalUncompressedBytes = 1024L * 1024L,
            maximumSingleFileBytes = 1024L * 1024L,
            maximumEntryNameBytes = 1_024,
            maximumPathDepth = 64,
            maximumMetadataBytes = 1024L * 1024L,
            maximumCompressionRatio = 100,
        )

        const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L
        const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L
        const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L
        const val VERSION_STORED = 10
        const val VERSION_DEFLATED = 20
        const val FLAG_UTF8 = 1 shl 11
        const val CREATOR_UNIX = 3
        const val DOS_VOLUME_LABEL_ATTRIBUTE = 0x08L
        const val DOS_DIRECTORY_ATTRIBUTE = 0x10L
        const val DOS_ALLOWED_REGULAR_ATTRIBUTES = 0x27L
        const val DOS_RESERVED_DEVICE_ATTRIBUTE = 0x40L
        const val DOS_RESERVED_HIGH_BIT = 0x80L
        const val UNIX_REGULAR_0644 = 0x81a4_0000L
        const val UNIX_CHARACTER_DEVICE_0600 = 0x21_80_0000L
        const val MEBIBYTE = 1024L * 1024L
    }
}
