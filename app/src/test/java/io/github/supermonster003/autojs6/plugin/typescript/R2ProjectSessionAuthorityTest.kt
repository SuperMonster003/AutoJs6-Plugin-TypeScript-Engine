package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class R2ProjectSessionAuthorityTest {

    @Test
    fun identityIsStableForOneCallerAndProjectButSeparatedAcrossEitherBoundary() {
        val first = derive(callingUid = 10_001, projectId = "project-a")
        val replay = derive(callingUid = 10_001, projectId = "project-a")
        val otherCaller = derive(callingUid = 10_002, projectId = "project-a")
        val otherProject = derive(callingUid = 10_001, projectId = "project-b")

        assertEquals(first, replay)
        assertEquals(TypeScriptR2Contract.PROJECT_SESSION_REVISION, first.revision)
        assertNotEquals(first.key, otherCaller.key)
        assertNotEquals(first.key, otherProject.key)
    }

    @Test
    fun unsupportedRevisionAndInvalidIdentityFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            R2ProjectSessionAuthority.derive(
                callingUid = 10_001,
                projectId = "project-a",
                revision = TypeScriptR2Contract.PROJECT_SESSION_REVISION + 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            derive(callingUid = -1, projectId = "project-a")
        }
        assertThrows(IllegalArgumentException::class.java) {
            derive(callingUid = 10_001, projectId = "")
        }
    }

    private fun derive(
        callingUid: Int,
        projectId: String,
    ) = R2ProjectSessionAuthority.derive(
        callingUid = callingUid,
        projectId = projectId,
        revision = TypeScriptR2Contract.PROJECT_SESSION_REVISION,
    )
}
