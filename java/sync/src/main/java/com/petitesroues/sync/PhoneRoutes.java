package com.petitesroues.sync;

/**
 * The paths the desktop's phone listener serves, shared by the server's controller and the phone's
 * client so neither can drift from the other.
 */
public final class PhoneRoutes {
  /** Everything under here is for phones, and only on the phone listener. */
  public static final String PREFIX = "/phone";

  /** {@code POST} a {@code PairRequest}, answered with a {@code PhoneToken}; needs no token. */
  public static final String PAIR = PREFIX + "/pair";

  /** {@code GET} the connection test, answered with a {@code Hello}. */
  public static final String HELLO = PREFIX + "/hello";

  private PhoneRoutes() {}
}
