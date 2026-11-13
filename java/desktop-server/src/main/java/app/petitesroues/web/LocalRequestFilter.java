package app.petitesroues.web;

import app.petitesroues.config.PhoneListener;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps each port to its own routes. The UI port serves this application's browser pages, so local
 * file imports and edits must originate from it: requests must be addressed to localhost and
 * same-origin. The phone listener ({@link PhoneListener}) serves only {@code /phone/**}, which
 * {@link PhoneRequestFilter} authenticates; {@code /phone/**} is not found on the UI port.
 */
@Component
@Order(1)
public class LocalRequestFilter extends OncePerRequestFilter {
  private final PhoneListener phones;
  private final String mcpEndpoint;

  public LocalRequestFilter(
      PhoneListener phones,
      @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}") String mcpEndpoint) {
    this.phones = phones;
    this.mcpEndpoint = mcpEndpoint;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    boolean phonePort = phones.handles(request);
    if (phonePort != PhoneRequestFilter.isPhonePath(request)) {
      response.sendError(404);
      return;
    }
    if (phonePort) {
      chain.doFilter(request, response);
      return;
    }
    String path = request.getRequestURI().substring(request.getContextPath().length());
    boolean mcp = path.equals(mcpEndpoint) || path.startsWith(mcpEndpoint + "/");
    // MCP is an agent-only local interface, including its GET event stream. Keep it local
    // even if the ordinary UI listener's bind address is overridden.
    if (mcp && !InetAddress.ofLiteral(request.getRemoteAddr()).isLoopbackAddress()) {
      response.sendError(403);
      return;
    }
    if (!Set.of("127.0.0.1", "localhost", "[::1]").contains(request.getServerName())) {
      response.sendError(403);
      return;
    }
    String origin = request.getHeader("Origin");
    if ((mcp || !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod()))
        && origin != null) {
      try {
        URI uri = URI.create(origin);
        int port = uri.getPort() < 0 ? 80 : uri.getPort();
        if (!uri.getScheme().equals("http")
            || !uri.getHost().equals(request.getServerName())
            || port != request.getServerPort()) {
          response.sendError(403);
          return;
        }
      } catch (RuntimeException e) {
        response.sendError(403);
        return;
      }
    }
    if ("cross-site".equals(request.getHeader("Sec-Fetch-Site"))
        && (mcp || !Set.of("GET", "HEAD").contains(request.getMethod()))) {
      response.sendError(403);
      return;
    }
    chain.doFilter(request, response);
  }
}
