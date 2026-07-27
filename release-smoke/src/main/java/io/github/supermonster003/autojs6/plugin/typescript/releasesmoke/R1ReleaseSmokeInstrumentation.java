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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin;

/**
 * Framework-only smoke runner for a minified release target.
 *
 * <p>The regular AndroidX test APK shares Kotlin runtime classes with its debug target. R8 may
 * legitimately remove those classes from release, so this runner intentionally uses only Android
 * framework APIs and the kept public AIDL surface.
 */
public final class R1ReleaseSmokeInstrumentation extends Instrumentation {

    private static final String ACTION_COMPILER =
            "org.autojs.plugin.typescript.COMPILER";
    private static final String BACKEND_WEBVIEW_WORKER = "webview_worker";
    private static final String CATEGORY_TYPESCRIPT = "typescript";
    private static final String CONTRACT_HASH =
            "e35e8c6f71b8e0799678974e95aca9233225b3371c4a484437c7740defe9f86b";
    private static final int CONTRACT_VERSION = 1;
    private static final String EXPECTED_DECLARATIONS_FINGERPRINT =
            "6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f";
    private static final String EXPECTED_DISTRIBUTION_FINGERPRINT =
            "3855b04b968db8a9529b60c96dd8b8ec94fa1ee040cac10a5ab569aa6e33f658";
    private static final String KEY_BACKEND = "backend";
    private static final String KEY_COMPILER_AVAILABLE = "typescriptCompilerAvailable";
    private static final String KEY_CONTRACT_HASH = "contractHash";
    private static final String KEY_CONTRACT_VERSION = "contractVersion";
    private static final String KEY_DECLARATIONS_FINGERPRINT =
            "declarationsFingerprint";
    private static final String KEY_DISTRIBUTION_FINGERPRINT =
            "compilerDistributionFingerprint";
    private static final String KEY_ERROR_CODE = "errorCode";
    private static final String KEY_PROJECT_COMPILATION = "typescriptProjectCompilation";
    private static final String KEY_SEMANTIC_DIAGNOSTICS = "typescriptSemanticDiagnostics";
    private static final String KEY_SERVICE_STATUS = "typescriptCompilerServiceStatus";
    private static final String KEY_SOURCE_MAP_V3 = "typescriptSourceMapV3";
    private static final String KEY_TIMEOUT_MS = "timeoutMs";
    private static final String STATUS_READY = "ready";
    private static final long TIMEOUT_MS = 30_000L;

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
                    "Minified release discovery, remote bind, and TypeScript Worker golden passed.\n");
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
        Context context = getTargetContext();
        Intent discovery = new Intent(ACTION_COMPILER)
                .addCategory(CATEGORY_TYPESCRIPT)
                .setPackage(context.getPackageName());
        List<ResolveInfo> matches = context.getPackageManager().queryIntentServices(discovery, 0);
        require(matches.size() == 1, "Expected exactly one compiler service.");
        ComponentName component = new ComponentName(
                matches.get(0).serviceInfo.packageName,
                matches.get(0).serviceInfo.name);
        require(
                (context.getPackageName() + ":typescript_compiler")
                        .equals(matches.get(0).serviceInfo.processName),
                "Compiler service escaped the dedicated process.");

        AwaitingConnection connection = new AwaitingConnection();
        boolean bound = false;
        try {
            bound = context.bindService(
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

            Bundle result = compiler.prewarm(validPrewarmOptions());
            require(!result.containsKey(KEY_ERROR_CODE), "Release prewarm failed.");
            require(
                    BACKEND_WEBVIEW_WORKER.equals(result.getString(KEY_BACKEND)),
                    "Release compiler backend mismatch.");
            require(
                    STATUS_READY.equals(result.getString(KEY_SERVICE_STATUS)),
                    "Release compiler host did not become ready.");
            require(
                    EXPECTED_DISTRIBUTION_FINGERPRINT.equals(
                            result.getString(KEY_DISTRIBUTION_FINGERPRINT)),
                    "Release distribution fingerprint mismatch.");
            require(
                    EXPECTED_DECLARATIONS_FINGERPRINT.equals(
                            result.getString(KEY_DECLARATIONS_FINGERPRINT)),
                    "Release declarations fingerprint mismatch.");
            require(!result.getBoolean(KEY_COMPILER_AVAILABLE, true),
                    "Release smoke advertised compiler availability.");
            require(!result.getBoolean(KEY_PROJECT_COMPILATION, true),
                    "Release smoke advertised project compilation.");
            require(!result.getBoolean(KEY_SEMANTIC_DIAGNOSTICS, true),
                    "Release smoke advertised semantic diagnostics.");
            require(!result.getBoolean(KEY_SOURCE_MAP_V3, true),
                    "Release smoke advertised public Source Map capability.");
        } finally {
            if (bound) {
                context.unbindService(connection);
            }
        }
    }

    private static Bundle validPrewarmOptions() {
        Bundle options = new Bundle();
        options.putInt(KEY_CONTRACT_VERSION, CONTRACT_VERSION);
        options.putString(KEY_CONTRACT_HASH, CONTRACT_HASH);
        options.putLong(KEY_TIMEOUT_MS, TIMEOUT_MS);
        return options;
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
