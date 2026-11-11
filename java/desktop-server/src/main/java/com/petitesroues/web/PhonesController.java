package com.petitesroues.web;

import com.petitesroues.config.PhoneListener;
import com.petitesroues.model.phone.PairedPhone;
import com.petitesroues.model.phone.PhonePairing;
import com.petitesroues.phone.PhonePairingService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The desktop's side of pairing phones, on the UI port. */
@RestController
@RequestMapping("/api")
public class PhonesController {
  private final PhonePairingService pairing;
  private final PhoneListener listener;

  public PhonesController(PhonePairingService pairing, PhoneListener listener) {
    this.pairing = pairing;
    this.listener = listener;
  }

  /**
   * Whether phones can connect, and where.
   *
   * @param enabled whether the phone listener is running
   * @param port its port, or -1
   * @param urls its base URLs on this computer's network addresses
   */
  public record Status(boolean enabled, int port, List<String> urls) {}

  @GetMapping("/phone")
  public Status status() {
    return new Status(listener.enabled(), listener.port(), listener.urls());
  }

  /**
   * The "Allow phones to connect" switch.
   *
   * @param enabled whether phones may connect
   */
  public record Switch(boolean enabled) {}

  /**
   * Opens or closes the phone listener, remembering the choice; returns the new status. A port that
   * cannot be opened is a 409.
   */
  @PutMapping("/phone")
  public ResponseEntity<Object> setEnabled(@RequestBody Switch request) {
    try {
      listener.setEnabled(request.enabled());
    } catch (IllegalStateException failure) {
      return ResponseEntity.status(409)
          .body(new ApiExceptionHandler.ApiError(failure.getMessage()));
    }
    return ResponseEntity.ok(status());
  }

  /**
   * Starts pairing a phone: a one-time code for the QR code.
   *
   * @throws IllegalArgumentException if the phone listener is off or has no network address
   */
  @PostMapping("/phone/pairings")
  public PhonePairing startPairing() {
    if (!listener.enabled())
      throw new IllegalArgumentException(
          "Phones can't connect: switch on Allow phones to connect first");
    var urls = listener.urls();
    if (urls.isEmpty())
      throw new IllegalArgumentException("This computer has no network address a phone can reach");
    return pairing.start(listener.desktopName(), urls);
  }

  /** The phones paired with this desktop. */
  @GetMapping("/phones")
  public List<PairedPhone> phones() {
    return pairing.phones();
  }

  /** Forgets a phone; it must pair again. */
  @DeleteMapping("/phones/{id}")
  public ResponseEntity<Void> forget(@PathVariable String id) {
    pairing.forget(id);
    return ResponseEntity.noContent().build();
  }
}
