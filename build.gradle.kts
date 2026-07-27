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
}
