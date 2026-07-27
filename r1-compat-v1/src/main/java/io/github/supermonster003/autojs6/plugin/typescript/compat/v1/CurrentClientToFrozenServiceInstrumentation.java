package io.github.supermonster003.autojs6.plugin.typescript.compat.v1;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Current-client to frozen-service gate without a project dependency.
 *
 * <p>The instrumentation deliberately loads the current target APK's AIDL Stub and callback
 * interface from the target ClassLoader. The compat APK itself still has exactly one dependency:
 * the frozen v1 AAR used by the old service and the opposite-direction client.
 */
public final class CurrentClientToFrozenServiceInstrumentation extends Instrumentation {

    private static final String COMPAT_PACKAGE =
            "io.github.supermonster003.autojs6.plugin.typescript.compat.v1";
    private static final String FROZEN_SERVICE =
            COMPAT_PACKAGE + ".FrozenR1CompilerService";
    private static final String COMPILER_INTERFACE =
            "org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin";
    private static final String CALLBACK_INTERFACE =
            "org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback";
    private static final String CONTRACT_HASH =
            "e35e8c6f71b8e0799678974e95aca9233225b3371c4a484437c7740defe9f86b";
    private static final String ERROR_R0_NOT_AVAILABLE =
            "ERR_AUTOJS6_TYPESCRIPT_R0_NOT_AVAILABLE";
    private static final byte[] INPUT_BYTES = "current-client-input".getBytes();
    private static final byte[] OUTPUT_BYTES = "current-client-output-sentinel".getBytes();

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
                    "Current target AIDL -> frozen v1 service passed: six-transaction prefix, "
                            + "unknown legal String[], caller FD preservation, frozen-service "
                            + "close accounting, zero callback.\n");
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
        Intent intent =
                new Intent(FrozenR1CompilerService.ACTION_COMPILER)
                        .setComponent(new ComponentName(COMPAT_PACKAGE, FROZEN_SERVICE));
        AwaitingConnection connection = new AwaitingConnection();
        boolean bound = false;
        File inputFile = null;
        File outputFile = null;
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            require(bound, "Failed to bind the exported frozen v1 service.");
            IBinder rawBinder = connection.awaitBinder();
            require(
                    COMPILER_INTERFACE.equals(rawBinder.getInterfaceDescriptor()),
                    "Frozen service descriptor drifted.");

            ClassLoader currentLoader = context.getClassLoader();
            Class<?> compilerType = currentLoader.loadClass(COMPILER_INTERFACE);
            Class<?> compilerStub = currentLoader.loadClass(COMPILER_INTERFACE + "$Stub");
            Class<?> callbackType = currentLoader.loadClass(CALLBACK_INTERFACE);
            Method asInterface = compilerStub.getMethod("asInterface", IBinder.class);
            Object compiler = asInterface.invoke(null, rawBinder);
            require(compilerType.isInstance(compiler), "Current target AIDL proxy was not loaded.");

            require(
                    ((Integer) invoke(compilerType, compiler, "getContractVersion")).intValue()
                            == 1,
                    "Transaction 1 version drifted.");
            require(
                    CONTRACT_HASH.equals(invoke(compilerType, compiler, "getContractHash")),
                    "Transaction 2 hash drifted.");
            Bundle initialInfo =
                    (Bundle) invoke(compilerType, compiler, "getCompilerInfo");
            require(
                    initialInfo.getInt("compatFixtureOpenArchiveReferenceCount", -1) == 0,
                    "Frozen service started with owned archive references.");

            Bundle prewarmOptions = new Bundle();
            prewarmOptions.putInt("contractVersion", 1);
            prewarmOptions.putString("contractHash", CONTRACT_HASH);
            prewarmOptions.putLong("timeoutMs", 30_000L);
            Bundle prewarm =
                    (Bundle)
                            compilerType
                                    .getMethod("prewarm", Bundle.class)
                                    .invoke(compiler, prewarmOptions);
            require(
                    ERROR_R0_NOT_AVAILABLE.equals(prewarm.getString("errorCode")),
                    "Transaction 4 frozen prewarm rejection drifted.");

            inputFile = stagingFile(context, "input", INPUT_BYTES);
            outputFile = stagingFile(context, "output", OUTPUT_BYTES);
            RecordingCallback callback = new RecordingCallback();
            Object callbackProxy =
                    Proxy.newProxyInstance(
                            currentLoader,
                            new Class<?>[] {callbackType},
                            (proxy, method, arguments) -> {
                                switch (method.getName()) {
                                    case "asBinder":
                                        return callback.binder;
                                    case "onProgress":
                                        callback.progressCount.incrementAndGet();
                                        callback.signal.countDown();
                                        return null;
                                    case "onCompleted":
                                        callback.completionCount.incrementAndGet();
                                        callback.signal.countDown();
                                        return null;
                                    case "hashCode":
                                        return System.identityHashCode(proxy);
                                    case "equals":
                                        return proxy == arguments[0];
                                    case "toString":
                                        return "CurrentTargetCallbackProxy";
                                    default:
                                        throw new UnsupportedOperationException(method.toString());
                                }
                            });

            try (ParcelFileDescriptor input =
                            ParcelFileDescriptor.open(
                                    inputFile, ParcelFileDescriptor.MODE_READ_ONLY);
                    ParcelFileDescriptor output =
                            ParcelFileDescriptor.open(
                                    outputFile, ParcelFileDescriptor.MODE_READ_WRITE)) {
                Bundle request = validRequest();
                request.putStringArray(
                        "futureOptionalMetadata",
                        new String[] {"alpha", null, "omega"});
                Bundle ticket =
                        (Bundle)
                                compilerType
                                        .getMethod(
                                                "startCompilation",
                                                Bundle.class,
                                                ParcelFileDescriptor.class,
                                                ParcelFileDescriptor.class,
                                                callbackType)
                                        .invoke(compiler, request, input, output, callbackProxy);
                require(!ticket.getBoolean("accepted", true), "Frozen R1 service accepted work.");
                require(
                        ERROR_R0_NOT_AVAILABLE.equals(ticket.getString("errorCode")),
                        "Frozen service rejected an unknown legal key as invalid metadata.");
                require(
                        "compat-v1-request".equals(ticket.getString("requestId")),
                        "Frozen service lost the request ID.");
                callback.assertNoCallback();

                require(input.getFileDescriptor().valid(), "Current caller input FD was closed.");
                require(output.getFileDescriptor().valid(), "Current caller output FD was closed.");
                assertDescriptorBytes(input, INPUT_BYTES);
                assertDescriptorBytes(output, OUTPUT_BYTES);
            }

            Bundle releasedInfo =
                    (Bundle) invoke(compilerType, compiler, "getCompilerInfo");
            require(
                    releasedInfo.getInt("compatFixtureOpenArchiveReferenceCount", -1) == 0,
                    "Frozen service retained a transported archive FD.");

            Bundle cancellation = new Bundle();
            cancellation.putInt("contractVersion", 1);
            cancellation.putString("contractHash", CONTRACT_HASH);
            cancellation.putString("requestId", "compat-v1-request");
            cancellation.putString("jobToken", "fixture-only-job-token");
            require(
                    !((Boolean)
                                    compilerType
                                            .getMethod("cancelCompilation", Bundle.class)
                                            .invoke(compiler, cancellation))
                            .booleanValue(),
                    "Transaction 6 unexpectedly cancelled rejected work.");
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

    private static Object invoke(Class<?> interfaceType, Object target, String method)
            throws Exception {
        return interfaceType.getMethod(method).invoke(target);
    }

    private static Bundle validRequest() {
        Bundle request = new Bundle();
        request.putInt("contractVersion", 1);
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
        File file = File.createTempFile("r1-current-" + suffix + "-", ".archive", context.getCacheDir());
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

    private static final class RecordingCallback {

        final IBinder binder = new android.os.Binder();
        final CountDownLatch signal = new CountDownLatch(1);
        final AtomicInteger progressCount = new AtomicInteger();
        final AtomicInteger completionCount = new AtomicInteger();

        void assertNoCallback() throws InterruptedException {
            require(
                    !signal.await(250L, TimeUnit.MILLISECONDS),
                    "Frozen R1 rejection unexpectedly delivered a callback.");
            require(progressCount.get() == 0, "Frozen R1 rejection delivered progress.");
            require(completionCount.get() == 0, "Frozen R1 rejection delivered completion.");
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
            require(connected.await(5L, TimeUnit.SECONDS), "Timed out waiting for service.");
            require(binder != null, "Frozen service returned a null binding.");
            return binder;
        }
    }
}
