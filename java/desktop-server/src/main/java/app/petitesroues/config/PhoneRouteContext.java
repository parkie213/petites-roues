package app.petitesroues.config;

import app.petitesroues.library.LibraryService;
import app.petitesroues.phone.PhonePairingService;
import app.petitesroues.phone.PhoneTransferService;
import app.petitesroues.web.ApiExceptionHandler;
import app.petitesroues.web.PhoneController;
import app.petitesroues.web.PhoneRequestFilter;
import jakarta.servlet.DispatcherType;
import java.net.InetAddress;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds a phone-only servlet context before opening its network connector. It has no parent MVC
 * context, component scan, desktop filters, static resource handlers or desktop controllers; only
 * explicitly supplied application services are shared with the desktop. Routing never consults the
 * listener's published port or enabled state, including during startup and shutdown.
 */
@Component
public class PhoneRouteContext {
  private final PhonePairingService pairing;
  private final LibraryService library;
  private final PhoneTransferService transfer;
  private final JsonMapper mapper;

  public PhoneRouteContext(
      PhonePairingService pairing,
      LibraryService library,
      PhoneTransferService transfer,
      JsonMapper mapper) {
    this.pairing = pairing;
    this.library = library;
    this.transfer = transfer;
    this.mapper = mapper;
  }

  /** Starts an isolated TLS server, releasing its context and server if startup fails. */
  public Running start(int port, PhoneListener listener, Consumer<Connector> secure) {
    var context = new AnnotationConfigWebApplicationContext();
    context.register(Mvc.class);
    context.addBeanFactoryPostProcessor(
        beans -> {
          beans.registerSingleton(
              "phoneController", new PhoneController(pairing, listener, library, transfer));
          beans.registerSingleton("phoneErrors", new ApiExceptionHandler());
          beans.registerSingleton("phoneJson", mapper);
        });
    var factory = new TomcatServletWebServerFactory(port);
    factory.setAddress(InetAddress.ofLiteral("0.0.0.0"));
    factory.addConnectorCustomizers(secure::accept);
    Running running = null;
    try {
      var server =
          factory.getWebServer(
              servlet -> {
                var dispatcher = servlet.addServlet("phone", new DispatcherServlet(context));
                dispatcher.setLoadOnStartup(1);
                dispatcher.setAsyncSupported(true);
                dispatcher.addMapping("/");
                var authentication =
                    servlet.addFilter(
                        "phoneAuthentication", new PhoneRequestFilter(pairing, mapper));
                authentication.setAsyncSupported(true);
                authentication.addMappingForUrlPatterns(
                    EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC), false, "/*");
              });
      running = new Running(server, context);
      server.start();
      return running;
    } catch (RuntimeException | Error failure) {
      try {
        if (running != null) running.close();
        else context.close();
      } catch (RuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  /** Owns the embedded server and its isolated MVC context for exactly one enabled lifetime. */
  public record Running(WebServer server, AnnotationConfigWebApplicationContext context)
      implements AutoCloseable {
    public int port() {
      return server.getPort();
    }

    @Override
    public void close() {
      try {
        try {
          server.stop();
        } finally {
          server.destroy();
        }
      } finally {
        context.close();
      }
    }
  }

  /** Explicit MVC wiring; deliberately has no component stereotype so desktop scanning skips it. */
  @EnableWebMvc
  static class Mvc implements WebMvcConfigurer {
    private final JsonMapper mapper;

    Mvc(JsonMapper mapper) {
      this.mapper = mapper;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
      converters.replaceAll(
          converter ->
              converter instanceof JacksonJsonHttpMessageConverter
                  ? new JacksonJsonHttpMessageConverter(mapper)
                  : converter);
    }
  }
}
