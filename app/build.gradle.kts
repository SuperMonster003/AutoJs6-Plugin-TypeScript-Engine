import com.android.build.api.variant.FilterConfiguration
import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import org.autojs.build.DownloadTypeScriptDistributionTarball
import org.autojs.build.GenerateTypeScriptDistribution
import org.autojs.build.VerifyTypeScriptDistribution
import org.gradle.api.provider.Property

plugins {
    id("org.autojs.build.utils")
    id("org.autojs.build.versions")
    id("org.autojs.build.signs")
    id("org.autojs.build.jvm-convention")
    id("com.android.application")
}

val globalApplicationId = "io.github.supermonster003.autojs6.plugin.typescript"
val projectProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val typeScriptVersion = projectProperties.getProperty("TYPESCRIPT_VERSION")
val requiredHostVersion = projectProperties.getProperty("AUTOJS6_HOST_VERSION_BUILD").toInt()
val generatedTypeScriptAssetsDirectory =
    layout.buildDirectory.dir("generated/typescriptCompilerAssets/assets")
val generatedTypeScriptDistributionDirectory =
    generatedTypeScriptAssetsDirectory.map { it.dir("typescript/$typeScriptVersion") }

android {
    namespace = globalApplicationId
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = globalApplicationId
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = versions.appVersionCode
        versionName = versions.appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TYPESCRIPT_VERSION", "\"$typeScriptVersion\"")
        buildConfigField("int", "REQUIRED_HOST_VERSION", "$requiredHostVersion")
        manifestPlaceholders["requiredHostVersion"] = requiredHostVersion
        buildConfigField(
            "String",
            "VERSION_DATE",
            "\"${utils.getDateString("MMM d, yyyy", "GMT+08:00")}\"",
        )
    }

    lint {
        abortOnError = true
    }

    signingConfigs {
        if (signs.isValid) {
            create("release") {
                storeFile = signs.properties["storeFile"]?.let { file(it as String) }
                keyPassword = signs.properties["keyPassword"] as String
                keyAlias = signs.properties["keyAlias"] as String
                storePassword = signs.properties["storePassword"] as String
            }
        }
    }

    buildTypes {
        val proguardFiles = arrayOf<Any>(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
        val releaseSigning = takeIf { signs.isValid }?.let {
            signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(*proguardFiles)
            releaseSigning?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(*proguardFiles)
            releaseSigning?.let { signingConfig = it }
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(generatedTypeScriptAssetsDirectory.get().asFile.absolutePath)
        }
    }

    packaging {
        resources.pickFirsts.addAll(
            listOf(
                "META-INF/LICENSE",
                "META-INF/LICENSE.*",
                "META-INF/NOTICE",
                "META-INF/NOTICE.*",
                "META-INF/*.kotlin_module",
            ),
        )
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val architecture = output.filters.find {
                it.filterType == FilterConfiguration.FilterType.ABI
            }?.identifier ?: "universal"
            val outputFileNameProperty = output.javaClass.methods.firstOrNull {
                it.name == "getOutputFileName" && it.parameterTypes.isEmpty()
            }?.invoke(output) as? Property<*>

            @Suppress("UNCHECKED_CAST")
            (outputFileNameProperty as? Property<String>)?.set(
                output.versionName.map { versionName ->
                    val version = versionName.replace("\\s".toRegex(), "-")
                    "${rootProject.name}-v$version-$architecture.apk".lowercase()
                },
            )
        }
    }
}

dependencies {
    implementation(project(":plugin-api:common-plugin-api"))
    implementation(project(":plugin-api:typescript-api"))
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.runner)
    testImplementation(libs.junit)
}

val compilerDistributionLockFile =
    rootProject.layout.projectDirectory.file("compiler-distribution.lock.json")
val downloadTypeScriptDistributionTarball =
    tasks.register<DownloadTypeScriptDistributionTarball>(
        "downloadTypeScriptDistributionTarball",
    ) {
        lockFile.set(compilerDistributionLockFile)
        tarballFile.set(
            layout.buildDirectory.file(
                "typescript-distribution/typescript-$typeScriptVersion.tgz",
            ),
        )
    }
val generateTypeScriptDistribution =
    tasks.register<GenerateTypeScriptDistribution>("generateTypeScriptDistribution") {
        lockFile.set(compilerDistributionLockFile)
        tarballFile.set(downloadTypeScriptDistributionTarball.flatMap { it.tarballFile })
        distributionDirectory.set(generatedTypeScriptDistributionDirectory)
    }
val verifyTypeScriptDistribution =
    tasks.register<VerifyTypeScriptDistribution>("verifyTypeScriptDistribution") {
        dependsOn(generateTypeScriptDistribution)
        lockFile.set(compilerDistributionLockFile)
        tarballFile.set(downloadTypeScriptDistributionTarball.flatMap { it.tarballFile })
        distributionDirectory.set(
            generateTypeScriptDistribution.flatMap { it.distributionDirectory },
        )
    }

private fun ByteArray.sha256String(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

@Suppress("UNCHECKED_CAST")
private fun resolveSingleApkOutput(apkDirectory: File): File {
    val metadataFile = apkDirectory.resolve("output-metadata.json")
    check(metadataFile.isFile) {
        "APK output metadata does not exist: $metadataFile"
    }
    val metadata = JsonSlurper().parse(metadataFile) as Map<String, Any?>
    check((metadata["artifactType"] as? Map<String, Any?>)?.get("type") == "APK")
    val elements = metadata["elements"] as? List<Map<String, Any?>>
        ?: error("APK output metadata does not contain an elements array.")
    check(elements.size == 1 && elements.single()["type"] == "SINGLE") {
        "Expected one universal APK output, found $elements"
    }
    val outputName = elements.single()["outputFile"] as? String
        ?: error("APK output metadata does not declare outputFile.")
    check(outputName.isNotBlank() && File(outputName).name == outputName) {
        "APK output metadata contains an unsafe outputFile: $outputName"
    }
    return apkDirectory.resolve(outputName).also { apkFile ->
        check(apkFile.isFile) {
            "APK declared by output metadata does not exist: $apkFile"
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun verifyTypeScriptDistributionInApk(
    apkFile: File,
    distributionDirectory: File,
    typeScriptVersion: String,
) {
    val manifestFile = distributionDirectory.resolve("distribution.json")
    check(manifestFile.isFile) {
        "TypeScript distribution manifest does not exist: $manifestFile"
    }
    val manifestBytes = manifestFile.readBytes()
    val manifest = JsonSlurper().parse(manifestFile) as Map<String, Any?>
    check((manifest["schemaVersion"] as? Number)?.toInt() == 1)
    val packageInfo = manifest["package"] as? Map<String, Any?> ?: emptyMap()
    check(packageInfo["name"] == "typescript")
    check(packageInfo["version"] == typeScriptVersion)

    val manifestFiles = manifest["files"] as? List<Map<String, Any?>>
        ?: error("TypeScript distribution manifest does not contain a files array.")
    val expectedFiles = manifestFiles.associate { file ->
        val path = file["path"] as? String
            ?: error("TypeScript distribution file is missing its path.")
        check(path.isNotBlank() && !path.startsWith('/') && '\\' !in path)
        val size = (file["size"] as? Number)?.toLong()
            ?: error("TypeScript distribution file $path is missing its size.")
        val sha256 = file["sha256"] as? String
            ?: error("TypeScript distribution file $path is missing its SHA-256.")
        check(size >= 0L)
        check(sha256.matches(Regex("[0-9a-f]{64}")))
        path to (size to sha256)
    }
    check(expectedFiles.size == manifestFiles.size) {
        "TypeScript distribution manifest contains duplicate paths."
    }
    check(expectedFiles.size == 112) {
        "Expected 112 TypeScript distribution files, found ${expectedFiles.size}."
    }
    check("lib/typescript.js" in expectedFiles)
    check("lib/typescript.d.ts" in expectedFiles)
    check("LICENSE.txt" in expectedFiles)
    check("ThirdPartyNoticeText.txt" in expectedFiles)

    val components = manifest["components"] as? Map<String, Any?> ?: emptyMap()
    val standardLibraries =
        components["standardLibraries"] as? Map<String, Any?> ?: emptyMap()
    check((standardLibraries["fileCount"] as? Number)?.toInt() == 108) {
        "Expected 108 TypeScript standard library declarations."
    }

    val assetPrefix = "assets/typescript/$typeScriptVersion/"
    ZipFile(apkFile).use { apk ->
        val allEntries = apk.entries().asSequence().toList()
        val distributionEntries = allEntries.filter {
            !it.isDirectory && it.name.startsWith(assetPrefix)
        }
        val duplicates = distributionEntries
            .groupingBy { it.name }
            .eachCount()
            .filterValues { it != 1 }
        check(duplicates.isEmpty()) {
            "APK contains duplicate TypeScript distribution entries: $duplicates"
        }

        val expectedEntryNames = (
            expectedFiles.keys.map { "$assetPrefix$it" } +
                "$assetPrefix${manifestFile.name}"
            ).toSet()
        val actualEntryNames = distributionEntries.map { it.name }.toSet()
        check(actualEntryNames == expectedEntryNames) {
            "APK TypeScript distribution mismatch: " +
                "missing=${expectedEntryNames - actualEntryNames}, " +
                "extra=${actualEntryNames - expectedEntryNames}"
        }

        val packagedManifest = requireNotNull(apk.getEntry("$assetPrefix${manifestFile.name}"))
        val packagedManifestBytes = apk.getInputStream(packagedManifest).use { it.readBytes() }
        check(MessageDigest.isEqual(manifestBytes, packagedManifestBytes)) {
            "APK distribution.json differs from the verified generated manifest."
        }

        expectedFiles.forEach { (path, expected) ->
            val entry = requireNotNull(apk.getEntry("$assetPrefix$path"))
            val bytes = apk.getInputStream(entry).use { it.readBytes() }
            check(bytes.size.toLong() == expected.first) {
                "APK TypeScript file size mismatch for $path."
            }
            check(bytes.sha256String() == expected.second) {
                "APK TypeScript file SHA-256 mismatch for $path."
            }
        }

        val compilerEntries = allEntries.filter {
            !it.isDirectory && (
                it.name == "lib/typescript.js" ||
                    it.name.endsWith("/lib/typescript.js")
            )
        }
        check(compilerEntries.size == 1) {
            "APK must contain exactly one TypeScript compiler JS, found " +
                compilerEntries.map { it.name }
        }
    }
}

fun registerTypeScriptDistributionApkVerification(
    taskName: String,
    assembleTaskName: String,
    apkDirectoryPath: String,
) = tasks.register(taskName) {
    group = "verification"
    description = "Verifies the TypeScript compiler distribution in the $assembleTaskName APK."
    dependsOn(assembleTaskName)
    dependsOn(verifyTypeScriptDistribution)
    val apkDirectory = layout.buildDirectory.dir(apkDirectoryPath)
    inputs.dir(apkDirectory)
    inputs.dir(generatedTypeScriptDistributionDirectory)
    doLast {
        verifyTypeScriptDistributionInApk(
            apkFile = resolveSingleApkOutput(apkDirectory.get().asFile),
            distributionDirectory = generatedTypeScriptDistributionDirectory.get().asFile,
            typeScriptVersion = typeScriptVersion,
        )
    }
}

val verifyDebugTypeScriptDistributionApk =
    registerTypeScriptDistributionApkVerification(
        taskName = "verifyDebugTypeScriptDistributionApk",
        assembleTaskName = "assembleDebug",
        apkDirectoryPath = "outputs/apk/debug",
    )
val verifyReleaseTypeScriptDistributionApk =
    registerTypeScriptDistributionApkVerification(
        taskName = "verifyReleaseTypeScriptDistributionApk",
        assembleTaskName = "assembleRelease",
        apkDirectoryPath = "outputs/apk/release",
    )

val r0ScannedSources = files(
    fileTree(rootProject.projectDir) {
        include("**/*.gradle.kts")
        include("**/*.properties")
        include("app/src/main/**")
        include("plugin-api/**/src/main/**")
        exclude("**/build/**")
    },
)

tasks {
    withType(JavaCompile::class.java) {
        options.encoding = "UTF-8"
    }

    register("verifyNodeRuntimeIndependence") {
        group = "verification"
        description = "Ensures the TypeScript compiler plugin has no Node Runtime dependency."
        inputs.files(r0ScannedSources)
        doLast {
            val forbidden = listOf(
                "nodejs" + "-api",
                "org.autojs.plugin." + "nodejs",
                "io.github.supermonster003.autojs6.plugin." + "nodejs",
                "lib" + "node.so",
            )
            val violations = r0ScannedSources.files
                .filter(File::isFile)
                .flatMap { file ->
                    val text = file.readText()
                    forbidden.filter(text::contains).map { token ->
                        "${file.relativeTo(rootProject.projectDir).invariantSeparatorsPath}: $token"
                    }
                }
                .plus(
                    configurations
                        .flatMap { configuration -> configuration.dependencies }
                        .map { dependency ->
                            listOfNotNull(dependency.group, dependency.name).joinToString(":")
                        }
                        .filter { notation -> forbidden.any(notation::contains) }
                        .map { notation -> "declared dependency: $notation" },
                )
            check(violations.isEmpty()) {
                "Node Runtime independence check failed:\n${violations.joinToString("\n")}"
            }
        }
    }

    register("verifyCompilerDistributionLock") {
        group = "verification"
        description = "Verifies the pinned TypeScript compiler distribution metadata."
        val lockFile = rootProject.file("compiler-distribution.lock.json")
        inputs.file(lockFile)
        inputs.file(rootProject.file("version.properties"))
        doLast {
            @Suppress("UNCHECKED_CAST")
            val lock = JsonSlurper().parse(lockFile) as Map<String, Any?>
            check(lock["schemaVersion"] == 2)
            check(lock["package"] == "typescript")
            check(lock["version"] == typeScriptVersion) {
                "TypeScript lock version ${lock["version"]} does not match $typeScriptVersion"
            }
            check(
                lock["source"] ==
                    "https://registry.npmjs.org/typescript/-/typescript-$typeScriptVersion.tgz",
            )
            check((lock["sha1"] as? String)?.matches(Regex("[0-9a-f]{40}")) == true)
            check(
                (lock["integrity"] as? String)
                    ?.matches(Regex("sha512-[A-Za-z0-9+/]+={0,2}")) == true,
            )
            check((lock["sha256"] as? String)?.matches(Regex("[0-9a-f]{64}")) == true)
            check(lock["runtimeEntry"] == "package/lib/typescript.js")
            check(lock["declarationEntry"] == "package/lib/typescript.d.ts")
            check(lock["licenseEntry"] == "package/LICENSE.txt")
            check(lock["thirdPartyNoticeEntry"] == "package/ThirdPartyNoticeText.txt")
            check(lock["standardLibraryGlob"] == "package/lib/lib*.d.ts")
            check(lock["bundled"] == true) {
                "R1 compiler distribution lock must declare the verified bundle."
            }
        }
    }

    register("verifyR0DebugApkContents") {
        group = "verification"
        description = "Verifies the R0 APK shape and merged plugin manifest."
        dependsOn("assembleDebug")
        val apkDirectory = layout.buildDirectory.dir("outputs/apk/debug")
        val mergedManifest = layout.buildDirectory.file(
            "intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml",
        )
        inputs.dir(apkDirectory)
        inputs.file(mergedManifest)
        doLast {
            val apkFiles = apkDirectory.get().asFile.listFiles()
                ?.filter { it.extension.equals("apk", ignoreCase = true) }
                .orEmpty()
            check(apkFiles.size == 1) {
                "Expected one universal debug APK, found ${apkFiles.map(File::getName)}"
            }
            ZipFile(apkFiles.single()).use { apk ->
                val entries = apk.entries().asSequence().map { it.name }.toList()
                check("AndroidManifest.xml" in entries)
                check(entries.any { it == "classes.dex" || it.matches(Regex("classes\\d+\\.dex")) })
                check(entries.none { it.startsWith("lib/") }) {
                    "R0 APK unexpectedly contains native libraries."
                }
            }
            val manifestText = mergedManifest.get().asFile.readText()
            check("org.autojs.plugin.INFO" in manifestText)
            check("org.autojs.plugin.typescript.COMPILER" in manifestText)
            check("android:process=\":typescript_compiler\"" in manifestText)
            check("android.permission.INTERNET" !in manifestText)
        }
    }

    named("check") {
        dependsOn("verifyNodeRuntimeIndependence")
        dependsOn("verifyCompilerDistributionLock")
        dependsOn("verifyR0DebugApkContents")
        dependsOn(verifyTypeScriptDistribution)
        dependsOn(verifyDebugTypeScriptDistributionApk)
    }

    matching {
        val taskName = it.name
        taskName.contains("lint", ignoreCase = true) ||
            (taskName.startsWith("merge") && taskName.endsWith("Assets"))
    }.configureEach {
        dependsOn(generateTypeScriptDistribution)
    }
}
