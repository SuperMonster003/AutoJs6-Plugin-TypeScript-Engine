package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R2DependencyLayerTest {

    @Test
    fun inventoryBindsLockfileAndExactCompilationRelevantFiles() {
        val fixture = fixture()
        val inventory = fixture.inventory

        assertEquals("package-lock", inventory.identityKind)
        assertEquals(4, inventory.files.size)
        assertEquals(fixture.bytes.sumOf(ByteArray::size).toLong(), inventory.totalBytes)
        assertEquals(64, inventory.inventoryFingerprint.length)
        assertEquals(64, inventory.fingerprint.length)
        assertEquals(inventory.fingerprint, inventory.authority.fingerprint)
        assertEquals(
            "/node_modules/dayjs/package.json",
            R2DependencyLayerPolicy.toFutureVfsPath(
                "dependencies/node_modules/dayjs/package.json",
            ),
        )
    }

    @Test
    fun nonInstallPackageScriptsAreAcceptedAsInertDependencyBytes() {
        val fixture = fixture()
        val scriptMetadataIndex = fixture.inventory.files.indexOfFirst { file ->
            file.path.endsWith("dayjs/package.json")
        }

        val snapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile("/declarations/lib.d.ts", ""),
                R2ProjectTextFile("/sources/main.mts", "export const ready = true;\n"),
            ),
            rootNames = listOf("/sources/main.mts"),
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            limits = R2ProjectSnapshotLimits.ORDINARY_COMPILATION,
            entryPath = "/sources/main.mts",
            dependencyLayerInventory = fixture.inventory,
            dependencyLayerBytes = fixture.bytes,
        )
        try {
            assertFalse(snapshot.filePaths.any { path -> path.startsWith("/node_modules/") })
            assertEquals(2, snapshot.fileInventory.size)
            assertEquals(1, R2ProjectSnapshot.ordinaryCompilationAdmission(snapshot).capacity.fileCount)
            val actual = ByteArray(fixture.bytes[scriptMetadataIndex].size)
            snapshot.openDependencyLayerFile(scriptMetadataIndex).use { cursor ->
                assertEquals(actual.size, cursor.read(actual, 0, actual.size))
                assertEquals(0L, cursor.remainingBytes)
            }
            assertArrayEquals(fixture.bytes[scriptMetadataIndex], actual)
        } finally {
            snapshot.close()
        }
        assertThrows(IllegalStateException::class.java) {
            snapshot.openDependencyLayerFile(0)
        }
    }

    @Test
    fun nativeAssetsAndInstallLifecycleAreStablePolicyFailures() {
        listOf(
            "dependencies/node_modules/bcrypt/prebuilds/linux-arm64/bcrypt.node",
            "dependencies/node_modules/bcrypt/binding.gyp",
        ).forEach { path ->
            val failure = assertThrows(R2DependencyLayerPolicyException::class.java) {
                descriptor(path, "native")
            }
            assertEquals(
                R2DependencyLayerPolicyCode.NATIVE_DEPENDENCY_UNSUPPORTED,
                failure.policyCode,
            )
        }

        val gypfile = assertThrows(R2DependencyLayerPolicyException::class.java) {
            R2DependencyLayerPolicy.validateDependencyFile(
                "dependencies/node_modules/native-pkg/package.json",
                "{\"name\":\"native-pkg\",\"gypfile\":true}".toByteArray(),
            )
        }
        assertEquals(
            R2DependencyLayerPolicyCode.NATIVE_DEPENDENCY_UNSUPPORTED,
            gypfile.policyCode,
        )

        R2DependencyLayerPolicy.INSTALL_LIFECYCLE_KEYS.forEach { lifecycle ->
            val failure = assertThrows(R2DependencyLayerPolicyException::class.java) {
                R2DependencyLayerPolicy.validateDependencyFile(
                    "dependencies/node_modules/installer/package.json",
                    ("{\"scripts\":{\"$lifecycle\":\"node prepare.js\"}}")
                        .toByteArray(),
                )
            }
            assertEquals(
                R2DependencyLayerPolicyCode.INSTALL_LIFECYCLE_UNSUPPORTED,
                failure.policyCode,
            )
        }
        val deterministicLifecycle = assertThrows(R2DependencyLayerPolicyException::class.java) {
            R2DependencyLayerPolicy.validateDependencyFile(
                "dependencies/node_modules/installer/package.json",
                ("{\"scripts\":{\"preinstall\":\"first-in-json\"," +
                    "\"postinstall\":\"second-in-json\",\"install\":\"last-in-json\"}}")
                    .toByteArray(),
            )
        }
        assertTrue(deterministicLifecycle.message.orEmpty().contains("'install'"))

        R2DependencyLayerPolicy.validateDependencyFile(
            "dependencies/node_modules/pure-js/package.json",
            "{\"scripts\":{\"test\":\"node test.js\",\"build\":\"node build.js\"}}"
                .toByteArray(),
        )
    }

    @Test
    fun currentRhinoSnapshotCarriesTheExactDependencyLayer() {
        val fixture = fixture()
        val snapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile("/declarations/lib.d.ts", ""),
                R2ProjectTextFile(
                    "/sources/main.ts",
                    "import { value } from 'dayjs'; export const ready = value;\n",
                ),
            ),
            rootNames = listOf("/sources/main.ts"),
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            limits = R2ProjectSnapshotLimits.ORDINARY_COMPILATION,
            entryPath = "/sources/main.ts",
            dependencyLayerInventory = fixture.inventory,
            dependencyLayerBytes = fixture.bytes,
        )
        try {
            assertEquals(fixture.inventory, snapshot.dependencyLayerInventory)
            assertEquals(
                fixture.inventory.files.size,
                snapshot.dependencyLayerInventory?.files?.size,
            )
        } finally {
            snapshot.close()
        }
    }

    @Test
    fun excludedAssetsAndCaseFoldCollisionsStayOutsideTheLayer() {
        val markdown = assertThrows(R2DependencyLayerPolicyException::class.java) {
            descriptor("dependencies/node_modules/dayjs/README.md", "docs")
        }
        assertEquals(R2DependencyLayerPolicyCode.PATH_INVALID, markdown.policyCode)

        val testAsset = assertThrows(R2DependencyLayerPolicyException::class.java) {
            descriptor("dependencies/node_modules/dayjs/tests/fixture.js", "test")
        }
        assertEquals(R2DependencyLayerPolicyCode.PATH_INVALID, testAsset.policyCode)

        val first = descriptor("dependencies/node_modules/pkg/index.js", "one")
        val second = descriptor("dependencies/node_modules/PKG/index.js", "two")
        val collision = assertThrows(R2DependencyLayerPolicyException::class.java) {
            R2DependencyLayerInventory.create(
                identityKind = R2DependencyLayerPolicy.IDENTITY_CONTENT,
                identityPath = "",
                identityFingerprint = inventoryFingerprintFor(first, second),
                files = listOf(second, first).sortedBy(R2DependencyLayerFileDescriptor::path),
            )
        }
        assertTrue(collision.message.orEmpty().contains("case-fold"))
    }

    private fun fixture(): Fixture {
        val values = linkedMapOf(
            "dependencies/lockfiles/package-lock.json" to
                "{\"lockfileVersion\":3}",
            "dependencies/node_modules/dayjs/index.js" to
                "export const value = 1;\n",
            "dependencies/node_modules/dayjs/package.json" to
                "{\"name\":\"dayjs\",\"scripts\":{\"test\":\"ignored\"}}",
            "dependencies/node_modules/lodash/index.d.ts" to
                "export declare const value: number;\n",
        )
        val bytes = values.values.map { value -> value.toByteArray(StandardCharsets.UTF_8) }
        val files = values.keys.mapIndexed { index, path ->
            R2DependencyLayerFileDescriptor(
                path = path,
                size = bytes[index].size.toLong(),
                sha256 = R2Digests.sha256(bytes[index]),
            )
        }
        return Fixture(
            inventory = R2DependencyLayerInventory.create(
                identityKind = R2DependencyLayerPolicy.IDENTITY_PACKAGE_LOCK,
                identityPath = "dependencies/lockfiles/package-lock.json",
                identityFingerprint = files.first().sha256,
                files = files,
            ),
            bytes = bytes,
        )
    }

    private fun descriptor(path: String, value: String): R2DependencyLayerFileDescriptor {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        return R2DependencyLayerFileDescriptor(
            path = path,
            size = bytes.size.toLong(),
            sha256 = R2Digests.sha256(bytes),
        )
    }

    private fun inventoryFingerprintFor(
        vararg files: R2DependencyLayerFileDescriptor,
    ): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(
            R2DependencyLayerPolicy.INVENTORY_DOMAIN.toByteArray(StandardCharsets.US_ASCII),
        )
        digest.update('\n'.code.toByte())
        files.sortedBy(R2DependencyLayerFileDescriptor::path).forEach { file ->
            digest.update(file.path.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(file.size.toString().toByteArray(StandardCharsets.US_ASCII))
            digest.update(0)
            digest.update(file.sha256.toByteArray(StandardCharsets.US_ASCII))
            digest.update('\n'.code.toByte())
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private data class Fixture(
        val inventory: R2DependencyLayerInventory,
        val bytes: List<ByteArray>,
    )
}
