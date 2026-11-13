package app.petitesroues;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tngtech.archunit.core.domain.JavaClass;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Explores package-based module boundaries without adding Modulith to the running application. */
class ModulithArchitectureTest {
  // Fixture JARs contain production-layout classes, so ArchUnit's test-folder exclusion alone
  // cannot distinguish them from the application. No production code lives in this package.
  private static final ApplicationModules MODULES =
      ApplicationModules.of(
          Application.class, JavaClass.Predicates.resideInAPackage("app.petitesroues.support.."));

  @Test
  void reportDefaultModuleModel() throws Exception {
    var violations = MODULES.detectViolations();
    var output = Path.of("target", "modulith-trial");
    Files.createDirectories(output);
    Files.writeString(output.resolve("modules.txt"), MODULES.toString());
    Files.writeString(output.resolve("violations.txt"), violations.getMessage());
    new Documenter(MODULES).writeModulesAsPlantUml();
  }

  /** Full adoption gate; deliberately opt-in while the default package model is under review. */
  @Test
  @EnabledIfSystemProperty(named = "modulith.verify", matches = "true")
  void verifyCompleteModuleStructure() {
    MODULES.verify();
  }

  /** Guards small existing boundaries independently of the full model's adoption findings. */
  @ParameterizedTest
  @CsvSource({"documents,model", "speech,library model settings", "media,''", "settings,''"})
  void supportingModulesStayWithinTheirDependencyBudgets(String name, String allowed) {
    Set<String> budget = allowed.isEmpty() ? Set.of() : Set.of(allowed.split(" "));
    var dependencies =
        MODULES
            .getModuleByName(name)
            .orElseThrow()
            .getDirectDependencies(MODULES)
            .uniqueModules()
            .map(module -> module.getIdentifier().toString())
            .collect(Collectors.toSet());
    var unexpected =
        dependencies.stream()
            .filter(dependency -> !budget.contains(dependency))
            .collect(Collectors.toSet());
    assertEquals(Set.of(), unexpected, () -> name + " acquired unexpected module dependencies");
  }
}
