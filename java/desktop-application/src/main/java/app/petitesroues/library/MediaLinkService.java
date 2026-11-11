package app.petitesroues.library;

import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.library.persistence.MediaLinkStore;
import app.petitesroues.library.persistence.MediaStore;
import app.petitesroues.model.library.MediaLink;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Links a media file to a title by hand, for when fingerprints don't: the document was timed
 * against another copy, or records no reference. The link is local library state; the study
 * document is never written, so another library does not see it ({@code prepare/reference.py}
 * records a file as the document's reference). How links take effect is {@link
 * TitleMediaService}'s.
 */
@Service
public class MediaLinkService {
  private final MediaLinkStore links;
  private final MediaStore media;
  private final CatalogueStore titles;
  private final TransactionTemplate transactions;

  public MediaLinkService(
      MediaLinkStore links,
      MediaStore media,
      CatalogueStore titles,
      TransactionTemplate transactions) {
    this.links = links;
    this.media = media;
    this.titles = titles;
    this.transactions = transactions;
  }

  /**
   * Links an indexed media file to a title of the same kind, replacing any link the file had. The
   * title keeps any other files linked to it (copies).
   *
   * @throws NoSuchElementException if the title or the indexed media is unknown
   * @throws IllegalArgumentException if the title is not in a registered folder, or its kind
   *     differs from the media's (a video document links to a video, a comic to a CBZ)
   */
  public void link(MediaLink link) {
    transactions.executeWithoutResult(
        status -> {
          var title =
              titles
                  .find(link.titleId())
                  .orElseThrow(() -> new NoSuchElementException("Title not found"));
          var item =
              media
                  .find(link.repositoryId(), link.relativePath())
                  .orElseThrow(
                      () -> new NoSuchElementException("Media not found — Refresh the library"));
          if (title.location() == null)
            throw new IllegalArgumentException("Only titles in a registered folder can be linked");
          String kind = "comic".equals(title.location().documentType()) ? "comic" : "video";
          if (!kind.equals(item.kind()))
            throw new IllegalArgumentException(
                "A " + kind + " document cannot be linked to a " + item.kind());
          links.link(link, Instant.now().toString());
        });
  }

  /**
   * Removes the file's manual link, so it links by fingerprint again.
   *
   * @throws NoSuchElementException if the file has no manual link
   */
  public void unlink(String repositoryId, String relativePath) {
    if (!links.unlink(repositoryId, relativePath))
      throw new NoSuchElementException("This file is not linked by hand");
  }
}
