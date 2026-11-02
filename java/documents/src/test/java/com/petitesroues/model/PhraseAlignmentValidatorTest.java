package com.petitesroues.model;

import static com.petitesroues.support.SpecFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.model.document.PhraseAlignment;
import com.petitesroues.model.document.PhraseLink;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PhraseAlignmentValidatorTest {
  private static final PhraseLink GREETING = newLink(List.of(0), List.of(0), "Greeting");

  private static PhraseAlignment greeting(
      List<String> source, List<String> translation, List<PhraseLink> links) {
    return newAlignment("Salut", "Hi", source, translation, links);
  }

  private static void validate(PhraseAlignment alignment) {
    PhraseAlignmentValidator.validateFor(
        alignment, alignment.sourceText(), alignment.translationText());
  }

  @Test
  void currentSentenceBindingIsRequiredOnBothSides() {
    var alignment = greeting(List.of("Salut"), List.of("Hi"), List.of(GREETING));
    assertDoesNotThrow(() -> validate(alignment));
    assertThrows(
        IllegalArgumentException.class,
        () -> PhraseAlignmentValidator.validateFor(null, "Salut", "Hi"));
    for (String source : Arrays.asList(null, "Bonsoir", "Salut!"))
      assertThrows(
          IllegalArgumentException.class,
          () -> PhraseAlignmentValidator.validateFor(alignment, source, "Hi"));
    for (String translation : Arrays.asList(null, "Hello", "Hi!"))
      assertThrows(
          IllegalArgumentException.class,
          () -> PhraseAlignmentValidator.validateFor(alignment, "Salut", translation));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            validate(newAlignment(null, "Hi", List.of("Salut"), List.of("Hi"), List.of(GREETING))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            validate(
                newAlignment("Salut", null, List.of("Salut"), List.of("Hi"), List.of(GREETING))));
  }

  static Stream<Arguments> invalidPhrases() {
    var cases = Stream.<Arguments>builder();
    List<List<String>> malformed = Arrays.asList(null, List.of(), List.of(" "));
    for (var phrases : malformed) {
      cases.add(
          Arguments.of(
              greeting(phrases, List.of("Hi"), List.of(GREETING)),
              "Source phrases must be nonempty text"));
      cases.add(
          Arguments.of(
              greeting(List.of("Salut"), phrases, List.of(GREETING)),
              "Translation phrases must be nonempty text"));
    }
    cases.add(
        Arguments.of(
            greeting(List.of("Salut!"), List.of("Hi"), List.of(GREETING)),
            "Source phrases must reproduce the complete sentence"));
    cases.add(
        Arguments.of(
            greeting(List.of("Salut"), List.of("Hi!"), List.of(GREETING)),
            "Translation phrases must reproduce the complete sentence"));
    return cases.build();
  }

  @ParameterizedTest
  @MethodSource("invalidPhrases")
  void phrasesMustReproduceEachSentenceWithoutNullOrBlankParts(
      PhraseAlignment alignment, String message) {
    assertEquals(
        message,
        assertThrows(IllegalArgumentException.class, () -> validate(alignment)).getMessage());
  }

  static Stream<Arguments> invalidLinks() {
    return Stream.of(
        Arguments.of(null, "Alignment must contain meaning links"),
        Arguments.of(List.of(), "Alignment must contain meaning links"),
        Arguments.of(
            List.of(newLink(List.of(0), List.of(0), null)),
            "Each alignment link needs an explanation"),
        Arguments.of(
            List.of(newLink(List.of(0), List.of(0), " ")),
            "Each alignment link needs an explanation"),
        Arguments.of(
            List.of(newLink(List.of(), List.of(), "No references")),
            "An alignment link must reference a phrase"));
  }

  @ParameterizedTest
  @MethodSource("invalidLinks")
  void linksNeedReferencesAndExplanations(List<PhraseLink> links, String message) {
    var alignment = greeting(List.of("Salut"), List.of("Hi"), links);
    assertEquals(
        message,
        assertThrows(IllegalArgumentException.class, () -> validate(alignment)).getMessage());
  }

  static Stream<Arguments> invalidIndices() {
    var cases = Stream.<Arguments>builder();
    List<List<Integer>> invalid = Arrays.asList(List.of(-1), List.of(1), List.of(0, 0));
    for (var indices : invalid) {
      cases.add(Arguments.of(newLink(indices, List.of(0), "Greeting"), "Source"));
      cases.add(Arguments.of(newLink(List.of(0), indices, "Greeting"), "Translation"));
    }
    return cases.build();
  }

  @ParameterizedTest
  @MethodSource("invalidIndices")
  void indicesMustBePresentDistinctAndInRangeOnBothSides(PhraseLink link, String language) {
    var alignment = greeting(List.of("Salut"), List.of("Hi"), List.of(link));
    assertEquals(
        "Invalid " + language + " alignment phrase references",
        assertThrows(IllegalArgumentException.class, () -> validate(alignment)).getMessage());
  }

  @Test
  void everyPhraseOnBothSidesMustBeCovered() {
    for (var link :
        List.of(
            newLink(List.of(0), List.of(0, 1), "Missing French phrase"),
            newLink(List.of(0, 1), List.of(0), "Missing English phrase"))) {
      var alignment =
          newAlignment(
              "Tu dors",
              "You sleep",
              List.of("Tu", "dors"),
              List.of("You", "sleep"),
              List.of(link));
      assertEquals(
          "Alignment must account for every phrase",
          assertThrows(IllegalArgumentException.class, () -> validate(alignment)).getMessage());
    }
  }

  @Test
  void crossingLinksKeepTheTwoLanguagesInTheirOwnSentenceOrder() {
    var alignment =
        newAlignment(
            "Tu me manques.",
            "I miss you.",
            List.of("Tu", "me", "manques."),
            List.of("I", "miss", "you."),
            List.of(
                newLink(List.of(0), List.of(2), "Person missed"),
                newLink(List.of(1), List.of(0), "Person feeling the absence"),
                newLink(List.of(2), List.of(1), "English reverses the roles")));
    assertDoesNotThrow(() -> validate(alignment));
  }

  @Test
  void manyToManyLinksAreAllowedButAPhraseBelongsToOneLink() {
    var grouped =
        newAlignment(
            "Tout va bien.",
            "All is well.",
            List.of("Tout", "va bien."),
            List.of("All", "is well."),
            List.of(newLink(List.of(0, 1), List.of(0, 1), "Whole expression")));
    assertDoesNotThrow(() -> validate(grouped));
    var shared = greeting(List.of("Salut"), List.of("Hi"), List.of(GREETING, GREETING));
    assertThrows(IllegalArgumentException.class, () -> validate(shared));
  }

  @Test
  void eitherLanguageMayHaveAPhraseWithNoSeparateCounterpart() {
    var omittedFrench =
        newAlignment(
            "Il dort.",
            "Sleeping.",
            List.of("Il", "dort."),
            List.of("Sleeping."),
            List.of(
                newLink(List.of(0), List.of(), "Omitted English subject"),
                newLink(List.of(1), List.of(0), "Action")));
    var addedEnglish =
        newAlignment(
            "Dors.",
            "You sleep.",
            List.of("Dors."),
            List.of("You", "sleep."),
            List.of(
                newLink(List.of(), List.of(0), "Explicit English subject"),
                newLink(List.of(0), List.of(1), "Action")));
    assertDoesNotThrow(() -> validate(omittedFrench));
    assertDoesNotThrow(() -> validate(addedEnglish));
  }
}
