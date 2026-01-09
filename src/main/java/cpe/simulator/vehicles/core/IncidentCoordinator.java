package cpe.simulator.vehicles.core;

import cpe.simulator.vehicles.domain.GeoPoint;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Coordonne les vehicules assignes a un meme incident.
 * Attend que tous les vehicules arrivent avant de demarrer le timer de depart.
 */
public final class IncidentCoordinator {

  private final Map<GeoPoint, Set<String>> assignedVehicles = new HashMap<>();
  private final Map<GeoPoint, Set<String>> arrivedVehicles = new HashMap<>();
  private final Map<GeoPoint, Long> allArrivedTimestamp = new HashMap<>();
  private final Map<GeoPoint, String> lastArrivedVehicle = new HashMap<>();

  /** Enregistre un vehicule comme assigne a un incident. */
  public synchronized void registerVehicle(String immatriculation, GeoPoint target) {
    assignedVehicles.computeIfAbsent(target, k -> new HashSet<>()).add(immatriculation);
    arrivedVehicles.computeIfAbsent(target, k -> new HashSet<>());
  }

  /** Marque un vehicule comme arrive sur l'incident. */
  public synchronized void markArrived(String immatriculation, GeoPoint target, long nowMs) {
    Set<String> arrived = arrivedVehicles.get(target);
    if (arrived == null) {
      return;
    }
    arrived.add(immatriculation);
    lastArrivedVehicle.put(target, immatriculation);

    if (areAllArrived(target) && !allArrivedTimestamp.containsKey(target)) {
      allArrivedTimestamp.put(target, nowMs);
    }
  }

  /** Retourne l'immatriculation du dernier vehicule arrive sur l'incident. */
  public synchronized String getLastArrivedVehicle(GeoPoint target) {
    return lastArrivedVehicle.get(target);
  }

  /** Verifie si tous les vehicules assignes sont arrives. */
  public synchronized boolean areAllArrived(GeoPoint target) {
    Set<String> assigned = assignedVehicles.get(target);
    Set<String> arrived = arrivedVehicles.get(target);
    if (assigned == null || arrived == null) {
      return false;
    }
    return arrived.containsAll(assigned);
  }

  /** Verifie si le temps sur site est ecoule et tous peuvent partir. */
  public synchronized boolean canReturn(GeoPoint target, long nowMs, long onSiteDurationMs) {
    Long timestamp = allArrivedTimestamp.get(target);
    if (timestamp == null) {
      return false;
    }
    return nowMs - timestamp >= onSiteDurationMs;
  }

  /** Retourne les vehicules assignes a un incident. */
  public synchronized Set<String> getAssignedVehicles(GeoPoint target) {
    Set<String> assigned = assignedVehicles.get(target);
    return assigned != null ? new HashSet<>(assigned) : new HashSet<>();
  }

  /** Nettoie les donnees d'un incident termine. */
  public synchronized void clearIncident(GeoPoint target) {
    assignedVehicles.remove(target);
    arrivedVehicles.remove(target);
    allArrivedTimestamp.remove(target);
    lastArrivedVehicle.remove(target);
  }

  /** Retire un vehicule d'un incident (par exemple s'il retourne a la base). */
  public synchronized void unregisterVehicle(String immatriculation, GeoPoint target) {
    Set<String> assigned = assignedVehicles.get(target);
    Set<String> arrived = arrivedVehicles.get(target);
    if (assigned != null) {
      assigned.remove(immatriculation);
    }
    if (arrived != null) {
      arrived.remove(immatriculation);
    }
    if (assigned != null && assigned.isEmpty()) {
      clearIncident(target);
    }
  }
}
