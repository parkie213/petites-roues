package app.petitesroues.mcp;

import static app.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.config.PhoneListener;
import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.phone.PhoneCertificateService;
import app.petitesroues.support.HttpClients;
import app.petitesroues.support.HttpIntegrationTest;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.JsonNode;

/** Real SDK calls over Streamable HTTP, with independent REST and SQLite persistence checks. */
@ActiveProfiles("mcp")
@TestPropertySource(properties = "petitesroues.phone.port=0")
class ReviewMcpIntegrationTest extends HttpIntegrationTest {
  @Autowired PhoneListener phones;
  @Autowired PhoneCertificateService certificates;

  private McpSyncClient client() {
    var transport =
        HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
            .endpoint("/mcp")
            .build();
    return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
  }

  private JsonNode success(CallToolResult result) {
    assertFalse(Boolean.TRUE.equals(result.isError()), () -> result.content().toString());
    var structured = mapper.valueToTree(result.structuredContent());
    assertEquals(structured, mapper.readTree(((TextContent) result.content().getFirst()).text()));
    return structured;
  }

  private long batchCount() {
    return jdbc.queryForObject("SELECT count(*) FROM review_batches", Long.class);
  }

  @Test
  void discoversToolsClaimsPersistentlyAndRestCanFinishTheSameBatch() throws Exception {
    registerTitle(document("mcp-review"));
    var file = documentPath("mcp-review");
    byte[] original = Files.readAllBytes(file);
    String api = "/api/titles/mcp-review";
    var queued =
        http.post()
            .uri(api + "/review/requests")
            .body(
                List.of(Map.of("cueId", "stable-cue", "text", "Check the first-pass translation")))
            .retrieve()
            .body(JsonNode.class);
    assertEquals(1, queued.get("queued").asInt());
    assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM review_queue", Long.class));

    try (var client = client()) {
      var initialized = client.initialize();
      assertEquals("petites-roues-review-trial", initialized.serverInfo().name());
      assertNull(initialized.capabilities().resources());
      assertNull(initialized.capabilities().prompts());
      var tools = client.listTools().tools();
      assertEquals(
          Set.of("list_pending_reviews", "claim_review_batch"),
          tools.stream().map(tool -> tool.name()).collect(Collectors.toSet()));
      var claimTool =
          tools.stream()
              .filter(t -> t.name().equals("claim_review_batch"))
              .findFirst()
              .orElseThrow();
      assertEquals(List.of("titleId"), claimTool.inputSchema().get("required"));
      assertFalse(claimTool.annotations().readOnlyHint());
      assertTrue(
          tools.stream()
              .filter(t -> t.name().equals("list_pending_reviews"))
              .findFirst()
              .orElseThrow()
              .annotations()
              .readOnlyHint());

      var pending = success(client.callTool(new CallToolRequest("list_pending_reviews", Map.of())));
      assertEquals("mcp-review", pending.path("titles").get(0).path("titleId").asString());
      assertEquals(1, pending.path("titles").get(0).path("pending").asInt());
      assertEquals(0, batchCount());

      var claimed =
          success(
              client.callTool(
                  new CallToolRequest("claim_review_batch", Map.of("titleId", "mcp-review"))));
      assertEquals("claimed", claimed.path("status").asString());
      var batch = claimed.path("batch");
      assertEquals(1, batch.path("changes").size());
      assertEquals("review-request", batch.path("changes").get(0).path("kind").asString());
      assertEquals(
          "Check the first-pass translation",
          batch.path("changes").get(0).path("suggestion").asString());
      var decoded =
          new StudyDocumentCodec()
              .decode(batch.path("document").toString().getBytes(StandardCharsets.UTF_8));
      assertEquals("mcp-review", decoded.id());
      assertTrue(batch.path("document").has("video"));
      assertFalse(batch.path("document").has("body"));
      assertEquals(1, batchCount());
      assertEquals(
          batch.path("batch").asLong(),
          jdbc.queryForObject("SELECT batch_id FROM review_queue", Long.class));
      assertArrayEquals(original, Files.readAllBytes(file));

      // The existing REST operation sees exactly the persisted claim, not a separate MCP queue.
      assertEquals(batch, http.post().uri(api + "/review/batches").retrieve().body(JsonNode.class));
      var repeated =
          success(
              client.callTool(
                  new CallToolRequest(
                      "claim_review_batch", Map.of("titleId", "mcp-review", "limit", 1))));
      assertEquals(batch, repeated.path("batch"));
      assertEquals(1, batchCount());

      // A review request changes no document text, so reviewed can complete without a file write.
      http.post()
          .uri(api + "/review/batches/" + batch.path("batch").asLong() + "/complete")
          .body(Map.of("outcome", "reviewed"))
          .retrieve()
          .toBodilessEntity();
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT count(*) FROM review_queue WHERE reviewed_at IS NULL", Integer.class));
      assertNotNull(jdbc.queryForObject("SELECT completed_at FROM review_batches", String.class));
      assertArrayEquals(original, Files.readAllBytes(file));
      assertEquals(decoded, http.get().uri(api).retrieve().body(StudyDocument.class));
      assertTrue(
          success(client.callTool(new CallToolRequest("list_pending_reviews", Map.of())))
              .path("titles")
              .isEmpty());
      assertEquals(
          "empty",
          success(
                  client.callTool(
                      new CallToolRequest("claim_review_batch", Map.of("titleId", "mcp-review"))))
              .path("status")
              .asString());
    }
  }

  @Test
  void domainErrorsDoNotCreateBatches() throws Exception {
    registerTitle(document("mcp-invalid"));
    try (var client = client()) {
      client.initialize();
      for (var args :
          List.of(
              Map.<String, Object>of("titleId", "missing"),
              Map.<String, Object>of("titleId", "mcp-invalid", "limit", 0))) {
        var result = client.callTool(new CallToolRequest("claim_review_batch", args));
        assertTrue(result.isError());
        assertFalse(((TextContent) result.content().getFirst()).text().isBlank());
        assertEquals(0, batchCount());
      }
    }
  }

  @Test
  void rejectsCrossOriginRequestsAndDoesNotExposeMcpOnThePhoneListener() {
    for (var method : List.of(HttpMethod.GET, HttpMethod.POST)) {
      var rejected =
          assertThrows(
              HttpClientErrorException.Forbidden.class,
              () ->
                  http.method(method)
                      .uri("/mcp")
                      .header("Origin", "https://example.org")
                      .retrieve()
                      .toBodilessEntity());
      assertEquals(403, rejected.getStatusCode().value());
    }
    assertThrows(
        HttpClientErrorException.Forbidden.class,
        () ->
            http.get()
                .uri("/mcp")
                .header("Sec-Fetch-Site", "cross-site")
                .retrieve()
                .toBodilessEntity());
    try {
      phones.setEnabled(true);
      var phone =
          HttpClients.phone("https://127.0.0.1:" + phones.port(), certificates.certificate().pin());
      assertThrows(
          HttpClientErrorException.NotFound.class,
          () -> phone.post().uri("/mcp").body(Map.of()).retrieve().toBodilessEntity());
    } finally {
      phones.setEnabled(false);
    }
  }
}
