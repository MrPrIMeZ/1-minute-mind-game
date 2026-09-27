import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun prop(name: String, default: String = ""): String =
    (project.findProperty(name) as String?) ?: default

// The cloud build passes VERSION_CODE so every upload gets a higher number.
val ciVersionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "com.primez.oneminutemind"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.primez.oneminutemind"
        minSdk = 24
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = "1.0.$ciVersionCode"

        manifestPlaceholders["admobAppId"] = prop("ADMOB_APP_ID")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"${prop("ADMOB_BANNER_ID")}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${prop("ADMOB_INTERSTITIAL_ID")}\"")
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"${prop("ADMOB_REWARDED_ID")}\"")
        buildConfigField("boolean", "ADS_TESTING", prop("ADS_TESTING", "true"))
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${prop("PRIVACY_POLICY_URL")}\"")
    }

    signingConfigs {
        create("release") {
            // Filled in by the cloud build from GitHub secrets. Never put passwords in this file.
            val ks = System.getenv("KEYSTORE_FILE")
            if (!ks.isNullOrBlank()) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!System.getenv("KEYSTORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    implementation("com.google.android.gms:play-services-ads:24.3.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
}
