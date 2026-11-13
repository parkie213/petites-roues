package app.petitesroues;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.progress.PlaybackPosition;
import app.petitesroues.study.DocumentEditingService;
import app.petitesroues.study.ReviewQueue;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Enforces existing responsibility boundaries without imposing a new logical module model. Imports
 * production artifact roots, which work as reactor class directories or dependency JARs in CI; test
 * classes and fixture artifacts are not part of the checked sets.
 */
class ArchitectureTest {
  private static final String[] JDBC = {"java.sql..", "javax.sql..", "org.springframework.jdbc.."};
  private static final JavaClasses APPLICATION = productionArtifacts(DocumentEditingService.class);
  private static final JavaClasses MODELS =
      productionArtifacts(StudyDocument.class, PlaybackPosition.class);

  @Test
  void servicesDelegateDatabaseAccess() {
    noClasses()
        .that()
        .haveNameMatching(".*Service(\\$.*)?")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(JDBC)
        .because("services own workflows and transactions; stores own JDBC and row mapping")
        .check(APPLICATION);
  }

  @Test
  void scannersDoNotAccessPersistence() {
    var persistence =
        JavaClass.Predicates.resideInAnyPackage(JDBC)
            .or(JavaClass.Predicates.simpleNameEndingWith("Store"))
            .or(JavaClass.Predicates.equivalentTo(ReviewQueue.class));
    noClasses()
        .that()
        .haveNameMatching(".*Scanner(\\$.*)?")
        .should()
        .dependOnClassesThat(persistence)
        .because("scanners discover content; services coordinate persistence")
        .check(APPLICATION);
  }

  @Test
  void applicationDoesNotDependOnTheHttpAdapter() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "app.petitesroues.web..",
            "app.petitesroues.config..",
            "org.springframework.web..",
            "org.springframework.http..",
            "jakarta.servlet..")
        .because(
            "desktop-application owns workflows without an HTTP or server composition dependency")
        .check(APPLICATION);
  }

  @Test
  void portableModelsStayIndependentOfFrameworksAndPersistence() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "jakarta.servlet..",
            "java.sql..",
            "javax.sql..",
            "com.fasterxml.jackson..",
            "tools.jackson..")
        .because(
            "content-model and learner-state-model are portable Java domain rules; adapters own serialization and storage")
        .check(MODELS);
  }

  private static JavaClasses productionArtifacts(Class<?>... anchors) {
    return new ClassFileImporter()
        .importUrls(
            Arrays.stream(anchors)
                .map(type -> type.getProtectionDomain().getCodeSource().getLocation())
                .distinct()
                .toList());
  }
}
