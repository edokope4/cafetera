plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val mqttAssetsDir = layout.buildDirectory.dir("generated/mqttAssets")

android {
    namespace = "com.cafetera"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cafetera"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{INDEX.LIST,DEPENDENCIES,NOTICE,LICENSE,LICENSE.txt,NOTICE.txt}"
        }
    }

    sourceSets.getByName("main").assets.srcDir(mqttAssetsDir)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

tasks.register<Copy>("copyMqttConfig") {
    from(rootProject.file("config.properties"))
    into(mqttAssetsDir)
}

tasks.named("preBuild").configure {
    dependsOn("copyMqttConfig")
}
