import com.android.build.api.variant.FilterConfiguration
import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import org.autojs.build.DownloadTypeScriptDistributionTarball
import org.autojs.build.GenerateTypeScriptDistribution
import org.autojs.build.VerifyR1OptionalPluginMatrix
import org.autojs.build.VerifyTypeScriptDistribution
import org.gradle.api.artifacts.FileCollectionDependency
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
val r1RhinoProbeLockFile =
    rootProject.layout.projectDirectory.file("rhino-probe.lock.json")
@Suppress("UNCHECKED_CAST")
val r1RhinoProbeLock =
    JsonSlurper().parse(r1RhinoProbeLockFile.asFile) as Map<String, Any?>
@Suppress("UNCHECKED_CAST")
val r1RhinoProbeArtifact =
    r1RhinoProbeLock["artifact"] as? Map<String, Any?>
        ?: error("Rhino probe lock does not contain artifact metadata.")
@Suppress("UNCHECKED_CAST")
val r1RhinoProbeSource =
    r1RhinoProbeLock["rhinoSource"] as? Map<String, Any?>
        ?: error("Rhino probe lock does not contain Rhino source metadata.")
@Suppress("UNCHECKED_CAST")
val r1RhinoProbeConsumer =
    r1RhinoProbeLock["autoJs6Consumer"] as? Map<String, Any?>
        ?: error("Rhino probe lock does not contain AutoJs6 consumer metadata.")
@Suppress("UNCHECKED_CAST")
val r1RhinoProbePolicy =
    r1RhinoProbeLock["policy"] as? Map<String, Any?>
        ?: error("Rhino probe lock does not contain policy metadata.")
val r1RhinoProbeArtifactName =
    r1RhinoProbeArtifact["fileName"] as? String
        ?: error("Rhino probe lock does not declare artifact.fileName.")
val r1RhinoProbeArtifactSha256 =
    r1RhinoProbeArtifact["sha256"] as? String
        ?: error("Rhino probe lock does not declare artifact.sha256.")
val r1RhinoProbeSourceCommit =
    r1RhinoProbeSource["commit"] as? String
        ?: error("Rhino probe lock does not declare rhinoSource.commit.")
val r1RhinoProbeConsumerCommit =
    r1RhinoProbeConsumer["commit"] as? String
        ?: error("Rhino probe lock does not declare autoJs6Consumer.commit.")
val r1RhinoProbeJarPath = providers.gradleProperty("r1RhinoProbeJar")
    .orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
val r1RhinoProbeJarFile = r1RhinoProbeJarPath?.let(rootProject::file)

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
            buildConfigField(
                "String",
                "R1_RHINO_PROBE_ARTIFACT_NAME",
                "\"$r1RhinoProbeArtifactName\"",
            )
            buildConfigField(
                "String",
                "R1_RHINO_PROBE_ARTIFACT_SHA256",
                "\"$r1RhinoProbeArtifactSha256\"",
            )
            buildConfigField(
                "String",
                "R1_RHINO_PROBE_SOURCE_COMMIT",
                "\"$r1RhinoProbeSourceCommit\"",
            )
            buildConfigField(
                "String",
                "R1_RHINO_PROBE_CONSUMER_COMMIT",
                "\"$r1RhinoProbeConsumerCommit\"",
            )
            buildConfigField(
                "boolean",
                "R1_RHINO_PROBE_PACKAGED",
                (r1RhinoProbeJarFile != null).toString(),
            )
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
        getByName("androidTest") {
            assets.directories.add(
                file("src/test/resources").absolutePath,
            )
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
    r1RhinoProbeJarFile?.let { probeJar ->
        add("debugImplementation", files(probeJar))
    }
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.runner)
    testImplementation(libs.junit)
}

val verifyR1RhinoProbeJar =
    tasks.register("verifyR1RhinoProbeJar") {
        group = "verification"
        description =
            "Verifies the explicitly selected AutoJs6 Rhino artifact against the R1 lock."
        inputs.file(r1RhinoProbeLockFile)
        inputs.property("r1RhinoProbeJar", r1RhinoProbeJarPath ?: "<missing>")
        r1RhinoProbeJarFile?.let(inputs::file)
        doLast {
            check(r1RhinoProbeLock["schemaVersion"] == 1) {
                "Unsupported Rhino probe lock schema: ${r1RhinoProbeLock["schemaVersion"]}"
            }
            check(r1RhinoProbeArtifactName == "org-mozilla-rhino-2_0_0-SNAPSHOT.jar")
            check(
                r1RhinoProbeArtifactSha256 ==
                    "712a73d62edc56eb36a909a229eca87ac433afa7aaceb6d60e3acbbc75a9533a",
            )
            check(
                r1RhinoProbeSourceCommit ==
                    "e0dfa6f316bfb5ed99fff3d0cf33310e18458d3d",
            )
            check(
                r1RhinoProbeConsumerCommit ==
                    "f78c281985618205d2dc3dd056cf9bf042cc5996",
            )
            check(r1RhinoProbePolicy["packaging"] == "debug-opt-in-only")
            check(r1RhinoProbePolicy["expectedOutcome"] == "unsupported")
            check(r1RhinoProbePolicy["fallbackEligible"] == false)

            val selectedJar = checkNotNull(r1RhinoProbeJarFile) {
                "R1 Rhino probe is opt-in. Supply the exact AutoJs6 artifact with " +
                    "-Pr1RhinoProbeJar=<path-to-$r1RhinoProbeArtifactName>."
            }
            check(selectedJar.isFile) {
                "R1 Rhino probe JAR does not exist: $selectedJar"
            }
            check(selectedJar.name == r1RhinoProbeArtifactName) {
                "R1 Rhino probe requires $r1RhinoProbeArtifactName, found ${selectedJar.name}."
            }
            val actualSha256 = selectedJar.readBytes().sha256String()
            check(actualSha256 == r1RhinoProbeArtifactSha256) {
                "R1 Rhino probe JAR SHA-256 mismatch: expected " +
                    "$r1RhinoProbeArtifactSha256, actual $actualSha256."
            }
        }
    }

if (r1RhinoProbeJarFile != null) {
    tasks.matching { task -> task.name == "preDebugBuild" }.configureEach {
        dependsOn(verifyR1RhinoProbeJar)
    }
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

private fun ByteArray.containsByteSequence(sequence: ByteArray): Boolean {
    if (sequence.isEmpty()) return true
    if (sequence.size > size) return false
    outer@ for (start in 0..size - sequence.size) {
        for (offset in sequence.indices) {
            if (this[start + offset] != sequence[offset]) {
                continue@outer
            }
        }
        return true
    }
    return false
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
    debugLifecycleControlExpected: Boolean,
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
        val dexBytes = entries
            .filter { entry ->
                entry.name.matches(Regex("""classes(?:\d+)?\.dex"""))
            }
            .map { entry ->
                apk.getInputStream(entry).use { input ->
                    input.readBytes()
                }
            }
            .toList()
        val debugOnlyMarkers = linkedMapOf(
            "lifecycle control" to "__autojs6_r1_debug_hold_until_service_destroyed",
            "request correlation control" to
                "__autojs6_r1_debug_inject_wrong_request_id_failure",
            "accepted transport control" to "__autojs6_r1_debug_transport_probe",
            "accepted transport executor" to "typescript-r1-transport-probe",
            "accepted transport coordinator" to "CompilationTransportProbeCoordinator",
        )
        debugOnlyMarkers.forEach { (label, markerText) ->
            val marker = markerText.toByteArray(Charsets.UTF_8)
            val present = dexBytes.any { dex ->
                dex.containsByteSequence(marker)
            }
            check(present == debugLifecycleControlExpected) {
                "Debug-only $label marker presence mismatch in ${apkFile.name}: " +
                    "expected=$debugLifecycleControlExpected, actual=$present"
            }
        }
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

private fun verifyRhinoProbeAbsentFromRelease(
    apkFile: File,
    mergedManifestFile: File,
    artifactName: String,
) {
    verifyRhinoProbeAbsentFromReleaseApk(apkFile, artifactName)
    check(mergedManifestFile.isFile) {
        "Merged release manifest does not exist: $mergedManifestFile"
    }
    val manifestText = mergedManifestFile.readText(Charsets.UTF_8)
    check("R1RhinoProbeService" !in manifestText)
    check("typescript_rhino_probe" !in manifestText) {
        "Release manifest must not contain the debug-only Rhino probe process."
    }
}

private fun verifyRhinoProbeAbsentFromReleaseApk(
    apkFile: File,
    artifactName: String,
) {
    val forbiddenEntries = arrayOf(artifactName, "org/mozilla/javascript/")
    val forbiddenMarkers = arrayOf(
        "R1RhinoProbeService".toByteArray(Charsets.UTF_8),
        "org/mozilla/javascript/Context".toByteArray(Charsets.UTF_8),
        artifactName.toByteArray(Charsets.UTF_8),
    )
    val apk = ZipFile(apkFile)
    try {
        val entries = apk.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            for (fragment in forbiddenEntries) {
                check(!entry.name.contains(fragment)) {
                    "Release APK must not package Rhino entry ${entry.name}."
                }
            }
            if (!entry.name.matches(Regex("""classes(?:\d+)?\.dex"""))) continue
            val input = apk.getInputStream(entry)
            val dexBytes = try {
                input.readBytes()
            } finally {
                input.close()
            }
            for (marker in forbiddenMarkers) {
                check(!dexBytes.containsByteSequence(marker)) {
                    "Release APK contains an R1 Rhino probe service, process, or class marker."
                }
            }
        }
    } finally {
        apk.close()
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
val compilerApiAidl = rootProject.layout.projectDirectory.file(
    "plugin-api/typescript-api/src/main/aidl/org/autojs/plugin/typescript/api/" +
        "ITypeScriptCompilerPlugin.aidl",
)
val compilerCallbackApiAidl = rootProject.layout.projectDirectory.file(
    "plugin-api/typescript-api/src/main/aidl/org/autojs/plugin/typescript/api/" +
        "ITypeScriptCompilationCallback.aidl",
)
val compilerServiceSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "TypeScriptCompilerPluginService.kt",
)

fun parseAidlTransactionNames(
    aidlFile: File,
    interfaceName: String,
): List<String> {
    val source = aidlFile.readText(Charsets.UTF_8)
        .replace(Regex("""(?s)/\*.*?\*/"""), "")
        .replace(Regex("""(?m)//.*$"""), "")
    val body = Regex(
        """(?s)\binterface\s+${Regex.escape(interfaceName)}\s*\{(.*?)\}""",
    ).find(source)?.groupValues?.get(1)
        ?: error("AIDL interface $interfaceName was not found in $aidlFile.")
    return Regex(
        """(?m)^\s*[A-Za-z_][A-Za-z0-9_.<>\[\]]*\s+""" +
            """([A-Za-z_][A-Za-z0-9_]*)\s*\(""",
    ).findAll(body).map { match -> match.groupValues[1] }.toList()
}

val verifyCompilerBinderTransactionOrder =
    tasks.register("verifyCompilerBinderTransactionOrder") {
        group = "verification"
        description =
            "Verifies the append-only compiler AIDL order used by raw Parcel interception."
        inputs.file(compilerApiAidl)
        inputs.file(compilerCallbackApiAidl)
        inputs.file(compilerServiceSource)
        doLast {
            val compilerTransactionPrefix = listOf(
                "getContractVersion",
                "getContractHash",
                "getCompilerInfo",
                "prewarm",
                "startCompilation",
                "cancelCompilation",
            )
            val callbackTransactionPrefix = listOf(
                "onProgress",
                "onCompleted",
            )
            val compilerTransactions = parseAidlTransactionNames(
                compilerApiAidl.asFile,
                "ITypeScriptCompilerPlugin",
            )
            val callbackTransactions = parseAidlTransactionNames(
                compilerCallbackApiAidl.asFile,
                "ITypeScriptCompilationCallback",
            )
            check(
                compilerTransactions.take(compilerTransactionPrefix.size) ==
                    compilerTransactionPrefix,
            ) {
                "Compiler AIDL transactions must keep the append-only prefix: " +
                    "expected=$compilerTransactionPrefix, actual=$compilerTransactions"
            }
            check(
                callbackTransactions.take(callbackTransactionPrefix.size) ==
                    callbackTransactionPrefix,
            ) {
                "Compiler callback AIDL transactions must keep the append-only prefix: " +
                    "expected=$callbackTransactionPrefix, actual=$callbackTransactions"
            }

            val serviceSource = compilerServiceSource.asFile.readText(Charsets.UTF_8)
            val rawOffsets = Regex(
                """(?m)^\s*const\s+val\s+(TRANSACTION_[A-Z_]+)\s*=\s*""" +
                    """IBinder\.FIRST_CALL_TRANSACTION\s*\+\s*(\d+)\s*$""",
            ).findAll(serviceSource).associate { match ->
                match.groupValues[1] to match.groupValues[2].toInt()
            }
            val interceptedTransactions = mapOf(
                "TRANSACTION_PREWARM" to "prewarm",
                "TRANSACTION_START_COMPILATION" to "startCompilation",
                "TRANSACTION_CANCEL_COMPILATION" to "cancelCompilation",
            )
            interceptedTransactions.forEach { (constantName, methodName) ->
                val expectedOffset = compilerTransactions.indexOf(methodName)
                check(expectedOffset >= 0) {
                    "Raw Parcel interception refers to missing AIDL method $methodName."
                }
                check(rawOffsets[constantName] == expectedOffset) {
                    "$constantName must use FIRST_CALL_TRANSACTION + $expectedOffset, " +
                        "found ${rawOffsets[constantName]}."
                }
            }
        }
    }

val verifyCompilerHostSourcePolicy =
    tasks.register("verifyCompilerHostSourcePolicy") {
        group = "verification"
        description =
            "Verifies the R1 compiler host asset allowlist and static WebView security policy."
        dependsOn(verifyCompilerBinderTransactionOrder)
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
            debugLifecycleControlExpected = variantName == "debug",
        )
        if (variantName == "release") {
            verifyRhinoProbeAbsentFromRelease(
                apkFile = resolveSingleApkOutput(apkDirectory.get().asFile),
                mergedManifestFile = mergedManifest.get().asFile,
                artifactName = r1RhinoProbeArtifactName,
            )
        }
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
val verifyR1RhinoProbe =
    tasks.register("verifyR1RhinoProbe") {
        group = "verification"
        description =
            "Builds and verifies the opt-in debug Rhino feasibility probe without running devices."
        dependsOn(verifyR1RhinoProbeJar)
        dependsOn("assembleDebug", "assembleDebugAndroidTest")
        dependsOn(verifyDebugCompilerHostApk)
        dependsOn(verifyReleaseCompilerHostApk)
        val debugApkDirectory = layout.buildDirectory.dir("outputs/apk/debug")
        val debugMergedManifest = layout.buildDirectory.file(
            "intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml",
        )
        inputs.dir(debugApkDirectory)
        inputs.file(debugMergedManifest)
        doLast {
            val apkFile = resolveSingleApkOutput(debugApkDirectory.get().asFile)
            val requiredDexMarkers = listOf(
                "R1RhinoProbeService",
                "org/mozilla/javascript/Context",
            ).map { marker -> marker.toByteArray(Charsets.UTF_8) }
            ZipFile(apkFile).use { apk ->
                val dexBytes = apk.entries().asSequence()
                    .filter { entry ->
                        !entry.isDirectory &&
                            entry.name.matches(Regex("""classes(?:\d+)?\.dex"""))
                    }
                    .map { entry ->
                        apk.getInputStream(entry).use { input -> input.readBytes() }
                    }
                    .toList()
                requiredDexMarkers.forEach { marker ->
                    check(dexBytes.any { dex -> dex.containsByteSequence(marker) }) {
                        "Debug APK is missing a required Rhino probe marker: " +
                            marker.toString(Charsets.UTF_8)
                    }
                }
            }
            val manifestText = debugMergedManifest.get().asFile.readText(Charsets.UTF_8)
            check("R1RhinoProbeService" in manifestText) {
                "Debug manifest is missing R1RhinoProbeService."
            }
            check("""android:exported="false"""" in manifestText)
            check("""android:process=":typescript_rhino_probe"""" in manifestText)
        }
    }

val optionalPluginIndependenceSources = files(
    fileTree(layout.projectDirectory.dir("src")) {
        // Debug-only probes bind the compiler deliberately; release APK exclusion is verified
        // separately, while this gate is scoped to production discovery/dependencies.
        exclude("debug/**")
        exclude("test/**")
        exclude("androidTest/**")
    },
    fileTree(rootProject.layout.projectDirectory.dir("plugin-api")) {
        include("**/src/**")
        exclude("**/src/test/**")
        exclude("**/src/androidTest/**")
        exclude("**/build/**")
    },
)
val optionalPluginIndependenceBuildInputs = files(
    layout.projectDirectory.file("build.gradle.kts"),
    rootProject.layout.projectDirectory.file("build.gradle.kts"),
    rootProject.layout.projectDirectory.file("settings.gradle.kts"),
    rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"),
    fileTree(rootProject.layout.projectDirectory.dir("build-logic")) {
        include("**/*.gradle.kts")
        include("**/*.kt")
        include("**/*.toml")
        exclude("**/build/**")
    },
    fileTree(rootProject.layout.projectDirectory.dir("plugin-api")) {
        include("**/build.gradle.kts")
        exclude("**/build/**")
    },
)
val optionalPluginManifest =
    layout.projectDirectory.file("src/main/AndroidManifest.xml")
val optionalPluginDependencyProjects = rootProject.subprojects.filter { dependencyProject ->
    dependencyProject == project ||
        dependencyProject.path.startsWith(":plugin-api:")
}

tasks {
    withType(JavaCompile::class.java) {
        options.encoding = "UTF-8"
    }

    register("verifyOptionalPluginIndependence") {
        group = "verification"
        description =
            "Ensures production compiler code cannot discover or depend on Node/Ace plugins."
        inputs.files(optionalPluginIndependenceSources)
        inputs.files(optionalPluginIndependenceBuildInputs)
        inputs.file(optionalPluginManifest)
        doLast {
            fun canonicalSignature(value: String): String =
                value.lowercase().filter { character ->
                    character in 'a'..'z' || character in '0'..'9'
                }

            fun withoutSourceComments(
                value: String,
                nestedBlockComments: Boolean = false,
            ): String = buildString(value.length) {
                var index = 0
                var blockCommentDepth = 0
                var quote: Char? = null
                var tripleQuoted = false
                var lineComment = false
                var xmlComment = false

                while (index < value.length) {
                    if (lineComment) {
                        val character = value[index]
                        if (character == '\r' || character == '\n') {
                            lineComment = false
                            append(character)
                        }
                        index++
                        continue
                    }
                    if (xmlComment) {
                        when {
                            value.startsWith("-->", index) -> {
                                xmlComment = false
                                index += 3
                            }
                            value[index] == '\r' || value[index] == '\n' -> {
                                append(value[index])
                                index++
                            }
                            else -> index++
                        }
                        continue
                    }
                    if (blockCommentDepth > 0) {
                        when {
                            nestedBlockComments && value.startsWith("/*", index) -> {
                                blockCommentDepth++
                                index += 2
                            }
                            value.startsWith("*/", index) -> {
                                blockCommentDepth--
                                index += 2
                            }
                            value[index] == '\r' || value[index] == '\n' -> {
                                append(value[index])
                                index++
                            }
                            else -> index++
                        }
                        continue
                    }
                    val quoteCharacter = quote
                    if (quoteCharacter != null) {
                        when {
                            tripleQuoted && value.startsWith("\"\"\"", index) -> {
                                append("\"\"\"")
                                index += 3
                                tripleQuoted = false
                                quote = null
                            }
                            !tripleQuoted && value[index] == '\\' -> {
                                append(value[index])
                                index++
                                if (index < value.length) {
                                    append(value[index])
                                    index++
                                }
                            }
                            !tripleQuoted && value[index] == quoteCharacter -> {
                                append(value[index])
                                index++
                                quote = null
                            }
                            else -> {
                                append(value[index])
                                index++
                            }
                        }
                        continue
                    }

                    when {
                        value.startsWith("<!--", index) -> {
                            xmlComment = true
                            index += 4
                        }
                        value.startsWith("//", index) -> {
                            lineComment = true
                            index += 2
                        }
                        value.startsWith("/*", index) -> {
                            blockCommentDepth = 1
                            index += 2
                        }
                        value.startsWith("\"\"\"", index) -> {
                            append("\"\"\"")
                            index += 3
                            quote = '"'
                            tripleQuoted = true
                        }
                        value[index] == '"' ||
                            value[index] == '\'' ||
                            value[index] == '`' -> {
                            quote = value[index]
                            append(value[index])
                            index++
                        }
                        else -> {
                            append(value[index])
                            index++
                        }
                    }
                }
            }

            val forbiddenPluginSignatures = linkedMapOf(
                "Node Runtime API artifact" to "nodejs-api",
                "Node Runtime API namespace" to "org.autojs.plugin.nodejs.api",
                "Node Runtime application package" to
                    "io.github.supermonster003.autojs6.plugin.nodejs",
                "Node Runtime service action" to "org.autojs.plugin.nodejs.RUNTIME",
                "Node Runtime native library" to "libnode.so",
                "Ace Editor artifact" to "autojs6-plugin-ace-editor",
                "Ace Editor API artifact" to "editor-api",
                "Ace Editor API namespace" to "org.autojs.plugin.editor.api",
                "Ace Editor application package" to
                    "io.github.supermonster003.autojs6.plugin.ace.editor",
                "Ace Editor service action" to "org.autojs.plugin.EDITOR",
                "Ace Editor wake action" to "org.autojs.plugin.action.WAKE",
                "Ace Editor wake metadata" to "org.autojs.plugin.WAKE_ACTIVITY",
            )
            val forbiddenDiscoverySignatures = linkedMapOf(
                "PackageManager access" to "android.content.pm.PackageManager",
                "PackageManager accessor" to "getPackageManager",
                "installed package discovery" to "getInstalledPackages",
                "installed application discovery" to "getInstalledApplications",
                "package info discovery" to "getPackageInfo",
                "application info discovery" to "getApplicationInfo",
                "service intent query" to "queryIntentServices",
                "activity intent query" to "queryIntentActivities",
                "provider intent query" to "queryIntentContentProviders",
                "receiver intent query" to "queryBroadcastReceivers",
                "service resolution" to "resolveService",
                "activity resolution" to "resolveActivity",
                "provider resolution" to "resolveContentProvider",
                "cross-package context creation" to "createPackageContext",
                "explicit package targeting" to "setPackage(",
                "explicit component targeting" to "setComponent(",
                "explicit component construction" to "ComponentName(",
                "service binding" to "bindService(",
                "isolated service binding" to "bindIsolatedService(",
                "service start" to "startService(",
                "foreground service start" to "startForegroundService(",
            )
            val forbiddenSourceSignatures =
                forbiddenPluginSignatures + forbiddenDiscoverySignatures
            val canonicalForbiddenSourceSignatures =
                forbiddenSourceSignatures.mapValues { (_, signature) ->
                    canonicalSignature(signature)
                }

            check(
                canonicalSignature(
                    """"org.autojs.plugin." + "nodejs"""",
                ).contains(canonicalSignature("org.autojs.plugin.nodejs")),
            ) {
                "Optional plugin signature normalization no longer catches literal concatenation."
            }
            check(
                !canonicalSignature(
                    withoutSourceComments(
                        "// android.content.pm.PackageManager is forbidden in production",
                    ),
                ).contains(canonicalSignature("android.content.pm.PackageManager")),
            ) {
                "Optional plugin independence comments are no longer excluded."
            }
            check(
                canonicalSignature(
                    withoutSourceComments(
                        """
                        val pluginId =
                            "//io.github.supermonster003.autojs6.plugin.nodejs"
                                .removePrefix("//")
                        """.trimIndent(),
                        nestedBlockComments = true,
                    ),
                ).contains(
                    canonicalSignature(
                        "io.github.supermonster003.autojs6.plugin.nodejs",
                    ),
                ),
            ) {
                "Optional plugin signatures inside strings containing // are no longer scanned."
            }
            check(
                canonicalSignature(
                    withoutSourceComments(
                        """val pluginId = "/*org.autojs.plugin.nodejs.api*/"""",
                        nestedBlockComments = true,
                    ),
                ).contains(canonicalSignature("org.autojs.plugin.nodejs.api")),
            ) {
                "Optional plugin signatures inside strings containing /* */ are no longer scanned."
            }

            val sourceViolations = optionalPluginIndependenceSources.files
                .filter(File::isFile)
                .flatMap { file ->
                    val source = file.readText(Charsets.UTF_8)
                    val canonicalSource =
                        canonicalSignature(
                            withoutSourceComments(
                                source,
                                nestedBlockComments =
                                    file.extension.equals("kt", ignoreCase = true) ||
                                        file.extension.equals("kts", ignoreCase = true),
                            ),
                        )
                    canonicalForbiddenSourceSignatures
                        .filterValues { signature ->
                            canonicalSource.contains(signature)
                        }
                        .keys
                        .map { label ->
                            "${file.relativeTo(rootProject.projectDir).invariantSeparatorsPath}: " +
                                label
                        }
                }

            val manifestPatterns = listOf(
                Regex("""<\s*queries(?:\s|>)""", RegexOption.IGNORE_CASE) to
                    "manifest package-visibility queries",
                Regex("""<\s*uses-library(?:\s|>)""", RegexOption.IGNORE_CASE) to
                    "manifest optional shared library",
            )
            val manifestFiles = optionalPluginIndependenceSources.files.filter { file ->
                file.isFile && file.name == "AndroidManifest.xml"
            }
            check(optionalPluginManifest.asFile in manifestFiles) {
                "The compiler application manifest is not covered by the independence gate."
            }
            val manifestViolations = manifestFiles.flatMap { manifestFile ->
                val manifestText = manifestFile.readText(Charsets.UTF_8)
                manifestPatterns.mapNotNull { (pattern, label) ->
                    label.takeIf { pattern.containsMatchIn(manifestText) }
                }.map { label ->
                    "${manifestFile.relativeTo(rootProject.projectDir)}: $label"
                }
            }

            val dependencyEvidence =
                optionalPluginDependencyProjects.flatMap { dependencyProject ->
                    dependencyProject.configurations
                        .filterNot { configuration ->
                            configuration.name.contains("test", ignoreCase = true)
                        }
                        .flatMap { configuration ->
                            configuration.dependencies.flatMap { dependency ->
                                buildList {
                                    add(
                                        listOfNotNull(
                                            dependency.group,
                                            dependency.name,
                                            dependency.version,
                                        ).joinToString(":"),
                                    )
                                    if (dependency is FileCollectionDependency) {
                                        addAll(dependency.files.files.map(File::getPath))
                                    }
                                }.map { notation ->
                                    "${dependencyProject.path}:" +
                                        "${configuration.name}:$notation"
                                }
                            }
                        }
                }.distinct()
            val canonicalForbiddenDependencies =
                forbiddenPluginSignatures.mapValues { (_, signature) ->
                    canonicalSignature(signature)
                }
            val dependencyViolations = dependencyEvidence.flatMap { evidence ->
                val canonicalEvidence = canonicalSignature(evidence)
                canonicalForbiddenDependencies
                    .filterValues(canonicalEvidence::contains)
                    .keys
                    .map { label -> "declared dependency: $evidence ($label)" }
            }

            val violations = (
                sourceViolations +
                    manifestViolations +
                    dependencyViolations
                ).distinct().sorted()
            check(violations.isEmpty()) {
                "Optional plugin independence check failed:\n" +
                    violations.joinToString("\n")
            }
        }
    }

    register("verifyNodeRuntimeIndependence") {
        group = "verification"
        description =
            "Compatibility alias for the Node/Ace optional plugin independence gate."
        dependsOn("verifyOptionalPluginIndependence")
    }

    register<VerifyR1OptionalPluginMatrix>("verifyR1OptionalPluginMatrix") {
        dependsOn("assembleDebug", "assembleDebugAndroidTest")
        adbExecutable.set(androidComponents.sdkComponents.adb)
        aapt2Executable.set(
            androidComponents.sdkComponents.aapt2.flatMap { aapt2 ->
                aapt2.executable
            },
        )
        targetApkDirectory.set(layout.buildDirectory.dir("outputs/apk/debug"))
        testApkDirectory.set(layout.buildDirectory.dir("outputs/apk/androidTest/debug"))
        nodeApk.set(
            layout.file(
                providers.gradleProperty("r1MatrixNodeApk").map { path ->
                    rootProject.file(path)
                },
            ),
        )
        aceApk.set(
            layout.file(
                providers.gradleProperty("r1MatrixAceApk").map { path ->
                    rootProject.file(path)
                },
            ),
        )
        deviceSerial.set(providers.gradleProperty("r1MatrixSerial"))
        mutationConfirmation.set(providers.gradleProperty("r1MatrixConfirm"))
        reportFile.set(
            layout.buildDirectory.file(
                "reports/r1/optional-plugin-matrix.txt",
            ),
        )
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
        dependsOn("verifyOptionalPluginIndependence")
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
