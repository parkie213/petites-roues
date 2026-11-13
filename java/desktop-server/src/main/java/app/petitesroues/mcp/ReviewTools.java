package app.petitesroues.mcp;

import app.petitesroues.library.DocumentConflictException;
import app.petitesroues.study.ReviewQueueService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in agent adapter for review discovery and claims. The application service owns transactions
 * and persistence; this adapter never writes documents or runs Git. Results use the HTTP mapper's
 * document wire adapter so a claimed document has the same format over REST and MCP.
 */
@Component
@Profile("mcp")
public class ReviewTools {
  private static final Logger LOG = LoggerFactory.getLogger(ReviewTools.class);
  private final ReviewQueueService review;
  private final ObjectMapper mapper;

  public ReviewTools(ReviewQueueService review, ObjectMapper mapper) {
    this.review = review;
    this.mapper = mapper;
  }

  /** Lists queue summaries without claiming work or loading document files. */
  @McpTool(
      name = "list_pending_reviews",
      description =
          "List titles awaiting study-document review, oldest work first. Returns titles"
              + " with titleId, title, pending count and openBatch. Does not claim or change work.",
      annotations =
          @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
  public CallToolResult pending() {
    return result(Map.of("titles", review.pendingTitles()));
  }

  /** Claims a durable batch or returns its existing claim; subsequent edits stay outside it. */
  @McpTool(
      name = "claim_review_batch",
      description =
          "Claim the oldest pending edits for a local titleId, or return its existing open"
              + " batch. This writes review-queue state. limit defaults to 50 and must be positive."
              + " Returns status empty, or status claimed with batch {batch, baseSha256, changes, document}."
              + " Read agents/workflows/review-content.md before editing. The agent validates the candidate,"
              + " writes the registered file, commits and pushes, then completes using the REST review API."
              + " This tool never writes files, commits, imports or completes a batch.",
      annotations =
          @McpAnnotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false))
  public CallToolResult claim(
      @McpToolParam(description = "Local catalogue title ID", required = true) String titleId,
      @McpToolParam(description = "Maximum pending edits to claim; default 50", required = false)
          Integer limit) {
    if (titleId == null || titleId.isBlank()) return error("titleId is required");
    try {
      var batch = review.claim(titleId, limit == null ? 50 : limit);
      return batch
          .map(value -> result(Map.of("status", "claimed", "batch", value)))
          .orElseGet(() -> result(Map.of("status", "empty")));
    } catch (IllegalArgumentException
        | NoSuchElementException
        | DocumentConflictException failure) {
      return error(failure.getMessage());
    } catch (IOException failure) {
      LOG.error("MCP review claim failed for title {}", titleId, failure);
      return error("Review claim failed; the server log has the details");
    }
  }

  private CallToolResult result(Object value) {
    Object wire = mapper.convertValue(value, Object.class);
    return CallToolResult.builder()
        .structuredContent(wire)
        .addTextContent(mapper.writeValueAsString(wire))
        .isError(false)
        .build();
  }

  private static CallToolResult error(String message) {
    return CallToolResult.builder().addTextContent(message).isError(true).build();
  }
}
