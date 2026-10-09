package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

object CompilerAssetOrigin {

    const val ORIGIN = "https://appassets.androidplatform.net"
    const val PORT_MESSAGE = "__autojs_typescript_compiler_port_v1__"
    const val ROOT_PATH = "/typescript-compiler-r1/"
    const val ENTRY_URL = "${ORIGIN}${ROOT_PATH}index.html"

    private val entryUri = Uri.parse(ENTRY_URL)

    fun createAssetLoader(
        context: Context,
        compilerVersion: String,
    ): WebViewAssetLoader {
        val assets = mapOf(
            "compiler-host.js" to AssetSpec(
                assetPath = "typescript-compiler-r1/compiler-host.js",
                mimeType = JAVASCRIPT_MIME_TYPE,
                contentSecurityPolicy = PAGE_CONTENT_SECURITY_POLICY,
            ),
            "compiler-worker.js" to AssetSpec(
                assetPath = "typescript-compiler-r1/compiler-worker.js",
                mimeType = JAVASCRIPT_MIME_TYPE,
                contentSecurityPolicy = WORKER_CONTENT_SECURITY_POLICY,
            ),
            "index.html" to AssetSpec(
                assetPath = "typescript-compiler-r1/index.html",
                mimeType = "text/html",
                contentSecurityPolicy = PAGE_CONTENT_SECURITY_POLICY,
            ),
            "typescript.js" to AssetSpec(
                assetPath = "typescript/$compilerVersion/lib/typescript.js",
                mimeType = JAVASCRIPT_MIME_TYPE,
                contentSecurityPolicy = WORKER_CONTENT_SECURITY_POLICY,
            ),
            // Declaration preload bundles generated from the verified declaration distribution
            // (startup-latency L2-1). The Worker re-verifies every record digest before use.
            "declarations-common.js" to AssetSpec(
                assetPath = "$DECLARATION_BUNDLE_ASSET_ROOT/declarations-common.js",
                mimeType = JAVASCRIPT_MIME_TYPE,
                contentSecurityPolicy = WORKER_CONTENT_SECURITY_POLICY,
            ),
            "declarations-rhino.js" to AssetSpec(
                assetPath = "$DECLARATION_BUNDLE_ASSET_ROOT/declarations-rhino.js",
                mimeType = JAVASCRIPT_MIME_TYPE,
                contentSecurityPolicy = WORKER_CONTENT_SECURITY_POLICY,
            ),
            "declarations-node.js" to AssetSpec(
                assetPath = "$DECLARATION_BUNDLE_ASSET_ROOT/declarations-node.js",
                mimeType = JAVASCRIPT_MIME_TYPE,
                contentSecurityPolicy = WORKER_CONTENT_SECURITY_POLICY,
            ),
        )
        return WebViewAssetLoader.Builder()
            .setHttpAllowed(false)
            .addPathHandler(
                ROOT_PATH,
                ExactAssetPathHandler(context.applicationContext, assets),
            )
            .build()
    }

    fun intercept(
        assetLoader: WebViewAssetLoader,
        request: WebResourceRequest,
    ): WebResourceResponse {
        if (request.method != "GET" || !isAllowedUri(request.url)) {
            return forbiddenResponse()
        }
        return assetLoader.shouldInterceptRequest(request.url) ?: forbiddenResponse()
    }

    fun isEntryUri(uri: Uri): Boolean = uri == entryUri

    private fun isAllowedUri(uri: Uri): Boolean =
        uri.scheme == "https" &&
            uri.encodedAuthority == WebViewAssetLoader.DEFAULT_DOMAIN &&
            uri.encodedQuery == null &&
            uri.encodedFragment == null &&
            uri.encodedPath in ALLOWED_ENCODED_PATHS

    fun forbiddenResponse(): WebResourceResponse {
        val body = "Forbidden".toByteArray(StandardCharsets.UTF_8)
        return WebResourceResponse(
            "text/plain",
            StandardCharsets.UTF_8.name(),
            403,
            "Forbidden",
            securityHeaders(PAGE_CONTENT_SECURITY_POLICY),
            ByteArrayInputStream(body),
        )
    }

    private fun securityHeaders(contentSecurityPolicy: String) = mapOf(
        "Cache-Control" to "no-store",
        "Content-Security-Policy" to contentSecurityPolicy,
        "Cross-Origin-Resource-Policy" to "same-origin",
        "Permissions-Policy" to
            "camera=(), geolocation=(), microphone=(), payment=()",
        "Referrer-Policy" to "no-referrer",
        "X-Content-Type-Options" to "nosniff",
    )

    private data class AssetSpec(
        val assetPath: String,
        val mimeType: String,
        val contentSecurityPolicy: String,
    )

    private class ExactAssetPathHandler(
        context: Context,
        private val assets: Map<String, AssetSpec>,
    ) : WebViewAssetLoader.PathHandler {

        private val assetManager = context.assets

        override fun handle(path: String): WebResourceResponse {
            val asset = assets[path] ?: return forbiddenResponse()
            val stream = try {
                assetManager.open(asset.assetPath, AssetManager.ACCESS_STREAMING)
            } catch (_: IOException) {
                return forbiddenResponse()
            } catch (_: SecurityException) {
                return forbiddenResponse()
            }
            return WebResourceResponse(
                asset.mimeType,
                StandardCharsets.UTF_8.name(),
                stream,
            ).apply {
                responseHeaders = securityHeaders(asset.contentSecurityPolicy)
            }
        }
    }

    private const val JAVASCRIPT_MIME_TYPE = "application/javascript"
    private const val PAGE_CONTENT_SECURITY_POLICY =
        "default-src 'none'; " +
            "script-src 'self'; " +
            "worker-src 'self'; " +
            "child-src 'self'; " +
            "connect-src 'none'; " +
            "frame-src 'none'; " +
            "object-src 'none'; " +
            "base-uri 'none'; " +
            "form-action 'none'; " +
            "frame-ancestors 'none'"
    private const val WORKER_CONTENT_SECURITY_POLICY =
        "default-src 'none'; " +
            "script-src 'self'; " +
            "connect-src 'none'; " +
            "object-src 'none'"
    private const val DECLARATION_BUNDLE_ASSET_ROOT = "r2-declaration-worker-bundles"
    private val ALLOWED_ENCODED_PATHS = setOf(
        "${ROOT_PATH}compiler-host.js",
        "${ROOT_PATH}compiler-worker.js",
        "${ROOT_PATH}index.html",
        "${ROOT_PATH}typescript.js",
        "${ROOT_PATH}declarations-common.js",
        "${ROOT_PATH}declarations-rhino.js",
        "${ROOT_PATH}declarations-node.js",
    )
}
