package com.petitesroues.config;

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
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * The phone listener: a second HTTP port, on every network interface, through which paired phones
 * reach this desktop. It serves only {@code /phone/**} ({@link
 * com.petitesroues.web.LocalRequestFilter}); the browser UI's port stays on 127.0.0.1. Off unless
 * {@code petitesroues.phone.enabled=true}, since it opens a port on the local network; {@code
 * petitesroues.phone.port} (default 8878) chooses the port, 0 any free one.
 */
@Component
public class PhoneListener implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
  private final boolean enabled;
  private final int configuredPort;
  private volatile Connector connector;

  public PhoneListener(
      @Value("${petitesroues.phone.enabled:false}") boolean enabled,
      @Value("${petitesroues.phone.port:8878}") int configuredPort) {
    this.enabled = enabled;
    this.configuredPort = configuredPort;
  }

  @Override
  public void customize(TomcatServletWebServerFactory factory) {
    if (!enabled) return;
    var phone = new Connector();
    phone.setPort(configuredPort);
    phone.setProperty("address", "0.0.0.0");
    factory.addAdditionalConnectors(phone);
    connector = phone;
  }

  /** Whether the phone listener was started. */
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
    return connector != null && request.getLocalPort() == port();
  }

  /**
   * Interfaces a phone never reaches: container, VM and overlay bridges (Docker, Kubernetes CNI,
   * libvirt, VirtualBox, VMware). The phone tries every listed address in turn, so each dead one
   * delays pairing.
   */
  private static final Pattern BRIDGE =
      Pattern.compile(
          "^(docker|br-|veth|virbr|vxlan|cali|flannel|cni|kube|lxc|lxd|podman|vboxnet|vmnet).*");

  /**
   * The listener's base URLs a phone on the same network can try: one per up, non-loopback IPv4
   * address outside container and VM bridges, site-local (192.168/16, 10/8, 172.16/12, as on Wi-Fi)
   * first, then others such as a Tailscale address. Empty when it is off.
   */
  public List<String> urls() {
    if (!enabled()) return List.of();
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
    return local.stream().map(a -> "http://" + a.getHostAddress() + ":" + port()).toList();
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
