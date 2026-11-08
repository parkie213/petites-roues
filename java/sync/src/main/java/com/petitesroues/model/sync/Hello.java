package com.petitesroues.model.sync;

/**
 * The desktop's answer to a paired phone's connection test.
 *
 * @param name the desktop's name
 * @param version the desktop's version, or null when unknown
 * @param titles study documents in the desktop's library
 * @param media videos and CBZ files in the desktop's library
 */
public record Hello(String name, String version, int titles, int media) {}
