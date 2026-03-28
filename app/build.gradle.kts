import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.appdistribution)
}

android {
    namespace = "com.xmelon.rubik_solver.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xmelon.rubik_solver"
        minSdk = 28
        targetSdk = 36
        versionCode = providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
        }.standardOutput.asText.get().trim().toInt()
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            firebaseAppDistribution {
                artifactType = "APK"
                serviceCredentialsFile = "app/firebase-service-account.json"
            }
        }
        getByName("debug") {
            firebaseAppDistribution {
                artifactType = "APK"
                serviceCredentialsFile = "app/firebase-service-account.json"
                testers = System.getenv("FIREBASE_TESTERS") ?: ""
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(project(":rubik-model"))
    implementation(libs.lifecycle.viewmodel.kmp)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    // Compose BOM for consistent versions
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
