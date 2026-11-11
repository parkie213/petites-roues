pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // The shared Java library (the document model), installed from ../java by Maven. Only this
    // project's group is read from ~/.m2, so nothing else there can leak into the build.
    mavenLocal { content { includeGroup("app.petitesroues") } }
  }
}

/*
 * Installs the shared Java library from ../java into ~/.m2 whenever Gradle configures the build
 * (every build and every Android Studio sync), so the app never compiles against a stale jar.
 * It runs here because dependencies are resolved while the configuration cache is stored, before
 * any task could install it; Gradle re-runs this command on each build to check the cache. Maven
 * skips work that is up to date. CI installs the library once in an earlier job and passes
 * -PprebuiltLibrary to skip this.
 */
if (!providers.gradleProperty("prebuiltLibrary").isPresent) {
  val maven = providers.exec {
    workingDir = settingsDir.resolve("../java")
    commandLine(
      "./mvnw",
      "-q",
      "-B",
      "-pl",
      "content-model,document-json,content-library,sync",
      "-am",
      "install",
      "-DskipTests",
    )
    // Maven compiles with the JDK Gradle runs on. Android Studio starts Gradle on its own JDK
    // without setting JAVA_HOME, and the system's `java` may be a runtime with no compiler.
    environment("JAVA_HOME", System.getProperty("java.home"))
    isIgnoreExitValue = true
  }
  // Gradle would only say the exit value; Maven's own output says what went wrong.
  if (maven.result.get().exitValue != 0) {
    val output =
      (maven.standardOutput.asText.get() + maven.standardError.asText.get())
        .lines()
        .filterNot { it.startsWith("WARNING:") || it.isBlank() }
        .takeLast(40)
        .joinToString("\n")
    throw GradleException("Installing the shared Java library from ../java failed:\n$output")
  }
}

rootProject.name = "petites-roues-android"

include(":app")
