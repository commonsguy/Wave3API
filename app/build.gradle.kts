import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.serialization)
}

val vendorPropertiesFile = rootProject.file("vendor.properties")
val vendorProperties = Properties()

vendorProperties.load(vendorPropertiesFile.inputStream())

fun String.quoted() = "\"$this\""

android {
    namespace = "com.commonsware.wave3api"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.commonsware.wave3api"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "ECOFLOW_ACCESS_KEY",
            vendorProperties.getProperty("ECOFLOW_ACCESS_KEY").quoted(),
        )

        buildConfigField(
            "String",
            "ECOFLOW_SECRET_KEY",
            vendorProperties.getProperty("ECOFLOW_SECRET_KEY").quoted(),
        )

        buildConfigField(
            "String",
            "ECOFLOW_ONE_SERIAL",
            vendorProperties.getProperty("ECOFLOW_ONE_SERIAL").quoted(),
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.okhttp.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.serialization.json)
}
