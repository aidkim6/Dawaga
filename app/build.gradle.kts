import java.io.FileInputStream
import java.util.Properties

// 1. local.properties 파일 읽기 로직
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.dawaga"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.dawaga"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 카카오 앱 키 설정
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = localProperties.getProperty("KAKAO_NATIVE_APP_KEY") ?: ""
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"${localProperties.getProperty("KAKAO_NATIVE_APP_KEY") ?: ""}\"")
    }

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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


    // 카카오맵 Vector SDK v2
    implementation("com.kakao.maps.open:android:2.12.8")

    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // 위치 수집 (FusedLocation)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    //  STOMP 웹소켓 라이브러리 추가
    implementation("com.github.NaikSoftware:StompProtocolAndroid:1.6.6")

    // RxJava & RxAndroid
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")

    implementation("com.google.firebase:firebase-firestore:25.1.1")

    //멤버별 도착 현황을 목록으로 표시할 RecyclerView 라이브러리 추가
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}