package app.petitesroues.web;

import app.petitesroues.library.ContentRepositoryService;
import app.petitesroues.library.SystemFileDialog;
import app.petitesroues.model.library.ContentRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * Exposes content-root registration, listing, refresh and removal, plus the system file dialog used
 * to choose repository folders and files to import. Add and Refresh delegate recursive discovery
 * and index updates to {@link ContentRepositoryService}; ordinary listing reads the index without
 * discovery.
 *
 * <p>Add, Refresh and Remove are serialized with player edits and review-queue operations by the
 * shared library update monitor, which {@link ContentRepositoryService} acquires itself, so a title
 * cannot be replaced or unregistered during an edit's read/change/save operation. Removing a
 * registration leaves content files on disk.
 */
@RestController
@RequestMapping("/api")
public class RepositoriesController {
  private static final List<String> MEDIA =
      List.of("mp4", "mkv", "webm", "mov", "m4v", "avi", "ogv", "cbz");

  private final ContentRepositoryService repositories;
  private final SystemFileDialog dialog;

  public RepositoriesController(ContentRepositoryService repositories, SystemFileDialog dialog) {
    this.repositories = repositories;
    this.dialog = dialog;
  }

  @GetMapping("/repositories")
  public List<ContentRepository> list() {
    return repositories.list();
  }

  @PostMapping("/repositories")
  public ContentRepository add(@RequestBody ContentRepositoryService.Add request)
      throws IOException {
    return repositories.add(request);
  }

  /** Refreshes every registered folder: the library's one Refresh button. */
  @PostMapping("/repositories/refresh")
  public List<ContentRepository> refreshAll() throws IOException {
    return repositories.refreshAll();
  }

  @PostMapping("/repositories/{id}/refresh")
  public ContentRepository refresh(@PathVariable String id) throws IOException {
    return repositories.refresh(id);
  }

  @DeleteMapping("/repositories/{id}")
  public Map<String, Boolean> remove(@PathVariable String id) {
    repositories.remove(id);
    return Map.of("removed", true);
  }

  /**
   * The chosen path, or {@code null} when the person cancelled the dialog.
   *
   * @param path absolute path on this computer
   */
  public record Chosen(String path) {}

  /**
   * POST {@code {"kind":"folder"}} or {@code {"kind":"media"}} to open this computer's own file
   * dialog for a repository folder or a video/CBZ to import. Waits until the person chooses or
   * cancels.
   */
  @PostMapping("/files/choose")
  public Chosen choose(@RequestBody Map<String, String> request) throws IOException {
    var chosen =
        "media".equals(request.get("kind"))
            ? dialog.chooseFile("Choose a video or comic to add", MEDIA)
            : dialog.chooseFolder("Choose a content repository folder");
    return new Chosen(chosen.map(Path::toString).orElse(null));
  }
}
