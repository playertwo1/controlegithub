import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val githubOAuthClientId = providers.gradleProperty("GITHUB_OAUTH_CLIENT_ID").orElse("").get()
val localSigningPropertiesFile = rootProject.file(".secrets/signing.properties")
val localSigningProperties = Properties().apply {
    if (localSigningPropertiesFile.isFile) {
        localSigningPropertiesFile.inputStream().use { inputStream -> load(inputStream) }
    }
}
fun signingValue(name: String): String? = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name))
    .orNull
    ?: localSigningProperties.getProperty(name)

val releaseStoreFilePath = signingValue("RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() }
val releaseStoreFile = releaseStoreFilePath?.let { rootProject.file(it) }
val releaseStorePassword = signingValue("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("RELEASE_KEY_PASSWORD")
val hasProductionReleaseSigning = releaseStoreFile?.exists() == true &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

gradle.taskGraph.whenReady {
    if (allTasks.any { it.name.contains("Release", ignoreCase = true) } && !hasProductionReleaseSigning) {
        throw org.gradle.api.GradleException(
            "Release exige RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS e RELEASE_KEY_PASSWORD válidos."
        )
    }
}

android {
    namespace = "com.playertwo.controlegithub"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.playertwo.controlegithub"
        minSdk = 26
        targetSdk = 36
        versionCode = 103
        versionName = "2.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GITHUB_OAUTH_CLIENT_ID", "\"$githubOAuthClientId\"")
    }
    signingConfigs {
        create("release") {
            if (hasProductionReleaseSigning) {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            } else {
                val debugConfig = getByName("debug")
                storeFile = debugConfig.storeFile
                storePassword = debugConfig.storePassword
                keyAlias = debugConfig.keyAlias
                keyPassword = debugConfig.keyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.commonmark:commonmark:0.30.1")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.30.1")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // org.json do android.jar é stub na JVM; testes de parsing usam a implementação real
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test> {
    jvmArgs("--add-opens=java.base/java.net=ALL-UNNAMED")
}
