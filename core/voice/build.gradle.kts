import java.util.Properties

plugins {
    alias(libs.plugins.basira.android.library)
    alias(libs.plugins.basira.hilt)
}

// API keys come from the git-ignored local.properties, never from source.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "STT_API_KEY", "\"${localProperties.getProperty("STT_API_KEY", "")}\"")
    }
}

dependencies {
    api(projects.core.common)
}
