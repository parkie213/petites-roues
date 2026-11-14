package app.petitesroues.support;

import static org.mockito.Mockito.spy;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.documents.StudyDocumentJson;
import app.petitesroues.documents.StudyDocumentReader;
import app.petitesroues.library.AcceptedDocumentBackfill;
import app.petitesroues.library.AcceptedDocuments;
import app.petitesroues.library.ContentRepositoryService;
import app.petitesroues.library.DocumentImportService;
import app.petitesroues.library.RepositoryContentService;
import app.petitesroues.library.discovery.ContentRepositoryScanner;
import app.petitesroues.library.persistence.AcceptedDocumentStore;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.library.persistence.ContentRepositoryStore;
import app.petitesroues.library.persistence.MediaStore;
import app.petitesroues.media.Ffmpeg;
import app.petitesroues.media.MediaProbe;
import app.petitesroues.media.ProcessRunner;
import app.petitesroues.study.DocumentEditingService;
import app.petitesroues.study.DocumentStore;
import app.petitesroues.study.ReviewQueue;
import app.petitesroues.study.ReviewQueueService;
import app.petitesroues.study.ServedDocuments;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The database and the object graph of one plain-Java service test: a SQLite file migrated by
 * Flyway and every store and service built over it, in the order production wiring implies.
 *
 * <p>Each instance owns its own database (a single connection, foreign keys on, as production
 * configures it) and its own collaborators; nothing is shared between tests. The services are the
 * real classes wired with the same collaborators production gives them, so a constructor change is
 * absorbed here instead of in every test. Use {@link #builder(Path)}, read the public fields, and
 * call {@link #close()} after the test. Test-only: there is no Spring context, and the media probe
 * runs real {@code ffprobe} on request but nothing here touches an installed application's data.
 */
public final class StudyTestServices implements AutoCloseable {
  public final SingleConnectionDataSource dataSource;
  public final JdbcTemplate jdbc;
  public final TransactionTemplate transactions;
  public final StudyDocumentCodec codec;
  public final JsonMapper mapper;
  public final CatalogueStore titles;
  public final ContentRepositoryStore repositories;
  public final MediaStore media;
  public final AcceptedDocumentStore accepted;
  public final DocumentStore documents;

  /** The review queue; a Mockito spy when {@link Builder#spyQueue()} was requested. */
  public final ReviewQueue queue;

  public final ServedDocuments served;
  public final RepositoryContentService registration;
  public final AcceptedDocuments acceptedDocuments;
  public final DocumentImportService imports;
  public final AcceptedDocumentBackfill backfill;
  public final ReviewQueueService review;
  public final DocumentEditingService editing;

  /** Repository Add/Refresh/Remove over the real scanner and probe, using {@link #registration}. */
  public final ContentRepositoryService contentRepositories;

  private final ContentRepositoryScanner scanner = new ContentRepositoryScanner();
  public final StudyDocumentReader reader;
  private final MediaProbe probe;
  private final ExecutorService processExecutor = Executors.newVirtualThreadPerTaskExecutor();

  private StudyTestServices(Builder options) {
    dataSource =
        new SingleConnectionDataSource(
            "jdbc:sqlite:" + options.directory.resolve("db.sqlite"), true);
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("PRAGMA foreign_keys=ON");
    var flyway = Flyway.configure().dataSource(dataSource);
    if (options.schemaVersion != null) flyway.target(options.schemaVersion);
    flyway.load().migrate();
    transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    codec = new StudyDocumentCodec();
    mapper = StudyDocumentJson.newMapper();
    titles = new CatalogueStore(jdbc);
    repositories = new ContentRepositoryStore(jdbc);
    media = new MediaStore(jdbc);
    accepted = new AcceptedDocumentStore(jdbc);
    reader = new StudyDocumentReader(codec);
    documents = new DocumentStore(reader);
    var plainQueue = new ReviewQueue(jdbc, mapper);
    queue = options.spyQueue ? spy(plainQueue) : plainQueue;
    probe =
        new MediaProbe(
            new Ffmpeg(mapper, new ProcessRunner(processExecutor, Duration.ofSeconds(30))));
    served = servedOver(documents);
    registration = new RepositoryContentService(repositories, titles);
    acceptedDocuments = acceptedOver(served, registration);
    imports = new DocumentImportService(titles, documents, acceptedDocuments, transactions);
    backfill = new AcceptedDocumentBackfill(accepted, imports);
    review =
        new ReviewQueueService(
            titles, documents, accepted, imports, acceptedDocuments, queue, served, transactions);
    editing = new DocumentEditingService(titles, served, queue, transactions);
    contentRepositories = contentRepositories(registration);
  }

  /** Starts a fixture whose database lives in {@code directory} (a JUnit {@code @TempDir}). */
  public static Builder builder(Path directory) {
    return new Builder(directory);
  }

  /** A fixture with every option at its default. */
  public static StudyTestServices create(Path directory) {
    return builder(directory).build();
  }

  /** Served copies computed over {@code documents}, for tests that substitute the file reader. */
  public ServedDocuments servedOver(DocumentStore documents) {
    return new ServedDocuments(accepted, queue, documents, codec);
  }

  /**
   * An import service reading files through {@code documents}, with its own served copies over the
   * same database, for tests that intercept the file read.
   */
  public DocumentImportService importsOver(DocumentStore documents) {
    return new DocumentImportService(
        titles, documents, acceptedOver(servedOver(documents), registration), transactions);
  }

  /**
   * A repository service (and the accepted-copy rules it uses) registering through {@code
   * registration}, for tests that spy on or fail registration.
   */
  public ContentRepositoryService contentRepositories(RepositoryContentService registration) {
    return new ContentRepositoryService(
        repositories,
        scanner,
        reader,
        transactions,
        registration,
        queue,
        acceptedOver(served, registration),
        media,
        probe);
  }

  private AcceptedDocuments acceptedOver(
      ServedDocuments served, RepositoryContentService registration) {
    return new AcceptedDocuments(accepted, queue, served, registration);
  }

  /** Closes the database connection and the probe's executor. */
  @Override
  public void close() {
    dataSource.destroy();
    processExecutor.close();
  }

  /** Options for one fixture; defaults give the latest schema and an unwrapped queue. */
  public static final class Builder {
    private final Path directory;
    private boolean spyQueue;
    private String schemaVersion;

    private Builder(Path directory) {
      this.directory = directory;
    }

    /**
     * Wraps the review queue in a Mockito spy before any dependant is constructed, so tests can
     * stub or verify it while every service still uses it.
     */
    public Builder spyQueue() {
      spyQueue = true;
      return this;
    }

    /** Migrates only up to {@code version}, to test upgrades from an older schema. */
    public Builder schemaVersion(String version) {
      schemaVersion = version;
      return this;
    }

    public StudyTestServices build() {
      return new StudyTestServices(this);
    }
  }
}
