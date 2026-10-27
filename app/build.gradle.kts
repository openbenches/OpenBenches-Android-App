import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.openbenches"
    compileSdk = 36

    val auth0Domain = (project.findProperty("AUTH0_DOMAIN") as String?) ?: "openbenches.eu.auth0.com"
    // Native Auth0 client id (do NOT embed a client secret in the Android app)
    val auth0ClientId = (project.findProperty("AUTH0_CLIENT_ID") as String?) ?: "ulXaB9bSF2Bar9UtM5NhFBWBUAesrorM"
    val auth0Scheme = (project.findProperty("AUTH0_SCHEME") as String?) ?: "openbenches"

    defaultConfig {
        applicationId = "org.openbenches"
        minSdk = 28
        targetSdk = 36
        versionCode = 14
        versionName = "1.19"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["auth0Domain"] = auth0Domain
        manifestPlaceholders["auth0Scheme"] = auth0Scheme
        buildConfigField("String", "AUTH0_DOMAIN", "\"$auth0Domain\"")
        buildConfigField("String", "AUTH0_CLIENT_ID", "\"$auth0ClientId\"")
        buildConfigField("String", "AUTH0_SCHEME", "\"$auth0Scheme\"")

        // OpenCV ships native libs per ABI; limit to common device + emulator arches.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            // R8 generates mapping.txt — upload it with each Play release to clear the
            // "no deobfuscation file" warning (see proguard-rules.pro header).
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Packages native debug symbols into the AAB when .so files still contain them.
            // OpenCV / ONNX Runtime AARs often ship already-stripped libs, so Play may still
            // warn; that warning is safe to ignore, or upload native-debug-symbols.zip manually
            // from app/build/outputs/native-debug-symbols/release/ (also copied by
            // copyReleaseNativeDebugSymbolsForPlay).
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
            // onnxruntime-android may appear from ppocr-sdk; keep a single copy.
            pickFirsts += setOf(
                "lib/arm64-v8a/libonnxruntime.so",
                "lib/armeabi-v7a/libonnxruntime.so",
                "lib/x86_64/libonnxruntime.so",
                "lib/arm64-v8a/libonnxruntime4j_jni.so",
                "lib/armeabi-v7a/libonnxruntime4j_jni.so",
                "lib/x86_64/libonnxruntime4j_jni.so",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)

    // osmdroid for OpenStreetMap
    implementation("org.osmdroid:osmdroid-android:6.1.16")

    // Retrofit for networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.auth0.android:auth0:2.11.0")
    // Align with Auth0’s Chrome Custom Tabs usage so connectAndInitialize / warmup APIs are available.
    implementation("androidx.browser:browser:1.8.0")

    // Sentry for error logging
    implementation("io.sentry:sentry-android:8.37.1")

    // Compose UI Test
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.1")

    // Location services for user location
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // Extended material icons for Compose (MyLocation icon)
    implementation("androidx.compose.material:material-icons-extended:1.6.1")

    // Accompanist permissions for runtime permission handling in Compose
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Coil for image loading in Compose
    implementation("io.coil-kt:coil-compose:2.4.0")
    implementation("io.coil-kt:coil-svg:2.4.0")

    // Landscapist Glide for image loading in Compose
    implementation("com.github.skydoves:landscapist-glide:2.2.6")

    // OpenCV for inscription photo preprocessing before PaddleOCR
    implementation("org.opencv:opencv:4.12.0")

    // Official PaddleOCR Android ONNX SDK (PP-OCRv5 mobile) — uses 16 KB-aligned onnxruntime-android.
    implementation(project(":ppocr-sdk"))
}

// Copy R8 mapping next to the release AAB for easy Play Console upload.
tasks.register<Copy>("copyReleaseMappingForPlay") {
    description = "Copies mapping.txt to app/release/ after bundleRelease (upload to Play Console)."
    group = "publishing"
    dependsOn("bundleRelease")
    from(layout.buildDirectory.file("outputs/mapping/release/mapping.txt"))
    into(layout.projectDirectory.dir("release"))
    rename { "mapping-${android.defaultConfig.versionName}.txt" }
    doFirst {
        val mappingFile = layout.buildDirectory.file("outputs/mapping/release/mapping.txt").get().asFile
        if (!mappingFile.exists()) {
            throw GradleException(
                "mapping.txt not found. Run ./gradlew :app:bundleRelease first " +
                    "(release must have isMinifyEnabled = true).",
            )
        }
    }
}

// Copy native debug symbols zip for optional manual Play Console upload.
tasks.register<Copy>("copyReleaseNativeDebugSymbolsForPlay") {
    description =
        "Copies native-debug-symbols.zip to app/release/ after bundleRelease (optional Play upload)."
    group = "publishing"
    dependsOn("bundleRelease")
    from(layout.buildDirectory.file("outputs/native-debug-symbols/release/native-debug-symbols.zip"))
    into(layout.projectDirectory.dir("release"))
    rename { "native-debug-symbols-${android.defaultConfig.versionName}.zip" }
    doFirst {
        val symbols = layout.buildDirectory
            .file("outputs/native-debug-symbols/release/native-debug-symbols.zip")
            .get()
            .asFile
        if (!symbols.exists()) {
            logger.warn(
                "native-debug-symbols.zip not found after bundleRelease. " +
                    "Install the NDK via SDK Manager if missing. Pre-stripped third-party " +
                    ".so files (OpenCV/ONNX) may produce an empty or absent symbols package; " +
                    "the Play Console warning can be ignored in that case.",
            )
        }
    }
    // Don't fail the build when symbols can't be extracted from prebuilt AARs.
    onlyIf {
        layout.buildDirectory
            .file("outputs/native-debug-symbols/release/native-debug-symbols.zip")
            .get()
            .asFile
            .exists()
    }
}
