package app.petitesroues.documents;

import static app.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class StudyDocumentCodecTest {
  private final StudyDocumentCodec codec = new StudyDocumentCodec(StudyDocumentJson.newMapper());

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

  @Test
  void recognitionDoesNotRelaxStrictDecodingOrRequireAValidDocument() {
    var duplicate =
        "{\"sourceLanguage\":\"fr\",\"sourceLanguage\":\"en\",\"video\":{}}"
            .getBytes(StandardCharsets.UTF_8);
    assertTrue(codec.isStudyDocument(duplicate));
    assertThrows(IllegalArgumentException.class, () -> codec.decode(duplicate));
    assertFalse(
        codec.isStudyDocument(
            "{\"sourceLanguage\":\"fr\",\"sourceLanguage\":\"en\"}"
                .getBytes(StandardCharsets.UTF_8)));
    assertTrue(
        codec.isStudyDocument(
            "{\"sourceLanguage\":\"fr\",\"video\":{broken".getBytes(StandardCharsets.UTF_8)));
    var json = codec.encode(document("example"));
    assertThrows(
        IllegalArgumentException.class,
        () -> codec.decode((json + "{}").getBytes(StandardCharsets.UTF_8)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            codec.decode(
                json.replaceFirst("\\{", "{\"unknown\":true,").getBytes(StandardCharsets.UTF_8)));
  }
}
