package app.petitesroues.sync;

import java.io.IOException;

/**
 * The desktop answered but refused the request (an expired pairing code, a forgotten phone), or
 * answered with a certificate other than the one this phone paired with. Trying again does not
 * help. The message is the desktop's own when it gave one, else a plain one.
 */
public class DesktopException extends IOException {
  private final int status;

  public DesktopException(int status, String message) {
    super(message);
    this.status = status;
  }

  /** The desktop's certificate is not the pinned one; there is no HTTP status. */
  public DesktopException(String message, Throwable cause) {
    super(message, cause);
    this.status = 0;
  }

  /** The HTTP status the desktop answered with, or 0 when its certificate was refused. */
  public int status() {
    return status;
  }
}
