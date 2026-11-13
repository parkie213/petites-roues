package app.petitesroues.speech;

/** Reading aloud is not set up on this computer, as opposed to a failure for one request. */
public class SpeechUnavailableException extends RuntimeException {
  public SpeechUnavailableException(String message) {
    super(message);
  }

  public SpeechUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
