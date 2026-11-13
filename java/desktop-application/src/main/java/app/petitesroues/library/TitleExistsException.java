package app.petitesroues.library;

/** Registration requires explicit replacement when the portable ID exists in the target scope. */
public class TitleExistsException extends IllegalArgumentException {
  public TitleExistsException() {
    this("This document is already registered in this scope; request replacement explicitly");
  }

  public TitleExistsException(String message) {
    super(message);
  }
}
