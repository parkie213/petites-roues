package app.petitesroues.model.phone;

import app.petitesroues.sync.CertificatePins;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * The phone listener's TLS identity: a self-signed certificate and its private key. Phones trust it
 * through its {@link #pin()}, which the pairing QR code carries.
 *
 * @param key the private key; a secret of this desktop
 * @param certificate the self-signed certificate for {@code key}
 */
public record PhoneCertificate(PrivateKey key, X509Certificate certificate) {
  /** The pin phones check the desktop against ({@link CertificatePins}). */
  public String pin() {
    return CertificatePins.of(certificate);
  }

  /**
   * An in-memory key store holding the key and certificate under {@code alias}, for a TLS server.
   * {@code password} only guards the store in memory; nothing is written.
   */
  public KeyStore keyStore(String alias, char[] password) {
    try {
      KeyStore store = KeyStore.getInstance("PKCS12");
      store.load(null, null);
      store.setKeyEntry(alias, key, password, new Certificate[] {certificate});
      return store;
    } catch (GeneralSecurityException | IOException impossible) {
      throw new IllegalStateException(
          "Could not hold the phone listener's certificate", impossible);
    }
  }
}
