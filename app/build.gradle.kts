plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android) }
android {
    namespace = "com.pleos.challenge.concierge"
    compileSdk = 35
    useLibrary("android.car")
    defaultConfig {
        applicationId = "com.pleos.challenge.concierge"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    val releaseKeyPath = System.getenv("PLEOS_KEYSTORE")
    signingConfigs {
        if (!releaseKeyPath.isNullOrBlank()) {
            create("submission") {
                storeFile = file(releaseKeyPath)
                storePassword = System.getenv("PLEOS_STORE_PASSWORD")
                keyAlias = "ecobuilder"
                keyPassword = System.getenv("PLEOS_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") { applicationIdSuffix = ".touch" }
        getByName("release") {
            isDebuggable = false
            // Keep SDK callback/reflection contracts until vendor shrinker rules are validated.
            isMinifyEnabled = false
            if (!releaseKeyPath.isNullOrBlank()) signingConfig = signingConfigs.getByName("submission")
        }
    }
    flavorDimensions += "source"
    productFlavors {
        create("vehicle") { dimension = "source"; buildConfigField("boolean", "DEMO_ONLY", "false"); resValue("string", "app_name", "Pleos Concierge") }
        create("demo") { dimension = "source"; applicationIdSuffix = ".demo"; buildConfigField("boolean", "DEMO_ONLY", "true"); resValue("string", "app_name", "Concierge DEMO") }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true }
}
dependencies {
    implementation(project(":core"))
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navigation)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.location)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    // Decode the approved WebP in JVM tests instead of Robolectric's 100x100 fallback.
    testImplementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
