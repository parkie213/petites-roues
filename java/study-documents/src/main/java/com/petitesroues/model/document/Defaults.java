package com.petitesroues.model.document;

import java.util.List;

/** Default values for absent JSON fields, matching the format's documented defaults. */
final class Defaults {
  private Defaults() {}

  static String text(String value) {
    return value == null ? "" : value;
  }

  static <T> List<T> list(List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
