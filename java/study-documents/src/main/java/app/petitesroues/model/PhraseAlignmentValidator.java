package app.petitesroues.model;

import app.petitesroues.model.document.PhraseAlignment;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks an alignment's intrinsic graph rules and its binding to the current line text: the chunks
 * on each side, joined with that side's {@link ChunkSeparator}, must reproduce it exactly. Document
 * loading uses this before exposing an alignment to the player. Validation is read-only and
 * requires no persistence, application services or framework configuration.
 */
public final class PhraseAlignmentValidator {
  private PhraseAlignmentValidator() {}

  /**
   * Rejects malformed, incomplete or stale graphs without changing the alignment or line text.
   * Crossing links, many-to-many links and phrases without a separate counterpart are allowed, but
   * each phrase belongs to exactly one link. Link notes are optional: a breakdown is complete
   * before the notes stage explains it. Structural validity does not establish the linguistic
   * accuracy of a translation or explanation.
   *
   * @param alignment graph to validate; required
   * @param source current source line text; required and matched exactly
   * @param target current translation; required and matched exactly
   * @param sourceLanguage the document's source language, which decides how source chunks join
   * @param translationLanguage the document's translation language, likewise for translation chunks
   * @throws IllegalArgumentException if the graph is absent, malformed or does not match the text
   */
  public static void validateFor(
      PhraseAlignment alignment,
      String source,
      String target,
      String sourceLanguage,
      String translationLanguage) {
    if (alignment == null) throw new IllegalArgumentException("Alignment is required");
    if (source == null || target == null)
      throw new IllegalArgumentException("An alignment needs a source and a translation");
    validatePhrases(alignment.source(), source, ChunkSeparator.of(sourceLanguage), "Source");
    validatePhrases(
        alignment.translation(), target, ChunkSeparator.of(translationLanguage), "Translation");
    validateLinks(alignment);
  }

  private static void validatePhrases(
      List<String> phrases, String sentence, String separator, String language) {
    if (phrases.isEmpty() || phrases.stream().anyMatch(phrase -> phrase.isBlank()))
      throw new IllegalArgumentException(language + " phrases must be nonempty text");
    if (!String.join(separator, phrases).equals(sentence))
      throw new IllegalArgumentException(
          language + " phrases must reproduce the complete sentence");
  }

  private static void validateLinks(PhraseAlignment alignment) {
    if (alignment.links().isEmpty())
      throw new IllegalArgumentException("Alignment must contain meaning links");
    Set<Integer> sourceCoverage = new HashSet<>();
    Set<Integer> targetCoverage = new HashSet<>();
    for (var link : alignment.links()) {
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
