package app.petitesroues.config;

import app.petitesroues.sync.PhoneRoutes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiFunction;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
public class PhoneAdvertiser implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(PhoneAdvertiser.class);

  private final Executor worker;
  private final BiFunction<InetAddress, String, JmDNS> open;
  private final List<JmDNS> responders = new ArrayList<>();
  private boolean closed;

  /** Borrows a serial executor; Spring owns its scheduling and shutdown. */
  @Autowired
  public PhoneAdvertiser(@Qualifier("phoneAdvertisementExecutor") Executor worker) {
    this(
        worker,
        (address, name) -> {
          try {
            return JmDNS.create(address, name);
          } catch (IOException failure) {
            throw new UncheckedIOException(failure);
          }
        });
  }

  /** Allows responder creation to be tested without opening multicast sockets. */
  PhoneAdvertiser(Executor worker, BiFunction<InetAddress, String, JmDNS> open) {
    this.worker = worker;
    this.open = open;
  }

  /** Starts announcing {@code port} on {@code addresses}, replacing any earlier announcement. */
  public synchronized void start(List<InetAddress> addresses, String name, int port, String pin) {
    requireOpen();
    var requested = List.copyOf(addresses);
    worker.execute(
        () -> {
          stopNow();
          for (var address : requested) {
            try {
              var responder = open.apply(address, name);
              responders.add(responder);
              responder.registerService(service(name, port, pin));
            } catch (IOException | RuntimeException failure) {
              LOG.warn("Could not announce the phone listener on {}", address, failure);
            }
          }
          if (!responders.isEmpty()) LOG.info("Announcing the phone listener on {}", requested);
        });
  }

  /** Stops announcing. */
  public synchronized void stop() {
    requireOpen();
    worker.execute(this::stopNow);
  }

  private void requireOpen() {
    if (closed) throw new RejectedExecutionException("Phone announcements are closed");
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
      try (responder) {
        responder.unregisterAllServices();
      } catch (IOException | RuntimeException failure) {
        LOG.debug("The phone listener's announcement did not close cleanly", failure);
      }
    }
    responders.clear();
  }

  /**
   * Queues final withdrawal after earlier Start/Stop operations and rejects later changes. Spring
   * drains the borrowed executor after this callback; this class never shuts that executor down.
   */
  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    worker.execute(this::stopNow);
  }
}
