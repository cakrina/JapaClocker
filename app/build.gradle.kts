import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import org.gradle.kotlin.dsl.support.uppercaseFirstChar


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cak.japaclocker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cak.japaclocker"
        minSdk = 23
        //noinspection OldTargetApi
        targetSdk = 34

        versionCode = 7
        versionName = "1.2"


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
        applicationVariants.configureEach {
            // rename the output APK file
            outputs.configureEach {
                (this as? ApkVariantOutputImpl)?.outputFileName =
                    "${namespace}_${versionName}-${versionCode}_${buildType.name}.apk"
            }

            // rename the output AAB file
            tasks.named(
                "sign${flavorName.uppercaseFirstChar()}${buildType.name.uppercaseFirstChar()}Bundle",
                com.android.build.gradle.internal.tasks.FinalizeBundleTask::class.java
            ) {
                val file = finalBundleFile.asFile.get()
                val finalFile =
                    File(
                        file.parentFile,
                        "${namespace}_$versionName-{$versionCode}_${buildType.name}.aab"
                    )
                finalBundleFile.set(finalFile)
            }
        }
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
