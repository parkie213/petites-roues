package app.petitesroues.config;

import app.petitesroues.sync.PhoneRoutes;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * One responder per network address, as multicast stays on its own network. JmDNS owns its
 * background discovery and withdrawal. A failure is logged and leaves the listener working at its
 * known addresses.
 */
@Component
public class PhoneAdvertiser implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(PhoneAdvertiser.class);

  private final List<JmDNS> responders = new ArrayList<>();

  /** Replaces earlier announcements; JmDNS performs probing and announcements in the background. */
  public synchronized void start(List<InetAddress> addresses, String name, int port, String pin) {
    close();
    for (var address : addresses) {
      try {
        var responder = JmDNS.create(address, name);
        responders.add(responder);
        responder.registerService(service(name, port, pin));
      } catch (IOException | RuntimeException failure) {
        LOG.warn("Could not announce the phone listener on {}", address, failure);
      }
    }
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

  /**
   * Closes each responder, which withdraws its services and releases its resources. Cleanup is
   * best-effort and may briefly wait for JmDNS's own cancellation. Safe to repeat or start again.
   */
  @Override
  public synchronized void close() {
    for (var responder : responders) {
      try {
        responder.close();
      } catch (IOException | RuntimeException failure) {
        LOG.debug("The phone listener's announcement did not close cleanly", failure);
      }
    }
    responders.clear();
  }
}
