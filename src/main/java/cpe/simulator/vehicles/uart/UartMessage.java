package cpe.simulator.vehicles.uart;

import cpe.simulator.vehicles.domain.GeoPoint;

/** Message UART standardise en CSV. */
public record UartMessage(
    String event,
    int status,
    String immatriculation,
    double latitude,
    double longitude,
    long timestampSeconds) {

  public static UartMessage position(
      String event, int status, String immatriculation, GeoPoint position, long timestampSeconds) {
    return new UartMessage(
        event, status, immatriculation, position.latitude(), position.longitude(), timestampSeconds);
  }
}
