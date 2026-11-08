package com.petitesroues.model.sync;

import java.util.List;

/**
 * What a phone scans to pair: the QR code's content, as JSON. Versioned so HTTPS can add the
 * certificate fingerprint the phone pins ({@code v} 2) without a new flow. Phones read it with
 * {@link com.petitesroues.sync.PairingCodeReader}.
 *
 * @param v payload version; 1 is plain HTTP
 * @param name the desktop's name, shown on the phone
 * @param urls the phone listener's base URLs, one per LAN address, to try in order
 * @param pair the one-time pairing secret
 */
public record PairingCode(int v, String name, List<String> urls, String pair) {
  /** The version this build writes and reads. */
  public static final int VERSION = 1;
}
