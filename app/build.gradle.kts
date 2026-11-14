import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.secrets)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        applicationId = "com.jarvisai.assistant"
        minSdk = 24
        targetSdk = 28

        // Auto-increment version code & name from GitHub Actions run number
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 40000 + runNumber
        versionName = if (runNumber > 0) "4.0.$runNumber" else "4.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Local environment and Go supervisor are optimized for arm64,
        // with armeabi-v7a retained for 32-bit legacy hardware.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        val isTermuxMode = project.findProperty("flavorMode")?.toString().equals("termux", ignoreCase = true)
        val isSharedUidMode = project.findProperty("sharedUid")?.toString().equals("true", ignoreCase = true)

        buildConfigField("int", "BRIDGE_PORT", if (isTermuxMode) "1234" else "1236")
        buildConfigField("int", "DEFAULT_BRIDGE_PORT", if (isTermuxMode) "1234" else "1236")
        buildConfigField("int", "HUB_PORT", if (isTermuxMode) "1235" else "1237")
        buildConfigField("int", "BROWSER_MCP_PORT", if (isTermuxMode) "8765" else "8767")
        buildConfigField("boolean", "IS_NATIVE_TERMUX_PATH", isTermuxMode.toString())
        buildConfigField("boolean", "IS_TERMUX_FLAVOR", isTermuxMode.toString())
        buildConfigField("boolean", "IS_SHARED_USER_ID", isSharedUidMode.toString())
    }

    // Do not remove or modify these signingConfigs. They are necessary for building, installing,
    // and updating Android apps in AI Studio.
    signingConfigs {
        create("release") {
            val releaseJks = file("${rootDir}/release.jks")
            val fallbackKey = file("${rootDir}/debug.keystore")
            storeFile = if (releaseJks.exists()) releaseJks else fallbackKey
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "sagar7846"
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "sagarjarvis"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "sagar7846"
        }
        create("debugConfig") {
            val releaseJks = file("${rootDir}/release.jks")
            val fallbackKey = file("${rootDir}/debug.keystore")
            storeFile = if (releaseJks.exists()) releaseJks else fallbackKey
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "sagar7846"
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "sagarjarvis"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "sagar7846"
        }
    }

    buildTypes {
        release {
            isCrunchPngs = false
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
            // AI Studio.
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
            // AI Studio.
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Critical: Do NOT let AAPT2 compress rootfs or binary archives into corrupted blobs
    androidResources {
        noCompress += listOf("gz", "tgz", "tar.gz", "tar", "xz")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "*.xsd"
            excludes += "**/*.xsd"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            // Exclude unused Unicode conversion tables to keep APK size lean
            excludes += "tables/Transcoder_*"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                val localJarDir = file("/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7")
                if (localJarDir.exists()) {
                    it.systemProperty("robolectric.offline", "true")
                    it.systemProperty("robolectric.dependency.dir", localJarDir.absolutePath)
                }
            }
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
    ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices {
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // --- Compose BOM & Base UI ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    // --- Core Architecture & Lifecycle ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.work.runtime.ktx)

    // --- Room Database ---
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // --- CameraX Vision Subsystem ---
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // --- Networking & Streaming ---
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.logging.interceptor)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.coil.compose)

    // --- Sora Code Editor Suite ---
    implementation(libs.sora.editor)
    implementation(libs.sora.language.textmate)
    implementation(libs.sora.language.java)

    // --- Termux Native PTY & Archive Subsystem ---
    implementation(libs.termux.terminal.emulator)
    implementation(libs.commons.compress)
    implementation(libs.tukaani.xz)
    implementation(libs.jsch.ssh)
    implementation(libs.jsoup.parser)

    // --- Canvas LaTeX & Math Rendering ---
    implementation(libs.markwon.ext.latex)
    implementation(libs.jlatexmath.android)

    // --- Wire Protobuf & gRPC Runtime ---
    implementation(libs.wire.runtime)
    implementation(libs.wire.grpc.client)

    // --- Testing ---
    testImplementation(libs.junit)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Multi-flavor build task aliases preserving single-variant container compatibility
listOf(
    "assembleStandardDebug" to "assembleDebug",
    "assembleTermuxDebug" to "assembleDebug",
    "assembleTermuxSharedDebug" to "assembleDebug",
    "assembleStandardRelease" to "assembleRelease",
    "assembleTermuxRelease" to "assembleRelease",
    "assembleTermuxSharedRelease" to "assembleRelease"
).forEach { (aliasName, targetTask) ->
    if (tasks.findByName(aliasName) == null) {
        tasks.register(aliasName) {
            group = "build"
            description = "Builds $aliasName APK artifact"
            dependsOn(targetTask)
        }
    }
}

