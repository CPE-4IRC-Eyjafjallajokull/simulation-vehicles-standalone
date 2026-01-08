package cpe.simulator.vehicles.core;

import cpe.simulator.vehicles.domain.GeoPoint;
import cpe.simulator.vehicles.domain.VehicleStatus;

/** Snapshot immutable de l'etat d'un vehicule. */
public record VehicleSnapshot(
    String immatriculation,
    GeoPoint position,
    GeoPoint base,
    GeoPoint assignmentTarget,
    VehicleStatus status,
    long arrivedAtTargetMs) {}
