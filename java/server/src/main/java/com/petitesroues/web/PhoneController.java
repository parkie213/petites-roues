package com.petitesroues.web;

import com.petitesroues.config.PhoneListener;
import com.petitesroues.library.LibraryService;
import com.petitesroues.model.phone.PhoneToken;
import com.petitesroues.phone.PhonePairingService;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

/**
 * What paired phones call, through the phone listener only ({@link LocalRequestFilter}); every
 * route but {@code /phone/pair} needs a phone's token ({@link PhoneRequestFilter}).
 */
@RestController
@RequestMapping("/phone")
public class PhoneController {
  private final PhonePairingService pairing;
  private final PhoneListener listener;
  private final LibraryService library;

  public PhoneController(
      PhonePairingService pairing, PhoneListener listener, LibraryService library) {
    this.pairing = pairing;
    this.listener = listener;
    this.library = library;
  }

  /**
   * A phone pairs: {@code {secret, name}} with the secret from the desktop's QR code.
   *
   * @param secret the one-time pairing secret
   * @param name the phone's name, shown in the desktop's list of phones
   */
  public record Pair(String secret, String name) {}

  /** Pairs a phone, returning its token; a spent or expired secret is a 400. */
  @PostMapping("/pair")
  public PhoneToken pair(@RequestBody Pair request) {
    return pairing.pair(request.secret(), request.name(), listener.desktopName());
  }

  /**
   * The connection test: which desktop this is and how much its library holds.
   *
   * @param name the desktop's name
   * @param version the server's version, or null when unknown
   * @param titles study documents in the library
   * @param media videos and CBZ files in the library
   */
  public record Hello(String name, String version, int titles, int media) {}

  /** Answers a paired phone's connection test. */
  @GetMapping("/hello")
  public Hello hello() throws IOException {
    var contents = library.library().repositories();
    return new Hello(
        listener.desktopName(),
        PhoneController.class.getPackage().getImplementationVersion(),
        contents.stream().mapToInt(folder -> folder.entries().size()).sum(),
        contents.stream().mapToInt(folder -> folder.media().size()).sum());
  }
}
