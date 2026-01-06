package cpe.simulator.vehicles.api;

/** Logger minimaliste pour le simulateur. */
public interface Logger {
  void info(String message);

  void warn(String message);

  void error(String message);
}
