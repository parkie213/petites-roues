package app.petitesroues.documents;

import static app.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class StudyDocumentCodecTest {
  private final StudyDocumentCodec codec = new StudyDocumentCodec();

  @Test
  void inspectionRetainsTheValidDocument() {
    var document = document("example");
    var inspection = codec.inspect(codec.encode(document).getBytes(StandardCharsets.UTF_8));
    assertEquals(document, inspection.document().orElseThrow());
    assertTrue(inspection.problems().isEmpty());
  }

  @Test
  void inspectionReportsStructuralProblemsWithoutOfferingAnInvalidDocument() {
    var invalid = document("example").withTitle("");
    var bytes = JsonMapper.builder().build().writeValueAsBytes(invalid);
    var inspection = codec.inspect(bytes);
    assertTrue(inspection.document().isEmpty());
    assertEquals(StudyDocumentValidator.problems(invalid), inspection.problems());
    assertThrows(UnsupportedOperationException.class, () -> inspection.problems().add("extra"));
  }

  @Test
  void inspectionReportsMalformedJsonWithoutThrowing() {
    var inspection = codec.inspect("{broken".getBytes(StandardCharsets.UTF_8));
    assertTrue(inspection.document().isEmpty());
    assertEquals(1, inspection.problems().size());
    assertTrue(inspection.problems().getFirst().startsWith("Invalid study document:"));
  }
}
