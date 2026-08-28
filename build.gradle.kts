// @Hint: declared here, applied by the modules.
//  ! These plugins used to arrive through the settings buildscript classpath, which the
//  ! platform-versions plugin replaces. Declaring them once here keeps the version in a
//  ! single place and leaves the module scripts untouched, which Groovy modules require:
//  ! their plugins block accepts string literals only, so a computed version cannot go there.
//  ! zh-CN: 这些插件原先经由 settings buildscript classpath 提供, 现已被 platform-versions 插件取代.
//  ! 在此声明一次可使版本只出现在一处, 且模块脚本无须改动 -- 这对 Groovy 模块是必需的:
//  ! 它们的 plugins 块只接受字符串字面量, 无法写入运行时计算出的版本.
plugins {
    id("com.android.application") version System.getProperty("gradle.agp.version") apply false
    id("com.android.library") version System.getProperty("gradle.agp.version") apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version System.getProperty("gradle.kotlin.version") apply false
}

// Top-level build file for the AutoJs6 TypeScript engine plugin.

allprojects {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
    }
}

tasks {
    register<Delete>("clean").configure {
        delete(rootProject.layout.buildDirectory)
    }

    register("verifyR0") {
        group = "verification"
        description = "Runs the complete R0 unit, independence, lock, and APK build gates."
        dependsOn(":plugin-api:typescript-api:testDebugUnitTest")
        dependsOn(":app:testDebugUnitTest")
        dependsOn(":app:verifyOptionalPluginIndependence")
        dependsOn(":app:verifyCompilerDistributionLock")
        dependsOn(":app:verifyDebugCompilerHostApk")
        dependsOn(":app:verifyR0DebugApkContents")
        dependsOn(":app:lintDebug")
        dependsOn(":app:assembleDebug")
    }

    register("verifyR1BinderFdDebug") {
        group = "verification"
        description = "Runs the R1 Binder and file-descriptor gates on connected devices."
        dependsOn(":app:testDebugUnitTest")
        dependsOn(":app:verifyOptionalPluginIndependence")
        dependsOn(":app:connectedDebugAndroidTest")
    }

    register("verifyR1CompilerDistribution") {
        group = "verification"
        description = "Runs the R1 compiler distribution and debug/release APK gates."
        dependsOn("verifyR1CompilerHost")
        dependsOn(gradle.includedBuild("build-logic").task(":convention:check"))
        dependsOn(":app:verifyCompilerDistributionLock")
        dependsOn(":app:verifyTypeScriptDistribution")
        dependsOn(":app:verifyDebugTypeScriptDistributionApk")
        dependsOn(":app:verifyReleaseTypeScriptDistributionApk")
    }

    register("verifyR1CompilerHost") {
        group = "verification"
        description = "Runs the R1 compiler host source and debug/release APK security gates."
        dependsOn(":app:verifyOptionalPluginIndependence")
        dependsOn(":app:verifyCompilerHostSourcePolicy")
        dependsOn(":app:verifyDebugCompilerHostApk")
        dependsOn(":app:verifyReleaseCompilerHostApk")
        dependsOn(":release-smoke:assembleDebug")
        dependsOn(":release-smoke:lintDebug")
    }

    register("verifyR1RhinoProbe") {
        group = "verification"
        description =
            "Builds the opt-in debug Rhino feasibility probe and release exclusion gates."
        dependsOn(":app:verifyR1RhinoProbe")
    }

    register("verifyR1ProtocolCompatibility") {
        group = "verification"
        description =
            "Verifies the frozen v1 API, canonical fixtures, signing, and compat APKs."
        dependsOn(":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze")
        dependsOn(":r1-compat-v1:verifyR1CompatV1Fixture")
    }

    register("verifyR2PublicPreviewFixture") {
        group = "verification"
        description =
            "Runs the complete no-device R2 core through P3b-b2k source, API, APK, lint, and fixture gates."
        dependsOn(":app:verifyR2ProjectCompilerCore")
        dependsOn(":app:verifyDebugCompilerHostApk")
        dependsOn(":app:verifyReleaseCompilerHostApk")
        dependsOn(":app:verifyOptionalPluginIndependence")
        dependsOn(":app:lintDebug")
        dependsOn(":app:verifyR2P3bB2gNaturalPhaseCancellationSourcePolicy")
        dependsOn(":app:verifyR2P3bB2gNaturalPhaseCancellationAndroidTestApk")
        dependsOn(":app:verifyR2P3bB2hNaturalPhaseTimeoutSourcePolicy")
        dependsOn(":app:verifyR2P3bB2hNaturalPhaseTimeoutAndroidTestApk")
        dependsOn(":app:verifyR2P3bB2iNaturalParseTimeoutSourcePolicy")
        dependsOn(":app:verifyR2P3bB2iNaturalParseTimeoutAndroidTestApk")
        dependsOn(":app:verifyR2P3bB2jNaturalEmitTimeoutSourcePolicy")
        dependsOn(":app:verifyR2P3bB2jNaturalEmitTimeoutAndroidTestApk")
        dependsOn(
            ":r2-public-preview:verifyR2P3bB2fExternalPlusOneCleanupSourcePolicy",
        )
        dependsOn(":r2-public-preview:verifyR2P3bB2fExternalPlusOneCleanupApks")
        dependsOn(
            ":r2-public-preview:verifyR2P3bB2kExternalNaturalEmitTimeoutSourcePolicy",
        )
        dependsOn(":r2-public-preview:verifyR2P3bB2kExternalNaturalEmitTimeoutApks")
        dependsOn(":r2-public-preview:verifyR2PublicPreviewFixture")
        dependsOn("verifyR1ProtocolCompatibility")
    }
}

// S1-6 daily verification policy. Android plugins attach lifecycle dependencies lazily, so the
// final replacement happens only after every project has been evaluated. Historical APK, lint,
// release, device, and verifyR2M* evidence tasks stay registered and remain explicitly runnable.
gradle.projectsEvaluated {
    project(":app").tasks.named("check").configure {
        setDependsOn(
            listOf(
                ":app:testDebugUnitTest",
                ":plugin-api:typescript-api:testDebugUnitTest",
                ":app:verifyCompilerDistributionLock",
                ":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze",
                ":app:verifyCompilerSmoke",
                ":app:verifyT3ResidentSessions",
                ":app:verifyS7UserGuidesAndTemplates",
                ":app:verifyS10Maintenance",
                ":app:verifyS10ColdStart",
                ":app:verifyS10TypeScriptUpgrade",
                ":app:verifyS10IssueTriage",
            ),
        )
    }
    project(":plugin-api:typescript-api").tasks.named("check").configure {
        setDependsOn(
            listOf(
                ":plugin-api:typescript-api:testDebugUnitTest",
                ":plugin-api:typescript-api:verifyTypeScriptApiV1Freeze",
            ),
        )
    }
    listOf(
        ":plugin-api:common-plugin-api",
        ":r1-compat-v1",
        ":r2-public-preview",
        ":release-smoke",
    ).forEach { projectPath ->
        project(projectPath).tasks.named("check").configure {
            setDependsOn(emptyList<Any>())
        }
    }
}
