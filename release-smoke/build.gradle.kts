plugins {
    id("org.autojs.build.signs")
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

    signingConfigs {
        if (signs.isValid) {
            create("release") {
                // Match the app module's historical sign.properties path semantics so target
                // and instrumentation use the exact same long-lived certificate.
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
        val releaseSigning = takeIf { signs.isValid }?.let {
            signingConfigs.getByName("release")
        }
        debug {
            releaseSigning?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            releaseSigning?.let { signingConfig = it }
        }
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
