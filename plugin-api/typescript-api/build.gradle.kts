import java.security.MessageDigest

plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "org.autojs.plugin.typescript.api"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        consumerProguardFiles("consumer-rules.pro")
    }

    lint {
        targetSdk = versions.sdkVersionTarget
        abortOnError = true
    }

    buildFeatures {
        aidl = true
        buildConfig = false
    }
}

dependencies {
    api(project(":plugin-api:common-plugin-api"))
    testImplementation(libs.junit)
}

val protocolRepositoryRoot = rootProject.layout.projectDirectory
val releaseApiAar = layout.buildDirectory.file("outputs/aar/typescript-api-release.aar")
val frozenApiAar = protocolRepositoryRoot.file("compat/v1/typescript-api-v1.aar")
val frozenApiChecksum =
    protocolRepositoryRoot.file("compat/v1/typescript-api-v1.aar.sha256")
val protocolFreezeSourceInputs = listOf(
    protocolRepositoryRoot.file("compat/v1/binder-transactions.json"),
    protocolRepositoryRoot.file("compat/v1/bundle-fixtures.json"),
    layout.projectDirectory.file(
        "src/main/aidl/org/autojs/plugin/typescript/api/ITypeScriptCompilerPlugin.aidl",
    ),
    layout.projectDirectory.file(
        "src/main/aidl/org/autojs/plugin/typescript/api/ITypeScriptCompilationCallback.aidl",
    ),
    layout.projectDirectory.file(
        "src/main/java/org/autojs/plugin/typescript/api/TypeScriptCompilerContract.kt",
    ),
)

fun sha256(file: File): String =
    MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    inputs.files(protocolFreezeSourceInputs)
    systemProperty(
        "typescript.protocol.repositoryRoot",
        protocolRepositoryRoot.asFile.absolutePath,
    )
}

tasks.register("freezeTypeScriptApiV1") {
    group = "release"
    description =
        "Explicitly replaces the checked-in v1 API AAR and SHA-256 with the current release AAR."
    dependsOn("bundleReleaseAar")
    inputs.file(releaseApiAar)
    outputs.files(frozenApiAar, frozenApiChecksum)

    doLast {
        val source = releaseApiAar.get().asFile
        check(source.isFile) { "Release API AAR is missing: $source" }
        val target = frozenApiAar.asFile
        target.parentFile.mkdirs()
        source.copyTo(target, overwrite = true)
        frozenApiChecksum.asFile.writeText(
            "${sha256(target)}  ${target.name}\n",
        )
    }
}

val verifyTypeScriptApiV1Freeze = tasks.register("verifyTypeScriptApiV1Freeze") {
    group = "verification"
    description =
        "Read-only comparison of real AIDL/contract fixtures and the current release AAR against v1."
    dependsOn("testDebugUnitTest", "bundleReleaseAar")
    inputs.files(frozenApiAar, frozenApiChecksum, releaseApiAar)

    doLast {
        val frozen = frozenApiAar.asFile
        val checksum = frozenApiChecksum.asFile
        val current = releaseApiAar.get().asFile
        check(frozen.isFile) {
            "Frozen API AAR is missing. Run freezeTypeScriptApiV1 explicitly."
        }
        check(checksum.isFile) { "Frozen API checksum is missing: $checksum" }
        val checksumLine = checksum.readText().trim()
        val expectedLine = "${sha256(frozen)}  ${frozen.name}"
        check(checksumLine == expectedLine) {
            "Frozen API checksum does not match ${frozen.name}."
        }
        check(sha256(current) == sha256(frozen)) {
            "Current release API AAR drifted from compat/v1/typescript-api-v1.aar. " +
                "Verification is read-only; review the protocol before explicitly freezing it."
        }
    }
}

tasks.named("check").configure {
    dependsOn(verifyTypeScriptApiV1Freeze)
}
