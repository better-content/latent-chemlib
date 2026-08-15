package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.bettercontent.latentchemlib.integration.adpother.AdpotherAtmosphereBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class CloudExtractionService {
    public static final CloudExtractionService INSTANCE = new CloudExtractionService();
    private CloudExtractionService() {}

    public int extractAdpotherUnits(ServerLevel level, BlockPos pos, String chemicalId, int requestedUnits) {
        return AdpotherAtmosphereBridge.INSTANCE.pollutantFor(chemicalId)
            .map(pollutant -> extractPollutantUnits(level, pos, AdpotherAtmosphereBridge.INSTANCE.pollutantId(pollutant), requestedUnits))
            .orElse(0);
    }

    public int extractPollutantUnits(ServerLevel level, BlockPos pos, String pollutantId, int requestedUnits) {
        if (!(level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity cloud)) return 0;
        int extracted = cloud.extractUnits(pollutantId, requestedUnits);
        if (cloud.pollutantState().isEmpty()) level.removeBlock(pos, false);
        return extracted;
    }
}
