package com.gerald.latentchemlib.api;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

public final class LatentCapabilities {
    public static final Capability<IChemicalStateHandler> CHEMICAL_STATE =
        CapabilityManager.get(new CapabilityToken<>() {});

    private LatentCapabilities() {}
}
