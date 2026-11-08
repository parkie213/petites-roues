package com.petitesroues.sync;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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

  /** {@code GET} the titles a phone can copy, answered with a list of {@code DesktopTitle}. */
  public static final String TITLES = PREFIX + "/titles";

  /**
   * {@code GET} one of a title's files by its {@code TitleFile} role; media and posters honour
   * {@code Range}. Spring's pattern; build URLs with {@link #titleFile}.
   */
  public static final String TITLE_FILE = TITLES + "/{id}/{role}";

  /**
   * {@code GET} a note or quiz listed in the notes and quizzes entry. Spring's pattern; build URLs
   * with {@link #page}.
   */
  public static final String PAGE = PREFIX + "/pages/{repository}/{folder}/{name}";

  /** The path of a repository's note or quiz page. */
  public static String page(String repository, String folder, String name) {
    return PREFIX + "/pages/" + encode(repository) + "/" + encode(folder) + "/" + encode(name);
  }

  private static String encode(String segment) {
    return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /** The path of a title's file. */
  public static String titleFile(String id, String role) {
    return TITLES + "/" + encode(id) + "/" + role;
  }

  private PhoneRoutes() {}
}
