package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertThrows
import org.junit.Test

class R2DeclarationDistributionTest {

    @Test
    fun acceptsOnlyTheExactlyFrozenRhinoSourceIdentity() {
        R2DeclarationDistributionLoader.validateRhinoSourceDescriptorForTesting(
            RHINO_SOURCE.toByteArray(StandardCharsets.UTF_8),
        )

        val tamperedDescriptors = listOf(
            RHINO_SOURCE.replace(
                EXPECTED_RHINO_SOURCE,
                "https://registry.npmjs.org/@sm003/autojs6-dts/-/autojs6-dts-2.1.4.tgz",
            ),
            RHINO_SOURCE.replace(EXPECTED_RHINO_SHA1, "0".repeat(40)),
            RHINO_SOURCE.replace(EXPECTED_RHINO_SHA256, "0".repeat(64)),
            RHINO_SOURCE.replace(EXPECTED_RHINO_INTEGRITY, "sha512-AA=="),
            RHINO_SOURCE.replace(
                "\"noticeFile\":\"notices/rhino-source.txt\"",
                "\"noticeFile\":\"notices/rhino-source.txt\",\"extra\":true",
            ),
        )
        tamperedDescriptors.forEach { descriptor ->
            assertThrows(IllegalArgumentException::class.java) {
                R2DeclarationDistributionLoader.validateRhinoSourceDescriptorForTesting(
                    descriptor.toByteArray(StandardCharsets.UTF_8),
                )
            }
        }
    }

    @Test
    fun commonBindingRequiresExactKeysAndTheCompilerDistributionIdentity() {
        val common = commonDeclarations()
        val source = commonSource(common, COMPILER_DISTRIBUTION_FINGERPRINT)
        val binding = commonBinding(common, COMPILER_DISTRIBUTION_FINGERPRINT)

        R2DeclarationDistributionLoader.validateCommonSourceAndBindingForTesting(
            sourceBytes = source.toByteArray(StandardCharsets.UTF_8),
            bindingBytes = binding.toByteArray(StandardCharsets.UTF_8),
            common = common,
            compilerDistributionFingerprint = COMPILER_DISTRIBUTION_FINGERPRINT,
        )

        assertThrows(IllegalArgumentException::class.java) {
            R2DeclarationDistributionLoader.validateCommonSourceAndBindingForTesting(
                sourceBytes = source.replace(
                    COMPILER_DISTRIBUTION_FINGERPRINT,
                    ZERO_SHA256,
                ).toByteArray(StandardCharsets.UTF_8),
                bindingBytes = binding.toByteArray(StandardCharsets.UTF_8),
                common = common,
                compilerDistributionFingerprint = COMPILER_DISTRIBUTION_FINGERPRINT,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2DeclarationDistributionLoader.validateCommonSourceAndBindingForTesting(
                sourceBytes = source.toByteArray(StandardCharsets.UTF_8),
                bindingBytes = binding.replace(
                    "\"totalBytes\":${common.files.single().size}",
                    "\"totalBytes\":${common.files.single().size},\"extra\":true",
                ).toByteArray(StandardCharsets.UTF_8),
                common = common,
                compilerDistributionFingerprint = COMPILER_DISTRIBUTION_FINGERPRINT,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2DeclarationDistributionLoader.validateCommonSourceAndBindingForTesting(
                sourceBytes = source.toByteArray(StandardCharsets.UTF_8),
                bindingBytes = binding.replace(
                    COMPILER_DISTRIBUTION_FINGERPRINT,
                    ZERO_SHA256,
                ).toByteArray(StandardCharsets.UTF_8),
                common = common,
                compilerDistributionFingerprint = COMPILER_DISTRIBUTION_FINGERPRINT,
            )
        }
    }

    @Test
    fun packagedPathsAllowFrozenLicenseCaseButRejectAsciiCaseFoldCollisions() {
        R2DeclarationDistributionLoader.validatePackagedPathsForTesting(
            listOf("licenses/rhino/MPL-2.0.txt"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            R2DeclarationDistributionLoader.validatePackagedPathsForTesting(
                listOf(
                    "licenses/rhino/MPL-2.0.txt",
                    "licenses/rhino/mpl-2.0.txt",
                ),
            )
        }
    }

    private fun commonDeclarations(): R2TrustedDeclarations {
        val text = "interface CommonDeclarationTest {}\n"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val file = R2TrustedDeclarationFile(
            archivePath = "declarations/lib.test.d.ts",
            fingerprintPath = "lib/lib.test.d.ts",
            size = bytes.size.toLong(),
            sha256 = R2Digests.sha256(bytes),
            text = text,
        )
        return R2TrustedDeclarations.create(
            fingerprint = R2TrustedDeclarations.fingerprint(listOf(file)),
            files = listOf(file),
        )
    }

    private fun commonSource(
        common: R2TrustedDeclarations,
        distributionFingerprint: String,
    ): String = """
        {
          "kind":"typescript-standard-libraries",
          "package":"typescript",
          "version":"${BuildConfig.TYPESCRIPT_VERSION}",
          "distributionAssetRoot":"typescript/${BuildConfig.TYPESCRIPT_VERSION}",
          "distributionManifest":"typescript/${BuildConfig.TYPESCRIPT_VERSION}/distribution.json",
          "bindingFile":"layers/common/binding.json",
          "distributionFingerprint":"$distributionFingerprint",
          "standardLibrariesFingerprint":"${common.fingerprint}",
          "fileCount":${common.files.size},
          "totalBytes":${common.files.sumOf(R2TrustedDeclarationFile::size)}
        }
    """.trimIndent()

    private fun commonBinding(
        common: R2TrustedDeclarations,
        distributionFingerprint: String,
    ): String = """
        {
          "schemaVersion":1,
          "package":"typescript",
          "version":"${BuildConfig.TYPESCRIPT_VERSION}",
          "distributionAssetRoot":"typescript/${BuildConfig.TYPESCRIPT_VERSION}",
          "distributionManifest":"typescript/${BuildConfig.TYPESCRIPT_VERSION}/distribution.json",
          "distributionFingerprint":"$distributionFingerprint",
          "standardLibrariesFingerprint":"${common.fingerprint}",
          "fileCount":${common.files.size},
          "totalBytes":${common.files.sumOf(R2TrustedDeclarationFile::size)}
        }
    """.trimIndent()

    private companion object {
        const val EXPECTED_RHINO_SOURCE =
            "https://registry.npmjs.org/@sm003/autojs6-dts/-/autojs6-dts-2.1.3.tgz"
        const val EXPECTED_RHINO_SHA1 = "20be1a50a9d029b150c99939c9f307dfebdf5abf"
        const val EXPECTED_RHINO_SHA256 =
            "ecc90a666c4383060d712a478752f9273534605add0f921e1537187f4386983c"
        const val EXPECTED_RHINO_INTEGRITY =
            "sha512-0k5z1il4at5C4uIDOAjFZmg6yTk8qsooahniDlc1flKOLl2AN6xTJELyddb+" +
                "yv4HRrTXRpCruhSz4pY40aLTjQ=="
        const val RHINO_SOURCE =
            "{\"kind\":\"npm-tarball-projection\",\"package\":\"@sm003/autojs6-dts\"," +
                "\"version\":\"2.1.3\",\"source\":\"$EXPECTED_RHINO_SOURCE\"," +
                "\"sha1\":\"$EXPECTED_RHINO_SHA1\",\"sha256\":\"$EXPECTED_RHINO_SHA256\"," +
                "\"integrity\":\"$EXPECTED_RHINO_INTEGRITY\"," +
                "\"conversion\":\"autojs6-rhino-modules-v2\",\"license\":\"MPL-2.0\"," +
                "\"licenseFile\":\"licenses/rhino/MPL-2.0.txt\"," +
                "\"noticeFile\":\"notices/rhino-source.txt\"}"
        const val COMPILER_DISTRIBUTION_FINGERPRINT =
            "3855b04b968db8a9529b60c96dd8b8ec94fa1ee040cac10a5ab569aa6e33f658"
        val ZERO_SHA256 = "0".repeat(64)
    }
}
