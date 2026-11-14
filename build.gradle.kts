plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// JitPack coordinates are com.github.<owner>.<repo>:<project name>, so the directory name is the
// artifact id and must not be overridden per module.
subprojects {
    group = "com.github.wraphex.span-grid"
    version = "0.1.0"
}
