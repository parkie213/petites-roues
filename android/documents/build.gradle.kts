// The desktop document model, compiled from its Maven module's sources so both apps read the
// same format without a publishing step. Maven pins those sources to Java 17 for Android.
plugins { `java-library` }

sourceSets { main { java.setSrcDirs(listOf("../../java/documents/src/main/java")) } }

tasks.withType<JavaCompile>().configureEach { options.release = 17 }

dependencies { api(libs.jackson.databind) }
