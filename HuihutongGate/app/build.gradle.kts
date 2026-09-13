plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.oooo00o0o.huihutong"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.oooo00o0o.huihutong"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
