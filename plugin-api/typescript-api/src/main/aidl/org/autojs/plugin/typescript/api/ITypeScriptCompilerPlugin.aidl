package org.autojs.plugin.typescript.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback;

interface ITypeScriptCompilerPlugin {

    // Transaction order is append-only after envelope v1 is frozen.
    int getContractVersion();

    String getContractHash();

    Bundle getCompilerInfo();

    Bundle prewarm(in Bundle options);

    Bundle startCompilation(
        in Bundle request,
        in ParcelFileDescriptor inputArchive,
        in ParcelFileDescriptor outputArchive,
        ITypeScriptCompilationCallback callback
    );

    boolean cancelCompilation(in Bundle cancellation);
}
