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

private fun playVersionCode(versionName: String, buildNumber: Int): Int {
    val parts = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(versionName)
        ?.groupValues?.drop(1)?.map { it.toLong() }
        ?: throw GradleException("Release version must be MAJOR.MINOR.CODE: $versionName")
    val (major, minor, code) = parts
    require(major in 0L..20L && minor in 0L..99L && code in 0L..999L && buildNumber in 1..999) {
        "Release version components exceed the supported Play versionCode range"
    }
    val value = major * 100_000_000L + minor * 1_000_000L + code * 1_000L + buildNumber
    require(value <= 2_100_000_000L) { "Derived Play versionCode exceeds Google's limit" }
    return value.toInt()
}

private data class ReleaseIdentity(val canonical: String, val marketingVersion: String, val build: Int, val playVersionCode: Int)

private fun releaseIdentity(): ReleaseIdentity {
    val manifest = rootProject.file("release-version.json")
    require(manifest.isFile) { "Missing canonical release-version.json" }
    val canonical = Regex("\"canonical\"\\s*:\\s*\"([^\"]+)\"")
        .find(manifest.readText())?.groupValues?.get(1)
        ?: throw GradleException("release-version.json must contain canonical MAJOR.MINOR.CODE+BUILD")
    val parts = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)\\+([1-9]\\d{0,2})$").matchEntire(canonical)
        ?.groupValues?.drop(1)?.map { it.toLong() }
        ?: throw GradleException("Release version must be MAJOR.MINOR.CODE+BUILD: $canonical")
    val (major, minor, code, build) = parts
    require(major in 0L..20L && minor in 0L..99L && code in 0L..999L && build in 1L..999L) {
        "Release version components exceed the supported Play versionCode range"
    }
    val marketingVersion = "$major.$minor.$code"
    val playCode = playVersionCode(marketingVersion, build.toInt())
    return ReleaseIdentity(canonical, marketingVersion, build.toInt(), playCode)
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
        // One canonical manifest feeds every store. Legacy CI variables are
        // accepted only as consistency assertions; they cannot allocate versions.
        val release = releaseIdentity()
        val releaseName = System.getenv("VERSION_NAME")?.also { supplied ->
            require(supplied == release.marketingVersion) {
                "VERSION_NAME is derived from release-version.json; expected ${release.marketingVersion}"
            }
        } ?: release.marketingVersion
        System.getenv("BUILD_NUMBER")?.let { raw ->
            val supplied = raw.toIntOrNull() ?: throw GradleException("BUILD_NUMBER must match release-version.json")
            require(supplied == release.build) { "BUILD_NUMBER is derived from release-version.json; expected ${release.build}" }
        }
        versionCode = release.playVersionCode.also { derived ->
            System.getenv("VERSION_CODE")?.toIntOrNull()?.let { supplied ->
                require(supplied == derived) {
                    "VERSION_CODE is derived from release-version.json; expected $derived"
                }
            }
        }
        versionName = releaseName
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
