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
        dependsOn(":app:verifyNodeRuntimeIndependence")
        dependsOn(":app:verifyCompilerDistributionLock")
        dependsOn(":app:verifyR0DebugApkContents")
        dependsOn(":app:lintDebug")
        dependsOn(":app:assembleDebug")
    }
}
