# MCP review trial

An optional agent interface over the existing review services. REST remains available
to preparation scripts, the browser and agents; there is one queue and one set of
transactions. This trial uses Spring AI 2.0.2's MCP WebMVC starter on the application's
existing Boot 4.0.8 baseline. It does not configure or call an AI model.

## Start and connect

Build the Java server, then start it with the `mcp` profile:

```sh
./java/mvnw -B -f java/pom.xml verify
java -jar java/desktop-server/target/desktop-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=mcp
```

In an MCP client that supports Streamable HTTP, register this server URL:

```text
http://127.0.0.1:8877/mcp
```

No model-provider key is required. The server uses the same local catalogue and
registered content as REST. Without the profile, the MCP server is disabled and
`/mcp` returns 404. If you configure a different Streamable HTTP endpoint, the
access filter follows `spring.ai.mcp.server.streamable-http.mcp-endpoint`.

Only loopback clients can reach MCP, even if the UI's bind address is changed.
The phone listener rejects it. Browser requests with a foreign Origin or cross-site
fetch metadata are rejected, including GET event streams. There is no separate
agent login: as with the local REST API, local clients are trusted. Do not proxy
this endpoint to other machines.

## Tools and effects

| Tool | Arguments | Result and effect |
| --- | --- | --- |
| `list_pending_reviews` | None | `{titles: [...]}` with titleId, title, pending count and openBatch. Reads queue summaries; writes nothing. |
| `claim_review_batch` | Required `titleId`, optional positive `limit` (default 50) | `{status: "claimed", batch: {batch, baseSha256, changes, document}}`, or `{status: "empty"}`. Persists a claim; returns the existing open batch when present. |

Results provide structured content and equivalent JSON text. Claimed documents use
the same canonical wire mapping as REST, including `video`/`comic` rather than the
Java model's `body`. Domain errors are MCP tool results with `isError: true`, not
successful empty batches. Discovery marks listing read-only and claiming as a
non-destructive write; hints do not enforce permissions or replace service checks.

Claiming is a real write, not a preview: it leaves an open batch that blocks plain
imports. The tool never writes document files, runs Git, imports or completes work.
Follow [the review workflow](../agents/workflows/review-content.md), including the
translation guide, candidate validation, file write and Git checkpoint. Finish the
claim with the existing REST operation:

```text
POST /api/titles/{titleId}/review/batches/{batch}/complete
```

See [the review API](REVIEW-API.md) for outcomes and retry rules. Do not complete a
batch merely to clear the queue. If work stops, retain the claim; the next claim
returns it. New learner edits remain outside the claimed document.

Completion, import, validation and skeleton tools are deliberately outside this
first trial. Large claimed documents still have the same context cost as REST.
The experiment tests discovery and review handoff without replacing preparation
scripts or exposing all application operations as tools.

## Verification

```sh
./java/mvnw -B -f java/pom.xml -pl desktop-server -am test \
  -Dtest=ReviewMcpIntegrationTest,McpDisabledIntegrationTest,McpAccessTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The real SDK integration test initializes the protocol, discovers schemas/hints,
lists queued work, claims it and checks persisted SQLite state independently. REST
sees the same claim and completes it; files and the served document are checked.
It also covers repeat claims, empty queues, domain errors, default-disabled startup,
foreign origins and phone-port isolation. A filter test checks IPv4/IPv6 loopback
admission and rejection of a non-loopback peer.

References: [Spring MCP server](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html),
[Spring AI version support](https://docs.spring.io/spring-ai/reference/getting-started.html).
