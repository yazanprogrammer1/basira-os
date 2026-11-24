plugins {
    alias(libs.plugins.basira.android.application)
    alias(libs.plugins.basira.android.compose)
    alias(libs.plugins.basira.hilt)
}

android {
    namespace = "com.basira.os"

    defaultConfig {
        applicationId = "com.basira.os"
        versionCode = 1
        versionName = "0.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.render)
    implementation(projects.core.tracking)
    implementation(projects.core.windows)
    implementation(projects.core.hands)
    implementation(projects.core.voice)
    implementation(projects.core.agent)
    implementation(projects.core.spaces)
    implementation(projects.core.thermal)
    implementation(projects.ui.system)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
