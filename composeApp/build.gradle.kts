import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    // Firebase plugins are applied conditionally below (see "Optional Firebase").
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.appdistribution) apply false
}

// ── Optional Firebase ──────────────────────────────────────────────────────────
// The shipped app contains no Firebase SDK (no analytics, no crash reporting). Firebase
// App Distribution is only a developer upload tool. Contributors and CI do not need it:
// the plugins are applied only when a local, git-ignored google-services.json for this
// application id exists
// (see composeApp/google-services.json.example). Credentials for uploading come from
// the FIREBASE_CREDENTIALS_FILE environment variable or the firebaseCredentialsFile
// Gradle property, never from the repository.
val appId = "org.freevia.rubikbuddy"
val hasFirebaseConfig = file("google-services.json").let { f ->
    // Skip the plugins when the file is absent or was generated for another package name;
    // the google-services plugin would otherwise fail the build on a mismatch.
    f.exists() && f.readText().contains("\"package_name\": \"$appId\"")
}
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.appdistribution")
}
val firebaseCredentials: String? =
    providers.gradleProperty("firebaseCredentialsFile")
        .orElse(providers.environmentVariable("FIREBASE_CREDENTIALS_FILE"))
        .orNull

compose.resources {
    packageOfResClass = "org.freevia.rubikbuddy.generated.resources"
    publicResClass = true
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    val iosTargets = listOf(iosArm64(), iosX64(), iosSimulatorArm64())
    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":rubik-model"))
            implementation(project(":rubik-solver"))
            implementation(project(":rubik-vision"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(libs.lifecycle.viewmodel.kmp)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.activity.compose)
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)
        }
    }
}

android {
    namespace = "org.freevia.rubikbuddy"
    compileSdk = 36

    defaultConfig {
        applicationId = appId
        minSdk = 28
        targetSdk = 36
        // CI and Play uploads may override with VERSION_CODE; default is the commit count.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull()
            ?: runCatching {
                providers.exec {
                    commandLine("git", "rev-list", "--count", "HEAD")
                }.standardOutput.asText.get().trim().toInt()
            }.getOrDefault(1)
        versionName = "0.1.0"
    }

    // Release signing is read from the environment or Gradle properties only. Without
    // them the release build is simply left unsigned (fine for CI and local checks).
    // The upload key must never be committed.
    fun signingValue(env: String, prop: String): String? =
        System.getenv(env) ?: providers.gradleProperty(prop).orNull
    val releaseKeystore = signingValue("SIGNING_KEYSTORE", "signing.keystore")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = signingValue("SIGNING_KEYSTORE_PASSWORD", "signing.keystorePassword")
                keyAlias = signingValue("SIGNING_KEY_ALIAS", "signing.keyAlias")
                keyPassword = signingValue("SIGNING_KEY_PASSWORD", "signing.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            if (hasFirebaseConfig) {
                firebaseAppDistribution {
                    artifactType = "APK"
                    firebaseCredentials?.let { serviceCredentialsFile = it }
                }
            }
        }
        getByName("debug") {
            if (hasFirebaseConfig) {
                firebaseAppDistribution {
                    artifactType = "APK"
                    firebaseCredentials?.let { serviceCredentialsFile = it }
                    testers = System.getenv("FIREBASE_TESTERS") ?: ""
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}
