import java.util.Properties

/**
 * Where published datasets live: a branch of this repository, served raw.
 *
 * A branch rather than the default one, so publishing cannot trigger the workflows that
 * watch master, and so a bad dataset is reverted without touching the source.
 */
val DEFAULT_DATASET_URL =
    "https://raw.githubusercontent.com/bdbais/ow-companion/dataset-published"

/**
 * What building this app cost, in tokens, as measured by tools/count_tokens.py.
 *
 * Absent or unreadable, it reports zero and the About screen says nothing, which is the
 * right behaviour for a checkout on a machine that has no transcripts to count.
 */
val development = Properties().apply {
    val file = rootProject.file("dataset/development.properties")
    if (file.exists()) file.inputStream().use { stream -> load(stream) }
}

fun developmentLong(key: String): Long = development.getProperty(key)?.toLongOrNull() ?: 0L

/**
 * How many commits have changed the data the app ships with.
 *
 * It increments exactly when the dataset does, which is what the update check needs: an APK
 * built from a given commit reports the version it actually carries, so it never downloads
 * a copy of what is already inside it. A checkout without git history falls back to 1, which
 * simply means the first published dataset looks newer.
 */
fun datasetVersion(): Int = try {
    val process = ProcessBuilder(
        "git", "rev-list", "--count", "HEAD", "--",
        "app/src/main/assets/weapons.json", "app/src/main/assets/wiki.json",
    ).directory(rootDir).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
    process.waitFor()
    output.toIntOrNull()?.takeIf { it > 0 } ?: 1
} catch (error: Exception) {
    logger.warn("Could not read the dataset version from git (${error.message}); using 1.")
    1
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.bellizia.owcompanion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bellizia.owcompanion"
        minSdk = 26
        targetSdk = 35
        // Kept in step with the git tag the release is published under: the in-app
        // update prompt compares this against the newest tag on GitHub.
        versionCode = 45
        versionName = "1.10.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Where the app looks for a newer dataset. Overridable in gradle.properties, and
        // blank switches updates off: the app is offline-first, so an absent server is a
        // non-event rather than an error.
        buildConfigField(
            "String",
            "DATASET_UPDATE_URL",
            "\"${project.findProperty("datasetUpdateUrl") ?: DEFAULT_DATASET_URL}\"",
        )

        buildConfigField("int", "DATASET_VERSION", "${datasetVersion()}")

        buildConfigField("long", "DEV_TOKENS", "${developmentLong("tokens")}L")
        buildConfigField("long", "DEV_TOKENS_OUTPUT", "${developmentLong("tokensOutput")}L")
        buildConfigField("long", "DEV_TOKENS_CACHE_READ", "${developmentLong("tokensCacheRead")}L")
        buildConfigField(
            "String",
            "DEV_MEASURED",
            "\"${development.getProperty("measured") ?: ""}\"",
        )
    }

    // Release signing is read from keystore.properties when present, so the keystore and
    // its passwords never enter the repository. Without it, a release build stays unsigned.
    val keystoreProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { stream -> load(stream) }
    }

    // A file still holding the placeholders is treated as no file at all. Left alone, the
    // build gets as far as packaging and then fails with "keystore password was incorrect",
    // which reads like a broken keystore rather than a line nobody has filled in yet.
    val placeholders = keystoreProperties.stringPropertyNames().any { name ->
        keystoreProperties.getProperty(name).orEmpty().startsWith("REPLACE_WITH_")
    }
    if (placeholders) {
        logger.warn(
            "keystore.properties still has its placeholder passwords, so this release will " +
                "be unsigned and will not install. Fill in storePassword and keyPassword.",
        )
        keystoreProperties.clear()
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    // Google's own rating card. Inert unless the app was installed from Play.
    implementation(libs.play.review)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
