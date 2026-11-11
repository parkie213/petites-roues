package app.petitesroues.study;

import app.petitesroues.documents.StudyDocumentReader;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.library.CatalogueEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reads portable subtitle/notes JSON documents. The application never changes them: player edits
 * are queued and replayed, and agents or preparation scripts own the files. Snapshots bind the
 * decoded document to the hash of the exact bytes read; this class never writes files.
 */
@Component
public class DocumentStore {
  private final StudyDocumentReader reader;

  public DocumentStore(StudyDocumentReader reader) {
    this.reader = reader;
  }

  /**
   * Every reason {@code candidate} could not replace the title's document file, without reading or
   * writing the file. Covers parsing, structural rules and the indexed document ID. Empty means
   * valid; teaching quality is not judged.
   */
  public List<String> problems(CatalogueEntry entry, byte[] candidate) {
    var inspection = reader.inspect(candidate);
    var problems = new ArrayList<>(inspection.problems());
    if (inspection.document().isPresent()) {
      var id = inspection.document().orElseThrow().id();
      if (!entry.documentId().equals(id))
        problems.add("Document ID must stay " + entry.documentId() + ", not " + id);
    }
    return problems;
  }

  /** The decoded file and the SHA-256 of its exact bytes, read together. */
  public record Snapshot(StudyDocument document, String sha256) {}

  public Snapshot read(CatalogueEntry entry) throws IOException {
    byte[] bytes = Files.readAllBytes(entry.documentFile());
    StudyDocument document = reader.decode(bytes);
    if (!entry.documentId().equals(document.id()))
      throw new IOException("Document ID differs from library index: " + entry.id());
    try {
      return new Snapshot(
          document, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
