package cpe.simulator.vehicles.uart;

import cpe.simulator.vehicles.domain.GeoPoint;

/** Message UART standardise en CSV. */
public record UartMessage(
    String event,
    String immatriculation,
    double latitude,
    double longitude,
    long timestampSeconds) {

  public static UartMessage position(String event, String immatriculation, GeoPoint position, long timestampSeconds) {
    return new UartMessage(
        event, immatriculation, position.latitude(), position.longitude(), timestampSeconds);
  }
}
