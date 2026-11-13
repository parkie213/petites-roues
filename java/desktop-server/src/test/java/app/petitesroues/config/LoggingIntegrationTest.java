package app.petitesroues.config;

import static app.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.model.editing.DocumentEdit;
import app.petitesroues.model.review.ReviewCompletion;
import app.petitesroues.study.ReviewQueue;
import app.petitesroues.study.ReviewQueueService;
import app.petitesroues.support.HttpIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Exercises the real Boot console binding, including application System.Logger calls. */
@ExtendWith(OutputCaptureExtension.class)
class LoggingIntegrationTest extends HttpIntegrationTest {
  @Autowired ReviewQueueService reviews;
  @Autowired ReviewQueue queue;
  @Autowired CatalogueStore titles;
  @Autowired FlywayMigrationStrategy migrations;

  @Test
  void replayConflictIncludesBatchAndSequenceWithoutCommandText(CapturedOutput output)
      throws Exception {
    registerTitle(document("logging-conflict"));
    var title = titles.find("logging-conflict").orElseThrow();
    String secret = "private cue text";
    queue.append(title, new DocumentEdit.DeleteVideoCue(secret));
    var batch = reviews.claim(title.id(), 10).orElseThrow();
    var change = batch.changes().getFirst();
    assertEquals("conflict", change.state());
    assertTrue(
        output
            .getOut()
            .contains(
                "operation=review.replay title=logging-conflict batch="
                    + batch.batch()
                    + " sequence="
                    + change.sequence()));
    assertFalse(output.getAll().contains(secret));
  }

  @Test
  void reviewFailureIncludesIdentifiersWithoutExceptionBody(CapturedOutput output) {
    String secret = "private document body";
    assertThrows(
        IllegalArgumentException.class,
        () -> reviews.complete("missing-title", 1234, new ReviewCompletion(secret, null)));
    assertTrue(
        output.getOut().contains("operation=review.complete title=missing-title batch=1234"));
    assertTrue(output.getOut().contains("failure=java.lang.IllegalArgumentException"));
    assertFalse(output.getAll().contains(secret));
  }

  @Test
  void migrationFailureIsLoggedAndStillStopsStartup(CapturedOutput output) {
    var flyway = mock(Flyway.class);
    var failure = new IllegalStateException("private SQL or document body");
    when(flyway.migrate()).thenThrow(failure);
    assertSame(
        failure, assertThrows(IllegalStateException.class, () -> migrations.migrate(flyway)));
    assertTrue(
        output
            .getOut()
            .contains("operation=catalogue.migrate failure=java.lang.IllegalStateException"));
    assertFalse(output.getAll().contains(failure.getMessage()));
  }
}
