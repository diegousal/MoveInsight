// build.gradle.kts (App)
import java.util.Properties

// Lee local.properties para obtener la URL del servidor de desarrollo
val localProps = Properties().also { props ->
    val file = rootProject.file("local.properties")
    if (file.exists()) props.load(file.inputStream())
}
val localBaseUrl: String = localProps.getProperty("BASE_URL", "http://10.0.2.2:8000/")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace   = "com.moveinsight"
    compileSdk  = 35

    defaultConfig {
        applicationId = "com.moveinsight"
        minSdk        = 26   // Android 8.0+  — cubre >95 % de dispositivos
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            // ── IP leída de local.properties (sin rastro en git) ──────────
            buildConfigField("String", "BASE_URL", "\"$localBaseUrl\"")
            isDebuggable = true
        }
        release {
            // ── Sustituir por la URL de producción cuando esté lista ──────
            buildConfigField("String", "BASE_URL", "\"https://api.neurosquat.com/\"")
            isMinifyEnabled    = true
            isShrinkResources  = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose     = true
        buildConfig = true   // Necesario para BuildConfig.BASE_URL
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // ── Compose BOM ───────────────────────────────────────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.1")

    // ── WorkManager + Hilt ────────────────────────────────────────────────────
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")   // complementa al hilt-compiler ya existente

    // ── CameraX ───────────────────────────────────────────────────────────────
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")

    // ── Vico Charts (MD3) ─────────────────────────────────────────────────────
    implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")
    implementation("com.patrykandpatrick.vico:compose:1.13.1")
    implementation("com.patrykandpatrick.vico:core:1.13.1")

    // ── Navigation Compose ────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ── Hilt (DI) ─────────────────────────────────────────────────────────
    implementation("com.google.dagger:hilt-android:2.51.1")
    implementation("com.google.android.material:material:1.12.0")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ── Retrofit + OkHttp ─────────────────────────────────────────────────
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ── DataStore ─────────────────────────────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── Lifecycle / ViewModel ─────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")

    // ── Coroutines ────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ── Splash Screen API ─────────────────────────────────────────────────
    implementation("androidx.core:core-splashscreen:1.0.1")

    // ── Core ──────────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")

    // ── Security (backup para EncryptedSharedPreferences si se requiere) ──
    implementation("androidx.security:security-crypto:1.0.0")

    // ── Test ──────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}