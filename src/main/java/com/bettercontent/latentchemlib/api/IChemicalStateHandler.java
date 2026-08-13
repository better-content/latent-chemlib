package com.bettercontent.latentchemlib.api;

import com.bettercontent.latentchemlib.sim.ChemicalState;

/**
 * Lossless transport boundary for Latent chemical matter. Unlike a Forge fluid
 * stack, this capability can carry a complete multi-species mixture.
 */
public interface IChemicalStateHandler {
    ChemicalState chemicalState();

    double chemicalCapacityMass();

    /** @return the portion which could not be inserted */
    ChemicalState insertChemical(ChemicalState incoming, boolean simulate);

    ChemicalState extractChemical(double mass, boolean simulate);
}
