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

  public static UartMessage build(
      String event, int status, String immatriculation, GeoPoint location, long timestampSeconds) {
    return new UartMessage(
        event, status, immatriculation, location.latitude(), location.longitude(), timestampSeconds);
  }
}
