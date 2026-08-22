package io.github.supermonster003.autojs6.plugin.typescript

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class R2PrivateTransientOrphanScavengerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun deletesOnlyRecognizedDirectFilesAndPreservesUnknownChildren() {
        val inputDirectory = temporaryFolder.newFolder("strict-input")
        val outputDirectory = temporaryFolder.newFolder("strict-output")
        val privateInput = inputDirectory.createFile("r2-private-input-101.staging")
        val projectSpool = inputDirectory.createFile("r2-project-vfs-202.spool")
        val privateOutput = outputDirectory.createFile(
            "r2-private-output-303.zip.staging",
        )

        val sentinel = inputDirectory.createFile("public-r2-sentinel.keep")
        val prefixOnly = inputDirectory.createFile("r2-private-input-404")
        val suffixOnly = inputDirectory.createFile("unrelated-404.staging")
        val trailingName = inputDirectory.createFile(
            "r2-private-input-404.staging.keep",
        )
        val wrongDirectoryOutput = inputDirectory.createFile(
            "r2-private-output-505.zip.staging",
        )
        val wrongDirectoryInput = outputDirectory.createFile(
            "r2-private-input-606.staging",
        )
        val recognizedDirectory = File(
            inputDirectory,
            "r2-project-vfs-directory.spool",
        )
        assertTrue(recognizedDirectory.mkdir())
        val nestedRecognizedFile = recognizedDirectory.createFile(
            "r2-private-input-nested.staging",
        )

        val report = R2PrivateTransientOrphanScavenger.scavenge(
            inputStagingDirectory = inputDirectory,
            outputStagingDirectory = outputDirectory,
        )

        assertEquals(1, report.privateInputFilesDeleted)
        assertEquals(1, report.projectSpoolFilesDeleted)
        assertEquals(1, report.privateOutputFilesDeleted)
        assertEquals(3, report.totalFilesDeleted)
        assertFalse(privateInput.exists())
        assertFalse(projectSpool.exists())
        assertFalse(privateOutput.exists())
        listOf(
            sentinel,
            prefixOnly,
            suffixOnly,
            trailingName,
            wrongDirectoryOutput,
            wrongDirectoryInput,
            recognizedDirectory,
            nestedRecognizedFile,
        ).forEach { preserved ->
            assertTrue("${preserved.name} should have been preserved", preserved.exists())
        }
    }

    @Test
    fun restoresOwnerWriteBeforeDeletingReadOnlyOrphans() {
        val inputDirectory = temporaryFolder.newFolder("read-only-input")
        val outputDirectory = temporaryFolder.newFolder("read-only-output")
        val orphans = setOf(
            inputDirectory.createFile("r2-private-input-read-only.staging"),
            inputDirectory.createFile("r2-project-vfs-read-only.spool"),
            outputDirectory.createFile("r2-private-output-read-only.zip.staging"),
        )
        orphans.forEach { orphan ->
            assertTrue("Failed to make ${orphan.name} read-only", orphan.setReadOnly())
        }
        val fileOperations = OwnerWriteRecordingFileOperations()

        val report = R2PrivateTransientOrphanScavenger.scavenge(
            inputStagingDirectory = inputDirectory,
            outputStagingDirectory = outputDirectory,
            fileOperations = fileOperations,
        )

        assertEquals(3, report.totalFilesDeleted)
        assertEquals(orphans, fileOperations.ownerWriteRestorations)
        orphans.forEach { orphan -> assertFalse(orphan.exists()) }
    }

    @Test
    fun cleanupIsIdempotent() {
        val inputDirectory = temporaryFolder.newFolder("idempotent-input")
        val outputDirectory = temporaryFolder.newFolder("idempotent-output")
        inputDirectory.createFile("r2-private-input-once.staging")
        inputDirectory.createFile("r2-project-vfs-once.spool")
        outputDirectory.createFile("r2-private-output-once.zip.staging")

        val first = R2PrivateTransientOrphanScavenger.scavenge(
            inputStagingDirectory = inputDirectory,
            outputStagingDirectory = outputDirectory,
        )
        val second = R2PrivateTransientOrphanScavenger.scavenge(
            inputStagingDirectory = inputDirectory,
            outputStagingDirectory = outputDirectory,
        )

        assertEquals(3, first.totalFilesDeleted)
        assertEquals(0, second.totalFilesDeleted)
    }

    @Test
    fun initialEnumerationFailureIsCleanupUnproven() {
        val inputDirectory = temporaryFolder.newFolder("list-failure-input")
        val outputDirectory = temporaryFolder.newFolder("list-failure-output")
        val fileOperations = object : R2PrivateTransientOrphanFileOperations {
            override fun listChildren(directory: File): Array<File>? =
                if (directory == inputDirectory) null else directory.listFiles()
        }

        val failure = assertThrows(
            R2PrivateTransientCleanupUnprovenException::class.java,
        ) {
            R2PrivateTransientOrphanScavenger.scavenge(
                inputStagingDirectory = inputDirectory,
                outputStagingDirectory = outputDirectory,
                fileOperations = fileOperations,
            )
        }

        assertTrue(failure.message.orEmpty().contains("initial enumeration"))
    }

    @Test
    fun rejectedDeletionIsCleanupUnproven() {
        val inputDirectory = temporaryFolder.newFolder("delete-failure-input")
        val outputDirectory = temporaryFolder.newFolder("delete-failure-output")
        val orphan = inputDirectory.createFile("r2-private-input-stuck.staging")
        val fileOperations = object : R2PrivateTransientOrphanFileOperations {
            override fun delete(file: File): Boolean =
                if (file == orphan) false else file.delete()
        }

        val failure = assertThrows(
            R2PrivateTransientCleanupUnprovenException::class.java,
        ) {
            R2PrivateTransientOrphanScavenger.scavenge(
                inputStagingDirectory = inputDirectory,
                outputStagingDirectory = outputDirectory,
                fileOperations = fileOperations,
            )
        }

        assertTrue(failure.message.orEmpty().contains("deletion was rejected"))
        assertTrue(orphan.exists())
    }

    @Test
    fun recheckEnumerationFailureIsCleanupUnproven() {
        val inputDirectory = temporaryFolder.newFolder("recheck-failure-input")
        val outputDirectory = temporaryFolder.newFolder("recheck-failure-output")
        val orphan = inputDirectory.createFile("r2-project-vfs-recheck.spool")
        val injectedFailure = IOException("injected recheck failure")
        var inputEnumerationCount = 0
        val fileOperations = object : R2PrivateTransientOrphanFileOperations {
            override fun listChildren(directory: File): Array<File>? {
                if (directory == inputDirectory) {
                    inputEnumerationCount += 1
                    if (inputEnumerationCount == 2) {
                        throw injectedFailure
                    }
                }
                return directory.listFiles()
            }
        }

        val failure = assertThrows(
            R2PrivateTransientCleanupUnprovenException::class.java,
        ) {
            R2PrivateTransientOrphanScavenger.scavenge(
                inputStagingDirectory = inputDirectory,
                outputStagingDirectory = outputDirectory,
                fileOperations = fileOperations,
            )
        }

        assertTrue(failure.message.orEmpty().contains("post-cleanup verification"))
        assertSame(injectedFailure, failure.cause)
        assertFalse(orphan.exists())
    }

    @Test
    fun recheckRejectsADeletionThatClaimsSuccessButLeavesResidue() {
        val inputDirectory = temporaryFolder.newFolder("residue-input")
        val outputDirectory = temporaryFolder.newFolder("residue-output")
        val orphan = outputDirectory.createFile(
            "r2-private-output-residue.zip.staging",
        )
        val fileOperations = object : R2PrivateTransientOrphanFileOperations {
            override fun delete(file: File): Boolean =
                if (file == orphan) true else file.delete()
        }

        val failure = assertThrows(
            R2PrivateTransientCleanupUnprovenException::class.java,
        ) {
            R2PrivateTransientOrphanScavenger.scavenge(
                inputStagingDirectory = inputDirectory,
                outputStagingDirectory = outputDirectory,
                fileOperations = fileOperations,
            )
        }

        assertTrue(failure.message.orEmpty().contains("left 1 recognized file"))
        assertTrue(orphan.exists())
    }

    private fun File.createFile(name: String): File = File(this, name).apply {
        writeText("R2 private transient orphan")
    }

    private class OwnerWriteRecordingFileOperations :
        R2PrivateTransientOrphanFileOperations {

        val ownerWriteRestorations = LinkedHashSet<File>()

        override fun restoreOwnerWritable(file: File): Boolean {
            ownerWriteRestorations += file
            return file.setWritable(true, true)
        }
    }
}
