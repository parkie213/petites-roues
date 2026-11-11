package app.petitesroues.config;

import app.petitesroues.sync.PhoneRoutes;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Announces the open phone listener on the local network with multicast DNS ({@link
 * PhoneRoutes#SERVICE_TYPE}), so a paired phone whose saved address stopped answering, typically
 * after the router gave this computer a new one, finds it again. The announcement names the port
 * and the certificate's pin; it grants nothing, since the phone still checks the pin and its token.
 * One responder per network address, as multicast stays on its own network. Announcing takes a
 * moment, so it runs on its own thread; a failure is logged and leaves the listener working at its
 * known addresses.
 */
@Component
public class PhoneAdvertiser {
  private static final Logger LOG = LoggerFactory.getLogger(PhoneAdvertiser.class);

  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          task -> {
            var thread = new Thread(task, "phone-advertiser");
            thread.setDaemon(true);
            return thread;
          });
  private final List<JmDNS> responders = new ArrayList<>();

  /** Starts announcing {@code port} on {@code addresses}, replacing any earlier announcement. */
  public void start(List<InetAddress> addresses, String name, int port, String pin) {
    worker.execute(
        () -> {
          stopNow();
          for (var address : addresses) {
            try {
              var responder = JmDNS.create(address, name);
              responders.add(responder);
              responder.registerService(service(name, port, pin));
            } catch (IOException | RuntimeException failure) {
              LOG.warn("Could not announce the phone listener on {}", address, failure);
            }
          }
          if (!responders.isEmpty()) LOG.info("Announcing the phone listener on {}", addresses);
        });
  }

  /** Stops announcing. */
  public void stop() {
    worker.execute(this::stopNow);
  }

  /** The announcement: this service type, the listener's port and the certificate's pin. */
  static ServiceInfo service(String name, int port, String pin) {
    return ServiceInfo.create(
        PhoneRoutes.SERVICE_TYPE + ".local.",
        name,
        port,
        0,
        0,
        Map.of(PhoneRoutes.SERVICE_PIN, pin));
  }

  private void stopNow() {
    for (var responder : responders) {
      try {
        responder.unregisterAllServices();
        responder.close();
      } catch (IOException | RuntimeException failure) {
        LOG.debug("The phone listener's announcement did not close cleanly", failure);
      }
    }
    responders.clear();
  }

  /** Withdraws the announcement at shutdown, so phones do not keep finding a closed desktop. */
  @PreDestroy
  void shutdown() {
    stop();
    worker.shutdown();
  }
}
