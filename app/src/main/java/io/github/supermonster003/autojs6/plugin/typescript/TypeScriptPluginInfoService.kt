package io.github.supermonster003.autojs6.plugin.typescript

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.autojs.plugin.common.api.IPluginInfoProvider

class TypeScriptPluginInfoService : Service() {

    private val binder = object : IPluginInfoProvider.Stub() {
        override fun getInfo() = TypeScriptPluginMetadata.pluginInfo(
            context = this@TypeScriptPluginInfoService,
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
