plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
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
  }

  buildFeatures { compose = true }

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
    sources.from(agents.resolve("workflows/subtitle-quiz.md"), agents.resolve("workflows/quiz-types.md"))
  }

androidComponents {
  onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(quizAssets, QuizAssets::output) }
}

dependencies {
  implementation(project(":documents"))
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.material3)
  implementation(libs.compose.icons)
  implementation(libs.activity.compose)
  implementation(libs.lifecycle.runtime.compose)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui.compose)
  implementation(libs.commons.compress)
  implementation(libs.markdown.renderer.m3)
  debugImplementation(libs.compose.ui.tooling)
  testImplementation(libs.junit)
}
