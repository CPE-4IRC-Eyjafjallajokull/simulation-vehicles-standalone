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
  private final Logger logger;

  public Fleet(List<VehicleState> initial, Logger logger) {
    this.logger = logger;
    for (VehicleState state : initial) {
      VehicleState existing = vehicles.putIfAbsent(state.immatriculation(), state);
      if (existing != null) {
        logger.warn("Immatriculation en double ignoree: " + state.immatriculation());
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

  public boolean setAssignment(String immatriculation, GeoPoint target, RoutePlan plan) {
    VehicleState state = vehicles.get(immatriculation);
    if (state == null) {
      return false;
    }
    state.setAssignment(target, plan);
    return true;
  }

  public boolean assignToBase(String immatriculation) {
    VehicleState state = vehicles.get(immatriculation);
    if (state == null) {
      return false;
    }
    state.assignToBase();
    return true;
  }

  public List<VehicleSnapshot> advanceAll(MovementModel model, double deltaSeconds) {
    List<VehicleSnapshot> list = new ArrayList<>(vehicles.size());
    for (VehicleState state : vehicles.values()) {
      list.add(state.advance(model, deltaSeconds));
    }
    return list;
  }
}
