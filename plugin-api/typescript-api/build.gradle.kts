import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

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

fun classesJarEntries(aar: File): Map<String, ByteArray> =
    ZipFile(aar).use { aarZip ->
        val classesJar = checkNotNull(aarZip.getEntry("classes.jar")) {
            "AAR does not contain classes.jar: $aar"
        }
        ZipInputStream(
            ByteArrayInputStream(aarZip.getInputStream(classesJar).use { it.readBytes() }),
        ).use { classesZip ->
            buildMap {
                while (true) {
                    val entry = classesZip.nextEntry ?: break
                    if (!entry.isDirectory && !entry.name.isKotlinModuleMetadata()) {
                        put(entry.name, classesZip.readBytes())
                    }
                }
            }
        }
    }

fun String.isKotlinModuleMetadata(): Boolean =
    startsWith("META-INF/") && endsWith(".kotlin_module")

fun String.isTypeScriptR2ContractClass(): Boolean {
    val contractClass =
        "org/autojs/plugin/typescript/api/TypeScriptR2Contract"
    return this == "$contractClass.class" ||
        startsWith("${contractClass}\$") && endsWith(".class")
}

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
        "Refuses to replace the immutable v1 API baseline with a post-v1 release AAR."

    doLast {
        throw GradleException(
            "The TypeScript API v1 baseline is immutable and cannot be regenerated " +
                "from the current post-v1 sources. Restore compat/v1/typescript-api-v1.aar " +
                "and its .sha256 from version control or a trusted legacy v1 release.",
        )
    }
}

val verifyTypeScriptApiV1Freeze = tasks.register("verifyTypeScriptApiV1Freeze") {
    group = "verification"
    description =
        "Verifies frozen v1 bytes and permits only the additive R2 contract in the current AAR."
    dependsOn("testDebugUnitTest", "bundleReleaseAar")
    inputs.files(frozenApiAar, frozenApiChecksum, releaseApiAar)

    doLast {
        val frozen = frozenApiAar.asFile
        val checksum = frozenApiChecksum.asFile
        val current = releaseApiAar.get().asFile
        check(frozen.isFile) {
            "Frozen API AAR is missing: $frozen. Do not run freezeTypeScriptApiV1: " +
                "the current AAR contains post-v1 additive API. Restore the immutable " +
                "baseline from version control or a trusted legacy v1 release."
        }
        check(checksum.isFile) {
            "Frozen API checksum is missing: $checksum. Restore it together with the " +
                "immutable v1 AAR from version control or a trusted legacy v1 release."
        }
        val checksumLine = checksum.readText().trim()
        val expectedLine = "${sha256(frozen)}  ${frozen.name}"
        check(checksumLine == expectedLine) {
            "Frozen API checksum does not match ${frozen.name}."
        }
        val frozenEntries = classesJarEntries(frozen)
        val currentEntries = classesJarEntries(current)
        val missingEntries = frozenEntries.keys - currentEntries.keys
        check(missingEntries.isEmpty()) {
            "Current classes.jar removed frozen v1 entries: " +
                missingEntries.sorted().joinToString()
        }
        val changedEntries = frozenEntries.keys.filter { entry ->
            !checkNotNull(currentEntries[entry]).contentEquals(
                checkNotNull(frozenEntries[entry]),
            )
        }
        check(changedEntries.isEmpty()) {
            "Current classes.jar changed frozen v1 entries: " +
                changedEntries.sorted().joinToString()
        }
        val addedEntries = currentEntries.keys - frozenEntries.keys
        val disallowedEntries = addedEntries.filterNot(String::isTypeScriptR2ContractClass)
        check(disallowedEntries.isEmpty()) {
            "Current classes.jar contains non-R2 API additions: " +
                disallowedEntries.sorted().joinToString()
        }
        check(addedEntries.any(String::isTypeScriptR2ContractClass)) {
            "Current classes.jar does not contain the additive TypeScriptR2Contract API."
        }
    }
}

tasks.named("check").configure {
    dependsOn(verifyTypeScriptApiV1Freeze)
}
