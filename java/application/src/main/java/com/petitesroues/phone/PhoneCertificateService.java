package com.petitesroues.phone;

import com.petitesroues.model.phone.PhoneCertificate;
import com.petitesroues.settings.SettingsStore;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Service;

/**
 * Owns the phone listener's certificate ({@link PhoneCertificate}): created the first time it is
 * needed, then kept in the library's settings so it lives with the pairings that trust it. Phones
 * pin its key, so it never changes on its own; a lost or new library means pairing phones again, as
 * their tokens are lost with it too. The certificate is self-signed, P-256, and valid for a
 * century, since nothing checks it against a clock that matters: phones check only the key.
 */
@Service
public class PhoneCertificateService {
  static final String KEY = "phone.key";
  static final String CERTIFICATE = "phone.certificate";
  private static final Duration VALIDITY = Duration.ofDays(36_500);

  private final SettingsStore settings;
  private PhoneCertificate current;

  public PhoneCertificateService(SettingsStore settings) {
    this.settings = settings;
  }

  /** This desktop's certificate, created and saved on first use. */
  public synchronized PhoneCertificate certificate() {
    if (current == null) current = load().orElseGet(this::create);
    return current;
  }

  /** The saved certificate; empty when there is none or it cannot be read, so a new one is made. */
  private Optional<PhoneCertificate> load() {
    var key = settings.get(KEY);
    var certificate = settings.get(CERTIFICATE);
    if (key.isEmpty() || certificate.isEmpty()) return Optional.empty();
    try {
      var decoder = Base64.getDecoder();
      var privateKey =
          KeyFactory.getInstance("EC")
              .generatePrivate(new PKCS8EncodedKeySpec(decoder.decode(key.get())));
      var x509 =
          (X509Certificate)
              CertificateFactory.getInstance("X.509")
                  .generateCertificate(new ByteArrayInputStream(decoder.decode(certificate.get())));
      return Optional.of(new PhoneCertificate(privateKey, x509));
    } catch (GeneralSecurityException | IllegalArgumentException unreadable) {
      return Optional.empty();
    }
  }

  /** A new key and certificate, saved: the key first, so a saved certificate always has its key. */
  private PhoneCertificate create() {
    try {
      var generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(new ECGenParameterSpec("secp256r1"));
      KeyPair pair = generator.generateKeyPair();
      var name = new X500Name("CN=petites roues");
      Instant now = Instant.now();
      var builder =
          new JcaX509v3CertificateBuilder(
              name,
              new BigInteger(64, new SecureRandom()),
              Date.from(now.minus(Duration.ofDays(1))),
              Date.from(now.plus(VALIDITY)),
              name,
              pair.getPublic());
      var certificate =
          new JcaX509CertificateConverter()
              .getCertificate(
                  builder.build(
                      new JcaContentSignerBuilder("SHA256withECDSA").build(pair.getPrivate())));
      var encoder = Base64.getEncoder();
      settings.put(KEY, encoder.encodeToString(pair.getPrivate().getEncoded()));
      settings.put(CERTIFICATE, encoder.encodeToString(certificate.getEncoded()));
      return new PhoneCertificate(pair.getPrivate(), certificate);
    } catch (GeneralSecurityException | OperatorCreationException failure) {
      throw new IllegalStateException("Could not create the phone listener's certificate", failure);
    }
  }
}
