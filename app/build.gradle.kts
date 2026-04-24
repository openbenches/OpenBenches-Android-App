import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.openbenches"
    compileSdk = 35

    val auth0Domain = (project.findProperty("AUTH0_DOMAIN") as String?) ?: "openbenches.eu.auth0.com"
    // Native Auth0 client id (do NOT embed a client secret in the Android app)
    val auth0ClientId = (project.findProperty("AUTH0_CLIENT_ID") as String?) ?: "ulXaB9bSF2Bar9UtM5NhFBWBUAesrorM"
    val auth0Scheme = (project.findProperty("AUTH0_SCHEME") as String?) ?: "openbenches"

    defaultConfig {
        applicationId = "org.openbenches"
        minSdk = 28
        targetSdk = 35
        versionCode = 4
        versionName = "1.10"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["auth0Domain"] = auth0Domain
        manifestPlaceholders["auth0Scheme"] = auth0Scheme
        buildConfigField("String", "AUTH0_DOMAIN", "\"$auth0Domain\"")
        buildConfigField("String", "AUTH0_CLIENT_ID", "\"$auth0ClientId\"")
        buildConfigField("String", "AUTH0_SCHEME", "\"$auth0Scheme\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    // Landscapist Glide for image loading in Compose
    implementation("com.github.skydoves:landscapist-glide:2.2.6")

    // On-device OCR for auto-filling inscription from photos
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
