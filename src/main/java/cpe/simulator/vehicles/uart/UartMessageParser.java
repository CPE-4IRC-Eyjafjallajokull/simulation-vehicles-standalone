package cpe.simulator.vehicles.uart;

/** Parseur/serialiseur CSV pour les messages UART. */
public final class UartMessageParser {

  private static final long MAX_TIMESTAMP_SECONDS = 0xFFFF_FFFFL;
  private static final int MAX_STATUS = 255;
  private static final int EXPECTED_FIELDS = 6;

  public UartMessage parse(String line) {
    if (line == null) {
      throw new IllegalArgumentException("UART line is null");
    }
    String trimmed = line.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("UART line is empty");
    }

    int[] commas = new int[EXPECTED_FIELDS - 1];
    int commaCount = 0;
    for (int i = 0; i < trimmed.length(); i++) {
      if (trimmed.charAt(i) == ',') {
        if (commaCount >= commas.length) {
          commaCount++;
          break;
        }
        commas[commaCount++] = i;
      }
    }
    if (commaCount != EXPECTED_FIELDS - 1) {
      throw new IllegalArgumentException(
          "Invalid UART frame, expected 6 fields: " + trimmed);
    }

    String event = sliceTrim(trimmed, 0, commas[0]);
    int status = parseStatus(sliceTrim(trimmed, commas[0] + 1, commas[1]), trimmed);
    String immatriculation =
        normalizeIncomingImmatriculation(sliceTrim(trimmed, commas[1] + 1, commas[2]));
    double latitude = parseDouble(sliceTrim(trimmed, commas[2] + 1, commas[3]), "latitude", trimmed);
    double longitude = parseDouble(sliceTrim(trimmed, commas[3] + 1, commas[4]), "longitude", trimmed);
    long timestampSeconds =
        parseTimestamp(sliceTrim(trimmed, commas[4] + 1, trimmed.length()), trimmed);

    if (event.isEmpty()) {
      throw new IllegalArgumentException("Missing event in UART frame: " + trimmed);
    }
    if (immatriculation.isEmpty()) {
      throw new IllegalArgumentException("Missing immatriculation in UART frame: " + trimmed);
    }

    return new UartMessage(event, status, immatriculation, latitude, longitude, timestampSeconds);
  }

  public String serialize(UartMessage message) {
    if (message == null) {
      throw new IllegalArgumentException("UART message is null");
    }
    String immatriculation = toUartImmatriculation(message.immatriculation());
    StringBuilder sb = new StringBuilder(64);
    sb.append(message.event());
    sb.append(',');
    sb.append(message.status());
    sb.append(',');
    sb.append(immatriculation);
    sb.append(',');
    appendCoord(sb, message.latitude());
    sb.append(',');
    appendCoord(sb, message.longitude());
    sb.append(',');
    sb.append(message.timestampSeconds());
    return sb.toString();
  }

  private int parseStatus(String raw, String line) {
    try {
      int value = Integer.parseInt(raw);
      if (value < 0 || value > MAX_STATUS) {
        throw new IllegalArgumentException("Status out of range in UART frame: " + line);
      }
      return value;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid status in UART frame: " + line, e);
    }
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
      return "";
    }
    String compact = filterImmatriculation(raw);
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
    return filterImmatriculation(immatriculation);
  }

  private String filterImmatriculation(String raw) {
    StringBuilder sb = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c >= 'a' && c <= 'z') {
        sb.append((char) (c - 32));
      } else if (c >= 'A' && c <= 'Z') {
        sb.append(c);
      } else if (c >= '0' && c <= '9') {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static String sliceTrim(String line, int start, int end) {
    int from = start;
    int to = end;
    while (from < to && line.charAt(from) <= ' ') {
      from++;
    }
    while (to > from && line.charAt(to - 1) <= ' ') {
      to--;
    }
    return line.substring(from, to);
  }

  private static void appendCoord(StringBuilder sb, double value) {
    long scaled = Math.round(value * 1_000_000d);
    long deg = scaled / 1_000_000L;
    long dec = Math.abs(scaled % 1_000_000L);
    sb.append(deg);
    sb.append('.');
    appendZeroPadded6(sb, dec);
  }

  private static void appendZeroPadded6(StringBuilder sb, long value) {
    if (value < 100000) {
      sb.append('0');
    }
    if (value < 10000) {
      sb.append('0');
    }
    if (value < 1000) {
      sb.append('0');
    }
    if (value < 100) {
      sb.append('0');
    }
    if (value < 10) {
      sb.append('0');
    }
    sb.append(value);
  }
}
