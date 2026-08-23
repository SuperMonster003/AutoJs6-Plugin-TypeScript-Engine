import com.android.build.api.variant.FilterConfiguration
import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.autojs.build.DownloadR2DeclarationDistributionTarball
import org.autojs.build.DownloadTypeScriptDistributionTarball
import org.autojs.build.GenerateR2DeclarationDistribution
import org.autojs.build.GenerateTypeScriptDistribution
import org.autojs.build.VerifyR1OptionalPluginMatrix
import org.autojs.build.VerifyR2DeclarationDistribution
import org.autojs.build.VerifyTypeScriptDistribution
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test

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
val pluginVersionName = projectProperties.getProperty("VERSION_NAME")
val requiredHostVersion = projectProperties.getProperty("AUTOJS6_HOST_VERSION_BUILD").toInt()
val androidNdkVersion = projectProperties.getProperty("NDK_VERSION")
val androidCmakeVersion = projectProperties.getProperty("CMAKE_VERSION")
val generatedTypeScriptAssetsDirectory =
    layout.buildDirectory.dir("generated/typescriptCompilerAssets/assets")
val generatedTypeScriptDistributionDirectory =
    generatedTypeScriptAssetsDirectory.map { it.dir("typescript/$typeScriptVersion") }
val generatedR2DeclarationAssetsDirectory =
    layout.buildDirectory.dir("generated/r2DeclarationAssetsV2/assets")
val generatedR2DeclarationDistributionDirectory =
    generatedR2DeclarationAssetsDirectory.map { it.dir("r2-declarations/v2") }
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

fun requireAdvertisedTypeScriptCompilerCapabilities(
    metadataSource: String,
    owner: String,
) {
    listOf(
        listOf(
            "COMPILER_AVAILABLE",
            "ADVERTISED_COMPILER_AVAILABLE",
            "compilerAvailable",
            "true",
        ),
        listOf(
            "PROJECT_COMPILATION",
            "ADVERTISED_PROJECT_COMPILATION",
            "projectCompilation",
            "true",
        ),
        listOf(
            "SEMANTIC_DIAGNOSTICS",
            "ADVERTISED_SEMANTIC_DIAGNOSTICS",
            "semanticDiagnostics",
            "true",
        ),
        listOf(
            "SOURCE_MAP_V3",
            "ADVERTISED_SOURCE_MAP_V3",
            "sourceMapV3",
            "true",
        ),
        listOf(
            "NODE_RUNTIME_REQUIRED",
            "ADVERTISED_NODE_RUNTIME_REQUIRED",
            "nodeRuntimeRequired",
            "false",
        ),
    ).forEach { (capability, constant, variable, expected) ->
        check(
            Regex(
                """internal\s+const\s+val\s+$constant\s*=\s*$expected""",
            ).containsMatchIn(metadataSource) &&
                Regex("""val\s+$variable\s*=\s*$constant""").containsMatchIn(metadataSource) &&
                Regex(
                    """putBoolean\(\s*TypeScriptPluginCapabilityKeys\.$capability,""" +
                        """\s*$variable\s*,?\s*\)""",
                    RegexOption.DOT_MATCHES_ALL,
                ).containsMatchIn(metadataSource),
        ) {
            "$owner must advertise TypeScriptPluginCapabilityKeys.$capability=$expected " +
                "through its single validated advertisement tuple."
        }
    }
}

android {
    namespace = globalApplicationId
    compileSdk = versions.sdkVersionCompile
    ndkVersion = androidNdkVersion

    defaultConfig {
        applicationId = globalApplicationId
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = versions.appVersionCode
        versionName = versions.appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TYPESCRIPT_VERSION", "\"$typeScriptVersion\"")
        buildConfigField("int", "REQUIRED_HOST_VERSION", "$requiredHostVersion")
        buildConfigField("boolean", "R2_EXTERNAL_CAPACITY_EVIDENCE", "false")
        buildConfigField(
            "String",
            "R2_EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY",
            "\"\"",
        )
        buildConfigField(
            "String",
            "R2_EXTERNAL_CAPACITY_EVIDENCE_STORED_SCOPE",
            "\"\"",
        )
        buildConfigField(
            "String",
            "R2_EXTERNAL_CAPACITY_EVIDENCE_DEFLATED_SCOPE",
            "\"\"",
        )
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
        create("r2CapacityEvidenceDebug") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "R2_EXTERNAL_CAPACITY_EVIDENCE", "true")
            buildConfigField(
                "String",
                "R2_EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY",
                "\"__autojs6_r2_external_capacity_evidence_scope\"",
            )
            buildConfigField(
                "String",
                "R2_EXTERNAL_CAPACITY_EVIDENCE_STORED_SCOPE",
                "\"stored-container-exact-64-v1\"",
            )
            buildConfigField(
                "String",
                "R2_EXTERNAL_CAPACITY_EVIDENCE_DEFLATED_SCOPE",
                "\"deflated-expanded-exact-64-v1\"",
            )
        }
        create("r2CapacityEvidenceRelease") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "R2_EXTERNAL_CAPACITY_EVIDENCE", "true")
            buildConfigField(
                "String",
                "R2_EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY",
                "\"__autojs6_r2_external_capacity_evidence_scope\"",
            )
            buildConfigField(
                "String",
                "R2_EXTERNAL_CAPACITY_EVIDENCE_STORED_SCOPE",
                "\"stored-container-exact-64-v1\"",
            )
            buildConfigField(
                "String",
                "R2_EXTERNAL_CAPACITY_EVIDENCE_DEFLATED_SCOPE",
                "\"deflated-expanded-exact-64-v1\"",
            )
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = androidCmakeVersion
        }
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(generatedTypeScriptAssetsDirectory.get().asFile.absolutePath)
            assets.directories.add(
                generatedR2DeclarationAssetsDirectory.get().asFile.absolutePath,
            )
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
            val evidenceChannel = when (variant.name) {
                "r2CapacityEvidenceDebug" -> "-r2-capacity-evidence-debug"
                "r2CapacityEvidenceRelease" -> "-r2-capacity-evidence-release"
                else -> ""
            }
            val outputFileNameProperty = output.javaClass.methods.firstOrNull {
                it.name == "getOutputFileName" && it.parameterTypes.isEmpty()
            }?.invoke(output) as? Property<*>

            @Suppress("UNCHECKED_CAST")
            (outputFileNameProperty as? Property<String>)?.set(
                output.versionName.map { versionName ->
                    val version = versionName.replace("\\s".toRegex(), "-")
                    (
                        "${rootProject.name}-v$version" +
                            "$evidenceChannel-$architecture.apk"
                        ).lowercase()
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

val r2DiagnosticStopEmitFixture = layout.projectDirectory.file(
    "src/test/resources/r2-compiler-diagnostics/x2-v1.json",
)
val r2DiagnosticStopEmitFixtureTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2DiagnosticStopEmitFixtureTest.kt",
)
val r2StableFailureCatalogFixture = layout.projectDirectory.file(
    "src/test/resources/r2-config-resolution-failures/x3-v1.json",
)
val r2StableFailureCatalogHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-m2-x3-stable-failures.js",
)
val r2InputArchiveAuthorityHarness = layout.projectDirectory.file(
    "src/test/scripts/r2-input-archive-authority.js",
)
val r2StableFailureCatalogFixtureTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2StableFailureCatalogFixtureTest.kt",
)
val r2ApiContractSource = rootProject.layout.projectDirectory.file(
    "plugin-api/typescript-api/src/main/java/org/autojs/plugin/typescript/api/" +
        "TypeScriptR2Contract.kt",
)
val r2ApiContractTestSource = rootProject.layout.projectDirectory.file(
    "plugin-api/typescript-api/src/test/java/org/autojs/plugin/typescript/api/" +
        "TypeScriptR2ContractTest.kt",
)
val r2OrdinaryInputQuotaDecisionFixture = layout.projectDirectory.file(
    "src/test/resources/r2-input-quota/d1-v1.json",
)
val r2OrdinaryInputQuotaDecisionFixtureTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2OrdinaryInputQuotaDecisionFixtureTest.kt",
)
val r2OrdinaryOutputQuotaDecisionFixture = layout.projectDirectory.file(
    "src/test/resources/r2-output-quota/d2-v1.json",
)
val r2OrdinaryOutputQuotaDecisionFixtureTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2OrdinaryOutputQuotaDecisionFixtureTest.kt",
)
val r2CapabilityPromotionCompatibilityDecisionFixture = layout.projectDirectory.file(
    "src/test/resources/r2-capability-promotion/d3-v1.json",
)
val r2CapabilityPromotionCompatibilityDecisionFixtureTestSource =
    layout.projectDirectory.file(
        "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "R2CapabilityPromotionCompatibilityDecisionFixtureTest.kt",
    )
val r2InputArchiveSchemaV1Fixture = layout.projectDirectory.file(
    "src/test/resources/r2-input-archive-schema/s1-v1.json",
)
val r2InputArchiveSchemaV1FixtureTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2InputArchiveSchemaV1FixtureTest.kt",
)
val r2OutputArchiveSchemaV1Fixture = layout.projectDirectory.file(
    "src/test/resources/r2-output-archive-schema/s2-v1.json",
)
val r2OutputArchiveSchemaV1FixtureTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2OutputArchiveSchemaV1FixtureTest.kt",
)
val r2DiagnosticsSchemaV1Fixture = layout.projectDirectory.file(
    "src/test/resources/r2-diagnostics-schema/s3-v1.json",
)
val r2DiagnosticsSchemaV1FixtureTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2DiagnosticsSchemaV1FixtureTest.kt",
)

val r2DeclarationDistributionLockFile =
    rootProject.layout.projectDirectory.file("r2-declarations.lock.json")
val r2DeclarationNodeSourceDirectory =
    rootProject.layout.projectDirectory.dir("r2-declaration-sources/node")
val downloadR2DeclarationDistributionTarball =
    tasks.register<DownloadR2DeclarationDistributionTarball>(
        "downloadR2DeclarationDistributionTarball",
    ) {
        lockFile.set(r2DeclarationDistributionLockFile)
        tarballFile.set(
            layout.buildDirectory.file(
                "downloads/r2-declarations/autojs6-dts-2.1.3.tgz",
            ),
        )
    }
val generateR2DeclarationDistribution =
    tasks.register<GenerateR2DeclarationDistribution>(
        "generateR2DeclarationDistribution",
    ) {
        dependsOn(generateTypeScriptDistribution)
        lockFile.set(r2DeclarationDistributionLockFile)
        rhinoTarballFile.set(
            downloadR2DeclarationDistributionTarball.flatMap { it.tarballFile },
        )
        nodeSourceDirectory.set(r2DeclarationNodeSourceDirectory)
        typescriptDistributionDirectory.set(generatedTypeScriptDistributionDirectory)
        distributionDirectory.set(generatedR2DeclarationDistributionDirectory)
        declarationReleaseVersion.set(pluginVersionName)
    }
val verifyR2DeclarationDistribution =
    tasks.register<VerifyR2DeclarationDistribution>(
        "verifyR2DeclarationDistribution",
    ) {
        dependsOn(generateR2DeclarationDistribution)
        lockFile.set(r2DeclarationDistributionLockFile)
        rhinoTarballFile.set(
            downloadR2DeclarationDistributionTarball.flatMap { it.tarballFile },
        )
        nodeSourceDirectory.set(r2DeclarationNodeSourceDirectory)
        typescriptDistributionDirectory.set(generatedTypeScriptDistributionDirectory)
        distributionDirectory.set(
            generateR2DeclarationDistribution.flatMap { it.distributionDirectory },
        )
        declarationReleaseVersion.set(pluginVersionName)
    }

tasks.withType<Test>().configureEach {
    if (name == "testDebugUnitTest") {
        // The single S1-6 supply-chain gate owns distribution acquisition and verification.
        dependsOn("verifyCompilerDistributionLock")
        inputs.dir(generatedR2DeclarationDistributionDirectory)
        inputs.dir(generatedTypeScriptDistributionDirectory)
        systemProperty(
            "r2.generatedDeclarationDistributionDir",
            generatedR2DeclarationDistributionDirectory.get().asFile.absolutePath,
        )
        systemProperty(
            "r2.generatedTypeScriptDistributionDir",
            generatedTypeScriptDistributionDirectory.get().asFile.absolutePath,
        )
    }
}

val verifyR2ProjectCompilerDesktop =
    tasks.register<Exec>("verifyR2ProjectCompilerDesktop") {
        group = "verification"
        description =
            "Runs the provisional R2 project compiler and P3b-a capacity golden " +
                "in a constrained desktop Node worker shim."
        dependsOn(generateTypeScriptDistribution)
        val workerFile =
            compilerHostSourceAssetDirectory.file("compiler-worker.js")
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        val fixtureFile = layout.projectDirectory.file(
            "src/test/resources/r2-project-compiler/provisional-rhino.json",
        )
        val harnessFile = layout.projectDirectory.file(
            "src/test/scripts/verify-r2-project-compiler.js",
        )
        inputs.files(
            workerFile,
            compilerFile,
            fixtureFile,
            r2DiagnosticStopEmitFixture,
            harnessFile,
            r2InputArchiveAuthorityHarness,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            "--expose-gc",
            "--max-old-space-size=512",
            harnessFile.asFile.absolutePath,
            workerFile.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            fixtureFile.asFile.absolutePath,
            typeScriptVersion,
        )
    }

val verifyR2M2C8DeclarationProgramLayering =
    tasks.register<Exec>("verifyR2M2C8DeclarationProgramLayering") {
        group = "verification"
        description =
            "Verifies the R2-M2-C8 common/Rhino/Node declaration profiles with real " +
                "TypeScript Program constructors."
        dependsOn(verifyR2DeclarationDistribution)
        val harnessFile = layout.projectDirectory.file(
            "src/test/scripts/verify-r2-declaration-layering.js",
        )
        val typeScriptLibDirectory = generatedTypeScriptDistributionDirectory.map {
            it.dir("lib")
        }
        inputs.file(harnessFile)
        inputs.dir(generatedR2DeclarationDistributionDirectory)
        inputs.dir(typeScriptLibDirectory)
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            "--max-old-space-size=512",
            harnessFile.asFile.absolutePath,
            generatedR2DeclarationDistributionDirectory.get().asFile.absolutePath,
            typeScriptLibDirectory.get().asFile.absolutePath,
            pluginVersionName,
        )
    }

val verifyR2M2C8DeclarationWorkerLayering =
    tasks.register<Exec>("verifyR2M2C8DeclarationWorkerLayering") {
        group = "verification"
        description =
            "Verifies the R2-M2-C8 Worker compiler-only Rhino root and Node-layer fences."
        dependsOn(generateTypeScriptDistribution)
        val harnessFile = layout.projectDirectory.file(
            "src/test/scripts/verify-r2-declaration-layer-worker.js",
        )
        val workerFile = compilerHostSourceAssetDirectory.file("compiler-worker.js")
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        inputs.files(harnessFile, r2InputArchiveAuthorityHarness, workerFile, compilerFile)
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            harnessFile.asFile.absolutePath,
            workerFile.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            typeScriptVersion,
        )
    }

val verifyR2M2P1ProvisionalNodeNextProfileDesktop =
    tasks.register<Exec>("verifyR2M2P1ProvisionalNodeNextProfileDesktop") {
        group = "verification"
        description =
            "Verifies the config-none R2-M2-P1 .ts -> .js NodeNext profile with real " +
                "TypeScript 6.0.3 and generated common+Node declarations."
        dependsOn(verifyR2DeclarationDistribution)
        val harnessFile = layout.projectDirectory.file(
            "src/test/scripts/verify-r2-provisional-nodenext-profile.js",
        )
        val fixtureFile = layout.projectDirectory.file(
            "src/test/resources/r2-project-compiler/provisional-nodenext-p1.json",
        )
        val workerFile = compilerHostSourceAssetDirectory.file("compiler-worker.js")
        val typeScriptLibDirectory = generatedTypeScriptDistributionDirectory.map {
            it.dir("lib")
        }
        inputs.files(harnessFile, r2InputArchiveAuthorityHarness, fixtureFile, workerFile)
        inputs.dir(generatedR2DeclarationDistributionDirectory)
        inputs.dir(typeScriptLibDirectory)
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            "--max-old-space-size=512",
            harnessFile.asFile.absolutePath,
            workerFile.asFile.absolutePath,
            generatedR2DeclarationDistributionDirectory.get().asFile.absolutePath,
            typeScriptLibDirectory.get().asFile.absolutePath,
            fixtureFile.asFile.absolutePath,
            typeScriptVersion,
        )
    }

val r2M2P2NodeModuleOutputPolicyFingerprint =
    "5c2294f0ee9d6d25bd2132b4ea24866d0a5f9caaa521a63bf81ffe1d897f6ce7"
val r2M2P2NodeModuleOutputPolicySource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2NodeModuleOutputPolicy.kt",
)
val r2M2P2NodeModuleOutputPolicyFixture = layout.projectDirectory.file(
    "src/test/resources/r2-node-module-output/policy-v1.json",
)
val r2M2P2GeneratedModuleMappingSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2GeneratedModuleMapping.kt",
)
val r2M2P2GeneratedModuleMappingFixture = layout.projectDirectory.file(
    "src/test/resources/r2-generated-module-mapping/parity-v1.json",
)
val r2M2P2GeneratedModuleMappingPolicy = layout.projectDirectory.file(
    "src/test/scripts/r2-generated-module-mapping-policy.js",
)
val r2M2P2GeneratedModuleMappingHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-generated-module-mapping-parity.js",
)
val r2M2P2NodePackageFormatCatalogSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2NodePackageFormatCatalog.kt",
)
val r2M2P2NodePackageFormatCatalogTest = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2NodePackageFormatCatalogTest.kt",
)
val r2M2P2NodePackageFormatCatalogFixture = layout.projectDirectory.file(
    "src/test/resources/r2-node-package-format/catalog-v1.json",
)
val r2M2P2NodePackageFormatCatalogPolicy = layout.projectDirectory.file(
    "src/test/scripts/r2-node-package-format-catalog-policy.js",
)
val r2M2P2NodePackageFormatCatalogHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-node-package-format-catalog-parity.js",
)
val r2M2P2NodePackageFormatWorkerTransportFixture = layout.projectDirectory.file(
    "src/test/resources/r2-node-package-format/transport-v1.json",
)
val r2M2P2NodePackageFormatWorkerTransportHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-node-package-format-worker-transport.js",
)
val r2M2P2P5NodePackageTargetPolicySource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2NodePackageTargetPolicyV2.kt",
)
val r2M2P2P5NodePackageTargetPolicyTest = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2NodePackageTargetPolicyV2Test.kt",
)
val r2M2P2P5NodePackageTargetPolicyFixture = layout.projectDirectory.file(
    "src/test/resources/r2-package-targets/policy-v2.json",
)
val r2M2P2P5NodeResolutionKernelSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2NodeResolutionKernelV2.kt",
)
val r2M2P2P5NodeResolutionKernelTest = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2NodeResolutionKernelV2Test.kt",
)
val r2M2P2P5NodeResolutionParityFixture = layout.projectDirectory.file(
    "src/test/resources/r2-node-resolution/parity-v2.json",
)
val r2M2P2P5NodeResolutionParityHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-node-resolution-v2-parity.js",
)
val r2M2P2P6NodeNextProgramFixture = layout.projectDirectory.file(
    "src/test/resources/r2-project-compiler/provisional-nodenext-p2.json",
)
val r2M2P2P6NodeNextProgramHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-nodenext-revision2-program.js",
)
val r2M2P2P6NodeNextP1RegressionFixture = layout.projectDirectory.file(
    "src/test/resources/r2-project-compiler/provisional-nodenext-p1.json",
)
val r2M2P2P6PackageMetadataPolicyFixture = layout.projectDirectory.file(
    "src/test/resources/r2-package-metadata/policy-v1.json",
)

val verifyR2M2P2GeneratedModuleMappingDesktop =
    tasks.register<Exec>("verifyR2M2P2GeneratedModuleMappingDesktop") {
        group = "verification"
        description =
            "Verifies the pure R2-M2-P2 revisioned module/output mapping with an " +
                "independent JavaScript mirror and shared instance-bound fixture."
        inputs.files(
            r2M2P2NodeModuleOutputPolicyFixture,
            r2M2P2GeneratedModuleMappingSource,
            r2M2P2GeneratedModuleMappingFixture,
            r2M2P2GeneratedModuleMappingPolicy,
            r2M2P2GeneratedModuleMappingHarness,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            r2M2P2GeneratedModuleMappingHarness.asFile.absolutePath,
            r2M2P2GeneratedModuleMappingFixture.asFile.absolutePath,
            r2M2P2NodeModuleOutputPolicyFingerprint,
        )
    }

val verifyR2M2P2P3aNodePackageFormatCatalogDesktop =
    tasks.register<Exec>("verifyR2M2P2P3aNodePackageFormatCatalogDesktop") {
        group = "verification"
        description =
            "Independently rebuilds the pure R2-M2-P2-3A C5-bound Node package-format " +
                "catalog with a shared Kotlin/JavaScript fixture."
        inputs.files(
            r2M2P2NodeModuleOutputPolicySource,
            r2M2P2NodeModuleOutputPolicyFixture,
            r2M2P2NodePackageFormatCatalogSource,
            r2M2P2NodePackageFormatCatalogTest,
            r2M2P2NodePackageFormatCatalogFixture,
            r2M2P2NodePackageFormatCatalogPolicy,
            r2M2P2NodePackageFormatCatalogHarness,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            r2M2P2NodePackageFormatCatalogHarness.asFile.absolutePath,
            r2M2P2NodePackageFormatCatalogFixture.asFile.absolutePath,
        )
    }

val verifyR2M2P2P3bNodePackageFormatWorkerTransportDesktop =
    tasks.register<Exec>("verifyR2M2P2P3bNodePackageFormatWorkerTransportDesktop") {
        group = "verification"
        description =
            "Verifies the exact R2-M2-P2-3B Node revision-2 package-format frame and " +
                "Worker transport-only authority without activating a TypeScript Program."
        dependsOn(generateTypeScriptDistribution)
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        val workerFile = compilerHostSourceAssetDirectory.file("compiler-worker.js")
        val packageMetadataPolicyFixture = layout.projectDirectory.file(
            "src/test/resources/r2-package-metadata/policy-v1.json",
        )
        inputs.files(
            workerFile,
            compilerFile,
            r2M2P2NodePackageFormatWorkerTransportFixture,
            r2M2P2NodePackageFormatWorkerTransportHarness,
            r2InputArchiveAuthorityHarness,
            r2M2P2NodePackageFormatCatalogFixture,
            r2M2P2NodePackageFormatCatalogPolicy,
            packageMetadataPolicyFixture,
            r2M2P2NodeModuleOutputPolicyFixture,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            r2M2P2NodePackageFormatWorkerTransportHarness.asFile.absolutePath,
            workerFile.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            r2M2P2NodePackageFormatWorkerTransportFixture.asFile.absolutePath,
            r2M2P2NodePackageFormatCatalogFixture.asFile.absolutePath,
            packageMetadataPolicyFixture.asFile.absolutePath,
            r2M2P2NodeModuleOutputPolicyFixture.asFile.absolutePath,
            typeScriptVersion,
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

private val expectedR2FileDescriptorNativeEntries =
    setOf(
        "lib/arm64-v8a/libautojs6_r2_fd_flags.so",
        "lib/armeabi-v7a/libautojs6_r2_fd_flags.so",
        "lib/x86/libautojs6_r2_fd_flags.so",
        "lib/x86_64/libautojs6_r2_fd_flags.so",
    )
private val r2FileDescriptorNativeSymbol =
    (
        "Java_io_github_supermonster003_autojs6_plugin_typescript_" +
            "R2FileDescriptorOpenFlagsNative_getFileStatusFlags"
        ).toByteArray(Charsets.US_ASCII)

private fun verifyR2FileDescriptorNativeBridgeInApk(apkFile: File) {
    ZipFile(apkFile).use { apk ->
        val entries =
            apk.entries().asSequence()
                .filterNot { entry -> entry.isDirectory }
                .toList()
        val nativeEntries =
            entries.filter { entry -> entry.name.startsWith("lib/") }
        check(nativeEntries.map { entry -> entry.name }.toSet() == expectedR2FileDescriptorNativeEntries) {
            "APK R2 file-descriptor native bridge set mismatch: " +
                "expected=$expectedR2FileDescriptorNativeEntries, " +
                "actual=${nativeEntries.map { entry -> entry.name }}"
        }
        nativeEntries.forEach { entry ->
            val bytes = apk.getInputStream(entry).use { input -> input.readBytes() }
            check(bytes.containsByteSequence(r2FileDescriptorNativeSymbol)) {
                "APK native bridge ${entry.name} is missing its exact JNI export."
            }
        }

        val dexBytes =
            entries.filter { entry ->
                entry.name.matches(Regex("""classes(?:\d+)?\.dex"""))
            }.map { entry ->
                apk.getInputStream(entry).use { input -> input.readBytes() }
            }
        check(dexBytes.isNotEmpty()) {
            "APK contains no DEX entries: $apkFile"
        }
        check(
            dexBytes.none { dex ->
                dex.containsByteSequence(
                    "/proc/self/fdinfo".toByteArray(Charsets.US_ASCII),
                )
            },
        ) {
            "APK must not use procfs to inspect caller file-descriptor flags."
        }
        check(
            dexBytes.any { dex ->
                dex.containsByteSequence(
                    "R2FileDescriptorOpenFlagsNative".toByteArray(Charsets.US_ASCII),
                )
            },
        ) {
            "APK is missing the kept API 24-29 file-descriptor bridge."
        }
    }
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

private fun verifyR2SupervisorManifestShape(manifestFile: File) {
    val androidNamespace = "http://schemas.android.com/apk/res/android"
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
    val document = factory.newDocumentBuilder().parse(manifestFile)
    val permissions = document.getElementsByTagName("uses-permission")
    check(
        (0 until permissions.length).any { index ->
            permissions.item(index)
                .attributes
                .getNamedItemNS(androidNamespace, "name")
                ?.nodeValue == "android.permission.RECEIVE_BOOT_COMPLETED"
        },
    ) {
        "Supervisor persisted-job manifest must request RECEIVE_BOOT_COMPLETED."
    }
    val services = document.getElementsByTagName("service")
    val supervisorServices = (0 until services.length)
        .map(services::item)
        .filter { service ->
            service.attributes
                .getNamedItemNS(androidNamespace, "name")
                ?.nodeValue
                ?.endsWith(".R2CompilerProcessSupervisorJobService") == true
        }
    check(supervisorServices.size == 1) {
        "Manifest must contain exactly one R2 supervisor JobService."
    }
    val supervisor = supervisorServices.single()
    fun attribute(name: String): String? =
        supervisor.attributes.getNamedItemNS(androidNamespace, name)?.nodeValue
    check(attribute("process") == ":typescript_supervisor")
    check(attribute("exported") == "true")
    check(attribute("permission") == "android.permission.BIND_JOB_SERVICE")
    check(attribute("directBootAware") == "false")
    check(
        (0 until supervisor.childNodes.length).none { index ->
            supervisor.childNodes.item(index).nodeName == "intent-filter"
        },
    ) {
        "R2 supervisor JobService must not expose an intent filter."
    }
    val liveOwnerServices = (0 until services.length)
        .map(services::item)
        .filter { service ->
            service.attributes
                .getNamedItemNS(androidNamespace, "name")
                ?.nodeValue
                ?.endsWith(".R2CompilerProcessSupervisorService") == true
        }
    check(liveOwnerServices.size == 1) {
        "Manifest must contain exactly one R2 live supervisor owner Service."
    }
    val liveOwner = liveOwnerServices.single()
    fun liveOwnerAttribute(name: String): String? =
        liveOwner.attributes.getNamedItemNS(androidNamespace, name)?.nodeValue
    check(liveOwnerAttribute("process") == ":typescript_supervisor")
    check(liveOwnerAttribute("exported") == "false")
    check(liveOwnerAttribute("permission") == null)
    check(liveOwnerAttribute("directBootAware") == "false")
    check(
        (0 until liveOwner.childNodes.length).none { index ->
            liveOwner.childNodes.item(index).nodeName == "intent-filter"
        },
    ) {
        "R2 live supervisor owner Service must not expose an intent filter."
    }
}

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
    check(
        workerSource.contains("""importScripts("typescript.js");""") &&
            Regex("""\bimportScripts\b""").findAll(workerSource).count() == 1,
    ) {
        "Compiler worker must load only the versioned compiler through the typescript.js alias."
    }
    check(workerSource.contains("ts.createIncrementalProgram({")) {
        "R2 project compilation must use TypeScript createIncrementalProgram."
    }
    check(!workerSource.contains("ts.sys")) {
        "R2 project compilation must not fall back to the host TypeScript system."
    }
    mapOf(
        "fetch" to Regex("""\bfetch\b"""),
        "XMLHttpRequest" to Regex("""\bXMLHttpRequest\b"""),
        "WebSocket" to Regex("""\bWebSocket\b"""),
        "EventSource" to Regex("""\bEventSource\b"""),
    ).forEach { (label, pattern) ->
        check(!pattern.containsMatchIn(workerSource)) {
            "Compiler worker must not access the network through $label."
        }
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
    verifyR2SupervisorManifestShape(manifestFile)
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
    verifyR2FileDescriptorNativeBridgeInApk(apkFile)
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
            "R2 stop-cause telemetry" to
                "__autojs6_r2_debug_stop_cause_selected",
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
        val privateR2FailClosedRequestMarkers = linkedMapOf(
            "R2 private compilation request sentinel" to
                "__autojs6_r2_debug_compilation",
            "R2 private input digest request sentinel" to
                "__autojs6_r2_debug_input_archive_sha256",
            "R2 private request-manifest digest sentinel" to
                "__autojs6_r2_debug_request_manifest_sha256",
            "R2 private profile revision request sentinel" to
                "__autojs6_r2_debug_profile_revision",
            "R2 Worker spin request sentinel" to
                "__autojs6_r2_debug_worker_spin_milliseconds",
            "R2 Worker spin phase request sentinel" to
                "__autojs6_r2_debug_worker_spin_phase",
            "R2 expanded Worker capacity request sentinel" to
                "__autojs6_r2_debug_expanded_worker_capacity",
            "R2 terminal cleanup delay request sentinel" to
                "__autojs6_r2_debug_terminal_cleanup_delay_milliseconds",
            "R2 private control-plane rejection" to
                "Debug R2 profile authority is incomplete, malformed, or inactive.",
        )
        privateR2FailClosedRequestMarkers.forEach { (label, markerText) ->
            val marker = markerText.toByteArray(Charsets.UTF_8)
            check(dexBytes.any { dex -> dex.containsByteSequence(marker) }) {
                "$label is missing from ${apkFile.name}; private R2 keys could escape their " +
                    "fail-closed request plane."
            }
        }
        val productionR2Markers = linkedMapOf(
            "R2 public input digest key" to "inputArchiveSha256",
            "R2 public config schema key" to "configSchemaRevision",
            "R2 public config kind key" to "configKind",
            "R2 public cancellation operation" to "cancellationOperation",
            "R2 production pipeline executor" to "typescript-r2-compilation",
            "R2 public metadata rejection" to
                "Public R2 metadata is incomplete or malformed.",
            "R2 post-return admission barrier" to
                "Compilation admission was not acknowledged.",
            "R2 startup transient cleanup failure" to
                "Compiler startup could not prove private transient cleanup.",
            "R2 startup transient residue failure" to
                "R2 private transient cleanup left",
        )
        productionR2Markers.forEach { (label, markerText) ->
            val marker = markerText.toByteArray(Charsets.UTF_8)
            check(dexBytes.any { dex -> dex.containsByteSequence(marker) }) {
                "$label is missing from ${apkFile.name}; the production R2 path was stripped."
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
    verifyR2SupervisorManifestShape(mergedManifestFile)
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

@Suppress("UNCHECKED_CAST")
private fun verifyR2DeclarationDistributionInApk(
    apkFile: File,
    distributionDirectory: File,
    declarationReleaseVersion: String,
) {
    val manifestFile = distributionDirectory.resolve("distribution.json")
    check(manifestFile.isFile) {
        "R2 declaration distribution manifest does not exist: $manifestFile"
    }
    val manifestBytes = manifestFile.readBytes()
    val manifest = JsonSlurper().parse(manifestFile) as Map<String, Any?>
    check((manifest["schemaVersion"] as? Number)?.toInt() == 2)
    val release = manifest["release"] as? Map<String, Any?>
        ?: error("R2 declaration manifest does not contain its release identity.")
    check(release.keys == setOf("package", "version"))
    check(release["package"] == "autojs6-plugin-typescript-engine-declarations")
    check(release["version"] == declarationReleaseVersion)
    check((manifest["fingerprint"] as? String)?.matches(Regex("[0-9a-f]{64}")) == true)
    val manifestFiles = manifest["files"] as? List<Map<String, Any?>>
        ?: error("R2 declaration manifest does not contain a files array.")
    val expectedFiles = manifestFiles.associate { record ->
        val path = record["path"] as? String
            ?: error("R2 declaration file is missing its path.")
        check(
            path.isNotBlank() &&
                !path.startsWith('/') &&
                '\\' !in path &&
                path.split('/').none { component -> component.isBlank() || component == ".." },
        ) {
            "R2 declaration file has a non-canonical path: $path"
        }
        val size = (record["size"] as? Number)?.toLong()
            ?: error("R2 declaration file $path is missing its size.")
        val sha256 = record["sha256"] as? String
            ?: error("R2 declaration file $path is missing its SHA-256.")
        check(size >= 0L)
        check(sha256.matches(Regex("[0-9a-f]{64}")))
        path to (size to sha256)
    }
    check(expectedFiles.size == manifestFiles.size) {
        "R2 declaration manifest contains duplicate paths."
    }
    check(expectedFiles.size == 116) {
        "Expected 116 R2 declaration distribution files, found ${expectedFiles.size}."
    }
    listOf(
        "layers/common/binding.json",
        "layers/rhino/index.d.ts",
        "layers/node/index.d.ts",
        "layers/node/package.json",
        "licenses/rhino/MPL-2.0.txt",
        "licenses/node/MPL-2.0.txt",
        "notices/rhino-source.txt",
        "notices/node-source.txt",
    ).forEach { requiredPath ->
        check(requiredPath in expectedFiles) {
            "R2 declaration manifest is missing $requiredPath."
        }
    }

    val assetPrefix = "assets/r2-declarations/v2/"
    ZipFile(apkFile).use { apk ->
        val distributionEntries = apk.entries().asSequence().filter { entry ->
            !entry.isDirectory && entry.name.startsWith(assetPrefix)
        }.toList()
        val duplicates = distributionEntries
            .groupingBy { entry -> entry.name }
            .eachCount()
            .filterValues { count -> count != 1 }
        check(duplicates.isEmpty()) {
            "APK contains duplicate R2 declaration entries: $duplicates"
        }
        val expectedEntryNames = (
            expectedFiles.keys.map { path -> "$assetPrefix$path" } +
                "${assetPrefix}distribution.json"
            ).toSet()
        val actualEntryNames = distributionEntries.map { entry -> entry.name }.toSet()
        check(actualEntryNames == expectedEntryNames) {
            "APK R2 declaration distribution mismatch: " +
                "missing=${expectedEntryNames - actualEntryNames}, " +
                "extra=${actualEntryNames - expectedEntryNames}"
        }

        val packagedManifest = requireNotNull(apk.getEntry("${assetPrefix}distribution.json"))
        val packagedManifestBytes = apk.getInputStream(packagedManifest).use { it.readBytes() }
        check(MessageDigest.isEqual(manifestBytes, packagedManifestBytes)) {
            "APK R2 declaration distribution.json differs from the verified manifest."
        }
        expectedFiles.forEach { (path, expected) ->
            val entry = requireNotNull(apk.getEntry("$assetPrefix$path"))
            val bytes = apk.getInputStream(entry).use { it.readBytes() }
            check(bytes.size.toLong() == expected.first) {
                "APK R2 declaration file size mismatch for $path."
            }
            check(bytes.sha256String() == expected.second) {
                "APK R2 declaration file SHA-256 mismatch for $path."
            }
        }
    }
}

fun registerR2DeclarationDistributionApkVerification(
    taskName: String,
    assembleTaskName: String,
    apkDirectoryPath: String,
) = tasks.register(taskName) {
    group = "verification"
    description =
        "Verifies the exact R2 declaration distribution inventory in the $assembleTaskName APK."
    dependsOn(assembleTaskName)
    dependsOn(verifyR2DeclarationDistribution)
    val apkDirectory = layout.buildDirectory.dir(apkDirectoryPath)
    inputs.dir(apkDirectory)
    inputs.dir(generatedR2DeclarationDistributionDirectory)
    doLast {
        verifyR2DeclarationDistributionInApk(
            apkFile = resolveSingleApkOutput(apkDirectory.get().asFile),
            distributionDirectory =
                generatedR2DeclarationDistributionDirectory.get().asFile,
            declarationReleaseVersion = pluginVersionName,
        )
    }
}

val verifyDebugR2DeclarationDistributionApk =
    registerR2DeclarationDistributionApkVerification(
        taskName = "verifyDebugR2DeclarationDistributionApk",
        assembleTaskName = "assembleDebug",
        apkDirectoryPath = "outputs/apk/debug",
    )
val verifyReleaseR2DeclarationDistributionApk =
    registerR2DeclarationDistributionApkVerification(
        taskName = "verifyReleaseR2DeclarationDistributionApk",
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
val r2HostProjectOperationSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2HostProjectOperation.kt",
)
val compilerHostManifest = layout.projectDirectory.file("src/main/AndroidManifest.xml")
val r2PublicPreviewServiceSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "TypeScriptCompilerPluginService.kt",
)
val r2PublicPreviewMetadataSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "TypeScriptPluginMetadata.kt",
)
val r2PublicPreviewInstrumentationSource = rootProject.layout.projectDirectory.file(
    "r2-public-preview/src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "publicpreview/R2PublicPreviewInstrumentation.java",
)
val r2ProjectArchiveSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ProjectArchive.kt",
)
val r2InputArchiveSchemaV1Source = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2InputArchiveSchemaV1.kt",
)
val r2TrustedDeclarationsSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2TrustedDeclarations.kt",
)
val r2DeclarationDistributionSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2DeclarationDistribution.kt",
)
val r2FileBackedProjectSnapshotSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2FileBackedProjectSnapshot.kt",
)
val r2ProjectSnapshotSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ProjectSnapshot.kt",
)
val r2ProjectSourceKindAdmissionSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ProjectSourceKindAdmission.kt",
)
val r2ProjectFramedTransportSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ProjectFramedTransport.kt",
)
val r2CompilationProbeSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2CompilationProbeProtocol.kt",
)
val r2ControlledTsConfigPolicySource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ControlledTsConfigPolicy.kt",
)
val r2ControlledTsConfigPolicyTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ControlledTsConfigPolicyTest.kt",
)
val r2CompilationProbeProtocolTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2CompilationProbeProtocolTest.kt",
)
val r2ControlledTsConfigPolicyFixture = layout.projectDirectory.file(
    "src/test/resources/r2-controlled-tsconfig/policy-v2.json",
)
val r2ControlledTsConfigPolicyHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-controlled-tsconfig-policy.js",
)
val r2ControlledTsConfigParserFixture = layout.projectDirectory.file(
    "src/test/resources/r2-controlled-tsconfig/parser-v2.json",
)
val r2ControlledTsConfigParserHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-controlled-tsconfig-parser.js",
)
val r2ControlledTsConfigEffectiveOptionsSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ControlledTsConfigEffectiveOptions.kt",
)
val r2ControlledTsConfigEffectiveOptionsTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ControlledTsConfigEffectiveOptionsTest.kt",
)
val r2ControlledTsConfigEffectiveOptionsFixture = layout.projectDirectory.file(
    "src/test/resources/r2-controlled-tsconfig/effective-options-v1.json",
)
val r2ControlledTsConfigEffectiveOptionsHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-controlled-tsconfig-effective-options.js",
)
val r2CompilerEscapePolicySource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2CompilerEscapePolicy.kt",
)
val r2CompilerEscapePolicyTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2CompilerEscapePolicyTest.kt",
)
val r2CompilerEscapePolicyFixture = layout.projectDirectory.file(
    "src/test/resources/r2-controlled-tsconfig/escape-policy-v2.json",
)
val r2CompilerEscapePolicyHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-compiler-escape-policy.js",
)
val r2PackageMetadataPolicySource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2PackageMetadataPolicy.kt",
)
val r2PackageMetadataPolicyTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2PackageMetadataPolicyTest.kt",
)
val r2PackageMetadataPolicyFixture = layout.projectDirectory.file(
    "src/test/resources/r2-package-metadata/policy-v1.json",
)
val r2PackageMetadataTransportHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-package-metadata-transport.js",
)
val r2NodeResolutionKernelSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2NodeResolutionKernel.kt",
)
val r2NodeResolutionKernelTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2NodeResolutionKernelTest.kt",
)
val r2NodeResolutionParityFixture = layout.projectDirectory.file(
    "src/test/resources/r2-node-resolution/parity-v1.json",
)
val r2NodeResolutionParityHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-node-resolution-parity.js",
)
val r2NodeResolutionWorkerHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-node-resolution-worker.js",
)
val r2PackageTargetWorkerTransportHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-package-target-worker-transport.js",
)
val r2NodeResolutionProgramHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-node-resolution-program.js",
)
val r2PackageTargetPolicySource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2PackageTargetPolicy.kt",
)
val r2PackageTargetPolicyTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2PackageTargetPolicyTest.kt",
)
val r2PackageTargetPolicyFixture = layout.projectDirectory.file(
    "src/test/resources/r2-package-targets/policy-v1.json",
)
val r2ControlledRootSelectionHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-controlled-root-selection.js",
)
val r2PrivateOutputArchiveTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2PrivateOutputArchiveTest.kt",
)
val r0CompilationGateSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R0CompilationGate.kt",
)
val r0CompilationGateTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R0CompilationGateTest.kt",
)
val binderMetadataValidatorSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "BinderMetadataValidator.kt",
)
val typeScriptR2ContractSource = rootProject.layout.projectDirectory.file(
    "plugin-api/typescript-api/src/main/java/org/autojs/plugin/typescript/api/" +
        "TypeScriptR2Contract.kt",
)
val r2CompilationProfileAuthoritySource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2CompilationProfileAuthority.kt",
)
val r2ExternalCapacityEvidenceProtocolSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ExternalCapacityEvidenceProtocol.kt",
)
val r2ZipArchiveValidatorSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/archive/" +
        "ZipArchiveV1Validator.kt",
)
val r2CompilerWorkerSource =
    compilerHostSourceAssetDirectory.file("compiler-worker.js")
val r2CompilerHostPageSource =
    compilerHostSourceAssetDirectory.file("compiler-host.js")
val r2CompilerHostProtocolSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "CompilerHostProtocol.kt",
)
val r2ProjectExecutionPhaseSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ProjectExecutionPhase.kt",
)
val r2FileDescriptorOpenFlagsSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2FileDescriptorOpenFlags.kt",
)
val r2FileDescriptorOpenFlagsNativeSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2FileDescriptorOpenFlagsNative.java",
)
val r2FileDescriptorOpenFlagsCppSource = layout.projectDirectory.file(
    "src/main/cpp/r2_file_descriptor_open_flags.cpp",
)
val r2CompilerProcessSupervisorControlSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2CompilerProcessSupervisorControl.kt",
)
val appProguardRules = layout.projectDirectory.file("proguard-rules.pro")
val r2PrivateTransientOrphanScavengerSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2PrivateTransientOrphanScavenger.kt",
)
val r2PrivateTransientOrphanScavengerTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2PrivateTransientOrphanScavengerTest.kt",
)
val r2PrivateTransientOrphanScavengerInstrumentationSource =
    layout.projectDirectory.file(
        "src/androidTest/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "R2PrivateTransientOrphanScavengerInstrumentationTest.kt",
    )
val r2BinderCompilationInstrumentationSource = layout.projectDirectory.file(
    "src/androidTest/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2BinderCompilationInstrumentationTest.kt",
)
val r2ProjectCompilerInstrumentationSource = layout.projectDirectory.file(
    "src/androidTest/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ProjectCompilerInstrumentationTest.kt",
)
val r2AbsoluteDeadlineRegressionInstrumentationSource =
    layout.projectDirectory.file(
        "src/androidTest/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "R2AbsoluteDeadlineRegressionInstrumentationTest.kt",
    )
val r2NaturalPhaseWorkloadFixtureSource = layout.projectDirectory.file(
    "src/androidTest/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2NaturalPhaseWorkloadFixture.kt",
)
val r2NaturalPhaseTimeoutWorkloadFixtureSource =
    layout.projectDirectory.file(
        "src/androidTest/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "R2NaturalPhaseTimeoutWorkloadFixture.kt",
    )
val r2NaturalParseTimeoutWorkloadFixtureSource =
    layout.projectDirectory.file(
        "src/androidTest/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "R2NaturalParseTimeoutWorkloadFixture.kt",
    )
val r2NaturalEmitTimeoutWorkloadFixtureSource =
    layout.projectDirectory.file(
        "src/androidTest/java/io/github/supermonster003/autojs6/plugin/typescript/" +
            "R2NaturalEmitTimeoutWorkloadFixture.kt",
    )
val r2ProjectFramedTransportTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ProjectFramedTransportTest.kt",
)
val r2ProjectArchiveTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ProjectArchiveTest.kt",
)
val r2ProjectArchiveLoaderTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ProjectArchiveLoaderTest.kt",
)
val r2ProjectSnapshotTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ProjectSnapshotTest.kt",
)
val r2ProjectSourceKindAdmissionTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ProjectSourceKindAdmissionTest.kt",
)
val r2FileBackedProjectSnapshotTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2FileBackedProjectSnapshotTest.kt",
)
val r2ProjectCompilationSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ProjectCompilation.kt",
)
val r2NodeRevisionTwoResultAuthoritySource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2NodeRevisionTwoResultAuthority.kt",
)
val r2NodeRevisionTwoResultAuthorityTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2NodeRevisionTwoResultAuthorityTest.kt",
)
val r2PrivateOutputArchiveSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2PrivateOutputArchive.kt",
)
val r2OutputArchiveSchemaV1Source = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2OutputArchiveSchemaV1.kt",
)
val r2OutputArchiveSchemaV1TestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2OutputArchiveSchemaV1Test.kt",
)
val r2DiagnosticsSchemaV1Source = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2DiagnosticsSchemaV1.kt",
)
val r2DiagnosticsSchemaV1TestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2DiagnosticsSchemaV1Test.kt",
)
val r2OrdinaryOutputQuotaSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2OrdinaryOutputQuota.kt",
)
val r2OrdinaryOutputQuotaTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2OrdinaryOutputQuotaTest.kt",
)
val r2PrivateOutputQuotaIntegrationTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2PrivateOutputQuotaIntegrationTest.kt",
)
val r2CompilationCoordinatorSource = layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2CompilationCoordinator.kt",
)
val r2ProjectCompilationTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ProjectCompilationTest.kt",
)
val r2CompilationProfileAuthorityTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2CompilationProfileAuthorityTest.kt",
)
val r2ControlledTsConfigWorkerProtocolTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2ControlledTsConfigWorkerProtocolTest.kt",
)
val r2ProjectCompilerDesktopHarnessSource = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-project-compiler.js",
)
val remoteProcessResourceProbeSource = layout.projectDirectory.file(
    "src/androidTest/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "RemoteProcessResourceProbe.kt",
)
val webViewProcessMemoryProbeSource = layout.projectDirectory.file(
    "src/androidTest/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R1WebViewProcessMemoryProbe.kt",
)
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

val verifyR2M2C1ControlledTsConfigPolicy =
    tasks.register<Exec>("verifyR2M2C1ControlledTsConfigPolicy") {
        group = "verification"
        description =
            "Verifies the activated revision-2 controlled tsconfig policy and structural fences."
        dependsOn(generateTypeScriptDistribution)
        dependsOn("testDebugUnitTest")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        inputs.files(
            compilerFile,
            r2ControlledTsConfigPolicySource,
            r2ControlledTsConfigPolicyTestSource,
            r2CompilerEscapePolicySource,
            r2CompilationProbeProtocolTestSource,
            r2ControlledTsConfigPolicyFixture,
            r2ControlledTsConfigPolicyHarness,
            typeScriptR2ContractSource,
            r2CompilationProfileAuthoritySource,
            r2ProjectSourceKindAdmissionSource,
            r2ProjectArchiveSource,
            r2CompilerWorkerSource,
            r2PublicPreviewMetadataSource,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            r2ControlledTsConfigPolicyHarness.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            r2ControlledTsConfigPolicyFixture.asFile.absolutePath,
            typeScriptVersion,
        )
        doLast {
            val policySource = r2ControlledTsConfigPolicySource.asFile.readText(Charsets.UTF_8)
            listOf(
                "internal object R2ControlledTsConfigPolicy",
                "val allowedTopLevelKeys: Set<String>",
                "val allowedBooleanCompilerOptions:",
                "val profileManagedCompilerOptions: Set<String>",
                "val escapeForbiddenCompilerOptions: Set<String>",
                "val outputForbiddenCompilerOptions: Set<String>",
                "fun topLevelRejectionCode(name: String): String?",
                "fun compilerOptionRejectionCode(",
                "CONFIG_POLICY_CODE_DUPLICATE_KEY",
            ).forEach { marker ->
                check(marker in policySource) {
                    "R2-M2-C1 policy authority is missing marker: $marker"
                }
            }

            val contractSource = typeScriptR2ContractSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "const val CONFIG_KIND_TSCONFIG = \"tsconfig\"",
                "const val CONTROLLED_TSCONFIG_POLICY_REVISION = 2",
                "const val CONFIG_POLICY_CODE_ENCODING_INVALID",
                "const val CONFIG_POLICY_CODE_ENTRY_NOT_SELECTED",
            ).forEach { marker ->
                check(marker in contractSource) {
                    "R2-M2-C1 additive contract is missing marker: $marker"
                }
            }

            val authoritySource =
                r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            check(
                "const val CONFIG_KIND_NONE = TypeScriptR2Contract.CONFIG_KIND_NONE" in
                    authoritySource &&
                    "TypeScriptR2Contract.CONFIG_KIND_TSCONFIG" !in authoritySource,
            ) {
                "R2-M2-C1 must retain config-none as the frozen runtime profile below the " +
                    "activated raw-config overlay."
            }
            check(
                "compilerOptionsJson == " +
                    "R2CompilerEscapePolicy.INLINE_COMPILER_OPTIONS_JSON" in
                    r2ProjectArchiveSource.asFile.readText(Charsets.UTF_8) &&
                    "const val INLINE_COMPILER_OPTIONS_JSON = \"{}\"" in
                    r2CompilerEscapePolicySource.asFile.readText(Charsets.UTF_8),
            ) {
                "R2-M2-C1 must retain the current empty inline-options archive fence."
            }
            check(
                "configKind: \"none\"" in r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8),
            ) {
                "R2-M2-C1 must retain the current config-none Worker authority."
            }

            val admissionTestSource =
                r2CompilationProbeProtocolTestSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "fun completeControlledTsConfigMetadataCreatesExactPublicRawConfigControls()",
                "R2PublicCompilationDisposition.ACCEPTED_CONTROLLED_TSCONFIG",
                "controls.rawConfigAuthority",
                "R2PublicCompilationProtocol.controlsOrNull(",
                "configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG",
            ).forEach { marker ->
                check(marker in admissionTestSource) {
                    "R2-M2-C1 public activation test is missing marker: $marker"
                }
            }

            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M2-C1")
        }
    }

val verifyR2M2C2ControlledTsConfigParser =
    tasks.register<Exec>("verifyR2M2C2ControlledTsConfigParser") {
        group = "verification"
        description =
            "Verifies the R2-M2-C2 raw tsconfig identity, framing, parser, and " +
                "public activation fences."
        dependsOn(generateTypeScriptDistribution)
        dependsOn(verifyR2M2C1ControlledTsConfigPolicy)
        dependsOn(verifyR2ProjectCompilerDesktop)
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn("testDebugUnitTest")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        inputs.files(
            compilerFile,
            r2ControlledTsConfigParserFixture,
            r2ControlledTsConfigParserHarness,
            r2InputArchiveAuthorityHarness,
            r2ControlledTsConfigPolicyFixture,
            r2CompilerEscapePolicySource,
            typeScriptR2ContractSource,
            r2CompilationProfileAuthoritySource,
            r2ControlledTsConfigPolicySource,
            r2ProjectArchiveSource,
            r2ProjectSnapshotSource,
            r2FileBackedProjectSnapshotSource,
            r2ProjectFramedTransportSource,
            r2ProjectCompilationSource,
            r2CompilationCoordinatorSource,
            r2CompilerWorkerSource,
            r2CompilerHostPageSource,
            r2CompilerHostProtocolSource,
            compilerWebViewHostSource,
            r2PublicPreviewServiceSource,
            r2PublicPreviewMetadataSource,
            r2CompilationProbeSource,
            r2CompilationProbeProtocolTestSource,
            r2ProjectArchiveTestSource,
            r2ProjectArchiveLoaderTestSource,
            r2ProjectSnapshotTestSource,
            r2ProjectFramedTransportTestSource,
            r2ProjectCompilationTestSource,
            r2ControlledTsConfigWorkerProtocolTestSource,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            r2ControlledTsConfigParserHarness.asFile.absolutePath,
            r2CompilerWorkerSource.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            r2ControlledTsConfigParserFixture.asFile.absolutePath,
            r2ControlledTsConfigPolicyFixture.asFile.absolutePath,
            typeScriptVersion,
        )
        doLast {
            val contractSource = typeScriptR2ContractSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "const val CONTROLLED_TSCONFIG_SCHEMA_REVISION = 2",
                "const val KEY_CONFIG_BYTE_LENGTH = \"configByteLength\"",
                "const val KEY_CONFIG_SHA256 = \"configSha256\"",
                "const val KEY_CONFIG_POLICY_REVISION = \"configPolicyRevision\"",
                "const val KEY_CONFIG_POLICY_FINGERPRINT = \"configPolicyFingerprint\"",
            ).forEach { marker ->
                check(marker in contractSource) {
                    "R2-M2-C2 additive contract is missing marker: $marker"
                }
            }

            val archiveSource = r2ProjectArchiveSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "val rawConfigAuthority: R2RawProjectConfigAuthority? = null",
                "val rawConfigDescriptor: R2RawProjectConfigDescriptor?",
                "val inputArchiveSchemaRevision: Int,",
                "require(inputArchiveSchemaRevision == R2InputArchiveSchemaV1.REVISION)",
                "TOP_LEVEL_KEYS_WITH_CONFIG = TOP_LEVEL_KEYS + \"config\"",
                "MAXIMUM_CONTROLLED_TSCONFIG_SOURCE_FILES",
                "TypeScriptCompilerContract.MAX_INPUT_FILES - 2",
                "validateRawConfigEntry(",
            ).forEach { marker ->
                check(marker in archiveSource) {
                    "R2-M2-C2 archive authority is missing marker: $marker"
                }
            }

            val snapshotSource = r2ProjectSnapshotSource.asFile.readText(Charsets.UTF_8)
            val fileBackedSource =
                r2FileBackedProjectSnapshotSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "data class R2RawProjectConfigAuthority(",
                "data class R2RawProjectConfigDescriptor(",
                "fun openRawConfig(): R2ProjectFileCursor?",
                "rawConfigBytes?.fill(0)",
            ).forEach { marker ->
                check(marker in snapshotSource) {
                    "R2-M2-C2 snapshot authority is missing marker: $marker"
                }
            }
            listOf(
                "data class R2FileBackedRawProjectConfigRecord(",
                "override fun openRawConfig(): R2ProjectFileCursor?",
            ).forEach { marker ->
                check(marker in fileBackedSource) {
                    "R2-M2-C2 file-backed config cursor is missing marker: $marker"
                }
            }

            val framerSource =
                r2ProjectFramedTransportSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "\"configByteLength\" to config.size",
                "\"configSha256\" to config.sha256",
                "\"configPolicyRevision\" to config.policyRevision",
                "\"configPolicyFingerprint\" to config.policyFingerprint",
                "\"kind\" to \"projectInputConfigStart\"",
                "\"kind\" to \"projectInputConfigChunk\"",
                "\"kind\" to \"projectInputConfigEnd\"",
                "verifyAndCloseCurrentConfig()",
            ).forEach { marker ->
                check(marker in framerSource) {
                    "R2-M2-C2 native config framing is missing marker: $marker"
                }
            }

            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "const controlledTsConfigAuthority = Object.freeze({",
                "const parseControlledJsonc = (",
                "const parseControlledTsConfig = (source) =>",
                "presentTopLevelKeys: Object.freeze(",
                "case \"projectInputConfigStart\":",
                "case \"projectInputConfigChunk\":",
                "case \"projectInputConfigEnd\":",
                "kind: \"projectConfigError\"",
                "const selectControlledRootNames = (parsedConfig, files, entryPath) =>",
            ).forEach { marker ->
                check(marker in workerSource) {
                    "R2-M2-C2 Worker parser is missing marker: $marker"
                }
            }

            val hostPageSource = r2CompilerHostPageSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "case \"projectInputConfigStart\":",
                "case \"projectInputConfigChunk\":",
                "case \"projectInputConfigEnd\":",
                "case \"projectConfigError\":",
            ).forEach { marker ->
                check(marker in hostPageSource) {
                    "R2-M2-C2 host page bridge is missing marker: $marker"
                }
            }
            val hostProtocolSource =
                r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8)
            check("data class ProjectConfigError(" in hostProtocolSource) {
                "R2-M2-C2 native host protocol lacks a typed config error."
            }
            val projectOutcomeSource =
                r2ProjectCompilationSource.asFile.readText(Charsets.UTF_8)
            check("val configPolicyCode: String? = null" in projectOutcomeSource) {
                "R2-M2-C2 project outcome does not preserve the typed policy code."
            }
            val webViewHostSource = compilerWebViewHostSource.asFile.readText(Charsets.UTF_8)
            check(
                "is CompilerHostMessage.ProjectConfigError" in webViewHostSource &&
                    "configPolicyCode = message.policyCode" in webViewHostSource,
            ) {
                "R2-M2-C2 WebView host does not terminate typed config diagnostics."
            }

            val admissionTestSource =
                r2CompilationProbeProtocolTestSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "fun completeControlledTsConfigMetadataCreatesExactPublicRawConfigControls()",
                "ACCEPTED_CONTROLLED_TSCONFIG",
                "R2RawProjectConfigAuthority(",
            ).forEach { marker ->
                check(marker in admissionTestSource) {
                    "R2-M2-C2 public activation test is missing marker: $marker"
                }
            }
            val serviceSource = r2PublicPreviewServiceSource.asFile.readText(Charsets.UTF_8)
            val publicProtocolSource =
                r2CompilationProbeSource.asFile.readText(Charsets.UTF_8)
            check(
                "val r2ControlPlaneRoute = request.r2CompilationControlPlaneRoute(" in
                    serviceSource &&
                    "hasAnyMetadata(presentKeys) -> R2CompilationControlPlaneRoute.PUBLIC" in
                    publicProtocolSource,
            ) {
                "R2-M2-C2 service does not recognize every additive config identity key."
            }
            listOf(
                "profileSchemaRevision = optionalPublicR2Value<Int>(",
                "profileRevision = optionalPublicR2Value<Int>(",
                "configSchemaRevision = optionalPublicR2Value<Int>(",
                "configByteLength = optionalPublicR2Value<Long>(",
                "configPolicyRevision = optionalPublicR2Value<Int>(",
            ).forEach { marker ->
                check(marker in serviceSource) {
                    "R2-M2-C2 Bundle metadata type fence is missing marker: $marker"
                }
            }
            val publicTsConfigRejectionIndex = Regex(
                """publicR2Requested\s*&&\s*publicR2Controls == null""",
            ).find(serviceSource)?.range?.first ?: -1
            val coordinatorAdmissionIndex = serviceSource.indexOf("coordinator.admit(")
            val ownershipTransferIndex =
                serviceSource.indexOf("archiveOwnershipTransferred = admission.accepted")
            check(
                publicTsConfigRejectionIndex >= 0 &&
                    coordinatorAdmissionIndex > publicTsConfigRejectionIndex &&
                    ownershipTransferIndex > publicTsConfigRejectionIndex,
            ) {
                "R2-M2-C2 malformed public metadata rejection must precede coordinator " +
                    "admission and " +
                    "FD ownership transfer."
            }
            val synchronousPublicRejection = Regex(
                """if\s*\(\s*publicR2Requested\s*&&\s*""" +
                    """publicR2Controls == null\s*\)\s*\{\s*""" +
                    """return R0CompilationDecision\(""",
                RegexOption.DOT_MATCHES_ALL,
            )
            check(synchronousPublicRejection.containsMatchIn(serviceSource)) {
                "R2-M2-C2 malformed public metadata classification must return synchronously."
            }

            val coordinatorSource =
                r2CompilationCoordinatorSource.asFile.readText(Charsets.UTF_8)
            val failedOutcomeIndex =
                coordinatorSource.indexOf("if (!compilationOutcome.succeeded)")
            val outputStageIndex =
                coordinatorSource.indexOf("R2PrivateOutputArchive.stage(", failedOutcomeIndex)
            check(failedOutcomeIndex >= 0 && outputStageIndex > failedOutcomeIndex) {
                "R2-M2-C2 coordinator failure/output ordering markers are missing."
            }
            val failureFence = coordinatorSource.substring(
                failedOutcomeIndex,
                outputStageIndex,
            )
            check(
                "finalizeFailure(job, failure)" in failureFence &&
                    Regex("""finalizeFailure\(job, failure\)\s*return""")
                        .containsMatchIn(failureFence),
            ) {
                "R2-M2-C2 typed config failure must finalize empty output before any ZIP stage."
            }

            val authoritySource =
                r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            check(
                "const val CONFIG_KIND_NONE = TypeScriptR2Contract.CONFIG_KIND_NONE" in
                    authoritySource &&
                    "TypeScriptR2Contract.CONFIG_KIND_TSCONFIG" !in authoritySource,
            ) {
                "R2-M2-C2 must retain config-none as the runtime profile beneath raw config."
            }
            check(
                "compilerOptionsJson == " +
                    "R2CompilerEscapePolicy.INLINE_COMPILER_OPTIONS_JSON" in archiveSource &&
                    "const val INLINE_COMPILER_OPTIONS_JSON = \"{}\"" in
                    r2CompilerEscapePolicySource.asFile.readText(Charsets.UTF_8),
            ) {
                "R2-M2-C2 must retain the empty inline-options archive fence."
            }
            check("configKind: \"none\"" in workerSource) {
                "R2-M2-C2 must retain the config-none Worker authority."
            }

            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M2-C2")
        }
    }

val verifyR2M2C3ControlledTsConfigEffectiveOptions =
    tasks.register<Exec>("verifyR2M2C3ControlledTsConfigEffectiveOptions") {
        group = "verification"
        description =
            "Verifies the R2-M2-C3 controlled-tsconfig precedence, canonical effective " +
                "options/fingerprint, and public activation fences."
        dependsOn(generateTypeScriptDistribution)
        dependsOn(verifyR2M2C2ControlledTsConfigParser)
        dependsOn(verifyR2ProjectCompilerDesktop)
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("testDebugUnitTest")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        inputs.files(
            compilerFile,
            r2ControlledTsConfigEffectiveOptionsSource,
            r2ControlledTsConfigEffectiveOptionsTestSource,
            r2ControlledTsConfigEffectiveOptionsFixture,
            r2ControlledTsConfigEffectiveOptionsHarness,
            r2InputArchiveAuthorityHarness,
            r2ControlledTsConfigPolicySource,
            r2ControlledTsConfigPolicyFixture,
            r2CompilerEscapePolicySource,
            r2CompilationProfileAuthoritySource,
            typeScriptR2ContractSource,
            r2CompilerWorkerSource,
            r2CompilerHostProtocolSource,
            r2ProjectArchiveSource,
            r2PrivateOutputArchiveSource,
            r2ProjectCompilationSource,
            r2CompilationCoordinatorSource,
            r2PublicPreviewServiceSource,
            r2PublicPreviewMetadataSource,
            r2CompilationProbeProtocolTestSource,
            compilerApiAidl,
            compilerCallbackApiAidl,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            r2ControlledTsConfigEffectiveOptionsHarness.asFile.absolutePath,
            r2CompilerWorkerSource.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            r2ControlledTsConfigEffectiveOptionsFixture.asFile.absolutePath,
            r2ControlledTsConfigPolicyFixture.asFile.absolutePath,
            typeScriptVersion,
        )
        doLast {
            val expectedCanonicalOptionKeys = listOf(
                "allowJs",
                "allowSyntheticDefaultImports",
                "alwaysStrict",
                "checkJs",
                "esModuleInterop",
                "exactOptionalPropertyTypes",
                "forceConsistentCasingInFileNames",
                "ignoreDeprecations",
                "incremental",
                "inlineSourceMap",
                "inlineSources",
                "module",
                "moduleDetection",
                "moduleResolution",
                "newLine",
                "noEmitOnError",
                "noFallthroughCasesInSwitch",
                "noImplicitAny",
                "noImplicitOverride",
                "noImplicitReturns",
                "noImplicitThis",
                "noLib",
                "noPropertyAccessFromIndexSignature",
                "noUncheckedIndexedAccess",
                "noUnusedLocals",
                "noUnusedParameters",
                "outDir",
                "resolveJsonModule",
                "rootDir",
                "skipLibCheck",
                "sourceMap",
                "strict",
                "strictBindCallApply",
                "strictBuiltinIteratorReturn",
                "strictFunctionTypes",
                "strictNullChecks",
                "strictPropertyInitialization",
                "target",
                "types",
                "useDefineForClassFields",
                "useUnknownInCatchVariables",
            )
            check(expectedCanonicalOptionKeys.size == 41) {
                "R2-M2-C3 canonical option inventory must contain exactly 41 keys."
            }

            val effectiveAuthoritySource =
                r2ControlledTsConfigEffectiveOptionsSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "internal class R2ControlledTsConfigEffectiveOptions",
                "const val REVISION = 1",
                "fun resolve(",
                "CONFIG_POLICY_CODE_INVALID_VALUE",
                "resolved.getValue(\"strictNullChecks\")",
                "resolved.getValue(\"exactOptionalPropertyTypes\")",
                "resolved.getValue(\"strictPropertyInitialization\")",
                "canonicalJson.toByteArray(StandardCharsets.US_ASCII)",
            ).forEach { marker ->
                check(marker in effectiveAuthoritySource) {
                    "R2-M2-C3 native effective-options authority is missing marker: $marker"
                }
            }
            val nativeKeysStart = effectiveAuthoritySource.indexOf("val canonicalOptionKeys")
            val nativeKeysEnd = effectiveAuthoritySource.indexOf("fun resolve(", nativeKeysStart)
            check(nativeKeysStart >= 0 && nativeKeysEnd > nativeKeysStart) {
                "R2-M2-C3 native canonical option inventory is not bounded."
            }
            val nativeCanonicalKeys = Regex("""["]([^"]+)["]""")
                .findAll(effectiveAuthoritySource.substring(nativeKeysStart, nativeKeysEnd))
                .map { match -> match.groupValues[1] }
                .toList()
            check(nativeCanonicalKeys == expectedCanonicalOptionKeys) {
                "R2-M2-C3 native canonical option inventory/order drifted: " +
                    nativeCanonicalKeys.joinToString(",")
            }

            val effectiveAuthorityTestSource =
                r2ControlledTsConfigEffectiveOptionsTestSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "assertEquals(41, R2ControlledTsConfigEffectiveOptions.canonicalOptionKeys.size)",
                "fun TypeScriptInvalidStrictNullCombinationsAreTypedPolicyFailures()",
                "assertEquals(18, legacyObject.size)",
                "76a173383972b1bd8d4ffa3fffe5d51f5adecf7b84e7b16f58554c14f3c08e0b",
            ).forEach { marker ->
                check(marker in effectiveAuthorityTestSource) {
                    "R2-M2-C3 JVM authority test is missing marker: $marker"
                }
            }

            val harnessSource =
                r2ControlledTsConfigEffectiveOptionsHarness.asFile.readText(Charsets.UTF_8)
            listOf(
                "5052",
                "CONFIG_INVALID_VALUE",
                "projectConfigError",
                "getOptionsDiagnostics",
                "createHash(\"sha256\")",
            ).forEach { marker ->
                check(marker in harnessSource) {
                    "R2-M2-C3 independent TypeScript/fingerprint oracle is missing marker: $marker"
                }
            }

            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "const controlledTsConfigEffectiveOptionsRevision = 1",
                "const controlledTsConfigCanonicalOptionKeys",
                "resolveControlledTsConfigEffectiveOptions",
                "controlledEffectiveOptions",
                "canonicalJson",
                "fingerprint",
                "ts.getStrictOptionValue",
                "result.effectiveOptionsFingerprint =",
            ).forEach { marker ->
                check(marker in workerSource) {
                    "R2-M2-C3 Worker effective-options authority is missing marker: $marker"
                }
            }
            val workerKeysStart =
                workerSource.indexOf("const controlledTsConfigCanonicalOptionKeys")
            val workerKeysEnd = workerSource.indexOf("]", workerKeysStart)
            check(workerKeysStart >= 0 && workerKeysEnd > workerKeysStart) {
                "R2-M2-C3 Worker canonical option inventory is not bounded."
            }
            val workerCanonicalKeys = Regex("""["]([^"]+)["]""")
                .findAll(workerSource.substring(workerKeysStart, workerKeysEnd))
                .map { match -> match.groupValues[1] }
                .toList()
            check(workerCanonicalKeys == expectedCanonicalOptionKeys) {
                "R2-M2-C3 Worker canonical option inventory/order drifted: " +
                    workerCanonicalKeys.joinToString(",")
            }

            val configEndStart = workerSource.indexOf("const acceptProjectInputConfigEnd")
            val configEndEnd = workerSource.indexOf(
                "const acceptProjectInputFileStart",
                configEndStart,
            )
            check(configEndStart >= 0 && configEndEnd > configEndStart) {
                "R2-M2-C3 Worker config-end handler is not bounded."
            }
            val configEndSource = workerSource.substring(configEndStart, configEndEnd)
            val configEndMarkers = listOf(
                "parseControlledTsConfig(text)",
                "await resolveControlledTsConfigEffectiveOptions(parsedConfig)",
                "state.controlledConfig = parsedConfig",
                "state.controlledEffectiveOptions = effectiveOptions",
                "state.configAccepted = true",
                "acknowledgeProjectInput(message.requestId, message.sequence)",
            )
            val configEndIndexes = configEndMarkers.map(configEndSource::indexOf)
            check(
                configEndIndexes.all { index -> index >= 0 } &&
                    configEndIndexes.zipWithNext().all { (left, right) -> left < right },
            ) {
                "R2-M2-C3 parse/merge/hash/commit must finish before config-end ACK: " +
                    configEndMarkers.zip(configEndIndexes).joinToString()
            }

            val projectEndStart = workerSource.indexOf("const acceptProjectInputEnd")
            val projectEndEnd = workerSource.indexOf(
                "const acceptProjectResultAck",
                projectEndStart,
            )
            check(projectEndStart >= 0 && projectEndEnd > projectEndStart) {
                "R2-M2-C3 Worker project-end handler is not bounded."
            }
            val projectEndSource = workerSource.substring(projectEndStart, projectEndEnd)
            val rootSelectionIndex =
                projectEndSource.indexOf("rootNames = selectControlledRootNames(")
            val resultStartIndex = projectEndSource.indexOf("return beginProjectResult(")
            check(rootSelectionIndex >= 0 && resultStartIndex > rootSelectionIndex) {
                "R2-M2-C3 effective options must reach result framing only after C7 roots."
            }

            val legacyAuthoritySource =
                r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            check(
                "const val CONFIG_KIND_NONE = TypeScriptR2Contract.CONFIG_KIND_NONE" in
                    legacyAuthoritySource &&
                    "TypeScriptR2Contract.CONFIG_KIND_TSCONFIG" !in legacyAuthoritySource,
            ) {
                "R2-M2-C3 must not reinterpret the config-none profile authority."
            }
            val archiveSource = r2ProjectArchiveSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "val inputArchiveSchemaRevision: Int,",
                "require(inputArchiveSchemaRevision == R2InputArchiveSchemaV1.REVISION)",
                "TOP_LEVEL_KEYS_WITH_CONFIG = TOP_LEVEL_KEYS + \"config\"",
                "compilerOptionsJson == " +
                    "R2CompilerEscapePolicy.INLINE_COMPILER_OPTIONS_JSON",
            ).forEach { marker ->
                check(marker in archiveSource) {
                    "R2-M2-C3 input archive compatibility fence is missing marker: $marker"
                }
            }
            check(
                "const val INLINE_COMPILER_OPTIONS_JSON = \"{}\"" in
                    r2CompilerEscapePolicySource.asFile.readText(Charsets.UTF_8),
            ) {
                "R2-M2-C3 empty inline-options authority drifted."
            }

            val outputSource = r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8)
            check(
                "const val FORMAL_MANIFEST_SCHEMA = R2OutputArchiveSchemaV1.SCHEMA" in
                    outputSource &&
                    "const val FORMAL_MANIFEST_REVISION = R2OutputArchiveSchemaV1.REVISION" in
                    outputSource &&
                    "authority.profileAuthority.canonicalEffectiveOptionsJson" in outputSource &&
                    "compilation.controlledEffectiveOptions?.canonicalJson" in outputSource &&
                    "\"effectiveOptionsFingerprint\"" in outputSource &&
                    "authority.configKind == TypeScriptR2Contract.CONFIG_KIND_TSCONFIG" in
                    outputSource,
            ) {
                "R2-M2-C3 effective authority is not bound to the current controlled output path."
            }
            val hostProtocolSource = r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8)
            check(
                "parseControlledEffectiveOptions(result, optionsJson, effectiveOptions)" in
                    hostProtocolSource &&
                    "R2ControlledTsConfigEffectiveOptions.canonicalOptionKeys" in
                    hostProtocolSource,
            ) {
                "R2-M2-C3 effective authority is not bound to the current framed result."
            }

            val contractSource = typeScriptR2ContractSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "const val CONTROLLED_TSCONFIG_SCHEMA_REVISION = 2",
                "const val CONTROLLED_TSCONFIG_POLICY_REVISION = 2",
            ).forEach { marker ->
                check(marker in contractSource) {
                    "R2-M2-C3 additive config contract is missing marker: $marker"
                }
            }
            check(
                "KEY_EFFECTIVE_OPTIONS" !in contractSource &&
                    "effectiveOptionsFingerprint" !in contractSource,
            ) {
                "R2-M2-C3 caller metadata must not declare effective options or a fingerprint."
            }

            val admissionTestSource =
                r2CompilationProbeProtocolTestSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "fun completeControlledTsConfigMetadataCreatesExactPublicRawConfigControls()",
                "ACCEPTED_CONTROLLED_TSCONFIG",
                "controls.authority(validRequest()).rawConfigAuthority",
            ).forEach { marker ->
                check(marker in admissionTestSource) {
                    "R2-M2-C3 public activation test is missing marker: $marker"
                }
            }
            val serviceSource = r2PublicPreviewServiceSource.asFile.readText(Charsets.UTF_8)
            val publicTsConfigRejectionIndex = Regex(
                """publicR2Requested\s*&&\s*publicR2Controls == null""",
            ).find(serviceSource)?.range?.first ?: -1
            val coordinatorAdmissionIndex = serviceSource.indexOf("coordinator.admit(")
            val ownershipTransferIndex =
                serviceSource.indexOf("archiveOwnershipTransferred = admission.accepted")
            val synchronousPublicRejection = Regex(
                """if\s*\(\s*publicR2Requested\s*&&\s*""" +
                    """publicR2Controls == null\s*\)\s*\{\s*""" +
                    """return R0CompilationDecision\(""",
                RegexOption.DOT_MATCHES_ALL,
            )
            check(
                publicTsConfigRejectionIndex >= 0 &&
                    coordinatorAdmissionIndex > publicTsConfigRejectionIndex &&
                    ownershipTransferIndex > publicTsConfigRejectionIndex &&
                    synchronousPublicRejection.containsMatchIn(serviceSource),
            ) {
                "R2-M2-C3 malformed public metadata must reject before admission/FD ownership."
            }

            val coordinatorSource =
                r2CompilationCoordinatorSource.asFile.readText(Charsets.UTF_8)
            val failedOutcomeIndex =
                coordinatorSource.indexOf("if (!compilationOutcome.succeeded)")
            val outputStageIndex =
                coordinatorSource.indexOf("R2PrivateOutputArchive.stage(", failedOutcomeIndex)
            check(failedOutcomeIndex >= 0 && outputStageIndex > failedOutcomeIndex) {
                "R2-M2-C3 coordinator failure/output ordering markers are missing."
            }
            val failureFence = coordinatorSource.substring(failedOutcomeIndex, outputStageIndex)
            check(
                "finalizeFailure(job, failure)" in failureFence &&
                    Regex("""finalizeFailure\(job, failure\)\s*return""")
                        .containsMatchIn(failureFence),
            ) {
                "R2-M2-C3 controlled failures must finalize empty output before ZIP staging."
            }

            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M2-C3")

            check(
                parseAidlTransactionNames(
                    compilerApiAidl.asFile,
                    "ITypeScriptCompilerPlugin",
                ) == listOf(
                    "getContractVersion",
                    "getContractHash",
                    "getCompilerInfo",
                    "prewarm",
                    "startCompilation",
                    "cancelCompilation",
                ),
            ) {
                "R2-M2-C3 must not change the frozen compiler Binder transaction prefix."
            }
            check(
                parseAidlTransactionNames(
                    compilerCallbackApiAidl.asFile,
                    "ITypeScriptCompilationCallback",
                ) == listOf("onProgress", "onCompleted"),
            ) {
                "R2-M2-C3 must not change the frozen callback Binder transaction prefix."
            }
        }
    }

val verifyR2M2C4CompilerEscapeClosure =
    tasks.register<Exec>("verifyR2M2C4CompilerEscapeClosure") {
        group = "verification"
        description =
            "Verifies the R2-M2-C4 compiler escape policy, package/path fences, " +
                "isolated host, and public controlled-config admission contract."
        dependsOn(generateTypeScriptDistribution)
        dependsOn(verifyR2M2C3ControlledTsConfigEffectiveOptions)
        dependsOn(verifyR2ProjectCompilerDesktop)
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn("testDebugUnitTest")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        inputs.files(
            compilerFile,
            r2CompilerEscapePolicySource,
            r2CompilerEscapePolicyTestSource,
            r2CompilerEscapePolicyFixture,
            r2CompilerEscapePolicyHarness,
            r2InputArchiveAuthorityHarness,
            r2ControlledTsConfigPolicySource,
            r2ControlledTsConfigPolicyFixture,
            r2ControlledTsConfigEffectiveOptionsTestSource,
            r0CompilationGateSource,
            r0CompilationGateTestSource,
            binderMetadataValidatorSource,
            typeScriptR2ContractSource,
            r2CompilationProbeSource,
            r2CompilationProbeProtocolTestSource,
            r2CompilationProfileAuthoritySource,
            r2ProjectArchiveSource,
            r2ProjectSnapshotSource,
            r2FileBackedProjectSnapshotSource,
            r2ProjectArchiveTestSource,
            r2ProjectArchiveLoaderTestSource,
            r2ProjectSnapshotTestSource,
            r2ProjectFramedTransportSource,
            r2ControlledTsConfigWorkerProtocolTestSource,
            r2CompilerWorkerSource,
            r2CompilerHostPageSource,
            r2CompilerHostProtocolSource,
            compilerAssetOriginSource,
            compilerWebViewHostSource,
            compilerHostManifest,
            r2PrivateOutputArchiveSource,
            r2PublicPreviewServiceSource,
            r2PublicPreviewMetadataSource,
            compilerApiAidl,
            compilerCallbackApiAidl,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            r2CompilerEscapePolicyHarness.asFile.absolutePath,
            r2CompilerWorkerSource.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            r2CompilerEscapePolicyFixture.asFile.absolutePath,
            r2ControlledTsConfigPolicyFixture.asFile.absolutePath,
            typeScriptVersion,
        )
        doLast {
            val escapePolicyFingerprint =
                "4f42fdf38782fd8d94b40a1bf5797295698b9ba24ca0f555c30186e4b43a1e98"
            val controlledPolicyFingerprint =
                "64ef1cf36d6531f84088cec49127e6407b107ab86eeaf7f4512358fd1343598b"
            val legacyEffectiveOptionsFingerprint =
                "76a173383972b1bd8d4ffa3fffe5d51f5adecf7b84e7b16f58554c14f3c08e0b"

            val fixtureSource =
                r2CompilerEscapePolicyFixture.asFile.readText(Charsets.UTF_8)
            listOf(
                "\"fingerprint\": \"$escapePolicyFingerprint\"",
                "\"configPolicyFingerprint\": \"$controlledPolicyFingerprint\"",
                "\"inlineCompilerOptionsJson\": \"{}\"",
                "\"forbiddenPackageMetadataKeys\"",
                "\"scripts\"",
                "\"forbiddenPackageFileNames\"",
                "\"package.json\"",
                "\"forbiddenPackagePathSegments\"",
                "\"node_modules\"",
                "\"deferredNodeSourceSuffixes\"",
                "\"forbiddenWorkerNetworkGlobals\"",
                "\"primitive-additive-no-executable-authority\"",
                "\"snapshot-only-no-ts-sys\"",
            ).forEach { marker ->
                check(marker in fixtureSource) {
                    "R2-M2-C4 escape-policy fixture is missing marker: $marker"
                }
            }

            val policySource = r2CompilerEscapePolicySource.asFile.readText(Charsets.UTF_8)
            listOf(
                "internal object R2CompilerEscapePolicy",
                "const val REVISION = 2",
                "const val INLINE_COMPILER_OPTIONS_JSON = \"{}\"",
                "val escapeForbiddenCompilerOptions:",
                "val customTransformerOptionNames:",
                "val forbiddenPackageMetadataKeys:",
                "val forbiddenPackageFileNames:",
                "val forbiddenPackagePathSegments:",
                "val deferredNodeSourceSuffixes:",
                "val forbiddenWorkerNetworkGlobals:",
                "fun sourcePathRejection(path: String)",
                "R2ControlledTsConfigPolicy.escapeForbiddenCompilerOptions",
            ).forEach { marker ->
                check(marker in policySource) {
                    "R2-M2-C4 native escape authority is missing marker: $marker"
                }
            }
            check(
                legacyEffectiveOptionsFingerprint in
                    r2ControlledTsConfigEffectiveOptionsTestSource.asFile
                        .readText(Charsets.UTF_8),
            ) {
                "R2-M2-C4 must retain the legacy 18-key effective-options fingerprint."
            }

            val r0GateSource = r0CompilationGateSource.asFile.readText(Charsets.UTF_8)
            val inlineFenceIndex = r0GateSource.indexOf(
                "request.compilerOptionsJson != " +
                    "R2CompilerEscapePolicy.INLINE_COMPILER_OPTIONS_JSON",
            )
            val archiveDescriptorFenceIndex = r0GateSource.indexOf(
                "!admission.inputArchiveValid",
            )
            check(
                inlineFenceIndex >= 0 &&
                    archiveDescriptorFenceIndex > inlineFenceIndex &&
                    "compilerOptionsJson cannot establish executable compiler authority." in
                    r0GateSource,
            ) {
                "R2-M2-C4 non-empty inline options must reject before descriptor-validity rejection."
            }
            val r0GateTestSource = r0CompilationGateTestSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "fun inlineCompilerAuthorityMustBeTheExactEmptyObject()",
                "{\"plugins\":[{\"name\":\"caller-plugin\"}]}",
                "{\"customTransformers\":[\"caller-transformer.js\"]}",
            ).forEach { marker ->
                check(marker in r0GateTestSource) {
                    "R2-M2-C4 inline-options JVM test is missing marker: $marker"
                }
            }

            val serviceSource = r2PublicPreviewServiceSource.asFile.readText(Charsets.UTF_8)
            val descriptorInspectionIndex = serviceSource.indexOf(
                "ArchiveFileDescriptorValidator.validate(",
            )
            val r0GateIndex = serviceSource.indexOf("R0CompilationGate.reject(")
            val coordinatorAdmissionIndex = serviceSource.indexOf("coordinator.admit(")
            val ownershipTransferIndex =
                serviceSource.indexOf("archiveOwnershipTransferred = admission.accepted")
            check(
                descriptorInspectionIndex >= 0 &&
                    r0GateIndex > descriptorInspectionIndex &&
                    coordinatorAdmissionIndex > r0GateIndex &&
                    ownershipTransferIndex > r0GateIndex,
            ) {
                "R2-M2-C4 service may inspect descriptors without ownership, then must reject " +
                    "before coordinator admission and FD ownership."
            }

            val contractSource = typeScriptR2ContractSource.asFile.readText(Charsets.UTF_8)
            check(
                "Peers that do not understand these keys must continue to ignore them" in
                    contractSource,
            ) {
                "R2-M2-C4 must retain additive unknown-Bundle-key compatibility."
            }
            val publicProtocolSource = r2CompilationProbeSource.asFile.readText(Charsets.UTF_8)
            val publicKeysStart = publicProtocolSource.indexOf("val PUBLIC_METADATA_KEYS")
            val publicKeysEnd = publicProtocolSource.indexOf("fun controlsOrNull", publicKeysStart)
            check(publicKeysStart >= 0 && publicKeysEnd > publicKeysStart) {
                "R2-M2-C4 public metadata key authority is not bounded."
            }
            val publicKeysSource = publicProtocolSource.substring(publicKeysStart, publicKeysEnd)
            listOf("plugins", "typeRoots", "customTransformers", "scripts").forEach { key ->
                check("\"$key\"" !in publicKeysSource) {
                    "R2-M2-C4 unknown additive Binder key became compiler authority: $key"
                }
            }
            val binderSource = binderMetadataValidatorSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "!hasFileDescriptors()",
                "is Boolean",
                "is String",
                "else -> false",
            ).forEach { marker ->
                check(marker in binderSource) {
                    "R2-M2-C4 primitive Binder metadata fence is missing marker: $marker"
                }
            }

            val archiveSource = r2ProjectArchiveSource.asFile.readText(Charsets.UTF_8)
            val snapshotSource = r2ProjectSnapshotSource.asFile.readText(Charsets.UTF_8)
            val sourceKindAdmissionSource =
                r2ProjectSourceKindAdmissionSource.asFile.readText(Charsets.UTF_8)
            val fileBackedSource =
                r2FileBackedProjectSnapshotSource.asFile.readText(Charsets.UTF_8)
            check(
                "R2CompilerEscapePolicy.sourcePathRejection(path)" in
                    sourceKindAdmissionSource &&
                    "R2ProjectSourceKindAdmission.sourcePathRejection(" in archiveSource &&
                    "R2ProjectSourceKindAdmission.sourcePathRejection(" in snapshotSource,
            ) {
                "R2-M2-C4 archive/snapshot overlay no longer starts from the frozen " +
                    "source-path escape policy."
            }
            listOf(
                "R2ProjectSnapshot.validateProjectPath(",
                "record.metadata.path",
                "validateProjectPath(rootName, profileAuthority)",
                "profileAuthority = profileAuthority",
            ).forEach { marker ->
                check(marker in fileBackedSource) {
                    "R2-M2-C4 file-backed snapshot defense is missing marker: $marker"
                }
            }

            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "const controlledTsConfigEscapeForbiddenOptions = new Set([",
                "const compilerEscapeSourcePathRejectionCodes = Object.freeze({",
                "foldedFileName === \"package.json\"",
                "foldedSegments.includes(\"node_modules\")",
                "foldedFileName.endsWith(\".cts\")",
                "foldedFileName.endsWith(\".mts\")",
            ).forEach { marker ->
                check(marker in workerSource) {
                    "R2-M2-C4 Worker escape/path authority is missing marker: $marker"
                }
            }

            fun workerFunctionSource(functionName: String): String {
                val start = workerSource.indexOf("const $functionName")
                val end = workerSource.indexOf("\nconst ", start + 1)
                check(start >= 0 && end > start) {
                    "R2-M2-C4 Worker function is not bounded: $functionName"
                }
                return workerSource.substring(start, end)
            }

            listOf(
                "acceptProjectInputStart",
                "acceptProjectInputConfigStart",
                "acceptProjectInputConfigChunk",
                "acceptProjectInputConfigEnd",
                "acceptProjectInputFileStart",
                "acceptProjectInputFileChunk",
                "acceptProjectInputFileEnd",
                "acceptProjectInputEnd",
                "acceptProjectResultAck",
            ).forEach { functionName ->
                check("hasExactKeys(message" in workerFunctionSource(functionName)) {
                    "R2-M2-C4 Worker frame does not enforce exact keys: $functionName"
                }
            }
            val exactCreateProgram = Regex(
                """ts\.createIncrementalProgram\(\{\s*host,\s*options,""" +
                    """\s*rootNames:\s*vfs\.compilerRootNames,\s*\}\)""",
                RegexOption.DOT_MATCHES_ALL,
            )
            check(exactCreateProgram.findAll(workerSource).count() == 1) {
                "R2-M2-C4 Worker Program construction authority drifted."
            }
            check(
                "const compilerRootNames = rootNames.slice();" in workerSource &&
                    "result.rootNames = vfs.rootNames.slice();" in workerSource
            ) {
                "R2-M2-C4 compiler-owned Program roots must remain separate from caller roots."
            }
            check(
                Regex("""\bbuilder\.emit\(\s*\)""").findAll(workerSource).count() == 1 &&
                    !Regex("""\bbuilder\.emit\(\s*[^\s)]""").containsMatchIn(workerSource),
            ) {
                "R2-M2-C4 Worker emit must not accept custom transformer arguments."
            }
            check("ts.sys" !in workerSource && !Regex("""\brequire\s*\(""").containsMatchIn(workerSource)) {
                "R2-M2-C4 Worker must not reach TypeScript or CommonJS host I/O."
            }

            val hostPageSource = r2CompilerHostPageSource.asFile.readText(Charsets.UTF_8)
            val dynamicImportPattern = Regex("""\bimport\s*\(""")
            check(
                !dynamicImportPattern.containsMatchIn(workerSource) &&
                    !dynamicImportPattern.containsMatchIn(hostPageSource),
            ) {
                "R2-M2-C4 trusted compiler page/Worker must not use dynamic import()."
            }
            val forbiddenNetworkAuthorities = listOf(
                "fetch",
                "XMLHttpRequest",
                "WebSocket",
                "EventSource",
                "WebTransport",
                "RTCPeerConnection",
                "SharedWorker",
                "navigator.sendBeacon",
            )
            forbiddenNetworkAuthorities.forEach { authority ->
                check(authority !in workerSource && authority !in hostPageSource) {
                    "R2-M2-C4 trusted compiler assets reference network authority: $authority"
                }
            }
            check(
                Regex("""\bnew\s+Worker\s*\(""").findAll(hostPageSource).count() == 1 &&
                    Regex(
                        """new\s+Worker\s*\(\s*[\"]compiler-worker\.js[\"]\s*\)""",
                    ).containsMatchIn(hostPageSource),
            ) {
                "R2-M2-C4 compiler page must create exactly the trusted Worker."
            }
            check(
                Regex("""\bimportScripts\b""").findAll(workerSource).count() == 1 &&
                    "importScripts(\"typescript.js\");" in workerSource,
            ) {
                "R2-M2-C4 Worker must import exactly the pinned TypeScript alias."
            }

            val harnessSource =
                r2CompilerEscapePolicyHarness.asFile.readText(Charsets.UTF_8)
            listOf(
                "poisonedTsSys",
                "programConstructionCount",
                "emitArgumentCounts",
                "pathAttacks",
                "frame-injection names",
                "maliciousResult.outputs.length === 0",
            ).forEach { marker ->
                check(marker in harnessSource) {
                    "R2-M2-C4 independent Node oracle is missing marker: $marker"
                }
            }

            val webViewSource = compilerWebViewHostSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "allowFileAccess = false",
                "allowContentAccess = false",
                "blockNetworkLoads = true",
                "ServiceWorkerController.getInstance().apply",
                "CompilerAssetOrigin.forbiddenResponse()",
                "handler.cancel()",
                "request.deny()",
                "filePathCallback.onReceiveValue(null)",
                "setSupportMultipleWindows(false)",
            ).forEach { marker ->
                check(marker in webViewSource) {
                    "R2-M2-C4 WebView isolation is missing marker: $marker"
                }
            }
            check(
                Regex(
                    """override\s+fun\s+shouldOverrideUrlLoading\(.*?\)\s*:\s*Boolean\s*=\s*true""",
                    RegexOption.DOT_MATCHES_ALL,
                ).containsMatchIn(webViewSource),
            ) {
                "R2-M2-C4 WebView must reject every navigation."
            }
            check(
                ".setHttpAllowed(false)" in
                    compilerAssetOriginSource.asFile.readText(Charsets.UTF_8) &&
                    "android.permission.INTERNET" !in
                    compilerHostManifest.asFile.readText(Charsets.UTF_8),
            ) {
                "R2-M2-C4 asset origin or manifest network fence drifted."
            }

            val controlledProjectEndSource = workerFunctionSource("acceptProjectInputEnd")
            val rootSelectionIndex = controlledProjectEndSource.indexOf(
                "rootNames = selectControlledRootNames(",
            )
            check(
                rootSelectionIndex >= 0 &&
                    rootSelectionIndex <
                    controlledProjectEndSource.indexOf("return beginProjectResult("),
            ) {
                "R2-M2-C4 controlled requests must select snapshot-only roots before Program."
            }
            check(
                "R2ControlledTsConfigEffectiveOptions" in
                    r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8) &&
                    "compilation.controlledEffectiveOptions?.canonicalJson" in
                    r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8) &&
                    "authority.configKind == TypeScriptR2Contract.CONFIG_KIND_TSCONFIG" in
                    r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8),
            ) {
                "R2-M2-C4 controlled result/output must use the frozen effective authority."
            }

            val authoritySource =
                r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            check(
                "const val CONFIG_KIND_NONE = TypeScriptR2Contract.CONFIG_KIND_NONE" in
                    authoritySource &&
                    "TypeScriptR2Contract.CONFIG_KIND_TSCONFIG" !in authoritySource,
            ) {
                "R2-M2-C4 must not activate controlled tsconfig in the production profile."
            }
            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M2-C4")
            check(
                parseAidlTransactionNames(
                    compilerApiAidl.asFile,
                    "ITypeScriptCompilerPlugin",
                ) == listOf(
                    "getContractVersion",
                    "getContractHash",
                    "getCompilerInfo",
                    "prewarm",
                    "startCompilation",
                    "cancelCompilation",
                ) &&
                    parseAidlTransactionNames(
                        compilerCallbackApiAidl.asFile,
                        "ITypeScriptCompilationCallback",
                    ) == listOf("onProgress", "onCompleted"),
            ) {
                "R2-M2-C4 must not change the frozen Binder transaction prefixes."
            }

            logger.lifecycle(
                "R2-M2-C4 compiler escape closure verified: " +
                    "policy=$escapePolicyFingerprint, C1=$controlledPolicyFingerprint",
            )
        }
    }

val verifyR2M2C5ControlledPackageMetadataTransport =
    tasks.register<Exec>("verifyR2M2C5ControlledPackageMetadataTransport") {
        group = "verification"
        description =
            "Verifies the R2-M2-C5 package metadata conditional manifest shape, isolated " +
                "snapshot transport, Worker policy, and public controlled-config contract."
        dependsOn(generateTypeScriptDistribution)
        dependsOn(verifyR2M2C4CompilerEscapeClosure)
        dependsOn(verifyR2ProjectCompilerDesktop)
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn("testDebugUnitTest")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        inputs.files(
            compilerFile,
            r2PackageMetadataPolicySource,
            r2PackageMetadataPolicyTestSource,
            r2PackageMetadataPolicyFixture,
            r2PackageMetadataTransportHarness,
            r2InputArchiveAuthorityHarness,
            r2ControlledTsConfigPolicyFixture,
            r2CompilerEscapePolicySource,
            r2ProjectSourceKindAdmissionSource,
            r2ProjectArchiveSource,
            r2ProjectArchiveTestSource,
            r2ProjectArchiveLoaderTestSource,
            r2ProjectSnapshotSource,
            r2ProjectSnapshotTestSource,
            r2FileBackedProjectSnapshotSource,
            r2FileBackedProjectSnapshotTestSource,
            r2ProjectFramedTransportSource,
            r2ProjectFramedTransportTestSource,
            r2CompilerWorkerSource,
            r2CompilerHostProtocolSource,
            r2PrivateOutputArchiveSource,
            r2CompilationProfileAuthoritySource,
            r2PublicPreviewMetadataSource,
            compilerApiAidl,
            compilerCallbackApiAidl,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            r2PackageMetadataTransportHarness.asFile.absolutePath,
            r2CompilerWorkerSource.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            r2PackageMetadataPolicyFixture.asFile.absolutePath,
            r2ControlledTsConfigPolicyFixture.asFile.absolutePath,
            typeScriptVersion,
        )
        doLast {
            val packagePolicyFingerprint =
                "fab8fec29ed0f0a1de65594620e233c9a9bef2fa514dac16c3079d5570325e97"
            val emptyInventoryFingerprint =
                "5226a24ffd4ccf70a6ae0aa18b5c0f30630612a2e2edd348bf007c87e62a941d"
            val controlledPolicyFingerprint =
                "64ef1cf36d6531f84088cec49127e6407b107ab86eeaf7f4512358fd1343598b"

            val fixtureSource =
                r2PackageMetadataPolicyFixture.asFile.readText(Charsets.UTF_8)
            listOf(
                "\"schemaRevision\": 1",
                "\"policyRevision\": 1",
                "\"fingerprint\": \"$packagePolicyFingerprint\"",
                "\"safeRelativePathGrammar\": \"^[A-Za-z0-9@._/+~-]+$\"",
                "\"maximumDependencyRoots\": 128",
                "\"maximumFiles\": 256",
                "\"maximumFileBytes\": 65536",
                "\"maximumTotalBytes\": 1048576",
                "\"maximumJsonDepth\": 16",
                "\"maximumJsonNodes\": 4096",
                "\"scripts\"",
                "\"resolverMode\": \"raw-not-resolver-authority\"",
                "\"fingerprint\": \"$emptyInventoryFingerprint\"",
            ).forEach { marker ->
                check(marker in fixtureSource) {
                    "R2-M2-C5 package-policy fixture is missing marker: $marker"
                }
            }
            val forbiddenManifestRevisionKey = "manifest" + "Revision"
            check(forbiddenManifestRevisionKey !in fixtureSource) {
                "R2-M2-C5 package policy must not duplicate input-archive schema authority."
            }

            val policySource = r2PackageMetadataPolicySource.asFile.readText(Charsets.UTF_8)
            listOf(
                "internal enum class R2PackageMetadataPolicyCode",
                "internal class R2PackageMetadataPolicyException",
                "internal data class R2PackageMetadataAuthority",
                "internal data class R2PackageMetadataFileDescriptor",
                "internal class R2PackageMetadataInventory",
                "internal object R2PackageMetadataPolicy",
                "const val MAXIMUM_DEPENDENCY_ROOTS = 128",
                "const val MAXIMUM_FILES = 256",
                "const val MAXIMUM_FILE_BYTES = 64L * 1024L",
                "const val MAXIMUM_TOTAL_BYTES = 1024L * 1024L",
                "const val SAFE_RELATIVE_PATH_GRAMMAR",
                "fun validatePackageJson(bytes: ByteArray)",
                "fun isExplicitlyAllowed(",
                "PACKAGE_LIFECYCLE_FORBIDDEN",
                "raw-not-resolver-authority",
            ).forEach { marker ->
                check(marker in policySource) {
                    "R2-M2-C5 native package authority is missing marker: $marker"
                }
            }
            val forbiddenManifestRevisionConstant = "MANIFEST_" + "REVISION"
            check(
                forbiddenManifestRevisionConstant !in policySource &&
                    forbiddenManifestRevisionKey !in policySource,
            ) {
                "R2-M2-C5 package policy must expose only its own schema/policy revisions."
            }
            check(
                packagePolicyFingerprint in
                    r2PackageMetadataPolicyTestSource.asFile.readText(Charsets.UTF_8),
            ) {
                "R2-M2-C5 native package policy fingerprint is not frozen in its JVM oracle."
            }

            val archiveSource = r2ProjectArchiveSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "TOP_LEVEL_KEYS_WITH_CONFIG + \"packageMetadata\"",
                "TOP_LEVEL_KEYS + \"packageMetadata\"",
                "private val PACKAGE_METADATA_KEYS = setOf(",
                "\"schemaRevision\"",
                "\"policyRevision\"",
                "\"policyFingerprint\"",
                "\"inventoryFingerprint\"",
                "\"dependencyRoots\"",
                "\"files\"",
                "R2PackageMetadataInventory.create(dependencyRoots, packageFiles)",
                "R2PackageMetadataPolicy.validatePackageJson(",
                "R2ProjectSourceKindAdmission.sourcePathRejection(",
            ).forEach { marker ->
                check(marker in archiveSource) {
                    "R2-M2-C5 formal-v1 archive authority is missing marker: $marker"
                }
            }

            val snapshotSource = r2ProjectSnapshotSource.asFile.readText(Charsets.UTF_8)
            val fileBackedSource =
                r2FileBackedProjectSnapshotSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "val packageMetadataInventory: R2PackageMetadataInventory?",
                "fun openPackageMetadataFile(fileIndex: Int): R2ProjectFileCursor",
                "R2PackageMetadataPolicy.validatePackageJson(ownedBytes)",
                "packageMetadataBytes?.forEach { bytes -> bytes.fill(0) }",
                "R2ProjectSourceKindAdmission.sourcePathRejection(",
            ).forEach { marker ->
                check(marker in snapshotSource) {
                    "R2-M2-C5 heap snapshot isolation is missing marker: $marker"
                }
            }
            check(
                "R2CompilerEscapePolicy.sourcePathRejection(path)" in
                    r2ProjectSourceKindAdmissionSource.asFile.readText(Charsets.UTF_8),
            ) {
                "R2-M2-C5 source-kind overlay no longer starts from the frozen " +
                    "source-path escape policy."
            }
            listOf(
                "internal data class R2FileBackedPackageMetadataRecord",
                "packageMetadataRecords: List<R2FileBackedPackageMetadataRecord>",
                "R2PackageMetadataPolicy.validatePackageJson(bytes)",
                "override fun openPackageMetadataFile(fileIndex: Int)",
            ).forEach { marker ->
                check(marker in fileBackedSource) {
                    "R2-M2-C5 file-backed snapshot isolation is missing marker: $marker"
                }
            }

            val transportSource =
                r2ProjectFramedTransportSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "private val transportedFileCount = Math.addExact(",
                "private val packageTargetFiles = packageTargets?.files?.toList().orEmpty()",
                "Math.addExact(packageMetadataFiles.size, packageTargetFiles.size)",
                "        files.size,",
                "\"packageMetadataSchemaRevision\"",
                "\"packageMetadataPolicyRevision\"",
                "\"packageMetadataPolicyFingerprint\"",
                "\"packageMetadataCanonical\"",
                "\"packageMetadataFingerprint\"",
                "\"packageMetadataFileCount\"",
                "\"packageMetadataByteLength\"",
                "packageMetadataIndex = index",
                "snapshot.openPackageMetadataFile(file.packageMetadataIndex)",
            ).forEach { marker ->
                check(marker in transportSource) {
                    "R2-M2-C5 framed package transport is missing marker: $marker"
                }
            }

            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "const controlledPackageMetadataAuthority = Object.freeze({",
                "const controlledPackageMetadataPolicyCodes = Object.freeze({",
                packagePolicyFingerprint,
                "const parseControlledPackageMetadataCanonical = async (",
                "const validateControlledPackageMetadataJson = (source) =>",
                "packageMetadataFiles: []",
                "isPackageMetadata",
                "state.packageMetadataFiles.push({",
                "const acceptedFileCount =",
                "state.packageMetadataFiles.length +",
                "state.packageTargetFiles.length +",
                "state.files.length;",
                "state.packageMetadata = null",
            ).forEach { marker ->
                check(marker in workerSource) {
                    "R2-M2-C5 Worker package boundary is missing marker: $marker"
                }
            }
            val packageStatePush = workerSource.substring(
                workerSource.indexOf("state.packageMetadataFiles.push({"),
                workerSource.indexOf("state.nextSequence += 1;", workerSource.indexOf("state.packageMetadataFiles.push({")),
            )
            check("text" !in packageStatePush) {
                "R2-M2-C5 Worker must not retain raw package JSON as resolver/source authority."
            }
            check(
                controlledPolicyFingerprint in workerSource &&
                    "PACKAGE_LIFECYCLE_FORBIDDEN" in workerSource &&
                    "PACKAGE_DUPLICATE_KEY" in workerSource &&
                    "PACKAGE_COMPLEXITY_EXCEEDED" in workerSource,
            ) {
                "R2-M2-C5 Worker policy catalogs drifted from C1/C5."
            }

            val harnessSource =
                r2PackageMetadataTransportHarness.asFile.readText(Charsets.UTF_8)
            listOf(
                "Package records entered compiler file/token/directory authority.",
                "PACKAGE_LIFECYCLE_FORBIDDEN",
                "PACKAGE_DUPLICATE_KEY",
                "PACKAGE_ENCODING_INVALID",
                "package descriptor digest mismatch",
                "metadata-absent package attack",
                "resolver/program/emit/result=0",
            ).forEach { marker ->
                check(marker in harnessSource) {
                    "R2-M2-C5 independent Node oracle is missing marker: $marker"
                }
            }

            check(
                "R2ControlledTsConfigEffectiveOptions" in
                    r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8) &&
                    "compilation.controlledEffectiveOptions?.canonicalJson" in
                    r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8) &&
                    "compilation.selectedRootNames" in
                    r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8),
            ) {
                "R2-M2-C5 package identity must remain separate from C7 result/output authority."
            }
            val authoritySource =
                r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            check(
                "const val CONFIG_KIND_NONE = TypeScriptR2Contract.CONFIG_KIND_NONE" in
                    authoritySource &&
                    "TypeScriptR2Contract.CONFIG_KIND_TSCONFIG" !in authoritySource,
            ) {
                "R2-M2-C5 must not activate controlled config/package metadata in production."
            }
            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M2-C5")
            check(
                parseAidlTransactionNames(
                    compilerApiAidl.asFile,
                    "ITypeScriptCompilerPlugin",
                ) == listOf(
                    "getContractVersion",
                    "getContractHash",
                    "getCompilerInfo",
                    "prewarm",
                    "startCompilation",
                    "cancelCompilation",
                ) &&
                    parseAidlTransactionNames(
                        compilerCallbackApiAidl.asFile,
                        "ITypeScriptCompilationCallback",
                    ) == listOf("onProgress", "onCompleted"),
            ) {
                "R2-M2-C5 must not change the frozen Binder transaction prefixes."
            }

            logger.lifecycle(
                "R2-M2-C5 controlled package metadata transport verified: " +
                    "policy=$packagePolicyFingerprint, inventory=$emptyInventoryFingerprint",
            )
        }
    }

val verifyR2M2C6ASnapshotNodeResolutionKernel =
    tasks.register("verifyR2M2C6ASnapshotNodeResolutionKernel") {
        group = "verification"
        description =
            "Verifies the R2-M2-C6-A pure snapshot Node resolution kernel and stable " +
                "failure catalog without claiming complete NodeNext or Program integration."
        dependsOn(verifyR2M2C5ControlledPackageMetadataTransport)
        dependsOn("testDebugUnitTest")
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        inputs.files(
            r2NodeResolutionKernelSource,
            r2NodeResolutionKernelTestSource,
            r2PackageMetadataPolicySource,
            r2PackageMetadataPolicyFixture,
            r2CompilerWorkerSource,
            r2CompilationProfileAuthoritySource,
            r2PublicPreviewMetadataSource,
            compilerApiAidl,
            compilerCallbackApiAidl,
        )
        doLast {
            val resolverSource = r2NodeResolutionKernelSource.asFile.readText(Charsets.UTF_8)
            val resolverTest = r2NodeResolutionKernelTestSource.asFile.readText(Charsets.UTF_8)
            val expectedFailureCatalog = listOf(
                "NODE_RESOLUTION_INVALID_CONTAINING_PATH",
                "NODE_RESOLUTION_INVALID_SPECIFIER",
                "NODE_RESOLUTION_INVALID_CONDITION",
                "NODE_RESOLUTION_AMBIGUOUS_CASE",
                "NODE_RESOLUTION_PACKAGE_NOT_ALLOWED",
                "NODE_RESOLUTION_PACKAGE_METADATA_MISSING",
                "NODE_RESOLUTION_PACKAGE_METADATA_INVALID",
                "NODE_RESOLUTION_IMPORTS_NOT_DEFINED",
                "NODE_RESOLUTION_IMPORTS_NO_MATCH",
                "NODE_RESOLUTION_EXPORTS_NOT_DEFINED",
                "NODE_RESOLUTION_EXPORTS_NO_MATCH",
                "NODE_RESOLUTION_CONDITION_NO_MATCH",
                "NODE_RESOLUTION_INVALID_TARGET",
                "NODE_RESOLUTION_TARGET_BLOCKED",
                "NODE_RESOLUTION_TARGET_ESCAPE",
                "NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT",
                "NODE_RESOLUTION_UNSUPPORTED_SOURCE_KIND",
                "NODE_RESOLUTION_CYCLE",
            )
            val productionCatalog = Regex("\"(NODE_RESOLUTION_[A-Z_]+)\"")
                .findAll(resolverSource)
                .map { match -> match.groupValues[1] }
                .toList()
            check(productionCatalog == expectedFailureCatalog) {
                "R2-M2-C6-A production failure catalog drifted: $productionCatalog"
            }
            check(
                expectedFailureCatalog.all { code -> "\"$code\"" in resolverTest },
            ) {
                "R2-M2-C6-A JVM oracle does not freeze the exact failure catalog."
            }
            listOf(
                "internal object R2NodeResolutionKernel",
                "internal class R2NodePackageMetadataCatalog",
                "R2PackageMetadataPolicy.validatePackageJson(bytes)",
                "R2StrictJson.parseObject(",
                "private fun findMapping(",
                "private fun resolveRelativePath(",
                "R2NodeResolutionFailureCode.CYCLE",
                "R2NodeResolutionFailureCode.TARGET_NOT_IN_SNAPSHOT",
            ).forEach { marker ->
                check(marker in resolverSource) {
                    "R2-M2-C6-A resolver source is missing marker: $marker"
                }
            }
            val imports = Regex("(?m)^import ([^\\r\\n]+)$")
                .findAll(resolverSource)
                .map { match -> match.groupValues[1] }
                .toList()
            check(imports == listOf("java.util.Collections")) {
                "R2-M2-C6-A introduced an unexpected runtime dependency: $imports"
            }
            listOf(
                "java.io.",
                "java.net.",
                "java.nio.file.",
                "android.",
                "TypeScriptWebViewCompilerHost",
                "createIncrementalProgram",
                "R2ProjectInputSnapshot",
            ).forEach { forbidden ->
                check(forbidden !in resolverSource) {
                    "R2-M2-C6-A resolver escaped its pure-kernel boundary: $forbidden"
                }
            }
            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            check(
                "state.packageMetadataFiles.push({" in workerSource &&
                    "R2NodeResolutionKernel" !in workerSource
            ) {
                "R2-M2-C6-A must not activate its kernel in the current Worker."
            }
            logger.lifecycle(
                "R2-M2-C6-A snapshot-only Node resolution kernel verified: " +
                    "${expectedFailureCatalog.size} stable failures, Program/host-fs/network=0",
            )
        }
    }

val verifyR2M2C7ControlledTsConfigRootSelection =
    tasks.register<Exec>("verifyR2M2C7ControlledTsConfigRootSelection") {
        group = "verification"
        description =
            "Verifies R2-M2-C7 snapshot-only controlled tsconfig root selection, " +
                "Program/reachable-output binding, and exact legacy compatibility."
        dependsOn(generateTypeScriptDistribution)
        dependsOn(verifyR2M2C6ASnapshotNodeResolutionKernel)
        dependsOn(verifyR2ProjectCompilerDesktop)
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn("testDebugUnitTest")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        inputs.files(
            compilerFile,
            r2ControlledRootSelectionHarness,
            r2InputArchiveAuthorityHarness,
            r2CompilerWorkerSource,
            r2ProjectArchiveSource,
            r2ProjectArchiveLoaderTestSource,
            r2ProjectSnapshotSource,
            r2ProjectSourceKindAdmissionSource,
            r2ProjectSnapshotTestSource,
            r2FileBackedProjectSnapshotSource,
            r2FileBackedProjectSnapshotTestSource,
            r2ProjectFramedTransportSource,
            r2ProjectFramedTransportTestSource,
            r2ProjectCompilationSource,
            r2ProjectCompilationTestSource,
            r2CompilerHostProtocolSource,
            compilerWebViewHostSource,
            r2PrivateOutputArchiveSource,
            r2PrivateOutputArchiveTestSource,
            r2ControlledTsConfigEffectiveOptionsSource,
            r2CompilationProfileAuthoritySource,
            r2PublicPreviewMetadataSource,
            compilerApiAidl,
            compilerCallbackApiAidl,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            r2ControlledRootSelectionHarness.asFile.absolutePath,
            r2CompilerWorkerSource.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            typeScriptVersion,
        )
        doLast {
            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "const selectControlledRootNames = (parsedConfig, files, entryPath) => {",
                "typeof ts.matchFiles !== \"function\"",
                "createControlledSnapshotFileSystemEntries(sourcePaths)",
                "const includeMatches = ts.matchFiles(",
                "controlledTsConfigPolicyCodes.noInputs",
                "controlledTsConfigPolicyCodes.entryNotSelected",
                "rootNames = selectControlledRootNames(",
                "result.effectiveOptionsFingerprint =",
                "result.effectiveOptionsRevision = controlledEffectiveOptions.revision",
                "result.rootNames = vfs.rootNames.slice()",
                "const controlledKeys = legacyKeys.concat([",
                "\"entryPath\",",
            ).forEach { marker ->
                check(marker in workerSource) {
                    "R2-M2-C7 Worker root-selection authority is missing marker: $marker"
                }
            }
            check("ts.sys" !in workerSource) {
                "R2-M2-C7 Worker must not expose TypeScript system I/O to root selection."
            }

            val archiveSource = r2ProjectArchiveSource.asFile.readText(Charsets.UTF_8)
            val snapshotSource = r2ProjectSnapshotSource.asFile.readText(Charsets.UTF_8)
            val fileBackedSource =
                r2FileBackedProjectSnapshotSource.asFile.readText(Charsets.UTF_8)
            val transportSource =
                r2ProjectFramedTransportSource.asFile.readText(Charsets.UTF_8)
            check("entryPath = \"/\${manifest.entryPath}\"" in archiveSource) {
                "R2-M2-C7 archive loader does not preserve canonical entry identity."
            }
            listOf(
                "val entryPath: String",
                "R2ProjectSourceKindAdmission.isExecutableSourcePath(",
                "profileAuthority = profileAuthority",
                "rootName == entryPath",
            ).forEach { marker ->
                check(marker in snapshotSource) {
                    "R2-M2-C7 heap snapshot entry authority is missing marker: $marker"
                }
            }
            listOf(
                "override val entryPath: String",
                "entryPath = entryPath",
            ).forEach { marker ->
                check(marker in fileBackedSource) {
                    "R2-M2-C7 file-backed snapshot entry authority is missing marker: $marker"
                }
            }
            check("\"entryPath\" to entryPath" in transportSource) {
                "R2-M2-C7 controlled start frame does not carry entryPath."
            }

            val compilationSource = r2ProjectCompilationSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "controlledEffectiveOptions: R2ControlledTsConfigEffectiveOptions? = null",
                "selectedRootNames: List<String> = emptyList()",
                "this.selectedRootNames.size <= R2_FILE_BACKED_MAXIMUM_FILES",
                "this.selectedRootNames == this.selectedRootNames.sorted()",
                "this.selectedRootNames.distinct() == this.selectedRootNames",
            ).forEach { marker ->
                check(marker in compilationSource) {
                    "R2-M2-C7 native result authority is missing marker: $marker"
                }
            }
            val protocolSource = r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "parseControlledEffectiveOptions(result, optionsJson, effectiveOptions)",
                "encodedRootNames.size <= R2_FILE_BACKED_MAXIMUM_FILES",
                "result.requireList(\"rootNames\")",
                "R2ControlledTsConfigEffectiveOptions.canonicalOptionKeys",
                "result.requireString(\"effectiveOptionsFingerprint\")",
                "result.requireProtocolInt(\"effectiveOptionsRevision\")",
            ).forEach { marker ->
                check(marker in protocolSource) {
                    "R2-M2-C7 native framed-result parser is missing marker: $marker"
                }
            }
            val webViewHostSource = compilerWebViewHostSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "snapshot.entryPath in compilation.selectedRootNames",
                "compilation.selectedRootNames.all(snapshot.filePaths::contains)",
                "snapshot.entryPath in moduleSources",
            ).forEach { marker ->
                check(marker in webViewHostSource) {
                    "R2-M2-C7 WebView result binding is missing marker: $marker"
                }
            }
            val outputSource = r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "acceptedRootPaths = acceptedArchive.snapshot.rootNames.toSet()",
                "acceptedSnapshotPaths = acceptedDiagnosticSourcePaths(acceptedArchive.snapshot)",
                "private fun acceptedDiagnosticSourcePaths(",
                "HashSet(snapshot.filePaths).apply",
                "snapshot.packageTargetInventory?.files?.forEach",
                "entrySourcePath !in compilation.selectedRootNames",
                "compilation.controlledEffectiveOptions?.canonicalJson",
            ).forEach { marker ->
                check(marker in outputSource) {
                    "R2-M2-C7 private output authority is missing marker: $marker"
                }
            }

            val harnessSource =
                r2ControlledRootSelectionHarness.asFile.readText(Charsets.UTF_8)
            listOf(
                "CONFIG_NO_INPUTS",
                "CONFIG_ENTRY_NOT_SELECTED",
                "Program-reachable executable output inventory drifted.",
                "5 selector semantics, 3 pre-Program failures",
                "41 controlled options",
                "18 legacy options, compiler host I/O=0",
            ).forEach { marker ->
                check(marker in harnessSource) {
                    "R2-M2-C7 independent Node oracle is missing marker: $marker"
                }
            }

            val authoritySource =
                r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            check(
                "const val CONFIG_KIND_NONE = TypeScriptR2Contract.CONFIG_KIND_NONE" in
                    authoritySource &&
                    "TypeScriptR2Contract.CONFIG_KIND_TSCONFIG" !in authoritySource,
            ) {
                "R2-M2-C7 must not activate controlled compilation in production."
            }
            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M2-C7")
            check(
                parseAidlTransactionNames(
                    compilerApiAidl.asFile,
                    "ITypeScriptCompilerPlugin",
                ) == listOf(
                    "getContractVersion",
                    "getContractHash",
                    "getCompilerInfo",
                    "prewarm",
                    "startCompilation",
                    "cancelCompilation",
                ) &&
                    parseAidlTransactionNames(
                        compilerCallbackApiAidl.asFile,
                        "ITypeScriptCompilationCallback",
                    ) == listOf("onProgress", "onCompleted"),
            ) {
                "R2-M2-C7 must not change the frozen Binder transaction prefixes."
            }
            logger.lifecycle(
                "R2-M2-C7 controlled root selection verified: selectors=5, " +
                    "pre-Program failures=3, reachable executable sources=3, " +
                    "controlled/legacy options=41/18, compiler host I/O=0",
            )
        }
    }

val verifyR2M2C6BPackageTargetsAndNodeResolutionParity =
    tasks.register<Exec>("verifyR2M2C6BPackageTargetsAndNodeResolutionParity") {
        group = "verification"
        description =
            "Verifies the inert R2-M2-C6-B package-target authority and the closed-world " +
                "NodeNext resolver parity envelope without activating Program integration."
        dependsOn(generateTypeScriptDistribution)
        dependsOn(verifyR2M2C7ControlledTsConfigRootSelection)
        dependsOn("testDebugUnitTest")
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        inputs.files(
            compilerFile,
            r2NodeResolutionKernelSource,
            r2NodeResolutionKernelTestSource,
            r2NodeResolutionParityFixture,
            r2NodeResolutionParityHarness,
            r2PackageMetadataPolicySource,
            r2PackageMetadataPolicyFixture,
            r2PackageTargetPolicySource,
            r2PackageTargetPolicyTestSource,
            r2PackageTargetPolicyFixture,
            r2ProjectArchiveSource,
            r2ProjectArchiveTestSource,
            r2ProjectSnapshotSource,
            r2ProjectSnapshotTestSource,
            r2FileBackedProjectSnapshotSource,
            r2FileBackedProjectSnapshotTestSource,
            r2ProjectFramedTransportSource,
            r2ProjectFramedTransportTestSource,
            r2CompilerWorkerSource,
            r2CompilationProfileAuthoritySource,
            r2PublicPreviewMetadataSource,
            compilerApiAidl,
            compilerCallbackApiAidl,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            r2NodeResolutionParityHarness.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            r2NodeResolutionParityFixture.asFile.absolutePath,
            typeScriptVersion,
        )
        doLast {
            val packageTargetSource =
                r2PackageTargetPolicySource.asFile.readText(Charsets.UTF_8)
            val expectedPackageTargetCodes = listOf(
                "PACKAGE_TARGET_PATH_INVALID",
                "PACKAGE_TARGET_ROOT_NOT_ALLOWED",
                "PACKAGE_TARGET_SOURCE_KIND_UNSUPPORTED",
                "PACKAGE_TARGET_INVENTORY_INVALID",
                "PACKAGE_TARGET_CASE_COLLISION",
                "PACKAGE_TARGET_QUOTA_EXCEEDED",
                "PACKAGE_TARGET_METADATA_IDENTITY_INVALID",
            )
            val productionPackageTargetCodes =
                Regex("\"(PACKAGE_TARGET_[A-Z_]+)\"")
                    .findAll(packageTargetSource)
                    .map { match -> match.groupValues[1] }
                    .toList()
            check(productionPackageTargetCodes == expectedPackageTargetCodes) {
                "R2-M2-C6-B package-target failure catalog drifted: " +
                    productionPackageTargetCodes
            }
            listOf(
                "class R2PackageTargetInventory private constructor",
                "packageMetadataInventoryFingerprint",
                "R2PackageMetadataInventory",
                "METADATA_BINDING_MODE = \"exact-c5-inventory-fingerprint\"",
                "SHARED_AGGREGATE_BUDGET_MODE = \"required-non-additive\"",
                "MAXIMUM_FILES = 256",
                "MAXIMUM_TOTAL_BYTES = 8L * 1024L * 1024L",
                "Collections.unmodifiableList",
                "Collections.unmodifiableSet",
                "path.endsWith(\".mts\")",
                "path.endsWith(\".cts\")",
            ).forEach { marker ->
                check(marker in packageTargetSource) {
                    "R2-M2-C6-B package-target authority is missing marker: $marker"
                }
            }

            @Suppress("UNCHECKED_CAST")
            val packageTargetFixture =
                JsonSlurper().parse(r2PackageTargetPolicyFixture.asFile) as Map<String, Any?>
            check(
                packageTargetFixture["schema"] ==
                    "autojs6.typescript.package-target-policy" &&
                    (packageTargetFixture["schemaRevision"] as Number).toInt() == 1 &&
                    (packageTargetFixture["policyRevision"] as Number).toInt() == 1 &&
                    packageTargetFixture["fingerprint"] ==
                    "e34f4825d8f75ba1b674849ff79c2be8f9c1b88fe940add86e6e466a139bbbaf" &&
                    packageTargetFixture["metadataBindingMode"] ==
                    "exact-c5-inventory-fingerprint" &&
                    packageTargetFixture["sharedAggregateBudgetMode"] ==
                    "required-non-additive" &&
                    packageTargetFixture["policyCodes"] == expectedPackageTargetCodes,
            ) {
                "R2-M2-C6-B checked-in package-target policy authority drifted."
            }

            val archiveSource = r2ProjectArchiveSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "val packageTargetAuthority: R2PackageTargetAuthority? = null",
                "val packageTargetInventory: R2PackageTargetInventory?",
                "TOP_LEVEL_KEYS_WITH_CONFIG_AND_PACKAGE_METADATA + \"packageTargets\"",
                "TOP_LEVEL_KEYS_WITH_PACKAGE_METADATA + \"packageTargets\"",
                "manifest.packageTargetInventory?.files?.forEach",
                "R2PackageTargetPolicy.isExplicitlyAllowed(path, packageTargetInventory)",
            ).forEach { marker ->
                check(marker in archiveSource) {
                    "R2-M2-C6-B native formal-v1 archive authority is missing: $marker"
                }
            }
            val snapshotSource = r2ProjectSnapshotSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "val packageTargetInventory: R2PackageTargetInventory?",
                "fun openPackageTargetFile(fileIndex: Int): R2ProjectFileCursor",
                "ownedPackageTargetBytes",
                "R2Utf8.decode(ownedBytes, \"Package target ${'$'}{descriptor.path}\")",
                "packageTargetBytes?.forEach { bytes -> bytes.fill(0) }",
            ).forEach { marker ->
                check(marker in snapshotSource) {
                    "R2-M2-C6-B heap package-target snapshot is missing: $marker"
                }
            }
            val fileBackedSource =
                r2FileBackedProjectSnapshotSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "internal data class R2FileBackedPackageTargetRecord",
                "packageTargetRecords: List<R2FileBackedPackageTargetRecord>",
                "override fun openPackageTargetFile(fileIndex: Int): R2ProjectFileCursor",
                "private fun requirePackageTargetDigest(",
                "private fun requirePackageTargetUtf8(",
                "PACKAGE_TARGET_DIGEST_BUFFER_BYTES = 16 * 1024",
            ).forEach { marker ->
                check(marker in fileBackedSource) {
                    "R2-M2-C6-B file-backed package-target snapshot is missing: $marker"
                }
            }
            val transportSource =
                r2ProjectFramedTransportSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "Math.addExact(packageMetadataFiles.size, packageTargetFiles.size)",
                "\"packageTargetSchemaRevision\" to R2PackageTargetPolicy.SCHEMA_REVISION",
                "\"packageTargetPolicyRevision\" to targets.authority.policyRevision",
                "\"packageTargetPolicyFingerprint\" to targets.authority.policyFingerprint",
                "\"packageTargetCanonical\" to targets.canonicalInput",
                "\"packageTargetFingerprint\" to targets.fingerprint",
                "\"packageTargetFileCount\" to packageTargetFiles.size",
                "\"packageTargetByteLength\" to declaredPackageTargetBytes",
                "packageTargetFiles.getOrNull(packageTargetIndex)",
                "snapshot.openPackageTargetFile(file.packageTargetIndex)",
            ).forEach { marker ->
                check(marker in transportSource) {
                    "R2-M2-C6-B ordered package-target framing is missing: $marker"
                }
            }
            val archiveTest =
                r2ProjectArchiveTestSource.asFile.readText(Charsets.UTF_8)
            val snapshotTest = r2ProjectSnapshotTestSource.asFile.readText(Charsets.UTF_8)
            val fileBackedTest =
                r2FileBackedProjectSnapshotTestSource.asFile.readText(Charsets.UTF_8)
            val transportTest =
                r2ProjectFramedTransportTestSource.asFile.readText(Charsets.UTF_8)
            listOf(
                archiveTest to
                    "revision5ExactlyBindsTargetSchemaPolicyMetadataInventoryAndArchiveBytes",
                archiveTest to
                    "revision5RejectsTargetPathCaseQuotaAndUndeclaredPackageEntries",
                snapshotTest to
                    "heapPackageTargetsAreIndependentBudgetedAndInvalidatedOnClose",
                fileBackedTest to
                    "constructorOwnsIndependentPackageTargetRecordsAndInvalidatesCursors",
                transportTest to
                    "packageTargetsUseRevision5StartAndFollowMetadataBeforeOrdinaryFiles",
                transportTest to
                    "releaseClosesAnActivePackageTargetCursorExactlyOnceAndZeroesTheBuffer",
            ).forEach { (testSource, marker) ->
                check(marker in testSource) {
                    "R2-M2-C6-B native package-target test coverage is missing: $marker"
                }
            }

            val resolverSource = r2NodeResolutionKernelSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "add(TYPES_CONDITION)",
                "add(NODE_CONDITION)",
                "condition.isCanonicalIntegerIndexCondition()",
                "result.code == R2NodeResolutionFailureCode.TARGET_BLOCKED",
                "private fun resolutionCandidates(candidate: String): List<String>",
                "listOf(\"${'$'}base.ts\", \"${'$'}base.d.ts\")",
            ).forEach { marker ->
                check(marker in resolverSource) {
                    "R2-M2-C6-B resolver parity authority is missing marker: $marker"
                }
            }

            @Suppress("UNCHECKED_CAST")
            val parityFixture =
                JsonSlurper().parse(r2NodeResolutionParityFixture.asFile) as Map<String, Any?>
            val parityCases = parityFixture["cases"] as? List<*>
                ?: error("R2-M2-C6-B parity fixture has no case inventory.")
            val classifications = parityCases.map { rawCase ->
                (rawCase as? Map<*, *>)?.get("classification") as? String
                    ?: error("R2-M2-C6-B parity case classification is malformed.")
            }
            check(
                parityFixture["schema"] ==
                    "autojs6.typescript.node-resolution-parity" &&
                    (parityFixture["revision"] as Number).toInt() == 1 &&
                    parityFixture["typescriptVersion"] == typeScriptVersion &&
                    parityCases.size == 18 &&
                    classifications.count { it == "supported" } == 12 &&
                    classifications.count { it == "fail-closed" } == 6,
            ) {
                "R2-M2-C6-B resolver parity envelope drifted."
            }
            val parityHarness =
                r2NodeResolutionParityHarness.asFile.readText(Charsets.UTF_8)
            listOf(
                "ts.resolveModuleName(",
                "ts.ModuleResolutionKind.NodeNext",
                "ModuleResolutionHost-only",
                "supported === 12 && failClosed === 6",
            ).forEach { marker ->
                check(marker in parityHarness) {
                    "R2-M2-C6-B independent TypeScript oracle is missing marker: $marker"
                }
            }
            check("ts.sys" !in parityHarness) {
                "R2-M2-C6-B parity oracle must not use the host TypeScript system adapter."
            }

            val authoritySource =
                r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            check(
                "const val CONFIG_KIND_NONE = TypeScriptR2Contract.CONFIG_KIND_NONE" in
                    authoritySource &&
                    "TypeScriptR2Contract.CONFIG_KIND_TSCONFIG" !in authoritySource,
            ) {
                "R2-M2-C6-B must not activate controlled project compilation in production."
            }
            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M2-C6-B")
            check(
                parseAidlTransactionNames(
                    compilerApiAidl.asFile,
                    "ITypeScriptCompilerPlugin",
                ) == listOf(
                    "getContractVersion",
                    "getContractHash",
                    "getCompilerInfo",
                    "prewarm",
                    "startCompilation",
                    "cancelCompilation",
                ) &&
                    parseAidlTransactionNames(
                        compilerCallbackApiAidl.asFile,
                        "ITypeScriptCompilationCallback",
                    ) == listOf("onProgress", "onCompleted"),
            ) {
                "R2-M2-C6-B must not change the frozen Binder transaction prefixes."
            }
            logger.lifecycle(
                "R2-M2-C6-B package-target/parity authority verified: " +
                    "package failures=7, supported/fail-closed=12/6, " +
                    "Program activation=0, compiler host I/O=0",
            )
        }
    }

val verifyR2M2C6CWorkerSnapshotNodeResolutionKernel =
    tasks.register<Exec>("verifyR2M2C6CWorkerSnapshotNodeResolutionKernel") {
        group = "verification"
        description =
            "Verifies the R2-M2-C6-C Worker snapshot resolver kernel remains pure, " +
                "parity-aligned, and deep-frozen independently of Program activation."
        dependsOn(generateTypeScriptDistribution)
        dependsOn(verifyR2M2C6BPackageTargetsAndNodeResolutionParity)
        dependsOn(verifyCompilerHostSourcePolicy)
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        inputs.files(
            compilerFile,
            r2CompilerWorkerSource,
            r2NodeResolutionWorkerHarness,
            r2NodeResolutionKernelSource,
            r2NodeResolutionKernelTestSource,
            r2NodeResolutionParityFixture,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            r2NodeResolutionWorkerHarness.asFile.absolutePath,
            r2CompilerWorkerSource.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            typeScriptVersion,
        )
        doLast {
            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            val expectedFailureCatalog = listOf(
                "NODE_RESOLUTION_INVALID_CONTAINING_PATH",
                "NODE_RESOLUTION_INVALID_SPECIFIER",
                "NODE_RESOLUTION_INVALID_CONDITION",
                "NODE_RESOLUTION_AMBIGUOUS_CASE",
                "NODE_RESOLUTION_PACKAGE_NOT_ALLOWED",
                "NODE_RESOLUTION_PACKAGE_METADATA_MISSING",
                "NODE_RESOLUTION_PACKAGE_METADATA_INVALID",
                "NODE_RESOLUTION_IMPORTS_NOT_DEFINED",
                "NODE_RESOLUTION_IMPORTS_NO_MATCH",
                "NODE_RESOLUTION_EXPORTS_NOT_DEFINED",
                "NODE_RESOLUTION_EXPORTS_NO_MATCH",
                "NODE_RESOLUTION_CONDITION_NO_MATCH",
                "NODE_RESOLUTION_INVALID_TARGET",
                "NODE_RESOLUTION_TARGET_BLOCKED",
                "NODE_RESOLUTION_TARGET_ESCAPE",
                "NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT",
                "NODE_RESOLUTION_UNSUPPORTED_SOURCE_KIND",
                "NODE_RESOLUTION_CYCLE",
            )
            val workerFailureCatalog = Regex("\"(NODE_RESOLUTION_[A-Z_]+)\"")
                .findAll(workerSource)
                .map { match -> match.groupValues[1] }
                .toList()
            check(workerFailureCatalog == expectedFailureCatalog) {
                "R2-M2-C6-C Worker failure catalog drifted: $workerFailureCatalog"
            }
            listOf(
                "const controlledNodeResolutionFailureCodes = Object.freeze({",
                "const controlledNodeResolutionModes = Object.freeze({",
                "const controlledNodeResolutionTargetKinds = Object.freeze({",
                "const isControlledNodeDeepFrozenJson =",
                "const isControlledNodeArrayIndexCondition = (condition) => {",
                "value < 0xffffffff",
                "const resolveControlledNodeSnapshot = (request, packageMetadata, targets) => {",
                "request.mode,",
                "\"types\",",
                "\"node\",",
                "candidate.endsWith(\".js\")",
                "controlledNodeResolutionFailureCodes.targetBlocked",
            ).forEach { marker ->
                check(marker in workerSource) {
                    "R2-M2-C6-C Worker resolver is missing marker: $marker"
                }
            }
            check(
                Regex("\\bresolveControlledNodeSnapshot\\b")
                    .findAll(workerSource)
                    .count() >= 2,
            ) {
                "R2-M2-C6-C resolver must retain its declaration and production hook."
            }
            val harnessSource =
                r2NodeResolutionWorkerHarness.asFile.readText(Charsets.UTF_8)
            listOf(
                "4294967294",
                "4294967295",
                "successCount === 14",
                "failureCount === 19",
                "18-code Kotlin catalog, host I/O=0, Program=0",
                "/\\bts\\.sys\\b/",
                "/resolveModuleNameLiterals/",
            ).forEach { marker ->
                check(marker in harnessSource) {
                    "R2-M2-C6-C independent Worker oracle is missing marker: $marker"
                }
            }
            logger.lifecycle(
                "R2-M2-C6-C Worker snapshot resolver verified: " +
                    "resolutions/failures=14/19, failures=18, " +
                    "host I/O=0, Program activation=0",
            )
        }
    }

val verifyR2M2C6DWorkerPackageTargetTransport =
    tasks.register<Exec>("verifyR2M2C6DWorkerPackageTargetTransport") {
        group = "verification"
        description =
            "Verifies the R2-M2-C6-D revision-5 package-target Worker ingress, " +
                "independent retention, cleanup, and transport-only runtime boundary."
        dependsOn(generateTypeScriptDistribution)
        dependsOn(verifyR2M2C6CWorkerSnapshotNodeResolutionKernel)
        dependsOn(verifyCompilerHostSourcePolicy)
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        inputs.files(
            compilerFile,
            r2CompilerWorkerSource,
            r2PackageTargetWorkerTransportHarness,
            r2InputArchiveAuthorityHarness,
            r2PackageTargetPolicyFixture,
            r2PackageMetadataPolicyFixture,
            r2ControlledTsConfigPolicyFixture,
            r2ProjectFramedTransportSource,
            r2ProjectFramedTransportTestSource,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            r2PackageTargetWorkerTransportHarness.asFile.absolutePath,
            r2CompilerWorkerSource.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            r2PackageTargetPolicyFixture.asFile.absolutePath,
            r2PackageMetadataPolicyFixture.asFile.absolutePath,
            r2ControlledTsConfigPolicyFixture.asFile.absolutePath,
            typeScriptVersion,
        )
        doLast {
            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            val expectedPackageTargetCodes = listOf(
                "PACKAGE_TARGET_CASE_COLLISION",
                "PACKAGE_TARGET_INVENTORY_INVALID",
                "PACKAGE_TARGET_METADATA_IDENTITY_INVALID",
                "PACKAGE_TARGET_PATH_INVALID",
                "PACKAGE_TARGET_QUOTA_EXCEEDED",
                "PACKAGE_TARGET_ROOT_NOT_ALLOWED",
                "PACKAGE_TARGET_SOURCE_KIND_UNSUPPORTED",
            )
            val workerPackageTargetCodes = Regex("\"(PACKAGE_TARGET_[A-Z_]+)\"")
                .findAll(workerSource)
                .map { match -> match.groupValues[1] }
                .toList()
            check(workerPackageTargetCodes == expectedPackageTargetCodes) {
                "R2-M2-C6-D Worker package-target failure catalog drifted: " +
                    workerPackageTargetCodes
            }
            listOf(
                "const controlledPackageTargetAuthority = Object.freeze({",
                "canonicalPrefix: \"autojs6.typescript.package-target.inventory.v1\"",
                "metadataBindingMode: \"exact-c5-inventory-fingerprint\"",
                "sharedAggregateBudgetMode: \"required-non-additive\"",
                "e34f4825d8f75ba1b674849ff79c2be8f9c1b88fe940add86e6e466a139bbbaf",
                "const parseControlledPackageTargetCanonical = async (",
                "const createRetainedPackageTargetRecord = (file, text) => {",
                "const createRetainedPackageTargetAuthority = (inventory) =>",
                "const createRetainedPackageTargetVfs = (records, textHolders, authority = null) => {",
                "const packageTargetKeys = packageMetadataKeys.concat([",
                "packageTargetFiles: [],",
                "packageTargetTextHolders: [],",
                "state.packageMetadataFiles.length +",
                "state.packageTargetFiles.length +",
                "state.packageTargetFiles.push(retained.record);",
                "state.packageTargetTextHolders.push(retained.textHolder);",
                "retainedPackageTargets.release();",
                "releaseRetainedPackageTargetTexts(state.packageTargetTextHolders);",
            ).forEach { marker ->
                check(marker in workerSource) {
                    "R2-M2-C6-D Worker transport is missing marker: $marker"
                }
            }
            listOf(
                "packageTargetByteLength",
                "packageTargetCanonical",
                "packageTargetFileCount",
                "packageTargetFingerprint",
                "packageTargetPolicyFingerprint",
                "packageTargetPolicyRevision",
                "packageTargetSchemaRevision",
            ).forEach { field ->
                check("\"$field\"" in workerSource) {
                    "R2-M2-C6-D revision-5 start authority is missing field: $field"
                }
            }
            check(
                Regex("\\bresolveControlledNodeSnapshot\\b")
                    .findAll(workerSource)
                    .count() >= 2,
            ) {
                "R2-M2-C6-D must retain the resolver declaration and production hook."
            }
            val compilerHostStart = workerSource.indexOf("const createProjectCompilerHost")
            val compilerHostEnd = workerSource.indexOf(
                "const serializeDiagnostic",
                startIndex = compilerHostStart,
            )
            check(compilerHostStart >= 0 && compilerHostEnd > compilerHostStart) {
                "R2-M2-C6-D could not isolate the compiler-host source boundary."
            }
            val compilerHostSource = workerSource.substring(compilerHostStart, compilerHostEnd)
            check(
                "resolutionSession" in compilerHostSource &&
                    "resolveModuleNameLiterals" in compilerHostSource &&
                    "resolveTypeReferenceDirectiveReferences" in compilerHostSource,
            ) {
                "R2-M2-C6-D compiler host is missing the controlled C6 activation seam."
            }
            val resolutionSessionStart =
                workerSource.indexOf("const createControlledNodeResolutionSession")
            check(
                resolutionSessionStart >= 0 &&
                    resolutionSessionStart < compilerHostStart &&
                    "retainedPackageTargets" in workerSource.substring(
                        resolutionSessionStart,
                        compilerHostStart,
                    ) &&
                    "resolveControlledNodeSnapshot" in workerSource.substring(
                        resolutionSessionStart,
                        compilerHostStart,
                    ),
            ) {
                "R2-M2-C6-D resolver session is missing its retained-target/kernel seam."
            }

            val harnessSource =
                r2PackageTargetWorkerTransportHarness.asFile.readText(Charsets.UTF_8)
            listOf(
                "2 retained targets, 9 rev5 rejections, host-visible=0",
                "Target retention escaped into compiler-visible VFS or lost C5 identity.",
                "Rejected target retained raw bytes.",
                "resolver-declared, Program/I-O=0",
                "Object.isFrozen(vfs.retainedPackageTargets.authority)",
                "retainedRecord.text === \"\"",
            ).forEach { marker ->
                check(marker in harnessSource) {
                    "R2-M2-C6-D independent Worker gate is missing marker: $marker"
                }
            }
            logger.lifecycle(
                "R2-M2-C6-D package-target Worker transport verified: " +
                    "retained=2, rev5 rejections=9, host-visible=0, " +
                    "resolver-declared, Program/I-O=0",
            )
        }
    }

val verifyR2M2C6NodeResolutionProgramIntegration =
    tasks.register<Exec>("verifyR2M2C6NodeResolutionProgramIntegration") {
        group = "verification"
        description =
            "Verifies the complete R2-M2-C6 revision-5 snapshot resolver through the real " +
                "TypeScript Program, typed native diagnostics, private output, and cleanup."
        dependsOn(generateTypeScriptDistribution)
        dependsOn(verifyR2M2C6DWorkerPackageTargetTransport)
        dependsOn(verifyCompilerHostSourcePolicy)
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        inputs.files(
            compilerFile,
            r2CompilerWorkerSource,
            r2NodeResolutionProgramHarness,
            r2InputArchiveAuthorityHarness,
            r2PackageTargetPolicyFixture,
            r2PackageMetadataPolicyFixture,
            r2ControlledTsConfigPolicyFixture,
            r2NodeResolutionKernelSource,
            r2ProjectCompilationSource,
            r2CompilerHostProtocolSource,
            compilerWebViewHostSource,
            r2PrivateOutputArchiveSource,
            r2ProjectCompilationTestSource,
            r2ProjectFramedTransportTestSource,
            r2PrivateOutputArchiveTestSource,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            r2NodeResolutionProgramHarness.asFile.absolutePath,
            r2CompilerWorkerSource.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            r2PackageTargetPolicyFixture.asFile.absolutePath,
            r2PackageMetadataPolicyFixture.asFile.absolutePath,
            r2ControlledTsConfigPolicyFixture.asFile.absolutePath,
            typeScriptVersion,
        )
        doLast {
            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "resolveModuleNameLiterals",
                "resolveTypeReferenceDirectiveReferences",
                "resolveControlledNodeSnapshot",
                "nodeResolution",
                "900_001 + index",
                "retainedPackageTargets",
            ).forEach { marker ->
                check(marker in workerSource) {
                    "R2-M2-C6 Program resolver is missing Worker marker: $marker"
                }
            }
            val harnessSource =
                r2NodeResolutionProgramHarness.asFile.readText(Charsets.UTF_8)
            listOf(
                "6 module + 1 type-reference resolutions (5 package targets)",
                "NODE_RESOLUTION_EXPORTS_NO_MATCH",
                "NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT",
                "assertOverTokenTargetRejected",
                "token boundary=65536/65537",
                "expectedResultKeys",
                "default resolver/ts.sys/network=0",
                "clean successor=1",
                "900001",
                "900018",
            ).forEach { marker ->
                check(marker in harnessSource) {
                    "R2-M2-C6 real Program gate is missing marker: $marker"
                }
            }

            val compilationSource =
                r2ProjectCompilationSource.asFile.readText(Charsets.UTF_8)
            val protocolSource =
                r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8)
            val outputSource =
                r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8)
            check(
                "RESOLUTION(\"resolution\")" in compilationSource &&
                    "R2NodeResolutionDiagnosticEvidence" in compilationSource
            ) {
                "R2-M2-C6 compilation model is missing its typed resolution diagnostic."
            }
            check("parseNodeResolutionDiagnosticEvidence" in protocolSource) {
                "R2-M2-C6 host protocol is missing strict resolution evidence parsing."
            }
            check("diagnostic.nodeResolution?.let" in outputSource) {
                "R2-M2-C6 private output is missing conditional resolution evidence."
            }
            mapOf(
                r2ProjectCompilationTestSource.asFile to
                    "resolutionDiagnosticsRequireExactEvidenceAndStopEveryOutput",
                r2ProjectFramedTransportTestSource.asFile to
                    "nodeResolutionJson",
                r2PrivateOutputArchiveTestSource.asFile to
                    "nodeResolutionDiagnosticIsConditionallySerializedAndTargetBound",
            ).forEach { (testFile, marker) ->
                check(marker in testFile.readText(Charsets.UTF_8)) {
                    "R2-M2-C6 native/output test seam is missing marker: $marker"
                }
            }
            logger.lifecycle(
                "R2-M2-C6 Node resolution verified: Program module/type resolutions=6/1, " +
                    "package targets=5, token boundary=65536/65537, " +
                    "typed failures=2/18-code catalog, roots/outputs polluted=0, " +
                    "default resolver/host/network=0, clean successor=1",
            )
        }
    }

val verifyR2M2C8DeclarationLayering =
    tasks.register("verifyR2M2C8DeclarationLayering") {
        group = "verification"
        description =
            "Verifies the complete R2-M2-C8 declaration supply chain, profile isolation, " +
                "runtime tests, and exact Debug/Release APK inventories."
        dependsOn(verifyR2M2C6NodeResolutionProgramIntegration)
        dependsOn(verifyR2DeclarationDistribution)
        dependsOn(verifyR2M2C8DeclarationProgramLayering)
        dependsOn(verifyR2M2C8DeclarationWorkerLayering)
        dependsOn(verifyDebugR2DeclarationDistributionApk)
        dependsOn(verifyReleaseR2DeclarationDistributionApk)
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn("verifyR2PublicPreviewSourcePolicy")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        dependsOn("testDebugUnitTest")
    }

val verifyR2M2P1ProvisionalNodeNextProfile =
    tasks.register("verifyR2M2P1ProvisionalNodeNextProfile") {
        group = "verification"
        description =
            "Verifies the complete R2-M2-P1 config-none NodeNext profile while public " +
                "compiler capabilities and the Node Runtime dependency remain disabled."
        dependsOn(verifyR2M2C8DeclarationLayering)
        dependsOn(verifyR2M2P1ProvisionalNodeNextProfileDesktop)
        dependsOn(verifyDebugR2DeclarationDistributionApk)
        dependsOn(verifyReleaseR2DeclarationDistributionApk)
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn("verifyR2PublicPreviewSourcePolicy")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        dependsOn("testDebugUnitTest")
        inputs.file(r2PublicPreviewMetadataSource)

        doLast {
            val metadataSource =
                r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M2-P1")
            logger.lifecycle(
                "R2-M2-P1 NodeNext profile verified: config=none, source=.ts, output=.js, " +
                    "module=199, moduleResolution=99, compiler capability mask=15, " +
                    "Node Runtime required=false",
            )
        }
    }

val verifyR2M2P2AuthorityFoundation =
    tasks.register("verifyR2M2P2AuthorityFoundation") {
        group = "verification"
        description =
            "Verifies the R2-M2-P2-0..P2-2 policy, exact revision registry, private " +
                "control-plane fences, and native/JavaScript generated mapping foundation."
        dependsOn(verifyR2M2P1ProvisionalNodeNextProfile)
        dependsOn(verifyR2M2P2GeneratedModuleMappingDesktop)
        dependsOn("testDebugUnitTest")
        dependsOn("compileDebugAndroidTestKotlin")

        val profileAuthoritySource = layout.projectDirectory.file(
            "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                "R2CompilationProfileAuthority.kt",
        )
        val probeProtocolSource = layout.projectDirectory.file(
            "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                "R2CompilationProbeProtocol.kt",
        )
        val compilerServiceSource = layout.projectDirectory.file(
            "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                "TypeScriptCompilerPluginService.kt",
        )
        val projectArchiveSource = layout.projectDirectory.file(
            "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                "R2ProjectArchive.kt",
        )
        inputs.files(
            r2M2P2NodeModuleOutputPolicySource,
            r2M2P2NodeModuleOutputPolicyFixture,
            r2M2P2GeneratedModuleMappingSource,
            r2M2P2GeneratedModuleMappingFixture,
            r2M2P2GeneratedModuleMappingPolicy,
            r2M2P2GeneratedModuleMappingHarness,
            profileAuthoritySource,
            probeProtocolSource,
            compilerServiceSource,
            projectArchiveSource,
        )

        doLast {
            @Suppress("UNCHECKED_CAST")
            val policyFixture =
                JsonSlurper().parse(r2M2P2NodeModuleOutputPolicyFixture.asFile)
                    as Map<String, Any?>
            check(
                policyFixture["fingerprint"] ==
                    r2M2P2NodeModuleOutputPolicyFingerprint,
            ) {
                "R2-M2-P2 node module/output policy fingerprint drifted."
            }
            check(policyFixture["packageTypeKey"] == "type")
            check(policyFixture["sourceExtensions"] == listOf(".ts", ".mts", ".cts"))
            check(policyFixture["moduleFormats"] == listOf("commonjs", "esm"))
            check(
                policyFixture["policyCodes"] ==
                    listOf("NODE_PACKAGE_TYPE_INVALID"),
            )

            @Suppress("UNCHECKED_CAST")
            val mappingFixture =
                JsonSlurper().parse(r2M2P2GeneratedModuleMappingFixture.asFile)
                    as Map<String, Any?>
            check(
                mappingFixture["moduleOutputPolicyFingerprint"] ==
                    r2M2P2NodeModuleOutputPolicyFingerprint,
            )
            check((mappingFixture["validCases"] as? List<*>)?.size == 10)
            check((mappingFixture["invalidCases"] as? List<*>)?.size == 19)
            val packageExamples =
                mappingFixture["packageMetadataInventoryFingerprints"] as? Map<*, *>
                    ?: error("P2-2 package instance fingerprint examples are missing.")
            check(packageExamples.keys == setOf("typeAbsent", "commonjs", "module"))
            check(
                packageExamples.values.all { value ->
                    value is String && Regex("[0-9a-f]{64}").matches(value)
                },
            )

            val mappingSource =
                r2M2P2GeneratedModuleMappingSource.asFile.readText(Charsets.UTF_8)
            val mappingPolicy =
                r2M2P2GeneratedModuleMappingPolicy.asFile.readText(Charsets.UTF_8)
            val kotlinFailureCodes = Regex("GENERATED_MAPPING_[A-Z_]+")
                .findAll(mappingSource)
                .map { match -> match.value }
                .toSet()
            val javaScriptFailureCodes = Regex("GENERATED_MAPPING_[A-Z_]+")
                .findAll(mappingPolicy)
                .map { match -> match.value }
                .toSet()
            check(kotlinFailureCodes.size == 15 && kotlinFailureCodes == javaScriptFailureCodes) {
                "R2-M2-P2 generated mapping failure catalogs drifted: " +
                    "Kotlin=$kotlinFailureCodes JS=$javaScriptFailureCodes"
            }
            listOf(
                "packageMetadataInventoryFingerprint",
                "validateGeneratedPath(sourceMapPath)",
                "R2GeneratedModuleMappingFailureCode.MIXED_PROJECT_INVENTORY",
                "Collections.unmodifiableList(",
            ).forEach { marker ->
                check(marker in mappingSource) {
                    "R2-M2-P2 native mapping authority is missing marker: $marker"
                }
            }

            val profileSource = profileAuthoritySource.asFile.readText(Charsets.UTF_8)
            listOf(
                "PROVISIONAL_NODE_NEXT_REVISION_1",
                "PROVISIONAL_NODE_NEXT_REVISION_2",
                "fun requireExact(",
                "fun requireEndToEndAdmitted(",
                "fun requireWorkerResultAdmittedExactContractValue(",
            ).forEach { marker ->
                check(marker in profileSource) {
                    "R2-M2-P2 exact revision registry is missing marker: $marker"
                }
            }
            val probeSource = probeProtocolSource.asFile.readText(Charsets.UTF_8)
            val serviceSource = compilerServiceSource.asFile.readText(Charsets.UTF_8)
            check("REQUEST_CONTROL_KEYS" in probeSource)
            check("requireProfileRevision(" in probeSource)
            check("R2CompilationProbeProtocol.requestsControlPlane(keySet())" in serviceSource)
            check("requireDebugR2Value<Int>" in serviceSource)
            check(
                "runtimeCapabilitiesFingerprint," in
                    projectArchiveSource.asFile.readText(Charsets.UTF_8),
            )

            logger.lifecycle(
                "R2-M2-P2 authority foundation verified: P2-0 exact policy=" +
                    "$r2M2P2NodeModuleOutputPolicyFingerprint, P2-1 authorities=3/" +
                    "admitted=2, P2-2 parity=10/19, compiler capability mask=15, " +
                    "Node Runtime required=false",
            )
        }
    }

val verifyR2M2P2P3aNodePackageFormatAuthority =
    tasks.register("verifyR2M2P2P3aNodePackageFormatAuthority") {
        group = "verification"
        description =
            "Verifies the partial R2-M2-P2-3A package-format catalog and formal-v1 " +
                "conditional manifest shape, " +
                "and heap/file-backed snapshot identity."
        dependsOn(verifyR2M2P2AuthorityFoundation)
        dependsOn(verifyR2M2P2P3aNodePackageFormatCatalogDesktop)
        dependsOn("testDebugUnitTest")
        dependsOn("compileDebugAndroidTestKotlin")

        val projectSnapshotSource = layout.projectDirectory.file(
            "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                "R2ProjectSnapshot.kt",
        )
        val fileBackedSnapshotSource = layout.projectDirectory.file(
            "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                "R2FileBackedProjectSnapshot.kt",
        )
        val projectArchiveSource = layout.projectDirectory.file(
            "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                "R2ProjectArchive.kt",
        )
        inputs.files(
            r2M2P2NodePackageFormatCatalogSource,
            r2M2P2NodePackageFormatCatalogTest,
            r2M2P2NodePackageFormatCatalogFixture,
            r2M2P2NodePackageFormatCatalogPolicy,
            r2M2P2NodePackageFormatCatalogHarness,
            projectSnapshotSource,
            fileBackedSnapshotSource,
            projectArchiveSource,
        )

        doLast {
            @Suppress("UNCHECKED_CAST")
            val fixture =
                JsonSlurper().parse(r2M2P2NodePackageFormatCatalogFixture.asFile)
                    as Map<String, Any?>
            check(fixture["schema"] ==
                "autojs6.typescript.node-package-format-catalog-parity")
            check(fixture["revision"] == 1)
            check((fixture["validCases"] as? List<*>)?.size == 4)
            check((fixture["invalidTypeCases"] as? List<*>)?.size == 8)
            check((fixture["invalidLookupCases"] as? List<*>)?.size == 5)
            @Suppress("UNCHECKED_CAST")
            val authority = fixture["catalogAuthority"] as? Map<String, Any?>
                ?: error("P2-3A catalog authority is missing.")
            check(authority["profileSchemaRevision"] == 1)
            check(authority["profileRevision"] == 2)
            check(authority["nodeModuleOutputPolicySchemaRevision"] == 1)
            check(authority["nodeModuleOutputPolicyRevision"] == 1)
            check(
                authority["nodeModuleOutputPolicyFingerprint"] ==
                    r2M2P2NodeModuleOutputPolicyFingerprint,
            )
            check(authority["packageMetadataSchemaRevision"] == 1)
            check(authority["packageMetadataPolicyRevision"] == 1)
            check(
                authority["packageMetadataPolicyFingerprint"] ==
                    "fab8fec29ed0f0a1de65594620e233c9a9bef2fa514dac16c3079d5570325e97",
            )

            val catalogSource =
                r2M2P2NodePackageFormatCatalogSource.asFile.readText(Charsets.UTF_8)
            val catalogPolicy =
                r2M2P2NodePackageFormatCatalogPolicy.asFile.readText(Charsets.UTF_8)
            check(
                Regex("NODE_PACKAGE_TYPE_INVALID").findAll(catalogSource).count() == 0 &&
                    "R2NodeModuleOutputPolicy.parsePackageType(record.root)" in catalogSource &&
                    "NODE_PACKAGE_TYPE_INVALID" in catalogPolicy,
            ) {
                "P2-3A package-type derivation no longer uses the frozen P2 policy authority."
            }
            listOf(
                "packageMetadataInventoryCanonicalInput",
                "packageMetadataInventoryFingerprint",
                "R2NodePackageMetadataCatalog.create(",
                "Collections.unmodifiableList(",
            ).forEach { marker ->
                check(marker in catalogSource) {
                    "P2-3A native catalog is missing marker: $marker"
                }
            }

            val snapshotSource = projectSnapshotSource.asFile.readText(Charsets.UTF_8)
            val fileBackedSource = fileBackedSnapshotSource.asFile.readText(Charsets.UTF_8)
            val archiveSource = projectArchiveSource.asFile.readText(Charsets.UTF_8)
            check("val nodePackageFormatCatalog: R2NodePackageFormatCatalog?" in snapshotSource)
            check("R2NodePackageFormatCatalog.create(" in snapshotSource)
            check("verifiedPackageMetadataBytes" in fileBackedSource)
            check("Node package-format catalog was not derived from immutable spool bytes." in
                fileBackedSource)
            check("isNodeRevisionTwo ->" in archiveSource)
            check("TOP_LEVEL_KEYS + \"packageMetadata\"" in archiveSource)
            check("R2GeneratedModuleMappingPolicy.originalSourceUri(" in archiveSource)
            check("nodePackageFormatCatalog = nodePackageFormatCatalog" in archiveSource)
            logger.lifecycle(
                "R2-M2-P2-3A Node package-format authority verified: formal manifest v1 " +
                    "conditional shape, catalog parity=4/8/5, heap/file-backed identity=closed",
            )
        }
    }

val verifyR2M2P2P3bNodePackageFormatTransport =
    tasks.register("verifyR2M2P2P3bNodePackageFormatTransport") {
        group = "verification"
        description =
            "Preserves the R2-M2-P2-3B exact schema-v1 24/37-field Worker transport " +
                "regression " +
                "while the native producer advances to the P2-6 Program shapes."
        dependsOn(verifyR2M2P2P3aNodePackageFormatAuthority)
        dependsOn(verifyR2M2P2P3bNodePackageFormatWorkerTransportDesktop)
        dependsOn(verifyR2M2C6NodeResolutionProgramIntegration)
        dependsOn("testDebugUnitTest")
        dependsOn("compileDebugAndroidTestKotlin")

        val projectFramerSource = layout.projectDirectory.file(
            "src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                "R2ProjectFramedTransport.kt",
        )
        val projectFramerTest = layout.projectDirectory.file(
            "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                "R2ProjectFramedTransportTest.kt",
        )
        val workerSource = compilerHostSourceAssetDirectory.file("compiler-worker.js")
        val pageSource = compilerHostSourceAssetDirectory.file("compiler-host.js")
        inputs.files(
            projectFramerSource,
            projectFramerTest,
            workerSource,
            pageSource,
            r2M2P2NodePackageFormatWorkerTransportFixture,
            r2M2P2NodePackageFormatWorkerTransportHarness,
            r2InputArchiveAuthorityHarness,
            r2M2P2NodePackageFormatCatalogFixture,
            r2M2P2NodePackageFormatCatalogPolicy,
        )

        doLast {
            val framerSource = projectFramerSource.asFile.readText(Charsets.UTF_8)
            val framerTest = projectFramerTest.asFile.readText(Charsets.UTF_8)
            val worker = workerSource.asFile.readText(Charsets.UTF_8)
            val page = pageSource.asFile.readText(Charsets.UTF_8)
            val harness =
                r2M2P2NodePackageFormatWorkerTransportHarness.asFile
                    .readText(Charsets.UTF_8)

            fun sourceSection(label: String, source: String, start: String, end: String): String {
                val startIndex = source.indexOf(start)
                check(startIndex >= 0) { "P2-3B $label start is missing: $start" }
                val endIndex = source.indexOf(end, startIndex + start.length)
                check(endIndex > startIndex) { "P2-3B $label end is missing: $end" }
                return source.substring(startIndex, endIndex)
            }

            fun requireOrderedMarkers(label: String, source: String, markers: List<String>) {
                var cursor = 0
                markers.forEach { marker ->
                    val markerIndex = source.indexOf(marker, cursor)
                    check(markerIndex >= cursor) {
                        "P2-3B $label ordering is missing: $marker"
                    }
                    cursor = markerIndex + marker.length
                }
            }
            listOf(
                "\"runtimeCapabilitiesFingerprint\" to",
                "\"packageMetadataInventoryFingerprint\" to metadata.fingerprint",
                "\"nodePackageFormatCatalogFingerprint\" to exactCatalog.fingerprint",
                "\"nodeModuleOutputPolicyFingerprint\" to",
                "R2_PROJECT_MAXIMUM_START_FRAME_BYTES",
            ).forEach { marker ->
                check(marker in framerSource) {
                    "P2-3B native exact frame is missing marker: $marker"
                }
            }
            check("\"nodePackageFormatCatalogCanonical\" to" !in framerSource)
            listOf(
                "nodeRevisionTwoNoBoundaryUsesExactThirtyFieldStartAndNoConfigFrames",
                "nodeRevisionTwoMetadataUsesExactFortyFieldStartAndPrecedesOrdinaryFiles",
                "nodeRevisionTwoMetadataAndV2TargetsUseExactFortySevenFieldIdentityAndOrder",
                "nodeRevisionTwoMaximumLegalMetadataUsesTheBoundedStartOnlyEnvelope",
                "framerRevalidatesExactNodePackageCombinationsFromDelegatedSnapshots",
                "nodeRevisionTwoNativeTransportAdmitsExactMtsAndCtsSourceKinds",
            ).forEach { marker ->
                check(marker in framerTest) {
                    "P2-3B/P2-6 native frame migration regression is missing: $marker"
                }
            }
            listOf(
                "const provisionalNodeNextRevision2ProfileAuthority = Object.freeze({",
                "P2_NODE_PROGRAM_NOT_ACTIVATED",
                "NODE_PACKAGE_TYPE_INVALID",
                "packageMetadataInventoryFingerprint",
                "nodePackageFormatCatalogFingerprint",
            ).forEach { marker ->
                check(marker in worker) {
                    "P2-3B Worker transport authority is missing marker: $marker"
                }
            }
            listOf(
                "let pendingProjectInputFrame = null;",
                "let projectInputFrameGeneration = 0;",
                "class CancelledProjectInputFrameError extends Error",
                "const createProjectInputFrameToken = (message) => {",
                "const requireActiveProjectInputFrame = (frameToken) => {",
                "pendingProjectInputFrame.cancelled = true;",
                "const terminalAlreadyPosted =",
                "cancelledPendingFrame.terminalPosted = true;",
            ).forEach { marker ->
                check(marker in worker) {
                    "P2-3B Worker async-frame ownership is missing marker: $marker"
                }
            }
            val startHandler = sourceSection(
                "Worker async start handler",
                worker,
                "const acceptProjectInputStart = async (message, frameToken) => {",
                "\nconst acceptProjectInputConfigStart",
            )
            requireOrderedMarkers(
                "Worker async start guards",
                startHandler,
                listOf(
                    "await parseControlledPackageMetadataCanonical(",
                    "requireActiveProjectInputFrame(frameToken);",
                    "await parseControlledPackageTargetCanonical(",
                    "requireActiveProjectInputFrame(frameToken);",
                    "framedInput = {",
                    "acknowledgeProjectInput(message.requestId, message.sequence);",
                ),
            )
            val configEndHandler = sourceSection(
                "Worker async config-end handler",
                worker,
                "const acceptProjectInputConfigEnd = async (message, frameToken) => {",
                "\nconst acceptProjectInputFileStart",
            )
            requireOrderedMarkers(
                "Worker async config-end guards",
                configEndHandler,
                listOf(
                    "const actualSha256 = await bytesToSha256(bytes);",
                    "requireActiveProjectInputFrame(frameToken);",
                    "await resolveControlledTsConfigEffectiveOptions(parsedConfig);",
                    "requireActiveProjectInputFrame(frameToken);",
                    "state.configAccepted = true;",
                    "acknowledgeProjectInput(message.requestId, message.sequence);",
                ),
            )
            val fileEndHandler = sourceSection(
                "Worker async file-end handler",
                worker,
                "const acceptProjectInputFileEnd = async (message, frameToken) => {",
                "\nconst postProjectResultChunk",
            )
            requireOrderedMarkers(
                "Worker async file-end guard",
                fileEndHandler,
                listOf(
                    "const actualSha256 = await bytesToSha256(bytes);",
                    "requireActiveProjectInputFrame(frameToken);",
                    "state.nextSequence += 1;",
                    "acknowledgeProjectInput(message.requestId, message.sequence);",
                ),
            )
            val inputEndHandler = sourceSection(
                "Worker async input-end handler",
                worker,
                "const acceptProjectInputEnd = async (message, frameToken) => {",
                "\nconst acceptProjectResultAck",
            )
            requireOrderedMarkers(
                "Worker async input-end guards",
                inputEndHandler,
                listOf(
                    "const catalog = await deriveNodePackageFormatCatalog(",
                    "requireActiveProjectInputFrame(frameToken);",
                    "Node revision 2 transport is verified, but Program admission is not activated.",
                    "requireActiveProjectInputFrame(frameToken);",
                    "acknowledgeProjectInput(message.requestId, message.sequence);",
                    "return beginProjectResult(message.requestId, compilerProject, frameToken);",
                ),
            )
            listOf(
                "await acceptProjectInputStart(message, frameToken);",
                "await acceptProjectInputConfigEnd(message, frameToken);",
                "await acceptProjectInputFileEnd(message, frameToken);",
                "await acceptProjectInputEnd(message, frameToken);",
            ).forEach { marker ->
                check(marker in worker) {
                    "P2-3B Worker async dispatch is missing marker: $marker"
                }
            }
            check(
                "R2 Node package-format Worker transport verification passed" in harness,
            ) {
                "P2-3B Worker transport harness has no stable success contract."
            }
            listOf(
                "const runDelayedStartSameIdRace = async () => {",
                "const runDelayedFileEndSameIdRace = async () => {",
                "const runDelayedInputEndSameIdRace = async () => {",
                "const runDelayedStartDifferentIdRace = async () => {",
                "await runDelayedStartSameIdRace();",
                "await runDelayedFileEndSameIdRace();",
                "await runDelayedInputEndSameIdRace();",
                "await runDelayedStartDifferentIdRace();",
            ).forEach { marker ->
                check(marker in harness) {
                    "P2-3B Worker async-race harness is missing marker: $marker"
                }
            }
            listOf(
                "const maximumJsonBytes = 128 * 1024;",
                "const maximumProjectInputStartJsonBytes = 576 * 1024;",
                "const nodeRevisionTwoPackageFormatStartKeys = Object.freeze([",
                "hasExactKeys(message, nodeRevisionTwoPackageFormatStartKeys)",
                "const exceedsOrdinaryJsonLimit =",
                "usesExtendedProjectInputStartEnvelope",
                "encoded.length > maximumJsonBytes",
                "jsonByteLength(encoded) > maximumJsonBytes",
            ).forEach { marker ->
                check(marker in page) {
                    "P2-3B compiler page start-only envelope is missing marker: $marker"
                }
            }
            check(
                "padded non-exact Node2 start" in harness,
            ) {
                "P2-3B compiler page exact-shape regression is missing."
            }

            logger.lifecycle(
                "R2-M2-P2-3B transport verified: schema-v1 starts=24/37 and " +
                    "pre-Program terminal unchanged; native producer owner=30/40/47 P2-6",
            )
        }
    }

val verifyR2M2P2P4NodeSourceKindAdmission =
    tasks.register("verifyR2M2P2P4NodeSourceKindAdmission") {
        group = "verification"
        description =
            "Verifies exact Node revision 2 native .mts/.cts archive, heap, and " +
                "file-backed snapshot admission plus its frozen P2-6 exact Program handoff."
        dependsOn(verifyR2M2P2P3bNodePackageFormatTransport)
        dependsOn("testDebugUnitTest")
        dependsOn("compileDebugAndroidTestKotlin")

        inputs.files(
            r2ProjectSourceKindAdmissionSource,
            r2CompilerEscapePolicySource,
            r2ProjectArchiveSource,
            r2ProjectSnapshotSource,
            r2FileBackedProjectSnapshotSource,
            r2ProjectFramedTransportSource,
            r2CompilerWorkerSource,
            r2PublicPreviewMetadataSource,
            r2ProjectSourceKindAdmissionTestSource,
            r2ProjectArchiveTestSource,
            r2ProjectArchiveLoaderTestSource,
            r2ProjectSnapshotTestSource,
            r2FileBackedProjectSnapshotTestSource,
            r2ProjectFramedTransportTestSource,
        )

        doLast {
            val admission = r2ProjectSourceKindAdmissionSource.asFile
                .readText(Charsets.UTF_8)
            val escape = r2CompilerEscapePolicySource.asFile.readText(Charsets.UTF_8)
            val archive = r2ProjectArchiveSource.asFile.readText(Charsets.UTF_8)
            val snapshot = r2ProjectSnapshotSource.asFile.readText(Charsets.UTF_8)
            val fileBacked = r2FileBackedProjectSnapshotSource.asFile
                .readText(Charsets.UTF_8)
            val framer = r2ProjectFramedTransportSource.asFile.readText(Charsets.UTF_8)
            val worker = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            val metadata = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            val tests = listOf(
                r2ProjectSourceKindAdmissionTestSource,
                r2ProjectArchiveTestSource,
                r2ProjectArchiveLoaderTestSource,
                r2ProjectSnapshotTestSource,
                r2FileBackedProjectSnapshotTestSource,
                r2ProjectFramedTransportTestSource,
            ).joinToString("\n") { source -> source.asFile.readText(Charsets.UTF_8) }

            listOf(
                "R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2",
                "path.endsWith(\".mts\") || path.endsWith(\".cts\")",
                "path.endsWith(\".d.mts\")",
                "path.endsWith(\".d.cts\")",
                "sourcePathRejection(path, profileAuthority) != null",
            ).forEach { marker ->
                check(marker in admission) {
                    "P2-4 source-kind authority is missing marker: $marker"
                }
            }
            check("const val REVISION = 2" in escape)
            check("\".cts\"" in escape && "\".mts\"" in escape)
            check("R2ProjectSourceKindAdmission.sourcePathRejection(" in archive)
            check("R2ProjectSourceKindAdmission.isExecutableSourcePath(" in archive)
            check("R2ProjectSourceKindAdmission.sourcePathRejection(" in snapshot)
            check("R2ProjectSourceKindAdmission.isExecutableSourcePath(" in snapshot)
            check("profileAuthority = profileAuthority" in snapshot)
            check("validateProjectPath(rootName, profileAuthority)" in fileBacked)
            check("profileAuthority = profileAuthority" in fileBacked)
            check("Formal schema-v1 exact 30-field no-metadata Program start" in framer)
            check(
                "Formal schema-v1 exact 40-field metadata-only or 47-field" in
                    framer,
            )
            listOf(
                "createNodeRevisionTwoResolutionSession",
                "createNodeRevisionTwoSourceFile",
                "nodeRevisionTwoImpliedModuleFormat",
            ).forEach { marker ->
                check(marker in worker) {
                    "P2-4/P2-6 Worker source-kind handoff is missing: $marker"
                }
            }
            listOf(
                "exactProfileMatrixOpensOnlyNodeRevisionTwoMtsAndCtsExecutables",
                "nodeRevisionTwoOverlayOnlyLiftsExactLowercaseExecutableDeferredKinds",
                "driftedNodeRevisionTwoAuthorityCannotOpenNewSourceKinds",
                "nodeRevisionTwoHeapSnapshotAdmitsCompleteTsMtsCtsRootsWithEitherMetadataMode",
                "nodeSourceKindsRemainProfileBoundCompleteAndDeclarationClosed",
                "nodeRevisionTwoConstructorIndependentlyAdmitsCompleteTsMtsCtsRoots",
                "nodeRevisionTwoFileBackedSnapshotRebuildsCatalogFromImmutableRecords",
                "nodeRevisionTwoSealAdmitsTsMtsCtsAndRevisionOneStillRejectsThem",
                "nodeRevisionTwoManifestAndHeapAdapterAdmitMtsAndCtsInSchemaRevisionOne",
                "nodeRevisionTwoMtsEntryStillRequiresExactRootAndGeneratedUriIdentity",
                "actualZipLoadsNodeRevisionTwoMtsAndCtsForBothFormalConditionalShapes",
                "actualZipUndeclaredMtsUsesNodeTwoIntegrityAndLegacyPathClassification",
                "nodeRevisionTwoNativeTransportAdmitsExactMtsAndCtsSourceKinds",
            ).forEach { marker ->
                check(marker in tests) {
                    "P2-4 regression inventory is missing: $marker"
                }
            }
            requireAdvertisedTypeScriptCompilerCapabilities(metadata, "P2-4")

            logger.lifecycle(
                "R2-M2-P2-4 Node source-kind admission verified: exact Node revision 2 " +
                    "native+Worker .ts/.mts/.cts and C4 unchanged; later result ownership is " +
                    "outside this historical gate",
            )
        }
    }

val verifyR2M2P2P5NodePackageTargetResolutionV2Desktop =
    tasks.register<Exec>("verifyR2M2P2P5NodePackageTargetResolutionV2Desktop") {
        group = "verification"
        description =
            "Verifies the pure R2-M2-P2-5 resolver v2 envelope against TypeScript 6.0.3 " +
                "without Worker or Program activation."
        dependsOn(generateTypeScriptDistribution)
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        inputs.files(
            compilerFile,
            r2M2P2P5NodeResolutionParityFixture,
            r2M2P2P5NodeResolutionParityHarness,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            r2M2P2P5NodeResolutionParityHarness.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            r2M2P2P5NodeResolutionParityFixture.asFile.absolutePath,
            typeScriptVersion,
        )
    }

val verifyR2M2P2P7NodeNextResultDesktop =
    tasks.register<Exec>("verifyR2M2P2P7NodeNextResultDesktop") {
        group = "verification"
        description =
            "Verifies the production R2-M2-P2-7 Node revision-2 Worker framed result with " +
                "real TypeScript 6.0.3, exact output shapes, failures, and regressions."
        dependsOn(generateTypeScriptDistribution)
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        val workerFile = compilerHostSourceAssetDirectory.file("compiler-worker.js")
        inputs.files(
            workerFile,
            compilerFile,
            r2M2P2P6NodeNextProgramFixture,
            r2M2P2P6NodeNextP1RegressionFixture,
            r2M2P2NodePackageFormatWorkerTransportFixture,
            r2M2P2P6PackageMetadataPolicyFixture,
            r2M2P2NodeModuleOutputPolicyFixture,
            r2M2P2P5NodePackageTargetPolicyFixture,
            r2M2P2NodePackageFormatCatalogPolicy,
            r2M2P2P6NodeNextProgramHarness,
            r2InputArchiveAuthorityHarness,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            "--max-old-space-size=512",
            r2M2P2P6NodeNextProgramHarness.asFile.absolutePath,
            workerFile.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            r2M2P2P6NodeNextProgramFixture.asFile.absolutePath,
            r2M2P2P6NodeNextP1RegressionFixture.asFile.absolutePath,
            r2M2P2NodePackageFormatWorkerTransportFixture.asFile.absolutePath,
            r2M2P2P6PackageMetadataPolicyFixture.asFile.absolutePath,
            r2M2P2NodeModuleOutputPolicyFixture.asFile.absolutePath,
            r2M2P2P5NodePackageTargetPolicyFixture.asFile.absolutePath,
            typeScriptVersion,
        )
    }

val verifyR2M2P2P6NodeNextProgramDesktop =
    tasks.register("verifyR2M2P2P6NodeNextProgramDesktop") {
        group = "verification"
        description =
            "Compatibility alias for the superseding P2-7 executable Worker proof; " +
                "the historical P2-6 aggregate remains a static Program/identity gate."
        dependsOn(verifyR2M2P2P7NodeNextResultDesktop)
    }

val verifyCompilerSmokeRhino =
    tasks.register<Exec>("verifyCompilerSmokeRhino") {
        group = "verification"
        description =
            "Runs one real Rhino Worker compilation with JavaScript and Source Map output."
        dependsOn("verifyCompilerDistributionLock")
        val workerFile = compilerHostSourceAssetDirectory.file("compiler-worker.js")
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        val fixtureFile = layout.projectDirectory.file(
            "src/test/resources/r2-project-compiler/provisional-rhino.json",
        )
        val harnessFile = layout.projectDirectory.file(
            "src/test/scripts/verify-r2-project-compiler.js",
        )
        inputs.files(
            workerFile,
            compilerFile,
            fixtureFile,
            r2DiagnosticStopEmitFixture,
            harnessFile,
            r2InputArchiveAuthorityHarness,
        )
        environment("R2_S1_SMOKE_ONLY", "1")
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            "--expose-gc",
            "--max-old-space-size=512",
            harnessFile.asFile.absolutePath,
            workerFile.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            fixtureFile.asFile.absolutePath,
            typeScriptVersion,
        )
    }

val verifyCompilerSmokeNode =
    tasks.register<Exec>("verifyCompilerSmokeNode") {
        group = "verification"
        description =
            "Runs one real NodeNext revision-2 Worker compilation with mjs/cjs Source Maps."
        dependsOn("verifyCompilerDistributionLock")
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        val workerFile = compilerHostSourceAssetDirectory.file("compiler-worker.js")
        inputs.files(
            workerFile,
            compilerFile,
            r2M2P2P6NodeNextProgramFixture,
            r2M2P2P6NodeNextP1RegressionFixture,
            r2M2P2NodePackageFormatWorkerTransportFixture,
            r2M2P2P6PackageMetadataPolicyFixture,
            r2M2P2NodeModuleOutputPolicyFixture,
            r2M2P2P5NodePackageTargetPolicyFixture,
            r2M2P2NodePackageFormatCatalogPolicy,
            r2M2P2P6NodeNextProgramHarness,
            r2InputArchiveAuthorityHarness,
        )
        environment("R2_S1_SMOKE_ONLY", "1")
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            "--max-old-space-size=512",
            r2M2P2P6NodeNextProgramHarness.asFile.absolutePath,
            workerFile.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            r2M2P2P6NodeNextProgramFixture.asFile.absolutePath,
            r2M2P2P6NodeNextP1RegressionFixture.asFile.absolutePath,
            r2M2P2NodePackageFormatWorkerTransportFixture.asFile.absolutePath,
            r2M2P2P6PackageMetadataPolicyFixture.asFile.absolutePath,
            r2M2P2NodeModuleOutputPolicyFixture.asFile.absolutePath,
            r2M2P2P5NodePackageTargetPolicyFixture.asFile.absolutePath,
            typeScriptVersion,
        )
    }

val verifyCompilerSmoke =
    tasks.register("verifyCompilerSmoke") {
        group = "verification"
        description =
            "Runs one real Worker compilation for each public Rhino and NodeNext profile."
        dependsOn(verifyCompilerSmokeRhino, verifyCompilerSmokeNode)
    }

val verifyR2M2P2P5NodePackageTargetResolutionV2 =
    tasks.register("verifyR2M2P2P5NodePackageTargetResolutionV2") {
        group = "verification"
        description =
            "Verifies independent Node revision-2 package-target/resolver v2 authority while " +
                "C6 v1 and the schema-v1 24/37 pre-Program terminal remain unchanged."
        dependsOn(verifyR2M2P2P4NodeSourceKindAdmission)
        dependsOn(verifyR2M2C6BPackageTargetsAndNodeResolutionParity)
        dependsOn(verifyR2M2P2P5NodePackageTargetResolutionV2Desktop)
        dependsOn("testDebugUnitTest")
        dependsOn("compileDebugAndroidTestKotlin")
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")

        inputs.files(
            r2M2P2NodeModuleOutputPolicySource,
            r2M2P2NodeModuleOutputPolicyFixture,
            r2M2P2NodePackageFormatCatalogSource,
            r2M2P2P5NodePackageTargetPolicySource,
            r2M2P2P5NodePackageTargetPolicyTest,
            r2M2P2P5NodePackageTargetPolicyFixture,
            r2NodeResolutionKernelSource,
            r2NodeResolutionKernelTestSource,
            r2NodeResolutionParityFixture,
            r2NodeResolutionParityHarness,
            r2M2P2P5NodeResolutionKernelSource,
            r2M2P2P5NodeResolutionKernelTest,
            r2M2P2P5NodeResolutionParityFixture,
            r2M2P2P5NodeResolutionParityHarness,
            r2PackageTargetPolicySource,
            r2PackageTargetPolicyFixture,
            r2CompilationProfileAuthoritySource,
            r2ProjectArchiveSource,
            r2ProjectFramedTransportSource,
            r2CompilerWorkerSource,
            r2CompilerHostPageSource,
            r2PublicPreviewMetadataSource,
        )

        doLast {
            @Suppress("UNCHECKED_CAST")
            val v1TargetFixture =
                JsonSlurper().parse(r2PackageTargetPolicyFixture.asFile) as Map<String, Any?>
            check(
                (v1TargetFixture["policyRevision"] as Number).toInt() == 1 &&
                    v1TargetFixture["fingerprint"] ==
                    "e34f4825d8f75ba1b674849ff79c2be8f9c1b88fe940add86e6e466a139bbbaf" &&
                    v1TargetFixture["allowedSourceSuffixes"] == listOf(".d.ts", ".ts"),
            ) {
                "P2-5 must not widen the frozen C6 package-target v1 fixture."
            }

            @Suppress("UNCHECKED_CAST")
            val v1ParityFixture =
                JsonSlurper().parse(r2NodeResolutionParityFixture.asFile) as Map<String, Any?>
            val v1Cases = v1ParityFixture["cases"] as? List<*>
                ?: error("C6 resolver v1 parity fixture has no case inventory.")
            val v1Classifications = v1Cases.map { rawCase ->
                (rawCase as? Map<*, *>)?.get("classification") as? String
                    ?: error("C6 resolver v1 parity classification is malformed.")
            }
            check(
                (v1ParityFixture["revision"] as Number).toInt() == 1 &&
                    v1Cases.size == 18 &&
                    v1Classifications.count { it == "supported" } == 12 &&
                    v1Classifications.count { it == "fail-closed" } == 6,
            ) {
                "P2-5 must preserve the exact C6 resolver v1 12/6 regression authority."
            }

            @Suppress("UNCHECKED_CAST")
            val v2TargetFixture =
                JsonSlurper().parse(
                    r2M2P2P5NodePackageTargetPolicyFixture.asFile,
                ) as Map<String, Any?>
            check(
                v2TargetFixture["schema"] ==
                    "autojs6.typescript.node-package-target-policy" &&
                    (v2TargetFixture["policyRevision"] as Number).toInt() == 2 &&
                v2TargetFixture["fingerprint"] ==
                    "175cc694dae3e925d66eac3f77f308c95dc0db7b939238a33022d27812578224" &&
                    (v2TargetFixture["nodeModuleOutputPolicySchemaRevision"] as Number)
                    .toInt() == 1 &&
                    v2TargetFixture["allowedExecutableSuffixes"] ==
                    listOf(".ts", ".mts", ".cts") &&
                    v2TargetFixture["allowedDeclarationSuffixes"] ==
                    listOf(".d.ts", ".d.mts", ".d.cts") &&
                    v2TargetFixture["resolverConditionSource"] ==
                    "implied-module-format" &&
                    (v2TargetFixture["resolverTrustedCustomConditionCount"] as Number)
                    .toInt() == 0 &&
                    v2TargetFixture["inventoryCanonicalDomain"] ==
                    "autojs6.typescript.node-package-target.inventory.v2" &&
                    v2TargetFixture["v1Compatibility"] ==
                    "unchanged-reject-new-kinds",
            ) {
                "P2-5 checked-in Node package-target v2 authority drifted."
            }

            val targetPolicySource =
                r2M2P2P5NodePackageTargetPolicySource.asFile.readText(Charsets.UTF_8)
            listOf(
                "class R2NodePackageTargetV2Inventory private constructor",
                "R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2",
                "R2NodeModuleOutputPolicy.RESOLVER_POLICY_REVISION",
                "TRUSTED_CUSTOM_CONDITION_COUNT = 0",
                "SOURCE_KIND_MODE = \"declaration-first-exact-lowercase\"",
                "listOf(\".ts\", \".mts\", \".cts\")",
                "listOf(\".d.ts\", \".d.mts\", \".d.cts\")",
                "fun toResolutionTargets(): List<R2NodeResolutionTarget>",
                "packageMetadataInventory.fingerprint",
                "Collections.unmodifiableList",
                "Collections.unmodifiableSet",
            ).forEach { marker ->
                check(marker in targetPolicySource) {
                    "P2-5 package-target v2 authority is missing: $marker"
                }
            }

            val resolverV2Source =
                r2M2P2P5NodeResolutionKernelSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "class R2NodeResolutionV2Authority(",
                "class R2NodeResolutionV2SnapshotTargetInventory private constructor",
                "R2NodePackageTargetPolicyV2.TRUSTED_CUSTOM_CONDITION_COUNT",
                "CONDITION_SOURCE = R2NodeModuleOutputPolicy.RESOLVER_CONDITION_SOURCE",
                "TYPE_REFERENCE_CONDITION_SOURCE = \"type-reference-hook\"",
                "fun resolveTypeReference(",
                "fun impliedModuleFormat(",
                "R2ModuleFormat.ESM -> R2NodeResolutionV2ConditionMode.IMPORT",
                "R2ModuleFormat.COMMONJS -> R2NodeResolutionV2ConditionMode.REQUIRE",
                "conditions = emptyList()",
                "targets = authority.resolutionTargets",
                "targetUniverseFingerprint",
                "packageTargetPolicyFingerprint",
                "packageMetadata: R2NodePackageMetadataCatalog? = null",
                "R2NodeResolutionKernel.resolveVersionTwo(",
                "This object has no Program, Worker, compiler-host, filesystem, network",
            ).forEach { marker ->
                check(marker in resolverV2Source) {
                    "P2-5 resolver v2 authority is missing: $marker"
                }
            }

            val sharedKernelSource =
                r2NodeResolutionKernelSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "internal fun resolveVersionTwo(",
                "TargetIndex.createVersionTwo(targets)",
                "private fun resolutionCandidatesVersionTwo(candidate: String): List<String>",
                "candidate.endsWith(\".d.js\")",
                "candidate.endsWith(\".d.mjs\")",
                "candidate.endsWith(\".d.cjs\")",
                "candidate.removeSuffix(\".mjs\") + \".mts\"",
                "candidate.removeSuffix(\".cjs\") + \".cts\"",
                "candidate.removeSuffix(\".js\") + \".ts\"",
                "private fun resolutionCandidates(candidate: String): List<String>",
                "listOf(\"${'$'}base.ts\", \"${'$'}base.d.ts\")",
            ).forEach { marker ->
                check(marker in sharedKernelSource) {
                    "P2-5 resolver kernel/v1 compatibility is missing: $marker"
                }
            }

            @Suppress("UNCHECKED_CAST")
            val v2ParityFixture =
                JsonSlurper().parse(
                    r2M2P2P5NodeResolutionParityFixture.asFile,
                ) as Map<String, Any?>
            val v2Cases = v2ParityFixture["cases"] as? List<*>
                ?: error("P2-5 resolver v2 parity fixture has no case inventory.")
            val v2Classifications = v2Cases.map { rawCase ->
                (rawCase as? Map<*, *>)?.get("classification") as? String
                    ?: error("P2-5 resolver v2 parity classification is malformed.")
            }
            check(
                (v2ParityFixture["revision"] as Number).toInt() == 2 &&
                    (v2ParityFixture["resolverPolicyRevision"] as Number).toInt() == 2 &&
                    v2ParityFixture["typescriptVersion"] == typeScriptVersion &&
                    v2Cases.size == 18 &&
                    v2Classifications.count { it == "supported" } == 14 &&
                    v2Classifications.count { it == "fail-closed" } == 4,
            ) {
                "P2-5 resolver v2 14/4 parity envelope drifted."
            }
            val parityHarness =
                r2M2P2P5NodeResolutionParityHarness.asFile.readText(Charsets.UTF_8)
            listOf(
                "ts.resolveModuleName(",
                "ts.resolveTypeReferenceDirective(",
                "ts.ModuleResolutionKind.NodeNext",
                "impliedResolutionMode",
                "supported === 14 && failClosed === 4",
                "complete-target-universe",
                "ModuleResolutionHost-only, Program=0",
            ).forEach { marker ->
                check(marker in parityHarness) {
                    "P2-5 independent TypeScript oracle is missing: $marker"
                }
            }
            listOf(
                "ts.sys",
                "importScripts",
                "fetch(",
                "XMLHttpRequest",
                "WebSocket",
                "createIncrementalProgram",
                "resolveModuleNameLiterals",
            ).forEach { forbidden ->
                check(forbidden !in parityHarness) {
                    "P2-5 pure TypeScript oracle must not use production/host API: $forbidden"
                }
            }

            val tests = listOf(
                r2M2P2P5NodePackageTargetPolicyTest,
                r2M2P2P5NodeResolutionKernelTest,
            ).joinToString("\n") { source -> source.asFile.readText(Charsets.UTF_8) }
            listOf(
                "revisionTwoAcceptsExactExecutableAndDeclarationKindsDeclarationFirst",
                "revisionOneStillRejectsEveryRevisionTwoOnlyKind",
                "revisionTwoSubstitutesRuntimeExtensionsToOneExactSourceFamily",
                "completeSnapshotTargetUniverseResolvesRelativeSourcesWithoutMetadata",
                "metadataOnlyRootImportsCanResolveOrdinarySnapshotTargets",
                "revisionTwoDeclarationTargetsAreExactAndNeverRuntimeFallbacks",
                "moduleConditionFollowsImpliedFormatAndTypeReferenceUsesTypesOnly",
                "invalidOrGhostContainingPathReturnsStableFailureOutcome",
                "sharedTypeScript603RevisionTwoParityFixtureMatchesPureKernel",
            ).forEach { marker ->
                check(marker in tests) {
                    "P2-5 regression inventory is missing: $marker"
                }
            }

            val productionSources = listOf(
                r2ProjectArchiveSource,
                r2ProjectFramedTransportSource,
                r2CompilerWorkerSource,
                r2CompilerHostPageSource,
            ).joinToString("\n") { source -> source.asFile.readText(Charsets.UTF_8) }
            listOf(
                "R2NodePackageTargetPolicyV2",
                "R2NodePackageTargetV2Inventory",
                "R2NodeResolutionKernelV2",
                "R2NodeResolutionV2Authority",
                "R2NodeResolutionV2SnapshotTargetInventory",
            ).forEach { integratedSymbol ->
                check(integratedSymbol in productionSources) {
                    "P2-5 authority is no longer bound to the P2-6 production transport: " +
                        integratedSymbol
                }
            }
            check(
                "P2_NODE_PROGRAM_NOT_ACTIVATED" in productionSources &&
                    "createNodeRevisionTwoResolutionSession" in productionSources &&
                    "parseNodeRevisionTwoPackageTargetCanonical" in productionSources,
            ) {
                "P2-5 historical authority is not preserved by the P2-6 Program owner."
            }

            val productionWorker =
                r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            val productionHostPage =
                r2CompilerHostPageSource.asFile.readText(Charsets.UTF_8)
            val productionFramer =
                r2ProjectFramedTransportSource.asFile.readText(Charsets.UTF_8)

            check(
                Regex(
                    """const isNodeRevision2Transport\s*=\s*""" +
                        """isNodeRevision2Start\s*\|\|\s*isNodePackageFormatStart\s*;""",
                ).findAll(productionWorker).count() == 1,
            ) {
                "P2-5 Worker Node revision-2 transport expression drifted or gained another owner."
            }
            check(
                Regex(
                    """const isPackageTargetStart\s*=\s*""" +
                        """hasExactKeys\(message,\s*packageTargetKeys\)\s*&&\s*""" +
                        """matchesControlledTsConfigAuthority\(message\)\s*&&""",
                ).findAll(productionWorker).count() == 1,
            ) {
                "P2-5 package-target admission is no longer owned by controlled C6 config."
            }
            val exactPackageTargetParseOwner = Regex(
                """const packageTargets\s*=\s*isPackageTargetStart\s*""" +
                    """\?\s*await parseControlledPackageTargetCanonical\(\s*""" +
                    """message\.packageTargetCanonical\s*,\s*""" +
                    """message\.packageTargetFingerprint\s*,\s*""" +
                    """message\.packageTargetFileCount\s*,\s*""" +
                    """message\.packageTargetByteLength\s*,\s*""" +
                    """packageMetadata\s*,?\s*\)\s*:\s*null\s*;""",
            )
            check(
                exactPackageTargetParseOwner.findAll(productionWorker).count() == 1 &&
                    Regex("""\bparseControlledPackageTargetCanonical\s*\(""")
                        .findAll(productionWorker)
                        .count() == 1,
            ) {
                "P2-5 package-target parser gained a non-C6 call site or changed parse ownership."
            }

            val workerInputEndMarker =
                "const acceptProjectInputEnd = async (message, frameToken) => {"
            val workerInputEndStart = productionWorker.indexOf(workerInputEndMarker)
            val workerInputEndSearchStart = if (workerInputEndStart >= 0) {
                workerInputEndStart + workerInputEndMarker.length
            } else {
                0
            }
            val workerInputEndEnd = productionWorker.indexOf(
                "const acceptProjectResultAck = (message) => {",
                startIndex = workerInputEndSearchStart,
            )
            check(workerInputEndStart >= 0 && workerInputEndEnd > workerInputEndStart) {
                "P2-5 could not isolate acceptProjectInputEnd."
            }
            val workerInputEnd =
                productionWorker.substring(workerInputEndStart, workerInputEndEnd)
            val nodeTransportGateStart =
                workerInputEnd.indexOf("if (state.nodePackageFormatMode !== null) {")
            val ordinaryProgramPreparationStart = workerInputEnd.indexOf("let rootNames;")
            val compilerProjectStart = workerInputEnd.indexOf("const compilerProject =")
            val programDispatchStart = workerInputEnd.indexOf(
                "return beginProjectResult(message.requestId, compilerProject, frameToken);",
            )
            check(
                nodeTransportGateStart >= 0 &&
                    ordinaryProgramPreparationStart > nodeTransportGateStart &&
                    compilerProjectStart > ordinaryProgramPreparationStart &&
                    programDispatchStart > compilerProjectStart,
            ) {
                "P2-5 legacy Node-v2 terminal no longer precedes P2-6 Program admission."
            }
            val nodeTransportTerminalRegion = workerInputEnd.substring(
                nodeTransportGateStart,
                ordinaryProgramPreparationStart,
            )
            check(
                Regex("""nodePackageFormatPolicyCodes\.programNotActivated""")
                    .findAll(nodeTransportTerminalRegion)
                    .count() == 1 &&
                    "Node revision 2 transport is verified, but Program admission is not activated." in
                    nodeTransportTerminalRegion &&
                    "nodePackageFormatPolicyCodes.resultNotActivated" !in
                    nodeTransportTerminalRegion,
            ) {
                "P2-5 schema-v1 24/37 pre-Program terminal identity drifted."
            }
            listOf(
                "createProjectCompilerHost(",
                "ts.createIncrementalProgram(",
                "beginProjectResult(",
            ).forEach { forbiddenProgramMarker ->
                check(forbiddenProgramMarker !in nodeTransportTerminalRegion) {
                    "P2-5 Node-v2 terminal region leaked Program authority: " +
                        forbiddenProgramMarker
                }
            }

            val productionHostStartKeyBlocks = Regex(
                """const nodeRevisionTwoPackageFormatStartKeys\s*=\s*""" +
                    """Object\.freeze\(\[(.*?)\]\);""",
                RegexOption.DOT_MATCHES_ALL,
            ).findAll(productionHostPage).toList()
            check(productionHostStartKeyBlocks.size == 1) {
                "P2-5 host must declare exactly one Node revision-2 start-key block."
            }
            val hostStartKeyLiteral = Regex("""^"([^"]+)",?$""")
            val productionHostStartKeys =
                productionHostStartKeyBlocks.single().groupValues[1]
                    .lineSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .map { line ->
                        hostStartKeyLiteral.matchEntire(line)?.groupValues?.get(1)
                            ?: error(
                                "P2-5 host start-key block contains a non-literal entry: $line",
                            )
                    }
                    .toList()
            val expectedProductionHostStartKeys = setOf(
                "callerInventoryFingerprint",
                "configKind",
                "configSchemaRevision",
                "declarationByteLength",
                "declarationFileCount",
                "declarationInventoryFingerprint",
                "declarationProfileFingerprint",
                "entryPath",
                "fileCount",
                "inputArchiveSchemaRevision",
                "inputQuotaRevision",
                "kind",
                "largestFileBytes",
                "nodeModuleOutputPolicyFingerprint",
                "nodeModuleOutputPolicyRevision",
                "nodeModuleOutputPolicySchemaRevision",
                "nodePackageFormatCatalogFingerprint",
                "nodePackageFormatCatalogRevision",
                "nodePackageFormatSchemaRevision",
                "packageMetadataByteLength",
                "packageMetadataCanonical",
                "packageMetadataFileCount",
                "packageMetadataInventoryFingerprint",
                "packageMetadataPolicyFingerprint",
                "packageMetadataPolicyRevision",
                "packageMetadataSchemaRevision",
                "profileRevision",
                "profileSchemaRevision",
                "requestId",
                "retainedFileCount",
                "retainedInventoryFingerprint",
                "runtimeCapabilitiesFingerprint",
                "sequence",
                "targetProfile",
                "totalBytes",
                "workerSpinMilliseconds",
                "workerSpinPhase",
            )
            check(
                productionHostStartKeys.size == 37 &&
                    productionHostStartKeys.toSet() == expectedProductionHostStartKeys,
            ) {
                "P2-5 host Node revision-2 37-key envelope drifted: $productionHostStartKeys"
            }
            check(
                Regex(
                    """hasExactKeys\(\s*message,\s*""" +
                        """nodeRevisionTwoPackageFormatStartKeys\s*\)""",
                ).findAll(productionHostPage).count() == 1,
            ) {
                "P2-5 host 37-key block is missing its sole exact-shape classifier."
            }

            val exactPackageTargetFramerFence = Regex(
                """require\(\s*packageTargets\s*==\s*null\s*\|\|\s*""" +
                    """rawConfig\s*!=\s*null\s*\)\s*\{\s*""" +
                    """"Package target transport requires controlled raw config authority\."\s*\}""",
            )
            check(
                "Formal schema-v1 exact 30-field no-metadata Program start" in
                    productionFramer &&
                    "Formal schema-v1 exact 40-field metadata-only or 47-field" in
                    productionFramer &&
                    "\"resolverPolicyRevision\" to" in productionFramer &&
                    "\"snapshotTargetInventoryFingerprint\" to" in productionFramer &&
                    "\"targetUniverseFingerprint\" to" in productionFramer &&
                    "\"nodePackageTargetCanonical\" to" in productionFramer &&
                    exactPackageTargetFramerFence.findAll(productionFramer).count() == 1,
            ) {
                "P2-5 native authority did not migrate to the exact P2-6 30/40/47 producer."
            }

            val profileSource =
                r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            val endToEndSet = Regex(
                """endToEndAdmittedAuthorities[^=]*= setOf\((.*?)\)""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(profileSource)?.groupValues?.get(1)
                ?: error("P2-5 could not locate the end-to-end profile authority set.")
            check("PROVISIONAL_NODE_NEXT_REVISION_2" in endToEndSet) {
                "P2-5 must tolerate the later S1-5 Node revision-2 end-to-end admission."
            }

            val metadataSource =
                r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "P2-5")

            logger.lifecycle(
                "R2-M2-P2-5 resolver/package-target v2 verified: target kinds=6, " +
                    "TypeScript parity=14/4, complete target universe, types hook, " +
                    "C6 v1=12/6 and schema-v1 24/37 terminal unchanged, " +
                    "P2-6 production owner=30/40/47; later result ownership is outside " +
                    "this historical gate",
            )
        }
    }

val verifyR2M2P2P6NodeNextProgramIntegration =
    tasks.register("verifyR2M2P2P6NodeNextProgramIntegration") {
        group = "verification"
        description =
            "Verifies the production Node revision-2 Worker/TypeScript Program owner through " +
                "the frozen exact 30/40/47 starts without claiming the later result boundary."
        dependsOn(verifyR2M2P2P5NodePackageTargetResolutionV2)
        dependsOn(verifyR2M2P1ProvisionalNodeNextProfile)
        dependsOn(verifyR2M2C6NodeResolutionProgramIntegration)
        dependsOn("testDebugUnitTest")
        dependsOn("compileDebugAndroidTestKotlin")
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")

        inputs.files(
            r2M2P2P6NodeNextProgramFixture,
            r2M2P2P6NodeNextP1RegressionFixture,
            r2M2P2NodePackageFormatWorkerTransportFixture,
            r2M2P2NodePackageFormatWorkerTransportHarness,
            r2M2P2NodePackageFormatCatalogPolicy,
            r2M2P2P6PackageMetadataPolicyFixture,
            r2M2P2NodeModuleOutputPolicyFixture,
            r2M2P2P5NodePackageTargetPolicyFixture,
            r2M2P2P5NodeResolutionParityFixture,
            r2M2P2P5NodeResolutionParityHarness,
            r2ProjectFramedTransportSource,
            r2ProjectFramedTransportTestSource,
            r2CompilerWorkerSource,
            r2CompilerHostPageSource,
            r2CompilationProfileAuthoritySource,
            r2PublicPreviewMetadataSource,
        )

        doLast {
            @Suppress("UNCHECKED_CAST")
            val fixture =
                JsonSlurper().parse(r2M2P2P6NodeNextProgramFixture.asFile) as
                    Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val legacyTransport =
                JsonSlurper().parse(
                    r2M2P2NodePackageFormatWorkerTransportFixture.asFile,
                ) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val programShapes = fixture["exactProgramStartKeys"] as
                Map<String, List<String>>
            @Suppress("UNCHECKED_CAST")
            val legacyShapes = legacyTransport["exactStartKeys"] as
                Map<String, List<String>>
            val program30 = checkNotNull(programShapes["noMetadata"])
            val program40 = checkNotNull(programShapes["metadata"])
            val program47 = checkNotNull(programShapes["metadataAndTargets"])
            val transport24 = checkNotNull(legacyShapes["noBoundary"])
            val transport37 = checkNotNull(legacyShapes["present"])
            val moduleOutputIdentity = setOf(
                "nodeModuleOutputPolicyFingerprint",
                "nodeModuleOutputPolicyRevision",
                "nodeModuleOutputPolicySchemaRevision",
            )
            val resolverIdentity = setOf(
                "resolverPolicyRevision",
                "snapshotTargetInventoryFingerprint",
                "targetUniverseFingerprint",
            )
            val nodeTargetIdentity = setOf(
                "nodePackageTargetByteLength",
                "nodePackageTargetCanonical",
                "nodePackageTargetFileCount",
                "nodePackageTargetFingerprint",
                "nodePackageTargetPolicyFingerprint",
                "nodePackageTargetPolicyRevision",
                "nodePackageTargetSchemaRevision",
            )
            check(
                fixture["schema"] ==
                    "autojs6.typescript.nodenext-program-integration" &&
                    (fixture["revision"] as Number).toInt() == 1 &&
                    fixture["typescriptVersion"] == typeScriptVersion &&
                    legacyTransport["schema"] ==
                    "autojs6.typescript.node-package-format-worker-transport" &&
                    program30.size == 30 && program30.toSet().size == 30 &&
                    program40.size == 40 && program40.toSet().size == 40 &&
                    program47.size == 47 && program47.toSet().size == 47 &&
                    transport24.size == 24 && transport24.toSet().size == 24 &&
                    transport37.size == 37 && transport37.toSet().size == 37 &&
                    program30.toSet() - moduleOutputIdentity - resolverIdentity ==
                    transport24.toSet() &&
                    program40.toSet() - resolverIdentity == transport37.toSet() &&
                    program47.toSet() - nodeTargetIdentity == program40.toSet() &&
                    resolverIdentity.none { key -> key in transport24 || key in transport37 } &&
                    nodeTargetIdentity.none { key -> key in program40 } &&
                    nodeTargetIdentity.all { key -> key.startsWith("nodePackageTarget") } &&
                    setOf(
                        "packageTargetByteLength",
                        "packageTargetCanonical",
                        "packageTargetFileCount",
                        "packageTargetFingerprint",
                        "packageTargetPolicyFingerprint",
                        "packageTargetPolicyRevision",
                        "packageTargetSchemaRevision",
                    ).none { key -> key in program47 },
            ) {
                "P2-6 exact 30/40/47 Program identity no longer extends schema-v1 24/37 " +
                    "transport identity without aliasing the C6 package-target fields."
            }

            @Suppress("UNCHECKED_CAST")
            val scenarios = fixture["scenarios"] as? List<Map<String, Any?>>
                ?: error("P2-6 Program fixture has no scenario inventory.")
            val scenarioIds = scenarios.map { scenario -> scenario["id"] as String }
            check(
                scenarioIds.size == 5 &&
                    scenarioIds.toSet() == setOf(
                        "metadata-only-root-imports",
                        "target-bearing-relative-exports-types",
                        "exports-no-match",
                        "target-not-in-snapshot",
                        "no-metadata-clean-successor",
                    ) &&
                    scenarios.map { scenario -> scenario["transportMode"] }.toSet() ==
                    setOf("noMetadata", "metadata", "metadataAndTargets"),
            ) {
                "P2-6 Program scenario inventory drifted."
            }

            val worker = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            val hostPage = r2CompilerHostPageSource.asFile.readText(Charsets.UTF_8)
            val framer = r2ProjectFramedTransportSource.asFile.readText(Charsets.UTF_8)
            val framerTests =
                r2ProjectFramedTransportTestSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "parseNodeRevisionTwoPackageTargetCanonical",
                "deriveNodeRevisionTwoResolutionAuthority",
                "resolveNodeRevisionTwoSnapshot",
                "createNodeRevisionTwoResolutionSession",
                "nodeRevisionTwoImpliedModuleFormat",
                "createNodeRevisionTwoSourceFile",
                "resolveModuleNameLiterals",
                "resolveTypeReferenceDirectiveReferences",
                "ts.createIncrementalProgram({",
                "P2_NODE_PROGRAM_NOT_ACTIVATED",
            ).forEach { marker ->
                check(marker in worker) {
                    "P2-6 Worker Program integration is missing: $marker"
                }
            }
            listOf(
                "const nodeRevisionTwoProgramKeys = nodeRevision2Keys.concat([",
                "const nodePackageFormatProgramKeys = nodePackageFormatKeys.concat([",
                "const nodePackageTargetProgramKeys = nodePackageFormatProgramKeys.concat([",
                "hasExactKeys(message, nodeRevisionTwoProgramKeys)",
                "hasExactKeys(message, nodePackageFormatProgramKeys)",
                "hasExactKeys(message, nodePackageTargetProgramKeys)",
                "const isNodeRevisionTwoProgramTransport =",
                "isNodeRevisionTwoProgramStart ||",
                "isNodePackageFormatProgramStart ||",
                "isNodePackageTargetProgramStart;",
            ).forEach { marker ->
                check(marker in worker) {
                    "P2-6 exact Program start classifier is missing: $marker"
                }
            }
            listOf(
                "nodeRevisionTwoProgramKeys",
                "nodePackageFormatProgramKeys",
                "nodePackageTargetProgramKeys",
            ).forEach { keyBlock ->
                check(
                    Regex("""hasExactKeys\(message,\s*$keyBlock\)""")
                        .findAll(worker)
                        .count() == 1,
                ) {
                    "P2-6 $keyBlock does not have one exact-shape owner."
                }
            }
            check(
                Regex(
                    """const isNodeRevision2Transport\s*=\s*""" +
                        """isNodeRevision2Start\s*\|\|\s*isNodePackageFormatStart\s*;""",
                ).findAll(worker).count() == 1 &&
                    Regex("""nodePackageFormatPolicyCodes\.programNotActivated""")
                        .findAll(worker)
                        .count() == 1,
            ) {
                "P2-6 changed the schema-v1 24/37 owner or duplicated a terminal owner."
            }
            check(
                Regex("""\bcreateNodeRevisionTwoResolutionSession\(""")
                    .findAll(worker)
                    .count() == 1 &&
                    Regex("""\bts\.createIncrementalProgram\(""")
                        .findAll(worker)
                        .count() == 1,
            ) {
                "P2-6 Program or resolver-session owner is no longer unique."
            }

            listOf(
                "nodeRevisionTwoPackageFormatStartKeys",
                "nodeRevisionTwoProgramPackageFormatStartKeys",
                "nodeRevisionTwoProgramPackageTargetStartKeys",
                "resolverPolicyRevision",
                "snapshotTargetInventoryFingerprint",
                "targetUniverseFingerprint",
                "nodePackageTargetSchemaRevision",
            ).forEach { marker ->
                check(marker in hostPage) {
                    "P2-6 compiler page envelope classifier is missing: $marker"
                }
            }
            listOf(
                "Formal schema-v1 exact 30-field no-metadata Program start",
                "Formal schema-v1 exact 40-field metadata-only or 47-field",
                "\"resolverPolicyRevision\" to",
                "\"snapshotTargetInventoryFingerprint\" to",
                "\"targetUniverseFingerprint\" to",
                "\"nodePackageTargetCanonical\" to",
            ).forEach { marker ->
                check(marker in framer) {
                    "P2-6 native producer is missing: $marker"
                }
            }
            listOf(
                "nodeRevisionTwoNoBoundaryUsesExactThirtyFieldStartAndNoConfigFrames",
                "nodeRevisionTwoMetadataUsesExactFortyFieldStartAndPrecedesOrdinaryFiles",
                "nodeRevisionTwoMetadataAndV2TargetsUseExactFortySevenFieldIdentityAndOrder",
                "nodeRevisionTwoNativeTransportAdmitsExactMtsAndCtsSourceKinds",
            ).forEach { marker ->
                check(marker in framerTests) {
                    "P2-6 native framing regression is missing: $marker"
                }
            }

            val profileSource =
                r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            val endToEndSet = Regex(
                """endToEndAdmittedAuthorities[^=]*= setOf\((.*?)\)""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(profileSource)?.groupValues?.get(1)
                ?: error("P2-6 could not locate the end-to-end authority set.")
            check(
                "PROVISIONAL_NODE_NEXT_REVISION_2" in endToEndSet,
            ) {
                "P2-6 must tolerate the later S1-5 Node revision-2 end-to-end admission."
            }
            val metadataSource =
                r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "P2-6")

            logger.lifecycle(
                "R2-M2-P2-6 production Worker Program verified: exact starts=30/40/47, " +
                    ".ts/.mts/.cts+NodeNext resolver hooks+js/mjs/cjs mapping handoff, " +
                    "P1/C6/schema-v1 24/37 identity bound; P2-7 owns the later result/private " +
                    "output admission and public capability remains closed",
            )
        }
    }

val verifyR2M2P2P7NodeNextResultAndPrivateOutput =
    tasks.register("verifyR2M2P2P7NodeNextResultAndPrivateOutput") {
        group = "verification"
        description =
            "Verifies exact Node revision-2 Worker framed results and native-only snapshot-bound " +
                "generated-module/private-output authority under the current public admission."
        dependsOn(verifyR2M2P2P6NodeNextProgramIntegration)
        dependsOn(verifyR2M2P2P7NodeNextResultDesktop)
        dependsOn("testDebugUnitTest")
        dependsOn("compileDebugAndroidTestKotlin")
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyR2PublicPreviewSourcePolicy")
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")

        inputs.files(
            r2M2P2P6NodeNextProgramFixture,
            r2M2P2P6NodeNextProgramHarness,
            r2InputArchiveAuthorityHarness,
            r2M2P2P6NodeNextP1RegressionFixture,
            r2M2P2NodePackageFormatWorkerTransportFixture,
            r2CompilerWorkerSource,
            r2CompilerHostProtocolSource,
            r2ProjectSnapshotSource,
            r2M2P2GeneratedModuleMappingSource,
            r2NodeRevisionTwoResultAuthoritySource,
            r2ProjectCompilationSource,
            r2PrivateOutputArchiveSource,
            compilerWebViewHostSource,
            r2CompilationProfileAuthoritySource,
            r2PublicPreviewMetadataSource,
            r2ProjectFramedTransportTestSource,
            r2ProjectArchiveLoaderTestSource,
            r2NodeRevisionTwoResultAuthorityTestSource,
            r2ProjectCompilationTestSource,
            r2PrivateOutputArchiveTestSource,
            r2CompilationProfileAuthorityTestSource,
        )

        doLast {
            val worker = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            val harness = r2M2P2P6NodeNextProgramHarness.asFile.readText(Charsets.UTF_8)
            listOf(
                "const result = await compileProject(project, frameToken);",
                "bytes = new TextEncoder().encode(JSON.stringify(result));",
                "kind: \"projectResultStart\"",
                "const acceptProjectResultAck = (message) => {",
                "postProjectResultChunk(state, nextSequence);",
                "completeProjectExecutionPhase(state.execution, \"emit\");",
                "state.bytes.fill(0);",
                "kind: \"projectResultEnd\"",
            ).forEach { marker ->
                check(marker in worker) {
                    "P2-7 Worker framed-result owner is missing: $marker"
                }
            }
            listOf(
                "P2_NODE_RESULT_NOT_ACTIVATED",
                "nodeRevisionTwoResultClosed",
            ).forEach { staleFence ->
                check(staleFence !in worker) {
                    "P2-7 Worker retained the superseded result fence: $staleFence"
                }
            }
            check(
                Regex("""const expectedGeneratedModulePath = \(""")
                    .findAll(worker)
                    .count() == 1 &&
                    Regex("""\bexpectedGeneratedModulePath\(""")
                        .findAll(worker)
                        .count() == 1,
            ) {
                "P2-7 Worker generated-path helper must have one owner and one emit caller."
            }
            listOf(
                "const configNoneResultKeys = Object.freeze([",
                "const outputKeys = Object.freeze([\"path\", \"sha256\", \"sourcePaths\", \"text\"]);",
                "exact result=11 keys, outputs=4 keys, stop-and-wait ACK/sha256",
                "ts/mts/cts->js/mjs/cjs+maps",
                "stable result failures=2x2",
                "clean P2 successor=1",
                "schema-v1 24/37 identity preserved, P1 regression=1",
                "P1 regression=1",
                "P2 framed results=7",
                "default resolver/ts.sys/network=0",
            ).forEach { marker ->
                check(marker in harness) {
                    "P2-7 executable Worker result proof is missing: $marker"
                }
            }
            val workerOutputShape = Regex(
                """const outputKeys = Object\.freeze\(\[(.*?)]\);""",
            ).find(harness)?.groupValues?.get(1)
                ?: error("P2-7 could not isolate the frozen Worker output shape.")
            listOf(
                "projectId",
                "projectNamespace",
                "originalSourceUri",
                "generatedUri",
                "canonicalEvidence",
                "fingerprint",
            ).forEach { untrustedClaim ->
                check(untrustedClaim !in workerOutputShape) {
                    "P2-7 Worker output wire gained a native-only mapping claim: $untrustedClaim"
                }
            }

            val protocol = r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "exactSnapshot: R2ProjectInputSnapshot? = null",
                "Node revision-2 result parsing requires its exact accepted snapshot.",
                "R2CompilationProfileRegistry.requireWorkerResultAdmittedExactContractValue(",
                "R2NodeRevisionTwoResultAuthority.derive(",
                "exactSnapshot.containsAcceptedDiagnosticPath(path)",
                "path.endsWith(\".mjs\") || path.endsWith(\".mjs.map\")",
                "path.endsWith(\".cjs\") || path.endsWith(\".cjs.map\")",
            ).forEach { marker ->
                check(marker in protocol) {
                    "P2-7 native framed-result parser is missing: $marker"
                }
            }

            val resultAuthority =
                r2NodeRevisionTwoResultAuthoritySource.asFile.readText(Charsets.UTF_8)
            listOf(
                "internal data class R2NodeRevisionTwoResultAuthority private constructor(",
                "val acceptedProjectId: String",
                "val generatedModuleMappings: List<R2GeneratedModuleMapping>",
                "R2GeneratedModuleMappingPolicy.mapInventory(requests)",
                "fun derive(",
                "fun requireExact(",
                "modules.size == mappings.size && sourceMaps.size == mappings.size",
                "actualPaths.sorted() == expectedPaths",
            ).forEach { marker ->
                check(marker in resultAuthority) {
                    "P2-7 native result authority is missing: $marker"
                }
            }

            val compilation = r2ProjectCompilationSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "val nodeRevisionTwoResultAuthority: R2NodeRevisionTwoResultAuthority? = null",
                "val generatedModuleMappings: List<R2GeneratedModuleMapping>",
                "isNodeRevisionTwo == (nodeRevisionTwoResultAuthority != null)",
                "R2NodeRevisionTwoResultAuthority.requireExact(",
                "return copy(exactSnapshot = snapshot)",
            ).forEach { marker ->
                check(marker in compilation) {
                    "P2-7 compilation snapshot binding is missing: $marker"
                }
            }

            val privateOutput = r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "compilationOutcome.exactSnapshot !== acceptedArchive.snapshot",
                "snapshot.nodePackageTargetV2Inventory?.files?.forEach",
                "snapshot.acceptedProjectId != authority.projectId",
                "R2NodeRevisionTwoResultAuthority.requireExact(",
                "compilation.nodeRevisionTwoResultAuthority",
                "generatedModuleMappings",
                "appendJsonField(\"originalSourceUri\", mapping.originalSourceUri)",
                "appendJsonField(\"generatedUri\", mapping.generatedUri)",
                "appendJsonField(\"canonicalEvidence\", mapping.canonicalEvidence)",
                "appendJsonField(\"fingerprint\", mapping.fingerprint)",
            ).forEach { marker ->
                check(marker in privateOutput) {
                    "P2-7 private-output snapshot/mapping authority is missing: $marker"
                }
            }
            listOf(protocol, privateOutput).forEach { nativeConsumer ->
                listOf(
                    "removeSuffix(\".mts\")",
                    "removeSuffix(\".cts\")",
                ).forEach { duplicateMapping ->
                    check(duplicateMapping !in nativeConsumer) {
                        "P2-7 native consumer duplicated the generated mapping policy: " +
                            duplicateMapping
                    }
                }
            }

            val webViewHost = compilerWebViewHostSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "exactSnapshot = requireNotNull(pending.projectSnapshot)",
                "R2ProjectSourceKindAdmission.isExecutableSourcePath(",
                "snapshot.nodePackageTargetV2Inventory?.files?.forEach",
            ).forEach { marker ->
                check(marker in webViewHost) {
                    "P2-7 WebView/native result binding is missing: $marker"
                }
            }

            val resultTests = listOf(
                r2ProjectFramedTransportTestSource,
                r2ProjectArchiveLoaderTestSource,
                r2NodeRevisionTwoResultAuthorityTestSource,
                r2PrivateOutputArchiveTestSource,
                r2CompilationProfileAuthorityTestSource,
            ).joinToString("\n") { source -> source.asFile.readText(Charsets.UTF_8) }
            listOf(
                "nodeRevisionTwoFramedResultRequiresExactSnapshotAndDerivesNativeMappings",
                "nodeRevisionTwoConfigNoneResolutionDiagnosticKeepsNativeSnapshotAuthority",
                "productionNodeRevisionTwoOutputStageRequiresExactAdaptedSnapshotIdentity",
                "derivesCanonicalMappingsForTsMtsAndCtsOutputs",
                "exactPackageTypeAndProjectIdDriveNativeMappingEvidence",
                "rejectsMissingProjectIdentityWrongOutputKindAndSnapshotRootDrift",
                "nodeRevisionTwoMixedOutputsUseFormalRevisionOneAndRejectProjectIdentityDrift",
                "nodeRevisionTwoIsExactRegisteredOnlyAndCannotReplaceTheRevisionOneDefault",
            ).forEach { marker ->
                check(marker in resultTests) {
                    "P2-7 framed-result/native-mapping regression inventory is missing: $marker"
                }
            }

            val profileSource =
                r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            val endToEndSet = Regex(
                """endToEndAdmittedAuthorities[^=]*= setOf\((.*?)\)""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(profileSource)?.groupValues?.get(1)
                ?: error("P2-7 could not locate the end-to-end authority set.")
            check(
                "PROVISIONAL_NODE_NEXT_REVISION_2" in endToEndSet &&
                    Regex(
                        """workerResultAdmittedAuthorities[^=]*=\s*""" +
                            """endToEndAdmittedAuthorities""",
                        RegexOption.DOT_MATCHES_ALL,
                    ).containsMatchIn(profileSource),
            ) {
                "P2-7 must preserve result admission after the S1-5 end-to-end promotion."
            }

            val metadataSource =
                r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "P2-7")

            logger.lifecycle(
                "R2-M2-P2-7 result/private output verified: Worker result=11 keys, " +
                    "outputs=4 keys, P2 framed results=7, native exact-snapshot mapping/archive " +
                    "authority bound, P1/C6/schema-v1 24/37/exact 30/40/47 preserved, " +
                    "S1-5 public admission active",
            )
        }
    }

val verifyR2M2P2P8FinalAggregate =
    tasks.register("verifyR2M2P2P8FinalAggregate") {
        group = "verification"
        description =
            "Closes the R2-M2-P2 internal module-kind/output policy by aggregating P1, " +
                "C6 v1, P2-0..P2-7, APK/API/source/public fences, and the final regression " +
                "inventory while tolerating the later S1-5 Node revision-2 admission."

        // Keep every historical slice directly visible in the final graph. The dependencies are
        // intentionally explicit even where the P2 chain already reaches an earlier gate.
        dependsOn(verifyR2M2P1ProvisionalNodeNextProfile)
        dependsOn(verifyR2M2C6NodeResolutionProgramIntegration)
        dependsOn(verifyR2M2P2AuthorityFoundation)
        dependsOn(verifyR2M2P2P3aNodePackageFormatAuthority)
        dependsOn(verifyR2M2P2P3bNodePackageFormatTransport)
        dependsOn(verifyR2M2P2P4NodeSourceKindAdmission)
        dependsOn(verifyR2M2P2P5NodePackageTargetResolutionV2)
        dependsOn(verifyR2M2P2P6NodeNextProgramIntegration)
        dependsOn(verifyR2M2P2P7NodeNextResultAndPrivateOutput)

        // APK inventory, frozen API, production source, controlled-config admission, and
        // optional-plugin independence are P2 completion evidence rather than a later Core-only
        // side effect.
        dependsOn(verifyDebugTypeScriptDistributionApk)
        dependsOn(verifyReleaseTypeScriptDistributionApk)
        dependsOn(verifyDebugR2DeclarationDistributionApk)
        dependsOn(verifyReleaseR2DeclarationDistributionApk)
        dependsOn("verifyDebugCompilerHostApk")
        dependsOn("verifyReleaseCompilerHostApk")
        dependsOn("verifyR2ReleaseCompatibilityApks")
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyR2PublicPreviewSourcePolicy")
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        dependsOn("testDebugUnitTest", "assembleDebugAndroidTest")

        val nodeModuleOutputPolicyTest = layout.projectDirectory.file(
            "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                "R2NodeModuleOutputPolicyTest.kt",
        )
        val generatedModuleMappingTest = layout.projectDirectory.file(
            "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                "R2GeneratedModuleMappingTest.kt",
        )
        val nodePackageFormatCatalogTest = layout.projectDirectory.file(
            "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                "R2NodePackageFormatCatalogTest.kt",
        )
        val compilationProbeProtocolTest = layout.projectDirectory.file(
            "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                "R2CompilationProbeProtocolTest.kt",
        )
        val provisionalNodeNextHarness = layout.projectDirectory.file(
            "src/test/scripts/verify-r2-provisional-nodenext-profile.js",
        )

        inputs.files(
            r2M2P2NodeModuleOutputPolicyFixture,
            r2M2P2GeneratedModuleMappingFixture,
            r2M2P2NodePackageFormatCatalogFixture,
            r2M2P2NodePackageFormatWorkerTransportFixture,
            r2PackageTargetPolicyFixture,
            r2NodeResolutionParityFixture,
            r2M2P2P5NodePackageTargetPolicyFixture,
            r2M2P2P5NodeResolutionParityFixture,
            r2M2P2P6NodeNextProgramFixture,
            r2M2P2P6NodeNextP1RegressionFixture,
            provisionalNodeNextHarness,
            r2NodeResolutionProgramHarness,
            r2M2P2P6NodeNextProgramHarness,
            r2InputArchiveAuthorityHarness,
            r2CompilationProfileAuthoritySource,
            r2ProjectSourceKindAdmissionSource,
            r2ProjectFramedTransportSource,
            r2CompilerWorkerSource,
            r2CompilerHostProtocolSource,
            r2NodeRevisionTwoResultAuthoritySource,
            r2ProjectCompilationSource,
            r2PrivateOutputArchiveSource,
            r2PublicPreviewMetadataSource,
            r2CompilationProbeSource,
            r2PublicPreviewServiceSource,
            nodeModuleOutputPolicyTest,
            generatedModuleMappingTest,
            nodePackageFormatCatalogTest,
            r2M2P2P5NodePackageTargetPolicyTest,
            r2NodeResolutionKernelTestSource,
            r2M2P2P5NodeResolutionKernelTest,
            r2CompilationProfileAuthorityTestSource,
            compilationProbeProtocolTest,
            r2ProjectSourceKindAdmissionTestSource,
            r2ProjectFramedTransportTestSource,
            r2ProjectArchiveLoaderTestSource,
            r2NodeRevisionTwoResultAuthorityTestSource,
            r2PrivateOutputArchiveTestSource,
        )

        doLast {
            @Suppress("UNCHECKED_CAST")
            fun parseFixture(file: File): Map<String, Any?> =
                JsonSlurper().parse(file) as Map<String, Any?>

            fun classifications(fixture: Map<String, Any?>, label: String): List<String> =
                (fixture["cases"] as? List<*>)?.map { rawCase ->
                    (rawCase as? Map<*, *>)?.get("classification") as? String
                        ?: error("P2-8 $label contains a malformed classification.")
                } ?: error("P2-8 $label has no case inventory.")

            val moduleOutputFixture = parseFixture(r2M2P2NodeModuleOutputPolicyFixture.asFile)
            check(
                moduleOutputFixture["fingerprint"] == r2M2P2NodeModuleOutputPolicyFingerprint &&
                    moduleOutputFixture["sourceExtensions"] == listOf(".ts", ".mts", ".cts") &&
                    moduleOutputFixture["moduleFormats"] == listOf("commonjs", "esm"),
            ) {
                "P2-8 module-kind/output policy identity drifted."
            }

            val generatedMappingFixture = parseFixture(r2M2P2GeneratedModuleMappingFixture.asFile)
            check(
                (generatedMappingFixture["validCases"] as? List<*>)?.size == 10 &&
                    (generatedMappingFixture["invalidCases"] as? List<*>)?.size == 19,
            ) {
                "P2-8 generated mapping parity must remain 10 valid / 19 invalid cases."
            }

            val packageFormatFixture = parseFixture(r2M2P2NodePackageFormatCatalogFixture.asFile)
            check(
                (packageFormatFixture["validCases"] as? List<*>)?.size == 4 &&
                    (packageFormatFixture["invalidTypeCases"] as? List<*>)?.size == 8 &&
                    (packageFormatFixture["invalidLookupCases"] as? List<*>)?.size == 5,
            ) {
                "P2-8 package-format catalog parity must remain 4/8/5."
            }

            val c6TargetFixture = parseFixture(r2PackageTargetPolicyFixture.asFile)
            check(
                (c6TargetFixture["policyRevision"] as? Number)?.toInt() == 1 &&
                    c6TargetFixture["allowedSourceSuffixes"] == listOf(".d.ts", ".ts"),
            ) {
                "P2-8 widened the frozen C6 v1 package-target authority."
            }
            val c6Classifications = classifications(
                parseFixture(r2NodeResolutionParityFixture.asFile),
                "C6 v1 resolver fixture",
            )
            check(
                c6Classifications.size == 18 &&
                    c6Classifications.count { it == "supported" } == 12 &&
                    c6Classifications.count { it == "fail-closed" } == 6,
            ) {
                "P2-8 must preserve the exact C6 resolver v1 12/6 inventory."
            }

            val revisionTwoTargetFixture =
                parseFixture(r2M2P2P5NodePackageTargetPolicyFixture.asFile)
            check(
                (revisionTwoTargetFixture["policyRevision"] as? Number)?.toInt() == 2 &&
                    revisionTwoTargetFixture["allowedExecutableSuffixes"] ==
                    listOf(".ts", ".mts", ".cts") &&
                    revisionTwoTargetFixture["allowedDeclarationSuffixes"] ==
                    listOf(".d.ts", ".d.mts", ".d.cts") &&
                    revisionTwoTargetFixture["v1Compatibility"] ==
                    "unchanged-reject-new-kinds",
            ) {
                "P2-8 Node revision-2 target policy or its v1 compatibility drifted."
            }
            val revisionTwoClassifications = classifications(
                parseFixture(r2M2P2P5NodeResolutionParityFixture.asFile),
                "Node revision-2 resolver fixture",
            )
            check(
                revisionTwoClassifications.size == 18 &&
                    revisionTwoClassifications.count { it == "supported" } == 14 &&
                    revisionTwoClassifications.count { it == "fail-closed" } == 4,
            ) {
                "P2-8 must preserve the exact Node revision-2 resolver 14/4 inventory."
            }

            val executableHarnesses = listOf(
                provisionalNodeNextHarness.asFile.readText(Charsets.UTF_8),
                r2NodeResolutionProgramHarness.asFile.readText(Charsets.UTF_8),
                r2M2P2P6NodeNextProgramHarness.asFile.readText(Charsets.UTF_8),
            ).joinToString("\n")
            listOf(
                "ts.sys/network/Node Runtime=0, clean successors=2",
                "6 module + 1 type-reference resolutions (5 package targets)",
                "token boundary=65536/65537, default resolver/ts.sys/network=0",
                "exact starts=30/40/47",
                "exact result=11 keys, outputs=4 keys, stop-and-wait ACK/sha256",
                "ts/mts/cts->js/mjs/cjs+maps",
                "stable result failures=2x2",
                "clean P2 successor=1",
                "schema-v1 24/37 identity preserved, P1 regression=1",
                "P2 framed results=7, default resolver/ts.sys/network=0",
            ).forEach { marker ->
                check(marker in executableHarnesses) {
                    "P2-8 executable regression evidence is missing: $marker"
                }
            }

            val regressionTests = listOf(
                nodeModuleOutputPolicyTest,
                generatedModuleMappingTest,
                nodePackageFormatCatalogTest,
                r2M2P2P5NodePackageTargetPolicyTest,
                r2NodeResolutionKernelTestSource,
                r2M2P2P5NodeResolutionKernelTest,
                r2CompilationProfileAuthorityTestSource,
                compilationProbeProtocolTest,
                r2ProjectSourceKindAdmissionTestSource,
                r2ProjectFramedTransportTestSource,
                r2ProjectArchiveLoaderTestSource,
                r2NodeRevisionTwoResultAuthorityTestSource,
                r2PrivateOutputArchiveTestSource,
            ).joinToString("\n") { source -> source.asFile.readText(Charsets.UTF_8) }
            listOf(
                "checkedInFixtureExactlyMirrorsThePolicyAuthority",
                "sharedFixtureProvesKotlinMappingAndJavaScriptGoldenParity",
                "sharedFixtureFreezesExactC5AndP2CatalogIdentity",
                "revisionOneStillRejectsEveryRevisionTwoOnlyKind",
                "sharedTypeScript603ParityFixtureFreezesTheClosedWorldSubset",
                "sharedTypeScript603RevisionTwoParityFixtureMatchesPureKernel",
                "provisionalNodeNextAuthorityIsExactConfigNoneTsOnlyAndIndependentFromRhino",
                "nodeRevisionTwoIsExactRegisteredOnlyAndCannotReplaceTheRevisionOneDefault",
                "publicNodeRevisionTwoCanEnterTheEndToEndPipeline",
                "exactProfileMatrixOpensOnlyNodeRevisionTwoMtsAndCtsExecutables",
                "nodeRevisionTwoNoBoundaryUsesExactThirtyFieldStartAndNoConfigFrames",
                "nodeRevisionTwoMetadataUsesExactFortyFieldStartAndPrecedesOrdinaryFiles",
                "nodeRevisionTwoMetadataAndV2TargetsUseExactFortySevenFieldIdentityAndOrder",
                "nodeRevisionTwoFramedResultRequiresExactSnapshotAndDerivesNativeMappings",
                "nodeRevisionTwoDiagnosticsKeepTrustedDeclarationsDtsOnlyAndPackageTargetsV2",
                "productionNodeRevisionTwoOutputStageRequiresExactAdaptedSnapshotIdentity",
                "derivesCanonicalMappingsForTsMtsAndCtsOutputs",
                "nodeRevisionTwoMixedOutputsUseFormalRevisionOneAndRejectProjectIdentityDrift",
            ).forEach { marker ->
                check(marker in regressionTests) {
                    "P2-8 final JVM regression inventory is missing: $marker"
                }
            }

            val protocolSource =
                r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8)
            check(
                "private fun String.isSupportedDeclarationPath(): Boolean = endsWith(\".d.ts\")" in
                    protocolSource,
            ) {
                "P2-8 trusted declaration diagnostics must remain .d.ts-only."
            }
            check(
                Regex(
                    """phase == R2ProjectDiagnosticPhase\.RESOLUTION\s*&&\s*""" +
                        """path\.startsWith\("/packages/"\)\s*&&\s*""" +
                        """path\.isSupportedResolutionTargetPath\(isNodeRevisionTwo\)""",
                    RegexOption.DOT_MATCHES_ALL,
                ).containsMatchIn(protocolSource) &&
                    Regex(
                        """private fun String\.isSupportedResolutionTargetPath""" +
                            """\(isNodeRevisionTwo: Boolean\): Boolean =\s*""" +
                            """endsWith\("\.ts"\)\s*\|\|\s*""" +
                            """isNodeRevisionTwo\s*&&\s*\(\s*""" +
                            """endsWith\("\.mts"\)\s*\|\|\s*endsWith\("\.cts"\)\s*\|\|\s*""" +
                            """endsWith\("\.d\.mts"\)\s*\|\|\s*endsWith\("\.d\.cts"\)""",
                        RegexOption.DOT_MATCHES_ALL,
                    ).containsMatchIn(protocolSource),
            ) {
                "P2-8 resolution diagnostics must retain the /packages Node-v2 suffix branch."
            }

            val productionServiceSource =
                r2PublicPreviewServiceSource.asFile.readText(Charsets.UTF_8)
            check(
                Regex(
                    """R2CompilationProfileRegistry\.requireEndToEndAdmitted\(\s*""" +
                        """profileAuthority\s*\)""",
                ).findAll(productionServiceSource).count() == 1,
            ) {
                "P2-8 production probe must retain one exact end-to-end registry admission call."
            }

            val profileSource =
                r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            val endToEndSet = Regex(
                """endToEndAdmittedAuthorities[^=]*= setOf\((.*?)\)""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(profileSource)?.groupValues?.get(1)
                ?: error("P2-8 could not isolate the end-to-end authority set.")
            val admittedAuthorityNames = Regex("PROVISIONAL_[A-Z0-9_]+")
                .findAll(endToEndSet)
                .map { match -> match.value }
                .toSet()
            val workerResultAdmission = Regex(
                """workerResultAdmittedAuthorities[^=]*=\s*""" +
                    """endToEndAdmittedAuthorities""",
                RegexOption.DOT_MATCHES_ALL,
            )
            check(
                admittedAuthorityNames == setOf(
                    "PROVISIONAL_RHINO",
                    "PROVISIONAL_NODE_NEXT_REVISION_1",
                    "PROVISIONAL_NODE_NEXT_REVISION_2",
                ) &&
                    workerResultAdmission.containsMatchIn(profileSource),
            ) {
                "P2-8 must preserve exact S1-5 end-to-end and Worker-result admission."
            }

            val metadataSource =
                r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "P2-8")

            logger.lifecycle(
                "R2-M2-P2-8 final aggregate verified: P1 + C6 v1=12/6 + P2-0..P2-7, " +
                    "mapping=10/19, catalog=4/8/5, resolver v2=14/4, exact starts=30/40/47, " +
                    "result=11 keys, outputs=4 keys, APK/API/source fences closed, " +
                    "Node revision 2 public end-to-end, compiler capability mask=15",
            )
        }
    }

val r2M2P3CompilerBundleGoldenFixture = layout.projectDirectory.file(
    "src/test/resources/r2-compiler-bundle/p3-v1.json",
)
val r2M2P3CompilerBundleGoldenHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-m2-p3-compiler-bundle.js",
)
val r2M2P3CompilerBundleGoldenTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2CompilerBundleGoldenFixtureTest.kt",
)
val r2M2P3BuildScript = layout.projectDirectory.file("build.gradle.kts")

val verifyR2M2P3CompilerBundleGoldenDesktop =
    tasks.register<Exec>("verifyR2M2P3CompilerBundleGoldenDesktop") {
        group = "verification"
        description =
            "Runs the R2-M2-P3 deterministic two-file import, circular-dependency, and " +
                "mixed .ts + .js + .json desktop compiler-bundle oracle without changing " +
                "production profile admission or creating a runtime engine."
        dependsOn(verifyTypeScriptDistribution, verifyR2DeclarationDistribution)
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        val typeScriptLibDirectory = generatedTypeScriptDistributionDirectory.map {
            it.dir("lib")
        }
        inputs.files(
            compilerFile,
            r2M2P3CompilerBundleGoldenFixture,
            r2M2P3CompilerBundleGoldenHarness,
        )
        inputs.dir(generatedR2DeclarationDistributionDirectory)
        inputs.dir(typeScriptLibDirectory)
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            "--max-old-space-size=512",
            r2M2P3CompilerBundleGoldenHarness.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            generatedR2DeclarationDistributionDirectory.get().asFile.absolutePath,
            typeScriptLibDirectory.get().asFile.absolutePath,
            r2M2P3CompilerBundleGoldenFixture.asFile.absolutePath,
            typeScriptVersion,
        )
    }

val verifyR2M2P3CompilerBundleGolden =
    tasks.register("verifyR2M2P3CompilerBundleGolden") {
        group = "verification"
        description =
            "Closes R2-M2-P3 with deterministic desktop compiler semantics and JVM/APK/API/" +
                "source fences while keeping mixed-source production admission, public " +
                "capabilities, and runtime execution closed."

        dependsOn(verifyR2M2P2P8FinalAggregate)
        dependsOn(verifyR2M2P3CompilerBundleGoldenDesktop)
        dependsOn(verifyDebugTypeScriptDistributionApk)
        dependsOn(verifyReleaseTypeScriptDistributionApk)
        dependsOn(verifyDebugR2DeclarationDistributionApk)
        dependsOn(verifyReleaseR2DeclarationDistributionApk)
        dependsOn("verifyDebugCompilerHostApk")
        dependsOn("verifyReleaseCompilerHostApk")
        dependsOn("verifyR2ReleaseCompatibilityApks")
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyR2PublicPreviewSourcePolicy")
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        dependsOn("testDebugUnitTest", "assembleDebugAndroidTest")

        inputs.files(
            r2M2P3CompilerBundleGoldenFixture,
            r2M2P3CompilerBundleGoldenHarness,
            r2M2P3CompilerBundleGoldenTestSource,
            r2M2P3BuildScript,
            r2CompilationProfileAuthoritySource,
            r2ProjectSourceKindAdmissionSource,
            r2CompilerWorkerSource,
            r2CompilerHostProtocolSource,
            r2ProjectCompilationSource,
            r2PrivateOutputArchiveSource,
            r2PublicPreviewMetadataSource,
            r2PublicPreviewServiceSource,
        )

        doLast {
            fun between(source: String, start: String, end: String): String {
                val startIndex = source.indexOf(start)
                val endIndex = source.indexOf(end, startIndex + start.length)
                check(startIndex >= 0 && endIndex > startIndex) {
                    "R2-M2-P3 could not isolate production fence $start..$end."
                }
                return source.substring(startIndex, endIndex)
            }

            val fixtureSource =
                r2M2P3CompilerBundleGoldenFixture.asFile.readText(Charsets.UTF_8)
            listOf(
                "\"scope\": \"desktop-compiler-only/no-production-admission\"",
                "\"outputCollectionCanonical\": " +
                    "\"sorted outputs; UTF-8(path + NUL + decimal byteLength + NUL + " +
                    "lowercase sha256 + LF)\"",
                "\"id\": \"rhino-two-file-import\"",
                "\"id\": \"node-two-file-import\"",
                "\"id\": \"rhino-source-cycle\"",
                "\"id\": \"node-source-cycle\"",
                "\"id\": \"mixed-ts-js-json\"",
                "\"productionAdmission\": false",
                "\"outputCollectionSha256\"",
            ).forEach { marker ->
                check(marker in fixtureSource) {
                    "R2-M2-P3 compiler-bundle fixture marker is missing: $marker"
                }
            }
            check(
                "\"expected\": null" !in fixtureSource &&
                    "PENDING" !in fixtureSource &&
                    Regex("\\\"outputCollectionSha256\\\"").findAll(fixtureSource).count() == 5,
            ) {
                "R2-M2-P3 compiler-bundle fixture is not fully sealed."
            }

            val harnessSource =
                r2M2P3CompilerBundleGoldenHarness.asFile.readText(Charsets.UTF_8)
            listOf(
                "ts.createIncrementalProgram({",
                "cases=5, compiles=20",
                "ts.sys/host-fs/network/output execution=0",
                "fixture.execution.expectedHostFsCalls",
                "fixture.execution.expectedTsSysCalls",
                "fixture.execution.expectedNetworkCalls",
                "fixture.execution.expectedEmittedJavaScriptExecutions",
            ).forEach { marker ->
                check(marker in harnessSource) {
                    "R2-M2-P3 direct oracle marker is missing: $marker"
                }
            }
            mapOf(
                "eval(...)" to Regex("""(?:^|[^\w$])eval\s*\("""),
                "Function(...)" to Regex("""(?:^|[^\w$])(?:new\s+)?Function\s*\("""),
                "node:vm require" to Regex("""require\s*\(\s*["']node:vm["']\s*\)"""),
                "vm context execution" to Regex("""(?:^|[^\w$])runIn(?:This|New)Context\s*\("""),
            ).forEach { (executionMarker, pattern) ->
                check(!pattern.containsMatchIn(harnessSource)) {
                    "R2-M2-P3 direct oracle must not execute emitted JavaScript: " +
                        executionMarker
                }
            }

            val fixtureTestSource =
                r2M2P3CompilerBundleGoldenTestSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "fixtureFreezesFiveScenariosAndRebuildsEveryOutputCollectionIdentity",
                "mixedOracleStaysOutsideEveryRegisteredProductionSourceKind",
                "PROVISIONAL_NODE_NEXT_REVISION_1",
                "R2ProjectSourceKindAdmission.isExecutableSourcePath",
            ).forEach { marker ->
                check(marker in fixtureTestSource) {
                    "R2-M2-P3 JVM fixture-contract marker is missing: $marker"
                }
            }

            val profileSource =
                r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            val rhinoFixedOptions = between(
                profileSource,
                "PROVISIONAL_RHINO_FIXED_OPTIONS_JSON",
                "PROVISIONAL_NODE_NEXT_FIXED_OPTIONS_JSON",
            )
            val nodeFixedOptions = between(
                profileSource,
                "PROVISIONAL_NODE_NEXT_FIXED_OPTIONS_JSON",
                "val PROVISIONAL_RHINO:",
            )
            listOf(
                "Rhino" to rhinoFixedOptions,
                "NodeNext" to nodeFixedOptions,
            ).forEach { (label, fixedOptions) ->
                check(
                    "\\\"allowJs\\\":false" in fixedOptions &&
                        "resolveJsonModule" !in fixedOptions,
                ) {
                    "R2-M2-P3 $label production config-none options admitted mixed inputs."
                }
            }

            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            val rhinoWorkerOptions = between(
                workerSource,
                "const createProvisionalRhinoCompilerOptions",
                "const createProvisionalNodeNextCompilerOptions",
            )
            val nodeWorkerOptions = between(
                workerSource,
                "const createProvisionalNodeNextCompilerOptions",
                "const compileProject",
            )
            listOf(
                "Rhino" to rhinoWorkerOptions,
                "NodeNext" to nodeWorkerOptions,
            ).forEach { (label, fixedOptions) ->
                check(
                    "allowJs: false" in fixedOptions &&
                        "resolveJsonModule" !in fixedOptions,
                ) {
                    "R2-M2-P3 $label production Worker options admitted mixed inputs."
                }
            }
            check("resolveJsonModule: false" in workerSource) {
                "R2-M2-P3 controlled Worker default no longer keeps JSON resolution closed."
            }

            val sourceKindSource =
                r2ProjectSourceKindAdmissionSource.asFile.readText(Charsets.UTF_8)
            check(
                "path.endsWith(\".ts\") -> true" in sourceKindSource &&
                    "path.endsWith(\".mts\") || path.endsWith(\".cts\")" in sourceKindSource &&
                    ".js" !in sourceKindSource &&
                    ".json" !in sourceKindSource,
            ) {
                "R2-M2-P3 production source-kind admission escaped the frozen ts/mts/cts matrix."
            }
            val workerExecutableKinds = between(
                workerSource,
                "const isExecutableProjectSourcePath",
                "const expectedGeneratedModulePath",
            )
            check(
                ".ts" in workerExecutableKinds &&
                    ".mts" in workerExecutableKinds &&
                    ".cts" in workerExecutableKinds &&
                    ".js" !in workerExecutableKinds &&
                    ".json" !in workerExecutableKinds,
            ) {
                "R2-M2-P3 production Worker executable source kinds drifted."
            }

            val protocolSource =
                r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8)
            val outputPathFence = between(
                protocolSource,
                "private fun validateProjectOutputPath",
                "private fun String.isSupportedDeclarationPath",
            )
            check(
                "path.endsWith(\".js\")" in outputPathFence &&
                    "path.endsWith(\".mjs\")" in outputPathFence &&
                    "path.endsWith(\".cjs\")" in outputPathFence &&
                    ".json" !in outputPathFence,
            ) {
                "R2-M2-P3 production result parser admitted a desktop-only JSON output."
            }
            val privateOutputSource =
                r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8)
            val outputSchemaSource =
                r2OutputArchiveSchemaV1Source.asFile.readText(Charsets.UTF_8)
            check(
                "const val KIND_JAVASCRIPT = \"javascript\"" in outputSchemaSource &&
                    "const val KIND_SOURCE_MAP = \"source_map\"" in outputSchemaSource &&
                    "private const val KIND_JAVASCRIPT = " +
                    "R2OutputArchiveSchemaV1.KIND_JAVASCRIPT" in privateOutputSource &&
                    "private const val KIND_SOURCE_MAP = " +
                    "R2OutputArchiveSchemaV1.KIND_SOURCE_MAP" in privateOutputSource &&
                    "KIND_JSON" !in outputSchemaSource &&
                    "KIND_JSON" !in privateOutputSource,
            ) {
                "R2-M2-P3 production private bundle gained an unreviewed JSON output kind."
            }

            val endToEndSet = Regex(
                """endToEndAdmittedAuthorities[^=]*= setOf\((.*?)\)""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(profileSource)?.groupValues?.get(1)
                ?: error("R2-M2-P3 could not isolate the end-to-end authority set.")
            val admittedAuthorityNames = Regex("PROVISIONAL_[A-Z0-9_]+")
                .findAll(endToEndSet)
                .map { match -> match.value }
                .toSet()
            check(
                admittedAuthorityNames == setOf(
                    "PROVISIONAL_RHINO",
                    "PROVISIONAL_NODE_NEXT_REVISION_1",
                    "PROVISIONAL_NODE_NEXT_REVISION_2",
                ) &&
                    Regex(
                        """workerResultAdmittedAuthorities[^=]*=\s*""" +
                            """endToEndAdmittedAuthorities""",
                        RegexOption.DOT_MATCHES_ALL,
                    ).containsMatchIn(profileSource),
            ) {
                "R2-M2-P3 must preserve the later S1-5 Node revision-2 admission."
            }

            val metadataSource =
                r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M2-P3")

            val buildScriptSource = r2M2P3BuildScript.asFile.readText(Charsets.UTF_8)
            val publicSourcePolicy = between(
                buildScriptSource,
                "tasks.register(\"verifyR2PublicPreviewSourcePolicy\")",
                "inputs.files(",
            )
            check(
                "complete M3-S2" in publicSourcePolicy &&
                    "complete M3-D3" !in publicSourcePolicy &&
                    "complete M3-D2" !in publicSourcePolicy &&
                    "complete M3-D1" !in publicSourcePolicy &&
                    "complete M2-X3" !in publicSourcePolicy &&
                    "complete M2-X2" !in publicSourcePolicy &&
                    "complete M2-X1" !in publicSourcePolicy &&
                    "complete M2-P4" !in publicSourcePolicy &&
                    "complete M2-P3" !in publicSourcePolicy &&
                    "complete M2-P2/P2-8" !in publicSourcePolicy,
            ) {
                "R2-M2-P3 historical gate did not advance to the current M3-S2 source policy."
            }

            logger.lifecycle(
                "R2-M2-P3 compiler-bundle golden verified: desktop TypeScript semantics + " +
                    "JVM fixture contract, production allowJs/resolveJsonModule/source-kind/" +
                    "JSON-output admission unchanged, Node revision 2 public end-to-end, " +
                    "compiler capability mask=15, runtime engines=0",
            )
        }
    }

val r2M2P4CompilerSyntaxFixture = layout.projectDirectory.file(
    "src/test/resources/r2-compiler-syntax/p4-v1.json",
)
val r2M2P4CompilerSyntaxHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-m2-p4-compiler-syntax.js",
)
val r2M2P4CompilerSyntaxTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2CompilerSyntaxMatrixFixtureTest.kt",
)

val verifyR2M2P4CompilerSyntaxDesktop =
    tasks.register<Exec>("verifyR2M2P4CompilerSyntaxDesktop") {
        group = "verification"
        description =
            "Runs the R2-M2-P4 deterministic enum, namespace, class-field, generic, " +
                "optional-chaining, async, and standard-decorator syntax matrix across the " +
                "frozen Rhino and Node profiles without creating a runtime engine."
        dependsOn(verifyTypeScriptDistribution, verifyR2DeclarationDistribution)
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        val typeScriptLibDirectory = generatedTypeScriptDistributionDirectory.map {
            it.dir("lib")
        }
        inputs.files(
            compilerFile,
            r2M2P4CompilerSyntaxFixture,
            r2M2P4CompilerSyntaxHarness,
        )
        inputs.dir(generatedR2DeclarationDistributionDirectory)
        inputs.dir(typeScriptLibDirectory)
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            "--max-old-space-size=512",
            r2M2P4CompilerSyntaxHarness.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            generatedR2DeclarationDistributionDirectory.get().asFile.absolutePath,
            typeScriptLibDirectory.get().asFile.absolutePath,
            r2M2P4CompilerSyntaxFixture.asFile.absolutePath,
            typeScriptVersion,
        )
    }

val verifyR2M2P4CompilerSyntax =
    tasks.register("verifyR2M2P4CompilerSyntax") {
        group = "verification"
        description =
            "Closes R2-M2-P4 with a deterministic 7-syntax x 2-profile desktop/JVM matrix " +
                "while keeping legacy decorators, production authorities, and runtime " +
                "execution closed."

        dependsOn(verifyR2M2P3CompilerBundleGolden)
        dependsOn(verifyR2M2P4CompilerSyntaxDesktop)
        dependsOn(verifyDebugTypeScriptDistributionApk)
        dependsOn(verifyReleaseTypeScriptDistributionApk)
        dependsOn(verifyDebugR2DeclarationDistributionApk)
        dependsOn(verifyReleaseR2DeclarationDistributionApk)
        dependsOn("verifyDebugCompilerHostApk")
        dependsOn("verifyReleaseCompilerHostApk")
        dependsOn("verifyR2ReleaseCompatibilityApks")
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyR2PublicPreviewSourcePolicy")
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        dependsOn("testDebugUnitTest", "compileDebugAndroidTestKotlin")

        inputs.files(
            r2M2P4CompilerSyntaxFixture,
            r2M2P4CompilerSyntaxHarness,
            r2M2P4CompilerSyntaxTestSource,
            r2M2P3BuildScript,
            r2CompilationProfileAuthoritySource,
            r2ControlledTsConfigPolicySource,
            r2ControlledTsConfigPolicyTestSource,
            r2ProjectSourceKindAdmissionSource,
            r2CompilerWorkerSource,
            r2PublicPreviewMetadataSource,
            r2PublicPreviewServiceSource,
        )

        doLast {
            fun between(source: String, start: String, end: String): String {
                val startIndex = source.indexOf(start)
                val endIndex = source.indexOf(end, startIndex + start.length)
                check(startIndex >= 0 && endIndex > startIndex) {
                    "R2-M2-P4 could not isolate production fence $start..$end."
                }
                return source.substring(startIndex, endIndex)
            }

            @Suppress("UNCHECKED_CAST")
            val fixture = JsonSlurper().parse(r2M2P4CompilerSyntaxFixture.asFile) as
                Map<String, Any?>
            check(
                fixture["schema"] == "autojs6.typescript.r2-m2-p4.compiler-syntax" &&
                    (fixture["revision"] as? Number)?.toInt() == 1 &&
                    fixture["typescriptVersion"] == typeScriptVersion &&
                    fixture["scope"] == "desktop-compiler-only/no-production-admission",
            ) {
                "R2-M2-P4 compiler-syntax fixture identity drifted."
            }

            val execution = fixture["execution"] as? Map<*, *>
                ?: error("R2-M2-P4 fixture has no execution authority.")
            check(
                execution["inputOrders"] == listOf("declared", "reversed") &&
                    (execution["repetitionsPerOrder"] as? Number)?.toInt() == 2 &&
                    listOf(
                        "expectedHostFsCalls",
                        "expectedTsSysCalls",
                        "expectedNetworkCalls",
                        "expectedEmittedJavaScriptExecutions",
                    ).all { key -> (execution[key] as? Number)?.toInt() == 0 } &&
                    execution["sourceCollectionCanonical"] ==
                    "sorted files; UTF-8(path + NUL + decimal byteLength + NUL + " +
                    "lowercase sha256 + LF)" &&
                    execution["outputCollectionCanonical"] ==
                    "sorted outputs; UTF-8(path + NUL + decimal byteLength + NUL + " +
                    "lowercase sha256 + LF)",
            ) {
                "R2-M2-P4 execution/determinism authority drifted."
            }

            val authorities = fixture["authorities"] as? Map<*, *>
                ?: error("R2-M2-P4 fixture has no profile authorities.")
            check(authorities.keys.toList() == listOf("rhino", "node")) {
                "R2-M2-P4 must use only the frozen Rhino and Node revision-1 authorities."
            }
            authorities.forEach { (name, rawAuthority) ->
                val authority = rawAuthority as? Map<*, *>
                    ?: error("R2-M2-P4 authority $name is malformed.")
                val options = authority["canonicalOptionsJson"] as? String
                    ?: error("R2-M2-P4 authority $name has no canonical options.")
                check(
                    authority["kind"] == "frozen-profile" &&
                        authority["compilerApi"] == "createIncrementalProgram" &&
                        (authority["profileRevision"] as? Number)?.toInt() == 1 &&
                        authority["productionAdmission"] == "unchanged" &&
                        authority["decoratorSemantics"] ==
                        "standard-stage-3/experimentalDecorators-absent" &&
                        "experimentalDecorators" !in options &&
                        "emitDecoratorMetadata" !in options,
                ) {
                    "R2-M2-P4 authority $name escaped the frozen standard-decorator profile."
                }
            }

            val expectedSyntax = listOf(
                "enum",
                "namespace",
                "class-fields",
                "generics",
                "optional-chaining",
                "async",
                "decorators",
            )
            val syntaxMatrix = fixture["syntaxMatrix"] as? List<*>
                ?: error("R2-M2-P4 fixture has no syntax matrix.")
            check(
                syntaxMatrix.map { rawEntry ->
                    val entry = rawEntry as? Map<*, *>
                        ?: error("R2-M2-P4 syntax entry is malformed.")
                    check(entry["profiles"] == listOf("rhino", "node")) {
                        "R2-M2-P4 syntax profile matrix drifted."
                    }
                    entry["syntax"] as? String
                        ?: error("R2-M2-P4 syntax entry has no name.")
                } == expectedSyntax,
            ) {
                "R2-M2-P4 must freeze exactly seven syntax kinds across two profiles."
            }

            val expectedCaseIds = expectedSyntax.flatMap { syntax ->
                listOf("rhino-$syntax", "node-$syntax")
            }
            val cases = fixture["cases"] as? List<*>
                ?: error("R2-M2-P4 fixture has no case inventory.")
            check(cases.size == 14) {
                "R2-M2-P4 must freeze exactly 14 syntax/profile cases."
            }
            val actualCaseIds = cases.map { rawCase ->
                val fixtureCase = rawCase as? Map<*, *>
                    ?: error("R2-M2-P4 case is malformed.")
                val id = fixtureCase["id"] as? String
                    ?: error("R2-M2-P4 case has no id.")
                val files = fixtureCase["files"] as? List<*>
                    ?: error("R2-M2-P4 case $id has no files.")
                val filePaths = files.map { rawFile ->
                    (rawFile as? Map<*, *>)?.get("path") as? String
                        ?: error("R2-M2-P4 case $id has a malformed file.")
                }
                val rootNames = (fixtureCase["rootNames"] as? List<*>)
                    ?.map { root -> root as? String ?: error("R2-M2-P4 root is malformed.") }
                    ?: error("R2-M2-P4 case $id has no roots.")
                val expected = fixtureCase["expected"] as? Map<*, *>
                    ?: error("R2-M2-P4 case $id is not sealed.")
                val outputs = expected["outputs"] as? List<*>
                    ?: error("R2-M2-P4 case $id has no outputs.")
                val outputPaths = outputs.map { rawOutput ->
                    (rawOutput as? Map<*, *>)?.get("path") as? String
                        ?: error("R2-M2-P4 case $id has a malformed output.")
                }
                val exactOutputPaths = filePaths.flatMap { sourcePath ->
                    val relative = sourcePath.removePrefix("/sources/").removeSuffix(".ts")
                    listOf("/outputs/$relative.js", "/outputs/$relative.js.map")
                }.sorted()
                check(
                    filePaths.size == 2 &&
                        filePaths.distinct().size == 2 &&
                        filePaths.all { path ->
                            path.startsWith("/sources/") &&
                                path.endsWith(".ts") &&
                                !path.endsWith(".d.ts")
                        } &&
                        rootNames.sorted() == filePaths.sorted() &&
                        expected["diagnostics"] == emptyList<Any?>() &&
                        expected["emitSkipped"] == false &&
                        outputs.size == 4 &&
                        outputPaths.count { path -> path.endsWith(".js") } == 2 &&
                        outputPaths.count { path -> path.endsWith(".js.map") } == 2 &&
                        outputPaths.sorted() == exactOutputPaths,
                ) {
                    "R2-M2-P4 case $id escaped the two-root/four-output production shape."
                }
                id
            }
            check(actualCaseIds == expectedCaseIds) {
                "R2-M2-P4 case identity/order drifted."
            }

            val fixtureSource = r2M2P4CompilerSyntaxFixture.asFile.readText(Charsets.UTF_8)
            check(
                "PENDING" !in fixtureSource &&
                    "\"expected\": null" !in fixtureSource &&
                    Regex("\"sourceCollectionSha256\"").findAll(fixtureSource).count() == 14 &&
                    Regex("\"outputCollectionSha256\"").findAll(fixtureSource).count() == 14,
            ) {
                "R2-M2-P4 compiler-syntax fixture is not fully sealed."
            }

            val harnessSource = r2M2P4CompilerSyntaxHarness.asFile.readText(Charsets.UTF_8)
            listOf(
                "ts.createIncrementalProgram({",
                "syntax=7, profiles=2, cases=14, compiles=56",
                "ts.sys/host-fs/network/output execution=0",
                "installedFences.length",
                "sourceCollectionSha256(testCase)",
                "testCase.expected.outputs.length === 4",
            ).forEach { marker ->
                check(marker in harnessSource) {
                    "R2-M2-P4 direct oracle marker is missing: $marker"
                }
            }
            check(
                "R2_M2_P4_RECORD_GOLDEN" !in harnessSource &&
                    "recordMode" !in harnessSource,
            ) {
                "R2-M2-P4 direct oracle retained a golden-recording bypass."
            }
            mapOf(
                "eval(...)" to Regex("""(?:^|[^\w$])eval\s*\("""),
                "Function(...)" to Regex("""(?:^|[^\w$])(?:new\s+)?Function\s*\("""),
                "node:vm require" to Regex("""require\s*\(\s*["']node:vm["']\s*\)"""),
                "vm context execution" to Regex("""(?:^|[^\w$])runIn(?:This|New)Context\s*\("""),
            ).forEach { (executionMarker, pattern) ->
                check(!pattern.containsMatchIn(harnessSource)) {
                    "R2-M2-P4 direct oracle must not execute emitted JavaScript: " +
                        executionMarker
                }
            }

            val fixtureTestSource =
                r2M2P4CompilerSyntaxTestSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "fixtureFreezesSevenSyntaxKindsAcrossTwoProfilesAndRebuildsSourceAndOutputIdentities",
                "standardDecoratorsKeepExperimentalOptionsClosedAndNodeRevisionTwoWorkerResultOnly",
                "R2ProjectSourceKindAdmission.isExecutableSourcePath",
                "CONFIG_DIAGNOSTIC_CODE_IGNORED_COMPILER_OPTION",
                "requireWorkerResultAdmittedExactContractValue",
            ).forEach { marker ->
                check(marker in fixtureTestSource) {
                    "R2-M2-P4 JVM fixture-contract marker is missing: $marker"
                }
            }

            val profileSource = r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            val rhinoFixedOptions = between(
                profileSource,
                "PROVISIONAL_RHINO_FIXED_OPTIONS_JSON",
                "PROVISIONAL_NODE_NEXT_FIXED_OPTIONS_JSON",
            )
            val nodeFixedOptions = between(
                profileSource,
                "PROVISIONAL_NODE_NEXT_FIXED_OPTIONS_JSON",
                "val PROVISIONAL_RHINO:",
            )
            listOf(
                "Rhino" to rhinoFixedOptions,
                "NodeNext" to nodeFixedOptions,
            ).forEach { (label, fixedOptions) ->
                check(
                    "\\\"target\\\":5" in fixedOptions &&
                        "experimentalDecorators" !in fixedOptions &&
                        "emitDecoratorMetadata" !in fixedOptions,
                ) {
                    "R2-M2-P4 $label production options changed decorator/target authority."
                }
            }

            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            val rhinoWorkerOptions = between(
                workerSource,
                "const createProvisionalRhinoCompilerOptions",
                "const createProvisionalNodeNextCompilerOptions",
            )
            val nodeWorkerOptions = between(
                workerSource,
                "const createProvisionalNodeNextCompilerOptions",
                "const compileProject",
            )
            listOf(
                "Rhino" to rhinoWorkerOptions,
                "NodeNext" to nodeWorkerOptions,
            ).forEach { (label, workerOptions) ->
                check(
                    "target: ts.ScriptTarget.ES2018" in workerOptions &&
                        "experimentalDecorators" !in workerOptions &&
                        "emitDecoratorMetadata" !in workerOptions,
                ) {
                    "R2-M2-P4 $label Worker options changed decorator/target authority."
                }
            }

            val controlledPolicyTest =
                r2ControlledTsConfigPolicyTestSource.asFile.readText(Charsets.UTF_8)
            check(
                "experimentalDecorators" in controlledPolicyTest &&
                    "isKnownTypeScriptOption = true" in controlledPolicyTest,
            ) {
                "R2-M2-P4 lost the controlled-config legacy-decorator rejection regression."
            }

            val sourceKindSource =
                r2ProjectSourceKindAdmissionSource.asFile.readText(Charsets.UTF_8)
            check(
                "path.endsWith(\".ts\") -> true" in sourceKindSource &&
                    "path.endsWith(\".d.ts\")" in sourceKindSource,
            ) {
                "R2-M2-P4 production executable .ts/declaration boundary drifted."
            }

            val endToEndSet = Regex(
                """endToEndAdmittedAuthorities[^=]*= setOf\((.*?)\)""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(profileSource)?.groupValues?.get(1)
                ?: error("R2-M2-P4 could not isolate the end-to-end authority set.")
            val admittedAuthorityNames = Regex("PROVISIONAL_[A-Z0-9_]+")
                .findAll(endToEndSet)
                .map { match -> match.value }
                .toSet()
            check(
                admittedAuthorityNames == setOf(
                    "PROVISIONAL_RHINO",
                    "PROVISIONAL_NODE_NEXT_REVISION_1",
                    "PROVISIONAL_NODE_NEXT_REVISION_2",
                ) &&
                    Regex(
                        """workerResultAdmittedAuthorities[^=]*=\s*""" +
                            """endToEndAdmittedAuthorities""",
                        RegexOption.DOT_MATCHES_ALL,
                    ).containsMatchIn(profileSource),
            ) {
                "R2-M2-P4 must preserve the later S1-5 Node revision-2 admission."
            }

            val metadataSource =
                r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M2-P4")

            val buildScriptSource = r2M2P3BuildScript.asFile.readText(Charsets.UTF_8)
            val publicSourcePolicy = between(
                buildScriptSource,
                "tasks.register(\"verifyR2PublicPreviewSourcePolicy\")",
                "inputs.files(",
            )
            check(
                "complete M3-S2" in publicSourcePolicy &&
                    "complete M3-D3" !in publicSourcePolicy &&
                    "complete M3-D2" !in publicSourcePolicy &&
                    "complete M3-D1" !in publicSourcePolicy &&
                    "complete M2-X3" !in publicSourcePolicy &&
                    "complete M2-X2" !in publicSourcePolicy &&
                    "complete M2-X1" !in publicSourcePolicy &&
                    "complete M2-P4" !in publicSourcePolicy &&
                    "complete M2-P3" !in publicSourcePolicy,
            ) {
                "R2-M2-P4 historical gate did not advance to the current M3-S2 source policy."
            }

            logger.lifecycle(
                "R2-M2-P4 compiler syntax verified: TypeScript 6.0.3, syntax=7, profiles=2, " +
                    "cases=14, compiles=56, two executable roots/four outputs per case, " +
                    "standard decorators, Node revision 2 public end-to-end, " +
                    "compiler capability mask=15, runtime engines=0",
            )
        }
    }

val r2M2P5CompilerBoundariesFixture = layout.projectDirectory.file(
    "src/test/resources/r2-compiler-boundaries/p5-v1.json",
)
val r2M2P5CompilerBoundariesHarness = layout.projectDirectory.file(
    "src/test/scripts/verify-r2-m2-p5-compiler-boundaries.js",
)
val r2M2P5CompilerBoundariesTestSource = layout.projectDirectory.file(
    "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/" +
        "R2CompilerBoundaryMatrixFixtureTest.kt",
)

val verifyR2M2P5CompilerBoundariesDesktop =
    tasks.register<Exec>("verifyR2M2P5CompilerBoundariesDesktop") {
        group = "verification"
        description =
            "Runs the R2-M2-P5 deterministic path, newline, BOM, Unicode, token, and " +
                "canonical-collision compiler-boundary matrix without production admission " +
                "changes or runtime execution."
        dependsOn(verifyTypeScriptDistribution, verifyR2DeclarationDistribution)
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        val typeScriptLibDirectory = generatedTypeScriptDistributionDirectory.map {
            it.dir("lib")
        }
        inputs.files(
            compilerFile,
            r2M2P5CompilerBoundariesFixture,
            r2M2P5CompilerBoundariesHarness,
        )
        inputs.dir(generatedR2DeclarationDistributionDirectory)
        inputs.dir(typeScriptLibDirectory)
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            "--max-old-space-size=512",
            r2M2P5CompilerBoundariesHarness.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            generatedR2DeclarationDistributionDirectory.get().asFile.absolutePath,
            typeScriptLibDirectory.get().asFile.absolutePath,
            r2M2P5CompilerBoundariesFixture.asFile.absolutePath,
            typeScriptVersion,
        )
    }

val verifyR2M2P5CompilerBoundaries =
    tasks.register("verifyR2M2P5CompilerBoundaries") {
        group = "verification"
        description =
            "Closes R2-M2-P5 with deterministic compiler-boundary desktop/JVM evidence " +
                "while keeping production paths canonical UTF-8, collisions fail-closed, " +
                "and runtime execution out of scope."

        dependsOn(verifyR2M2P4CompilerSyntax)
        dependsOn(verifyR2M2P5CompilerBoundariesDesktop)
        dependsOn(verifyR2ProjectCompilerDesktop)
        dependsOn(verifyDebugTypeScriptDistributionApk)
        dependsOn(verifyReleaseTypeScriptDistributionApk)
        dependsOn(verifyDebugR2DeclarationDistributionApk)
        dependsOn(verifyReleaseR2DeclarationDistributionApk)
        dependsOn("verifyDebugCompilerHostApk")
        dependsOn("verifyReleaseCompilerHostApk")
        dependsOn("verifyR2ReleaseCompatibilityApks")
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyR2PublicPreviewSourcePolicy")
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        dependsOn("testDebugUnitTest", "compileDebugAndroidTestKotlin")

        inputs.files(
            r2M2P5CompilerBoundariesFixture,
            r2M2P5CompilerBoundariesHarness,
            r2M2P5CompilerBoundariesTestSource,
            r2ProjectCompilerDesktopHarnessSource,
            r2M2P3BuildScript,
            r2CompilationProfileAuthoritySource,
            r2ProjectSourceKindAdmissionSource,
            r2ProjectSnapshotSource,
            r2ProjectArchiveSource,
            r2FileBackedProjectSnapshotSource,
            r2ZipArchiveValidatorSource,
            r2M2P2GeneratedModuleMappingSource,
            r2CompilerWorkerSource,
            r2CompilerHostProtocolSource,
            r2PrivateOutputArchiveSource,
            r2PublicPreviewMetadataSource,
            r2PublicPreviewServiceSource,
            r2ProjectSnapshotTestSource,
            r2ProjectArchiveTestSource,
            r2ProjectArchiveLoaderTestSource,
            layout.projectDirectory.file(
                "src/test/java/io/github/supermonster003/autojs6/plugin/typescript/archive/" +
                    "ZipArchiveV1ValidatorTest.kt",
            ),
        )

        doLast {
            fun requireMarkers(label: String, source: String, markers: List<String>) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-M2-P5 $label marker is missing: $marker"
                    }
                }
            }

            fun between(source: String, start: String, end: String): String {
                val startIndex = source.indexOf(start)
                val endIndex = source.indexOf(end, startIndex + start.length)
                check(startIndex >= 0 && endIndex > startIndex) {
                    "R2-M2-P5 could not isolate production fence $start..$end."
                }
                return source.substring(startIndex, endIndex)
            }

            @Suppress("UNCHECKED_CAST")
            val fixture = JsonSlurper().parse(r2M2P5CompilerBoundariesFixture.asFile) as
                Map<String, Any?>
            check(
                fixture.keys.toList() == listOf(
                    "schema",
                    "revision",
                    "typescriptVersion",
                    "scope",
                    "execution",
                    "authorities",
                    "generatorAuthority",
                    "boundaryMatrix",
                    "cases",
                ) &&
                    fixture["schema"] == "autojs6.typescript.r2-m2-p5.compiler-boundaries" &&
                    (fixture["revision"] as? Number)?.toInt() == 1 &&
                    fixture["typescriptVersion"] == typeScriptVersion &&
                    fixture["scope"] == "desktop-compiler-only/no-production-admission",
            ) {
                "R2-M2-P5 compiler-boundary fixture identity drifted."
            }

            val generator = fixture["generatorAuthority"] as? Map<*, *>
                ?: error("R2-M2-P5 fixture has no generator authority.")
            val profileAggregateTokenCounts =
                generator["profileAggregateTokenCounts"] as? Map<*, *>
                    ?: error("R2-M2-P5 generator has no profile aggregate token counts.")
            val overflowExpected = generator["overflowExpected"] as? Map<*, *>
                ?: error("R2-M2-P5 generator has no overflow rejection contract.")
            val renderedGeneratorBytes =
                (generator["line"] as? String)
                    ?.repeat((generator["repetitions"] as? Number)?.toInt() ?: -1)
                    ?.toByteArray(Charsets.UTF_8)
                    ?: error("R2-M2-P5 generator cannot be rendered.")
            val renderedGeneratorSha256 = MessageDigest.getInstance("SHA-256")
                .digest(renderedGeneratorBytes)
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
            check(
                generator.keys.toList() == listOf(
                    "kind",
                    "line",
                    "repetitions",
                    "renderedUtf8ByteLength",
                    "renderedSha256",
                    "tokensPerRepetition",
                    "acceptedTokenCount",
                    "overflowSuffix",
                    "overflowTokenCount",
                    "maximumProjectTokens",
                    "profileAggregateTokenCounts",
                    "overflowExpected",
                ) &&
                    generator["kind"] == "repeated-interface-v1" &&
                    generator["line"] == "interface Boundary{}\r\n" &&
                    (generator["repetitions"] as? Number)?.toInt() == 16_384 &&
                    (generator["renderedUtf8ByteLength"] as? Number)?.toInt() == 360_448 &&
                    renderedGeneratorBytes.size == 360_448 &&
                    generator["renderedSha256"] ==
                        "f55b81564428621c455a2860f5ab99a30827ace8089e5a3118b1af35ecbae26b" &&
                    renderedGeneratorSha256 == generator["renderedSha256"] &&
                    (generator["tokensPerRepetition"] as? Number)?.toInt() == 4 &&
                    (generator["acceptedTokenCount"] as? Number)?.toInt() == 65_536 &&
                    generator["overflowSuffix"] == ";\r\n" &&
                    (generator["overflowTokenCount"] as? Number)?.toInt() == 65_537 &&
                    (generator["maximumProjectTokens"] as? Number)?.toInt() == 262_144 &&
                    profileAggregateTokenCounts.keys.toList() == listOf("rhino", "node") &&
                    (profileAggregateTokenCounts["rhino"] as? Number)?.toInt() == 152_036 &&
                    (profileAggregateTokenCounts["node"] as? Number)?.toInt() == 191_040 &&
                    overflowExpected.keys.toList() == listOf(
                        "outcome",
                        "stage",
                        "code",
                        "programCreated",
                        "diagnostics",
                        "outputs",
                        "compilerApiCalls",
                        "outputWrites",
                    ) &&
                    overflowExpected["outcome"] == "rejected" &&
                    overflowExpected["stage"] == "pre-program" &&
                    overflowExpected["code"] == "ORACLE_FILE_TOKEN_LIMIT_EXCEEDED" &&
                    overflowExpected["programCreated"] == false &&
                    overflowExpected["diagnostics"] == emptyList<Any?>() &&
                    overflowExpected["outputs"] == emptyList<Any?>() &&
                    (overflowExpected["compilerApiCalls"] as? Number)?.toInt() == 0 &&
                    (overflowExpected["outputWrites"] as? Number)?.toInt() == 0,
            ) {
                "R2-M2-P5 generator/token/rejection authority drifted."
            }

            val expectedCaseIds = listOf(
                "rhino-chinese-path",
                "node-chinese-path",
                "rhino-space-path",
                "node-space-path",
                "rhino-crlf",
                "node-crlf",
                "rhino-bom",
                "node-bom",
                "rhino-unicode",
                "node-unicode",
                "rhino-high-token",
                "node-high-token",
                "rhino-canonical-collision",
                "node-canonical-collision",
            )
            val cases = fixture["cases"] as? List<*>
                ?: error("R2-M2-P5 fixture has no case inventory.")
            check(cases.size == 14) {
                "R2-M2-P5 fixture must freeze exactly 14 boundary/profile rows."
            }
            val caseIds = cases.map { rawCase ->
                val fixtureCase = rawCase as? Map<*, *>
                    ?: error("R2-M2-P5 fixture case is malformed.")
                fixtureCase["id"] as? String
                    ?: error("R2-M2-P5 fixture case has no ID.")
            }
            check(caseIds == expectedCaseIds) {
                "R2-M2-P5 fixture row identity/order drifted."
            }
            val positiveCases = cases.filter { rawCase ->
                val fixtureCase = rawCase as Map<*, *>
                val expected = fixtureCase["expected"] as? Map<*, *>
                    ?: error("R2-M2-P5 fixture case has no expected outcome.")
                expected["outcome"] == "compiled"
            }
            val collisionCases = cases.filter { rawCase ->
                val fixtureCase = rawCase as Map<*, *>
                val expected = fixtureCase["expected"] as Map<*, *>
                expected["outcome"] == "rejected"
            }
            check(
                positiveCases.size == 12 &&
                    positiveCases.all { rawCase ->
                        val fixtureCase = rawCase as Map<*, *>
                        val files = fixtureCase["files"] as? List<*>
                        val roots = fixtureCase["rootNames"] as? List<*>
                        val expected = fixtureCase["expected"] as Map<*, *>
                        val outputs = expected["outputs"] as? List<*>
                        files?.size == 2 &&
                            roots?.size == 2 &&
                            outputs?.size == 4 &&
                            expected["diagnostics"] == emptyList<Any?>() &&
                            expected["emitSkipped"] == false
                    } &&
                    collisionCases.size == 2 &&
                    collisionCases.all { rawCase ->
                        val expected = (rawCase as Map<*, *>)["expected"] as Map<*, *>
                        expected.keys.toList() == listOf(
                            "outcome",
                            "stage",
                            "code",
                            "programCreated",
                            "diagnostics",
                            "outputs",
                            "compilerApiCalls",
                            "outputWrites",
                        ) &&
                            expected["outcome"] == "rejected" &&
                            expected["stage"] == "pre-program" &&
                            expected["code"] == "ORACLE_ASCII_CASE_FOLD_COLLISION" &&
                            expected["programCreated"] == false &&
                            expected["diagnostics"] == emptyList<Any?>() &&
                            expected["outputs"] == emptyList<Any?>() &&
                            (expected["compilerApiCalls"] as? Number)?.toInt() == 0 &&
                            (expected["outputWrites"] as? Number)?.toInt() == 0
                    } &&
                    positiveCases.sumOf { rawCase ->
                        val expected = (rawCase as Map<*, *>)["expected"] as Map<*, *>
                        (expected["outputs"] as List<*>).size
                    } == 48,
            ) {
                "R2-M2-P5 positive/collision exact outcome shape drifted."
            }

            val fixtureSource =
                r2M2P5CompilerBoundariesFixture.asFile.readText(Charsets.UTF_8)
            check(
                "PENDING" !in fixtureSource &&
                    "\"expected\": null" !in fixtureSource &&
                    Regex("\"sourceCollectionSha256\"").findAll(fixtureSource).count() == 14 &&
                    Regex("\"outputCollectionSha256\"").findAll(fixtureSource).count() == 12,
            ) {
                "R2-M2-P5 fixture is not fully sealed."
            }

            val harnessSource =
                r2M2P5CompilerBoundariesHarness.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "direct oracle",
                source = harnessSource,
                markers = listOf(
                    "ts.createIncrementalProgram({",
                    "assertValidScalarTree",
                    "repeated-interface-v1",
                    "renderedUtf8ByteLength",
                    "profileAggregateTokenCounts",
                    "overflowExpected",
                    "requireFileTokenLimit",
                    "verifyProjectTokenAggregate",
                    "ORACLE_ASCII_CASE_FOLD_COLLISION",
                    "ORACLE_FILE_TOKEN_LIMIT_EXCEEDED",
                    "kinds=7, profiles=2, rows=14, positive=12, compiles=48",
                    "collision-rejections=8, token-overflow-rejections=8",
                    "ts.sys/host-fs/network/output execution=0",
                ),
            )
            check(
                "R2_M2_P5_RECORD_GOLDEN" !in harnessSource &&
                    "recordMode" !in harnessSource,
            ) {
                "R2-M2-P5 direct oracle retained a golden-recording bypass."
            }
            mapOf(
                "eval(...)" to Regex("""(?:^|[^\w$])eval\s*\("""),
                "Function(...)" to Regex("""(?:^|[^\w$])(?:new\s+)?Function\s*\("""),
                "node:vm require" to Regex("""require\s*\(\s*["']node:vm["']\s*\)"""),
                "vm context execution" to
                    Regex("""(?:^|[^\w$])runIn(?:This|New)Context\s*\("""),
            ).forEach { (executionMarker, pattern) ->
                check(!pattern.containsMatchIn(harnessSource)) {
                    "R2-M2-P5 direct oracle must not execute emitted JavaScript: " +
                        executionMarker
                }
            }

            val fixtureTestSource =
                r2M2P5CompilerBoundariesTestSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "JVM fixture contract",
                source = fixtureTestSource,
                markers = listOf(
                    "fixtureFreezesSevenBoundaryKindsAcrossTwoProfilesAndRebuildsDeterministicIdentities",
                    "productionPathPolicyAcceptsUtf8AndSpaceWhileKeepingCollisionsFailClosed",
                    "R2ProjectSnapshot.validateProjectPath",
                    "acceptedTokenCount",
                    "overflowTokenCount",
                    "renderedUtf8ByteLength",
                    "renderedSha256",
                    "maximumProjectTokens",
                    "profileAggregateTokenCounts",
                    "overflowExpected",
                    "buildCollectionCanonical",
                    "EXPECTED_SOURCE_IDENTITIES",
                    "EXPECTED_OUTPUT_IDENTITIES",
                    "REJECTION_EXPECTED_KEYS",
                    "requireWorkerResultAdmittedExactContractValue",
                ),
            )

            val zipSource = r2ZipArchiveValidatorSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "ZIP canonical UTF-8 authority",
                source = zipSource,
                markers = listOf(
                    "ZIP entry path contains non-ASCII bytes without the UTF-8 flag.",
                    "R2Utf8.decode(rawName, \"ZIP entry path\")",
                    "ZIP entry path contains a control character:",
                    "asciiLowercase(path)",
                ),
            )
            val archiveSource = r2ProjectArchiveSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "manifest canonical UTF-8 authority",
                source = archiveSource,
                markers = listOf(
                    "validateCanonicalArchivePath(path, label)",
                    "R2UnicodePathPolicy.isCanonicalRelativePath(path)",
                    "val collisionKey = R2UnicodePathPolicy.asciiCaseFold(path)",
                ),
            )
            val snapshotSource = r2ProjectSnapshotSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "snapshot path/UTF-8 authority",
                source = snapshotSource,
                markers = listOf(
                    "Project VFS path must be a bounded canonical UTF-8 absolute path",
                    "val collisionKey = R2UnicodePathPolicy.asciiCaseFold(file.path)",
                    ".onMalformedInput(CodingErrorAction.REPORT)",
                    ".onUnmappableCharacter(CodingErrorAction.REPORT)",
                ),
            )
            val mappingSource =
                r2M2P2GeneratedModuleMappingSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "generated mapping path authority",
                source = mappingSource,
                markers = listOf(
                    "R2UnicodePathPolicy.isCanonicalAbsolutePath(",
                    "Generated mapping source path is not a canonical UTF-8 /sources path.",
                    "canonicalEvidence.toByteArray(StandardCharsets.US_ASCII)",
                    "mapping.sourcePath.asciiCaseFold()",
                ),
            )

            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "Worker path/UTF-8/token authority",
                source = workerSource,
                markers = listOf(
                    "const maximumTokensPerFile = 65_536;",
                    "const maximumProjectTokens = 262_144;",
                    "const validateCanonicalVfsPath = (",
                    "!isSafeUnicodePathText(path)",
                    "Project VFS path is not a canonical absolute path.",
                    "new TextDecoder(\"utf-8\", {",
                    "fatal: true,",
                    "ignoreBOM: true,",
                    "const countSourceTokens = (text) => {",
                    "Project snapshot contains a canonical path collision.",
                    "Project source file exceeds the token limit.",
                    "Project snapshot exceeds the token limit.",
                    "newLine: ts.NewLineKind.LineFeed",
                ),
            )

            val protocolSource = r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8)
            val protocolPathFence = between(
                protocolSource,
                "private fun String.isCanonicalProtocolPath(): Boolean =",
                "private fun ByteArray.sha256(): String",
            )
            requireMarkers(
                label = "result protocol path authority",
                source = protocolPathFence,
                markers = listOf(
                    "R2UnicodePathPolicy.isCanonicalAbsolutePath(this)",
                ),
            )
            val privateOutputSource =
                r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "private output path authority",
                source = privateOutputSource,
                markers = listOf(
                    "R2Utf8.encode(entry.path, \"Output ZIP entry path\")",
                    "FLAG_UTF8",
                    "R2UnicodePathPolicy.isCanonicalRelativePath(this)",
                    "Compiler Source Map contains a non-canonical source.",
                ),
            )

            val legacyDesktopSource =
                r2ProjectCompilerDesktopHarnessSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "existing Worker token/UTF-8 regression",
                source = legacyDesktopSource,
                markers = listOf(
                    "verifyWorkerRejectsInvalidUtf8",
                    "verifyP3bFatalDecodeAcrossFrameBoundary",
                    "verifyP3bScannerLimits",
                    "p3bWorkerLimits.maximumTokensPerFile + 1",
                    "desktop-p3b-project-token-overflow",
                    "desktop-p3b-lazy-source-file",
                ),
            )

            val archiveTestSource = r2ProjectArchiveTestSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "manifest path authority regression",
                source = archiveTestSource,
                markers = listOf(
                    "manifestAuthorityAcceptsSafeUtf8AndSpacePathsWhileCaseCollisionsStayClosed",
                    "sources/中文目录/入口.ts",
                    "ASCII case-fold collision",
                ),
            )

            val profileSource = r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "frozen LF/profile authority",
                source = profileSource,
                markers = listOf(
                    "\\\"newLine\\\":1",
                    "append(\"|newLine=LF\")",
                    "requireWorkerResultAdmittedExactContractValue",
                ),
            )
            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M2-P5")

            val buildScriptSource = r2M2P3BuildScript.asFile.readText(Charsets.UTF_8)
            val publicSourcePolicy = between(
                buildScriptSource,
                "tasks.register(\"verifyR2PublicPreviewSourcePolicy\")",
                "inputs.files(",
            )
            check(
                "complete M3-S2" in publicSourcePolicy &&
                    "complete M3-D3" !in publicSourcePolicy &&
                    "complete M3-D2" !in publicSourcePolicy &&
                    "complete M3-D1" !in publicSourcePolicy &&
                    "complete M2-X3" !in publicSourcePolicy &&
                    "complete M2-X2" !in publicSourcePolicy &&
                    "complete M2-X1" !in publicSourcePolicy &&
                    "complete M2-P4" !in publicSourcePolicy,
            ) {
                "R2-M2-P5 historical gate did not advance to the current M3-S2 source policy."
            }

            logger.lifecycle(
                "R2-M2-P5 compiler boundaries verified: TypeScript 6.0.3, kinds=7, " +
                    "profiles=2, rows=14, positive=12, compiles=48, safe production Unicode " +
                    "and space paths accepted, controls rejected, 65,536-token inclusive, " +
                    "ASCII collision pre-Program, compiler capability mask=15",
            )
        }
    }

val verifyR2M2X1PublicShapedDeterministicBundle =
    tasks.register("verifyR2M2X1PublicShapedDeterministicBundle") {
        group = "verification"
        description =
            "Verifies the source/JVM/APK side of R2-M2-X1 for deterministic Rhino " +
                "revision-1 and NodeNext revision-1 " +
                "bundles over the private-admission public-shaped Binder service surface " +
                "with compiler capability mask 15; roadmap closure still " +
                "requires the focused Android runtime receipt."

        dependsOn(verifyR2M2P5CompilerBoundaries)
        dependsOn("testDebugUnitTest", "assembleDebugAndroidTest")

        inputs.files(
            r2M2P3BuildScript,
            r2BinderCompilationInstrumentationSource,
            r2CompilationProfileAuthoritySource,
            r2CompilationProfileAuthorityTestSource,
            r2CompilationProbeSource,
            r2CompilationProbeProtocolTestSource,
            r2PublicPreviewServiceSource,
            r2CompilationCoordinatorSource,
            r2PrivateOutputArchiveSource,
            r2PrivateOutputArchiveTestSource,
            r2PublicPreviewMetadataSource,
        )

        doLast {
            fun requireMarkers(label: String, source: String, markers: List<String>) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-M2-X1 $label marker is missing: $marker"
                    }
                }
            }

            fun between(source: String, start: String, end: String): String {
                val startIndex = source.indexOf(start)
                val endIndex = source.indexOf(end, startIndex + start.length)
                check(startIndex >= 0 && endIndex > startIndex) {
                    "R2-M2-X1 could not isolate $start..$end."
                }
                return source.substring(startIndex, endIndex)
            }

            val profileSource =
                r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            val endToEndAuthorities = between(
                profileSource,
                "private val endToEndAdmittedAuthorities",
                "private val workerResultAdmittedAuthorities",
            )
            check(
                "PROVISIONAL_RHINO" in endToEndAuthorities &&
                    "PROVISIONAL_NODE_NEXT_REVISION_1" in endToEndAuthorities &&
                    "PROVISIONAL_NODE_NEXT_REVISION_2" in endToEndAuthorities
            ) {
                "R2-M2-X1 must preserve exact S1-5 end-to-end profile admission."
            }
            val workerResultAuthorities = between(
                profileSource,
                "private val workerResultAdmittedAuthorities",
                "fun require(profile:",
            )
            check(
                "endToEndAdmittedAuthorities" in
                    workerResultAuthorities
            ) {
                "R2-M2-X1 must preserve the unified end-to-end/Worker-result admission."
            }

            val probeSource = r2CompilationProbeSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "private service authority",
                source = probeSource,
                markers = listOf(
                    "R2CompilationControlOrigin.DEBUG_PROBE",
                    "R2CompilationProfileRegistry.requireEndToEndAdmitted(profileAuthority)",
                    "profileAuthority == R2CompilationProfileRegistry.PROVISIONAL_RHINO ||",
                    "R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 ||",
                    "origin == R2CompilationControlOrigin.DEBUG_PROBE",
                    "Public compilation requires the exact Rhino or NodeNext revision-2 authority.",
                ),
            )
            val serviceSource = r2PublicPreviewServiceSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "public-shaped Binder service",
                source = serviceSource,
                markers = listOf(
                    "override fun startCompilation(",
                    "request.r2DebugCompilationControlsOrNull(",
                    "callingUid = callingUid,",
                    "R2CompilationProfileRegistry.requireExactRuntimeAuthority(",
                    "R2CompilationProfileRegistry.requireEndToEndAdmitted(profileAuthority)",
                    "coordinator.admit(",
                    "TypeScriptR2Contract.CANCELLATION_OPERATION_ACKNOWLEDGE",
                ),
            )
            val coordinatorSource =
                r2CompilationCoordinatorSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "coordinator/output commit",
                source = coordinatorSource,
                markers = listOf(
                    "R2ProjectArchiveLoader.load(",
                    "compilerHost.compileProjectUntilElapsedRealtimeDeadline(",
                    "R2PrivateOutputArchive.stage(",
                    "R2OutputFdCommitter.commit(",
                    "publishCommitted(job, action.summary)",
                ),
            )

            val instrumentationSource =
                r2BinderCompilationInstrumentationSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "Android service evidence",
                source = instrumentationSource,
                markers = listOf(
                    "rhinoAndNodeRevisionOneProduceDeterministicBundlesThroughPublicShapedService",
                    "R2CompilationProfileRegistry.PROVISIONAL_RHINO",
                    "R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1",
                    "request = fixture.x1PublicShapedRequest()",
                    "publicShapedAcknowledgement = true",
                    "publicAcknowledgement(fixture.requestId, token)",
                    "R2OutputArchiveSchemaV1.artifactInventoryFingerprint(outputFiles)",
                    "R2OutputArchiveSchemaV1.artifactInventoryFingerprint(normalizedOutputFiles)",
                    "R2OutputArchiveSchemaV1.moduleInventoryFingerprint(outputFiles)",
                    "assertStableServiceBundlesEqual(",
                    "publicNodeRevisionTwoEmitsMjsAndCjsWithPairedSourceMaps",
                    "The external request must select an exact public profile authority.",
                    "remove(R2CompilationProbeProtocol.KEY_EXPANDED_WORKER_CAPACITY)",
                    "TypeScriptPluginCapabilityKeys.NODE_RUNTIME_REQUIRED",
                ),
            )
            val privateOutputTestSource =
                r2PrivateOutputArchiveTestSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "JVM deterministic private output",
                source = privateOutputTestSource,
                markers = listOf(
                    "emittedArchiveIsDeterministicStrictAndBoundToItsInputAuthority",
                    "nodeRevisionOnePrivateBundleIsByteDeterministicAndKeepsExactProfile",
                    "firstBytes.contentEquals(secondBytes)",
                ),
            )
            val probeTestSource =
                r2CompilationProbeProtocolTestSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "promotion fences",
                source = probeTestSource,
                markers = listOf(
                    "debugNodeProfileBindsItsOwnExactRuntimeAuthority",
                    "publicPreviewRejectsLegacyNodeRevisionOne",
                    "publicNodeRevisionTwoCanEnterTheEndToEndPipeline",
                ),
            )

            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M2-X1")

            val buildScriptSource = r2M2P3BuildScript.asFile.readText(Charsets.UTF_8)
            val publicSourcePolicy = between(
                buildScriptSource,
                "tasks.register(\"verifyR2PublicPreviewSourcePolicy\")",
                "inputs.files(",
            )
            check(
                "complete M3-S2" in publicSourcePolicy &&
                    "complete M3-D3" !in publicSourcePolicy &&
                    "complete M3-D2" !in publicSourcePolicy &&
                    "complete M3-D1" !in publicSourcePolicy &&
                    "complete M2-X3" !in publicSourcePolicy &&
                    "complete M2-X2" !in publicSourcePolicy &&
                    "complete M2-X1" !in publicSourcePolicy &&
                    "complete M2-P5" !in publicSourcePolicy
            ) {
                "R2-M2-X1 historical gate did not advance to the current M3-S2 source policy."
            }

            logger.lifecycle(
                "R2-M2-X1 pre-device source/JVM/APK evidence verified: private same-UID " +
                    "admission over frozen Binder/PFD/ACK/callback service surface, Rhino r1 + " +
                    "NodeNext r1, two roots/two repeats, Node r2 public end-to-end, " +
                    "compiler capability mask=15",
            )
        }
    }

val verifyR2M2X2DiagnosticStopEmitModel =
    tasks.register("verifyR2M2X2DiagnosticStopEmitModel") {
        group = "verification"
        description =
            "Verifies the source/JVM/APK side of R2-M2-X2: one unified pre-emit " +
                "diagnostic model stops output for options, syntactic, global, and semantic " +
                "errors; the Android receipt remains source-reachable syntactic/semantic " +
                "over private same-UID public-shaped Binder admission, and roadmap closure " +
                "still requires that focused Android runtime receipt."

        dependsOn(verifyR2M2X1PublicShapedDeterministicBundle)
        dependsOn(verifyR2ProjectCompilerDesktop)
        dependsOn("testDebugUnitTest", "assembleDebugAndroidTest")

        inputs.files(
            r2M2P3BuildScript,
            r2CompilerWorkerSource,
            r2ProjectCompilerDesktopHarnessSource,
            r2DiagnosticStopEmitFixture,
            r2DiagnosticStopEmitFixtureTestSource,
            r2ProjectCompilationSource,
            r2ProjectCompilationTestSource,
            r2CompilerHostProtocolSource,
            r2ProjectFramedTransportTestSource,
            r2PrivateOutputArchiveSource,
            r2PrivateOutputArchiveTestSource,
            r2BinderCompilationInstrumentationSource,
            r2CompilationProfileAuthoritySource,
            r2CompilationProbeSource,
            r2CompilationProbeProtocolTestSource,
            r2PublicPreviewMetadataSource,
        )

        doLast {
            fun requireMarkers(label: String, source: String, markers: List<String>) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-M2-X2 $label marker is missing: $marker"
                    }
                }
            }

            fun between(source: String, start: String, end: String): String {
                val startIndex = source.indexOf(start)
                val endIndex = source.indexOf(end, startIndex + start.length)
                check(startIndex >= 0 && endIndex > startIndex) {
                    "R2-M2-X2 could not isolate $start..$end."
                }
                return source.substring(startIndex, endIndex)
            }

            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "shipping Worker diagnostic pipeline",
                source = workerSource,
                markers = listOf(
                    "diagnosticCollector.consume(\"options\", program.getOptionsDiagnostics())",
                    "program.getSyntacticDiagnostics(sourceFile)",
                    "diagnosticCollector.consume(\"global\", program.getGlobalDiagnostics())",
                    "program.getSemanticDiagnostics(sourceFile)",
                    "if (diagnosticCollector.errorCount() > 0)",
                    "emitSkipped: true",
                    "if ((errorCount > 0 || emitResult.emitSkipped) && outputs.size !== 0)",
                    "throw new Error(\"noEmitOnError produced executable output.\")",
                ),
            )

            val desktopHarnessSource =
                r2ProjectCompilerDesktopHarnessSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "desktop-only four-phase defensive seam",
                source = desktopHarnessSource,
                markers = listOf(
                    "createX2ActualOptionsDiagnostic",
                    "createX2ActualGlobalDiagnostic",
                    "withX2CompilerDiagnosticProbe",
                    "verifyX2StoppedEmit",
                    "verifyX2CleanSuccessor",
                    "verifyX2UnifiedNoEmitDiagnostics",
                    "builder.emit() call count drifted",
                    "emitSkipped === true",
                    "outputs.length === 0",
                    "phase-less diagnostic identity did not keep first-phase precedence",
                    "Warning diagnostic blocked builder.emit() or discarded outputs",
                    "r2-compiler-diagnostics",
                ),
            )
            listOf(
                "createX2ActualOptionsDiagnostic",
                "createX2ActualGlobalDiagnostic",
                "withX2CompilerDiagnosticProbe",
                "autojs6.typescript.r2-m2-x2.unified-no-emit-diagnostics",
            ).forEach { desktopOnlyMarker ->
                check(desktopOnlyMarker !in workerSource) {
                    "R2-M2-X2 desktop options/global marker escaped into the shipping Worker: " +
                        desktopOnlyMarker
                }
            }

            val fixtureSource = r2DiagnosticStopEmitFixture.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "canonical four-phase fixture",
                source = fixtureSource,
                markers = listOf(
                    "\"options\"",
                    "\"syntactic\"",
                    "\"global\"",
                    "\"semantic\"",
                    "\"emitSkipped\": true",
                    "\"outputs\": []",
                ),
            )
            val fixtureTestSource =
                r2DiagnosticStopEmitFixtureTestSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "fixture JVM contract",
                source = fixtureTestSource,
                markers = listOf(
                    "class R2DiagnosticStopEmitFixtureTest",
                    "fixtureFreezesFourErrorPhasesAndOneNineFieldDiagnosticModel",
                    "fixtureFreezesFirstPhaseDeduplicationAndWarningPassThrough",
                ),
            )

            val compilationSource = r2ProjectCompilationSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "native unified terminal invariant",
                source = compilationSource,
                markers = listOf(
                    "OPTIONS(\"options\")",
                    "SYNTACTIC(\"syntactic\")",
                    "GLOBAL(\"global\")",
                    "SEMANTIC(\"semantic\")",
                    "val hasBlockingDiagnostics",
                    "Blocking TypeScript diagnostics must skip emit without output.",
                    "An error-free TypeScript result must not skip emit.",
                ),
            )
            val compilationTestSource =
                r2ProjectCompilationTestSource.asFile.readText(Charsets.UTF_8)
            val framedTestSource =
                r2ProjectFramedTransportTestSource.asFile.readText(Charsets.UTF_8)
            val privateOutputTestSource =
                r2PrivateOutputArchiveTestSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "native/JVM four-phase preservation",
                source = compilationTestSource + "\n" + framedTestSource + "\n" +
                    privateOutputTestSource,
                markers = listOf(
                    "diagnosticModelFreezesTheUnifiedNineFieldShape",
                    "unifiedPreEmitBlockingDiagnosticsFreezeNoEmitAndNonErrorsDoNotBlock",
                    "framedResultParserPreservesExactUnifiedPreEmitDiagnosticPhases",
                    "unifiedPreEmitPhasesProduceDeterministicDiagnosticsOnlyArchives",
                    "NORMAL_DIAGNOSTIC_KEYS",
                ),
            )

            val instrumentationSource =
                r2BinderCompilationInstrumentationSource.asFile.readText(Charsets.UTF_8)
            val instrumentationMethod = between(
                instrumentationSource,
                "fun rhinoAndNodeRevisionOneStopEmitForSourceDiagnosticsThroughPublicShapedService",
                "fun storedContainerCapacityAcceptsExact64MiBAndRejectsPlusOne",
            )
            requireMarkers(
                label = "Android public-shaped diagnostics-only matrix",
                source = instrumentationMethod,
                markers = listOf(
                    "R2CompilationProfileRegistry.PROVISIONAL_RHINO",
                    "R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1",
                    "R2ProjectDiagnosticPhase.SYNTACTIC",
                    "R2ProjectDiagnosticPhase.SEMANTIC",
                    "expectedCode = 1_109",
                    "expectedCode = 2_322",
                    "request = fixture.x1PublicShapedRequest()",
                    "publicShapedAcknowledgement = true",
                    "assertX2DiagnosticsOnlyTerminalAndArchive(",
                    "awaitActiveJobCount(session.compiler, 0)",
                    "assertNoKnownR2TransientNames(",
                ),
            )
            val archiveAssertionMethod = between(
                instrumentationSource,
                "private fun assertX2DiagnosticsOnlyTerminalAndArchive(",
                "private fun readStableServiceBundle(",
            )
            requireMarkers(
                label = "Android exact diagnostic/output assertions",
                source = archiveAssertionMethod,
                markers = listOf(
                    "TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS",
                    "terminal.getInt(TypeScriptCompilerContract.KEY_DIAGNOSTIC_COUNT, -1)",
                    "terminal.getInt(TypeScriptCompilerContract.KEY_EMITTED_MODULE_COUNT, -1)",
                    "R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH",
                    "R2PrivateOutputArchive.DIAGNOSTICS_PATH",
                    "entries.keys.toList()",
                    "path.startsWith(\"modules/\")",
                    "path.startsWith(\"maps/\")",
                    "assertEquals(true, manifest[\"emitSkipped\"])",
                    "assertEquals(X2_SOURCE_DIAGNOSTIC_KEYS, diagnostic.keys)",
                    "assertEquals(sourceCase.expectedPhase.protocolValue, diagnostic[\"phase\"])",
                    "assertEquals(sourceCase.expectedMessage, diagnostic[\"message\"])",
                    "assertEquals(listOf(X2_SOURCE_PATH), diagnosticsFile.sourcePaths)",
                    "R2OutputArchiveSchemaV1.artifactInventoryFingerprint(outputFiles)",
                    "R2OutputArchiveSchemaV1.moduleInventoryFingerprint(outputFiles)",
                ),
            )
            requireMarkers(
                label = "public ACK and exactly-one callback helper",
                source = instrumentationSource,
                markers = listOf(
                    "publicAcknowledgement(fixture.requestId, token)",
                    "assertEquals(1, callback.terminalCount.get())",
                    "SystemClock.sleep(EXACTLY_ONCE_OBSERVATION_MILLIS)",
                ),
            )

            val profileSource =
                r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            val endToEndAuthorities = between(
                profileSource,
                "private val endToEndAdmittedAuthorities",
                "private val workerResultAdmittedAuthorities",
            )
            check(
                "PROVISIONAL_RHINO" in endToEndAuthorities &&
                    "PROVISIONAL_NODE_NEXT_REVISION_1" in endToEndAuthorities &&
                    "PROVISIONAL_NODE_NEXT_REVISION_2" in endToEndAuthorities
            ) {
                "R2-M2-X2 must preserve exact S1-5 end-to-end profile admission."
            }
            val workerResultAuthorities = between(
                profileSource,
                "private val workerResultAdmittedAuthorities",
                "fun require(profile:",
            )
            check(
                "endToEndAdmittedAuthorities" in
                    workerResultAuthorities
            ) {
                "R2-M2-X2 must preserve the unified end-to-end/Worker-result admission."
            }

            val probeSource = r2CompilationProbeSource.asFile.readText(Charsets.UTF_8)
            val publicClassifier = between(
                probeSource,
                "internal object R2PublicCompilationProtocol",
                "internal object R2RuntimeCapabilityMetadata",
            )
            check(
                "R2CompilationProfileRegistry.PROVISIONAL_RHINO" in publicClassifier &&
                    "PROVISIONAL_NODE_NEXT_REVISION_2" in publicClassifier &&
                    "PROVISIONAL_NODE_NEXT_REVISION_1" !in publicClassifier &&
                    "ACCEPTED_CONTROLLED_TSCONFIG" in publicClassifier &&
                    "rawConfigAuthority = R2RawProjectConfigAuthority(" in publicClassifier
            ) {
                "R2-M2-X2 must admit only Node rev2 publicly and keep tsconfig Rhino-only."
            }
            val probeTestSource =
                r2CompilationProbeProtocolTestSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "public admission fences",
                source = probeTestSource,
                markers = listOf(
                    "publicPreviewRejectsLegacyNodeRevisionOne",
                    "completeControlledTsConfigMetadataCreatesExactPublicRawConfigControls",
                    "publicNodeRevisionTwoCanEnterTheEndToEndPipeline",
                    "completeNodeRevisionTwoMetadataCreatesPublicConfigNoneControls",
                ),
            )

            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M2-X2")

            val buildScriptSource = r2M2P3BuildScript.asFile.readText(Charsets.UTF_8)
            val publicSourcePolicy = between(
                buildScriptSource,
                "tasks.register(\"verifyR2PublicPreviewSourcePolicy\")",
                "inputs.files(",
            )
            check(
                "complete M3-S2" in publicSourcePolicy &&
                    "complete M3-D3" !in publicSourcePolicy &&
                    "complete M3-D2" !in publicSourcePolicy &&
                    "complete M3-D1" !in publicSourcePolicy &&
                    "complete M2-X3" !in publicSourcePolicy &&
                    "complete M2-X2" !in publicSourcePolicy &&
                    "complete M2-X1" !in publicSourcePolicy
            ) {
                "R2-M2-X2 historical gate did not advance to the current M3-S2 source policy."
            }

            logger.lifecycle(
                "R2-M2-X2 pre-device source/JVM/APK evidence verified: unified options/" +
                    "syntactic/global/semantic no-emit model; Android Rhino r1 + NodeNext r1 " +
                    "source-reachable diagnostics-only matrix; Node r2 public end-to-end; " +
                    "compiler capability mask=15; controlled tsconfig revision 2 active",
            )
        }
    }

val verifyR2M2X3FailureIsolationDesktop =
    tasks.register<Exec>("verifyR2M2X3FailureIsolationDesktop") {
        group = "verification"
        description =
            "Runs the R2-M2-X3 TypeScript 6.0.3 Worker oracle for the exact 11 " +
                "controlled-configuration and 18 resolver-decision failure identities " +
                "with snapshot-only/no-network isolation."
        dependsOn(verifyTypeScriptDistribution)
        val compilerFile = generatedTypeScriptDistributionDirectory.map {
            it.file("lib/typescript.js")
        }
        inputs.files(
            r2CompilerWorkerSource,
            compilerFile,
            r2StableFailureCatalogFixture,
            r2StableFailureCatalogHarness,
            r2InputArchiveAuthorityHarness,
        )
        commandLine(
            providers.environmentVariable("NODE_BINARY").getOrElse("node"),
            "--max-old-space-size=512",
            r2StableFailureCatalogHarness.asFile.absolutePath,
            r2CompilerWorkerSource.asFile.absolutePath,
            compilerFile.get().asFile.absolutePath,
            r2StableFailureCatalogFixture.asFile.absolutePath,
            typeScriptVersion,
        )
    }

val verifyR2M2X3StableConfigurationAndResolutionFailures =
    tasks.register("verifyR2M2X3StableConfigurationAndResolutionFailures") {
        group = "verification"
        description =
            "Closes R2-M2-X3 and M2 with exact internal 11 CONFIG_* and 18 " +
                "NODE_RESOLUTION_* detail identities, pre-Program/stop-emit boundaries, " +
                "and snapshot-only/no-network evidence while public schemas remain provisional."

        dependsOn(verifyR2M2X2DiagnosticStopEmitModel)
        dependsOn(verifyR2M2X3FailureIsolationDesktop)
        dependsOn(verifyR2M2C2ControlledTsConfigParser)
        dependsOn(verifyR2M2C3ControlledTsConfigEffectiveOptions)
        dependsOn(verifyR2M2C4CompilerEscapeClosure)
        dependsOn(verifyR2M2C7ControlledTsConfigRootSelection)
        dependsOn(verifyR2M2C6CWorkerSnapshotNodeResolutionKernel)
        dependsOn(verifyR2M2C6NodeResolutionProgramIntegration)
        dependsOn(verifyR2M2P2P7NodeNextResultAndPrivateOutput)
        dependsOn("testDebugUnitTest", "assembleDebugAndroidTest")

        inputs.files(
            r2M2P3BuildScript,
            r2StableFailureCatalogFixture,
            r2StableFailureCatalogHarness,
            r2StableFailureCatalogFixtureTestSource,
            r2ApiContractSource,
            r2ControlledTsConfigPolicySource,
            r2ControlledTsConfigWorkerProtocolTestSource,
            r2NodeResolutionKernelSource,
            r2NodeResolutionKernelTestSource,
            r2CompilerWorkerSource,
            r2CompilerHostPageSource,
            r2CompilerHostProtocolSource,
            r2ProjectCompilationSource,
            r2ProjectCompilationTestSource,
            r2ProjectFramedTransportTestSource,
            r2PrivateOutputArchiveTestSource,
            r2CompilationCoordinatorSource,
            r2NodeResolutionProgramHarness,
            r2M2P2P6NodeNextProgramHarness,
            r2CompilationProfileAuthoritySource,
            r2CompilationProbeSource,
            r2CompilationProbeProtocolTestSource,
            r2PublicPreviewMetadataSource,
        )

        doLast {
            fun requireMarkers(label: String, source: String, markers: List<String>) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-M2-X3 $label marker is missing: $marker"
                    }
                }
            }

            fun between(source: String, start: String, end: String): String {
                val startIndex = source.indexOf(start)
                val endIndex = source.indexOf(end, startIndex + start.length)
                check(startIndex >= 0 && endIndex > startIndex) {
                    "R2-M2-X3 could not isolate $start..$end."
                }
                return source.substring(startIndex, endIndex)
            }

            @Suppress("UNCHECKED_CAST")
            val fixture = JsonSlurper().parse(r2StableFailureCatalogFixture.asFile) as
                Map<String, Any?>
            check(
                fixture.keys.toList() == listOf(
                    "schema",
                    "revision",
                    "typescriptVersion",
                    "callerErrorCode",
                    "scope",
                    "excludedDomains",
                    "configurationFailures",
                    "resolutionFailures",
                    "cleanSuccessors",
                ) &&
                    fixture["schema"] ==
                    "autojs6.typescript.r2-m2-x3.configuration-resolution-failures" &&
                    (fixture["revision"] as Number).toInt() == 1 &&
                    fixture["typescriptVersion"] == typeScriptVersion
            ) {
                "R2-M2-X3 stable-failure fixture identity or exact root shape drifted."
            }
            @Suppress("UNCHECKED_CAST")
            val configFailures = fixture["configurationFailures"] as List<Map<String, Any?>>
            @Suppress("UNCHECKED_CAST")
            val resolutionFailures = fixture["resolutionFailures"] as List<Map<String, Any?>>
            check(
                configFailures.size == 11 &&
                    configFailures.map { failure -> failure["policyCode"] }.toSet().size == 11 &&
                    resolutionFailures.size == 18 &&
                    resolutionFailures.map { failure -> failure["policyCode"] }.toSet().size == 18 &&
                    resolutionFailures.mapIndexed { index, failure ->
                        (failure["diagnosticCode"] as Number).toInt() == 900_001 + index
                    }.all { it }
            ) {
                "R2-M2-X3 must retain exact 11/18 failure inventories and 900001..900018 mapping."
            }

            val apiSource = r2ApiContractSource.asFile.readText(Charsets.UTF_8)
            val policySource = r2ControlledTsConfigPolicySource.asFile.readText(Charsets.UTF_8)
            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            val hostPageSource = r2CompilerHostPageSource.asFile.readText(Charsets.UTF_8)
            val kernelSource = r2NodeResolutionKernelSource.asFile.readText(Charsets.UTF_8)
            configFailures.forEach { failure ->
                val code = failure["policyCode"] as String
                check(code in apiSource && code in workerSource && code in hostPageSource) {
                    "R2-M2-X3 configuration detail identity is not cross-layer exact: $code"
                }
            }
            resolutionFailures.forEach { failure ->
                val code = failure["policyCode"] as String
                val diagnosticCode = (failure["diagnosticCode"] as Number).toInt()
                check(code in kernelSource && code in workerSource && diagnosticCode in 900_001..900_018) {
                    "R2-M2-X3 resolution detail identity is not cross-layer exact: $code"
                }
            }
            requireMarkers(
                label = "controlled-config typed terminal",
                source = policySource + "\n" +
                    r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8) + "\n" +
                    r2ControlledTsConfigWorkerProtocolTestSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "val policyCodes: Set<String>",
                    "projectConfigError",
                    "Controlled-config error has an unknown policy code.",
                    "typedConfigErrorPreservesBoundedPolicyIdentity",
                ),
            )
            requireMarkers(
                label = "resolver diagnostic authority",
                source = kernelSource + "\n" +
                    r2ProjectCompilationSource.asFile.readText(Charsets.UTF_8) + "\n" +
                    r2ProjectFramedTransportTestSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "internal enum class R2NodeResolutionFailureCode",
                    "900_001",
                    "fromDiagnosticIdentityOrNull",
                    "Only a resolution diagnostic can carry node-resolution evidence.",
                    "framedResultParserAcceptsEveryExactNodeResolutionDiagnosticIdentity",
                ),
            )

            val compilationSource = r2ProjectCompilationSource.asFile.readText(Charsets.UTF_8)
            val compilationTests = r2ProjectCompilationTestSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "exact controlled/resolution profile boundary",
                source = compilationSource + "\n" + compilationTests,
                markers = listOf(
                    "exactProfileAuthority ==",
                    "R2CompilationProfileRegistry.PROVISIONAL_RHINO",
                    "Controlled compilation profile/effective-options authority is inconsistent.",
                    "controlledCompilationRequiresExactRhinoRevisionOneProfile",
                    "resolutionDiagnosticsAreAdmittedOnlyForControlledRhinoOrNodeRevisionTwo",
                ),
            )

            val harnessSource = r2StableFailureCatalogHarness.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "direct isolation oracle",
                source = harnessSource,
                markers = listOf(
                    "config=11/11",
                    "resolution=18/18",
                    "failure Program=0",
                    "outside-snapshot positive reads=0",
                    "ts.sys/default resolver/network=0",
                ),
            )
            val existingProgramHarnesses =
                r2NodeResolutionProgramHarness.asFile.readText(Charsets.UTF_8) + "\n" +
                    r2M2P2P6NodeNextProgramHarness.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "natural resolver Program sentinels",
                source = existingProgramHarnesses,
                markers = listOf(
                    "default resolver/ts.sys/network=0",
                    "P2 framed results=7, default resolver/ts.sys/network=0",
                ),
            )

            val fixtureTestSource =
                r2StableFailureCatalogFixtureTestSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "JVM catalog authority",
                source = fixtureTestSource,
                markers = listOf(
                    "class R2StableFailureCatalogFixtureTest",
                    "fixtureFreezesExactConfigurationAndResolutionFailureCatalogs",
                    "catalogKeepsInternalDetailsSeparateFromCallerTerminalAndExcludedDomains",
                ),
            )

            val coordinatorSource = r2CompilationCoordinatorSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "provisional caller terminal",
                source = coordinatorSource,
                markers = listOf(
                    "TypeScriptCompilerContract.ERROR_COMPILATION_DIAGNOSTICS",
                    "Project compilation produced diagnostics without executable output.",
                ),
            )

            val profileSource = r2CompilationProfileAuthoritySource.asFile.readText(Charsets.UTF_8)
            val endToEndAuthorities = between(
                profileSource,
                "private val endToEndAdmittedAuthorities",
                "private val workerResultAdmittedAuthorities",
            )
            check(
                "PROVISIONAL_RHINO" in endToEndAuthorities &&
                    "PROVISIONAL_NODE_NEXT_REVISION_1" in endToEndAuthorities &&
                    "PROVISIONAL_NODE_NEXT_REVISION_2" in endToEndAuthorities
            ) {
                "R2-M2-X3 must preserve exact S1-5 end-to-end profile admission."
            }
            val publicClassifier = between(
                r2CompilationProbeSource.asFile.readText(Charsets.UTF_8),
                "internal object R2PublicCompilationProtocol",
                "internal object R2RuntimeCapabilityMetadata",
            )
            check(
                "R2CompilationProfileRegistry.PROVISIONAL_RHINO" in publicClassifier &&
                    "PROVISIONAL_NODE_NEXT_REVISION_2" in publicClassifier &&
                    "PROVISIONAL_NODE_NEXT_REVISION_1" !in publicClassifier &&
                    "ACCEPTED_CONTROLLED_TSCONFIG" in publicClassifier &&
                    "rawConfigAuthority = R2RawProjectConfigAuthority(" in publicClassifier
            ) {
                "R2-M2-X3 must admit only Node rev2 publicly and keep tsconfig Rhino-only."
            }
            val probeTests = r2CompilationProbeProtocolTestSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "public promotion fences",
                source = probeTests,
                markers = listOf(
                    "publicPreviewRejectsLegacyNodeRevisionOne",
                    "completeControlledTsConfigMetadataCreatesExactPublicRawConfigControls",
                    "publicNodeRevisionTwoCanEnterTheEndToEndPipeline",
                    "completeNodeRevisionTwoMetadataCreatesPublicConfigNoneControls",
                ),
            )
            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M2-X3")

            val buildScriptSource = r2M2P3BuildScript.asFile.readText(Charsets.UTF_8)
            val publicSourcePolicy = between(
                buildScriptSource,
                "tasks.register(\"verifyR2PublicPreviewSourcePolicy\")",
                "inputs.files(",
            )
            check(
                "complete M3-S2" in publicSourcePolicy &&
                    "complete M3-D3" !in publicSourcePolicy &&
                    "complete M3-D2" !in publicSourcePolicy &&
                    "complete M3-D1" !in publicSourcePolicy &&
                    "complete M2-X3" !in publicSourcePolicy &&
                    "complete M2-X2" !in publicSourcePolicy
            ) {
                "R2-M2-X3 historical gate did not advance to the current M3-S2 source policy."
            }

            logger.lifecycle(
                "R2-M2-X3 and M2 stable-failure closure verified: internal config=11/11, " +
                    "resolution=18/18 with 900001..900018, pre-Program/stop-emit isolation, " +
                    "outside-snapshot positive reads/default resolver/ts.sys/network=0, " +
                    "formal input-archive schema v1, Node r2 public end-to-end, " +
                    "compiler capability mask=15",
            )
        }
    }

val verifyR2M3D1OrdinaryInputQuotaFixture =
    tasks.register("verifyR2M3D1OrdinaryInputQuotaFixture") {
        group = "verification"
        description =
            "Freezes the R2-M3-D1 ordinary 512-file/8 MiB input quota, exact counting " +
                "domain, inclusive/+1 boundaries, current profile declaration occupancy, " +
                "and separation from 64 MiB transport/TEST_ONLY capacity."

        dependsOn(verifyR2DeclarationDistribution)
        dependsOn("testDebugUnitTest")
        dependsOn(":plugin-api:typescript-api:testDebugUnitTest")
        inputs.files(
            r2M2P3BuildScript,
            r2OrdinaryInputQuotaDecisionFixture,
            r2OrdinaryInputQuotaDecisionFixtureTestSource,
            r2ApiContractSource,
            r2ApiContractTestSource,
            r2ProjectSnapshotSource,
            r2ProjectArchiveSource,
            r2FileBackedProjectSnapshotSource,
            r2ProjectArchiveLoaderTestSource,
            r2ProjectSnapshotTestSource,
            r2FileBackedProjectSnapshotTestSource,
            r2CompilationCoordinatorSource,
            compilerWebViewHostSource,
            r2ExternalCapacityEvidenceProtocolSource,
            r2CompilerWorkerSource,
            r2PublicPreviewMetadataSource,
            r2BinderCompilationInstrumentationSource,
        )
        inputs.dir(generatedR2DeclarationDistributionDirectory)

        doLast {
            fun requireMarkers(label: String, source: String, markers: List<String>) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-M3-D1 $label marker is missing: $marker"
                    }
                }
            }

            fun between(source: String, start: String, end: String): String {
                val startIndex = source.indexOf(start)
                val endIndex = source.indexOf(end, startIndex + start.length)
                check(startIndex >= 0 && endIndex > startIndex) {
                    "R2-M3-D1 could not isolate $start..$end."
                }
                return source.substring(startIndex, endIndex)
            }

            @Suppress("UNCHECKED_CAST")
            val fixture = JsonSlurper().parse(r2OrdinaryInputQuotaDecisionFixture.asFile) as
                Map<String, Any?>
            check(
                fixture.keys.toList() == listOf(
                    "schema",
                    "revision",
                    "decision",
                    "ordinaryCompilationQuota",
                    "countingDomain",
                    "profileDeclarationOccupancy",
                    "boundaryCases",
                    "transportCeiling",
                    "testOnlyExpandedCapacity",
                    "quotaRejection",
                    "promotionState",
                ) &&
                    fixture["schema"] ==
                    "autojs6.typescript.r2-m3-d1.ordinary-input-quota-decision" &&
                    (fixture["revision"] as Number).toInt() == 1 &&
                    fixture["decision"] ==
                    "retain-512-files-8mib-single-file-8mib-total"
            ) {
                "R2-M3-D1 quota fixture identity or exact root shape drifted."
            }

            @Suppress("UNCHECKED_CAST")
            val quota = fixture["ordinaryCompilationQuota"] as Map<String, Any?>
            check(
                quota.keys.toList() == listOf(
                    "schemaRevision",
                    "maximumFiles",
                    "maximumSingleFileBytes",
                    "maximumTotalBytes",
                    "comparison",
                ) &&
                    (quota["schemaRevision"] as Number).toInt() == 1 &&
                    (quota["maximumFiles"] as Number).toInt() == 512 &&
                    (quota["maximumSingleFileBytes"] as Number).toLong() ==
                    8L * 1024L * 1024L &&
                    (quota["maximumTotalBytes"] as Number).toLong() ==
                    8L * 1024L * 1024L &&
                    quota["comparison"] ==
                    "inclusive-when-all-other-dimensions-remain-eligible"
            ) {
                "R2-M3-D1 ordinary compilation quota is not the exact 512/8 MiB decision."
            }

            val countedInputs = listOf(
                "project-vfs-files-including-trusted-declarations",
                "raw-config-if-present",
                "package-metadata-files",
                "package-target-files",
                "node-package-target-v2-files",
            )
            @Suppress("UNCHECKED_CAST")
            val countingDomain = fixture["countingDomain"] as Map<String, Any?>
            check(
                countingDomain.keys.toList() == listOf(
                    "unit",
                    "fileCountIncludes",
                    "byteCountIncludes",
                    "excludes",
                ) &&
                    countingDomain["unit"] == "decoded-utf8-bytes" &&
                    countingDomain["fileCountIncludes"] == countedInputs &&
                    countingDomain["byteCountIncludes"] == countedInputs &&
                    countingDomain["excludes"] == listOf(
                        "request-manifest-json",
                        "zip-container-bytes",
                        "framing-and-base64-overhead",
                        "output-archive-bytes",
                    )
            ) {
                "R2-M3-D1 ordinary quota counting domain drifted."
            }

            @Suppress("UNCHECKED_CAST")
            val boundaryCases = fixture["boundaryCases"] as List<Map<String, Any?>>
            val expectedLimits = linkedMapOf(
                "file-count" to 512L,
                "single-file-bytes" to 8L * 1024L * 1024L,
                "total-bytes" to 8L * 1024L * 1024L,
            )
            check(
                boundaryCases.map { boundary -> boundary["dimension"] } ==
                    expectedLimits.keys.toList() &&
                    boundaryCases.all { boundary ->
                        val limit = expectedLimits.getValue(boundary["dimension"] as String)
                        boundary.keys.toList() == listOf(
                            "dimension",
                            "atLimit",
                            "atLimitAccepted",
                            "plusOne",
                            "plusOneAccepted",
                        ) &&
                            (boundary["atLimit"] as Number).toLong() == limit &&
                            boundary["atLimitAccepted"] == true &&
                            (boundary["plusOne"] as Number).toLong() == limit + 1L &&
                            boundary["plusOneAccepted"] == false
                    }
            ) {
                "R2-M3-D1 inclusive/+1 boundary matrix drifted."
            }

            @Suppress("UNCHECKED_CAST")
            val profileOccupancy = fixture["profileDeclarationOccupancy"] as
                List<Map<String, Any?>>
            val expectedProfileOccupancy = linkedMapOf(
                "rhino" to listOf(1L, 825L, 109L, 3_785_583L, 403L, 4_603_025L),
                "node" to listOf(59L, 246_572L, 167L, 4_031_330L, 345L, 4_357_278L),
            )
            check(
                profileOccupancy.map { profile -> profile["profile"] } ==
                    expectedProfileOccupancy.keys.toList() &&
                    profileOccupancy.all { profile ->
                        val expected = expectedProfileOccupancy.getValue(
                            profile["profile"] as String,
                        )
                        profile.keys.toList() == listOf(
                            "profile",
                            "commonFiles",
                            "commonBytes",
                            "profileFiles",
                            "profileBytes",
                            "occupiedFiles",
                            "occupiedBytes",
                            "maximumCallerFilesBeforeOptionalMetadata",
                            "maximumCallerBytesBeforeOptionalMetadata",
                        ) &&
                            (profile["commonFiles"] as Number).toLong() == 108L &&
                            (profile["commonBytes"] as Number).toLong() == 3_784_758L &&
                            (profile["profileFiles"] as Number).toLong() == expected[0] &&
                            (profile["profileBytes"] as Number).toLong() == expected[1] &&
                            (profile["occupiedFiles"] as Number).toLong() == expected[2] &&
                            (profile["occupiedBytes"] as Number).toLong() == expected[3] &&
                            (profile["maximumCallerFilesBeforeOptionalMetadata"] as Number)
                                .toLong() == expected[4] &&
                            (profile["maximumCallerBytesBeforeOptionalMetadata"] as Number)
                                .toLong() == expected[5] &&
                            expected[2] + expected[4] == 512L &&
                            expected[3] + expected[5] == 8L * 1024L * 1024L
                    }
            ) {
                "R2-M3-D1 profile-specific declaration occupancy/headroom drifted."
            }

            @Suppress("UNCHECKED_CAST")
            val distribution = JsonSlurper().parse(
                generatedR2DeclarationDistributionDirectory.get()
                    .file("distribution.json").asFile,
            ) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val generatedLayers = distribution["layers"] as Map<String, Map<String, Any?>>
            val commonLayer = generatedLayers.getValue("common")
            check(
                (commonLayer["fileCount"] as Number).toInt() == 108 &&
                    (commonLayer["totalBytes"] as Number).toLong() == 3_784_758L &&
                    profileOccupancy.all { profile ->
                        val layer = generatedLayers.getValue(profile["profile"] as String)
                        (layer["fileCount"] as Number).toInt() ==
                            (profile["profileFiles"] as Number).toInt() &&
                            (layer["totalBytes"] as Number).toLong() ==
                            (profile["profileBytes"] as Number).toLong()
                    }
            ) {
                "R2-M3-D1 quota headroom no longer matches the generated declaration layers."
            }

            @Suppress("UNCHECKED_CAST")
            val transport = fixture["transportCeiling"] as Map<String, Any?>
            check(
                transport.keys.toList() == listOf(
                    "authority",
                    "maximumArchiveFiles",
                    "maximumArchiveContainerBytes",
                    "maximumArchiveExpandedBytes",
                    "maximumSingleFileBytes",
                    "promotesOrdinaryCompilationQuota",
                ) &&
                    transport["authority"] == "archive-validation-and-private-staging-only" &&
                    (transport["maximumArchiveFiles"] as Number).toInt() == 4_096 &&
                    (transport["maximumArchiveContainerBytes"] as Number).toLong() ==
                    64L * 1024L * 1024L &&
                    (transport["maximumArchiveExpandedBytes"] as Number).toLong() ==
                    64L * 1024L * 1024L &&
                    (transport["maximumSingleFileBytes"] as Number).toLong() ==
                    8L * 1024L * 1024L &&
                    transport["promotesOrdinaryCompilationQuota"] == false
            ) {
                "R2-M3-D1 transport ceiling was conflated with ordinary compilation quota."
            }

            @Suppress("UNCHECKED_CAST")
            val testOnly = fixture["testOnlyExpandedCapacity"] as Map<String, Any?>
            check(
                testOnly.keys.toList() == listOf(
                    "authority",
                    "evidenceBuildRequired",
                    "testOnlyCallerRequired",
                    "sameSignerRequired",
                    "distinctUidRequired",
                    "publicRequestAllowed",
                    "ordinaryQuotaMutated",
                    "maximumWorkerFiles",
                    "maximumWorkerTotalBytes",
                ) &&
                    testOnly["authority"] ==
                    "same-signer-distinct-uid-test-only-evidence" &&
                    testOnly["evidenceBuildRequired"] == true &&
                    testOnly["testOnlyCallerRequired"] == true &&
                    testOnly["sameSignerRequired"] == true &&
                    testOnly["distinctUidRequired"] == true &&
                    testOnly["publicRequestAllowed"] == false &&
                    testOnly["ordinaryQuotaMutated"] == false &&
                    (testOnly["maximumWorkerFiles"] as Number).toInt() == 4_607 &&
                    (testOnly["maximumWorkerTotalBytes"] as Number).toLong() ==
                    72L * 1024L * 1024L
            ) {
                "R2-M3-D1 TEST_ONLY expanded-capacity isolation drifted."
            }

            @Suppress("UNCHECKED_CAST")
            val rejection = fixture["quotaRejection"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val promotion = fixture["promotionState"] as Map<String, Any?>
            check(
                rejection.keys.toList() == listOf(
                    "errorCode",
                    "diagnosticCount",
                    "callerOutputBytes",
                    "workerDispatchStarted",
                    "automaticRetryAllowed",
                ) &&
                    rejection["errorCode"] ==
                    "ERR_AUTOJS6_TYPESCRIPT_ARCHIVE_QUOTA" &&
                    (rejection["diagnosticCount"] as Number).toInt() == 0 &&
                    (rejection["callerOutputBytes"] as Number).toLong() == 0L &&
                    rejection["workerDispatchStarted"] == false &&
                    rejection["automaticRetryAllowed"] == false &&
                    promotion.keys.toList() == listOf(
                        "publicCapabilityChanged",
                        "runtimeCapabilityChanged",
                        "nodeRevisionTwoEndToEndChanged",
                    ) &&
                    promotion.values.all { value -> value == false }
            ) {
                "R2-M3-D1 rejection or non-promotion decision drifted."
            }

            val apiSource = r2ApiContractSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "public ordinary quota authority",
                source = apiSource + "\n" +
                    r2ApiContractTestSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "const val ORDINARY_COMPILATION_INPUT_QUOTA_REVISION = 2",
                    "const val MAX_ORDINARY_COMPILATION_INPUT_FILES = 512",
                    "const val MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES = " +
                        "8L * 1024L * 1024L",
                    "const val MAX_ORDINARY_COMPILATION_INPUT_BYTES = 8L * 1024L * 1024L",
                    "transport-valid does",
                    "not make it eligible for ordinary compilation",
                    "ordinaryCompilationInputQuotaIsExactRevisionTwoAndNotTheTransportCeiling",
                ),
            )

            val snapshotSource = r2ProjectSnapshotSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "native ordinary quota authority",
                source = snapshotSource,
                markers = listOf(
                    "val ORDINARY_COMPILATION = R2ProjectSnapshotLimits(",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES",
                    "val PROVISIONAL_WORKER = ORDINARY_COMPILATION",
                    "internal fun ordinaryCompilationAdmission(",
                    "(authority?.callerCapacity ?: derivedCaller).admissionAgainst(limits)",
                    "fileCount > limits.maximumFiles",
                    "largestFileUtf8Bytes > limits.maximumSingleFileBytes",
                    "totalUtf8Bytes > limits.maximumTotalBytes",
                ),
            )
            val capacityRegion = between(
                snapshotSource,
                "private fun inputCapacity(",
                "internal fun validateInventory(",
            )
            requireMarkers(
                label = "complete Worker-retained counting domain",
                source = capacityRegion,
                markers = listOf(
                    "snapshot.fileInventory.size",
                    "snapshot.totalUtf8Bytes",
                    "snapshot.largestFileUtf8Bytes",
                    "rawConfig",
                    "packageMetadata",
                    "packageTargets",
                    "nodePackageTargetsV2",
                    "Math.addExact(",
                ),
            )

            val fileBackedSource =
                r2FileBackedProjectSnapshotSource.asFile.readText(Charsets.UTF_8)
            val fileBackedCapacityRegion = between(
                fileBackedSource,
                "val acceptedCapacity = R2ProjectInputCapacity(",
                "val writer = requireNotNull(output)",
            )
            requireMarkers(
                label = "file-backed five-domain accepted-capacity reconstruction",
                source = fileBackedCapacityRegion,
                markers = listOf(
                    "projectRecords.size + if (rawConfigRecord == null) 0 else 1",
                    "packageMetadataRecords.size",
                    "packageTargetRecords.size",
                    "nodePackageTargetV2Records.size",
                    "inventory.totalUtf8Bytes",
                    "rawConfigRecord?.descriptor?.size ?: 0L",
                    "packageMetadataInventory?.totalBytes ?: 0L",
                    "packageTargetInventory?.totalBytes ?: 0L",
                    "nodePackageTargetV2Inventory?.totalBytes ?: 0L",
                    "acceptedCapacity.admissionAgainst(limits).isEligible",
                ),
            )

            val archiveSource = r2ProjectArchiveSource.asFile.readText(Charsets.UTF_8)
            val archiveCapacityRegion = between(
                archiveSource,
                "val snapshotLimits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE",
                "val records = ArrayList<R2FileBackedProjectRecord>(",
            )
            requireMarkers(
                label = "archive five-domain quota accounting",
                source = archiveCapacityRegion,
                markers = listOf(
                    "manifest.files.size",
                    "if (rawConfigRecord == null) 0 else 1",
                    "packageMetadataRecords.size",
                    "packageTargetRecords.size",
                    "nodePackageTargetV2Records.size",
                    "trustedDeclarations.files.size",
                    "manifest.rawConfigDescriptor?.let",
                    "manifest.packageMetadataInventory?.files?.forEach",
                    "manifest.packageTargetInventory?.files?.forEach",
                    "manifest.nodePackageTargetV2Inventory?.files?.forEach",
                    "manifest.files.forEach",
                    "TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA",
                ),
            )
            requireMarkers(
                label = "archive-to-file-backed five-domain adapter and quota mapping",
                source = archiveSource,
                markers = listOf(
                    "R2ProjectArchiveAdapter.adaptFileBacked(",
                    "spool.seal(",
                    "rawConfigRecord = rawConfigRecord",
                    "packageMetadataRecords = packageMetadataRecords",
                    "packageTargetRecords = packageTargetRecords",
                    "nodePackageTargetV2Records = nodePackageTargetV2Records",
                    "is R2PrivateInputStagingQuotaException -> R2ProjectArchiveException(",
                    "TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA",
                ),
            )

            val hostSource = compilerWebViewHostSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "pre-dispatch ordinary admission",
                source = hostSource,
                markers = listOf(
                    "workerAdmissionLimits: R2ProjectSnapshotLimits =\n" +
                        "            R2ProjectSnapshotLimits.ORDINARY_COMPILATION",
                    "val workerAdmission = R2ProjectSnapshot.ordinaryCompilationAdmission(",
                    "code = TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA",
                    "Project is outside the ordinary compilation input limit:",
                ),
            )
            requireMarkers(
                label = "plugin-owned declaration failure ownership",
                source = r2ProjectArchiveSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "Local declarations exceed the ordinary compilation file-count limit.",
                    "A local declaration exceeds the ordinary compilation single-file limit.",
                    "Local declarations exceed the ordinary compilation byte limit.",
                    "TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE",
                ),
            )
            val admissionIndex = hostSource.indexOf(
                "val workerAdmission = R2ProjectSnapshot.ordinaryCompilationAdmission(",
            )
            val claimIndex = hostSource.indexOf("if (!handle.claim())", admissionIndex)
            val framingIndex = hostSource.indexOf("R2ProjectInputFramer(", admissionIndex)
            check(
                admissionIndex >= 0 && claimIndex > admissionIndex && framingIndex > claimIndex
            ) {
                "R2-M3-D1 ordinary quota rejection must precede handle claim and Worker framing."
            }
            val coordinatorSource =
                r2CompilationCoordinatorSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "accepted-job quota failure empty output",
                source = coordinatorSource,
                markers = listOf(
                    "if (!compilationOutcome.succeeded)",
                    "finalizeFailure(job, failure)",
                    "R2OutputFdCommitter.clear(output)",
                    "diagnosticCount = 0",
                    "emittedModuleCount = 0",
                    "outputManifestSha256 = null",
                ),
            )

            val expandedRegion = between(
                hostSource,
                "internal fun compileProjectWithExpandedWorkerCapacityForTesting(",
                "internal fun requestStop(",
            )
            requireMarkers(
                label = "expanded capacity non-shipping seam",
                source = expandedRegion,
                markers = listOf(
                    "BuildConfig.DEBUG ||",
                    "BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE",
                    "Expanded compiler Worker capacity is unavailable outside debug/evidence builds.",
                    "workerAdmissionLimits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE",
                ),
            )
            requireMarkers(
                label = "external TEST_ONLY attestation",
                source = r2ExternalCapacityEvidenceProtocolSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "attestation.evidenceBuildEnabled",
                    "attestation.callingUid != attestation.applicationUid",
                    "attestation.applicationTestOnly",
                    "attestation.fixtureTestOnly",
                    "attestation.signaturesMatch",
                    "R2CompilationControlOrigin.EXTERNAL_CAPACITY_EVIDENCE",
                    "expandedWorkerCapacity = true",
                ),
            )
            requireMarkers(
                label = "expanded Worker transport envelope",
                source = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "const maximumProjectFiles = 4_607;",
                    "const maximumProjectFileBytes = 8 * 1024 * 1024;",
                    "const maximumProjectBytes = 72 * 1024 * 1024;",
                ),
            )

            val fixtureTestSource =
                r2OrdinaryInputQuotaDecisionFixtureTestSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "strict JVM cross-layer fixture",
                source = fixtureTestSource,
                markers = listOf(
                    "class R2OrdinaryInputQuotaDecisionFixtureTest",
                    "fixtureFreezesOrdinaryQuotaAcrossPublicAndNativeAuthorities",
                    "profileDeclarationOccupancyDerivesCurrentCallerHeadroom",
                    "countingDomainAndTestOnlyTransportCapacityRemainDisjoint",
                    "R2ProjectSnapshot.ordinaryCompilationAdmission(snapshot)",
                    "r2.generatedDeclarationDistributionDir",
                    "TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA",
                    "callerOutputBytes",
                    "workerDispatchStarted",
                    "R2PackageTargetInventory.create(",
                    "R2NodePackageTargetV2Inventory.create(",
                ),
            )
            requireMarkers(
                label = "existing JVM five-domain and file-backed adapter coverage",
                source = listOf(
                    r2ProjectSnapshotTestSource,
                    r2ProjectArchiveLoaderTestSource,
                    r2FileBackedProjectSnapshotTestSource,
                ).joinToString("\n") { source -> source.asFile.readText(Charsets.UTF_8) },
                markers = listOf(
                    "packageMetadataConsumesOrdinaryCapacityButNotProjectVfsInventory",
                    "heapPackageTargetsAreIndependentBudgetedAndInvalidatedOnClose",
                    "nodeRevisionTwoHeapV2TargetsAreIndependentBudgetedAndInvalidatedOnClose",
                    "nodeRevisionTwoMetadataProducesAnExactFileBackedPackageFormatCatalog",
                    "nodeRevisionTwoFormalSchemaLoadsSixV2TargetSuffixesIntoIndependentCursors",
                    "sealRejectsCombinedPackageMetadataCapacityAndRetainsCleanupOwnership",
                    "sealCountsPackageTargetsAgainstTheSharedCapacityBudget",
                ),
            )

            val instrumentationSource =
                r2BinderCompilationInstrumentationSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "historical 64 MiB transport-only evidence",
                source = instrumentationSource,
                markers = listOf(
                    "storedContainerCapacityAcceptsExact64MiBAndRejectsPlusOne",
                    "deflatedCapacityAcceptsExact64MiBExpandedAndRejectsPlusOne",
                    "TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES",
                    "TypeScriptCompilerContract.MAX_INPUT_BYTES",
                    "assertEquals(0L, output.length())",
                ),
            )

            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M3-D1")

            val buildScriptSource = r2M2P3BuildScript.asFile.readText(Charsets.UTF_8)
            val publicSourcePolicy = between(
                buildScriptSource,
                "tasks.register(\"verifyR2PublicPreviewSourcePolicy\")",
                "inputs.files(",
            )
            check(
                "complete M3-S2" in publicSourcePolicy &&
                    "complete M3-D3" !in publicSourcePolicy &&
                    "complete M3-D2" !in publicSourcePolicy &&
                    "complete M3-D1" !in publicSourcePolicy &&
                    "complete M2-X3" !in publicSourcePolicy
            ) {
                "R2-M3-D1 historical gate did not advance to the current M3-S2 source policy."
            }

            logger.lifecycle(
                "R2-M3-D1 ordinary input quota fixture verified: public/native=512 files, " +
                    "8 MiB single/total inclusive; complete Worker-retained counting domain; " +
                    "Rhino declarations=109/3785583 with headroom=403/4603025; " +
                    "Node declarations=167/4031330 with headroom=345/4357278; " +
                    "64 MiB transport and 72 MiB TEST_ONLY Worker evidence remain isolated; " +
                    "quota rejection is pre-dispatch with empty-output contract; " +
                    "compiler capability mask=15",
            )
        }
    }

val verifyR2M3D1OrdinaryInputQuotaDecision =
    tasks.register("verifyR2M3D1OrdinaryInputQuotaDecision") {
        group = "verification"
        description =
            "Closes R2-M3-D1 through the canonical quota fixture, strict JVM/source gates, " +
                "generated declaration occupancy, complete M2-X3 dependency, and AndroidTest APK."

        dependsOn(verifyR2M2X3StableConfigurationAndResolutionFailures)
        dependsOn(verifyR2M3D1OrdinaryInputQuotaFixture)
        dependsOn("verifyR2PublicPreviewSourcePolicy")
        dependsOn("assembleDebugAndroidTest")

        doLast {
            logger.lifecycle(
                "R2-M3-D1 ordinary input quota decision verified: 512 files / 8 MiB " +
                    "single / 8 MiB total, inclusive/+1 and profile-derived headroom; " +
                    "transport/TEST_ONLY capacity is not public compilation authority",
            )
        }
    }

val verifyR2M3D2OrdinaryOutputQuotaFixture =
    tasks.register("verifyR2M3D2OrdinaryOutputQuotaFixture") {
        group = "verification"
        description =
            "Freezes the R2-M3-D2 final-packaged JS/map quota at 512 artifacts, " +
                "512 KiB per artifact, and 1 MiB payload while keeping diagnostics, " +
                "result framing, and ZIP ceilings in separate evidence domains."

        dependsOn("testDebugUnitTest")
        dependsOn(":plugin-api:typescript-api:testDebugUnitTest")
        inputs.files(
            r2M2P3BuildScript,
            r2OrdinaryOutputQuotaDecisionFixture,
            r2OrdinaryOutputQuotaDecisionFixtureTestSource,
            r2ApiContractSource,
            r2ApiContractTestSource,
            r2OrdinaryOutputQuotaSource,
            r2OrdinaryOutputQuotaTestSource,
            r2PrivateOutputQuotaIntegrationTestSource,
            r2CompilerWorkerSource,
            r2CompilerHostProtocolSource,
            compilerWebViewHostSource,
            r2CompilationCoordinatorSource,
            r2PrivateOutputArchiveSource,
            r2PrivateOutputArchiveTestSource,
            r2PublicPreviewMetadataSource,
        )

        doLast {
            fun requireMarkers(label: String, source: String, markers: List<String>) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-M3-D2 $label marker is missing: $marker"
                    }
                }
            }

            @Suppress("UNCHECKED_CAST")
            val fixture = JsonSlurper().parse(r2OrdinaryOutputQuotaDecisionFixture.asFile) as
                Map<String, Any?>
            check(
                fixture.keys.toList() == listOf(
                    "schema",
                    "revision",
                    "decision",
                    "ordinaryCompilationOutputQuota",
                    "countingDomain",
                    "boundaryCases",
                    "separateDeferredGuardrails",
                    "measuredWorkloads",
                    "legacyTransportAndArchiveCeiling",
                    "failureOwnership",
                    "promotionState",
                ) &&
                    fixture["schema"] ==
                    "autojs6.typescript.r2-m3-d2.ordinary-output-quota-decision" &&
                    (fixture["revision"] as Number).toInt() == 1 &&
                    fixture["decision"] ==
                    "retain-512-artifacts-512kib-per-artifact-1mib-packaged-payload"
            ) {
                "R2-M3-D2 fixture identity or exact root shape drifted."
            }

            @Suppress("UNCHECKED_CAST")
            val quota = fixture["ordinaryCompilationOutputQuota"] as Map<String, Any?>
            check(
                quota.keys.toList() == listOf(
                    "schemaRevision",
                    "maximumPackagedFiles",
                    "maximumSinglePackagedJavaScriptBytes",
                    "maximumSinglePackagedSourceMapBytes",
                    "maximumPackagedPayloadBytes",
                    "comparison",
                ) &&
                    (quota["schemaRevision"] as Number).toInt() == 1 &&
                    (quota["maximumPackagedFiles"] as Number).toInt() == 512 &&
                    (quota["maximumSinglePackagedJavaScriptBytes"] as Number).toLong() ==
                    512L * 1024L &&
                    (quota["maximumSinglePackagedSourceMapBytes"] as Number).toLong() ==
                    512L * 1024L &&
                    (quota["maximumPackagedPayloadBytes"] as Number).toLong() ==
                    1024L * 1024L &&
                    quota["comparison"] ==
                    "inclusive-when-all-other-dimensions-remain-eligible"
            ) {
                "R2-M3-D2 ordinary packaged-output quota drifted."
            }

            @Suppress("UNCHECKED_CAST")
            val domain = fixture["countingDomain"] as Map<String, Any?>
            check(
                domain.keys.toList() == listOf(
                    "unit",
                    "includes",
                    "excludes",
                    "packagedArchiveQuotaOwner",
                    "packagedArchiveDiagnosticsShapeDependency",
                ) &&
                    domain["unit"] == "final-packaged-artifact-utf8-bytes" &&
                    domain["includes"] == listOf(
                        "javascript-after-source-mapping-url-rewrite",
                        "source-map-v3-bytes",
                    ) &&
                    domain["excludes"] == listOf(
                        "diagnostics-json",
                        "typed-result-json-overhead",
                        "result-framing-and-base64-overhead",
                        "compilation-manifest-json",
                        "zip-metadata-and-container-bytes",
                    ) &&
                    domain["packagedArchiveQuotaOwner"] ==
                    "R2-M3-S2-output-archive-schema" &&
                    domain["packagedArchiveDiagnosticsShapeDependency"] ==
                    "R2-M3-S3-diagnostics-schema"
            ) {
                "R2-M3-D2 final packaged-artifact counting domain drifted."
            }

            @Suppress("UNCHECKED_CAST")
            val boundaries = fixture["boundaryCases"] as List<Map<String, Any?>>
            val expectedBoundaries = linkedMapOf(
                "packaged-file-count" to 512L,
                "packaged-javascript-bytes" to 512L * 1024L,
                "packaged-source-map-bytes" to 512L * 1024L,
                "packaged-payload-bytes" to 1024L * 1024L,
            )
            check(
                boundaries.map { boundary -> boundary["dimension"] } ==
                    expectedBoundaries.keys.toList() &&
                    boundaries.all { boundary ->
                        val limit = expectedBoundaries.getValue(boundary["dimension"] as String)
                        boundary.keys.toList() == listOf(
                            "dimension",
                            "atLimit",
                            "atLimitAccepted",
                            "plusOne",
                            "plusOneAccepted",
                        ) &&
                            (boundary["atLimit"] as Number).toLong() == limit &&
                            boundary["atLimitAccepted"] == true &&
                            (boundary["plusOne"] as Number).toLong() == limit + 1L &&
                            boundary["plusOneAccepted"] == false
                    }
            ) {
                "R2-M3-D2 inclusive/+1 boundary matrix drifted."
            }

            @Suppress("UNCHECKED_CAST")
            val deferred = fixture["separateDeferredGuardrails"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val diagnostics = deferred["diagnostics"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val resultEnvelope = deferred["internalResultEnvelope"] as Map<String, Any?>
            check(
                deferred.keys.toList() == listOf("diagnostics", "internalResultEnvelope") &&
                    (diagnostics["maximumDiagnostics"] as Number).toInt() == 1_000 &&
                    (diagnostics["maximumEncodedArrayBytes"] as Number).toLong() ==
                    512L * 1024L &&
                    diagnostics["publicD2Quota"] == false &&
                    diagnostics["freezeDeferredTo"] == "R2-M3-S3-diagnostics-schema" &&
                    (resultEnvelope["maximumDecodedTypedResultBytes"] as Number).toLong() ==
                    6L * 1024L * 1024L &&
                    resultEnvelope["publicD2Quota"] == false &&
                    resultEnvelope["freezeDeferredTo"] == "R2-M3-O1-streaming-output"
            ) {
                "R2-M3-D2 diagnostics/result guardrails were conflated with Public D2 quota."
            }

            @Suppress("UNCHECKED_CAST")
            val workloads = fixture["measuredWorkloads"] as List<Map<String, Any?>>
            val b2k = workloads.single { workload ->
                workload["id"] == "b2k-historical-pre-c8-external-emit"
            }
            val b2j = workloads.single { workload ->
                workload["id"] == "b2j-direct-natural-emit"
            }
            val b2g = workloads.single { workload ->
                workload["id"] == "b2g-direct-natural-phase"
            }
            check(
                workloads.map { workload -> workload["id"] } == listOf(
                    "b2k-historical-pre-c8-external-emit",
                    "b2j-direct-natural-emit",
                    "b2g-direct-natural-phase",
                ) &&
                    b2k["evidenceClass"] ==
                    "historical-device-pre-c8-declaration-workload" &&
                    (b2k["rawWorkerFiles"] as Number).toInt() == 512 &&
                    (b2k["rawWorkerBytes"] as Number).toLong() == 976_438L &&
                    (b2k["packagedFiles"] as Number).toInt() == 512 &&
                    (b2k["packagedPayloadBytes"] as Number).toLong() == 978_486L &&
                    (b2k["packagerRewriteDeltaBytes"] as Number).toLong() == 2_048L &&
                    (b2k["packagedPayloadHeadroomBytes"] as Number).toLong() == 70_090L &&
                    b2k["withinOrdinaryPackagedPayloadQuota"] == true &&
                    b2k["closesCurrentBinaryReleaseAcceptance"] == false &&
                    b2j["evidenceClass"] ==
                    "historical-device-pre-current-egress-boundary" &&
                    (b2j["rawWorkerFiles"] as Number).toInt() == 16 &&
                    (b2j["rawWorkerBytes"] as Number).toLong() == 965_714L &&
                    b2g["evidenceClass"] == "historical-device-provisional-workload" &&
                    (b2g["rawWorkerFiles"] as Number).toInt() == 384 &&
                    (b2g["rawWorkerBytes"] as Number).toLong() == 562_945L &&
                    workloads.drop(1).all { workload ->
                        workload["packagedFiles"] == null &&
                            workload["packagedPayloadBytes"] == null &&
                            workload["withinOrdinaryPackagedPayloadQuota"] == null &&
                            workload["closesCurrentBinaryReleaseAcceptance"] == false
                    }
            ) {
                "R2-M3-D2 raw Worker and final packaged workload evidence was conflated."
            }

            @Suppress("UNCHECKED_CAST")
            val legacy = fixture["legacyTransportAndArchiveCeiling"] as Map<String, Any?>
            check(
                legacy["authority"] ==
                    "v1-transport-zip-validation-and-schema-ceiling-only" &&
                    (legacy["maximumArchiveFiles"] as Number).toInt() == 8_192 &&
                    (legacy["maximumArchiveContainerBytes"] as Number).toLong() ==
                    128L * 1024L * 1024L &&
                    (legacy["maximumArchiveExpandedBytes"] as Number).toLong() ==
                    256L * 1024L * 1024L &&
                    (legacy["maximumSingleJavaScriptBytes"] as Number).toLong() ==
                    16L * 1024L * 1024L &&
                    (legacy["maximumSingleSourceMapBytes"] as Number).toLong() ==
                    32L * 1024L * 1024L &&
                    legacy["promotesOrdinaryCompilationOutputQuota"] == false &&
                    legacy["definesOrdinaryPackagedArchiveQuota"] == false
            ) {
                "R2-M3-D2 legacy transport/ZIP ceiling was promoted into ordinary capacity."
            }

            @Suppress("UNCHECKED_CAST")
            val failure = fixture["failureOwnership"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val promotion = fixture["promotionState"] as Map<String, Any?>
            check(
                failure["workerOrHostQuotaFailureCode"] ==
                    "ERR_AUTOJS6_TYPESCRIPT_COMPILER_UNAVAILABLE" &&
                    failure["nativeFinalPackagedQuotaFailureCode"] ==
                    "ERR_AUTOJS6_TYPESCRIPT_OUTPUT_TRANSPORT" &&
                    failure["unifiedPublicQuotaFailureDeferredTo"] == listOf(
                        "R2-M3-O1-streaming-output",
                        "R2-M3-S2-output-archive-schema",
                    ) &&
                    (failure["diagnosticCount"] as Number).toInt() == 0 &&
                    (failure["callerOutputBytes"] as Number).toLong() == 0L &&
                    failure["automaticRetryAllowed"] == false &&
                    promotion.keys.toList() == listOf(
                        "publicCapabilityChanged",
                        "runtimeCapabilityChanged",
                        "nodeRevisionTwoEndToEndChanged",
                        "deviceEvidenceRefreshed",
                    ) &&
                    promotion.values.all { value -> value == false }
            ) {
                "R2-M3-D2 failure ownership or non-promotion state drifted."
            }

            val apiSource = r2ApiContractSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "public final packaged-output authority",
                source = apiSource + "\n" + r2ApiContractTestSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "const val ORDINARY_COMPILATION_OUTPUT_QUOTA_REVISION = 1",
                    "const val MAX_ORDINARY_COMPILATION_OUTPUT_FILES = 512",
                    "const val MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES = " +
                        "512L * 1024L",
                    "const val MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES = " +
                        "512L * 1024L",
                    "const val MAX_ORDINARY_COMPILATION_OUTPUT_BYTES = 1024L * 1024L",
                    "after the private",
                    "packager's canonical `sourceMappingURL` rewrite",
                    "ordinaryCompilationOutputQuotaIsExactRevisionOneAndNotTheTransportCeiling",
                ),
            )

            val nativeSource = r2OrdinaryOutputQuotaSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "native ordinary output authority",
                source = nativeSource,
                markers = listOf(
                    "internal object R2OrdinaryOutputQuota",
                    "fun admission(",
                    "R2OrdinaryOutputQuotaDimension.FILE_COUNT",
                    "R2OrdinaryOutputQuotaDimension.JAVASCRIPT_BYTES",
                    "R2OrdinaryOutputQuotaDimension.SOURCE_MAP_BYTES",
                    "R2OrdinaryOutputQuotaDimension.TOTAL_BYTES",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES",
                    "saturatedAdd",
                ),
            )
            requireMarkers(
                label = "native inclusive/+1 JVM tests",
                source = r2OrdinaryOutputQuotaTestSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "class R2OrdinaryOutputQuotaTest",
                    "exactAggregateBoundaryIsInclusiveAcrossFinalPackagedArtifacts",
                    "aggregatePlusOneIsRejectedAfterPerArtifactChecksPass",
                    "countAndPerKindPlusOneHaveStableDimensions",
                    "negativeOrOverflowingMeasurementsFailClosed",
                ),
            )

            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "Worker conservative early guardrail",
                source = workerSource,
                markers = listOf(
                    "const maximumOutputFiles = 512;",
                    "const maximumOutputBytes = 1024 * 1024;",
                    "const maximumJavaScriptBytes = 512 * 1024;",
                    "const maximumSourceMapBytes = 512 * 1024;",
                    "outputBytes > maximumFileBytes",
                    "outputBudget.bytes + outputBytes > maximumOutputBytes",
                    "throw new Error(\"TypeScript output exceeds the byte limit.\");",
                    "postProjectError(request.requestId, error);",
                ),
            )
            requireMarkers(
                label = "native raw-result early guardrail",
                source = r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES",
                    "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES",
                    "Project compiler output exceeds its per-file byte limit.",
                    "Project compiler output exceeds its total byte limit.",
                    "private const val MAX_FRAMED_PROJECT_RESULT_BYTES = 6 * 1024 * 1024",
                    "TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT",
                    "TypeScriptR2Contract.MAX_DIAGNOSTICS_DOCUMENT_BYTES",
                ),
            )

            val webViewHostSource = compilerWebViewHostSource.asFile.readText(Charsets.UTF_8)
            val workerFailureStart = webViewHostSource.indexOf(
                "private fun handleWorkerFailureOnMain(",
            )
            val workerFailureEnd = webViewHostSource.indexOf(
                "private fun stopProjectOnMain(",
                workerFailureStart,
            )
            check(workerFailureStart >= 0 && workerFailureEnd > workerFailureStart) {
                "R2-M3-D2 could not isolate Worker failure ownership."
            }
            requireMarkers(
                label = "Worker/host quota failure ownership",
                source = webViewHostSource.substring(workerFailureStart, workerFailureEnd),
                markers = listOf(
                    "code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE",
                    "Compiler Worker failed:",
                ),
            )

            val packagerSource = r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "post-rewrite packager enforcement",
                source = packagerSource,
                markers = listOf(
                    "payloadEntries.sortBy(OutputEntry::path)",
                    "requireStrictOutputInventory(payloadEntries)",
                    "requireOrdinaryEmittedArtifactQuota(payloadEntries)",
                    "val files = payloadEntries.map(OutputEntry::descriptor)",
                    "R2OrdinaryOutputQuota.admission(artifacts)",
                    "Private output exceeds the ordinary compilation limit:",
                    "contractErrorCode = TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT",
                ),
            )
            val inventoryIndex = packagerSource.indexOf("requireStrictOutputInventory(payloadEntries)")
            val quotaIndex = packagerSource.indexOf("requireOrdinaryEmittedArtifactQuota(payloadEntries)")
            val manifestIndex = packagerSource.indexOf("val files = payloadEntries.map", quotaIndex)
            check(inventoryIndex >= 0 && quotaIndex > inventoryIndex && manifestIndex > quotaIndex) {
                "R2-M3-D2 final quota must run after rewrites/inventory and before manifest creation."
            }
            requireMarkers(
                label = "packager boundary integration tests",
                source = r2PrivateOutputQuotaIntegrationTestSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "class R2PrivateOutputQuotaIntegrationTest",
                    "exactFinalPackagedBoundaryIsAcceptedAfterRewrite",
                    "rewriteThatPushesJavaScriptPastPerArtifactLimitFailsBeforeStaging",
                    "rewriteThatPushesAggregatePastLimitFailsBeforeStaging",
                    "nestedRewriteExpansionIsMeasuredByTheFinalQuotaAuthority",
                    "TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT",
                    "R2OrdinaryOutputQuotaDimension.JAVASCRIPT_BYTES",
                    "failure.message.orEmpty().contains(\"total_bytes\")",
                    "assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())",
                ),
            )

            val coordinatorSource =
                r2CompilationCoordinatorSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "final packaged quota failure ownership and empty caller output",
                source = coordinatorSource,
                markers = listOf(
                    "finalizeFailure(job, error.toPipelineFailure())",
                    "is R2OutputArchiveException -> R2PipelineFailure(",
                    "errorCode = contractErrorCode",
                    "private fun finalizeFailure(",
                    "R2OutputFdCommitter.clear(output)",
                    "errorCode = failure.errorCode",
                    "diagnosticCount = 0",
                    "emittedModuleCount = 0",
                    "outputManifestSha256 = null",
                ),
            )
            val outputArchiveFailureIndex = coordinatorSource.indexOf(
                "is R2OutputArchiveException -> R2PipelineFailure(",
            )
            val fallbackFailureIndex = coordinatorSource.indexOf(
                "else -> R2PipelineFailure(",
                outputArchiveFailureIndex,
            )
            val finalizeFailureStart = coordinatorSource.indexOf("private fun finalizeFailure(")
            val finalizeFailureEnd = coordinatorSource.indexOf(
                "private fun finalizeFailureAfterOutputConsumed(",
                finalizeFailureStart,
            )
            check(
                outputArchiveFailureIndex >= 0 &&
                    fallbackFailureIndex > outputArchiveFailureIndex &&
                    "errorCode = contractErrorCode" in coordinatorSource.substring(
                        outputArchiveFailureIndex,
                        fallbackFailureIndex,
                    ) &&
                    finalizeFailureStart >= 0 &&
                    finalizeFailureEnd > finalizeFailureStart &&
                    "R2OutputFdCommitter.clear(output)" in coordinatorSource.substring(
                        finalizeFailureStart,
                        finalizeFailureEnd,
                    )
            ) {
                "R2-M3-D2 packaged quota failure must preserve its transport code and clear output."
            }

            val fixtureTestSource =
                r2OrdinaryOutputQuotaDecisionFixtureTestSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "strict decision fixture JVM coverage",
                source = fixtureTestSource,
                markers = listOf(
                    "class R2OrdinaryOutputQuotaDecisionFixtureTest",
                    "fixtureFreezesFinalPackagedArtifactQuotaAgainstPublicAuthority",
                    "finalPackagedDomainKeepsDiagnosticsResultAndZipGuardrailsSeparate",
                    "measuredWorkloadsDistinguishRawWorkerFromFinalPackagedPayload",
                    "b2k-historical-pre-c8-external-emit",
                    "packagedPayloadHeadroomBytes",
                    "closesCurrentBinaryReleaseAcceptance",
                ),
            )

            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2-M3-D2")

            val buildScriptSource = r2M2P3BuildScript.asFile.readText(Charsets.UTF_8)
            val sourcePolicyStart = buildScriptSource.indexOf(
                "tasks.register(\"verifyR2PublicPreviewSourcePolicy\")",
            )
            val sourcePolicyEnd = buildScriptSource.indexOf("inputs.files(", sourcePolicyStart)
            check(sourcePolicyStart >= 0 && sourcePolicyEnd > sourcePolicyStart) {
                "R2-M3-D2 could not isolate the public source-policy task description."
            }
            val publicSourcePolicy = buildScriptSource.substring(sourcePolicyStart, sourcePolicyEnd)
            check(
                "complete M3-S2" in publicSourcePolicy &&
                    "complete M3-D3" !in publicSourcePolicy &&
                    "complete M3-D2" !in publicSourcePolicy
            ) {
                "R2-M3-D2 public source-policy description is stale; current must be M3-S2."
            }

            logger.lifecycle(
                "R2-M3-D2 ordinary output quota fixture verified: final packaged JS/maps=" +
                    "512 files, 512 KiB per artifact, 1 MiB aggregate inclusive; " +
                    "pre-C8 b2k raw=976438 vs packaged=978486 with 70090-byte headroom; " +
                    "diagnostics/result/ZIP domains remain separate; compiler capability mask=15",
            )
        }
    }

val verifyR2M3D2OrdinaryOutputQuotaDecision =
    tasks.register("verifyR2M3D2OrdinaryOutputQuotaDecision") {
        group = "verification"
        description =
            "Closes R2-M3-D2 through its canonical fixture, public/native/Worker/host/" +
                "post-rewrite packager JVM/source gates, complete D1 dependency, and AndroidTest APK."

        dependsOn(verifyR2M3D1OrdinaryInputQuotaDecision)
        dependsOn(verifyR2M3D2OrdinaryOutputQuotaFixture)
        dependsOn("verifyR2PublicPreviewSourcePolicy")
        dependsOn("assembleDebugAndroidTest")

        doLast {
            logger.lifecycle(
                "R2-M3-D2 ordinary output quota decision verified: final packaged JS/maps " +
                    "512 files / 512 KiB each / 1 MiB aggregate; diagnostics/result/archive " +
                    "ceilings remain separate and no Public capability was promoted",
            )
        }
    }

val verifyR2M3D3CapabilityPromotionCompatibilityFixture =
    tasks.register("verifyR2M3D3CapabilityPromotionCompatibilityFixture") {
        group = "verification"
        description =
            "Freezes the R2-M3-D3 revision-1 capability dependency matrix, strict metadata " +
                "compatibility, legacy-disabled exception, host route closure, and current mask 15."

        dependsOn("testDebugUnitTest")
        dependsOn(":plugin-api:typescript-api:testDebugUnitTest")
        inputs.files(
            r2M2P3BuildScript,
            r2CapabilityPromotionCompatibilityDecisionFixture,
            r2CapabilityPromotionCompatibilityDecisionFixtureTestSource,
            r2ApiContractSource,
            r2ApiContractTestSource,
            r2PublicPreviewMetadataSource,
        )

        doLast {
            fun requireMarkers(label: String, source: String, markers: List<String>) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-M3-D3 $label marker is missing: $marker"
                    }
                }
            }

            val fixtureSource =
                r2CapabilityPromotionCompatibilityDecisionFixture.asFile
                    .readText(Charsets.UTF_8)
            requireMarkers(
                label = "canonical compatibility fixture",
                source = fixtureSource,
                markers = listOf(
                    "autojs6.typescript.r2-m3-d3.capability-promotion-compatibility-decision",
                    "dependency-ordered-independent-promotion",
                    "capabilityPromotionCompatibilityRevision",
                    "four-compiler-capabilities-explicit-false-node-absent-or-explicit-false",
                    "ignore-without-authority",
                    "fail-closed-new-compatibility-revision-required",
                    "\"deviceEvidenceRefreshed\": false",
                ),
            )
            val compatibleMatrixRows = Regex(
                """\{\s*"mask":\s*(\d+),[^}]*"compatible":\s*true\s*}""",
                RegexOption.DOT_MATCHES_ALL,
            ).findAll(fixtureSource).map { match -> match.groupValues[1].toInt() }.toList()
            check(compatibleMatrixRows == listOf(0, 1, 3, 7, 11, 15)) {
                "R2-M3-D3 compatible mask set drifted: $compatibleMatrixRows"
            }
            val matrixRows = Regex(
                """\{\s*"mask":\s*(\d+),\s*"C":\s*(true|false),\s*"P":\s*(true|false),\s*"D":\s*(true|false),\s*"M":\s*(true|false),\s*"compatible":\s*(true|false)\s*}""",
            ).findAll(fixtureSource).toList()
            check(matrixRows.size == 16) {
                "R2-M3-D3 fixture must contain exactly 16 explicit mask rows."
            }
            matrixRows.forEachIndexed { expectedMask, match ->
                val mask = match.groupValues[1].toInt()
                check(mask == expectedMask) {
                    "R2-M3-D3 mask rows must be ordered 0 through 15."
                }
                listOf(1, 2, 4, 8).forEachIndexed { index, bit ->
                    check((match.groupValues[index + 2] == "true") == (mask and bit != 0)) {
                        "R2-M3-D3 mask $mask Boolean/bit mapping drifted."
                    }
                }
            }

            val fixtureTestSource =
                r2CapabilityPromotionCompatibilityDecisionFixtureTestSource.asFile
                    .readText(Charsets.UTF_8)
            requireMarkers(
                label = "strict decision fixture JVM coverage",
                source = fixtureTestSource,
                markers = listOf(
                    "class R2CapabilityPromotionCompatibilityDecisionFixtureTest",
                    "fixtureFreezesExactSixteenStateDependencyMatrix",
                    "legacyAllFalseWithoutRevisionRemainsDormantAndCompatible",
                    "missingWrongTypedOrUnknownRevisionFailsClosedWithoutCapabilityInference",
                    "hostRoutesRequireExactCapabilityClosure",
                    "nodeRuntimeDependencyRemainsSeparateAndCannotPromoteCompilerCapabilities",
                    "!metadata.containsKey(REVISION_KEY)",
                    "listOf<Any?>(0, -1, 2, 1L, \"1\", true, null)",
                ),
            )

            val apiSource = r2ApiContractSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "additive Public API authority",
                source = apiSource,
                markers = listOf(
                    "KEY_CAPABILITY_PROMOTION_COMPATIBILITY_REVISION",
                    "CAPABILITY_PROMOTION_COMPATIBILITY_REVISION = 1",
                    "CAPABILITY_MASK_COMPILER_AVAILABLE = 1",
                    "CAPABILITY_MASK_PROJECT_COMPILATION = 2",
                    "CAPABILITY_MASK_SEMANTIC_DIAGNOSTICS = 4",
                    "CAPABILITY_MASK_SOURCE_MAP_V3 = 8",
                    "fun capabilityPromotionMask(",
                    "fun isCapabilityPromotionStructurallyCompatible(",
                    "nodeRuntimeRequired",
                ),
            )
            val apiTestSource = r2ApiContractTestSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "Public API exact-six JVM coverage",
                source = apiTestSource,
                markers = listOf(
                    "capabilityPromotionCompatibilityRevisionAndKeyAreStable",
                    "revisionOneCapabilityPromotionCompatibilityAcceptsExactlySixMasks",
                    "setOf(0, 1, 3, 7, 11, 15)",
                    "unknownCapabilityRevisionAndNodeRuntimeDependencyFailClosed",
                ),
            )

            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "current producer advertisement",
                source = metadataSource,
                markers = listOf(
                    "val compatibilityRevision =",
                    "TypeScriptR2Contract.CAPABILITY_PROMOTION_COMPATIBILITY_REVISION",
                    "ADVERTISED_COMPILER_AVAILABLE = true",
                    "ADVERTISED_PROJECT_COMPILATION = true",
                    "ADVERTISED_SEMANTIC_DIAGNOSTICS = true",
                    "ADVERTISED_SOURCE_MAP_V3 = true",
                    "ADVERTISED_NODE_RUNTIME_REQUIRED = false",
                    "val compilerAvailable = ADVERTISED_COMPILER_AVAILABLE",
                    "val nodeRuntimeRequired = ADVERTISED_NODE_RUNTIME_REQUIRED",
                    "TypeScriptR2Contract.isCapabilityPromotionStructurallyCompatible(",
                    "TypeScriptR2Contract.KEY_CAPABILITY_PROMOTION_COMPATIBILITY_REVISION",
                    "putBoolean(TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE, compilerAvailable)",
                    "putBoolean(TypeScriptPluginCapabilityKeys.NODE_RUNTIME_REQUIRED, nodeRuntimeRequired)",
                ),
            )

            val buildScriptSource = r2M2P3BuildScript.asFile.readText(Charsets.UTF_8)
            val sourcePolicyStart = buildScriptSource.indexOf(
                "tasks.register(\"verifyR2PublicPreviewSourcePolicy\")",
            )
            val sourcePolicyEnd = buildScriptSource.indexOf("inputs.files(", sourcePolicyStart)
            check(sourcePolicyStart >= 0 && sourcePolicyEnd > sourcePolicyStart) {
                "R2-M3-D3 could not isolate the public source-policy task description."
            }
            val publicSourcePolicy = buildScriptSource.substring(sourcePolicyStart, sourcePolicyEnd)
            check(
                "complete M3-S2" in publicSourcePolicy &&
                    "complete M3-D3" !in publicSourcePolicy &&
                    "complete M3-D2" !in publicSourcePolicy
            ) {
                "R2-M3-D3 public source-policy description is stale; current must be M3-S2."
            }

            logger.lifecycle(
                "R2-M3-D3 capability promotion compatibility fixture verified: revision=1, " +
                    "masks=0/1/3/7/11/15, legacy=explicit-all-false discovery-only, " +
                    "Node runtime=false and current compiler capability mask=15",
            )
        }
    }

val verifyR2M3D3CapabilityPromotionCompatibilityDecision =
    tasks.register("verifyR2M3D3CapabilityPromotionCompatibilityDecision") {
        group = "verification"
        description =
            "Closes R2-M3-D3 through canonical/API/JVM/source compatibility gates, complete " +
                "D2 dependency, frozen API, compiler-host APKs, and optional-plugin independence."

        dependsOn(verifyR2M3D2OrdinaryOutputQuotaDecision)
        dependsOn(verifyR2M3D3CapabilityPromotionCompatibilityFixture)
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        dependsOn("verifyR2PublicPreviewSourcePolicy")
        dependsOn("verifyDebugCompilerHostApk")
        dependsOn("verifyReleaseCompilerHostApk")
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn("assembleDebugAndroidTest")

        doLast {
            logger.lifecycle(
                "R2-M3-D3 capability promotion compatibility decision verified: exact-six " +
                    "dependency shapes, strict revision/Boolean handling, legacy-disabled " +
                    "discovery only, exact route closure, and no capability promotion",
            )
        }
    }

val verifyR2M3S1InputArchiveSchemaV1Fixture =
    tasks.register("verifyR2M3S1InputArchiveSchemaV1Fixture") {
        group = "verification"
        description =
            "Freezes the R2-M3-S1 input-archive schema revision 1, its exact eight-shape " +
                "matrix, caller/trusted inventory authority, and D1 quota/transport split."

        dependsOn(verifyR2DeclarationDistribution)
        dependsOn("testDebugUnitTest")
        dependsOn(":plugin-api:typescript-api:testDebugUnitTest")
        inputs.files(
            r2M2P3BuildScript,
            r2InputArchiveSchemaV1Fixture,
            r2InputArchiveSchemaV1FixtureTestSource,
            r2ApiContractSource,
            r2ApiContractTestSource,
            r2InputArchiveSchemaV1Source,
            r2ProjectArchiveSource,
            r2ProjectArchiveTestSource,
            r2ProjectArchiveLoaderTestSource,
            r2ProjectSnapshotSource,
            r2ProjectSnapshotTestSource,
            r2FileBackedProjectSnapshotSource,
            r2FileBackedProjectSnapshotTestSource,
            r2ProjectFramedTransportSource,
            r2ProjectFramedTransportTestSource,
            r2CompilationProbeProtocolTestSource,
            r2TrustedDeclarationsSource,
            r2DeclarationDistributionSource,
            r2CompilerWorkerSource,
            r2ProjectCompilerDesktopHarnessSource,
            compilerWebViewHostSource,
            r2PublicPreviewServiceSource,
            r2PublicPreviewMetadataSource,
            r2ZipArchiveValidatorSource,
            r2OrdinaryInputQuotaDecisionFixture,
            r2OrdinaryInputQuotaDecisionFixtureTestSource,
        )
        inputs.dir(generatedR2DeclarationDistributionDirectory)

        doLast {
            fun requireMarkers(label: String, source: String, markers: List<String>) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-M3-S1 $label marker is missing: $marker"
                    }
                }
            }

            @Suppress("UNCHECKED_CAST")
            val fixture = JsonSlurper().parse(r2InputArchiveSchemaV1Fixture.asFile) as
                Map<String, Any?>
            check(
                fixture.keys.toList() == listOf(
                    "schema",
                    "revision",
                    "decision",
                    "publicContract",
                    "manifestShape",
                    "conditionalMatrix",
                    "rejectedConditionalCombinations",
                    "callerArchiveDomains",
                    "trustedDeclarationDomain",
                    "logicalInventory",
                    "hashAuthority",
                    "ordinaryQuota",
                    "transportCeiling",
                    "failurePolicy",
                    "promotionState",
                ) &&
                    fixture["schema"] ==
                    "autojs6.typescript.r2-m3-s1.input-archive-schema" &&
                    (fixture["revision"] as Number).toInt() == 1 &&
                    fixture["decision"] ==
                    "single-formal-revision-one-with-domain-separated-authority"
            ) {
                "R2-M3-S1 fixture identity or exact root shape drifted."
            }

            @Suppress("UNCHECKED_CAST")
            val publicContract = fixture["publicContract"] as Map<String, Any?>
            check(
                publicContract["schemaRevisionKey"] == "inputArchiveSchemaRevision" &&
                    (publicContract["schemaRevision"] as Number).toInt() == 1 &&
                    publicContract["schemaName"] == "autojs6.typescript.input-archive" &&
                    publicContract["manifestPath"] == "request-manifest.json" &&
                    publicContract["declarationProfileFingerprintKey"] ==
                    "declarationProfileFingerprint" &&
                    publicContract["acceptedSchemaRevisions"] == listOf(1) &&
                    publicContract["rejectedLegacyManifestRevisions"] ==
                    (2..7).toList() &&
                    publicContract["legacyRevisionPolicy"] ==
                    "reject-not-formal-v1-alias" &&
                    publicContract["unknownRevisionPolicy"] == "fail-closed"
            ) {
                "R2-M3-S1 public schema identity or revision rejection drifted."
            }

            @Suppress("UNCHECKED_CAST")
            val shape = fixture["manifestShape"] as Map<String, Any?>
            val baseKeys = listOf(
                "schema",
                "revision",
                "inputQuotaRevision",
                "contractVersion",
                "contractHash",
                "inputArchiveTransportVersion",
                "requestId",
                "projectId",
                "entrySourceUri",
                "entryPath",
                "targetProfile",
                "compilationMode",
                "profileSchemaRevision",
                "profileRevision",
                "runtimeCapabilitySchemaRevision",
                "configSchemaRevision",
                "runtimeCapabilitiesFingerprint",
                "compilerPackage",
                "compilerVersion",
                "compilerDistributionFingerprint",
                "declarationsFingerprint",
                "declarationProfileFingerprint",
                "configKind",
                "callerInventoryFingerprint",
                "rootPaths",
                "files",
            )
            check(
                shape["baseTopLevelKeys"] == baseKeys &&
                    shape["sourceFileDescriptorKeys"] == listOf("path", "sha256", "size") &&
                    shape["conditionalKeys"] ==
                    listOf("config", "packageMetadata", "packageTargets") &&
                    shape["absentConditionalEncoding"] == "key-absent" &&
                    shape["unknownFieldPolicy"] == "fail-closed" &&
                    shape["missingFieldPolicy"] == "fail-closed"
            ) {
                "R2-M3-S1 manifest exact-key authority drifted."
            }
            val archiveSchemaSource = r2ProjectArchiveSource.asFile.readText(Charsets.UTF_8)
            fun exactStringSet(name: String): List<String> {
                val match = Regex(
                    """private val $name = setOf\((.*?)\)\s*private val""",
                    RegexOption.DOT_MATCHES_ALL,
                ).find(archiveSchemaSource)
                check(match != null) {
                    "R2-M3-S1 could not isolate production $name."
                }
                return Regex(""""([^"]+)"""")
                    .findAll(match.groupValues[1])
                    .map { value -> value.groupValues[1] }
                    .toList()
            }
            check(
                exactStringSet("FILE_KEYS") == shape["sourceFileDescriptorKeys"] &&
                    exactStringSet("CONFIG_KEYS") == shape["configKeys"] &&
                    exactStringSet("PACKAGE_METADATA_KEYS") == shape["packageMetadataKeys"] &&
                    exactStringSet("PACKAGE_TARGET_KEYS") == shape["packageTargetKeys"] &&
                    exactStringSet("TOP_LEVEL_KEYS") == baseKeys
            ) {
                "R2-M3-S1 fixture and production manifest key sets diverged."
            }
            requireMarkers(
                label = "conditional exact-key derivation",
                source = archiveSchemaSource,
                markers = listOf(
                    "TOP_LEVEL_KEYS_WITH_CONFIG = TOP_LEVEL_KEYS + \"config\"",
                    "TOP_LEVEL_KEYS + \"packageMetadata\"",
                    "TOP_LEVEL_KEYS_WITH_PACKAGE_METADATA + \"packageTargets\"",
                    "TOP_LEVEL_KEYS_WITH_CONFIG + \"packageMetadata\"",
                    "TOP_LEVEL_KEYS_WITH_CONFIG_AND_PACKAGE_METADATA + \"packageTargets\"",
                ),
            )

            @Suppress("UNCHECKED_CAST")
            val rows = fixture["conditionalMatrix"] as List<Map<String, Any?>>
            val expectedRows = linkedMapOf(
                "rhino-r1-none" to emptyList<String>(),
                "rhino-r1-tsconfig" to listOf("config"),
                "rhino-r1-tsconfig-metadata" to listOf("config", "packageMetadata"),
                "rhino-r1-tsconfig-metadata-target-v1" to
                    listOf("config", "packageMetadata", "packageTargets"),
                "node-r1-none" to emptyList(),
                "node-r2-none" to emptyList(),
                "node-r2-metadata" to listOf("packageMetadata"),
                "node-r2-metadata-target-v2" to
                    listOf("packageMetadata", "packageTargets"),
            )
            check(
                rows.size == 8 &&
                    rows.map { row -> row["id"] } == expectedRows.keys.toList() &&
                    rows.all { row ->
                        row["exactConditionalKeys"] ==
                            expectedRows.getValue(row["id"] as String)
                    }
            ) {
                "R2-M3-S1 exact eight-shape conditional matrix drifted."
            }

            @Suppress("UNCHECKED_CAST")
            val caller = fixture["callerArchiveDomains"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val declarations = fixture["trustedDeclarationDomain"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val inventory = fixture["logicalInventory"] as Map<String, Any?>
            check(
                caller["payloadDomains"] == listOf(
                    "source",
                    "config",
                    "package-metadata",
                    "package-target-v1",
                    "package-target-v2",
                ) &&
                    caller["callerDeclarationPathsAllowed"] == false &&
                    caller["manifestIncludedInPayloadInventory"] == false &&
                    declarations["physicallyPresentInCallerArchive"] == false &&
                    declarations["injectedAfterCallerArchiveValidation"] == true &&
                    inventory["canonicalPrefix"] ==
                    "autojs6.typescript.input-inventory.v1\n" &&
                    inventory["canonicalDomainOrder"] == listOf(
                    "source",
                    "declaration",
                    "config",
                    "package-metadata",
                    "package-target-v1",
                    "package-target-v2",
                ) &&
                    inventory["canonicalRecord"] ==
                    "domain-nul-path-nul-size-nul-lower-sha256-newline"
            ) {
                "R2-M3-S1 caller/trusted inventory separation drifted."
            }

            @Suppress("UNCHECKED_CAST")
            val hashes = fixture["hashAuthority"] as List<Map<String, Any?>>
            check(
                hashes.map { hash -> hash["name"] } == listOf(
                    "archiveSha256",
                    "requestManifestSha256",
                    "callerInventoryFingerprint",
                    "declarationProfileFingerprint",
                    "declarationInventoryFingerprint",
                    "retainedInventoryFingerprint",
                ) && hashes.all { hash -> hash["recomputed"] == true }
            ) {
                "R2-M3-S1 archive/manifest/inventory hash authority drifted."
            }

            @Suppress("UNCHECKED_CAST")
            val quota = fixture["ordinaryQuota"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val transport = fixture["transportCeiling"] as Map<String, Any?>
            check(
                (quota["inputQuotaRevision"] as Number).toInt() == 1 &&
                    (quota["maximumFiles"] as Number).toInt() == 512 &&
                    (quota["maximumSingleFileBytes"] as Number).toLong() ==
                    8L * 1024L * 1024L &&
                    (quota["maximumTotalBytes"] as Number).toLong() ==
                    8L * 1024L * 1024L &&
                    quota["admissionPoint"] == "before-worker-handle-claim-and-framing" &&
                    (transport["maximumArchiveFiles"] as Number).toInt() == 4_096 &&
                    (transport["maximumArchiveContainerBytes"] as Number).toLong() ==
                    64L * 1024L * 1024L &&
                    (transport["privateFileBackedMaximumFiles"] as Number).toInt() == 4_607 &&
                    (transport["privateFileBackedMaximumTotalBytes"] as Number).toLong() ==
                    72L * 1024L * 1024L &&
                    (transport["testOnlyWorkerQuotaRevision"] as Number).toInt() ==
                    Int.MAX_VALUE &&
                    transport["testOnlySelectionAuthority"] ==
                    "trusted-debug-or-evidence-host-only" &&
                    transport["promotesFormalSchema"] == false &&
                    transport["promotesOrdinaryQuota"] == false
            ) {
                "R2-M3-S1 D1 quota and legacy/private transport ceilings were conflated."
            }

            @Suppress("UNCHECKED_CAST")
            val promotion = fixture["promotionState"] as Map<String, Any?>
            check(promotion.values.all { value -> value == false }) {
                "R2-M3-S1 must not promote Public/runtime/device evidence."
            }
            @Suppress("UNCHECKED_CAST")
            val failure = fixture["failurePolicy"] as Map<String, Any?>
            check(
                failure["outerMissingOrWrongType"] ==
                    "ERR_AUTOJS6_TYPESCRIPT_INVALID_REQUEST" &&
                    failure["outerUnknownSchemaOrProfile"] ==
                    "ERR_AUTOJS6_TYPESCRIPT_CONTRACT_MISMATCH" &&
                    failure["fdOwnershipTransferred"] == false &&
                    failure["archiveStagingStarted"] == false &&
                    failure["workerDispatchStarted"] == false &&
                    (failure["callerOutputBytes"] as Number).toLong() == 0L &&
                    failure["automaticRetryAllowed"] == false
            ) {
                "R2-M3-S1 pre-ownership fail-closed policy drifted."
            }

            requireMarkers(
                label = "public additive schema authority",
                source = r2ApiContractSource.asFile.readText(Charsets.UTF_8) + "\n" +
                    r2ApiContractTestSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "KEY_INPUT_ARCHIVE_SCHEMA_REVISION = \"inputArchiveSchemaRevision\"",
                    "INPUT_ARCHIVE_SCHEMA_REVISION = 1",
                    "INPUT_ARCHIVE_SCHEMA = \"autojs6.typescript.input-archive\"",
                    "INPUT_ARCHIVE_MANIFEST_PATH = \"request-manifest.json\"",
                    "KEY_DECLARATION_PROFILE_FINGERPRINT = \"declarationProfileFingerprint\"",
                    "inputArchiveSchemaV1IdentityIsStable",
                ),
            )
            requireMarkers(
                label = "canonical production owner",
                source = r2InputArchiveSchemaV1Source.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "internal object R2InputArchiveSchemaV1",
                    "autojs6.typescript.input-inventory.v1\\n",
                    "fun callerInventoryFingerprint(",
                    "fun acceptedAuthority(",
                    "internal data class R2AcceptedInputAuthorityV1(",
                    "val retainedInventoryFingerprint: String",
                    "val declarationInventoryFingerprint: String",
                    "val declarationFileCount: Int",
                    "val declarationByteLength: Long",
                    "val inputQuotaRevision: Int",
                    "val capacity: R2ProjectInputCapacity",
                ),
            )
            requireMarkers(
                label = "strict archive revision/shape and accepted authority",
                source = r2ProjectArchiveSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "root.requireExactKeys(",
                    "R2InputArchiveSchemaV1.callerInventoryFingerprint(",
                    "Request manifest caller inventory fingerprint does not match",
                    "R2InputArchiveSchemaV1.acceptedAuthority(",
                    "acceptedInputAuthority = acceptedInputAuthority",
                    "R2TrustedDeclarations.loadForProfile(context, authority.profile)",
                ),
            )
            requireMarkers(
                label = "accepted-authority framing",
                source = r2ProjectFramedTransportSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "internal enum class R2WorkerInputQuotaAuthority(",
                    "TEST_ONLY_EXPANDED(",
                    "revision = Int.MAX_VALUE",
                    "private val acceptedInputAuthority = snapshot.acceptedInputAuthority",
                    "Project-scoped transport requires frozen accepted-input authority.",
                    "acceptedInputAuthority.inputQuotaRevision ==",
                    "R2WorkerInputQuotaAuthority.ORDINARY.revision",
                    "\"inputArchiveSchemaRevision\" to acceptedInputAuthority.schemaRevision",
                    "\"inputQuotaRevision\" to workerInputQuotaAuthority.revision",
                    "\"callerInventoryFingerprint\" to",
                    "\"retainedInventoryFingerprint\" to",
                    "\"declarationProfileFingerprint\" to",
                    "\"declarationInventoryFingerprint\" to",
                    "\"declarationFileCount\" to",
                    "\"declarationByteLength\" to",
                    "\"largestFileBytes\" to",
                ),
            )
            requireMarkers(
                label = "Worker start and end-to-end recomputation",
                source = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "inventoryCanonicalPrefix: \"autojs6.typescript.input-inventory.v1\\n\"",
                    "const testOnlyExpandedInputQuotaAuthority = Object.freeze({",
                    "quotaRevision: 2_147_483_647",
                    "const workerInputQuotaAuthority =",
                    "const deriveInputInventoryFingerprint = async (records) =>",
                    "message.inputArchiveSchemaRevision !== inputArchiveAuthority.schemaRevision",
                    "deriveInputInventoryFingerprint(callerRecords)",
                    "deriveInputInventoryFingerprint(retainedRecords)",
                    "deriveInputInventoryFingerprint(declarationRecords)",
                    "callerInventoryFingerprint !== state.callerInventoryFingerprint",
                    "retainedInventoryFingerprint !== state.retainedInventoryFingerprint",
                    "declarationInventoryFingerprint !== state.declarationInventoryFingerprint",
                    "retainedRecords.length !== state.retainedFileCount",
                    "declarationRecords.length !== state.declarationFileCount",
                    "declarationBytes !== state.declarationByteLength",
                    "largestFileBytes !== state.largestFileBytes",
                ),
            )
            requireMarkers(
                label = "Worker accepted-authority tamper rejection matrix",
                source = r2ProjectCompilerDesktopHarnessSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "const verifyWorkerRejectsEveryTamperedAcceptedInputAuthorityField = async () =>",
                    "[\"callerInventoryFingerprint\", \"0\".repeat(64)]",
                    "[\"retainedInventoryFingerprint\", \"0\".repeat(64)]",
                    "[\"declarationInventoryFingerprint\", \"0\".repeat(64)]",
                    "[\"declarationFileCount\", 2]",
                    "[\"declarationByteLength\", 2]",
                    "[\"largestFileBytes\", 2]",
                    "Worker created a Program for tampered ${'$'}{field}.",
                    "Worker did not run exactly one clean successor after tampered ${'$'}{field}.",
                    "fields=6, clean successors=6, failure Program=0",
                    "S1 accepted-input authority tamper rejection and clean successors",
                    "await verifyWorkerRejectsEveryTamperedAcceptedInputAuthorityField();",
                ),
            )
            val hostSource = compilerWebViewHostSource.asFile.readText(Charsets.UTF_8)
            val admissionIndex = hostSource.indexOf(
                "val workerAdmission = R2ProjectSnapshot.ordinaryCompilationAdmission(",
            )
            val claimIndex = hostSource.indexOf("if (!handle.claim())", admissionIndex)
            val framingIndex = hostSource.indexOf("R2ProjectInputFramer(", admissionIndex)
            check(admissionIndex >= 0 && claimIndex > admissionIndex && framingIndex > claimIndex) {
                "R2-M3-S1 ordinary admission must precede handle claim and Worker framing."
            }
            requireMarkers(
                label = "trusted ordinary/test-only quota selection",
                source = hostSource + "\n" +
                    r2ProjectFramedTransportTestSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "workerInputQuotaAuthority = R2WorkerInputQuotaAuthority.TEST_ONLY_EXPANDED",
                    "workerAdmissionLimits == workerInputQuotaAuthority.limits",
                    "ordinaryAndTestOnlyExpandedWorkerQuotaAuthoritiesStaySeparated",
                    "projectScopedFramerRejectsMissingAcceptedInputAuthority",
                ),
            )
            requireMarkers(
                label = "exact debug profile declaration selection",
                source = r2PublicPreviewServiceSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "declarationProfileFingerprintOrNull(profileAuthority.profile)",
                    "R2TrustedDeclarations.loadForProfile(",
                ),
            )
            val serviceSource = r2PublicPreviewServiceSource.asFile.readText(Charsets.UTF_8)
            val serviceStart = serviceSource.indexOf("override fun startCompilation(")
            val debugAuthorityIndex = serviceSource.indexOf(
                "val debugR2FormalAuthorityError =",
                serviceStart,
            )
            val controlPlaneRouteIndex = serviceSource.indexOf(
                "val r2ControlPlaneRoute = request.r2CompilationControlPlaneRoute(",
                debugAuthorityIndex,
            )
            val publicAuthorityIndex = serviceSource.indexOf(
                "val publicR2Classification =",
                serviceStart,
            )
            val debugRejectionIndex = serviceSource.indexOf(
                "if (debugR2Requested && debugR2Controls == null)",
                publicAuthorityIndex,
            )
            val conflictRejectionIndex = serviceSource.indexOf(
                "if (r2ControlPlaneRoute == R2CompilationControlPlaneRoute.CONFLICT)",
                debugRejectionIndex,
            )
            val publicRejectionIndex = serviceSource.indexOf(
                "publicR2Requested &&",
                conflictRejectionIndex,
            )
            val coordinatorAdmissionIndex = serviceSource.indexOf(
                "coordinator.admit(",
                serviceStart,
            )
            val ownershipIndex = serviceSource.indexOf(
                "archiveOwnershipTransferred = admission.accepted",
                serviceStart,
            )
            check(
                serviceStart >= 0 &&
                    debugAuthorityIndex > serviceStart &&
                    controlPlaneRouteIndex > debugAuthorityIndex &&
                    publicAuthorityIndex > controlPlaneRouteIndex &&
                    debugRejectionIndex > publicAuthorityIndex &&
                    conflictRejectionIndex > debugRejectionIndex &&
                    publicRejectionIndex > conflictRejectionIndex &&
                    coordinatorAdmissionIndex > publicAuthorityIndex &&
                    ownershipIndex > coordinatorAdmissionIndex
            ) {
                "R2-M3-S1 formal authority must be classified before coordinator/FD ownership."
            }
            requireMarkers(
                label = "service-bound debug/public route classification",
                source = serviceSource.substring(
                    controlPlaneRouteIndex,
                    publicAuthorityIndex,
                ),
                markers = listOf(
                    "debugControlPlaneRequested = debugR2Requested",
                    "debugControlPlaneAccepted = debugR2Controls != null",
                    "r2ControlPlaneRoute == R2CompilationControlPlaneRoute.PUBLIC",
                ),
            )
            requireMarkers(
                label = "service-bound debug/public conflict rejection",
                source = serviceSource.substring(
                    conflictRejectionIndex,
                    publicRejectionIndex,
                ),
                markers = listOf(
                    "if (r2ControlPlaneRoute == R2CompilationControlPlaneRoute.CONFLICT)",
                    "errorCode = TypeScriptCompilerContract.ERROR_INVALID_REQUEST",
                    "Debug and public R2 control planes cannot be combined.",
                ),
            )
            val routeAdapterStart = serviceSource.indexOf(
                "private fun Bundle?.r2CompilationControlPlaneRoute(",
            )
            val routeAdapterEnd = serviceSource.indexOf(
                "private fun Bundle?.requestsR2ExternalCapacityEvidence()",
                routeAdapterStart,
            )
            check(routeAdapterStart >= 0 && routeAdapterEnd > routeAdapterStart) {
                "R2-M3-S1 could not isolate the Bundle-to-route adapter."
            }
            requireMarkers(
                label = "Bundle-to-route adapter",
                source = serviceSource.substring(routeAdapterStart, routeAdapterEnd),
                markers = listOf(
                    "debugControlPlaneRequested: Boolean",
                    "debugControlPlaneAccepted: Boolean",
                    "R2PublicCompilationProtocol.controlPlaneRoute(",
                    "debugControlPlaneRequested = debugControlPlaneRequested",
                    "debugControlPlaneAccepted = debugControlPlaneAccepted",
                    "presentKeys = keySet()",
                    ".getOrDefault(R2CompilationControlPlaneRoute.NONE)",
                ),
            )
            requireMarkers(
                label = "pre-ownership malformed/unsupported rejection",
                source = serviceSource,
                markers = listOf(
                    "if (debugR2Requested && debugR2Controls == null)",
                    "debugR2FormalAuthorityError ?:" ,
                    "publicR2Controls == null",
                    "if (!archiveOwnershipTransferred)",
                    "inputArchive.closeQuietly()",
                    "outputArchive.closeQuietly()",
                ),
            )
            requireMarkers(
                label = "strict JVM fixture and archive coverage",
                source = r2InputArchiveSchemaV1FixtureTestSource.asFile
                    .readText(Charsets.UTF_8) + "\n" +
                    r2ProjectArchiveTestSource.asFile.readText(Charsets.UTF_8),
                markers = listOf(
                    "class R2InputArchiveSchemaV1FixtureTest",
                    "fixtureFreezesFormalRevisionOneAndRejectsLegacyRevisionOverloading",
                    "archiveAuthorityRejectsEveryLegacyAndUnknownSchemaRevision",
                    "schemaRevisionOneUnifiesAllLegacyFeatureCombinationsWithoutRevisionOverloading",
                    "productionOwnerUsesOnePrefixAndDomainTaggedCanonicalRecords",
                    "legacyZipTransportCeilingDoesNotPromoteOrdinaryInputQuota",
                    "inputArchiveSchemaRejectsLegacyRevisionAndProfileDrift",
                    "nodeProfileRequiresItsExactTrustedDeclarationLayer",
                ),
            )

            val buildScriptSource = r2M2P3BuildScript.asFile.readText(Charsets.UTF_8)
            val sourcePolicyStart = buildScriptSource.indexOf(
                "tasks.register(\"verifyR2PublicPreviewSourcePolicy\")",
            )
            val sourcePolicyEnd = buildScriptSource.indexOf("inputs.files(", sourcePolicyStart)
            check(sourcePolicyStart >= 0 && sourcePolicyEnd > sourcePolicyStart) {
                "R2-M3-S1 could not isolate the public source-policy task description."
            }
            val publicSourcePolicy = buildScriptSource.substring(sourcePolicyStart, sourcePolicyEnd)
            check(
                "complete M3-S2" in publicSourcePolicy &&
                    "complete M3-D3" !in publicSourcePolicy
            ) {
                "R2-M3-S1 public source-policy description is stale."
            }

            logger.lifecycle(
                "R2-M3-S1 input-archive schema v1 fixture verified: one formal revision, " +
                    "eight exact conditional shapes, caller/trusted domain separation, " +
                    "canonical caller/declaration/retained fingerprints, pre-frame D1 quota, " +
                    "exact Rhino/Node declaration profiles, and no capability promotion",
            )
        }
    }

val verifyR2M3S1InputArchiveSchemaV1 =
    tasks.register("verifyR2M3S1InputArchiveSchemaV1") {
        group = "verification"
        description =
            "Closes R2-M3-S1 through canonical fixture/JVM/source gates, complete M3-D3, " +
                "frozen API, compiler-host APKs, and optional-plugin independence."

        dependsOn(verifyR2M3D3CapabilityPromotionCompatibilityDecision)
        dependsOn(verifyR2M3S1InputArchiveSchemaV1Fixture)
        dependsOn(verifyR2ProjectCompilerDesktop)
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        dependsOn("verifyR2PublicPreviewSourcePolicy")
        dependsOn("verifyDebugCompilerHostApk")
        dependsOn("verifyReleaseCompilerHostApk")
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn("assembleDebugAndroidTest")

        doLast {
            logger.lifecycle(
                "R2-M3-S1 input-archive schema v1 verified: exact conditional wire shapes, " +
                    "domain-separated accepted authority, Worker recomputation, D1 quota " +
                    "admission before framing, exact profile declarations, promotion=false",
            )
        }
    }

val verifyR2M3S2OutputArchiveSchemaV1Fixture =
    tasks.register("verifyR2M3S2OutputArchiveSchemaV1Fixture") {
        group = "verification"
        description =
            "Freezes R2-M3-S2 output-archive schema revision 1, accepted-input/compiler " +
                "provenance, effective-options binding, and module/artifact authority."

        dependsOn("testDebugUnitTest")
        dependsOn(":plugin-api:typescript-api:testDebugUnitTest")
        inputs.files(
            r2OutputArchiveSchemaV1Fixture,
            r2OutputArchiveSchemaV1FixtureTestSource,
            r2OutputArchiveSchemaV1Source,
            r2OutputArchiveSchemaV1TestSource,
            r2PrivateOutputArchiveSource,
            r2PrivateOutputArchiveTestSource,
            r2CompilationCoordinatorSource,
            r2ApiContractSource,
            r2ApiContractTestSource,
            r2InputArchiveSchemaV1Source,
            r2OrdinaryOutputQuotaDecisionFixture,
            r2OrdinaryOutputQuotaDecisionFixtureTestSource,
            r2PublicPreviewMetadataSource,
        )

        doLast {
            @Suppress("UNCHECKED_CAST")
            val fixture = JsonSlurper().parse(r2OutputArchiveSchemaV1Fixture.asFile) as
                Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            fun fixtureObject(name: String): Map<String, Any?> =
                fixture[name] as Map<String, Any?>
            check(
                fixture.keys.toList() == listOf(
                    "schema",
                    "revision",
                    "decision",
                    "publicContract",
                    "manifestShape",
                    "outcomes",
                    "emptyOutputOutcomes",
                    "provenance",
                    "effectiveOptionsAuthority",
                    "moduleInventory",
                    "artifactAuthority",
                    "ordinaryQuota",
                    "archiveContainer",
                    "diagnosticsDependency",
                    "failurePolicy",
                    "promotionState",
                ) &&
                    fixture["schema"] ==
                    "autojs6.typescript.r2-m3-s2.output-archive-schema" &&
                    (fixture["revision"] as Number).toInt() == 1
            ) {
                "R2-M3-S2 canonical fixture identity or root shape drifted."
            }

            val contract = fixtureObject("publicContract")
            check(
                contract["schemaRevisionKey"] == "outputArchiveSchemaRevision" &&
                    contract["schemaRevision"] == 1 &&
                    contract["schemaName"] == "autojs6.typescript.output-archive" &&
                    contract["manifestPath"] == "compilation-manifest.json" &&
                    contract["diagnosticsPath"] == "diagnostics.json" &&
                    contract["acceptedSchemaRevisions"] == listOf(1) &&
                    contract["rejectedLegacyManifestRevisions"] == listOf(2, 3) &&
                    contract["legacyRevisionPolicy"] == "reject-not-formal-v1-alias" &&
                    contract["unknownRevisionPolicy"] == "fail-closed"
            ) {
                "R2-M3-S2 formal identity or legacy/unknown revision closure drifted."
            }

            val manifestShape = fixtureObject("manifestShape")
            val manifestKeys = manifestShape["baseTopLevelKeys"] as List<*>
            listOf(
                "schema",
                "revision",
                "outputQuotaRevision",
                "inputArchiveSchemaRevision",
                "inputQuotaRevision",
                "callerInventoryFingerprint",
                "retainedInventoryFingerprint",
                "declarationProfileFingerprint",
                "effectiveOptions",
                "effectiveOptionsFingerprint",
                "moduleInventoryFingerprint",
                "artifactInventoryFingerprint",
                "files",
            ).forEach { key ->
                check(key in manifestKeys) {
                    "R2-M3-S2 formal manifest key is missing from the fixture: $key"
                }
            }
            check(
                manifestShape["artifactDescriptorKeys"] ==
                    listOf("path", "kind", "size", "sha256", "sourcePaths") &&
                    manifestShape["missingFieldPolicy"] == "fail-closed"
            ) {
                "R2-M3-S2 artifact descriptor or closed-world policy drifted."
            }

            val apiSource = r2ApiContractSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION = \"outputArchiveSchemaRevision\"",
                "OUTPUT_ARCHIVE_SCHEMA = \"autojs6.typescript.output-archive\"",
                "OUTPUT_ARCHIVE_SCHEMA_REVISION = 1",
                "OUTPUT_ARCHIVE_MANIFEST_PATH = \"compilation-manifest.json\"",
                "OUTPUT_ARCHIVE_DIAGNOSTICS_PATH = \"diagnostics.json\"",
            ).forEach { marker ->
                check(marker in apiSource) {
                    "R2-M3-S2 public contract marker is missing: $marker"
                }
            }

            val schemaSource = r2OutputArchiveSchemaV1Source.asFile.readText(Charsets.UTF_8)
            listOf(
                "internal object R2OutputArchiveSchemaV1",
                "autojs6.typescript.output-artifact-inventory.v1\\n",
                "autojs6.typescript.output-module-inventory.v1\\n",
                "fun requireSupportedIdentity(",
                "fun requireOutcomeInventory(",
                "fun artifactInventoryFingerprint(",
                "fun moduleInventoryFingerprint(",
            ).forEach { marker ->
                check(marker in schemaSource) {
                    "R2-M3-S2 schema authority marker is missing: $marker"
                }
            }

            val outputSource = r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "const val FORMAL_MANIFEST_SCHEMA = R2OutputArchiveSchemaV1.SCHEMA",
                "const val FORMAL_MANIFEST_REVISION = R2OutputArchiveSchemaV1.REVISION",
                "R2OutputArchiveSchemaV1.requireOutcomeInventory(",
                "authority.inputArchiveSchemaRevision",
                "authority.retainedInventoryFingerprint",
                "R2OutputArchiveSchemaV1.moduleInventoryFingerprint(files)",
                "R2OutputArchiveSchemaV1.artifactInventoryFingerprint(files)",
            ).forEach { marker ->
                check(marker in outputSource) {
                    "R2-M3-S2 production packager marker is missing: $marker"
                }
            }
            check(
                "autojs6.typescript.compilation.provisional" !in outputSource &&
                    "NODE_REVISION_TWO_PROVISIONAL_REVISION" !in outputSource &&
                    "PROVISIONAL_MANIFEST_SCHEMA" !in outputSource
            ) {
                "R2-M3-S2 production packager retained a legacy output-manifest alias."
            }

            val coordinatorSource = r2CompilationCoordinatorSource.asFile.readText(Charsets.UTF_8)
            check(
                "TypeScriptR2Contract.KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION" in coordinatorSource &&
                    "TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION" in coordinatorSource
            ) {
                "R2-M3-S2 completion Bundle does not publish the formal output revision."
            }

            val diagnostics = fixtureObject("diagnosticsDependency")
            check(
                diagnostics["schema"] == "autojs6.typescript.diagnostics.provisional" &&
                    diagnostics["revision"] == 2 &&
                    diagnostics["pathFrozenByS2"] == true &&
                    diagnostics["documentShapeFrozenByS2"] == false &&
                    diagnostics["formalDiagnosticsOwner"] == "R2-M3-S3"
            ) {
                "R2-M3-S2 crossed or obscured the separate diagnostics-schema boundary."
            }

            val promotion = fixtureObject("promotionState")
            check(promotion.values.all { value -> value == false }) {
                "R2-M3-S2 must not promote Public/runtime/device state."
            }
            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "ADVERTISED_COMPILER_AVAILABLE = true",
                "ADVERTISED_PROJECT_COMPILATION = true",
                "ADVERTISED_SEMANTIC_DIAGNOSTICS = true",
                "ADVERTISED_SOURCE_MAP_V3 = true",
                "ADVERTISED_NODE_RUNTIME_REQUIRED = false",
                "TypeScriptR2Contract.KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION",
                "TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION",
            ).forEach { marker ->
                check(marker in metadataSource) {
                    "R2-M3-S2 current capability advertisement drifted: $marker"
                }
            }

            logger.lifecycle(
                "R2-M3-S2 output-archive schema v1 fixture verified: one formal revision, " +
                    "S1 input provenance, effective-options binding, dual canonical " +
                    "module/artifact inventories, D2 quota, S3 diagnostics boundary, " +
                    "and promotion=false",
            )
        }
    }

val verifyR2M3S2OutputArchiveSchemaV1 =
    tasks.register("verifyR2M3S2OutputArchiveSchemaV1") {
        group = "verification"
        description =
            "Closes R2-M3-S2 through canonical fixture/JVM/source gates, complete M3-S1, " +
                "frozen API, compiler-host APKs, and optional-plugin independence."

        dependsOn(verifyR2M3S1InputArchiveSchemaV1)
        dependsOn(verifyR2M3S2OutputArchiveSchemaV1Fixture)
        dependsOn(verifyR2ProjectCompilerDesktop)
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        dependsOn("verifyR2PublicPreviewSourcePolicy")
        dependsOn("verifyDebugCompilerHostApk")
        dependsOn("verifyReleaseCompilerHostApk")
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn("assembleDebugAndroidTest")

        doLast {
            logger.lifecycle(
                "R2-M3-S2 output-archive schema v1 verified: formal outcome/provenance, " +
                    "effective-options identity, canonical module/artifact authority, " +
                    "D2 quota, provisional diagnostics isolated for S3, promotion=false",
            )
        }
    }

val verifyR2M3S3DiagnosticsSchemaV1Fixture =
    tasks.register("verifyR2M3S3DiagnosticsSchemaV1Fixture") {
        group = "verification"
        description =
            "Freezes R2-M3-S3 diagnostics schema revision 1: project-scoped URI, UTF-16 " +
                "range, category/code/message, related information, and semantic limits."

        dependsOn("testDebugUnitTest")
        dependsOn(":plugin-api:typescript-api:testDebugUnitTest")
        inputs.files(
            r2DiagnosticsSchemaV1Fixture,
            r2DiagnosticsSchemaV1FixtureTestSource,
            r2DiagnosticsSchemaV1Source,
            r2DiagnosticsSchemaV1TestSource,
            r2ProjectCompilationSource,
            r2ProjectCompilationTestSource,
            r2ProjectFramedTransportTestSource,
            r2CompilerHostProtocolSource,
            r2CompilerWorkerSource,
            r2PrivateOutputArchiveSource,
            r2PrivateOutputArchiveTestSource,
            r2OutputArchiveSchemaV1Source,
            r2CompilationCoordinatorSource,
            r2ApiContractSource,
            r2ApiContractTestSource,
            r2PublicPreviewMetadataSource,
            r2PublicPreviewInstrumentationSource,
            r2OutputArchiveSchemaV1Fixture,
        )

        doLast {
            @Suppress("UNCHECKED_CAST")
            val fixture = JsonSlurper().parse(r2DiagnosticsSchemaV1Fixture.asFile) as
                Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            fun objectValue(parent: Map<String, Any?>, name: String): Map<String, Any?> =
                parent[name] as Map<String, Any?>

            check(
                fixture.keys.toList() == listOf(
                    "schema",
                    "revision",
                    "decision",
                    "publicContract",
                    "documentShape",
                    "primaryDiagnosticShape",
                    "relatedInformationShape",
                    "uriPolicy",
                    "rangePolicy",
                    "categories",
                    "limits",
                    "consumerFixture",
                    "failurePolicy",
                    "promotionState",
                ) &&
                    fixture["schema"] ==
                    "autojs6.typescript.r2-m3-s3.diagnostics-schema" &&
                    (fixture["revision"] as Number).toInt() == 1 &&
                    fixture["decision"] ==
                    "formal-diagnostics-v1-with-project-scoped-uri-utf16-range-" +
                    "related-information-and-bounded-document"
            ) {
                "R2-M3-S3 canonical fixture identity or root shape drifted."
            }

            val contract = objectValue(fixture, "publicContract")
            check(
                contract["schemaRevisionKey"] == "diagnosticsSchemaRevision" &&
                    contract["schemaName"] == "autojs6.typescript.diagnostics" &&
                    (contract["schemaRevision"] as Number).toInt() == 1 &&
                    contract["diagnosticsPath"] == "diagnostics.json" &&
                    contract["acceptedSchemaRevisions"] == listOf(1) &&
                    contract["rejectedLegacyIdentities"] ==
                    listOf("autojs6.typescript.diagnostics.provisional@2") &&
                    contract["unknownRevisionPolicy"] == "fail-closed"
            ) {
                "R2-M3-S3 formal identity or legacy/unknown revision closure drifted."
            }

            val documentShape = objectValue(fixture, "documentShape")
            check(
                documentShape["topLevelKeys"] == listOf(
                    "schema",
                    "revision",
                    "diagnosticsQuotaRevision",
                    "requestId",
                    "projectId",
                    "targetProfile",
                    "profileSchemaRevision",
                    "profileRevision",
                    "configSchemaRevision",
                    "configKind",
                    "positionEncoding",
                    "lineBase",
                    "columnBase",
                    "diagnosticCount",
                    "relatedInformationCount",
                    "diagnostics",
                ) &&
                    documentShape["diagnosticCountMatchesArray"] == true &&
                    documentShape["relatedInformationCountMatchesNestedArrays"] == true &&
                    documentShape["canonicalUtf8"] == true
            ) {
                "R2-M3-S3 diagnostics document envelope drifted."
            }

            val primaryShape = objectValue(fixture, "primaryDiagnosticShape")
            check(
                primaryShape["baseKeys"] == listOf(
                    "phase",
                    "uri",
                    "range",
                    "category",
                    "code",
                    "message",
                    "relatedInformation",
                ) &&
                    primaryShape["nodeResolutionConditionalKey"] == "nodeResolution" &&
                    primaryShape["phaseOrder"] ==
                    listOf("options", "syntactic", "global", "resolution", "semantic", "emit") &&
                    primaryShape["relatedInformationAlwaysArray"] == true
            ) {
                "R2-M3-S3 primary diagnostic shape drifted."
            }
            val relatedShape = objectValue(fixture, "relatedInformationShape")
            check(
                relatedShape["keys"] == listOf("uri", "range", "category", "code", "message") &&
                    relatedShape["phaseInheritedFromPrimaryForPathAdmission"] == true &&
                    relatedShape["canonicalOrder"] ==
                    listOf("uri", "range", "category", "code", "message")
            ) {
                "R2-M3-S3 related-information shape drifted."
            }

            val uriPolicy = objectValue(fixture, "uriPolicy")
            check(
                uriPolicy["scheme"] == "autojs" &&
                    uriPolicy["authority"] == "source" &&
                    uriPolicy["namespace"] == "p-<lower-sha256(projectId-utf8)>" &&
                    uriPolicy["pathDomains"] == listOf("sources", "declarations", "packages") &&
                    uriPolicy["segmentEncoding"] ==
                    "uppercase-percent-encoded-strict-utf8-unreserved-pass-through" &&
                    uriPolicy["entrySelectionIndependent"] == true &&
                    uriPolicy["sourceDomainMatchesGeneratedMappingOriginalUri"] == true &&
                    (uriPolicy["maximumAsciiCharacters"] as Number).toInt() == 4096
            ) {
                "R2-M3-S3 canonical source-URI policy drifted."
            }
            val rangePolicy = objectValue(fixture, "rangePolicy")
            check(
                rangePolicy["keys"] == listOf("start", "length", "line", "column") &&
                    rangePolicy["positionEncoding"] == "utf-16-code-unit" &&
                    (rangePolicy["startAndLengthBase"] as Number).toInt() == 0 &&
                    (rangePolicy["lineBase"] as Number).toInt() == 1 &&
                    (rangePolicy["columnBase"] as Number).toInt() == 1 &&
                    rangePolicy["partialRangePolicy"] == "reject" &&
                    rangePolicy["nullUriRequiresNullRange"] == true &&
                    rangePolicy["uriMayHaveNullRange"] == true
            ) {
                "R2-M3-S3 UTF-16 range/nullability policy drifted."
            }
            val categories = objectValue(fixture, "categories")
            check(
                categories.keys.toList() == listOf("warning", "error", "suggestion", "message") &&
                    (categories["warning"] as Number).toInt() == 0 &&
                    (categories["error"] as Number).toInt() == 1 &&
                    (categories["suggestion"] as Number).toInt() == 2 &&
                    (categories["message"] as Number).toInt() == 3
            ) {
                "R2-M3-S3 TypeScript diagnostic-category identities drifted."
            }

            val limits = objectValue(fixture, "limits")
            check(
                (limits["quotaRevision"] as Number).toInt() == 1 &&
                    (limits["maximumDiagnostics"] as Number).toInt() == 1_000 &&
                    (limits["maximumDocumentUtf8Bytes"] as Number).toLong() == 512L * 1024L &&
                    (limits["maximumRelatedInformationPerDiagnostic"] as Number).toInt() == 1_000 &&
                    (limits["maximumRelatedInformationTotal"] as Number).toInt() == 1_000 &&
                    (limits["maximumMessageUtf16Characters"] as Number).toInt() == 4_096 &&
                    limits["boundariesInclusive"] == true &&
                    (limits["legacyTransportMaximumDiagnostics"] as Number).toInt() == 10_000 &&
                    (limits["legacyTransportMaximumDiagnosticsBytes"] as Number).toLong() ==
                    8L * 1024L * 1024L &&
                    limits["legacyTransportIsOrdinarySchemaAuthority"] == false
            ) {
                "R2-M3-S3 semantic diagnostics quota or transport separation drifted."
            }

            val consumer = objectValue(fixture, "consumerFixture")
            @Suppress("UNCHECKED_CAST")
            val diagnostics = consumer["diagnostics"] as List<Map<String, Any?>>
            val diagnostic = diagnostics.single()
            @Suppress("UNCHECKED_CAST")
            val related = diagnostic["relatedInformation"] as List<Map<String, Any?>>
            check(
                consumer.keys.toList() == documentShape["topLevelKeys"] &&
                    consumer["schema"] == contract["schemaName"] &&
                    (consumer["revision"] as Number).toInt() == 1 &&
                    (consumer["diagnosticCount"] as Number).toInt() == diagnostics.size &&
                    (consumer["relatedInformationCount"] as Number).toInt() == related.size &&
                    diagnostic.keys.toList() == primaryShape["baseKeys"] &&
                    diagnostic["uri"] ==
                    "autojs://source/" +
                    "p-9876095ce2297318967f87b9d9e90bd863e8c90940a23bb3b74079cfaa74526a/" +
                    "sources/nested/hash%23%20percent%25.ts" &&
                    related.single().keys.toList() == relatedShape["keys"] &&
                    related.single()["uri"] ==
                    "autojs://source/" +
                    "p-9876095ce2297318967f87b9d9e90bd863e8c90940a23bb3b74079cfaa74526a/" +
                    "declarations/lib.d.ts"
            ) {
                "R2-M3-S3 exact consumer fixture or canonical URI drifted."
            }

            val apiSource = r2ApiContractSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "KEY_DIAGNOSTICS_SCHEMA_REVISION = \"diagnosticsSchemaRevision\"",
                "DIAGNOSTICS_SCHEMA = \"autojs6.typescript.diagnostics\"",
                "DIAGNOSTICS_SCHEMA_REVISION = 1",
                "DIAGNOSTICS_QUOTA_REVISION = 1",
                "MAX_DIAGNOSTICS_COUNT = 1_000",
                "MAX_DIAGNOSTICS_DOCUMENT_BYTES = 512L * 1024L",
                "MAX_RELATED_INFORMATION_PER_DIAGNOSTIC = 1_000",
                "MAX_RELATED_INFORMATION_TOTAL = 1_000",
                "DIAGNOSTICS_POSITION_ENCODING = \"utf-16-code-unit\"",
                "DIAGNOSTICS_LINE_BASE = 1",
                "DIAGNOSTICS_COLUMN_BASE = 1",
            ).forEach { marker ->
                check(marker in apiSource) {
                    "R2-M3-S3 public contract marker is missing: $marker"
                }
            }

            val schemaSource = r2DiagnosticsSchemaV1Source.asFile.readText(Charsets.UTF_8)
            listOf(
                "internal object R2DiagnosticsSchemaV1",
                "fun requireSupportedIdentity(",
                "fun requireCounts(",
                "fun requireDocumentBytes(",
                "fun requireCategory(",
                "fun sourceUri(",
                "R2NodeModuleOutputPolicy.PROJECT_NAMESPACE_PREFIX",
                "percentEncodeSegment(segment)",
            ).forEach { marker ->
                check(marker in schemaSource) {
                    "R2-M3-S3 canonical schema authority marker is missing: $marker"
                }
            }

            val modelSource = r2ProjectCompilationSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "internal data class R2ProjectRelatedInformation(",
                "val relatedInformation: List<R2ProjectRelatedInformation> = emptyList()",
                "R2DiagnosticsSchemaV1.requireCategory(category)",
                "relatedInformationCount = this.diagnostics.sumOf",
            ).forEach { marker ->
                check(marker in modelSource) {
                    "R2-M3-S3 native diagnostic model marker is missing: $marker"
                }
            }

            val protocolSource = r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT",
                "TypeScriptR2Contract.MAX_DIAGNOSTICS_DOCUMENT_BYTES",
                "parseProjectRelatedInformation(",
                "TypeScriptR2Contract.MAX_RELATED_INFORMATION_PER_DIAGNOSTIC",
                "TypeScriptR2Contract.MAX_RELATED_INFORMATION_TOTAL",
                "R2DiagnosticsSchemaV1.requireCounts(",
            ).forEach { marker ->
                check(marker in protocolSource) {
                    "R2-M3-S3 native protocol parser marker is missing: $marker"
                }
            }

            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "const maximumDiagnostics = 1000;",
                "const maximumDiagnosticsBytes = 512 * 1024;",
                "const maximumRelatedInformationPerDiagnostic = 1000;",
                "const maximumRelatedInformationTotal = 1000;",
                "const serializeDiagnosticRelatedInformation = (related) => {",
                "serialized.relatedInformation = relatedInformation.map(",
                "let relatedInformationCount = 0;",
            ).forEach { marker ->
                check(marker in workerSource) {
                    "R2-M3-S3 Worker diagnostic marker is missing: $marker"
                }
            }

            val outputSource = r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "FORMAL_DIAGNOSTICS_SCHEMA = R2DiagnosticsSchemaV1.SCHEMA",
                "FORMAL_DIAGNOSTICS_REVISION = R2DiagnosticsSchemaV1.REVISION",
                "R2DiagnosticsSchemaV1.sourceUri(projectId, sourcePath)",
                "\"diagnosticsQuotaRevision\"",
                "appendJsonField(\"relatedInformationCount\"",
                "appendJsonString(\"relatedInformation\")",
                "R2DiagnosticsSchemaV1.requireDocumentBytes(diagnosticsBytes)",
            ).forEach { marker ->
                check(marker in outputSource) {
                    "R2-M3-S3 private packager marker is missing: $marker"
                }
            }
            check("autojs6.typescript.diagnostics.provisional" !in outputSource) {
                "R2-M3-S3 production packager retained the provisional diagnostics alias."
            }

            val coordinatorSource = r2CompilationCoordinatorSource.asFile.readText(Charsets.UTF_8)
            val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
            val publicPreviewSource =
                r2PublicPreviewInstrumentationSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "TypeScriptR2Contract.KEY_DIAGNOSTICS_SCHEMA_REVISION",
                "TypeScriptR2Contract.DIAGNOSTICS_SCHEMA_REVISION",
            ).forEach { marker ->
                check(marker in coordinatorSource && marker in metadataSource) {
                    "R2-M3-S3 revision consumer marker is missing: $marker"
                }
            }
            listOf(
                "KEY_DIAGNOSTICS_SCHEMA_REVISION",
                "DIAGNOSTICS_SCHEMA_REVISION",
                "TypeScriptR2Contract.DIAGNOSTICS_SCHEMA",
            ).forEach { marker ->
                check(marker in publicPreviewSource) {
                    "R2-M3-S3 public preview consumer marker is missing: $marker"
                }
            }

            val promotion = objectValue(fixture, "promotionState")
            check(promotion.values.all { value -> value == false }) {
                "R2-M3-S3 must not promote Public/runtime/device state."
            }
            listOf(
                "ADVERTISED_COMPILER_AVAILABLE = true",
                "ADVERTISED_PROJECT_COMPILATION = true",
                "ADVERTISED_SEMANTIC_DIAGNOSTICS = true",
                "ADVERTISED_SOURCE_MAP_V3 = true",
                "ADVERTISED_NODE_RUNTIME_REQUIRED = false",
            ).forEach { marker ->
                check(marker in metadataSource) {
                    "R2-M3-S3 current capability advertisement drifted: $marker"
                }
            }

            logger.lifecycle(
                "R2-M3-S3 diagnostics schema v1 fixture verified: formal revision, " +
                    "project-scoped URI, UTF-16 range, TypeScript categories/codes/messages, " +
                    "related information, 1000-record/512-KiB quota, and promotion=false",
            )
        }
    }

val verifyR2M3S3DiagnosticsSchemaV1 =
    tasks.register("verifyR2M3S3DiagnosticsSchemaV1") {
        group = "verification"
        description =
            "Closes R2-M3-S3 through canonical fixture/JVM/source gates, complete M3-S2, " +
                "frozen API, compiler-host APKs, public consumer checks, and independence."

        dependsOn(verifyR2M3S2OutputArchiveSchemaV1)
        dependsOn(verifyR2M3S3DiagnosticsSchemaV1Fixture)
        dependsOn(verifyR2ProjectCompilerDesktop)
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        dependsOn(":r2-public-preview:compileDebugJavaWithJavac")
        dependsOn("verifyR2PublicPreviewSourcePolicy")
        dependsOn("verifyDebugCompilerHostApk")
        dependsOn("verifyReleaseCompilerHostApk")
        dependsOn("verifyOptionalPluginIndependence")
        dependsOn("assembleDebugAndroidTest")

        doLast {
            logger.lifecycle(
                "R2-M3-S3 diagnostics schema v1 verified: formal URI/range/category/code/" +
                    "message/related-information document, bounded Worker/native/archive " +
                    "consumers, legacy alias rejected, promotion=false",
            )
        }
    }

val verifyR2ProjectCompilerCore =
    tasks.register("verifyR2ProjectCompilerCore") {
        group = "verification"
        description =
            "Builds and verifies the provisional R2 compiler core through the complete " +
                "M3-S3 source/JVM/APK diagnostics-schema-v1 aggregate and the " +
                "normal/shipping Release natural parse-timeout evidence slice without devices."
        dependsOn(verifyR2ProjectCompilerDesktop)
        dependsOn(verifyR2M3S3DiagnosticsSchemaV1)
        dependsOn(verifyTypeScriptDistribution)
        dependsOn(verifyCompilerHostSourcePolicy)
        dependsOn("verifyR2PublicPreviewSourcePolicy")
        dependsOn("verifyR2ReleaseCompatibilitySourcePolicy")
        dependsOn("verifyR2P3bB2bResourceSourcePolicy")
        dependsOn("verifyR2P3bB2bAndroidTestApk")
        dependsOn("verifyR2P3bB2eNaturalIngressTimeoutSourcePolicy")
        dependsOn("verifyR2P3bB2eNaturalIngressTimeoutAndroidTestApk")
        dependsOn("verifyR2P3bB2gNaturalPhaseCancellationSourcePolicy")
        dependsOn("verifyR2P3bB2gNaturalPhaseCancellationAndroidTestApk")
        dependsOn("verifyR2P3bB2hNaturalPhaseTimeoutSourcePolicy")
        dependsOn("verifyR2P3bB2hNaturalPhaseTimeoutAndroidTestApk")
        dependsOn("verifyR2P3bB2iNaturalParseTimeoutSourcePolicy")
        dependsOn("verifyR2P3bB2iNaturalParseTimeoutAndroidTestApk")
        dependsOn("verifyR2P3bB2jNaturalEmitTimeoutSourcePolicy")
        dependsOn("verifyR2P3bB2jNaturalEmitTimeoutAndroidTestApk")
        dependsOn(
            ":r2-public-preview:verifyR2P3bB2fExternalPlusOneCleanupSourcePolicy",
        )
        dependsOn(":r2-public-preview:verifyR2P3bB2fExternalPlusOneCleanupApks")
        dependsOn(
            ":r2-public-preview:verifyR2P3bB2kExternalNaturalEmitTimeoutSourcePolicy",
        )
        dependsOn(":r2-public-preview:verifyR2P3bB2kExternalNaturalEmitTimeoutApks")
        dependsOn(
            ":r2-public-preview:verifyR2ExternalNaturalSemanticTimeoutSourcePolicy",
        )
        dependsOn(":r2-public-preview:verifyR2ExternalNaturalSemanticTimeoutApks")
        dependsOn(
            ":r2-public-preview:verifyR2ExternalNaturalParseTimeoutSourcePolicy",
        )
        dependsOn(":r2-public-preview:verifyR2ExternalNaturalParseTimeoutApks")
        dependsOn(
            ":r2-public-preview:verifyR2ShippingReleaseNaturalParseTimeoutSourcePolicy",
        )
        dependsOn(
            ":r2-public-preview:verifyR2ShippingReleaseNaturalParseTimeoutApks",
        )
        dependsOn("testDebugUnitTest", "assembleDebugAndroidTest")
    }

val verifyR2ReleaseCompatibilitySourcePolicy =
    tasks.register("verifyR2ReleaseCompatibilitySourcePolicy") {
        group = "verification"
        description =
            "Verifies release-safe FD inspection and Binder-authenticated R2 supervisor handshakes."
        inputs.files(
            r2FileDescriptorOpenFlagsSource,
            r2FileDescriptorOpenFlagsNativeSource,
            r2FileDescriptorOpenFlagsCppSource,
            r2CompilerProcessSupervisorControlSource,
            appProguardRules,
        )
        doLast {
            val descriptorSource =
                r2FileDescriptorOpenFlagsSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "Build.VERSION.SDK_INT >= Build.VERSION_CODES.R",
                "private object Api30",
                "Os.fcntlInt(",
                "OsConstants.F_GETFL",
                "R2FileDescriptorOpenFlagsNative.getFileStatusFlags(",
                "ErrnoException(",
            ).forEach { marker ->
                check(marker in descriptorSource) {
                    "R2 release FD inspection is missing: $marker"
                }
            }
            check("/proc/self/fdinfo" !in descriptorSource) {
                "R2 release FD inspection must not depend on procfs."
            }

            val nativeSource =
                r2FileDescriptorOpenFlagsNativeSource.asFile.readText(Charsets.UTF_8)
            check("""System.loadLibrary("autojs6_r2_fd_flags")""" in nativeSource)
            check("static native int getFileStatusFlags(int descriptor);" in nativeSource)

            val cppSource =
                r2FileDescriptorOpenFlagsCppSource.asFile.readText(Charsets.UTF_8)
            check("fcntl(descriptor, F_GETFL)" in cppSource) {
                "R2 API 24-29 native bridge must call libc fcntl(F_GETFL)."
            }
            check("errno == EINTR" in cppSource) {
                "R2 API 24-29 native bridge must retry interrupted fcntl calls."
            }
            check("return static_cast<jint>(-error_number);" in cppSource) {
                "R2 API 24-29 native bridge must return a bounded negative errno."
            }

            val proguardSource = appProguardRules.asFile.readText(Charsets.UTF_8)
            check(
                Regex(
                    """-keep class io\.github\.supermonster003\.autojs6\.plugin\.typescript\.""" +
                        """R2FileDescriptorOpenFlagsNative\s*\{\s*""" +
                        """static native int getFileStatusFlags\(int\);\s*\}""",
                    RegexOption.DOT_MATCHES_ALL,
                ).containsMatchIn(proguardSource),
            ) {
                "R2 API 24-29 JNI symbol must retain its exact class and native member."
            }

            val supervisorSource =
                r2CompilerProcessSupervisorControlSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "capture(identity.pid)",
                "capture(supervisorPid)",
                "capture(Binder.getCallingPid())",
            ).forEach { forbidden ->
                check(forbidden !in supervisorSource) {
                    "R2 online supervisor handshake must not cross-read procfs: $forbidden"
                }
            }
            listOf(
                "Binder.getCallingUid()",
                "Binder.getCallingPid()",
                "COMPILER_PROCESS_NAME",
                "typescript_supervisor",
                "secureEquals(",
            ).forEach { marker ->
                check(marker in supervisorSource) {
                    "R2 Binder-authenticated supervisor handshake is missing: $marker"
                }
            }
            check(
                Regex(
                    """R2CompilerProcessProcfs\(\)\s*\.\s*""" +
                        """capture\(\s*Process\.myPid\(\)\s*\)""",
                ).containsMatchIn(supervisorSource),
            ) {
                "R2 online supervisor handshake must retain self procfs capture."
            }
        }
    }

val verifyR2ReleaseCompatibilityApks =
    tasks.register("verifyR2ReleaseCompatibilityApks") {
        group = "verification"
        description =
            "Verifies the four-ABI FD bridge and procfs-free FD policy in debug and release APKs."
        dependsOn(verifyR2ReleaseCompatibilitySourcePolicy)
        dependsOn("assembleDebug", "assembleRelease")
        val debugApkDirectory = layout.buildDirectory.dir("outputs/apk/debug")
        val releaseApkDirectory = layout.buildDirectory.dir("outputs/apk/release")
        inputs.dir(debugApkDirectory)
        inputs.dir(releaseApkDirectory)
        doLast {
            listOf(debugApkDirectory, releaseApkDirectory).forEach { directory ->
                verifyR2FileDescriptorNativeBridgeInApk(
                    resolveSingleApkOutput(directory.get().asFile),
                )
            }
        }
    }

tasks.register("verifyR2PublicPreviewSourcePolicy") {
    group = "verification"
    description =
        "Verifies production R2 wiring for complete M3-S2 plus M3-S3 with compiler " +
            "capability mask 15 and no Node Runtime dependency."
    inputs.files(
        r2PublicPreviewServiceSource,
        r2PublicPreviewMetadataSource,
        r2ProjectArchiveSource,
        r2InputArchiveSchemaV1Source,
        r2TrustedDeclarationsSource,
        r2DeclarationDistributionSource,
        r2FileBackedProjectSnapshotSource,
        r2ProjectSnapshotSource,
        r2ApiContractSource,
        r2ProjectSourceKindAdmissionSource,
        r2M2P2P5NodePackageTargetPolicySource,
        r2M2P2P5NodeResolutionKernelSource,
        r2ProjectFramedTransportSource,
        r2CompilationProbeSource,
        r2ExternalCapacityEvidenceProtocolSource,
        r2ZipArchiveValidatorSource,
        compilerWebViewHostSource,
        r2CompilerWorkerSource,
        r2CompilerHostPageSource,
        r2CompilerHostProtocolSource,
        r2NodeRevisionTwoResultAuthoritySource,
        r2ProjectCompilationSource,
        r2OutputArchiveSchemaV1Source,
        r2DiagnosticsSchemaV1Source,
        r2PrivateOutputArchiveSource,
        r2CompilationCoordinatorSource,
    )

    doLast {
        fun requireSourceMarkers(
            label: String,
            source: String,
            markers: List<String>,
        ) {
            markers.forEach { marker ->
                check(marker in source) {
                    "R2 complete M3-S3 $label wiring is missing: $marker"
                }
            }
        }

        val serviceSource = r2PublicPreviewServiceSource.asFile.readText(Charsets.UTF_8)
        requireSourceMarkers(
            label = "production service",
            source = serviceSource,
            markers = listOf(
                "r2CompilationCoordinator = R2CompilationCoordinator(",
                "request.r2PublicCompilationClassification(",
                "supportedDeclarationProfileFingerprint,",
                "TypeScriptR2Contract.KEY_CANCELLATION_OPERATION",
                "R2CompilerProcessSupervisorClient.isReady()",
            ),
        )
        requireSourceMarkers(
            label = "input-archive schema v1 public and canonical authority",
            source = r2ApiContractSource.asFile.readText(Charsets.UTF_8) + "\n" +
                r2InputArchiveSchemaV1Source.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "KEY_INPUT_ARCHIVE_SCHEMA_REVISION = \"inputArchiveSchemaRevision\"",
                "INPUT_ARCHIVE_SCHEMA_REVISION = 1",
                "INPUT_ARCHIVE_SCHEMA = \"autojs6.typescript.input-archive\"",
                "internal object R2InputArchiveSchemaV1",
                "autojs6.typescript.input-inventory.v1\\n",
                "fun callerInventoryFingerprint(",
                "fun acceptedAuthority(",
                "internal data class R2AcceptedInputAuthorityV1(",
            ),
        )
        requireSourceMarkers(
            label = "input-archive exact shape and accepted snapshot authority",
            source = r2ProjectArchiveSource.asFile.readText(Charsets.UTF_8) + "\n" +
                r2ProjectSnapshotSource.asFile.readText(Charsets.UTF_8) + "\n" +
                r2FileBackedProjectSnapshotSource.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "root.requireExactKeys(",
                "R2InputArchiveSchemaV1.callerInventoryFingerprint(",
                "R2InputArchiveSchemaV1.acceptedAuthority(",
                "acceptedInputAuthority = acceptedInputAuthority",
                "val acceptedInputAuthority: R2AcceptedInputAuthorityV1?",
                "Accepted input authority capacity drifted after archive acceptance.",
            ),
        )
        requireSourceMarkers(
            label = "output-archive schema v1 and completion authority",
            source = r2ApiContractSource.asFile.readText(Charsets.UTF_8) + "\n" +
                r2OutputArchiveSchemaV1Source.asFile.readText(Charsets.UTF_8) + "\n" +
                r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8) + "\n" +
                r2CompilationCoordinatorSource.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION = \"outputArchiveSchemaRevision\"",
                "OUTPUT_ARCHIVE_SCHEMA = \"autojs6.typescript.output-archive\"",
                "OUTPUT_ARCHIVE_SCHEMA_REVISION = 1",
                "internal object R2OutputArchiveSchemaV1",
                "autojs6.typescript.output-artifact-inventory.v1\\n",
                "autojs6.typescript.output-module-inventory.v1\\n",
                "R2OutputArchiveSchemaV1.requireOutcomeInventory(",
                "authority.retainedInventoryFingerprint",
                "R2OutputArchiveSchemaV1.moduleInventoryFingerprint(files)",
                "R2OutputArchiveSchemaV1.artifactInventoryFingerprint(files)",
                "TypeScriptR2Contract.KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION",
            ),
        )
        requireSourceMarkers(
            label = "diagnostics schema v1 and completion authority",
            source = r2ApiContractSource.asFile.readText(Charsets.UTF_8) + "\n" +
                r2DiagnosticsSchemaV1Source.asFile.readText(Charsets.UTF_8) + "\n" +
                r2ProjectCompilationSource.asFile.readText(Charsets.UTF_8) + "\n" +
                r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8) + "\n" +
                r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8) + "\n" +
                r2CompilationCoordinatorSource.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "KEY_DIAGNOSTICS_SCHEMA_REVISION = \"diagnosticsSchemaRevision\"",
                "DIAGNOSTICS_SCHEMA = \"autojs6.typescript.diagnostics\"",
                "DIAGNOSTICS_SCHEMA_REVISION = 1",
                "MAX_DIAGNOSTICS_COUNT = 1_000",
                "MAX_DIAGNOSTICS_DOCUMENT_BYTES = 512L * 1024L",
                "internal object R2DiagnosticsSchemaV1",
                "fun sourceUri(",
                "internal data class R2ProjectRelatedInformation(",
                "parseProjectRelatedInformation(",
                "FORMAL_DIAGNOSTICS_SCHEMA = R2DiagnosticsSchemaV1.SCHEMA",
                "appendJsonString(\"relatedInformation\")",
                "TypeScriptR2Contract.KEY_DIAGNOSTICS_SCHEMA_REVISION",
            ),
        )
        requireSourceMarkers(
            label = "exact debug declaration-profile selection",
            source = serviceSource,
            markers = listOf(
                "declarationProfileFingerprintOrNull(profileAuthority.profile)",
                "R2TrustedDeclarations.loadForProfile(",
                "val r2ControlPlaneRoute = request.r2CompilationControlPlaneRoute(",
                "r2ControlPlaneRoute == R2CompilationControlPlaneRoute.PUBLIC",
                "if (r2ControlPlaneRoute == R2CompilationControlPlaneRoute.CONFLICT)",
            ),
        )
        listOf(
            "hasAnyMetadata(",
            "hasPublicOnlyMetadata(",
            "conflictsWithDebugControlPlane(",
            "hasPublicR2Metadata",
            "debugPublicR2Conflict",
        ).forEach { forbidden ->
            check(forbidden !in serviceSource) {
                "R2-M3-S3 service bypasses the single debug/public route classifier: $forbidden"
            }
        }
        requireSourceMarkers(
            label = "debug/public route authority",
            source = r2CompilationProbeSource.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "internal enum class R2CompilationControlPlaneRoute",
                "SHARED_FORMAL_AUTHORITY_KEYS",
                "PUBLIC_ONLY_METADATA_KEYS",
                "fun controlPlaneRoute(",
                "debugControlPlaneRequested -> R2CompilationControlPlaneRoute.DEBUG",
                "hasAnyMetadata(presentKeys) -> R2CompilationControlPlaneRoute.PUBLIC",
            ),
        )
        requireSourceMarkers(
            label = "debug/public route semantic matrix",
            source = r2CompilationProbeProtocolTestSource.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "fun sharedFormalAuthorityDoesNotConflictWithThePrivateDebugControlPlane()",
                "fun controlPlaneRouteSeparatesSharedFormalPublicAndMalformedDebugRequests()",
                "R2CompilationControlPlaneRoute.NONE",
                "R2CompilationControlPlaneRoute.DEBUG",
                "R2CompilationControlPlaneRoute.PUBLIC",
                "R2CompilationControlPlaneRoute.CONFLICT",
            ),
        )
        val routeTestSource =
            r2CompilationProbeProtocolTestSource.asFile.readText(Charsets.UTF_8)
        val routeTestStart = routeTestSource.indexOf(
            "fun controlPlaneRouteSeparatesSharedFormalPublicAndMalformedDebugRequests()",
        )
        val routeTestEnd = routeTestSource.indexOf(
            "\n    @Test",
            routeTestStart + 1,
        ).let { index -> if (index >= 0) index else routeTestSource.length }
        check(routeTestStart >= 0 && routeTestEnd > routeTestStart) {
            "R2-M3-S3 could not isolate the debug/public route semantic matrix."
        }
        requireSourceMarkers(
            label = "isolated debug/public route semantic matrix",
            source = routeTestSource.substring(routeTestStart, routeTestEnd),
            markers = listOf(
                "debugControlPlaneRequested = true",
                "debugControlPlaneAccepted = true",
                "R2CompilationControlPlaneRoute.CONFLICT",
                "debugControlPlaneAccepted = false",
                "R2CompilationControlPlaneRoute.DEBUG",
                "debugControlPlaneRequested = false",
                "R2CompilationControlPlaneRoute.PUBLIC",
            ),
        )

        requireSourceMarkers(
            label = "profile-aware native source-kind overlay",
            source = r2ProjectSourceKindAdmissionSource.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "R2CompilerEscapePolicy.sourcePathRejection(path)",
                "profileAuthority == " +
                    "R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2",
                "!endsWith(\".d.mts\")",
                "!endsWith(\".d.cts\")",
                "endsWith(\".mts\") || endsWith(\".cts\")",
                "sourcePathRejection(path, profileAuthority) != null",
            ),
        )
        requireSourceMarkers(
            label = "resolver/package-target v2 authority",
            source =
                r2M2P2P5NodePackageTargetPolicySource.asFile.readText(Charsets.UTF_8) +
                    "\n" +
                    r2M2P2P5NodeResolutionKernelSource.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "R2NodePackageTargetPolicyV2",
                "R2NodeResolutionKernelV2",
                "TRUSTED_CUSTOM_CONDITION_COUNT = 0",
                "targetUniverseFingerprint",
            ),
        )
        requireSourceMarkers(
            label = "streaming ZIP validator",
            source = r2ZipArchiveValidatorSource.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "internal interface ZipArchiveV1EntrySink {",
                "fun validateImmutableTo(",
                "sink.abort(failure)",
            ),
        )
        requireSourceMarkers(
            label = "file-backed archive adapter",
            source = r2ProjectArchiveSource.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "R2PrivateProjectSpool.create(privateDirectory)",
                "ZipArchiveV1Validator.validateImmutableTo(",
                "limits = ZipArchiveV1Limits.INPUT",
                "R2ProjectArchiveAdapter.adaptFileBacked(",
                "spool.appendTrustedDeclaration(declaration)",
            ),
        )
        requireSourceMarkers(
            label = "file-backed VFS snapshot",
            source = r2FileBackedProjectSnapshotSource.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "internal class R2PrivateProjectSpool private constructor(",
                ") : ZipArchiveV1EntrySink, Closeable {",
                "internal class R2FileBackedProjectSnapshot internal constructor(",
                "override fun openFile(fileIndex: Int): R2ProjectFileCursor",
                "private const val MAXIMUM_STREAM_CHUNK_BYTES = 16 * 1024",
            ),
        )
        val framedTransportSource =
            r2ProjectFramedTransportSource.asFile.readText(Charsets.UTF_8)
        requireSourceMarkers(
            label = "bounded random-access framer",
            source = framedTransportSource,
            markers = listOf(
                "private val snapshot: R2ProjectInputSnapshot",
                "private val acceptedInputAuthority = snapshot.acceptedInputAuthority",
                "\"inputArchiveSchemaRevision\" to acceptedInputAuthority.schemaRevision",
                "\"retainedInventoryFingerprint\" to",
                "\"declarationInventoryFingerprint\" to",
                "\"largestFileBytes\" to",
                "private val chunkBuffer = ByteArray(R2_PROJECT_FRAME_RAW_CHUNK_BYTES)",
                "fileIndex - packageMetadataFiles.size - packageTargetFiles.size",
                "val maximumFrameBytes =",
                "if (stage == Stage.PROJECT_START)",
                "R2_PROJECT_MAXIMUM_START_FRAME_BYTES",
                "check(encodedBytes <= maximumFrameBytes)",
                "fun hasAcceptedCompleteInventory(): Boolean =",
                "nextChunkIndex == chunkCount",
                "acceptedBytes == byteLength",
                "internal const val R2_PROJECT_MAXIMUM_FRAME_BYTES = 128 * 1024",
                "internal const val R2_PROJECT_MAXIMUM_START_FRAME_BYTES = 576 * 1024",
                "internal const val R2_PROJECT_FRAME_RAW_CHUNK_BYTES = 64 * 1024",
            ),
        )
        check(
            Regex(
                """val maximumFrameBytes =\s*""" +
                    """if \(stage == Stage\.PROJECT_START\) \{\s*""" +
                    """R2_PROJECT_MAXIMUM_START_FRAME_BYTES\s*""" +
                    """\} else \{\s*R2_PROJECT_MAXIMUM_FRAME_BYTES\s*\}\s*""" +
                    """check\(encodedBytes <= maximumFrameBytes\)""",
                RegexOption.DOT_MATCHES_ALL,
            ).containsMatchIn(framedTransportSource),
        ) {
            "R2 complete M3-S3 start/ordinary frame budgets are no longer " +
                "structurally bound."
        }
        requireSourceMarkers(
            label = "phase-bound Worker control framing",
            source = framedTransportSource,
            markers = listOf(
                "private val workerSpinPhase: R2ProjectExecutionPhase? = null",
                "(workerSpinMilliseconds == 0) == (workerSpinPhase == null)",
                "\"workerSpinPhase\" to",
                "workerSpinPhase?.protocolValue ?: WORKER_SPIN_PHASE_NONE",
                "Formal schema-v1 exact 30-field no-metadata Program start",
                "Formal schema-v1 exact 40-field metadata-only or 47-field",
            ),
        )
        requireSourceMarkers(
            label = "Worker Node revision 2 Program/result owner",
            source = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "inventoryCanonicalPrefix: \"autojs6.typescript.input-inventory.v1\\n\"",
                "const deriveInputInventoryFingerprint = async (records) =>",
                "message.inputArchiveSchemaRevision !== inputArchiveAuthority.schemaRevision",
                "deriveInputInventoryFingerprint(callerRecords)",
                "deriveInputInventoryFingerprint(retainedRecords)",
                "deriveInputInventoryFingerprint(declarationRecords)",
                "createNodeRevisionTwoResolutionSession",
                "createNodeRevisionTwoSourceFile",
                "kind: \"projectResultStart\"",
                "const acceptProjectResultAck = (message) => {",
                "kind: \"projectResultEnd\"",
            ),
        )
        requireSourceMarkers(
            label = "native Node revision 2 result/private-output authority",
            source =
                r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8) +
                    "\n" +
                    r2NodeRevisionTwoResultAuthoritySource.asFile.readText(Charsets.UTF_8) +
                    "\n" +
                    r2ProjectCompilationSource.asFile.readText(Charsets.UTF_8) +
                    "\n" +
                    r2PrivateOutputArchiveSource.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "exactSnapshot: R2ProjectInputSnapshot? = null",
                "R2NodeRevisionTwoResultAuthority.derive(",
                "R2NodeRevisionTwoResultAuthority.requireExact(",
                "generatedModuleMappings",
                "compilationOutcome.exactSnapshot !== acceptedArchive.snapshot",
            ),
        )

        requireSourceMarkers(
            label = "public ordinary compilation quota",
            source = r2ApiContractSource.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "const val ORDINARY_COMPILATION_INPUT_QUOTA_REVISION = 2",
                "const val MAX_ORDINARY_COMPILATION_INPUT_FILES = 512",
                "const val MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES = " +
                    "8L * 1024L * 1024L",
                "const val MAX_ORDINARY_COMPILATION_INPUT_BYTES = 8L * 1024L * 1024L",
            ),
        )
        val snapshotSource = r2ProjectSnapshotSource.asFile.readText(Charsets.UTF_8)
        requireSourceMarkers(
            label = "native ordinary compilation quota",
            source = snapshotSource,
            markers = listOf(
                "val ORDINARY_COMPILATION = R2ProjectSnapshotLimits(",
                "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES",
                "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES",
                "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES",
                "val PROVISIONAL_WORKER = ORDINARY_COMPILATION",
                "internal fun ordinaryCompilationAdmission(",
                "(authority?.callerCapacity ?: derivedCaller).admissionAgainst(limits)",
            ),
        )

        val webViewHostSource = compilerWebViewHostSource.asFile.readText(Charsets.UTF_8)
        requireSourceMarkers(
            label = "off-main input framing",
            source = webViewHostSource,
            markers = listOf(
                "private val inputFrameExecutor = ThreadPoolExecutor(",
                "workerAdmissionLimits: R2ProjectSnapshotLimits =\n" +
                    "            R2ProjectSnapshotLimits.ORDINARY_COMPILATION",
                "R2ProjectSnapshot.ordinaryCompilationAdmission(",
                "private fun scheduleProjectInputPreparationOnMain(",
                "inputFrameExecutor.execute {",
                "val encoded = framer.nextFrame()",
            ),
        )
        check(
            webViewHostSource.indexOf(
                "R2ProjectSnapshot.ordinaryCompilationAdmission(",
            ) < webViewHostSource.indexOf("R2ProjectInputFramer("),
        ) {
            "R2-M3-D1 ordinary eligibility must be checked before the input framer is constructed."
        }
        requireSourceMarkers(
            label = "debug/evidence-only expanded Worker capacity",
            source = webViewHostSource,
            markers = listOf(
                "internal fun compileProjectWithExpandedWorkerCapacityForTesting(",
                "BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE",
                "\"Expanded compiler Worker capacity is unavailable outside debug/evidence builds.\"",
                "workerAdmissionLimits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE",
            ),
        )
        val probeSource = r2CompilationProbeSource.asFile.readText(Charsets.UTF_8)
        requireSourceMarkers(
            label = "public debug-control exclusion",
            source = probeSource,
            markers = listOf(
                "val workerSpinPhase: R2ProjectExecutionPhase? = null",
                "val expandedWorkerCapacity: Boolean = false",
                "workerSpinPhase == null",
                "!expandedWorkerCapacity",
                "(workerSpinMilliseconds == 0) == (workerSpinPhase == null)",
                "R2CompilationControlOrigin.EXTERNAL_CAPACITY_EVIDENCE",
                "\"R2 control origin cannot enable an unauthorized capacity or " +
                    "fault-injection hook.\"",
            ),
        )
        requireSourceMarkers(
            label = "same-UID private phase/capacity parser",
            source = serviceSource,
            markers = listOf(
                "!BuildConfig.DEBUG ||",
                "callingUid != applicationInfo.uid",
                "val workerSpinPhase = when (workerSpinMilliseconds)",
                "R2CompilationProbeProtocol.KEY_WORKER_SPIN_PHASE",
                "R2ProjectExecutionPhase.fromProtocolValue(",
                "R2CompilationProbeProtocol.KEY_EXPANDED_WORKER_CAPACITY",
            ),
        )
        requireSourceMarkers(
            label = "non-shipping external capacity evidence authority",
            source = r2ExternalCapacityEvidenceProtocolSource.asFile
                .readText(Charsets.UTF_8),
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
                "TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES",
                "R2CompilationControlOrigin.EXTERNAL_CAPACITY_EVIDENCE",
                "expandedWorkerCapacity = true",
            ),
        )
        requireSourceMarkers(
            label = "external capacity evidence service derivation",
            source = serviceSource,
            markers = listOf(
                "request.requestsR2ExternalCapacityEvidence()",
                "request.r2ExternalCapacityEvidenceControlsOrNull(",
                "BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE",
                "ApplicationInfo.FLAG_TEST_ONLY",
                "packageManager.checkSignatures(",
                "PackageManager.SIGNATURE_MATCH",
                "Os.fstat(descriptor).st_size",
                "externalCapacityEvidenceRequested &&",
                "externalCapacityEvidenceControls == null",
            ),
        )

        val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
        val p3bWorkerCapacity = Regex(
            """const maximumProjectFiles = 4_607;\s*""" +
                """const maximumProjectFileBytes = 8 \* 1024 \* 1024;\s*""" +
                """const maximumProjectBytes = 72 \* 1024 \* 1024;""",
            RegexOption.DOT_MATCHES_ALL,
        )
        check(
            p3bWorkerCapacity.containsMatchIn(workerSource),
        ) {
            "R2-P3b-a Worker capacity must cover the 4,607-file/72 MiB native spool envelope."
        }
        requireSourceMarkers(
            label = "bounded Worker input lifetime",
            source = workerSource,
            markers = listOf(
                "const maximumProjectPathChars = 8 * 1024 * 1024;",
                "const maximumProjectDirectoryNodes = 65_536;",
                "const maximumProjectDirectoryChars = 4 * 1024 * 1024;",
                "const maximumTokensPerFile = 65_536;",
                "const maximumProjectTokens = 262_144;",
                "const countSourceTokens = (text) => {",
                "const createDirectoryIndex = (initialPaths = []) => {",
                "sourceFile.version = \"0\";",
                "const createDiagnosticCollector = () => {",
                "record.text = \"\";",
            ),
        )
        requireSourceMarkers(
            label = "unified pre-emit diagnostic stop",
            source = workerSource,
            markers = listOf(
                "diagnosticCollector.consume(\"options\", program.getOptionsDiagnostics())",
                "program.getSyntacticDiagnostics(sourceFile)",
                "diagnosticCollector.consume(\"global\", program.getGlobalDiagnostics())",
                "program.getSemanticDiagnostics(sourceFile)",
                "if (diagnosticCollector.errorCount() > 0)",
                "emitSkipped: true",
                "if ((errorCount > 0 || emitResult.emitSkipped) && outputs.size !== 0)",
                "throw new Error(\"noEmitOnError produced executable output.\")",
            ),
        )
        requireSourceMarkers(
            label = "native diagnostic/result invariant",
            source = r2ProjectCompilationSource.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "val hasBlockingDiagnostics",
                "Blocking TypeScript diagnostics must skip emit without output.",
                "An error-free TypeScript result must not skip emit.",
            ),
        )
        val projectExecutionPhaseOrder = Regex(
            """const projectExecutionPhases = Object\.freeze\(\[\s*""" +
                """"ingress",\s*"parse",\s*"semantic",\s*"emit",\s*]\);""",
            RegexOption.DOT_MATCHES_ALL,
        )
        check(projectExecutionPhaseOrder.containsMatchIn(workerSource)) {
            "R2-P3b-b2a Worker execution phases must retain ingress/parse/semantic/emit order."
        }
        requireSourceMarkers(
            label = "Worker phase protocol",
            source = workerSource,
            markers = listOf(
                "const enterProjectExecutionPhase = (execution, phase, totalUnits = 0) => {",
                "const completeProjectExecutionPhase = (execution, phase) => {",
                "kind: \"projectPhase\"",
                "state: \"started\"",
                "state: \"completed\"",
                "enterProjectExecutionPhase(framedInput, \"ingress\");",
                "message.workerSpinPhase !== \"none\"",
                "!projectExecutionPhaseSet.has(message.workerSpinPhase)",
            ),
        )
        val emitFinalizationMarkers =
            listOf(
                "const beginProjectResult = async (requestId, project, frameToken) => {",
                "const result = await compileProject(project, frameToken);",
                "requireActiveProjectInputFrame(frameToken);",
                "new TextEncoder().encode(JSON.stringify(result))",
                "const digest = await bytesToSha256(bytes);",
                "requireActiveProjectInputFrame(frameToken);",
                "execution: project.execution,",
                "kind: \"projectResultStart\"",
                "const acceptProjectResultAck = (message) => {",
                "const nextSequence = message.sequence + 1;",
                "if (nextSequence < state.chunkCount) {",
                "postProjectResultChunk(state, nextSequence);",
                "return;",
                "const resultByteLength = state.bytes.byteLength;",
                "completeProjectExecutionPhase(state.execution, \"emit\");",
                "state.bytes.fill(0);",
                "framedResult = null;",
                "kind: \"projectResultEnd\"",
            )
        var emitFinalizationOffset = 0
        emitFinalizationMarkers.forEach { marker ->
            val markerOffset = workerSource.indexOf(marker, emitFinalizationOffset)
            check(markerOffset >= 0) {
                "R2 Worker emit/result finalization ordering is missing: $marker"
            }
            emitFinalizationOffset = markerOffset + marker.length
        }
        val boundedOutputHashMarkers =
            listOf(
                "const serializedOutputs = [];",
                "const sortedOutputs = Array.from(outputs.entries()).sort(",
                "for (const [path, output] of sortedOutputs) {",
                "const outputSha256 = await sha256(output.text);",
                "if (frameToken !== null) {",
                "requireActiveProjectInputFrame(frameToken);",
                "serializedOutputs.push({",
                "sha256: outputSha256,",
            )
        var boundedOutputHashOffset = 0
        boundedOutputHashMarkers.forEach { marker ->
            val markerOffset = workerSource.indexOf(marker, boundedOutputHashOffset)
            check(markerOffset >= 0) {
                "R2 Worker bounded output hashing is missing: $marker"
            }
            boundedOutputHashOffset = markerOffset + marker.length
        }
        check("const serializedOutputs = await Promise.all(" !in workerSource) {
            "R2 Worker must not issue every per-output digest concurrently."
        }
        listOf(
            "let transferredToFramedResult = false;",
            "transferredToFramedResult = true;",
            "if (!transferredToFramedResult && bytes !== null) {",
            "bytes.fill(0);",
        ).forEach { marker ->
            check(marker in workerSource) {
                "R2 Worker local result-byte ownership cleanup is missing: $marker"
            }
        }

        val hostPageSource = r2CompilerHostPageSource.asFile.readText(Charsets.UTF_8)
        check(projectExecutionPhaseOrder.containsMatchIn(hostPageSource)) {
            "R2-P3b-b2a host page phase order must match the Worker phase order."
        }
        requireSourceMarkers(
            label = "host-page phase state machine",
            source = hostPageSource,
            markers = listOf(
                "case \"projectPhase\":",
                "const handleWorkerProjectPhase = (message, messageGeneration) => {",
                "message.phase !== expectedPhase",
                "const isResultEmitCompletion =",
                "active.resultAwaitingNativeAck !== null",
                "active.resultNextIndex !== active.resultChunkCount",
                "active.activeExecutionPhase !== \"emit\"",
                "projectExecutionPhases[active.nextExecutionPhaseIndex] !== \"emit\"",
                "active.activeExecutionPhase !== null",
                "active.nextExecutionPhaseIndex !== projectExecutionPhases.length",
            ),
        )
        requireSourceMarkers(
            label = "native phase protocol parser",
            source = r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8),
            markers = listOf(
                "\"projectPhase\" -> {",
                "CompilerHostMessage.ProjectPhase(",
                "R2ProjectExecutionPhase.fromProtocolValue(",
                "R2ProjectExecutionPhaseState.fromProtocolValue(",
            ),
        )
        requireSourceMarkers(
            label = "exact active-phase correlation",
            source = webViewHostSource,
            markers = listOf(
                "private fun handleProjectPhaseOnMain(",
                "pending.activeProjectExecutionPhase == message.phase",
                "pending.completedProjectExecutionPhases ==",
                "hasAcceptedCompleteInventory() == true",
                "private fun handleProjectResultStartOnMain(",
                "R2ProjectExecutionPhase.EMIT",
                "R2ProjectExecutionPhase.ordered.dropLast(1)",
                "private fun handleProjectResultChunkOnMain(",
                "Project result chunk arrived outside active emit transport.",
                "message.bytes.fill(0)",
                "private fun handleProjectResultEndOnMain(",
                "pending.activeProjectExecutionPhase != null",
                "Project result ended before emit transport completed.",
                "internal fun terminateActiveProjectRendererForTesting(",
                "candidate.operationHandle === handle &&",
                "candidate.activeProjectExecutionPhase == expectedPhase &&",
                "candidate.rendererTerminationRequestedAtPhase == null &&",
                "candidate.sessionToken === token &&",
                "candidate.rendererTerminationRequestedAtPhase = expectedPhase",
                "phase = pending?.activeProjectExecutionPhase",
                "requestedAtPhase = pending?.rendererTerminationRequestedAtPhase",
                "private const val PROTOCOL_VERIFICATION_QUEUE_CAPACITY = 16",
            ),
        )

        val metadataSource = r2PublicPreviewMetadataSource.asFile.readText(Charsets.UTF_8)
        requireAdvertisedTypeScriptCompilerCapabilities(metadataSource, "R2 through P3b-b2e")
    }
}

val verifyR2P3bB2bResourceSourcePolicy =
    tasks.register("verifyR2P3bB2bResourceSourcePolicy") {
        group = "verification"
        description =
            "Statically verifies the R2-P3b-b2b repeated-resource and startup-cleanup evidence " +
                "shape without running devices."
        dependsOn("verifyR2PublicPreviewSourcePolicy")
        inputs.files(
            r2PublicPreviewServiceSource,
            r2PrivateTransientOrphanScavengerSource,
            r2PrivateTransientOrphanScavengerTestSource,
            r2PrivateTransientOrphanScavengerInstrumentationSource,
            r2ProjectFramedTransportSource,
            r2BinderCompilationInstrumentationSource,
            r2ProjectCompilerInstrumentationSource,
            remoteProcessResourceProbeSource,
            webViewProcessMemoryProbeSource,
        )

        doLast {
            fun requireSourceMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-P3b-b2b $label evidence shape is missing: $marker"
                    }
                }
            }

            fun requireResourceConstants(
                label: String,
                source: String,
            ) {
                val constants = Regex(
                    """const val REPEATED_RESOURCE_CYCLE_COUNT = 3\s*""" +
                        """const val RESOURCE_PSS_SAMPLE_COUNT = 5""",
                    RegexOption.DOT_MATCHES_ALL,
                )
                check(constants.containsMatchIn(source)) {
                    "R2-P3b-b2b $label must retain three repeated cycles and five-sample " +
                        "odd PSS batches."
                }
            }

            val memoryProbeSource =
                webViewProcessMemoryProbeSource.asFile.readText(Charsets.UTF_8)
            requireSourceMarkers(
                label = "exact PSS process identity and odd-sample median",
                source = memoryProbeSource,
                markers = listOf(
                    "internal data class R1SingleProcessPssBatch(",
                    "require(samplesPssKiB.isNotEmpty() && samplesPssKiB.size % 2 == 1)",
                    "require(sampleCount > 0 && sampleCount % 2 == 1)",
                    "val medianPssKiB: Int = samplesPssKiB.median()",
                    "process = process",
                ),
            )
            val identityBoundPssSampling = Regex(
                """repeat\(sampleCount\).*?""" +
                    """requireIdentity\(process\).*?""" +
                    """samples \+= readTotalPssKiB\(process\).*?""" +
                    """requireIdentity\(process\)""",
                RegexOption.DOT_MATCHES_ALL,
            )
            check(identityBoundPssSampling.containsMatchIn(memoryProbeSource)) {
                "R2-P3b-b2b single-process PSS sampling must prove the exact process identity " +
                    "before and after every sample."
            }

            val resourceProbeSource =
                remoteProcessResourceProbeSource.asFile.readText(Charsets.UTF_8)
            requireSourceMarkers(
                label = "stable FD target multiset and transient exclusion",
                source = resourceProbeSource,
                markers = listOf(
                    "val canonicalTargets = snapshot.targets.sorted()",
                    "previousQualifiedTargets == canonicalTargets",
                    "snapshot.targets.filter(::isR2TransientTarget)",
                    "fun awaitR2TransientFileDescriptors(",
                    "expectedIdentity = expectedIdentity",
                    "matchesTarget = ::isR2TransientTarget",
                    "const val DELETED_FILE_SUFFIX = \" (deleted)\"",
                    "const val R2_PRIVATE_INPUT_PREFIX = \"r2-private-input-\"",
                    "const val R2_PRIVATE_INPUT_SUFFIX = \".staging\"",
                    "const val R2_PROJECT_SPOOL_PREFIX = \"r2-project-vfs-\"",
                    "const val R2_PROJECT_SPOOL_SUFFIX = \".spool\"",
                    "const val R2_PRIVATE_OUTPUT_PREFIX = \"r2-private-output-\"",
                    "const val R2_PRIVATE_OUTPUT_SUFFIX = \".zip.staging\"",
                    "const val R2_CALLER_INPUT_PREFIX = \"r2-binder-input-\"",
                    "const val R2_CALLER_OUTPUT_PREFIX = \"r2-binder-output-\"",
                    "const val R2_CAPACITY_INPUT_PREFIX = \"r2-capacity-\"",
                    "const val R2_CAPACITY_OUTPUT_PREFIX = " +
                        "\"r2-binder-capacity-output-\"",
                    "const val FD_STABILITY_LOOKUP_ATTEMPTS = 50",
                    "const val FD_STABILITY_LOOKUP_INTERVAL_MILLIS = 100L",
                    "const val REQUIRED_CONSECUTIVE_STABLE_SAMPLES = 5",
                ),
            )

            val binderResourceSource =
                r2BinderCompilationInstrumentationSource.asFile.readText(Charsets.UTF_8)
            requireResourceConstants(
                label = "Binder resource matrix",
                source = binderResourceSource,
            )
            requireSourceMarkers(
                label = "three-cycle exact-64-MiB Binder resource matrix",
                source = binderResourceSource,
                markers = listOf(
                    "fun repeatedExact64BinderJobsReachCompilerPssFdAndTransientCleanupPlateau()",
                    "repeat(REPEATED_RESOURCE_CYCLE_COUNT)",
                    "R2CapacityArchiveBoundary.STORED_CONTAINER_INCLUSIVE",
                    "TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES",
                    "processResourceProbe.requireIdentity(compilerPid)",
                    "processResourceProbe.awaitR2TransientFileDescriptors(",
                    "processResourceProbe.awaitStableFileDescriptors(",
                    "memoryProbe.samplePss(",
                    "cycleFileDescriptors.first().targets.sorted()",
                    "cycleFileDescriptors.last().targets.sorted()",
                    "assertNoUnexpectedFileDescriptorGrowth(",
                    "COMPILER_EVENT_LOOP_FD_TARGETS",
                    "setOf(\"anon_inode:[eventpoll]\", \"anon_inode:[eventfd]\")",
                    "MAX_ADJACENT_WARM_MEDIAN_DELTA_KIB",
                    "Build.VERSION_CODES.N_MR1",
                    "MAX_LEGACY_SINGLE_PROCESS_COMPILER_MEDIAN_GROWTH_FROM_WARM_KIB",
                    "MAX_COMPILER_MEDIAN_GROWTH_AFTER_MATURE_CYCLE_KIB",
                    "inputDirectory.listFiles()\n" +
                        "            ?: throw AssertionError(",
                    "outputDirectory.listFiles()\n" +
                        "            ?: throw AssertionError(",
                    "assertNoKnownR2TransientNames(\"small successor\")",
                    "\"BINDER_MATRIX_PASS api=",
                ),
            )

            val directResourceSource =
                r2ProjectCompilerInstrumentationSource.asFile.readText(Charsets.UTF_8)
            requireResourceConstants(
                label = "direct Worker resource matrix",
                source = directResourceSource,
            )
            requireSourceMarkers(
                label = "natural-ingress cursor, renderer, and Worker cleanup matrix",
                source = directResourceSource,
                markers = listOf(
                    "fun repeatedExact64NaturalIngressCancellationReleasesCursorAndBoundsRendererPss()",
                    "repeat(REPEATED_RESOURCE_CYCLE_COUNT)",
                    "host.compileProjectWithExpandedWorkerCapacityForTesting(",
                    "val liveTelemetry = waitForActiveInputCursor(",
                    "processProbe.samplePss(",
                    "cause = R2ProjectStopCause.CANCELLED",
                    "assertReleasedInputCursors(",
                    "host.lastTerminatedWorkerGenerationForTesting()",
                    "\"The repeated ingress fault did not terminate a newer Worker.\"",
                    "assertTrue(recovered.errorMessage.orEmpty(), recovered.succeeded)",
                    "assertRetiredProjectSession(host.lifecycleTelemetryForTesting())",
                    "\"INGRESS_MATRIX_PASS api=",
                ),
            )
            requireSourceMarkers(
                label = "balanced and released input cursors",
                source = directResourceSource,
                markers = listOf(
                    "telemetry.openedCursorCount > 0",
                    "telemetry.openedCursorCount,\n" +
                        "            telemetry.closedCursorCount",
                    "telemetry.activeCursor",
                    "telemetry.released",
                ),
            )

            val framerSource =
                r2ProjectFramedTransportSource.asFile.readText(Charsets.UTF_8)
            requireSourceMarkers(
                label = "production cursor accounting telemetry",
                source = framerSource,
                markers = listOf(
                    "private var openedCursorCount = 0",
                    "private var closedCursorCount = 0",
                    "openedCursorCount = Math.addExact(openedCursorCount, 1)",
                    "closedCursorCount = Math.addExact(closedCursorCount, 1)",
                    "activeCursor = currentConfigCursor != null || currentFileCursor != null",
                    "released = released",
                ),
            )

            val scavengerSource =
                r2PrivateTransientOrphanScavengerSource.asFile.readText(Charsets.UTF_8)
            requireSourceMarkers(
                label = "startup orphan scavenger",
                source = scavengerSource,
                markers = listOf(
                    "internal object R2PrivateTransientOrphanScavenger",
                    "operation = \"initial enumeration\"",
                    "operation = \"post-cleanup verification\"",
                    "fileOperations.listChildren(directory)",
                    "fileOperations.restoreOwnerWritable(file)",
                    "fileOperations.delete(file)",
                    "throw R2PrivateTransientCleanupUnprovenException(",
                    "prefix = \"r2-private-input-\"",
                    "suffix = \".staging\"",
                    "prefix = \"r2-project-vfs-\"",
                    "suffix = \".spool\"",
                    "prefix = \"r2-private-output-\"",
                    "suffix = \".zip.staging\"",
                ),
            )

            val scavengerTestSource =
                r2PrivateTransientOrphanScavengerTestSource.asFile.readText(Charsets.UTF_8)
            requireSourceMarkers(
                label = "orphan-scavenger fail-closed JVM tests",
                source = scavengerTestSource,
                markers = listOf(
                    "fun deletesOnlyRecognizedDirectFilesAndPreservesUnknownChildren()",
                    "fun restoresOwnerWriteBeforeDeletingReadOnlyOrphans()",
                    "fun cleanupIsIdempotent()",
                    "fun initialEnumerationFailureIsCleanupUnproven()",
                    "fun rejectedDeletionIsCleanupUnproven()",
                    "fun recheckEnumerationFailureIsCleanupUnproven()",
                    "fun recheckRejectsADeletionThatClaimsSuccessButLeavesResidue()",
                ),
            )

            val scavengerInstrumentationSource =
                r2PrivateTransientOrphanScavengerInstrumentationSource.asFile
                    .readText(Charsets.UTF_8)
            requireSourceMarkers(
                label = "cold-successor orphan-scavenger instrumentation",
                source = scavengerInstrumentationSource,
                markers = listOf(
                    "fun coldSuccessorScavengesExactOrphansAndPreservesOtherChildren()",
                    "terminateCompilerProcess(",
                    "fixture.assertScavengedAndPreserved()",
                    "prewarm.containsKey(TypeScriptCompilerContract.KEY_ERROR_CODE)",
                    "R2PrivateTransientOrphanScavenger.INPUT_STAGING_DIRECTORY_NAME",
                    "R2PrivateTransientOrphanScavenger.OUTPUT_STAGING_DIRECTORY_NAME",
                ),
            )

            val serviceSource =
                r2PublicPreviewServiceSource.asFile.readText(Charsets.UTF_8)
            requireSourceMarkers(
                label = "startup scavenger fail-closed service wiring",
                source = serviceSource,
                markers = listOf(
                    "admissionArbiter = R2ProcessAdmissionAuthority.openServiceEpoch()",
                    "R2PrivateTransientOrphanScavenger.scavenge(",
                    "catch (failure: R2PrivateTransientCleanupUnprovenException)",
                    "admissionArbiter.failClosed()",
                    "\"Compiler startup could not prove private transient cleanup.\"",
                    "TypeScriptWebViewCompilerHost.prepareCompilerProcess()",
                    "r2CompilationCoordinator = R2CompilationCoordinator(",
                ),
            )
            val epochOpen =
                serviceSource.indexOf(
                    "admissionArbiter = R2ProcessAdmissionAuthority.openServiceEpoch()",
                )
            val scavenge =
                serviceSource.indexOf("R2PrivateTransientOrphanScavenger.scavenge(")
            val startupFailure =
                serviceSource.indexOf(
                    "catch (failure: R2PrivateTransientCleanupUnprovenException)",
                    scavenge,
                )
            val failClosed =
                serviceSource.indexOf("admissionArbiter.failClosed()", startupFailure)
            val prepareHost =
                serviceSource.indexOf("TypeScriptWebViewCompilerHost.prepareCompilerProcess()")
            val createCoordinator =
                serviceSource.indexOf("r2CompilationCoordinator = R2CompilationCoordinator(")
            check(
                epochOpen >= 0 &&
                    epochOpen < scavenge &&
                    scavenge < startupFailure &&
                    startupFailure < failClosed &&
                    failClosed < prepareHost &&
                    prepareHost < createCoordinator,
            ) {
                "R2-P3b-b2b startup transient cleanup must run after admission authority opens, " +
                    "fail closed on unproven cleanup, and finish before host/coordinator startup."
            }
        }
    }

val verifyR2P3bB2bAndroidTestApk =
    tasks.register("verifyR2P3bB2bAndroidTestApk") {
        group = "verification"
        description =
            "Verifies that the debug androidTest APK contains the R2-P3b-b2b resource " +
                "evidence matrix; it does not run devices."
        dependsOn("assembleDebugAndroidTest")
        dependsOn(verifyR2P3bB2bResourceSourcePolicy)
        val testApkDirectory =
            layout.buildDirectory.dir("outputs/apk/androidTest/debug")
        inputs.dir(testApkDirectory)
        doLast {
            val apkFile =
                resolveSingleApkOutput(testApkDirectory.get().asFile)
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
                check(dexBytes.isNotEmpty()) {
                    "R2-P3b-b2b androidTest APK contains no DEX entries: $apkFile"
                }
            val requiredMarkers = linkedMapOf(
                    "Binder resource test class" to
                        "R2BinderCompilationInstrumentationTest",
                    "direct Worker resource test class" to
                        "R2ProjectCompilerInstrumentationTest",
                    "startup scavenger instrumentation class" to
                        "R2PrivateTransientOrphanScavengerInstrumentationTest",
                    "remote FD resource probe" to "RemoteProcessResourceProbe",
                    "exact PSS identity probe" to "R1WebViewProcessMemoryProbe",
                    "three-cycle exact64 Binder method" to
                        "repeatedExact64BinderJobsReachCompilerPssFdAndTransientCleanupPlateau",
                    "natural ingress cleanup method" to
                        "repeatedExact64NaturalIngressCancellationReleasesCursorAndBoundsRendererPss",
                    "cold-successor startup cleanup method" to
                        "coldSuccessorScavengesExactOrphansAndPreservesOtherChildren",
                    "shared resource evidence tag" to "R2P3bResource",
                    "Binder completion evidence" to "BINDER_MATRIX_PASS",
                    "ingress completion evidence" to "INGRESS_MATRIX_PASS",
                    "stable FD target multiset assertion" to
                        "The mature compiler-service FD target multiset grew or churned",
                    "balanced cursor assertion" to
                        "Project input transport did not close every opened cursor",
                    "private input transient namespace" to "r2-private-input-",
                    "project spool transient namespace" to "r2-project-vfs-",
                    "private output transient namespace" to "r2-private-output-",
                )
                requiredMarkers.forEach { (label, markerText) ->
                    val marker = markerText.toByteArray(Charsets.UTF_8)
                    check(dexBytes.any { dex -> dex.containsByteSequence(marker) }) {
                        "R2-P3b-b2b androidTest APK is missing $label marker: $markerText"
                    }
                }
            }
        }
    }

val verifyR2P3bB2eNaturalIngressTimeoutSourcePolicy =
    tasks.register("verifyR2P3bB2eNaturalIngressTimeoutSourcePolicy") {
        group = "verification"
        description =
            "Statically verifies the R2-P3b-b2e exact64 natural-ingress timeout, cleanup, " +
                "and successor evidence shape without running devices."
        dependsOn(verifyR2P3bB2bResourceSourcePolicy)
        inputs.files(
            r2ProjectCompilerInstrumentationSource,
            r2BinderCompilationInstrumentationSource,
        )
        doLast {
            fun testMethod(
                source: String,
                signature: String,
            ): String {
                val start = source.indexOf(signature)
                check(start >= 0) {
                    "R2-P3b-b2e test method is missing: $signature"
                }
                val end = source.indexOf("\n    @Test", start + signature.length)
                    .takeIf { index -> index >= 0 }
                    ?: source.length
                return source.substring(start, end)
            }

            fun requireMethodMarkers(
                label: String,
                method: String,
                markers: List<String>,
            ) {
                markers.forEach { marker ->
                    check(marker in method) {
                        "R2-P3b-b2e $label evidence shape is missing: $marker"
                    }
                }
            }

            val directSource =
                r2ProjectCompilerInstrumentationSource.asFile.readText(Charsets.UTF_8)
            check(
                Regex("""const val NATURAL_INGRESS_TIMEOUT_MILLIS\s*=\s*5_000L""")
                    .containsMatchIn(directSource),
            ) {
                "R2-P3b-b2e direct-host evidence must retain its fixed 5,000 ms deadline."
            }
            val directMethod = testMethod(
                source = directSource,
                signature =
                    "fun repeatedExact64NaturalIngressTimeoutReleasesCursorAndRecovers()",
            )
            requireMethodMarkers(
                label = "direct-host natural timeout",
                method = directMethod,
                markers = listOf(
                    "createExactCallerCapacitySnapshot(",
                    "EXACT_CALLER_CAPACITY_BYTES",
                    "repeat(REPEATED_RESOURCE_CYCLE_COUNT)",
                    "host.compileProjectWithExpandedWorkerCapacityForTesting(",
                    "timeoutMs = NATURAL_INGRESS_TIMEOUT_MILLIS",
                    "workerSpinMilliseconds = 0",
                    "workerSpinPhase = null",
                    "waitForActiveInputCursor(",
                    "minimumAcceptedBytes = 1L",
                    "R2ProjectExecutionPhase.INGRESS",
                    "assertFalse(liveTelemetry.complete)",
                    "assertFalse(liveTelemetry.released)",
                    "TypeScriptCompilerContract.ERROR_TIMEOUT",
                    "assertEquals(null, timedOut.compilation)",
                    "timedOut.elapsedMs >= NATURAL_INGRESS_TIMEOUT_MILLIS",
                    "assertReleasedInputCursors(",
                    "expectedComplete = false",
                    "terminalTelemetry.acceptedPayloadBytes > 0L",
                    "terminalTelemetry.totalPayloadBytes",
                    "host.lastTerminatedWorkerGenerationForTesting()",
                    "terminatedGeneration > lastTerminatedGeneration",
                    "boundarySnapshot.close()",
                    "assertTrue(recovered.errorMessage.orEmpty(), recovered.succeeded)",
                    "baselineRetiredSessionGeneration",
                    "host.lastRetiredSessionGenerationForTesting()",
                    "assertRetiredProjectSession(host.lifecycleTelemetryForTesting())",
                    "requireNotNull(closeReport).cleanupProven",
                    "\"INGRESS_TIMEOUT_CYCLE_PASS",
                    "\"INGRESS_TIMEOUT_MATRIX_PASS",
                ),
            )
            check("requestStop(" !in directMethod) {
                "R2-P3b-b2e direct-host evidence must reach timeout naturally."
            }
            check("SYNCHRONOUS_WORKER_SPIN_MILLIS" !in directMethod) {
                "R2-P3b-b2e direct-host evidence must not inject synchronous Worker delay."
            }

            val binderSource =
                r2BinderCompilationInstrumentationSource.asFile.readText(Charsets.UTF_8)
            check(
                Regex(
                    """const val NATURAL_INGRESS_BINDER_TIMEOUT_MILLIS\s*=\s*10_000L""",
                ).containsMatchIn(binderSource),
            ) {
                "R2-P3b-b2e Binder evidence must retain its fixed 10,000 ms deadline."
            }
            listOf(
                "timeoutMs: Long = TypeScriptCompilerContract.MAX_TIMEOUT_MS",
                "fixture.capacityRequest(timeoutMs)",
                "TypeScriptCompilerContract.KEY_TIMEOUT_MS",
                "timeoutMs,",
            ).forEach { marker ->
                check(marker in binderSource) {
                    "R2-P3b-b2e capacity request must bind the requested timeout: $marker"
                }
            }
            val binderMethod = testMethod(
                source = binderSource,
                signature =
                    "fun exact64NaturalDeadlinePublishesTimeoutCleansBinderStateAndRecovers()",
            )
            requireMethodMarkers(
                label = "Binder natural timeout",
                method = binderMethod,
                markers = listOf(
                    "R2CapacityArchiveBoundary.STORED_CONTAINER_INCLUSIVE",
                    "TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES",
                    "timeoutMs =",
                    "NATURAL_INGRESS_BINDER_TIMEOUT_MILLIS,",
                    "publicAcknowledgement(fixture.requestId, token)",
                    "timeoutCallback.awaitTerminal(",
                    "assertFalse(timeoutCallback.callbackBeforeStartReturned)",
                    "TypeScriptCompilerContract.KEY_REQUEST_ID",
                    "TypeScriptCompilerContract.OUTCOME_TIMED_OUT",
                    "TypeScriptCompilerContract.ERROR_TIMEOUT",
                    "TypeScriptCompilerContract.KEY_DIAGNOSTIC_COUNT",
                    "TypeScriptCompilerContract.KEY_EMITTED_MODULE_COUNT",
                    "KEY_OUTPUT_MANIFEST_SHA256",
                    "NATURAL_INGRESS_BINDER_TIMEOUT_MILLIS",
                    "wallElapsedMillis >= NATURAL_INGRESS_BINDER_TIMEOUT_MILLIS",
                    "assertEquals(0L, output.length())",
                    "assertEquals(1, timeoutCallback.terminalCount.get())",
                    "EXACTLY_ONCE_OBSERVATION_MILLIS",
                    "awaitActiveJobCount(session.compiler, 0)",
                    "assertNoKnownR2TransientNames(\"natural-timeout terminal\")",
                    "processResourceProbe.awaitR2TransientFileDescriptors(",
                    "processResourceProbe.requireIdentity(compilerPid)",
                    "compileAndAcknowledge(",
                    "assertNoKnownR2TransientNames(\"natural-timeout successor\")",
                    "\"BINDER_TIMEOUT_PASS",
                ),
            )
            check("CANCELLATION_OPERATION_CANCEL" !in binderMethod) {
                "R2-P3b-b2e Binder evidence must ACK admission without requesting cancellation."
            }
            check("workerSpinMilliseconds" !in binderMethod) {
                "R2-P3b-b2e Binder evidence must not inject synchronous Worker delay."
            }
        }
    }

val verifyR2P3bB2eNaturalIngressTimeoutAndroidTestApk =
    tasks.register("verifyR2P3bB2eNaturalIngressTimeoutAndroidTestApk") {
        group = "verification"
        description =
            "Verifies that the debug androidTest APK contains the R2-P3b-b2e natural-ingress " +
                "timeout and cleanup evidence matrix; it does not run devices."
        dependsOn("assembleDebugAndroidTest")
        dependsOn(verifyR2P3bB2eNaturalIngressTimeoutSourcePolicy)
        val testApkDirectory =
            layout.buildDirectory.dir("outputs/apk/androidTest/debug")
        inputs.dir(testApkDirectory)
        doLast {
            val apkFile =
                resolveSingleApkOutput(testApkDirectory.get().asFile)
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
                check(dexBytes.isNotEmpty()) {
                    "R2-P3b-b2e androidTest APK contains no DEX entries: $apkFile"
                }
                val requiredMarkers = linkedMapOf(
                    "direct-host natural timeout method" to
                        "repeatedExact64NaturalIngressTimeoutReleasesCursorAndRecovers",
                    "Binder natural timeout method" to
                        "exact64NaturalDeadlinePublishesTimeoutCleansBinderStateAndRecovers",
                    "shared natural-timeout log tag" to "R2P3bNaturalTimeout",
                    "direct timeout cycle evidence" to "INGRESS_TIMEOUT_CYCLE_PASS",
                    "direct timeout matrix evidence" to "INGRESS_TIMEOUT_MATRIX_PASS",
                    "Binder timeout evidence" to "BINDER_TIMEOUT_PASS",
                    "partial exact64 ingress assertion" to
                        "Natural ingress timeout did not accept any exact input.",
                    "fixed Binder deadline assertion" to
                        "Natural ingress Binder timeout fired before its fixed deadline.",
                    "direct host cleanup assertion" to
                        "Natural-ingress timeout host close did not prove cleanup.",
                    "compiler identity cleanup assertion" to
                        "The compiler service process changed across natural ingress timeout cleanup.",
                    "remote transient cleanup evidence" to
                        "R2 natural-timeout terminal transient FD cleanup",
                    "small successor evidence" to "natural-timeout successor",
                )
                requiredMarkers.forEach { (label, markerText) ->
                    val marker = markerText.toByteArray(Charsets.UTF_8)
                    check(dexBytes.any { dex -> dex.containsByteSequence(marker) }) {
                        "R2-P3b-b2e androidTest APK is missing $label marker: $markerText"
                    }
                }
            }
        }
    }

val verifyR2P3bB2gNaturalPhaseCancellationSourcePolicy =
    tasks.register("verifyR2P3bB2gNaturalPhaseCancellationSourcePolicy") {
        group = "verification"
        description =
            "Verifies the R2-P3b-b2g ordinary-admission natural parse/semantic/emit " +
                "cancellation, exact termination receipt, cleanup, and successor policy."
        dependsOn(verifyR2P3bB2eNaturalIngressTimeoutSourcePolicy)
        dependsOn(verifyR2ProjectCompilerDesktop)
        inputs.files(
            r2ProjectCompilerInstrumentationSource,
            r2NaturalPhaseWorkloadFixtureSource,
            r2ProjectCompilerDesktopHarnessSource,
            r2CompilerWorkerSource,
            r2CompilerHostPageSource,
            r2CompilerHostProtocolSource,
            r2ProjectExecutionPhaseSource,
            compilerWebViewHostSource,
            r2ProjectFramedTransportTestSource,
        )
        doLast {
            fun requireMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-P3b-b2g $label is missing: $marker"
                    }
                }
            }

            fun sourceSection(
                label: String,
                source: String,
                startMarker: String,
                endMarker: String,
            ): String {
                val start = source.indexOf(startMarker)
                check(start >= 0) {
                    "R2-P3b-b2g $label start is missing: $startMarker"
                }
                val end = source.indexOf(endMarker, start + startMarker.length)
                check(end > start) {
                    "R2-P3b-b2g $label end is missing: $endMarker"
                }
                return source.substring(start, end)
            }

            fun requireOrderedMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                var cursor = 0
                markers.forEach { marker ->
                    val index = source.indexOf(marker, cursor)
                    check(index >= cursor) {
                        "R2-P3b-b2g $label ordering is missing: $marker"
                    }
                    cursor = index + marker.length
                }
            }

            val workloadSource =
                r2NaturalPhaseWorkloadFixtureSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "frozen natural-phase workload",
                source = workloadSource,
                markers = listOf(
                    "internal object R2NaturalPhaseWorkloadFixture",
                    "const val ROOT_COUNT = 192",
                    "const val RUNTIME_VALUE_COUNT_PER_ROOT = 16",
                    "const val TYPE_ALIAS_COUNT_PER_ROOT = 46",
                    "const val TRUSTED_DECLARATION_FILE_COUNT = 108",
                    "const val TRUSTED_DECLARATION_BYTES = 3_784_758L",
                    "const val TRUSTED_DECLARATION_TOKENS = 86_329",
                    "const val ROOT_BYTES = 810_241L",
                    "const val ROOT_TOKENS = 145_920",
                    "const val MAXIMUM_ROOT_TOKENS = 760",
                    "const val TOTAL_FILE_COUNT = 300",
                    "const val TOTAL_BYTES = 4_594_999L",
                    "const val TOTAL_TOKENS = 232_249",
                    "const val OUTPUT_FILE_COUNT = 384",
                    "const val JAVASCRIPT_OUTPUT_BYTES = 290_497L",
                    "const val SOURCE_MAP_OUTPUT_BYTES = 272_448L",
                    "const val OUTPUT_BYTES = 562_945L",
                    "\"2f4dc949984424bb6eff1d806efcac2fd21f4ae8ad87a0c44f03f2c086d1444d\"",
                    "R2ProjectSnapshotLimits.PROVISIONAL_WORKER",
                    "\"/sources/natural-phase-",
                    "repeat(RUNTIME_VALUE_COUNT_PER_ROOT)",
                    "repeat(TYPE_ALIAS_COUNT_PER_ROOT)",
                    "outputs.sortedBy(R2ProjectOutput::path)",
                    "digest.update(0.toByte())",
                ),
            )
            check(232_249L * 10L <= 262_144L * 9L) {
                "R2-P3b-b2g token authority exceeds 90% of the production Worker limit."
            }
            check("R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE" !in workloadSource) {
                "R2-P3b-b2g workload must not use expanded Worker admission."
            }

            val instrumentationSource =
                r2ProjectCompilerInstrumentationSource.asFile.readText(Charsets.UTF_8)
            val matrixMethod = sourceSection(
                label = "instrumentation matrix",
                source = instrumentationSource,
                startMarker =
                    "fun naturalParseSemanticEmitCancellationUsesRealProgressAndRecovers()",
                endMarker = "\n    @Test",
            )
            requireMarkers(
                label = "instrumentation matrix",
                source = matrixMethod,
                markers = listOf(
                    "R2NaturalPhaseWorkloadFixture.createSnapshot(",
                    "R2NaturalPhaseWorkloadFixture.TOTAL_FILE_COUNT",
                    "R2NaturalPhaseWorkloadFixture.TOTAL_BYTES",
                    "\"The natural-phase baseline did not compile:",
                    "baselineCompilation.diagnostics.isEmpty()",
                    "baselineCompilation.emitSkipped",
                    "R2NaturalPhaseWorkloadFixture.OUTPUT_FILE_COUNT",
                    "R2NaturalPhaseWorkloadFixture.JAVASCRIPT_OUTPUT_BYTES",
                    "R2NaturalPhaseWorkloadFixture.SOURCE_MAP_OUTPUT_BYTES",
                    "R2NaturalPhaseWorkloadFixture.OUTPUT_BYTES",
                    "R2NaturalPhaseWorkloadFixture.OUTPUT_AGGREGATE_SHA256",
                    "host.lastProjectExecutionPhaseProgressHistoryForTesting()",
                    "R2ProjectExecutionPhase.PARSE",
                    "R2ProjectExecutionPhase.SEMANTIC",
                    "R2ProjectExecutionPhase.EMIT",
                    "waitForExecutionPhaseProgress(",
                    "sequence = NATURAL_PHASE_STOP_PROGRESS_SEQUENCE",
                    "selectedProgress.completedUnits <",
                    "host.requestStop(",
                    "cause = R2ProjectStopCause.CANCELLED",
                    "TypeScriptCompilerContract.ERROR_CANCELLED",
                    "assertEquals(null, cancelled.compilation)",
                    "expectedComplete = true",
                    "host.lastProjectPhaseTerminationEvidenceForTesting()",
                    "evidence.phaseAtHostStopSelection",
                    "evidence.phaseAtPageTermination",
                    "evidence.enteredPhasesAtHostStopSelection",
                    "evidence.completedPhasesAtHostStopSelection",
                    "evidence.phaseProgressSequenceAtPageTermination",
                    "evidence.phaseProgressCompletedUnitsAtPageTermination",
                    "evidence.phaseProgressTotalUnitsAtPageTermination",
                    "evidence.workerGeneration",
                    "evidence.terminatedWorkerGeneration",
                    "evidence.replacementWorkerGeneration",
                    "evidence.terminatedWorkerGeneration + 1",
                    "\"Natural phase cancellation did not terminate a newer Worker.\"",
                    "\"NATURAL_PHASE_CANCEL_PASS ",
                    "\"workerSpinMilliseconds=0 workerSpinPhase=none ",
                    "workload.close()",
                    "snapshot = successorSnapshot",
                    "\"The natural-phase successor did not retire a newer session.\"",
                    "assertRetiredProjectSession(host.lifecycleTelemetryForTesting())",
                    "\"NATURAL_PHASE_CANCEL_MATRIX_PASS ",
                    "\"phases=3 roots=",
                    "\"ordinaryAdmission=true ",
                    "host.close()",
                    "\"Natural phase cancellation host close did not prove cleanup.\"",
                    "\"Natural phase cancellation cleanup left a project spool file.\"",
                    "file.name.startsWith(CAPACITY_SPOOL_FILE_PREFIX)",
                ),
            )
            check(
                Regex("""host\.compileProject\(""")
                    .findAll(matrixMethod)
                    .count() == 3,
            ) {
                "R2-P3b-b2g must use ordinary compileProject for baseline, faults, and successor."
            }
            check(
                Regex("""host\.requestStop\(""")
                    .findAll(matrixMethod)
                    .count() == 1,
            ) {
                "R2-P3b-b2g must have one looped, keyed cancellation selection."
            }
            listOf(
                "compileProjectWithExpandedWorkerCapacityForTesting",
                "compileProjectWithSynchronousWorkerSpinForTesting",
                "workerSpinMilliseconds =",
                "workerSpinPhase =",
                "SYNCHRONOUS_WORKER_SPIN",
                "R2ProjectStopCause.TIMED_OUT",
                "TypeScriptCompilerContract.ERROR_TIMEOUT",
                "PHASE_TIMEOUT",
                "NATURAL_INGRESS_TIMEOUT",
                "terminateActiveProjectRendererForTesting",
                "armRendererTerminationsForTesting",
            ).forEach { forbidden ->
                check(forbidden !in matrixMethod) {
                    "R2-P3b-b2g natural cancellation matrix contains forbidden control: $forbidden"
                }
            }
            requireMarkers(
                label = "bounded natural progress helpers",
                source = instrumentationSource,
                markers = listOf(
                    "private fun waitForExecutionPhaseProgress(",
                    "host.projectExecutionPhaseProgressForTesting(handle)",
                    "progress.phase == phase && progress.sequence == sequence",
                    "private fun expectedNaturalPhaseProgressHistory():",
                    "listOf(1, 2).map { sequence ->",
                    "private fun expectedNaturalPhaseCompletedUnits(",
                    "val divisor = if (sequence == 1) 8 else 4",
                    "return (totalUnits - 1) / divisor + 1",
                    "private fun expectedNaturalPhaseTotalUnits(",
                    "R2NaturalPhaseWorkloadFixture.ROOT_COUNT",
                    "R2NaturalPhaseWorkloadFixture.OUTPUT_FILE_COUNT",
                    "error(\"Natural TypeScript progress excludes ingress.\")",
                    "const val NATURAL_PHASE_STOP_PROGRESS_SEQUENCE = 2",
                    "const val NATURAL_PHASE_SPOOL_DIRECTORY = \"r2-p3b-natural-phase\"",
                    "const val NATURAL_PHASE_LOG_TAG = \"R2P3bNaturalPhase\"",
                ),
            )

            val desktopSource =
                r2ProjectCompilerDesktopHarnessSource.asFile.readText(Charsets.UTF_8)
            val desktopAuthority = sourceSection(
                label = "desktop workload authority",
                source = desktopSource,
                startMarker = "const naturalPhaseWorkloadAuthority = Object.freeze({",
                endMarker = "\nconst naturalPhaseSourceText",
            )
            requireMarkers(
                label = "desktop workload authority",
                source = desktopAuthority,
                markers = listOf(
                    "declarationBytes: 3_784_758",
                    "declarationFiles: 108",
                    "declarationTokens: 86_329",
                    "javascriptOutputBytes: 290_497",
                    "maximumRootTokens: 760",
                    "outputBytes: 562_945",
                    "outputFiles: 384",
                    "rootBytes: 810_241",
                    "rootFiles: 192",
                    "rootTokens: 145_920",
                    "runtimeValuesPerRoot: 16",
                    "sourceMapOutputBytes: 272_448",
                    "totalBytes: 4_594_999",
                    "totalFiles: 300",
                    "totalTokens: 232_249",
                    "typeAliasesPerRoot: 46",
                    "2f4dc949984424bb6eff1d806efcac2fd21f4ae8ad87a0c44f03f2c086d1444d",
                ),
            )
            val desktopGolden = sourceSection(
                label = "desktop natural-phase golden",
                source = desktopSource,
                startMarker = "const verifyP3bB2gNaturalPhaseProgressWorkload = async () => {",
                endMarker = "\nconst p3bB2hNaturalPhaseCalibrationBaselineRuns",
            )
            requireMarkers(
                label = "desktop natural-phase golden",
                source = desktopGolden,
                markers = listOf(
                    "countSourceTokens(file.text)",
                    "declarationTokens === authority.declarationTokens",
                    "rootTokens === authority.rootTokens",
                    "maximumRootTokens === authority.maximumRootTokens",
                    "declarationTokens + rootTokens === authority.totalTokens",
                    "authority.totalFiles <= 512",
                    "authority.totalBytes <= 8 * 1024 * 1024",
                    "authority.totalTokens <= Math.floor(",
                    "p3bWorkerLimits.maximumProjectTokens * 0.9",
                    "\"desktop-p3b-b2g-natural-phase-progress\"",
                    "JSON.stringify(phaseProgressEvents)",
                    "JSON.stringify(expectedProgress)",
                    "!result.emitSkipped && result.diagnostics.length === 0",
                    "result.outputs.length === authority.outputFiles",
                    "authority.javascriptOutputBytes",
                    "authority.sourceMapOutputBytes",
                    "authority.outputBytes",
                    "aggregate.digest(\"hex\") === authority.outputAggregateSha256",
                ),
            )
            val progressTuples =
                Regex(
                    """completedUnits:\s*(\d+),\s*phase:\s*"([^"]+)",\s*""" +
                        """sequence:\s*(\d+),\s*totalUnits:\s*(\d+),""",
                ).findAll(desktopGolden).map { match ->
                    "${match.groupValues[2]}:${match.groupValues[3]}:" +
                        "${match.groupValues[1]}:${match.groupValues[4]}"
                }.toList()
            check(
                progressTuples == listOf(
                    "parse:1:24:192",
                    "parse:2:48:192",
                    "semantic:1:24:192",
                    "semantic:2:48:192",
                    "emit:1:48:384",
                    "emit:2:96:384",
                ),
            ) {
                "R2-P3b-b2g desktop golden must retain exactly two real-work receipts per phase: " +
                    progressTuples
            }
            requireMarkers(
                label = "desktop TypeScript 6.0.3 execution hook",
                source = desktopSource,
                markers = listOf(
                    "const countSourceTokens = (text) => {",
                    "global.ts.createScanner(",
                    "global.ts.ScriptTarget.ES2018",
                    "global.ts.LanguageVariant.Standard",
                    "scanner.scan() !== global.ts.SyntaxKind.EndOfFileToken",
                    "\"P3b-b2g natural parse/semantic/emit progress workload\"",
                    "await verifyP3bB2gNaturalPhaseProgressWorkload();",
                ),
            )

            val workerSource = r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "Worker real-work progress protocol",
                source = workerSource,
                markers = listOf(
                    "const minimumProjectExecutionProgressUnits = 8;",
                    "const advanceProjectExecutionProgress = (execution, phase) => {",
                    "progress.completedUnits += 1;",
                    "progress.nextSequence > 2",
                    "const divisor = progress.nextSequence === 1 ? 8 : 4;",
                    "const threshold = Math.ceil(progress.totalUnits / divisor);",
                    "progress.completedUnits !== threshold",
                    "kind: \"projectPhaseProgress\"",
                    "sequence: progress.nextSequence",
                    "totalUnits: progress.totalUnits",
                    "progress.nextSequence += 1;",
                    "\"parse\"",
                    "\"semantic\"",
                    "\"emit\"",
                    "vfs.sourcePaths.length * 2",
                ),
            )
            requireOrderedMarkers(
                label = "parse work before progress",
                source = workerSource,
                markers = listOf(
                    "sourceFileCache.set(fileName, sourceFile);",
                    "advanceProjectExecutionProgress(execution, \"parse\");",
                ),
            )
            requireOrderedMarkers(
                label = "semantic work before progress",
                source = workerSource,
                markers = listOf(
                    "program.getSemanticDiagnostics(sourceFile)",
                    "advanceProjectExecutionProgress(",
                    "\"semantic\"",
                ),
            )
            requireOrderedMarkers(
                label = "emit work before progress",
                source = workerSource,
                markers = listOf(
                    "outputs.set(path, {",
                    "advanceProjectExecutionProgress(execution, \"emit\");",
                ),
            )
            val inputStartFrame = sourceSection(
                label = "Worker input start frame",
                source = workerSource,
                startMarker =
                    "const acceptProjectInputStart = async (message, frameToken) => {",
                endMarker = "\nconst acceptProjectInputFileStart",
            )
            val inputStartKeys =
                Regex(""""([^"]+)"""")
                    .findAll(
                        inputStartFrame.substringAfter("const legacyKeys = [")
                            .substringBefore("];"),
                    )
                    .map { match -> match.groupValues[1] }
                    .toList()
            check(
                inputStartKeys == listOf(
                    "callerInventoryFingerprint",
                    "configKind",
                    "configSchemaRevision",
                    "declarationByteLength",
                    "declarationFileCount",
                    "declarationInventoryFingerprint",
                    "declarationProfileFingerprint",
                    "fileCount",
                    "inputArchiveSchemaRevision",
                    "inputQuotaRevision",
                    "kind",
                    "largestFileBytes",
                    "profileRevision",
                    "profileSchemaRevision",
                    "requestId",
                    "retainedFileCount",
                    "retainedInventoryFingerprint",
                    "sequence",
                    "targetProfile",
                    "totalBytes",
                    "workerSpinMilliseconds",
                    "workerSpinPhase",
                ),
            ) {
                "R2-P3b-b2g formal-v1 input authority or natural-progress/debug controls " +
                    "drifted: " +
                    inputStartKeys
            }

            val pageSource = r2CompilerHostPageSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "host-page progress validation and termination receipt",
                source = pageSource,
                markers = listOf(
                    "case \"projectPhaseProgress\":",
                    "handleWorkerProjectPhaseProgress(message, createdGeneration);",
                    "const handleWorkerProjectPhaseProgress = (message, messageGeneration) => {",
                    "\"completedUnits\"",
                    "\"phase\"",
                    "\"requestId\"",
                    "\"sequence\"",
                    "\"totalUnits\"",
                    "message.sequence !== expectedSequence",
                    "message.sequence < 1",
                    "message.sequence > 2",
                    "message.completedUnits !== Math.ceil(message.totalUnits / divisor)",
                    "active.phaseProgressCompletedUnits = message.completedUnits;",
                    "active.phaseProgressSequence = message.sequence;",
                    "active.phaseProgressTotalUnits = message.totalUnits;",
                    "activeProject.phaseProgressSequence === 2",
                    "phaseAtTermination:",
                    "phaseProgressSequenceAtTermination:",
                    "phaseProgressCompletedUnitsAtTermination:",
                    "phaseProgressTotalUnitsAtTermination:",
                    "kind: \"workerTerminated\"",
                ),
            )
            requireOrderedMarkers(
                label = "page termination receipt before exact Worker replacement",
                source = pageSource,
                markers = listOf(
                    "const hasNaturalPhaseReceipt =",
                    "const terminated = {",
                    "activeProject = null;",
                    "spawnReplacementWorker(terminated);",
                ),
            )

            val protocolSource =
                r2CompilerHostProtocolSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "strict native progress and termination parser",
                source = protocolSource,
                markers = listOf(
                    "\"projectPhaseProgress\" -> {",
                    "message.requireExactKeys(",
                    "val sequence = message.requirePositiveInt(\"sequence\")",
                    "val completedUnits = message.requirePositiveInt(\"completedUnits\")",
                    "val totalUnits = message.requirePositiveInt(\"totalUnits\")",
                    "validateProjectPhaseProgress(",
                    "data class ProjectPhaseProgress(",
                    "replacementGeneration == Math.addExact(terminatedGeneration, 1)",
                    "if (phaseAtTermination == null)",
                    "phaseProgressSequenceAtTermination == 0",
                    "phaseProgressCompletedUnitsAtTermination == 0",
                    "phaseProgressTotalUnitsAtTermination == 0",
                    "check(phaseProgressSequenceAtTermination == 2)",
                    "data class WorkerTerminated(",
                    "private const val MIN_PROJECT_PHASE_PROGRESS_UNITS = 8",
                    "private const val MAX_PROJECT_PHASE_PROGRESS_UNITS = 2 * 4_607",
                ),
            )

            val phaseSource =
                r2ProjectExecutionPhaseSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "native atomic termination evidence model",
                source = phaseSource,
                markers = listOf(
                    "internal data class R2ProjectExecutionPhaseProgress(",
                    "internal data class R2ProjectPhaseTerminationEvidence(",
                    "val requestId: String",
                    "val stopCause: R2ProjectStopCause",
                    "val phaseAtHostStopSelection: R2ProjectExecutionPhase",
                    "val enteredPhasesAtHostStopSelection: List<R2ProjectExecutionPhase>",
                    "val completedPhasesAtHostStopSelection: List<R2ProjectExecutionPhase>",
                    "val sessionGeneration: Int",
                    "val workerGeneration: Int",
                    "val phaseAtPageTermination: R2ProjectExecutionPhase",
                    "val phaseProgressSequenceAtPageTermination: Int",
                    "val phaseProgressCompletedUnitsAtPageTermination: Int",
                    "val phaseProgressTotalUnitsAtPageTermination: Int",
                    "val terminatedWorkerGeneration: Int",
                    "val replacementWorkerGeneration: Int",
                ),
            )

            val nativeHostSource =
                compilerWebViewHostSource.asFile.readText(Charsets.UTF_8)
            val ordinaryCompileRegion = sourceSection(
                label = "ordinary compile zero-spin dispatch",
                source = nativeHostSource,
                startMarker = "internal fun compileProject(\n        snapshot:",
                endMarker = "\n    internal fun compileProjectWithSynchronousWorkerSpinForTesting",
            )
            check(
                Regex("""workerSpinMilliseconds\s*=\s*0""")
                    .findAll(ordinaryCompileRegion)
                    .count() == 3 &&
                    Regex("""workerSpinPhase\s*=\s*null""")
                        .findAll(ordinaryCompileRegion)
                        .count() == 3,
            ) {
                "R2-P3b-b2g ordinary relative-timeout and absolute-deadline compile " +
                    "overloads must retain zero spin and no spin phase."
            }
            requireMarkers(
                label = "native progress, atomic stop selection, and receipt correlation",
                source = nativeHostSource,
                markers = listOf(
                    "internal fun projectExecutionPhaseProgressForTesting(",
                    "internal fun lastProjectExecutionPhaseProgressHistoryForTesting():",
                    "internal fun lastProjectPhaseTerminationEvidenceForTesting():",
                    "is CompilerHostMessage.ProjectPhaseProgress ->",
                    "handleProjectPhaseProgressOnMain(message)",
                    "private fun handleProjectPhaseProgressOnMain(",
                    "synchronized(stateLock)",
                    "message.sequence == expectedSequence",
                    "message.sequence in 1..2",
                    "pending.activeProjectExecutionPhaseProgress =",
                    "pending.projectExecutionPhaseProgressHistory +=",
                    "selectAcceptedProjectStopLocked(candidate)",
                    "private fun selectAcceptedProjectStopLocked(",
                    "pending.projectStopSelection = ProjectStopSelection(",
                    "activePhase = pending.activeProjectExecutionPhase",
                    "activeProgress =",
                    "pending.activeProjectExecutionPhaseProgress",
                    "enteredPhases =",
                    "pending.enteredProjectExecutionPhases.toList()",
                    "completedPhases =",
                    "pending.completedProjectExecutionPhases.toList()",
                    "private fun projectPhaseTerminationEvidence(",
                    "selectedProgress.sequence != 2",
                    "message.phaseAtTermination != selectedPhase",
                    "selectedWorkerGeneration != message.terminatedGeneration",
                    "R2ProjectPhaseTerminationEvidence(",
                    "replacementWorkerGeneration = message.replacementGeneration",
                    "lastProjectPhaseTerminationEvidenceForTestingValue = evidence",
                    "completeStoppedProjectAfterTeardownOnMain(pending)",
                ),
            )
            requireOrderedMarkers(
                label = "atomic host stop snapshot",
                source = nativeHostSource,
                markers = listOf(
                    "val pending = synchronized(stateLock) {",
                    "selectAcceptedProjectStopLocked(candidate)",
                    "private fun selectAcceptedProjectStopLocked(",
                    "pending.stopCause = acceptedCause",
                    "pending.projectStopSelection = ProjectStopSelection(",
                    "activePhase = pending.activeProjectExecutionPhase",
                    "activeProgress =",
                    "pending.activeProjectExecutionPhaseProgress",
                ),
            )
            requireOrderedMarkers(
                label = "correlated evidence before stopped completion",
                source = nativeHostSource,
                markers = listOf(
                    "private fun handleWorkerTerminatedOnMain(",
                    "val evidence = projectPhaseTerminationEvidence(",
                    "candidate.projectPhaseTerminationEvidence = evidence",
                    "candidate to evidence",
                    "val (pending, evidence) = pendingAndEvidence",
                    "lastProjectPhaseTerminationEvidenceForTestingValue = evidence",
                    "completeStoppedProjectAfterTeardownOnMain(pending)",
                ),
            )

            val parserTestSource =
                r2ProjectFramedTransportTestSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "strict parser unit coverage",
                source = parserTestSource,
                markers = listOf(
                    "fun framedControlParserIsStrictAndDecodesChunksOffTheMainThreadBoundary()",
                    "\"kind\":\"projectPhaseProgress\"",
                    "\"phaseAtTermination\":\"none\"",
                    "\"phaseAtTermination\":\"semantic\"",
                    "\"phaseProgressSequenceAtTermination\":2",
                    "\"phaseProgressCompletedUnitsAtTermination\":48",
                    "\"phaseProgressTotalUnitsAtTermination\":192",
                    "\"replacementGeneration\":5",
                    "\"sequence\":2,\"completedUnits\":47",
                ),
            )

            val allB2gSources = listOf(
                workloadSource,
                instrumentationSource,
                desktopSource,
                workerSource,
                pageSource,
                protocolSource,
                phaseSource,
                nativeHostSource,
                parserTestSource,
            )
            check(
                allB2gSources.none { source ->
                    "workerNaturalPhaseProgressEvidence" in source
                },
            ) {
                "R2-P3b-b2g must not add a natural-progress evidence flag or start-frame bypass."
            }
        }
    }

val verifyR2P3bB2gNaturalPhaseCancellationAndroidTestApk =
    tasks.register("verifyR2P3bB2gNaturalPhaseCancellationAndroidTestApk") {
        group = "verification"
        description =
            "Verifies that the paired debug app/androidTest APKs contain the R2-P3b-b2g " +
                "ordinary natural phase cancellation and exact cleanup evidence matrix; " +
                "it does not run devices."
        dependsOn("assembleDebug", "assembleDebugAndroidTest")
        dependsOn(verifyR2P3bB2gNaturalPhaseCancellationSourcePolicy)
        val appApkDirectory =
            layout.buildDirectory.dir("outputs/apk/debug")
        val testApkDirectory =
            layout.buildDirectory.dir("outputs/apk/androidTest/debug")
        inputs.dir(appApkDirectory)
        inputs.dir(testApkDirectory)
        doLast {
            fun readDexBytes(apkFile: File): List<ByteArray> =
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

            val appApkFile =
                resolveSingleApkOutput(appApkDirectory.get().asFile)
            val appDexBytes = readDexBytes(appApkFile)
            check(appDexBytes.isNotEmpty()) {
                "R2-P3b-b2g debug app APK contains no DEX entries: $appApkFile"
            }
            val appMarkers = linkedMapOf(
                "phase progress telemetry type" to
                    "R2ProjectExecutionPhaseProgress",
                "phase termination evidence type" to
                    "R2ProjectPhaseTerminationEvidence",
                "active progress accessor" to
                    "projectExecutionPhaseProgressForTesting",
                "terminal progress history accessor" to
                    "lastProjectExecutionPhaseProgressHistoryForTesting",
                "termination receipt accessor" to
                    "lastProjectPhaseTerminationEvidenceForTesting",
                "framed progress kind" to "projectPhaseProgress",
                "termination progress receipt" to
                    "phaseProgressSequenceAtTermination",
            )
            appMarkers.forEach { (label, markerText) ->
                val marker = markerText.toByteArray(Charsets.UTF_8)
                check(appDexBytes.any { dex -> dex.containsByteSequence(marker) }) {
                    "R2-P3b-b2g debug app APK is missing $label marker: $markerText"
                }
            }

            val testApkFile =
                resolveSingleApkOutput(testApkDirectory.get().asFile)
            val testDexBytes = readDexBytes(testApkFile)
            check(testDexBytes.isNotEmpty()) {
                "R2-P3b-b2g androidTest APK contains no DEX entries: $testApkFile"
            }
                val requiredMarkers = linkedMapOf(
                    "natural-phase instrumentation class" to
                        "R2ProjectCompilerInstrumentationTest",
                    "natural-phase workload fixture" to
                        "R2NaturalPhaseWorkloadFixture",
                    "natural cancellation matrix method" to
                        "naturalParseSemanticEmitCancellationUsesRealProgressAndRecovers",
                    "natural progress wait helper" to
                        "waitForExecutionPhaseProgress",
                    "natural progress history helper" to
                        "expectedNaturalPhaseProgressHistory",
                    "phase progress telemetry type" to
                        "R2ProjectExecutionPhaseProgress",
                    "phase termination evidence type" to
                        "R2ProjectPhaseTerminationEvidence",
                    "active progress accessor" to
                        "projectExecutionPhaseProgressForTesting",
                    "terminal progress history accessor" to
                        "lastProjectExecutionPhaseProgressHistoryForTesting",
                    "termination receipt accessor" to
                        "lastProjectPhaseTerminationEvidenceForTesting",
                    "natural phase source namespace" to
                        "/sources/natural-phase-",
                    "natural phase spool namespace" to
                        "r2-p3b-natural-phase",
                    "natural phase log tag" to "R2P3bNaturalPhase",
                    "per-phase cancellation evidence" to
                        "NATURAL_PHASE_CANCEL_PASS",
                    "three-phase matrix evidence" to
                        "NATURAL_PHASE_CANCEL_MATRIX_PASS",
                    "ordinary zero-spin evidence" to
                        "workerSpinMilliseconds=0 workerSpinPhase=none",
                    "ordinary admission evidence" to "ordinaryAdmission=true",
                    "baseline compile assertion" to
                        "The natural-phase baseline did not compile",
                    "incomplete natural progress assertion" to
                        "progress reached completion.",
                    "newer Worker termination assertion" to
                        "Natural phase cancellation did not terminate a newer Worker.",
                    "small successor assertion" to
                        "The natural-phase successor did not retire a newer session.",
                    "host cleanup assertion" to
                        "Natural phase cancellation host close did not prove cleanup.",
                    "spool cleanup assertion" to
                        "Natural phase cancellation cleanup left a project spool file.",
                    "frozen output aggregate" to
                        "2f4dc949984424bb6eff1d806efcac2fd21f4ae8ad87a0c44f03f2c086d1444d",
            )
            requiredMarkers.forEach { (label, markerText) ->
                val marker = markerText.toByteArray(Charsets.UTF_8)
                check(testDexBytes.any { dex -> dex.containsByteSequence(marker) }) {
                    "R2-P3b-b2g androidTest APK is missing $label marker: $markerText"
                }
            }
        }
    }

val verifyR2P3bB2hNaturalPhaseTimeoutSourcePolicy =
    tasks.register("verifyR2P3bB2hNaturalPhaseTimeoutSourcePolicy") {
        group = "verification"
        description =
            "Verifies the R2-P3b-b2h ordinary request-start absolute-deadline " +
                "semantic timeout calibration, exact teardown, cleanup, " +
                "and successor policy."
        dependsOn(verifyR2P3bB2gNaturalPhaseCancellationSourcePolicy)
        dependsOn(verifyR2ProjectCompilerDesktop)
        dependsOn("testDebugUnitTest")
        inputs.files(
            r2ProjectCompilerInstrumentationSource,
            r2AbsoluteDeadlineRegressionInstrumentationSource,
            r2NaturalPhaseTimeoutWorkloadFixtureSource,
            r2ProjectFramedTransportTestSource,
            r2ProjectCompilerDesktopHarnessSource,
            r2CompilerWorkerSource,
            r2CompilerHostPageSource,
            r2ProjectExecutionPhaseSource,
            r2HostProjectOperationSource,
            compilerWebViewHostSource,
        )
        doLast {
            fun requireMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-P3b-b2h $label is missing: $marker"
                    }
                }
            }

            fun sourceSection(
                label: String,
                source: String,
                startMarker: String,
                endMarker: String,
            ): String {
                val start = source.indexOf(startMarker)
                check(start >= 0) {
                    "R2-P3b-b2h $label start is missing: $startMarker"
                }
                val end = source.indexOf(endMarker, start + startMarker.length)
                check(end > start) {
                    "R2-P3b-b2h $label end is missing: $endMarker"
                }
                return source.substring(start, end)
            }

            fun requireOrderedMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                var cursor = 0
                markers.forEach { marker ->
                    val index = source.indexOf(marker, cursor)
                    check(index >= cursor) {
                        "R2-P3b-b2h $label ordering is missing: $marker"
                    }
                    cursor = index + marker.length
                }
            }

            val instrumentationSource =
                r2ProjectCompilerInstrumentationSource.asFile.readText(Charsets.UTF_8)
            val timeoutWorkloadSource =
                r2NaturalPhaseTimeoutWorkloadFixtureSource.asFile
                    .readText(Charsets.UTF_8)
            fun frozenTimeoutWorkloadLong(name: String): Long {
                val match = Regex(
                    """const val $name = ([0-9_]+)L?""",
                ).find(timeoutWorkloadSource)
                check(match != null) {
                    "R2-P3b-b2h timeout workload constant is missing: $name"
                }
                return match.groupValues[1].replace("_", "").toLong()
            }
            val frozenTimeoutWorkloadAuthority = linkedMapOf(
                "ROOT_COUNT" to 256L,
                "LIGHT_ROOT_COUNT" to 64L,
                "HELPER_COUNT" to 148L,
                "HELPER_ATOM_COUNT" to 72L,
                "HEAVY_PRIVATE_FIELD_COUNT" to 8L,
                "TRUSTED_DECLARATION_FILE_COUNT" to 108L,
                "TRUSTED_DECLARATION_BYTES" to 3_784_758L,
                "TRUSTED_DECLARATION_TOKENS" to 86_329L,
                "ROOT_BYTES" to 178_722L,
                "ROOT_TOKENS" to 36_928L,
                "MAXIMUM_ROOT_TOKENS" to 169L,
                "HELPER_BYTES" to 3_763_492L,
                "HELPER_TOKENS" to 28_268L,
                "MAXIMUM_HELPER_TOKENS" to 191L,
                "TOTAL_FILE_COUNT" to 512L,
                "TOTAL_BYTES" to 7_726_972L,
                "TOTAL_TOKENS" to 151_525L,
                "OUTPUT_FILE_COUNT" to 512L,
                "JAVASCRIPT_OUTPUT_BYTES" to 535_202L,
                "SOURCE_MAP_OUTPUT_BYTES" to 185_664L,
                "OUTPUT_BYTES" to 720_866L,
            )
            frozenTimeoutWorkloadAuthority.forEach { (name, expected) ->
                check(frozenTimeoutWorkloadLong(name) == expected) {
                    "R2-P3b-b2h timeout workload authority drifted: $name"
                }
            }
            check(
                frozenTimeoutWorkloadAuthority.getValue("ROOT_COUNT") +
                    frozenTimeoutWorkloadAuthority.getValue("HELPER_COUNT") +
                    frozenTimeoutWorkloadAuthority.getValue(
                        "TRUSTED_DECLARATION_FILE_COUNT",
                    ) ==
                    frozenTimeoutWorkloadAuthority.getValue("TOTAL_FILE_COUNT"),
            )
            check(
                frozenTimeoutWorkloadAuthority.getValue("ROOT_BYTES") +
                    frozenTimeoutWorkloadAuthority.getValue("HELPER_BYTES") +
                    frozenTimeoutWorkloadAuthority.getValue(
                        "TRUSTED_DECLARATION_BYTES",
                    ) ==
                    frozenTimeoutWorkloadAuthority.getValue("TOTAL_BYTES"),
            )
            check(
                frozenTimeoutWorkloadAuthority.getValue("ROOT_TOKENS") +
                    frozenTimeoutWorkloadAuthority.getValue("HELPER_TOKENS") +
                    frozenTimeoutWorkloadAuthority.getValue(
                        "TRUSTED_DECLARATION_TOKENS",
                    ) ==
                    frozenTimeoutWorkloadAuthority.getValue("TOTAL_TOKENS"),
            )
            check(
                frozenTimeoutWorkloadAuthority.getValue(
                    "JAVASCRIPT_OUTPUT_BYTES",
                ) +
                    frozenTimeoutWorkloadAuthority.getValue(
                        "SOURCE_MAP_OUTPUT_BYTES",
                    ) ==
                    frozenTimeoutWorkloadAuthority.getValue("OUTPUT_BYTES"),
            )
            check(
                frozenTimeoutWorkloadAuthority.getValue("LIGHT_ROOT_COUNT") * 4L ==
                    frozenTimeoutWorkloadAuthority.getValue("ROOT_COUNT") &&
                    frozenTimeoutWorkloadAuthority.getValue("ROOT_COUNT") * 2L ==
                    frozenTimeoutWorkloadAuthority.getValue("OUTPUT_FILE_COUNT") &&
                    frozenTimeoutWorkloadAuthority.getValue("TOTAL_FILE_COUNT") <=
                    512L &&
                    frozenTimeoutWorkloadAuthority.getValue("TOTAL_BYTES") <=
                    8L * 1024L * 1024L &&
                    frozenTimeoutWorkloadAuthority.getValue("TOTAL_TOKENS") <=
                    262_144L &&
                    frozenTimeoutWorkloadAuthority.getValue("OUTPUT_FILE_COUNT") <=
                    512L &&
                    frozenTimeoutWorkloadAuthority.getValue("OUTPUT_BYTES") <=
                    1024L * 1024L,
            ) {
                "R2-P3b-b2h timeout workload escaped ordinary admission limits."
            }
            requireMarkers(
                label = "frozen semantic-timeout workload",
                source = timeoutWorkloadSource,
                markers = listOf(
                    "request-start natural semantic timeout evidence",
                    "const val HELPER_WHITESPACE_BYTES = 24 * 1024",
                    "\"4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1\"",
                    "R2ProjectSnapshotLimits.PROVISIONAL_WORKER",
                    "\"/sources/natural-timeout-phase-\"",
                    "\"/sources/natural-timeout-helper-\"",
                    "import type { NaturalTimeoutMatrix",
                    "satisfies NaturalTimeoutMatrix",
                    "repeat(HEAVY_PRIVATE_FIELD_COUNT)",
                    "export type NaturalTimeoutCross",
                    "A extends A ? B extends B ? readonly [A, B] : never : never;",
                    "repeat(HELPER_WHITESPACE_BYTES)",
                    "(rootIndex - LIGHT_ROOT_COUNT) % HELPER_COUNT",
                ),
            )
            check("R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE" !in timeoutWorkloadSource) {
                "R2-P3b-b2h timeout workload must use only ordinary Worker admission."
            }
            val timeoutMatrix = sourceSection(
                label = "natural phase timeout matrix",
                source = instrumentationSource,
                startMarker =
                    "fun naturalSemanticRequestStartAbsoluteDeadlineTimeoutUsesCalibratedWindowAndRecovers()",
                endMarker = "\n    @Test",
            )
            requireMarkers(
                label = "natural phase timeout matrix",
                source = timeoutMatrix,
                markers = listOf(
                    "R2NaturalPhaseTimeoutWorkloadFixture.createSnapshot(",
                    "R2NaturalPhaseTimeoutWorkloadFixture.TOTAL_FILE_COUNT",
                    "R2NaturalPhaseTimeoutWorkloadFixture.TOTAL_BYTES",
                    "R2ProjectExecutionPhase.PARSE",
                    "R2ProjectExecutionPhase.SEMANTIC",
                    "R2ProjectExecutionPhase.EMIT",
                    "val timeoutPhases = listOf(R2ProjectExecutionPhase.SEMANTIC)",
                    "repeat(NATURAL_PHASE_TIMEOUT_CALIBRATION_RUNS)",
                    "TypeScriptCompilerContract.MAX_TIMEOUT_MS",
                    "R2NaturalPhaseTimeoutWorkloadFixture.OUTPUT_FILE_COUNT",
                    "R2NaturalPhaseTimeoutWorkloadFixture.OUTPUT_BYTES",
                    ".OUTPUT_AGGREGATE_SHA256",
                    "expectedNaturalPhaseTimeoutProgressHistory()",
                    "host.lastProjectExecutionPhaseTimingEvidenceForTesting()",
                    "R2ProjectExecutionPhaseTimingEvidence::phase",
                    "NATURAL_PHASE_TIMEOUT_BASELINE_PASS",
                    "phases = timeoutPhases",
                    "check(NATURAL_PHASE_TIMEOUT_FAULT_ATTEMPTS == 1)",
                    "timeoutPhases.forEach { phase ->",
                    "val calibration = requireNotNull(calibrations[phase])",
                    "handle = handle",
                    "snapshot = workload",
                    "timeoutMs = calibration.timeoutMillis",
                    "TypeScriptCompilerContract.ERROR_TIMEOUT",
                    "assertEquals(null, timedOut.compilation)",
                    "timedOut.elapsedMs >= calibration.timeoutMillis",
                    "wallElapsedMillis >= calibration.timeoutMillis",
                    "expectedComplete = true",
                    "host.lastProjectPhaseTerminationEvidenceForTesting()",
                    "R2ProjectStopCause.TIMED_OUT",
                    "evidence.phaseAtHostStopSelection",
                    "evidence.phaseAtPageTermination",
                    "evidence.enteredPhasesAtHostStopSelection",
                    "evidence.completedPhasesAtHostStopSelection",
                    "evidence.phaseProgressSequenceAtPageTermination",
                    "evidence.phaseProgressCompletedUnitsAtPageTermination",
                    "evidence.phaseProgressTotalUnitsAtPageTermination",
                    "evidence.stopSelectedAtElapsedRealtimeNanos >=",
                    "evidence.deadlineAtElapsedRealtimeNanos",
                    "evidence.terminatedWorkerGeneration + 1",
                    "NATURAL_PHASE_TIMEOUT_PASS",
                    "workerSpinMilliseconds=0 workerSpinPhase=none",
                    "snapshot = successorSnapshot",
                    "The natural-phase timeout successor did not retire a newer session.",
                    "Natural phase timeout host close did not prove cleanup.",
                    "Natural phase timeout cleanup left a project spool file.",
                    "NATURAL_PHASE_TIMEOUT_MATRIX_PASS",
                    "\"phases=1 calibrationRuns=\"",
                    "ordinaryAdmission=true requestStartAbsoluteDeadline=true",
                    "hostCleanupProven=true spoolCleanupProven=true",
                ),
            )
            requireOrderedMarkers(
                label = "freeze calibration before unattended fault matrix",
                source = timeoutMatrix,
                markers = listOf(
                    "repeat(NATURAL_PHASE_TIMEOUT_CALIBRATION_RUNS)",
                    "calibrations = calibrateNaturalPhaseDeadlines(",
                    "phases = timeoutPhases",
                    "check(NATURAL_PHASE_TIMEOUT_FAULT_ATTEMPTS == 1)",
                    "timeoutPhases.forEach { phase ->",
                    "val calibration = requireNotNull(calibrations[phase])",
                    "val timedOut = executor.submit(",
                    "host.compileProject(",
                    "timeoutMs = calibration.timeoutMillis",
                ),
            )
            check(
                Regex("""host\.compileProject\(""")
                    .findAll(timeoutMatrix)
                    .count() == 3,
            ) {
                "R2-P3b-b2h must use ordinary compileProject exactly for baseline, " +
                    "the looped fixed deadline, and the successor."
            }
            check(
                Regex("""host\.requestStop\(""")
                    .findAll(timeoutMatrix)
                    .count() == 0,
            ) {
                "R2-P3b-b2h must not select timeout through the cancellation API."
            }
            listOf(
                "armProjectAbsoluteDeadlineForTesting",
                "rebaseProjectAbsoluteDeadlineForTesting",
                "shortenProjectAbsoluteDeadlineForTesting",
                "setProjectAbsoluteDeadlineForTesting",
                "compileProjectWithExpandedWorkerCapacityForTesting",
                "compileProjectWithSynchronousWorkerSpinForTesting",
                "workerSpinMilliseconds =",
                "workerSpinPhase =",
                "SYNCHRONOUS_WORKER_SPIN",
                "terminateActiveProjectRendererForTesting",
                "armRendererTerminationsForTesting",
                "waitForExecutionPhaseProgress(",
                "projectExecutionPhaseProgressForTesting(",
                "R2ProjectStopCause.CANCELLED",
                "TypeScriptCompilerContract.ERROR_CANCELLED",
                "Thread.sleep(",
                "SystemClock.sleep(",
                "NATURAL_INGRESS_TIMEOUT",
            ).forEach { forbidden ->
                check(forbidden !in timeoutMatrix) {
                    "R2-P3b-b2h timeout matrix contains forbidden mid-flight control: $forbidden"
                }
            }

            val calibrationHelper = sourceSection(
                label = "three-baseline calibration helper",
                source = instrumentationSource,
                startMarker = "private fun calibrateNaturalPhaseDeadlines(",
                endMarker = "\n    private data class NaturalPhaseDeadlineCalibration",
            )
            requireMarkers(
                label = "three-baseline calibration helper",
                source = calibrationHelper,
                markers = listOf(
                    "NATURAL_PHASE_TIMEOUT_CALIBRATION_RUNS",
                    "samples.maxOf",
                    "sequenceTwoElapsedRealtimeNanosSinceProjectStart",
                    "+ 1L",
                    "samples.minOf",
                    "completedElapsedRealtimeNanosSinceProjectStart -",
                    "1L",
                    "maximumTimeoutMillis - minimumTimeoutMillis >=",
                    "NATURAL_PHASE_TIMEOUT_MINIMUM_MARGIN_MILLIS * 2L",
                    "(maximumTimeoutMillis - minimumTimeoutMillis) / 2L",
                    "leftMarginMillis >=",
                    "rightMarginMillis >=",
                    "sequenceTwoElapsedRealtimeNanosSinceProjectStart <",
                    "timeoutNanos <",
                    "completedElapsedRealtimeNanosSinceProjectStart",
                    "NATURAL_PHASE_TIMEOUT_WINDOW_PASS",
                ),
            )
            requireMarkers(
                label = "frozen calibration constants",
                source = instrumentationSource,
                markers = listOf(
                    "const val NATURAL_PHASE_TIMEOUT_CALIBRATION_RUNS = 3",
                    "const val NATURAL_PHASE_TIMEOUT_FAULT_ATTEMPTS = 1",
                    "const val NATURAL_PHASE_TIMEOUT_MINIMUM_MARGIN_MILLIS = 100L",
                    "const val NATURAL_PHASE_TIMEOUT_PROGRESS_SEQUENCE = 2",
                    "const val NATURAL_PHASE_TIMEOUT_SPOOL_DIRECTORY =",
                    "\"r2-p3b-natural-phase-timeout\"",
                    "const val NATURAL_PHASE_TIMEOUT_LOG_TAG =",
                    "\"R2P3bNaturalPhaseTimeout\"",
                ),
            )

            val cancellationMatrix = sourceSection(
                label = "historical cancellation matrix",
                source = instrumentationSource,
                startMarker =
                    "fun naturalParseSemanticEmitCancellationUsesRealProgressAndRecovers()",
                endMarker = "\n    @Test",
            )
            requireMarkers(
                label = "cancellation/timeout terminal distinction",
                source = cancellationMatrix,
                markers = listOf(
                    "host.requestStop(",
                    "cause = R2ProjectStopCause.CANCELLED",
                    "TypeScriptCompilerContract.ERROR_CANCELLED",
                    "NATURAL_PHASE_CANCEL_PASS",
                    "NATURAL_PHASE_CANCEL_MATRIX_PASS",
                ),
            )
            check("R2ProjectStopCause.TIMED_OUT" !in cancellationMatrix) {
                "R2-P3b-b2g cancellation evidence was conflated with b2h timeout."
            }

            val regressionSource =
                r2AbsoluteDeadlineRegressionInstrumentationSource.asFile
                    .readText(Charsets.UTF_8)
            requireMarkers(
                label = "absolute deadline interrupt regression",
                source = regressionSource,
                markers = listOf(
                    "class R2AbsoluteDeadlineRegressionInstrumentationTest",
                    "fun interruptedDeadlineWaitDoesNotTimeoutEarlyAndRestoresInterruptStatus()",
                    "host.holdProjectUntilCloseForTesting(",
                    "const val ABSOLUTE_DEADLINE_MILLIS = 1_500L",
                    "const val EARLY_RETURN_OBSERVATION_MILLIS = 250L",
                    "compilerThread.interrupt()",
                    "returned.await(EARLY_RETURN_OBSERVATION_MILLIS, TimeUnit.MILLISECONDS)",
                    "TypeScriptCompilerContract.ERROR_TIMEOUT",
                    "timedOut.elapsedMs >= ABSOLUTE_DEADLINE_MILLIS",
                    "interruptRestored.get()",
                    "assertNull(host.lastProjectPhaseTerminationEvidenceForTesting())",
                ),
            )

            val phaseSource =
                r2ProjectExecutionPhaseSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "native timing and timeout evidence model",
                source = phaseSource,
                markers = listOf(
                    "internal data class R2ProjectExecutionPhaseTimingEvidence(",
                    "val sequenceTwoElapsedRealtimeNanosSinceProjectStart: Long",
                    "val completedElapsedRealtimeNanosSinceProjectStart: Long",
                    "internal data class R2ProjectPhaseTerminationEvidence(",
                    "val stopCause: R2ProjectStopCause",
                    "val deadlineAtElapsedRealtimeNanos: Long",
                    "val stopSelectedAtElapsedRealtimeNanos: Long",
                ),
            )

            val nativeHostSource =
                compilerWebViewHostSource.asFile.readText(Charsets.UTF_8)
            val operationSource =
                r2HostProjectOperationSource.asFile.readText(Charsets.UTF_8)
            val deadlineDecisionRegion = sourceSection(
                label = "operation-handle atomic deadline decision",
                source = operationSource,
                startMarker =
                    "internal fun tryCompleteBeforeElapsedRealtimeDeadline(",
                endMarker = "\n\n    internal fun completeStopped",
            )
            requireOrderedMarkers(
                label = "operation-handle atomic deadline decision",
                source = deadlineDecisionRegion,
                markers = listOf(
                    "elapsedRealtimeNanos: () -> Long",
                    "synchronized(stateLock)",
                    "terminal -> R2HostProjectTerminalDecision.ALREADY_TERMINAL",
                    "stopCause != null -> R2HostProjectTerminalDecision.STOP_REQUESTED",
                    "elapsedRealtimeNanos() >= deadlineAtElapsedRealtimeNanos",
                    "stopCause = R2ProjectStopCause.TIMED_OUT",
                    "R2HostProjectTerminalDecision.STOP_REQUESTED",
                    "terminal = true",
                    "R2HostProjectTerminalDecision.COMPLETED",
                ),
            )
            check("tryCompleteWithoutStop" !in operationSource) {
                "R2-P3b-b2h retained the non-deadline-aware terminal decision."
            }

            val deadlineRaceRegressionSource =
                r2ProjectFramedTransportTestSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "operation-handle deadline race regression",
                source = deadlineRaceRegressionSource,
                markers = listOf(
                    "fun completionDeadlineDecisionSharesTheOperationHandleCriticalSection()",
                    "beforeDeadline.tryCompleteBeforeElapsedRealtimeDeadline(",
                    "val clockEntered = CountDownLatch(1)",
                    "val releaseClock = CountDownLatch(1)",
                    "atDeadline.tryCompleteBeforeElapsedRealtimeDeadline(",
                    "stopThread.state != Thread.State.BLOCKED",
                    "assertFalse(stopReturned.await(100L, TimeUnit.MILLISECONDS))",
                    "R2HostProjectTerminalDecision.STOP_REQUESTED",
                    "R2ProjectStopCause.TIMED_OUT",
                ),
            )
            val compileRegion = sourceSection(
                label = "absolute deadline compile path",
                source = nativeHostSource,
                startMarker = "private fun compileProject(\n",
                endMarker = "\n    private fun completeUnregisteredProject",
            )
            requireOrderedMarkers(
                label = "request-start absolute deadline compile path",
                source = compileRegion,
                markers = listOf(
                    "val startedAtElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()",
                    "val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs)",
                    "val deadlineAtElapsedRealtimeNanos =",
                    "absoluteDeadlineAtElapsedRealtimeNanos",
                    "?: Math.addExact(",
                    "startedAtElapsedRealtimeNanos,",
                    "timeoutNanos,",
                    "else -> PendingProbe(",
                    "startedAtElapsedRealtimeNanos =",
                    "deadlineAtElapsedRealtimeNanos =",
                    "pending.completion.awaitUntilElapsedRealtimeNanos(",
                    "deadlineAtElapsedRealtimeNanos,",
                    "if (!completed)",
                    "requestStop(",
                    "cause = R2ProjectStopCause.TIMED_OUT",
                ),
            )
            listOf(
                "val remainingTimeoutMs =",
                "pending.completion.await(remainingTimeoutMs, TimeUnit.MILLISECONDS)",
                "Thread.currentThread().interrupt()\n            false",
            ).forEach { forbidden ->
                check(forbidden !in compileRegion) {
                    "R2-P3b-b2h retained an interruptible/relative deadline path: $forbidden"
                }
            }

            val selectionRegion = sourceSection(
                label = "atomic accepted stop selection",
                source = nativeHostSource,
                startMarker = "internal fun requestStop(",
                endMarker = "\n    private fun completeStoppedProjectBeforeDispatch",
            )
            requireOrderedMarkers(
                label = "atomic accepted stop selection",
                source = selectionRegion,
                markers = listOf(
                    "if (!handle.requestStop(cause)) return false",
                    "synchronized(stateLock)",
                    "candidate.operationHandle === handle",
                    "if (candidate.stopCause == null)",
                    "selectAcceptedProjectStopLocked(candidate)",
                    "private fun selectAcceptedProjectStopLocked(",
                    "val acceptedCause = requireNotNull(handle.acceptedStopCause())",
                    "check(pending.stopCause == null)",
                    "val stopSelectedAtElapsedRealtimeNanos =",
                    "SystemClock.elapsedRealtimeNanos()",
                    "pending.stopCause = acceptedCause",
                    "pending.projectStopSelection = ProjectStopSelection(",
                    "activePhase = pending.activeProjectExecutionPhase",
                    "activeProgress = pending.activeProjectExecutionPhaseProgress",
                    "deadlineAtElapsedRealtimeNanos =",
                    "pending.deadlineAtElapsedRealtimeNanos",
                    "stopSelectedAtElapsedRealtimeNanos =",
                ),
            )

            val completionRegion = sourceSection(
                label = "completion/deadline terminal race",
                source = nativeHostSource,
                startMarker = "private fun completePendingProject(",
                endMarker = "\n    private fun dispatchAcceptedProjectStop",
            )
            requireOrderedMarkers(
                label = "completion/deadline terminal race",
                source = completionRegion,
                markers = listOf(
                    "val deadlineAtElapsedRealtimeNanos =",
                    "handle.tryCompleteBeforeElapsedRealtimeDeadline(",
                    "deadlineAtElapsedRealtimeNanos =",
                    "deadlineAtElapsedRealtimeNanos,",
                    "elapsedRealtimeNanos =",
                    "SystemClock::elapsedRealtimeNanos",
                    "R2HostProjectTerminalDecision.STOP_REQUESTED",
                    "if (pending.stopCause == null)",
                    "selectAcceptedProjectStopLocked(pending)",
                    "dispatchAcceptedProjectStop(pending)",
                ),
            )
            check(
                "pending.stopCause = requireNotNull(handle.acceptedStopCause())" !in
                    completionRegion,
            ) {
                "R2-P3b-b2h completion deadline bypasses atomic stop-selection evidence."
            }
            listOf(
                "SystemClock.elapsedRealtimeNanos() >=",
                "handle.requestStop(R2ProjectStopCause.TIMED_OUT)",
                "tryCompleteWithoutStop",
            ).forEach { forbidden ->
                check(forbidden !in completionRegion) {
                    "R2-P3b-b2h completion retained a deadline TOCTOU: $forbidden"
                }
            }

            val absoluteAwaitRegion = sourceSection(
                label = "uninterruptible absolute wait",
                source = nativeHostSource,
                startMarker =
                    "private fun CountDownLatch.awaitUntilElapsedRealtimeNanos(",
                endMarker = "\n    private fun CountDownLatch.awaitUninterruptibly",
            )
            requireOrderedMarkers(
                label = "uninterruptible absolute wait",
                source = absoluteAwaitRegion,
                markers = listOf(
                    "var interrupted = false",
                    "while (count != 0L)",
                    "deadlineAtElapsedRealtimeNanos -",
                    "SystemClock.elapsedRealtimeNanos()",
                    "if (remainingNanos <= 0L) return false",
                    "await(remainingNanos, TimeUnit.NANOSECONDS)",
                    "catch (_: InterruptedException)",
                    "interrupted = true",
                    "finally",
                    "Thread.currentThread().interrupt()",
                ),
            )

            requireMarkers(
                label = "native timing observation and exact timeout receipt",
                source = nativeHostSource,
                markers = listOf(
                    "internal fun lastProjectExecutionPhaseTimingEvidenceForTesting():",
                    ".phaseSequenceTwoObservedAtElapsedRealtimeNanos",
                    "if (message.sequence == 2)",
                    "SystemClock.elapsedRealtimeNanos()",
                    "R2ProjectExecutionPhaseTimingEvidence(",
                    "sequenceTwoObservedAt -",
                    "pending.startedAtElapsedRealtimeNanos",
                    "completedAt -",
                    "selection.stopCause == R2ProjectStopCause.TIMED_OUT",
                    "selection.stopSelectedAtElapsedRealtimeNanos <",
                    "selectedDeadlineAtNanos",
                    "message.phaseAtTermination != selectedPhase",
                    "message.phaseProgressSequenceAtTermination !=",
                    "R2ProjectPhaseTerminationEvidence(",
                    "deadlineAtElapsedRealtimeNanos = selectedDeadlineAtNanos",
                ),
            )

            val desktopSource =
                r2ProjectCompilerDesktopHarnessSource.asFile.readText(Charsets.UTF_8)
            requireMarkers(
                label = "desktop semantic-timeout workload mirror",
                source = desktopSource,
                markers = listOf(
                    "const naturalPhaseTimeoutWorkloadAuthority = Object.freeze({",
                    "declarationBytes: 3_784_758,",
                    "helperAtoms: 72,",
                    "helperBytes: 3_763_492,",
                    "helperFiles: 148,",
                    "helperTokens: 28_268,",
                    "helperWhitespaceBytes: 24 * 1024,",
                    "javascriptOutputBytes: 535_202,",
                    "lightRootFiles: 64,",
                    "maximumHelperTokens: 191,",
                    "maximumRootTokens: 169,",
                    "\"4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1\"",
                    "outputBytes: 720_866,",
                    "outputFiles: 512,",
                    "rootBytes: 178_722,",
                    "rootFiles: 256,",
                    "rootTokens: 36_928,",
                    "sourceMapOutputBytes: 185_664,",
                    "totalBytes: 7_726_972,",
                    "totalFiles: 512,",
                    "totalTokens: 151_525,",
                    "const naturalPhaseTimeoutSourceText = (index) =>",
                    "const naturalPhaseTimeoutHelperText = (index) =>",
                    "const naturalPhaseTimeoutWorkloadSnapshot = () =>",
                    "files: roots.concat(helpers, declarations)",
                    "const authority = naturalPhaseTimeoutWorkloadAuthority;",
                    "const project = naturalPhaseTimeoutWorkloadSnapshot();",
                    "Natural semantic-timeout aggregate input authority drifted.",
                    "authority.totalFiles <= 512",
                    "authority.totalBytes <= 8 * 1024 * 1024",
                    "authority.totalTokens <= p3bWorkerLimits.maximumProjectTokens",
                    "Natural semantic-timeout fixture escaped ordinary Worker admission.",
                    "javascriptOutputs.length === authority.rootFiles",
                    "sourceMapOutputs.length === authority.rootFiles",
                    "authority.outputFiles <= 512",
                    "authority.outputBytes <= 1024 * 1024",
                    "naturalPhaseOutputAggregateSha256(baseline.result.outputs)",
                ),
            )
            val desktopCalibration = sourceSection(
                label = "desktop deadline calibration golden",
                source = desktopSource,
                startMarker =
                    "const p3bB2hNaturalPhaseCalibrationBaselineRuns = 3;",
                endMarker = "\nconst verifySemanticError",
            )
            requireMarkers(
                label = "desktop deadline calibration golden",
                source = desktopCalibration,
                markers = listOf(
                    "const p3bB2hNaturalPhaseCalibrationBaselineRuns = 3;",
                    "const p3bB2hNaturalPhaseCalibrationMarginMilliseconds = 100;",
                    "const p3bB2hNaturalPhaseFaultAttempts = 1;",
                    "const calibrateP3bB2hNaturalSemanticRequestStartDeadline = (",
                    "timingRuns.length === p3bB2hNaturalPhaseCalibrationBaselineRuns",
                    "const expectedPhases = [\"semantic\"];",
                    "sequenceTwoElapsedRealtimeNanosSinceProjectStart",
                    "completedElapsedRealtimeNanosSinceProjectStart",
                    "rawUpperNanos - rawLowerNanos >= marginNanos * 2n",
                    "(minimumTimeoutMilliseconds + maximumTimeoutMilliseconds) / 2",
                    "timeoutNanos - rawLowerNanos >= marginNanos",
                    "rawUpperNanos - timeoutNanos >= marginNanos",
                    "const verifyP3bB2hNaturalSemanticDeadlineCalibration = async () =>",
                    "const timingRuns = [];",
                    "const expectedProgress = [",
                    "let runIndex = 0;",
                    "runIndex < p3bB2hNaturalPhaseCalibrationBaselineRuns",
                    "desktop-p3b-b2h-natural-phase-calibration-",
                    "baseline.phaseProgressEvents",
                    "JSON.stringify(expectedProgress)",
                    "naturalPhaseOutputAggregateSha256(baseline.result.outputs)",
                    "const observedPhaseRuns = timingRuns.map",
                    "timing.completedElapsedRealtimeNanosSinceProjectStart >",
                    "timing.sequenceTwoElapsedRealtimeNanosSinceProjectStart",
                    "calibrateP3bB2hNaturalSemanticRequestStartDeadline(",
                    "timingRuns,",
                    "() => [\"parse\", \"semantic\", \"emit\"],",
                    "JSON.stringify([\"semantic\"])",
                    "p3bB2hNaturalPhaseCalibrationMarginMilliseconds",
                    "p3bB2hNaturalPhaseFaultAttempts === 1",
                ),
            )
            check("syntheticTimingRuns" !in desktopCalibration) {
                "R2-P3b-b2h desktop calibration must use its three real timing runs."
            }
            requireMarkers(
                label = "desktop native-only timing observations",
                source = desktopSource,
                markers = listOf(
                    "const messageObservations = [];",
                    "observedAtElapsedRealtimeNanos: process.hrtime.bigint()",
                    "const requestStartedAtElapsedRealtimeNanos = process.hrtime.bigint();",
                    "const phaseTimingEvidence = [\"parse\", \"semantic\", \"emit\"]",
                    "sequenceTwo.observedAtElapsedRealtimeNanos -",
                    "completed.observedAtElapsedRealtimeNanos -",
                    "phaseTimingEvidence,",
                    "\"P3b-b2h three-baseline semantic request-start deadline calibration\"",
                    "verifyP3bB2hNaturalSemanticDeadlineCalibration();",
                ),
            )

            val workerSource =
                r2CompilerWorkerSource.asFile.readText(Charsets.UTF_8)
            val pageSource =
                r2CompilerHostPageSource.asFile.readText(Charsets.UTF_8)
            listOf(
                "deadlineAtElapsedRealtimeNanos",
                "requestStartAbsoluteDeadline",
                "naturalPhaseTimeoutEvidence",
                "targetTimeoutPhase",
                "workerNaturalPhaseTimeoutEvidence",
            ).forEach { forbidden ->
                check(forbidden !in workerSource && forbidden !in pageSource) {
                    "R2-P3b-b2h leaked native deadline targeting into Worker/page: $forbidden"
                }
            }
        }
    }

val verifyR2P3bB2hNaturalPhaseTimeoutAndroidTestApk =
    tasks.register("verifyR2P3bB2hNaturalPhaseTimeoutAndroidTestApk") {
        group = "verification"
        description =
            "Verifies that the paired debug app/androidTest APKs contain the R2-P3b-b2h " +
                "semantic request-start absolute-deadline runtime, calibration, timeout, and " +
                "cleanup evidence matrix; it does not run devices."
        dependsOn("assembleDebug", "assembleDebugAndroidTest")
        dependsOn(verifyR2P3bB2hNaturalPhaseTimeoutSourcePolicy)
        val appApkDirectory =
            layout.buildDirectory.dir("outputs/apk/debug")
        val testApkDirectory =
            layout.buildDirectory.dir("outputs/apk/androidTest/debug")
        inputs.dir(appApkDirectory)
        inputs.dir(testApkDirectory)
        doLast {
            fun readDexBytes(apkFile: File): List<ByteArray> =
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

            fun requireDexMarkers(
                label: String,
                dexBytes: List<ByteArray>,
                markers: Map<String, String>,
            ) {
                markers.forEach { (markerLabel, markerText) ->
                    val marker = markerText.toByteArray(Charsets.UTF_8)
                    check(dexBytes.any { dex -> dex.containsByteSequence(marker) }) {
                        "R2-P3b-b2h $label is missing $markerLabel marker: $markerText"
                    }
                }
            }

            val appApkFile =
                resolveSingleApkOutput(appApkDirectory.get().asFile)
            val appDexBytes = readDexBytes(appApkFile)
            check(appDexBytes.isNotEmpty()) {
                "R2-P3b-b2h debug app APK contains no DEX entries: $appApkFile"
            }
            requireDexMarkers(
                label = "debug app APK",
                dexBytes = appDexBytes,
                markers = linkedMapOf(
                    "phase timing evidence type" to
                        "R2ProjectExecutionPhaseTimingEvidence",
                    "phase termination evidence type" to
                        "R2ProjectPhaseTerminationEvidence",
                    "timing evidence accessor" to
                        "lastProjectExecutionPhaseTimingEvidenceForTesting",
                    "termination evidence accessor" to
                        "lastProjectPhaseTerminationEvidenceForTesting",
                    "absolute deadline field" to
                        "deadlineAtElapsedRealtimeNanos",
                    "absolute wait helper" to
                        "awaitUntilElapsedRealtimeNanos",
                    "atomic deadline terminal helper" to
                        "tryCompleteBeforeElapsedRealtimeDeadline",
                    "atomic stop selection helper" to
                        "selectAcceptedProjectStopLocked",
                    "framed progress kind" to "projectPhaseProgress",
                    "termination progress receipt" to
                        "phaseProgressSequenceAtTermination",
                    "timeout outcome" to
                        "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_TIMEOUT",
                ),
            )

            val testApkFile =
                resolveSingleApkOutput(testApkDirectory.get().asFile)
            val testDexBytes = readDexBytes(testApkFile)
            check(testDexBytes.isNotEmpty()) {
                "R2-P3b-b2h androidTest APK contains no DEX entries: $testApkFile"
            }
            requireDexMarkers(
                label = "androidTest APK",
                dexBytes = testDexBytes,
                markers = linkedMapOf(
                    "natural timeout instrumentation class" to
                        "R2ProjectCompilerInstrumentationTest",
                    "absolute deadline regression class" to
                        "R2AbsoluteDeadlineRegressionInstrumentationTest",
                    "request-start timeout matrix method" to
                        "naturalSemanticRequestStartAbsoluteDeadlineTimeoutUsesCalibratedWindowAndRecovers",
                    "interrupt regression method" to
                        "interruptedDeadlineWaitDoesNotTimeoutEarlyAndRestoresInterruptStatus",
                    "natural workload fixture" to
                        "R2NaturalPhaseTimeoutWorkloadFixture",
                    "calibration helper" to "calibrateNaturalPhaseDeadlines",
                    "timing evidence type" to
                        "R2ProjectExecutionPhaseTimingEvidence",
                    "calibration baseline evidence" to
                        "NATURAL_PHASE_TIMEOUT_BASELINE_PASS",
                    "calibration window evidence" to
                        "NATURAL_PHASE_TIMEOUT_WINDOW_PASS",
                    "per-phase timeout evidence" to
                        "NATURAL_PHASE_TIMEOUT_PASS",
                    "semantic timeout matrix evidence" to
                        "NATURAL_PHASE_TIMEOUT_MATRIX_PASS",
                    "natural timeout log tag" to
                        "R2P3bNaturalPhaseTimeout",
                    "request-start deadline evidence" to
                        "requestStartAbsoluteDeadline=true",
                    "ordinary admission evidence" to
                        "ordinaryAdmission=true",
                    "ordinary zero-spin evidence" to
                        "workerSpinMilliseconds=0 workerSpinPhase=none",
                    "single semantic target evidence" to
                        "phases=1 calibrationRuns=",
                    "deadline lower-bound assertion" to
                        "timeout fired before its request-start absolute deadline.",
                    "deadline selection assertion" to
                        "stop selection preceded the absolute deadline.",
                    "small successor assertion" to
                        "The natural-phase timeout successor did not retire a newer session.",
                    "host cleanup assertion" to
                        "Natural phase timeout host close did not prove cleanup.",
                    "spool cleanup assertion" to
                        "Natural phase timeout cleanup left a project spool file.",
                    "interrupt quiet-window assertion" to
                        "Interrupting the compile thread published timeout before its absolute deadline.",
                    "interrupt restoration assertion" to
                        "absolute deadline wait did not restore the compile thread interrupt flag.",
                    "natural phase source namespace" to
                        "/sources/natural-timeout-phase-",
                    "natural phase helper namespace" to
                        "/sources/natural-timeout-helper-",
                    "natural timeout spool namespace" to
                        "r2-p3b-natural-phase-timeout",
                    "frozen output aggregate" to
                        "4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1",
                ),
            )
        }
    }

val verifyR2P3bB2iNaturalParseTimeoutSourcePolicy =
    tasks.register("verifyR2P3bB2iNaturalParseTimeoutSourcePolicy") {
        group = "verification"
        description =
            "Verifies the R2-P3b-b2i ordinary request-start absolute-deadline " +
                "two-precondition parse calibration, exact teardown, cleanup, and successor policy."
        dependsOn(verifyR2P3bB2hNaturalPhaseTimeoutSourcePolicy)
        dependsOn(verifyR2ProjectCompilerDesktop)
        dependsOn("testDebugUnitTest")
        inputs.files(
            r2ProjectCompilerInstrumentationSource,
            r2NaturalParseTimeoutWorkloadFixtureSource,
            r2ProjectCompilerDesktopHarnessSource,
        )
        doLast {
            fun requireMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-P3b-b2i $label is missing: $marker"
                    }
                }
            }

            fun sourceSection(
                label: String,
                source: String,
                startMarker: String,
                endMarker: String,
            ): String {
                val start = source.indexOf(startMarker)
                check(start >= 0) {
                    "R2-P3b-b2i $label start is missing: $startMarker"
                }
                val end = source.indexOf(endMarker, start + startMarker.length)
                check(end > start) {
                    "R2-P3b-b2i $label end is missing: $endMarker"
                }
                return source.substring(start, end)
            }

            fun requireOrderedMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                var cursor = 0
                markers.forEach { marker ->
                    val index = source.indexOf(marker, cursor)
                    check(index >= cursor) {
                        "R2-P3b-b2i $label ordering is missing: $marker"
                    }
                    cursor = index + marker.length
                }
            }

            val parseWorkloadSource =
                r2NaturalParseTimeoutWorkloadFixtureSource.asFile
                    .readText(Charsets.UTF_8)
            fun frozenParseWorkloadLong(name: String): Long {
                val match = Regex(
                    """(?:private\s+)?const val $name = ([0-9_]+)L?""",
                ).find(parseWorkloadSource)
                check(match != null) {
                    "R2-P3b-b2i parse workload constant is missing: $name"
                }
                return match.groupValues[1].replace("_", "").toLong()
            }
            val frozenParseWorkloadAuthority = linkedMapOf(
                "ROOT_COUNT" to 8L,
                "SOURCE_DIRECTORY_DEPTH" to 8L,
                "SOURCE_PATH_DEPTH" to 10L,
                "REFERENCE_ROOT_INDEX" to 2L,
                "FIRST_IMPORT_ROOT_INDEX" to 3L,
                "IMPORT_ROOT_COUNT" to 3L,
                "IMPORTS_PER_ROOT" to 5_700L,
                "MAXIMUM_MODULE_INDEX" to 17_099L,
                "AMBIENT_DECLARATION_FILE_COUNT" to 1L,
                "TRUSTED_DECLARATION_FILE_COUNT" to 108L,
                "TRUSTED_DECLARATION_BYTES" to 3_784_758L,
                "TRUSTED_DECLARATION_TOKENS" to 86_329L,
                "ROOT_BYTES" to 1_094_708L,
                "ROOT_TOKENS" to 171_048L,
                "MAXIMUM_ROOT_BYTES" to 364_832L,
                "MAXIMUM_ROOT_TOKENS" to 57_006L,
                "AMBIENT_DECLARATION_BYTES" to 61L,
                "AMBIENT_DECLARATION_TOKENS" to 11L,
                "TOTAL_FILE_COUNT" to 117L,
                "TOTAL_BYTES" to 4_879_527L,
                "TOTAL_TOKENS" to 257_388L,
                "TOTAL_PATH_CHARACTERS" to 4_434L,
                "MAXIMUM_PATH_CHARACTERS" to 65L,
                "INPUT_TRANSPORT_FRAME_COUNT" to 418L,
                "OUTPUT_FILE_COUNT" to 16L,
                "JAVASCRIPT_OUTPUT_BYTES" to 1_524L,
                "SOURCE_MAP_OUTPUT_BYTES" to 1_814L,
                "OUTPUT_BYTES" to 3_338L,
            )
            frozenParseWorkloadAuthority.forEach { (name, expected) ->
                check(frozenParseWorkloadLong(name) == expected) {
                    "R2-P3b-b2i parse workload authority drifted: $name"
                }
            }
            check(
                frozenParseWorkloadAuthority.getValue("ROOT_COUNT") +
                    frozenParseWorkloadAuthority.getValue(
                        "AMBIENT_DECLARATION_FILE_COUNT",
                    ) +
                    frozenParseWorkloadAuthority.getValue(
                        "TRUSTED_DECLARATION_FILE_COUNT",
                    ) ==
                    frozenParseWorkloadAuthority.getValue("TOTAL_FILE_COUNT"),
            )
            check(
                frozenParseWorkloadAuthority.getValue("ROOT_BYTES") +
                    frozenParseWorkloadAuthority.getValue(
                        "AMBIENT_DECLARATION_BYTES",
                    ) +
                    frozenParseWorkloadAuthority.getValue(
                        "TRUSTED_DECLARATION_BYTES",
                    ) ==
                    frozenParseWorkloadAuthority.getValue("TOTAL_BYTES"),
            )
            check(
                frozenParseWorkloadAuthority.getValue("ROOT_TOKENS") +
                    frozenParseWorkloadAuthority.getValue(
                        "AMBIENT_DECLARATION_TOKENS",
                    ) +
                    frozenParseWorkloadAuthority.getValue(
                        "TRUSTED_DECLARATION_TOKENS",
                    ) ==
                    frozenParseWorkloadAuthority.getValue("TOTAL_TOKENS"),
            )
            check(
                frozenParseWorkloadAuthority.getValue(
                    "JAVASCRIPT_OUTPUT_BYTES",
                ) +
                    frozenParseWorkloadAuthority.getValue(
                        "SOURCE_MAP_OUTPUT_BYTES",
                    ) ==
                    frozenParseWorkloadAuthority.getValue("OUTPUT_BYTES"),
            )
            val parseSequenceTwoCompletedRoots =
                (
                    frozenParseWorkloadAuthority.getValue("ROOT_COUNT") + 3L
                    ) / 4L
            check(
                frozenParseWorkloadAuthority.getValue(
                    "REFERENCE_ROOT_INDEX",
                ) == parseSequenceTwoCompletedRoots &&
                    parseSequenceTwoCompletedRoots == 2L &&
                    frozenParseWorkloadAuthority.getValue(
                        "FIRST_IMPORT_ROOT_INDEX",
                    ) ==
                    frozenParseWorkloadAuthority.getValue(
                        "REFERENCE_ROOT_INDEX",
                    ) + 1L &&
                    frozenParseWorkloadAuthority.getValue(
                        "FIRST_IMPORT_ROOT_INDEX",
                    ) == parseSequenceTwoCompletedRoots + 1L &&
                    frozenParseWorkloadAuthority.getValue(
                        "FIRST_IMPORT_ROOT_INDEX",
                    ) +
                    frozenParseWorkloadAuthority.getValue(
                        "IMPORT_ROOT_COUNT",
                    ) - 1L == 5L &&
                    frozenParseWorkloadAuthority.getValue(
                        "FIRST_IMPORT_ROOT_INDEX",
                    ) +
                    frozenParseWorkloadAuthority.getValue(
                        "IMPORT_ROOT_COUNT",
                    ) <=
                    frozenParseWorkloadAuthority.getValue("ROOT_COUNT") &&
                    frozenParseWorkloadAuthority.getValue(
                        "IMPORT_ROOT_COUNT",
                    ) *
                    frozenParseWorkloadAuthority.getValue(
                        "IMPORTS_PER_ROOT",
                    ) ==
                    frozenParseWorkloadAuthority.getValue(
                        "MAXIMUM_MODULE_INDEX",
                    ) + 1L &&
                    frozenParseWorkloadAuthority.getValue(
                        "SOURCE_DIRECTORY_DEPTH",
                    ) + 2L ==
                    frozenParseWorkloadAuthority.getValue(
                        "SOURCE_PATH_DEPTH",
                    ) &&
                    frozenParseWorkloadAuthority.getValue("ROOT_COUNT") * 2L ==
                    frozenParseWorkloadAuthority.getValue(
                        "OUTPUT_FILE_COUNT",
                    ) &&
                    frozenParseWorkloadAuthority.getValue(
                        "MAXIMUM_ROOT_TOKENS",
                    ) <= 65_536L &&
                    frozenParseWorkloadAuthority.getValue("TOTAL_FILE_COUNT") <=
                    512L &&
                    frozenParseWorkloadAuthority.getValue("TOTAL_BYTES") <=
                    8L * 1024L * 1024L &&
                    frozenParseWorkloadAuthority.getValue("TOTAL_TOKENS") <=
                    262_144L &&
                    frozenParseWorkloadAuthority.getValue("OUTPUT_FILE_COUNT") <=
                    512L &&
                    frozenParseWorkloadAuthority.getValue("OUTPUT_BYTES") <=
                    1024L * 1024L,
            ) {
                "R2-P3b-b2i parse workload escaped ordinary admission limits."
            }
            requireMarkers(
                label = "frozen parse-timeout workload",
                source = parseWorkloadSource,
                markers = listOf(
                    "request-start natural parse timeout evidence",
                    "R2NaturalParseTimeoutWorkloadFixture",
                    "\"c16cf1e19354655321bc7bd13bea9840900f2f2b2bf67793b4d1b926ee42e6b3\"",
                    "R2ProjectSnapshotLimits.PROVISIONAL_WORKER",
                    "val sequenceTwoCompletedRoots = (ROOT_COUNT - 1) / 4 + 1",
                    "check(sequenceTwoCompletedRoots == REFERENCE_ROOT_INDEX)",
                    "check(REFERENCE_ROOT_INDEX < FIRST_IMPORT_ROOT_INDEX)",
                    "FIRST_IMPORT_ROOT_INDEX + IMPORT_ROOT_COUNT <= ROOT_COUNT",
                    ".count(String::isNotEmpty) +",
                    "1 == SOURCE_PATH_DEPTH",
                    "rootName.substringBeforeLast('/') == sourceDirectoryPath()",
                    "snapshot.fileInventory.sumOf { file -> file.path.length } ==",
                    "TOTAL_PATH_CHARACTERS",
                    "snapshot.fileInventory.maxOf { file -> file.path.length } ==",
                    "MAXIMUM_PATH_CHARACTERS",
                    "if (index == REFERENCE_ROOT_INDEX)",
                    "/// <reference path=\\\"./probe-parse-ambient.d.ts\\\" />\\n",
                    "FIRST_IMPORT_ROOT_INDEX until",
                    "FIRST_IMPORT_ROOT_INDEX + IMPORT_ROOT_COUNT",
                    "repeat(IMPORTS_PER_ROOT)",
                    "index - FIRST_IMPORT_ROOT_INDEX",
                    "IMPORTS_PER_ROOT +",
                    "MAXIMUM_MODULE_INDEX",
                    "append(\"import type { ProbeType as ProbeType\")",
                    "append(\" } from \\\"probe-\")",
                    "fun ambientDeclarationPath(): String",
                    "sourceDirectoryPath() + \"/probe-parse-ambient.d.ts\"",
                    "fun ambientDeclarationText(): String",
                    "declare module \\\"probe-*\\\" { export type ProbeType = number; }",
                ),
            )
            val parseRootPathFactory = sourceSection(
                label = "frozen parse root/common-directory path factory",
                source = parseWorkloadSource,
                startMarker = "fun sourcePath(index: Int): String",
                endMarker = "\n    fun sourceText(index: Int): String",
            )
            requireMarkers(
                label = "frozen parse root/common-directory path factory",
                source = parseRootPathFactory,
                markers = listOf(
                    "return sourceDirectoryPath() +",
                    "\"/probe-parse-root-\"",
                    "fun sourceDirectoryPath(): String =",
                    "\"/sources/\" +",
                    "List(SOURCE_DIRECTORY_DEPTH) { index ->",
                    "\"p\${index.toString().padStart(2, '0')}\"",
                    "}.joinToString(separator = \"/\")",
                ),
            )
            val parseAmbientPathFactory = sourceSection(
                label = "frozen parse ambient/common-directory path factory",
                source = parseWorkloadSource,
                startMarker = "fun ambientDeclarationPath(): String",
                endMarker = "\n    fun aggregateOutputSha256(",
            )
            requireMarkers(
                label = "frozen parse ambient/common-directory path factory",
                source = parseAmbientPathFactory,
                markers = listOf(
                    "sourceDirectoryPath() + \"/probe-parse-ambient.d.ts\"",
                    "declare module \\\"probe-*\\\" { export type ProbeType = number; }",
                ),
            )
            listOf(
                "HEAVY_HELPER",
                "heavyHelper",
                "probe-parse-heavy",
                "WHITESPACE_BYTES",
                "FILLER_HELPER",
                "fillerHelper",
                "probe-parse-filler",
            ).forEach { forbidden ->
                check(forbidden !in parseWorkloadSource) {
                    "R2-P3b-b2i frozen parse workload retained obsolete " +
                        "heavy/whitespace authority: $forbidden"
                }
            }
            check(
                "R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE" !in
                    parseWorkloadSource,
            ) {
                "R2-P3b-b2i parse workload must use only ordinary Worker admission."
            }

            val instrumentationSource =
                r2ProjectCompilerInstrumentationSource.asFile.readText(
                    Charsets.UTF_8,
                )
            val parseTimeoutMatrix = sourceSection(
                label = "natural parse timeout matrix",
                source = instrumentationSource,
                startMarker =
                    "fun naturalParseRequestStartAbsoluteDeadlineTimeoutUsesCalibratedWindowAndRecovers()",
                endMarker = "\n    @Test",
            )
            requireMarkers(
                label = "natural parse timeout matrix",
                source = parseTimeoutMatrix,
                markers = listOf(
                    "R2NaturalParseTimeoutWorkloadFixture.createSnapshot(",
                    "R2NaturalParseTimeoutWorkloadFixture.TOTAL_FILE_COUNT",
                    "R2NaturalParseTimeoutWorkloadFixture.TOTAL_BYTES",
                    "R2NaturalParseTimeoutWorkloadFixture.INPUT_TRANSPORT_FRAME_COUNT",
                    "R2ProjectExecutionPhase.PARSE",
                    "R2ProjectExecutionPhase.SEMANTIC",
                    "R2ProjectExecutionPhase.EMIT",
                    "fun successfulNaturalParseRun(",
                    "check(NATURAL_PARSE_TIMEOUT_PRECONDITION_RUNS == 2)",
                    "repeat(NATURAL_PARSE_TIMEOUT_PRECONDITION_RUNS)",
                    "\"calibration precondition \${preconditionIndex + 1}\"",
                    "NATURAL_PARSE_TIMEOUT_PRECONDITION_PASS",
                    "repeat(NATURAL_PARSE_TIMEOUT_CALIBRATION_RUNS)",
                    "TypeScriptCompilerContract.MAX_TIMEOUT_MS",
                    ".JAVASCRIPT_OUTPUT_BYTES",
                    ".SOURCE_MAP_OUTPUT_BYTES",
                    "R2NaturalParseTimeoutWorkloadFixture.OUTPUT_BYTES",
                    ".OUTPUT_AGGREGATE_SHA256",
                    "expectedNaturalParseTimeoutProgressHistory()",
                    "host.lastProjectExecutionPhaseTimingEvidenceForTesting()",
                    "inputTelemetry.sentFrameCount",
                    "NATURAL_PARSE_TIMEOUT_BASELINE_PASS",
                    "calibration = calibrateNaturalParseDeadline(calibrationRuns)",
                    "check(NATURAL_PARSE_TIMEOUT_FAULT_ATTEMPTS == 1)",
                    "handle = handle",
                    "snapshot = workload",
                    "timeoutMs = selectedCalibration.timeoutMillis",
                    "TypeScriptCompilerContract.ERROR_TIMEOUT",
                    "assertEquals(null, timedOut.compilation)",
                    "timedOut.elapsedMs >= selectedCalibration.timeoutMillis",
                    "expectedNaturalParseTimeoutProgressHistory().take(2)",
                    ".isEmpty()",
                    "expectedComplete = true",
                    "host.lastProjectPhaseTerminationEvidenceForTesting()",
                    "R2ProjectStopCause.TIMED_OUT",
                    "evidence.phaseAtHostStopSelection",
                    "evidence.phaseAtPageTermination",
                    "R2ProjectExecutionPhase.INGRESS",
                    "NATURAL_PARSE_TIMEOUT_PROGRESS_SEQUENCE",
                    "evidence.phaseProgressCompletedUnitsAtPageTermination",
                    "evidence.phaseProgressTotalUnitsAtPageTermination",
                    "evidence.stopSelectedAtElapsedRealtimeNanos >=",
                    "evidence.deadlineAtElapsedRealtimeNanos",
                    "evidence.terminatedWorkerGeneration + 1",
                    "NATURAL_PARSE_TIMEOUT_PASS",
                    "workerSpinMilliseconds=0 workerSpinPhase=none",
                    "snapshot = successorSnapshot",
                    "The natural parse timeout successor did not retire a newer ",
                    "Natural parse timeout host close did not prove cleanup.",
                    "Natural parse timeout cleanup left a project spool file.",
                    "NATURAL_PARSE_TIMEOUT_MATRIX_PASS",
                    "\"phases=1 preconditionRuns=\"",
                    "\"calibrationRuns=\"",
                    "\"parseTimeoutMs=",
                    "\"inputFrames=",
                    "\"pathDepth=",
                    "\"resolverDepth=",
                    "ordinaryAdmission=true requestStartAbsoluteDeadline=true",
                    "hostCleanupProven=true spoolCleanupProven=true",
                ),
            )
            requireOrderedMarkers(
                label = "freeze parse calibration before its single fault",
                source = parseTimeoutMatrix,
                markers = listOf(
                    "check(NATURAL_PARSE_TIMEOUT_PRECONDITION_RUNS == 2)",
                    "repeat(NATURAL_PARSE_TIMEOUT_PRECONDITION_RUNS)",
                    "successfulNaturalParseRun(",
                    "\"calibration precondition \${preconditionIndex + 1}\"",
                    "NATURAL_PARSE_TIMEOUT_PRECONDITION_PASS",
                    "val calibrationRuns =",
                    "repeat(NATURAL_PARSE_TIMEOUT_CALIBRATION_RUNS)",
                    "calibration = calibrateNaturalParseDeadline(calibrationRuns)",
                    "check(NATURAL_PARSE_TIMEOUT_FAULT_ATTEMPTS == 1)",
                    "val selectedCalibration = requireNotNull(calibration)",
                    "val timedOut = executor.submit(",
                    "host.compileProject(",
                    "timeoutMs = selectedCalibration.timeoutMillis",
                ),
            )
            check(
                Regex("""host\.compileProject\(""")
                    .findAll(parseTimeoutMatrix)
                    .count() == 3,
            ) {
                "R2-P3b-b2i must use ordinary compileProject exactly in its " +
                    "shared successful-run helper, single fixed parse deadline, " +
                    "and successor."
            }
            check(
                Regex("""host\.requestStop\(""")
                    .findAll(parseTimeoutMatrix)
                    .count() == 0,
            ) {
                "R2-P3b-b2i must not select parse timeout through cancellation."
            }
            listOf(
                "armProjectAbsoluteDeadlineForTesting",
                "rebaseProjectAbsoluteDeadlineForTesting",
                "shortenProjectAbsoluteDeadlineForTesting",
                "setProjectAbsoluteDeadlineForTesting",
                "compileProjectWithExpandedWorkerCapacityForTesting",
                "compileProjectWithSynchronousWorkerSpinForTesting",
                "workerSpinMilliseconds =",
                "workerSpinPhase =",
                "SYNCHRONOUS_WORKER_SPIN",
                "terminateActiveProjectRendererForTesting",
                "armRendererTerminationsForTesting",
                "waitForExecutionPhaseProgress(",
                "projectExecutionPhaseProgressForTesting(",
                "activeProjectExecutionPhaseForTesting(",
                "R2ProjectStopCause.CANCELLED",
                "TypeScriptCompilerContract.ERROR_CANCELLED",
                "Thread.sleep(",
                "SystemClock.sleep(",
                "while (",
                "repeat(NATURAL_PARSE_TIMEOUT_FAULT_ATTEMPTS",
                "NATURAL_INGRESS_TIMEOUT",
                "phaseRelative",
                "phaseStartedAt",
                "elapsedSincePhase",
                "sequenceTwoToCompleted",
            ).forEach { forbidden ->
                check(forbidden !in parseTimeoutMatrix) {
                    "R2-P3b-b2i parse timeout matrix contains forbidden " +
                        "mid-flight or phase-relative control: $forbidden"
                }
            }

            val parseCalibration = sourceSection(
                label = "three-baseline parse calibration helper",
                source = instrumentationSource,
                startMarker = "private fun calibrateNaturalParseDeadline(",
                endMarker = "\n    private fun calibrateNaturalPhaseDeadlines(",
            )
            requireMarkers(
                label = "three-baseline parse calibration helper",
                source = parseCalibration,
                markers = listOf(
                    "NATURAL_PARSE_TIMEOUT_CALIBRATION_RUNS",
                    "val phase = R2ProjectExecutionPhase.PARSE",
                    "samples.maxOf",
                    "sequenceTwoElapsedRealtimeNanosSinceProjectStart",
                    "+ 1L",
                    "samples.minOf",
                    "completedElapsedRealtimeNanosSinceProjectStart - 1L",
                    "maximumTimeoutMillis - minimumTimeoutMillis >=",
                    "NATURAL_PARSE_TIMEOUT_MINIMUM_MARGIN_MILLIS * 2L",
                    "(maximumTimeoutMillis - minimumTimeoutMillis) / 2L",
                    "leftMarginMillis >=",
                    "rightMarginMillis >=",
                    "sequenceTwoElapsedRealtimeNanosSinceProjectStart <",
                    "timeoutNanos <",
                    "completedElapsedRealtimeNanosSinceProjectStart",
                    "NATURAL_PARSE_TIMEOUT_WINDOW_PASS",
                ),
            )
            listOf(
                "System.nanoTime(",
                "SystemClock.",
                "Thread.sleep(",
                "synthetic",
                "phaseRelative",
                "phaseStartedAt",
                "elapsedSincePhase",
            ).forEach { forbidden ->
                check(forbidden !in parseCalibration) {
                    "R2-P3b-b2i parse calibration is not purely request-start " +
                        "evidence: $forbidden"
                }
            }
            requireMarkers(
                label = "frozen parse timeout constants",
                source = instrumentationSource,
                markers = listOf(
                    "const val NATURAL_PARSE_TIMEOUT_CALIBRATION_RUNS = 3",
                    "const val NATURAL_PARSE_TIMEOUT_PRECONDITION_RUNS = 2",
                    "const val NATURAL_PARSE_TIMEOUT_FAULT_ATTEMPTS = 1",
                    "const val NATURAL_PARSE_TIMEOUT_MINIMUM_MARGIN_MILLIS = 100L",
                    "const val NATURAL_PARSE_TIMEOUT_PROGRESS_SEQUENCE = 2",
                    "const val NATURAL_PARSE_TIMEOUT_MAXIMUM_DEADLINE_OVERSHOOT_MILLIS =",
                    "const val NATURAL_PARSE_TIMEOUT_SPOOL_DIRECTORY =",
                    "\"r2-p3b-natural-parse-timeout\"",
                    "const val NATURAL_PARSE_TIMEOUT_LOG_TAG =",
                    "\"R2P3bNaturalParseTimeout\"",
                ),
            )

            val desktopSource =
                r2ProjectCompilerDesktopHarnessSource.asFile.readText(
                    Charsets.UTF_8,
                )
            val desktopParseWorkload = sourceSection(
                label = "desktop parse-timeout workload mirror",
                source = desktopSource,
                startMarker =
                    "const naturalParseTimeoutWorkloadAuthority = Object.freeze({",
                endMarker = "\nconst frameBytes",
            )
            requireMarkers(
                label = "desktop parse-timeout workload mirror",
                source = desktopParseWorkload,
                markers = listOf(
                    "const naturalParseTimeoutWorkloadAuthority = Object.freeze({",
                    "ambientDeclarationBytes: 61,",
                    "ambientDeclarationFiles: 1,",
                    "ambientDeclarationTokens: 11,",
                    "aggregatePathCharacters: 4_434,",
                    "commonDirectoryDepth: 8,",
                    "commonDirectorySegmentPrefix: \"p\",",
                    "declarationBytes: 3_784_758,",
                    "declarationFiles: 108,",
                    "declarationTokens: 86_329,",
                    "firstImportRootIndex: 3,",
                    "inputTransportFrameCount: 418,",
                    "importRootFiles: 3,",
                    "importsPerRoot: 5_700,",
                    "javascriptOutputBytes: 1_524,",
                    "maximumPathCharacters: 65,",
                    "maximumModuleIndex: 17_099,",
                    "maximumRootBytes: 364_832,",
                    "maximumRootTokens: 57_006,",
                    "\"c16cf1e19354655321bc7bd13bea9840900f2f2b2bf67793b4d1b926ee42e6b3\"",
                    "outputBytes: 3_338,",
                    "outputFiles: 16,",
                    "referenceRootIndex: 2,",
                    "rootBytes: 1_094_708,",
                    "rootFiles: 8,",
                    "rootTokens: 171_048,",
                    "sourcePathDepth: 10,",
                    "sourceMapOutputBytes: 1_814,",
                    "totalBytes: 4_879_527,",
                    "totalFiles: 117,",
                    "totalTokens: 257_388,",
                    "const naturalParseTimeoutCommonDirectorySegments = Array.from(",
                    "naturalParseTimeoutWorkloadAuthority.commonDirectoryDepth",
                    "naturalParseTimeoutWorkloadAuthority.commonDirectorySegmentPrefix",
                    "const naturalParseTimeoutCommonDirectory =",
                    "naturalParseTimeoutCommonDirectorySegments.join(\"/\")",
                    "`${'$'}{naturalParseTimeoutCommonDirectory}/probe-parse-root-`",
                    "const naturalParseTimeoutSourceText = (index) =>",
                    "naturalParseTimeoutWorkloadAuthority.referenceRootIndex",
                    "'/// <reference path=\"./probe-parse-ambient.d.ts\" />\\n'",
                    "naturalParseTimeoutWorkloadAuthority.firstImportRootIndex",
                    "naturalParseTimeoutWorkloadAuthority.importsPerRoot",
                    "naturalParseTimeoutWorkloadAuthority.maximumModuleIndex",
                    "import type { ProbeType as ProbeType",
                    "from \"probe-",
                    "const naturalParseTimeoutAmbientDeclarationPath =",
                    "`${'$'}{naturalParseTimeoutCommonDirectory}/probe-parse-ambient.d.ts`",
                    "const naturalParseTimeoutAmbientDeclarationText =",
                    "'declare module \"probe-*\" { export type ProbeType = number; } '",
                    "const naturalParseTimeoutWorkloadSnapshot = () =>",
                    "files: roots.concat(",
                    "[ambientDeclaration],",
                    "declarations),",
                ),
            )
            listOf(
                "heavyHelper",
                "probe-parse-heavy",
                "Whitespace",
                "whitespace",
                "\" \".repeat(",
                "fillerHelper",
                "probe-parse-filler",
            ).forEach { forbidden ->
                check(forbidden !in desktopParseWorkload) {
                    "R2-P3b-b2i desktop workload retained obsolete " +
                        "heavy/whitespace authority: $forbidden"
                }
            }
            val desktopParseCalibration = sourceSection(
                label = "desktop three-baseline parse calibration",
                source = desktopSource,
                startMarker =
                    "const p3bB2iNaturalParseCalibrationBaselineRuns = 3;",
                endMarker = "\nconst verifySemanticError",
            )
            requireMarkers(
                label = "desktop three-baseline parse calibration",
                source = desktopParseCalibration,
                markers = listOf(
                    "const p3bB2iNaturalParseCalibrationBaselineRuns = 3;",
                    "const p3bB2iNaturalParseCalibrationMarginMilliseconds = 100;",
                    "const p3bB2iNaturalParsePreconditionRuns = 2;",
                    "const p3bB2iNaturalParseFaultAttempts = 1;",
                    "const calibrateP3bB2iNaturalParseRequestStartDeadline = (",
                    "timingRuns.length === p3bB2iNaturalParseCalibrationBaselineRuns",
                    "const phase = \"parse\";",
                    "sequenceTwoElapsedRealtimeNanosSinceProjectStart",
                    "completedElapsedRealtimeNanosSinceProjectStart",
                    "rawUpperNanos - rawLowerNanos >= marginNanos * 2n",
                    "(minimumTimeoutMilliseconds + maximumTimeoutMilliseconds) / 2",
                    "timeoutNanos - rawLowerNanos >= marginNanos",
                    "rawUpperNanos - timeoutNanos >= marginNanos",
                    "const verifyP3bB2iNaturalParseDeadlineCalibration = async () =>",
                    "const project = naturalParseTimeoutWorkloadSnapshot();",
                    "authority.commonDirectorySegmentPrefix === \"p\"",
                    "JSON.stringify(naturalParseTimeoutCommonDirectorySegments)",
                    "`p\${index.toString().padStart(2, \"0\")}`",
                    "naturalParseTimeoutCommonDirectory ===",
                    "path.dirname(root.path) ===",
                    "naturalParseTimeoutCommonDirectory",
                    "authority.sourcePathDepth",
                    "path.dirname(ambientDeclarations[0].path) ===",
                    "aggregatePathCharacters(project.files) ===",
                    "authority.aggregatePathCharacters",
                    "maximumPathCharacters(project.files) ===",
                    "authority.maximumPathCharacters",
                    "inputTransportFrameCount ===",
                    "authority.inputTransportFrameCount",
                    "authority.referenceRootIndex ===",
                    "Math.ceil(authority.rootFiles / 4)",
                    "Natural parse-timeout imports are not confined to roots 3 through 5.",
                    "moduleIndex === index",
                    "Natural parse-timeout module imports lost their unique 0..17099 authority.",
                    "Natural parse-timeout fixture escaped ordinary Worker admission.",
                    "p3bB2iNaturalParsePreconditionRuns === 2",
                    "preconditionIndex < p3bB2iNaturalParsePreconditionRuns",
                    "desktop-p3b-b2i-natural-parse-precondition",
                    "precondition.phaseProgressEvents",
                    "P3b-b2i natural parse precondition authority drifted.",
                    "const timingRuns = [];",
                    "const expectedProgress = [",
                    "runIndex < p3bB2iNaturalParseCalibrationBaselineRuns",
                    "desktop-p3b-b2i-natural-parse-calibration-",
                    "baseline.phaseProgressEvents",
                    "naturalPhaseOutputAggregateSha256(baseline.result.outputs)",
                    "calibrateP3bB2iNaturalParseRequestStartDeadline(",
                    "calibration.phase === \"parse\"",
                    "p3bB2iNaturalParseCalibrationMarginMilliseconds",
                    "p3bB2iNaturalParseFaultAttempts === 1",
                ),
            )
            requireOrderedMarkers(
                label = "desktop double precondition before three baselines",
                source = desktopParseCalibration,
                markers = listOf(
                    "p3bB2iNaturalParsePreconditionRuns === 2",
                    "preconditionIndex < p3bB2iNaturalParsePreconditionRuns",
                    "desktop-p3b-b2i-natural-parse-precondition-",
                    "P3b-b2i natural parse precondition authority drifted.",
                    "const timingRuns = [];",
                    "runIndex < p3bB2iNaturalParseCalibrationBaselineRuns",
                    "timingRuns.push(baseline.phaseTimingEvidence)",
                    "calibrateP3bB2iNaturalParseRequestStartDeadline(",
                    "p3bB2iNaturalParseFaultAttempts === 1",
                ),
            )
            listOf(
                "syntheticTimingRuns",
                "requestStop",
                "Thread.sleep",
                "setTimeout(",
                "phaseRelative",
                "phaseStartedAt",
                "elapsedSincePhase",
            ).forEach { forbidden ->
                check(forbidden !in desktopParseCalibration) {
                    "R2-P3b-b2i desktop parse calibration contains forbidden " +
                        "synthetic or phase-relative control: $forbidden"
                }
            }
            requireMarkers(
                label = "desktop b2i golden registration",
                source = desktopSource,
                markers = listOf(
                    "\"P3b-b2i three-baseline parse request-start deadline calibration\"",
                    "verifyP3bB2iNaturalParseDeadlineCalibration();",
                ),
            )
        }
    }

val verifyR2P3bB2iNaturalParseTimeoutAndroidTestApk =
    tasks.register("verifyR2P3bB2iNaturalParseTimeoutAndroidTestApk") {
        group = "verification"
        description =
            "Verifies that the paired debug app/androidTest APKs contain the R2-P3b-b2i " +
                "parse request-start absolute-deadline calibration, timeout, and cleanup " +
                "evidence matrix; it does not run devices."
        dependsOn("assembleDebug", "assembleDebugAndroidTest")
        dependsOn(verifyR2P3bB2iNaturalParseTimeoutSourcePolicy)
        val appApkDirectory =
            layout.buildDirectory.dir("outputs/apk/debug")
        val testApkDirectory =
            layout.buildDirectory.dir("outputs/apk/androidTest/debug")
        inputs.dir(appApkDirectory)
        inputs.dir(testApkDirectory)
        doLast {
            fun readDexBytes(apkFile: File): List<ByteArray> =
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

            fun requireDexMarkers(
                label: String,
                dexBytes: List<ByteArray>,
                markers: Map<String, String>,
            ) {
                markers.forEach { (markerLabel, markerText) ->
                    val marker = markerText.toByteArray(Charsets.UTF_8)
                    check(dexBytes.any { dex -> dex.containsByteSequence(marker) }) {
                        "R2-P3b-b2i $label is missing $markerLabel marker: $markerText"
                    }
                }
            }

            val appApkFile =
                resolveSingleApkOutput(appApkDirectory.get().asFile)
            val appDexBytes = readDexBytes(appApkFile)
            check(appDexBytes.isNotEmpty()) {
                "R2-P3b-b2i debug app APK contains no DEX entries: $appApkFile"
            }
            requireDexMarkers(
                label = "debug app APK",
                dexBytes = appDexBytes,
                markers = linkedMapOf(
                    "phase timing evidence type" to
                        "R2ProjectExecutionPhaseTimingEvidence",
                    "phase termination evidence type" to
                        "R2ProjectPhaseTerminationEvidence",
                    "timing evidence accessor" to
                        "lastProjectExecutionPhaseTimingEvidenceForTesting",
                    "termination evidence accessor" to
                        "lastProjectPhaseTerminationEvidenceForTesting",
                    "absolute deadline field" to
                        "deadlineAtElapsedRealtimeNanos",
                    "absolute wait helper" to
                        "awaitUntilElapsedRealtimeNanos",
                    "atomic deadline terminal helper" to
                        "tryCompleteBeforeElapsedRealtimeDeadline",
                    "atomic stop selection helper" to
                        "selectAcceptedProjectStopLocked",
                    "framed progress kind" to "projectPhaseProgress",
                    "termination progress receipt" to
                        "phaseProgressSequenceAtTermination",
                    "timeout outcome" to
                        "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_TIMEOUT",
                ),
            )

            val testApkFile =
                resolveSingleApkOutput(testApkDirectory.get().asFile)
            val testDexBytes = readDexBytes(testApkFile)
            check(testDexBytes.isNotEmpty()) {
                "R2-P3b-b2i androidTest APK contains no DEX entries: $testApkFile"
            }
            requireDexMarkers(
                label = "androidTest APK",
                dexBytes = testDexBytes,
                markers = linkedMapOf(
                    "natural parse timeout instrumentation class" to
                        "R2ProjectCompilerInstrumentationTest",
                    "request-start parse timeout method" to
                        "naturalParseRequestStartAbsoluteDeadlineTimeoutUsesCalibratedWindowAndRecovers",
                    "natural parse workload fixture" to
                        "R2NaturalParseTimeoutWorkloadFixture",
                    "parse calibration helper" to
                        "calibrateNaturalParseDeadline",
                    "timing evidence type" to
                        "R2ProjectExecutionPhaseTimingEvidence",
                    "calibration baseline evidence" to
                        "NATURAL_PARSE_TIMEOUT_BASELINE_PASS",
                    "successful precondition evidence" to
                        "NATURAL_PARSE_TIMEOUT_PRECONDITION_PASS",
                    "calibration window evidence" to
                        "NATURAL_PARSE_TIMEOUT_WINDOW_PASS",
                    "parse timeout evidence" to
                        "NATURAL_PARSE_TIMEOUT_PASS",
                    "parse timeout matrix evidence" to
                        "NATURAL_PARSE_TIMEOUT_MATRIX_PASS",
                    "natural parse timeout log tag" to
                        "R2P3bNaturalParseTimeout",
                    "single parse target evidence" to
                        "phases=1 preconditionRuns=",
                    "three calibration runs evidence" to
                        "calibrationRuns=",
                    "parse timeout field" to
                        "parseTimeoutMs=",
                    "request-start deadline evidence" to
                        "requestStartAbsoluteDeadline=true",
                    "ordinary admission evidence" to
                        "ordinaryAdmission=true",
                    "ordinary zero-spin evidence" to
                        "workerSpinMilliseconds=0 workerSpinPhase=none",
                    "deadline lower-bound assertion" to
                        "Natural parse timeout fired before its request-start absolute deadline.",
                    "deadline selection assertion" to
                        "Natural parse stop selection preceded the absolute deadline.",
                    "small successor assertion" to
                        "The natural parse timeout successor did not retire a newer session.",
                    "host cleanup assertion" to
                        "Natural parse timeout host close did not prove cleanup.",
                    "spool cleanup assertion" to
                        "Natural parse timeout cleanup left a project spool file.",
                    "parse root namespace" to
                        "probe-parse-root-",
                    "parse ambient declaration namespace" to
                        "probe-parse-ambient.d.ts",
                    "parse ambient reference" to
                        "/// <reference path=\"./probe-parse-ambient.d.ts\" />",
                    "parse unique module import" to
                        "import type { ProbeType as ProbeType",
                    "parse wildcard module declaration" to
                        "declare module \"probe-*\" { export type ProbeType = number; }",
                    "parse timeout spool namespace" to
                        "r2-p3b-natural-parse-timeout",
                    "frozen output aggregate" to
                        "c16cf1e19354655321bc7bd13bea9840900f2f2b2bf67793b4d1b926ee42e6b3",
                ),
            )
        }
    }

val verifyR2P3bB2jNaturalEmitTimeoutSourcePolicy =
    tasks.register("verifyR2P3bB2jNaturalEmitTimeoutSourcePolicy") {
        group = "verification"
        description =
            "Verifies the R2-P3b-b2j ordinary request-start absolute-deadline " +
                "two-precondition emit calibration, exact teardown, cleanup, and successor policy."
        dependsOn(verifyR2P3bB2iNaturalParseTimeoutSourcePolicy)
        dependsOn(verifyR2ProjectCompilerDesktop)
        dependsOn("testDebugUnitTest")
        inputs.files(
            r2ProjectCompilerInstrumentationSource,
            r2NaturalEmitTimeoutWorkloadFixtureSource,
            r2ProjectCompilerDesktopHarnessSource,
        )
        doLast {
            fun requireMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                markers.forEach { marker ->
                    check(marker in source) {
                        "R2-P3b-b2j $label is missing: $marker"
                    }
                }
            }

            fun sourceSection(
                label: String,
                source: String,
                startMarker: String,
                endMarker: String,
            ): String {
                val start = source.indexOf(startMarker)
                check(start >= 0) {
                    "R2-P3b-b2j $label start is missing: $startMarker"
                }
                val end = source.indexOf(endMarker, start + startMarker.length)
                check(end > start) {
                    "R2-P3b-b2j $label end is missing: $endMarker"
                }
                return source.substring(start, end)
            }

            fun requireOrderedMarkers(
                label: String,
                source: String,
                markers: List<String>,
            ) {
                var cursor = 0
                markers.forEach { marker ->
                    val index = source.indexOf(marker, cursor)
                    check(index >= cursor) {
                        "R2-P3b-b2j $label ordering is missing: $marker"
                    }
                    cursor = index + marker.length
                }
            }

            val workloadSource =
                r2NaturalEmitTimeoutWorkloadFixtureSource.asFile
                    .readText(Charsets.UTF_8)
            fun frozenWorkloadLong(name: String): Long {
                val match = Regex(
                    """(?:private\s+)?const val $name = ([0-9_]+)L?""",
                ).find(workloadSource)
                check(match != null) {
                    "R2-P3b-b2j emit workload constant is missing: $name"
                }
                return match.groupValues[1].replace("_", "").toLong()
            }
            val frozenAuthority = linkedMapOf(
                "ROOT_COUNT" to 8L,
                "LIGHT_ROOT_COUNT" to 2L,
                "HEAVY_ROOT_COUNT" to 6L,
                "OPTIONAL_CHAIN_COUNT_PER_HEAVY_ROOT" to 18L,
                "OPTIONAL_CHAIN_DEPTH" to 110L,
                "TRUSTED_DECLARATION_FILE_COUNT" to 108L,
                "TRUSTED_DECLARATION_BYTES" to 3_784_758L,
                "TRUSTED_DECLARATION_TOKENS" to 86_329L,
                "ROOT_BYTES" to 57_074L,
                "ROOT_TOKENS" to 24_310L,
                "MAXIMUM_ROOT_BYTES" to 9_502L,
                "MAXIMUM_ROOT_TOKENS" to 4_049L,
                "TOTAL_FILE_COUNT" to 116L,
                "TOTAL_BYTES" to 3_841_832L,
                "TOTAL_TOKENS" to 110_639L,
                "TOTAL_PATH_CHARACTERS" to 4_145L,
                "MAXIMUM_PATH_CHARACTERS" to 46L,
                "INPUT_TRANSPORT_FRAME_COUNT" to 400L,
                "OUTPUT_FILE_COUNT" to 16L,
                "JAVASCRIPT_OUTPUT_BYTES" to 769_510L,
                "SOURCE_MAP_OUTPUT_BYTES" to 196_204L,
                "OUTPUT_BYTES" to 965_714L,
            )
            frozenAuthority.forEach { (name, expected) ->
                check(frozenWorkloadLong(name) == expected) {
                    "R2-P3b-b2j emit workload authority drifted: $name"
                }
            }
            check(
                frozenAuthority.getValue("LIGHT_ROOT_COUNT") +
                    frozenAuthority.getValue("HEAVY_ROOT_COUNT") ==
                    frozenAuthority.getValue("ROOT_COUNT") &&
                    frozenAuthority.getValue("ROOT_COUNT") +
                    frozenAuthority.getValue(
                        "TRUSTED_DECLARATION_FILE_COUNT",
                    ) == frozenAuthority.getValue("TOTAL_FILE_COUNT") &&
                    frozenAuthority.getValue("ROOT_BYTES") +
                    frozenAuthority.getValue(
                        "TRUSTED_DECLARATION_BYTES",
                    ) == frozenAuthority.getValue("TOTAL_BYTES") &&
                    frozenAuthority.getValue("ROOT_TOKENS") +
                    frozenAuthority.getValue(
                        "TRUSTED_DECLARATION_TOKENS",
                    ) == frozenAuthority.getValue("TOTAL_TOKENS") &&
                    frozenAuthority.getValue("ROOT_COUNT") * 2L ==
                    frozenAuthority.getValue("OUTPUT_FILE_COUNT") &&
                    frozenAuthority.getValue("LIGHT_ROOT_COUNT") * 2L ==
                    (
                        frozenAuthority.getValue("OUTPUT_FILE_COUNT") + 3L
                        ) / 4L &&
                    frozenAuthority.getValue(
                        "JAVASCRIPT_OUTPUT_BYTES",
                    ) +
                    frozenAuthority.getValue(
                        "SOURCE_MAP_OUTPUT_BYTES",
                    ) == frozenAuthority.getValue("OUTPUT_BYTES") &&
                    frozenAuthority.getValue("TOTAL_FILE_COUNT") <= 512L &&
                    frozenAuthority.getValue("TOTAL_BYTES") <=
                    8L * 1024L * 1024L &&
                    frozenAuthority.getValue("TOTAL_TOKENS") <= 262_144L &&
                    frozenAuthority.getValue("MAXIMUM_ROOT_TOKENS") <=
                    65_536L &&
                    frozenAuthority.getValue("OUTPUT_FILE_COUNT") <= 512L &&
                    frozenAuthority.getValue("OUTPUT_BYTES") <=
                    1024L * 1024L &&
                    1024L * 1024L -
                    frozenAuthority.getValue("OUTPUT_BYTES") >=
                    64L * 1024L,
            ) {
                "R2-P3b-b2j emit workload escaped ordinary admission limits."
            }
            requireMarkers(
                label = "frozen emit-timeout workload",
                source = workloadSource,
                markers = listOf(
                    "request-start natural emit timeout evidence",
                    "R2NaturalEmitTimeoutWorkloadFixture",
                    "\"e7eac5f110b4dff7bbb732ea47c3f21c083b44fa215b740895a8e8bac9ba4fa6\"",
                    "R2ProjectSnapshotLimits.PROVISIONAL_WORKER",
                    "check(LIGHT_ROOT_COUNT + HEAVY_ROOT_COUNT == ROOT_COUNT)",
                    "(OUTPUT_FILE_COUNT - 1) / 4 + 1 ==",
                    "LIGHT_ROOT_COUNT * OUTPUTS_PER_ROOT",
                    "\"/sources/natural-emit-timeout-\"",
                    "if (index < LIGHT_ROOT_COUNT)",
                    "declare const q: any;",
                    "repeat(OPTIONAL_CHAIN_COUNT_PER_HEAVY_ROOT)",
                    "repeat(OPTIONAL_CHAIN_DEPTH)",
                    "append(\"?.p\")",
                    "depthIndex.toString(36)",
                    "append(\" ?? \")",
                    "append(chainIndex)",
                    "snapshot.fileInventory.sumOf { file -> file.path.length } ==",
                    "TOTAL_PATH_CHARACTERS",
                    "snapshot.fileInventory.maxOf { file -> file.path.length } ==",
                    "MAXIMUM_PATH_CHARACTERS",
                ),
            )
            listOf(
                "Thread.sleep",
                "SystemClock.sleep",
                "workerSpin",
                "ExpandedWorker",
                "FILE_BACKED_ARCHIVE",
                "requestStop",
            ).forEach { forbidden ->
                check(forbidden !in workloadSource) {
                    "R2-P3b-b2j frozen emit workload contains forbidden " +
                        "control or expanded admission: $forbidden"
                }
            }

            val instrumentationSource =
                r2ProjectCompilerInstrumentationSource.asFile.readText(
                    Charsets.UTF_8,
                )
            val timeoutMatrix = sourceSection(
                label = "natural emit timeout matrix",
                source = instrumentationSource,
                startMarker =
                    "fun naturalEmitRequestStartAbsoluteDeadlineTimeoutUsesCalibratedWindowAndRecovers()",
                endMarker = "\n    @Test",
            )
            requireMarkers(
                label = "natural emit timeout matrix",
                source = timeoutMatrix,
                markers = listOf(
                    "R2NaturalEmitTimeoutWorkloadFixture.createSnapshot(",
                    "R2NaturalEmitTimeoutWorkloadFixture.TOTAL_FILE_COUNT",
                    "R2NaturalEmitTimeoutWorkloadFixture.TOTAL_BYTES",
                    "R2NaturalEmitTimeoutWorkloadFixture.INPUT_TRANSPORT_FRAME_COUNT",
                    "fun successfulNaturalEmitRun(",
                    "check(NATURAL_EMIT_TIMEOUT_PRECONDITION_RUNS == 2)",
                    "repeat(NATURAL_EMIT_TIMEOUT_PRECONDITION_RUNS)",
                    "NATURAL_EMIT_TIMEOUT_PRECONDITION_PASS",
                    "repeat(NATURAL_EMIT_TIMEOUT_CALIBRATION_RUNS)",
                    "TypeScriptCompilerContract.MAX_TIMEOUT_MS",
                    ".JAVASCRIPT_OUTPUT_BYTES",
                    ".SOURCE_MAP_OUTPUT_BYTES",
                    ".OUTPUT_AGGREGATE_SHA256",
                    "expectedNaturalEmitTimeoutProgressHistory()",
                    "NATURAL_EMIT_TIMEOUT_BASELINE_PASS",
                    "calibration = calibrateNaturalEmitDeadline(calibrationRuns)",
                    "check(NATURAL_EMIT_TIMEOUT_FAULT_ATTEMPTS == 1)",
                    "timeoutMs = selectedCalibration.timeoutMillis",
                    "TypeScriptCompilerContract.ERROR_TIMEOUT",
                    "assertEquals(null, timedOut.compilation)",
                    "timedOut.elapsedMs >= selectedCalibration.timeoutMillis",
                    "R2ProjectExecutionPhase.EMIT",
                    "NATURAL_EMIT_TIMEOUT_PROGRESS_SEQUENCE",
                    "evidence.stopSelectedAtElapsedRealtimeNanos >=",
                    "evidence.deadlineAtElapsedRealtimeNanos",
                    "evidence.terminatedWorkerGeneration + 1",
                    "NATURAL_EMIT_TIMEOUT_PASS",
                    "workerSpinMilliseconds=0 workerSpinPhase=none",
                    "snapshot = successorSnapshot",
                    "The natural emit timeout successor did not retire a newer ",
                    "Natural emit timeout host close did not prove cleanup.",
                    "Natural emit timeout cleanup left a project spool file.",
                    "NATURAL_EMIT_TIMEOUT_MATRIX_PASS",
                    "\"phases=1 preconditionRuns=\"",
                    "\"calibrationRuns=\"",
                    "\"emitTimeoutMs=",
                    "\"optionalChainCount=",
                    "\"optionalChainDepth=",
                    "\"lightRoots=",
                    "\"heavyRoots=",
                    "ordinaryAdmission=true requestStartAbsoluteDeadline=true",
                    "hostCleanupProven=true spoolCleanupProven=true",
                ),
            )
            requireOrderedMarkers(
                label = "freeze emit calibration before its single fault",
                source = timeoutMatrix,
                markers = listOf(
                    "check(NATURAL_EMIT_TIMEOUT_PRECONDITION_RUNS == 2)",
                    "repeat(NATURAL_EMIT_TIMEOUT_PRECONDITION_RUNS)",
                    "successfulNaturalEmitRun(",
                    "NATURAL_EMIT_TIMEOUT_PRECONDITION_PASS",
                    "val calibrationRuns =",
                    "repeat(NATURAL_EMIT_TIMEOUT_CALIBRATION_RUNS)",
                    "calibration = calibrateNaturalEmitDeadline(calibrationRuns)",
                    "check(NATURAL_EMIT_TIMEOUT_FAULT_ATTEMPTS == 1)",
                    "val selectedCalibration = requireNotNull(calibration)",
                    "val timedOut = executor.submit(",
                    "host.compileProject(",
                    "timeoutMs = selectedCalibration.timeoutMillis",
                ),
            )
            check(
                Regex("""host\.compileProject\(""")
                    .findAll(timeoutMatrix)
                    .count() == 3,
            ) {
                "R2-P3b-b2j must use ordinary compileProject exactly in its " +
                    "shared successful-run helper, single fixed emit deadline, " +
                    "and successor."
            }
            listOf(
                "host.requestStop(",
                "armProjectAbsoluteDeadlineForTesting",
                "rebaseProjectAbsoluteDeadlineForTesting",
                "shortenProjectAbsoluteDeadlineForTesting",
                "setProjectAbsoluteDeadlineForTesting",
                "compileProjectWithExpandedWorkerCapacityForTesting",
                "compileProjectWithSynchronousWorkerSpinForTesting",
                "workerSpinMilliseconds =",
                "workerSpinPhase =",
                "terminateActiveProjectRendererForTesting",
                "waitForExecutionPhaseProgress(",
                "R2ProjectStopCause.CANCELLED",
                "TypeScriptCompilerContract.ERROR_CANCELLED",
                "Thread.sleep(",
                "SystemClock.sleep(",
                "repeat(NATURAL_EMIT_TIMEOUT_FAULT_ATTEMPTS",
                "phaseRelative",
                "phaseStartedAt",
                "elapsedSincePhase",
                "sequenceTwoToCompleted",
            ).forEach { forbidden ->
                check(forbidden !in timeoutMatrix) {
                    "R2-P3b-b2j emit timeout matrix contains forbidden " +
                        "mid-flight or phase-relative control: $forbidden"
                }
            }

            val calibrationSource = sourceSection(
                label = "three-baseline emit calibration helper",
                source = instrumentationSource,
                startMarker = "private fun calibrateNaturalEmitDeadline(",
                endMarker = "\n    private fun calibrateNaturalParseDeadline(",
            )
            requireMarkers(
                label = "three-baseline emit calibration helper",
                source = calibrationSource,
                markers = listOf(
                    "NATURAL_EMIT_TIMEOUT_CALIBRATION_RUNS",
                    "val phase = R2ProjectExecutionPhase.EMIT",
                    "samples.maxOf",
                    "sequenceTwoElapsedRealtimeNanosSinceProjectStart",
                    "+ 1L",
                    "samples.minOf",
                    "completedElapsedRealtimeNanosSinceProjectStart - 1L",
                    "maximumTimeoutMillis - minimumTimeoutMillis >=",
                    "NATURAL_EMIT_TIMEOUT_MINIMUM_MARGIN_MILLIS * 2L",
                    "(maximumTimeoutMillis - minimumTimeoutMillis) / 2L",
                    "leftMarginMillis >=",
                    "rightMarginMillis >=",
                    "sequenceTwoElapsedRealtimeNanosSinceProjectStart <",
                    "timeoutNanos <",
                    "completedElapsedRealtimeNanosSinceProjectStart",
                    "NATURAL_EMIT_TIMEOUT_WINDOW_PASS",
                ),
            )
            listOf(
                "System.nanoTime(",
                "SystemClock.",
                "Thread.sleep(",
                "synthetic",
                "phaseRelative",
                "phaseStartedAt",
                "elapsedSincePhase",
            ).forEach { forbidden ->
                check(forbidden !in calibrationSource) {
                    "R2-P3b-b2j emit calibration is not purely request-start " +
                        "evidence: $forbidden"
                }
            }
            requireMarkers(
                label = "frozen emit timeout constants",
                source = instrumentationSource,
                markers = listOf(
                    "const val NATURAL_EMIT_TIMEOUT_CALIBRATION_RUNS = 3",
                    "const val NATURAL_EMIT_TIMEOUT_PRECONDITION_RUNS = 2",
                    "const val NATURAL_EMIT_TIMEOUT_FAULT_ATTEMPTS = 1",
                    "const val NATURAL_EMIT_TIMEOUT_MINIMUM_MARGIN_MILLIS = 100L",
                    "const val NATURAL_EMIT_TIMEOUT_PROGRESS_SEQUENCE = 2",
                    "const val NATURAL_EMIT_TIMEOUT_MAXIMUM_DEADLINE_OVERSHOOT_MILLIS =",
                    "const val NATURAL_EMIT_TIMEOUT_SPOOL_DIRECTORY =",
                    "\"r2-p3b-natural-emit-timeout\"",
                    "const val NATURAL_EMIT_TIMEOUT_LOG_TAG =",
                    "\"R2P3bNaturalEmitTimeout\"",
                ),
            )

            val desktopSource =
                r2ProjectCompilerDesktopHarnessSource.asFile.readText(
                    Charsets.UTF_8,
                )
            val desktopWorkload = sourceSection(
                label = "desktop emit-timeout workload mirror",
                source = desktopSource,
                startMarker =
                    "const naturalEmitTimeoutWorkloadAuthority = Object.freeze({",
                endMarker = "\nconst frameBytes",
            )
            requireMarkers(
                label = "desktop emit-timeout workload mirror",
                source = desktopWorkload,
                markers = listOf(
                    "aggregatePathCharacters: 4_145,",
                    "declarationBytes: 3_784_758,",
                    "declarationFiles: 108,",
                    "declarationTokens: 86_329,",
                    "heavyRootFiles: 6,",
                    "inputTransportFrameCount: 400,",
                    "javascriptOutputBytes: 769_510,",
                    "lightRootFiles: 2,",
                    "maximumPathCharacters: 46,",
                    "maximumRootBytes: 9_502,",
                    "maximumRootTokens: 4_049,",
                    "optionalChainsPerHeavyRoot: 18,",
                    "optionalChainDepth: 110,",
                    "\"e7eac5f110b4dff7bbb732ea47c3f21c083b44fa215b740895a8e8bac9ba4fa6\"",
                    "outputBytes: 965_714,",
                    "outputFiles: 16,",
                    "rootBytes: 57_074,",
                    "rootFiles: 8,",
                    "rootTokens: 24_310,",
                    "sourceMapOutputBytes: 196_204,",
                    "sourcePathDepth: 2,",
                    "totalBytes: 3_841_832,",
                    "totalFiles: 116,",
                    "totalTokens: 110_639,",
                    "const naturalEmitTimeoutSourcePath = (index) =>",
                    "const naturalEmitTimeoutSourceText = (index) =>",
                    "index < authority.lightRootFiles",
                    "\"declare const q: any;\\n\"",
                    "authority.optionalChainsPerHeavyRoot",
                    "authority.optionalChainDepth",
                    "depthIndex.toString(36)",
                    "const naturalEmitTimeoutWorkloadSnapshot = () =>",
                ),
            )

            val desktopCalibration = sourceSection(
                label = "desktop three-baseline emit calibration",
                source = desktopSource,
                startMarker =
                    "const p3bB2jNaturalEmitCalibrationBaselineRuns = 3;",
                endMarker = "\nconst verifySemanticError",
            )
            requireMarkers(
                label = "desktop three-baseline emit calibration",
                source = desktopCalibration,
                markers = listOf(
                    "const p3bB2jNaturalEmitCalibrationBaselineRuns = 3;",
                    "const p3bB2jNaturalEmitCalibrationMarginMilliseconds = 100;",
                    "const p3bB2jNaturalEmitPreconditionRuns = 2;",
                    "const p3bB2jNaturalEmitFaultAttempts = 1;",
                    "const calibrateP3bB2jNaturalEmitRequestStartDeadline = (",
                    "const phase = \"emit\";",
                    "rawUpperNanos - rawLowerNanos >= marginNanos * 2n",
                    "Natural emit midpoint escaped an observed strict interval.",
                    "const verifyP3bB2jNaturalEmitDeadlineCalibration = async () =>",
                    "const project = naturalEmitTimeoutWorkloadSnapshot();",
                    "authority.optionalChainsPerHeavyRoot *",
                    "authority.optionalChainDepth",
                    "root.text.includes(\"?.p31 ?? 17,\")",
                    "authority.lightRootFiles * 2 ===",
                    "Math.ceil(authority.outputFiles / 4)",
                    "1024 * 1024 - authority.outputBytes >= 64 * 1024",
                    "const expectedProgress = [",
                    "const verifySuccessfulRun = async (requestId) =>",
                    "p3bB2jNaturalEmitPreconditionRuns === 2",
                    "desktop-p3b-b2j-natural-emit-precondition-",
                    "runIndex < p3bB2jNaturalEmitCalibrationBaselineRuns",
                    "desktop-p3b-b2j-natural-emit-calibration-",
                    "calibrateP3bB2jNaturalEmitRequestStartDeadline(",
                    "calibration.phase === \"emit\"",
                    "p3bB2jNaturalEmitFaultAttempts === 1",
                ),
            )
            requireOrderedMarkers(
                label = "desktop double precondition before three baselines",
                source = desktopCalibration,
                markers = listOf(
                    "p3bB2jNaturalEmitPreconditionRuns === 2",
                    "preconditionIndex < p3bB2jNaturalEmitPreconditionRuns",
                    "desktop-p3b-b2j-natural-emit-precondition-",
                    "const timingRuns = [];",
                    "runIndex < p3bB2jNaturalEmitCalibrationBaselineRuns",
                    "calibrateP3bB2jNaturalEmitRequestStartDeadline(",
                    "p3bB2jNaturalEmitFaultAttempts === 1",
                ),
            )
            listOf(
                "syntheticTimingRuns",
                "requestStop",
                "Thread.sleep",
                "setTimeout(",
                "phaseRelative",
                "phaseStartedAt",
                "elapsedSincePhase",
            ).forEach { forbidden ->
                check(forbidden !in desktopCalibration) {
                    "R2-P3b-b2j desktop emit calibration contains forbidden " +
                        "synthetic or phase-relative control: $forbidden"
                }
            }
            requireMarkers(
                label = "desktop b2j golden registration",
                source = desktopSource,
                markers = listOf(
                    "\"P3b-b2j three-baseline emit request-start deadline calibration\"",
                    "verifyP3bB2jNaturalEmitDeadlineCalibration();",
                ),
            )
        }
    }

val verifyR2P3bB2jNaturalEmitTimeoutAndroidTestApk =
    tasks.register("verifyR2P3bB2jNaturalEmitTimeoutAndroidTestApk") {
        group = "verification"
        description =
            "Verifies that the paired debug app/androidTest APKs contain the R2-P3b-b2j " +
                "emit request-start absolute-deadline calibration, timeout, and cleanup " +
                "evidence matrix; it does not run devices."
        dependsOn("assembleDebug", "assembleDebugAndroidTest")
        dependsOn(verifyR2P3bB2jNaturalEmitTimeoutSourcePolicy)
        val appApkDirectory =
            layout.buildDirectory.dir("outputs/apk/debug")
        val testApkDirectory =
            layout.buildDirectory.dir("outputs/apk/androidTest/debug")
        inputs.dir(appApkDirectory)
        inputs.dir(testApkDirectory)
        doLast {
            fun readDexBytes(apkFile: File): List<ByteArray> =
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

            fun requireDexMarkers(
                label: String,
                dexBytes: List<ByteArray>,
                markers: Map<String, String>,
            ) {
                markers.forEach { (markerLabel, markerText) ->
                    val marker = markerText.toByteArray(Charsets.UTF_8)
                    check(dexBytes.any { dex -> dex.containsByteSequence(marker) }) {
                        "R2-P3b-b2j $label is missing $markerLabel marker: $markerText"
                    }
                }
            }

            val appApkFile =
                resolveSingleApkOutput(appApkDirectory.get().asFile)
            val appDexBytes = readDexBytes(appApkFile)
            check(appDexBytes.isNotEmpty()) {
                "R2-P3b-b2j debug app APK contains no DEX entries: $appApkFile"
            }
            requireDexMarkers(
                label = "debug app APK",
                dexBytes = appDexBytes,
                markers = linkedMapOf(
                    "phase timing evidence type" to
                        "R2ProjectExecutionPhaseTimingEvidence",
                    "phase termination evidence type" to
                        "R2ProjectPhaseTerminationEvidence",
                    "timing evidence accessor" to
                        "lastProjectExecutionPhaseTimingEvidenceForTesting",
                    "termination evidence accessor" to
                        "lastProjectPhaseTerminationEvidenceForTesting",
                    "absolute deadline field" to
                        "deadlineAtElapsedRealtimeNanos",
                    "absolute wait helper" to
                        "awaitUntilElapsedRealtimeNanos",
                    "atomic deadline terminal helper" to
                        "tryCompleteBeforeElapsedRealtimeDeadline",
                    "atomic stop selection helper" to
                        "selectAcceptedProjectStopLocked",
                    "framed progress kind" to "projectPhaseProgress",
                    "termination progress receipt" to
                        "phaseProgressSequenceAtTermination",
                    "timeout outcome" to
                        "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_TIMEOUT",
                ),
            )

            val testApkFile =
                resolveSingleApkOutput(testApkDirectory.get().asFile)
            val testDexBytes = readDexBytes(testApkFile)
            check(testDexBytes.isNotEmpty()) {
                "R2-P3b-b2j androidTest APK contains no DEX entries: $testApkFile"
            }
            val temporarySuccessProbeMarker =
                "R2NaturalEmitTimeoutSuccessProbe".toByteArray(Charsets.UTF_8)
            check(
                testDexBytes.none { dex ->
                    dex.containsByteSequence(temporarySuccessProbeMarker)
                },
            ) {
                "R2-P3b-b2j androidTest APK retained the temporary " +
                    "success-only workload selector."
            }
            requireDexMarkers(
                label = "androidTest APK",
                dexBytes = testDexBytes,
                markers = linkedMapOf(
                    "natural emit timeout instrumentation class" to
                        "R2ProjectCompilerInstrumentationTest",
                    "request-start emit timeout method" to
                        "naturalEmitRequestStartAbsoluteDeadlineTimeoutUsesCalibratedWindowAndRecovers",
                    "natural emit workload fixture" to
                        "R2NaturalEmitTimeoutWorkloadFixture",
                    "emit calibration helper" to
                        "calibrateNaturalEmitDeadline",
                    "calibration baseline evidence" to
                        "NATURAL_EMIT_TIMEOUT_BASELINE_PASS",
                    "successful precondition evidence" to
                        "NATURAL_EMIT_TIMEOUT_PRECONDITION_PASS",
                    "calibration window evidence" to
                        "NATURAL_EMIT_TIMEOUT_WINDOW_PASS",
                    "emit timeout evidence" to
                        "NATURAL_EMIT_TIMEOUT_PASS",
                    "emit timeout matrix evidence" to
                        "NATURAL_EMIT_TIMEOUT_MATRIX_PASS",
                    "natural emit timeout log tag" to
                        "R2P3bNaturalEmitTimeout",
                    "single emit target evidence" to
                        "phases=1 preconditionRuns=",
                    "three calibration runs evidence" to
                        "calibrationRuns=",
                    "emit timeout field" to
                        "emitTimeoutMs=",
                    "optional chain count evidence" to
                        "optionalChainCount=",
                    "optional chain depth evidence" to
                        "optionalChainDepth=",
                    "light-root evidence" to
                        "lightRoots=",
                    "heavy-root evidence" to
                        "heavyRoots=",
                    "request-start deadline evidence" to
                        "requestStartAbsoluteDeadline=true",
                    "ordinary admission evidence" to
                        "ordinaryAdmission=true",
                    "ordinary zero-spin evidence" to
                        "workerSpinMilliseconds=0 workerSpinPhase=none",
                    "deadline lower-bound assertion" to
                        "Natural emit timeout fired before its request-start absolute deadline.",
                    "deadline selection assertion" to
                        "Natural emit stop selection preceded the absolute deadline.",
                    "small successor assertion" to
                        "The natural emit timeout successor did not retire a newer session.",
                    "host cleanup assertion" to
                        "Natural emit timeout host close did not prove cleanup.",
                    "spool cleanup assertion" to
                        "Natural emit timeout cleanup left a project spool file.",
                    "emit root namespace" to
                        "natural-emit-timeout-",
                    "emit optional-chain tail" to
                        "?.p",
                    "emit timeout spool namespace" to
                        "r2-p3b-natural-emit-timeout",
                    "frozen output aggregate" to
                        "e7eac5f110b4dff7bbb732ea47c3f21c083b44fa215b740895a8e8bac9ba4fa6",
                ),
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
            /*
             * Production supervision owns two explicit in-package components. The non-shipping
             * exact-capacity evidence build also attests one fixed same-signer TEST_ONLY fixture.
             * Keep every exception path-scoped and operation-scoped: optional plugin signatures
             * remain forbidden in these files, as do all unlisted discovery/binding primitives.
             */
            val allowedPathScopedOperations = mapOf(
                "app/src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                    "R2CompilerProcessSupervisorJobService.kt" to
                    setOf("explicit component construction"),
                "app/src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                    "R2CompilerProcessSupervisorControl.kt" to
                    setOf(
                        "explicit component construction",
                        "service binding",
                    ),
                "app/src/main/java/io/github/supermonster003/autojs6/plugin/typescript/" +
                    "TypeScriptCompilerPluginService.kt" to
                    setOf(
                        "PackageManager access",
                        "application info discovery",
                    ),
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
                    val relativePath =
                        file.relativeTo(rootProject.projectDir).invariantSeparatorsPath
                    val allowedOperations =
                        allowedPathScopedOperations[relativePath].orEmpty()
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
                        .filterNot(allowedOperations::contains)
                        .map { label ->
                            "$relativePath: $label"
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
        description =
            "Verifies the pinned TypeScript and declaration distributions and lock metadata."
        dependsOn(verifyTypeScriptDistribution, verifyR2DeclarationDistribution)
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
            }
            verifyR2FileDescriptorNativeBridgeInApk(apkFiles.single())
            val manifestText = mergedManifest.get().asFile.readText()
            check("org.autojs.plugin.INFO" in manifestText)
            check("org.autojs.plugin.typescript.COMPILER" in manifestText)
            check("android:process=\":typescript_compiler\"" in manifestText)
            check("android.permission.INTERNET" !in manifestText)
        }
    }

    matching {
        val taskName = it.name
        taskName.contains("lint", ignoreCase = true) ||
            (taskName.startsWith("merge") && taskName.endsWith("Assets"))
    }.configureEach {
        dependsOn(generateTypeScriptDistribution)
        dependsOn(generateR2DeclarationDistribution)
    }
}
