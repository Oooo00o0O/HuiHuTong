plugins {
    id("com.android.application")
}

android {
    namespace = "cn.ac.xjtlu.huihutong"
    compileSdk = 36

    defaultConfig {
        applicationId = "cn.ac.xjtlu.huihutong"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
