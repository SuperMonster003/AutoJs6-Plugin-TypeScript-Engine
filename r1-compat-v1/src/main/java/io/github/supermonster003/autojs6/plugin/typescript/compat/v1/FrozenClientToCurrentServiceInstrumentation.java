package io.github.supermonster003.autojs6.plugin.typescript.compat.v1;

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
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback;
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin;

/**
 * Framework-only old client, compiled solely from compat/v1/typescript-api-v1.aar.
 *
 * <p>The generated v1 proxy exercises the complete six-transaction prefix against the current
 * service. No AndroidX, JUnit, project dependency, or current API classes enter this APK.
 */
public final class FrozenClientToCurrentServiceInstrumentation extends Instrumentation {

    private static final String ACTION_COMPILER = "org.autojs.plugin.typescript.COMPILER";
    private static final String CATEGORY_TYPESCRIPT = "typescript";
    private static final String CURRENT_PACKAGE =
            "io.github.supermonster003.autojs6.plugin.typescript";
    private static final String CONTRACT_HASH =
            "e35e8c6f71b8e0799678974e95aca9233225b3371c4a484437c7740defe9f86b";
    private static final int CONTRACT_VERSION = 1;
    private static final String ERROR_CONTRACT_MISMATCH =
            "ERR_AUTOJS6_TYPESCRIPT_CONTRACT_MISMATCH";
    private static final String ERROR_R0_NOT_AVAILABLE =
            "ERR_AUTOJS6_TYPESCRIPT_R0_NOT_AVAILABLE";
    private static final byte[] INPUT_BYTES = "frozen-v1-input".getBytes();
    private static final byte[] OUTPUT_BYTES = "frozen-v1-output-sentinel".getBytes();

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            runCompatibilityGate();
            result.putString(
                    "stream",
                    "Frozen v1 client -> current service passed: six-transaction prefix, "
                            + "unknown legal String[], R1 reject, caller FD preservation, "
                            + "zero callback.\n");
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
    private void runCompatibilityGate() throws Exception {
        Context context = getTargetContext();
        Intent discovery =
                new Intent(ACTION_COMPILER)
                        .addCategory(CATEGORY_TYPESCRIPT)
                        .setPackage(CURRENT_PACKAGE);
        List<ResolveInfo> matches = context.getPackageManager().queryIntentServices(discovery, 0);
        require(matches.size() == 1, "Expected exactly one current compiler service.");
        ComponentName component =
                new ComponentName(
                        matches.get(0).serviceInfo.packageName,
                        matches.get(0).serviceInfo.name);

        AwaitingConnection connection = new AwaitingConnection();
        boolean bound = false;
        File inputFile = null;
        File outputFile = null;
        try {
            bound =
                    context.bindService(
                            new Intent(discovery).setComponent(component),
                            connection,
                            Context.BIND_AUTO_CREATE);
            require(bound, "Failed to bind current compiler service.");
            IBinder rawBinder = connection.awaitBinder();
            require(rawBinder.pingBinder(), "Current compiler Binder is not alive.");
            require(
                    ITypeScriptCompilerPlugin.DESCRIPTOR.equals(
                            rawBinder.getInterfaceDescriptor()),
                    "Compiler Binder descriptor drifted.");
            require(
                    rawBinder.queryLocalInterface(ITypeScriptCompilerPlugin.DESCRIPTOR) == null,
                    "Compatibility gate did not cross into the compiler process.");

            ITypeScriptCompilerPlugin compiler =
                    ITypeScriptCompilerPlugin.Stub.asInterface(rawBinder);
            require(compiler != null, "Frozen v1 AIDL proxy is null.");

            // Frozen transaction prefix codes 1, 2, and 3.
            require(
                    compiler.getContractVersion() == CONTRACT_VERSION,
                    "Transaction 1 contract version drifted.");
            require(
                    CONTRACT_HASH.equals(compiler.getContractHash()),
                    "Transaction 2 contract hash drifted.");
            Bundle info = compiler.getCompilerInfo();
            require(
                    info.getInt("contractVersion", -1) == CONTRACT_VERSION,
                    "Transaction 3 compilerInfo version drifted.");
            require(
                    CONTRACT_HASH.equals(info.getString("contractHash")),
                    "Transaction 3 compilerInfo hash drifted.");

            // Frozen transaction 4, intentionally rejected before the compiler host starts.
            Bundle mismatchedPrewarm = new Bundle();
            mismatchedPrewarm.putInt("contractVersion", CONTRACT_VERSION + 1);
            mismatchedPrewarm.putString("contractHash", CONTRACT_HASH);
            mismatchedPrewarm.putLong("timeoutMs", 1L);
            Bundle prewarm = compiler.prewarm(mismatchedPrewarm);
            require(
                    ERROR_CONTRACT_MISMATCH.equals(prewarm.getString("errorCode")),
                    "Transaction 4 no longer returns the stable contract mismatch.");

            inputFile = stagingFile(context, "input", INPUT_BYTES);
            outputFile = stagingFile(context, "output", OUTPUT_BYTES);
            try (ParcelFileDescriptor input =
                            ParcelFileDescriptor.open(
                                    inputFile, ParcelFileDescriptor.MODE_READ_ONLY);
                    ParcelFileDescriptor output =
                            ParcelFileDescriptor.open(
                                    outputFile, ParcelFileDescriptor.MODE_READ_WRITE)) {
                RecordingCallback callback = new RecordingCallback();
                Bundle request = validRequest();
                request.putStringArray(
                        "futureOptionalMetadata",
                        new String[] {"alpha", null, "omega"});

                // Frozen transaction 5. The current service must ignore the legal unknown key.
                Bundle ticket =
                        compiler.startCompilation(request, input, output, callback);
                require(!ticket.getBoolean("accepted", true), "R1 request was unexpectedly accepted.");
                require(
                        ERROR_R0_NOT_AVAILABLE.equals(ticket.getString("errorCode")),
                        "Unknown legal metadata was not ignored before the stable R1 reject.");
                require(
                        "compat-v1-request".equals(ticket.getString("requestId")),
                        "Rejected ticket lost the request ID.");
                callback.assertNoCallback();

                require(input.getFileDescriptor().valid(), "Caller input FD was closed.");
                require(output.getFileDescriptor().valid(), "Caller output FD was closed.");
                assertDescriptorBytes(input, INPUT_BYTES);
                assertDescriptorBytes(output, OUTPUT_BYTES);
            }

            // Frozen transaction 6.
            Bundle cancellation = new Bundle();
            cancellation.putInt("contractVersion", CONTRACT_VERSION);
            cancellation.putString("contractHash", CONTRACT_HASH);
            cancellation.putString("requestId", "compat-v1-request");
            cancellation.putString("jobToken", "fixture-only-job-token");
            require(
                    !compiler.cancelCompilation(cancellation),
                    "R1 rejected request unexpectedly became cancellable.");
        } finally {
            if (bound) {
                context.unbindService(connection);
            }
            if (inputFile != null) {
                inputFile.delete();
            }
            if (outputFile != null) {
                outputFile.delete();
            }
        }
    }

    private static Bundle validRequest() {
        Bundle request = new Bundle();
        request.putInt("contractVersion", CONTRACT_VERSION);
        request.putString("contractHash", CONTRACT_HASH);
        request.putInt("inputArchiveTransportVersion", 1);
        request.putInt("outputArchiveTransportVersion", 1);
        request.putString("requestId", "compat-v1-request");
        request.putString("projectId", "compat-v1-project");
        request.putString("entrySourceUri", "autojs://source/compat-v1/main.ts");
        request.putString("targetProfile", "rhino");
        request.putString("compilationMode", "development");
        request.putString("compilerOptionsJson", "{}");
        request.putLong("timeoutMs", 30_000L);
        return request;
    }

    private static File stagingFile(Context context, String suffix, byte[] bytes)
            throws Exception {
        File file = File.createTempFile("r1-compat-v1-" + suffix + "-", ".archive", context.getCacheDir());
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
        return file;
    }

    private static void assertDescriptorBytes(
            ParcelFileDescriptor descriptor, byte[] expected) throws Exception {
        Os.lseek(descriptor.getFileDescriptor(), 0L, OsConstants.SEEK_SET);
        byte[] actual = new byte[expected.length];
        int offset = 0;
        while (offset < actual.length) {
            int read =
                    Os.read(
                            descriptor.getFileDescriptor(),
                            actual,
                            offset,
                            actual.length - offset);
            if (read <= 0) {
                break;
            }
            offset += read;
        }
        require(offset == expected.length, "Descriptor content was truncated.");
        for (int index = 0; index < expected.length; index++) {
            require(actual[index] == expected[index], "Descriptor content changed at " + index + ".");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
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
            // Liveness is asserted after connection.
        }

        @Override
        public void onNullBinding(ComponentName name) {
            connected.countDown();
        }

        IBinder awaitBinder() throws InterruptedException {
            require(connected.await(5, TimeUnit.SECONDS), "Timed out waiting for service.");
            require(binder != null, "Current service returned a null binding.");
            return binder;
        }
    }

    private static final class RecordingCallback
            extends ITypeScriptCompilationCallback.Stub {

        private final CountDownLatch signal = new CountDownLatch(1);
        private final AtomicInteger progressCount = new AtomicInteger();
        private final AtomicInteger completionCount = new AtomicInteger();

        @Override
        public void onProgress(Bundle progress) {
            progressCount.incrementAndGet();
            signal.countDown();
        }

        @Override
        public void onCompleted(Bundle result) {
            completionCount.incrementAndGet();
            signal.countDown();
        }

        void assertNoCallback() throws InterruptedException {
            require(
                    !signal.await(250L, TimeUnit.MILLISECONDS),
                    "R1 rejection unexpectedly delivered a callback.");
            require(progressCount.get() == 0, "R1 rejection delivered progress.");
            require(completionCount.get() == 0, "R1 rejection delivered completion.");
        }
    }
}
