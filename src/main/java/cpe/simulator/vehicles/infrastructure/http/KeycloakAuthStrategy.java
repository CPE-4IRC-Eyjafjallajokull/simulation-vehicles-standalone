package cpe.simulator.vehicles.infrastructure.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

/** Authentification via Keycloak (OAuth2 client credentials). */
public final class KeycloakAuthStrategy implements AuthStrategy {

  private final URI tokenEndpoint;
  private final String clientId;
  private final String clientSecret;
  private final long timeoutMs;
  private final long tokenExpirySkewSeconds;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  private volatile Token cachedToken;
  private final Object lock = new Object();

  public KeycloakAuthStrategy(
      String issuerUrl,
      String clientId,
      String clientSecret,
      long timeoutMs,
      long tokenExpirySkewSeconds,
      HttpClient httpClient,
      ObjectMapper objectMapper,
      Clock clock) {
    String normalizedUrl =
        issuerUrl.endsWith("/") ? issuerUrl.substring(0, issuerUrl.length() - 1) : issuerUrl;
    this.tokenEndpoint = URI.create(normalizedUrl + "/protocol/openid-connect/token");
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.timeoutMs = timeoutMs;
    this.tokenExpirySkewSeconds = tokenExpirySkewSeconds;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  public void apply(HttpRequest.Builder builder) {
    builder.header("Authorization", "Bearer " + getAccessToken());
  }

  private String getAccessToken() {
    Token token = cachedToken;
    if (token != null && token.isValid(clock, tokenExpirySkewSeconds)) {
      return token.value;
    }
    synchronized (lock) {
      token = cachedToken;
      if (token != null && token.isValid(clock, tokenExpirySkewSeconds)) {
        return token.value;
      }
      Token refreshed = fetchToken();
      cachedToken = refreshed;
      return refreshed.value;
    }
  }

  private Token fetchToken() {
    String form =
        "grant_type=client_credentials"
            + "&client_id="
            + encode(clientId)
            + "&client_secret="
            + encode(clientSecret);

    HttpRequest request =
        HttpRequest.newBuilder(tokenEndpoint)
            .timeout(Duration.ofMillis(timeoutMs))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "Keycloak token request failed (status="
                + response.statusCode()
                + ", url="
                + tokenEndpoint
                + ", body="
                + response.body()
                + ")");
      }

      TokenResponse tokenResponse = objectMapper.readValue(response.body(), TokenResponse.class);
      if (tokenResponse.accessToken == null || tokenResponse.accessToken.isBlank()) {
        throw new IllegalStateException("Keycloak response did not include an access token");
      }

      long expiresIn = tokenResponse.expiresIn > 0 ? tokenResponse.expiresIn : 60;
      long expiresAt = clock.instant().getEpochSecond() + expiresIn;
      return new Token(tokenResponse.accessToken, expiresAt);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to parse Keycloak token response from " + tokenEndpoint + ": " + e.getMessage(),
          e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Keycloak token request interrupted", e);
    }
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private record Token(String value, long expiresAtEpochSeconds) {
    boolean isValid(Clock clock, long skewSeconds) {
      return clock.instant().getEpochSecond() + skewSeconds < expiresAtEpochSeconds;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") long expiresIn) {}
}
