package com.petitesroues.web;

import com.petitesroues.phone.PhonePairingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Admits only paired phones to {@code /phone/**}: every request but {@code POST /phone/pair} needs
 * {@code Authorization: Bearer <token>} for a phone that is still paired, else 401. The paired
 * phone is left in the request attribute {@link #PHONE}. Runs after {@link LocalRequestFilter},
 * which keeps these routes to the phone listener.
 */
@Component
@Order(2)
public class PhoneRequestFilter extends OncePerRequestFilter {
  /** The request attribute holding the authenticated {@code PairedPhone}. */
  public static final String PHONE = "petitesroues.phone";

  private final PhonePairingService pairing;

  public PhoneRequestFilter(PhonePairingService pairing) {
    this.pairing = pairing;
  }

  static boolean isPhonePath(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return path.equals("/phone") || path.startsWith("/phone/");
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !isPhonePath(request)
        || ("POST".equals(request.getMethod())
            && request.getRequestURI().equals(request.getContextPath() + "/phone/pair"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    String token =
        header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
            ? header.substring(7).strip()
            : null;
    var phone = pairing.authenticate(token);
    if (phone.isEmpty()) {
      response.setHeader("WWW-Authenticate", "Bearer");
      response.sendError(401, "Pair this phone with the desktop again");
      return;
    }
    request.setAttribute(PHONE, phone.get());
    chain.doFilter(request, response);
  }
}
