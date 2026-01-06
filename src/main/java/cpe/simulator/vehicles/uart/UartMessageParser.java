package cpe.simulator.vehicles.uart;

import java.util.Locale;

/** Parseur/serialiseur CSV pour les messages UART. */
public final class UartMessageParser {

  private static final long MAX_TIMESTAMP_SECONDS = 0xFFFF_FFFFL;

  public UartMessage parse(String line) {
    if (line == null) {
      throw new IllegalArgumentException("UART line is null");
    }
    String trimmed = line.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("UART line is empty");
    }

    String[] parts = trimmed.split(",");
    if (parts.length != 5) {
      throw new IllegalArgumentException(
          "Invalid UART frame, expected 5 fields: " + trimmed);
    }

    String event = parts[0].trim();
    String immatriculation = normalizeIncomingImmatriculation(parts[1]);
    double latitude = parseDouble(parts[2].trim(), "latitude", trimmed);
    double longitude = parseDouble(parts[3].trim(), "longitude", trimmed);
    long timestampSeconds = parseTimestamp(parts[4].trim(), trimmed);

    if (event.isEmpty()) {
      throw new IllegalArgumentException("Missing event in UART frame: " + trimmed);
    }
    if (immatriculation.isEmpty()) {
      throw new IllegalArgumentException("Missing immatriculation in UART frame: " + trimmed);
    }

    return new UartMessage(event, immatriculation, latitude, longitude, timestampSeconds);
  }

  public String serialize(UartMessage message) {
    if (message == null) {
      throw new IllegalArgumentException("UART message is null");
    }
    String immatriculation = toUartImmatriculation(message.immatriculation());
    return String.format(
        Locale.US,
        "%s,%s,%.6f,%.6f,%d",
        message.event(),
        immatriculation,
        message.latitude(),
        message.longitude(),
        message.timestampSeconds());
  }

  private double parseDouble(String raw, String label, String line) {
    try {
      return Double.parseDouble(raw);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid " + label + " in UART frame: " + line, e);
    }
  }

  private long parseTimestamp(String raw, String line) {
    try {
      long value = Long.parseLong(raw);
      if (value < 0 || value > MAX_TIMESTAMP_SECONDS) {
        throw new IllegalArgumentException("Timestamp out of range in UART frame: " + line);
      }
      return value;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid timestamp in UART frame: " + line, e);
    }
  }

  private String normalizeIncomingImmatriculation(String raw) {
    if (raw == null) {
      return null;
    }
    String compact = raw.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
    if (compact.length() == 7
        && Character.isLetter(compact.charAt(0))
        && Character.isLetter(compact.charAt(1))
        && Character.isDigit(compact.charAt(2))
        && Character.isDigit(compact.charAt(3))
        && Character.isDigit(compact.charAt(4))
        && Character.isLetter(compact.charAt(5))
        && Character.isLetter(compact.charAt(6))) {
      return compact.substring(0, 2) + "-" + compact.substring(2, 5) + "-" + compact.substring(5);
    }
    return compact;
  }

  private String toUartImmatriculation(String immatriculation) {
    if (immatriculation == null) {
      return "";
    }
    return immatriculation.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
  }
}
