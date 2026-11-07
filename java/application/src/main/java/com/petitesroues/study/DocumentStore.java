package com.petitesroues.study;

import com.petitesroues.documents.StudyDocumentCodec;
import com.petitesroues.documents.StudyDocumentReader;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.library.CatalogueEntry;
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
 * are queued and replayed, and agents or preparation scripts own the files. The one write is
 * creating a new title's document, which never replaces an existing file.
 */
@Component
public class DocumentStore {
  private final StudyDocumentReader reader;
  private final StudyDocumentCodec codec;

  public DocumentStore(StudyDocumentReader reader) {
    this.reader = reader;
    this.codec = new StudyDocumentCodec();
  }

  /**
   * Every reason {@code candidate} could not replace the title's document file, without reading or
   * writing the file. Covers parsing, structural rules and the indexed document ID. Empty means
   * valid; teaching quality is not judged.
   */
  public List<String> problems(CatalogueEntry entry, byte[] candidate) throws IOException {
    var problems = new ArrayList<>(codec.problems(candidate));
    if (problems.isEmpty()) {
      var id = reader.decode(candidate).id();
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
