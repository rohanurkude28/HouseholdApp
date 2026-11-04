import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.houseaffairs.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.houseaffairs.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "false")
        // Optional free TMDB key (themoviedb.org) for movie ratings and "where to watch in India".
        // Put tmdb.apiKey=… in local.properties; without it, shows still get ratings from TVMaze.
        val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load) }
        buildConfigField("String", "TMDB_API_KEY", "\"${local.getProperty("tmdb.apiKey", "")}\"")
        // Optional free OMDb key (omdbapi.com) for IMDb and Rotten Tomatoes scores: omdb.apiKey=… in local.properties.
        buildConfigField("String", "OMDB_API_KEY", "\"${local.getProperty("omdb.apiKey", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Debug build wired to the Firebase Local Emulator Suite, with test accounts instead of real
        // Google sign-in. No Firebase project needed: ./gradlew installLocal
        create("local") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "true")
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
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.ai)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}

// The rules moved to :shared keep running with the app's own tests.
tasks.matching { it.name == "testLocalUnitTest" || it.name == "testDebugUnitTest" }.configureEach {
    dependsOn(":shared:testDebugUnitTest")
}
