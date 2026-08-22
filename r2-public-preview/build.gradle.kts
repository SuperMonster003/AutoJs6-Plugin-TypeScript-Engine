import java.util.zip.ZipFile

plugins {
    id("org.autojs.build.signs")
    id("org.autojs.build.versions")
    id("com.android.application")
}

android {
    namespace = "io.github.supermonster003.autojs6.plugin.typescript.publicpreview"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId =
            "io.github.supermonster003.autojs6.plugin.typescript.publicpreview"
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = 1
        versionName = "1"
    }

    signingConfigs {
        if (signs.isValid) {
            create("release") {
                storeFile = signs.properties["storeFile"]?.let {
                    project(":app").file(it as String)
                }
                keyPassword = signs.properties["keyPassword"] as String
                keyAlias = signs.properties["keyAlias"] as String
                storePassword = signs.properties["storePassword"] as String
            }
        }
    }

    buildTypes {
        val sharedSigning = takeIf { signs.isValid }?.let {
            signingConfigs.getByName("release")
        }
        debug {
            sharedSigning?.let { signingConfig = it }
        }
        release {
            sharedSigning?.let { signingConfig = it }
        }
    }

    buildFeatures {
        buildConfig = false
    }

    lint {
        abortOnError = true
    }
}

dependencies {
    implementation(project(":plugin-api:typescript-api"))
}

private val pluginApplicationId =
    "io.github.supermonster003.autojs6.plugin.typescript"
private val fixtureApplicationId =
    "$pluginApplicationId.publicpreview"
private val pluginSignaturePermission =
    "org.autojs.permission.PLUGIN"
private val compilerServiceName =
    "$pluginApplicationId.TypeScriptCompilerPluginService"
private val compilerServiceAction =
    "org.autojs.plugin.typescript.COMPILER"
private val compilerServiceCategory = "typescript"
private val compilerServiceProcess = ":typescript_compiler"
private val fixtureInstrumentationName =
    ".R2PublicPreviewInstrumentation"
private val androidManifestNamespace =
    "http://schemas.android.com/apk/res/android"
private val r2PublicPreviewInstrumentationSource =
    layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/publicpreview/" +
            "R2PublicPreviewInstrumentation.java",
    )
private val r2PublicProgressSource =
    layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/publicpreview/" +
            "R2PublicProgress.java",
    )
private val r2FenceBoundedProjectFixtureSource =
    layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/publicpreview/" +
            "R2FenceBoundedProjectFixture.java",
    )
private val r2ExternalCapacityProjectFixtureSource =
    layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/publicpreview/" +
            "R2ExternalCapacityProjectFixture.java",
    )
private val r2ExternalNaturalEmitTimeoutProjectFixtureSource =
    layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/publicpreview/" +
            "R2ExternalNaturalEmitTimeoutProjectFixture.java",
    )
private val r2ProductionSnapshotSource =
    project(":app").layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "R2ProjectSnapshot.kt",
    )
private val r2ExternalCapacityEvidenceProtocolSource =
    project(":app").layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "R2ExternalCapacityEvidenceProtocol.kt",
    )
private val r2ExternalPlusOneCleanupEvidenceSource =
    project(":app").layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "R2ExternalPlusOneCleanupEvidence.kt",
    )
private val r2ExternalNaturalPhaseTimeoutEvidenceSource =
    project(":app").layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "R2ExternalNaturalPhaseTimeoutEvidence.kt",
    )
private val r2CompilationCoordinatorSource =
    project(":app").layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "R2CompilationCoordinator.kt",
    )
private val r2PrivateTransientOrphanScavengerSource =
    project(":app").layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "R2PrivateTransientOrphanScavenger.kt",
    )
private val r2CompilerServiceSource =
    project(":app").layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "TypeScriptCompilerPluginService.kt",
    )
private val r2CompilerHostSource =
    project(":app").layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "TypeScriptWebViewCompilerHost.kt",
    )
private val r2ProjectExecutionPhaseSource =
    project(":app").layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "R2ProjectExecutionPhase.kt",
    )
private val typeScriptR2ContractSource =
    project(":plugin-api:typescript-api").layout.projectDirectory.file(
        "src/main/java/org/autojs/plugin/typescript/api/TypeScriptR2Contract.kt",
    )
private val r2PluginMetadataSource =
    project(":app").layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "TypeScriptPluginMetadata.kt",
    )
private val appBuildScript =
    project(":app").layout.projectDirectory.file("build.gradle.kts")

private fun requireDisabledCompilerCapabilityAdvertisement(
    label: String,
    metadata: String,
) {
    val disabledCapabilities = linkedMapOf(
        "COMPILER_AVAILABLE" to "compilerAvailable",
        "PROJECT_COMPILATION" to "projectCompilation",
        "SEMANTIC_DIAGNOSTICS" to "semanticDiagnostics",
        "SOURCE_MAP_V3" to "sourceMapV3",
        "NODE_RUNTIME_REQUIRED" to "nodeRuntimeRequired",
    )
    disabledCapabilities.forEach { (capability, localValue) ->
        check(
            Regex("""\bval\s+$localValue\s*=\s*false\b""")
                .containsMatchIn(metadata) &&
                Regex(
                    """putBoolean\(\s*TypeScriptPluginCapabilityKeys\.$capability,\s*""" +
                        """$localValue\s*,?\s*\)""",
                    RegexOption.DOT_MATCHES_ALL,
                ).containsMatchIn(metadata),
        ) {
            "$label must keep $capability disabled through its validated local value."
        }
    }
}

private fun ByteArray.indexOfSubsequence(needle: ByteArray): Int {
    if (needle.isEmpty()) {
        return 0
    }
    val lastStart = size - needle.size
    for (start in 0..lastStart) {
        var matches = true
        for (offset in needle.indices) {
            if (this[start + offset] != needle[offset]) {
                matches = false
                break
            }
        }
        if (matches) {
            return start
        }
    }
    return -1
}

private fun androidManifestAttribute(localName: String): String =
    "$androidManifestNamespace:$localName"

private data class BinaryManifestElement(
    val name: String,
    val indent: Int,
    val attributes: MutableMap<String, String> = linkedMapOf(),
    val children: MutableList<BinaryManifestElement> = mutableListOf(),
)

private fun parseBinaryManifest(
    apkFile: File,
    xmlTree: String,
): BinaryManifestElement {
    val stack = mutableListOf<BinaryManifestElement>()
    var root: BinaryManifestElement? = null

    xmlTree.lineSequence().forEachIndexed { index, line ->
        val content = line.trimStart()
        val indent = line.length - content.length
        when {
            content.startsWith("E: ") -> {
                val elementName = content
                    .removePrefix("E: ")
                    .substringBefore(' ')
                check(elementName.isNotBlank()) {
                    "Malformed aapt2 element at ${apkFile.name}:${index + 1}: $line"
                }
                while (stack.lastOrNull()?.indent?.let { it >= indent } == true) {
                    stack.removeAt(stack.lastIndex)
                }
                val element = BinaryManifestElement(elementName, indent)
                if (stack.isEmpty()) {
                    check(root == null) {
                        "Found multiple binary manifest roots in ${apkFile.name}."
                    }
                    root = element
                } else {
                    stack.last().children += element
                }
                stack += element
            }

            content.startsWith("A: ") -> {
                val element = stack.lastOrNull()
                    ?: error(
                        "Found an aapt2 attribute without an element at " +
                            "${apkFile.name}:${index + 1}.",
                    )
                check(indent > element.indent) {
                    "Malformed aapt2 attribute indentation at " +
                        "${apkFile.name}:${index + 1}: $line"
                }
                val declaration = content.removePrefix("A: ")
                val equalsIndex = declaration.indexOf('=')
                check(equalsIndex > 0) {
                    "Malformed aapt2 attribute at ${apkFile.name}:${index + 1}: $line"
                }
                val attributeName = declaration
                    .substring(0, equalsIndex)
                    .substringBefore('(')
                val encodedValue = declaration.substring(equalsIndex + 1)
                val attributeValue =
                    if (encodedValue.startsWith('"')) {
                        val closingQuote = encodedValue.indexOf('"', startIndex = 1)
                        check(closingQuote > 0) {
                            "Malformed quoted aapt2 attribute at " +
                                "${apkFile.name}:${index + 1}: $line"
                        }
                        encodedValue.substring(1, closingQuote)
                    } else {
                        encodedValue.substringBefore(' ')
                    }
                check(element.attributes.put(attributeName, attributeValue) == null) {
                    "Duplicate $attributeName attribute on ${element.name} " +
                        "in ${apkFile.name}."
                }
            }
        }
    }

    return checkNotNull(root) {
        "aapt2 returned no manifest root for $apkFile."
    }.also {
        check(it.name == "manifest") {
            "Expected a manifest root in ${apkFile.name}, found ${it.name}."
        }
    }
}

private fun BinaryManifestElement.singleChild(
    childName: String,
    apkFile: File,
    context: String,
): BinaryManifestElement {
    val matches = children.filter { it.name == childName }
    check(matches.size == 1) {
        "Expected exactly one $childName under $context in ${apkFile.name}, " +
            "found ${matches.size}."
    }
    return matches.single()
}

private fun BinaryManifestElement.requireAttribute(
    attributeName: String,
    expected: String,
    apkFile: File,
    context: String,
) {
    val actual = attributes[attributeName]
    check(actual == expected) {
        "Expected $context $attributeName=$expected in ${apkFile.name}, " +
            "found $actual."
    }
}

private fun BinaryManifestElement.requireAndroidAttribute(
    localName: String,
    expected: String,
    apkFile: File,
    context: String,
) {
    requireAttribute(
        attributeName = androidManifestAttribute(localName),
        expected = expected,
        apkFile = apkFile,
        context = context,
    )
}

private fun BinaryManifestElement.requireEnabled(
    apkFile: File,
    context: String,
) {
    val enabled = attributes[androidManifestAttribute("enabled")]
    check(enabled == null || enabled == "true") {
        "Expected $context to be enabled in ${apkFile.name}, found " +
            "android:enabled=$enabled."
    }
}

private fun BinaryManifestElement.hasCompilerEndpointFilter(): Boolean {
    if (name != "intent-filter") {
        return false
    }
    val actions = children
        .filter { child -> child.name == "action" }
        .mapNotNull { child ->
            child.attributes[androidManifestAttribute("name")]
        }
    val categories = children
        .filter { child -> child.name == "category" }
        .mapNotNull { child ->
            child.attributes[androidManifestAttribute("name")]
        }
    return compilerServiceAction in actions &&
        compilerServiceCategory in categories
}

private fun verifyCompilerServiceManifest(
    apkFile: File,
    manifest: BinaryManifestElement,
    debuggableExpected: Boolean,
    testOnlyExpected: Boolean = false,
) {
    manifest.requireAttribute(
        attributeName = "package",
        expected = pluginApplicationId,
        apkFile = apkFile,
        context = "plugin manifest",
    )
    val application =
        manifest.singleChild("application", apkFile, "plugin manifest")
    application.requireEnabled(apkFile, "plugin application")
    val debuggable =
        application.attributes[androidManifestAttribute("debuggable")]
    if (debuggableExpected) {
        check(debuggable == "true") {
            "Debug plugin APK ${apkFile.name} must declare android:debuggable=true."
        }
    } else {
        check(debuggable == null || debuggable == "false") {
            "Release plugin APK ${apkFile.name} must not be debuggable."
        }
    }
    val testOnly =
        application.attributes[androidManifestAttribute("testOnly")]
    if (testOnlyExpected) {
        check(testOnly == "true") {
            "Evidence plugin APK ${apkFile.name} must declare android:testOnly=true."
        }
    } else {
        check(testOnly == null || testOnly == "false") {
            "Ordinary plugin APK ${apkFile.name} must not be test-only."
        }
    }
    val compilerServices = application.children.filter { element ->
        element.name == "service" &&
            element.attributes[androidManifestAttribute("name")] ==
                compilerServiceName
    }
    check(compilerServices.size == 1) {
        "Expected exactly one $compilerServiceName in ${apkFile.name}, " +
            "found ${compilerServices.size}."
    }
    val compilerService = compilerServices.single()
    compilerService.requireEnabled(apkFile, compilerServiceName)
    compilerService.requireAndroidAttribute(
        "exported",
        "true",
        apkFile,
        compilerServiceName,
    )
    compilerService.requireAndroidAttribute(
        "permission",
        pluginSignaturePermission,
        apkFile,
        compilerServiceName,
    )
    compilerService.requireAndroidAttribute(
        "process",
        compilerServiceProcess,
        apkFile,
        compilerServiceName,
    )

    val intentFilter = compilerService.singleChild(
        "intent-filter",
        apkFile,
        compilerServiceName,
    )
    val action = intentFilter.singleChild(
        "action",
        apkFile,
        "$compilerServiceName intent-filter",
    )
    action.requireAndroidAttribute(
        "name",
        compilerServiceAction,
        apkFile,
        "$compilerServiceName action",
    )
    val category = intentFilter.singleChild(
        "category",
        apkFile,
        "$compilerServiceName intent-filter",
    )
    category.requireAndroidAttribute(
        "name",
        compilerServiceCategory,
        apkFile,
        "$compilerServiceName category",
    )
    check(intentFilter.children.size == 2) {
        "$compilerServiceName intent-filter in ${apkFile.name} must contain " +
            "only its compiler action and TypeScript category."
    }

    val compilerEndpoints = application.children
        .filter { element -> element.name == "service" }
        .flatMap { service ->
            service.children
                .filter(BinaryManifestElement::hasCompilerEndpointFilter)
                .map { filter -> service to filter }
        }
    check(
        compilerEndpoints.size == 1 &&
            compilerEndpoints.single().first === compilerService &&
            compilerEndpoints.single().second === intentFilter
    ) {
        "Expected exactly one compiler action/category service endpoint in " +
            "${apkFile.name}, found ${compilerEndpoints.size}."
    }
}

private fun verifyFixtureManifest(
    apkFile: File,
    manifest: BinaryManifestElement,
) {
    manifest.requireAttribute(
        attributeName = "package",
        expected = fixtureApplicationId,
        apkFile = apkFile,
        context = "fixture manifest",
    )
    check(fixtureApplicationId != pluginApplicationId) {
        "Fixture and plugin application IDs must remain distinct."
    }
    check(
        androidManifestAttribute("sharedUserId") !in manifest.attributes
    ) {
        "Fixture APK ${apkFile.name} must not declare android:sharedUserId."
    }
    val application =
        manifest.singleChild("application", apkFile, "fixture manifest")
    application.requireAndroidAttribute(
        "testOnly",
        "true",
        apkFile,
        "fixture application",
    )

    val permissions = manifest.children.filter { it.name == "uses-permission" }
    check(permissions.size == 1) {
        "Fixture APK ${apkFile.name} must declare exactly one uses-permission, " +
            "found ${permissions.size}."
    }
    permissions.single().requireAndroidAttribute(
        "name",
        pluginSignaturePermission,
        apkFile,
        "fixture uses-permission",
    )

    val queries = manifest.singleChild("queries", apkFile, "fixture manifest")
    val queriedPackages = queries.children.filter { it.name == "package" }
    check(queriedPackages.size == 1 && queries.children.size == 1) {
        "Fixture APK ${apkFile.name} must contain exactly one package query."
    }
    queriedPackages.single().requireAndroidAttribute(
        "name",
        pluginApplicationId,
        apkFile,
        "fixture package query",
    )

    val instrumentation =
        manifest.singleChild("instrumentation", apkFile, "fixture manifest")
    instrumentation.requireAndroidAttribute(
        "name",
        fixtureInstrumentationName,
        apkFile,
        "fixture instrumentation",
    )
    instrumentation.requireAndroidAttribute(
        "targetPackage",
        fixtureApplicationId,
        apkFile,
        "fixture instrumentation",
    )
}

private fun singleVariantApk(
    apkProject: Project,
    variant: String,
): File {
    val directory = apkProject.layout.buildDirectory
        .dir("outputs/apk/$variant")
        .get()
        .asFile
    val apks = directory
        .listFiles { file ->
            file.isFile && file.extension.equals("apk", ignoreCase = true)
        }
        .orEmpty()
        .toList()
    check(apks.size == 1) {
        "Expected exactly one $variant APK in $directory, found $apks."
    }
    return apks.single()
}

val verifyR2PublicPreviewApkManifestShape =
    tasks.register("verifyR2PublicPreviewApkManifestShape") {
        group = "verification"
        description =
            "Verifies the plugin service and external fixture binary APK manifests."
        dependsOn(
            ":app:assembleDebug",
            ":app:assembleRelease",
            ":app:assembleR2CapacityEvidenceDebug",
            ":app:assembleR2CapacityEvidenceRelease",
            "assembleDebug",
            "assembleRelease",
        )

        doLast {
            val aapt2Executable = androidComponents.sdkComponents.aapt2
                .flatMap { aapt2 -> aapt2.executable }
                .get()
                .asFile
            check(aapt2Executable.isFile) {
                "Could not find aapt2 at $aapt2Executable."
            }

            fun binaryManifest(apkFile: File): BinaryManifestElement {
                val xmlTree = providers.exec {
                    commandLine(
                        aapt2Executable.absolutePath,
                        "dump",
                        "xmltree",
                        apkFile.absolutePath,
                        "--file",
                        "AndroidManifest.xml",
                    )
                }.standardOutput.asText.get()
                return parseBinaryManifest(apkFile, xmlTree)
            }

            listOf("debug", "release").forEach { buildTypeName ->
                val appApk =
                    singleVariantApk(project(":app"), buildTypeName)
                val fixtureApk =
                    singleVariantApk(project, buildTypeName)
                verifyCompilerServiceManifest(
                    appApk,
                    binaryManifest(appApk),
                    debuggableExpected = buildTypeName == "debug",
                )
                verifyFixtureManifest(fixtureApk, binaryManifest(fixtureApk))
            }
            listOf(
                "r2CapacityEvidenceDebug" to true,
                "r2CapacityEvidenceRelease" to false,
            ).forEach { (buildTypeName, debuggableExpected) ->
                val appApk =
                    singleVariantApk(project(":app"), buildTypeName)
                verifyCompilerServiceManifest(
                    appApk,
                    binaryManifest(appApk),
                    debuggableExpected = debuggableExpected,
                    testOnlyExpected = true,
                )
                check("r2-capacity-evidence" in appApk.name) {
                    "Evidence APK filename is not mechanically distinguished: ${appApk.name}"
                }
            }
        }
    }

val verifyR2PublicPreviewSigningConfiguration =
    tasks.register("verifyR2PublicPreviewSigningConfiguration") {
        group = "verification"
        description =
            "Checks that app and public R2 preview APKs use the same signer."
        dependsOn(
            ":app:assembleDebug",
            ":app:assembleRelease",
            ":app:assembleR2CapacityEvidenceDebug",
            ":app:assembleR2CapacityEvidenceRelease",
            "assembleDebug",
            "assembleRelease",
        )

        doLast {
            val appAndroid = project(":app").extensions.getByType(
                com.android.build.api.dsl.ApplicationExtension::class.java,
            )
            fun signerCertificateSha256(apk: File): String {
                val executableName =
                    if (System.getProperty("os.name").startsWith("Windows")) {
                        "apksigner.bat"
                    } else {
                        "apksigner"
                    }
                val executable = androidComponents.sdkComponents.sdkDirectory
                    .get()
                    .asFile
                    .resolve("build-tools")
                    .resolve(android.buildToolsVersion)
                    .resolve(executableName)
                check(executable.isFile) {
                    "Could not find apksigner at $executable."
                }
                val output = providers.exec {
                    commandLine(
                        executable.absolutePath,
                        "verify",
                        "--print-certs",
                        apk.absolutePath,
                    )
                }.standardOutput.asText.get()
                val digests = output.lineSequence()
                    .map(String::trim)
                    .filter { line ->
                        "certificate SHA-256 digest:" in line
                    }
                    .map { line ->
                        line.substringAfter("certificate SHA-256 digest:")
                            .trim()
                            .lowercase()
                    }
                    .distinct()
                    .toList()
                check(digests.size == 1) {
                    "Expected one signer certificate in $apk, found $digests."
                }
                return digests.single()
            }

            listOf("debug", "release").forEach { buildTypeName ->
                val appSigning =
                    appAndroid.buildTypes.getByName(buildTypeName).signingConfig
                val fixtureSigning =
                    android.buildTypes.getByName(buildTypeName).signingConfig
                check((appSigning == null) == (fixtureSigning == null)) {
                    "$buildTypeName app/fixture signing availability differs."
                }
                if (appSigning != null && fixtureSigning != null) {
                    check(
                        appSigning.storeFile?.canonicalFile ==
                            fixtureSigning.storeFile?.canonicalFile,
                    ) {
                        "$buildTypeName app/fixture keystore differs."
                    }
                    check(appSigning.keyAlias == fixtureSigning.keyAlias) {
                        "$buildTypeName app/fixture signing alias differs."
                    }
                }

                val appApk =
                    singleVariantApk(project(":app"), buildTypeName)
                val fixtureApk =
                    singleVariantApk(project, buildTypeName)
                val appDigest = signerCertificateSha256(appApk)
                val fixtureDigest = signerCertificateSha256(fixtureApk)
                check(appDigest == fixtureDigest) {
                    "$buildTypeName APK signer certificate differs: " +
                        "app=$appDigest, fixture=$fixtureDigest."
                }
            }
            listOf(
                "r2CapacityEvidenceDebug" to "debug",
                "r2CapacityEvidenceRelease" to "release",
            ).forEach { (appBuildTypeName, fixtureBuildTypeName) ->
                val appSigning =
                    appAndroid.buildTypes
                        .getByName(appBuildTypeName)
                        .signingConfig
                val fixtureSigning =
                    android.buildTypes
                        .getByName(fixtureBuildTypeName)
                        .signingConfig
                check((appSigning == null) == (fixtureSigning == null)) {
                    "$appBuildTypeName/$fixtureBuildTypeName signing availability differs."
                }
                if (appSigning != null && fixtureSigning != null) {
                    check(
                        appSigning.storeFile?.canonicalFile ==
                            fixtureSigning.storeFile?.canonicalFile,
                    ) {
                        "$appBuildTypeName/$fixtureBuildTypeName keystore differs."
                    }
                    check(appSigning.keyAlias == fixtureSigning.keyAlias) {
                        "$appBuildTypeName/$fixtureBuildTypeName signing alias differs."
                    }
                }

                val appApk =
                    singleVariantApk(project(":app"), appBuildTypeName)
                val fixtureApk =
                    singleVariantApk(project, fixtureBuildTypeName)
                check(
                    signerCertificateSha256(appApk) ==
                        signerCertificateSha256(fixtureApk),
                ) {
                    "$appBuildTypeName/$fixtureBuildTypeName APK signer differs."
                }
            }
        }
    }

val verifyR2P3bB2cExternalSourcePolicy =
    tasks.register("verifyR2P3bB2cExternalSourcePolicy") {
        group = "verification"
        description =
            "Statically preserves the pre-C8 R2-P3b-b2c external production-fence evidence " +
                "matrix without treating its common-only declaration workload as current D1 " +
                "profile authority or running devices."
        dependsOn(":app:verifyR2ReleaseCompatibilitySourcePolicy")
        inputs.files(
            r2PublicPreviewInstrumentationSource,
            r2FenceBoundedProjectFixtureSource,
            r2ProductionSnapshotSource,
            typeScriptR2ContractSource,
        )

        doLast {
            val instrumentationSource =
                r2PublicPreviewInstrumentationSource.asFile.readText(Charsets.UTF_8)
            val fixtureSource =
                r2FenceBoundedProjectFixtureSource.asFile.readText(Charsets.UTF_8)
            val productionSnapshotSource =
                r2ProductionSnapshotSource.asFile.readText(Charsets.UTF_8)

            fun requireMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-P3b-b2c $label is missing source marker: $marker"
                    }
                }
            }

            requireMarkers(
                label = "external UID, signer, ACK, and three-path instrumentation",
                source = instrumentationSource,
                markers = listOf(
                    "Process.myUid() != currentApplication.uid",
                    "PackageManager.SIGNATURE_MATCH",
                    "PermissionInfo.PROTECTION_SIGNATURE",
                    "runInvalidArchiveCase(context, discovery, component)",
                    "runFenceInclusiveCase(",
                    "runFencePlusOneCase(",
                    "wrongToken(jobToken)",
                    "callback.assertExactlyOneCompletion()",
                    "TypeScriptCompilerContract.OUTCOME_EMITTED",
                    "TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA",
                    "TypeScriptCompilerContract.ERROR_ARCHIVE_FORMAT",
                    "assertNoCompilationPayload(",
                    "assertSyncedEmptyOutput(",
                    "verifyEmittedOutput(",
                    "CALLBACK_QUIET_MS = 1_000L",
                    "metadata.requireMatches(requiredAuthority)",
                    "verifyOutputManifestFiles(",
                    "outputInventoryFingerprint(",
                    "deleteFixtureFiles(",
                    "\"event=identity fixtureUid=\"",
                    "\"event=case\"",
                    "\" elapsedMs=\"",
                    "\"compilation-manifest.json\"",
                    "\"diagnostics.json\"",
                    "\"modules/main.js\"",
                    "\"maps/main.js.map\"",
                    "context.createPackageContext(CURRENT_PACKAGE, 0)",
                    "TypeScriptCompilerContract.MAX_TIMEOUT_MS",
                ),
            )
            listOf(
                "PROFILE_SCHEMA_REVISION",
                "RUNTIME_CAPABILITY_SCHEMA_REVISION",
            ).forEach { capability ->
                check(
                    Regex(
                        """TypeScriptPluginCapabilityKeys\s*\.\s*$capability""",
                    ).containsMatchIn(instrumentationSource),
                ) {
                    "R2-P3b-b2c compilerInfo must read the $capability capability key."
                }
            }
            val invalidCase =
                instrumentationSource.indexOf(
                    "runInvalidArchiveCase(context, discovery, component)",
                )
            val inclusiveCase =
                instrumentationSource.indexOf(
                    "runFenceInclusiveCase(",
                    invalidCase + 1,
                )
            val plusOneCase =
                instrumentationSource.indexOf(
                    "runFencePlusOneCase(",
                    inclusiveCase + 1,
                )
            check(
                invalidCase >= 0 &&
                    invalidCase < inclusiveCase &&
                    inclusiveCase < plusOneCase,
            ) {
                "R2-P3b-b2c external instrumentation must retain invalid ZIP, " +
                    "fence-inclusive emit, then fence-plus-one quota paths."
            }

            requireMarkers(
                label = "strict fence-bounded STORED ZIP32 fixture",
                source = fixtureSource,
                markers = listOf(
                    "WORKER_MAXIMUM_TOTAL_BYTES = 8L * 1024L * 1024L",
                    "EXPECTED_STANDARD_LIBRARY_FILES = 108",
                    "EXPECTED_STANDARD_LIBRARY_BYTES = 3_784_758L",
                    "EXPECTED_CALLER_BYTES = 4_603_850L",
                    "exceedsWorkerFence ? 1L : 0L",
                    "\"typescript/\"",
                    "\"/distribution.json\"",
                    "writeStoredZip32(",
                    "putLocalHeader(",
                    "putCentralHeader(",
                    "fileOutput.getFD().sync()",
                    "verifyStoredZip32(",
                    "actual.getMethod() == ZipEntry.STORED",
                    "expected.metrics.sha256.equals(sha256(input))",
                    "deleteExactly(input, \"fence-bounded input fixture\")",
                ),
            )
            check("Deflater" !in fixtureSource && "ZipOutputStream" !in fixtureSource) {
                "R2-P3b-b2c fixture must retain its explicit STORED ZIP32 writer."
            }
            check(
                "compileProjectWithExpandedWorkerCapacityForTesting" !in
                    instrumentationSource &&
                    "compileProjectWithExpandedWorkerCapacityForTesting" !in
                    fixtureSource &&
                    "BuildConfig.DEBUG" !in instrumentationSource &&
                    "BuildConfig.DEBUG" !in fixtureSource,
            ) {
                "R2-P3b-b2c external evidence must not gain a DEBUG expanded-capacity seam."
            }

            requireMarkers(
                label = "current additive ordinary compilation authority",
                source = productionSnapshotSource,
                markers = listOf(
                    "val ORDINARY_COMPILATION = R2ProjectSnapshotLimits(",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES",
                    "val PROVISIONAL_WORKER = ORDINARY_COMPILATION",
                ),
            )
            val ordinaryQuotaSource =
                typeScriptR2ContractSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "current public ordinary quota values",
                source = ordinaryQuotaSource,
                markers = listOf(
                    "const val MAX_ORDINARY_COMPILATION_INPUT_FILES = 512",
                    "const val MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES = " +
                        "8L * 1024L * 1024L",
                    "const val MAX_ORDINARY_COMPILATION_INPUT_BYTES = 8L * 1024L * 1024L",
                ),
            )
            check(
                "EXPECTED_STANDARD_LIBRARY_FILES = 108" in fixtureSource &&
                    "EXPECTED_CALLER_BYTES = 4_603_850L" in fixtureSource
            ) {
                "R2-P3b-b2c must remain an immutable pre-C8 common-only historical workload."
            }
        }
    }

val verifyR2P3bB2cExternalApks =
    tasks.register("verifyR2P3bB2cExternalApks") {
        group = "verification"
        description =
            "Verifies that both external fixture APK variants contain the R2-P3b-b2c " +
                "three-path matrix; it does not run devices."
        dependsOn("assembleDebug", "assembleRelease")
        dependsOn(verifyR2P3bB2cExternalSourcePolicy)

        doLast {
            listOf("debug", "release").forEach { variant ->
                val apkFile = singleVariantApk(project, variant)
                val dexBytes = ZipFile(apkFile).use { apk ->
                    apk.entries().asSequence()
                        .filter { entry ->
                            !entry.isDirectory &&
                                entry.name.matches(Regex("""classes(?:\d+)?\.dex"""))
                        }
                        .map { entry ->
                            apk.getInputStream(entry).use { input -> input.readBytes() }
                        }
                        .toList()
                }
                check(dexBytes.isNotEmpty()) {
                    "R2-P3b-b2c $variant fixture APK contains no DEX entries: $apkFile"
                }
                val requiredMarkers = linkedMapOf(
                    "instrumentation class" to "R2PublicPreviewInstrumentation",
                    "fence fixture class" to "R2FenceBoundedProjectFixture",
                    "invalid ZIP path" to "runInvalidArchiveCase",
                    "inclusive emit path" to "runFenceInclusiveCase",
                    "plus-one quota path" to "runFencePlusOneCase",
                    "output verifier" to "verifyEmittedOutput",
                    "empty-output verifier" to "assertSyncedEmptyOutput",
                )
                requiredMarkers.forEach { (label, marker) ->
                    val markerBytes = marker.toByteArray(Charsets.UTF_8)
                    check(
                        dexBytes.any { dex -> dex.indexOfSubsequence(markerBytes) >= 0 },
                    ) {
                        "R2-P3b-b2c $variant fixture APK is missing $label marker: $marker"
                    }
                }
            }
        }
    }

val verifyR2P3bB2dExternalExact64SourcePolicy =
    tasks.register("verifyR2P3bB2dExternalExact64SourcePolicy") {
        group = "verification"
        description =
            "Statically verifies the non-shipping external exact-64-MiB evidence authority, " +
                "four streaming boundaries, and the current additive ordinary quota fence."
        dependsOn(":app:testDebugUnitTest")
        dependsOn(":app:verifyR2PublicPreviewSourcePolicy")
        inputs.files(
            r2PublicPreviewInstrumentationSource,
            r2ExternalCapacityProjectFixtureSource,
            r2ExternalCapacityEvidenceProtocolSource,
            r2CompilerServiceSource,
            r2CompilerHostSource,
            r2ProductionSnapshotSource,
            typeScriptR2ContractSource,
            appBuildScript,
        )

        doLast {
            fun requireMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-P3b-b2d $label is missing source marker: $marker"
                    }
                }
            }

            val instrumentation =
                r2PublicPreviewInstrumentationSource.asFile
                    .readText(Charsets.UTF_8)
            requireMarkers(
                label = "external exact-capacity runner",
                source = instrumentation,
                markers = listOf(
                    "ARG_EXTERNAL_CAPACITY_EVIDENCE",
                    "EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY",
                    "stored-container-exact-64-v1",
                    "deflated-expanded-exact-64-v1",
                    "runExternalCapacityBoundaryCase(",
                    ".STORED_CONTAINER_INCLUSIVE",
                    ".STORED_CONTAINER_PLUS_ONE",
                    ".DEFLATED_UNCOMPRESSED_INCLUSIVE",
                    ".DEFLATED_UNCOMPRESSED_PLUS_ONE",
                    "callback.assertExactlyOneCompletion()",
                    "acknowledgeAdmission(admission)",
                    "TypeScriptCompilerContract.OUTCOME_EMITTED",
                    "TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA",
                    "assertNoCompilationPayload(",
                    "assertSyncedEmptyOutput(",
                    "verifyEmittedOutput(",
                    "logCapacityEvidence(",
                    "CALLBACK_QUIET_MS = 1_000L",
                    "TypeScriptCompilerContract.MAX_TIMEOUT_MS",
                ),
            )
            check(
                "expandedWorkerCapacity = true" !in instrumentation &&
                    "compileProjectWithExpandedWorkerCapacityForTesting" !in
                    instrumentation,
            ) {
                "External fixture must request an evidence scope, not a generic expanded bit."
            }
            val lastCapacityCase = instrumentation.lastIndexOf(
                ".DEFLATED_UNCOMPRESSED_PLUS_ONE",
            )
            val successor = instrumentation.indexOf(
                "runFenceInclusiveCase(",
                lastCapacityCase,
            )
            check(lastCapacityCase >= 0 && successor > lastCapacityCase) {
                "External exact-capacity evidence must finish with its historical fence successor."
            }

            val fixture =
                r2ExternalCapacityProjectFixtureSource.asFile
                    .readText(Charsets.UTF_8)
            requireMarkers(
                label = "constant-memory four-boundary ZIP32 fixture",
                source = fixture,
                markers = listOf(
                    "STORED_CONTAINER_INCLUSIVE",
                    "STORED_CONTAINER_PLUS_ONE",
                    "DEFLATED_UNCOMPRESSED_INCLUSIVE",
                    "DEFLATED_UNCOMPRESSED_PLUS_ONE",
                    "CAPACITY_PADDING_DECLARATION_COUNT = 8",
                    "MAIN_SOURCE_BYTES = 4L * 1024L",
                    "MAXIMUM_FIXED_POINT_ATTEMPTS = 8",
                    "new Deflater(Deflater.DEFAULT_COMPRESSION, true)",
                    "LOCAL_FILE_HEADER_SIGNATURE",
                    "CENTRAL_DIRECTORY_SIGNATURE",
                    "END_OF_CENTRAL_DIRECTORY_SIGNATURE",
                    "fileOutput.getFD().sync()",
                    "verifyWrittenArchive(",
                    "verifyQuotaBoundary(",
                    "relaxedContainerBytes",
                    "relaxedUncompressedBytes",
                    "MAX_SINGLE_FILE_BYTES",
                    "MAX_ARCHIVE_COMPRESSION_RATIO",
                    "Arrays.fill(buffer, (byte) 0)",
                    "deleteExactly(input, \"external capacity input fixture\")",
                ),
            )
            check(
                "ZipOutputStream" !in fixture &&
                    "readBytes(" !in fixture
            ) {
                "External exact-capacity fixture must retain explicit streaming ZIP32 output."
            }

            val policy =
                r2ExternalCapacityEvidenceProtocolSource.asFile
                    .readText(Charsets.UTF_8)
            requireMarkers(
                label = "derived evidence authority",
                source = policy,
                markers = listOf(
                    "attestation.evidenceBuildEnabled",
                    "attestation.callingUid != attestation.applicationUid",
                    "attestation.callingPackages == setOf(FIXTURE_PACKAGE)",
                    "attestation.applicationTestOnly",
                    "attestation.fixtureTestOnly",
                    "attestation.signaturesMatch",
                    "storedContainerScope.isEmpty()",
                    "deflatedExpandedScope.isEmpty()",
                    "scopeAcceptsInputSize(",
                    "R2CompilationControlOrigin.EXTERNAL_CAPACITY_EVIDENCE",
                    "expandedWorkerCapacity = true",
                ),
            )

            val service =
                r2CompilerServiceSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "Binder-side fixture attestation",
                source = service,
                markers = listOf(
                    "BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE",
                    "packageManager.getPackagesForUid(callingUid)",
                    "ApplicationInfo.FLAG_TEST_ONLY",
                    "packageManager.checkSignatures(",
                    "PackageManager.SIGNATURE_MATCH",
                    "Os.fstat(descriptor).st_size",
                    "externalCapacityEvidenceRequested &&",
                    "externalCapacityEvidenceControls == null",
                    "Public R2 metadata or external evidence authority is ",
                    "incomplete, malformed, or unsupported.",
                ),
            )

            val host =
                r2CompilerHostSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "release-equivalent internal Worker authority",
                source = host,
                markers = listOf(
                    "BuildConfig.DEBUG ||",
                    "BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE",
                    "workerAdmissionLimits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE",
                ),
            )

            val buildScript = appBuildScript.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "non-shipping evidence build types",
                source = buildScript,
                markers = listOf(
                    "\"R2_EXTERNAL_CAPACITY_EVIDENCE\", \"false\"",
                    "\"R2_EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY\"",
                    "create(\"r2CapacityEvidenceDebug\")",
                    "initWith(getByName(\"debug\"))",
                    "create(\"r2CapacityEvidenceRelease\")",
                    "initWith(getByName(\"release\"))",
                    "\"R2_EXTERNAL_CAPACITY_EVIDENCE\", \"true\"",
                    "__autojs6_r2_external_capacity_evidence_scope",
                    "stored-container-exact-64-v1",
                    "deflated-expanded-exact-64-v1",
                ),
            )
            val productionSnapshot =
                r2ProductionSnapshotSource.asFile.readText(Charsets.UTF_8)
            val publicR2Contract =
                typeScriptR2ContractSource.asFile.readText(Charsets.UTF_8)
            check(
                "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES" in
                    productionSnapshot &&
                    ("const val MAX_ORDINARY_COMPILATION_INPUT_BYTES = " +
                        "8L * 1024L * 1024L") in publicR2Contract,
            ) {
                "R2-P3b-b2d must preserve the public ordinary compilation 8 MiB fence."
            }
        }
    }

val verifyR2P3bB2dExternalExact64Apks =
    tasks.register("verifyR2P3bB2dExternalExact64Apks") {
        group = "verification"
        description =
            "Verifies fixture/evidence APK markers, minified release-equivalent shape, " +
                "normal Debug/Release evidence exclusion, signing, manifests, and four native ABIs."
        dependsOn(verifyR2P3bB2dExternalExact64SourcePolicy)
        dependsOn(verifyR2PublicPreviewApkManifestShape)
        dependsOn(verifyR2PublicPreviewSigningConfiguration)
        dependsOn(verifyR2P3bB2cExternalApks)
        dependsOn(
            ":app:assembleR2CapacityEvidenceDebug",
            ":app:assembleR2CapacityEvidenceRelease",
            "assembleDebug",
            "assembleRelease",
        )

        doLast {
            fun dexBytes(apkFile: File): List<ByteArray> =
                ZipFile(apkFile).use { apk ->
                    apk.entries().asSequence()
                        .filter { entry ->
                            !entry.isDirectory &&
                                entry.name.matches(
                                    Regex("""classes(?:\d+)?\.dex"""),
                                )
                        }
                        .map { entry ->
                            apk.getInputStream(entry).use { it.readBytes() }
                        }
                        .toList()
                }

            fun containsMarker(
                dex: List<ByteArray>,
                marker: String,
            ): Boolean {
                val bytes = marker.toByteArray(Charsets.UTF_8)
                return dex.any { it.indexOfSubsequence(bytes) >= 0 }
            }

            listOf("debug", "release").forEach { fixtureVariant ->
                val fixtureApk = singleVariantApk(project, fixtureVariant)
                val dex = dexBytes(fixtureApk)
                check(dex.isNotEmpty()) {
                    "$fixtureVariant external fixture contains no DEX."
                }
                listOf(
                    "R2ExternalCapacityProjectFixture",
                    "STORED_CONTAINER_INCLUSIVE",
                    "STORED_CONTAINER_PLUS_ONE",
                    "DEFLATED_UNCOMPRESSED_INCLUSIVE",
                    "DEFLATED_UNCOMPRESSED_PLUS_ONE",
                    "externalCapacityEvidence",
                    "__autojs6_r2_external_capacity_evidence_scope",
                    "runExternalCapacityBoundaryCase",
                    "logCapacityEvidence",
                ).forEach { marker ->
                    check(containsMarker(dex, marker)) {
                        "$fixtureVariant external fixture APK is missing: $marker"
                    }
                }
            }

            val evidenceVariants = listOf(
                "r2CapacityEvidenceDebug",
                "r2CapacityEvidenceRelease",
            )
            evidenceVariants.forEach { variant ->
                val apkFile = singleVariantApk(project(":app"), variant)
                val dex = dexBytes(apkFile)
                listOf(
                    "__autojs6_r2_external_capacity_evidence_scope",
                    "stored-container-exact-64-v1",
                    "deflated-expanded-exact-64-v1",
                    "capacity-project",
                    "Public R2 metadata or external evidence authority is incomplete, " +
                        "malformed, or unsupported.",
                ).forEach { marker ->
                    check(containsMarker(dex, marker)) {
                        "$variant evidence APK is missing authority marker: $marker"
                    }
                }
                val nativeEntries = ZipFile(apkFile).use { apk ->
                    apk.entries().asSequence()
                        .filter { entry ->
                            !entry.isDirectory &&
                                entry.name.endsWith(
                                    "/libautojs6_r2_fd_flags.so",
                                )
                        }
                        .map { it.name }
                        .toSet()
                }
                check(
                    nativeEntries == setOf(
                        "lib/arm64-v8a/libautojs6_r2_fd_flags.so",
                        "lib/armeabi-v7a/libautojs6_r2_fd_flags.so",
                        "lib/x86/libautojs6_r2_fd_flags.so",
                        "lib/x86_64/libautojs6_r2_fd_flags.so",
                    ),
                ) {
                    "$variant evidence APK native ABI inventory differs: $nativeEntries"
                }
            }

            listOf("debug", "release").forEach { normalVariant ->
                val normalApk = singleVariantApk(project(":app"), normalVariant)
                val normalDex = dexBytes(normalApk)
                listOf(
                    "__autojs6_r2_external_capacity_evidence_scope",
                    "stored-container-exact-64-v1",
                    "deflated-expanded-exact-64-v1",
                    "Public R2 metadata or external evidence authority is incomplete, " +
                        "malformed, or unsupported.",
                ).forEach { marker ->
                    check(!containsMarker(normalDex, marker)) {
                        "Normal $normalVariant APK retained non-shipping evidence marker: $marker"
                    }
                }
            }
            val mappingFile =
                project(":app").layout.buildDirectory.file(
                    "outputs/mapping/r2CapacityEvidenceRelease/mapping.txt",
                ).get().asFile
            check(mappingFile.isFile && mappingFile.length() > 0L) {
                "Minified evidence release-equivalent mapping is missing."
            }
        }
    }

val verifyR2P3bB2fExternalPlusOneCleanupSourcePolicy =
    tasks.register("verifyR2P3bB2fExternalPlusOneCleanupSourcePolicy") {
        group = "verification"
        description =
            "Statically verifies the R2-P3b-b2f external plus-one cleanup, receipt ordering, " +
                "exact evidence caller, safe process identity, and successor evidence."
        dependsOn(verifyR2P3bB2cExternalSourcePolicy)
        dependsOn(verifyR2P3bB2dExternalExact64SourcePolicy)
        inputs.files(
            r2PublicPreviewInstrumentationSource,
            r2ExternalCapacityEvidenceProtocolSource,
            r2ExternalPlusOneCleanupEvidenceSource,
            r2CompilationCoordinatorSource,
            r2PrivateTransientOrphanScavengerSource,
            r2CompilerServiceSource,
            r2ProductionSnapshotSource,
            typeScriptR2ContractSource,
            r2PluginMetadataSource,
            appBuildScript,
        )

        doLast {
            fun requireMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-P3b-b2f $label is missing source marker: $marker"
                    }
                }
            }

            fun javaMethod(
                source: String,
                signature: String,
            ): String {
                val start = source.indexOf(signature)
                check(start >= 0) {
                    "R2-P3b-b2f fixture method is missing: $signature"
                }
                val end = source.indexOf("\n    private ", start + signature.length)
                    .takeIf { index -> index >= 0 }
                    ?: source.length
                return source.substring(start, end)
            }

            val instrumentation =
                r2PublicPreviewInstrumentationSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "dedicated external cleanup runner",
                source = instrumentation,
                markers = listOf(
                    "ARG_EXTERNAL_PLUS_ONE_CLEANUP_EVIDENCE",
                    "\"externalPlusOneCleanupEvidence\"",
                    "runExternalPlusOneCleanupEvidence(",
                    "runFencePlusOneCleanupCase(",
                    "runExternalCapacityPlusOneCleanupCase(",
                    ".STORED_CONTAINER_PLUS_ONE",
                    ".DEFLATED_UNCOMPRESSED_PLUS_ONE",
                    "runFenceInclusiveCleanupSuccessor(",
                    "\"fence-inclusive-successor-\"",
                    "assertPreAcknowledgementCleanupTelemetry(",
                    "awaitCleanupEvidence(",
                    "ExternalPlusOneCleanupTelemetry",
                    "REQUIRED_CONSECUTIVE_CLEANUP_SAMPLES = 5",
                    "callback.assertExactlyOneCompletion()",
                    "TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA",
                    "assertSyncedEmptyOutput(",
                    "currentIsAllZero()",
                    "requireLiveSameBinder(session)",
                    "\"event=external-plus-one-cleanup-pass\"",
                ),
            )

            val runner = javaMethod(
                source = instrumentation,
                signature = "private void runExternalPlusOneCleanupEvidence(",
            )
            val ordinaryPlusOne = runner.indexOf("runFencePlusOneCleanupCase(")
            val storedPlusOne = runner.indexOf(".STORED_CONTAINER_PLUS_ONE")
            val deflatedPlusOne =
                runner.indexOf(".DEFLATED_UNCOMPRESSED_PLUS_ONE")
            val successorCalls =
                Regex("""runFenceInclusiveCleanupSuccessor\(""").findAll(runner)
                    .map { match -> match.range.first }
                    .toList()
            check(
                ordinaryPlusOne >= 0 &&
                    storedPlusOne > ordinaryPlusOne &&
                    deflatedPlusOne > storedPlusOne &&
                    successorCalls.size == 3 &&
                    successorCalls[0] in (ordinaryPlusOne + 1) until storedPlusOne &&
                    successorCalls[1] in (storedPlusOne + 1) until deflatedPlusOne &&
                    successorCalls[2] > deflatedPlusOne,
            ) {
                "R2-P3b-b2f dedicated mode must run ordinary, STORED, and DEFLATED plus-one " +
                    "rejections, each followed by an ordinary same-bound successor."
            }

            val capacityPlusOneMethod = javaMethod(
                source = instrumentation,
                signature = "private static void runExternalCapacityPlusOneCleanupCase(",
            )
            requireMarkers(
                label = "scope-free exact-capacity plus-one rejection",
                source = capacityPlusOneMethod,
                markers = listOf(
                    "boundary.isPlusOne()",
                    "MAX_INPUT_ARCHIVE_BYTES",
                    "MAX_INPUT_BYTES",
                    "+ 1L",
                    "admitOnBoundCompiler(",
                    "assertPreAcknowledgementCleanupTelemetry(",
                    "callback.awaitTerminal(",
                    "callback.assertExactlyOneCompletion()",
                    "TypeScriptCompilerContract.OUTCOME_FAILED",
                    "TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA",
                    "awaitCleanupEvidence(",
                ),
            )
            check(
                "EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY" !in capacityPlusOneMethod &&
                    "STORED_CONTAINER_EXACT_64_SCOPE" !in capacityPlusOneMethod &&
                    "DEFLATED_EXPANDED_EXACT_64_SCOPE" !in capacityPlusOneMethod,
            ) {
                "R2-P3b-b2f plus-one cleanup requests must not carry an exact64 evidence scope."
            }

            val boundAdmissionMethod = javaMethod(
                source = instrumentation,
                signature = "private static Admission admitOnBoundCompiler(",
            )
            requireMarkers(
                label = "ordinary-scope dedicated admission",
                source = boundAdmissionMethod,
                markers = listOf(
                    "while (SystemClock.elapsedRealtime() < deadline)",
                    "requireLiveSameBinder(session)",
                    "TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE",
                    "TypeScriptCompilerContract.ERROR_BUSY",
                    "SystemClock.sleep(Math.min(RETRY_DELAY_MS, remaining))",
                    "externalCapacityEvidenceScope = null",
                    "publicRequest(",
                    "null),",
                ),
            )
            check(
                "EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY" !in boundAdmissionMethod &&
                    "STORED_CONTAINER_EXACT_64_SCOPE" !in boundAdmissionMethod &&
                    "DEFLATED_EXPANDED_EXACT_64_SCOPE" !in boundAdmissionMethod,
            ) {
                "R2-P3b-b2f dedicated admission must not expose a capacity-evidence scope."
            }

            val successorMethod = javaMethod(
                source = instrumentation,
                signature = "private static void runFenceInclusiveCleanupSuccessor(",
            )
            requireMarkers(
                label = "same-bound ordinary successor",
                source = successorMethod,
                markers = listOf(
                    "fixture.callerSourceBytes == 4_603_850L",
                    "false))",
                    "assertPreAcknowledgementCleanupTelemetry(",
                    "callback.assertExactlyOneCompletion()",
                    "TypeScriptCompilerContract.OUTCOME_EMITTED",
                    "verifyEmittedOutput(",
                    "awaitCleanupEvidence(",
                    "\"ordinary-inclusive-successor\"",
                ),
            )

            listOf(
                "private static void runFencePlusOneCleanupCase(",
                "private static void runExternalCapacityPlusOneCleanupCase(",
                "private static void runFenceInclusiveCleanupSuccessor(",
            ).forEach { signature ->
                val method = javaMethod(instrumentation, signature)
                val preAcknowledgement =
                    method.indexOf("assertPreAcknowledgementCleanupTelemetry(")
                val acknowledgement = method.indexOf("acknowledgeAdmission(")
                val callback = method.indexOf("callback.awaitTerminal(")
                val cleanupReceipt = method.indexOf("awaitCleanupEvidence(")
                check(
                    preAcknowledgement >= 0 &&
                        preAcknowledgement < acknowledgement &&
                        acknowledgement < callback &&
                        callback < cleanupReceipt,
                ) {
                    "R2-P3b-b2f $signature must prove pre-ACK ownership, then ACK, receive " +
                    "the callback, and finally correlate the pre-callback cleanup receipt."
                }
            }

            val preAcknowledgementMethod = javaMethod(
                source = instrumentation,
                signature = "private static CleanupCheckpoint",
            )
            requireMarkers(
                label = "pre-ACK exact remote ownership",
                source = preAcknowledgementMethod,
                markers = listOf(
                    "admission.requestId.equals(preAcknowledgement.requestId)",
                    "jobTokenSha256.equals(",
                    "preAcknowledgement.jobTokenSha256",
                    "descriptorTargetSha256(input).equals(",
                    "preAcknowledgement.inputTargetSha256",
                    "descriptorTargetSha256(output).equals(",
                    "preAcknowledgement.outputTargetSha256",
                    "preAcknowledgement.activeLease",
                    "preAcknowledgement.inputExactTargetFdCount == 1",
                    "preAcknowledgement.outputExactTargetFdCount == 1",
                    "PRE_ACK_TELEMETRY_WAIT_MS",
                    "PRE_ACK_TELEMETRY_SAMPLE_DELAY_MS",
                    "CLEANUP_KEY_PRE_ACK_AVAILABLE",
                    "CLEANUP_KEY_CURRENT_AVAILABLE",
                    "telemetrySamples++",
                    "SystemClock.sleep(",
                    "stable exact ",
                    "telemetryWaitElapsedMs",
                    "telemetry.current.activeLeaseCount == 1",
                    "telemetry.current.inputExactTargetFdCount == 1",
                    "telemetry.current.outputExactTargetFdCount == 1",
                    "requireSameProcessIdentity(",
                ),
            )

            val cleanupReceiptMethod = javaMethod(
                source = instrumentation,
                signature = "private static void assertCleanupReceipt(",
            )
            requireMarkers(
                label = "pre-callback zero-resource receipt",
                source = cleanupReceiptMethod,
                markers = listOf(
                    "receipt.snapshotAvailable",
                    "receipt.activeLeaseReleased",
                    "receipt.createdBeforeCallback",
                    "receipt.inputExactTargetFdCount == 0",
                    "receipt.outputExactTargetFdCount == 0",
                    "receipt.privateInputNamedFileCount == 0",
                    "receipt.privateInputFdCount == 0",
                    "receipt.projectSpoolNamedFileCount == 0",
                    "receipt.projectSpoolFdCount == 0",
                    "receipt.privateOutputNamedFileCount == 0",
                    "receipt.privateOutputFdCount == 0",
                    "receipt.cursorReleased",
                    "\"NOT_CREATED\".equals(receipt.cursorState)",
                    "receipt.cursorOpenedCount == 0",
                    "receipt.cursorClosedCount == 0",
                    "\"RELEASED\".equals(receipt.cursorState)",
                    "receipt.cursorOpenedCount",
                    "== receipt.cursorClosedCount",
                    "requireSameProcessIdentity(",
                ),
            )

            val cleanupAwaitMethod = javaMethod(
                source = instrumentation,
                signature = "private static CleanupReceipt awaitCleanupEvidence(",
            )
            requireMarkers(
                label = "callback-correlated five-zero convergence",
                source = cleanupAwaitMethod,
                markers = listOf(
                    "telemetry.receipt.requestId",
                    "telemetry.receipt.jobTokenSha256",
                    "assertCleanupReceipt(",
                    "telemetry.currentIsAllZero()",
                    "consecutiveAllZeroSamples++",
                    ">= REQUIRED_CONSECUTIVE_CLEANUP_SAMPLES",
                    "\" callbackBeforeReceiptRead=true\"",
                    "\" receiptCreatedBeforeCallback=true\"",
                    "requireSameProcessIdentity(",
                ),
            )

            val cleanupKeySuffixes = listOf(
                "enabled",
                "pre_ack_available",
                "pre_ack_request_id",
                "pre_ack_job_token_sha256",
                "pre_ack_input_target_sha256",
                "pre_ack_output_target_sha256",
                "pre_ack_input_exact_target_fd_count",
                "pre_ack_output_exact_target_fd_count",
                "pre_ack_active_lease",
                "pre_ack_process_pid",
                "pre_ack_process_start_time_ticks",
                "pre_ack_process_name",
                "pre_ack_process_epoch_sha256",
                "receipt_available",
                "receipt_request_id",
                "receipt_job_token_sha256",
                "receipt_snapshot_available",
                "receipt_active_lease_released",
                "receipt_input_exact_target_fd_count",
                "receipt_output_exact_target_fd_count",
                "receipt_private_input_named_file_count",
                "receipt_private_input_fd_count",
                "receipt_project_spool_named_file_count",
                "receipt_project_spool_fd_count",
                "receipt_private_output_named_file_count",
                "receipt_private_output_fd_count",
                "receipt_cursor_state",
                "receipt_cursor_opened_count",
                "receipt_cursor_closed_count",
                "receipt_cursor_released",
                "receipt_created_before_callback",
                "receipt_process_pid",
                "receipt_process_start_time_ticks",
                "receipt_process_name",
                "receipt_process_epoch_sha256",
                "current_available",
                "current_active_lease_count",
                "current_input_exact_target_fd_count",
                "current_output_exact_target_fd_count",
                "current_private_input_named_file_count",
                "current_private_input_fd_count",
                "current_project_spool_named_file_count",
                "current_project_spool_fd_count",
                "current_private_output_named_file_count",
                "current_private_output_fd_count",
                "current_process_pid",
                "current_process_start_time_ticks",
                "current_process_name",
                "current_process_epoch_sha256",
            )
            val cleanupEvidence =
                r2ExternalPlusOneCleanupEvidenceSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "bounded remote cleanup snapshot",
                source = cleanupEvidence,
                markers = listOf(
                    "R2ExternalPlusOneCleanupEvidenceProtocol",
                    "__autojs6_r2_external_plus_one_cleanup_",
                    "R2ExternalPlusOneCleanupSafeProcessIdentity",
                    "autojs6-r2-external-plus-one-cleanup-epoch-v1",
                    "R2Digests.sha256(",
                    "identity.nonce",
                    "capturePreAcknowledgement(",
                    "captureTerminalReceiptBeforeCallback(",
                    "captureCurrentSnapshot(",
                    "R2ExternalPlusOneCleanupFdEnumeration",
                    "NOT_CREATED",
                    "RELEASED",
                    "UNPROVEN",
                    "R2PrivateTransientOrphanScavenger.recognizedKind(",
                    "R2PrivateTransientKind.PRIVATE_INPUT",
                    "R2PrivateTransientKind.PROJECT_SPOOL",
                    "R2PrivateTransientKind.PRIVATE_OUTPUT",
                    "DELETED_FD_TARGET_SUFFIX",
                ),
            )
            cleanupKeySuffixes.forEach { suffix ->
                check(
                    "\"__autojs6_r2_external_plus_one_cleanup_$suffix\"" in
                        cleanupEvidence &&
                        "\"$suffix\"" in instrumentation,
                ) {
                    "R2-P3b-b2f runtime/fixture cleanup key drifted: $suffix"
                }
            }
            requireMarkers(
                label = "shared private transient namespaces",
                source = r2PrivateTransientOrphanScavengerSource.asFile
                    .readText(Charsets.UTF_8),
                markers = listOf(
                    "prefix = \"r2-private-input-\"",
                    "suffix = \".staging\"",
                    "prefix = \"r2-project-vfs-\"",
                    "suffix = \".spool\"",
                    "prefix = \"r2-private-output-\"",
                    "suffix = \".zip.staging\"",
                ),
            )
            check(
                "BuildConfig.DEBUG" !in cleanupEvidence &&
                    "R2CompilerProcessSupervisorProtocol.KEY_PROCESS_NONCE" !in cleanupEvidence &&
                    "identity.putInto(" !in cleanupEvidence &&
                    Regex(
                        """putString\([^)]*identity\.nonce""",
                        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                    ).find(cleanupEvidence) == null,
            ) {
                "R2-P3b-b2f cleanup telemetry must be evidence-build gated and must never export " +
                    "the raw compiler process nonce."
            }
            requireMarkers(
                label = "safe exact fixture identity",
                source = instrumentation,
                markers = listOf(
                    "private static final class CleanupProcessIdentity",
                    "boolean sameAs(CleanupProcessIdentity other)",
                    "pid == other.pid",
                    "startTicks == other.startTicks",
                    "processName.equals(other.processName)",
                    "epochSha256.equals(other.epochSha256)",
                    "(CURRENT_PACKAGE + \":typescript_compiler\")",
                ),
            )

            val callerPolicy =
                r2ExternalCapacityEvidenceProtocolSource.asFile
                    .readText(Charsets.UTF_8)
            requireMarkers(
                label = "exact non-shipping evidence caller",
                source = callerPolicy,
                markers = listOf(
                    "cleanupEvidenceControlsOrNull(",
                    "externalCleanupEvidenceEnabled = true",
                    "attestation.evidenceBuildEnabled",
                    "attestation.callingUid != attestation.applicationUid",
                    "attestation.fixtureUid == attestation.callingUid",
                    "attestation.callingPackages == setOf(FIXTURE_PACKAGE)",
                    "attestation.applicationTestOnly",
                    "attestation.fixtureTestOnly",
                    "attestation.signaturesMatch",
                ),
            )
            check("BuildConfig.DEBUG" !in callerPolicy) {
                "R2-P3b-b2f exact external caller authority must not depend on DEBUG."
            }

            val service =
                r2CompilerServiceSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "Binder-side evidence authorization and publication",
                source = service,
                markers = listOf(
                    "BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE",
                    "packageManager.getPackagesForUid(callingUid)",
                    "applicationInfo.flags and",
                    "ApplicationInfo.FLAG_TEST_ONLY",
                    "fixtureApplication.flags and",
                    "packageManager.checkSignatures(",
                    "PackageManager.SIGNATURE_MATCH",
                    "cleanupEvidenceControlsOrNull(",
                    "externalPlusOneCleanupEvidenceState()",
                    "?.putInto(this)",
                ),
            )
            check(
                !Regex(
                    """BuildConfig\.DEBUG\s*&&\s*""" +
                        """BuildConfig\.R2_EXTERNAL_CAPACITY_EVIDENCE""",
                ).containsMatchIn(service) &&
                    !Regex(
                        """BuildConfig\.R2_EXTERNAL_CAPACITY_EVIDENCE\s*&&\s*""" +
                            """BuildConfig\.DEBUG""",
                    ).containsMatchIn(service),
            ) {
                "R2-P3b-b2f external cleanup telemetry must not require a debuggable process."
            }

            val coordinator =
                r2CompilationCoordinatorSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "terminal cleanup receipt",
                source = coordinator,
                markers = listOf(
                    "capturePreAcknowledgement(",
                    "captureTerminalReceiptBeforeCallback(",
                    "terminalCallback.onCompleted(",
                ),
            )
            val receiptCapture =
                coordinator.indexOf("captureTerminalReceiptBeforeCallback(")
            val callbackPublication =
                coordinator.indexOf("terminalCallback.onCompleted(", receiptCapture)
            check(receiptCapture >= 0 && callbackPublication > receiptCapture) {
                "R2-P3b-b2f must publish its immutable cleanup receipt before invoking the " +
                    "external terminal callback."
            }
            val evidenceStateStart =
                coordinator.indexOf("internal fun externalPlusOneCleanupEvidenceState()")
            val admissionStart = coordinator.indexOf("fun admit(", evidenceStateStart)
            val acknowledgementStart = coordinator.indexOf("fun acknowledge(", admissionStart)
            check(
                evidenceStateStart >= 0 &&
                    admissionStart > evidenceStateStart &&
                    acknowledgementStart > admissionStart,
            ) {
                "R2-P3b-b2f could not locate the evidence-state and admission boundaries."
            }
            val evidenceStateMethod =
                coordinator.substring(evidenceStateStart, admissionStart)
            val admissionMethod =
                coordinator.substring(admissionStart, acknowledgementStart)
            requireMarkers(
                label = "post-transaction pre-ACK descriptor snapshot",
                source = evidenceStateMethod,
                markers = listOf(
                    "!job.callbackArmed",
                    "R2CoordinatorPhase.RESERVED_UNACKNOWLEDGED",
                    "admissionArbiter.isCurrent(job.lease)",
                    "capturePreAcknowledgement(",
                    "inputFd = job.inputArchive?.fd ?: -1",
                    "outputFd = job.outputArchive?.fd ?: -1",
                    "candidate?.takeIf",
                    "it.exactPostTransactionOwnershipProven",
                ),
            )
            check(
                "lastExternalCleanupPreAcknowledgement = null" in admissionMethod &&
                    "capturePreAcknowledgement(" !in admissionMethod,
            ) {
                "R2-P3b-b2f must defer exact FD enumeration until the synchronous Binder " +
                    "admission transaction has returned, while the ticket is still unacknowledged."
            }

            val metadata =
                r2PluginMetadataSource.asFile.readText(Charsets.UTF_8)
            requireDisabledCompilerCapabilityAdvertisement(
                label = "R2-P3b-b2f",
                metadata = metadata,
            )
            val productionSnapshot =
                r2ProductionSnapshotSource.asFile.readText(Charsets.UTF_8)
            val publicR2Contract =
                typeScriptR2ContractSource.asFile.readText(Charsets.UTF_8)
            check(
                "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES" in
                    productionSnapshot &&
                    ("const val MAX_ORDINARY_COMPILATION_INPUT_BYTES = " +
                        "8L * 1024L * 1024L") in publicR2Contract,
            ) {
                "R2-P3b-b2f must preserve the public ordinary compilation 8 MiB fence."
            }
        }
    }

val verifyR2P3bB2fExternalPlusOneCleanupApks =
    tasks.register("verifyR2P3bB2fExternalPlusOneCleanupApks") {
        group = "verification"
        description =
            "Verifies b2f markers in both fixture and both evidence APKs, shipping-release " +
                "cleanup-key exclusion, and the minified release-equivalent mapping."
        dependsOn(verifyR2P3bB2fExternalPlusOneCleanupSourcePolicy)
        dependsOn(verifyR2P3bB2cExternalApks)
        dependsOn(verifyR2P3bB2dExternalExact64Apks)
        dependsOn(
            ":app:assembleDebug",
            ":app:assembleRelease",
            ":app:assembleR2CapacityEvidenceDebug",
            ":app:assembleR2CapacityEvidenceRelease",
            "assembleDebug",
            "assembleRelease",
        )

        doLast {
            fun dexBytes(apkFile: File): List<ByteArray> =
                ZipFile(apkFile).use { apk ->
                    apk.entries().asSequence()
                        .filter { entry ->
                            !entry.isDirectory &&
                                entry.name.matches(
                                    Regex("""classes(?:\d+)?\.dex"""),
                                )
                        }
                        .map { entry ->
                            apk.getInputStream(entry).use { it.readBytes() }
                        }
                        .toList()
                }

            fun containsMarker(
                dex: List<ByteArray>,
                marker: String,
            ): Boolean {
                val bytes = marker.toByteArray(Charsets.UTF_8)
                return dex.any { it.indexOfSubsequence(bytes) >= 0 }
            }

            val fixtureMarkers = listOf(
                "externalPlusOneCleanupEvidence",
                "runExternalPlusOneCleanupEvidence",
                "runFencePlusOneCleanupCase",
                "runExternalCapacityPlusOneCleanupCase",
                "STORED_CONTAINER_PLUS_ONE",
                "DEFLATED_UNCOMPRESSED_PLUS_ONE",
                "runFenceInclusiveCleanupSuccessor",
                "ExternalPlusOneCleanupTelemetry",
                "Expected exactly one public R2 terminal callback",
                "event=external-plus-one-cleanup-pass",
            )
            listOf("debug", "release").forEach { variant ->
                val apkFile = singleVariantApk(project, variant)
                val dex = dexBytes(apkFile)
                check(dex.isNotEmpty()) {
                    "R2-P3b-b2f $variant fixture APK contains no DEX."
                }
                fixtureMarkers.forEach { marker ->
                    check(containsMarker(dex, marker)) {
                        "R2-P3b-b2f $variant fixture APK is missing: $marker"
                    }
                }
            }

            val cleanupPrefix =
                "__autojs6_r2_external_plus_one_cleanup_"
            val evidenceMarkers = listOf(
                "${cleanupPrefix}enabled",
                "${cleanupPrefix}pre_ack_request_id",
                "${cleanupPrefix}pre_ack_job_token_sha256",
                "${cleanupPrefix}pre_ack_input_exact_target_fd_count",
                "${cleanupPrefix}pre_ack_output_exact_target_fd_count",
                "${cleanupPrefix}pre_ack_active_lease",
                "${cleanupPrefix}receipt_request_id",
                "${cleanupPrefix}receipt_job_token_sha256",
                "${cleanupPrefix}receipt_active_lease_released",
                "${cleanupPrefix}receipt_private_input_named_file_count",
                "${cleanupPrefix}receipt_project_spool_fd_count",
                "${cleanupPrefix}receipt_private_output_fd_count",
                "${cleanupPrefix}receipt_cursor_state",
                "${cleanupPrefix}receipt_created_before_callback",
                "${cleanupPrefix}receipt_process_epoch_sha256",
                "${cleanupPrefix}current_active_lease_count",
                "${cleanupPrefix}current_input_exact_target_fd_count",
                "${cleanupPrefix}current_output_exact_target_fd_count",
                "${cleanupPrefix}current_private_input_fd_count",
                "${cleanupPrefix}current_project_spool_named_file_count",
                "${cleanupPrefix}current_private_output_named_file_count",
                "${cleanupPrefix}current_process_pid",
                "${cleanupPrefix}current_process_start_time_ticks",
                "${cleanupPrefix}current_process_name",
                "${cleanupPrefix}current_process_epoch_sha256",
            )
            listOf(
                "r2CapacityEvidenceDebug",
                "r2CapacityEvidenceRelease",
            ).forEach { variant ->
                val apkFile = singleVariantApk(project(":app"), variant)
                val dex = dexBytes(apkFile)
                check(dex.isNotEmpty()) {
                    "R2-P3b-b2f $variant evidence APK contains no DEX."
                }
                evidenceMarkers.forEach { marker ->
                    check(containsMarker(dex, marker)) {
                        "R2-P3b-b2f $variant evidence APK is missing: $marker"
                    }
                }
            }

            val normalRelease =
                singleVariantApk(project(":app"), "release")
            val normalReleaseDex = dexBytes(normalRelease)
            listOf(
                cleanupPrefix,
                "${cleanupPrefix}pre_ack_request_id",
                "${cleanupPrefix}receipt_created_before_callback",
                "${cleanupPrefix}current_process_epoch_sha256",
            ).forEach { marker ->
                check(!containsMarker(normalReleaseDex, marker)) {
                    "Shipping release APK retained external cleanup evidence key: $marker"
                }
            }

            val mappingFile =
                project(":app").layout.buildDirectory.file(
                    "outputs/mapping/r2CapacityEvidenceRelease/mapping.txt",
                ).get().asFile
            check(mappingFile.isFile && mappingFile.length() > 0L) {
                "R2-P3b-b2f minified evidence release-equivalent mapping is missing."
            }
        }
    }

val verifyR2P3bB2kExternalNaturalEmitTimeoutSourcePolicy =
    tasks.register("verifyR2P3bB2kExternalNaturalEmitTimeoutSourcePolicy") {
        group = "verification"
        description =
            "Statically verifies the R2-P3b-b2k external Binder natural emit-timeout " +
                "workload, strict request-start deadline calibration, pre-callback phase " +
                "receipt, cleanup, and same-process successor authority."
        dependsOn(verifyR2P3bB2fExternalPlusOneCleanupSourcePolicy)
        inputs.files(
            r2PublicPreviewInstrumentationSource,
            r2ExternalNaturalEmitTimeoutProjectFixtureSource,
            r2ExternalCapacityEvidenceProtocolSource,
            r2ExternalPlusOneCleanupEvidenceSource,
            r2ExternalNaturalPhaseTimeoutEvidenceSource,
            r2CompilationCoordinatorSource,
            r2CompilerServiceSource,
            r2CompilerHostSource,
            r2ProductionSnapshotSource,
            typeScriptR2ContractSource,
            r2PluginMetadataSource,
            appBuildScript,
        )

        doLast {
            fun requireMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-P3b-b2k $label is missing source marker: $marker"
                    }
                }
            }

            fun javaMethod(
                source: String,
                signature: String,
            ): String {
                val start = source.indexOf(signature)
                check(start >= 0) {
                    "R2-P3b-b2k fixture method is missing: $signature"
                }
                val end = source.indexOf("\n    private ", start + signature.length)
                    .takeIf { index -> index >= 0 }
                    ?: source.length
                return source.substring(start, end)
            }

            val instrumentation =
                r2PublicPreviewInstrumentationSource.asFile
                    .readText(Charsets.UTF_8)
            val workloadFixture =
                r2ExternalNaturalEmitTimeoutProjectFixtureSource.asFile
                    .readText(Charsets.UTF_8)
            val phaseEvidence =
                r2ExternalNaturalPhaseTimeoutEvidenceSource.asFile
                    .readText(Charsets.UTF_8)
            val coordinator =
                r2CompilationCoordinatorSource.asFile.readText(Charsets.UTF_8)
            val service =
                r2CompilerServiceSource.asFile.readText(Charsets.UTF_8)
            val compilerHost =
                r2CompilerHostSource.asFile.readText(Charsets.UTF_8)

            requireMarkers(
                label = "exclusive ordinary external mode",
                source = instrumentation,
                markers = listOf(
                    "ARG_EXTERNAL_NATURAL_EMIT_TIMEOUT_EVIDENCE",
                    "\"externalNaturalEmitTimeoutEvidence\"",
                    "externalNaturalEmitTimeoutEvidenceRequested",
                    "External natural emit timeout evidence must run in its exclusive ",
                    "runExternalNaturalEmitTimeoutEvidence()",
                    "runExternalNaturalEmitTimeoutMatrix(",
                    "two successful preconditions, three successful ",
                    "one fixed-deadline emit fault, ",
                    "same-Binder/process successor recovery",
                ),
            )

            requireMarkers(
                label = "frozen natural workload authority",
                source = workloadFixture,
                markers = listOf(
                    "static final int ROOT_COUNT = 256;",
                    "static final int LIGHT_ROOT_COUNT = 64;",
                    "static final int HEAVY_ROOT_COUNT = 192;",
                    "static final int OPTIONAL_CHAIN_COUNT_PER_HEAVY_ROOT = 1;",
                    "static final int OPTIONAL_CHAIN_DEPTH = 61;",
                    "static final int TYPE_ASSERTION_GROUP_COUNT_PER_HEAVY_ROOT = 4;",
                    "static final int TYPE_ASSERTION_COUNT_PER_GROUP = 90;",
                    "static final long NATURAL_ROOT_BYTES = 555_766L;",
                    "static final int NATURAL_ROOT_TOKENS = 168_896;",
                    "static final long NATURAL_MAXIMUM_ROOT_BYTES = 2_884L;",
                    "static final int NATURAL_MAXIMUM_ROOT_TOKENS = 877;",
                    "static final int NATURAL_CALLER_FILE_COUNT = 256;",
                    "static final long NATURAL_CALLER_BYTES = 555_766L;",
                    "static final int NATURAL_TOTAL_FILE_COUNT = 364;",
                    "static final long NATURAL_TOTAL_BYTES = 4_340_524L;",
                    "static final int NATURAL_TOTAL_TOKENS = 255_225;",
                    "static final int NATURAL_TOTAL_PATH_CHARACTERS = 13_073;",
                    "static final int NATURAL_INPUT_TRANSPORT_FRAME_COUNT = 1_144;",
                    "\"7cd2119c93657dbf2a8709e67d8045331b1d444f0e3b3e6a3831e1a712ce187e\"",
                    "source.append(\"?.p\")",
                    ".append(Integer.toString(depthIndex, 36));",
                    "source.append(\" as any\");",
                    "static final OutputAuthority NATURAL_OUTPUT_AUTHORITY =",
                    "new OutputAuthority(",
                    "512,",
                    "717_302L,",
                    "259_136L,",
                    "976_438L,",
                    "\"2b2a4529552aa3a3f9b33cd2a379240bc2852ca065a33ff884a6160847f1b1de\"",
                    "static final OutputAuthority SUCCESSOR_OUTPUT_AUTHORITY =",
                    "166L,",
                    "146L,",
                    "312L,",
                    "\"0b504753f68a345d6aa82eeb174f6d9c63eec3cb21b7810ed09f69e59296be25\"",
                    "ORDINARY_MAXIMUM_FILES =",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES",
                    "ORDINARY_MAXIMUM_TOTAL_BYTES =",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES",
                    "ORDINARY_MAXIMUM_FILE_BYTES =",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES",
                    "WORKER_MAXIMUM_PROJECT_TOKENS = 262_144",
                    "WORKER_MAXIMUM_TOKENS_PER_FILE = 65_536",
                    "MAXIMUM_OUTPUT_BYTES = 1024L * 1024L",
                    "There is no",
                    "phase hook",
                    "artificial spin",
                    "capacity override",
                    "expanded admission",
                ),
            )

            val matrix = javaMethod(
                instrumentation,
                "private static void runExternalNaturalEmitTimeoutMatrix(",
            )
            requireMarkers(
                label = "two-plus-three-plus-one evidence matrix",
                source = matrix,
                markers = listOf(
                    "NATURAL_EMIT_PRECONDITION_RUNS == 2",
                    "NATURAL_EMIT_BASELINE_RUNS == 3",
                    "NATURAL_EMIT_FAULT_ATTEMPTS == 1",
                    "for (int run = 1; run <= NATURAL_EMIT_PRECONDITION_RUNS; run++)",
                    "for (int run = 1; run <= NATURAL_EMIT_BASELINE_RUNS; run++)",
                    "calibrateExternalNaturalEmitDeadline(baselineTimings)",
                    "runExternalNaturalEmitFault(",
                    "external-natural-emit-successor",
                    "EXTERNAL_NATURAL_EMIT_TIMEOUT_MATRIX_PASS",
                    "ordinaryAdmission=true expandedWorkerCapacity=false",
                    "requestStartAbsoluteDeadline=true sameBinder=true",
                    "sameProcessEpoch=true zeroSamples=",
                ),
            )
            val precondition = matrix.indexOf("NATURAL_EMIT_PRECONDITION_RUNS")
            val baseline = matrix.indexOf("NATURAL_EMIT_BASELINE_RUNS", precondition + 1)
            val calibration =
                matrix.indexOf("calibrateExternalNaturalEmitDeadline(", baseline + 1)
            val fault = matrix.indexOf("runExternalNaturalEmitFault(", calibration + 1)
            val successor = matrix.indexOf("external-natural-emit-successor", fault + 1)
            check(
                precondition >= 0 &&
                    baseline > precondition &&
                    calibration > baseline &&
                    fault > calibration &&
                    successor > fault,
            ) {
                "R2-P3b-b2k evidence must calibrate only after its preconditions and three " +
                    "baselines, then make exactly one fault attempt and one successor request."
            }

            val calibrationMethod = javaMethod(
                instrumentation,
                "private static NaturalEmitCalibration",
            )
            requireMarkers(
                label = "strict request-start interval intersection",
                source = calibrationMethod,
                markers = listOf(
                    "baselineTimings.size() == NATURAL_EMIT_BASELINE_RUNS",
                    "timing.sequenceTwoNanosSinceRequestStart",
                    "timing.completedNanosSinceRequestStart",
                    "maximumTimeoutMillis - minimumTimeoutMillis",
                    "NATURAL_EMIT_MINIMUM_MARGIN_MS * 2L",
                    "(maximumTimeoutMillis - minimumTimeoutMillis) / 2L",
                    "TypeScriptR2Contract",
                    ".MIN_PUBLIC_COMPILATION_TIMEOUT_MS",
                    "timeoutMillis - minimumTimeoutMillis",
                    "maximumTimeoutMillis - timeoutMillis",
                    "timing.sequenceTwoNanosSinceRequestStart",
                    "< timeoutNanos",
                    "timeoutNanos",
                    "< timing.completedNanosSinceRequestStart",
                    "strictIntersection=true faultAttempts=0",
                ),
            )
            listOf(
                "SystemClock.sleep(",
                "Thread.sleep(",
                "while (",
                "Math.max(",
                "Math.min(",
                "coerceAtLeast(",
                "coerceAtMost(",
                "EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY",
                "STORED_CONTAINER_EXACT_64_SCOPE",
                "DEFLATED_EXPANDED_EXACT_64_SCOPE",
            ).forEach { forbidden ->
                check(forbidden !in calibrationMethod) {
                    "R2-P3b-b2k calibration must not retry, spin, clamp, or acquire " +
                        "capacity scope: $forbidden"
                }
            }

            val oneShotAdmission = javaMethod(
                instrumentation,
                "private static Admission admitNaturalEmitOnce(",
            )
            requireMarkers(
                label = "one-shot ordinary public admission",
                source = oneShotAdmission,
                markers = listOf(
                    "requireLiveSameBinder(session)",
                    ".MIN_PUBLIC_COMPILATION_TIMEOUT_MS",
                    "compiler.startCompilation(",
                    "publicRequest(",
                    "null,",
                    "timeoutMs)",
                    "Natural emit request was rejected without retry:",
                ),
            )
            listOf(
                "while (",
                "for (",
                "SystemClock.sleep(",
                "Thread.sleep(",
                "RETRY_DELAY_MS",
                "EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY",
                "STORED_CONTAINER_EXACT_64_SCOPE",
                "DEFLATED_EXPANDED_EXACT_64_SCOPE",
                "expandedWorkerCapacity",
                "workerSpin",
            ).forEach { forbidden ->
                check(forbidden !in oneShotAdmission) {
                    "R2-P3b-b2k natural fault admission must remain a single ordinary " +
                        "public attempt: $forbidden"
                }
            }

            val successfulRun = javaMethod(
                instrumentation,
                "private static NaturalEmitTiming runExternalNaturalEmitSuccessfulRun(",
            )
            requireMarkers(
                label = "successful baseline authority and cleanup",
                source = successfulRun,
                markers = listOf(
                    "TypeScriptCompilerContract.MAX_TIMEOUT_MS",
                    "assertPreAcknowledgementCleanupTelemetry(",
                    "acknowledgeAdmission(admission)",
                    "callback.awaitTerminal(CALLBACK_WAIT_MS)",
                    "callback.assertExactlyOneCompletion()",
                    "TypeScriptCompilerContract.OUTCOME_EMITTED",
                    "verifyExternalNaturalEmitOutput(",
                    "if (successor)",
                    ".SUCCESSOR_TOTAL_FILE_COUNT",
                    "phaseReceiptRequired=false sameBinder=true",
                    "sameProcessEpoch=true",
                    "requireNaturalPhaseReceipt(",
                    "assertSuccessfulNaturalPhaseReceipt(",
                    "awaitCleanupEvidence(",
                    "cursorOpenedCount",
                    ".NATURAL_TOTAL_FILE_COUNT",
                    "cursorClosedCount",
                ),
            )
            val successfulPreAck =
                successfulRun.indexOf("assertPreAcknowledgementCleanupTelemetry(")
            val successfulAck =
                successfulRun.indexOf("acknowledgeAdmission(", successfulPreAck)
            val successfulCallback =
                successfulRun.indexOf("callback.awaitTerminal(", successfulAck)
            val successfulPhaseReceipt =
                successfulRun.indexOf("requireNaturalPhaseReceipt(", successfulCallback)
            val successfulCleanup =
                successfulRun.indexOf("awaitCleanupEvidence(", successfulPhaseReceipt)
            check(
                successfulPreAck >= 0 &&
                    successfulAck > successfulPreAck &&
                    successfulCallback > successfulAck &&
                    successfulPhaseReceipt > successfulCallback &&
                    successfulCleanup > successfulPhaseReceipt,
            ) {
                "R2-P3b-b2k successful runs must prove pre-ACK ownership, ACK once, " +
                    "receive one callback, then read phase and cleanup receipts."
            }

            val faultMethod = javaMethod(
                instrumentation,
                "private static void runExternalNaturalEmitFault(",
            )
            requireMarkers(
                label = "single natural emit timeout fault",
                source = faultMethod,
                markers = listOf(
                    "NATURAL_EMIT_FAULT_ATTEMPTS == 1",
                    "calibration.timeoutMillis",
                    "callback.assertExactlyOneCompletion()",
                    "TypeScriptCompilerContract.OUTCOME_TIMED_OUT",
                    "TypeScriptCompilerContract.ERROR_TIMEOUT",
                    "assertNoCompilationPayload(",
                    "assertSyncedEmptyOutput(",
                    "assertTimedOutNaturalPhaseReceipt(",
                    "awaitCleanupEvidence(",
                    "NATURAL_TOTAL_FILE_COUNT",
                    "termination.progressSequence",
                    "termination.terminatedWorkerGeneration",
                    "termination.replacementWorkerGeneration",
                    "callbackBeforeReceiptRead=true",
                    "receiptCreatedBeforeCallback=true",
                    "EXTERNAL_NATURAL_EMIT_TIMEOUT_FAULT_PASS",
                ),
            )
            check(
                Regex("""admitNaturalEmitOnce\(""").findAll(faultMethod).count() == 1 &&
                    "while (" !in faultMethod &&
                    "SystemClock.sleep(Math.min(" !in faultMethod &&
                    "RETRY_DELAY_MS" !in faultMethod,
            ) {
                "R2-P3b-b2k fault must have exactly one irreversible admission attempt."
            }

            val terminationAssertion = javaMethod(
                instrumentation,
                "private static void assertTimedOutNaturalPhaseReceipt(",
            )
            requireMarkers(
                label = "natural emit phase-location authority",
                source = terminationAssertion,
                markers = listOf(
                    "new String[] {\"parse\", \"semantic\"}",
                    "\"TIMED_OUT\".equals(termination.stopCause)",
                    "\"emit\".equals(termination.hostStopPhase)",
                    "\"emit\".equals(termination.pageTerminationPhase)",
                    "\"ingress\", \"parse\", \"semantic\", \"emit\"",
                    "\"ingress\", \"parse\", \"semantic\"",
                    "termination.progressSequence == 2",
                    ".NATURAL_OUTPUT_AUTHORITY.fileCount",
                    "- 1)",
                    "/ 4",
                    "+ 1",
                    "termination.replacementWorkerGeneration",
                    "== termination.terminatedWorkerGeneration + 1",
                    "termination.deadlineNanosSinceRequestStart",
                    "NATURAL_EMIT_MAXIMUM_OVERSHOOT_MS",
                ),
            )

            requireMarkers(
                label = "private immutable phase receipt protocol",
                source = phaseEvidence,
                markers = listOf(
                    "R2ExternalNaturalPhaseTimeoutEvidenceProtocol",
                    "__autojs6_r2_external_natural_phase_timeout_",
                    "KEY_ENABLED",
                    "KEY_RECEIPT_AVAILABLE",
                    "KEY_REQUEST_ID",
                    "KEY_HOST_REQUEST_ID",
                    "KEY_OUTCOME",
                    "KEY_CREATED_BEFORE_CALLBACK",
                    "KEY_PROJECT_START_NANOS_SINCE_REQUEST_START",
                    "KEY_PHASE_VALUES",
                    "KEY_PHASE_SEQUENCE_TWO_NANOS_SINCE_REQUEST_START",
                    "KEY_PHASE_COMPLETED_NANOS_SINCE_REQUEST_START",
                    "KEY_TERMINATION_STOP_CAUSE",
                    "KEY_TERMINATION_PHASE_AT_HOST_STOP_SELECTION",
                    "KEY_TERMINATION_PHASE_AT_PAGE_TERMINATION",
                    "KEY_TERMINATION_DEADLINE_NANOS_SINCE_REQUEST_START",
                    "KEY_TERMINATION_DEADLINE_OVERSHOOT_NANOS",
                    "KEY_TERMINATION_PROGRESS_SEQUENCE",
                    "KEY_TERMINATION_TERMINATED_WORKER_GENERATION",
                    "KEY_TERMINATION_REPLACEMENT_WORKER_GENERATION",
                    "KEY_INPUT_SENT_FRAME_COUNT",
                    "KEY_INPUT_TOTAL_PAYLOAD_BYTES",
                    "KEY_INPUT_OPENED_CURSOR_COUNT",
                    "KEY_INPUT_CLOSED_CURSOR_COUNT",
                    "KEY_INPUT_ACTIVE_CURSOR",
                    "KEY_INPUT_RELEASED",
                    "SUCCESS(\"success\")",
                    "TIMED_OUT(\"timed_out\")",
                    "Collections.unmodifiableList(",
                ),
            )
            check(
                "BuildConfig.DEBUG" !in phaseEvidence &&
                    !Regex(
                        """(?:if|check)\s*\(\s*BuildConfig\.""",
                    ).containsMatchIn(phaseEvidence),
            ) {
                "R2-P3b-b2k receipt values must be inert data; exact-caller build gating " +
                    "belongs at the service/coordinator boundary."
            }

            requireMarkers(
                label = "evidence-build host snapshot",
                source = compilerHost,
                markers = listOf(
                    "internal fun lastProjectCompilationEvidenceSnapshot():",
                    "check(BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE)",
                    "R2ProjectCompilationEvidenceSnapshot(",
                    "projectStartedAtElapsedRealtimeNanos",
                    "phaseTimingEvidence",
                    "phaseTerminationEvidence",
                    "inputTransportTelemetry",
                ),
            )

            val ordinaryCompilationBranchStart = coordinator.indexOf(
                "} else if (job.workerSpinMilliseconds == 0) {",
            )
            val ordinaryCompilationBranchEnd = coordinator.indexOf(
                "\n            } else {",
                ordinaryCompilationBranchStart + 1,
            )
            check(
                ordinaryCompilationBranchStart >= 0 &&
                    ordinaryCompilationBranchEnd > ordinaryCompilationBranchStart,
            ) {
                "R2-P3b-b2k could not isolate the ordinary coordinator compilation branch."
            }
            val ordinaryCompilationBranch = coordinator.substring(
                ordinaryCompilationBranchStart,
                ordinaryCompilationBranchEnd,
            )
            requireMarkers(
                label = "ordinary absolute-deadline coordinator seam",
                source = ordinaryCompilationBranch,
                markers = listOf(
                    "job.workerSpinMilliseconds == 0",
                    "compilerHost.compileProjectUntilElapsedRealtimeDeadline(",
                    "handle = job.hostHandle",
                    "snapshot = adapted.snapshot",
                    "deadlineAtElapsedRealtimeNanos =",
                    "job.deadlineAtElapsedRealtimeNanos",
                ),
            )
            listOf(
                "compileProject(",
                "timeoutMs = remaining",
                "TimeUnit.MILLISECONDS.toNanos(remaining)",
                "SystemClock.elapsedRealtimeNanos() +",
            ).forEach { forbidden ->
                check(forbidden !in ordinaryCompilationBranch) {
                    "R2-P3b-b2k ordinary coordinator compilation must forward the " +
                        "admission-time absolute deadline without rebuilding it: $forbidden"
                }
            }

            val absoluteHostMethodStart = compilerHost.indexOf(
                "internal fun compileProjectUntilElapsedRealtimeDeadline(",
            )
            val absoluteHostMethodEnd = compilerHost.indexOf(
                "\n    internal fun ",
                absoluteHostMethodStart + 1,
            )
            check(
                absoluteHostMethodStart >= 0 &&
                    absoluteHostMethodEnd > absoluteHostMethodStart,
            ) {
                "R2-P3b-b2k could not isolate the host absolute-deadline entry point."
            }
            val absoluteHostMethod = compilerHost.substring(
                absoluteHostMethodStart,
                absoluteHostMethodEnd,
            )
            requireMarkers(
                label = "host absolute-deadline forwarding seam",
                source = absoluteHostMethod,
                markers = listOf(
                    "deadlineAtElapsedRealtimeNanos: Long",
                    "deadlineAtElapsedRealtimeNanos -",
                    "SystemClock.elapsedRealtimeNanos()",
                    "return compileProject(",
                    "absoluteDeadlineAtElapsedRealtimeNanos =",
                    "deadlineAtElapsedRealtimeNanos",
                ),
            )
            check(
                "absoluteDeadlineAtElapsedRealtimeNanos =\n" +
                    "                deadlineAtElapsedRealtimeNanos" in
                    absoluteHostMethod &&
                    "absoluteDeadlineAtElapsedRealtimeNanos = Math.addExact" !in
                    absoluteHostMethod &&
                    "absoluteDeadlineAtElapsedRealtimeNanos =\n" +
                    "                Math.addExact" !in absoluteHostMethod,
            ) {
                "R2-P3b-b2k host entry point must pass the caller's exact elapsed-realtime " +
                    "deadline to private compile rather than reconstructing it from remaining ms."
            }

            val privateCompileStart = compilerHost.indexOf(
                "private fun compileProject(",
            )
            val privateCompileEnd = compilerHost.indexOf(
                "\n    private fun ",
                privateCompileStart + 1,
            )
            check(
                privateCompileStart >= 0 && privateCompileEnd > privateCompileStart,
            ) {
                "R2-P3b-b2k could not isolate the private host compilation boundary."
            }
            val privateCompileMethod = compilerHost.substring(
                privateCompileStart,
                privateCompileEnd,
            )
            requireMarkers(
                label = "private compile absolute-deadline preference",
                source = privateCompileMethod,
                markers = listOf(
                    "absoluteDeadlineAtElapsedRealtimeNanos: Long? = null",
                    "val deadlineAtElapsedRealtimeNanos =",
                    "absoluteDeadlineAtElapsedRealtimeNanos",
                    "?: Math.addExact(",
                    "startedAtElapsedRealtimeNanos",
                    "timeoutNanos",
                    "SystemClock.elapsedRealtimeNanos() >=",
                    "deadlineAtElapsedRealtimeNanos",
                ),
            )
            val exactDeadlinePreference = privateCompileMethod.indexOf(
                "absoluteDeadlineAtElapsedRealtimeNanos\n" +
                    "                ?: Math.addExact(",
            )
            check(exactDeadlinePreference >= 0) {
                "R2-P3b-b2k private compile must prefer the supplied absolute deadline; " +
                    "the timeout-derived value may only be its null fallback."
            }

            requireMarkers(
                label = "exact external Binder receipt publication",
                source = service,
                markers = listOf(
                    "if (BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE)",
                    "r2ExternalEvidenceAttestationOrNull(",
                    "R2ExternalCapacityEvidenceProtocol",
                    ".isExactEvidenceCaller(attestation)",
                    ".externalNaturalPhaseTimeoutEvidenceState()",
                    "?.putInto(this)",
                ),
            )
            check(
                !Regex(
                    """BuildConfig\.DEBUG\s*&&\s*""" +
                        """BuildConfig\.R2_EXTERNAL_CAPACITY_EVIDENCE""",
                ).containsMatchIn(service),
            ) {
                "R2-P3b-b2k minified release-equivalent receipt publication must not " +
                    "require a debuggable process."
            }

            requireMarkers(
                label = "request-start correlated receipt creation",
                source = coordinator,
                markers = listOf(
                    "job.externalCleanupEvidenceEnabled",
                    "compilerHost",
                    ".lastProjectCompilationEvidenceSnapshot()",
                    "lastExternalNaturalPhaseTimeoutReceipt =",
                    "createExternalNaturalPhaseTimeoutReceipt(",
                    "job.startedAtElapsedRealtimeNanos",
                    "projectStartedElapsedRealtimeNanosSinceRequestStart",
                    "createdBeforeCallback = true",
                    "R2ExternalNaturalPhaseTimingReceipt.from(",
                    "R2ExternalNaturalPhaseTerminationReceipt.from(",
                    "R2ExternalNaturalPhaseInputTelemetry.from(",
                    "terminalCallback.onCompleted(",
                ),
            )
            val receiptCapture =
                coordinator.indexOf(
                    "lastExternalNaturalPhaseTimeoutReceipt =",
                    coordinator.indexOf("captureTerminalReceiptBeforeCallback("),
                )
            val callbackPublication =
                coordinator.indexOf("terminalCallback.onCompleted(", receiptCapture)
            check(receiptCapture >= 0 && callbackPublication > receiptCapture) {
                "R2-P3b-b2k must freeze its correlated natural-phase receipt before " +
                    "publishing the external terminal callback."
            }

            val callerPolicy =
                r2ExternalCapacityEvidenceProtocolSource.asFile
                    .readText(Charsets.UTF_8)
            requireMarkers(
                label = "same-signer distinct-UID evidence caller",
                source = callerPolicy,
                markers = listOf(
                    "externalCleanupEvidenceEnabled = true",
                    "attestation.evidenceBuildEnabled",
                    "attestation.callingUid != attestation.applicationUid",
                    "attestation.fixtureUid == attestation.callingUid",
                    "attestation.callingPackages == setOf(FIXTURE_PACKAGE)",
                    "attestation.applicationTestOnly",
                    "attestation.fixtureTestOnly",
                    "attestation.signaturesMatch",
                ),
            )

            val appBuild = appBuildScript.asFile.readText(Charsets.UTF_8)
            check(
                Regex(
                    """buildConfigField\(\s*"boolean",\s*""" +
                        """"R2_EXTERNAL_CAPACITY_EVIDENCE",\s*"false"\s*\)""",
                    RegexOption.DOT_MATCHES_ALL,
                ).containsMatchIn(appBuild),
            ) {
                "R2-P3b-b2k normal builds must keep the evidence transport disabled."
            }
            val productionSnapshot =
                r2ProductionSnapshotSource.asFile.readText(Charsets.UTF_8)
            val publicR2Contract =
                typeScriptR2ContractSource.asFile.readText(Charsets.UTF_8)
            check(
                "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES" in
                    productionSnapshot &&
                    ("const val MAX_ORDINARY_COMPILATION_INPUT_BYTES = " +
                        "8L * 1024L * 1024L") in publicR2Contract,
            ) {
                "R2-P3b-b2k must preserve the public ordinary compilation 8 MiB fence."
            }
            val metadata =
                r2PluginMetadataSource.asFile.readText(Charsets.UTF_8)
            requireDisabledCompilerCapabilityAdvertisement(
                label = "R2-P3b-b2k",
                metadata = metadata,
            )
        }
    }

val verifyR2P3bB2kExternalNaturalEmitTimeoutApks =
    tasks.register("verifyR2P3bB2kExternalNaturalEmitTimeoutApks") {
        group = "verification"
        description =
            "Verifies b2k external natural emit-timeout markers in both fixture and both " +
                "TEST_ONLY evidence APKs, their exclusion from normal Release, and the " +
                "minified release-equivalent mapping."
        dependsOn(verifyR2P3bB2kExternalNaturalEmitTimeoutSourcePolicy)
        dependsOn(verifyR2P3bB2fExternalPlusOneCleanupApks)
        dependsOn(verifyR2PublicPreviewApkManifestShape)
        dependsOn(verifyR2PublicPreviewSigningConfiguration)
        dependsOn(
            ":app:assembleDebug",
            ":app:assembleRelease",
            ":app:assembleR2CapacityEvidenceDebug",
            ":app:assembleR2CapacityEvidenceRelease",
            "assembleDebug",
            "assembleRelease",
        )

        doLast {
            fun dexBytes(apkFile: File): List<ByteArray> =
                ZipFile(apkFile).use { apk ->
                    apk.entries().asSequence()
                        .filter { entry ->
                            !entry.isDirectory &&
                                entry.name.matches(
                                    Regex("""classes(?:\d+)?\.dex"""),
                                )
                        }
                        .map { entry ->
                            apk.getInputStream(entry).use { it.readBytes() }
                        }
                        .toList()
                }

            fun containsMarker(
                dex: List<ByteArray>,
                marker: String,
            ): Boolean {
                val bytes = marker.toByteArray(Charsets.UTF_8)
                return dex.any { it.indexOfSubsequence(bytes) >= 0 }
            }

            val phasePrefix =
                "__autojs6_r2_external_natural_phase_timeout_"
            val fixtureMarkers = listOf(
                "externalNaturalEmitTimeoutEvidence",
                "R2ExternalNaturalEmitTimeoutProjectFixture",
                phasePrefix,
                "EXTERNAL_NATURAL_EMIT_TIMEOUT_PRECONDITION_PASS",
                "EXTERNAL_NATURAL_EMIT_TIMEOUT_BASELINE_PASS",
                "EXTERNAL_NATURAL_EMIT_TIMEOUT_WINDOW_PASS",
                "EXTERNAL_NATURAL_EMIT_TIMEOUT_FAULT_PASS",
                "EXTERNAL_NATURAL_EMIT_TIMEOUT_SUCCESSOR_PASS",
                "EXTERNAL_NATURAL_EMIT_TIMEOUT_MATRIX_PASS",
                "strictIntersection=true faultAttempts=0",
                "Natural emit request was rejected without retry:",
                "2b2a4529552aa3a3f9b33cd2a379240bc2852ca065a33ff884a6160847f1b1de",
                "0b504753f68a345d6aa82eeb174f6d9c63eec3cb21b7810ed09f69e59296be25",
            )
            listOf("debug", "release").forEach { variant ->
                val apkFile = singleVariantApk(project, variant)
                val dex = dexBytes(apkFile)
                check(dex.isNotEmpty()) {
                    "R2-P3b-b2k $variant fixture APK contains no DEX."
                }
                fixtureMarkers.forEach { marker ->
                    check(containsMarker(dex, marker)) {
                        "R2-P3b-b2k $variant fixture APK is missing: $marker"
                    }
                }
            }

            val evidenceMarkers = listOf(
                "${phasePrefix}enabled",
                "${phasePrefix}receipt_available",
                "${phasePrefix}request_id",
                "${phasePrefix}host_request_id",
                "${phasePrefix}outcome",
                "${phasePrefix}created_before_callback",
                "${phasePrefix}project_start_nanos_since_request_start",
                "${phasePrefix}phase_values",
                "${phasePrefix}phase_sequence_two_nanos_since_request_start",
                "${phasePrefix}phase_completed_nanos_since_request_start",
                "${phasePrefix}termination_stop_cause",
                "${phasePrefix}termination_phase_at_host_stop_selection",
                "${phasePrefix}termination_phase_at_page_termination",
                "${phasePrefix}termination_deadline_nanos_since_request_start",
                "${phasePrefix}termination_deadline_overshoot_nanos",
                "${phasePrefix}termination_progress_sequence",
                "${phasePrefix}termination_terminated_worker_generation",
                "${phasePrefix}termination_replacement_worker_generation",
                "${phasePrefix}input_sent_frame_count",
                "${phasePrefix}input_total_payload_bytes",
                "${phasePrefix}input_opened_cursor_count",
                "${phasePrefix}input_closed_cursor_count",
                "${phasePrefix}input_active_cursor",
                "${phasePrefix}input_released",
            )
            listOf(
                "r2CapacityEvidenceDebug",
                "r2CapacityEvidenceRelease",
            ).forEach { variant ->
                val apkFile = singleVariantApk(project(":app"), variant)
                val dex = dexBytes(apkFile)
                check(dex.isNotEmpty()) {
                    "R2-P3b-b2k $variant evidence APK contains no DEX."
                }
                evidenceMarkers.forEach { marker ->
                    check(containsMarker(dex, marker)) {
                        "R2-P3b-b2k $variant evidence APK is missing: $marker"
                    }
                }
            }

            val normalRelease =
                singleVariantApk(project(":app"), "release")
            val normalReleaseDex = dexBytes(normalRelease)
            listOf(
                phasePrefix,
                "${phasePrefix}request_id",
                "${phasePrefix}created_before_callback",
                "${phasePrefix}termination_progress_sequence",
                "${phasePrefix}input_released",
            ).forEach { marker ->
                check(!containsMarker(normalReleaseDex, marker)) {
                    "Shipping release APK retained external natural-phase evidence key: $marker"
                }
            }

            val mappingFile =
                project(":app").layout.buildDirectory.file(
                    "outputs/mapping/r2CapacityEvidenceRelease/mapping.txt",
                ).get().asFile
            check(mappingFile.isFile && mappingFile.length() > 0L) {
                "R2-P3b-b2k minified evidence release-equivalent mapping is missing."
            }
        }
    }

val verifyR2ExternalNaturalSemanticTimeoutSourcePolicy =
    tasks.register("verifyR2ExternalNaturalSemanticTimeoutSourcePolicy") {
        group = "verification"
        description =
            "Statically verifies the external-UID Binder natural semantic-timeout " +
                "workload, strict request-start calibration, one-shot fault, cleanup, " +
                "and same-process successor authority."
        dependsOn(verifyR2P3bB2kExternalNaturalEmitTimeoutSourcePolicy)
        inputs.files(
            r2PublicPreviewInstrumentationSource,
            r2ExternalNaturalEmitTimeoutProjectFixtureSource,
            r2ExternalNaturalPhaseTimeoutEvidenceSource,
            r2ExternalCapacityEvidenceProtocolSource,
            r2ExternalPlusOneCleanupEvidenceSource,
            r2CompilationCoordinatorSource,
            r2CompilerServiceSource,
            r2PluginMetadataSource,
            r2ProductionSnapshotSource,
            typeScriptR2ContractSource,
            appBuildScript,
        )

        doLast {
            fun requireMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "External natural semantic-timeout $label is missing: $marker"
                    }
                }
            }

            fun javaMethod(
                source: String,
                signature: String,
            ): String {
                val start = source.indexOf(signature)
                check(start >= 0) {
                    "External natural semantic-timeout method is missing: $signature"
                }
                val end = source.indexOf("\n    private ", start + signature.length)
                    .takeIf { index -> index >= 0 }
                    ?: source.length
                return source.substring(start, end)
            }

            val instrumentation =
                r2PublicPreviewInstrumentationSource.asFile
                    .readText(Charsets.UTF_8)
            val workloadFixture =
                r2ExternalNaturalEmitTimeoutProjectFixtureSource.asFile
                    .readText(Charsets.UTF_8)

            requireMarkers(
                label = "exclusive external mode",
                source = instrumentation,
                markers = listOf(
                    "ARG_EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_EVIDENCE",
                    "\"externalNaturalSemanticTimeoutEvidence\"",
                    "externalNaturalSemanticTimeoutEvidenceRequested",
                    "External natural semantic timeout evidence must run in its exclusive ",
                    "runExternalNaturalSemanticTimeoutEvidence()",
                    "runExternalNaturalSemanticTimeoutMatrix(",
                    "one fixed-deadline semantic ",
                    "same-Binder/process successor recovery",
                ),
            )

            val evidenceEntry = javaMethod(
                instrumentation,
                "private void runExternalNaturalSemanticTimeoutEvidence(",
            )
            requireMarkers(
                label = "same-signer distinct-UID TEST_ONLY Binder entry",
                source = evidenceEntry,
                markers = listOf(
                    "Process.myUid() != currentApplication.uid",
                    "PackageManager.SIGNATURE_MATCH",
                    "ApplicationInfo.FLAG_TEST_ONLY",
                    "External natural semantic fixture is not TEST_ONLY.",
                    "External natural semantic compiler evidence build is not TEST_ONLY.",
                    "PermissionInfo.PROTECTION_SIGNATURE",
                    "serviceInfo.exported",
                    "PLUGIN_PERMISSION.equals(serviceInfo.permission)",
                    "awaitCleanupEvidenceSession(",
                    "runExternalNaturalSemanticTimeoutMatrix(",
                    "requireLiveSameBinder(session)",
                ),
            )

            requireMarkers(
                label = "frozen workload and output authority",
                source = workloadFixture,
                markers = listOf(
                    "static final int SEMANTIC_ROOT_COUNT = 256;",
                    "static final int SEMANTIC_LIGHT_ROOT_COUNT = 64;",
                    "static final int SEMANTIC_HELPER_COUNT = 148;",
                    "static final int SEMANTIC_HELPER_ATOM_COUNT = 72;",
                    "static final int SEMANTIC_HELPER_WHITESPACE_BYTES = 24 * 1024;",
                    "static final int SEMANTIC_HEAVY_PRIVATE_FIELD_COUNT = 8;",
                    "static final long SEMANTIC_ROOT_BYTES = 178_722L;",
                    "static final int SEMANTIC_ROOT_TOKENS = 36_928;",
                    "static final long SEMANTIC_HELPER_BYTES = 3_763_492L;",
                    "static final int SEMANTIC_HELPER_TOKENS = 28_268;",
                    "static final int SEMANTIC_CALLER_FILE_COUNT = 404;",
                    "static final long SEMANTIC_CALLER_BYTES = 3_942_214L;",
                    "static final int SEMANTIC_CALLER_TOKENS = 65_196;",
                    "static final int SEMANTIC_TOTAL_FILE_COUNT = 512;",
                    "static final long SEMANTIC_TOTAL_BYTES = 7_726_972L;",
                    "static final int SEMANTIC_TOTAL_TOKENS = 151_525;",
                    "static final int SEMANTIC_INPUT_TRANSPORT_FRAME_COUNT = 1_588;",
                    "\"460b3b9755e2669fb789c3190d9f6bc14e42c9f6cbe87bbf81cbe0d97bf6b3eb\"",
                    "static final OutputAuthority SEMANTIC_OUTPUT_AUTHORITY =",
                    "535_202L,",
                    "185_664L,",
                    "720_866L,",
                    "\"4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1\"",
                    "static R2ExternalNaturalEmitTimeoutProjectFixture createSemantic(",
                    "Workload.NATURAL_SEMANTIC",
                    "boolean isNaturalSemanticWorkload()",
                    "ORDINARY_MAXIMUM_FILES =",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES",
                    "ORDINARY_MAXIMUM_TOTAL_BYTES =",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES",
                    "ORDINARY_MAXIMUM_FILE_BYTES =",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES",
                    "WORKER_MAXIMUM_PROJECT_TOKENS = 262_144",
                    "MAXIMUM_OUTPUT_BYTES = 1024L * 1024L",
                ),
            )
            val sourceAuthority = javaMethod(
                workloadFixture,
                "private static void assertSourceAuthority(",
            )
            requireMarkers(
                label = "semantic ordinary-admission enforcement",
                source = sourceAuthority,
                markers = listOf(
                    "workload == Workload.NATURAL_SEMANTIC",
                    "sources.size() == SEMANTIC_CALLER_FILE_COUNT",
                    "callerBytes == SEMANTIC_CALLER_BYTES",
                    "rootPaths.size() == SEMANTIC_ROOT_COUNT",
                    "SEMANTIC_TOTAL_TOKENS",
                    "SEMANTIC_TOTAL_FILE_COUNT",
                    "SEMANTIC_TOTAL_BYTES",
                    "SEMANTIC_INPUT_TRANSPORT_FRAME_COUNT",
                    "External natural semantic aggregate authority escaped ordinary admission.",
                    "External natural semantic input framing authority drifted.",
                ),
            )

            val matrix = javaMethod(
                instrumentation,
                "private static void runExternalNaturalSemanticTimeoutMatrix(",
            )
            requireMarkers(
                label = "exact two-plus-three-plus-one matrix",
                source = matrix,
                markers = listOf(
                    "NATURAL_SEMANTIC_PRECONDITION_RUNS == 2",
                    "NATURAL_SEMANTIC_BASELINE_RUNS == 3",
                    "NATURAL_SEMANTIC_FAULT_ATTEMPTS == 1",
                    "for (int run = 1; run <= NATURAL_SEMANTIC_PRECONDITION_RUNS; run++)",
                    "for (int run = 1; run <= NATURAL_SEMANTIC_BASELINE_RUNS; run++)",
                    "calibrateExternalNaturalSemanticDeadline(baselineTimings)",
                    "runExternalNaturalSemanticFault(",
                    "external-natural-semantic-successor",
                    "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_PRECONDITION_PASS",
                    "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_BASELINE_PASS",
                    "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_SUCCESSOR_PASS",
                    "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_MATRIX_PASS",
                    "ordinaryAdmission=true expandedWorkerCapacity=false",
                    "requestStartAbsoluteDeadline=true sameBinder=true",
                    "sameProcessEpoch=true zeroSamples=",
                ),
            )
            val precondition = matrix.indexOf("NATURAL_SEMANTIC_PRECONDITION_RUNS")
            val baseline = matrix.indexOf("NATURAL_SEMANTIC_BASELINE_RUNS", precondition + 1)
            val calibration =
                matrix.indexOf("calibrateExternalNaturalSemanticDeadline(", baseline + 1)
            val fault =
                matrix.indexOf("runExternalNaturalSemanticFault(", calibration + 1)
            val successor =
                matrix.indexOf("external-natural-semantic-successor", fault + 1)
            check(
                precondition >= 0 &&
                    baseline > precondition &&
                    calibration > baseline &&
                    fault > calibration &&
                    successor > fault &&
                    Regex("""runExternalNaturalSemanticFault\(""")
                        .findAll(matrix)
                        .count() == 1,
            ) {
                "External natural semantic evidence must run two preconditions, three " +
                    "baselines, one fault, then one successor in that order."
            }
            listOf(
                "while (",
                "RETRY_DELAY_MS",
                "requestStop(",
                "workerSpin",
                "EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY",
                "STORED_CONTAINER_EXACT_64_SCOPE",
                "DEFLATED_EXPANDED_EXACT_64_SCOPE",
            ).forEach { forbidden ->
                check(forbidden !in matrix) {
                    "External natural semantic matrix gained a retry, stop, spin, or " +
                        "expanded-capacity path: $forbidden"
                }
            }

            val calibrationMethod = javaMethod(
                instrumentation,
                "private static NaturalSemanticCalibration",
            )
            requireMarkers(
                label = "unclamped strict request-start midpoint",
                source = calibrationMethod,
                markers = listOf(
                    "baselineTimings.size() == NATURAL_SEMANTIC_BASELINE_RUNS",
                    "timing.sequenceTwoNanosSinceRequestStart",
                    "timing.completedNanosSinceRequestStart",
                    "maximumTimeoutMillis - minimumTimeoutMillis",
                    "NATURAL_SEMANTIC_MINIMUM_MARGIN_MS * 2L",
                    "(maximumTimeoutMillis - minimumTimeoutMillis) / 2L",
                    "TypeScriptR2Contract",
                    ".MIN_PUBLIC_COMPILATION_TIMEOUT_MS",
                    "timeoutMillis - minimumTimeoutMillis",
                    "maximumTimeoutMillis - timeoutMillis",
                    "timing.sequenceTwoNanosSinceRequestStart",
                    "< timeoutNanos",
                    "timeoutNanos",
                    "< timing.completedNanosSinceRequestStart",
                    "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_WINDOW_PASS",
                    "strictIntersection=true faultAttempts=0",
                ),
            )
            listOf(
                "SystemClock.sleep(",
                "Thread.sleep(",
                "while (",
                "Math.max(",
                "Math.min(",
                "coerceAtLeast(",
                "coerceAtMost(",
                "RETRY_DELAY_MS",
                "requestStop(",
                "workerSpin",
                "expandedWorkerCapacity",
                "EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY",
            ).forEach { forbidden ->
                check(forbidden !in calibrationMethod) {
                    "External natural semantic calibration must not retry, stop, spin, " +
                        "clamp, or acquire expanded capacity: $forbidden"
                }
            }

            val oneShotAdmission = javaMethod(
                instrumentation,
                "private static Admission admitNaturalSemanticOnce(",
            )
            requireMarkers(
                label = "one-shot ordinary public admission",
                source = oneShotAdmission,
                markers = listOf(
                    "requireLiveSameBinder(session)",
                    ".MIN_PUBLIC_COMPILATION_TIMEOUT_MS",
                    "compiler.startCompilation(",
                    "publicRequest(",
                    "null,",
                    "timeoutMs)",
                    "Natural semantic request was rejected without retry:",
                ),
            )
            check(
                Regex("""compiler\.startCompilation\(""")
                    .findAll(oneShotAdmission)
                    .count() == 1,
            ) {
                "External natural semantic admission must call startCompilation exactly once."
            }
            listOf(
                "while (",
                "for (",
                "SystemClock.sleep(",
                "Thread.sleep(",
                "RETRY_DELAY_MS",
                "requestStop(",
                ".cancel(",
                "workerSpin",
                "expandedWorkerCapacity",
                "EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY",
                "STORED_CONTAINER_EXACT_64_SCOPE",
                "DEFLATED_EXPANDED_EXACT_64_SCOPE",
            ).forEach { forbidden ->
                check(forbidden !in oneShotAdmission) {
                    "External natural semantic admission gained a retry, stop, spin, or " +
                        "expanded-capacity path: $forbidden"
                }
            }

            val successfulRun = javaMethod(
                instrumentation,
                "private static NaturalSemanticTiming\n" +
                    "            runExternalNaturalSemanticSuccessfulRun(",
            )
            requireMarkers(
                label = "successful baseline and successor receipt order",
                source = successfulRun,
                markers = listOf(
                    "createSemantic(",
                    "TypeScriptCompilerContract.MAX_TIMEOUT_MS",
                    "assertPreAcknowledgementCleanupTelemetry(",
                    "acknowledgeAdmission(admission)",
                    "callback.awaitTerminal(CALLBACK_WAIT_MS)",
                    "callback.assertExactlyOneCompletion()",
                    "TypeScriptCompilerContract.OUTCOME_EMITTED",
                    "verifyExternalNaturalEmitOutput(",
                    "if (successor)",
                    ".SUCCESSOR_TOTAL_FILE_COUNT",
                    "phaseReceiptRequired=false sameBinder=true",
                    "sameProcessEpoch=true",
                    "requireNaturalPhaseReceipt(",
                    "assertSuccessfulNaturalPhaseReceipt(",
                    "awaitCleanupEvidence(",
                    ".SEMANTIC_TOTAL_FILE_COUNT",
                    "phaseReceipt.semanticTiming()",
                ),
            )
            val successfulPreAck =
                successfulRun.indexOf("assertPreAcknowledgementCleanupTelemetry(")
            val successfulAck =
                successfulRun.indexOf("acknowledgeAdmission(", successfulPreAck)
            val successfulCallback =
                successfulRun.indexOf("callback.awaitTerminal(", successfulAck)
            val successfulPhaseReceipt =
                successfulRun.indexOf("requireNaturalPhaseReceipt(", successfulCallback)
            val successfulCleanup =
                successfulRun.indexOf("awaitCleanupEvidence(", successfulPhaseReceipt)
            check(
                successfulPreAck >= 0 &&
                    successfulAck > successfulPreAck &&
                    successfulCallback > successfulAck &&
                    successfulPhaseReceipt > successfulCallback &&
                    successfulCleanup > successfulPhaseReceipt,
            ) {
                "External natural semantic successful runs must prove pre-ACK ownership, " +
                    "ACK once, receive one callback, then read phase and cleanup receipts."
            }

            val faultMethod = javaMethod(
                instrumentation,
                "private static void runExternalNaturalSemanticFault(",
            )
            requireMarkers(
                label = "single natural semantic timeout fault",
                source = faultMethod,
                markers = listOf(
                    "NATURAL_SEMANTIC_FAULT_ATTEMPTS == 1",
                    "createSemantic(",
                    "calibration.timeoutMillis",
                    "assertPreAcknowledgementCleanupTelemetry(",
                    "acknowledgeAdmission(admission)",
                    "callback.assertExactlyOneCompletion()",
                    "TypeScriptCompilerContract.OUTCOME_TIMED_OUT",
                    "TypeScriptCompilerContract.ERROR_TIMEOUT",
                    "assertNoCompilationPayload(",
                    "assertSyncedEmptyOutput(",
                    "assertTimedOutNaturalSemanticPhaseReceipt(",
                    "awaitCleanupEvidence(",
                    ".SEMANTIC_TOTAL_FILE_COUNT",
                    "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_FAULT_PASS",
                    "phase=semantic progressSequence=",
                    "callbackBeforeReceiptRead=true",
                    "receiptCreatedBeforeCallback=true",
                ),
            )
            check(
                Regex("""admitNaturalSemanticOnce\(""")
                    .findAll(faultMethod)
                    .count() == 1 &&
                    "while (" !in faultMethod &&
                    "RETRY_DELAY_MS" !in faultMethod &&
                    "requestStop(" !in faultMethod &&
                    "workerSpin" !in faultMethod &&
                    "expandedWorkerCapacity" !in faultMethod,
            ) {
                "External natural semantic fault must have one irreversible ordinary admission."
            }

            val terminationAssertion = javaMethod(
                instrumentation,
                "private static void assertTimedOutNaturalSemanticPhaseReceipt(",
            )
            requireMarkers(
                label = "parse-completed semantic 64-of-256 termination",
                source = terminationAssertion,
                markers = listOf(
                    "fixture.isNaturalSemanticWorkload()",
                    "new String[] {\"parse\"}",
                    "\"TIMED_OUT\".equals(termination.stopCause)",
                    "\"semantic\".equals(termination.hostStopPhase)",
                    "\"semantic\".equals(termination.pageTerminationPhase)",
                    "\"ingress\", \"parse\", \"semantic\"",
                    "new String[] {\"ingress\", \"parse\"}",
                    "termination.progressSequence == 2",
                    ".SEMANTIC_ROOT_COUNT",
                    "- 1)",
                    "/ 4",
                    "+ 1",
                    "termination.progressTotalUnits",
                    "termination.replacementWorkerGeneration",
                    "== termination.terminatedWorkerGeneration + 1",
                    "NATURAL_SEMANTIC_MAXIMUM_OVERSHOOT_MS",
                ),
            )
            check((256 - 1) / 4 + 1 == 64) {
                "External natural semantic sequence-two authority is not 64/256."
            }

            val inputAssertion = javaMethod(
                instrumentation,
                "private static void assertNaturalPhaseInput(",
            )
            requireMarkers(
                label = "512-cursor 1,588-frame input receipt",
                source = inputAssertion,
                markers = listOf(
                    "fixture.isNaturalSemanticWorkload()",
                    ".SEMANTIC_TOTAL_FILE_COUNT",
                    ".SEMANTIC_INPUT_TRANSPORT_FRAME_COUNT",
                    ".SEMANTIC_TOTAL_BYTES",
                    "input.openedCursorCount == expectedFiles",
                    "input.closedCursorCount == expectedFiles",
                    "!input.activeCursor",
                    "input.released",
                ),
            )

            val appBuild = appBuildScript.asFile.readText(Charsets.UTF_8)
            check(
                Regex(
                    """buildConfigField\(\s*"boolean",\s*""" +
                        """"R2_EXTERNAL_CAPACITY_EVIDENCE",\s*"false"\s*\)""",
                    RegexOption.DOT_MATCHES_ALL,
                ).containsMatchIn(appBuild),
            ) {
                "Normal builds must keep external natural semantic receipts disabled."
            }
            val productionSnapshot =
                r2ProductionSnapshotSource.asFile.readText(Charsets.UTF_8)
            val publicR2Contract =
                typeScriptR2ContractSource.asFile.readText(Charsets.UTF_8)
            check(
                "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES" in
                    productionSnapshot &&
                    ("const val MAX_ORDINARY_COMPILATION_INPUT_BYTES = " +
                        "8L * 1024L * 1024L") in publicR2Contract,
            ) {
                "External natural semantic evidence changed the public ordinary 8 MiB fence."
            }
            val metadata = r2PluginMetadataSource.asFile.readText(Charsets.UTF_8)
            requireDisabledCompilerCapabilityAdvertisement(
                label = "External natural semantic evidence",
                metadata = metadata,
            )
        }
    }

val verifyR2ExternalNaturalSemanticTimeoutApks =
    tasks.register("verifyR2ExternalNaturalSemanticTimeoutApks") {
        group = "verification"
        description =
            "Verifies external natural semantic-timeout markers in both fixture and both " +
                "TEST_ONLY evidence APKs, their exclusion from normal Release, and the " +
                "minified release-equivalent mapping."
        dependsOn(verifyR2ExternalNaturalSemanticTimeoutSourcePolicy)
        dependsOn(verifyR2P3bB2kExternalNaturalEmitTimeoutApks)
        dependsOn(verifyR2PublicPreviewApkManifestShape)
        dependsOn(verifyR2PublicPreviewSigningConfiguration)
        dependsOn(
            ":app:assembleDebug",
            ":app:assembleRelease",
            ":app:assembleR2CapacityEvidenceDebug",
            ":app:assembleR2CapacityEvidenceRelease",
            "assembleDebug",
            "assembleRelease",
        )

        doLast {
            fun dexBytes(apkFile: File): List<ByteArray> =
                ZipFile(apkFile).use { apk ->
                    apk.entries().asSequence()
                        .filter { entry ->
                            !entry.isDirectory &&
                                entry.name.matches(
                                    Regex("""classes(?:\d+)?\.dex"""),
                                )
                        }
                        .map { entry ->
                            apk.getInputStream(entry).use { it.readBytes() }
                        }
                        .toList()
                }

            fun containsMarker(
                dex: List<ByteArray>,
                marker: String,
            ): Boolean {
                val bytes = marker.toByteArray(Charsets.UTF_8)
                return dex.any { it.indexOfSubsequence(bytes) >= 0 }
            }

            val phasePrefix =
                "__autojs6_r2_external_natural_phase_timeout_"
            val fixtureMarkers = listOf(
                "externalNaturalSemanticTimeoutEvidence",
                "R2ExternalNaturalEmitTimeoutProjectFixture",
                "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_PRECONDITION_PASS",
                "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_BASELINE_PASS",
                "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_WINDOW_PASS",
                "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_FAULT_PASS",
                "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_SUCCESSOR_PASS",
                "EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_MATRIX_PASS",
                "strictIntersection=true faultAttempts=0",
                "Natural semantic request was rejected without retry:",
                "phase=semantic progressSequence=",
                "460b3b9755e2669fb789c3190d9f6bc14e42c9f6cbe87bbf81cbe0d97bf6b3eb",
                "4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1",
                "0b504753f68a345d6aa82eeb174f6d9c63eec3cb21b7810ed09f69e59296be25",
            )
            listOf("debug", "release").forEach { variant ->
                val apkFile = singleVariantApk(project, variant)
                val dex = dexBytes(apkFile)
                check(dex.isNotEmpty()) {
                    "External natural semantic $variant fixture APK contains no DEX."
                }
                fixtureMarkers.forEach { marker ->
                    check(containsMarker(dex, marker)) {
                        "External natural semantic $variant fixture APK is missing: $marker"
                    }
                }
            }

            val evidenceMarkers = listOf(
                "${phasePrefix}enabled",
                "${phasePrefix}receipt_available",
                "${phasePrefix}request_id",
                "${phasePrefix}host_request_id",
                "${phasePrefix}outcome",
                "${phasePrefix}created_before_callback",
                "${phasePrefix}phase_values",
                "${phasePrefix}phase_sequence_two_nanos_since_request_start",
                "${phasePrefix}phase_completed_nanos_since_request_start",
                "${phasePrefix}termination_stop_cause",
                "${phasePrefix}termination_phase_at_host_stop_selection",
                "${phasePrefix}termination_phase_at_page_termination",
                "${phasePrefix}termination_progress_sequence",
                "${phasePrefix}termination_progress_completed_units",
                "${phasePrefix}termination_progress_total_units",
                "${phasePrefix}termination_terminated_worker_generation",
                "${phasePrefix}termination_replacement_worker_generation",
                "${phasePrefix}input_sent_frame_count",
                "${phasePrefix}input_total_payload_bytes",
                "${phasePrefix}input_opened_cursor_count",
                "${phasePrefix}input_closed_cursor_count",
                "${phasePrefix}input_released",
            )
            listOf(
                "r2CapacityEvidenceDebug",
                "r2CapacityEvidenceRelease",
            ).forEach { variant ->
                val apkFile = singleVariantApk(project(":app"), variant)
                val dex = dexBytes(apkFile)
                check(dex.isNotEmpty()) {
                    "External natural semantic $variant evidence APK contains no DEX."
                }
                evidenceMarkers.forEach { marker ->
                    check(containsMarker(dex, marker)) {
                        "External natural semantic $variant evidence APK is missing: $marker"
                    }
                }
            }

            val normalRelease = singleVariantApk(project(":app"), "release")
            val normalReleaseDex = dexBytes(normalRelease)
            listOf(
                phasePrefix,
                "${phasePrefix}request_id",
                "${phasePrefix}created_before_callback",
                "${phasePrefix}termination_progress_sequence",
                "${phasePrefix}input_released",
            ).forEach { marker ->
                check(!containsMarker(normalReleaseDex, marker)) {
                    "Normal Release retained external natural semantic receipt key: $marker"
                }
            }

            val mappingFile =
                project(":app").layout.buildDirectory.file(
                    "outputs/mapping/r2CapacityEvidenceRelease/mapping.txt",
                ).get().asFile
            check(mappingFile.isFile && mappingFile.length() > 0L) {
                "External natural semantic minified evidence Release mapping is missing."
            }
        }
    }

val verifyR2ExternalNaturalParseTimeoutSourcePolicy =
    tasks.register("verifyR2ExternalNaturalParseTimeoutSourcePolicy") {
        group = "verification"
        description =
            "Statically verifies the external-UID Binder natural parse-timeout " +
                "workload, unclamped request-start calibration, one-shot fault, " +
                "cleanup, and same-process successor authority."
        dependsOn(verifyR2ExternalNaturalSemanticTimeoutSourcePolicy)
        inputs.files(
            r2PublicPreviewInstrumentationSource,
            r2ExternalNaturalEmitTimeoutProjectFixtureSource,
            r2ExternalNaturalPhaseTimeoutEvidenceSource,
            r2ExternalCapacityEvidenceProtocolSource,
            r2ExternalPlusOneCleanupEvidenceSource,
            r2CompilationCoordinatorSource,
            r2CompilerServiceSource,
            r2PluginMetadataSource,
            r2ProductionSnapshotSource,
            typeScriptR2ContractSource,
            appBuildScript,
        )

        doLast {
            fun requireMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "External natural parse-timeout $label is missing: $marker"
                    }
                }
            }

            fun javaMethod(
                source: String,
                signature: String,
            ): String {
                val start = source.indexOf(signature)
                check(start >= 0) {
                    "External natural parse-timeout method is missing: $signature"
                }
                val end = source.indexOf("\n    private ", start + signature.length)
                    .takeIf { index -> index >= 0 }
                    ?: source.length
                return source.substring(start, end)
            }

            val instrumentation =
                r2PublicPreviewInstrumentationSource.asFile
                    .readText(Charsets.UTF_8)
            val workloadFixture =
                r2ExternalNaturalEmitTimeoutProjectFixtureSource.asFile
                    .readText(Charsets.UTF_8)

            requireMarkers(
                label = "exclusive external mode",
                source = instrumentation,
                markers = listOf(
                    "ARG_EXTERNAL_NATURAL_PARSE_TIMEOUT_EVIDENCE",
                    "\"externalNaturalParseTimeoutEvidence\"",
                    "externalNaturalParseTimeoutEvidenceRequested",
                    "External natural parse timeout evidence must run in its exclusive ",
                    "runExternalNaturalParseTimeoutEvidence()",
                    "runExternalNaturalParseTimeoutMatrix(",
                    "one fixed-deadline parse ",
                    "same-Binder/process successor recovery",
                ),
            )

            val evidenceEntry = javaMethod(
                instrumentation,
                "private void runExternalNaturalParseTimeoutEvidence(",
            )
            requireMarkers(
                label = "same-signer distinct-UID TEST_ONLY Binder entry",
                source = evidenceEntry,
                markers = listOf(
                    "Process.myUid() != currentApplication.uid",
                    "PackageManager.SIGNATURE_MATCH",
                    "ApplicationInfo.FLAG_TEST_ONLY",
                    "External natural parse fixture is not TEST_ONLY.",
                    "External natural parse compiler evidence build is not TEST_ONLY.",
                    "PermissionInfo.PROTECTION_SIGNATURE",
                    "serviceInfo.exported",
                    "PLUGIN_PERMISSION.equals(serviceInfo.permission)",
                    "awaitCleanupEvidenceSession(",
                    "runExternalNaturalParseTimeoutMatrix(",
                    "requireLiveSameBinder(session)",
                ),
            )

            requireMarkers(
                label = "frozen workload and output authority",
                source = workloadFixture,
                markers = listOf(
                    "static final int PARSE_ROOT_COUNT = 32;",
                    "static final int PARSE_SEQUENCE_TWO_COMPLETED_ROOTS = 8;",
                    "static final int PARSE_SOURCE_DIRECTORY_DEPTH = 8;",
                    "static final int PARSE_FIRST_IMPORT_ROOT_INDEX = 2;",
                    "static final int PARSE_IMPORT_ROOT_COUNT = 3;",
                    "static final int PARSE_IMPORTS_PER_ROOT = 3_000;",
                    "static final int PARSE_PRE_SEQUENCE_MAXIMUM_MODULE_INDEX = 8_999;",
                    "static final int PARSE_GRAPH_TRIGGER_ROOT_INDEX = 8;",
                    "static final int PARSE_POST_SEQUENCE_IMPORT_ROOT_INDEX =",
                    "static final int PARSE_POST_SEQUENCE_IMPORT_COUNT = 6_000;",
                    "static final int PARSE_MAXIMUM_MODULE_INDEX = 14_999;",
                    "static final int PARSE_GRAPH_FILE_COUNT = 371;",
                    "static final int PARSE_GRAPH_ENTRY_INDEX = 370;",
                    "static final int PARSE_GRAPH_EDGE_COUNT = 68_635;",
                    "static final int PARSE_REFERENCE_LINE_BYTES = 50;",
                    "static final long PARSE_ROOT_BYTES = 961_148L;",
                    "static final int PARSE_ROOT_TOKENS = 150_192;",
                    "static final int PARSE_CALLER_FILE_COUNT = 404;",
                    "static final long PARSE_CALLER_BYTES = 4_392_959L;",
                    "static final int PARSE_CALLER_TOKENS = 150_203;",
                    "static final long PARSE_MAXIMUM_ROOT_BYTES = 384_082L;",
                    "static final int PARSE_MAXIMUM_ROOT_TOKENS = 60_006;",
                    "static final int PARSE_TOTAL_FILE_COUNT = 512;",
                    "static final long PARSE_TOTAL_BYTES = 8_177_717L;",
                    "static final int PARSE_TOTAL_TOKENS = 236_532;",
                    "static final int PARSE_TOTAL_PATH_CHARACTERS = 29_343;",
                    "static final int PARSE_INPUT_TRANSPORT_FRAME_COUNT = 1_598;",
                    "static final String PARSE_CALLER_INVENTORY_FINGERPRINT =",
                    "\"334b1a3a940a741105c5222ca6c189c72dfb6c0ea994a72b86013df1fdbf72ef\"",
                    "static final OutputAuthority PARSE_OUTPUT_AUTHORITY =",
                    "64,",
                    "5_962L,",
                    "7_193L,",
                    "13_155L,",
                    "\"18f79093d9a09c3111740e282a3ad642576230c142d6b7d7c61bb9c7950ca038\"",
                    "static R2ExternalNaturalEmitTimeoutProjectFixture createParse(",
                    "Workload.NATURAL_PARSE",
                    "boolean isNaturalParseWorkload()",
                    "ORDINARY_MAXIMUM_FILES =",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES",
                    "ORDINARY_MAXIMUM_TOTAL_BYTES =",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES",
                    "ORDINARY_MAXIMUM_FILE_BYTES =",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES",
                    "WORKER_MAXIMUM_PROJECT_TOKENS = 262_144",
                    "MAXIMUM_OUTPUT_BYTES = 1024L * 1024L",
                ),
            )

            val parseSources = javaMethod(
                workloadFixture,
                "private static List<SourcePayload> parseSources()",
            )
            requireMarkers(
                label = "sequence-two tail topology",
                source = parseSources,
                markers = listOf(
                    "PARSE_PRE_SEQUENCE_MAXIMUM_MODULE_INDEX",
                    "PARSE_IMPORT_ROOT_COUNT",
                    "* PARSE_IMPORTS_PER_ROOT",
                    "PARSE_POST_SEQUENCE_IMPORT_ROOT_INDEX",
                    "== PARSE_GRAPH_TRIGGER_ROOT_INDEX",
                    "PARSE_POST_SEQUENCE_IMPORT_COUNT > 0",
                    "PARSE_MAXIMUM_MODULE_INDEX",
                    "+ PARSE_POST_SEQUENCE_IMPORT_COUNT",
                    "PARSE_GRAPH_ENTRY_INDEX",
                    "== PARSE_GRAPH_FILE_COUNT - 1",
                    "graphEdges = Math.addExact(graphEdges, index)",
                    "Natural parse progress topology drifted.",
                ),
            )

            val sourceAuthority = javaMethod(
                workloadFixture,
                "private static void assertSourceAuthority(",
            )
            requireMarkers(
                label = "parse ordinary-admission enforcement",
                source = sourceAuthority,
                markers = listOf(
                    "workload == Workload.NATURAL_PARSE",
                    "sources.size() == PARSE_CALLER_FILE_COUNT",
                    "callerBytes == PARSE_CALLER_BYTES",
                    "rootPaths.size() == PARSE_ROOT_COUNT",
                    "PARSE_TOTAL_TOKENS",
                    "PARSE_TOTAL_FILE_COUNT",
                    "PARSE_TOTAL_BYTES",
                    "PARSE_TOTAL_PATH_CHARACTERS",
                    "PARSE_INPUT_TRANSPORT_FRAME_COUNT",
                    "External natural parse aggregate authority escaped ordinary admission.",
                    "External natural parse input framing authority drifted.",
                ),
            )

            val matrix = javaMethod(
                instrumentation,
                "private static void runExternalNaturalParseTimeoutMatrix(",
            )
            requireMarkers(
                label = "exact two-plus-three-plus-one matrix",
                source = matrix,
                markers = listOf(
                    "NATURAL_PARSE_PRECONDITION_RUNS == 2",
                    "NATURAL_PARSE_BASELINE_RUNS == 3",
                    "NATURAL_PARSE_FAULT_ATTEMPTS == 1",
                    "PRE_ACK_TELEMETRY_WAIT_MS",
                    "< NATURAL_PARSE_MINIMUM_MARGIN_MS",
                    "for (int run = 1; run <= NATURAL_PARSE_PRECONDITION_RUNS; run++)",
                    "for (int run = 1; run <= NATURAL_PARSE_BASELINE_RUNS; run++)",
                    "calibrateNaturalParseDeadline(",
                    "EXTERNAL_NATURAL_PARSE_TIMEOUT_WINDOW_PASS",
                    "runExternalNaturalParseFault(",
                    "external-natural-parse-successor",
                    "EXTERNAL_NATURAL_PARSE_TIMEOUT_PRECONDITION_PASS",
                    "EXTERNAL_NATURAL_PARSE_TIMEOUT_BASELINE_PASS",
                    "EXTERNAL_NATURAL_PARSE_TIMEOUT_SUCCESSOR_PASS",
                    "EXTERNAL_NATURAL_PARSE_TIMEOUT_MATRIX_PASS",
                    "preSequenceImports=",
                    "postSequenceImports=",
                    "postSequenceRoot=",
                    "ordinaryAdmission=true expandedWorkerCapacity=false",
                    "requestStartAbsoluteDeadline=true sameBinder=true",
                    "sameProcessEpoch=true zeroSamples=",
                ),
            )
            val precondition = matrix.indexOf("NATURAL_PARSE_PRECONDITION_RUNS")
            val baseline = matrix.indexOf("NATURAL_PARSE_BASELINE_RUNS", precondition + 1)
            val calibration =
                matrix.indexOf("calibrateNaturalParseDeadline(", baseline + 1)
            val fault = matrix.indexOf("runExternalNaturalParseFault(", calibration + 1)
            val successor = matrix.indexOf("external-natural-parse-successor", fault + 1)
            check(
                precondition >= 0 &&
                    baseline > precondition &&
                    calibration > baseline &&
                    fault > calibration &&
                    successor > fault &&
                    Regex("""runExternalNaturalParseFault\(""")
                        .findAll(matrix)
                        .count() == 1,
            ) {
                "External natural parse evidence must run two preconditions, three " +
                    "baselines, one fault, then one successor in that order."
            }
            listOf(
                "while (",
                "RETRY_DELAY_MS",
                "requestStop(",
                "workerSpin",
                "EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY",
                "STORED_CONTAINER_EXACT_64_SCOPE",
                "DEFLATED_EXPANDED_EXACT_64_SCOPE",
            ).forEach { forbidden ->
                check(forbidden !in matrix) {
                    "External natural parse matrix gained a retry, stop, spin, or " +
                        "expanded-capacity path: $forbidden"
                }
            }

            val calibrationMethod = javaMethod(
                instrumentation,
                "private static NaturalParseCalibration",
            )
            requireMarkers(
                label = "unclamped strict request-start midpoint",
                source = calibrationMethod,
                markers = listOf(
                    "baselineTimings.size() == NATURAL_PARSE_BASELINE_RUNS",
                    "timing.sequenceTwoNanosSinceRequestStart",
                    "timing.completedNanosSinceRequestStart",
                    "maximumTimeoutMillis - minimumTimeoutMillis",
                    "NATURAL_PARSE_MINIMUM_MARGIN_MS * 2L",
                    "(maximumTimeoutMillis - minimumTimeoutMillis) / 2L",
                    "TypeScriptR2Contract",
                    ".MIN_PUBLIC_COMPILATION_TIMEOUT_MS",
                    "timeoutMillis - minimumTimeoutMillis",
                    "maximumTimeoutMillis - timeoutMillis",
                    "< timeoutNanos",
                    "< timing.completedNanosSinceRequestStart",
                    "passEvent",
                    "strictIntersection=true faultAttempts=0",
                ),
            )
            listOf(
                "SystemClock.sleep(",
                "Thread.sleep(",
                "while (",
                "Math.max(",
                "Math.min(",
                "coerceAtLeast(",
                "coerceAtMost(",
                "RETRY_DELAY_MS",
                "requestStop(",
                "workerSpin",
                "expandedWorkerCapacity",
                "EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY",
            ).forEach { forbidden ->
                check(forbidden !in calibrationMethod) {
                    "External natural parse calibration must not retry, stop, spin, " +
                        "clamp, or acquire expanded capacity: $forbidden"
                }
            }

            val oneShotAdmission = javaMethod(
                instrumentation,
                "private static Admission admitNaturalParseOnce(",
            )
            requireMarkers(
                label = "one-shot ordinary public admission",
                source = oneShotAdmission,
                markers = listOf(
                    "requireLiveSameBinder(session)",
                    ".MIN_PUBLIC_COMPILATION_TIMEOUT_MS",
                    "TypeScriptCompilerContract.MAX_TIMEOUT_MS",
                    "compiler.startCompilation(",
                    "publicRequest(",
                    "null,",
                    "timeoutMs)",
                    "Natural parse request was rejected without retry:",
                ),
            )
            check(
                Regex("""compiler\.startCompilation\(""")
                    .findAll(oneShotAdmission)
                    .count() == 1,
            ) {
                "External natural parse admission must call startCompilation exactly once."
            }
            listOf(
                "while (",
                "for (",
                "SystemClock.sleep(",
                "Thread.sleep(",
                "RETRY_DELAY_MS",
                "requestStop(",
                ".cancel(",
                "workerSpin",
                "expandedWorkerCapacity",
                "EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY",
                "STORED_CONTAINER_EXACT_64_SCOPE",
                "DEFLATED_EXPANDED_EXACT_64_SCOPE",
            ).forEach { forbidden ->
                check(forbidden !in oneShotAdmission) {
                    "External natural parse admission gained a retry, stop, spin, or " +
                        "expanded-capacity path: $forbidden"
                }
            }

            val successfulRun = javaMethod(
                instrumentation,
                "private static NaturalParseTiming\n" +
                    "            runExternalNaturalParseSuccessfulRun(",
            )
            requireMarkers(
                label = "successful baseline and successor receipt order",
                source = successfulRun,
                markers = listOf(
                    "createParse(",
                    "TypeScriptCompilerContract.MAX_TIMEOUT_MS",
                    "assertPreAcknowledgementCleanupTelemetry(",
                    "acknowledgeAdmission(admission)",
                    "callback.awaitTerminal(CALLBACK_WAIT_MS)",
                    "callback.assertExactlyOneCompletion()",
                    "TypeScriptCompilerContract.OUTCOME_EMITTED",
                    "verifyExternalNaturalEmitOutput(",
                    "if (successor)",
                    ".SUCCESSOR_TOTAL_FILE_COUNT",
                    "phaseReceiptRequired=false sameBinder=true",
                    "sameProcessEpoch=true",
                    "requireNaturalPhaseReceipt(",
                    "assertSuccessfulNaturalPhaseReceipt(",
                    "awaitCleanupEvidence(",
                    ".PARSE_TOTAL_FILE_COUNT",
                    "phaseReceipt.parseTiming()",
                ),
            )
            val successfulPreAck =
                successfulRun.indexOf("assertPreAcknowledgementCleanupTelemetry(")
            val successfulAck =
                successfulRun.indexOf("acknowledgeAdmission(", successfulPreAck)
            val successfulCallback =
                successfulRun.indexOf("callback.awaitTerminal(", successfulAck)
            val successfulPhaseReceipt =
                successfulRun.indexOf("requireNaturalPhaseReceipt(", successfulCallback)
            val successfulCleanup =
                successfulRun.indexOf("awaitCleanupEvidence(", successfulPhaseReceipt)
            check(
                successfulPreAck >= 0 &&
                    successfulAck > successfulPreAck &&
                    successfulCallback > successfulAck &&
                    successfulPhaseReceipt > successfulCallback &&
                    successfulCleanup > successfulPhaseReceipt,
            ) {
                "External natural parse successful runs must prove pre-ACK ownership, " +
                    "ACK once, receive one callback, then read phase and cleanup receipts."
            }

            val rawModuleRecovery = javaMethod(
                instrumentation,
                "private static byte[] recoverRawWorkerModuleBytes(",
            )
            requireMarkers(
                label = "depth-aware packaged Source Map recovery",
                source = rawModuleRecovery,
                markers = listOf(
                    "int parentDepth = 1;",
                    "stem.charAt(index) == '/'",
                    "index < parentDepth",
                    "packagedDirectiveBuilder.append(\"../\")",
                    ".append(\"maps/\")",
                    ".append(stem)",
                    ".append(\".js.map\")",
                    "Packaged JavaScript Source Map reference differs.",
                    "Worker JavaScript reconstruction differs.",
                ),
            )

            val faultMethod = javaMethod(
                instrumentation,
                "private static void runExternalNaturalParseFault(",
            )
            requireMarkers(
                label = "single natural parse timeout fault",
                source = faultMethod,
                markers = listOf(
                    "NATURAL_PARSE_FAULT_ATTEMPTS == 1",
                    "createParse(",
                    "calibration.timeoutMillis",
                    "assertPreAcknowledgementCleanupTelemetry(",
                    "acknowledgeAdmission(admission)",
                    "callback.assertExactlyOneCompletion()",
                    "TypeScriptCompilerContract.OUTCOME_TIMED_OUT",
                    "TypeScriptCompilerContract.ERROR_TIMEOUT",
                    "assertNoCompilationPayload(",
                    "assertSyncedEmptyOutput(",
                    "assertTimedOutNaturalParsePhaseReceipt(",
                    "awaitCleanupEvidence(",
                    ".PARSE_TOTAL_FILE_COUNT",
                    "EXTERNAL_NATURAL_PARSE_TIMEOUT_FAULT_PASS",
                    "phase=parse progressSequence=",
                    "callbackBeforeReceiptRead=true",
                    "receiptCreatedBeforeCallback=true",
                ),
            )
            check(
                Regex("""admitNaturalParseOnce\(""")
                    .findAll(faultMethod)
                    .count() == 1 &&
                    "while (" !in faultMethod &&
                    "RETRY_DELAY_MS" !in faultMethod &&
                    "requestStop(" !in faultMethod &&
                    "workerSpin" !in faultMethod &&
                    "expandedWorkerCapacity" !in faultMethod,
            ) {
                "External natural parse fault must have one irreversible ordinary admission."
            }

            val terminationAssertion = javaMethod(
                instrumentation,
                "private static void assertTimedOutNaturalParsePhaseReceipt(",
            )
            requireMarkers(
                label = "empty-timing parse 8-of-32 termination",
                source = terminationAssertion,
                markers = listOf(
                    "fixture.isNaturalParseWorkload()",
                    "new String[0]",
                    "\"TIMED_OUT\".equals(termination.stopCause)",
                    "\"parse\".equals(termination.hostStopPhase)",
                    "\"parse\".equals(termination.pageTerminationPhase)",
                    "new String[] {\"ingress\", \"parse\"}",
                    "new String[] {\"ingress\"}",
                    "termination.progressSequence == 2",
                    ".PARSE_ROOT_COUNT",
                    "- 1)",
                    "/ 4",
                    "+ 1",
                    "termination.progressTotalUnits",
                    "termination.replacementWorkerGeneration",
                    "== termination.terminatedWorkerGeneration + 1",
                    "NATURAL_PARSE_MAXIMUM_OVERSHOOT_MS",
                ),
            )
            check((32 - 1) / 4 + 1 == 8) {
                "External natural parse sequence-two authority is not 8/32."
            }

            val inputAssertion = javaMethod(
                instrumentation,
                "private static void assertNaturalPhaseInput(",
            )
            requireMarkers(
                label = "512-cursor 1,598-frame input receipt",
                source = inputAssertion,
                markers = listOf(
                    "fixture.isNaturalParseWorkload()",
                    ".PARSE_TOTAL_FILE_COUNT",
                    ".PARSE_INPUT_TRANSPORT_FRAME_COUNT",
                    ".PARSE_TOTAL_BYTES",
                    "input.openedCursorCount == expectedFiles",
                    "input.closedCursorCount == expectedFiles",
                    "!input.activeCursor",
                    "input.released",
                ),
            )

            val appBuild = appBuildScript.asFile.readText(Charsets.UTF_8)
            check(
                Regex(
                    """buildConfigField\(\s*"boolean",\s*""" +
                        """"R2_EXTERNAL_CAPACITY_EVIDENCE",\s*"false"\s*\)""",
                    RegexOption.DOT_MATCHES_ALL,
                ).containsMatchIn(appBuild),
            ) {
                "Normal builds must keep external natural parse receipts disabled."
            }
            val productionSnapshot =
                r2ProductionSnapshotSource.asFile.readText(Charsets.UTF_8)
            val publicR2Contract =
                typeScriptR2ContractSource.asFile.readText(Charsets.UTF_8)
            check(
                "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES" in
                    productionSnapshot &&
                    ("const val MAX_ORDINARY_COMPILATION_INPUT_BYTES = " +
                        "8L * 1024L * 1024L") in publicR2Contract,
            ) {
                "External natural parse evidence changed the public ordinary 8 MiB fence."
            }
            val metadata = r2PluginMetadataSource.asFile.readText(Charsets.UTF_8)
            requireDisabledCompilerCapabilityAdvertisement(
                label = "External natural parse evidence",
                metadata = metadata,
            )
        }
    }

val verifyR2ExternalNaturalParseTimeoutApks =
    tasks.register("verifyR2ExternalNaturalParseTimeoutApks") {
        group = "verification"
        description =
            "Verifies external natural parse-timeout markers in both fixture and both " +
                "TEST_ONLY evidence APKs, their exclusion from normal Release, and the " +
                "minified release-equivalent mapping."
        dependsOn(verifyR2ExternalNaturalParseTimeoutSourcePolicy)
        dependsOn(verifyR2ExternalNaturalSemanticTimeoutApks)
        dependsOn(verifyR2PublicPreviewApkManifestShape)
        dependsOn(verifyR2PublicPreviewSigningConfiguration)
        dependsOn(
            ":app:assembleDebug",
            ":app:assembleRelease",
            ":app:assembleR2CapacityEvidenceDebug",
            ":app:assembleR2CapacityEvidenceRelease",
            "assembleDebug",
            "assembleRelease",
        )

        doLast {
            fun dexBytes(apkFile: File): List<ByteArray> =
                ZipFile(apkFile).use { apk ->
                    apk.entries().asSequence()
                        .filter { entry ->
                            !entry.isDirectory &&
                                entry.name.matches(
                                    Regex("""classes(?:\d+)?\.dex"""),
                                )
                        }
                        .map { entry ->
                            apk.getInputStream(entry).use { it.readBytes() }
                        }
                        .toList()
                }

            fun containsMarker(
                dex: List<ByteArray>,
                marker: String,
            ): Boolean {
                val bytes = marker.toByteArray(Charsets.UTF_8)
                return dex.any { it.indexOfSubsequence(bytes) >= 0 }
            }

            val phasePrefix =
                "__autojs6_r2_external_natural_phase_timeout_"
            val fixtureMarkers = listOf(
                "externalNaturalParseTimeoutEvidence",
                "R2ExternalNaturalEmitTimeoutProjectFixture",
                "EXTERNAL_NATURAL_PARSE_TIMEOUT_PRECONDITION_PASS",
                "EXTERNAL_NATURAL_PARSE_TIMEOUT_BASELINE_PASS",
                "EXTERNAL_NATURAL_PARSE_TIMEOUT_WINDOW_PASS",
                "EXTERNAL_NATURAL_PARSE_TIMEOUT_FAULT_PASS",
                "EXTERNAL_NATURAL_PARSE_TIMEOUT_SUCCESSOR_PASS",
                "EXTERNAL_NATURAL_PARSE_TIMEOUT_MATRIX_PASS",
                "preSequenceImports=",
                "postSequenceImports=",
                "postSequenceRoot=",
                "strictIntersection=true faultAttempts=0",
                "Natural parse request was rejected without retry:",
                "phase=parse progressSequence=",
                "334b1a3a940a741105c5222ca6c189c72dfb6c0ea994a72b86013df1fdbf72ef",
                "18f79093d9a09c3111740e282a3ad642576230c142d6b7d7c61bb9c7950ca038",
                "0b504753f68a345d6aa82eeb174f6d9c63eec3cb21b7810ed09f69e59296be25",
            )
            listOf("debug", "release").forEach { variant ->
                val apkFile = singleVariantApk(project, variant)
                val dex = dexBytes(apkFile)
                check(dex.isNotEmpty()) {
                    "External natural parse $variant fixture APK contains no DEX."
                }
                fixtureMarkers.forEach { marker ->
                    check(containsMarker(dex, marker)) {
                        "External natural parse $variant fixture APK is missing: $marker"
                    }
                }
            }

            val evidenceMarkers = listOf(
                "${phasePrefix}enabled",
                "${phasePrefix}receipt_available",
                "${phasePrefix}request_id",
                "${phasePrefix}host_request_id",
                "${phasePrefix}outcome",
                "${phasePrefix}created_before_callback",
                "${phasePrefix}phase_values",
                "${phasePrefix}phase_sequence_two_nanos_since_request_start",
                "${phasePrefix}phase_completed_nanos_since_request_start",
                "${phasePrefix}termination_stop_cause",
                "${phasePrefix}termination_phase_at_host_stop_selection",
                "${phasePrefix}termination_phase_at_page_termination",
                "${phasePrefix}termination_progress_sequence",
                "${phasePrefix}termination_progress_completed_units",
                "${phasePrefix}termination_progress_total_units",
                "${phasePrefix}termination_terminated_worker_generation",
                "${phasePrefix}termination_replacement_worker_generation",
                "${phasePrefix}input_sent_frame_count",
                "${phasePrefix}input_total_payload_bytes",
                "${phasePrefix}input_opened_cursor_count",
                "${phasePrefix}input_closed_cursor_count",
                "${phasePrefix}input_released",
            )
            listOf(
                "r2CapacityEvidenceDebug",
                "r2CapacityEvidenceRelease",
            ).forEach { variant ->
                val apkFile = singleVariantApk(project(":app"), variant)
                val dex = dexBytes(apkFile)
                check(dex.isNotEmpty()) {
                    "External natural parse $variant evidence APK contains no DEX."
                }
                evidenceMarkers.forEach { marker ->
                    check(containsMarker(dex, marker)) {
                        "External natural parse $variant evidence APK is missing: $marker"
                    }
                }
            }

            val normalRelease = singleVariantApk(project(":app"), "release")
            val normalReleaseDex = dexBytes(normalRelease)
            listOf(
                phasePrefix,
                "${phasePrefix}request_id",
                "${phasePrefix}created_before_callback",
                "${phasePrefix}termination_progress_sequence",
                "${phasePrefix}input_released",
            ).forEach { marker ->
                check(!containsMarker(normalReleaseDex, marker)) {
                    "Normal Release retained external natural parse receipt key: $marker"
                }
            }

            val mappingFile =
                project(":app").layout.buildDirectory.file(
                    "outputs/mapping/r2CapacityEvidenceRelease/mapping.txt",
                ).get().asFile
            check(mappingFile.isFile && mappingFile.length() > 0L) {
                "External natural parse minified evidence Release mapping is missing."
            }
        }
    }

val verifyR2ShippingReleaseNaturalParseTimeoutSourcePolicy =
    tasks.register("verifyR2ShippingReleaseNaturalParseTimeoutSourcePolicy") {
        group = "verification"
        description =
            "Statically verifies bounded public phase/work progress and the normal/shipping " +
                "Release external-UID Binder natural parse-timeout matrix."
        dependsOn(verifyR2ExternalNaturalParseTimeoutSourcePolicy)
        dependsOn(":plugin-api:typescript-api:testDebugUnitTest")
        inputs.files(
            r2PublicPreviewInstrumentationSource,
            r2PublicProgressSource,
            r2ExternalNaturalEmitTimeoutProjectFixtureSource,
            r2CompilationCoordinatorSource,
            r2CompilerHostSource,
            r2ProjectExecutionPhaseSource,
            typeScriptR2ContractSource,
            r2PluginMetadataSource,
        )

        doLast {
            fun requireMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "Shipping Release natural parse $label is missing: $marker"
                    }
                }
            }

            fun method(
                source: String,
                signature: String,
            ): String {
                val start = source.indexOf(signature)
                check(start >= 0) {
                    "Shipping Release natural parse method is missing: $signature"
                }
                val end = source.indexOf("\n    private ", start + signature.length)
                    .takeIf { index -> index >= 0 }
                    ?: source.length
                return source.substring(start, end)
            }

            val instrumentation =
                r2PublicPreviewInstrumentationSource.asFile.readText(Charsets.UTF_8)
            val publicProgress =
                r2PublicProgressSource.asFile.readText(Charsets.UTF_8)
            val coordinator =
                r2CompilationCoordinatorSource.asFile.readText(Charsets.UTF_8)
            val host = r2CompilerHostSource.asFile.readText(Charsets.UTF_8)
            val phaseModel =
                r2ProjectExecutionPhaseSource.asFile.readText(Charsets.UTF_8)
            val contract =
                typeScriptR2ContractSource.asFile.readText(Charsets.UTF_8)

            requireMarkers(
                label = "additive public progress contract",
                source = contract,
                markers = listOf(
                    "const val PROGRESS_SCHEMA_REVISION = 1",
                    "const val KEY_PROGRESS_SCHEMA_REVISION = \"progressSchemaRevision\"",
                    "const val KEY_PROGRESS_KIND = \"progressKind\"",
                    "const val PROGRESS_KIND_PHASE = \"phase\"",
                    "const val PROGRESS_KIND_WORK = \"work\"",
                    "const val KEY_PHASE_STATE = \"phaseState\"",
                    "const val KEY_PROGRESS_SEQUENCE = \"progressSequence\"",
                    "const val KEY_COMPLETED_UNITS = \"completedUnits\"",
                    "const val KEY_TOTAL_UNITS = \"totalUnits\"",
                    "elapsedRealtimeNanosSinceRequestStart",
                    "const val MAX_PROGRESS_EVENTS_PER_COMPILATION = 14",
                ),
            )
            requireMarkers(
                label = "native-observed event model",
                source = phaseModel,
                markers = listOf(
                    "internal sealed interface R2ProjectExecutionEvent",
                    "data class Phase(",
                    "val state: R2ProjectExecutionPhaseState",
                    "data class Work(",
                    "val progress: R2ProjectExecutionPhaseProgress",
                    "observedAtElapsedRealtimeNanos",
                ),
            )
            requireMarkers(
                label = "strict host correlation before publication",
                source = host,
                markers = listOf(
                    "projectExecutionObserver: ((R2ProjectExecutionEvent) -> Unit)? = null",
                    "observedEvent = R2ProjectExecutionEvent.Phase(",
                    "observedEvent = R2ProjectExecutionEvent.Work(",
                    "pending.projectExecutionObserver?.invoke(event)",
                    "Project execution phase is unexpected or out of sequence.",
                    "Project execution progress is unexpected or out of sequence.",
                ),
            )

            val publication = method(coordinator, "private fun publishProgress(")
            requireMarkers(
                label = "primitive request-start progress publication",
                source = publication,
                markers = listOf(
                    "event.observedAtElapsedRealtimeNanos",
                    "job.startedAtElapsedRealtimeNanos",
                    "job.callbackArmed",
                    "job.callbackBinder.isBinderAlive",
                    "TypeScriptR2Contract.MAX_PROGRESS_EVENTS_PER_COMPILATION",
                    "job.publishedProgressEventCount += 1",
                    "TypeScriptCompilerContract.KEY_REQUEST_ID",
                    "TypeScriptR2Contract.KEY_PROGRESS_SCHEMA_REVISION",
                    "TypeScriptR2Contract.KEY_PHASE",
                    "KEY_ELAPSED_REALTIME_NANOS_SINCE_REQUEST_START",
                    "TypeScriptCompilerContract.KEY_ELAPSED_MS",
                    "TypeScriptR2Contract.PROGRESS_KIND_PHASE",
                    "TypeScriptR2Contract.KEY_PHASE_STATE",
                    "TypeScriptR2Contract.PROGRESS_KIND_WORK",
                    "TypeScriptR2Contract.KEY_PROGRESS_SEQUENCE",
                    "TypeScriptR2Contract.KEY_COMPLETED_UNITS",
                    "TypeScriptR2Contract.KEY_TOTAL_UNITS",
                    "callback.onProgress(progress)",
                ),
            )
            listOf("path", "diagnostic", "sourceText", "outputText").forEach { forbidden ->
                check(forbidden !in publication) {
                    "Public progress publication leaked forbidden payload authority: $forbidden"
                }
            }

            requireMarkers(
                label = "strict fixture-side public progress parser",
                source = publicProgress,
                markers = listOf(
                    "bundles.size()",
                    "MAX_PROGRESS_EVENTS_PER_COMPILATION",
                    "requireExactKeys(bundle, phaseKeys())",
                    "requireExactKeys(bundle, workKeys())",
                    "elapsedNanos >= previousElapsedNanos",
                    "completedUnits == (totalUnits - 1) / divisor + 1",
                    "requireSuccessfulNaturalParse(",
                    "requireTimedOutNaturalParse(",
                    "events.size() == 8",
                    "events.get(4)",
                    "events.get(5)",
                ),
            )

            val entry = method(
                instrumentation,
                "private void runShippingReleaseNaturalParseTimeoutEvidence(",
            )
            requireMarkers(
                label = "normal Release runtime attestation",
                source = entry,
                markers = listOf(
                    "Process.myUid() != currentApplication.uid",
                    "PackageManager.SIGNATURE_MATCH",
                    "ApplicationInfo.FLAG_TEST_ONLY",
                    "ApplicationInfo.FLAG_DEBUGGABLE",
                    "Natural parse target is a TEST_ONLY evidence build, not normal Release.",
                    "Natural parse target is debuggable, not normal Release.",
                    "PermissionInfo.PROTECTION_SIGNATURE",
                    "serviceInfo.exported",
                    "awaitCleanupEvidenceSession(",
                    "false",
                    "!session.telemetryRequired && session.identity == null",
                    "!key.startsWith(NATURAL_PHASE_TIMEOUT_PREFIX)",
                    "!key.startsWith(CLEANUP_TELEMETRY_PREFIX)",
                    "runShippingReleaseNaturalParseTimeoutMatrix(context, session)",
                    "requireLiveSameBinder(session)",
                ),
            )

            val matrix = method(
                instrumentation,
                "private static void runShippingReleaseNaturalParseTimeoutMatrix(",
            )
            requireMarkers(
                label = "two-plus-three-plus-one shipping matrix",
                source = matrix,
                markers = listOf(
                    "NATURAL_PARSE_PRECONDITION_RUNS == 2",
                    "NATURAL_PARSE_BASELINE_RUNS == 3",
                    "NATURAL_PARSE_FAULT_ATTEMPTS == 1",
                    "runShippingReleaseNaturalParseSuccessfulRun(",
                    "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_PRECONDITION_PASS",
                    "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_BASELINE_PASS",
                    "calibrateNaturalParseDeadline(",
                    "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_WINDOW_PASS",
                    "runShippingReleaseNaturalParseFault(",
                    "shipping-release-natural-parse-successor",
                    "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_SUCCESSOR_PASS",
                    "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_MATRIX_PASS",
                    "privateReceipt=false pluginTestOnly=false",
                    "pluginDebuggable=false sameBinder=true",
                ),
            )
            check(
                Regex("""runShippingReleaseNaturalParseFault\(""")
                    .findAll(matrix)
                    .count() == 1 &&
                    "while (" !in matrix &&
                    "requestStop(" !in matrix &&
                    "workerSpin" !in matrix &&
                    "EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY" !in matrix,
            ) {
                "Shipping Release natural parse matrix gained retry, stop, spin, or expanded scope."
            }

            val successful = method(
                instrumentation,
                "private static NaturalParseTiming\n" +
                    "            runShippingReleaseNaturalParseSuccessfulRun(",
            )
            requireMarkers(
                label = "public-progress successful baseline",
                source = successful,
                markers = listOf(
                    "createParse(",
                    "TypeScriptCompilerContract.MAX_TIMEOUT_MS",
                    "acknowledgeAdmission(admission)",
                    "callback.awaitTerminal(CALLBACK_WAIT_MS)",
                    "callback.assertTerminalFollowsAllProgress()",
                    "verifyExternalNaturalEmitOutput(",
                    "R2PublicProgress.parse(",
                    "R2PublicProgress.requireSuccessfulNaturalParse(",
                    "awaitCleanupEvidence(",
                    "window.sequenceTwoElapsedNanos",
                    "window.completedElapsedNanos",
                ),
            )
            val fault = method(
                instrumentation,
                "private static void runShippingReleaseNaturalParseFault(",
            )
            requireMarkers(
                label = "single public-progress timeout fault",
                source = fault,
                markers = listOf(
                    "NATURAL_PARSE_FAULT_ATTEMPTS == 1",
                    "calibration.timeoutMillis",
                    "acknowledgeAdmission(admission)",
                    "callback.awaitTerminal(CALLBACK_WAIT_MS)",
                    "callback.assertTerminalFollowsAllProgress()",
                    "TypeScriptCompilerContract.OUTCOME_TIMED_OUT",
                    "TypeScriptCompilerContract.ERROR_TIMEOUT",
                    "assertNoCompilationPayload(",
                    "assertSyncedEmptyOutput(",
                    "R2PublicProgress.requireTimedOutNaturalParse(",
                    "sequenceTwo.elapsedNanos",
                    "awaitCleanupEvidence(",
                    "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_FAULT_PASS",
                    "privateReceipt=false sameBinder=true",
                ),
            )
            listOf(successful, fault).forEach { run ->
                listOf(
                    "requireNaturalPhaseReceipt(",
                    "assertSuccessfulNaturalPhaseReceipt(",
                    "assertTimedOutNaturalParsePhaseReceipt(",
                    "NATURAL_PHASE_KEY_RECEIPT_AVAILABLE",
                    "workerSpin",
                    "requestStop(",
                    "EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY",
                ).forEach { forbidden ->
                    check(forbidden !in run) {
                        "Shipping Release run retained private/test authority: $forbidden"
                    }
                }
            }

            requireMarkers(
                label = "exclusive instrumentation mode",
                source = instrumentation,
                markers = listOf(
                    "ARG_SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_EVIDENCE",
                    "\"shippingReleaseNaturalParseTimeoutEvidence\"",
                    "shippingReleaseNaturalParseTimeoutEvidenceRequested",
                    "Shipping Release natural parse timeout must run in its exclusive ",
                    "runShippingReleaseNaturalParseTimeoutEvidence()",
                    "timeout evidence passed: bounded public phase/work ",
                    "progress, two successful preconditions",
                ),
            )
        }
    }

val verifyR2ShippingReleaseNaturalParseTimeoutApks =
    tasks.register("verifyR2ShippingReleaseNaturalParseTimeoutApks") {
        group = "verification"
        description =
            "Verifies the bounded public progress markers in normal Release and the " +
                "shipping natural parse runner in both TEST_ONLY fixture APKs."
        dependsOn(verifyR2ShippingReleaseNaturalParseTimeoutSourcePolicy)
        dependsOn(verifyR2ExternalNaturalParseTimeoutApks)
        dependsOn(verifyR2PublicPreviewApkManifestShape)
        dependsOn(verifyR2PublicPreviewSigningConfiguration)
        dependsOn(":app:assembleRelease", "assembleDebug", "assembleRelease")

        doLast {
            fun dexBytes(apkFile: File): List<ByteArray> =
                ZipFile(apkFile).use { apk ->
                    apk.entries().asSequence()
                        .filter { entry ->
                            !entry.isDirectory &&
                                entry.name.matches(Regex("""classes(?:\d+)?\.dex"""))
                        }
                        .map { entry ->
                            apk.getInputStream(entry).use { input -> input.readBytes() }
                        }
                        .toList()
                }

            fun containsMarker(
                dex: List<ByteArray>,
                marker: String,
            ): Boolean {
                val bytes = marker.toByteArray(Charsets.UTF_8)
                return dex.any { candidate -> candidate.indexOfSubsequence(bytes) >= 0 }
            }

            val normalRelease = singleVariantApk(project(":app"), "release")
            val normalReleaseDex = dexBytes(normalRelease)
            check(normalReleaseDex.isNotEmpty()) {
                "Normal Release APK contains no DEX entries."
            }
            listOf(
                "progressSchemaRevision",
                "progressKind",
                "phaseState",
                "progressSequence",
                "completedUnits",
                "totalUnits",
                "elapsedRealtimeNanosSinceRequestStart",
                "R2 progress escaped its per-compilation event bound.",
                "R2 progress preceded the public request-start boundary.",
            ).forEach { marker ->
                check(containsMarker(normalReleaseDex, marker)) {
                    "Normal Release APK is missing bounded public progress marker: $marker"
                }
            }
            val privatePrefix = "__autojs6_r2_external_natural_phase_timeout_"
            check(!containsMarker(normalReleaseDex, privatePrefix)) {
                "Normal Release retained the private natural-phase receipt namespace."
            }

            val fixtureMarkers = listOf(
                "shippingReleaseNaturalParseTimeoutEvidence",
                "R2PublicProgress",
                "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_PRECONDITION_PASS",
                "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_BASELINE_PASS",
                "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_WINDOW_PASS",
                "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_FAULT_PASS",
                "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_SUCCESSOR_PASS",
                "SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_MATRIX_PASS",
                "Natural parse target is a TEST_ONLY evidence build, not normal Release.",
                "Natural parse target is debuggable, not normal Release.",
                "publicProgress=true",
                "privateReceipt=false pluginTestOnly=false",
                "334b1a3a940a741105c5222ca6c189c72dfb6c0ea994a72b86013df1fdbf72ef",
                "18f79093d9a09c3111740e282a3ad642576230c142d6b7d7c61bb9c7950ca038",
                "0b504753f68a345d6aa82eeb174f6d9c63eec3cb21b7810ed09f69e59296be25",
            )
            listOf("debug", "release").forEach { variant ->
                val fixture = singleVariantApk(project, variant)
                val fixtureDex = dexBytes(fixture)
                check(fixtureDex.isNotEmpty()) {
                    "Shipping natural parse $variant fixture contains no DEX."
                }
                fixtureMarkers.forEach { marker ->
                    check(containsMarker(fixtureDex, marker)) {
                        "Shipping natural parse $variant fixture is missing: $marker"
                    }
                }
            }

            val normalReleaseMapping =
                project(":app").layout.buildDirectory.file(
                    "outputs/mapping/release/mapping.txt",
                ).get().asFile
            check(normalReleaseMapping.isFile && normalReleaseMapping.length() > 0L) {
                "Normal/shipping Release R8 mapping is missing."
            }
        }
    }

tasks.register("verifyR2PublicPreviewFixture") {
    group = "verification"
    description =
        "Builds and verifies the external-UID public R2 preview fixture through the " +
            "normal/shipping Release natural parse-timeout evidence slice."
    dependsOn(verifyR2PublicPreviewApkManifestShape)
    dependsOn(verifyR2PublicPreviewSigningConfiguration)
    dependsOn(verifyR2P3bB2cExternalSourcePolicy)
    dependsOn(verifyR2P3bB2cExternalApks)
    dependsOn(verifyR2P3bB2dExternalExact64SourcePolicy)
    dependsOn(verifyR2P3bB2dExternalExact64Apks)
    dependsOn(verifyR2P3bB2fExternalPlusOneCleanupSourcePolicy)
    dependsOn(verifyR2P3bB2fExternalPlusOneCleanupApks)
    dependsOn(verifyR2P3bB2kExternalNaturalEmitTimeoutSourcePolicy)
    dependsOn(verifyR2P3bB2kExternalNaturalEmitTimeoutApks)
    dependsOn(verifyR2ExternalNaturalSemanticTimeoutSourcePolicy)
    dependsOn(verifyR2ExternalNaturalSemanticTimeoutApks)
    dependsOn(verifyR2ExternalNaturalParseTimeoutSourcePolicy)
    dependsOn(verifyR2ExternalNaturalParseTimeoutApks)
    dependsOn(verifyR2ShippingReleaseNaturalParseTimeoutSourcePolicy)
    dependsOn(verifyR2ShippingReleaseNaturalParseTimeoutApks)
    dependsOn(":app:verifyR2ReleaseCompatibilityApks")
    dependsOn("assembleDebug", "assembleRelease", "lintDebug", "lintRelease")
}
