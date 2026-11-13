package app.petitesroues.web;

import app.petitesroues.config.PhoneListener;
import app.petitesroues.library.LibraryService;
import app.petitesroues.phone.PhonePairingService;
import app.petitesroues.phone.PhoneTransferService;
import app.petitesroues.sync.PhoneRoutes;
import app.petitesroues.sync.model.DesktopTitle;
import app.petitesroues.sync.model.Hello;
import app.petitesroues.sync.model.PairRequest;
import app.petitesroues.sync.model.PhoneToken;
import app.petitesroues.sync.model.TitleFile;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * What paired phones call, registered explicitly in the isolated phone context only; every route
 * but {@code /phone/pair} needs a phone's token ({@link PhoneRequestFilter}). The routes and
 * records are shared with the phone's client ({@code app.petitesroues.sync}).
 */
@RestController
public class PhoneController {
  private final PhonePairingService pairing;
  private final PhoneListener listener;
  private final LibraryService library;
  private final PhoneTransferService transfer;

  public PhoneController(
      PhonePairingService pairing,
      PhoneListener listener,
      LibraryService library,
      PhoneTransferService transfer) {
    this.pairing = pairing;
    this.listener = listener;
    this.library = library;
    this.transfer = transfer;
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

  /** A note or quiz from the notes and quizzes entry, served unchanged. */
  @GetMapping(PhoneRoutes.PAGE)
  public ResponseEntity<FileSystemResource> page(
      @PathVariable String repository, @PathVariable String folder, @PathVariable String name)
      throws IOException {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
        .cacheControl(CacheControl.noStore())
        .body(new FileSystemResource(transfer.page(repository, folder, name)));
  }

  /** The notes and quizzes entry, then the titles a phone can copy, with the files each needs. */
  @GetMapping(PhoneRoutes.TITLES)
  public List<DesktopTitle> titles() throws IOException {
    return transfer.titles();
  }

  /**
   * One of a title's files by role. Media and posters are served unchanged, and Spring answers
   * {@code Range} requests, so a phone resumes a cut download; the document is served as the player
   * sees it.
   */
  @GetMapping(PhoneRoutes.TITLE_FILE)
  public ResponseEntity<?> titleFile(@PathVariable String id, @PathVariable String role)
      throws IOException {
    if (TitleFile.DOCUMENT.equals(role))
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_JSON)
          .cacheControl(CacheControl.noStore())
          .body(transfer.document(id));
    var file = new FileSystemResource(transfer.file(id, role));
    return ResponseEntity.ok()
        .contentType(MediaTypeFactory.getMediaType(file).orElse(MediaType.APPLICATION_OCTET_STREAM))
        .body(file);
  }
}
