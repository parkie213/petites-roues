package app.petitesroues.web;

import app.petitesroues.library.TitleSkeletonService;
import app.petitesroues.model.library.TitleSkeletonRequest;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Starts new titles for agents and preparation scripts; the app never writes the document. */
@RestController
@RequestMapping("/api/titles")
public class TitleSkeletonController {
  private final TitleSkeletonService skeletons;

  public TitleSkeletonController(TitleSkeletonService skeletons) {
    this.skeletons = skeletons;
  }

  /**
   * POST {@code {"media":"/abs/path.mkv","title":"Peppa S01E01"}} for the JSON of an empty study
   * document that references that video or CBZ, which must already be in the media index. Optional
   * fields are {@code id}, {@code series}, {@code volume} (comics), {@code sourceLanguage} (fr),
   * {@code translationLanguage} (en), {@code notesLanguage} (en), {@code leftToRight} (comics) and
   * {@code ignoreTrackLanguage}. Returns the canonical document JSON; the caller writes it into a
   * content repository. 400 for a request that cannot make a document, 409 if the document ID is
   * taken. Nothing is stored.
   */
  @PostMapping(value = "/skeleton", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> skeleton(@RequestBody TitleSkeletonRequest request)
      throws IOException {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_JSON)
        .body(skeletons.skeleton(request));
  }
}
