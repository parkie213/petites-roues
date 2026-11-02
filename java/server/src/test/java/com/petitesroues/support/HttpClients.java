package com.petitesroues.support;

import org.springframework.web.client.RestClient;

/** HTTP clients for integration tests; documents travel as plain JSON records. */
public final class HttpClients {
  private HttpClients() {}

  public static RestClient client(String url) {
    return RestClient.builder().baseUrl(url).build();
  }
}
