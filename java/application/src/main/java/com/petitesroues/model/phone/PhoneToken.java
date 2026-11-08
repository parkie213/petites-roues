package com.petitesroues.model.phone;

/**
 * What a phone receives when it pairs.
 *
 * @param token the bearer token for every later request; shown once, kept only as a hash here
 * @param name the desktop's name
 */
public record PhoneToken(String token, String name) {}
