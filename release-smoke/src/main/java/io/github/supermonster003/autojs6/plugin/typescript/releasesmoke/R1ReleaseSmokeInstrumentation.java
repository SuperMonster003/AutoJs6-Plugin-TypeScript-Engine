package io.github.supermonster003.autojs6.plugin.typescript.releasesmoke;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback;
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin;

/**
 * Framework-only smoke runner for the minified, signed release target.
 *
 * <p>The regular AndroidX test APK shares Kotlin runtime classes with its debug target. R8 may
 * legitimately remove those classes from release, so this runner intentionally uses only Android
 * framework and Java APIs plus the kept public AIDL surface. The smoke constructs a formal public
 * Rhino request rather than relying on target-private fixture code.
 */
public final class R1ReleaseSmokeInstrumentation extends Instrumentation {

    private static final String ACTION_COMPILER = "org.autojs.plugin.typescript.COMPILER";
    private static final String ARCHIVE_MANIFEST_PATH = "request-manifest.json";
    private static final String BACKEND_WEBVIEW_WORKER = "webview_worker";
    private static final String CATEGORY_TYPESCRIPT = "typescript";
    private static final String COMPILATION_MODE_DEVELOPMENT = "development";
    private static final String CONFIG_KIND_NONE = "none";
    private static final String CONTRACT_HASH =
            "e35e8c6f71b8e0799678974e95aca9233225b3371c4a484437c7740defe9f86b";
    private static final int CONTRACT_VERSION = 1;
    private static final String EXPECTED_DECLARATIONS_FINGERPRINT =
            "6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f";
    private static final String EXPECTED_DISTRIBUTION_FINGERPRINT =
            "3855b04b968db8a9529b60c96dd8b8ec94fa1ee040cac10a5ab569aa6e33f658";
    private static final String EXPECTED_TYPESCRIPT_VERSION = "6.0.3";
    private static final String ERROR_COMPILER_UNAVAILABLE =
            "ERR_AUTOJS6_TYPESCRIPT_COMPILER_UNAVAILABLE";
    private static final String INPUT_ARCHIVE_SCHEMA = "autojs6.typescript.input-archive";
    private static final int INPUT_ARCHIVE_SCHEMA_REVISION = 1;
    private static final int INPUT_ARCHIVE_TRANSPORT_VERSION = 1;
    private static final int INPUT_QUOTA_REVISION = 3;
    private static final String INVENTORY_PREFIX = "autojs6.typescript.input-inventory.v1\n";
    private static final String KEY_ACCEPTED = "accepted";
    private static final String KEY_ACKNOWLEDGEMENT_REQUIRED = "acknowledgementRequired";
    private static final String KEY_BACKEND = "backend";
    private static final String KEY_CANCELLATION_OPERATION = "cancellationOperation";
    private static final String KEY_COMPILATION_MODE = "compilationMode";
    private static final String KEY_COMPILER_AVAILABLE = "typescriptCompilerAvailable";
    private static final String KEY_COMPILER_DISTRIBUTION_FINGERPRINT =
            "compilerDistributionFingerprint";
    private static final String KEY_COMPILER_OPTIONS_JSON = "compilerOptionsJson";
    private static final String KEY_COMPILER_VERSION = "typescriptCompilerVersion";
    private static final String KEY_CONFIG_KIND = "configKind";
    private static final String KEY_CONFIG_SCHEMA_REVISION = "configSchemaRevision";
    private static final String KEY_CONTRACT_HASH = "contractHash";
    private static final String KEY_CONTRACT_VERSION = "contractVersion";
    private static final String KEY_DECLARATION_PROFILE_FINGERPRINT =
            "declarationProfileFingerprint";
    private static final String KEY_DECLARATIONS_FINGERPRINT = "declarationsFingerprint";
    private static final String KEY_DIAGNOSTIC_COUNT = "diagnosticCount";
    private static final String KEY_EMITTED_MODULE_COUNT = "emittedModuleCount";
    private static final String KEY_ENTRY_SOURCE_URI = "entrySourceUri";
    private static final String KEY_ERROR_CODE = "errorCode";
    private static final String KEY_ERROR_MESSAGE = "errorMessage";
    private static final String KEY_INPUT_ARCHIVE_SCHEMA_REVISION =
            "inputArchiveSchemaRevision";
    private static final String KEY_INPUT_ARCHIVE_SHA256 = "inputArchiveSha256";
    private static final String KEY_INPUT_ARCHIVE_TRANSPORT_VERSION =
            "inputArchiveTransportVersion";
    private static final String KEY_JOB_TOKEN = "jobToken";
    private static final String KEY_NODE_RUNTIME_REQUIRED = "typescriptNodeRuntimeRequired";
    private static final String KEY_OUTCOME = "outcome";
    private static final String KEY_OUTPUT_ARCHIVE_TRANSPORT_VERSION =
            "outputArchiveTransportVersion";
    private static final String KEY_PROFILE_REVISION = "profileRevision";
    private static final String KEY_PROFILE_SCHEMA_REVISION = "profileSchemaRevision";
    private static final String KEY_PROJECT_COMPILATION = "typescriptProjectCompilation";
    private static final String KEY_PROJECT_ID = "projectId";
    private static final String KEY_REQUEST_ID = "requestId";
    private static final String KEY_REQUEST_MANIFEST_SHA256 = "requestManifestSha256";
    private static final String KEY_RUNTIME_CAPABILITIES_FINGERPRINT =
            "runtimeCapabilitiesFingerprint";
    private static final String KEY_RUNTIME_CAPABILITY_SCHEMA_REVISION =
            "runtimeCapabilitySchemaRevision";
    private static final String KEY_SEMANTIC_DIAGNOSTICS = "typescriptSemanticDiagnostics";
    private static final String KEY_SERVICE_STATUS = "typescriptCompilerServiceStatus";
    private static final String KEY_SOURCE_MAP_V3 = "typescriptSourceMapV3";
    private static final String KEY_STAGE = "stage";
    private static final String KEY_SUCCEEDED = "succeeded";
    private static final String KEY_TARGET_PROFILE = "targetProfile";
    private static final String KEY_TIMEOUT_MS = "timeoutMs";
    private static final String OPERATION_ACKNOWLEDGE = "acknowledge";
    private static final String OUTCOME_EMITTED = "emitted";
    private static final int OUTPUT_ARCHIVE_TRANSPORT_VERSION = 1;
    private static final String OUTPUT_DIAGNOSTICS_PATH = "diagnostics.json";
    private static final String OUTPUT_MANIFEST_PATH = "compilation-manifest.json";
    private static final int PROFILE_SCHEMA_REVISION = 1;
    private static final String PROFILE_RHINO = "rhino";
    private static final int RUNTIME_CAPABILITY_SCHEMA_REVISION = 1;
    private static final String SOURCE_PATH = "sources/main.ts";
    private static final String STATUS_READY = "ready";
    private static final long TIMEOUT_MS = 120_000L;

    private static final int VERSION_STORED = 10;
    private static final int FLAG_UTF8 = 1 << 11;
    private static final long LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L;
    private static final long CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L;
    private static final long END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            runSmoke();
            result.putString(
                    "stream",
                    "S6 signed release discovery, mask-15 metadata, and hello.ts Program "
                            + "compilation passed.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            result.putString("shortMsg", error.toString());
            result.putString("stream", trace.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    @SuppressWarnings("deprecation")
    private void runSmoke() throws Exception {
        Context targetContext = getTargetContext();
        Intent discovery = new Intent(ACTION_COMPILER)
                .addCategory(CATEGORY_TYPESCRIPT)
                .setPackage(targetContext.getPackageName());
        List<ResolveInfo> matches =
                targetContext.getPackageManager().queryIntentServices(discovery, 0);
        require(matches.size() == 1, "Expected exactly one compiler service.");
        ComponentName component = new ComponentName(
                matches.get(0).serviceInfo.packageName,
                matches.get(0).serviceInfo.name);
        require(
                (targetContext.getPackageName() + ":typescript_compiler")
                        .equals(matches.get(0).serviceInfo.processName),
                "Compiler service escaped the dedicated process.");

        AwaitingConnection connection = new AwaitingConnection();
        boolean bound = false;
        try {
            bound = targetContext.bindService(
                    new Intent(discovery).setComponent(component),
                    connection,
                    Context.BIND_AUTO_CREATE);
            require(bound, "Failed to bind the compiler service.");
            IBinder rawBinder = connection.awaitBinder();
            require(rawBinder.pingBinder(), "Compiler Binder is not alive.");
            require(
                    rawBinder.queryLocalInterface(ITypeScriptCompilerPlugin.DESCRIPTOR) == null,
                    "Compiler Binder did not cross into :typescript_compiler.");

            ITypeScriptCompilerPlugin compiler =
                    ITypeScriptCompilerPlugin.Stub.asInterface(rawBinder);
            require(compiler != null, "Compiler AIDL proxy is null.");
            require(
                    compiler.getContractVersion() == CONTRACT_VERSION,
                    "Compiler contract version mismatch.");
            require(
                    CONTRACT_HASH.equals(compiler.getContractHash()),
                    "Compiler contract hash mismatch.");

            Bundle prewarm = prewarmWhenSupervisorReady(compiler);
            validateReadyCompilerInfo(prewarm);

            Bundle compilerInfo = compiler.getCompilerInfo();
            validateReadyCompilerInfo(compilerInfo);
            runRhinoCompilation(compiler, compilerInfo);
        } finally {
            if (bound) {
                targetContext.unbindService(connection);
            }
        }
    }

    private static Bundle prewarmWhenSupervisorReady(
            ITypeScriptCompilerPlugin compiler) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        Bundle result;
        do {
            result = compiler.prewarm(validPrewarmOptions());
            if (!result.containsKey(KEY_ERROR_CODE)) {
                return result;
            }
            require(
                    ERROR_COMPILER_UNAVAILABLE.equals(result.getString(KEY_ERROR_CODE)),
                    describeFailure("prewarm", result));
            SystemClock.sleep(100L);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError(describeFailure("prewarm timeout", result));
    }

    private void validateReadyCompilerInfo(Bundle result) {
        require(
                BACKEND_WEBVIEW_WORKER.equals(result.getString(KEY_BACKEND)),
                "Release compiler backend mismatch.");
        require(
                STATUS_READY.equals(result.getString(KEY_SERVICE_STATUS)),
                "Release compiler host did not become ready.");
        require(
                EXPECTED_DISTRIBUTION_FINGERPRINT.equals(
                        result.getString(KEY_COMPILER_DISTRIBUTION_FINGERPRINT)),
                "Release distribution fingerprint mismatch.");
        require(
                EXPECTED_DECLARATIONS_FINGERPRINT.equals(
                        result.getString(KEY_DECLARATIONS_FINGERPRINT)),
                "Release declarations fingerprint mismatch.");
        require(
                EXPECTED_TYPESCRIPT_VERSION.equals(result.getString(KEY_COMPILER_VERSION)),
                "Release TypeScript version mismatch.");
        require(result.getBoolean(KEY_COMPILER_AVAILABLE, false),
                "Release compiler availability is not promoted.");
        require(result.getBoolean(KEY_PROJECT_COMPILATION, false),
                "Release project compilation is not promoted.");
        require(result.getBoolean(KEY_SEMANTIC_DIAGNOSTICS, false),
                "Release semantic diagnostics are not promoted.");
        require(result.getBoolean(KEY_SOURCE_MAP_V3, false),
                "Release Source Map v3 is not promoted.");
        require(!result.getBoolean(KEY_NODE_RUNTIME_REQUIRED, true),
                "Compiler metadata incorrectly requires Node Runtime.");
    }

    private void runRhinoCompilation(
            ITypeScriptCompilerPlugin compiler,
            Bundle compilerInfo) throws Exception {
        String requestId = "s6-release-smoke-" + UUID.randomUUID();
        String projectId = "release-smoke";
        String entrySourceUri = "autojs://source/project/main.ts";
        byte[] source = (
                "export const releaseSmoke: number = 42;\n"
                        + "export const releaseMessage: string = \"value=\" + releaseSmoke;\n")
                .getBytes(StandardCharsets.UTF_8);
        String sourceSha256 = sha256(source);
        String callerInventoryFingerprint = sourceInventoryFingerprint(
                SOURCE_PATH,
                source.length,
                sourceSha256);

        int profileRevision = compilerInfo.getInt(KEY_PROFILE_REVISION, -1);
        require(profileRevision > 0, "Compiler did not advertise a Rhino profile revision.");
        String runtimeFingerprint = requiredString(
                compilerInfo,
                KEY_RUNTIME_CAPABILITIES_FINGERPRINT);
        String distributionFingerprint = requiredString(
                compilerInfo,
                KEY_COMPILER_DISTRIBUTION_FINGERPRINT);
        String declarationsFingerprint = requiredString(
                compilerInfo,
                KEY_DECLARATIONS_FINGERPRINT);
        String declarationProfileFingerprint = requiredString(
                compilerInfo,
                KEY_DECLARATION_PROFILE_FINGERPRINT);

        String manifestJson =
                "{\"schema\":\"" + INPUT_ARCHIVE_SCHEMA + "\","
                        + "\"revision\":" + INPUT_ARCHIVE_SCHEMA_REVISION + ","
                        + "\"inputQuotaRevision\":" + INPUT_QUOTA_REVISION + ","
                        + "\"contractVersion\":" + CONTRACT_VERSION + ","
                        + "\"contractHash\":\"" + CONTRACT_HASH + "\","
                        + "\"inputArchiveTransportVersion\":"
                        + INPUT_ARCHIVE_TRANSPORT_VERSION + ","
                        + "\"requestId\":\"" + requestId + "\","
                        + "\"projectId\":\"" + projectId + "\","
                        + "\"entrySourceUri\":\"" + entrySourceUri + "\","
                        + "\"entryPath\":\"" + SOURCE_PATH + "\","
                        + "\"targetProfile\":\"" + PROFILE_RHINO + "\","
                        + "\"compilationMode\":\"" + COMPILATION_MODE_DEVELOPMENT + "\","
                        + "\"profileSchemaRevision\":" + PROFILE_SCHEMA_REVISION + ","
                        + "\"profileRevision\":" + profileRevision + ","
                        + "\"runtimeCapabilitySchemaRevision\":"
                        + RUNTIME_CAPABILITY_SCHEMA_REVISION + ","
                        + "\"configSchemaRevision\":1,"
                        + "\"runtimeCapabilitiesFingerprint\":\""
                        + runtimeFingerprint + "\","
                        + "\"compilerPackage\":\"typescript\","
                        + "\"compilerVersion\":\"" + EXPECTED_TYPESCRIPT_VERSION + "\","
                        + "\"compilerDistributionFingerprint\":\""
                        + distributionFingerprint + "\","
                        + "\"declarationsFingerprint\":\"" + declarationsFingerprint + "\","
                        + "\"declarationProfileFingerprint\":\""
                        + declarationProfileFingerprint + "\","
                        + "\"configKind\":\"" + CONFIG_KIND_NONE + "\","
                        + "\"callerInventoryFingerprint\":\""
                        + callerInventoryFingerprint + "\","
                        + "\"rootPaths\":[\"" + SOURCE_PATH + "\"],"
                        + "\"files\":[{\"path\":\"" + SOURCE_PATH + "\","
                        + "\"sha256\":\"" + sourceSha256 + "\","
                        + "\"size\":" + source.length + "}]}";
        byte[] manifest = manifestJson.getBytes(StandardCharsets.UTF_8);

        File cacheDirectory = getTargetContext().getCacheDir();
        require(
                cacheDirectory.isDirectory() || cacheDirectory.mkdirs(),
                "Could not create the target cache directory for release smoke.");
        File input = File.createTempFile("s6-release-input-", ".zip", cacheDirectory);
        File output = File.createTempFile("s6-release-output-", ".zip", cacheDirectory);
        try {
            LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
            entries.put(ARCHIVE_MANIFEST_PATH, manifest);
            entries.put(SOURCE_PATH, source);
            writeStoredZip(input, entries);
            try (FileOutputStream sentinel = new FileOutputStream(output, false)) {
                sentinel.write("release-output-sentinel".getBytes(StandardCharsets.US_ASCII));
                sentinel.getFD().sync();
            }

            Bundle request = publicRhinoRequest(
                    requestId,
                    projectId,
                    entrySourceUri,
                    profileRevision,
                    runtimeFingerprint,
                    distributionFingerprint,
                    declarationsFingerprint,
                    declarationProfileFingerprint,
                    sha256(readFile(input)),
                    sha256(manifest));
            RecordingCallback callback = new RecordingCallback();
            Bundle ticket;
            try (ParcelFileDescriptor inputDescriptor = ParcelFileDescriptor.open(
                            input,
                            ParcelFileDescriptor.MODE_READ_ONLY);
                    ParcelFileDescriptor outputDescriptor = ParcelFileDescriptor.open(
                            output,
                            ParcelFileDescriptor.MODE_READ_WRITE)) {
                ticket = compiler.startCompilation(
                        request,
                        inputDescriptor,
                        outputDescriptor,
                        callback);
            }
            callback.markStartReturned();
            require(ticket.getBoolean(KEY_ACCEPTED, false), describeFailure("admission", ticket));
            require(ticket.getBoolean(KEY_ACKNOWLEDGEMENT_REQUIRED, false),
                    "Public compilation did not require acknowledgement.");
            String jobToken = requiredString(ticket, KEY_JOB_TOKEN);
            require(
                    compiler.cancelCompilation(acknowledgement(requestId, jobToken)),
                    "Compiler rejected the public acknowledgement.");

            Bundle terminal = callback.awaitTerminal();
            require(!callback.callbackBeforeStartReturned,
                    "Compiler callback arrived before startCompilation returned.");
            require(callback.terminalCount.get() == 1,
                    "Compiler did not publish exactly one terminal callback.");
            require(OUTCOME_EMITTED.equals(terminal.getString(KEY_OUTCOME)),
                    describeFailure("terminal", terminal));
            require(terminal.getBoolean(KEY_SUCCEEDED, false),
                    describeFailure("terminal", terminal));
            require(terminal.getInt(KEY_DIAGNOSTIC_COUNT, -1) == 0,
                    "hello.ts produced diagnostics.");
            require(terminal.getInt(KEY_EMITTED_MODULE_COUNT, 0) == 1,
                    "hello.ts did not emit exactly one JavaScript module.");
            validateOutputArchive(output);
        } finally {
            require(!input.exists() || input.delete(), "Could not delete release-smoke input.");
            require(!output.exists() || output.delete(), "Could not delete release-smoke output.");
        }
    }

    private static Bundle publicRhinoRequest(
            String requestId,
            String projectId,
            String entrySourceUri,
            int profileRevision,
            String runtimeFingerprint,
            String distributionFingerprint,
            String declarationsFingerprint,
            String declarationProfileFingerprint,
            String inputArchiveSha256,
            String requestManifestSha256) {
        Bundle request = new Bundle();
        request.putInt(KEY_CONTRACT_VERSION, CONTRACT_VERSION);
        request.putString(KEY_CONTRACT_HASH, CONTRACT_HASH);
        request.putInt(
                KEY_INPUT_ARCHIVE_TRANSPORT_VERSION,
                INPUT_ARCHIVE_TRANSPORT_VERSION);
        request.putInt(
                KEY_OUTPUT_ARCHIVE_TRANSPORT_VERSION,
                OUTPUT_ARCHIVE_TRANSPORT_VERSION);
        request.putString(KEY_REQUEST_ID, requestId);
        request.putString(KEY_PROJECT_ID, projectId);
        request.putString(KEY_ENTRY_SOURCE_URI, entrySourceUri);
        request.putString(KEY_TARGET_PROFILE, PROFILE_RHINO);
        request.putString(KEY_COMPILATION_MODE, COMPILATION_MODE_DEVELOPMENT);
        request.putString(KEY_COMPILER_OPTIONS_JSON, "{}");
        request.putLong(KEY_TIMEOUT_MS, TIMEOUT_MS);
        request.putInt(KEY_PROFILE_SCHEMA_REVISION, PROFILE_SCHEMA_REVISION);
        request.putInt(KEY_INPUT_ARCHIVE_SCHEMA_REVISION, INPUT_ARCHIVE_SCHEMA_REVISION);
        request.putInt(
                KEY_RUNTIME_CAPABILITY_SCHEMA_REVISION,
                RUNTIME_CAPABILITY_SCHEMA_REVISION);
        request.putString(KEY_COMPILER_DISTRIBUTION_FINGERPRINT, distributionFingerprint);
        request.putString(KEY_DECLARATIONS_FINGERPRINT, declarationsFingerprint);
        request.putString(
                KEY_DECLARATION_PROFILE_FINGERPRINT,
                declarationProfileFingerprint);
        request.putString(KEY_RUNTIME_CAPABILITIES_FINGERPRINT, runtimeFingerprint);
        request.putInt(KEY_PROFILE_REVISION, profileRevision);
        request.putInt(KEY_CONFIG_SCHEMA_REVISION, 1);
        request.putString(KEY_CONFIG_KIND, CONFIG_KIND_NONE);
        request.putString(KEY_INPUT_ARCHIVE_SHA256, inputArchiveSha256);
        request.putString(KEY_REQUEST_MANIFEST_SHA256, requestManifestSha256);
        return request;
    }

    private static Bundle acknowledgement(String requestId, String jobToken) {
        Bundle acknowledgement = new Bundle();
        acknowledgement.putInt(KEY_CONTRACT_VERSION, CONTRACT_VERSION);
        acknowledgement.putString(KEY_CONTRACT_HASH, CONTRACT_HASH);
        acknowledgement.putString(KEY_REQUEST_ID, requestId);
        acknowledgement.putString(KEY_JOB_TOKEN, jobToken);
        acknowledgement.putString(KEY_CANCELLATION_OPERATION, OPERATION_ACKNOWLEDGE);
        return acknowledgement;
    }

    private static void validateOutputArchive(File output) throws Exception {
        Set<String> names = new LinkedHashSet<>();
        String emittedJavaScript;
        try (ZipFile archive = new ZipFile(output)) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
            require(names.contains(OUTPUT_MANIFEST_PATH),
                    "Output archive is missing its manifest.");
            require(names.contains(OUTPUT_DIAGNOSTICS_PATH),
                    "Output archive is missing diagnostics.");
            require(names.contains("modules/main.js"),
                    "Output archive is missing modules/main.js.");
            require(names.contains("maps/main.js.map"),
                    "Output archive is missing maps/main.js.map.");
            ZipEntry module = archive.getEntry("modules/main.js");
            try (InputStream stream = archive.getInputStream(module)) {
                emittedJavaScript = new String(readAll(stream), StandardCharsets.UTF_8);
            }
        }
        require(emittedJavaScript.contains("releaseSmoke"),
                "Emitted JavaScript does not contain the hello.ts binding.");
        require(emittedJavaScript.contains("sourceMappingURL="),
                "Emitted JavaScript does not reference its external Source Map.");
    }

    private static String sourceInventoryFingerprint(
            String path,
            long size,
            String sourceSha256) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(INVENTORY_PREFIX.getBytes(StandardCharsets.US_ASCII));
        digest.update("source".getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) 0);
        digest.update(("/" + path).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(Long.toString(size).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) 0);
        digest.update(sourceSha256.getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) '\n');
        return toLowerHex(digest.digest());
    }

    private static void writeStoredZip(
            File file,
            LinkedHashMap<String, byte[]> entries) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            List<StoredRecord> records = new ArrayList<>(entries.size());
            long position = 0L;
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                byte[] name = entry.getKey().getBytes(StandardCharsets.UTF_8);
                byte[] bytes = entry.getValue();
                int flags = containsNonAscii(name) ? FLAG_UTF8 : 0;
                CRC32 crc32 = new CRC32();
                crc32.update(bytes);
                long crc = crc32.getValue();
                records.add(new StoredRecord(name, bytes, flags, crc, position));
                putU32(output, LOCAL_FILE_HEADER_SIGNATURE);
                putU16(output, VERSION_STORED);
                putU16(output, flags);
                putU16(output, 0);
                putU16(output, 0);
                putU16(output, 0);
                putU32(output, crc);
                putU32(output, bytes.length);
                putU32(output, bytes.length);
                putU16(output, name.length);
                putU16(output, 0);
                output.write(name);
                output.write(bytes);
                position += 30L + name.length + bytes.length;
            }
            long centralOffset = position;
            for (StoredRecord record : records) {
                putU32(output, CENTRAL_DIRECTORY_SIGNATURE);
                putU16(output, VERSION_STORED);
                putU16(output, VERSION_STORED);
                putU16(output, record.flags);
                putU16(output, 0);
                putU16(output, 0);
                putU16(output, 0);
                putU32(output, record.crc);
                putU32(output, record.bytes.length);
                putU32(output, record.bytes.length);
                putU16(output, record.name.length);
                putU16(output, 0);
                putU16(output, 0);
                putU16(output, 0);
                putU16(output, 0);
                putU32(output, 0);
                putU32(output, record.localOffset);
                output.write(record.name);
                position += 46L + record.name.length;
            }
            putU32(output, END_OF_CENTRAL_DIRECTORY_SIGNATURE);
            putU16(output, 0);
            putU16(output, 0);
            putU16(output, records.size());
            putU16(output, records.size());
            putU32(output, position - centralOffset);
            putU32(output, centralOffset);
            putU16(output, 0);
            output.getFD().sync();
        }
    }

    private static boolean containsNonAscii(byte[] bytes) {
        for (byte value : bytes) {
            if ((value & 0x80) != 0) {
                return true;
            }
        }
        return false;
    }

    private static void putU16(FileOutputStream output, int value) throws Exception {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }

    private static void putU32(FileOutputStream output, long value) throws Exception {
        output.write((int) (value & 0xff));
        output.write((int) ((value >>> 8) & 0xff));
        output.write((int) ((value >>> 16) & 0xff));
        output.write((int) ((value >>> 24) & 0xff));
    }

    private static Bundle validPrewarmOptions() {
        Bundle options = new Bundle();
        options.putInt(KEY_CONTRACT_VERSION, CONTRACT_VERSION);
        options.putString(KEY_CONTRACT_HASH, CONTRACT_HASH);
        options.putLong(KEY_TIMEOUT_MS, TIMEOUT_MS);
        return options;
    }

    private static String requiredString(Bundle bundle, String key) {
        String value = bundle.getString(key);
        require(value != null && !value.isEmpty(), "Missing required Bundle key: " + key);
        return value;
    }

    private static String describeFailure(String phase, Bundle result) {
        return phase
                + " failed: stage="
                + result.getString(KEY_STAGE)
                + ", code="
                + result.getString(KEY_ERROR_CODE)
                + ", message="
                + result.getString(KEY_ERROR_MESSAGE);
    }

    private static byte[] readFile(File file) throws Exception {
        try (InputStream stream = new FileInputStream(file)) {
            return readAll(stream);
        }
    }

    private static byte[] readAll(InputStream stream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return toLowerHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String toLowerHex(byte[] bytes) {
        char[] hex = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = hex[value >>> 4];
            result[index * 2 + 1] = hex[value & 0x0f];
        }
        return new String(result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class StoredRecord {
        final byte[] name;
        final byte[] bytes;
        final int flags;
        final long crc;
        final long localOffset;

        StoredRecord(byte[] name, byte[] bytes, int flags, long crc, long localOffset) {
            this.name = name;
            this.bytes = bytes;
            this.flags = flags;
            this.crc = crc;
            this.localOffset = localOffset;
        }
    }

    private static final class RecordingCallback extends ITypeScriptCompilationCallback.Stub {
        final AtomicInteger terminalCount = new AtomicInteger();
        final CountDownLatch terminal = new CountDownLatch(1);
        final AtomicReference<Bundle> result = new AtomicReference<>();
        volatile boolean startReturned;
        volatile boolean callbackBeforeStartReturned;

        void markStartReturned() {
            startReturned = true;
        }

        @Override
        public void onProgress(Bundle progress) {
            // Progress is intentionally non-authoritative for this smoke.
        }

        @Override
        public void onCompleted(Bundle completion) {
            if (!startReturned) {
                callbackBeforeStartReturned = true;
            }
            terminalCount.incrementAndGet();
            result.compareAndSet(null, completion == null ? new Bundle() : completion);
            terminal.countDown();
        }

        Bundle awaitTerminal() throws InterruptedException {
            require(
                    terminal.await(TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "Timed out waiting for hello.ts compilation.");
            return result.get();
        }
    }

    private static final class AwaitingConnection implements ServiceConnection {

        private final CountDownLatch connected = new CountDownLatch(1);
        private volatile IBinder binder;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = service;
            connected.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // The smoke assertion reads binder liveness after connection.
        }

        @Override
        public void onNullBinding(ComponentName name) {
            connected.countDown();
        }

        IBinder awaitBinder() throws InterruptedException {
            require(
                    connected.await(10, TimeUnit.SECONDS),
                    "Timed out waiting for the compiler service.");
            require(binder != null, "Compiler service returned a null binding.");
            return binder;
        }
    }
}
