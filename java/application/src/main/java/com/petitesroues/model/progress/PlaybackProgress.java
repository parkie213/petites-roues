package com.petitesroues.model.progress;

/**
 * Local resume position; never included in portable study documents. Null timestamp means unsaved.
 */
public record PlaybackProgress(double positionSeconds, String updatedAt) {}
