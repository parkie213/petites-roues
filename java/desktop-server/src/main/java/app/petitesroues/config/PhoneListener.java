package app.petitesroues.config;

import app.petitesroues.phone.PhoneCertificateService;
import app.petitesroues.settings.SettingsStore;
import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The phone listener: a second port, on every network interface, through which paired phones reach
 * this desktop. It speaks only HTTPS (TLS 1.3) with this desktop's self-signed certificate ({@link
 * PhoneCertificateService}), which phones trust by the pin the pairing code carries. It serves only
 * {@code /phone/**} ({@link app.petitesroues.web.LocalRequestFilter}); the browser UI's port stays
 * on 127.0.0.1. Opened and closed while the server runs, from the "Allow phones to connect" switch;
 * the choice is remembered ({@link #SETTING}) and restored at startup. Closed until first switched
 * on, since it opens a port on the local network. While open it is announced on the network ({@link
 * PhoneAdvertiser}), so phones find this desktop when its address changes. {@code
 * petitesroues.phone.port} (default 8878) chooses the port, 0 any free one.
 */
@Component
public class PhoneListener {
  /** The setting remembering whether phones may connect. */
  public static final String SETTING = "phone.enabled";

  private static final Logger LOG = LoggerFactory.getLogger(PhoneListener.class);

  /**
   * Interfaces a phone never reaches: container, VM and overlay bridges (Docker, Kubernetes CNI,
   * libvirt, VirtualBox, VMware). The phone tries every listed address in turn, so each dead one
   * delays pairing.
   */
  private static final Pattern BRIDGE =
      Pattern.compile(
          "^(docker|br-|veth|virbr|vxlan|cali|flannel|cni|kube|lxc|lxd|podman|vboxnet|vmnet).*");

  /** The certificate's name in the listener's in-memory key store. */
  private static final String ALIAS = "phone";

  private final SettingsStore settings;
  private final PhoneCertificateService certificates;
  private final PhoneAdvertiser advertiser;
  private final int configuredPort;
  private Service service;
  private volatile Connector connector;

  public PhoneListener(
      SettingsStore settings,
      PhoneCertificateService certificates,
      PhoneAdvertiser advertiser,
      @Value("${petitesroues.phone.port:8878}") int configuredPort) {
    this.settings = settings;
    this.certificates = certificates;
    this.advertiser = advertiser;
    this.configuredPort = configuredPort;
  }

  /** Once the UI port is up, reopens the phone listener if it was left switched on. */
  @EventListener
  public synchronized void started(ServletWebServerInitializedEvent event) {
    if (!(event.getWebServer() instanceof TomcatWebServer tomcat)) return;
    service = tomcat.getTomcat().getService();
    if (settings.get(SETTING).map(Boolean::parseBoolean).orElse(false)) {
      try {
        open();
      } catch (IllegalStateException failure) {
        LOG.warn("Phones cannot connect: {}", failure.getMessage());
      }
    }
  }

  /**
   * Switches the phone listener on or off and remembers the choice. Paired phones stay paired while
   * it is off; they just cannot reach this desktop.
   *
   * @throws IllegalStateException if it cannot open, e.g. its port is taken; the choice is then not
   *     remembered
   */
  public synchronized void setEnabled(boolean enabled) {
    if (enabled) open();
    else close();
    settings.put(SETTING, Boolean.toString(enabled));
  }

  private void open() {
    if (connector != null) return;
    if (service == null) throw new IllegalStateException("The server is still starting");
    var phone = new Connector();
    phone.setPort(configuredPort);
    phone.setProperty("address", "0.0.0.0");
    secure(phone);
    service.addConnector(phone);
    if (phone.getState() != LifecycleState.STARTED) {
      service.removeConnector(phone);
      destroy(phone);
      throw new IllegalStateException(
          "Port " + configuredPort + " is in use; choose another with petitesroues.phone.port");
    }
    connector = phone;
    LOG.info("Phones can connect on port {}", phone.getLocalPort());
    advertiser.start(
        addresses(), desktopName(), phone.getLocalPort(), certificates.certificate().pin());
  }

  /** Makes the connector serve TLS 1.3 only, with this desktop's certificate. */
  private void secure(Connector phone) {
    // The store lives only in memory, so its password guards nothing.
    char[] password = ALIAS.toCharArray();
    var tls = new SSLHostConfig();
    tls.setProtocols("TLSv1.3");
    var certificate = new SSLHostConfigCertificate(tls, SSLHostConfigCertificate.Type.EC);
    certificate.setCertificateKeystore(certificates.certificate().keyStore(ALIAS, password));
    certificate.setCertificateKeyAlias(ALIAS);
    certificate.setCertificateKeyPassword(new String(password));
    tls.addCertificate(certificate);
    phone.addSslHostConfig(tls);
    phone.setScheme("https");
    phone.setSecure(true);
    phone.setProperty("SSLEnabled", "true");
  }

  private void close() {
    var phone = connector;
    if (phone == null) return;
    connector = null;
    advertiser.stop();
    service.removeConnector(phone);
    destroy(phone);
    LOG.info("Phones can no longer connect");
  }

  private static void destroy(Connector phone) {
    try {
      phone.destroy();
    } catch (LifecycleException e) {
      LOG.debug("Phone listener did not close cleanly", e);
    }
  }

  /** Whether the phone listener is open. */
  public boolean enabled() {
    return connector != null;
  }

  /** The port it listens on, or -1 when it is off. */
  public int port() {
    var phone = connector;
    return phone == null ? -1 : phone.getLocalPort();
  }

  /** Whether the request arrived through the phone listener rather than the UI port. */
  public boolean handles(HttpServletRequest request) {
    var phone = connector;
    return phone != null && request.getLocalPort() == phone.getLocalPort();
  }

  /**
   * The listener's {@code https} base URLs a phone on the same network can try: one per up,
   * non-loopback IPv4 address outside container and VM bridges, site-local (192.168/16, 10/8,
   * 172.16/12, as on Wi-Fi) first, then others such as a Tailscale address. Empty when it is off.
   */
  public List<String> urls() {
    int port = port();
    if (port < 0) return List.of();
    return addresses().stream().map(a -> "https://" + a.getHostAddress() + ":" + port).toList();
  }

  /** This computer's addresses a phone can reach, in the order {@link #urls} gives. */
  private static List<InetAddress> addresses() {
    List<InetAddress> local = new ArrayList<>(), other = new ArrayList<>();
    try {
      for (var face : Collections.list(NetworkInterface.getNetworkInterfaces())) {
        if (!face.isUp()
            || face.isLoopback()
            || face.isVirtual()
            || BRIDGE.matcher(face.getName()).matches()) continue;
        for (var address : Collections.list(face.getInetAddresses()))
          if (address instanceof Inet4Address && !address.isLinkLocalAddress())
            (address.isSiteLocalAddress() ? local : other).add(address);
      }
    } catch (SocketException e) {
      return List.of();
    }
    local.addAll(other);
    return local;
  }

  /** This computer's name, for the phone to show; "petites roues" when it has none. */
  public String desktopName() {
    try {
      String name = InetAddress.getLocalHost().getHostName();
      return name == null || name.isBlank() ? "petites roues" : name;
    } catch (UnknownHostException e) {
      return "petites roues";
    }
  }
}
