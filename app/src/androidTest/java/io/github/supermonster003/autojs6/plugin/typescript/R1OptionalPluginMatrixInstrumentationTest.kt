package io.github.supermonster003.autojs6.plugin.typescript

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptPluginActions
import org.autojs.plugin.typescript.api.TypeScriptPluginCapabilityKeys
import org.autojs.plugin.typescript.api.TypeScriptPluginIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R1OptionalPluginMatrixInstrumentationTest {

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    @Test
    fun installedOptionalPluginStateDoesNotAffectRemotePrewarmGolden() {
        expectedPackageStatesOrNull()?.let { expected ->
            val nodeInstalled = isPackageInstalled(NODE_RUNTIME_PACKAGE)
            val aceInstalled = isPackageInstalled(ACE_EDITOR_PACKAGE)
            assertEquals(
                "Unexpected Node Runtime package state.",
                expected.nodeInstalled,
                nodeInstalled,
            )
            assertEquals(
                "Unexpected Ace Editor package state.",
                expected.aceInstalled,
                aceInstalled,
            )
            Log.i(
                TAG,
                "Strict optional plugin state: " +
                    "nodeInstalled=$nodeInstalled, aceInstalled=$aceInstalled",
            )
        } ?: Log.i(
            TAG,
            "Optional plugin state is not asserted in the ordinary connected gate; " +
                "the four-state harness supplies both expected runner arguments.",
        )

        withCompilerService { serviceInfo, rawBinder, compiler ->
            assertEquals(
                "${context.packageName}:typescript_compiler",
                serviceInfo.processName,
            )
            assertNull(
                "The compiler Binder must be a remote proxy from :typescript_compiler.",
                rawBinder.queryLocalInterface(ITypeScriptCompilerPlugin.DESCRIPTOR),
            )
            assertTrue(rawBinder.pingBinder())
            assertEquals(TypeScriptCompilerContract.VERSION, compiler.contractVersion)
            assertEquals(TypeScriptCompilerContract.HASH, compiler.contractHash)

            assertIndependentCapabilities(compiler.compilerInfo)

            val result = compiler.prewarm(validPrewarmOptions())
            assertFalse(
                result.getString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE).orEmpty(),
                result.containsKey(TypeScriptCompilerContract.KEY_ERROR_CODE),
            )
            assertIndependentCapabilities(result)
            assertEquals(
                TypeScriptCompilerContract.STATUS_READY,
                result.getString(TypeScriptPluginCapabilityKeys.SERVICE_STATUS),
            )
        }
    }

    private fun expectedPackageStatesOrNull(): ExpectedPackageStates? {
        val arguments = InstrumentationRegistry.getArguments()
        val hasExpectedNode = arguments.containsKey(ARG_EXPECTED_NODE)
        val hasExpectedAce = arguments.containsKey(ARG_EXPECTED_ACE)
        assertEquals(
            "$ARG_EXPECTED_NODE and $ARG_EXPECTED_ACE must be supplied together.",
            hasExpectedNode,
            hasExpectedAce,
        )
        if (!hasExpectedNode) return null
        return ExpectedPackageStates(
            nodeInstalled = parseExpectedState(arguments, ARG_EXPECTED_NODE),
            aceInstalled = parseExpectedState(arguments, ARG_EXPECTED_ACE),
        )
    }

    private fun parseExpectedState(
        arguments: Bundle,
        key: String,
    ): Boolean = when (val value = arguments.getString(key)) {
        "true" -> true
        "false" -> false
        else -> throw AssertionError("$key must be exactly true or false, found $value.")
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        val output = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("pm path $packageName"),
        ).bufferedReader().use { reader -> reader.readText() }
        val lines = output.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        assertTrue(
            "Unexpected pm path output for $packageName: $output",
            lines.all { line -> line.startsWith("package:") },
        )
        return lines.isNotEmpty()
    }

    private fun assertIndependentCapabilities(info: Bundle) {
        assertEquals(
            TypeScriptCompilerContract.BACKEND_WEBVIEW_WORKER,
            info.getString(TypeScriptCompilerContract.KEY_BACKEND),
        )
        assertEquals(
            EXPECTED_DISTRIBUTION_FINGERPRINT,
            info.getString(
                TypeScriptCompilerContract.KEY_COMPILER_DISTRIBUTION_FINGERPRINT,
            ),
        )
        assertEquals(
            EXPECTED_DECLARATIONS_FINGERPRINT,
            info.getString(TypeScriptCompilerContract.KEY_DECLARATIONS_FINGERPRINT),
        )
        assertFalse(
            info.getBoolean(TypeScriptPluginCapabilityKeys.NODE_RUNTIME_REQUIRED, true),
        )
        assertFalse(
            info.getBoolean(TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE, true),
        )
        assertFalse(
            info.getBoolean(TypeScriptPluginCapabilityKeys.PROJECT_COMPILATION, true),
        )
        assertFalse(
            info.getBoolean(TypeScriptPluginCapabilityKeys.SEMANTIC_DIAGNOSTICS, true),
        )
        assertFalse(
            info.getBoolean(TypeScriptPluginCapabilityKeys.SOURCE_MAP_V3, true),
        )
    }

    private fun validPrewarmOptions() = Bundle().apply {
        putInt(
            TypeScriptCompilerContract.KEY_CONTRACT_VERSION,
            TypeScriptCompilerContract.VERSION,
        )
        putString(
            TypeScriptCompilerContract.KEY_CONTRACT_HASH,
            TypeScriptCompilerContract.HASH,
        )
        putLong(
            TypeScriptCompilerContract.KEY_TIMEOUT_MS,
            TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
        )
    }

    @Suppress("DEPRECATION")
    private fun withCompilerService(
        block: (
            android.content.pm.ServiceInfo,
            IBinder,
            ITypeScriptCompilerPlugin,
        ) -> Unit,
    ) {
        val discoveryIntent = Intent(TypeScriptPluginActions.COMPILER)
            .addCategory(TypeScriptPluginIds.ID)
            .setPackage(context.packageName)
        val matches = context.packageManager.queryIntentServices(discoveryIntent, 0)
        assertEquals("Expected exactly one TypeScript compiler service.", 1, matches.size)
        val serviceInfo = matches.single().serviceInfo
        assertNotNull(serviceInfo)
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        val connection = AwaitingServiceConnection()
        var bound = false
        try {
            bound = context.bindService(
                Intent(discoveryIntent).setComponent(component),
                connection,
                Context.BIND_AUTO_CREATE,
            )
            assertTrue("Failed to bind $component.", bound)
            val rawBinder = connection.awaitBinder()
            val compiler = ITypeScriptCompilerPlugin.Stub.asInterface(rawBinder)
            assertNotNull(compiler)
            block(serviceInfo, rawBinder, compiler)
        } finally {
            if (bound) {
                context.unbindService(connection)
            }
        }
    }

    private class AwaitingServiceConnection : ServiceConnection {

        private val connected = CountDownLatch(1)

        @Volatile
        private var binder: IBinder? = null

        override fun onServiceConnected(
            name: ComponentName,
            service: IBinder,
        ) {
            binder = service
            connected.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) = Unit

        override fun onNullBinding(name: ComponentName) {
            connected.countDown()
        }

        fun awaitBinder(): IBinder {
            assertTrue(
                "Timed out waiting for the compiler service.",
                connected.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            return requireNotNull(binder) { "Compiler service returned a null binding." }
        }
    }

    private data class ExpectedPackageStates(
        val nodeInstalled: Boolean,
        val aceInstalled: Boolean,
    )

    private companion object {
        const val ACE_EDITOR_PACKAGE =
            "io.github.supermonster003.autojs6.plugin.ace.editor"
        const val ARG_EXPECTED_ACE = "expectedAce"
        const val ARG_EXPECTED_NODE = "expectedNode"
        const val EXPECTED_DECLARATIONS_FINGERPRINT =
            "6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f"
        const val EXPECTED_DISTRIBUTION_FINGERPRINT =
            "3855b04b968db8a9529b60c96dd8b8ec94fa1ee040cac10a5ab569aa6e33f658"
        const val NODE_RUNTIME_PACKAGE =
            "io.github.supermonster003.autojs6.plugin.nodejs"
        const val SERVICE_TIMEOUT_SECONDS = 5L
        const val TAG = "R1OptionalPluginMatrix"
    }
}
