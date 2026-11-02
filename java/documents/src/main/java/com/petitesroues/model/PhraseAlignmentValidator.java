package com.petitesroues.model;

import com.petitesroues.model.document.PhraseAlignment;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Checks an alignment's intrinsic graph rules and its binding to the current cue text. Document
 * loading uses this before exposing an alignment to the player. Validation is read-only and
 * requires no persistence, application services or framework configuration.
 */
public final class PhraseAlignmentValidator {
  private PhraseAlignmentValidator() {}

  /**
   * Rejects malformed, incomplete or stale graphs without changing the alignment or cue text.
   * Crossing links, many-to-many links and phrases without a separate counterpart are allowed, but
   * each phrase belongs to exactly one link. Structural validity does not establish the linguistic
   * accuracy of a translation or explanation.
   *
   * @param alignment graph to validate; required
   * @param source current source cue text; required and matched exactly
   * @param target current translation; required and matched exactly
   * @throws IllegalArgumentException if the graph is absent, malformed or does not match the text
   */
  public static void validateFor(PhraseAlignment alignment, String source, String target) {
    if (alignment == null) throw new IllegalArgumentException("Alignment is required");
    if (source == null
        || target == null
        || !Objects.equals(alignment.sourceText(), source)
        || !Objects.equals(alignment.translationText(), target))
      throw new IllegalArgumentException("Alignment sentences must match the current cue text");
    validatePhrases(alignment.source(), source, "Source");
    validatePhrases(alignment.translation(), target, "Translation");
    validateLinks(alignment);
  }

  private static void validatePhrases(List<String> phrases, String sentence, String language) {
    if (phrases.isEmpty() || phrases.stream().anyMatch(phrase -> phrase.isBlank()))
      throw new IllegalArgumentException(language + " phrases must be nonempty text");
    if (!String.join(" ", phrases).equals(sentence))
      throw new IllegalArgumentException(
          language + " phrases must reproduce the complete sentence");
  }

  private static void validateLinks(PhraseAlignment alignment) {
    if (alignment.links().isEmpty())
      throw new IllegalArgumentException("Alignment must contain meaning links");
    Set<Integer> sourceCoverage = new HashSet<>();
    Set<Integer> targetCoverage = new HashSet<>();
    for (var link : alignment.links()) {
      if (link.note().isBlank())
        throw new IllegalArgumentException("Each alignment link needs an explanation");
      checkIndices(link.source(), alignment.source().size(), sourceCoverage, "Source");
      checkIndices(
          link.translation(), alignment.translation().size(), targetCoverage, "Translation");
      if (link.source().isEmpty() && link.translation().isEmpty())
        throw new IllegalArgumentException("An alignment link must reference a phrase");
    }
    if (sourceCoverage.size() != alignment.source().size()
        || targetCoverage.size() != alignment.translation().size())
      throw new IllegalArgumentException("Alignment must account for every phrase");
  }

  private static void checkIndices(
      List<Integer> indices, int size, Set<Integer> coverage, String language) {
    if (indices.stream().anyMatch(i -> i < 0 || i >= size)
        || new HashSet<>(indices).size() != indices.size())
      throw new IllegalArgumentException("Invalid " + language + " alignment phrase references");
    for (int index : indices)
      if (!coverage.add(index))
        throw new IllegalArgumentException(
            language + " phrase " + index + " belongs to more than one link");
  }
}
