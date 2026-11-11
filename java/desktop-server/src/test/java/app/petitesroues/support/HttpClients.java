package app.petitesroues.support;

import app.petitesroues.documents.StudyDocumentJson;
import app.petitesroues.sync.CertificatePins;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;

/** Integration-test HTTP clients using the explicit document JSON adapter. */
public final class HttpClients {
  private HttpClients() {}

  public static RestClient client(String url) {
    return builder(url).build();
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
    return builder(url).requestFactory(pinned).build();
  }

  /** Decodes domain objects through the explicit wire adapter, just like a real JSON client. */
  private static RestClient.Builder builder(String url) {
    return RestClient.builder()
        .baseUrl(url)
        .messageConverters(
            converters ->
                converters.replaceAll(
                    converter ->
                        converter instanceof JacksonJsonHttpMessageConverter
                            ? new JacksonJsonHttpMessageConverter(StudyDocumentJson.newMapper())
                            : converter));
  }
}
