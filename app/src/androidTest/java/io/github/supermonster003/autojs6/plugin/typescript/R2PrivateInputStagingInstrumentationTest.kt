package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R2PrivateInputStagingInstrumentationTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun parcelFileDescriptorCaptureStartsAtZeroWithoutMovingCallerSharedOffset() {
        val directory = newPrivateDirectory("offset-zero")
        val inputFile = File.createTempFile("r2-staging-caller-", ".zip", context.cacheDir)
        val bytes = "complete-caller-archive".toByteArray()
        val descriptorsBefore = stagingDescriptorTargets()
        assertTrue(
            "Expected no staging descriptors before capture: $descriptorsBefore",
            descriptorsBefore.isEmpty(),
        )
        inputFile.writeBytes(bytes)
        val callerDescriptor = ParcelFileDescriptor.open(
            inputFile,
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
        val serviceDescriptor = ParcelFileDescriptor.dup(callerDescriptor.fileDescriptor)
        val rawServiceDescriptor = serviceDescriptor.fileDescriptor
        try {
            Os.lseek(callerDescriptor.fileDescriptor, CALLER_OFFSET, OsConstants.SEEK_SET)
            R2PrivateInputStaging.stage(
                inputArchive = serviceDescriptor,
                privateDirectory = directory,
                maximumBytes = bytes.size.toLong(),
            ).use { staged ->
                assertFalse(rawServiceDescriptor.valid())
                assertTrue(callerDescriptor.fileDescriptor.valid())
                assertEquals(
                    "Explicit-offset capture must not move the caller's shared file offset.",
                    CALLER_OFFSET,
                    Os.lseek(
                        callerDescriptor.fileDescriptor,
                        0L,
                        OsConstants.SEEK_CUR,
                    ),
                )
                assertFalse(
                    "Android must unlink the staging path while retaining its read-only inode.",
                    staged.namedPathRetained,
                )
                assertDirectoryEmpty(directory)
                staged.withImmutableReader { reader ->
                    val mode = Os.fstat(reader.fd).st_mode
                    assertEquals(
                        "The held staging inode must have no owner, group, or other write bits.",
                        0,
                        mode and WRITE_PERMISSION_BITS,
                    )
                    assertArrayEquals(bytes, readAllBytes(reader))

                    val activeDescriptors = stagingDescriptorTargets()
                    assertEquals(
                        "Exactly one descriptor should retain the unlinked staging inode.",
                        1,
                        activeDescriptors.size,
                    )
                    assertTrue(
                        "The active staging descriptor must point to a deleted directory entry: " +
                            activeDescriptors.single(),
                        activeDescriptors.single().endsWith(DELETED_TARGET_SUFFIX),
                    )
                }
            }
            assertDirectoryEmpty(directory)
            assertNoStagingDescriptors("after staging close")
        } finally {
            runCatching { serviceDescriptor.close() }
            runCatching { callerDescriptor.close() }
            assertTrue(inputFile.delete() || !inputFile.exists())
            assertTrue(directory.delete() || !directory.exists())
        }
    }

    @Test
    fun captureRemainsStableWhileCallerChurnsSharedOffset() {
        val directory = newPrivateDirectory("offset-churn")
        val inputFile = File.createTempFile("r2-staging-churn-", ".zip", context.cacheDir)
        val bytes = ByteArray(CONCURRENT_CAPTURE_BYTES) { index ->
            ((index * 31) xor (index ushr 7)).toByte()
        }
        inputFile.writeBytes(bytes)
        val callerDescriptor = ParcelFileDescriptor.open(
            inputFile,
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
        val serviceDescriptor = ParcelFileDescriptor.dup(callerDescriptor.fileDescriptor)
        val rawServiceDescriptor = serviceDescriptor.fileDescriptor
        val keepChurning = AtomicBoolean(true)
        val churnStarted = CountDownLatch(1)
        val completedChurns = AtomicInteger()
        val churnFailure = AtomicReference<Throwable?>()
        val churnThread = Thread(
            {
                try {
                    while (keepChurning.get()) {
                        Os.lseek(
                            callerDescriptor.fileDescriptor,
                            1L,
                            OsConstants.SEEK_SET,
                        )
                        Os.lseek(
                            callerDescriptor.fileDescriptor,
                            (bytes.size - 1).toLong(),
                            OsConstants.SEEK_SET,
                        )
                        completedChurns.incrementAndGet()
                        churnStarted.countDown()
                    }
                } catch (failure: Throwable) {
                    churnFailure.set(failure)
                    churnStarted.countDown()
                }
            },
            "r2-shared-offset-churn",
        )
        var churnsDuringCapture = 0
        try {
            assertNoStagingDescriptors("before concurrent-offset capture")
            churnThread.start()
            assertTrue(
                "Caller offset churn did not start.",
                churnStarted.await(OFFSET_CHURN_START_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            val churnsBeforeCapture = completedChurns.get()
            R2PrivateInputStaging.stage(
                inputArchive = serviceDescriptor,
                privateDirectory = directory,
                maximumBytes = bytes.size.toLong(),
            ).use { staged ->
                assertFalse(rawServiceDescriptor.valid())
                staged.withImmutableReader { reader ->
                    assertArrayEquals(bytes, readAllBytes(reader))
                }
            }
            churnsDuringCapture = completedChurns.get() - churnsBeforeCapture
            assertDirectoryEmpty(directory)
            assertNoStagingDescriptors("after concurrent-offset capture")
        } finally {
            keepChurning.set(false)
            if (churnThread.isAlive) {
                churnThread.join(OFFSET_CHURN_JOIN_TIMEOUT_MILLIS)
            }
            runCatching { serviceDescriptor.close() }
            runCatching { callerDescriptor.close() }
            assertTrue(inputFile.delete() || !inputFile.exists())
            assertTrue(directory.delete() || !directory.exists())
        }
        assertFalse("Caller offset churn thread did not stop.", churnThread.isAlive)
        churnFailure.get()?.let { failure ->
            throw AssertionError("Caller offset churn failed.", failure)
        }
        assertTrue(
            "Expected caller lseek operations to complete while capture was in progress.",
            churnsDuringCapture > 0,
        )
    }

    @Test
    fun failedSeekClosesPipeDescriptorAndLeavesNoStaging() {
        val directory = newPrivateDirectory("failed-seek")
        val pipe = ParcelFileDescriptor.createPipe()
        val rawReadDescriptor = pipe[0].fileDescriptor
        val descriptorsBefore = stagingDescriptorTargets()
        assertTrue(
            "Expected no staging descriptors before failed seek: $descriptorsBefore",
            descriptorsBefore.isEmpty(),
        )
        try {
            pipe[1].close()
            assertThrows(R2PrivateInputStagingIntegrityException::class.java) {
                R2PrivateInputStaging.stage(
                    inputArchive = pipe[0],
                    privateDirectory = directory,
                    maximumBytes = 1_024L,
                )
            }
            assertFalse(rawReadDescriptor.valid())
            assertDirectoryEmpty(directory)
            assertNoStagingDescriptors("after failed pipe seek")
        } finally {
            pipe.forEach { descriptor -> runCatching { descriptor.close() } }
            assertTrue(directory.delete() || !directory.exists())
        }
    }

    @Test
    fun quotaFailureConsumesRegularDescriptorAndCleansPartialStaging() {
        val directory = newPrivateDirectory("quota-failure")
        val inputFile = File.createTempFile("r2-staging-quota-", ".zip", context.cacheDir)
        val bytes = ByteArray(STAGING_QUOTA_BYTES + 1) { index ->
            (index and 0xff).toByte()
        }
        val descriptorsBefore = stagingDescriptorTargets()
        assertTrue(
            "Expected no staging descriptors before quota failure: $descriptorsBefore",
            descriptorsBefore.isEmpty(),
        )
        inputFile.writeBytes(bytes)
        val descriptor = ParcelFileDescriptor.open(
            inputFile,
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
        val rawDescriptor = descriptor.fileDescriptor
        try {
            assertThrows(R2PrivateInputStagingQuotaException::class.java) {
                R2PrivateInputStaging.stage(
                    inputArchive = descriptor,
                    privateDirectory = directory,
                    maximumBytes = STAGING_QUOTA_BYTES.toLong(),
                )
            }
            assertFalse(rawDescriptor.valid())
            assertDirectoryEmpty(directory)
            assertNoStagingDescriptors("after regular-descriptor quota failure")
        } finally {
            runCatching { descriptor.close() }
            assertTrue(inputFile.delete() || !inputFile.exists())
            assertTrue(directory.delete() || !directory.exists())
        }
    }

    private fun newPrivateDirectory(label: String): File {
        val root = context.getDir(PRIVATE_DIRECTORY_NAME, Context.MODE_PRIVATE)
        return File(root, "$label-${UUID.randomUUID()}").also { directory ->
            assertTrue("Could not create private staging test directory.", directory.mkdir())
        }
    }

    private fun assertDirectoryEmpty(directory: File) {
        assertTrue(
            "Expected no private staging residue in $directory.",
            directory.listFiles().orEmpty().isEmpty(),
        )
    }

    private fun readAllBytes(reader: RandomAccessFile): ByteArray {
        val length = reader.length()
        assertTrue("Staging input is too large for this test helper.", length <= Int.MAX_VALUE)
        return ByteArray(length.toInt()).also { bytes ->
            reader.seek(0L)
            reader.readFully(bytes)
        }
    }

    private fun stagingDescriptorTargets(): List<String> =
        File(PROCESS_FILE_DESCRIPTOR_DIRECTORY)
            .listFiles()
            .orEmpty()
            .mapNotNull { descriptor ->
                runCatching { Os.readlink(descriptor.absolutePath) }.getOrNull()
            }
            .filter { target ->
                target
                    .substringAfterLast('/')
                    .removeSuffix(DELETED_TARGET_SUFFIX)
                    .let { name ->
                        name.startsWith(STAGING_FILE_PREFIX) &&
                            name.endsWith(STAGING_FILE_SUFFIX)
                    }
            }
            .sorted()

    private fun assertNoStagingDescriptors(label: String) {
        val targets = stagingDescriptorTargets()
        assertTrue(
            "Expected no open staging descriptors $label: $targets",
            targets.isEmpty(),
        )
        assertFalse(
            "Expected no deleted staging inode descriptor $label: $targets",
            targets.any { it.endsWith(DELETED_TARGET_SUFFIX) },
        )
    }

    private companion object {
        const val PRIVATE_DIRECTORY_NAME = "r2-private-input-staging-tests"
        const val PROCESS_FILE_DESCRIPTOR_DIRECTORY = "/proc/self/fd"
        const val STAGING_FILE_PREFIX = "r2-private-input-"
        const val STAGING_FILE_SUFFIX = ".staging"
        const val DELETED_TARGET_SUFFIX = " (deleted)"
        const val STAGING_QUOTA_BYTES = 64 * 1024
        const val CONCURRENT_CAPTURE_BYTES = 8 * 1024 * 1024
        const val OFFSET_CHURN_START_TIMEOUT_SECONDS = 5L
        const val OFFSET_CHURN_JOIN_TIMEOUT_MILLIS = 5_000L
        const val CALLER_OFFSET = 9L
        val WRITE_PERMISSION_BITS =
            OsConstants.S_IWUSR or OsConstants.S_IWGRP or OsConstants.S_IWOTH
    }
}
