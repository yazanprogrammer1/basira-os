plugins {
    alias(libs.plugins.basira.android.library)
    alias(libs.plugins.basira.hilt)
}

dependencies {
    api(projects.core.common)
}
