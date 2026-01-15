package cpe.simulator.vehicles.domain;

/** Status operationnel d'un vehicule. */
public enum VehicleStatus {
  DISPONIBLE(0),
  ENGAGE(1),
  SUR_INTERVENTION(2),
  TRANSPORT(3),
  RETOUR(4),
  INDISPONIBLE(5),
  HORS_SERVICE(6);

  private final int code;

  VehicleStatus(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }

  public String label() {
    return switch (this) {
      case DISPONIBLE -> "Disponible";
      case ENGAGE -> "Engagé";
      case SUR_INTERVENTION -> "Sur intervention";
      case TRANSPORT -> "Transport";
      case RETOUR -> "Retour";
      case INDISPONIBLE -> "Indisponible";
      case HORS_SERVICE -> "Hors service";
    };
  }
}
