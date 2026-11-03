plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

// AGP 9 provides Kotlin itself. AGP 8 (GBSat) still needs the Kotlin Android plugin.
if (com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION.substringBefore('.').toInt() < 9) {
    apply(plugin = "org.jetbrains.kotlin.android")
}

android {
    namespace = "gb.frame"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation("androidx.activity:activity-compose:1.9.3") // BackHandler for shared quick-menu navigation
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7") // Refresh purchase ownership on return to the app
    api(composeBom)
    api("androidx.compose.ui:ui")
    api("androidx.compose.material3:material3")
    api("androidx.compose.material:material-icons-extended")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.apache.commons:commons-compress:1.28.0") // ZIP and 7z texture packages
    implementation("org.tukaani:xz:1.10") // LZMA/LZMA2 filters used by 7z packages
    implementation("com.github.junrar:junrar:8.1.2") // RAR4/RAR5 texture packages
    implementation("com.google.android.play:app-update:2.1.0") // Shared update check (Google Play In-App Updates channel)
    implementation("com.android.billingclient:billing:9.1.0") // Shared one-time visual customization purchase
    constraints {
        // Play Services still requests Fragment 1.1.0 transitively; keep apps on a maintained AndroidX line.
        implementation("androidx.fragment:fragment:1.9.1") {
            because("Avoid shipping the obsolete Fragment 1.1.0 requested by Play Services")
        }
    }
    testImplementation("junit:junit:4.13.2")
}
