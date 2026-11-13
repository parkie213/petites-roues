package app.petitesroues.library;

/**
 * A document operation that cannot proceed because the title's state does not allow it: an open
 * review batch blocks an import, the accepted copy moved under a completion, or a title has no
 * accepted copy yet. Nothing was changed; the message says what to do. Served as HTTP 409.
 */
public class DocumentConflictException extends IllegalStateException {
  public DocumentConflictException(String message) {
    super(message);
  }
}
