package com.petitesroues.web;

import com.petitesroues.config.PhoneListener;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
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

  public LocalRequestFilter(PhoneListener phones) {
    this.phones = phones;
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
    if (!Set.of("127.0.0.1", "localhost", "[::1]").contains(request.getServerName())) {
      response.sendError(403);
      return;
    }
    String origin = request.getHeader("Origin");
    if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod()) && origin != null) {
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
        && !Set.of("GET", "HEAD").contains(request.getMethod())) {
      response.sendError(403);
      return;
    }
    chain.doFilter(request, response);
  }
}
