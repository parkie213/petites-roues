package com.petitesroues.library.shelves;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a media file's path says about it, the way media servers read it, for naming media that no
 * study document describes and for placing a comic whose document names no series. For display
 * only: linking a document to its media is by fingerprint alone ({@link
 * com.petitesroues.library.linking.MediaLinks}).
 */
public final class MediaNames {
  private MediaNames() {}

  /**
   * A path's reading.
   *
   * @param series the nearest folder that is not a season or episode folder, made readable; null
   *     for a file at the top of its folder
   * @param title the episode or file name, made readable
   */
  public record Named(String series, Integer season, Integer episode, String title) {}

  private static final Pattern EPISODE =
      Pattern.compile("s(\\d{1,3})\\s*e(\\d{1,4})", Pattern.CASE_INSENSITIVE);
  private static final Pattern SEASON_FOLDER =
      Pattern.compile("^(?:season|saison|staffel)[\\s._-]*(\\d{1,3})$", Pattern.CASE_INSENSITIVE);

  /** A trailing language tag on a file name, as in "lunch.zh" or "film.pt-BR". */
  private static final Pattern LANGUAGE_TAG =
      Pattern.compile("\\.[a-z]{2,3}(?:-[a-z0-9]{2,8})?$", Pattern.CASE_INSENSITIVE);

  private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d{1,4})(?=[\\s._-])");

  /**
   * Reads a path relative to its registered folder, with forward slashes. Season and episode come
   * from "s01e02" in the file or an episode folder, or from a "Season 1" folder; in a series folder
   * a leading number is the episode ("083-bienenalarm"). An episode folder ("s01e01-les-flaques/
   * video.mp4") names the episode better than its file.
   */
  public static Named fromPath(String relativePath) {
    List<String> parts = new ArrayList<>(Arrays.asList(relativePath.split("/")));
    String file = parts.remove(parts.size() - 1);
    String stem = LANGUAGE_TAG.matcher(file.replaceFirst("\\.[^.]+$", "")).replaceFirst("");
    Integer season = null, episode = null;
    String series = null, episodeFolder = null;
    for (int i = parts.size() - 1; i >= 0; i--) {
      String folder = parts.get(i);
      Matcher seasonFolder = SEASON_FOLDER.matcher(folder);
      if (seasonFolder.matches()) {
        if (season == null) season = Integer.valueOf(seasonFolder.group(1));
      } else if (EPISODE.matcher(folder).find()) {
        if (episodeFolder == null) episodeFolder = folder;
      } else {
        series = readable(folder);
        break;
      }
    }
    String rest = EPISODE.matcher(stem).find() || episodeFolder == null ? stem : episodeFolder;
    Matcher coded = EPISODE.matcher(rest);
    if (coded.find()) {
      season = Integer.valueOf(coded.group(1));
      episode = Integer.valueOf(coded.group(2));
      rest = coded.replaceFirst("");
    } else if (series != null) {
      Matcher leading = LEADING_NUMBER.matcher(stem);
      if (leading.find()) {
        episode = Integer.valueOf(leading.group(1));
        rest = stem.substring(leading.end());
      }
    }
    String title = readable(rest);
    return new Named(series, season, episode, title.isEmpty() ? readable(stem) : title);
  }

  /** "les-flaques_de.boue" → "Les flaques de boue". */
  static String readable(String name) {
    String words = name.replaceAll("[-_.]+", " ").replaceAll("\\s+", " ").strip();
    if (words.isEmpty()) return words;
    int first = words.codePointAt(0);
    return new String(Character.toChars(first)).toUpperCase(Locale.ROOT)
        + words.substring(Character.charCount(first));
  }
}
