package com.petitesroues.web;

import static com.petitesroues.support.SpecFixtures.newPage;
import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.config.PhoneListener;
import com.petitesroues.documents.StudyDocumentCodec;
import com.petitesroues.media.MediaFingerprint;
import com.petitesroues.model.document.ComicReference;
import com.petitesroues.model.document.ReadingDirection;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.library.ContentRepository;
import com.petitesroues.model.sync.Desktop;
import com.petitesroues.model.sync.DesktopTitle;
import com.petitesroues.model.sync.TitleFile;
import com.petitesroues.phone.PhonePairingService;
import com.petitesroues.support.DocumentFixtures;
import com.petitesroues.support.HttpIntegrationTest;
import com.petitesroues.sync.DesktopClient;
import com.petitesroues.sync.DesktopException;
import com.petitesroues.sync.PathTransferFolder;
import com.petitesroues.sync.PhoneRoutes;
import com.petitesroues.sync.TitleDownloader;
import com.petitesroues.sync.TransferFolder;
import com.petitesroues.sync.TransferPlan;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Copying titles to a phone end to end: the desktop's catalogue and phone listener on one side, the
 * phone's shared client, plan and downloader on the other, into a folder on disk.
 */
@TestPropertySource(properties = {"petitesroues.phone.enabled=true", "petitesroues.phone.port=0"})
class PhoneTransferIntegrationTest extends HttpIntegrationTest {
  @Autowired PhoneListener listener;
  @Autowired PhonePairingService pairing;
  private final DesktopClient client = new DesktopClient();
  private Desktop desktop;

  @BeforeEach
  void pairPhone() throws Exception {
    jdbc.update("DELETE FROM phones");
    jdbc.update("DELETE FROM settings");
    http.put().uri("/api/phone").body(Map.of("enabled", true)).retrieve().toBodilessEntity();
    String url = "https://127.0.0.1:" + listener.port();
    desktop = client.pair(pairing.start("desk", List.of(url)).code(), "Pixel");
  }

  @AfterEach
  void closeListener() {
    listener.setEnabled(false);
  }

  /** A CBZ with one page of random bytes, big enough to cut a download part way through. */
  private static Path cbz(Path file, int bytes, long seed) throws Exception {
    Files.createDirectories(file.getParent());
    byte[] page = new byte[bytes];
    new Random(seed).nextBytes(page);
    try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
      zip.setLevel(0);
      zip.putNextEntry(new ZipEntry("page-001.png"));
      zip.write(page);
      zip.closeEntry();
    }
    return file;
  }

  private static void comic(Path file, String id, String fingerprint) throws Exception {
    StudyDocument comic =
        DocumentFixtures.comic(
            id, id, ReadingDirection.LTR, List.of(newPage("p1", "page-001.png", "1")));
    comic = comic.withComic(body -> body.withReference(new ComicReference(fingerprint, null)));
    Files.createDirectories(file.getParent());
    Files.writeString(file, new StudyDocumentCodec().encode(comic), StandardCharsets.UTF_8);
  }

  private String add(Path folder) {
    return http.post()
        .uri("/api/repositories")
        .body(Map.of("localPath", folder.toString()))
        .retrieve()
        .body(ContentRepository.class)
        .id();
  }

  private static DesktopTitle title(List<DesktopTitle> titles, String id) {
    return titles.stream().filter(title -> title.id().equals(id)).findFirst().orElseThrow();
  }

  @Test
  void aPhoneCopiesTitlesResumesACutAndSkipsWhatItHas() throws Exception {
    Path media = root.resolve("fr"), docs = root.resolve("study");
    Path t01 = cbz(media.resolve("comics/demon/T01.cbz"), 600_000, 1);
    Files.write(media.resolve("comics/demon/poster.jpg"), new byte[] {1, 2, 3});
    Path stray = cbz(media.resolve("comics/stray.cbz"), 1_000, 2);
    comic(docs.resolve("comics/demon/t01/document.json"), "t01", MediaFingerprint.of(t01));
    comic(docs.resolve("comics/lost/document.json"), "lost", "v1:" + "0".repeat(64));
    String mediaId = add(media);
    add(docs);
    String lostId =
        jdbc.queryForObject("SELECT id FROM titles WHERE document_id='lost'", String.class);
    http.put()
        .uri("/api/library/media-links")
        .body(
            Map.of("repositoryId", mediaId, "relativePath", "comics/stray.cbz", "titleId", lostId))
        .retrieve()
        .toBodilessEntity();

    var titles = client.titles(desktop);
    assertNotEquals(DesktopTitle.PAGES_ID, titles.get(0).id(), "no notes or quizzes, no entry");
    var lost = title(titles, lostId);
    assertNotNull(lost.problem(), "a hand link does not reach the phone");
    assertEquals(List.of(), lost.files());
    String t01Id =
        jdbc.queryForObject("SELECT id FROM titles WHERE document_id='t01'", String.class);
    var demon = title(titles, t01Id);
    assertNull(demon.problem());
    assertEquals(
        List.of(TitleFile.MEDIA, TitleFile.POSTER, TitleFile.DOCUMENT),
        demon.files().stream().map(TitleFile::role).toList(),
        "media first, the document last");
    assertEquals("fr/comics/demon/T01.cbz", demon.files().get(0).path());
    assertEquals("study/comics/demon/t01/document.json", demon.files().get(2).path());

    // Cut the CBZ part way through, as a dropped Wi-Fi or a stopped job would.
    Path phone = root.resolve("phone");
    TransferFolder folder = new PathTransferFolder(phone);
    var plan = TransferPlan.of(titles, folder);
    assertEquals(List.of(demon), plan.titles(), "titles with a problem are left out");
    long[] seen = {0};
    assertThrows(
        CancellationException.class,
        () ->
            new TitleDownloader(client)
                .run(
                    desktop,
                    plan,
                    folder,
                    new TitleDownloader.Listener() {
                      @Override
                      public void progress(DesktopTitle title, long done, long total) {
                        seen[0] = done;
                      }

                      @Override
                      public boolean cancelled() {
                        return seen[0] >= 200_000;
                      }
                    }));
    var cbzPath = demon.files().get(0).segments();
    long part = folder.partSize(cbzPath);
    assertTrue(part > 0 && part < Files.size(t01), "the cut leaves a part");
    assertEquals(-1, folder.size(cbzPath));

    var resumed = TransferPlan.of(client.titles(desktop), folder);
    assertEquals(plan.remaining() - part, resumed.remaining(), "the part counts as downloaded");
    long[] fetched = {0};
    List<DesktopTitle> finished = new ArrayList<>();
    new TitleDownloader(client)
        .run(
            desktop,
            resumed,
            folder,
            new TitleDownloader.Listener() {
              @Override
              public void progress(DesktopTitle title, long done, long total) {
                fetched[0] = done;
              }

              @Override
              public void finished(DesktopTitle title) {
                finished.add(title);
              }
            });
    assertEquals(resumed.remaining(), fetched[0], "the desktop sent only the rest (Range)");
    assertEquals(List.of(t01Id), finished.stream().map(DesktopTitle::id).toList());
    assertEquals(
        MediaFingerprint.of(t01), MediaFingerprint.of(phone.resolve("fr/comics/demon/T01.cbz")));
    assertEquals(3, Files.size(phone.resolve("fr/comics/demon/poster.jpg")));
    assertEquals(
        "t01",
        new StudyDocumentCodec()
            .decode(Files.readAllBytes(phone.resolve("study/comics/demon/t01/document.json")))
            .id());
    assertEquals(-1, folder.partSize(cbzPath));

    var again = TransferPlan.of(client.titles(desktop), folder);
    assertEquals(List.of(), again.steps(), "nothing is fetched again while nothing changed");

    // The desktop's document changes (a review lands): only it is offered and fetched again.
    Path document = docs.resolve("comics/demon/t01/document.json");
    var codec = new StudyDocumentCodec();
    var edited = codec.decode(Files.readAllBytes(document));
    Files.writeString(
        document,
        codec.encode(
            edited.withComic(
                body ->
                    body.withReference(new ComicReference(body.reference().fingerprint(), 42L)))),
        StandardCharsets.UTF_8);
    var update = TransferPlan.of(client.titles(desktop), folder);
    assertEquals(
        List.of(TitleFile.DOCUMENT),
        update.steps().stream().map(step -> step.file().role()).toList(),
        "an edited document is fetched again, alone");
    new TitleDownloader(client).run(desktop, update, folder, new TitleDownloader.Listener() {});
    assertEquals(
        Files.readString(document),
        Files.readString(phone.resolve("study/comics/demon/t01/document.json")));
  }

  @Test
  void onlyCatalogueFilesAreServedAndOnlyToPairedPhones() throws Exception {
    var missing =
        assertThrows(
            DesktopException.class,
            () ->
                client.download(
                    desktop,
                    new TitleFile(
                        TitleFile.MEDIA,
                        "x/y.cbz",
                        1,
                        null,
                        PhoneRoutes.titleFile("nope", TitleFile.MEDIA)),
                    new PathTransferFolder(root.resolve("phone")),
                    bytes -> {},
                    () -> false));
    assertEquals(404, missing.status());
    var stranger = new Desktop("desk", desktop.url(), "wrong", desktop.pin());
    assertEquals(401, assertThrows(DesktopException.class, () -> client.titles(stranger)).status());
    assertEquals("/phone/titles/a%20b/media", PhoneRoutes.titleFile("a b", TitleFile.MEDIA));
  }

  @Test
  void notesAndQuizzesComeFirstAndCopyLikeATitle() throws Exception {
    Path docs = root.resolve("study");
    Files.createDirectories(docs.resolve("notes"));
    Files.createDirectories(docs.resolve("quizzes"));
    Files.writeString(docs.resolve("notes/verbs.md"), "# Verbs\n");
    Files.writeString(docs.resolve("quizzes/verbs.md"), "# Verb quiz\n");
    Files.writeString(docs.resolve("notes/draft.txt"), "not a page");
    add(docs);

    var titles = client.titles(desktop);
    var pages = titles.get(0);
    assertEquals(DesktopTitle.PAGES_ID, pages.id());
    assertEquals(
        List.of("study/notes/verbs.md", "study/quizzes/verbs.md"),
        pages.files().stream().map(TitleFile::path).toList());
    var folder = new PathTransferFolder(root.resolve("phone"));
    new TitleDownloader(client)
        .run(
            desktop,
            TransferPlan.of(List.of(pages), folder),
            folder,
            new TitleDownloader.Listener() {});
    assertEquals("# Verb quiz\n", Files.readString(root.resolve("phone/study/quizzes/verbs.md")));
    assertEquals(
        List.of(),
        TransferPlan.of(List.of(client.titles(desktop).get(0)), folder).steps(),
        "copied pages are up to date");

    var outside =
        new TitleFile(
            TitleFile.PAGE,
            "x/notes/draft.txt",
            10,
            null,
            pages.files().get(0).source().replace("verbs.md", "draft.txt"));
    assertEquals(
        404,
        assertThrows(
                DesktopException.class,
                () -> client.download(desktop, outside, folder, bytes -> {}, () -> false))
            .status(),
        "only listed pages are served");
  }
}
