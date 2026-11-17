package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import android.util.Log
import android.webkit.WebSettings
import androidx.webkit.WebViewCompat

/**
 * The WebView engine the compiler and parser pages need (3-Probe Debugger Q150 A, 0.7.1).
 *
 * The compiler host page, its Worker and TypeScript 6.0.3 use JavaScript syntax that old engines
 * cannot parse: the stock WebView of an API 24 image (Chrome 51) or Chrome 69 stops at
 * `compiler-host.js` with "Invalid or unexpected token", and every `.ts` script then failed only
 * after the bounded Worker timeout with a message that did not name the cause. The supported
 * minimum is Chromium major [MINIMUM_CHROMIUM_MAJOR] (docs/maintenance/WEBVIEW_SUPPORT.md); an
 * older engine is now refused before any page loads, with a message naming the required and the
 * current version. The engine version comes from the default user agent (`Chrome/<version>`),
 * which follows the Chromium engine even for OEM providers whose package versions use another
 * scheme. An engine without a readable version is not refused: the existing bounded path stays.
 *
 * zh-CN: 编译器与解析页需要的 WebView 引擎 (3-Probe Debugger Q150 A, 0.7.1). 旧引擎解析不了编译器宿主页,
 * 此前 `.ts` 脚本要等 Worker 超时后才以不指明原因的错误失败. 支持的最低线是 Chromium 主版本
 * [MINIMUM_CHROMIUM_MAJOR] (docs/maintenance/WEBVIEW_SUPPORT.md); 更旧的引擎现在在加载任何页面之前被拒绝,
 * 消息写明需要的与当前的版本. 版本取自默认 User-Agent 的 `Chrome/<版本>`, OEM 提供方的包版本号另有规则时
 * 也跟随引擎. 读不出版本时不拒绝, 沿用原有的有界路径.
 */
internal object CompilerWebViewRequirement {

    const val MINIMUM_CHROMIUM_MAJOR = 119

    private const val TAG = "TypeScriptWebView"
    private val CHROMIUM_VERSION = Regex("""\bChrome/(\d{1,5}(?:\.\d{1,9}){0,3})\b""")

    internal data class Engine(
        val chromiumVersion: String?,
        val providerPackage: String?,
        val providerVersion: String?,
    ) {
        val chromiumMajor: Int? =
            chromiumVersion?.substringBefore('.')?.toIntOrNull()

        /** Too old only when a version was read and is below the minimum. */
        val isTooOld: Boolean
            get() = chromiumMajor != null && chromiumMajor < MINIMUM_CHROMIUM_MAJOR

        /** "69.0.3497.100 (com.google.android.webview)", or the version alone. */
        fun describe(): String = buildString {
            append(chromiumVersion ?: providerVersion ?: "unknown")
            if (providerPackage != null) append(" (").append(providerPackage).append(')')
        }
    }

    @Volatile
    private var cachedEngine: Engine? = null

    internal fun chromiumVersion(userAgent: String?): String? =
        userAgent?.let { CHROMIUM_VERSION.find(it)?.groupValues?.get(1) }

    internal fun engineOf(
        userAgent: String?,
        providerPackage: String?,
        providerVersion: String?,
    ): Engine = Engine(chromiumVersion(userAgent), providerPackage, providerVersion)

    /** The message for a too-old [engine], or null when it may run the compiler. */
    internal fun rejectionMessage(engine: Engine, format: (Int, String) -> String): String? =
        if (engine.isTooOld) format(MINIMUM_CHROMIUM_MAJOR, engine.describe()) else null

    /**
     * The WebView engine of this process, read once: a WebView provider update restarts the
     * processes that loaded the old one. Any thread; null when no provider can be loaded.
     */
    fun current(context: Context): Engine? {
        cachedEngine?.let { return it }
        val engine = runCatching {
            val provider = runCatching {
                WebViewCompat.getCurrentWebViewPackage(context)
            }.getOrNull()
            engineOf(
                userAgent = WebSettings.getDefaultUserAgent(context),
                providerPackage = provider?.packageName,
                providerVersion = provider?.versionName,
            )
        }.onFailure { error ->
            Log.w(TAG, "WebView engine version is unavailable: ${error.javaClass.simpleName}")
        }.getOrNull() ?: return null
        cachedEngine = engine
        if (engine.isTooOld) {
            Log.e(
                TAG,
                "WebView ${engine.describe()} is older than Chromium $MINIMUM_CHROMIUM_MAJOR; " +
                    "TypeScript compilation and parsing are refused.",
            )
        }
        return engine
    }

    /** The localized refusal for this process's WebView, or null when it may run the compiler. */
    fun rejectionMessage(context: Context): String? {
        val engine = current(context) ?: return null
        return rejectionMessage(engine) { minimum, current ->
            context.getString(R.string.error_webview_too_old, minimum, current)
        }
    }
}
