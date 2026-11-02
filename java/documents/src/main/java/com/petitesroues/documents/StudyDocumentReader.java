package com.petitesroues.documents;

import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.document.VideoCue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Reads the application's current document format, sorting video cues chronologically. No catalogue
 * or older-pack conversion is performed.
 */
public final class StudyDocumentReader {
  private final StudyDocumentCodec codec = new StudyDocumentCodec();

  /** Reads and validates one document file. */
  public StudyDocument read(Path file) throws IOException {
    return decode(Files.readAllBytes(file));
  }

  /** Decodes and validates one document, preserving comic order and sorting video cues by start. */
  public StudyDocument decode(byte[] bytes) {
    StudyDocument document = codec.decode(bytes);
    // Files may contain cues appended after transcription; playback needs chronological order.
    if (document.hasVideo())
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
