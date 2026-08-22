package io.github.supermonster003.autojs6.plugin.typescript

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.attribute.DosFileAttributes
import java.nio.file.attribute.PosixFilePermission
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class R2PrivateInputStagingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun capturesBytesFingerprintAndLeavesPureInputOwnershipWithCaller() {
        val directory = temporaryFolder.newFolder("capture")
        val bytes = buildString {
            repeat(20_000) { index ->
                append("source-$index\n")
            }
        }.toByteArray()
        val source = CloseRecordingInputStream(bytes)

        val staged = R2PrivateInputStaging.stage(
            input = source,
            privateDirectory = directory,
            maximumBytes = bytes.size.toLong(),
        )
        try {
            assertFalse(source.closed)
            assertActiveNamedPathState(staged, directory)
            assertEquals(bytes.size.toLong(), staged.fingerprint.byteCount)
            assertEquals(
                "31d943e234c5742ce6b6d30cff780c414a27e4dbd58a1f7153c3d910828e4151",
                staged.fingerprint.sha256,
            )
            staged.withImmutableReader { reader ->
                assertArrayEquals(bytes, reader.readAllBytes())
            }
            assertEquals(staged.fingerprint, staged.verifyUnchanged())
        } finally {
            staged.close()
        }

        assertDirectoryEmpty(directory)
        staged.close()
        assertFalse(source.closed)
    }

    @Test
    fun acceptsExactInclusiveAndEmptyLimitsButRejectsTheNextByte() {
        val exactDirectory = temporaryFolder.newFolder("exact")
        val exactBytes = "exact-boundary".toByteArray()
        R2PrivateInputStaging.stage(
            input = ByteArrayInputStream(exactBytes),
            privateDirectory = exactDirectory,
            maximumBytes = exactBytes.size.toLong(),
        ).use { staged ->
            assertEquals(exactBytes.size.toLong(), staged.fingerprint.byteCount)
        }
        assertDirectoryEmpty(exactDirectory)

        val emptyDirectory = temporaryFolder.newFolder("empty")
        R2PrivateInputStaging.stage(
            input = ByteArrayInputStream(ByteArray(0)),
            privateDirectory = emptyDirectory,
            maximumBytes = 0L,
        ).use { staged ->
            assertEquals(0L, staged.fingerprint.byteCount)
            assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                staged.fingerprint.sha256,
            )
        }
        assertDirectoryEmpty(emptyDirectory)

        val oversizedDirectory = temporaryFolder.newFolder("oversized")
        assertThrows(R2PrivateInputStagingQuotaException::class.java) {
            R2PrivateInputStaging.stage(
                input = ByteArrayInputStream(exactBytes),
                privateDirectory = oversizedDirectory,
                maximumBytes = exactBytes.size - 1L,
            )
        }
        assertDirectoryEmpty(oversizedDirectory)
    }

    @Test
    fun sourceFailureDeletesPartialStagingAndDoesNotClosePureInput() {
        val directory = temporaryFolder.newFolder("source-failure")
        val source = ThrowingInputStream("partial".toByteArray())

        val failure = assertThrows(IOException::class.java) {
            R2PrivateInputStaging.stage(
                input = source,
                privateDirectory = directory,
                maximumBytes = 1_024L,
            )
        }

        assertEquals("synthetic source failure", failure.message)
        assertFalse(source.closed)
        assertDirectoryEmpty(directory)
    }

    @Test
    fun heldReaderIsStableAndDetachedFromTheCallerPathUntilClose() {
        val directory = temporaryFolder.newFolder("stable-reader")
        val callerFile = temporaryFolder.newFile("caller-input.zip")
        val original = "original".toByteArray()
        callerFile.writeBytes(original)
        val staged = callerFile.inputStream().use { input ->
            R2PrivateInputStaging.stage(
                input = input,
                privateDirectory = directory,
                maximumBytes = original.size.toLong(),
            )
        }
        var firstReader: RandomAccessFile? = null
        try {
            assertActiveNamedPathState(staged, directory)
            staged.withImmutableReader { reader ->
                firstReader = reader
                assertArrayEquals(original, reader.readAllBytes())
            }

            callerFile.writeBytes("modified-caller-source".toByteArray())
            staged.withImmutableReader { reader ->
                assertSame(firstReader, reader)
                assertArrayEquals(original, reader.readAllBytes())
                staged.withImmutableReader { nestedReader ->
                    assertSame(
                        "The lifecycle lock is intentionally reentrant over the one held reader.",
                        reader,
                        nestedReader,
                    )
                    assertEquals(staged.fingerprint, staged.verifyUnchanged())
                }
            }
        } finally {
            staged.close()
        }
        assertDirectoryEmpty(directory)

        assertThrows(IllegalStateException::class.java) {
            staged.withImmutableReader { Unit }
        }
        assertThrows(IllegalStateException::class.java) {
            staged.verifyUnchanged()
        }
    }

    @Test
    fun zeroLengthReadsStillMakeProgressAndRemainBounded() {
        val directory = temporaryFolder.newFolder("zero-read")
        val bytes = "zero-read-fallback".toByteArray()

        R2PrivateInputStaging.stage(
            input = ZeroThenBytesInputStream(bytes),
            privateDirectory = directory,
            maximumBytes = bytes.size.toLong(),
        ).use { staged ->
            staged.withImmutableReader { reader ->
                assertArrayEquals(bytes, reader.readAllBytes())
            }
        }
        assertDirectoryEmpty(directory)
    }

    @Test
    fun invalidDirectoryAndNegativeLimitDoNotCreateStaging() {
        val missingDirectory = File(temporaryFolder.root, "not-created")
        assertThrows(IllegalArgumentException::class.java) {
            R2PrivateInputStaging.stage(
                input = ByteArrayInputStream(ByteArray(0)),
                privateDirectory = missingDirectory,
            )
        }
        assertFalse(missingDirectory.exists())

        val directory = temporaryFolder.newFolder("negative")
        assertThrows(IllegalArgumentException::class.java) {
            R2PrivateInputStaging.stage(
                input = ByteArrayInputStream(ByteArray(0)),
                privateDirectory = directory,
                maximumBytes = -1L,
            )
        }
        assertDirectoryEmpty(directory)
    }

    private fun assertDirectoryEmpty(directory: File) {
        assertTrue(
            "Expected no private staging residue in $directory.",
            directory.listFiles().orEmpty().isEmpty(),
        )
    }

    private fun assertActiveNamedPathState(
        staged: R2PrivateInputStaging,
        directory: File,
    ) {
        val files = directory.listFiles().orEmpty()
        if (staged.namedPathRetained) {
            assertEquals(
                "The fallback platform must retain exactly one staging path.",
                1,
                files.size,
            )
            assertReadOnly(files.single())
        } else {
            assertTrue(
                "An unlink-capable platform retained an active staging path.",
                files.isEmpty(),
            )
        }
    }

    private fun RandomAccessFile.readAllBytes(): ByteArray {
        val byteCount = length()
        assertTrue("Test staging input is unexpectedly large.", byteCount <= Int.MAX_VALUE)
        seek(0L)
        return ByteArray(byteCount.toInt()).also { bytes ->
            readFully(bytes)
            assertEquals(-1, read())
        }
    }

    private fun assertReadOnly(file: File) {
        val posixPermissions = runCatching {
            Files.getPosixFilePermissions(file.toPath())
        }.getOrNull()
        if (posixPermissions != null) {
            assertFalse(
                "Private staging retained a POSIX write bit: $posixPermissions",
                posixPermissions.any { permission ->
                    permission == PosixFilePermission.OWNER_WRITE ||
                        permission == PosixFilePermission.GROUP_WRITE ||
                        permission == PosixFilePermission.OTHERS_WRITE
                },
            )
            return
        }
        assertTrue(
            "Private staging did not receive the DOS read-only attribute.",
            Files.readAttributes(
                file.toPath(),
                DosFileAttributes::class.java,
            ).isReadOnly,
        )
    }

    private class CloseRecordingInputStream(
        bytes: ByteArray,
    ) : ByteArrayInputStream(bytes) {

        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class ThrowingInputStream(
        private val prefix: ByteArray,
    ) : InputStream() {

        private var delivered = false
        var closed = false
            private set

        override fun read(): Int {
            throw IOException("synthetic source failure")
        }

        override fun read(
            destination: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (delivered) throw IOException("synthetic source failure")
            delivered = true
            prefix.copyInto(
                destination = destination,
                destinationOffset = offset,
                startIndex = 0,
                endIndex = prefix.size,
            )
            return prefix.size
        }

        override fun close() {
            closed = true
        }
    }

    private class ZeroThenBytesInputStream(
        bytes: ByteArray,
    ) : InputStream() {

        private val delegate = ByteArrayInputStream(bytes)
        private var returnedZero = false

        override fun read(): Int = delegate.read()

        override fun read(
            destination: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (!returnedZero) {
                returnedZero = true
                return 0
            }
            return delegate.read(destination, offset, length)
        }
    }
}
