package app.petitesroues.sync.model;

/**
 * What a phone receives when it pairs.
 *
 * @param token the bearer token for every later request; shown once; the desktop keeps only its
 *     hash
 * @param name the desktop's name
 */
public record PhoneToken(String token, String name) {}
