package com.petitesroues.sync;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * How a phone trusts its desktop over HTTPS without a certificate authority. The desktop's
 * certificate is self-signed, and the QR code carries its pin: the SHA-256 of the certificate's
 * public key (its DER {@code SubjectPublicKeyInfo}). A connection is trusted only when the desktop
 * presents a certificate with that key, so scanning the code is what makes the phone trust the
 * desktop. Host names are not checked: the desktop's address changes with the network, and the pin
 * already names the machine. Pinning the key rather than the certificate lets the desktop renew its
 * certificate without phones pairing again.
 */
public final class CertificatePins {
  private static final Pattern PIN = Pattern.compile("sha256:[0-9a-f]{64}");

  private CertificatePins() {}

  /** The pin of {@code certificate}'s public key, {@code sha256:} and 64 lowercase hex digits. */
  public static String of(Certificate certificate) {
    return "sha256:" + HexFormat.of().formatHex(sha256(certificate.getPublicKey().getEncoded()));
  }

  /** Whether {@code pin} is a pin in the form {@link #of} writes. */
  public static boolean valid(String pin) {
    return pin != null && PIN.matcher(pin).matches();
  }

  /**
   * Makes {@code connection}, not yet connected, trust only a desktop whose certificate has the key
   * {@code pin} names.
   *
   * @throws IOException if the connection is not HTTPS, since a desktop is only reached over it
   */
  public static void secure(HttpURLConnection connection, String pin) throws IOException {
    if (!(connection instanceof HttpsURLConnection https))
      throw new IOException("The desktop's address is not HTTPS. Pair this phone again.");
    https.setSSLSocketFactory(socketFactory(pin));
    https.setHostnameVerifier((host, session) -> true);
  }

  /** Whether {@code failure}, from a TLS handshake, came from the desktop's key not matching. */
  public static boolean mismatch(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause())
      if (cause instanceof Mismatch) return true;
    return false;
  }

  private static SSLSocketFactory socketFactory(String pin) {
    byte[] expected = pin.getBytes(StandardCharsets.US_ASCII);
    TrustManager pinned =
        new X509TrustManager() {
          @Override
          public void checkServerTrusted(X509Certificate[] chain, String authType)
              throws CertificateException {
            if (chain == null
                || chain.length == 0
                || !MessageDigest.isEqual(
                    expected, of(chain[0]).getBytes(StandardCharsets.US_ASCII)))
              throw new Mismatch();
          }

          @Override
          public void checkClientTrusted(X509Certificate[] chain, String authType)
              throws CertificateException {
            throw new CertificateException("A phone trusts no clients");
          }

          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }
        };
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, new TrustManager[] {pinned}, null);
      return context.getSocketFactory();
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static byte[] sha256(byte[] bytes) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  /** The desktop presented a key other than the pinned one. */
  private static final class Mismatch extends CertificateException {
    Mismatch() {
      super("The desktop's certificate does not match the one this phone paired with");
    }
  }
}
