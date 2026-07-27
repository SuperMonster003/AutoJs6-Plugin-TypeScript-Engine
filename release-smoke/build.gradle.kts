plugins {
    id("org.autojs.build.versions")
    id("com.android.application")
}

android {
    namespace = "io.github.supermonster003.autojs6.plugin.typescript.releasesmoke"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = "io.github.supermonster003.autojs6.plugin.typescript.releasesmoke"
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = 1
        versionName = "1"
    }

    lint {
        abortOnError = true
    }
}

dependencies {
    compileOnly(project(":plugin-api:typescript-api"))
}

configurations.matching {
    it.name == "debugRuntimeClasspath" || it.name == "releaseRuntimeClasspath"
}.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
}
