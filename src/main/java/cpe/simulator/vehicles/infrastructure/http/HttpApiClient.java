package cpe.simulator.vehicles.infrastructure.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** Client HTTP generique avec authentification pluggable. */
public final class HttpApiClient {

  private final URI baseUri;
  private final long timeoutMs;
  private final AuthStrategy authStrategy;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public HttpApiClient(
      String baseUrl,
      long timeoutMs,
      AuthStrategy authStrategy,
      HttpClient httpClient,
      ObjectMapper objectMapper) {
    this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
    this.timeoutMs = timeoutMs;
    this.authStrategy = authStrategy;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  public <T> T get(String path, Class<T> responseType) throws IOException, InterruptedException {
    HttpResponse<String> response = send("GET", path, null);
    ensureSuccess(response);
    return objectMapper.readValue(response.body(), responseType);
  }

  public <T> List<T> getList(String path, TypeReference<List<T>> responseType)
      throws IOException, InterruptedException {
    HttpResponse<String> response = send("GET", path, null);
    ensureSuccess(response);
    return objectMapper.readValue(response.body(), responseType);
  }

  public <T> T post(String path, Object payload, Class<T> responseType)
      throws IOException, InterruptedException {
    String json = objectMapper.writeValueAsString(payload);
    HttpResponse<String> response = send("POST", path, json);
    ensureSuccess(response);
    return objectMapper.readValue(response.body(), responseType);
  }

  private HttpResponse<String> send(String method, String path, String body)
      throws IOException, InterruptedException {
    String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
    URI uri = baseUri.resolve(normalizedPath);

    HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMillis(timeoutMs))
            .header("Accept", "application/json");

    authStrategy.apply(builder);

    if (body == null) {
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      builder
          .header("Content-Type", "application/json")
          .method(method, HttpRequest.BodyPublishers.ofString(body));
    }

    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private void ensureSuccess(HttpResponse<String> response) {
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
    }
  }
}
