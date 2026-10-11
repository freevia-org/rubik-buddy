// rubik-solver: KMP wrapper for the min2phase Rubik's Cube solver.
// Android: uses the Java min2phase implementation.
// iOS: stub now; wired to min2phaseCXX in Task 5 (requires macOS).

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "org.freevia.rubikbuddy.solver"
        compileSdk = 36
        minSdk = 28
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
        withJava()
        withHostTest { }
    }
    // cinterop activates only after build-ios-framework.sh has been run on macOS
    val xcfDir = project.file("min2phaseXCFramework/min2phase.xcframework")
    val cinteropReady = xcfDir.exists()

    fun org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget.configureCinterop(libDir: String) {
        if (!cinteropReady) return
        compilations.getByName("main").cinterops.create("min2phase") {
            defFile(project.file("src/iosMain/cinterop/min2phase.def"))
            includeDirs(project.file("min2phaseCXX/include"))
            includeDirs(project.file("min2phase_wrapper/include"))
            extraOpts("-libraryPath", libDir)
        }
    }

    iosArm64 {
        configureCinterop("$xcfDir/ios-arm64")
    }
    iosX64 {
        configureCinterop("$xcfDir/ios-x86_64-simulator")
    }
    iosSimulatorArm64 {
        configureCinterop("$xcfDir/ios-arm64-simulator")
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":rubik-model"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.junit.jupiter)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
