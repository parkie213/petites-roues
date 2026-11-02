package com.petitesroues.web;

import com.petitesroues.library.ContentRepositoryService;
import com.petitesroues.library.LibraryUpdateService;
import com.petitesroues.library.SystemFileDialog;
import com.petitesroues.model.library.ContentRepository;
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
 * <p>Mutations share the {@link LibraryUpdateService} monitor with player edits so a title cannot
 * be replaced or unregistered during an edit's read/change/save operation. Removing a registration
 * leaves content files on disk.
 */
@RestController
@RequestMapping("/api")
public class RepositoriesController {
  private static final List<String> MEDIA =
      List.of("mp4", "mkv", "webm", "mov", "m4v", "avi", "ogv", "cbz");

  private final ContentRepositoryService repositories;
  private final LibraryUpdateService updates;
  private final SystemFileDialog dialog;

  public RepositoriesController(
      ContentRepositoryService repositories,
      LibraryUpdateService updates,
      SystemFileDialog dialog) {
    this.repositories = repositories;
    this.updates = updates;
    this.dialog = dialog;
  }

  @GetMapping("/repositories")
  public List<ContentRepository> list() {
    return repositories.list();
  }

  @PostMapping("/repositories")
  public ContentRepository add(@RequestBody ContentRepositoryService.Add request)
      throws IOException {
    synchronized (updates) {
      return repositories.add(request);
    }
  }

  @PostMapping("/repositories/{id}/refresh")
  public ContentRepository refresh(@PathVariable String id) throws IOException {
    synchronized (updates) {
      return repositories.refresh(id);
    }
  }

  @DeleteMapping("/repositories/{id}")
  public Map<String, Boolean> remove(@PathVariable String id) {
    synchronized (updates) {
      repositories.remove(id);
      return Map.of("removed", true);
    }
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
