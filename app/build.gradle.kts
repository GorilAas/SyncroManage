plugins {
    alias(libs.plugins.android.application)
    id ("com.google.gms.google-services")
}

android {
    namespace = "tech.turso.SyncroManage"
    compileSdk = 35

    defaultConfig {
        applicationId = "tech.turso.SyncroManage"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
    }

}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(files("src/libsql_android-release.aar"))
    implementation (libs.firebase.auth)
    implementation (libs.play.services.auth)
    implementation (libs.googleid)
    implementation(libs.firebase.database)
    implementation(libs.okhttp)
    implementation(libs.ui.text.android)
    implementation(libs.compose.foundation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.firebase.ui.auth)
    implementation (libs.kernel.android)
    implementation (libs.layout.android)
}