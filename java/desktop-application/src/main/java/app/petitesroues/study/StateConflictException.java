package app.petitesroues.study;

/** A displayed version or retry identity no longer matches the requested operation. */
public class StateConflictException extends RuntimeException {
  public StateConflictException(String message) {
    super(message);
  }
}
