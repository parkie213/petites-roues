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
    mavenLocal { content { includeGroup("com.petitesroues") } }
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
  providers
    .exec {
      workingDir = settingsDir.resolve("../java")
      commandLine("./mvnw", "-q", "-B", "-pl", "documents,library,sync", "-am", "install", "-DskipTests")
    }
    .result
    .get()
}

rootProject.name = "petites-roues-android"

include(":app")
