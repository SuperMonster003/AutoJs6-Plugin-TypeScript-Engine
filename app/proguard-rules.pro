-dontwarn kotlinx.parcelize.Parcelize

-keep class io.github.supermonster003.autojs6.plugin.typescript.TypeScriptPluginInfoService { *; }
-keep class io.github.supermonster003.autojs6.plugin.typescript.TypeScriptCompilerPluginService { *; }
-keep class io.github.supermonster003.autojs6.plugin.typescript.TypeScriptPluginMetadata { *; }
-keep class io.github.supermonster003.autojs6.plugin.typescript.R2FileDescriptorOpenFlagsNative {
    static native int getFileStatusFlags(int);
}
-keep class org.autojs.plugin.common.api.** { *; }
-keep class org.autojs.plugin.typescript.api.** { *; }
