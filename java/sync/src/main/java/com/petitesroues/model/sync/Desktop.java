package com.petitesroues.model.sync;

/**
 * A desktop a phone is paired with, as the phone keeps it.
 *
 * @param name the desktop's name
 * @param url the phone listener's base URL that answered when pairing
 * @param token the phone's bearer token there; its credential, to be stored privately
 * @param pin the pin of the desktop's certificate, the only one the phone trusts ({@link
 *     com.petitesroues.sync.CertificatePins})
 */
public record Desktop(String name, String url, String token, String pin) {}
