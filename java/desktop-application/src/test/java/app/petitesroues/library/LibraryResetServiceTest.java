package app.petitesroues.library;

import static app.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.library.progress.LibraryProgress;
import app.petitesroues.support.StudyTestServices;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Resets a real SQLite library built from real document files. */
class LibraryResetServiceTest {
  /** Tables a reset deliberately keeps. */
  private static final Set<String> KEPT =
      Set.of("flyway_schema_history", "repositories", "settings");

  @TempDir Path root;
  StudyTestServices services;

  @BeforeEach
  void setup() throws IOException {
    services = StudyTestServices.create(root);
    write("a");
    write("b");
    services.contentRepositories.add(new ContentRepositoryService.Add(root.toString(), "Test"));
  }

  @AfterEach
  void close() {
    services.close();
  }

  private Path write(String id) throws IOException {
    Path file = root.resolve(id).resolve("document.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, services.codec.encode(document(id)));
    return file;
  }

  /** Gives every table that a reset deletes at least one row. */
  private void populate() {
    var jdbc = services.jdbc;
    String repository = jdbc.queryForObject("SELECT id FROM repositories", String.class);
    jdbc.update(
        "INSERT INTO review_batches(id,title_id,base_sha256,created_at,completed_at)"
            + " VALUES(1,'a','x','t','t')");
    jdbc.update(
        "INSERT INTO review_queue(title_id,timestamp,kind,cue_ids_json,reviewed_at,outcome,batch_id)"
            + " VALUES('a','t','edit','[]','t','applied',1)");
    jdbc.update("INSERT INTO playback_progress VALUES('a',4.5,'t')");
    jdbc.update("INSERT INTO reading_progress VALUES('b','page-1','t',0.5)");
    jdbc.update(
        "INSERT INTO media(repository_id,relative_path,kind,size_bytes,modified_millis,fingerprint)"
            + " VALUES(?,'v.mp4','video',1,1,'f')",
        repository);
    jdbc.update("INSERT INTO media_links VALUES(?,'v.mp4','a','t')", repository);
    jdbc.update("INSERT INTO phones(id,name,token_sha256,paired_at) VALUES('p','Phone','h','t')");
    jdbc.update("INSERT INTO settings VALUES('phone.key','secret')");
  }

  private List<String> tables() {
    return services.jdbc.queryForList(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
        String.class);
  }

  private Map<String, Integer> counts() {
    Map<String, Integer> counts = new TreeMap<>();
    for (String table : tables())
      counts.put(
          table, services.jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class));
    return counts;
  }

  private List<Map<String, Object>> rows(String table) {
    return services.jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1");
  }

  @Test
  void afterAResetEveryTableExceptTheKeptOnesIsEmptyBeforeTheRebuild() throws Exception {
    populate();
    var before = counts();
    for (String table : tables())
      if (!KEPT.contains(table)) assertTrue(before.get(table) > 0, table + " was not populated");
    // Stop the rebuild: with no folder to read, refresh fails after the wipe committed.
    services.jdbc.update("UPDATE repositories SET local_path=?", root.resolve("gone").toString());
    assertThrows(
        IllegalArgumentException.class, () -> services.reset.reset(LibraryProgress.Listener.NONE));
    var after = counts();
    for (String table : tables())
      if (!KEPT.contains(table)) assertEquals(0, after.get(table), table + " was not wiped");
    assertEquals(before.get("repositories"), after.get("repositories"));
    assertEquals(before.get("settings"), after.get("settings"));
    assertEquals(before.get("flyway_schema_history"), after.get("flyway_schema_history"));
  }

  @Test
  void aPendingEditRefusesTheResetAndChangesNothing() throws Exception {
    populate();
    services.jdbc.update(
        "INSERT INTO review_queue(title_id,timestamp,kind,cue_ids_json) VALUES('a','t','edit','[]')");
    var before = counts();
    var failure =
        assertThrows(
            DocumentConflictException.class,
            () -> services.reset.reset(LibraryProgress.Listener.NONE));
    assertEquals("1 edit is pending: process or discard them first", failure.getMessage());
    assertEquals(before, counts());
  }

  @Test
  void anOpenBatchRefusesTheResetAndChangesNothing() throws Exception {
    populate();
    services.jdbc.update("UPDATE review_batches SET completed_at=NULL");
    var before = counts();
    assertThrows(
        DocumentConflictException.class, () -> services.reset.reset(LibraryProgress.Listener.NONE));
    assertEquals(before, counts());
  }

  @Test
  void resetRebuildsTheLibraryFromTheFoldersAndKeepsFoldersAndSettings() throws Exception {
    populate();
    var repositories = rows("repositories");
    var settings = rows("settings");
    var phases = new ArrayList<String>();
    services.reset.reset(progress -> phases.add(progress.phase()));
    assertFalse(phases.isEmpty());
    assertEquals(
        repositories.stream().map(this::ignoreRefreshTime).toList(),
        rows("repositories").stream().map(this::ignoreRefreshTime).toList());
    assertEquals(settings, rows("settings"));
    assertEquals(0, counts().get("phones"));
    assertEquals(0, counts().get("playback_progress"));
    assertEquals(0, counts().get("reading_progress"));
    assertEquals(0, counts().get("review_queue"));
    assertEquals(0, counts().get("review_batches"));
    assertEquals(0, counts().get("media_links"));
    assertEquals(
        List.of("a", "b"), services.titles.all().stream().map(t -> t.id()).sorted().toList());
    for (var title : services.titles.all()) {
      var accepted = services.accepted.find(title.id()).orElseThrow();
      assertArrayEquals(Files.readAllBytes(root.resolve(title.documentPath())), accepted.json());
      assertArrayEquals(
          accepted.json(),
          services.accepted.findServed(title.id(), accepted.sha256()).orElseThrow());
      assertArrayEquals(accepted.json(), services.review.servedBytes(title));
    }
  }

  private Map<String, Object> ignoreRefreshTime(Map<String, Object> row) {
    var copy = new TreeMap<>(row);
    copy.remove("last_refreshed_at");
    return copy;
  }

  @Test
  void aFailedRefreshAfterTheWipeLeavesTheFoldersRegisteredAndALaterRefreshFinishes()
      throws Exception {
    populate();
    String path = services.jdbc.queryForObject("SELECT local_path FROM repositories", String.class);
    services.jdbc.update("UPDATE repositories SET local_path=?", root.resolve("gone").toString());
    assertThrows(
        IllegalArgumentException.class, () -> services.reset.reset(LibraryProgress.Listener.NONE));
    assertEquals(1, counts().get("repositories"));
    assertEquals(0, counts().get("titles"));
    services.jdbc.update("UPDATE repositories SET local_path=?", path);
    services.contentRepositories.refreshAll();
    assertEquals(2, counts().get("titles"));
    assertEquals(2, counts().get("served_documents"));
  }
}
