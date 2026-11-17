package io.github.supermonster003.autojs6.plugin.typescript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompilerWebViewRequirementTest {

    @Test
    fun minimumMatchesTheWebViewSupportContract() {
        assertEquals(119, CompilerWebViewRequirement.MINIMUM_CHROMIUM_MAJOR)
    }

    @Test
    fun chromiumVersionIsReadFromTheWebViewUserAgent() {
        assertEquals("51.0.2704.90", CompilerWebViewRequirement.chromiumVersion(STOCK_API_24))
        assertEquals("126.0.6478.186", CompilerWebViewRequirement.chromiumVersion(G8441_CHROME_126))
        assertEquals("99.0.4844.88", CompilerWebViewRequirement.chromiumVersion(OEM_PROVIDER))
        assertNull(CompilerWebViewRequirement.chromiumVersion(WITHOUT_CHROME_TOKEN))
        assertNull(CompilerWebViewRequirement.chromiumVersion(""))
        assertNull(CompilerWebViewRequirement.chromiumVersion(null))
        assertNull(CompilerWebViewRequirement.chromiumVersion("Mozilla/5.0 HeadlessChrome/x"))
    }

    @Test
    fun enginesBelowTheMinimumAreRefusedWithTheRequiredAndCurrentVersions() {
        val oldest = engine(STOCK_API_24, "com.android.webview", "51.0.2704.90")
        assertTrue(oldest.isTooOld)
        assertEquals("51.0.2704.90 (com.android.webview)", oldest.describe())
        assertEquals(
            "needs 119, current 51.0.2704.90 (com.android.webview)",
            CompilerWebViewRequirement.rejectionMessage(oldest, ::format),
        )

        val chrome69 = engine(ua("69.0.3497.100"), "com.google.android.webview", "69.0.3497.100")
        assertTrue(chrome69.isTooOld)
        assertTrue(engine(ua("118.0.5993.111"), null, null).isTooOld)
        assertEquals("118.0.5993.111", engine(ua("118.0.5993.111"), null, null).describe())
    }

    @Test
    fun enginesAtOrAboveTheMinimumAreNotRefused() {
        listOf("119.0.6045.194", "126.0.6478.186", "145.0.7632.218", "1000.0.0.0").forEach { version ->
            val engine = engine(ua(version), "com.google.android.webview", version)
            assertFalse(version, engine.isTooOld)
            assertNull(CompilerWebViewRequirement.rejectionMessage(engine, ::format))
        }
        // An OEM provider whose package version is not Chromium's follows the engine version.
        val oem = engine(ua("126.0.6478.71"), "com.huawei.webview", "14.0.0.302")
        assertFalse(oem.isTooOld)
    }

    @Test
    fun enginesWithoutAReadableVersionKeepTheExistingPath() {
        val unknown = engine(WITHOUT_CHROME_TOKEN, "com.example.webview", "3.1")
        assertNull(unknown.chromiumMajor)
        assertFalse(unknown.isTooOld)
        assertNull(CompilerWebViewRequirement.rejectionMessage(unknown, ::format))
        assertEquals("3.1 (com.example.webview)", unknown.describe())
        assertEquals("unknown", engine(null, null, null).describe())
    }

    private fun engine(userAgent: String?, providerPackage: String?, providerVersion: String?) =
        CompilerWebViewRequirement.engineOf(userAgent, providerPackage, providerVersion)

    private fun format(minimum: Int, current: String) = "needs $minimum, current $current"

    private companion object {
        const val STOCK_API_24 =
            "Mozilla/5.0 (Linux; Android 7.0; Android SDK built for x86 Build/NYC; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/51.0.2704.90 Mobile Safari/537.36"
        const val G8441_CHROME_126 =
            "Mozilla/5.0 (Linux; Android 9; G8441 Build/47.2.A.11.228; wv) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/126.0.6478.186 Mobile Safari/537.36"
        const val OEM_PROVIDER =
            "Mozilla/5.0 (Linux; Android 10; ELS-AN00; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Version/4.0 Chrome/99.0.4844.88 Mobile Safari/537.36"
        const val WITHOUT_CHROME_TOKEN =
            "Mozilla/5.0 (Linux; Android 9; Custom; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0"

        fun ua(version: String) =
            "Mozilla/5.0 (Linux; Android 7.0; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Version/4.0 Chrome/$version Mobile Safari/537.36"
    }
}
