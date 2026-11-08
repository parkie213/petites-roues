package com.petitesroues.support;

import com.petitesroues.sync.CertificatePins;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP clients for integration tests; documents travel as plain JSON records. */
public final class HttpClients {
  private HttpClients() {}

  public static RestClient client(String url) {
    return RestClient.builder().baseUrl(url).build();
  }

  /**
   * A client for the phone listener at {@code url}, trusting only the certificate {@code pin}
   * names, as a phone does.
   */
  public static RestClient phone(String url, String pin) {
    var pinned =
        new SimpleClientHttpRequestFactory() {
          @Override
          protected void prepareConnection(HttpURLConnection connection, String method)
              throws IOException {
            CertificatePins.secure(connection, pin);
            super.prepareConnection(connection, method);
          }
        };
    return RestClient.builder().baseUrl(url).requestFactory(pinned).build();
  }
}
