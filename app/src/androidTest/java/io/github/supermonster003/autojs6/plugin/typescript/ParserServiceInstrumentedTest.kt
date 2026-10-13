package io.github.supermonster003.autojs6.plugin.typescript

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.typescript.parser.ITypeScriptParserPlugin
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.autojs.plugin.typescript.parser.TypeScriptParserContract as Contract

/** TypeScript parser contract v1 over the real Binder service, WebView page and Worker. */
@RunWith(AndroidJUnit4::class)
class ParserServiceInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun <T> withParser(block: (ITypeScriptParserPlugin) -> T): T {
        val intent = Intent(Contract.ACTION).addCategory("typescript").setPackage(context.packageName)
        val service = context.packageManager.queryIntentServices(intent, 0).single().serviceInfo
        assertTrue(service.exported)
        assertEquals("org.autojs.permission.PLUGIN", service.permission)
        assertEquals("${context.packageName}:typescript_compiler", service.processName)
        val latch = CountDownLatch(1)
        var remote: IBinder? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { remote = binder; latch.countDown() }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        assertTrue(context.bindService(Intent().setComponent(ComponentName(service.packageName, service.name)), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue("parser binding timed out", latch.await(20, TimeUnit.SECONDS))
            assertEquals("org.autojs.plugin.typescript.parser.ITypeScriptParserPlugin", remote!!.interfaceDescriptor)
            return block(ITypeScriptParserPlugin.Stub.asInterface(remote))
        } finally {
            context.unbindService(connection)
        }
    }

    private class Parsed(val result: Bundle, val tree: String?)

    private fun ITypeScriptParserPlugin.parseFile(fileName: String, source: String, extra: Bundle.() -> Unit = {}): Parsed {
        val file = File(context.cacheDir, "parser-test-${UUID.randomUUID()}.json")
        try {
            val descriptor = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY,
            )
            val request = Bundle().apply {
                putInt(Contract.KEY_CONTRACT_VERSION, Contract.VERSION)
                putString(Contract.KEY_REQUEST_ID, UUID.randomUUID().toString())
                putString(Contract.KEY_FILE_NAME, fileName)
                putString(Contract.KEY_SOURCE_TEXT, source)
                putLong(Contract.KEY_TIMEOUT_MS, 30_000L)
                extra()
            }
            val result = descriptor.use { parse(request, it) }
            val tree = if (result.getString(Contract.KEY_OUTCOME) == Contract.OUTCOME_PARSED) file.readText() else null
            return Parsed(result, tree)
        } finally {
            file.delete()
        }
    }

    @Test
    fun parserInfoAndPluginCapabilitiesAdvertiseContractV1() {
        withParser { parser ->
            assertEquals(Contract.VERSION, parser.contractVersion)
            assertEquals(Contract.HASH, parser.contractHash)
            val info = parser.parserInfo
            assertEquals(Contract.TREE_SCHEMA_REVISION, info.getInt(Contract.KEY_TREE_SCHEMA_REVISION))
            assertEquals(BuildConfig.TYPESCRIPT_VERSION, info.getString(Contract.KEY_COMPILER_VERSION))
            assertTrue(".tsx" in info.getStringArray(Contract.KEY_SUPPORTED_EXTENSIONS)!!)
            assertEquals(Contract.MAX_CONCURRENT_PARSES, info.getInt(Contract.KEY_MAX_CONCURRENT_PARSES))
        }
        val capabilities = TypeScriptPluginMetadata.pluginInfo(context).capabilities!!
        assertEquals(Contract.VERSION, capabilities.getInt(Contract.CAPABILITY_CONTRACT_VERSION))
        assertEquals(Contract.ACTION, capabilities.getString(Contract.CAPABILITY_SERVICE_ACTION))
        assertEquals(Contract.TREE_SCHEMA_REVISION, capabilities.getInt(Contract.CAPABILITY_TREE_SCHEMA_REVISION))
    }

    @Test
    fun aTsxSourceBecomesATreeWithPositions() {
        val source = "let count = compose.state(0);\n" +
            "compose.mount(<compose.Column padding={16}>\n" +
            "    <compose.Text text={'Clicked ' + count.value} />\n" +
            "    <compose.Button onClick={() => { count.value++; }}>Add 😀</compose.Button>\n" +
            "</compose.Column>);\n"
        withParser { parser ->
            val cold = SystemClock.elapsedRealtime()
            val first = parser.parseFile("counter.tsx", source)
            val coldMs = SystemClock.elapsedRealtime() - cold
            assertEquals(first.result.toString(), Contract.OUTCOME_PARSED, first.result.getString(Contract.KEY_OUTCOME))
            val tree = requireNotNull(first.tree)
            val bytes = tree.toByteArray(Charsets.UTF_8)
            assertEquals(bytes.size.toLong(), first.result.getLong(Contract.KEY_TREE_BYTE_LENGTH))
            assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, first.result.getString(Contract.KEY_TREE_SHA256))
            val json = JSONObject(tree)
            assertEquals(1, json.getInt("schemaRevision"))
            assertEquals(BuildConfig.TYPESCRIPT_VERSION, json.getString("compilerVersion"))
            assertEquals("jsx", json.getString("languageVariant"))
            assertEquals(first.result.getInt(Contract.KEY_NODE_COUNT), json.getInt("nodeCount"))
            val statements = json.getJSONObject("root").getJSONArray("statements")
            assertEquals("let", statements.getJSONObject(0).getJSONObject("declarationList").getString("declarationKind"))
            val column = statements.getJSONObject(1).getJSONObject("expression").getJSONArray("arguments").getJSONObject(0)
            assertEquals("JsxElement", column.getString("kind"))
            val tag = column.getJSONObject("openingElement").getJSONObject("tagName")
            assertEquals("compose.Column", source.substring(tag.getInt("start"), tag.getInt("end")))
            val button = (0 until column.getJSONArray("children").length())
                .map { column.getJSONArray("children").getJSONObject(it) }
                .single { it.getString("kind") == "JsxElement" }
            val label = button.getJSONArray("children").getJSONObject(0)
            assertEquals("Add 😀", label.getString("text"))

            val warm = SystemClock.elapsedRealtime()
            val second = parser.parseFile("counter.tsx", source)
            val warmMs = SystemClock.elapsedRealtime() - warm
            assertEquals(tree, second.tree)
            Log.i("ParserServiceTest", "PARSER_TIMING cold=${coldMs}ms warm=${warmMs}ms nodes=${json.getInt("nodeCount")}")
            assertTrue("a warm parse took $warmMs ms", warmMs < 5_000)
        }
    }

    @Test
    fun syntaxErrorsAreDiagnosticsAndBadRequestsAreRejected() {
        withParser { parser ->
            val broken = parser.parseFile("broken.tsx", "const a = <compose.Text text={1} ;\n")
            assertEquals(Contract.OUTCOME_PARSED, broken.result.getString(Contract.KEY_OUTCOME))
            assertTrue(broken.result.getInt(Contract.KEY_DIAGNOSTIC_COUNT) > 0)
            val diagnostic = JSONObject(requireNotNull(broken.tree)).getJSONArray("diagnostics").getJSONObject(0)
            assertEquals("error", diagnostic.getString("category"))

            fun code(parsed: Parsed): String? {
                assertEquals(Contract.OUTCOME_FAILED, parsed.result.getString(Contract.KEY_OUTCOME))
                assertNotNull(parsed.result.getString(Contract.KEY_ERROR_MESSAGE))
                return parsed.result.getString(Contract.KEY_ERROR_CODE)
            }
            assertEquals(Contract.ERROR_INVALID_REQUEST, code(parser.parseFile("counter.vue", "")))
            assertEquals(Contract.ERROR_CONTRACT_MISMATCH, code(parser.parseFile("a.tsx", "") { putInt(Contract.KEY_CONTRACT_VERSION, 2) }))
            assertEquals(Contract.ERROR_INVALID_REQUEST, code(parser.parseFile("a.tsx", "x".repeat(Contract.MAX_SOURCE_CHARS + 1))))
            assertEquals(Contract.ERROR_TREE_LIMIT, code(parser.parseFile("deep.ts", "let x = " + "(".repeat(100_000) + "1" + ")".repeat(100_000) + ";")))
            // The Worker that hit the limit keeps serving.
            assertEquals(Contract.OUTCOME_PARSED, parser.parseFile("ok.ts", "let ok = 1;").result.getString(Contract.KEY_OUTCOME))
        }
    }
}
