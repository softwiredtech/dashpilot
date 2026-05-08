import org.gradle.internal.os.OperatingSystem

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.softwiredtech.dashpilot"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.softwiredtech.dashpilot"
        minSdk = 29
        targetSdk = 36
        versionCode = 8
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
        compose = true
        buildConfig = true
    }
}

val syncDashApps by tasks.registering(Exec::class) {
    description = "Sync dash-apps into Android assets"

    val scriptPath = rootProject.projectDir.resolve("../scripts/sync-to-android.sh").absolutePath

    if (OperatingSystem.current().isWindows) {
        val repoRoot = rootProject.projectDir.resolve("..").absolutePath.replace('\\', '/')
        val repoRootWsl = if (repoRoot.length >= 2 && repoRoot[1] == ':') {
            "/mnt/${repoRoot[0].lowercaseChar()}${repoRoot.substring(2)}"
        } else {
            repoRoot
        }
        commandLine(
            "wsl",
            "--cd",
            repoRootWsl,
            "bash",
            "./scripts/sync-to-android.sh"
        )
    } else {
        commandLine("bash", scriptPath)
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(syncDashApps)
}

dependencies {
    implementation(project(":bridge"))
    implementation("app.rive:rive-android:11.1.2")
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
