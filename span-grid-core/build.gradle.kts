import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    testImplementation(libs.junit)
}

// Artifact id is the directory name (`span-grid-core`), which is what JitPack expects:
// com.github.wraphex.span-grid:span-grid-core:<tag>
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
