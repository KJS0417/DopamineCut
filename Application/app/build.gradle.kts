import groovy.json.JsonSlurper
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val firebaseAndroidPackage = "com.example.dopaminecut2"
val legacyGoogleServices = file("google-services.json")
val debugGoogleServices = file("src/debug/google-services.json")
val releaseGoogleServices = file("src/release/google-services.json")

if (legacyGoogleServices.exists()) {
    throw GradleException(
        "app/google-services.json은 빌드 종류를 구분하지 않으므로 사용할 수 없습니다. " +
            "개발 설정은 app/src/debug/, 운영 설정은 app/src/release/에 두세요."
    )
}

// Firebase 설정 없이도 로컬 단위 테스트는 가능하지만 Release 빌드는 아래 검증이 차단한다.
val hasGoogleServices = debugGoogleServices.isFile || releaseGoogleServices.isFile
if (hasGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn(
        "google-services.json이 없어 Firebase 런타임 기능이 비활성화됩니다. " +
            "FIREBASE_SETUP.md를 참고하세요."
    )
}

fun googleServicesProjectId(configurationFile: File, environment: String): String {
    val root = JsonSlurper().parse(configurationFile) as? Map<*, *>
        ?: throw GradleException("$environment google-services.json의 JSON 형식이 올바르지 않습니다.")
    val projectInfo = root["project_info"] as? Map<*, *>
    val projectId = projectInfo?.get("project_id") as? String
    if (projectId.isNullOrBlank()) {
        throw GradleException("$environment google-services.json에 project_info.project_id가 없습니다.")
    }
    val clients = root["client"] as? List<*> ?: emptyList<Any>()
    val hasExpectedPackage = clients.any { client ->
        val clientMap = client as? Map<*, *> ?: return@any false
        val clientInfo = clientMap["client_info"] as? Map<*, *> ?: return@any false
        val androidInfo = clientInfo["android_client_info"] as? Map<*, *> ?: return@any false
        androidInfo["package_name"] == firebaseAndroidPackage
    }
    if (!hasExpectedPackage) {
        throw GradleException(
            "$environment google-services.json에 Android 패키지 $firebaseAndroidPackage 등록이 없습니다."
        )
    }
    return projectId
}

val verifyFirebaseEnvironments = tasks.register("verifyFirebaseEnvironments") {
    group = "verification"
    description = "개발/운영 Firebase 설정의 존재, 패키지, 프로젝트 분리를 검증합니다."
    doLast {
        if (!debugGoogleServices.isFile) {
            throw GradleException("개발 Firebase 설정 app/src/debug/google-services.json이 없습니다.")
        }
        if (!releaseGoogleServices.isFile) {
            throw GradleException("운영 Firebase 설정 app/src/release/google-services.json이 없습니다.")
        }
        val debugProjectId = googleServicesProjectId(debugGoogleServices, "Debug")
        val releaseProjectId = googleServicesProjectId(releaseGoogleServices, "Release")
        if (debugProjectId == releaseProjectId) {
            throw GradleException(
                "Debug와 Release Firebase project_id가 '$debugProjectId'로 같습니다. " +
                    "서로 다른 Firebase 프로젝트를 사용해야 합니다."
            )
        }
        logger.lifecycle("Firebase environment separation verified: debug != release")
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyFirebaseEnvironments)
}

android {
    namespace = "com.example.dopaminecut2"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.dopaminecut2"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "FIREBASE_ENVIRONMENT", "\"development\"")
        }
        release {
            buildConfigField("String", "FIREBASE_ENVIRONMENT", "\"production\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
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
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation(platform("com.google.firebase:firebase-bom:34.10.0"))
    implementation("com.google.firebase:firebase-auth")      // 로그인용
    implementation("com.google.firebase:firebase-firestore") // DB용
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.7.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.9")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.9")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0") // 차트용
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1") // OCR
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
}
