package io.github.supermonster003.autojs6.plugin.typescript.compat.v1;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import java.util.concurrent.atomic.AtomicInteger;
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback;
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin;

/**
 * Exported old-service fixture compiled only against the checked-in v1 AAR.
 *
 * <p>R1 does not admit public project compilation. The service therefore closes its transported
 * FD references before returning the stable rejection and never invokes the callback.
 */
public final class FrozenR1CompilerService extends Service {

    public static final String ACTION_COMPILER =
            "org.autojs.plugin.typescript.compat.v1.COMPILER";
    private static final String CONTRACT_HASH =
            "e35e8c6f71b8e0799678974e95aca9233225b3371c4a484437c7740defe9f86b";
    private static final int CONTRACT_VERSION = 1;
    private static final String ERROR_R0_NOT_AVAILABLE =
            "ERR_AUTOJS6_TYPESCRIPT_R0_NOT_AVAILABLE";
    private final AtomicInteger openArchiveReferenceCount = new AtomicInteger();

    private final ITypeScriptCompilerPlugin.Stub binder =
            new ITypeScriptCompilerPlugin.Stub() {
                @Override
                public int getContractVersion() {
                    return CONTRACT_VERSION;
                }

                @Override
                public String getContractHash() {
                    return CONTRACT_HASH;
                }

                @Override
                public Bundle getCompilerInfo() {
                    return compilerInfo();
                }

                @Override
                public Bundle prewarm(Bundle options) {
                    Bundle result = compilerInfo();
                    result.putString("errorCode", ERROR_R0_NOT_AVAILABLE);
                    result.putString(
                            "errorMessage",
                            "The frozen R1 fixture does not host a compiler backend.");
                    return result;
                }

                @Override
                public Bundle startCompilation(
                        Bundle request,
                        ParcelFileDescriptor inputArchive,
                        ParcelFileDescriptor outputArchive,
                        ITypeScriptCompilationCallback callback) {
                    String requestId = safeRequestId(request);
                    int receivedReferenceCount =
                            (inputArchive == null ? 0 : 1) + (outputArchive == null ? 0 : 1);
                    openArchiveReferenceCount.addAndGet(receivedReferenceCount);
                    try {
                        Bundle ticket = new Bundle();
                        ticket.putInt("contractVersion", CONTRACT_VERSION);
                        ticket.putString("contractHash", CONTRACT_HASH);
                        ticket.putString("requestId", requestId);
                        ticket.putBoolean("accepted", false);
                        ticket.putString("errorCode", ERROR_R0_NOT_AVAILABLE);
                        ticket.putString(
                                "errorMessage",
                                "Project compilation is not available in the frozen R1 fixture.");
                        return ticket;
                    } finally {
                        closeQuietly(inputArchive);
                        closeQuietly(outputArchive);
                        openArchiveReferenceCount.addAndGet(-receivedReferenceCount);
                    }
                }

                @Override
                public boolean cancelCompilation(Bundle cancellation) {
                    return false;
                }
            };

    @Override
    public IBinder onBind(Intent intent) {
        if (intent == null || !ACTION_COMPILER.equals(intent.getAction())) {
            return null;
        }
        return binder;
    }

    private Bundle compilerInfo() {
        Bundle info = new Bundle();
        info.putInt("contractVersion", CONTRACT_VERSION);
        info.putString("contractHash", CONTRACT_HASH);
        info.putString("backend", "uninitialized");
        info.putBoolean("typescriptCompilerAvailable", false);
        info.putBoolean("typescriptProjectCompilation", false);
        info.putBoolean("typescriptSemanticDiagnostics", false);
        info.putBoolean("typescriptSourceMapV3", false);
        info.putInt("compatFixtureOpenArchiveReferenceCount", openArchiveReferenceCount.get());
        return info;
    }

    private static String safeRequestId(Bundle request) {
        if (request == null) {
            return "";
        }
        try {
            Object value = request.get("requestId");
            return value instanceof String ? (String) value : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (Exception ignored) {
            // The ownership invariant is best-effort even for an already-invalid descriptor.
        }
    }
}
