package com.petitesroues.documents;

import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.document.VideoCue;
import com.petitesroues.model.document.VideoDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the application's current document format, sorting video cues chronologically. No catalogue
 * or older-pack conversion is performed.
 */
public final class StudyDocumentReader {
  private static final JsonMapper SHAPE = JsonMapper.builder().build();
  private final StudyDocumentCodec codec = new StudyDocumentCodec();

  /**
   * Whether a JSON file claims to be a study document: a top-level object with {@code
   * sourceLanguage} and a {@code video} or {@code comic} body. Documents may have any file name, so
   * discovery uses this to tell them from other JSON (package files, media-server metadata). It
   * says nothing about validity: a file that claims to be a document is then read with {@link
   * #read}, which rejects an invalid one. Unparseable JSON counts as a document only if it mentions
   * {@code "sourceLanguage"}, so a document broken mid-edit fails Refresh with its path instead of
   * silently dropping out of the library.
   */
  public boolean isStudyDocument(Path file) throws IOException {
    return isStudyDocument(Files.readAllBytes(file));
  }

  /** {@link #isStudyDocument(Path)} for a file already read, as Android reads content URIs. */
  public boolean isStudyDocument(byte[] bytes) {
    JsonNode root;
    try {
      root = SHAPE.readTree(bytes);
    } catch (JacksonException notJson) {
      return new String(bytes, StandardCharsets.UTF_8).contains("\"sourceLanguage\"");
    }
    return root != null
        && root.isObject()
        && root.has("sourceLanguage")
        && (root.has("video") || root.has("comic"));
  }

  /** Reads and validates one document file. */
  public StudyDocument read(Path file) throws IOException {
    return decode(Files.readAllBytes(file));
  }

  /** Decodes and validates one document, preserving comic order and sorting video cues by start. */
  public StudyDocument decode(byte[] bytes) {
    StudyDocument document = codec.decode(bytes);
    // Files may contain cues appended after transcription; playback needs chronological order.
    if (document.body() instanceof VideoDocument)
      document =
          document.withVideo(
              video ->
                  video.withCues(
                      video.cues().stream()
                          .sorted(Comparator.comparingDouble(VideoCue::start))
                          .toList()));
    return document;
  }
}
