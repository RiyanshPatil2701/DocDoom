
import java.io.File
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// DOCX renderer webjar configuration
val ooxmlWebjarConfig = configurations.create("ooxmlWebjar") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    add(ooxmlWebjarConfig.name, libs.ooxml.webjar)
}

android {
    namespace = "com.example.docdoom"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.docdoom"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

// Include generated renderer assets
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(
            "build/generated/ooxml/assets"
        )
    }
}

// Package OOXML renderer webjar assets
val packageOoxmlAssets = tasks.register("packageOoxmlAssets") {
    val webjarJar = ooxmlWebjarConfig.elements.map {
        it.single().asFile
    }

    val assetsDir =
        layout.buildDirectory.dir("generated/ooxml/assets")

    inputs.files(webjarJar)
    outputs.dir(assetsDir)

    doLast {
        val jar = webjarJar.get()
        val outputRoot =
            assetsDir.get().asFile.apply { mkdirs() }

        ZipFile(jar).use { zip ->
            val entries = zip.entries().asSequence().toList()

            val marker = entries.firstOrNull { entry ->
                !entry.isDirectory &&
                        entry.name.endsWith("dist/docx.mjs")
            } ?: error(
                "${jar.name} does not contain dist/docx.mjs"
            )

            val distRoot =
                marker.name.removeSuffix("dist/docx.mjs")

            entries
                .filter { entry ->
                    !entry.isDirectory &&
                            entry.name.startsWith(distRoot)
                }
                .forEach { entry ->
                    val relative = entry.name
                        .removePrefix(distRoot)
                        .removePrefix("dist/")

                    if (
                        relative.isBlank() ||
                        relative.startsWith("types/") ||
                        relative.endsWith(".d.ts") ||
                        relative == "node.mjs"
                    ) {
                        return@forEach
                    }

                    val target = File(
                        outputRoot,
                        "ooxml/$relative"
                    )

                    target.parentFile?.mkdirs()

                    zip.getInputStream(entry).use { input ->
                        target.outputStream().buffered().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
        }
    }
}

// Ensure renderer assets are packaged before merge/lint tasks
tasks.configureEach {
    if (
        (name.startsWith("merge") &&
                name.endsWith("Assets")) ||
        name.contains("lint", ignoreCase = true)
    ) {
        dependsOn(packageOoxmlAssets)
    }
}

dependencies {
    // Android core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // AppCompat and Material
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")

    // Room database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Existing DocDoom dependencies
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.pdf:pdf-viewer-fragment:1.0.0-beta01")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    // WebView support
    implementation(libs.androidx.webkit)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // Tests
    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
