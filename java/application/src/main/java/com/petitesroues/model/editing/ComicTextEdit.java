package com.petitesroues.model.editing;

/**
 * Source-text replacement bound to the text the editor actually opened, preventing lost updates.
 */
public record ComicTextEdit(String expectedSource, String source) {}
