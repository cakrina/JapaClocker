import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cak.japaclocker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cak.japaclocker"
        minSdk = 24
        targetSdk = 34

        // Version code and version name are defined here
        val versionPropsFile = file("version.properties")
        val versionProps = Properties()

        if (versionPropsFile.exists()) {
            versionProps.load(versionPropsFile.inputStream())
        }

        val currentVersionCode = versionProps["VERSION_CODE"]?.toString()?.toInt() ?: 1
        val newVersionCode = currentVersionCode + 1

        versionProps["VERSION_CODE"] = newVersionCode.toString()
        versionProps.store(versionPropsFile.outputStream(), null)

        versionCode = newVersionCode
        versionName = versionProps["VERSION_NAME"]?.toString() ?: "1.0"

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
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
