package com.petitesroues.support;

import static com.petitesroues.support.HttpClients.client;

import com.petitesroues.documents.StudyDocumentCodec;
import com.petitesroues.model.document.StudyDocument;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.web.client.RestClient;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import tools.jackson.databind.ObjectMapper;

/** Shared HTTP test setup with real services, SQLite and isolated per-test content folders. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(HttpIntegrationTest.InMemoryDatabase.class)
public abstract class HttpIntegrationTest {
  @TempDir protected Path root;
  @Autowired protected ObjectMapper mapper;
  @Autowired protected JdbcTemplate jdbc;
  @LocalServerPort protected int port;
  protected RestClient http;

  @TestConfiguration(proxyBeanMethods = false)
  public static class InMemoryDatabase {
    @Bean(destroyMethod = "destroy")
    @Primary
    SingleConnectionDataSource inMemoryDataSource() throws java.sql.SQLException {
      var config = new SQLiteConfig();
      config.enforceForeignKeys(true);
      var source = new SQLiteDataSource(config);
      source.setUrl("jdbc:sqlite::memory:");
      return new SingleConnectionDataSource(source.getConnection(), true);
    }
  }

  @BeforeEach
  void resetHttpFixture() throws Exception {
    jdbc.update("DELETE FROM titles");
    jdbc.update("DELETE FROM repositories");
    try (var media = getClass().getResourceAsStream("/media/aac.mp4")) {
      Files.copy(media, root.resolve("video.mp4"));
    }
    http = client("http://127.0.0.1:" + port);
  }

  protected void registerTitle(StudyDocument title) throws Exception {
    registerTitle(title, false);
  }

  protected Path documentPath(String titleId) {
    return Path.of(
            jdbc.queryForObject(
                "SELECT r.local_path FROM repositories r JOIN titles t ON t.repository_id=r.id WHERE t.id=?",
                String.class,
                titleId))
        .resolve(
            jdbc.queryForObject(
                "SELECT document_path FROM titles WHERE id=?", String.class, titleId));
  }

  protected void registerTitle(StudyDocument title, boolean replace) throws Exception {
    Path folder = root.resolve("library").resolve(title.id());
    Files.createDirectories(folder);
    Files.write(
        folder.resolve("document.json"),
        new StudyDocumentCodec().encode(title).getBytes(StandardCharsets.UTF_8));
    Files.copy(
        root.resolve("video.mp4"),
        folder.resolve("video.mp4"),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    if (replace) {
      String repositoryId =
          jdbc.queryForObject(
              "SELECT repository_id FROM titles WHERE id=?", String.class, title.id());
      http.post()
          .uri("/api/repositories/" + repositoryId + "/refresh")
          .retrieve()
          .toBodilessEntity();
    } else {
      http.post()
          .uri("/api/repositories")
          .body(Map.of("localPath", folder.toString()))
          .retrieve()
          .toBodilessEntity();
    }
  }
}
