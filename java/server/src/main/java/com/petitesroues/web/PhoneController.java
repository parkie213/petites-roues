package com.petitesroues.web;

import com.petitesroues.config.PhoneListener;
import com.petitesroues.library.LibraryService;
import com.petitesroues.model.sync.Hello;
import com.petitesroues.model.sync.PairRequest;
import com.petitesroues.model.sync.PhoneToken;
import com.petitesroues.phone.PhonePairingService;
import com.petitesroues.sync.PhoneRoutes;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

/**
 * What paired phones call, through the phone listener only ({@link LocalRequestFilter}); every
 * route but {@code /phone/pair} needs a phone's token ({@link PhoneRequestFilter}). The routes and
 * records are shared with the phone's client ({@code com.petitesroues.sync}).
 */
@RestController
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

  /** Pairs a phone, returning its token; a spent or expired secret is a 400. */
  @PostMapping(PhoneRoutes.PAIR)
  public PhoneToken pair(@RequestBody PairRequest request) {
    return pairing.pair(request.secret(), request.name(), listener.desktopName());
  }

  /** Answers a paired phone's connection test: which desktop this is and how much it holds. */
  @GetMapping(PhoneRoutes.HELLO)
  public Hello hello() throws IOException {
    var contents = library.library().repositories();
    return new Hello(
        listener.desktopName(),
        PhoneController.class.getPackage().getImplementationVersion(),
        contents.stream().mapToInt(folder -> folder.entries().size()).sum(),
        contents.stream().mapToInt(folder -> folder.media().size()).sum());
  }
}
