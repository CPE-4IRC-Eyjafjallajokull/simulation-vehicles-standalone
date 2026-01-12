package cpe.simulator.vehicles.core;

import cpe.simulator.vehicles.domain.GeoPoint;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Coordonne les vehicules assignes a une meme phase d'incident.
 * Attend que tous les vehicules arrivent avant de demarrer le timer de depart.
 */
public final class IncidentCoordinator {

  private final Map<String, PhaseState> phases = new HashMap<>();

  private static final class PhaseState {
    private final Set<String> assigned = new HashSet<>();
    private final Set<String> arrived = new HashSet<>();
    private long allArrivedTimestamp = -1L;
    private String lastArrivedVehicle;
    private GeoPoint target;
  }

  /** Enregistre un vehicule comme assigne a une phase d'incident. */
  public synchronized void registerVehicle(
      String immatriculation, String incidentPhaseId, GeoPoint target) {
    if (incidentPhaseId == null) {
      return;
    }
    PhaseState state = phases.computeIfAbsent(incidentPhaseId, k -> new PhaseState());
    state.assigned.add(immatriculation);
    if (state.target == null) {
      state.target = target;
    }
  }

  /** Marque un vehicule comme arrive sur la phase. */
  public synchronized void markArrived(
      String immatriculation, String incidentPhaseId, long nowMs, GeoPoint target) {
    PhaseState state = phases.get(incidentPhaseId);
    if (state == null) {
      return;
    }
    state.arrived.add(immatriculation);
    state.lastArrivedVehicle = immatriculation;
    if (state.target == null) {
      state.target = target;
    }

    if (areAllArrived(state) && state.allArrivedTimestamp < 0) {
      state.allArrivedTimestamp = nowMs;
    }
  }

  /** Retourne l'immatriculation du dernier vehicule arrive sur la phase. */
  public synchronized String getLastArrivedVehicle(String incidentPhaseId) {
    PhaseState state = phases.get(incidentPhaseId);
    return state == null ? null : state.lastArrivedVehicle;
  }

  /** Verifie si tous les vehicules assignes sont arrives. */
  public synchronized boolean areAllArrived(String incidentPhaseId) {
    PhaseState state = phases.get(incidentPhaseId);
    return state != null && areAllArrived(state);
  }

  private static boolean areAllArrived(PhaseState state) {
    return state.arrived.containsAll(state.assigned);
  }

  /** Verifie si le temps sur site est ecoule et tous peuvent partir. */
  public synchronized boolean canReturn(String incidentPhaseId, long nowMs, long onSiteDurationMs) {
    PhaseState state = phases.get(incidentPhaseId);
    if (state == null || state.allArrivedTimestamp < 0) {
      return false;
    }
    return nowMs - state.allArrivedTimestamp >= onSiteDurationMs;
  }

  /** Retourne les vehicules assignes a une phase. */
  public synchronized Set<String> getAssignedVehicles(String incidentPhaseId) {
    PhaseState state = phases.get(incidentPhaseId);
    return state != null ? new HashSet<>(state.assigned) : new HashSet<>();
  }

  public synchronized GeoPoint getTarget(String incidentPhaseId) {
    PhaseState state = phases.get(incidentPhaseId);
    return state == null ? null : state.target;
  }

  /** Nettoie les donnees d'une phase terminee. */
  public synchronized void clearIncident(String incidentPhaseId) {
    phases.remove(incidentPhaseId);
  }

  /** Retire un vehicule d'une phase (par exemple s'il retourne a la base). */
  public synchronized void unregisterVehicle(String immatriculation, String incidentPhaseId) {
    PhaseState state = phases.get(incidentPhaseId);
    if (state == null) {
      return;
    }
    state.assigned.remove(immatriculation);
    state.arrived.remove(immatriculation);
    if (state.assigned.isEmpty()) {
      phases.remove(incidentPhaseId);
    }
  }
}
