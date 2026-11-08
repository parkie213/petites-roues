package com.petitesroues.phone;

import com.petitesroues.model.phone.PairedPhone;
import com.petitesroues.model.phone.PhonePairing;
import com.petitesroues.model.sync.PairingCode;
import com.petitesroues.model.sync.PhoneToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Pairs phones with this desktop and recognises them afterwards. The desktop offers a one-time
 * secret (shown as a QR code, valid for {@link #SECRET_LIFETIME}); a phone that presents it in time
 * receives a long-lived bearer token. Secrets live in memory, so a restart cancels pairings in
 * progress; tokens are kept only as SHA-256 hashes ({@link PhoneStore}).
 */
@Service
public class PhonePairingService {
  /** How long a pairing secret works. */
  public static final Duration SECRET_LIFETIME = Duration.ofMinutes(5);

  private final PhoneStore phones;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Instant> secrets = new ConcurrentHashMap<>();

  @Autowired
  public PhonePairingService(PhoneStore phones) {
    this(phones, Clock.systemUTC());
  }

  PhonePairingService(PhoneStore phones, Clock clock) {
    this.phones = phones;
    this.clock = clock;
  }

  /**
   * Starts a pairing: a new secret, valid for {@link #SECRET_LIFETIME}, in a code naming this
   * desktop and the phone listener's URLs.
   */
  public PhonePairing start(String desktopName, List<String> urls) {
    Instant now = clock.instant();
    secrets.values().removeIf(expiry -> !expiry.isAfter(now));
    String secret = randomText(18);
    Instant expiry = now.plus(SECRET_LIFETIME);
    secrets.put(secret, expiry);
    return new PhonePairing(
        new PairingCode(PairingCode.VERSION, desktopName, List.copyOf(urls), secret), expiry);
  }

  /**
   * Pairs the phone presenting a pairing secret, spending the secret.
   *
   * @throws IllegalArgumentException if the secret is unknown, spent or expired, or the phone gives
   *     no name
   */
  public PhoneToken pair(String secret, String phoneName, String desktopName) {
    if (phoneName == null || phoneName.isBlank())
      throw new IllegalArgumentException("Name the phone");
    Instant expiry = secret == null ? null : secrets.remove(secret);
    Instant now = clock.instant();
    if (expiry == null || !expiry.isAfter(now))
      throw new IllegalArgumentException(
          "This pairing code has expired or was already used. Show a new one on the desktop.");
    String token = randomText(32);
    phones.insert(
        new PairedPhone(UUID.randomUUID().toString(), phoneName.strip(), now.toString(), null),
        sha256(token));
    return new PhoneToken(token, desktopName);
  }

  /** The paired phone holding this token, recording that it was seen; empty if none does. */
  public Optional<PairedPhone> authenticate(String token) {
    if (token == null || token.isBlank()) return Optional.empty();
    var phone = phones.withToken(sha256(token));
    phone.ifPresent(found -> phones.seen(found.id(), clock.instant().toString()));
    return phone;
  }

  /** Every paired phone, oldest first. */
  public List<PairedPhone> phones() {
    return phones.all();
  }

  /**
   * Forgets a phone, revoking its token.
   *
   * @throws NoSuchElementException if no phone has this ID
   */
  public void forget(String id) {
    if (!phones.delete(id)) throw new NoSuchElementException("Phone not found");
  }

  private String randomText(int bytes) {
    byte[] value = new byte[bytes];
    random.nextBytes(value);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  static String sha256(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
