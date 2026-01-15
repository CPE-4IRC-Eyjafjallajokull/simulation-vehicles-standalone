package cpe.simulator.vehicles.api;

import cpe.simulator.vehicles.domain.GeoPoint;
import cpe.simulator.vehicles.domain.VehicleStatus;

/** Abstraction du transport RabbitMQ pour la telemetrie et les affectations. */
public interface TelemetryGateway extends AutoCloseable {
  void start(AssignmentMessageListener listener);

  void publishVehiclePosition(
      String immatriculation, GeoPoint position, long timestampSeconds);

  void publishVehicleStatus(
      String immatriculation, VehicleStatus status, long timestampSeconds);

  void publishIncidentStatus(
      String immatriculation, int status, long timestampSeconds);

  @Override
  void close();
}
