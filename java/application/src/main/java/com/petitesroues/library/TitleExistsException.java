package com.petitesroues.library;

/** Registration requires explicit replacement when the portable ID exists in the target scope. */
public class TitleExistsException extends IllegalArgumentException {
  public TitleExistsException() {
    super("This document is already registered in this scope; request replacement explicitly");
  }
}
