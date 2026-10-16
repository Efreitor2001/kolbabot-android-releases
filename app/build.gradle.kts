import java.util.Properties
import java.io.File
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

abstract class GenerateHuaweiAssets : DefaultTask() {
    @get:Input abstract val publicConfiguration: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun generate() {
        val directory = outputDirectory.get().asFile
        directory.mkdirs()
        val destination = directory.resolve("kolbabot-huawei.json")
        val configuration = publicConfiguration.get()
        if (configuration != "{}") destination.writeText(configuration, Charsets.UTF_8)
        else destination.delete()
    }
}

val serverUrl = providers.gradleProperty("KOLBABOT_BASE_URL").orElse("https://kolbabot.ru").get().trimEnd('/')
require(serverUrl.matches(Regex("https://[a-zA-Z0-9.-]+(:[0-9]+)?"))) { "KOLBABOT_BASE_URL must be an HTTPS origin" }
val hasFirebaseConfiguration = file("google-services.json").isFile
val huaweiConfigurationFile = file("agconnect-services.json")
val huaweiConfiguration = if (huaweiConfigurationFile.isFile) {
    runCatching { JsonSlurper().parse(huaweiConfigurationFile) as? Map<*, *> }.getOrNull()
} else null
val huaweiClient = huaweiConfiguration?.get("client") as? Map<*, *>
val huaweiAppId = huaweiClient?.get("app_id")?.toString().orEmpty()
val hasHuaweiConfiguration = huaweiAppId.matches(Regex("[0-9]{5,64}")) &&
    huaweiClient?.get("package_name") == "ru.kolbabot.app"
// AGConnect exports can include an OAuth client secret. Package only the public
// values required by Push Kit, never the original configuration file.
val publicHuaweiConfiguration = linkedMapOf<String, Any>()
if (hasHuaweiConfiguration) {
    listOf("configuration_version", "region").forEach { key ->
        huaweiConfiguration?.get(key)?.takeIf { it is String || it is Number }?.let { publicHuaweiConfiguration[key] = it }
    }
    (huaweiConfiguration?.get("agcgw") as? Map<*, *>)?.let { gateway ->
        publicHuaweiConfiguration["agcgw"] = gateway.filter { (key, value) ->
            key in setOf("url", "backurl") && value is String
        }
    }
    publicHuaweiConfiguration["client"] = listOf("app_id", "package_name", "cp_id", "project_id", "api_key", "product_id", "client_id")
        .mapNotNull { key -> huaweiClient?.get(key)?.takeIf { it is String || it is Number }?.let { key to it } }
        .toMap()
} else if (huaweiConfigurationFile.isFile) {
    logger.warn("Huawei client configuration has no valid app_id for ru.kolbabot.app; HMS push is disabled.")
}
val generatedHuaweiAssets = layout.buildDirectory.dir("generated/kolbabotHuaweiAssets")
val generateHuaweiAssets = tasks.register<GenerateHuaweiAssets>("generateHuaweiAssets") {
    publicConfiguration.set(JsonOutput.toJson(publicHuaweiConfiguration))
    outputDirectory.set(generatedHuaweiAssets)
}
if (hasFirebaseConfiguration) apply(plugin = "com.google.gms.google-services")

// Release keys belong to this application and stay outside the source checkout.
// A missing/incomplete configuration never falls back to the debug signing key.
val defaultSigningPath = providers.environmentVariable("LOCALAPPDATA").orNull
    ?.let { "$it/KolbaBot/signing/signing.properties" }
    ?: "${System.getProperty("user.home")}/.config/KolbaBot/signing/signing.properties"
val signingPropertiesFile = file(providers.environmentVariable("KOLBABOT_SIGNING_PROPERTIES").orElse(defaultSigningPath).get()).canonicalFile
val checkoutRoot = rootProject.projectDir.parentFile.takeIf { it.resolve(".git").exists() } ?: rootProject.projectDir
require(!signingPropertiesFile.toPath().startsWith(checkoutRoot.canonicalFile.toPath())) {
    "Release signing.properties must be outside the source checkout. Set KOLBABOT_SIGNING_PROPERTIES."
}
val releaseSigning = Properties().apply {
    if (signingPropertiesFile.isFile) signingPropertiesFile.inputStream().use { load(it) }
}
val releaseKeystorePath = releaseSigning.getProperty("storeFile").orEmpty().trim()
val releaseKeystore = if (releaseKeystorePath.isNotEmpty()) File(releaseKeystorePath).canonicalFile
    else signingPropertiesFile.parentFile.resolve("RELEASE_KEYSTORE_NOT_CONFIGURED")
if (releaseKeystorePath.isNotEmpty()) {
    require(File(releaseKeystorePath).isAbsolute && !releaseKeystore.toPath().startsWith(checkoutRoot.canonicalFile.toPath())) {
        "Release storeFile must be an absolute path outside the source checkout."
    }
}
if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
    require(signingPropertiesFile.isFile && releaseKeystore.isFile &&
        listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { !releaseSigning.getProperty(it).isNullOrBlank() }) {
        "Release signing is not configured. Provide external signing.properties with storeFile, storePassword, keyAlias and keyPassword."
    }
}

android {
    namespace = "ru.kolbabot.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "ru.kolbabot.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.1"
        buildConfigField("String", "BASE_URL", "\"$serverUrl\"")
        buildConfigField("boolean", "HAS_FIREBASE_CONFIG", hasFirebaseConfiguration.toString())
        buildConfigField("boolean", "HAS_HUAWEI_CONFIG", hasHuaweiConfiguration.toString())
        manifestPlaceholders["huaweiAppId"] = if (hasHuaweiConfiguration) huaweiAppId else ""
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "store"
    productFlavors {
        listOf("google", "rustore", "huawei").forEach { channel ->
            create(channel) {
                dimension = "store"
                buildConfigField("String", "STORE_CHANNEL", "\"$channel\"")
                manifestPlaceholders["distributionChannel"] = channel
            }
        }
    }
    signingConfigs {
        create("storeRelease") {
            storeFile = releaseKeystore
            storePassword = releaseSigning.getProperty("storePassword").orEmpty()
            keyAlias = releaseSigning.getProperty("keyAlias").orEmpty()
            keyPassword = releaseSigning.getProperty("keyPassword").orEmpty()
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("storeRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    // ABI splitting is disabled; AGP 9 no longer has density APK splitting.
    // Both APK channels therefore contain every packaged ABI and density.
    splits {
        abi { isEnable = false }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(generateHuaweiAssets, GenerateHuaweiAssets::outputDirectory)
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.browser:browser:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.huawei.hms:push:6.13.0.301")
    implementation("com.huawei.agconnect:agconnect-core:1.9.6.300")
    // HMS Network Kit explicitly enables EncryptUtil's AES/SP800 random generator.
    // Keep the real crypto implementation; do not suppress its missing classes.
    implementation("org.bouncycastle:bcprov-jdk15to18:1.86")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
