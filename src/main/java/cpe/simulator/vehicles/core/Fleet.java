package cpe.simulator.vehicles.core;

import cpe.simulator.vehicles.api.Logger;
import cpe.simulator.vehicles.domain.GeoPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Stockage thread-safe des vehicules simules. */
public final class Fleet {

  private final Map<String, VehicleState> vehicles = new ConcurrentHashMap<>();
  private final IncidentCoordinator incidentCoordinator = new IncidentCoordinator();
  private final Logger logger;

  public Fleet(List<VehicleState> initial, Logger logger) {
    this.logger = logger;
    for (VehicleState state : initial) {
      VehicleState existing = vehicles.putIfAbsent(state.immatriculation(), state);
      if (existing != null) {
        logger.warn("Immatriculation en double ignorée: " + state.immatriculation());
      }
    }
  }

  public int size() {
    return vehicles.size();
  }

  public List<VehicleSnapshot> snapshots() {
    List<VehicleSnapshot> list = new ArrayList<>(vehicles.size());
    for (VehicleState state : vehicles.values()) {
      list.add(state.snapshot());
    }
    return list;
  }

  public VehicleSnapshot snapshotFor(String immatriculation) {
    VehicleState state = vehicles.get(immatriculation);
    if (state == null) {
      return null;
    }
    return state.snapshot();
  }

  public boolean setAssignment(
      String immatriculation, GeoPoint target, RoutePlan plan, String incidentPhaseId) {
    VehicleState state = vehicles.get(immatriculation);
    if (state == null) {
      return false;
    }
    if (incidentPhaseId == null || incidentPhaseId.isBlank()) {
      logger.warn("Affectation ignoree (phase manquante): " + immatriculation);
      return false;
    }
    state.setAssignment(target, plan, incidentPhaseId);
    incidentCoordinator.registerVehicle(immatriculation, incidentPhaseId, target);
    return true;
  }

  public IncidentCoordinator incidentCoordinator() {
    return incidentCoordinator;
  }

  public boolean assignToBase(String immatriculation) {
    VehicleState state = vehicles.get(immatriculation);
    if (state == null) {
      return false;
    }
    state.assignToBase();
    return true;
  }

  public boolean markArrivedAtTarget(String immatriculation, long timestampMs) {
    VehicleState state = vehicles.get(immatriculation);
    if (state == null) {
      return false;
    }
    state.markArrivedAtTarget(timestampMs);
    return true;
  }

  public boolean startReturn(String immatriculation) {
    VehicleState state = vehicles.get(immatriculation);
    if (state == null) {
      return false;
    }
    state.startReturn();
    return true;
  }

  public List<VehicleSnapshot> advanceAll(MovementModel model, double deltaSeconds) {
    List<VehicleSnapshot> list = new ArrayList<>(vehicles.size());
    for (VehicleState state : vehicles.values()) {
      list.add(state.advance(model, deltaSeconds));
    }
    return list;
  }

  public boolean startReturnWithRoute(String immatriculation, RoutePlan returnPlan) {
    VehicleState state = vehicles.get(immatriculation);
    if (state == null) {
      return false;
    }
    state.startReturnWithRoute(returnPlan);
    return true;
  }
}
