package app.petitesroues.model;

import static app.petitesroues.support.SpecFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.model.document.PhraseAlignment;
import app.petitesroues.model.document.PhraseLink;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhraseAlignmentTest {

  @Test
  void reusingAPhraseListCannotChangeAnExistingAlignment() {
    var phrases = new ArrayList<>(List.of("Salut"));
    var alignment =
        newAlignment(phrases, List.of("Hi"), List.of(newLink(List.of(0), List.of(0), "Greeting")));

    // A caller reuses its input list while preparing a different sentence.
    phrases.set(0, "Bonsoir");

    assertEquals(
        List.of("Salut"),
        alignment.source(),
        "Changing a caller's list must not rewrite an existing alignment");
    assertDoesNotThrow(
        () -> PhraseAlignmentValidator.validateFor(alignment, "Salut", "Hi", "fr", "en"),
        "The alignment must still match the original subtitle");
  }

  @Test
  void alignmentAndNestedLinksDefensivelyCopyEveryList() {
    var source = new ArrayList<>(List.of("Salut"));
    var translation = new ArrayList<>(List.of("Hi"));
    var sourceIndices = new ArrayList<>(List.of(0));
    var targetIndices = new ArrayList<>(List.of(0));
    var link = newLink(sourceIndices, targetIndices, "Greeting");
    var links = new ArrayList<>(List.of(link));
    var alignment = newAlignment(source, translation, links);
    source.clear();
    translation.clear();
    sourceIndices.clear();
    targetIndices.clear();
    links.clear();
    assertEquals(List.of("Salut"), alignment.source());
    assertEquals(List.of("Hi"), alignment.translation());
    assertEquals(List.of(link), alignment.links());
    assertEquals(List.of(0), link.source());
    assertEquals(List.of(0), link.translation());
    assertThrows(UnsupportedOperationException.class, () -> alignment.source().clear());
    assertThrows(UnsupportedOperationException.class, () -> alignment.translation().clear());
    assertThrows(UnsupportedOperationException.class, () -> alignment.links().clear());
    assertThrows(UnsupportedOperationException.class, () -> link.source().clear());
    assertThrows(UnsupportedOperationException.class, () -> link.translation().clear());
  }

  @Test
  void nullListElementsAreRejectedBeforeDomainValidation() {
    List<String> nullText = Arrays.asList((String) null);
    List<Integer> nullIndex = Arrays.asList((Integer) null);
    assertThrows(
        NullPointerException.class, () -> new PhraseAlignment(nullText, List.of(), List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new PhraseAlignment(List.of(), List.of(), Arrays.asList((PhraseLink) null)));
    assertThrows(NullPointerException.class, () -> new PhraseLink(nullIndex, List.of(), ""));
  }
}
