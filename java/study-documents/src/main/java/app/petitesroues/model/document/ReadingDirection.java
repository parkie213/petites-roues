package app.petitesroues.model.document;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Page order of a comic; a comic without one reads left to right. */
public enum ReadingDirection {
  @JsonProperty("ltr")
  LTR,
  @JsonProperty("rtl")
  RTL
}
