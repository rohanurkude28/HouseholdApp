plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// Screens and building blocks both phones draw, in Compose Multiplatform: the theme, the list
// pieces every long list uses, and whole screens as they move over. Data comes in as plain values
// and callbacks, so each phone keeps its own wiring (Hilt on Android, repositories on iPhone).
kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api(project(":data"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api("org.jetbrains.compose.material:material-icons-core:${libs.versions.composeMultiplatform.get()}")
            // The tab icons Android uses (Kitchen, TaskAlt…), so both phones' bars match.
            api("org.jetbrains.compose.material:material-icons-extended:${libs.versions.composeMultiplatform.get()}")
            // The bundled font, so the iPhone reads in the same type as Android.
            implementation(compose.components.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "com.houseaffairs.ui.res"
}

android {
    namespace = "com.houseaffairs.ui"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
