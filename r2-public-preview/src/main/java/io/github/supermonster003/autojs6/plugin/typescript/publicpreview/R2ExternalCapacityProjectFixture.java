package io.github.supermonster003.autojs6.plugin.typescript.publicpreview;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract;
import org.autojs.plugin.typescript.api.TypeScriptR2Contract;
import org.json.JSONObject;

/**
 * Constant-memory external-caller fixtures for the four frozen ZIP-v1 input boundaries.
 *
 * <p>Source bodies are deterministic replayable streams. The first pass measures exact
 * uncompressed/compressed sizes, CRC-32, and SHA-256. The second pass writes explicit ZIP32 records
 * and recomputes all four values before fsync. A final read of the written archive binds those
 * measurements to the actual ZIP inventory and proves that a plus-one archive exceeds exactly one
 * production quota while remaining valid against limits relaxed by one byte.
 */
final class R2ExternalCapacityProjectFixture implements AutoCloseable {

    static final String REQUEST_MANIFEST_PATH =
            TypeScriptR2Contract.INPUT_ARCHIVE_MANIFEST_PATH;
    static final String PROJECT_ID = "capacity-project";
    static final String ENTRY_PATH = "sources/main.ts";
    static final String ENTRY_SOURCE_URI =
            "autojs://source/project/main.ts";
    static final String EXPORTED_IDENTIFIER = "capacityValue";

    private static final String COMPILER_PACKAGE = "typescript";
    private static final String CONFIG_KIND_NONE = "none";

    private static final int CAPACITY_PADDING_DECLARATION_COUNT = 8;
    private static final long MAIN_SOURCE_BYTES = 4L * 1024L;
    private static final long INITIAL_MANIFEST_SIZE_ALLOWANCE_BYTES = 4L * 1024L;
    private static final int MAXIMUM_FIXED_POINT_ATTEMPTS = 8;

    private static final int MEDIUM_ENTROPY_BLOCK_BYTES = 8 * 1024;
    private static final String MEDIUM_ENTROPY_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";
    private static final int NON_ZERO_FALLBACK_SEED = 0x13579bdf;
    private static final long CAPACITY_SEED_BASE = 0x6d2b79f5L;
    private static final long CAPACITY_SEED_STEP = 0x9e3779b9L;

    private static final String SHA_256 = "SHA-256";
    private static final int DEFLATE_BUFFER_BYTES = 16 * 1024;
    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    private static final int UINT16_MAX = 0xffff;
    private static final long UINT32_MAX = 0xffff_ffffL;

    private static final long LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L;
    private static final long CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L;
    private static final long END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L;
    private static final long LOCAL_FILE_HEADER_BYTES = 30L;
    private static final long CENTRAL_DIRECTORY_HEADER_BYTES = 46L;
    private static final long END_OF_CENTRAL_DIRECTORY_BYTES = 22L;

    final String requestId;
    final File input;
    final String inputArchiveSha256;
    final String requestManifestSha256;
    final List<SourceDescriptor> sourceDescriptors;
    final Boundary boundary;
    final long containerBytes;
    final long uncompressedBytes;
    final long compressedPayloadBytes;
    final long metadataBytes;
    final long manifestBytes;
    final long sourceBytes;
    final QuotaAxis productionQuotaAxis;
    final String projectId;
    final String entrySourceUri;
    final String compilerDistributionFingerprint;
    final String declarationsFingerprint;
    final String declarationProfileFingerprint;
    final String runtimeCapabilitiesFingerprint;

    private R2ExternalCapacityProjectFixture(
            String requestId,
            File input,
            String inputArchiveSha256,
            String requestManifestSha256,
            List<SourceDescriptor> sourceDescriptors,
            Boundary boundary,
            long containerBytes,
            long uncompressedBytes,
            long compressedPayloadBytes,
            long metadataBytes,
            long manifestBytes,
            QuotaAxis productionQuotaAxis,
            R2FenceBoundedProjectFixture.CompilerAuthority authority) {
        this.requestId = requestId;
        this.input = input;
        this.inputArchiveSha256 = inputArchiveSha256;
        this.requestManifestSha256 = requestManifestSha256;
        this.sourceDescriptors =
                Collections.unmodifiableList(
                        new ArrayList<>(sourceDescriptors));
        this.boundary = boundary;
        this.containerBytes = containerBytes;
        this.uncompressedBytes = uncompressedBytes;
        this.compressedPayloadBytes = compressedPayloadBytes;
        this.metadataBytes = metadataBytes;
        this.manifestBytes = manifestBytes;
        long sourceTotal = 0L;
        for (SourceDescriptor descriptor : sourceDescriptors) {
            sourceTotal = Math.addExact(sourceTotal, descriptor.size);
        }
        this.sourceBytes = sourceTotal;
        this.productionQuotaAxis = productionQuotaAxis;
        this.projectId = PROJECT_ID;
        this.entrySourceUri = ENTRY_SOURCE_URI;
        this.compilerDistributionFingerprint =
                authority.compilerDistributionFingerprint;
        this.declarationsFingerprint = authority.declarationsFingerprint;
        this.declarationProfileFingerprint =
                authority.declarationProfileFingerprint;
        this.runtimeCapabilitiesFingerprint =
                authority.runtimeCapabilitiesFingerprint;
    }

    static R2ExternalCapacityProjectFixture create(
            Context context,
            String requestId,
            Boundary boundary,
            R2FenceBoundedProjectFixture.CompilerAuthority authority)
            throws Exception {
        require(context != null, "R2 capacity fixture context is null.");
        return new Builder(context, requestId, boundary, authority).build();
    }

    static R2ExternalCapacityProjectFixture storedContainerInclusive(
            Context context,
            String requestId,
            R2FenceBoundedProjectFixture.CompilerAuthority authority)
            throws Exception {
        return create(
                context,
                requestId,
                Boundary.STORED_CONTAINER_INCLUSIVE,
                authority);
    }

    static R2ExternalCapacityProjectFixture storedContainerPlusOne(
            Context context,
            String requestId,
            R2FenceBoundedProjectFixture.CompilerAuthority authority)
            throws Exception {
        return create(
                context,
                requestId,
                Boundary.STORED_CONTAINER_PLUS_ONE,
                authority);
    }

    static R2ExternalCapacityProjectFixture deflatedUncompressedInclusive(
            Context context,
            String requestId,
            R2FenceBoundedProjectFixture.CompilerAuthority authority)
            throws Exception {
        return create(
                context,
                requestId,
                Boundary.DEFLATED_UNCOMPRESSED_INCLUSIVE,
                authority);
    }

    static R2ExternalCapacityProjectFixture deflatedUncompressedPlusOne(
            Context context,
            String requestId,
            R2FenceBoundedProjectFixture.CompilerAuthority authority)
            throws Exception {
        return create(
                context,
                requestId,
                Boundary.DEFLATED_UNCOMPRESSED_PLUS_ONE,
                authority);
    }

    String archiveSha256() {
        return inputArchiveSha256;
    }

    String manifestSha256() {
        return requestManifestSha256;
    }

    boolean isProductionInclusive() {
        return boundary.quotaDeltaBytes == 0L;
    }

    @Override
    public void close() {
        deleteExactly(input, "external capacity input fixture");
    }

    enum Boundary {
        STORED_CONTAINER_INCLUSIVE(Compression.STORED, 0L),
        STORED_CONTAINER_PLUS_ONE(Compression.STORED, 1L),
        DEFLATED_UNCOMPRESSED_INCLUSIVE(Compression.DEFLATED, 0L),
        DEFLATED_UNCOMPRESSED_PLUS_ONE(Compression.DEFLATED, 1L);

        final Compression compression;
        final long quotaDeltaBytes;

        Boundary(Compression compression, long quotaDeltaBytes) {
            this.compression = compression;
            this.quotaDeltaBytes = quotaDeltaBytes;
        }

        boolean isPlusOne() {
            return quotaDeltaBytes == 1L;
        }
    }

    enum Compression {
        STORED(0, 10),
        DEFLATED(8, 20);

        final int method;
        final int minimumVersion;

        Compression(int method, int minimumVersion) {
            this.method = method;
            this.minimumVersion = minimumVersion;
        }
    }

    enum QuotaAxis {
        NONE,
        CONTAINER_BYTES,
        UNCOMPRESSED_BYTES
    }

    static final class SourceDescriptor {

        final String path;
        final long size;
        final String sha256;
        final long crc32;
        final long compressedSize;
        final int seed;

        SourceDescriptor(
                String path,
                long size,
                String sha256,
                long crc32,
                long compressedSize,
                int seed) {
            this.path = path;
            this.size = size;
            this.sha256 = sha256;
            this.crc32 = crc32;
            this.compressedSize = compressedSize;
            this.seed = seed;
        }
    }

    private static final class Builder {

        private final Context context;
        private final String requestId;
        private final Boundary boundary;
        private final Compression compression;
        private final R2FenceBoundedProjectFixture.CompilerAuthority authority;
        private final List<SourceSpec> sourceSpecs;

        Builder(
                Context context,
                String requestId,
                Boundary boundary,
                R2FenceBoundedProjectFixture.CompilerAuthority authority) {
            this.context = context;
            this.requestId = requestId;
            this.boundary =
                    requireNonNull(boundary, "R2 capacity boundary is null.");
            this.compression = boundary.compression;
            this.authority =
                    requireNonNull(authority, "R2 compiler authority is null.");
            this.sourceSpecs = capacitySourceSpecs();

            require(
                    requestId != null && !requestId.trim().isEmpty(),
                    "R2 capacity request ID must not be blank.");
            require(
                    requestId.length()
                            <= TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS,
                    "R2 capacity request ID exceeds the contract limit.");
            requireAuthority(authority);

            List<String> paths = new ArrayList<>(sourceSpecs.size());
            for (SourceSpec spec : sourceSpecs) {
                paths.add(spec.path);
            }
            List<String> sorted = new ArrayList<>(paths);
            Collections.sort(sorted);
            require(
                    paths.equals(sorted),
                    "R2 capacity source inventory must be strictly sorted.");
            require(
                    new HashSet<>(paths).size() == paths.size(),
                    "R2 capacity source inventory contains a duplicate path.");
        }

        R2ExternalCapacityProjectFixture build() throws Exception {
            ResolvedSources resolved = resolveSourceBoundary();
            ReplayablePayload manifestPayload =
                    new ByteArrayPayload(resolved.manifest);
            PayloadMetrics manifestMetrics =
                    measurePayload(manifestPayload, compression);

            List<ArchiveEntry> archiveEntries =
                    new ArrayList<>(resolved.sources.size() + 1);
            archiveEntries.add(
                    new ArchiveEntry(
                            REQUEST_MANIFEST_PATH,
                            manifestPayload,
                            manifestMetrics));
            for (SourceEntry source : resolved.sources) {
                archiveEntries.add(
                        new ArchiveEntry(
                                source.spec.path,
                                source.payload,
                                source.metrics));
            }

            long uncompressedBytes = 0L;
            long compressedPayloadBytes = 0L;
            List<String> archivePaths =
                    new ArrayList<>(archiveEntries.size());
            for (ArchiveEntry entry : archiveEntries) {
                uncompressedBytes =
                        Math.addExact(
                                uncompressedBytes,
                                entry.metrics.uncompressedBytes);
                compressedPayloadBytes =
                        Math.addExact(
                                compressedPayloadBytes,
                                entry.metrics.compressedBytes);
                archivePaths.add(entry.path);
            }
            long metadataBytes = zipRecordOverheadBytes(archivePaths);
            long expectedContainerBytes =
                    Math.addExact(compressedPayloadBytes, metadataBytes);
            assertResolvedBoundary(
                    uncompressedBytes, expectedContainerBytes);

            File input =
                    File.createTempFile(
                            "r2-external-capacity-"
                                    + boundary.name().toLowerCase()
                                    + "-",
                            ".archive",
                            context.getCacheDir());
            boolean retained = false;
            try {
                ArchiveEvidence archiveEvidence =
                        writeArchive(
                                input,
                                archiveEntries,
                                compression);
                require(
                        archiveEvidence.containerBytes
                                == expectedContainerBytes,
                        "R2 capacity ZIP bytes differ from the precomputed ZIP32 layout.");
                require(
                        input.length() == expectedContainerBytes,
                        "R2 capacity ZIP file length differs from its written bytes.");
                require(
                        archiveEvidence.sha256.equals(sha256(input)),
                        "R2 capacity ZIP SHA-256 changed after fsync.");

                ArchiveVerification verification =
                        verifyWrittenArchive(
                                input,
                                archiveEntries,
                                compression,
                                archiveEvidence.sha256);
                require(
                        verification.containerBytes
                                == expectedContainerBytes,
                        "Verified R2 capacity container size differs.");
                require(
                        verification.uncompressedBytes
                                == uncompressedBytes,
                        "Verified R2 capacity expanded size differs.");
                require(
                        verification.compressedPayloadBytes
                                == compressedPayloadBytes,
                        "Verified R2 capacity compressed payload differs.");
                require(
                        verification.metadataBytes == metadataBytes,
                        "Verified R2 capacity metadata size differs.");
                QuotaAxis quotaAxis =
                        verifyQuotaBoundary(
                                verification,
                                archiveEntries,
                                manifestMetrics.uncompressedBytes);

                List<SourceDescriptor> descriptors =
                        new ArrayList<>(resolved.sources.size());
                for (SourceEntry source : resolved.sources) {
                    descriptors.add(source.descriptor());
                }
                R2ExternalCapacityProjectFixture fixture =
                        new R2ExternalCapacityProjectFixture(
                                requestId,
                                input,
                                archiveEvidence.sha256,
                                sha256(resolved.manifest),
                                descriptors,
                                boundary,
                                expectedContainerBytes,
                                uncompressedBytes,
                                compressedPayloadBytes,
                                metadataBytes,
                                manifestMetrics.uncompressedBytes,
                                quotaAxis,
                                authority);
                retained = true;
                return fixture;
            } finally {
                if (!retained) {
                    deleteExactly(
                            input,
                            "failed external capacity input fixture");
                }
            }
        }

        private ResolvedSources resolveSourceBoundary()
                throws Exception {
            SourceSpec adjustableSpec = null;
            List<SourceEntry> fixedSources = new ArrayList<>();
            long fixedSourceBytes = 0L;
            for (SourceSpec spec : sourceSpecs) {
                if (spec.fixedSize == null) {
                    require(
                            adjustableSpec == null,
                            "R2 capacity fixture has more than one adjustable source.");
                    adjustableSpec = spec;
                } else {
                    SourceEntry source =
                            sourceEntry(spec, spec.fixedSize);
                    fixedSources.add(source);
                    fixedSourceBytes =
                            Math.addExact(
                                    fixedSourceBytes,
                                    source.metrics.uncompressedBytes);
                }
            }
            require(
                    adjustableSpec != null,
                    "R2 capacity fixture has no adjustable source.");

            long targetPayloadBytes;
            if (compression == Compression.STORED) {
                long targetContainerBytes =
                        Math.addExact(
                                TypeScriptCompilerContract
                                        .MAX_INPUT_ARCHIVE_BYTES,
                                boundary.quotaDeltaBytes);
                List<String> archivePaths =
                        new ArrayList<>(sourceSpecs.size() + 1);
                archivePaths.add(REQUEST_MANIFEST_PATH);
                for (SourceSpec spec : sourceSpecs) {
                    archivePaths.add(spec.path);
                }
                targetPayloadBytes =
                        Math.subtractExact(
                                targetContainerBytes,
                                zipRecordOverheadBytes(archivePaths));
            } else {
                targetPayloadBytes =
                        Math.addExact(
                                TypeScriptCompilerContract.MAX_INPUT_BYTES,
                                boundary.quotaDeltaBytes);
            }

            long candidateSize =
                    Math.subtractExact(
                            TypeScriptCompilerContract
                                    .MAX_SINGLE_FILE_BYTES,
                            INITIAL_MANIFEST_SIZE_ALLOWANCE_BYTES);
            for (int attempt = 0;
                    attempt < MAXIMUM_FIXED_POINT_ATTEMPTS;
                    attempt++) {
                SourceEntry lastSource =
                        sourceEntry(adjustableSpec, candidateSize);
                List<SourceEntry> sources =
                        new ArrayList<>(fixedSources.size() + 1);
                sources.addAll(fixedSources);
                sources.add(lastSource);
                Collections.sort(
                        sources,
                        (left, right) ->
                                left.spec.path.compareTo(
                                        right.spec.path));
                byte[] manifest = renderManifest(sources);
                long requiredSize =
                        Math.subtractExact(
                                Math.subtractExact(
                                        targetPayloadBytes,
                                        fixedSourceBytes),
                                manifest.length);
                require(
                        requiredSize
                                >= lastSource.payload.minimumSize,
                        "R2 capacity boundary leaves no room for a valid final TypeScript source.");
                require(
                        requiredSize
                                <= TypeScriptCompilerContract
                                        .MAX_SINGLE_FILE_BYTES,
                        "R2 capacity boundary requires a source above the single-file limit.");
                if (requiredSize == candidateSize) {
                    return new ResolvedSources(sources, manifest);
                }
                candidateSize = requiredSize;
            }
            throw new AssertionError(
                    "R2 capacity manifest/source-size fixed point did not converge.");
        }

        private SourceEntry sourceEntry(
                SourceSpec spec, long size) throws Exception {
            CapacitySourcePayload payload =
                    new CapacitySourcePayload(spec, size);
            return new SourceEntry(
                    spec,
                    payload,
                    measurePayload(payload, compression));
        }

        private byte[] renderManifest(List<SourceEntry> sources) {
            String inventoryFingerprint =
                    inventoryFingerprint(sources);
            StringBuilder manifest = new StringBuilder(4 * 1024);
            manifest.append("{\"schema\":")
                    .append(JSONObject.quote(TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA))
                    .append(",\"revision\":")
                    .append(TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION)
                    .append(",\"inputQuotaRevision\":")
                    .append(TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION)
                    .append(",\"contractVersion\":")
                    .append(TypeScriptCompilerContract.VERSION)
                    .append(",\"contractHash\":")
                    .append(
                            JSONObject.quote(
                                    TypeScriptCompilerContract.HASH))
                    .append(",\"inputArchiveTransportVersion\":")
                    .append(
                            TypeScriptCompilerContract
                                    .INPUT_ARCHIVE_TRANSPORT_VERSION)
                    .append(",\"requestId\":")
                    .append(JSONObject.quote(requestId))
                    .append(",\"projectId\":")
                    .append(JSONObject.quote(PROJECT_ID))
                    .append(",\"entrySourceUri\":")
                    .append(JSONObject.quote(ENTRY_SOURCE_URI))
                    .append(",\"entryPath\":")
                    .append(JSONObject.quote(ENTRY_PATH))
                    .append(",\"targetProfile\":")
                    .append(
                            JSONObject.quote(
                                    TypeScriptCompilerContract
                                            .PROFILE_RHINO))
                    .append(",\"compilationMode\":")
                    .append(
                            JSONObject.quote(
                                    TypeScriptCompilerContract
                                            .MODE_DEVELOPMENT))
                    .append(",\"profileSchemaRevision\":")
                    .append(
                            TypeScriptCompilerContract
                                    .PROFILE_SCHEMA_REVISION)
                    .append(",\"profileRevision\":")
                    .append(authority.profileRevision)
                    .append(",\"runtimeCapabilitySchemaRevision\":")
                    .append(
                            TypeScriptCompilerContract
                                    .RUNTIME_CAPABILITY_SCHEMA_REVISION)
                    .append(",\"configSchemaRevision\":")
                    .append(authority.configSchemaRevision)
                    .append(",\"runtimeCapabilitiesFingerprint\":")
                    .append(
                            JSONObject.quote(
                                    authority
                                            .runtimeCapabilitiesFingerprint))
                    .append(",\"compilerPackage\":")
                    .append(JSONObject.quote(COMPILER_PACKAGE))
                    .append(",\"compilerVersion\":")
                    .append(
                            JSONObject.quote(
                                    authority.compilerVersion))
                    .append(",\"compilerDistributionFingerprint\":")
                    .append(
                            JSONObject.quote(
                                    authority
                                            .compilerDistributionFingerprint))
                    .append(",\"declarationsFingerprint\":")
                    .append(
                            JSONObject.quote(
                                    authority.declarationsFingerprint))
                    .append(",\"declarationProfileFingerprint\":")
                    .append(
                            JSONObject.quote(
                                    authority.declarationProfileFingerprint))
                    .append(",\"configKind\":")
                    .append(JSONObject.quote(CONFIG_KIND_NONE))
                    .append(",\"callerInventoryFingerprint\":")
                    .append(JSONObject.quote(inventoryFingerprint))
                    .append(",\"rootPaths\":[");

            boolean firstRoot = true;
            for (SourceEntry source : sources) {
                if (source.spec.path.endsWith(".d.ts")) {
                    continue;
                }
                if (!firstRoot) {
                    manifest.append(',');
                }
                firstRoot = false;
                manifest.append(JSONObject.quote(source.spec.path));
            }
            manifest.append("],\"files\":[");
            for (int index = 0; index < sources.size(); index++) {
                if (index > 0) {
                    manifest.append(',');
                }
                SourceEntry source = sources.get(index);
                manifest.append("{\"path\":")
                        .append(JSONObject.quote(source.spec.path))
                        .append(",\"sha256\":")
                        .append(
                                JSONObject.quote(
                                        source.metrics.sha256))
                        .append(",\"size\":")
                        .append(source.metrics.uncompressedBytes)
                        .append('}');
            }
            manifest.append("]}");
            return manifest.toString().getBytes(StandardCharsets.UTF_8);
        }

        private void assertResolvedBoundary(
                long uncompressedBytes,
                long expectedContainerBytes) {
            if (compression == Compression.STORED) {
                long target =
                        Math.addExact(
                                TypeScriptCompilerContract
                                        .MAX_INPUT_ARCHIVE_BYTES,
                                boundary.quotaDeltaBytes);
                require(
                        expectedContainerBytes == target,
                        "STORED R2 capacity fixture does not bind the container boundary.");
                require(
                        uncompressedBytes
                                <= TypeScriptCompilerContract
                                        .MAX_INPUT_BYTES,
                        "STORED R2 capacity fixture unexpectedly exceeds expanded bytes.");
            } else {
                long target =
                        Math.addExact(
                                TypeScriptCompilerContract.MAX_INPUT_BYTES,
                                boundary.quotaDeltaBytes);
                require(
                        uncompressedBytes == target,
                        "DEFLATED R2 capacity fixture does not bind the expanded boundary.");
                require(
                        expectedContainerBytes
                                <= TypeScriptCompilerContract
                                        .MAX_INPUT_ARCHIVE_BYTES,
                        "DEFLATED R2 capacity fixture also exceeds the container boundary.");
            }
        }

        private QuotaAxis verifyQuotaBoundary(
                ArchiveVerification verification,
                List<ArchiveEntry> entries,
                long actualManifestBytes) {
            require(
                    entries.size()
                            <= TypeScriptCompilerContract.MAX_INPUT_FILES,
                    "R2 capacity fixture exceeds the input file-count quota.");
            require(
                    actualManifestBytes > 0L
                            && actualManifestBytes
                                    <= TypeScriptCompilerContract
                                            .MAX_MANIFEST_BYTES,
                    "R2 capacity fixture manifest exceeds its quota.");
            require(
                    verification.metadataBytes
                            <= TypeScriptCompilerContract
                                    .MAX_ZIP_METADATA_BYTES,
                    "R2 capacity fixture metadata exceeds its quota.");
            for (ArchiveEntry entry : entries) {
                require(
                        entry.metrics.uncompressedBytes
                                <= TypeScriptCompilerContract
                                        .MAX_SINGLE_FILE_BYTES,
                        "R2 capacity fixture entry exceeds the single-file quota: "
                                + entry.path);
                enforceCompressionRatio(
                        entry.metrics.compressedBytes,
                        entry.metrics.uncompressedBytes,
                        "R2 capacity fixture entry " + entry.path);
            }
            enforceCompressionRatio(
                    verification.compressedPayloadBytes,
                    verification.uncompressedBytes,
                    "R2 capacity fixture archive");

            long relaxedContainerBytes =
                    Math.addExact(
                            TypeScriptCompilerContract
                                    .MAX_INPUT_ARCHIVE_BYTES,
                            boundary.quotaDeltaBytes);
            long relaxedUncompressedBytes =
                    Math.addExact(
                            TypeScriptCompilerContract.MAX_INPUT_BYTES,
                            boundary.quotaDeltaBytes);
            require(
                    verification.containerBytes <= relaxedContainerBytes,
                    "R2 capacity fixture does not pass its relaxed container limit.");
            require(
                    verification.uncompressedBytes
                            <= relaxedUncompressedBytes,
                    "R2 capacity fixture does not pass its relaxed expanded limit.");

            boolean containerExceeded =
                    verification.containerBytes
                            > TypeScriptCompilerContract
                                    .MAX_INPUT_ARCHIVE_BYTES;
            boolean uncompressedExceeded =
                    verification.uncompressedBytes
                            > TypeScriptCompilerContract.MAX_INPUT_BYTES;
            if (!boundary.isPlusOne()) {
                require(
                        !containerExceeded && !uncompressedExceeded,
                        "Inclusive R2 capacity fixture fails a production byte limit.");
                return QuotaAxis.NONE;
            }
            if (compression == Compression.STORED) {
                require(
                        containerExceeded && !uncompressedExceeded,
                        "STORED plus-one fixture does not isolate the container quota.");
                require(
                        verification.containerBytes
                                == Math.addExact(
                                        TypeScriptCompilerContract
                                                .MAX_INPUT_ARCHIVE_BYTES,
                                        1L),
                        "STORED plus-one fixture is not exactly one container byte over quota.");
                return QuotaAxis.CONTAINER_BYTES;
            }
            require(
                    !containerExceeded && uncompressedExceeded,
                    "DEFLATED plus-one fixture does not isolate the expanded quota.");
            require(
                    verification.uncompressedBytes
                            == Math.addExact(
                                    TypeScriptCompilerContract
                                            .MAX_INPUT_BYTES,
                                    1L),
                    "DEFLATED plus-one fixture is not exactly one expanded byte over quota.");
            return QuotaAxis.UNCOMPRESSED_BYTES;
        }
    }

    private enum CapacitySourceKind {
        MAIN,
        PADDING_DECLARATION
    }

    private static final class SourceSpec {

        final String path;
        final int ordinal;
        final int seed;
        final CapacitySourceKind kind;
        final Long fixedSize;

        SourceSpec(
                String path,
                int ordinal,
                int seed,
                CapacitySourceKind kind,
                Long fixedSize) {
            this.path = path;
            this.ordinal = ordinal;
            this.seed = seed;
            this.kind = kind;
            this.fixedSize = fixedSize;
        }
    }

    private static final class SourceEntry {

        final SourceSpec spec;
        final CapacitySourcePayload payload;
        final PayloadMetrics metrics;

        SourceEntry(
                SourceSpec spec,
                CapacitySourcePayload payload,
                PayloadMetrics metrics) {
            this.spec = spec;
            this.payload = payload;
            this.metrics = metrics;
        }

        SourceDescriptor descriptor() {
            return new SourceDescriptor(
                    spec.path,
                    metrics.uncompressedBytes,
                    metrics.sha256,
                    metrics.crc32,
                    metrics.compressedBytes,
                    spec.seed);
        }
    }

    private static final class ResolvedSources {

        final List<SourceEntry> sources;
        final byte[] manifest;

        ResolvedSources(
                List<SourceEntry> sources, byte[] manifest) {
            this.sources = sources;
            this.manifest = manifest;
        }
    }

    private static final class ArchiveEntry {

        final String path;
        final ReplayablePayload payload;
        final PayloadMetrics metrics;

        ArchiveEntry(
                String path,
                ReplayablePayload payload,
                PayloadMetrics metrics) {
            this.path = path;
            this.payload = payload;
            this.metrics = metrics;
        }
    }

    private static final class CentralRecord {

        final ArchiveEntry entry;
        final byte[] name;
        final long localOffset;

        CentralRecord(
                ArchiveEntry entry, byte[] name, long localOffset) {
            this.entry = entry;
            this.name = name;
            this.localOffset = localOffset;
        }
    }

    private static final class PayloadMetrics {

        final long uncompressedBytes;
        final long compressedBytes;
        final long crc32;
        final String sha256;

        PayloadMetrics(
                long uncompressedBytes,
                long compressedBytes,
                long crc32,
                String sha256) {
            this.uncompressedBytes = uncompressedBytes;
            this.compressedBytes = compressedBytes;
            this.crc32 = crc32;
            this.sha256 = sha256;
        }
    }

    private static final class ArchiveEvidence {

        final long containerBytes;
        final String sha256;

        ArchiveEvidence(long containerBytes, String sha256) {
            this.containerBytes = containerBytes;
            this.sha256 = sha256;
        }
    }

    private static final class ArchiveVerification {

        final long containerBytes;
        final long compressedPayloadBytes;
        final long uncompressedBytes;
        final long metadataBytes;

        ArchiveVerification(
                long containerBytes,
                long compressedPayloadBytes,
                long uncompressedBytes,
                long metadataBytes) {
            this.containerBytes = containerBytes;
            this.compressedPayloadBytes = compressedPayloadBytes;
            this.uncompressedBytes = uncompressedBytes;
            this.metadataBytes = metadataBytes;
        }
    }

    private interface ReplayablePayload {

        long size();

        void replay(ChunkSink sink) throws Exception;
    }

    private interface ChunkSink {

        void write(byte[] bytes, int offset, int length)
                throws Exception;
    }

    /**
     * Exact-size valid TypeScript source.
     *
     * <p>The body is one block comment followed by a tiny declaration or exported constant. Filler
     * omits '*' and '/', so only the suffix can close the comment. Each deterministic 8 KiB
     * xorshift block is repeated once, retaining a moderate raw-DEFLATE ratio.
     */
    private static final class CapacitySourcePayload
            implements ReplayablePayload {

        final SourceSpec spec;
        final long size;
        final byte[] prefix;
        final byte[] suffix;
        final long minimumSize;

        CapacitySourcePayload(SourceSpec spec, long size) {
            this.spec = spec;
            this.size = size;
            if (spec.kind == CapacitySourceKind.MAIN) {
                StringBuilder mainPrefix = new StringBuilder();
                for (int index = 0;
                        index < CAPACITY_PADDING_DECLARATION_COUNT;
                        index++) {
                    mainPrefix.append(
                                    "/// <reference path=\"./padding-")
                            .append(twoDigits(index))
                            .append(".d.ts\" />\n");
                }
                mainPrefix.append(
                        "/* autojs6 deterministic R2 capacity main source\n");
                prefix =
                        mainPrefix.toString()
                                .getBytes(StandardCharsets.US_ASCII);
                suffix =
                        "\n*/\nexport const capacityValue: number = 1;\n"
                                .getBytes(StandardCharsets.US_ASCII);
            } else {
                prefix =
                        ("/* autojs6 deterministic R2 capacity declaration "
                                        + spec.ordinal
                                        + "\n")
                                .getBytes(StandardCharsets.US_ASCII);
                suffix =
                        ("\n*/\nexport declare const capacityDeclaration"
                                        + spec.ordinal
                                        + ": number;\n")
                                .getBytes(StandardCharsets.US_ASCII);
            }
            minimumSize = Math.addExact(prefix.length, suffix.length);
            require(
                    size >= minimumSize,
                    "R2 capacity source is too small for its valid TypeScript envelope.");
            require(
                    size
                            <= TypeScriptCompilerContract
                                    .MAX_SINGLE_FILE_BYTES,
                    "R2 capacity source exceeds the single-file limit.");
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public void replay(ChunkSink sink) throws Exception {
            sink.write(prefix, 0, prefix.length);
            long remaining = size - minimumSize;
            int state =
                    spec.seed != 0
                            ? spec.seed
                            : NON_ZERO_FALLBACK_SEED;
            byte[] uniqueBlock =
                    new byte[MEDIUM_ENTROPY_BLOCK_BYTES];
            try {
                while (remaining > 0L) {
                    int uniqueBytes =
                            (int)
                                    Math.min(
                                            remaining,
                                            uniqueBlock.length);
                    for (int index = 0;
                            index < uniqueBytes;
                            index++) {
                        state ^= state << 13;
                        state ^= state >>> 17;
                        state ^= state << 5;
                        uniqueBlock[index] =
                                (byte)
                                        MEDIUM_ENTROPY_ALPHABET.charAt(
                                                (state >>> 26) & 0x3f);
                    }
                    sink.write(uniqueBlock, 0, uniqueBytes);
                    remaining -= uniqueBytes;
                    if (remaining > 0L) {
                        int repeatedBytes =
                                (int)
                                        Math.min(
                                                remaining,
                                                uniqueBytes);
                        sink.write(
                                uniqueBlock,
                                0,
                                repeatedBytes);
                        remaining -= repeatedBytes;
                    }
                }
            } finally {
                Arrays.fill(uniqueBlock, (byte) 0);
            }
            sink.write(suffix, 0, suffix.length);
        }
    }

    private static final class ByteArrayPayload
            implements ReplayablePayload {

        private final byte[] bytes;

        ByteArrayPayload(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public long size() {
            return bytes.length;
        }

        @Override
        public void replay(ChunkSink sink) throws Exception {
            if (bytes.length > 0) {
                sink.write(bytes, 0, bytes.length);
            }
        }
    }

    private static PayloadMetrics measurePayload(
            ReplayablePayload payload, Compression compression)
            throws Exception {
        CRC32 crc = new CRC32();
        MessageDigest sha256 = sha256Digest();
        CountingValue uncompressedBytes = new CountingValue();
        RawDeflater deflater =
                compression == Compression.DEFLATED
                        ? new RawDeflater(
                                (bytes, offset, length) -> {
                                    // The first pass only counts compressed bytes.
                                })
                        : null;
        long compressedBytes;
        try {
            payload.replay(
                    (bytes, offset, length) -> {
                        requireChunk(bytes, offset, length);
                        crc.update(bytes, offset, length);
                        sha256.update(bytes, offset, length);
                        uncompressedBytes.value =
                                Math.addExact(
                                        uncompressedBytes.value,
                                        length);
                        if (deflater != null) {
                            deflater.write(
                                    bytes, offset, length);
                        }
                    });
            compressedBytes =
                    deflater != null
                            ? deflater.finish()
                            : uncompressedBytes.value;
        } finally {
            if (deflater != null) {
                deflater.close();
            }
        }
        require(
                uncompressedBytes.value == payload.size(),
                "Replayable R2 capacity payload emitted an unexpected byte count.");
        return new PayloadMetrics(
                uncompressedBytes.value,
                compressedBytes,
                crc.getValue(),
                lowerHex(sha256.digest()));
    }

    private static ArchiveEvidence writeArchive(
            File file,
            List<ArchiveEntry> entries,
            Compression compression)
            throws Exception {
        require(
                !entries.isEmpty() && entries.size() <= UINT16_MAX,
                "R2 capacity ZIP entry count is outside ZIP32.");
        MessageDigest archiveDigest = sha256Digest();
        long finalPosition;
        try (FileOutputStream fileOutput =
                new FileOutputStream(file, false)) {
            DigestingCountingOutput output =
                    new DigestingCountingOutput(
                            fileOutput, archiveDigest);
            List<CentralRecord> centralRecords =
                    new ArrayList<>(entries.size());
            for (ArchiveEntry entry : entries) {
                byte[] name = asciiZipName(entry.path);
                long localOffset = output.position;
                output.putU32(LOCAL_FILE_HEADER_SIGNATURE);
                output.putU16(compression.minimumVersion);
                output.putU16(0);
                output.putU16(compression.method);
                output.putU16(0);
                output.putU16(0);
                output.putU32(entry.metrics.crc32);
                output.putU32(entry.metrics.compressedBytes);
                output.putU32(entry.metrics.uncompressedBytes);
                output.putU16(name.length);
                output.putU16(0);
                output.write(name);
                writeAndVerifyPayload(
                        output, entry, compression);
                long expectedEnd =
                        Math.addExact(
                                localOffset,
                                Math.addExact(
                                        LOCAL_FILE_HEADER_BYTES
                                                + name.length,
                                        entry.metrics
                                                .compressedBytes));
                require(
                        output.position == expectedEnd,
                        "R2 capacity ZIP local record has an unexpected length.");
                centralRecords.add(
                        new CentralRecord(
                                entry, name, localOffset));
            }

            long centralOffset = output.position;
            for (CentralRecord record : centralRecords) {
                output.putU32(CENTRAL_DIRECTORY_SIGNATURE);
                output.putU16(compression.minimumVersion);
                output.putU16(compression.minimumVersion);
                output.putU16(0);
                output.putU16(compression.method);
                output.putU16(0);
                output.putU16(0);
                output.putU32(record.entry.metrics.crc32);
                output.putU32(
                        record.entry.metrics.compressedBytes);
                output.putU32(
                        record.entry.metrics.uncompressedBytes);
                output.putU16(record.name.length);
                output.putU16(0);
                output.putU16(0);
                output.putU16(0);
                output.putU16(0);
                output.putU32(0);
                output.putU32(record.localOffset);
                output.write(record.name);
            }
            long centralSize = output.position - centralOffset;
            output.putU32(END_OF_CENTRAL_DIRECTORY_SIGNATURE);
            output.putU16(0);
            output.putU16(0);
            output.putU16(centralRecords.size());
            output.putU16(centralRecords.size());
            output.putU32(centralSize);
            output.putU32(centralOffset);
            output.putU16(0);
            output.flush();
            fileOutput.getFD().sync();
            finalPosition = output.position;
        }
        return new ArchiveEvidence(
                finalPosition,
                lowerHex(archiveDigest.digest()));
    }

    private static void writeAndVerifyPayload(
            OutputStream output,
            ArchiveEntry entry,
            Compression compression)
            throws Exception {
        CRC32 crc = new CRC32();
        MessageDigest sha256 = sha256Digest();
        CountingValue uncompressedBytes = new CountingValue();
        RawDeflater deflater =
                compression == Compression.DEFLATED
                        ? new RawDeflater(output::write)
                        : null;
        long compressedBytes;
        try {
            entry.payload.replay(
                    (bytes, offset, length) -> {
                        requireChunk(bytes, offset, length);
                        crc.update(bytes, offset, length);
                        sha256.update(bytes, offset, length);
                        uncompressedBytes.value =
                                Math.addExact(
                                        uncompressedBytes.value,
                                        length);
                        if (deflater == null) {
                            output.write(
                                    bytes, offset, length);
                        } else {
                            deflater.write(
                                    bytes, offset, length);
                        }
                    });
            compressedBytes =
                    deflater != null
                            ? deflater.finish()
                            : uncompressedBytes.value;
        } finally {
            if (deflater != null) {
                deflater.close();
            }
        }
        require(
                uncompressedBytes.value
                        == entry.metrics.uncompressedBytes,
                "R2 capacity ZIP replay changed the uncompressed byte count for "
                        + entry.path
                        + ".");
        require(
                compressedBytes == entry.metrics.compressedBytes,
                "R2 capacity ZIP replay changed the compressed byte count for "
                        + entry.path
                        + ".");
        require(
                crc.getValue() == entry.metrics.crc32,
                "R2 capacity ZIP replay changed CRC-32 for "
                        + entry.path
                        + ".");
        require(
                lowerHex(sha256.digest())
                        .equals(entry.metrics.sha256),
                "R2 capacity ZIP replay changed SHA-256 for "
                        + entry.path
                        + ".");
    }

    private static ArchiveVerification verifyWrittenArchive(
            File file,
            List<ArchiveEntry> expectedEntries,
            Compression compression,
            String expectedArchiveSha256)
            throws Exception {
        require(
                file.isFile(),
                "R2 capacity ZIP is not a regular file.");
        require(
                expectedArchiveSha256.equals(sha256(file)),
                "R2 capacity ZIP digest changed while verifying.");

        long compressedPayloadBytes = 0L;
        long uncompressedBytes = 0L;
        List<String> expectedNames =
                new ArrayList<>(expectedEntries.size());
        for (ArchiveEntry entry : expectedEntries) {
            expectedNames.add(entry.path);
        }

        try (ZipFile zip = new ZipFile(file)) {
            List<String> actualNames = new ArrayList<>();
            Set<String> uniqueNames = new HashSet<>();
            Enumeration<? extends ZipEntry> enumeration =
                    zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry actual = enumeration.nextElement();
                require(
                        uniqueNames.add(actual.getName()),
                        "R2 capacity ZIP contains a duplicate entry.");
                actualNames.add(actual.getName());
            }
            require(
                    expectedNames.equals(actualNames),
                    "R2 capacity ZIP inventory or order differs: "
                            + actualNames);
            require(
                    zip.getComment() == null
                            || zip.getComment().isEmpty(),
                    "R2 capacity ZIP contains an archive comment.");

            for (ArchiveEntry expected : expectedEntries) {
                ZipEntry actual = zip.getEntry(expected.path);
                require(
                        actual != null && !actual.isDirectory(),
                        "R2 capacity ZIP entry is missing or not regular: "
                                + expected.path);
                require(
                        actual.getMethod() == compression.method,
                        "R2 capacity ZIP compression method differs: "
                                + expected.path);
                require(
                        actual.getSize()
                                == expected.metrics.uncompressedBytes,
                        "R2 capacity ZIP expanded size differs: "
                                + expected.path);
                require(
                        actual.getCompressedSize()
                                == expected.metrics.compressedBytes,
                        "R2 capacity ZIP compressed size differs: "
                                + expected.path);
                require(
                        actual.getCrc() == expected.metrics.crc32,
                        "R2 capacity ZIP CRC-32 differs: "
                                + expected.path);
                require(
                        actual.getExtra() == null
                                || actual.getExtra().length == 0,
                        "R2 capacity ZIP entry contains extra data: "
                                + expected.path);
                require(
                        actual.getComment() == null
                                || actual.getComment().isEmpty(),
                        "R2 capacity ZIP entry contains a comment: "
                                + expected.path);
                StreamEvidence payload;
                try (InputStream input =
                        zip.getInputStream(actual)) {
                    payload = digestStream(input);
                }
                require(
                        payload.bytes
                                == expected.metrics.uncompressedBytes,
                        "R2 capacity ZIP actual expanded bytes differ: "
                                + expected.path);
                require(
                        payload.sha256.equals(expected.metrics.sha256),
                        "R2 capacity ZIP actual SHA-256 differs: "
                                + expected.path);
                uncompressedBytes =
                        Math.addExact(
                                uncompressedBytes,
                                actual.getSize());
                compressedPayloadBytes =
                        Math.addExact(
                                compressedPayloadBytes,
                                actual.getCompressedSize());
            }
        }
        long metadataBytes =
                Math.subtractExact(
                        file.length(), compressedPayloadBytes);
        return new ArchiveVerification(
                file.length(),
                compressedPayloadBytes,
                uncompressedBytes,
                metadataBytes);
    }

    private static final class RawDeflater
            implements AutoCloseable {

        private final ChunkSink sink;
        private final Deflater deflater =
                new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        private final byte[] outputBuffer =
                new byte[DEFLATE_BUFFER_BYTES];
        private long compressedBytes;
        private boolean finishing;
        private boolean closed;

        RawDeflater(ChunkSink sink) {
            this.sink = sink;
        }

        void write(byte[] bytes, int offset, int length)
                throws Exception {
            require(
                    !finishing && !closed,
                    "Raw DEFLATE stream is already finishing or closed.");
            requireChunk(bytes, offset, length);
            if (length == 0) {
                return;
            }
            require(
                    deflater.needsInput(),
                    "Raw DEFLATE stream retained prior input.");
            deflater.setInput(bytes, offset, length);
            while (!deflater.needsInput()) {
                drainOnce();
            }
        }

        long finish() throws Exception {
            require(
                    !finishing && !closed,
                    "Raw DEFLATE stream was finished more than once.");
            finishing = true;
            deflater.finish();
            while (!deflater.finished()) {
                drainOnce();
            }
            return compressedBytes;
        }

        private void drainOnce() throws Exception {
            int count = deflater.deflate(outputBuffer);
            if (count > 0) {
                sink.write(outputBuffer, 0, count);
                compressedBytes =
                        Math.addExact(compressedBytes, count);
                return;
            }
            require(
                    deflater.needsInput() || deflater.finished(),
                    "Raw DEFLATE stream made no progress.");
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            Arrays.fill(outputBuffer, (byte) 0);
            deflater.end();
        }
    }

    private static final class DigestingCountingOutput
            extends OutputStream {

        private final OutputStream delegate;
        private final MessageDigest digest;
        long position;

        DigestingCountingOutput(
                OutputStream delegate, MessageDigest digest) {
            this.delegate = delegate;
            this.digest = digest;
        }

        @Override
        public void write(int value) throws IOException {
            delegate.write(value);
            digest.update((byte) value);
            position = Math.addExact(position, 1L);
        }

        @Override
        public void write(
                byte[] bytes, int offset, int length)
                throws IOException {
            requireChunk(bytes, offset, length);
            delegate.write(bytes, offset, length);
            digest.update(bytes, offset, length);
            position = Math.addExact(position, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        void putU16(int value) throws IOException {
            require(
                    value >= 0 && value <= UINT16_MAX,
                    "ZIP16 field is outside its unsigned range.");
            write(value & 0xff);
            write((value >>> 8) & 0xff);
        }

        void putU32(long value) throws IOException {
            require(
                    value >= 0L && value <= UINT32_MAX,
                    "ZIP32 field is outside its unsigned range.");
            write((int) (value & 0xff));
            write((int) ((value >>> 8) & 0xff));
            write((int) ((value >>> 16) & 0xff));
            write((int) ((value >>> 24) & 0xff));
        }
    }

    private static final class CountingValue {

        long value;
    }

    private static final class StreamEvidence {

        final long bytes;
        final String sha256;

        StreamEvidence(long bytes, String sha256) {
            this.bytes = bytes;
            this.sha256 = sha256;
        }
    }

    private static List<SourceSpec> capacitySourceSpecs() {
        List<SourceSpec> specs =
                new ArrayList<>(
                        CAPACITY_PADDING_DECLARATION_COUNT + 1);
        specs.add(
                new SourceSpec(
                        ENTRY_PATH,
                        0,
                        (int) CAPACITY_SEED_BASE,
                        CapacitySourceKind.MAIN,
                        MAIN_SOURCE_BYTES));
        for (int index = 0;
                index < CAPACITY_PADDING_DECLARATION_COUNT;
                index++) {
            int seed =
                    (int)
                            (CAPACITY_SEED_BASE
                                    ^ ((index + 1L)
                                            * CAPACITY_SEED_STEP));
            Long fixedSize =
                    index + 1
                                    == CAPACITY_PADDING_DECLARATION_COUNT
                            ? null
                            : TypeScriptCompilerContract
                                    .MAX_SINGLE_FILE_BYTES;
            specs.add(
                    new SourceSpec(
                            "sources/padding-"
                                    + twoDigits(index)
                                    + ".d.ts",
                            index,
                            seed,
                            CapacitySourceKind
                                    .PADDING_DECLARATION,
                            fixedSize));
        }
        return specs;
    }

    private static String inventoryFingerprint(
            List<SourceEntry> sources) {
        MessageDigest digest = sha256Digest();
        digest.update(
                "autojs6.typescript.input-inventory.v1\n"
                        .getBytes(StandardCharsets.US_ASCII));
        for (SourceEntry source : sources) {
            digest.update("source".getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(
                    ("/" + source.spec.path).getBytes(
                            StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(
                    Long.toString(
                                    source.metrics.uncompressedBytes)
                            .getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(
                    source.metrics.sha256.getBytes(
                            StandardCharsets.US_ASCII));
            digest.update((byte) '\n');
        }
        return lowerHex(digest.digest());
    }

    private static byte[] asciiZipName(String path) {
        require(
                path != null
                        && !path.isEmpty()
                        && path.length()
                                <= TypeScriptCompilerContract
                                        .MAX_ARCHIVE_ENTRY_NAME_BYTES,
                "R2 capacity ZIP path length is outside its bound.");
        for (int index = 0; index < path.length(); index++) {
            char character = path.charAt(index);
            require(
                    character >= 0x20 && character <= 0x7e,
                    "R2 capacity ZIP path must be bounded printable ASCII.");
        }
        byte[] bytes =
                path.getBytes(StandardCharsets.US_ASCII);
        require(
                path.equals(
                        new String(
                                bytes,
                                StandardCharsets.US_ASCII)),
                "R2 capacity ZIP path is not ASCII.");
        return bytes;
    }

    private static long zipRecordOverheadBytes(
            List<String> paths) {
        require(
                paths != null && !paths.isEmpty(),
                "R2 capacity ZIP has no paths.");
        long total = END_OF_CENTRAL_DIRECTORY_BYTES;
        for (String path : paths) {
            long nameBytes = asciiZipName(path).length;
            total =
                    Math.addExact(
                            total,
                            Math.addExact(
                                    LOCAL_FILE_HEADER_BYTES
                                            + CENTRAL_DIRECTORY_HEADER_BYTES,
                                    Math.multiplyExact(
                                            2L, nameBytes)));
        }
        return total;
    }

    private static void enforceCompressionRatio(
            long compressedBytes,
            long uncompressedBytes,
            String label) {
        if (uncompressedBytes == 0L) {
            return;
        }
        require(
                compressedBytes > 0L,
                label + " has an unbounded compression ratio.");
        long maximumExpanded =
                compressedBytes
                                > Long.MAX_VALUE
                                        / TypeScriptCompilerContract
                                                .MAX_ARCHIVE_COMPRESSION_RATIO
                        ? Long.MAX_VALUE
                        : compressedBytes
                                * TypeScriptCompilerContract
                                        .MAX_ARCHIVE_COMPRESSION_RATIO;
        require(
                uncompressedBytes <= maximumExpanded,
                label
                        + " exceeds compression ratio "
                        + TypeScriptCompilerContract
                                .MAX_ARCHIVE_COMPRESSION_RATIO
                        + ":1.");
    }

    private static StreamEvidence digestStream(InputStream input)
            throws Exception {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
            total = Math.addExact(total, read);
        }
        Arrays.fill(buffer, (byte) 0);
        return new StreamEvidence(
                total, lowerHex(digest.digest()));
    }

    private static String sha256(File file) throws Exception {
        try (InputStream input = new FileInputStream(file)) {
            return digestStream(input).sha256;
        }
    }

    private static String sha256(byte[] bytes) {
        return lowerHex(sha256Digest().digest(bytes));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (Exception error) {
            throw new AssertionError(
                    "SHA-256 is unavailable.", error);
        }
    }

    private static String lowerHex(byte[] bytes) {
        StringBuilder result =
                new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            result.append(
                    Character.forDigit(
                            unsigned >>> 4, 16));
            result.append(
                    Character.forDigit(
                            unsigned & 0x0f, 16));
        }
        return result.toString();
    }

    private static void requireAuthority(
            R2FenceBoundedProjectFixture.CompilerAuthority authority) {
        require(
                authority.profileRevision > 0,
                "R2 capacity profile revision is invalid.");
        require(
                authority.configSchemaRevision > 0,
                "R2 capacity config revision is invalid.");
        require(
                authority.compilerVersion != null
                        && !authority.compilerVersion.isEmpty(),
                "R2 capacity compiler version is empty.");
        requireLowerSha256(
                authority.compilerDistributionFingerprint,
                "R2 capacity compiler distribution fingerprint");
        requireLowerSha256(
                authority.declarationsFingerprint,
                "R2 capacity declarations fingerprint");
        requireLowerSha256(
                authority.declarationProfileFingerprint,
                "R2 capacity declaration profile fingerprint");
        requireLowerSha256(
                authority.runtimeCapabilitiesFingerprint,
                "R2 capacity runtime-capabilities fingerprint");
    }

    private static void requireLowerSha256(
            String value, String label) {
        require(
                value != null && value.length() == 64,
                label + " has an invalid length.");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            require(
                    (character >= '0' && character <= '9')
                            || (character >= 'a'
                                    && character <= 'f'),
                    label + " is not lower-case hexadecimal.");
        }
    }

    private static String twoDigits(int value) {
        require(
                value >= 0 && value <= 99,
                "Two-digit fixture ordinal is outside its bound.");
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    private static void requireChunk(
            byte[] bytes, int offset, int length) {
        require(
                bytes != null
                        && offset >= 0
                        && length >= 0
                        && offset <= bytes.length - length,
                "R2 capacity stream chunk is outside its backing array.");
    }

    private static void deleteExactly(File file, String label) {
        require(
                file != null
                        && (!file.exists() || file.delete()),
                "Could not delete " + label + ".");
        require(
                !file.exists(),
                "Deleted " + label + " still exists.");
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
