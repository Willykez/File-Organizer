import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

// Optional Gemini API key for the AI Chat "smart parsing" feature. Never commit a real key —
// it is read from a local, gitignored properties file (or CI secret) and baked into
// BuildConfig. If it's absent, the app falls back to fully-offline command parsing.
val secrets = Properties().apply {
    val secretsFile = rootProject.file("local.properties")
    if (secretsFile.exists()) {
        secretsFile.inputStream().use { load(it) }
    }
}
val geminiApiKey: String =
    (secrets.getProperty("GEMINI_API_KEY") ?: System.getenv("GEMINI_API_KEY") ?: "")

android {
    namespace = "com.willykez.files"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.willykez.files"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0"

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
