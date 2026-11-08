package com.petitesroues.sync;

import java.io.IOException;

/**
 * The desktop answered but refused the request (an expired pairing code, a forgotten phone). The
 * message is the desktop's own when it gave one, else a plain one for the status.
 */
public class DesktopException extends IOException {
  private final int status;

  public DesktopException(int status, String message) {
    super(message);
    this.status = status;
  }

  /** The HTTP status the desktop answered with. */
  public int status() {
    return status;
  }
}
