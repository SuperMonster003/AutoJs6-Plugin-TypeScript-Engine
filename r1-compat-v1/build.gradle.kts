plugins {
    id("org.autojs.build.signs")
    id("org.autojs.build.versions")
    id("com.android.application")
}

android {
    namespace = "io.github.supermonster003.autojs6.plugin.typescript.compat.v1"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = "io.github.supermonster003.autojs6.plugin.typescript.compat.v1"
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
    implementation(files(rootProject.file("compat/v1/typescript-api-v1.aar")))
}

val verifyR1CompatV1SigningConfiguration =
    tasks.register("verifyR1CompatV1SigningConfiguration") {
        group = "verification"
        description =
            "Checks that built app and compat APK variants have the same signing certificate."
        dependsOn(
            ":app:assembleDebug",
            ":app:assembleRelease",
            "assembleDebug",
            "assembleRelease",
        )

        doLast {
            val appAndroid = project(":app").extensions.getByType(
                com.android.build.api.dsl.ApplicationExtension::class.java,
            )
            listOf("debug", "release").forEach { buildTypeName ->
                val appSigning =
                    appAndroid.buildTypes.getByName(buildTypeName).signingConfig
                val compatSigning =
                    android.buildTypes.getByName(buildTypeName).signingConfig
                check((appSigning == null) == (compatSigning == null)) {
                    "$buildTypeName app/compat signing availability differs."
                }
                if (appSigning != null && compatSigning != null) {
                    check(
                        appSigning.storeFile?.canonicalFile ==
                            compatSigning.storeFile?.canonicalFile,
                    ) {
                        "$buildTypeName app/compat keystore differs."
                    }
                    check(appSigning.keyAlias == compatSigning.keyAlias) {
                        "$buildTypeName app/compat signing alias differs."
                    }
                }

                fun singleApk(
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

                val appApk = singleApk(project(":app"), buildTypeName)
                val compatApk = singleApk(project, buildTypeName)
                val appDigest = signerCertificateSha256(appApk)
                val compatDigest = signerCertificateSha256(compatApk)
                check(appDigest == compatDigest) {
                    "$buildTypeName APK signer certificate differs: " +
                        "app=$appDigest, compat=$compatDigest."
                }
            }
        }
    }

tasks.register("verifyR1CompatV1Fixture") {
    group = "verification"
    description =
        "Builds and lints both variants of the frozen-v1 service and framework-only old client."
    dependsOn(verifyR1CompatV1SigningConfiguration)
    dependsOn("assembleDebug", "assembleRelease", "lintDebug", "lintRelease")
}

tasks.named("check").configure {
    dependsOn("verifyR1CompatV1Fixture")
}
