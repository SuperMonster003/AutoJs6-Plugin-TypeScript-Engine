package io.github.supermonster003.autojs6.plugin.typescript

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R2TrustedDeclarationsInstrumentationTest {

    @Test
    fun packagedTypeScriptStandardLibrariesMatchRuntimeTrustedInventory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val declarations = R2TrustedDeclarations.loadTypeScriptStandardLibraries(context)

        assertEquals(EXPECTED_DECLARATIONS_FINGERPRINT, declarations.fingerprint)
        assertEquals(108, declarations.files.size)
        assertEquals(3_784_758L, declarations.files.sumOf(R2TrustedDeclarationFile::size))
        assertNotNull(declarations.filesByArchivePath["declarations/lib.d.ts"])
        assertEquals(
            declarations.fingerprint,
            R2TrustedDeclarations.fingerprint(declarations.files),
        )
    }

    @Test
    fun packagedDeclarationProfilesAreStrictlyLayerSelected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val common = R2TrustedDeclarations.loadTypeScriptStandardLibraries(context)
        val rhino = R2TrustedDeclarations.loadForProfile(
            context,
            R2ProjectProfile.PROVISIONAL_RHINO,
        )
        val node = R2TrustedDeclarations.loadForProfile(
            context,
            R2ProjectProfile.PROVISIONAL_NODE_NEXT,
        )

        assertEquals(common.fingerprint, rhino.fingerprint)
        assertEquals(common.fingerprint, node.fingerprint)
        assertEquals(R2DeclarationProfile.RHINO, rhino.profile)
        assertEquals(R2DeclarationProfile.NODE, node.profile)
        assertEquals(setOf("common", "rhino"), rhino.layerFingerprints.keys)
        assertEquals(setOf("common", "node"), node.layerFingerprints.keys)
        assertEquals(listOf("/declarations/rhino/index.d.ts"), rhino.compilerRootNames)
        assertEquals(listOf("/declarations/node/index.d.ts"), node.compilerRootNames)
        assertEquals(common.files.size + 1, rhino.files.size)
        assertEquals(common.files.size + 59, node.files.size)
        assertNotNull(rhino.filesByArchivePath["declarations/rhino/index.d.ts"])
        assertNotNull(node.filesByArchivePath["declarations/node/index.d.ts"])
        assertFalse(
            rhino.files.any { file ->
                file.archivePath.startsWith("declarations/node/")
            },
        )
        assertFalse(
            node.files.any { file ->
                file.archivePath.startsWith("declarations/rhino/")
            },
        )
        assertTrue(rhino.profileFingerprint != node.profileFingerprint)
    }

    private companion object {
        const val EXPECTED_DECLARATIONS_FINGERPRINT =
            "6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f"
    }
}
