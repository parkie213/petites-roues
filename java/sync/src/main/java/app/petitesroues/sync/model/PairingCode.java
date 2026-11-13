package app.petitesroues.sync.model;

import java.util.List;

/**
 * What a phone scans to pair: the QR code's content, as JSON. Phones read it with {@link
 * app.petitesroues.sync.PairingCodeReader}. Scanning it is what makes the phone trust the desktop:
 * it carries the pin of the desktop's certificate ({@link app.petitesroues.sync.CertificatePins}).
 *
 * @param v payload version; 2 is HTTPS with a pinned certificate (1, plain HTTP, is no longer read)
 * @param name the desktop's name, shown on the phone
 * @param urls the phone listener's {@code https} base URLs, one per LAN address, to try in order
 * @param pair the one-time pairing secret
 * @param cert the pin of the phone listener's certificate
 */
public record PairingCode(int v, String name, List<String> urls, String pair, String cert) {
  /** The version this build writes and reads. */
  public static final int VERSION = 2;
}
