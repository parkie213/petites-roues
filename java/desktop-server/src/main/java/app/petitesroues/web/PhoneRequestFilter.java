package app.petitesroues.web;

import app.petitesroues.phone.PhonePairingService;
import app.petitesroues.sync.PhoneRoutes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Admits only paired phones to {@code /phone/**}: every request but {@code POST /phone/pair} needs
 * {@code Authorization: Bearer <token>} for a phone that is still paired, else 401 with the API's
 * {@code {"error": message}} body, which the phone shows. The paired phone is left in the request
 * attribute {@link #PHONE}. Runs after {@link LocalRequestFilter}, which keeps these routes to the
 * phone listener.
 */
@Component
@Order(2)
@Profile("!security")
public class PhoneRequestFilter extends OncePerRequestFilter {
  /** The request attribute holding the authenticated {@code PairedPhone}. */
  public static final String PHONE = "petitesroues.phone";

  private final PhonePairingService pairing;
  private final JsonMapper mapper;

  public PhoneRequestFilter(PhonePairingService pairing, JsonMapper mapper) {
    this.pairing = pairing;
    this.mapper = mapper;
  }

  /** Whether a request addresses the phone namespace, independently of its arrival port. */
  public static boolean isPhonePath(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return path.equals(PhoneRoutes.PREFIX) || path.startsWith(PhoneRoutes.PREFIX + "/");
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !isPhonePath(request)
        || ("POST".equals(request.getMethod())
            && request.getRequestURI().equals(request.getContextPath() + PhoneRoutes.PAIR));
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
      response.setStatus(401);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      response
          .getWriter()
          .write(
              mapper.writeValueAsString(
                  new ApiExceptionHandler.ApiError("Pair this phone with the desktop again")));
      return;
    }
    request.setAttribute(PHONE, phone.get());
    chain.doFilter(request, response);
  }
}
