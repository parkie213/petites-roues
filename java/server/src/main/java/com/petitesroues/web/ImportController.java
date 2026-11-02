package com.petitesroues.web;

import com.petitesroues.library.TitleImportService;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Creates titles from a video or CBZ on this computer. POST {@code /api/import/inspect} with {@code
 * {"path":...}} to list a video's audio tracks or a comic's page count, then POST {@code
 * /api/import} with a {@link TitleImportService.Create}. That answers 202 with {@code {"job":...}}
 * as soon as the request is checked, because converting a long video's audio takes minutes; poll
 * GET {@code /api/import/{job}} for its {@link TitleImportService.Job} until it is no longer
 * RUNNING.
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {
  private final TitleImportService imports;

  public ImportController(TitleImportService imports) {
    this.imports = imports;
  }

  @PostMapping("/inspect")
  public TitleImportService.Inspection inspect(@RequestBody Map<String, String> body)
      throws IOException {
    return imports.inspect(body == null ? null : body.get("path"));
  }

  /**
   * The ID of a started import.
   *
   * @param job pass to GET {@code /api/import/{job}}
   */
  public record Started(String job) {}

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Started start(@RequestBody TitleImportService.Create request) throws IOException {
    return new Started(imports.start(request));
  }

  @GetMapping("/{job}")
  public TitleImportService.Job job(@PathVariable String job) {
    return imports.job(job);
  }
}
