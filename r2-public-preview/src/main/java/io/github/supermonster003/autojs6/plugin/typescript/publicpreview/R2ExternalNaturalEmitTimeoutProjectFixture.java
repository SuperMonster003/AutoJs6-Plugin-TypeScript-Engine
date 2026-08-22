package io.github.supermonster003.autojs6.plugin.typescript.publicpreview;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract;
import org.autojs.plugin.typescript.api.TypeScriptR2Contract;
import org.json.JSONObject;

/**
 * Deterministic ordinary-admission projects for external natural emit-, semantic-, and parse-timeout
 * evidence.
 *
 * <p>The primary workload uses 256 executable roots and a bounded optional-chain topology. Sixty-four
 * light roots place emit sequence 2 exactly after their 128 outputs. Each of the remaining 192 roots
 * uses one depth-61 optional chain plus three literal expressions. Each expression is wrapped in 90
 * erased type assertions, keeping AST depth bounded while lengthening real compiler emit and output
 * finalization. There is no distributive resolver workload or expanded admission.
 * There is no phase hook or artificial spin. There is no mutable deadline, capacity override, or retry.
 *
 * <p>{@link #createSemantic} mirrors the internal 256-root plus 148-helper natural semantic
 * workload byte for byte. {@link #createParse} keeps a bounded depth-eight resolver topology and
 * places a finite declaration-reference DAG plus 6,000 of its 15,000 real named type-only imports
 * after parse progress sequence two. {@link
 * #createSuccessor} supplies a separate one-root ordinary project
 * for the same-Binder and same-process post-timeout successor proof. All flavors write canonical
 * STORED ZIP32 archives and bind every caller source through the provisional request manifest.
 */
final class R2ExternalNaturalEmitTimeoutProjectFixture implements AutoCloseable {

    static final String REQUEST_MANIFEST_PATH =
            TypeScriptR2Contract.INPUT_ARCHIVE_MANIFEST_PATH;

    static final String NATURAL_PROJECT_ID =
            "r2-public-preview-external-natural-emit-timeout";
    static final String NATURAL_ENTRY_PATH =
            "sources/natural-emit-timeout-000.ts";
    static final String NATURAL_ENTRY_SOURCE_URI =
            "autojs://source/r2-public-preview-external-natural-emit-timeout/"
                    + "natural-emit-timeout-000.ts";
    static final String NATURAL_EXPORTED_IDENTIFIER = "e000";
    static final String SEMANTIC_PROJECT_ID =
            "r2-public-preview-external-natural-semantic-timeout";
    static final String SEMANTIC_ENTRY_PATH =
            "sources/natural-timeout-phase-000.ts";
    static final String SEMANTIC_ENTRY_SOURCE_URI =
            "autojs://source/r2-public-preview-external-natural-semantic-timeout/"
                    + "natural-timeout-phase-000.ts";
    static final String SEMANTIC_EXPORTED_IDENTIFIER =
            "naturalTimeoutValue000";
    static final String PARSE_PROJECT_ID =
            "r2-public-preview-external-natural-parse-timeout";
    static final String PARSE_ENTRY_PATH =
            "sources/p00/p01/p02/p03/p04/p05/p06/p07/probe-parse-root-000.ts";
    static final String PARSE_ENTRY_SOURCE_URI =
            "autojs://source/r2-public-preview-external-natural-parse-timeout/"
                    + "probe-parse-root-000.ts";
    static final String PARSE_EXPORTED_IDENTIFIER = "probeValue000";
    static final String SUCCESSOR_PROJECT_ID =
            "r2-public-preview-external-natural-emit-successor";
    static final String SUCCESSOR_ENTRY_PATH = "sources/successor.ts";
    static final String SUCCESSOR_ENTRY_SOURCE_URI =
            "autojs://source/r2-public-preview-external-natural-emit-successor/"
                    + "successor.ts";
    static final String SUCCESSOR_EXPORTED_IDENTIFIER = "successor";
    static final String SUCCESSOR_SOURCE_TEXT =
            "export const successor: number = 42;\n";

    static final int ROOT_COUNT = 256;
    static final int LIGHT_ROOT_COUNT = 64;
    static final int HEAVY_ROOT_COUNT = 192;
    static final int OPTIONAL_CHAIN_COUNT_PER_HEAVY_ROOT = 1;
    static final int OPTIONAL_CHAIN_DEPTH = 61;
    static final int TYPE_ASSERTION_GROUP_COUNT_PER_HEAVY_ROOT = 4;
    static final int TYPE_ASSERTION_COUNT_PER_GROUP = 90;

    static final int SEMANTIC_ROOT_COUNT = 256;
    static final int SEMANTIC_LIGHT_ROOT_COUNT = 64;
    static final int SEMANTIC_HELPER_COUNT = 148;
    static final int SEMANTIC_HELPER_ATOM_COUNT = 72;
    static final int SEMANTIC_HELPER_WHITESPACE_BYTES = 24 * 1024;
    static final int SEMANTIC_HEAVY_PRIVATE_FIELD_COUNT = 8;

    static final int PARSE_ROOT_COUNT = 32;
    static final int PARSE_SEQUENCE_TWO_COMPLETED_ROOTS = 8;
    static final int PARSE_FIRST_IMPORT_ROOT_INDEX = 2;
    static final int PARSE_IMPORT_ROOT_COUNT = 3;
    static final int PARSE_IMPORTS_PER_ROOT = 3_000;
    static final int PARSE_PRE_SEQUENCE_MAXIMUM_MODULE_INDEX = 8_999;
    static final int PARSE_GRAPH_TRIGGER_ROOT_INDEX = 8;
    static final int PARSE_POST_SEQUENCE_IMPORT_ROOT_INDEX =
            PARSE_GRAPH_TRIGGER_ROOT_INDEX;
    static final int PARSE_POST_SEQUENCE_IMPORT_COUNT = 6_000;
    static final int PARSE_MAXIMUM_MODULE_INDEX = 14_999;
    static final int PARSE_GRAPH_FILE_COUNT = 371;
    static final int PARSE_GRAPH_ENTRY_INDEX = 370;
    static final int PARSE_GRAPH_EDGE_COUNT = 68_635;
    static final int PARSE_REFERENCE_LINE_BYTES = 50;
    static final int PARSE_SOURCE_DIRECTORY_DEPTH = 8;
    static final int PARSE_SOURCE_PATH_DEPTH = 10;

    static final int TRUSTED_DECLARATION_FILE_COUNT = 108;
    static final long TRUSTED_DECLARATION_BYTES = 3_784_758L;
    static final int TRUSTED_DECLARATION_TOKENS = 86_329;
    static final int TRUSTED_DECLARATION_PATH_CHARACTERS = 3_857;

    static final long NATURAL_ROOT_BYTES = 555_766L;
    static final int NATURAL_ROOT_TOKENS = 168_896;
    static final long NATURAL_MAXIMUM_ROOT_BYTES = 2_884L;
    static final int NATURAL_MAXIMUM_ROOT_TOKENS = 877;
    static final int NATURAL_CALLER_FILE_COUNT = 256;
    static final long NATURAL_CALLER_BYTES = 555_766L;
    static final int NATURAL_TOTAL_FILE_COUNT = 364;
    static final long NATURAL_TOTAL_BYTES = 4_340_524L;
    static final int NATURAL_TOTAL_TOKENS = 255_225;
    static final int NATURAL_TOTAL_PATH_CHARACTERS = 13_073;
    static final int NATURAL_MAXIMUM_FILE_TOKENS = NATURAL_MAXIMUM_ROOT_TOKENS;
    static final long NATURAL_MAXIMUM_FILE_BYTES = NATURAL_MAXIMUM_ROOT_BYTES;
    static final int NATURAL_INPUT_TRANSPORT_FRAME_COUNT = 1_144;
    static final String NATURAL_CALLER_INVENTORY_FINGERPRINT =
            "7cd2119c93657dbf2a8709e67d8045331b1d444f0e3b3e6a3831e1a712ce187e";

    static final long SEMANTIC_ROOT_BYTES = 178_722L;
    static final int SEMANTIC_ROOT_TOKENS = 36_928;
    static final long SEMANTIC_MAXIMUM_ROOT_BYTES = 811L;
    static final int SEMANTIC_MAXIMUM_ROOT_TOKENS = 169;
    static final long SEMANTIC_HELPER_BYTES = 3_763_492L;
    static final int SEMANTIC_HELPER_TOKENS = 28_268;
    static final long SEMANTIC_MAXIMUM_HELPER_BYTES = 25_429L;
    static final int SEMANTIC_MAXIMUM_HELPER_TOKENS = 191;
    static final int SEMANTIC_CALLER_FILE_COUNT = 404;
    static final long SEMANTIC_CALLER_BYTES = 3_942_214L;
    static final int SEMANTIC_CALLER_TOKENS = 65_196;
    static final int SEMANTIC_TOTAL_FILE_COUNT = 512;
    static final long SEMANTIC_TOTAL_BYTES = 7_726_972L;
    static final int SEMANTIC_TOTAL_TOKENS = 151_525;
    static final int SEMANTIC_CALLER_PATH_CHARACTERS = 15_392;
    static final int SEMANTIC_TOTAL_PATH_CHARACTERS = 19_249;
    static final long SEMANTIC_MAXIMUM_FILE_BYTES =
            SEMANTIC_MAXIMUM_HELPER_BYTES;
    static final int SEMANTIC_MAXIMUM_CALLER_FILE_TOKENS =
            SEMANTIC_MAXIMUM_HELPER_TOKENS;
    static final int SEMANTIC_INPUT_TRANSPORT_FRAME_COUNT = 1_588;
    static final String SEMANTIC_CALLER_INVENTORY_FINGERPRINT =
            "460b3b9755e2669fb789c3190d9f6bc14e42c9f6cbe87bbf81cbe0d97bf6b3eb";

    static final long PARSE_ROOT_BYTES = 961_148L;
    static final int PARSE_ROOT_TOKENS = 150_192;
    static final long PARSE_MAXIMUM_ROOT_BYTES = 384_082L;
    static final int PARSE_MAXIMUM_ROOT_TOKENS = 60_006;
    static final long PARSE_AMBIENT_BYTES = 61L;
    static final int PARSE_AMBIENT_TOKENS = 11;
    static final long PARSE_GRAPH_BYTES = 3_431_750L;
    static final int PARSE_GRAPH_TOKENS = 0;
    static final int PARSE_CALLER_FILE_COUNT = 404;
    static final long PARSE_CALLER_BYTES = 4_392_959L;
    static final int PARSE_CALLER_TOKENS = 150_203;
    static final int PARSE_CALLER_PATH_CHARACTERS = 25_486;
    static final int PARSE_TOTAL_FILE_COUNT = 512;
    static final long PARSE_TOTAL_BYTES = 8_177_717L;
    static final int PARSE_TOTAL_TOKENS = 236_532;
    static final int PARSE_TOTAL_PATH_CHARACTERS = 29_343;
    static final int PARSE_MAXIMUM_PATH_CHARACTERS = 65;
    static final int PARSE_INPUT_TRANSPORT_FRAME_COUNT = 1_598;
    static final String PARSE_CALLER_INVENTORY_FINGERPRINT =
            "334b1a3a940a741105c5222ca6c189c72dfb6c0ea994a72b86013df1fdbf72ef";

    static final int SUCCESSOR_CALLER_FILE_COUNT = 1;
    static final long SUCCESSOR_CALLER_BYTES = 37L;
    static final int SUCCESSOR_CALLER_TOKENS = 8;
    static final int SUCCESSOR_TOTAL_FILE_COUNT = 109;
    static final long SUCCESSOR_TOTAL_BYTES = 3_784_795L;
    static final int SUCCESSOR_TOTAL_TOKENS = 86_337;
    static final int SUCCESSOR_TOTAL_PATH_CHARACTERS = 3_878;
    static final int SUCCESSOR_INPUT_TRANSPORT_FRAME_COUNT = 379;
    static final String SUCCESSOR_CALLER_INVENTORY_FINGERPRINT =
            "180c4c4a7d2667af0edd952be24d733e8f0e75aa7f98f9252128cfd414a58b17";

    static final OutputAuthority NATURAL_OUTPUT_AUTHORITY =
            new OutputAuthority(
                    512,
                    717_302L,
                    259_136L,
                    976_438L,
                    "2b2a4529552aa3a3f9b33cd2a379240bc2852ca065a33ff884a6160847f1b1de");
    static final OutputAuthority SEMANTIC_OUTPUT_AUTHORITY =
            new OutputAuthority(
                    512,
                    535_202L,
                    185_664L,
                    720_866L,
                     "4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1");
    static final OutputAuthority PARSE_OUTPUT_AUTHORITY =
            new OutputAuthority(
                    64,
                    5_962L,
                    7_193L,
                    13_155L,
                    "18f79093d9a09c3111740e282a3ad642576230c142d6b7d7c61bb9c7950ca038");
    static final OutputAuthority SUCCESSOR_OUTPUT_AUTHORITY =
            new OutputAuthority(
                    2,
                    166L,
                    146L,
                    312L,
                    "0b504753f68a345d6aa82eeb174f6d9c63eec3cb21b7810ed09f69e59296be25");

    private static final String COMPILER_PACKAGE = "typescript";
    private static final String CONFIG_KIND_NONE = "none";

    private static final int ORDINARY_MAXIMUM_FILES =
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES;
    private static final long ORDINARY_MAXIMUM_TOTAL_BYTES =
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES;
    private static final long ORDINARY_MAXIMUM_FILE_BYTES =
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES;
    private static final int WORKER_MAXIMUM_PROJECT_TOKENS = 262_144;
    private static final int WORKER_MAXIMUM_TOKENS_PER_FILE = 65_536;
    private static final int INPUT_TRANSPORT_FRAME_BYTES = 64 * 1024;
    private static final long MAXIMUM_OUTPUT_BYTES = 1024L * 1024L;
    private static final long MINIMUM_OUTPUT_HEADROOM_BYTES = 64L * 1024L;

    private static final int STORED_METHOD = 0;
    private static final int ZIP32_VERSION_STORED = 10;
    private static final long UINT32_MAX = 0xffff_ffffL;
    private static final int COPY_BUFFER_BYTES = 64 * 1024;

    final Workload workload;
    final String requestId;
    final File input;
    final File output;
    final String inputArchiveSha256;
    final String requestManifestSha256;
    final String callerInventoryFingerprint;
    final List<SourceAuthority> sourceAuthorities;
    final List<String> rootPaths;
    final String projectId;
    final String entryPath;
    final String entrySourceUri;
    final String exportedIdentifier;
    final long callerBytes;
    final OutputAuthority expectedOutputAuthority;

    private R2ExternalNaturalEmitTimeoutProjectFixture(
            Workload workload,
            String requestId,
            File input,
            File output,
            String inputArchiveSha256,
            String requestManifestSha256,
            String callerInventoryFingerprint,
            List<SourceAuthority> sourceAuthorities,
            List<String> rootPaths,
            String projectId,
            String entryPath,
            String entrySourceUri,
            String exportedIdentifier,
            long callerBytes,
            OutputAuthority expectedOutputAuthority) {
        this.workload = workload;
        this.requestId = requestId;
        this.input = input;
        this.output = output;
        this.inputArchiveSha256 = inputArchiveSha256;
        this.requestManifestSha256 = requestManifestSha256;
        this.callerInventoryFingerprint = callerInventoryFingerprint;
        this.sourceAuthorities =
                Collections.unmodifiableList(new ArrayList<>(sourceAuthorities));
        this.rootPaths = Collections.unmodifiableList(new ArrayList<>(rootPaths));
        this.projectId = projectId;
        this.entryPath = entryPath;
        this.entrySourceUri = entrySourceUri;
        this.exportedIdentifier = exportedIdentifier;
        this.callerBytes = callerBytes;
        this.expectedOutputAuthority = expectedOutputAuthority;
    }

    static R2ExternalNaturalEmitTimeoutProjectFixture create(
            Context context,
            String requestId,
            R2FenceBoundedProjectFixture.CompilerAuthority authority)
            throws Exception {
        return createExact(context, requestId, authority, Workload.NATURAL_EMIT);
    }

    static R2ExternalNaturalEmitTimeoutProjectFixture createSuccessor(
            Context context,
            String requestId,
            R2FenceBoundedProjectFixture.CompilerAuthority authority)
            throws Exception {
        return createExact(context, requestId, authority, Workload.SUCCESSOR);
    }

    static R2ExternalNaturalEmitTimeoutProjectFixture createSemantic(
            Context context,
            String requestId,
            R2FenceBoundedProjectFixture.CompilerAuthority authority)
            throws Exception {
        return createExact(context, requestId, authority, Workload.NATURAL_SEMANTIC);
    }

    static R2ExternalNaturalEmitTimeoutProjectFixture createParse(
            Context context,
            String requestId,
            R2FenceBoundedProjectFixture.CompilerAuthority authority)
            throws Exception {
        return createExact(context, requestId, authority, Workload.NATURAL_PARSE);
    }

    private static R2ExternalNaturalEmitTimeoutProjectFixture createExact(
            Context context,
            String requestId,
            R2FenceBoundedProjectFixture.CompilerAuthority authority,
            Workload workload)
            throws Exception {
        require(context != null, "External natural emit fixture context is null.");
        requireRequestId(requestId);
        requireAuthority(authority);
        require(workload != null, "External natural emit workload is null.");

        List<SourcePayload> sources;
        List<String> rootPaths;
        if (workload == Workload.NATURAL_EMIT) {
            sources = naturalSources();
            rootPaths = naturalRootPaths();
        } else if (workload == Workload.NATURAL_SEMANTIC) {
            sources = semanticSources();
            rootPaths = semanticRootPaths();
        } else if (workload == Workload.NATURAL_PARSE) {
            sources = parseSources();
            rootPaths = parseRootPaths();
        } else {
            sources = successorSources();
            rootPaths = Collections.singletonList(SUCCESSOR_ENTRY_PATH);
        }
        assertSourceAuthority(workload, sources, rootPaths);
        String workloadIdentityFingerprint = workloadIdentityFingerprint(sources);
        String inventoryFingerprint = callerInventoryFingerprint(sources);
        String expectedInventoryFingerprint;
        if (workload == Workload.NATURAL_EMIT) {
            expectedInventoryFingerprint = NATURAL_CALLER_INVENTORY_FINGERPRINT;
        } else if (workload == Workload.NATURAL_SEMANTIC) {
            expectedInventoryFingerprint = SEMANTIC_CALLER_INVENTORY_FINGERPRINT;
        } else if (workload == Workload.NATURAL_PARSE) {
            expectedInventoryFingerprint = PARSE_CALLER_INVENTORY_FINGERPRINT;
        } else {
            expectedInventoryFingerprint = SUCCESSOR_CALLER_INVENTORY_FINGERPRINT;
        }
        require(
                expectedInventoryFingerprint.equals(workloadIdentityFingerprint),
                "External natural emit caller inventory authority drifted: "
                        + workloadIdentityFingerprint);

        String projectId;
        String entryPath;
        String entrySourceUri;
        String exportedIdentifier;
        OutputAuthority outputAuthority;
        if (workload == Workload.NATURAL_EMIT) {
            projectId = NATURAL_PROJECT_ID;
            entryPath = NATURAL_ENTRY_PATH;
            entrySourceUri = NATURAL_ENTRY_SOURCE_URI;
            exportedIdentifier = NATURAL_EXPORTED_IDENTIFIER;
            outputAuthority = NATURAL_OUTPUT_AUTHORITY;
        } else if (workload == Workload.NATURAL_SEMANTIC) {
            projectId = SEMANTIC_PROJECT_ID;
            entryPath = SEMANTIC_ENTRY_PATH;
            entrySourceUri = SEMANTIC_ENTRY_SOURCE_URI;
            exportedIdentifier = SEMANTIC_EXPORTED_IDENTIFIER;
            outputAuthority = SEMANTIC_OUTPUT_AUTHORITY;
        } else if (workload == Workload.NATURAL_PARSE) {
            projectId = PARSE_PROJECT_ID;
            entryPath = PARSE_ENTRY_PATH;
            entrySourceUri = PARSE_ENTRY_SOURCE_URI;
            exportedIdentifier = PARSE_EXPORTED_IDENTIFIER;
            outputAuthority = PARSE_OUTPUT_AUTHORITY;
        } else {
            projectId = SUCCESSOR_PROJECT_ID;
            entryPath = SUCCESSOR_ENTRY_PATH;
            entrySourceUri = SUCCESSOR_ENTRY_SOURCE_URI;
            exportedIdentifier = SUCCESSOR_EXPORTED_IDENTIFIER;
            outputAuthority = SUCCESSOR_OUTPUT_AUTHORITY;
        }

        byte[] manifest =
                requestManifest(
                        requestId,
                        authority,
                        projectId,
                        entryPath,
                        entrySourceUri,
                        inventoryFingerprint,
                        rootPaths,
                        sources);
        PayloadMetrics manifestMetrics = PayloadMetrics.of(manifest);
        String temporaryPrefix =
                workload == Workload.NATURAL_EMIT
                        ? "r2-external-natural-emit-"
                        : workload == Workload.NATURAL_SEMANTIC
                                ? "r2-external-natural-semantic-"
                                : workload == Workload.NATURAL_PARSE
                                        ? "r2-external-natural-parse-"
                                        : "r2-external-natural-emit-successor-";
        File input =
                File.createTempFile(
                        temporaryPrefix,
                        ".archive",
                        context.getCacheDir());
        File output = null;
        boolean retained = false;
        try {
            List<ArchiveEntry> archiveEntries =
                    new ArrayList<>(sources.size() + 1);
            archiveEntries.add(
                    new ArchiveEntry(
                            REQUEST_MANIFEST_PATH,
                            manifest,
                            manifestMetrics));
            for (SourcePayload source : sources) {
                archiveEntries.add(
                        new ArchiveEntry(
                                source.authority.path,
                                source.bytes,
                                source.metrics));
            }
            String archiveSha256 = writeStoredZip32(input, archiveEntries);
            verifyStoredZip32(input, archiveEntries, archiveSha256);

            output =
                    File.createTempFile(
                            temporaryPrefix + "output-",
                            ".archive",
                            context.getFilesDir());
            truncateAndSync(output);

            List<SourceAuthority> sourceAuthorities =
                    new ArrayList<>(sources.size());
            long callerBytes = 0L;
            for (SourcePayload source : sources) {
                sourceAuthorities.add(source.authority);
                callerBytes =
                        Math.addExact(
                                callerBytes,
                                source.authority.size);
            }
            R2ExternalNaturalEmitTimeoutProjectFixture fixture =
                    new R2ExternalNaturalEmitTimeoutProjectFixture(
                            workload,
                            requestId,
                            input,
                            output,
                            archiveSha256,
                            manifestMetrics.sha256,
                            inventoryFingerprint,
                            sourceAuthorities,
                            rootPaths,
                            projectId,
                            entryPath,
                            entrySourceUri,
                            exportedIdentifier,
                            callerBytes,
                            outputAuthority);
            fixture.assertOutputEmpty();
            retained = true;
            return fixture;
        } finally {
            for (SourcePayload source : sources) {
                Arrays.fill(source.bytes, (byte) 0);
            }
            Arrays.fill(manifest, (byte) 0);
            if (!retained) {
                deleteExactly(input, "failed external natural emit input fixture");
                if (output != null) {
                    deleteExactly(output, "failed external natural emit output fixture");
                }
            }
        }
    }

    boolean isNaturalEmitWorkload() {
        return workload == Workload.NATURAL_EMIT;
    }

    boolean isNaturalSemanticWorkload() {
        return workload == Workload.NATURAL_SEMANTIC;
    }

    boolean isNaturalParseWorkload() {
        return workload == Workload.NATURAL_PARSE;
    }

    void assertOutputEmpty() throws Exception {
        require(output.isFile(), "External natural emit output is not a regular file.");
        require(output.length() == 0L, "External natural emit output is not empty.");
        try (FileInputStream inputStream = new FileInputStream(output)) {
            require(
                    inputStream.read() == -1,
                    "External natural emit output descriptor contains bytes.");
        }
    }

    void deleteOutputExactly() {
        deleteExactly(output, "external natural emit output fixture");
    }

    @Override
    public void close() {
        AssertionError failure = null;
        try {
            deleteExactly(input, "external natural emit input fixture");
        } catch (AssertionError error) {
            failure = error;
        }
        try {
            deleteExactly(output, "external natural emit output fixture");
        } catch (AssertionError error) {
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static List<SourcePayload> naturalSources() {
        require(
                LIGHT_ROOT_COUNT + HEAVY_ROOT_COUNT == ROOT_COUNT,
                "Natural emit root partition drifted.");
        require(
                NATURAL_OUTPUT_AUTHORITY.fileCount == ROOT_COUNT * 2
                        && LIGHT_ROOT_COUNT * 2
                                == (NATURAL_OUTPUT_AUTHORITY.fileCount - 1)
                                                / 4
                                        + 1,
                "Natural emit output/progress topology drifted.");
        List<SourcePayload> sources =
                new ArrayList<>(NATURAL_CALLER_FILE_COUNT);
        for (int index = 0; index < ROOT_COUNT; index++) {
            sources.add(
                    SourcePayload.ascii(
                            naturalSourcePath(index),
                            naturalSourceText(index)));
        }
        Collections.sort(
                sources,
                (left, right) ->
                        left.authority.path.compareTo(
                                right.authority.path));
        return sources;
    }

    private static List<SourcePayload> semanticSources() {
        require(
                SEMANTIC_LIGHT_ROOT_COUNT < SEMANTIC_ROOT_COUNT,
                "Natural semantic root partition drifted.");
        require(
                SEMANTIC_CALLER_FILE_COUNT
                        == SEMANTIC_ROOT_COUNT + SEMANTIC_HELPER_COUNT,
                "Natural semantic caller partition drifted.");
        require(
                SEMANTIC_OUTPUT_AUTHORITY.fileCount
                        == SEMANTIC_ROOT_COUNT * 2,
                "Natural semantic output topology drifted.");
        List<SourcePayload> sources =
                new ArrayList<>(SEMANTIC_CALLER_FILE_COUNT);
        long rootBytes = 0L;
        long maximumRootBytes = 0L;
        for (int index = 0; index < SEMANTIC_ROOT_COUNT; index++) {
            SourcePayload source =
                    SourcePayload.ascii(
                            semanticSourcePath(index),
                            semanticSourceText(index));
            sources.add(source);
            rootBytes = Math.addExact(rootBytes, source.authority.size);
            maximumRootBytes =
                    Math.max(maximumRootBytes, source.authority.size);
        }
        long helperBytes = 0L;
        long maximumHelperBytes = 0L;
        for (int index = 0; index < SEMANTIC_HELPER_COUNT; index++) {
            SourcePayload source =
                    SourcePayload.ascii(
                            semanticHelperPath(index),
                            semanticHelperText(index));
            sources.add(source);
            helperBytes = Math.addExact(helperBytes, source.authority.size);
            maximumHelperBytes =
                    Math.max(maximumHelperBytes, source.authority.size);
        }
        require(
                rootBytes == SEMANTIC_ROOT_BYTES
                        && maximumRootBytes == SEMANTIC_MAXIMUM_ROOT_BYTES,
                "Natural semantic root byte authority drifted.");
        require(
                helperBytes == SEMANTIC_HELPER_BYTES
                        && maximumHelperBytes == SEMANTIC_MAXIMUM_HELPER_BYTES,
                "Natural semantic helper byte authority drifted.");
        Collections.sort(
                sources,
                (left, right) ->
                        left.authority.path.compareTo(
                                right.authority.path));
        return sources;
    }

    private static List<SourcePayload> parseSources() {
        require(
                PARSE_CALLER_FILE_COUNT
                        == PARSE_ROOT_COUNT + 1 + PARSE_GRAPH_FILE_COUNT,
                "Natural parse caller partition drifted.");
        require(
                PARSE_SEQUENCE_TWO_COMPLETED_ROOTS
                                == (PARSE_ROOT_COUNT - 1) / 4 + 1
                        && PARSE_FIRST_IMPORT_ROOT_INDEX + PARSE_IMPORT_ROOT_COUNT
                                < PARSE_SEQUENCE_TWO_COMPLETED_ROOTS
                        && PARSE_PRE_SEQUENCE_MAXIMUM_MODULE_INDEX
                                == PARSE_IMPORT_ROOT_COUNT
                                                * PARSE_IMPORTS_PER_ROOT
                                        - 1
                        && PARSE_GRAPH_TRIGGER_ROOT_INDEX
                                == PARSE_SEQUENCE_TWO_COMPLETED_ROOTS
                        && PARSE_POST_SEQUENCE_IMPORT_ROOT_INDEX
                                == PARSE_GRAPH_TRIGGER_ROOT_INDEX
                        && PARSE_POST_SEQUENCE_IMPORT_COUNT > 0
                        && PARSE_MAXIMUM_MODULE_INDEX
                                == PARSE_PRE_SEQUENCE_MAXIMUM_MODULE_INDEX
                                        + PARSE_POST_SEQUENCE_IMPORT_COUNT
                        && PARSE_GRAPH_ENTRY_INDEX
                                == PARSE_GRAPH_FILE_COUNT - 1,
                "Natural parse progress topology drifted.");
        require(
                PARSE_GRAPH_EDGE_COUNT
                        == PARSE_GRAPH_FILE_COUNT * (PARSE_GRAPH_FILE_COUNT - 1) / 2,
                "Natural parse declaration-reference topology drifted.");
        require(
                PARSE_OUTPUT_AUTHORITY.fileCount == PARSE_ROOT_COUNT * 2,
                "Natural parse output topology drifted.");
        require(
                parseSourceDirectory().split("/").length
                                == PARSE_SOURCE_DIRECTORY_DEPTH + 1
                        && parseSourcePath(0).split("/").length
                                == PARSE_SOURCE_PATH_DEPTH,
                "Natural parse source-directory depth drifted.");

        List<SourcePayload> sources =
                new ArrayList<>(PARSE_CALLER_FILE_COUNT);
        long rootBytes = 0L;
        long maximumRootBytes = 0L;
        for (int index = 0; index < PARSE_ROOT_COUNT; index++) {
            SourcePayload source =
                    SourcePayload.ascii(
                            parseSourcePath(index),
                            parseSourceText(index));
            sources.add(source);
            rootBytes = Math.addExact(rootBytes, source.authority.size);
            maximumRootBytes =
                    Math.max(maximumRootBytes, source.authority.size);
        }
        SourcePayload ambient =
                SourcePayload.ascii(
                        parseAmbientPath(),
                        parseAmbientText());
        sources.add(ambient);

        long graphBytes = 0L;
        int graphEdges = 0;
        for (int index = 0; index < PARSE_GRAPH_FILE_COUNT; index++) {
            SourcePayload source =
                    SourcePayload.ascii(
                            parseGraphPath(index),
                            parseGraphText(index));
            sources.add(source);
            graphBytes = Math.addExact(graphBytes, source.authority.size);
            graphEdges = Math.addExact(graphEdges, index);
        }
        require(
                rootBytes == PARSE_ROOT_BYTES
                        && maximumRootBytes == PARSE_MAXIMUM_ROOT_BYTES,
                "Natural parse root byte authority drifted.");
        require(
                ambient.authority.size == PARSE_AMBIENT_BYTES,
                "Natural parse ambient byte authority drifted.");
        require(
                graphBytes == PARSE_GRAPH_BYTES
                        && graphEdges == PARSE_GRAPH_EDGE_COUNT,
                "Natural parse declaration-reference authority drifted.");
        Collections.sort(
                sources,
                (left, right) ->
                        left.authority.path.compareTo(
                                right.authority.path));
        return sources;
    }

    private static List<SourcePayload> successorSources() {
        return Collections.singletonList(
                SourcePayload.ascii(
                        SUCCESSOR_ENTRY_PATH,
                        SUCCESSOR_SOURCE_TEXT));
    }

    private static List<String> naturalRootPaths() {
        List<String> roots = new ArrayList<>(ROOT_COUNT);
        for (int index = 0; index < ROOT_COUNT; index++) {
            roots.add(naturalSourcePath(index));
        }
        return roots;
    }

    private static List<String> semanticRootPaths() {
        List<String> roots = new ArrayList<>(SEMANTIC_ROOT_COUNT);
        for (int index = 0; index < SEMANTIC_ROOT_COUNT; index++) {
            roots.add(semanticSourcePath(index));
        }
        return roots;
    }

    private static List<String> parseRootPaths() {
        List<String> roots = new ArrayList<>(PARSE_ROOT_COUNT);
        for (int index = 0; index < PARSE_ROOT_COUNT; index++) {
            roots.add(parseSourcePath(index));
        }
        return roots;
    }

    static String naturalSourcePath(int index) {
        require(
                index >= 0 && index < ROOT_COUNT,
                "Natural emit root ordinal is outside its bound.");
        return "sources/natural-emit-timeout-"
                + threeDigits(index)
                + ".ts";
    }

    static String naturalSourceText(int index) {
        require(
                index >= 0 && index < ROOT_COUNT,
                "Natural emit root ordinal is outside its bound.");
        String prefix = threeDigits(index);
        if (index < LIGHT_ROOT_COUNT) {
            return "export const e"
                    + prefix
                    + ": number = "
                    + index
                    + ";\n";
        }
        StringBuilder source = new StringBuilder(16 * 1024);
        source.append("declare const q: any;\n")
                .append("export const e")
                .append(prefix)
                .append(": number = [\n");
        for (int chainIndex = 0;
                chainIndex < OPTIONAL_CHAIN_COUNT_PER_HEAVY_ROOT;
                chainIndex++) {
            for (int groupIndex = 0;
                    groupIndex < TYPE_ASSERTION_GROUP_COUNT_PER_HEAVY_ROOT;
                    groupIndex++) {
                source.append("  (");
                if (groupIndex == 0) {
                    source.append('q');
                    for (int depthIndex = 0;
                            depthIndex < OPTIONAL_CHAIN_DEPTH;
                            depthIndex++) {
                        source.append("?.p")
                                .append(Integer.toString(depthIndex, 36));
                    }
                    source.append(" ?? ").append(chainIndex);
                } else {
                    source.append(groupIndex);
                }
                source.append(')');
                for (int assertionIndex = 0;
                        assertionIndex < TYPE_ASSERTION_COUNT_PER_GROUP;
                        assertionIndex++) {
                    source.append(" as any");
                }
                source.append(",\n");
            }
        }
        return source.append("].length;\n").toString();
    }

    static String semanticSourcePath(int index) {
        require(
                index >= 0 && index < SEMANTIC_ROOT_COUNT,
                "Natural semantic root ordinal is outside its bound.");
        return "sources/natural-timeout-phase-"
                + threeDigits(index)
                + ".ts";
    }

    static String semanticSourceText(int index) {
        require(
                index >= 0 && index < SEMANTIC_ROOT_COUNT,
                "Natural semantic root ordinal is outside its bound.");
        String prefix = threeDigits(index);
        boolean lightRoot = index < SEMANTIC_LIGHT_ROOT_COUNT;
        StringBuilder source = new StringBuilder(1024);
        if (!lightRoot) {
            String helperPrefix = threeDigits(semanticHelperIndex(index));
            source.append("import type { NaturalTimeoutMatrix")
                    .append(helperPrefix)
                    .append(" } from \"./natural-timeout-helper-")
                    .append(helperPrefix)
                    .append("\";\n");
        }
        source.append("export interface NaturalTimeoutRecord")
                .append(prefix)
                .append("<T> { readonly id: number; readonly value: T; }\n")
                .append("export const naturalTimeoutValue")
                .append(prefix)
                .append(": NaturalTimeoutRecord")
                .append(prefix)
                .append("<number> = { id: ")
                .append(index)
                .append(", value: ")
                .append(index + 1)
                .append(" };\n");
        if (lightRoot) {
            source.append("export type NaturalTimeoutAlias")
                    .append(prefix)
                    .append(" = NaturalTimeoutRecord")
                    .append(prefix)
                    .append("<number>;\n");
        } else {
            String helperPrefix = threeDigits(semanticHelperIndex(index));
            source.append("const naturalTimeoutProof")
                    .append(prefix)
                    .append(" = [\"a00\", \"a00\"] satisfies NaturalTimeoutMatrix")
                    .append(helperPrefix)
                    .append(";\n")
                    .append("class NaturalTimeoutEmitter")
                    .append(prefix)
                    .append(" {\n");
            for (int fieldIndex = 0;
                    fieldIndex < SEMANTIC_HEAVY_PRIVATE_FIELD_COUNT;
                    fieldIndex++) {
                source.append("  #v")
                        .append(twoDigits(fieldIndex))
                        .append(" = ")
                        .append(index + fieldIndex)
                        .append(";\n");
            }
            source.append("  value(): number { return ");
            for (int fieldIndex = 0;
                    fieldIndex < SEMANTIC_HEAVY_PRIVATE_FIELD_COUNT;
                    fieldIndex++) {
                if (fieldIndex != 0) {
                    source.append(" + ");
                }
                source.append("this.#v")
                        .append(twoDigits(fieldIndex));
            }
            source.append("; }\n}\n")
                    .append("export const naturalTimeoutEmitterValue")
                    .append(prefix)
                    .append(" = new NaturalTimeoutEmitter")
                    .append(prefix)
                    .append("().value();\n");
        }
        return source.append("export function selectTimeout")
                .append(prefix)
                .append("(item: NaturalTimeoutRecord")
                .append(prefix)
                .append("<number>): number { return item.id + item.value; }\n")
                .toString();
    }

    static String semanticHelperPath(int index) {
        require(
                index >= 0 && index < SEMANTIC_HELPER_COUNT,
                "Natural semantic helper ordinal is outside its bound.");
        return "sources/natural-timeout-helper-"
                + threeDigits(index)
                + ".d.ts";
    }

    static String semanticHelperText(int index) {
        require(
                index >= 0 && index < SEMANTIC_HELPER_COUNT,
                "Natural semantic helper ordinal is outside its bound.");
        String prefix = threeDigits(index);
        StringBuilder source =
                new StringBuilder(SEMANTIC_HELPER_WHITESPACE_BYTES + 1024);
        source.append("export type NaturalTimeoutAtom")
                .append(prefix)
                .append(" = ");
        for (int atomIndex = 0;
                atomIndex < SEMANTIC_HELPER_ATOM_COUNT;
                atomIndex++) {
            if (atomIndex != 0) {
                source.append(" | ");
            }
            source.append('\"')
                    .append('a')
                    .append(twoDigits(atomIndex))
                    .append('\"');
        }
        source.append(";\n")
                .append("export type NaturalTimeoutCross")
                .append(prefix)
                .append("<A extends string, B extends string> = ")
                .append("A extends A ? B extends B ? readonly [A, B] : never : never;\n")
                .append("export type NaturalTimeoutMatrix")
                .append(prefix)
                .append(" = NaturalTimeoutCross")
                .append(prefix)
                .append("<NaturalTimeoutAtom")
                .append(prefix)
                .append(", NaturalTimeoutAtom")
                .append(prefix)
                .append(">;\n");
        for (int indexOfSpace = 0;
                indexOfSpace < SEMANTIC_HELPER_WHITESPACE_BYTES;
                indexOfSpace++) {
            source.append(' ');
        }
        return source.toString();
    }

    private static int semanticHelperIndex(int rootIndex) {
        require(
                rootIndex >= SEMANTIC_LIGHT_ROOT_COUNT
                        && rootIndex < SEMANTIC_ROOT_COUNT,
                "Natural semantic heavy-root ordinal is outside its bound.");
        return (rootIndex - SEMANTIC_LIGHT_ROOT_COUNT)
                % SEMANTIC_HELPER_COUNT;
    }

    static String parseSourceDirectory() {
        return "sources/p00/p01/p02/p03/p04/p05/p06/p07";
    }

    static String parseSourcePath(int index) {
        require(
                index >= 0 && index < PARSE_ROOT_COUNT,
                "Natural parse root ordinal is outside its bound.");
        return parseSourceDirectory()
                + "/probe-parse-root-"
                + threeDigits(index)
                + ".ts";
    }

    static String parseSourceText(int index) {
        require(
                index >= 0 && index < PARSE_ROOT_COUNT,
                "Natural parse root ordinal is outside its bound.");
        String prefix = threeDigits(index);
        StringBuilder source = new StringBuilder(384 * 1024);
        if (index == 0) {
            source.append(
                    "/// <reference path=\"./probe-parse-ambient.d.ts\" />\n");
        }
        if (index == PARSE_GRAPH_TRIGGER_ROOT_INDEX) {
            source.append(parseGraphReferenceLine(PARSE_GRAPH_ENTRY_INDEX));
        }
        int firstModuleIndex = -1;
        int importCount = 0;
        if (index >= PARSE_FIRST_IMPORT_ROOT_INDEX
                && index
                        < PARSE_FIRST_IMPORT_ROOT_INDEX
                                + PARSE_IMPORT_ROOT_COUNT) {
            firstModuleIndex =
                    (index - PARSE_FIRST_IMPORT_ROOT_INDEX)
                            * PARSE_IMPORTS_PER_ROOT;
            importCount = PARSE_IMPORTS_PER_ROOT;
        } else if (index == PARSE_POST_SEQUENCE_IMPORT_ROOT_INDEX) {
            firstModuleIndex = PARSE_PRE_SEQUENCE_MAXIMUM_MODULE_INDEX + 1;
            importCount = PARSE_POST_SEQUENCE_IMPORT_COUNT;
        }
        for (int importIndex = 0;
                importIndex < importCount;
                importIndex++) {
            int moduleIndex = firstModuleIndex + importIndex;
            require(
                    moduleIndex <= PARSE_MAXIMUM_MODULE_INDEX,
                    "Natural parse module index escaped authority.");
            String moduleSuffix = fiveDigits(moduleIndex);
            source.append("import type { ProbeType as ProbeType")
                    .append(moduleSuffix)
                    .append(" } from \"probe-")
                    .append(moduleSuffix)
                    .append("\"; ");
        }
        return source.append("export const probeValue")
                .append(prefix)
                .append(" = ")
                .append(index)
                .append("; ")
                .toString();
    }

    static String parseAmbientPath() {
        return parseSourceDirectory() + "/probe-parse-ambient.d.ts";
    }

    static String parseAmbientText() {
        return "declare module \"probe-*\" { export type ProbeType = number; } ";
    }

    static String parseGraphPath(int index) {
        require(
                index >= 0 && index < PARSE_GRAPH_FILE_COUNT,
                "Natural parse declaration ordinal is outside its bound.");
        return parseSourceDirectory()
                + "/probe-parse-xx"
                + threeDigits(index)
                + ".d.ts";
    }

    static String parseGraphText(int index) {
        require(
                index >= 0 && index < PARSE_GRAPH_FILE_COUNT,
                "Natural parse declaration ordinal is outside its bound.");
        StringBuilder source =
                new StringBuilder(
                        index * PARSE_REFERENCE_LINE_BYTES);
        for (int target = 0;
                target < index;
                target++) {
            source.append(parseGraphReferenceLine(target));
        }
        return source.toString();
    }

    private static String parseGraphReferenceLine(int index) {
        String line =
                "/// <reference path=\"./probe-parse-xx"
                        + threeDigits(index)
                        + ".d.ts\" />\n";
        require(
                line.getBytes(StandardCharsets.US_ASCII).length
                        == PARSE_REFERENCE_LINE_BYTES,
                "Natural parse declaration reference width drifted.");
        return line;
    }

    private static void assertSourceAuthority(
            Workload workload,
            List<SourcePayload> sources,
            List<String> rootPaths) {
        require(!sources.isEmpty(), "External natural emit source inventory is empty.");
        require(!rootPaths.isEmpty(), "External natural emit root inventory is empty.");
        List<String> paths = new ArrayList<>(sources.size());
        long callerBytes = 0L;
        long callerPathCharacters = 0L;
        long maximumFileBytes = 0L;
        long maximumPathCharacters = 0L;
        for (SourcePayload source : sources) {
            paths.add(source.authority.path);
            callerBytes =
                    Math.addExact(
                            callerBytes,
                            source.authority.size);
            callerPathCharacters =
                    Math.addExact(
                            callerPathCharacters,
                                source.authority.path.length() + 1L);
            maximumPathCharacters =
                    Math.max(
                            maximumPathCharacters,
                            source.authority.path.length() + 1L);
            maximumFileBytes =
                    Math.max(
                            maximumFileBytes,
                            source.authority.size);
            require(
                    source.authority.size <= ORDINARY_MAXIMUM_FILE_BYTES,
                    "External natural emit source exceeds the ordinary file limit.");
        }
        List<String> sortedPaths = new ArrayList<>(paths);
        Collections.sort(sortedPaths);
        require(
                paths.equals(sortedPaths),
                "External natural emit source inventory is not sorted.");
        require(
                new HashSet<>(paths).size() == paths.size(),
                "External natural emit source inventory contains a duplicate.");
        List<String> sortedRoots = new ArrayList<>(rootPaths);
        Collections.sort(sortedRoots);
        require(
                rootPaths.equals(sortedRoots),
                "External natural emit root inventory is not sorted.");
        require(
                paths.containsAll(rootPaths),
                "External natural emit root inventory is not covered by caller files.");

        if (workload == Workload.NATURAL_EMIT) {
            require(
                    sources.size() == NATURAL_CALLER_FILE_COUNT
                            && callerBytes == NATURAL_CALLER_BYTES
                            && rootPaths.size() == ROOT_COUNT,
                    "External natural emit caller authority drifted.");
            require(
                    NATURAL_MAXIMUM_FILE_TOKENS
                                    < WORKER_MAXIMUM_TOKENS_PER_FILE
                            && NATURAL_TOTAL_TOKENS
                                    < WORKER_MAXIMUM_PROJECT_TOKENS,
                    "External natural emit token authority escaped ordinary Worker admission.");
            require(
                    NATURAL_TOTAL_FILE_COUNT
                                    == NATURAL_CALLER_FILE_COUNT
                                            + TRUSTED_DECLARATION_FILE_COUNT
                            && NATURAL_TOTAL_FILE_COUNT
                                    < ORDINARY_MAXIMUM_FILES
                            && NATURAL_TOTAL_BYTES
                                    == NATURAL_CALLER_BYTES
                                            + TRUSTED_DECLARATION_BYTES
                            && NATURAL_TOTAL_BYTES
                                    < ORDINARY_MAXIMUM_TOTAL_BYTES
                            && maximumFileBytes
                                    == NATURAL_MAXIMUM_FILE_BYTES
                            && NATURAL_TOTAL_PATH_CHARACTERS
                                    == callerPathCharacters
                                            + TRUSTED_DECLARATION_PATH_CHARACTERS,
                    "External natural emit aggregate authority escaped ordinary admission.");
            require(
                    NATURAL_OUTPUT_AUTHORITY.totalBytes
                                    <= MAXIMUM_OUTPUT_BYTES
                            && MAXIMUM_OUTPUT_BYTES
                                            - NATURAL_OUTPUT_AUTHORITY.totalBytes
                                    >= MINIMUM_OUTPUT_HEADROOM_BYTES,
                    "External natural emit output lost its ordinary headroom.");
            require(
                    NATURAL_INPUT_TRANSPORT_FRAME_COUNT
                            == inputTransportFrameCount(sources),
                    "External natural emit input framing authority drifted.");
        } else if (workload == Workload.NATURAL_SEMANTIC) {
            require(
                    sources.size() == SEMANTIC_CALLER_FILE_COUNT
                            && callerBytes == SEMANTIC_CALLER_BYTES
                            && rootPaths.size() == SEMANTIC_ROOT_COUNT,
                    "External natural semantic caller authority drifted.");
            require(
                    SEMANTIC_CALLER_TOKENS
                                    == SEMANTIC_ROOT_TOKENS
                                            + SEMANTIC_HELPER_TOKENS
                            && SEMANTIC_MAXIMUM_CALLER_FILE_TOKENS
                                    < WORKER_MAXIMUM_TOKENS_PER_FILE
                            && SEMANTIC_TOTAL_TOKENS
                                    == SEMANTIC_CALLER_TOKENS
                                            + TRUSTED_DECLARATION_TOKENS
                            && SEMANTIC_TOTAL_TOKENS
                                    < WORKER_MAXIMUM_PROJECT_TOKENS,
                    "External natural semantic token authority escaped ordinary Worker admission.");
            require(
                    SEMANTIC_CALLER_FILE_COUNT
                                    == SEMANTIC_ROOT_COUNT
                                            + SEMANTIC_HELPER_COUNT
                            && SEMANTIC_TOTAL_FILE_COUNT
                                    == SEMANTIC_CALLER_FILE_COUNT
                                            + TRUSTED_DECLARATION_FILE_COUNT
                            && SEMANTIC_TOTAL_FILE_COUNT
                                    <= ORDINARY_MAXIMUM_FILES
                            && SEMANTIC_TOTAL_FILE_COUNT
                                    == ORDINARY_MAXIMUM_FILES
                            && SEMANTIC_CALLER_BYTES
                                    == SEMANTIC_ROOT_BYTES
                                            + SEMANTIC_HELPER_BYTES
                            && SEMANTIC_TOTAL_BYTES
                                    == SEMANTIC_CALLER_BYTES
                                            + TRUSTED_DECLARATION_BYTES
                            && SEMANTIC_TOTAL_BYTES
                                    < ORDINARY_MAXIMUM_TOTAL_BYTES
                            && maximumFileBytes
                                    == SEMANTIC_MAXIMUM_FILE_BYTES
                            && callerPathCharacters
                                    == SEMANTIC_CALLER_PATH_CHARACTERS
                            && SEMANTIC_TOTAL_PATH_CHARACTERS
                                    == SEMANTIC_CALLER_PATH_CHARACTERS
                                            + TRUSTED_DECLARATION_PATH_CHARACTERS,
                    "External natural semantic aggregate authority escaped ordinary admission.");
            require(
                    SEMANTIC_OUTPUT_AUTHORITY.totalBytes
                                    <= MAXIMUM_OUTPUT_BYTES
                            && MAXIMUM_OUTPUT_BYTES
                                            - SEMANTIC_OUTPUT_AUTHORITY.totalBytes
                                    >= MINIMUM_OUTPUT_HEADROOM_BYTES,
                    "External natural semantic output lost its ordinary headroom.");
            require(
                    SEMANTIC_INPUT_TRANSPORT_FRAME_COUNT
                            == inputTransportFrameCount(sources),
                    "External natural semantic input framing authority drifted.");
        } else if (workload == Workload.NATURAL_PARSE) {
            require(
                    sources.size() == PARSE_CALLER_FILE_COUNT
                            && callerBytes == PARSE_CALLER_BYTES
                            && rootPaths.size() == PARSE_ROOT_COUNT,
                    "External natural parse caller authority drifted.");
            require(
                    PARSE_CALLER_TOKENS
                                    == PARSE_ROOT_TOKENS
                                            + PARSE_AMBIENT_TOKENS
                                            + PARSE_GRAPH_TOKENS
                            && PARSE_MAXIMUM_ROOT_TOKENS
                                    < WORKER_MAXIMUM_TOKENS_PER_FILE
                            && PARSE_TOTAL_TOKENS
                                    == PARSE_CALLER_TOKENS
                                            + TRUSTED_DECLARATION_TOKENS
                            && PARSE_TOTAL_TOKENS
                                    < WORKER_MAXIMUM_PROJECT_TOKENS,
                    "External natural parse token authority escaped ordinary Worker admission.");
            require(
                    PARSE_CALLER_FILE_COUNT
                                    == PARSE_ROOT_COUNT
                                            + 1
                                            + PARSE_GRAPH_FILE_COUNT
                            && PARSE_TOTAL_FILE_COUNT
                                    == PARSE_CALLER_FILE_COUNT
                                            + TRUSTED_DECLARATION_FILE_COUNT
                            && PARSE_TOTAL_FILE_COUNT
                                    <= ORDINARY_MAXIMUM_FILES
                            && PARSE_TOTAL_FILE_COUNT
                                    == ORDINARY_MAXIMUM_FILES
                            && PARSE_CALLER_BYTES
                                    == PARSE_ROOT_BYTES
                                            + PARSE_AMBIENT_BYTES
                                            + PARSE_GRAPH_BYTES
                            && PARSE_TOTAL_BYTES
                                    == PARSE_CALLER_BYTES
                                            + TRUSTED_DECLARATION_BYTES
                            && PARSE_TOTAL_BYTES
                                    < ORDINARY_MAXIMUM_TOTAL_BYTES
                            && maximumFileBytes
                                    == PARSE_MAXIMUM_ROOT_BYTES
                            && callerPathCharacters
                                    == PARSE_CALLER_PATH_CHARACTERS
                            && maximumPathCharacters
                                    == PARSE_MAXIMUM_PATH_CHARACTERS
                            && PARSE_TOTAL_PATH_CHARACTERS
                                    == PARSE_CALLER_PATH_CHARACTERS
                                            + TRUSTED_DECLARATION_PATH_CHARACTERS,
                    "External natural parse aggregate authority escaped ordinary admission.");
            require(
                    PARSE_OUTPUT_AUTHORITY.totalBytes
                                    <= MAXIMUM_OUTPUT_BYTES
                            && MAXIMUM_OUTPUT_BYTES
                                            - PARSE_OUTPUT_AUTHORITY.totalBytes
                                    >= MINIMUM_OUTPUT_HEADROOM_BYTES,
                    "External natural parse output lost its ordinary headroom.");
            require(
                    PARSE_INPUT_TRANSPORT_FRAME_COUNT
                            == inputTransportFrameCount(sources),
                    "External natural parse input framing authority drifted.");
        } else {
            require(
                    sources.size() == SUCCESSOR_CALLER_FILE_COUNT
                            && callerBytes == SUCCESSOR_CALLER_BYTES
                            && rootPaths.equals(
                                    Collections.singletonList(
                                            SUCCESSOR_ENTRY_PATH)),
                    "External natural emit successor authority drifted.");
            require(
                    SUCCESSOR_TOTAL_FILE_COUNT
                                    == SUCCESSOR_CALLER_FILE_COUNT
                                            + TRUSTED_DECLARATION_FILE_COUNT
                            && SUCCESSOR_TOTAL_FILE_COUNT
                                    < ORDINARY_MAXIMUM_FILES
                            && SUCCESSOR_TOTAL_BYTES
                                    == SUCCESSOR_CALLER_BYTES
                                            + TRUSTED_DECLARATION_BYTES
                            && SUCCESSOR_TOTAL_BYTES
                                    < ORDINARY_MAXIMUM_TOTAL_BYTES
                            && SUCCESSOR_TOTAL_TOKENS
                                    < WORKER_MAXIMUM_PROJECT_TOKENS
                            && SUCCESSOR_TOTAL_PATH_CHARACTERS
                                    == callerPathCharacters
                                            + TRUSTED_DECLARATION_PATH_CHARACTERS,
                    "External natural emit successor escaped ordinary admission.");
            require(
                    SUCCESSOR_INPUT_TRANSPORT_FRAME_COUNT
                            == inputTransportFrameCount(sources),
                    "External natural emit successor framing authority drifted.");
        }
    }

    private static int inputTransportFrameCount(
            List<SourcePayload> callerSources) {
        int trustedDeclarationFrames =
                SUCCESSOR_INPUT_TRANSPORT_FRAME_COUNT
                        - 2
                        - 2 * SUCCESSOR_CALLER_FILE_COUNT
                        - SUCCESSOR_CALLER_FILE_COUNT;
        int frames = 2 + trustedDeclarationFrames;
        for (SourcePayload source : callerSources) {
            frames =
                    Math.addExact(
                            frames,
                            2
                                    + (int)
                                            ((source.authority.size
                                                            + INPUT_TRANSPORT_FRAME_BYTES
                                                            - 1L)
                                                    / INPUT_TRANSPORT_FRAME_BYTES));
        }
        return frames;
    }

    private static byte[] requestManifest(
            String requestId,
            R2FenceBoundedProjectFixture.CompilerAuthority authority,
            String projectId,
            String entryPath,
            String entrySourceUri,
            String inventoryFingerprint,
            List<String> rootPaths,
            List<SourcePayload> sources) {
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
                .append(JSONObject.quote(TypeScriptCompilerContract.HASH))
                .append(",\"inputArchiveTransportVersion\":")
                .append(TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION)
                .append(",\"requestId\":")
                .append(JSONObject.quote(requestId))
                .append(",\"projectId\":")
                .append(JSONObject.quote(projectId))
                .append(",\"entrySourceUri\":")
                .append(JSONObject.quote(entrySourceUri))
                .append(",\"entryPath\":")
                .append(JSONObject.quote(entryPath))
                .append(",\"targetProfile\":")
                .append(
                        JSONObject.quote(
                                TypeScriptCompilerContract.PROFILE_RHINO))
                .append(",\"compilationMode\":")
                .append(
                        JSONObject.quote(
                                TypeScriptCompilerContract.MODE_DEVELOPMENT))
                .append(",\"profileSchemaRevision\":")
                .append(TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION)
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
                                authority.runtimeCapabilitiesFingerprint))
                .append(",\"compilerPackage\":")
                .append(JSONObject.quote(COMPILER_PACKAGE))
                .append(",\"compilerVersion\":")
                .append(JSONObject.quote(authority.compilerVersion))
                .append(",\"compilerDistributionFingerprint\":")
                .append(
                        JSONObject.quote(
                                authority.compilerDistributionFingerprint))
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
        for (int index = 0; index < rootPaths.size(); index++) {
            if (index > 0) {
                manifest.append(',');
            }
            manifest.append(JSONObject.quote(rootPaths.get(index)));
        }
        manifest.append("],\"files\":[");
        for (int index = 0; index < sources.size(); index++) {
            if (index > 0) {
                manifest.append(',');
            }
            SourcePayload source = sources.get(index);
            manifest.append("{\"path\":")
                    .append(JSONObject.quote(source.authority.path))
                    .append(",\"sha256\":")
                    .append(JSONObject.quote(source.authority.sha256))
                    .append(",\"size\":")
                    .append(source.authority.size)
                    .append('}');
        }
        return manifest.append("]}").toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String callerInventoryFingerprint(
            List<SourcePayload> sources) {
        MessageDigest digest = sha256Digest();
        digest.update(
                "autojs6.typescript.input-inventory.v1\n"
                        .getBytes(StandardCharsets.US_ASCII));
        for (SourcePayload source : sources) {
            digest.update("source".getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(
                    ("/" + source.authority.path).getBytes(
                            StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(
                    Long.toString(source.authority.size)
                            .getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(
                    source.authority.sha256.getBytes(
                            StandardCharsets.US_ASCII));
            digest.update((byte) '\n');
        }
        return lowerHex(digest.digest());
    }

    /** Preserves the pre-S1 workload identity independently from the formal archive wire. */
    private static String workloadIdentityFingerprint(
            List<SourcePayload> sources) {
        MessageDigest digest = sha256Digest();
        for (SourcePayload source : sources) {
            digest.update(
                    source.authority.path.getBytes(
                            StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(
                    Long.toString(source.authority.size)
                            .getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(
                    source.authority.sha256.getBytes(
                            StandardCharsets.US_ASCII));
            digest.update((byte) '\n');
        }
        return lowerHex(digest.digest());
    }

    private static String writeStoredZip32(
            File file, List<ArchiveEntry> entries) throws Exception {
        require(
                !entries.isEmpty() && entries.size() <= 0xffff,
                "External natural emit ZIP32 entry count is outside its bound.");
        MessageDigest archiveDigest = sha256Digest();
        try (FileOutputStream fileOutput =
                new FileOutputStream(file, false)) {
            DigestingCountingOutput output =
                    new DigestingCountingOutput(fileOutput, archiveDigest);
            List<CentralRecord> centralRecords =
                    new ArrayList<>(entries.size());
            for (ArchiveEntry entry : entries) {
                byte[] name = asciiZipName(entry.path);
                long localOffset = output.position;
                putLocalHeader(output, entry, name);
                output.write(entry.bytes);
                centralRecords.add(
                        new CentralRecord(entry, name, localOffset));
            }

            long centralOffset = output.position;
            for (CentralRecord record : centralRecords) {
                putCentralHeader(output, record);
            }
            long centralSize = output.position - centralOffset;
            output.putU32(0x06054b50L);
            output.putU16(0);
            output.putU16(0);
            output.putU16(centralRecords.size());
            output.putU16(centralRecords.size());
            output.putU32(centralSize);
            output.putU32(centralOffset);
            output.putU16(0);
            output.flush();
            fileOutput.getFD().sync();
        }
        return lowerHex(archiveDigest.digest());
    }

    private static void putLocalHeader(
            DigestingCountingOutput output,
            ArchiveEntry entry,
            byte[] name)
            throws Exception {
        output.putU32(0x04034b50L);
        output.putU16(ZIP32_VERSION_STORED);
        output.putU16(0);
        output.putU16(STORED_METHOD);
        output.putU16(0);
        output.putU16(0);
        output.putU32(entry.metrics.crc32);
        output.putU32(entry.bytes.length);
        output.putU32(entry.bytes.length);
        output.putU16(name.length);
        output.putU16(0);
        output.write(name);
    }

    private static void putCentralHeader(
            DigestingCountingOutput output, CentralRecord record)
            throws Exception {
        output.putU32(0x02014b50L);
        output.putU16(ZIP32_VERSION_STORED);
        output.putU16(ZIP32_VERSION_STORED);
        output.putU16(0);
        output.putU16(STORED_METHOD);
        output.putU16(0);
        output.putU16(0);
        output.putU32(record.entry.metrics.crc32);
        output.putU32(record.entry.bytes.length);
        output.putU32(record.entry.bytes.length);
        output.putU16(record.name.length);
        output.putU16(0);
        output.putU16(0);
        output.putU16(0);
        output.putU16(0);
        output.putU32(0);
        output.putU32(record.localOffset);
        output.write(record.name);
    }

    private static void verifyStoredZip32(
            File file,
            List<ArchiveEntry> expectedEntries,
            String expectedArchiveSha256)
            throws Exception {
        require(file.isFile(), "External natural emit ZIP is not a regular file.");
        require(
                expectedArchiveSha256.equals(sha256(file)),
                "External natural emit ZIP digest changed after fsync.");
        try (ZipFile zip = new ZipFile(file)) {
            List<String> expectedNames =
                    new ArrayList<>(expectedEntries.size());
            for (ArchiveEntry entry : expectedEntries) {
                expectedNames.add(entry.path);
            }
            List<String> actualNames = new ArrayList<>();
            Set<String> uniqueNames = new HashSet<>();
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                require(
                        uniqueNames.add(entry.getName()),
                        "External natural emit ZIP contains a duplicate entry.");
                actualNames.add(entry.getName());
            }
            require(
                    expectedNames.equals(actualNames),
                    "External natural emit ZIP inventory or order differs: "
                            + actualNames);
            for (ArchiveEntry expected : expectedEntries) {
                ZipEntry actual = zip.getEntry(expected.path);
                require(
                        actual != null && !actual.isDirectory(),
                        "External natural emit ZIP entry is missing.");
                require(
                        actual.getMethod() == ZipEntry.STORED,
                        "External natural emit ZIP entry is not STORED.");
                require(
                        actual.getSize() == expected.bytes.length
                                && actual.getCompressedSize()
                                        == expected.bytes.length,
                        "External natural emit ZIP entry size differs.");
                require(
                        actual.getCrc() == expected.metrics.crc32,
                        "External natural emit ZIP entry CRC differs.");
                try (InputStream inputStream = zip.getInputStream(actual)) {
                    require(
                            expected.metrics.sha256.equals(
                                    sha256(inputStream)),
                            "External natural emit ZIP entry SHA-256 differs.");
                }
            }
        }
    }

    private static byte[] asciiZipName(String path) {
        require(
                path != null
                        && !path.isEmpty()
                        && path.length()
                                <= TypeScriptCompilerContract
                                        .MAX_ARCHIVE_ENTRY_NAME_BYTES,
                "External natural emit ZIP path length is outside its bound.");
        byte[] bytes = path.getBytes(StandardCharsets.US_ASCII);
        require(
                path.equals(new String(bytes, StandardCharsets.US_ASCII)),
                "External natural emit ZIP path is not ASCII.");
        return bytes;
    }

    private static void truncateAndSync(File file) throws Exception {
        try (FileOutputStream outputStream =
                new FileOutputStream(file, false)) {
            outputStream.flush();
            outputStream.getFD().sync();
        }
    }

    private static String sha256(File file) throws Exception {
        try (InputStream inputStream = new FileInputStream(file)) {
            return sha256(inputStream);
        }
    }

    private static String sha256(InputStream inputStream)
            throws Exception {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        Arrays.fill(buffer, (byte) 0);
        return lowerHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception error) {
            throw new AssertionError("SHA-256 is unavailable.", error);
        }
    }

    private static String lowerHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            result.append(
                    Character.forDigit(unsigned >>> 4, 16));
            result.append(
                    Character.forDigit(unsigned & 0x0f, 16));
        }
        return result.toString();
    }

    private static void requireRequestId(String requestId) {
        require(
                requestId != null
                        && !requestId.trim().isEmpty()
                        && requestId.length()
                                <= TypeScriptCompilerContract
                                        .MAX_REQUEST_ID_CHARS,
                "External natural emit request ID is invalid.");
    }

    private static void requireAuthority(
            R2FenceBoundedProjectFixture.CompilerAuthority authority) {
        require(authority != null, "External natural emit compiler authority is null.");
        require(
                authority.profileRevision > 0,
                "External natural emit profile revision is invalid.");
        require(
                authority.configSchemaRevision > 0,
                "External natural emit config revision is invalid.");
        require(
                authority.compilerVersion != null
                        && !authority.compilerVersion.isEmpty(),
                "External natural emit compiler version is empty.");
        requireLowerSha256(
                authority.compilerDistributionFingerprint,
                "External natural emit compiler distribution fingerprint");
        requireLowerSha256(
                authority.declarationsFingerprint,
                "External natural emit declarations fingerprint");
        requireLowerSha256(
                authority.declarationProfileFingerprint,
                "External natural emit declaration profile fingerprint");
        requireLowerSha256(
                authority.runtimeCapabilitiesFingerprint,
                "External natural emit runtime-capabilities fingerprint");
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

    private static String threeDigits(int value) {
        require(
                value >= 0 && value <= 999,
                "Three-digit fixture ordinal is outside its bound.");
        if (value < 10) {
            return "00" + value;
        }
        return value < 100 ? "0" + value : Integer.toString(value);
    }

    private static String fiveDigits(int value) {
        require(
                value >= 0 && value <= 99_999,
                "Five-digit fixture ordinal is outside its bound.");
        if (value < 10) {
            return "0000" + value;
        }
        if (value < 100) {
            return "000" + value;
        }
        if (value < 1_000) {
            return "00" + value;
        }
        return value < 10_000 ? "0" + value : Integer.toString(value);
    }

    private static String twoDigits(int value) {
        require(
                value >= 0 && value <= 99,
                "Two-digit fixture ordinal is outside its bound.");
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    private static void deleteExactly(File file, String label) {
        require(
                file != null && (!file.exists() || file.delete()),
                "Could not delete " + label + ".");
        require(!file.exists(), "Deleted " + label + " still exists.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    enum Workload {
        NATURAL_EMIT,
        SUCCESSOR,
        NATURAL_SEMANTIC,
        NATURAL_PARSE
    }

    static final class OutputAuthority {

        final int fileCount;
        final long javascriptBytes;
        final long sourceMapBytes;
        final long totalBytes;
        final String aggregateSha256;

        OutputAuthority(
                int fileCount,
                long javascriptBytes,
                long sourceMapBytes,
                long totalBytes,
                String aggregateSha256) {
            require(fileCount > 0, "Expected output file count is invalid.");
            require(
                    javascriptBytes > 0L
                            && sourceMapBytes > 0L
                            && totalBytes
                                    == javascriptBytes
                                            + sourceMapBytes,
                    "Expected output byte authority is invalid.");
            requireLowerSha256(
                    aggregateSha256,
                    "Expected output aggregate SHA-256");
            this.fileCount = fileCount;
            this.javascriptBytes = javascriptBytes;
            this.sourceMapBytes = sourceMapBytes;
            this.totalBytes = totalBytes;
            this.aggregateSha256 = aggregateSha256;
        }
    }

    static final class SourceAuthority {

        final String path;
        final long size;
        final String sha256;

        SourceAuthority(String path, long size, String sha256) {
            this.path = path;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    private static final class SourcePayload {

        final byte[] bytes;
        final PayloadMetrics metrics;
        final SourceAuthority authority;

        SourcePayload(
                String path, byte[] bytes, PayloadMetrics metrics) {
            this.bytes = bytes;
            this.metrics = metrics;
            this.authority =
                    new SourceAuthority(
                            path, bytes.length, metrics.sha256);
        }

        static SourcePayload ascii(String path, String text) {
            byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
            require(
                    text.equals(
                            new String(
                                    bytes,
                                    StandardCharsets.US_ASCII)),
                    "External natural emit source is not ASCII.");
            return new SourcePayload(
                    path, bytes, PayloadMetrics.of(bytes));
        }
    }

    private static final class PayloadMetrics {

        final long crc32;
        final String sha256;

        PayloadMetrics(long crc32, String sha256) {
            this.crc32 = crc32;
            this.sha256 = sha256;
        }

        static PayloadMetrics of(byte[] bytes) {
            CRC32 crc = new CRC32();
            crc.update(bytes, 0, bytes.length);
            MessageDigest digest = sha256Digest();
            digest.update(bytes);
            return new PayloadMetrics(
                    crc.getValue(), lowerHex(digest.digest()));
        }
    }

    private static final class ArchiveEntry {

        final String path;
        final byte[] bytes;
        final PayloadMetrics metrics;

        ArchiveEntry(
                String path, byte[] bytes, PayloadMetrics metrics) {
            this.path = path;
            this.bytes = bytes;
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
        public void write(int value) throws java.io.IOException {
            delegate.write(value);
            digest.update((byte) value);
            position++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length)
                throws java.io.IOException {
            delegate.write(bytes, offset, length);
            digest.update(bytes, offset, length);
            position = Math.addExact(position, length);
        }

        @Override
        public void flush() throws java.io.IOException {
            delegate.flush();
        }

        void putU16(long value) throws java.io.IOException {
            require(
                    value >= 0 && value <= 0xffffL,
                    "ZIP32 u16 value is outside range.");
            write((int) value);
            write((int) (value >>> 8));
        }

        void putU32(long value) throws java.io.IOException {
            require(
                    value >= 0 && value <= UINT32_MAX,
                    "ZIP32 u32 value is outside range.");
            write((int) value);
            write((int) (value >>> 8));
            write((int) (value >>> 16));
            write((int) (value >>> 24));
        }
    }
}
