plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
  alias(libs.plugins.room)
}

android {
  namespace = "com.petitesroues.android"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.petitesroues.android"
    // Android 14: records stay native, which Jackson needs to read documents.
    minSdk = 34
    targetSdk = 37
    versionCode = 1
    versionName = "0.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures { compose = true }

  lint {
    // Every warning fails the build, as errors do, so the report stays empty.
    warningsAsErrors = true
    abortOnError = true
    // New releases of Gradle, the plugin and libraries are not faults in the code; updating them
    // is a separate job, and these checks would fail builds whenever something is released.
    disable += setOf("NewerVersionAvailable", "AndroidGradlePluginVersion", "GradleDependency")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging { resources { excludes += setOf("META-INF/{AL2.0,LGPL2.1,LICENSE*,NOTICE*}") } }
}

/**
 * Bundles the subtitle quiz workflow and quiz types from `agents/` as app assets, so the phone's
 * "Quiz me" share always carries the current versions.
 */
abstract class QuizAssets : DefaultTask() {
  @get:InputFiles abstract val sources: ConfigurableFileCollection
  @get:OutputDirectory abstract val output: DirectoryProperty

  @TaskAction
  fun copy() {
    val folder = output.get().asFile.resolve("quiz")
    folder.deleteRecursively()
    folder.mkdirs()
    sources.forEach { it.copyTo(folder.resolve(it.name)) }
  }
}

val quizAssets =
  tasks.register<QuizAssets>("quizAssets") {
    val agents = rootDir.resolve("../agents")
    sources.from(
      agents.resolve("workflows/subtitle-quiz.md"),
      agents.resolve("workflows/quiz-types.md"),
    )
  }

// Room writes each version of the progress database's schema here, to review and migrate from.
room { schemaDirectory("$projectDir/schemas") }

// The compiler reports no warnings; keep it that way.
kotlin { compilerOptions { allWarningsAsErrors = true } }

androidComponents {
  onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(quizAssets, QuizAssets::output)
  }
}

dependencies {
  implementation(libs.petites.roues.documents)
  implementation(libs.petites.roues.library)
  implementation(libs.petites.roues.sync)
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.material3)
  implementation(libs.compose.icons)
  implementation(libs.activity.compose)
  implementation(libs.lifecycle.runtime.compose)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui.compose)
  implementation(libs.commons.compress)
  implementation(libs.markdown.renderer.m3)
  implementation(libs.play.services.code.scanner)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.room.runtime)
  ksp(libs.room.compiler)
  testImplementation(libs.junit)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.room.testing)
  constraints {
    // Test classpaths are held to the app's versions; Room's schema reader (room-migration)
    // needs a newer serialization runtime than the app's other libraries pull in.
    implementation(libs.kotlinx.serialization.core) {
      because("androidx.room:room-migration needs 1.8.1")
    }
  }
}
