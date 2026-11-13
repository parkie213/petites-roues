package app.petitesroues.library;

import app.petitesroues.comic.CbzReader;
import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.library.persistence.MediaStore;
import app.petitesroues.media.AudioLanguages;
import app.petitesroues.model.document.ComicDocument;
import app.petitesroues.model.document.ComicPage;
import app.petitesroues.model.document.ComicReference;
import app.petitesroues.model.document.ReadingDirection;
import app.petitesroues.model.document.StudyBody;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.document.VideoDocument;
import app.petitesroues.model.document.VideoReference;
import app.petitesroues.model.library.IndexedMedia;
import app.petitesroues.model.library.TitleSkeletonRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

/**
 * Builds the empty study document that starts a title: its metadata and a reference to media that
 * Refresh has already indexed, with no cues, or with a comic's page list and no boxes. The
 * fingerprint, size, duration and audio languages come from the index; a CBZ's page list is read
 * from the archive. The result is encoded with the canonical document codec, which validates it, so
 * it is a document the app will load. Nothing is written: the caller saves the JSON in its content
 * repository and imports it. The archive is read outside any transaction, and no lock is held.
 */
@Service
public class TitleSkeletonService {
  private final MediaStore media;
  private final CatalogueStore catalogue;
  private final CbzReader cbz;
  private final StudyDocumentCodec codec;

  public TitleSkeletonService(
      MediaStore media, CatalogueStore catalogue, CbzReader cbz, StudyDocumentCodec codec) {
    this.media = media;
    this.catalogue = catalogue;
    this.cbz = cbz;
    this.codec = codec;
  }

  /**
   * The canonical JSON of a new document for the requested media.
   *
   * @throws IllegalArgumentException (400) for a request that cannot make a valid document: media
   *     not in the index or changed since the refresh, a missing title, a volume on a video, a
   *     video with no audio or with another language first, a CBZ without images
   * @throws TitleExistsException (409) if a title already has the document ID
   * @throws IOException if the CBZ cannot be read
   */
  public String skeleton(TitleSkeletonRequest request) throws IOException {
    if (request.title() == null || request.title().isBlank())
      throw new IllegalArgumentException("A title is required");
    var item = indexed(request.media());
    boolean comic = "comic".equals(item.kind());
    if (request.volume() != null && !comic)
      throw new IllegalArgumentException("A volume is for comics, not a video");
    String source = valueOr(request.sourceLanguage(), "fr");
    String id =
        request.id() == null || request.id().isBlank() ? idOf(request.title()) : request.id();
    if (id.isEmpty())
      throw new IllegalArgumentException("The title gives no ID; pass an id of letters or digits");
    catalogue
        .findByDocumentId(id)
        .ifPresent(
            existing -> {
              throw new TitleExistsException(
                  "A title with document ID " + id + " already exists: " + existing.title());
            });

    StudyBody body =
        comic
            ? comic(item, request)
            : video(item, source, Boolean.TRUE.equals(request.ignoreTrackLanguage()));
    String series = request.series() == null ? "" : request.series().strip();
    var document =
        new StudyDocument(
            id,
            request.title().strip(),
            source,
            valueOr(request.translationLanguage(), "en"),
            valueOr(request.notesLanguage(), "en"),
            series.isEmpty() ? null : series,
            null,
            body);
    String json = codec.encode(document);
    var problems = codec.problems(json.getBytes(StandardCharsets.UTF_8));
    if (!problems.isEmpty()) throw new IllegalArgumentException(String.join("; ", problems));
    return json;
  }

  /** The default ID: the title in lower case, each run of other characters one dash, trimmed. */
  static String idOf(String title) {
    return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
  }

  private static String valueOr(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.strip();
  }

  private static VideoDocument video(IndexedMedia item, String source, boolean ignoreLanguage) {
    String name = item.file().getFileName().toString();
    if (item.audioCodecs() != null && item.audioCodecs().isEmpty())
      throw new IllegalArgumentException(name + " has no audio track.");
    if (item.durationSeconds() == null)
      throw new IllegalArgumentException(
          name
              + " has no probed duration; check that ffprobe works, then Refresh the media folder.");
    if (!ignoreLanguage && item.languageWarning(source) != null) {
      var tracks = AudioLanguages.split(item.audioLanguages());
      var listing = new ArrayList<String>();
      for (int i = 0; i < tracks.size(); i++)
        listing.add("0:a:" + i + " " + (tracks.get(i).isEmpty() ? "untagged" : tracks.get(i)));
      int wanted = tracks.indexOf(AudioLanguages.tag(source.split("-")[0]));
      String fix =
          wanted >= 0
              ? "Put 0:a:" + wanted + " first (see 'Start the document' in docs/PREPARE-TITLE.md)"
              : "It has no " + source + " track";
      throw new IllegalArgumentException(
          name
              + " plays "
              + item.playedLanguage()
              + " audio first, not "
              + source
              + " ("
              + String.join(", ", listing)
              + "). "
              + fix
              + ", or pass ignoreTrackLanguage if the tag is wrong.");
    }
    double seconds =
        BigDecimal.valueOf(item.durationSeconds())
            .setScale(3, RoundingMode.HALF_EVEN)
            .doubleValue();
    return new VideoDocument(
        null, null, new VideoReference(item.fingerprint(), item.sizeBytes(), seconds), List.of());
  }

  private ComicDocument comic(IndexedMedia item, TitleSkeletonRequest request) throws IOException {
    var entries = cbz.pages(item.file());
    if (entries.isEmpty())
      throw new IllegalArgumentException(item.file().getFileName() + " has no page images.");
    var pages =
        IntStream.rangeClosed(1, entries.size())
            .mapToObj(
                n -> new ComicPage("page-%03d".formatted(n), entries.get(n - 1), String.valueOf(n)))
            .toList();
    return new ComicDocument(
        request.volume(),
        Boolean.TRUE.equals(request.leftToRight()) ? ReadingDirection.LTR : ReadingDirection.RTL,
        new ComicReference(item.fingerprint(), item.sizeBytes()),
        pages,
        List.of());
  }

  /**
   * The indexed media at this absolute path, compared by real path so a symlinked or differently
   * spelled path finds it. Refuses any file the index does not hold, and one that has changed since
   * its refresh, because the index then no longer describes it.
   */
  private IndexedMedia indexed(String requested) {
    String notIndexed =
        "That file is not in the media index: Add or Refresh its media folder first.";
    if (requested == null || requested.isBlank())
      throw new IllegalArgumentException("The media path is required");
    Path wanted = Path.of(requested);
    if (!wanted.isAbsolute()) throw new IllegalArgumentException("The media path must be absolute");
    Path real;
    try {
      real = wanted.toRealPath();
    } catch (IOException missing) {
      throw new IllegalArgumentException(notIndexed);
    }
    for (var item : media.all()) {
      try {
        if (!item.file().getFileName().equals(real.getFileName())
            || !item.file().toRealPath().equals(real)) continue;
        if (Files.size(real) != item.sizeBytes()
            || Files.getLastModifiedTime(real).toMillis() != item.modifiedMillis())
          throw new IllegalArgumentException(
              "That file has changed since the last refresh: Refresh its media folder first.");
      } catch (IOException gone) {
        continue;
      }
      return item;
    }
    throw new IllegalArgumentException(notIndexed);
  }
}
