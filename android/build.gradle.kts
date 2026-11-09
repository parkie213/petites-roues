plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.spotless)
}

/**
 * Formatting, as google-java-format does for the Java modules: ktfmt in Google style, which also
 * removes unused imports. `./gradlew spotlessApply` formats; CI runs `spotlessCheck`.
 */
spotless {
  val ktfmt = libs.versions.ktfmt.get()
  kotlin {
    target("app/src/**/*.kt")
    ktfmt(ktfmt).googleStyle()
  }
  kotlinGradle {
    target("*.gradle.kts", "app/*.gradle.kts")
    ktfmt(ktfmt).googleStyle()
  }
}
