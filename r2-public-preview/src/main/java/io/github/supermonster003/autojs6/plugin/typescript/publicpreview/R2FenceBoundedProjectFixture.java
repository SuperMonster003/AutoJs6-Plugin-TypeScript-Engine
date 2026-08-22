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
 * Generates the external-caller archive immediately below or above the production Worker fence.
 *
 * <p>The caller contributes one low-token source. Trusted standard libraries are appended by the
 * plugin after ZIP validation, so their installed distribution authority determines the exact
 * caller-byte boundary.
 */
final class R2FenceBoundedProjectFixture implements AutoCloseable {

    static final String REQUEST_MANIFEST_PATH =
            TypeScriptR2Contract.INPUT_ARCHIVE_MANIFEST_PATH;
    static final String SOURCE_PATH = "sources/main.ts";
    static final String PROJECT_ID = "r2-public-preview-fence-project";
    static final String ENTRY_SOURCE_URI =
            "autojs://source/r2-public-preview-fence/main.ts";

    private static final String COMPILER_PACKAGE = "typescript";
    private static final String CONFIG_KIND_NONE = "none";
    private static final long WORKER_MAXIMUM_TOTAL_BYTES = 8L * 1024L * 1024L;
    private static final int EXPECTED_STANDARD_LIBRARY_FILES = 108;
    private static final long EXPECTED_STANDARD_LIBRARY_BYTES = 3_784_758L;
    private static final long EXPECTED_CALLER_BYTES = 4_603_850L;
    private static final int STORED_METHOD = 0;
    private static final int ZIP32_VERSION_STORED = 10;
    private static final long UINT32_MAX = 0xffff_ffffL;
    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    private static final byte[] SOURCE_PREFIX =
            "export const fenceBounded: number = 42;\n"
                    .getBytes(StandardCharsets.US_ASCII);

    final String requestId;
    final File input;
    final String inputArchiveSha256;
    final String requestManifestSha256;
    final long callerSourceBytes;
    final boolean exceedsWorkerFence;

    private R2FenceBoundedProjectFixture(
            String requestId,
            File input,
            String inputArchiveSha256,
            String requestManifestSha256,
            long callerSourceBytes,
            boolean exceedsWorkerFence) {
        this.requestId = requestId;
        this.input = input;
        this.inputArchiveSha256 = inputArchiveSha256;
        this.requestManifestSha256 = requestManifestSha256;
        this.callerSourceBytes = callerSourceBytes;
        this.exceedsWorkerFence = exceedsWorkerFence;
    }

    static R2FenceBoundedProjectFixture create(
            Context fixtureContext,
            Context pluginContext,
            String requestId,
            CompilerAuthority authority,
            boolean exceedsWorkerFence)
            throws Exception {
        require(requestId != null && !requestId.isEmpty(), "Request ID is empty.");
        DistributionBoundary boundary =
                DistributionBoundary.load(pluginContext, authority.compilerVersion);
        long callerSourceBytes =
                Math.addExact(
                        Math.subtractExact(
                                WORKER_MAXIMUM_TOTAL_BYTES,
                                boundary.standardLibraryBytes),
                        exceedsWorkerFence ? 1L : 0L);
        require(
                boundary.standardLibraryFiles
                        == EXPECTED_STANDARD_LIBRARY_FILES,
                "Standard-library file-count authority drifted: "
                        + boundary.standardLibraryFiles);
        require(
                boundary.standardLibraryBytes
                        == EXPECTED_STANDARD_LIBRARY_BYTES,
                "Standard-library byte authority drifted: "
                        + boundary.standardLibraryBytes);
        require(
                callerSourceBytes
                        == EXPECTED_CALLER_BYTES
                                + (exceedsWorkerFence ? 1L : 0L),
                "Caller Worker-fence boundary drifted: " + callerSourceBytes);

        byte[] source = sourceBytes(callerSourceBytes);
        PayloadMetrics sourceMetrics = PayloadMetrics.of(source);
        byte[] manifest =
                requestManifest(
                        requestId,
                        authority,
                        sourceMetrics.sha256,
                        callerSourceBytes);
        PayloadMetrics manifestMetrics = PayloadMetrics.of(manifest);

        File input =
                File.createTempFile(
                        exceedsWorkerFence
                                ? "r2-public-preview-fence-plus-one-"
                                : "r2-public-preview-fence-inclusive-",
                        ".archive",
                        fixtureContext.getCacheDir());
        boolean retained = false;
        try {
            List<ArchiveEntry> entries =
                    Arrays.asList(
                            new ArchiveEntry(
                                    REQUEST_MANIFEST_PATH,
                                    manifest,
                                    manifestMetrics),
                            new ArchiveEntry(SOURCE_PATH, source, sourceMetrics));
            String archiveSha256 = writeStoredZip32(input, entries);
            verifyStoredZip32(input, entries, archiveSha256);
            R2FenceBoundedProjectFixture fixture =
                    new R2FenceBoundedProjectFixture(
                            requestId,
                            input,
                            archiveSha256,
                            manifestMetrics.sha256,
                            callerSourceBytes,
                            exceedsWorkerFence);
            retained = true;
            return fixture;
        } finally {
            Arrays.fill(source, (byte) 0);
            if (!retained) {
                deleteExactly(input, "failed fence-bounded input fixture");
            }
        }
    }

    @Override
    public void close() {
        deleteExactly(input, "fence-bounded input fixture");
    }

    private static byte[] sourceBytes(long requestedBytes) {
        require(
                requestedBytes >= SOURCE_PREFIX.length
                        && requestedBytes <= Integer.MAX_VALUE,
                "Fence-bounded source size cannot be materialized.");
        byte[] source = new byte[(int) requestedBytes];
        Arrays.fill(source, (byte) ' ');
        System.arraycopy(
                SOURCE_PREFIX,
                0,
                source,
                0,
                SOURCE_PREFIX.length);
        return source;
    }

    private static byte[] requestManifest(
            String requestId,
            CompilerAuthority authority,
            String sourceSha256,
            long sourceBytes) {
        String inventoryFingerprint =
                inventoryFingerprint(SOURCE_PATH, sourceBytes, sourceSha256);
        String manifest =
                "{\"schema\":"
                        + JSONObject.quote(TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA)
                        + ",\"revision\":"
                        + TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION
                        + ",\"inputQuotaRevision\":"
                        + TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION
                        + ",\"contractVersion\":"
                        + TypeScriptCompilerContract.VERSION
                        + ",\"contractHash\":"
                        + JSONObject.quote(TypeScriptCompilerContract.HASH)
                        + ",\"inputArchiveTransportVersion\":"
                        + TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION
                        + ",\"requestId\":"
                        + JSONObject.quote(requestId)
                        + ",\"projectId\":"
                        + JSONObject.quote(PROJECT_ID)
                        + ",\"entrySourceUri\":"
                        + JSONObject.quote(ENTRY_SOURCE_URI)
                        + ",\"entryPath\":"
                        + JSONObject.quote(SOURCE_PATH)
                        + ",\"targetProfile\":"
                        + JSONObject.quote(TypeScriptCompilerContract.PROFILE_RHINO)
                        + ",\"compilationMode\":"
                        + JSONObject.quote(TypeScriptCompilerContract.MODE_DEVELOPMENT)
                        + ",\"profileSchemaRevision\":"
                        + TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION
                        + ",\"profileRevision\":"
                        + authority.profileRevision
                        + ",\"runtimeCapabilitySchemaRevision\":"
                        + TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION
                        + ",\"configSchemaRevision\":"
                        + authority.configSchemaRevision
                        + ",\"runtimeCapabilitiesFingerprint\":"
                        + JSONObject.quote(
                                authority.runtimeCapabilitiesFingerprint)
                        + ",\"compilerPackage\":"
                        + JSONObject.quote(COMPILER_PACKAGE)
                        + ",\"compilerVersion\":"
                        + JSONObject.quote(authority.compilerVersion)
                        + ",\"compilerDistributionFingerprint\":"
                        + JSONObject.quote(
                                authority.compilerDistributionFingerprint)
                        + ",\"declarationsFingerprint\":"
                        + JSONObject.quote(authority.declarationsFingerprint)
                        + ",\"declarationProfileFingerprint\":"
                        + JSONObject.quote(authority.declarationProfileFingerprint)
                        + ",\"configKind\":"
                        + JSONObject.quote(CONFIG_KIND_NONE)
                        + ",\"callerInventoryFingerprint\":"
                        + JSONObject.quote(inventoryFingerprint)
                        + ",\"rootPaths\":["
                        + JSONObject.quote(SOURCE_PATH)
                        + "],\"files\":[{\"path\":"
                        + JSONObject.quote(SOURCE_PATH)
                        + ",\"sha256\":"
                        + JSONObject.quote(sourceSha256)
                        + ",\"size\":"
                        + sourceBytes
                        + "}]}";
        return manifest.getBytes(StandardCharsets.UTF_8);
    }

    private static String inventoryFingerprint(
            String path, long size, String sha256) {
        MessageDigest digest = sha256Digest();
        digest.update(
                "autojs6.typescript.input-inventory.v1\n"
                        .getBytes(StandardCharsets.US_ASCII));
        digest.update("source".getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) 0);
        digest.update(("/" + path).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(
                Long.toString(size).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) 0);
        digest.update(sha256.getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) '\n');
        return lowerHex(digest.digest());
    }

    private static String writeStoredZip32(
            File file, List<ArchiveEntry> entries) throws Exception {
        require(!entries.isEmpty(), "ZIP32 fixture has no entries.");
        MessageDigest archiveDigest = sha256Digest();
        try (FileOutputStream fileOutput = new FileOutputStream(file, false)) {
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
        require(file.isFile(), "ZIP32 fixture is not a regular file.");
        require(
                expectedArchiveSha256.equals(sha256(file)),
                "ZIP32 archive digest changed after fsync.");
        try (ZipFile zip = new ZipFile(file)) {
            Set<String> expectedNames = new HashSet<>();
            for (ArchiveEntry entry : expectedEntries) {
                expectedNames.add(entry.path);
            }
            Set<String> actualNames = new HashSet<>();
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                require(
                        actualNames.add(entry.getName()),
                        "ZIP32 fixture contains a duplicate entry.");
            }
            require(
                    expectedNames.equals(actualNames),
                    "ZIP32 fixture inventory differs: " + actualNames);
            for (ArchiveEntry expected : expectedEntries) {
                ZipEntry actual = zip.getEntry(expected.path);
                require(actual != null, "ZIP32 fixture entry is missing.");
                require(
                        actual.getMethod() == ZipEntry.STORED,
                        "ZIP32 fixture entry is not STORED.");
                require(
                        actual.getSize() == expected.bytes.length
                                && actual.getCompressedSize()
                                        == expected.bytes.length,
                        "ZIP32 fixture entry size differs.");
                require(
                        actual.getCrc() == expected.metrics.crc32,
                        "ZIP32 fixture entry CRC differs.");
                try (InputStream input = zip.getInputStream(actual)) {
                    require(
                            expected.metrics.sha256.equals(sha256(input)),
                            "ZIP32 fixture entry SHA-256 differs.");
                }
            }
        }
    }

    private static byte[] asciiZipName(String path) {
        byte[] bytes = path.getBytes(StandardCharsets.US_ASCII);
        require(
                path.equals(new String(bytes, StandardCharsets.US_ASCII)),
                "ZIP32 entry path is not ASCII.");
        return bytes;
    }

    private static String sha256(File file) throws Exception {
        try (InputStream input = new FileInputStream(file)) {
            return sha256(input);
        }
    }

    private static String sha256(InputStream input) throws Exception {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        int read;
        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
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
            result.append(
                    Character.forDigit((value >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }

    private static void deleteExactly(File file, String label) {
        require(
                !file.exists() || file.delete(),
                "Could not delete " + label + ".");
        require(!file.exists(), "Deleted " + label + " still exists.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static final class CompilerAuthority {

        final int profileRevision;
        final int configSchemaRevision;
        final String compilerVersion;
        final String compilerDistributionFingerprint;
        final String declarationsFingerprint;
        final String declarationProfileFingerprint;
        final String runtimeCapabilitiesFingerprint;

        CompilerAuthority(
                int profileRevision,
                int configSchemaRevision,
                String compilerVersion,
                String compilerDistributionFingerprint,
                String declarationsFingerprint,
                String declarationProfileFingerprint,
                String runtimeCapabilitiesFingerprint) {
            this.profileRevision = profileRevision;
            this.configSchemaRevision = configSchemaRevision;
            this.compilerVersion = compilerVersion;
            this.compilerDistributionFingerprint =
                    compilerDistributionFingerprint;
            this.declarationsFingerprint = declarationsFingerprint;
            this.declarationProfileFingerprint = declarationProfileFingerprint;
            this.runtimeCapabilitiesFingerprint =
                    runtimeCapabilitiesFingerprint;
        }
    }

    private static final class DistributionBoundary {

        final int standardLibraryFiles;
        final long standardLibraryBytes;

        DistributionBoundary(
                int standardLibraryFiles, long standardLibraryBytes) {
            this.standardLibraryFiles = standardLibraryFiles;
            this.standardLibraryBytes = standardLibraryBytes;
        }

        static DistributionBoundary load(
                Context pluginContext, String compilerVersion)
                throws Exception {
            String path =
                    "typescript/"
                            + compilerVersion
                            + "/distribution.json";
            byte[] bytes;
            try (InputStream input =
                    pluginContext.getAssets().open(path)) {
                bytes = readAllBounded(input, 2 * 1024 * 1024);
            }
            JSONObject standardLibraries =
                    new JSONObject(
                                    new String(
                                            bytes,
                                            StandardCharsets.UTF_8))
                            .getJSONObject("components")
                            .getJSONObject("standardLibraries");
            return new DistributionBoundary(
                    standardLibraries.getInt("fileCount"),
                    standardLibraries.getLong("totalBytes"));
        }
    }

    private static byte[] readAllBounded(
            InputStream input, int maximumBytes) throws Exception {
        byte[] result = new byte[maximumBytes];
        int offset = 0;
        while (offset < result.length) {
            int read = input.read(result, offset, result.length - offset);
            if (read == -1) {
                return Arrays.copyOf(result, offset);
            }
            offset += read;
        }
        require(input.read() == -1, "Bounded input exceeds its limit.");
        return result;
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
