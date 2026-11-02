package com.petitesroues.model.document;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Page order of a comic; {@code UNSPECIFIED} reads left to right. */
public enum ReadingDirection {
  @JsonProperty("READING_DIRECTION_UNSPECIFIED")
  UNSPECIFIED,
  @JsonProperty("READING_DIRECTION_LTR")
  LTR,
  @JsonProperty("READING_DIRECTION_RTL")
  RTL
}
