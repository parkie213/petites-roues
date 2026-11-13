package app.petitesroues.config;

import app.petitesroues.phone.PhonePairingService;
import app.petitesroues.sync.PhoneRoutes;
import app.petitesroues.web.ApiExceptionHandler.ApiError;
import app.petitesroues.web.LocalRequestFilter;
import app.petitesroues.web.PhoneRequestFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.json.JsonMapper;

/**
 * Optional comparison with the existing phone filter. Spring owns request authentication,
 * authorization and context cleanup; pairing, token hashing/revocation and listener isolation
 * retain their existing application owners. No session, login page or password user is created.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@Profile("security")
public class PhoneSecurityConfiguration {
  /** Local routing/origin checks run inside the security chain exactly once, before credentials. */
  @Bean
  public FilterRegistrationBean<LocalRequestFilter> localRequestFilterRegistration(
      LocalRequestFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  /** Preserves the existing JSON challenge and checks the current phone token on every request. */
  @Bean
  public SecurityFilterChain phoneSecurity(
      HttpSecurity http, LocalRequestFilter local, PhonePairingService pairing, JsonMapper mapper) {
    RequestMatcher protectedPhone =
        request ->
            PhoneRequestFilter.isPhonePath(request)
                && !("POST".equals(request.getMethod())
                    && request.getRequestURI().equals(request.getContextPath() + PhoneRoutes.PAIR));
    AuthenticationManager manager =
        authentication -> {
          var phone =
              pairing
                  .authenticate((String) authentication.getCredentials())
                  .orElseThrow(() -> new BadCredentialsException("Unknown phone token"));
          return new PreAuthenticatedAuthenticationToken(
              phone, null, AuthorityUtils.createAuthorityList("ROLE_PHONE"));
        };
    var authentication =
        new AuthenticationFilter(
            manager,
            request -> {
              String header = request.getHeader("Authorization");
              if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
              return new PreAuthenticatedAuthenticationToken("phone", header.substring(7).strip());
            });
    authentication.setRequestMatcher(protectedPhone);
    authentication.setSecurityContextRepository(new NullSecurityContextRepository());
    authentication.setSuccessHandler(
        (request, response, identity) ->
            request.setAttribute(PhoneRequestFilter.PHONE, identity.getPrincipal()));
    authentication.setFailureHandler((request, response, failure) -> challenge(response, mapper));

    return http
        // The UI retains its existing Host/Origin/Fetch-Metadata write checks in local.
        // Phones supply explicit bearer credentials, never ambient cookie authentication.
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        // Keep media caching and existing response-header policies outside this auth trial.
        .headers(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            errors ->
                errors.authenticationEntryPoint(
                    (request, response, failure) -> challenge(response, mapper)))
        .authorizeHttpRequests(
            routes ->
                routes.requestMatchers(protectedPhone).hasRole("PHONE").anyRequest().permitAll())
        .addFilterBefore(local, SecurityContextHolderFilter.class)
        .addFilterAfter(authentication, SecurityContextHolderFilter.class)
        .build();
  }

  private static void challenge(HttpServletResponse response, JsonMapper mapper)
      throws IOException {
    response.setHeader("WWW-Authenticate", "Bearer");
    response.setStatus(401);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response
        .getWriter()
        .write(mapper.writeValueAsString(new ApiError("Pair this phone with the desktop again")));
  }
}
