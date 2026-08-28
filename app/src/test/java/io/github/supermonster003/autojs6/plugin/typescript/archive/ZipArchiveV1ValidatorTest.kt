package io.github.supermonster003.autojs6.plugin.typescript.archive

import io.github.supermonster003.autojs6.plugin.typescript.R2_INPUT_ARCHIVE_MAXIMUM_CONTAINER_BYTES
import io.github.supermonster003.autojs6.plugin.typescript.R2_INPUT_ARCHIVE_MAXIMUM_EXPANDED_BYTES
import io.github.supermonster003.autojs6.plugin.typescript.R2_INPUT_ARCHIVE_MAXIMUM_FILES
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
        assertEquals(
            "e\u0301.ts",
            ZipArchiveV1Validator.validate(
                fixtures.getValue("non-ascii-with-utf8-flag"),
                TEST_LIMITS,
            ).entries.single().path,
        )
        val expected = linkedMapOf(
            "encrypted" to ZipArchiveV1FailureKind.FORMAT,
            "non-ascii-without-utf8-flag" to ZipArchiveV1FailureKind.PATH,
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
        assertEquals(expected.keys + "non-ascii-with-utf8-flag", fixtures.keys)
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
    fun immutableHeldReaderMatchesTheByteArrayValidatorAcrossTheFrozenCorpus() {
        goldenSections("valid-golden.hex").forEach { (name, archive) ->
            val expected = ZipArchiveV1Validator.validate(archive, TEST_LIMITS)
            withTemporaryArchive("archive-v1-held-valid-", archive) { file ->
                RandomAccessFile(file, "r").use { heldReader ->
                    assertEquals(
                        name,
                        expected,
                        ZipArchiveV1Validator.validateImmutable(
                            heldReader,
                            TEST_LIMITS,
                        ),
                    )
                    assertTrue(
                        "$name: immutable validation must leave the caller's handle open.",
                        heldReader.fd.valid(),
                    )
                    heldReader.seek(0L)
                    assertEquals(
                        "$name: the caller must remain able to read the held inode.",
                        archive[0].toInt() and 0xff,
                        heldReader.read(),
                    )
                }
            }
        }

        val formerlyInvalid = goldenSections("invalid-golden.hex")
            .getValue("non-ascii-with-utf8-flag")
        withTemporaryArchive("archive-v1-held-utf8-", formerlyInvalid) { file ->
            RandomAccessFile(file, "r").use { heldReader ->
                assertEquals(
                    "e\u0301.ts",
                    ZipArchiveV1Validator.validateImmutable(
                        heldReader,
                        TEST_LIMITS,
                    ).entries.single().path,
                )
            }
        }

        goldenSections("invalid-golden.hex")
            .filterKeys { name -> name != "non-ascii-with-utf8-flag" }
            .forEach { (name, archive) ->
                val expectedKind = rejectedKind {
                    ZipArchiveV1Validator.validate(archive, TEST_LIMITS)
                }
                withTemporaryArchive("archive-v1-held-invalid-", archive) { file ->
                    RandomAccessFile(file, "r").use { heldReader ->
                        assertRejected(expectedKind, name) {
                            ZipArchiveV1Validator.validateImmutable(
                                heldReader,
                                TEST_LIMITS,
                            )
                        }
                        assertTrue(
                            "$name: rejected immutable validation must leave the caller's handle open.",
                            heldReader.fd.valid(),
                        )
                    }
                }
            }
    }

    @Test
    fun immutableOutputValidationStreamsLargeUncompressedPayloadWithoutReturningEntryBytes() {
        val uncompressedBytes = 4L * MEBIBYTE
        val archive = moderatelyCompressedArchive(
            path = "modules/large.js",
            uncompressedByteCount = uncompressedBytes.toInt(),
        )
        assertTrue(
            "The fixture must exercise inflation rather than a container-sized payload.",
            archive.size.toLong() < uncompressedBytes,
        )

        withTemporaryArchive("archive-v1-held-output-", archive) { file ->
            RandomAccessFile(file, "r").use { heldReader ->
                val validation = ZipArchiveV1Validator.validateImmutable(
                    heldReader,
                    ZipArchiveV1Limits.OUTPUT,
                )

                assertEquals(archive.size.toLong(), validation.containerBytes)
                assertEquals(uncompressedBytes, validation.uncompressedBytes)
                assertEquals(1, validation.entries.size)
                assertEquals("modules/large.js", validation.entries.single().path)
                assertTrue(
                    "Streaming validation must leave the private output inode open.",
                    heldReader.fd.valid(),
                )
            }
        }
    }

    @Test
    fun immutableStagingReaderReturnsActualStoredAndDeflatedBytesWithoutOwningOpenFile() {
        val fixtures = goldenSections("valid-golden.hex")
        val storedBytes = fixtures.getValue("stored-ascii-comments")
        withTemporaryArchive("archive-v1-stored-", storedBytes) { file ->
            val contents = ZipArchiveV1Validator.validateImmutableAndRead(file, TEST_LIMITS)

            assertEquals(
                ZipArchiveV1Validator.validate(storedBytes, TEST_LIMITS),
                contents.validation,
            )
            assertEquals(listOf("src/main.ts"), contents.entries.keys.toList())
            assertArrayEquals(
                "let x = 1;\n".toByteArray(Charsets.UTF_8),
                contents.entries.getValue("src/main.ts"),
            )
        }

        val deflatedBytes = fixtures.getValue("deflated-ascii-utf8-signed-descriptor")
        withTemporaryArchive("archive-v1-deflated-", deflatedBytes) { file ->
            RandomAccessFile(file, "r").use { openedFile ->
                val contents = ZipArchiveV1Validator.validateImmutableAndRead(
                    openedFile,
                    TEST_LIMITS,
                )

                assertEquals(
                    ZipArchiveV1Validator.validate(deflatedBytes, TEST_LIMITS),
                    contents.validation,
                )
                assertArrayEquals(
                    "const 答案 = 42;\n".toByteArray(Charsets.UTF_8),
                    contents.entries.getValue("src/main.ts"),
                )
                assertEquals(
                    "The immutable staging reader must not close its RandomAccessFile.",
                    deflatedBytes.size.toLong(),
                    openedFile.length(),
                )
            }
        }
    }

    @Test
    fun immutableStreamingSinkCoversStoredAndDeflatedPayloadsWithBoundedChunks() {
        val cases = listOf(
            StreamingFixture(
                label = "stored",
                path = "sources/stored.ts",
                payload = patternedBytes(3 * STREAMING_CHUNK_BYTES + 73),
                archive = storedArchive(
                    RawEntry(
                        rawName = "sources/stored.ts".toByteArray(Charsets.US_ASCII),
                        data = patternedBytes(3 * STREAMING_CHUNK_BYTES + 73),
                    ),
                ),
            ),
            StreamingFixture(
                label = "deflated",
                path = "sources/deflated.ts",
                payload = moderatelyCompressedPayload(5 * STREAMING_CHUNK_BYTES + 91),
                archive = moderatelyCompressedArchive(
                    path = "sources/deflated.ts",
                    uncompressedByteCount = 5 * STREAMING_CHUNK_BYTES + 91,
                ),
            ),
        )

        cases.forEach { fixture ->
            val sink = RecordingEntrySink()
            withTemporaryArchive("archive-v1-stream-${fixture.label}-", fixture.archive) { file ->
                RandomAccessFile(file, "r").use { heldReader ->
                    val validation = ZipArchiveV1Validator.validateImmutableTo(
                        file = heldReader,
                        sink = sink,
                        limits = TEST_LIMITS,
                    )

                    assertEquals(
                        listOf(fixture.path),
                        validation.entries.map(ZipArchiveV1Entry::path),
                    )
                    assertArrayEquals(fixture.payload, sink.completedEntries.getValue(fixture.path))
                    assertEquals(
                        listOf("begin:${fixture.path}", "end:${fixture.path}"),
                        sink.events,
                    )
                    assertTrue(
                        "${fixture.label}: expected more than one bounded payload callback.",
                        sink.writeCalls > 1,
                    )
                    assertTrue(
                        "${fixture.label}: sink callback exceeded $STREAMING_CHUNK_BYTES bytes.",
                        sink.maximumWriteBytes in 1..STREAMING_CHUNK_BYTES,
                    )
                    assertEquals(0, sink.abortCalls)
                    assertTrue(
                        "${fixture.label}: streaming validation closed the caller's reader.",
                        heldReader.fd.valid(),
                    )
                }
            }
        }
    }

    @Test
    fun immutableStreamingSinkFailureAbortsAndDiscardsPartialPayload() {
        val payload = patternedBytes(3 * STREAMING_CHUNK_BYTES + 17)
        val archive = storedArchive(
            RawEntry(
                rawName = "sources/failing.ts".toByteArray(Charsets.US_ASCII),
                data = payload,
            ),
        )
        val sink = RecordingEntrySink(failOnWriteCall = 2)

        withTemporaryArchive("archive-v1-stream-sink-failure-", archive) { file ->
            RandomAccessFile(file, "r").use { heldReader ->
                try {
                    ZipArchiveV1Validator.validateImmutableTo(
                        file = heldReader,
                        sink = sink,
                        limits = TEST_LIMITS,
                    )
                    fail("A failed streaming sink unexpectedly completed validation.")
                } catch (failure: ZipArchiveV1EntrySinkException) {
                    assertSame(sink.syntheticWriteFailure, failure.cause)
                    assertTrue(failure.message.orEmpty().contains("write"))
                    assertTrue(failure.message.orEmpty().contains("sources/failing.ts"))
                }
                assertTrue(heldReader.fd.valid())
            }
        }

        assertEquals(1, sink.abortCalls)
        assertEquals(0, sink.endCalls)
        assertTrue(sink.partialBytesObservedByAbort > 0)
        assertEquals(0, sink.retainedByteCount)
        assertTrue(sink.completedEntries.isEmpty())
        assertTrue(sink.abortedFailure is ZipArchiveV1EntrySinkException)
    }

    @Test
    fun immutableStreamingArchiveIntegrityFailureAbortsPreviouslyDeliveredBytes() {
        val path = "sources/crc-failure.ts"
        val payload = patternedBytes(2 * STREAMING_CHUNK_BYTES + 29)
        val archive = storedArchive(
            RawEntry(
                rawName = path.toByteArray(Charsets.US_ASCII),
                data = payload,
            ),
        ).also { bytes ->
            val payloadOffset = 30 + path.toByteArray(Charsets.US_ASCII).size
            bytes[payloadOffset + payload.size / 2] =
                (bytes[payloadOffset + payload.size / 2].toInt() xor 0x5a).toByte()
        }
        val sink = RecordingEntrySink()

        withTemporaryArchive("archive-v1-stream-integrity-failure-", archive) { file ->
            RandomAccessFile(file, "r").use { heldReader ->
                assertRejected(ZipArchiveV1FailureKind.INTEGRITY, "streamed CRC mismatch") {
                    ZipArchiveV1Validator.validateImmutableTo(
                        file = heldReader,
                        sink = sink,
                        limits = TEST_LIMITS,
                    )
                }
                assertTrue(heldReader.fd.valid())
            }
        }

        assertEquals(1, sink.abortCalls)
        assertEquals(0, sink.endCalls)
        assertEquals(payload.size, sink.partialBytesObservedByAbort)
        assertEquals(0, sink.retainedByteCount)
        assertTrue(sink.completedEntries.isEmpty())
        assertTrue(sink.abortedFailure is ZipArchiveV1Exception)
    }

    @Test
    fun immutableStagingReaderEnforcesActualStreamingQuotaAndCrc() {
        val forgedSize = goldenSections("valid-golden.hex")
            .getValue("deflated-repetition-at-exact-100-to-1-bound")
            .copyOf()
            .also { archive ->
                val local = archive.indexOfSignature(LOCAL_FILE_HEADER_SIGNATURE)
                val central = archive.indexOfSignature(CENTRAL_DIRECTORY_SIGNATURE)
                archive.putU32(local + 22, 10L)
                archive.putU32(central + 24, 10L)
            }
        withTemporaryArchive("archive-v1-stream-quota-", forgedSize) { file ->
            assertRejected(ZipArchiveV1FailureKind.QUOTA, "immutable staging actual quota") {
                ZipArchiveV1Validator.validateImmutableAndRead(
                    file,
                    TEST_LIMITS.copy(maximumCompressionRatio = 99),
                )
            }
        }

        val crcMismatch = goldenSections("invalid-golden.hex").getValue("crc-mismatch")
        withTemporaryArchive("archive-v1-stream-crc-", crcMismatch) { file ->
            assertRejected(ZipArchiveV1FailureKind.INTEGRITY, "immutable staging CRC") {
                ZipArchiveV1Validator.validateImmutableAndRead(file, TEST_LIMITS)
            }
        }
    }

    @Test
    fun utf8PathsRequireTheirFlagWhileControlsMalformedTextAndCaseCollisionsStayClosed() {
        val printableAscii = storedArchive(
            RawEntry("AZ az-09_~.ts".toByteArray(Charsets.US_ASCII), flags = FLAG_UTF8),
        )
        assertEquals(
            "AZ az-09_~.ts",
            ZipArchiveV1Validator.validate(printableAscii, TEST_LIMITS).entries.single().path,
        )

        val nonAsciiWithUtf8Flag = storedArchive(
            RawEntry(
                "中文目录/入口.ts".toByteArray(Charsets.UTF_8),
                flags = FLAG_UTF8,
            ),
        )
        assertEquals(
            "中文目录/入口.ts",
            ZipArchiveV1Validator.validate(nonAsciiWithUtf8Flag, TEST_LIMITS)
                .entries.single().path,
        )

        val nonAsciiWithoutUtf8Flag = storedArchive(
            RawEntry("é.ts".toByteArray(Charsets.UTF_8)),
        )
        assertRejected(ZipArchiveV1FailureKind.PATH, "non-ASCII without UTF-8 flag") {
            ZipArchiveV1Validator.validate(nonAsciiWithoutUtf8Flag, TEST_LIMITS)
        }

        val malformedUtf8 = storedArchive(
            RawEntry(
                byteArrayOf(
                    0xc3.toByte(),
                    0x28,
                    '.'.code.toByte(),
                    't'.code.toByte(),
                    's'.code.toByte(),
                ),
                flags = FLAG_UTF8,
            ),
        )
        assertRejected(ZipArchiveV1FailureKind.PATH, "malformed UTF-8") {
            ZipArchiveV1Validator.validate(malformedUtf8, TEST_LIMITS)
        }

        val unicodeControl = storedArchive(
            RawEntry(
                "sources/\u0085main.ts".toByteArray(Charsets.UTF_8),
                flags = FLAG_UTF8,
            ),
        )
        assertRejected(ZipArchiveV1FailureKind.PATH, "Unicode control character") {
            ZipArchiveV1Validator.validate(unicodeControl, TEST_LIMITS)
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
        assertEquals(140L * MEBIBYTE, input.maximumContainerBytes)
        assertEquals(12_289, input.maximumFiles)
        assertEquals(132L * MEBIBYTE, input.maximumTotalUncompressedBytes)
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
            R2_INPUT_ARCHIVE_MAXIMUM_CONTAINER_BYTES,
            input.maximumContainerBytes,
        )
        assertEquals(R2_INPUT_ARCHIVE_MAXIMUM_FILES, input.maximumFiles)
        assertEquals(
            R2_INPUT_ARCHIVE_MAXIMUM_EXPANDED_BYTES,
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
    fun provisionalMaterializedProjectUsesASeparateHeapBudget() {
        val provisional = ZipArchiveV1Limits.PROVISIONAL_MATERIALIZED_PROJECT

        assertEquals(140L * MEBIBYTE, provisional.maximumContainerBytes)
        assertEquals(257, provisional.maximumFiles)
        assertEquals(1L * MEBIBYTE, provisional.maximumTotalUncompressedBytes)
        assertEquals(512L * 1024L, provisional.maximumSingleFileBytes)
        assertEquals(
            132L * MEBIBYTE,
            ZipArchiveV1Limits.INPUT.maximumTotalUncompressedBytes,
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

    private fun <T> withTemporaryArchive(
        prefix: String,
        bytes: ByteArray,
        block: (File) -> T,
    ): T {
        val file = File.createTempFile(prefix, ".zip")
        return try {
            file.writeBytes(bytes)
            block(file)
        } finally {
            file.delete()
        }
    }

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

    private fun rejectedKind(block: () -> Unit): ZipArchiveV1FailureKind {
        try {
            block()
            fail("Archive unexpectedly passed ZIP v1 validation.")
        } catch (failure: ZipArchiveV1Exception) {
            return failure.kind
        }
        error("unreachable")
    }

    private fun moderatelyCompressedArchive(
        path: String,
        uncompressedByteCount: Int,
    ): ByteArray {
        require(uncompressedByteCount > 0)
        val data = moderatelyCompressedPayload(uncompressedByteCount)
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        val compressed = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        try {
            deflater.setInput(data)
            deflater.finish()
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                check(count > 0) {
                    "Test DEFLATE encoder made no progress."
                }
                compressed.write(buffer, 0, count)
            }
        } finally {
            deflater.end()
        }

        val rawName = path.toByteArray(Charsets.US_ASCII)
        val compressedBytes = compressed.toByteArray()
        val crc32 = CRC32().apply { update(data) }.value
        return ByteArrayOutputStream().apply {
            putU32(LOCAL_FILE_HEADER_SIGNATURE)
            putU16(VERSION_DEFLATED)
            putU16(0)
            putU16(COMPRESSION_DEFLATED)
            putU16(0)
            putU16(0)
            putU32(crc32)
            putU32(compressedBytes.size.toLong())
            putU32(data.size.toLong())
            putU16(rawName.size)
            putU16(0)
            write(rawName)
            write(compressedBytes)

            val centralOffset = size()
            putU32(CENTRAL_DIRECTORY_SIGNATURE)
            putU16(VERSION_DEFLATED)
            putU16(VERSION_DEFLATED)
            putU16(0)
            putU16(COMPRESSION_DEFLATED)
            putU16(0)
            putU16(0)
            putU32(crc32)
            putU32(compressedBytes.size.toLong())
            putU32(data.size.toLong())
            putU16(rawName.size)
            putU16(0)
            putU16(0)
            putU16(0)
            putU16(0)
            putU32(0L)
            putU32(0L)
            write(rawName)

            val centralSize = size() - centralOffset
            putU32(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
            putU16(0)
            putU16(0)
            putU16(1)
            putU16(1)
            putU32(centralSize.toLong())
            putU32(centralOffset.toLong())
            putU16(0)
        }.toByteArray()
    }

    private fun moderatelyCompressedPayload(uncompressedByteCount: Int): ByteArray {
        require(uncompressedByteCount > 0)
        val data = ByteArray(uncompressedByteCount)
        val blockBytes = 16 * 1024
        var state = 0x6d2b79f5
        var offset = 0
        while (offset < data.size) {
            val uniqueBytes = minOf(blockBytes, data.size - offset)
            repeat(uniqueBytes) { index ->
                state = state xor (state shl 13)
                state = state xor (state ushr 17)
                state = state xor (state shl 5)
                data[offset + index] = state.toByte()
            }
            val repeatedOffset = offset + uniqueBytes
            val repeatedBytes = minOf(uniqueBytes, data.size - repeatedOffset)
            if (repeatedBytes > 0) {
                data.copyInto(
                    destination = data,
                    destinationOffset = repeatedOffset,
                    startIndex = offset,
                    endIndex = offset + repeatedBytes,
                )
            }
            offset = repeatedOffset + repeatedBytes
        }
        return data
    }

    private fun patternedBytes(size: Int): ByteArray =
        ByteArray(size) { index ->
            ((index * 31) xor (index ushr 5)).toByte()
        }

    private class RecordingEntrySink(
        private val failOnWriteCall: Int? = null,
    ) : ZipArchiveV1EntrySink {

        val completedEntries = linkedMapOf<String, ByteArray>()
        val events = mutableListOf<String>()
        val syntheticWriteFailure = IllegalStateException("synthetic sink write failure")
        var abortCalls = 0
        var endCalls = 0
        var maximumWriteBytes = 0
        var partialBytesObservedByAbort = 0
        var writeCalls = 0
        var abortedFailure: Throwable? = null

        private var currentEntry: ZipArchiveV1Entry? = null
        private val currentBytes = ByteArrayOutputStream()

        val retainedByteCount: Int
            get() = currentBytes.size() + completedEntries.values.sumOf { bytes -> bytes.size }

        override fun begin(entry: ZipArchiveV1Entry) {
            check(currentEntry == null)
            check(currentBytes.size() == 0)
            currentEntry = entry
            events += "begin:${entry.path}"
        }

        override fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
            checkNotNull(currentEntry)
            check(offset >= 0 && length > 0 && offset <= bytes.size - length)
            writeCalls++
            maximumWriteBytes = maxOf(maximumWriteBytes, length)
            currentBytes.write(bytes, offset, length)
            if (writeCalls == failOnWriteCall) {
                throw syntheticWriteFailure
            }
        }

        override fun end(entry: ZipArchiveV1Entry) {
            check(entry == currentEntry)
            endCalls++
            events += "end:${entry.path}"
            check(completedEntries.put(entry.path, currentBytes.toByteArray()) == null)
            currentBytes.reset()
            currentEntry = null
        }

        override fun abort(failure: Throwable) {
            abortCalls++
            abortedFailure = failure
            partialBytesObservedByAbort = retainedByteCount
            currentBytes.reset()
            completedEntries.clear()
            currentEntry = null
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

    private data class StreamingFixture(
        val label: String,
        val path: String,
        val payload: ByteArray,
        val archive: ByteArray,
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
        const val COMPRESSION_DEFLATED = 8
        const val FLAG_UTF8 = 1 shl 11
        const val CREATOR_UNIX = 3
        const val DOS_VOLUME_LABEL_ATTRIBUTE = 0x08L
        const val DOS_DIRECTORY_ATTRIBUTE = 0x10L
        const val DOS_ALLOWED_REGULAR_ATTRIBUTES = 0x27L
        const val DOS_RESERVED_DEVICE_ATTRIBUTE = 0x40L
        const val DOS_RESERVED_HIGH_BIT = 0x80L
        const val STREAMING_CHUNK_BYTES = 16 * 1024
        const val UNIX_REGULAR_0644 = 0x81a4_0000L
        const val UNIX_CHARACTER_DEVICE_0600 = 0x21_80_0000L
        const val MEBIBYTE = 1024L * 1024L
    }
}
