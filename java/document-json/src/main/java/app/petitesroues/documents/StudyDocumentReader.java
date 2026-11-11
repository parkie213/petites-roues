package app.petitesroues.documents;

import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.document.VideoDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Recognises and loads the current document format for study. Unlike the codec, loading sorts video
 * cues chronologically for playback; comic order is preserved. Recognition is permissive so broken
 * documents are reported during discovery rather than silently skipped. No catalogue or older-pack
 * conversion is performed.
 */
public final class StudyDocumentReader {
  private final StudyDocumentCodec codec;

  /** Uses the supplied format codec for validation and decoding; owns no codec configuration. */
  public StudyDocumentReader(StudyDocumentCodec codec) {
    this.codec = codec;
  }

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
    return codec.isStudyDocument(bytes);
  }

  /** Inspects a candidate once; valid documents have the same study ordering as {@link #decode}. */
  public StudyDocumentCodec.Inspection inspect(byte[] bytes) {
    var result = codec.inspect(bytes);
    return new StudyDocumentCodec.Inspection(
        result.document().map(StudyDocumentReader::forStudy), result.problems());
  }

  /** Reads and validates one document file. */
  public StudyDocument read(Path file) throws IOException {
    return decode(Files.readAllBytes(file));
  }

  /** Decodes and validates one document, preserving comic order and sorting video cues by start. */
  public StudyDocument decode(byte[] bytes) {
    return forStudy(codec.decode(bytes));
  }

  /** Keeps comic order and prepares video cues for chronological playback. */
  private static StudyDocument forStudy(StudyDocument document) {
    if (document.body() instanceof VideoDocument)
      return document.withVideo(VideoDocument::inPlaybackOrder);
    return document;
  }
}
