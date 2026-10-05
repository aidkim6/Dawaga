import java.io.FileInputStream
import java.util.Properties

// 1. local.properties 파일 읽기 로직 추가
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.dawaga"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.dawaga"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        ndk {
            // abiFilters 설정
            abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 2. AndroidManifest.xml에서 사용할 카카오 앱 키 (${KAKAO_NATIVE_APP_KEY})
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = localProperties.getProperty("KAKAO_NATIVE_APP_KEY") ?: ""

        // 3. Kotlin 코드에서 사용할 BuildConfig 값 (BuildConfig.KAKAO_NATIVE_APP_KEY)
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"${localProperties.getProperty("KAKAO_NATIVE_APP_KEY") ?: ""}\"")
    }

    // 4. BuildConfig 클래스 자동 생성을 위한 옵션 추가
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // ★ 카카오맵 Vector SDK v2 추가 ★
    implementation("com.kakao.maps.open:android:2.12.8")

    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // ★ 위치 수집 (FusedLocation) 추가 ★
    implementation("com.google.android.gms:play-services-location:21.3.0")
}