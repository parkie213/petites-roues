package app.petitesroues.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import app.petitesroues.sync.SyncStatus.State;
import app.petitesroues.sync.model.DesktopTitle;
import app.petitesroues.sync.model.TitleFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyncStatusTest {
  @TempDir Path phone;

  private static DesktopTitle episode(String id, String series, String problem, String document) {
    return new DesktopTitle(
        id,
        id,
        series,
        "video",
        1,
        null,
        null,
        problem,
        List.of(
            new TitleFile(TitleFile.SERIES_POSTER, "repo/" + series + "/poster.jpg", 3, null, "/p"),
            new TitleFile(TitleFile.MEDIA, "repo/" + id + ".mp4", 10, null, "/m"),
            new TitleFile(
                TitleFile.DOCUMENT,
                "repo/" + id + "/document.json",
                document.length(),
                FileFingerprints.sha256(document.getBytes(StandardCharsets.UTF_8)),
                "/d")));
  }

  private void put(String path, String content) throws IOException {
    Path file = phone.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  @Test
  void syncsUpdatesEverywhereAndNewTitlesOnlyInFollowedGroups() throws IOException {
    put("repo/peppa/poster.jpg", "abc");
    put("repo/peppa-1.mp4", "0123456789");
    put("repo/peppa-1/document.json", "{}");
    put("repo/masha-1.mp4", "0123456789");
    put("repo/masha-1/document.json", "{old}");
    var titles =
        List.of(
            episode("peppa-1", "peppa", null, "{}"),
            episode("peppa-2", "peppa", null, "{}"),
            episode("masha-1", "masha", null, "{new}"),
            episode("masha-2", "masha", null, "{}"),
            episode("masha-3", "masha", "No media", "{}"),
            episode("film", null, null, "{}"));

    SyncStatus status =
        SyncStatus.of(titles, Set.of("peppa"), Set.of(), new PathTransferFolder(phone));

    var groups = status.groups();
    assertEquals(
        List.of("peppa", "masha", "film"), groups.stream().map(SyncStatus.Group::key).toList());
    assertEquals(
        List.of(State.UP_TO_DATE, State.MISSING),
        groups.get(0).entries().stream().map(SyncStatus.Entry::state).toList());
    assertEquals(
        List.of(State.UPDATE, State.MISSING, State.PROBLEM),
        groups.get(1).entries().stream().map(SyncStatus.Entry::state).toList());
    // Peppa is followed, so its new episode comes; Masha's update comes though it is not followed.
    assertEquals(
        List.of("peppa-2", "masha-1"), status.toSync().stream().map(DesktopTitle::id).toList());
    // peppa-2 whole (10 + 2); masha-1 its new document (5) and the series poster it lacks (3).
    assertEquals(12 + 5 + 3, status.remaining());
  }

  @Test
  void aChosenTitleIsCopiedWithoutFollowingItsSeries() throws IOException {
    var titles =
        List.of(episode("masha-1", "masha", null, "{}"), episode("masha-2", "masha", null, "{}"));
    var folder = new PathTransferFolder(phone);

    SyncStatus status = SyncStatus.of(titles, Set.of(), Set.of("masha-2"), folder);
    assertEquals(List.of("masha-2"), status.toSync().stream().map(DesktopTitle::id).toList());
    assertEquals(Set.of("masha-2"), status.stillChosen());

    // Once it is on the phone it is fetched as an update, so it no longer needs choosing.
    put("repo/masha-2.mp4", "0123456789");
    assertEquals(
        Set.of(), SyncStatus.of(titles, Set.of(), Set.of("masha-2"), folder).stillChosen());
  }

  @Test
  void aGroupWhoseTitlesAllHaveProblemsCannotBeFollowed() throws IOException {
    var status =
        SyncStatus.of(
            List.of(episode("a", "s", "No media", "{}")),
            Set.of(),
            Set.of(),
            new PathTransferFolder(phone));
    assertFalse(status.groups().getFirst().followable());
  }
}
