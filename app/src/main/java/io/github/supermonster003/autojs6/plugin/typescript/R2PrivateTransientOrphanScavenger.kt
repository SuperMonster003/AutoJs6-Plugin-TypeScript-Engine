package io.github.supermonster003.autojs6.plugin.typescript

import java.io.File
import java.io.IOException

/**
 * File operations used by [R2PrivateTransientOrphanScavenger].
 *
 * The production implementation delegates directly to [File]. Keeping the boundary injectable
 * lets JVM tests prove that startup cleanup fails closed when enumeration, permission restoration
 * or deletion cannot be verified.
 */
internal interface R2PrivateTransientOrphanFileOperations {

    fun listChildren(directory: File): Array<File>? = directory.listFiles()

    fun isRegularFile(file: File): Boolean = file.isFile

    fun restoreOwnerWritable(file: File): Boolean = file.setWritable(true, true)

    fun delete(file: File): Boolean = file.delete()
}

private object R2SystemPrivateTransientOrphanFileOperations :
    R2PrivateTransientOrphanFileOperations

internal data class R2PrivateTransientOrphanScavengeReport(
    val privateInputFilesDeleted: Int,
    val projectSpoolFilesDeleted: Int,
    val privateOutputFilesDeleted: Int,
) {

    val totalFilesDeleted: Int
        get() =
            privateInputFilesDeleted +
                projectSpoolFilesDeleted +
                privateOutputFilesDeleted
}

/**
 * Removes private R2 transient files left behind by a previous hard process exit.
 *
 * This scavenger is deliberately non-recursive. It recognizes only the exact, case-sensitive
 * prefix/suffix namespaces used by R2's three private temporary-file producers. Unknown children,
 * near-matching names and directories are never removed. A second enumeration proves that no
 * recognized regular file remains before cleanup is reported as successful.
 */
internal object R2PrivateTransientOrphanScavenger {

    const val INPUT_STAGING_DIRECTORY_NAME = "typescript-r2-input-staging"
    const val OUTPUT_STAGING_DIRECTORY_NAME = "typescript-r2-output-staging"

    /**
     * Classifies one direct child against the same exact namespaces used by startup cleanup.
     *
     * Evidence code must call this function instead of duplicating transient prefixes. Unknown
     * directories, near-matching names and nested paths remain outside the recognized surface.
     */
    fun recognizedKind(
        directoryName: String,
        childName: String,
    ): R2PrivateTransientKind? {
        val allowedKinds = when (directoryName) {
            INPUT_STAGING_DIRECTORY_NAME,
            "app_$INPUT_STAGING_DIRECTORY_NAME",
            -> INPUT_STAGING_KINDS
            OUTPUT_STAGING_DIRECTORY_NAME,
            "app_$OUTPUT_STAGING_DIRECTORY_NAME",
            -> OUTPUT_STAGING_KINDS
            else -> return null
        }
        return recognizedKind(
            childName = childName,
            allowedKinds = allowedKinds,
        )
    }

    fun scavenge(
        inputStagingDirectory: File,
        outputStagingDirectory: File,
        fileOperations: R2PrivateTransientOrphanFileOperations =
            R2SystemPrivateTransientOrphanFileOperations,
    ): R2PrivateTransientOrphanScavengeReport {
        val deletedCounts = IntArray(R2PrivateTransientKind.values().size)
        scavengeDirectory(
            directory = inputStagingDirectory,
            allowedKinds = INPUT_STAGING_KINDS,
            deletedCounts = deletedCounts,
            fileOperations = fileOperations,
        )
        scavengeDirectory(
            directory = outputStagingDirectory,
            allowedKinds = OUTPUT_STAGING_KINDS,
            deletedCounts = deletedCounts,
            fileOperations = fileOperations,
        )
        return R2PrivateTransientOrphanScavengeReport(
            privateInputFilesDeleted =
                deletedCounts[R2PrivateTransientKind.PRIVATE_INPUT.ordinal],
            projectSpoolFilesDeleted =
                deletedCounts[R2PrivateTransientKind.PROJECT_SPOOL.ordinal],
            privateOutputFilesDeleted =
                deletedCounts[R2PrivateTransientKind.PRIVATE_OUTPUT.ordinal],
        )
    }

    private fun scavengeDirectory(
        directory: File,
        allowedKinds: Set<R2PrivateTransientKind>,
        deletedCounts: IntArray,
        fileOperations: R2PrivateTransientOrphanFileOperations,
    ) {
        val candidates = listCandidates(
            directory = directory,
            allowedKinds = allowedKinds,
            operation = "initial enumeration",
            fileOperations = fileOperations,
        )
        candidates.forEach { candidate ->
            if (deleteCandidate(candidate.file, fileOperations)) {
                deletedCounts[candidate.kind.ordinal] =
                    Math.addExact(deletedCounts[candidate.kind.ordinal], 1)
            }
        }

        val remaining = listCandidates(
            directory = directory,
            allowedKinds = allowedKinds,
            operation = "post-cleanup verification",
            fileOperations = fileOperations,
        )
        if (remaining.isNotEmpty()) {
            throw R2PrivateTransientCleanupUnprovenException(
                "R2 private transient cleanup left ${remaining.size} recognized file(s) in " +
                    "${directory.absolutePath}: " +
                    remaining
                        .map { candidate -> candidate.file.name }
                        .sorted()
                        .joinToString(limit = 3, truncated = "..."),
            )
        }
    }

    private fun listCandidates(
        directory: File,
        allowedKinds: Set<R2PrivateTransientKind>,
        operation: String,
        fileOperations: R2PrivateTransientOrphanFileOperations,
    ): List<R2PrivateTransientCandidate> {
        val children = try {
            fileOperations.listChildren(directory)
        } catch (failure: Throwable) {
            throw R2PrivateTransientCleanupUnprovenException(
                "R2 private transient $operation failed for ${directory.absolutePath}.",
                failure,
            )
        } ?: throw R2PrivateTransientCleanupUnprovenException(
            "R2 private transient $operation returned no result for ${directory.absolutePath}.",
        )

        return children.mapNotNull { child ->
            val kind = recognizedKind(
                childName = child.name,
                allowedKinds = allowedKinds,
            ) ?: return@mapNotNull null
            if (isRegularFile(child, operation, fileOperations)) {
                R2PrivateTransientCandidate(
                    file = child,
                    kind = kind,
                )
            } else {
                null
            }
        }
    }

    private fun deleteCandidate(
        file: File,
        fileOperations: R2PrivateTransientOrphanFileOperations,
    ): Boolean {
        val writableRestored = try {
            fileOperations.restoreOwnerWritable(file)
        } catch (failure: Throwable) {
            throw R2PrivateTransientCleanupUnprovenException(
                "R2 private transient owner-write restoration failed for ${file.absolutePath}.",
                failure,
            )
        }
        if (!writableRestored) {
            if (isRegularFile(file, "owner-write verification", fileOperations)) {
                throw R2PrivateTransientCleanupUnprovenException(
                    "R2 private transient owner-write restoration was rejected for " +
                        "${file.absolutePath}.",
                )
            }
            return false
        }

        // Preserve a directory or other non-regular replacement that raced the initial listing.
        if (!isRegularFile(file, "pre-delete verification", fileOperations)) {
            return false
        }
        val deleted = try {
            fileOperations.delete(file)
        } catch (failure: Throwable) {
            throw R2PrivateTransientCleanupUnprovenException(
                "R2 private transient deletion failed for ${file.absolutePath}.",
                failure,
            )
        }
        if (!deleted && isRegularFile(file, "delete verification", fileOperations)) {
            throw R2PrivateTransientCleanupUnprovenException(
                "R2 private transient deletion was rejected for ${file.absolutePath}.",
            )
        }
        return deleted
    }

    private fun isRegularFile(
        file: File,
        operation: String,
        fileOperations: R2PrivateTransientOrphanFileOperations,
    ): Boolean = try {
        fileOperations.isRegularFile(file)
    } catch (failure: Throwable) {
        throw R2PrivateTransientCleanupUnprovenException(
            "R2 private transient $operation could not classify ${file.absolutePath}.",
            failure,
        )
    }

    private fun recognizedKind(
        childName: String,
        allowedKinds: Set<R2PrivateTransientKind>,
    ): R2PrivateTransientKind? =
        allowedKinds.firstOrNull { candidate ->
            childName.startsWith(candidate.prefix) &&
                childName.endsWith(candidate.suffix)
        }

    private val INPUT_STAGING_KINDS = setOf(
        R2PrivateTransientKind.PRIVATE_INPUT,
        R2PrivateTransientKind.PROJECT_SPOOL,
    )
    private val OUTPUT_STAGING_KINDS = setOf(
        R2PrivateTransientKind.PRIVATE_OUTPUT,
    )
}

private data class R2PrivateTransientCandidate(
    val file: File,
    val kind: R2PrivateTransientKind,
)

internal enum class R2PrivateTransientKind(
    val prefix: String,
    val suffix: String,
) {
    PRIVATE_INPUT(
        prefix = "r2-private-input-",
        suffix = ".staging",
    ),
    PROJECT_SPOOL(
        prefix = "r2-project-vfs-",
        suffix = ".spool",
    ),
    PRIVATE_OUTPUT(
        prefix = "r2-private-output-",
        suffix = ".zip.staging",
    ),
}

internal class R2PrivateTransientCleanupUnprovenException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
