package cpe.simulator.vehicles;

import cpe.simulator.vehicles.api.Logger;
import cpe.simulator.vehicles.config.SimulatorConfig;
import cpe.simulator.vehicles.core.VehicleSimulator;
import cpe.simulator.vehicles.infrastructure.ConsoleLogger;

/** Point d'entree du simulateur de vehicules. */
public final class SimulatorApplication {

  public static void main(String[] args) {
    Logger logger = new ConsoleLogger();

    try {
      logger.info("Demarrage du simulateur de vehicules");

      SimulatorConfig config = SimulatorConfig.fromEnvironment();
      VehicleSimulator simulator = SimulatorFactory.create(config, logger);
      simulator.run();
    } catch (Exception e) {
      logger.error("Erreur fatale: " + e.getMessage());
      System.exit(1);
    }
  }
}
