package com.bettercontent.latentchemlib.sim;

public final class MachineTransfer {
    public static final double TRANSFER_MASS = 250.0;

    private MachineTransfer() {}

    public static double captureAmount(double storedMass, double availableMass, double capacity) {
        double remaining = Math.max(0.0, capacity - Math.max(0.0, storedMass));
        double available = Math.max(0.0, availableMass) * 0.25;
        return Math.min(remaining, Math.min(TRANSFER_MASS, available));
    }
}
