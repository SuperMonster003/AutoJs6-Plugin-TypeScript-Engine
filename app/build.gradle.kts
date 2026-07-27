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
val compilerHostSourceAssetDirectory =
    layout.projectDirectory.dir("src/main/assets/typescript-compiler-r1")

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
    implementation(libs.webkit)
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

private val expectedCompilerHostAssetNames = setOf(
    "compiler-host.js",
    "compiler-worker.js",
    "index.html",
)
private val expectedCompilerHostOriginPaths = expectedCompilerHostAssetNames +
    "typescript.js"
private val compilerHostOrigin = "https://appassets.androidplatform.net"
private val compilerHostRootPath = "/typescript-compiler-r1/"

private fun verifyCompilerHostSourcePolicy(
    hostAssetDirectory: File,
    originSourceFile: File,
    webViewHostSourceFile: File,
    productionSourceFiles: Collection<File>,
    manifestFile: File,
) {
    check(hostAssetDirectory.isDirectory) {
        "Compiler host asset directory does not exist: $hostAssetDirectory"
    }
    val actualHostAssets = hostAssetDirectory.walkTopDown()
        .filter(File::isFile)
        .map { it.relativeTo(hostAssetDirectory).invariantSeparatorsPath }
        .toSet()
    check(actualHostAssets == expectedCompilerHostAssetNames) {
        "Compiler host assets must match the exact allowlist: " +
            "expected=$expectedCompilerHostAssetNames, actual=$actualHostAssets"
    }

    val hostAssetText = expectedCompilerHostAssetNames.associateWith { name ->
        hostAssetDirectory.resolve(name).also { file ->
            check(file.isFile && file.length() > 0L) {
                "Compiler host asset is missing or empty: $file"
            }
        }.readText(Charsets.UTF_8)
    }
    val forbiddenJavaScriptPatterns = mapOf(
        "eval(...)" to Regex("""(?:^|[^\w$])eval\s*\("""),
        "Function(...)" to Regex("""(?:^|[^\w$])(?:new\s+)?Function\s*\("""),
        "javascript: URL" to Regex("""["']javascript\s*:""", RegexOption.IGNORE_CASE),
    )
    hostAssetText.forEach { (name, source) ->
        forbiddenJavaScriptPatterns.forEach { (label, pattern) ->
            check(!pattern.containsMatchIn(source)) {
                "Compiler host asset $name contains forbidden JavaScript: $label"
            }
        }
        check(!Regex("""https?://""", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            "Compiler host asset $name must not embed an absolute network URL."
        }
    }

    val indexHtml = hostAssetText.getValue("index.html")
    val requiredCspDirectives = setOf(
        "default-src 'none'",
        "script-src 'self'",
        "worker-src 'self'",
        "child-src 'self'",
        "connect-src 'none'",
        "frame-src 'none'",
        "object-src 'none'",
        "base-uri 'none'",
        "form-action 'none'",
    )
    check(indexHtml.contains("http-equiv=\"Content-Security-Policy\"")) {
        "Compiler host index must declare a Content-Security-Policy meta element."
    }
    check(requiredCspDirectives.all(indexHtml::contains)) {
        "Compiler host index CSP is missing a required directive."
    }
    check(indexHtml.contains("src=\"compiler-host.js\"")) {
        "Compiler host index must load only compiler-host.js."
    }
    check(
        !Regex(
            """<script\b(?![^>]*\bsrc\s*=)[^>]*>""",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(indexHtml),
    ) {
        "Compiler host index must not contain inline script."
    }

    val pageSource = hostAssetText.getValue("compiler-host.js")
    val workerSource = hostAssetText.getValue("compiler-worker.js")
    check(pageSource.contains("new Worker(\"compiler-worker.js\"")) {
        "Compiler host page must create only the allowlisted compiler-worker.js."
    }
    check(workerSource.contains("""importScripts("typescript.js");""")) {
        "Compiler worker must load the versioned compiler through the typescript.js alias."
    }

    check(originSourceFile.isFile) {
        "Compiler asset origin source does not exist: $originSourceFile"
    }
    val originSource = originSourceFile.readText(Charsets.UTF_8)
    val declaredOrigins = Regex(
        """https?://[A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=%-]+""",
    ).findAll(originSource).map(MatchResult::value).toSet()
    check(declaredOrigins == setOf(compilerHostOrigin)) {
        "Compiler asset origin must be exactly $compilerHostOrigin, found $declaredOrigins"
    }
    check(originSource.contains("const val ROOT_PATH = \"$compilerHostRootPath\"")) {
        "Compiler asset root path must be exactly $compilerHostRootPath."
    }
    check(originSource.contains("uri.scheme == \"https\"")) {
        "Compiler asset origin must require HTTPS."
    }
    check(originSource.contains("uri.encodedAuthority == WebViewAssetLoader.DEFAULT_DOMAIN")) {
        "Compiler asset origin must require the reserved appassets authority."
    }
    check(originSource.contains("uri.encodedQuery == null")) {
        "Compiler asset origin must reject query strings."
    }
    check(originSource.contains("uri.encodedFragment == null")) {
        "Compiler asset origin must reject fragments."
    }
    check(!originSource.contains(".setHttpAllowed(true)")) {
        "Compiler asset loader must not allow HTTP."
    }
    check(
        originSource.contains(
            "assetPath = \"typescript/\$compilerVersion/lib/typescript.js\"",
        ),
    ) {
        "Compiler asset origin must map typescript.js to the pinned version directory."
    }
    val allowlistBlock = Regex(
        """private val ALLOWED_ENCODED_PATHS\s*=\s*setOf\((.*?)\n\s*\)""",
        RegexOption.DOT_MATCHES_ALL,
    ).find(originSource)?.groupValues?.get(1)
        ?: error("Compiler asset origin does not declare ALLOWED_ENCODED_PATHS.")
    val declaredOriginPaths = Regex(
        """\$\{ROOT_PATH}([A-Za-z0-9.-]+)""",
    ).findAll(allowlistBlock).map { it.groupValues[1] }.toSet()
    check(declaredOriginPaths == expectedCompilerHostOriginPaths) {
        "Compiler HTTPS origin allowlist mismatch: " +
            "expected=$expectedCompilerHostOriginPaths, actual=$declaredOriginPaths"
    }
    check(requiredCspDirectives.all(originSource::contains)) {
        "Compiler asset response headers are missing a required CSP directive."
    }
    check(originSource.contains("frame-ancestors 'none'")) {
        "Compiler asset response CSP must deny frame ancestors."
    }

    val forbiddenAndroidBridgeApis = setOf(
        "addJavascriptInterface",
        "evaluateJavascript",
    )
    val forbiddenBridgeUses = productionSourceFiles
        .filter(File::isFile)
        .flatMap { file ->
            val source = file.readText(Charsets.UTF_8)
            forbiddenAndroidBridgeApis
                .filter(source::contains)
                .map { api -> "${file.name}: $api" }
        }
    check(forbiddenBridgeUses.isEmpty()) {
        "Compiler host must use origin-bound WebMessagePort only: $forbiddenBridgeUses"
    }

    check(webViewHostSourceFile.isFile) {
        "Compiler WebView host source does not exist: $webViewHostSourceFile"
    }
    val webViewHostSource = webViewHostSourceFile.readText(Charsets.UTF_8)
    val requiredWebViewRestrictions = setOf(
        "allowFileAccess = false",
        "allowContentAccess = false",
        "allowFileAccessFromFileURLs = false",
        "allowUniversalAccessFromFileURLs = false",
        "blockNetworkLoads = true",
        "databaseEnabled = false",
        "domStorageEnabled = false",
        "javaScriptCanOpenWindowsAutomatically = false",
        "mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW",
        "setSupportMultipleWindows(false)",
        "setAcceptCookie(false)",
        "setAcceptThirdPartyCookies(createdWebView, false)",
    )
    check(requiredWebViewRestrictions.all(webViewHostSource::contains)) {
        "Compiler WebView source is missing a required security restriction."
    }

    check(manifestFile.isFile) {
        "Application manifest does not exist: $manifestFile"
    }
    val manifestText = manifestFile.readText(Charsets.UTF_8)
    check("android.permission.INTERNET" !in manifestText) {
        "Compiler plugin manifest must not request INTERNET permission."
    }
    check("""android:usesCleartextTraffic="false"""" in manifestText) {
        "Compiler plugin manifest must explicitly disable cleartext traffic."
    }
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
private fun verifyCompilerHostInApk(
    apkFile: File,
    sourceAssetDirectory: File,
    mergedManifestFile: File,
    typeScriptVersion: String,
) {
    val hostAssetPrefix = "assets/typescript-compiler-r1/"
    val expectedHostEntries = expectedCompilerHostAssetNames
        .associateWith { name -> sourceAssetDirectory.resolve(name).readBytes() }
        .mapKeys { (name, _) -> "$hostAssetPrefix$name" }
    val expectedCompilerEntry = "assets/typescript/$typeScriptVersion/lib/typescript.js"
    val expectedDistributionManifestEntry =
        "assets/typescript/$typeScriptVersion/distribution.json"

    ZipFile(apkFile).use { apk ->
        val entries = apk.entries().asSequence().filterNot { it.isDirectory }.toList()
        val hostEntries = entries
            .filter { it.name.startsWith(hostAssetPrefix) }
            .map { it.name }
            .toSet()
        check(hostEntries == expectedHostEntries.keys) {
            "APK compiler host assets must match the exact allowlist: " +
                "expected=${expectedHostEntries.keys}, actual=$hostEntries"
        }
        expectedHostEntries.forEach { (entryName, expectedBytes) ->
            val entry = requireNotNull(apk.getEntry(entryName)) {
                "APK is missing compiler host asset $entryName."
            }
            val actualBytes = apk.getInputStream(entry).use { it.readBytes() }
            check(MessageDigest.isEqual(expectedBytes, actualBytes)) {
                "APK compiler host asset differs from its verified source: $entryName"
            }
        }

        val compilerEntries = entries.filter {
            it.name.substringAfterLast('/') == "typescript.js"
        }
        check(compilerEntries.map { it.name } == listOf(expectedCompilerEntry)) {
            "APK must contain exactly one TypeScript compiler at $expectedCompilerEntry, found " +
                compilerEntries.map { it.name }
        }
        val distributionManifestEntry = requireNotNull(
            apk.getEntry(expectedDistributionManifestEntry),
        ) {
            "APK is missing the versioned TypeScript distribution manifest."
        }
        val distributionManifest = apk.getInputStream(distributionManifestEntry).use {
            JsonSlurper().parse(it) as Map<String, Any?>
        }
        val packageInfo = distributionManifest["package"] as? Map<String, Any?>
            ?: error("Packaged TypeScript distribution manifest has no package metadata.")
        check(packageInfo["name"] == "typescript")
        check(packageInfo["version"] == typeScriptVersion) {
            "Packaged TypeScript version ${packageInfo["version"]} does not match " +
                typeScriptVersion
        }
        val files = distributionManifest["files"] as? List<Map<String, Any?>>
            ?: error("Packaged TypeScript distribution manifest has no file list.")
        val compilerMetadata = files.singleOrNull { it["path"] == "lib/typescript.js" }
            ?: error("Packaged distribution must declare exactly one lib/typescript.js.")
        val compilerEntry = compilerEntries.single()
        val compilerBytes = apk.getInputStream(compilerEntry).use { it.readBytes() }
        check((compilerMetadata["size"] as? Number)?.toLong() == compilerBytes.size.toLong()) {
            "Packaged TypeScript compiler size does not match distribution.json."
        }
        check(compilerMetadata["sha256"] == compilerBytes.sha256String()) {
            "Packaged TypeScript compiler SHA-256 does not match distribution.json."
        }
    }

    check(mergedManifestFile.isFile) {
        "Merged manifest does not exist: $mergedManifestFile"
    }
    val mergedManifestText = mergedManifestFile.readText(Charsets.UTF_8)
    check("android.permission.INTERNET" !in mergedManifestText) {
        "Merged compiler plugin manifest must not request INTERNET permission."
    }
    check("""android:usesCleartextTraffic="false"""" in mergedManifestText) {
        "Merged compiler plugin manifest must disable cleartext traffic."
    }
    check("""android:process=":typescript_compiler"""" in mergedManifestText) {
        "Merged compiler service must remain in :typescript_compiler."
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

val compilerHostProductionSources = fileTree("src/main/java") {
    include("**/*.java")
    include("**/*.kt")
}
val compilerAssetOriginSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "CompilerAssetOrigin.kt",
)
val compilerWebViewHostSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "TypeScriptWebViewCompilerHost.kt",
)
val compilerHostManifest = layout.projectDirectory.file("src/main/AndroidManifest.xml")
val verifyCompilerHostSourcePolicy =
    tasks.register("verifyCompilerHostSourcePolicy") {
        group = "verification"
        description =
            "Verifies the R1 compiler host asset allowlist and static WebView security policy."
        inputs.dir(compilerHostSourceAssetDirectory)
        inputs.file(compilerAssetOriginSource)
        inputs.file(compilerWebViewHostSource)
        inputs.files(compilerHostProductionSources)
        inputs.file(compilerHostManifest)
        doLast {
            verifyCompilerHostSourcePolicy(
                hostAssetDirectory = compilerHostSourceAssetDirectory.asFile,
                originSourceFile = compilerAssetOriginSource.asFile,
                webViewHostSourceFile = compilerWebViewHostSource.asFile,
                productionSourceFiles = compilerHostProductionSources.files,
                manifestFile = compilerHostManifest.asFile,
            )
        }
    }

fun registerCompilerHostApkVerification(
    taskName: String,
    assembleTaskName: String,
    variantName: String,
) = tasks.register(taskName) {
    group = "verification"
    description =
        "Verifies the R1 compiler host security boundary in the $variantName APK."
    dependsOn(assembleTaskName)
    dependsOn(verifyCompilerHostSourcePolicy)
    dependsOn(verifyTypeScriptDistribution)
    val apkDirectory = layout.buildDirectory.dir("outputs/apk/$variantName")
    val mergedManifest = layout.buildDirectory.file(
        "intermediates/merged_manifest/$variantName/" +
            "process${variantName.replaceFirstChar(Char::uppercase)}MainManifest/" +
            "AndroidManifest.xml",
    )
    inputs.dir(apkDirectory)
    inputs.dir(compilerHostSourceAssetDirectory)
    inputs.file(mergedManifest)
    doLast {
        verifyCompilerHostInApk(
            apkFile = resolveSingleApkOutput(apkDirectory.get().asFile),
            sourceAssetDirectory = compilerHostSourceAssetDirectory.asFile,
            mergedManifestFile = mergedManifest.get().asFile,
            typeScriptVersion = typeScriptVersion,
        )
    }
}

val verifyDebugCompilerHostApk =
    registerCompilerHostApkVerification(
        taskName = "verifyDebugCompilerHostApk",
        assembleTaskName = "assembleDebug",
        variantName = "debug",
    )
val verifyReleaseCompilerHostApk =
    registerCompilerHostApkVerification(
        taskName = "verifyReleaseCompilerHostApk",
        assembleTaskName = "assembleRelease",
        variantName = "release",
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
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn(verifyDebugCompilerHostApk)
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
