package cpe.simulator.vehicles.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Configuration du simulateur chargee depuis les variables d'environnement. */
public record SimulatorConfig(
    // Keycloak
    String keycloakIssuer,
    String keycloakClientId,
    String keycloakClientSecret,
    long keycloakTimeoutMs,
    long keycloakTokenExpirySkewSeconds,

    // API SDMIS
    String apiBaseUrl,
    long apiTimeoutMs,

    // Simulation
    long simTickMs,
    double vehicleSpeedMps,
    double positionEpsilonMeters,

    // UART
    String uartPort,
    int uartBaud,
    long uartReconnectMs,
    int uartReadTimeoutMs,
    long uartWriteDelayMs,
    long uartBaseSendIntervalMs,
    long uartMovingSendIntervalMs,
    long uartStatusSendIntervalMs,
    String uartEventPosition,
    String uartEventStatus,
    String uartEventAffectation,
    boolean routeSnapStart,
    boolean uartLogSends,

    // Intervention
    long onSiteDurationMs) {

  public static SimulatorConfig fromEnvironment() {
    Map<String, String> env = loadEnv();

    return new SimulatorConfig(
        env.getOrDefault("KEYCLOAK_ISSUER", "http://localhost:8080/realms/sdmis"),
        requireEnv(env, "KEYCLOAK_CLIENT_ID"),
        requireEnv(env, "KEYCLOAK_CLIENT_SECRET"),
        parseLong(env, "KEYCLOAK_TIMEOUT_MS", 3_000L),
        parseLong(env, "KEYCLOAK_TOKEN_EXPIRY_SKEW_SECONDS", 30L),
        env.getOrDefault("SDMIS_API_BASE_URL", "http://localhost:3001"),
        parseLong(env, "SDMIS_API_TIMEOUT_MS", 5_000L),
        parseLong(env, "SIM_TICK_MS", 200L),
        parseDouble(env, "VEHICLE_SPEED_MPS", 13.89),
        parseDouble(env, "POSITION_EPSILON_METERS", 50.0),
        env.getOrDefault("UART_PORT", "/dev/ttyACM0"),
        parseInt(env, "UART_BAUD", 115_200),
        parseLong(env, "UART_RECONNECT_MS", 2_000L),
        parseInt(env, "UART_READ_TIMEOUT_MS", 1_000),
        parseLong(env, "UART_WRITE_DELAY_MS", 10L),
        parseLong(env, "UART_BASE_SEND_INTERVAL_MS", 60_000L),
        parseLong(env, "UART_MOVING_SEND_INTERVAL_MS", 500L),
        parseLong(env, "UART_STATUS_SEND_INTERVAL_MS", 5_000L),
        env.getOrDefault("UART_EVENT_POSITION", "vehicle_position"),
        env.getOrDefault("UART_EVENT_STATUS", "vehicle_status"),
        env.getOrDefault("UART_EVENT_AFFECTATION", "vehicle_affectation"),
        parseBoolean(env, "ROUTE_SNAP_START", true),
        parseBoolean(env, "UART_LOG_SENDS", false),
        parseLong(env, "ON_SITE_DURATION_MS", 60_000L));
  }

  private static Map<String, String> loadEnv() {
    Map<String, String> merged = new HashMap<>();
    merged.putAll(loadDotenv());
    merged.putAll(System.getenv());
    return merged;
  }

  private static Map<String, String> loadDotenv() {
    Path dotenvPath = Path.of(".env");
    if (!Files.exists(dotenvPath)) {
      return Map.of();
    }
    Map<String, String> values = new HashMap<>();
    try {
      for (String line : Files.readAllLines(dotenvPath)) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
          continue;
        }
        if (trimmed.startsWith("export ")) {
          trimmed = trimmed.substring(7).trim();
        }
        int sep = trimmed.indexOf('=');
        if (sep > 0) {
          String key = trimmed.substring(0, sep).trim();
          String value = trimmed.substring(sep + 1).trim();
          if ((value.startsWith("\"") && value.endsWith("\""))
              || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
          }
          values.put(key, value);
        }
      }
    } catch (IOException ignored) {
    }
    return values;
  }

  private static String requireEnv(Map<String, String> env, String key) {
    String value = env.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing required environment variable: " + key);
    }
    return value;
  }

  private static long parseLong(Map<String, String> env, String key, long defaultValue) {
    String value = env.get(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static int parseInt(Map<String, String> env, String key, int defaultValue) {
    String value = env.get(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static double parseDouble(Map<String, String> env, String key, double defaultValue) {
    String value = env.get(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static boolean parseBoolean(Map<String, String> env, String key, boolean defaultValue) {
    String value = env.get(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return Boolean.parseBoolean(value);
  }
}
