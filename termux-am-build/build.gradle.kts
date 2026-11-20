import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
}

private val termuxAm = rootProject.file("termux-am/app/src/main")

android {
    namespace = "com.termux.termuxam"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.termux.termuxam"
        minSdk = 21
        // Kept < 28 as upstream so am can access hidden Android APIs; am.apk is never installed
        targetSdk = 27
        versionCode = 2
        versionName = "0.8.1"
        buildConfigField("String", "TERMUX_PACKAGE_NAME", "\"ai.agentra\"")
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        named("main") {
            java.srcDir(File(termuxAm, "java"))
            manifest.srcFile(File(termuxAm, "AndroidManifest.xml"))
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.7.1")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
}
