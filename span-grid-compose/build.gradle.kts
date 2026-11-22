plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "wraphex.spangrid.compose"

    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // Placement rules live here; the POM must expose them, since GridSpan/GridItem are API types.
    api(project(":span-grid-core"))

    // Compose types appear in this module's public signatures (Modifier, Dp, Rect, Offset,
    // Density, Color, ImageVector, BoxScope), so those artifacts are api, not implementation.
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)

    implementation(libs.compose.material3)

    testImplementation(libs.junit)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
            }
        }
    }
}
