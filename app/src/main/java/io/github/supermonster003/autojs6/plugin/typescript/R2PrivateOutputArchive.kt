package io.github.supermonster003.autojs6.plugin.typescript

import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Limits
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Validation
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Validator
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.CRC32
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/** Control-plane values copied from the accepted input authority into formal output schema v1. */
internal data class R2OutputArchiveAuthority(
    val requestId: String,
    val projectId: String,
    val entrySourceUri: String,
    val entryPath: String,
    val profile: R2ProjectProfile,
    val profileAuthority: R2CompilationProfileAuthority,
    val configSchemaRevision: Int = profileAuthority.configSchemaRevision,
    val configKind: String = profileAuthority.configKind,
    val compilationMode: String,
    val compilerVersion: String,
    val compilerDistributionFingerprint: String,
    val declarationsFingerprint: String,
    val runtimeCapabilitiesFingerprint: String,
    val inputArchiveSha256: String,
    val requestManifestSha256: String,
    val inputArchiveSchemaRevision: Int,
    val inputQuotaRevision: Int,
    val callerInventoryFingerprint: String,
    val retainedInventoryFingerprint: String,
    val declarationProfileFingerprint: String,
    val declarationInventoryFingerprint: String,
    val declarationFileCount: Int,
    val declarationByteLength: Long,
    val retainedFileCount: Int,
    val retainedByteLength: Long,
    val largestRetainedFileBytes: Long,
) {

    init {
        require(requestId.isNotBlank())
        require(requestId.length <= TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS)
        require(projectId.isNotBlank())
        require(projectId.length <= TypeScriptCompilerContract.MAX_PROJECT_ID_CHARS)
        require(
            profileAuthority.profile == profile &&
                R2CompilationProfileRegistry.containsExact(profileAuthority),
        ) {
            "Output profile authority is not the exact local authority."
        }
        require(
            entrySourceUri.startsWith("autojs://source/") &&
                entrySourceUri.length <= TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS,
        )
        require(
            entryPath.startsWith("sources/") &&
                R2ProjectSourceKindAdmission.isExecutableSourcePath(
                    "/$entryPath",
                    profileAuthority,
                ) &&
                entryPath.isCanonicalArchivePath(),
        )
        if (
            profileAuthority ==
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        ) {
            require(
                entrySourceUri == R2GeneratedModuleMappingPolicy.originalSourceUri(
                    projectId = projectId,
                    sourcePath = "/$entryPath",
                ),
            ) {
                "Node revision-2 output entry URI is not its exact generated-mapping identity."
            }
        }
        require(
            R2Digests.equal(
                runtimeCapabilitiesFingerprint,
                profileAuthority.runtimeCapabilitiesFingerprint,
            ),
        ) {
            "Output runtime capabilities do not match the profile authority."
        }
        require(
            configKind == TypeScriptR2Contract.CONFIG_KIND_NONE &&
                configSchemaRevision == profileAuthority.configSchemaRevision ||
                (
                    profileAuthority == R2CompilationProfileRegistry.PROVISIONAL_RHINO ||
                        profileAuthority ==
                        R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
                    ) &&
                configKind == TypeScriptR2Contract.CONFIG_KIND_TSCONFIG &&
                configSchemaRevision ==
                TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION
        ) {
            "Output config authority is not supported."
        }
        require(compilationMode in TypeScriptCompilerContract.COMPILATION_MODES)
        require(compilerVersion.isNotBlank() && compilerVersion.length <= 128)
        R2Digests.requireLowerSha256(
            compilerDistributionFingerprint,
            "Compiler distribution fingerprint",
        )
        R2Digests.requireLowerSha256(
            declarationsFingerprint,
            "Declarations fingerprint",
        )
        R2Digests.requireLowerSha256(
            runtimeCapabilitiesFingerprint,
            "Runtime capabilities fingerprint",
        )
        R2Digests.requireLowerSha256(inputArchiveSha256, "Input archive SHA-256")
        R2Digests.requireLowerSha256(
            requestManifestSha256,
            "Request manifest SHA-256",
        )
        require(inputArchiveSchemaRevision == R2InputArchiveSchemaV1.REVISION) {
            "Output provenance does not use formal input archive schema v1."
        }
        require(R2InputArchiveSchemaV1.supportsInputQuotaRevision(inputQuotaRevision)) {
            "Output provenance does not use a supported ordinary input quota."
        }
        R2Digests.requireLowerSha256(
            callerInventoryFingerprint,
            "Caller inventory fingerprint",
        )
        R2Digests.requireLowerSha256(
            retainedInventoryFingerprint,
            "Retained inventory fingerprint",
        )
        R2Digests.requireLowerSha256(
            declarationProfileFingerprint,
            "Declaration profile fingerprint",
        )
        R2Digests.requireLowerSha256(
            declarationInventoryFingerprint,
            "Declaration inventory fingerprint",
        )
        require(
            declarationFileCount > 0 &&
                declarationFileCount <= retainedFileCount &&
                declarationByteLength in 0L..retainedByteLength &&
                retainedFileCount in 1..R2_FILE_BACKED_MAXIMUM_FILES &&
                retainedByteLength in 0L..R2_FILE_BACKED_MAXIMUM_TOTAL_BYTES &&
                largestRetainedFileBytes in
                0L..TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
        ) {
            "Output accepted-input capacity provenance is invalid."
        }
    }

    companion object {

        fun from(adapted: R2AdaptedProjectArchive): R2OutputArchiveAuthority =
            R2OutputArchiveAuthority(
                requestId = adapted.manifest.requestId,
                projectId = adapted.manifest.projectId,
                entrySourceUri = adapted.manifest.entrySourceUri,
                entryPath = adapted.manifest.entryPath,
                profile = adapted.manifest.profile,
                profileAuthority = adapted.manifest.profileAuthority,
                configSchemaRevision =
                    if (adapted.manifest.rawConfigDescriptor == null) {
                        adapted.manifest.profileAuthority.configSchemaRevision
                    } else {
                        TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION
                    },
                configKind =
                    if (adapted.manifest.rawConfigDescriptor == null) {
                        adapted.manifest.profileAuthority.configKind
                    } else {
                        TypeScriptR2Contract.CONFIG_KIND_TSCONFIG
                    },
                compilationMode = adapted.manifest.compilationMode,
                compilerVersion = adapted.manifest.compilerVersion,
                compilerDistributionFingerprint =
                    adapted.manifest.compilerDistributionFingerprint,
                declarationsFingerprint = adapted.manifest.declarationsFingerprint,
                runtimeCapabilitiesFingerprint =
                    adapted.manifest.runtimeCapabilitiesFingerprint,
                inputArchiveSha256 = adapted.archiveSha256,
                requestManifestSha256 = adapted.requestManifestSha256,
                inputArchiveSchemaRevision = adapted.acceptedInputAuthority.schemaRevision,
                inputQuotaRevision = adapted.acceptedInputAuthority.inputQuotaRevision,
                callerInventoryFingerprint =
                    adapted.acceptedInputAuthority.callerInventoryFingerprint,
                retainedInventoryFingerprint =
                    adapted.acceptedInputAuthority.retainedInventoryFingerprint,
                declarationProfileFingerprint =
                    adapted.acceptedInputAuthority.declarationProfileFingerprint,
                declarationInventoryFingerprint =
                    adapted.acceptedInputAuthority.declarationInventoryFingerprint,
                declarationFileCount = adapted.acceptedInputAuthority.declarationFileCount,
                declarationByteLength = adapted.acceptedInputAuthority.declarationByteLength,
                retainedFileCount = adapted.acceptedInputAuthority.capacity.fileCount,
                retainedByteLength = adapted.acceptedInputAuthority.capacity.totalUtf8Bytes,
                largestRetainedFileBytes =
                    adapted.acceptedInputAuthority.capacity.largestFileUtf8Bytes,
            )
    }
}

internal data class R2OutputArchiveFile(
    val path: String,
    val kind: String,
    val size: Long,
    val sha256: String,
    val sourcePaths: List<String>,
)

internal data class R2OutputArchiveSummary(
    val outcome: String,
    val diagnosticCount: Int,
    val emittedModuleCount: Int,
    val outputManifestSha256: String,
    val containerSha256: String,
    val containerBytes: Long,
)

/**
 * A read-only handle to the exact app-private output inode that passed ZIP v1 validation.
 *
 * Android/Linux removes the directory entry before this object is returned. Windows JVM tests
 * retain a read-only fallback name until [close].
 */
internal class R2StagedOutputArchive private constructor(
    private val reader: RandomAccessFile,
    private val fallbackFile: File?,
    val summary: R2OutputArchiveSummary,
    val validation: ZipArchiveV1Validation,
) : Closeable {

    private val lifecycleLock = Any()
    private var usable = true
    private var readerClosed = false
    private var cleanupComplete = false

    internal val fileDescriptor
        get() = synchronized(lifecycleLock) {
            checkUsable()
            reader.fd
        }

    internal fun verifyUnchanged(): Boolean = synchronized(lifecycleLock) {
        checkUsable()
        summary.containerBytes == reader.length() &&
            R2Digests.equal(summary.containerSha256, digestReader(reader))
    }

    internal fun validateStructure(): ZipArchiveV1Validation = synchronized(lifecycleLock) {
        checkUsable()
        ZipArchiveV1Validator.validateImmutable(
            file = reader,
            limits = ZipArchiveV1Limits.OUTPUT,
        )
    }

    internal fun <T> withImmutableReader(block: (RandomAccessFile) -> T): T =
        synchronized(lifecycleLock) {
            checkUsable()
            block(reader)
        }

    override fun close() {
        synchronized(lifecycleLock) {
            if (cleanupComplete) return
            usable = false
            var failure: Throwable? = null
            if (!readerClosed) {
                try {
                    reader.close()
                    readerClosed = true
                } catch (error: Throwable) {
                    failure = error
                }
            }
            fallbackFile?.let { file ->
                try {
                    file.setWritable(true, true)
                    if (!file.delete() && file.exists()) {
                        throw IOException(
                            "R2 private output staging could not be deleted: " +
                                file.absolutePath,
                        )
                    }
                } catch (error: Throwable) {
                    failure?.addSuppressed(error) ?: run { failure = error }
                }
            }
            if (failure == null) {
                cleanupComplete = true
                return
            }
            throw when (val cleanupFailure = failure) {
                is IOException -> cleanupFailure
                else -> IOException(
                    "R2 private output staging could not be closed.",
                    cleanupFailure,
                )
            }
        }
    }

    private fun checkUsable() {
        check(usable && !readerClosed) {
            "R2 output staging is closed."
        }
    }

    companion object {

        internal fun create(
            reader: RandomAccessFile,
            fallbackFile: File?,
            summary: R2OutputArchiveSummary,
            validation: ZipArchiveV1Validation,
        ) = R2StagedOutputArchive(
            reader = reader,
            fallbackFile = fallbackFile,
            summary = summary,
            validation = validation,
        )
    }
}

/**
 * Builds formal output archive schema v1 in a bounded private ZIP v1 container.
 *
 * S1-3 quota revision 2 bounds final packaged output at 512 artifacts, 16 MiB per JavaScript or
 * Source Map artifact, and 64 MiB aggregate after sourceMappingURL rewriting. This class is the
 * final quota authority and durable staging/validation boundary. The larger legacy ZIP ceilings
 * remain transport validation limits. The independently versioned diagnostics document is frozen
 * by [R2DiagnosticsSchemaV1] and retains its own 1000-record/512-KiB semantic quota.
 */
internal object R2PrivateOutputArchive {

    const val OUTPUT_MANIFEST_PATH = R2OutputArchiveSchemaV1.MANIFEST_PATH
    const val DIAGNOSTICS_PATH = R2OutputArchiveSchemaV1.DIAGNOSTICS_PATH
    const val FORMAL_MANIFEST_SCHEMA = R2OutputArchiveSchemaV1.SCHEMA
    const val FORMAL_MANIFEST_REVISION = R2OutputArchiveSchemaV1.REVISION
    const val FORMAL_DIAGNOSTICS_SCHEMA = R2DiagnosticsSchemaV1.SCHEMA
    const val FORMAL_DIAGNOSTICS_REVISION = R2DiagnosticsSchemaV1.REVISION

    fun stage(
        privateDirectory: File,
        acceptedArchive: R2AdaptedProjectArchive,
        compilationOutcome: R2ProjectCompilationOutcome,
    ): R2StagedOutputArchive {
        val compilation = compilationOutcome.compilation
            ?: fail("Only a successful project result can be staged.")
        if (compilationOutcome.exactSnapshot !== acceptedArchive.snapshot) {
            fail(
                "Project result is not bound to the exact accepted input snapshot.",
            )
        }
        return stageBound(
            privateDirectory = privateDirectory,
            authority = R2OutputArchiveAuthority.from(acceptedArchive),
            compilation = compilation,
            acceptedRootPaths = acceptedArchive.snapshot.rootNames.toSet(),
            acceptedSnapshotPaths = acceptedDiagnosticSourcePaths(acceptedArchive.snapshot),
            acceptedSnapshot = acceptedArchive.snapshot,
        )
    }

    private fun acceptedDiagnosticSourcePaths(
        snapshot: R2ProjectInputSnapshot,
    ): Set<String> = HashSet(snapshot.filePaths).apply {
        snapshot.packageTargetInventory?.files?.forEach { file ->
            add("/${file.path}")
        }
        snapshot.nodePackageTargetV2Inventory?.files?.forEach { file ->
            add("/${file.path}")
        }
    }

    /**
     * Direct-construction seam for boundary tests. Production callers must use the exact
     * accepted-archive/result overload above.
     */
    internal fun stageForTesting(
        privateDirectory: File,
        authority: R2OutputArchiveAuthority,
        compilation: R2ProjectCompilation,
        acceptedRootPaths: Set<String> = setOf("/${authority.entryPath}"),
        acceptedSnapshotPaths: Set<String>? = null,
        acceptedSnapshot: R2ProjectInputSnapshot? = null,
    ): R2StagedOutputArchive {
        check(BuildConfig.DEBUG) {
            "Unbound output staging is unavailable outside debug builds."
        }
        return stageBound(
            privateDirectory = privateDirectory,
            authority = authority,
            compilation = compilation,
            acceptedRootPaths = acceptedRootPaths,
            acceptedSnapshotPaths = acceptedSnapshotPaths,
            acceptedSnapshot = acceptedSnapshot,
        )
    }

    private fun stageBound(
        privateDirectory: File,
        authority: R2OutputArchiveAuthority,
        compilation: R2ProjectCompilation,
        acceptedRootPaths: Set<String>,
        acceptedSnapshotPaths: Set<String>?,
        acceptedSnapshot: R2ProjectInputSnapshot?,
    ): R2StagedOutputArchive {
        require(privateDirectory.isDirectory) {
            "R2 private output directory is unavailable."
        }
        val staged = prepareEntries(
            authority = authority,
            compilation = compilation,
            acceptedRootPaths = acceptedRootPaths,
            acceptedSnapshotPaths = acceptedSnapshotPaths,
            acceptedSnapshot = acceptedSnapshot,
        )
        val stagingFile = try {
            File.createTempFile(
                STAGING_FILE_PREFIX,
                STAGING_FILE_SUFFIX,
                privateDirectory,
            )
        } catch (error: Throwable) {
            staged.archiveEntries.forEach { entry -> entry.bytes.fill(0) }
            throw R2OutputArchiveException(
                contractErrorCode =
                TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT,
                message = "R2 private output staging file could not be created.",
                cause = error,
            )
        }
        var reader: RandomAccessFile? = null
        try {
            val output = FileOutputStream(stagingFile)
            var writeFailure: Throwable? = null
            try {
                writeStoredZip(output, staged.archiveEntries)
                output.fd.sync()
            } catch (failure: Throwable) {
                writeFailure = failure
            }
            val writerCloseFailure = runCatching { output.close() }.exceptionOrNull()
            if (writerCloseFailure != null) {
                throw R2PrivateOutputCleanupUnprovenException(
                    "R2 private output writer close could not be proven.",
                    writerCloseFailure,
                ).also { unproven ->
                    writeFailure?.let(unproven::addSuppressed)
                }
            }
            writeFailure?.let { throw it }
            if (!stagingFile.setReadOnly()) {
                throw IOException("R2 private output staging could not become read-only.")
            }
            reader = RandomAccessFile(stagingFile, "r")
            val fallbackFile =
                if (SUPPORTS_UNLINK_WHILE_OPEN) {
                    if (!stagingFile.delete() && stagingFile.exists()) {
                        throw IOException(
                            "R2 private output staging could not be unlinked.",
                        )
                    }
                    null
                } else {
                    stagingFile
                }
            val validation = ZipArchiveV1Validator.validateImmutable(
                file = reader,
                limits = ZipArchiveV1Limits.OUTPUT,
            )
            check(
                validation.entries.map { entry -> entry.path } ==
                    staged.archiveEntries.map(OutputEntry::path),
            ) {
                "R2 private output ZIP inventory changed during staging."
            }
            val containerSha256 = digestReader(reader)
            val summary = R2OutputArchiveSummary(
                outcome = staged.outcome,
                diagnosticCount = compilation.diagnostics.size,
                emittedModuleCount = compilation.executableModules.size,
                outputManifestSha256 = staged.outputManifestSha256,
                containerSha256 = containerSha256,
                containerBytes = validation.containerBytes,
            )
            return R2StagedOutputArchive.create(
                reader = reader,
                fallbackFile = fallbackFile,
                summary = summary,
                validation = validation,
            ).also {
                reader = null
            }
        } catch (error: Throwable) {
            var cleanupFailure =
                runCatching { reader?.close() }.exceptionOrNull()
            try {
                stagingFile.setWritable(true, true)
                if (!stagingFile.delete() && stagingFile.exists()) {
                    throw IOException(
                        "R2 failed output staging could not be deleted: " +
                            stagingFile.absolutePath,
                    )
                }
            } catch (deleteFailure: Throwable) {
                cleanupFailure?.addSuppressed(deleteFailure)
                    ?: run { cleanupFailure = deleteFailure }
            }
            if (cleanupFailure != null) {
                throw R2PrivateOutputCleanupUnprovenException(
                    "R2 failed output staging cleanup could not be proven.",
                    error,
                ).also { unproven ->
                    unproven.addSuppressed(cleanupFailure)
                }
            }
            throw when (error) {
                is R2PrivateOutputCleanupUnprovenException -> error
                is R2OutputArchiveException -> error
                else -> R2OutputArchiveException(
                    contractErrorCode =
                    TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT,
                    message = "R2 private output archive could not be staged.",
                    cause = error,
                )
            }
        } finally {
            staged.archiveEntries.forEach { entry -> entry.bytes.fill(0) }
        }
    }

    private fun prepareEntries(
        authority: R2OutputArchiveAuthority,
        compilation: R2ProjectCompilation,
        acceptedRootPaths: Set<String>,
        acceptedSnapshotPaths: Set<String>?,
        acceptedSnapshot: R2ProjectInputSnapshot?,
    ): PreparedOutput {
        val outcome = requireRepresentableCompilation(
            authority = authority,
            compilation = compilation,
            acceptedRootPaths = acceptedRootPaths,
            acceptedSnapshotPaths = acceptedSnapshotPaths,
            acceptedSnapshot = acceptedSnapshot,
        )

        val diagnosticsBytes = diagnosticsJson(authority, compilation)
        try {
            R2DiagnosticsSchemaV1.requireDocumentBytes(diagnosticsBytes)
        } catch (_: IllegalArgumentException) {
            diagnosticsBytes.fill(0)
            fail("Diagnostics JSON exceeds the frozen semantic byte limit.")
        }
        val payloadEntries = ArrayList<OutputEntry>(compilation.outputs.size + 1)
        payloadEntries += OutputEntry(
            path = DIAGNOSTICS_PATH,
            kind = KIND_DIAGNOSTICS,
            bytes = diagnosticsBytes,
            sourcePaths = compilation.diagnostics
                .flatMap { diagnostic ->
                    buildList {
                        diagnostic.sourcePath?.let { sourcePath ->
                            add(
                                toArchiveDiagnosticSourcePath(
                                    sourcePath = sourcePath,
                                    phase = diagnostic.phase,
                                ),
                            )
                        }
                        diagnostic.relatedInformation.forEach { related ->
                            related.sourcePath?.let { sourcePath ->
                                add(
                                    toArchiveDiagnosticSourcePath(
                                        sourcePath = sourcePath,
                                        phase = diagnostic.phase,
                                    ),
                                )
                            }
                        }
                    }
                }
                .distinct()
                .sorted(),
        )
        var manifestBytesToWipe: ByteArray? = null
        try {
            compilation.outputs.forEach { output ->
                val path = toArchiveOutputPath(output.path)
                var ownedBytes: ByteArray? =
                    R2Utf8.encode(output.text, "Project output ${output.path}")
                try {
                    if (
                        !R2Digests.equal(
                            output.sha256,
                            R2Digests.sha256(requireNotNull(ownedBytes)),
                        )
                    ) {
                        fail("Project output digest changed before private staging.")
                    }
                    if (!output.isSourceMap) {
                        val packagedText = rewriteSourceMappingUrl(
                            compilerPath = output.path,
                            moduleArchivePath = path,
                            source = output.text,
                        )
                        val packagedBytes = R2Utf8.encode(
                            packagedText,
                            "Packaged project output ${output.path}",
                        )
                        ownedBytes.fill(0)
                        ownedBytes = packagedBytes
                    }
                    requireKindByteQuota(
                        kind =
                        if (output.isSourceMap) KIND_SOURCE_MAP else KIND_JAVASCRIPT,
                        size = requireNotNull(ownedBytes).size.toLong(),
                    )
                    payloadEntries += OutputEntry(
                        path = path,
                        kind =
                        if (output.isSourceMap) KIND_SOURCE_MAP else KIND_JAVASCRIPT,
                        bytes = requireNotNull(ownedBytes),
                        sourcePaths = output.sourcePaths.map(::toArchiveSourcePath),
                    )
                    ownedBytes = null
                } finally {
                    ownedBytes?.fill(0)
                }
            }
            payloadEntries.sortBy(OutputEntry::path)
            requireStrictOutputInventory(payloadEntries)
            requireOrdinaryEmittedArtifactQuota(payloadEntries)
            val files = payloadEntries.map(OutputEntry::descriptor)
            R2OutputArchiveSchemaV1.requireOutcomeInventory(
                outcome = outcome,
                emitSkipped = compilation.emitSkipped,
                diagnosticCount = compilation.diagnostics.size,
                emittedModuleCount = compilation.executableModules.size,
                files = files,
            )
            val manifestBytes = manifestJson(
                authority = authority,
                compilation = compilation,
                outcome = outcome,
                files = files,
            )
            manifestBytesToWipe = manifestBytes
            if (manifestBytes.size.toLong() > TypeScriptCompilerContract.MAX_MANIFEST_BYTES) {
                fail("Compilation manifest exceeds the frozen semantic byte limit.")
            }
            val archiveEntries = ArrayList<OutputEntry>(payloadEntries.size + 1)
            archiveEntries += OutputEntry(
                path = OUTPUT_MANIFEST_PATH,
                kind = KIND_MANIFEST,
                bytes = manifestBytes,
                sourcePaths = emptyList(),
            )
            archiveEntries += payloadEntries
            requireFrozenOutputQuotas(archiveEntries)
            return PreparedOutput(
                outcome = outcome,
                outputManifestSha256 = R2Digests.sha256(manifestBytes),
                archiveEntries = archiveEntries,
            ).also {
                manifestBytesToWipe = null
            }
        } catch (error: Throwable) {
            manifestBytesToWipe?.fill(0)
            payloadEntries.forEach { entry -> entry.bytes.fill(0) }
            throw error
        }
    }

    private fun requireRepresentableCompilation(
        authority: R2OutputArchiveAuthority,
        compilation: R2ProjectCompilation,
        acceptedRootPaths: Set<String>,
        acceptedSnapshotPaths: Set<String>?,
        acceptedSnapshot: R2ProjectInputSnapshot?,
    ): String {
        if (
            compilation.compilerApi != "createIncrementalProgram" ||
            compilation.compilerVersion != authority.compilerVersion ||
            compilation.targetProfile != authority.profile.contractValue ||
            compilation.profileSchemaRevision != authority.profileAuthority.profileSchemaRevision ||
            compilation.profileRevision != authority.profileAuthority.profileRevision ||
            compilation.configSchemaRevision != authority.configSchemaRevision ||
            compilation.configKind != authority.configKind ||
            (
                authority.configKind == TypeScriptR2Contract.CONFIG_KIND_NONE &&
                    (
                        compilation.effectiveOptions != authority.profileAuthority.effectiveOptions ||
                            compilation.controlledEffectiveOptions != null ||
                            compilation.selectedRootNames.isNotEmpty()
                        )
                ) ||
            (
                authority.configKind == TypeScriptR2Contract.CONFIG_KIND_TSCONFIG &&
                    compilation.controlledEffectiveOptions == null
                )
        ) {
            fail("Compiler result does not match the accepted output authority.")
        }
        if (
            authority.profileAuthority ==
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        ) {
            val snapshot = acceptedSnapshot
                ?: fail("Node revision-2 output requires its exact accepted input snapshot.")
            if (
                snapshot.acceptedProjectId != authority.projectId ||
                snapshot.profileAuthority != authority.profileAuthority
            ) {
                fail("Node revision-2 output is bound to a different input authority.")
            }
            try {
                R2NodeRevisionTwoResultAuthority.requireExact(
                    snapshot = snapshot,
                    outputs = compilation.outputs,
                    claimed = requireNotNull(compilation.nodeRevisionTwoResultAuthority),
                )
            } catch (error: IllegalArgumentException) {
                fail(
                    "Node revision-2 result mapping does not match its exact input snapshot: " +
                        error.message.orEmpty(),
                )
            }
        }
        val entrySourcePath = "/${authority.entryPath}"
        if (
            entrySourcePath !in acceptedRootPaths ||
            acceptedRootPaths.isEmpty() ||
            acceptedRootPaths.any { sourcePath ->
                !sourcePath.isCanonicalProjectSourcePath(authority.profileAuthority)
            }
        ) {
            fail("Accepted project roots do not contain the exact entry source.")
        }
        if (
            authority.configKind == TypeScriptR2Contract.CONFIG_KIND_TSCONFIG &&
            (
                entrySourcePath !in compilation.selectedRootNames ||
                    acceptedSnapshotPaths?.let { paths ->
                        compilation.selectedRootNames.any { root -> root !in paths }
                    } == true
                )
        ) {
            fail("Controlled roots do not select the exact accepted entry snapshot.")
        }
        if (compilation.diagnostics.size > TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT) {
            fail("Compiler result exceeds the frozen diagnostic-count limit.")
        }
        val relatedInformationCount = compilation.diagnostics.sumOf { diagnostic ->
            diagnostic.relatedInformation.size
        }
        if (
            relatedInformationCount > TypeScriptR2Contract.MAX_RELATED_INFORMATION_TOTAL
        ) {
            fail("Compiler result exceeds the frozen related-information-count limit.")
        }
        compilation.diagnostics.forEach { diagnostic ->
            requireValidDiagnostic(authority, diagnostic)
        }
        if (
            acceptedSnapshotPaths != null &&
            compilation.diagnostics.any { diagnostic ->
                diagnostic.sourcePath?.let { path -> path !in acceptedSnapshotPaths } == true ||
                    diagnostic.relatedInformation.any { related ->
                        related.sourcePath?.let { path -> path !in acceptedSnapshotPaths } == true
                    }
            }
        ) {
            fail("Compiler diagnostic is outside the exact accepted input snapshot.")
        }

        val hasBlockingDiagnostics = compilation.diagnostics.any { diagnostic ->
            diagnostic.category == TYPESCRIPT_ERROR_CATEGORY
        }
        val outcome = when {
            compilation.emitSkipped &&
                hasBlockingDiagnostics &&
                compilation.outputs.isEmpty() ->
                TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS
            !compilation.emitSkipped &&
                !hasBlockingDiagnostics &&
                compilation.executableModules.isNotEmpty() &&
                compilation.executableModules.size == compilation.sourceMaps.size ->
                TypeScriptCompilerContract.OUTCOME_EMITTED
            else -> fail(
                "Compiler result cannot be represented by the formal output archive.",
            )
        }
        if (outcome == TypeScriptCompilerContract.OUTCOME_EMITTED) {
            requireExactEmittedInventory(
                compilation = compilation,
                acceptedRootPaths = acceptedRootPaths,
                entrySourcePath = entrySourcePath,
                profileAuthority = authority.profileAuthority,
                controlled =
                    authority.configKind == TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
            )
        }
        return outcome
    }

    private fun requireValidDiagnostic(
        authority: R2OutputArchiveAuthority,
        diagnostic: R2ProjectDiagnostic,
    ) {
        if (
            diagnostic.code <= 0 ||
            diagnostic.message.length > TypeScriptR2Contract.MAX_DIAGNOSTIC_MESSAGE_CHARS ||
            diagnostic.relatedInformation.size >
            TypeScriptR2Contract.MAX_RELATED_INFORMATION_PER_DIAGNOSTIC
        ) {
            fail("Compiler result contains an invalid diagnostic.")
        }
        try {
            R2DiagnosticsSchemaV1.requireCategory(diagnostic.category)
        } catch (error: IllegalArgumentException) {
            fail("Compiler result contains an invalid diagnostic category.")
        }
        diagnostic.sourcePath?.let {
            val archivePath = toArchiveDiagnosticSourcePath(
                sourcePath = it,
                phase = diagnostic.phase,
            )
            if (
                !isSupportedDiagnosticArchivePath(
                    archivePath,
                    authority.profileAuthority,
                    diagnostic.phase,
                )
            ) {
                fail("Compiler result contains an invalid diagnostic source.")
            }
            requireDiagnosticSourceUri(authority.projectId, it)
        }
        val positions = listOf(
            diagnostic.start,
            diagnostic.length,
            diagnostic.line,
            diagnostic.column,
        )
        if (
            positions.any { value -> value == null } &&
            positions.any { value -> value != null }
        ) {
            fail("Compiler result contains a partial diagnostic position.")
        }
        if (
            diagnostic.start != null &&
            (
                    diagnostic.sourcePath == null ||
                    diagnostic.start < 0 ||
                    requireNotNull(diagnostic.length) < 0 ||
                    diagnostic.start > Int.MAX_VALUE - requireNotNull(diagnostic.length) ||
                    requireNotNull(diagnostic.line) <
                    TypeScriptR2Contract.DIAGNOSTICS_LINE_BASE ||
                    requireNotNull(diagnostic.column) <
                    TypeScriptR2Contract.DIAGNOSTICS_COLUMN_BASE
                )
        ) {
            fail("Compiler result contains an invalid diagnostic position.")
        }
        diagnostic.relatedInformation.forEach { related ->
            requireValidRelatedInformation(
                authority = authority,
                phase = diagnostic.phase,
                related = related,
            )
        }
    }

    private fun requireValidRelatedInformation(
        authority: R2OutputArchiveAuthority,
        phase: R2ProjectDiagnosticPhase,
        related: R2ProjectRelatedInformation,
    ) {
        if (
            related.code <= 0 ||
            related.message.length > TypeScriptR2Contract.MAX_DIAGNOSTIC_MESSAGE_CHARS
        ) {
            fail("Compiler result contains invalid diagnostic related information.")
        }
        try {
            R2DiagnosticsSchemaV1.requireCategory(related.category)
        } catch (error: IllegalArgumentException) {
            fail("Compiler result contains an invalid related-information category.")
        }
        related.sourcePath?.let { sourcePath ->
            val archivePath = toArchiveDiagnosticSourcePath(sourcePath, phase)
            if (
                !isSupportedDiagnosticArchivePath(
                    archivePath,
                    authority.profileAuthority,
                    phase,
                )
            ) {
                fail("Compiler result contains an invalid related-information source.")
            }
            requireDiagnosticSourceUri(authority.projectId, sourcePath)
        }
        val positions = listOf(related.start, related.length, related.line, related.column)
        if (
            positions.any { value -> value == null } &&
            positions.any { value -> value != null }
        ) {
            fail("Compiler result contains a partial related-information position.")
        }
        if (
            related.start != null &&
            (
                related.sourcePath == null ||
                    related.start < 0 ||
                    requireNotNull(related.length) < 0 ||
                    related.start > Int.MAX_VALUE - requireNotNull(related.length) ||
                    requireNotNull(related.line) <
                    TypeScriptR2Contract.DIAGNOSTICS_LINE_BASE ||
                    requireNotNull(related.column) <
                    TypeScriptR2Contract.DIAGNOSTICS_COLUMN_BASE
                )
        ) {
            fail("Compiler result contains an invalid related-information position.")
        }
    }

    private fun requireExactEmittedInventory(
        compilation: R2ProjectCompilation,
        acceptedRootPaths: Set<String>,
        entrySourcePath: String,
        profileAuthority: R2CompilationProfileAuthority,
        controlled: Boolean,
    ) {
        val nodeMappingsBySource = compilation.nodeRevisionTwoResultAuthority
            ?.generatedModuleMappings
            ?.associateBy(R2GeneratedModuleMapping::sourcePath)
            .orEmpty()
        val allPaths = HashSet<String>(compilation.outputs.size)
        val foldedPaths = HashSet<String>(compilation.outputs.size)
        compilation.outputs.forEach { output ->
            if (
                !output.path.isCanonicalCompilerOutputPath(profileAuthority) ||
                !allPaths.add(output.path) ||
                !foldedPaths.add(R2UnicodePathPolicy.asciiCaseFold(output.path))
            ) {
                fail("Compiler result contains a non-canonical or colliding output path.")
            }
        }
        val sourceMaps = compilation.sourceMaps.associateBy(R2ProjectOutput::path)
        if (
            sourceMaps.size != compilation.sourceMaps.size ||
            compilation.outputs.size != compilation.executableModules.size * 2
        ) {
            fail("Compiler result does not contain exactly one Source Map per module.")
        }
        val emittedSources = LinkedHashSet<String>()
        compilation.executableModules.forEach { module ->
            if (module.sourcePaths.size != 1) {
                fail("Compiler module does not identify exactly one project source.")
            }
            val sourcePath = module.sourcePaths.single()
            val expectedModulePath = nodeMappingsBySource[sourcePath]
                ?.generatedPath
                ?: sourcePath.expectedCompilerOutputPath()
            if (
                sourcePath !in acceptedRootPaths ||
                module.path != expectedModulePath ||
                !emittedSources.add(sourcePath)
            ) {
                fail("Compiler module does not map one-to-one to the accepted project roots.")
            }
            val sourceMap = sourceMaps["${module.path}.map"]
                ?: fail("Compiler module has no exact Source Map pair.")
            if (sourceMap.sourcePaths != module.sourcePaths) {
                fail("Compiler module and Source Map identify different sources.")
            }
            requireValidSourceMap(sourceMap, profileAuthority)
            requireTerminalSourceMappingUrl(module)
        }
        if (controlled) {
            val selectedExecutableRoots = compilation.selectedRootNames
                .filterNot { path -> path.endsWith(".d.ts") }
                .toSet()
            if (
                selectedExecutableRoots.isEmpty() ||
                !emittedSources.containsAll(selectedExecutableRoots) ||
                !acceptedRootPaths.containsAll(emittedSources) ||
                entrySourcePath !in emittedSources
            ) {
                fail(
                    "Controlled compiler output does not cover selected and reachable sources.",
                )
            }
        } else if (
            emittedSources != acceptedRootPaths ||
            entrySourcePath !in emittedSources
        ) {
            fail("Compiler output does not exactly cover the accepted project roots.")
        }
    }

    private fun requireValidSourceMap(
        output: R2ProjectOutput,
        profileAuthority: R2CompilationProfileAuthority,
    ) {
        val bytes = R2Utf8.encode(output.text, "Project Source Map")
        val sourceMap = try {
            R2StrictJson.parseObject(
                bytes = bytes,
                maximumDepth = TypeScriptCompilerContract.MAX_JSON_DEPTH,
            )
        } catch (error: IllegalArgumentException) {
            fail("Compiler result contains an invalid Source Map JSON.")
        } finally {
            bytes.fill(0)
        }
        if (sourceMap.keys != SOURCE_MAP_KEYS) {
            fail("Compiler result contains a Source Map with an invalid shape.")
        }
        val version = sourceMap["version"] as? R2StrictJson.NumberToken
        val names = sourceMap["names"] as? List<*>
        val sources = sourceMap["sources"] as? List<*>
        val sourceRoot = sourceMap["sourceRoot"] as? String
        val mappings = sourceMap["mappings"] as? String
        if (
            version?.source != "3" ||
            sourceMap["file"] != output.path.removeSuffix(".map").substringAfterLast('/') ||
            sourceRoot != "" ||
            names == null ||
            names.any { name -> name !is String } ||
            sources == null ||
            sources.any { source -> source !is String } ||
            mappings == null
        ) {
            fail("Compiler result contains an invalid Source Map v3.")
        }
        val resolvedSources = sources.map { source ->
            resolveSourceMapSource(
                mapPath = output.path,
                source = source as String,
                profileAuthority = profileAuthority,
            )
        }
        if (resolvedSources != output.sourcePaths) {
            fail("Compiler Source Map does not resolve to its declared source inventory.")
        }
    }

    private fun requireTerminalSourceMappingUrl(module: R2ProjectOutput) {
        val expected =
            "//# sourceMappingURL=" +
                encodeTypeScriptSourceMappingUrl(
                    "${module.path.substringAfterLast('/')}.map",
                )
        val withoutFinalLf = module.text.removeSuffix("\n")
        if (
            '\r' in module.text ||
            withoutFinalLf.substringAfterLast('\n') != expected
        ) {
            fail("Compiler module has no exact terminal Source Map reference.")
        }
    }

    internal fun rewriteSourceMappingUrlForOrdinaryQuota(
        compilerPath: String,
        moduleArchivePath: String,
        source: String,
    ): String = rewriteSourceMappingUrl(
        compilerPath = compilerPath,
        moduleArchivePath = moduleArchivePath,
        source = source,
    )

    private fun rewriteSourceMappingUrl(
        compilerPath: String,
        moduleArchivePath: String,
        source: String,
    ): String {
        val terminalLf = source.endsWith('\n')
        val withoutFinalLf = source.removeSuffix("\n")
        val directiveStart = withoutFinalLf.lastIndexOf('\n') + 1
        val mapArchivePath = toArchiveOutputPath("$compilerPath.map")
        val parentDepth = moduleArchivePath.substringBeforeLast('/').split('/').size
        val relativeMapPath = encodeRelativeArchiveUrl(
            "../".repeat(parentDepth) + mapArchivePath,
        )
        return buildString(source.length + relativeMapPath.length) {
            append(withoutFinalLf, 0, directiveStart)
            append("//# sourceMappingURL=")
            append(relativeMapPath)
            if (terminalLf) append('\n')
        }
    }

    private fun encodeRelativeArchiveUrl(path: String): String {
        return percentEncodeUtf8(
            value = path,
            label = "Relative Source Map archive URL",
        ) { value -> isUriUnreserved(value) || value == '/'.code }
    }

    /** Mirrors TypeScript's `encodeURI(sourceMapFile)` terminal directive exactly. */
    private fun encodeTypeScriptSourceMappingUrl(fileName: String): String =
        percentEncodeUtf8(
            value = fileName,
            label = "TypeScript Source Map URL",
            isAllowed = ::isEncodeUriByte,
        )

    private fun percentEncodeUtf8(
        value: String,
        label: String,
        isAllowed: (Int) -> Boolean,
    ): String {
        val bytes = R2Utf8.encode(value, label)
        return try {
            buildString(bytes.size) {
                bytes.forEach { byte ->
                    val byteValue = byte.toInt() and 0xff
                    if (isAllowed(byteValue)) {
                        append(byteValue.toChar())
                    } else {
                        append('%')
                        append(HEX_DIGITS[byteValue ushr 4])
                        append(HEX_DIGITS[byteValue and 0x0f])
                    }
                }
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun isUriUnreserved(value: Int): Boolean =
        value in 'a'.code..'z'.code ||
            value in 'A'.code..'Z'.code ||
            value in '0'.code..'9'.code ||
            value == '-'.code ||
            value == '.'.code ||
            value == '_'.code ||
            value == '~'.code

    private fun isEncodeUriByte(value: Int): Boolean =
        isUriUnreserved(value) ||
            value.toChar() in TYPE_SCRIPT_ENCODE_URI_EXTRA_SAFE

    private fun resolveSourceMapSource(
        mapPath: String,
        source: String,
        profileAuthority: R2CompilationProfileAuthority,
    ): String {
        if (source.isEmpty() || source.startsWith('/') || '\\' in source) {
            fail("Compiler Source Map contains an invalid source reference.")
        }
        val resolved = ArrayList<String>()
        resolved += mapPath.removePrefix("/").split('/').dropLast(1)
        source.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> {
                    if (resolved.isEmpty()) {
                        fail("Compiler Source Map source escapes the project VFS.")
                    }
                    resolved.removeAt(resolved.lastIndex)
                }
                else -> {
                    if (!R2UnicodePathPolicy.isSafeSegment(segment)) {
                        fail("Compiler Source Map contains a non-canonical source.")
                    }
                    resolved += segment
                }
            }
        }
        return "/${resolved.joinToString("/")}".also { path ->
            if (!path.isCanonicalProjectSourcePath(profileAuthority)) {
                fail("Compiler Source Map resolves outside project sources.")
            }
        }
    }

    private fun isSupportedDiagnosticArchivePath(
        path: String,
        profileAuthority: R2CompilationProfileAuthority,
        phase: R2ProjectDiagnosticPhase,
    ): Boolean = when {
        path.startsWith("sources/") ->
            R2ProjectSourceKindAdmission.sourcePathRejection(
                path,
                profileAuthority,
            ) == null
        path.startsWith("declarations/") -> path.endsWith(".d.ts")
        phase == R2ProjectDiagnosticPhase.RESOLUTION && path.startsWith("packages/") ->
            path.endsWith(".ts") ||
                profileAuthority ==
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 &&
                (
                    path.endsWith(".mts") ||
                        path.endsWith(".cts") ||
                        path.endsWith(".d.mts") ||
                        path.endsWith(".d.cts")
                    )
        else -> false
    }

    private fun String.isCanonicalProjectSourcePath(
        profileAuthority: R2CompilationProfileAuthority,
    ): Boolean =
        R2ProjectSourceKindAdmission.isExecutableSourcePath(this, profileAuthority) &&
            removePrefix("/").isCanonicalArchivePath()

    private fun String.expectedCompilerOutputPath(): String =
        "/outputs/" +
            removePrefix("/sources/")
                .removeSuffix(if (endsWith(".tsx")) ".tsx" else ".ts") +
            ".js"

    private fun String.isCanonicalCompilerOutputPath(
        profileAuthority: R2CompilationProfileAuthority,
    ): Boolean {
        if (!startsWith("/outputs/") || !removePrefix("/").isCanonicalArchivePath()) {
            return false
        }
        val modulePath = removeSuffix(".map")
        return modulePath.endsWith(".js") ||
            profileAuthority ==
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 &&
            (modulePath.endsWith(".mjs") || modulePath.endsWith(".cjs"))
    }

    private fun diagnosticsJson(
        authority: R2OutputArchiveAuthority,
        compilation: R2ProjectCompilation,
    ): ByteArray {
        val diagnostics = compilation.diagnostics.sortedWith(
            compareBy<R2ProjectDiagnostic>(
                { diagnostic -> diagnostic.phase.ordinal },
                { diagnostic -> diagnostic.sourcePath.orEmpty() },
                { diagnostic -> diagnostic.start ?: -1 },
                { diagnostic -> diagnostic.length ?: -1 },
                { diagnostic -> diagnostic.line ?: -1 },
                { diagnostic -> diagnostic.column ?: -1 },
                R2ProjectDiagnostic::category,
                R2ProjectDiagnostic::code,
                R2ProjectDiagnostic::message,
            ),
        )
        val relatedInformationCount = diagnostics.sumOf { diagnostic ->
            diagnostic.relatedInformation.size
        }
        R2DiagnosticsSchemaV1.requireCounts(
            diagnosticCount = diagnostics.size,
            relatedInformationCount = relatedInformationCount,
        )
        val json = buildString {
            append('{')
            appendJsonField("schema", FORMAL_DIAGNOSTICS_SCHEMA)
            append(',')
            appendJsonField("revision", FORMAL_DIAGNOSTICS_REVISION)
            append(',')
            appendJsonField(
                "diagnosticsQuotaRevision",
                R2DiagnosticsSchemaV1.QUOTA_REVISION,
            )
            append(',')
            appendJsonField("requestId", authority.requestId)
            append(',')
            appendJsonField("projectId", authority.projectId)
            append(',')
            appendJsonField("targetProfile", authority.profile.contractValue)
            append(',')
            appendJsonField(
                "profileSchemaRevision",
                authority.profileAuthority.profileSchemaRevision,
            )
            append(',')
            appendJsonField(
                "profileRevision",
                authority.profileAuthority.profileRevision,
            )
            append(',')
            appendJsonField(
                "configSchemaRevision",
                authority.configSchemaRevision,
            )
            append(',')
            appendJsonField("configKind", authority.configKind)
            append(',')
            appendJsonField("positionEncoding", R2DiagnosticsSchemaV1.POSITION_ENCODING)
            append(',')
            appendJsonField("lineBase", R2DiagnosticsSchemaV1.LINE_BASE)
            append(',')
            appendJsonField("columnBase", R2DiagnosticsSchemaV1.COLUMN_BASE)
            append(',')
            appendJsonField("diagnosticCount", diagnostics.size)
            append(',')
            appendJsonField("relatedInformationCount", relatedInformationCount)
            append(',')
            appendJsonString("diagnostics")
            append(':')
            append('[')
            diagnostics.forEachIndexed { index, diagnostic ->
                if (index > 0) append(',')
                append('{')
                appendJsonField("phase", diagnostic.phase.protocolValue)
                append(',')
                appendJsonNullableField(
                    "uri",
                    diagnostic.sourcePath?.let { sourcePath ->
                        requireDiagnosticSourceUri(authority.projectId, sourcePath)
                    },
                )
                append(',')
                appendJsonString("range")
                append(':')
                appendDiagnosticRange(
                    start = diagnostic.start,
                    length = diagnostic.length,
                    line = diagnostic.line,
                    column = diagnostic.column,
                )
                append(',')
                appendJsonField("category", diagnostic.category)
                append(',')
                appendJsonField("code", diagnostic.code)
                append(',')
                appendJsonField("message", diagnostic.message)
                append(',')
                appendJsonString("relatedInformation")
                append(':')
                append('[')
                diagnostic.relatedInformation.sortedWith(
                    compareBy<R2ProjectRelatedInformation>(
                        { related -> related.sourcePath.orEmpty() },
                        { related -> related.start ?: -1 },
                        { related -> related.length ?: -1 },
                        { related -> related.line ?: -1 },
                        { related -> related.column ?: -1 },
                        R2ProjectRelatedInformation::category,
                        R2ProjectRelatedInformation::code,
                        R2ProjectRelatedInformation::message,
                    ),
                ).forEachIndexed { relatedIndex, related ->
                    if (relatedIndex > 0) append(',')
                    append('{')
                    appendJsonNullableField(
                        "uri",
                        related.sourcePath?.let { sourcePath ->
                            requireDiagnosticSourceUri(authority.projectId, sourcePath)
                        },
                    )
                    append(',')
                    appendJsonString("range")
                    append(':')
                    appendDiagnosticRange(
                        start = related.start,
                        length = related.length,
                        line = related.line,
                        column = related.column,
                    )
                    append(',')
                    appendJsonField("category", related.category)
                    append(',')
                    appendJsonField("code", related.code)
                    append(',')
                    appendJsonField("message", related.message)
                    append('}')
                }
                append(']')
                diagnostic.nodeResolution?.let { evidence ->
                    append(',')
                    appendJsonString("nodeResolution")
                    append(':')
                    append('{')
                    appendJsonField("policyCode", evidence.policyCode.contractValue)
                    append('}')
                }
                append('}')
            }
            append(']')
            append('}')
        }
        return R2Utf8.encode(json, "Formal diagnostics JSON")
    }

    private fun StringBuilder.appendDiagnosticRange(
        start: Int?,
        length: Int?,
        line: Int?,
        column: Int?,
    ) {
        if (start == null) {
            append("null")
            return
        }
        append('{')
        appendJsonField("start", start)
        append(',')
        appendJsonField("length", requireNotNull(length))
        append(',')
        appendJsonField("line", requireNotNull(line))
        append(',')
        appendJsonField("column", requireNotNull(column))
        append('}')
    }

    private fun manifestJson(
        authority: R2OutputArchiveAuthority,
        compilation: R2ProjectCompilation,
        outcome: String,
        files: List<R2OutputArchiveFile>,
    ): ByteArray {
        val nodeResultAuthority = compilation.nodeRevisionTwoResultAuthority
        val entryModulePath =
            if (outcome == TypeScriptCompilerContract.OUTCOME_EMITTED) {
                nodeResultAuthority
                    ?.generatedModuleMappings
                    ?.singleOrNull { mapping ->
                        mapping.sourcePath == "/${authority.entryPath}"
                    }
                    ?.generatedPath
                    ?.let(::toArchiveOutputPath)
                    ?: (
                        "modules/" +
                            authority.entryPath
                                .removePrefix("sources/")
                                .removeSuffix(
                                    if (authority.entryPath.endsWith(".tsx")) ".tsx" else ".ts",
                                ) +
                            ".js"
                        )
            } else {
                null
            }
        if (entryModulePath != null && files.none { file -> file.path == entryModulePath }) {
            fail("Emitted output does not contain the accepted entry module.")
        }
        val json = buildString {
            append('{')
            appendJsonField("schema", FORMAL_MANIFEST_SCHEMA)
            append(',')
            appendJsonField("revision", FORMAL_MANIFEST_REVISION)
            append(',')
            appendJsonField(
                "outputQuotaRevision",
                R2OutputArchiveSchemaV1.OUTPUT_QUOTA_REVISION,
            )
            append(',')
            appendJsonField("contractVersion", TypeScriptCompilerContract.VERSION)
            append(',')
            appendJsonField("contractHash", TypeScriptCompilerContract.HASH)
            append(',')
            appendJsonField(
                "outputArchiveTransportVersion",
                TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION,
            )
            append(',')
            appendJsonField("requestId", authority.requestId)
            append(',')
            appendJsonField("projectId", authority.projectId)
            append(',')
            appendJsonField("entrySourceUri", authority.entrySourceUri)
            append(',')
            appendJsonNullableField("entryModulePath", entryModulePath)
            append(',')
            appendJsonField("targetProfile", authority.profile.contractValue)
            append(',')
            appendJsonField(
                "profileSchemaRevision",
                authority.profileAuthority.profileSchemaRevision,
            )
            append(',')
            appendJsonField(
                "profileRevision",
                authority.profileAuthority.profileRevision,
            )
            append(',')
            appendJsonField(
                "configSchemaRevision",
                authority.configSchemaRevision,
            )
            append(',')
            appendJsonField("configKind", authority.configKind)
            append(',')
            appendJsonField(
                "runtimeCapabilitySchemaRevision",
                TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION,
            )
            append(',')
            appendJsonField("compilationMode", authority.compilationMode)
            append(',')
            appendJsonField("compilerApi", compilation.compilerApi)
            append(',')
            appendJsonField("compilerPackage", "typescript")
            append(',')
            appendJsonField("compilerVersion", compilation.compilerVersion)
            append(',')
            appendJsonField(
                "compilerDistributionFingerprint",
                authority.compilerDistributionFingerprint,
            )
            append(',')
            appendJsonField(
                "declarationsFingerprint",
                authority.declarationsFingerprint,
            )
            append(',')
            appendJsonField(
                "runtimeCapabilitiesFingerprint",
                authority.runtimeCapabilitiesFingerprint,
            )
            append(',')
            appendJsonField("inputArchiveSha256", authority.inputArchiveSha256)
            append(',')
            appendJsonField(
                "requestManifestSha256",
                authority.requestManifestSha256,
            )
            append(',')
            appendJsonField(
                "inputArchiveSchemaRevision",
                authority.inputArchiveSchemaRevision,
            )
            append(',')
            appendJsonField("inputQuotaRevision", authority.inputQuotaRevision)
            append(',')
            appendJsonField(
                "callerInventoryFingerprint",
                authority.callerInventoryFingerprint,
            )
            append(',')
            appendJsonField(
                "retainedInventoryFingerprint",
                authority.retainedInventoryFingerprint,
            )
            append(',')
            appendJsonField(
                "declarationProfileFingerprint",
                authority.declarationProfileFingerprint,
            )
            append(',')
            appendJsonField(
                "declarationInventoryFingerprint",
                authority.declarationInventoryFingerprint,
            )
            append(',')
            appendJsonField("declarationFileCount", authority.declarationFileCount)
            append(',')
            appendJsonField("declarationByteLength", authority.declarationByteLength)
            append(',')
            appendJsonField("retainedFileCount", authority.retainedFileCount)
            append(',')
            appendJsonField("retainedByteLength", authority.retainedByteLength)
            append(',')
            appendJsonField(
                "largestRetainedFileBytes",
                authority.largestRetainedFileBytes,
            )
            append(',')
            appendJsonField("outcome", outcome)
            append(',')
            appendJsonField("emitSkipped", compilation.emitSkipped)
            append(',')
            appendJsonField("diagnosticCount", compilation.diagnostics.size)
            append(',')
            appendJsonField(
                "emittedModuleCount",
                compilation.executableModules.size,
            )
            append(',')
            appendJsonField("diagnosticsPath", DIAGNOSTICS_PATH)
            append(',')
            appendJsonString("effectiveOptions")
            append(':')
            append(
                compilation.controlledEffectiveOptions?.canonicalJson
                    ?: authority.profileAuthority.canonicalEffectiveOptionsJson,
            )
            append(',')
            appendJsonField(
                "effectiveOptionsFingerprint",
                compilation.controlledEffectiveOptions?.fingerprint
                    ?: authority.profileAuthority.effectiveOptionsFingerprint,
            )
            nodeResultAuthority?.let { resultAuthority ->
                append(',')
                appendJsonField(
                    "nodeModuleOutputPolicySchemaRevision",
                    resultAuthority.nodeModuleOutputPolicySchemaRevision,
                )
                append(',')
                appendJsonField(
                    "nodeModuleOutputPolicyRevision",
                    resultAuthority.nodeModuleOutputPolicyRevision,
                )
                append(',')
                appendJsonField(
                    "nodeModuleOutputPolicyFingerprint",
                    resultAuthority.nodeModuleOutputPolicyFingerprint,
                )
                append(',')
                appendJsonField(
                    "generatedMappingSchema",
                    resultAuthority.generatedMappingSchema,
                )
                append(',')
                appendJsonField(
                    "generatedMappingSchemaRevision",
                    resultAuthority.generatedMappingSchemaRevision,
                )
                append(',')
                appendJsonField(
                    "generatedMappingPolicyRevision",
                    resultAuthority.generatedMappingPolicyRevision,
                )
                append(',')
                appendJsonField(
                    "resolverPolicyRevision",
                    resultAuthority.resolverPolicyRevision,
                )
                append(',')
                appendJsonField(
                    "snapshotTargetInventoryFingerprint",
                    resultAuthority.snapshotTargetInventoryFingerprint,
                )
                append(',')
                appendJsonNullableField(
                    "packageMetadataInventoryFingerprint",
                    resultAuthority.packageMetadataInventoryFingerprint,
                )
                append(',')
                appendJsonNullableField(
                    "packageFormatCatalogFingerprint",
                    resultAuthority.packageFormatCatalogFingerprint,
                )
                append(',')
                appendJsonNullableField(
                    "packageTargetInventoryFingerprint",
                    resultAuthority.packageTargetInventoryFingerprint,
                )
                append(',')
                appendJsonField(
                    "targetUniverseFingerprint",
                    resultAuthority.targetUniverseFingerprint,
                )
                append(',')
                appendJsonField("resultSnapshotEntryPath", resultAuthority.entryPath)
                append(',')
                appendJsonString("resultSnapshotRootNames")
                append(':')
                append('[')
                resultAuthority.rootNames.forEachIndexed { index, rootName ->
                    if (index > 0) append(',')
                    appendJsonString(rootName)
                }
                append(']')
                append(',')
                appendJsonString("generatedModuleMappings")
                append(':')
                append('[')
                resultAuthority.generatedModuleMappings.forEachIndexed { index, mapping ->
                    if (index > 0) append(',')
                    append('{')
                    appendJsonField("sourcePath", mapping.sourcePath)
                    append(',')
                    appendJsonField("projectNamespace", mapping.projectNamespace)
                    append(',')
                    appendJsonField("originalSourceUri", mapping.originalSourceUri)
                    append(',')
                    appendJsonField("moduleFormat", mapping.moduleFormat.contractValue)
                    append(',')
                    appendJsonField("outputExtension", mapping.outputExtension)
                    append(',')
                    appendJsonField("generatedPath", mapping.generatedPath)
                    append(',')
                    appendJsonField("sourceMapPath", mapping.sourceMapPath)
                    append(',')
                    appendJsonField("generatedUri", mapping.generatedUri)
                    append(',')
                    appendJsonField("canonicalEvidence", mapping.canonicalEvidence)
                    append(',')
                    appendJsonField("fingerprint", mapping.fingerprint)
                    append('}')
                }
                append(']')
            }
            append(',')
            appendJsonField(
                "moduleInventoryFingerprint",
                R2OutputArchiveSchemaV1.moduleInventoryFingerprint(files),
            )
            append(',')
            appendJsonField(
                "artifactInventoryFingerprint",
                R2OutputArchiveSchemaV1.artifactInventoryFingerprint(files),
            )
            append(',')
            appendJsonString("files")
            append(':')
            append('[')
            files.forEachIndexed { index, file ->
                if (index > 0) append(',')
                append('{')
                appendJsonField("path", file.path)
                append(',')
                appendJsonField("kind", file.kind)
                append(',')
                appendJsonField("size", file.size)
                append(',')
                appendJsonField("sha256", file.sha256)
                append(',')
                appendJsonString("sourcePaths")
                append(':')
                append('[')
                file.sourcePaths.forEachIndexed { sourceIndex, sourcePath ->
                    if (sourceIndex > 0) append(',')
                    appendJsonString(sourcePath)
                }
                append(']')
                append('}')
            }
            append(']')
            append('}')
        }
        return R2Utf8.encode(json, "Formal compilation manifest")
    }

    private fun requireStrictOutputInventory(entries: List<OutputEntry>) {
        val paths = HashSet<String>(entries.size)
        val foldedPaths = HashSet<String>(entries.size)
        entries.forEach { entry ->
            if (
                !entry.path.isCanonicalArchivePath() ||
                !paths.add(entry.path) ||
                !foldedPaths.add(R2UnicodePathPolicy.asciiCaseFold(entry.path))
            ) {
                fail("Formal output contains a non-canonical or colliding path.")
            }
            if (
                entry.sourcePaths != entry.sourcePaths.distinct() ||
                entry.sourcePaths.any { sourcePath ->
                    !sourcePath.isCanonicalArchivePath() ||
                        (
                            !sourcePath.startsWith("sources/") &&
                                !sourcePath.startsWith("declarations/") &&
                                (
                                    entry.kind != KIND_DIAGNOSTICS ||
                                        !sourcePath.startsWith("packages/")
                                    )
                            )
                }
            ) {
                fail("Formal output contains an invalid source inventory.")
            }
        }
    }

    private fun requireOrdinaryEmittedArtifactQuota(entries: List<OutputEntry>) {
        val artifacts = entries.mapNotNull { entry ->
            when (entry.kind) {
                KIND_JAVASCRIPT -> R2OrdinaryOutputArtifact(
                    kind = R2OrdinaryOutputArtifactKind.JAVASCRIPT,
                    size = entry.bytes.size.toLong(),
                )
                KIND_SOURCE_MAP -> R2OrdinaryOutputArtifact(
                    kind = R2OrdinaryOutputArtifactKind.SOURCE_MAP,
                    size = entry.bytes.size.toLong(),
                )
                else -> null
            }
        }
        val admission = R2OrdinaryOutputQuota.admission(artifacts)
        if (!admission.eligible) {
            fail(
                "Private output exceeds the ordinary compilation limit: " +
                    admission.exceededDimension?.name?.lowercase(),
            )
        }
    }

    private fun requireFrozenOutputQuotas(entries: List<OutputEntry>) {
        if (entries.size > TypeScriptCompilerContract.MAX_OUTPUT_FILES) {
            fail("Private output exceeds the frozen output file-count limit.")
        }
        var totalBytes = 0L
        entries.forEach { entry ->
            val size = entry.bytes.size.toLong()
            requireKindByteQuota(entry.kind, size)
            if (totalBytes > TypeScriptCompilerContract.MAX_OUTPUT_BYTES - size) {
                fail("Private output exceeds the frozen uncompressed byte limit.")
            }
            totalBytes += size
        }
        val storedMetadataBytes =
            entries.fold(END_OF_CENTRAL_DIRECTORY_BYTES) { total, entry ->
                val pathBytes = R2UnicodePathPolicy.encodedByteCount(
                    entry.path,
                    "Output ZIP entry path",
                )
                total +
                    LOCAL_FILE_HEADER_BYTES +
                    CENTRAL_DIRECTORY_HEADER_BYTES +
                    pathBytes * 2L
            }
        if (storedMetadataBytes > TypeScriptCompilerContract.MAX_ZIP_METADATA_BYTES) {
            fail("Private output exceeds the frozen ZIP metadata limit.")
        }
        val storedContainerBytes = storedMetadataBytes + totalBytes
        if (storedContainerBytes > TypeScriptCompilerContract.MAX_OUTPUT_ARCHIVE_BYTES) {
            fail("Private output exceeds the frozen ZIP container limit.")
        }
    }

    private fun requireKindByteQuota(
        kind: String,
        size: Long,
    ) {
        val maximumBytes = when (kind) {
            KIND_MANIFEST -> TypeScriptCompilerContract.MAX_MANIFEST_BYTES
            KIND_DIAGNOSTICS -> TypeScriptR2Contract.MAX_DIAGNOSTICS_DOCUMENT_BYTES
            KIND_JAVASCRIPT -> TypeScriptCompilerContract.MAX_SINGLE_JAVASCRIPT_BYTES
            KIND_SOURCE_MAP -> TypeScriptCompilerContract.MAX_SINGLE_SOURCE_MAP_BYTES
            else -> fail("Private output contains an unknown entry kind.")
        }
        if (size > maximumBytes) {
            fail("Private output $kind entry exceeds its frozen semantic byte limit.")
        }
    }

    private fun writeStoredZip(
        output: FileOutputStream,
        entries: List<OutputEntry>,
    ) {
        val records = ArrayList<StoredRecord>(entries.size)
        var position = 0L
        entries.forEach { entry ->
            val name = R2Utf8.encode(entry.path, "Output ZIP entry path")
            val flags = if (name.any { byte -> byte.toInt() and 0x80 != 0 }) FLAG_UTF8 else 0
            val crc32 = CRC32().apply { update(entry.bytes) }.value
            val localOffset = position
            output.putU32(LOCAL_FILE_HEADER_SIGNATURE)
            output.putU16(VERSION_STORED)
            output.putU16(flags)
            output.putU16(COMPRESSION_STORED)
            output.putU16(0)
            output.putU16(0)
            output.putU32(crc32)
            output.putU32(entry.bytes.size.toLong())
            output.putU32(entry.bytes.size.toLong())
            output.putU16(name.size)
            output.putU16(0)
            output.write(name)
            output.write(entry.bytes)
            position += LOCAL_FILE_HEADER_BYTES + name.size + entry.bytes.size
            records += StoredRecord(
                entry = entry,
                name = name,
                flags = flags,
                crc32 = crc32,
                localOffset = localOffset,
            )
        }
        val centralOffset = position
        records.forEach { record ->
            output.putU32(CENTRAL_DIRECTORY_SIGNATURE)
            output.putU16(VERSION_MADE_BY)
            output.putU16(VERSION_STORED)
            output.putU16(record.flags)
            output.putU16(COMPRESSION_STORED)
            output.putU16(0)
            output.putU16(0)
            output.putU32(record.crc32)
            output.putU32(record.entry.bytes.size.toLong())
            output.putU32(record.entry.bytes.size.toLong())
            output.putU16(record.name.size)
            output.putU16(0)
            output.putU16(0)
            output.putU16(0)
            output.putU16(0)
            output.putU32(0)
            output.putU32(record.localOffset)
            output.write(record.name)
            position += CENTRAL_DIRECTORY_HEADER_BYTES + record.name.size
        }
        val centralBytes = position - centralOffset
        output.putU32(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
        output.putU16(0)
        output.putU16(0)
        output.putU16(entries.size)
        output.putU16(entries.size)
        output.putU32(centralBytes)
        output.putU32(centralOffset)
        output.putU16(0)
        position += END_OF_CENTRAL_DIRECTORY_BYTES
        if (position > TypeScriptCompilerContract.MAX_OUTPUT_ARCHIVE_BYTES) {
            fail("Private output exceeds the frozen ZIP container limit.")
        }
    }

    private fun FileOutputStream.putU16(value: Int) {
        require(value in 0..0xffff)
        write(value and 0xff)
        write(value ushr 8 and 0xff)
    }

    private fun FileOutputStream.putU32(value: Long) {
        require(value in 0..0xffff_ffffL)
        repeat(4) { index ->
            write((value ushr (index * 8)).toInt() and 0xff)
        }
    }

    private fun StringBuilder.appendJsonField(
        name: String,
        value: String,
    ) {
        appendJsonString(name)
        append(':')
        appendJsonString(value)
    }

    private fun StringBuilder.appendJsonField(
        name: String,
        value: Int,
    ) {
        appendJsonString(name)
        append(':')
        append(value)
    }

    private fun StringBuilder.appendJsonField(
        name: String,
        value: Long,
    ) {
        appendJsonString(name)
        append(':')
        append(value)
    }

    private fun StringBuilder.appendJsonField(
        name: String,
        value: Boolean,
    ) {
        appendJsonString(name)
        append(':')
        append(value)
    }

    private fun StringBuilder.appendJsonNullableField(
        name: String,
        value: String?,
    ) {
        appendJsonString(name)
        append(':')
        if (value == null) append("null") else appendJsonString(value)
    }

    private fun StringBuilder.appendJsonNullableField(
        name: String,
        value: Int?,
    ) {
        appendJsonString(name)
        append(':')
        if (value == null) append("null") else append(value)
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
                else -> {
                    if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
    }

    private fun toArchiveOutputPath(path: String): String {
        val relative = path.removePrefix("/outputs/")
        if (relative == path) {
            fail("Compiler output path cannot be represented in the output archive.")
        }
        val moduleRelative = relative.removeSuffix(".map")
        if (
            !moduleRelative.endsWith(".js") &&
            !moduleRelative.endsWith(".mjs") &&
            !moduleRelative.endsWith(".cjs")
        ) {
            fail("Compiler output path cannot be represented in the output archive.")
        }
        return if (relative.endsWith(".map")) {
            "maps/$relative"
        } else {
            "modules/$relative"
        }
    }

    private fun toArchiveDiagnosticSourcePath(
        sourcePath: String,
        phase: R2ProjectDiagnosticPhase,
    ): String {
        val path = sourcePath
        val archivePath = path.removePrefix("/")
        if (
            archivePath == path ||
            (
                !archivePath.startsWith("sources/") &&
                    !archivePath.startsWith("declarations/") &&
                    (
                        phase != R2ProjectDiagnosticPhase.RESOLUTION ||
                            !archivePath.startsWith("packages/")
                        )
                ) ||
            !archivePath.isCanonicalArchivePath()
        ) {
            fail("Compiler diagnostic source path cannot be represented in the output archive.")
        }
        return archivePath
    }

    private fun requireDiagnosticSourceUri(
        projectId: String,
        sourcePath: String,
    ): String = try {
        R2DiagnosticsSchemaV1.sourceUri(projectId, sourcePath)
    } catch (error: IllegalArgumentException) {
        fail("Compiler diagnostic source cannot produce a formal canonical URI.")
    }

    private fun toArchiveSourcePath(path: String): String {
        val archivePath = path.removePrefix("/")
        if (
            archivePath == path ||
            (
                !archivePath.startsWith("sources/") &&
                    !archivePath.startsWith("declarations/")
                ) ||
            !archivePath.isCanonicalArchivePath()
        ) {
            fail("Compiler source path cannot be represented in the output archive.")
        }
        return archivePath
    }

    private fun ByteArray.toLowerHex(): String =
        joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

    private fun fail(message: String): Nothing =
        throw R2OutputArchiveException(
            contractErrorCode = TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT,
            message = message,
        )

    private data class PreparedOutput(
        val outcome: String,
        val outputManifestSha256: String,
        val archiveEntries: List<OutputEntry>,
    )

    private data class OutputEntry(
        val path: String,
        val kind: String,
        val bytes: ByteArray,
        val sourcePaths: List<String>,
    ) {
        fun descriptor(): R2OutputArchiveFile =
            R2OutputArchiveFile(
                path = path,
                kind = kind,
                size = bytes.size.toLong(),
                sha256 = R2Digests.sha256(bytes),
                sourcePaths = sourcePaths.toList(),
            )
    }

    private data class StoredRecord(
        val entry: OutputEntry,
        val name: ByteArray,
        val flags: Int,
        val crc32: Long,
        val localOffset: Long,
    )

    private const val KIND_MANIFEST = "manifest"
    private const val KIND_DIAGNOSTICS = R2OutputArchiveSchemaV1.KIND_DIAGNOSTICS
    private const val KIND_JAVASCRIPT = R2OutputArchiveSchemaV1.KIND_JAVASCRIPT
    private const val KIND_SOURCE_MAP = R2OutputArchiveSchemaV1.KIND_SOURCE_MAP
    private const val TYPESCRIPT_ERROR_CATEGORY =
        TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_ERROR
    private const val COMPRESSION_STORED = 0
    private const val VERSION_STORED = 10
    private const val VERSION_MADE_BY = 20
    private const val FLAG_UTF8 = 1 shl 11
    private const val LOCAL_FILE_HEADER_BYTES = 30L
    private const val CENTRAL_DIRECTORY_HEADER_BYTES = 46L
    private const val END_OF_CENTRAL_DIRECTORY_BYTES = 22L
    private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L
    private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L
    private const val STAGING_FILE_PREFIX = "r2-private-output-"
    private const val STAGING_FILE_SUFFIX = ".zip.staging"
    private val SOURCE_MAP_KEYS = setOf(
        "version",
        "file",
        "sourceRoot",
        "sources",
        "names",
        "mappings",
    )
    private const val TYPE_SCRIPT_ENCODE_URI_EXTRA_SAFE = ";,/?:@&=+$#!*'()"
    private const val HEX_DIGITS = "0123456789ABCDEF"
    private val SUPPORTS_UNLINK_WHILE_OPEN = File.separatorChar != '\\'
}

internal class R2OutputArchiveException(
    val contractErrorCode: String,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

internal class R2PrivateOutputCleanupUnprovenException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

private fun String.isCanonicalArchivePath(): Boolean {
    return R2UnicodePathPolicy.isCanonicalRelativePath(this)
}

private fun digestReader(reader: RandomAccessFile): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val originalOffset = reader.filePointer
    val buffer = ByteArray(64 * 1024)
    try {
        reader.seek(0L)
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    } finally {
        buffer.fill(0)
        reader.seek(originalOffset.coerceAtMost(reader.length()))
    }
}
