package io.github.supermonster003.autojs6.plugin.typescript

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.common.api.IPluginInfoProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ActivationContractInstrumentedTest {
    @Suppress("DEPRECATION")
    @Test fun wakeAndInfoAreDiscoverableAndMetadataMatchesInstalledPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.packageManager
        val wake = Intent("org.autojs.plugin.action.WAKE").addCategory(Intent.CATEGORY_DEFAULT).setPackage(context.packageName)
        val activity = manager.queryIntentActivities(wake, 0).single().activityInfo
        assertEquals("io.github.supermonster003.autojs6.plugin.typescript.WakeActivity", activity.name)
        assertTrue(activity.exported)
        assertEquals("org.autojs.permission.PLUGIN", activity.permission)
        val application = manager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        assertEquals(activity.name, application.metaData.getString("org.autojs.plugin.WAKE_ACTIVITY"))
        val discovery = Intent("org.autojs.plugin.INFO").addCategory("typescript").setPackage(context.packageName)
        val service = manager.queryIntentServices(discovery, 0).single().serviceInfo
        assertTrue(service.exported)
        assertEquals("org.autojs.permission.PLUGIN", service.permission)
        val latch = CountDownLatch(1)
        var remote: IBinder? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { remote = binder; latch.countDown() }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        assertTrue(context.bindService(Intent().setComponent(ComponentName(service.packageName, service.name)), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue("INFO binding timed out", latch.await(10, TimeUnit.SECONDS))
            assertEquals("org.autojs.plugin.common.api.IPluginInfoProvider", remote!!.interfaceDescriptor)
            val info = IPluginInfoProvider.Stub.asInterface(remote).info
            val installed = manager.getPackageInfo(context.packageName, 0)
            assertEquals(installed.versionName, info.versionName)
            assertEquals(context.getString(R.string.app_name), info.name)
            assertEquals(context.getString(R.string.plugin_description), info.description)
            val supported = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            assertTrue(info.supportedAbis!!.isNotEmpty())
            assertTrue(info.supportedAbis!!.all(supported::contains))
            assertTrue(info.supportedAbis!!.any { it in android.os.Build.SUPPORTED_ABIS })
            assertNotNull(info.capabilities)
        } finally { context.unbindService(connection) }
    }
}
