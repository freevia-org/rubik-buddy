// rubik-vision: KMP module for camera-based colour detection.
// Android: CubeFrameAnalyzer (CameraX), ColorSession, LabConverter.
// iOS: PixelFrameAnalyzer processes AVFoundation pixels; CameraX stays Android-only.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "org.freevia.rubikbuddy.vision"
        compileSdk = 36
        minSdk = 28
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
        withHostTest {
            isReturnDefaultValues = true
        }
    }
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":rubik-model"))
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
        }
        val androidHostTest by getting {
            kotlin.srcDir("src/androidUnitTest/kotlin")
            dependencies {
                implementation(libs.junit.jupiter)
                implementation(libs.truth)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
