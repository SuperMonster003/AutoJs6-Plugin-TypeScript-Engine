import com.android.build.api.variant.FilterConfiguration
import groovy.json.JsonSlurper
import java.util.Properties
import java.util.zip.ZipFile
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

android {
    namespace = globalApplicationId
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = globalApplicationId
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = versions.appVersionCode
        versionName = versions.appVersionName

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
    testImplementation(libs.junit)
}

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
            check(lock["schemaVersion"] == 1)
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
            check(lock["runtimeEntry"] == "package/lib/typescript.js")
            check(lock["licenseEntry"] == "package/LICENSE.txt")
            check(lock["thirdPartyNoticeEntry"] == "package/ThirdPartyNoticeText.txt")
            check(lock["standardLibraryGlob"] == "package/lib/lib*.d.ts")
            check(lock["bundled"] == false) {
                "R0 must not claim that the compiler distribution is bundled."
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
    }
}
