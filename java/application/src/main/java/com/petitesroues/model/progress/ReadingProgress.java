package com.petitesroues.model.progress;

/** Local comic resume position; null page and timestamp mean no saved position. */
public record ReadingProgress(String pageId, String updatedAt) {}
