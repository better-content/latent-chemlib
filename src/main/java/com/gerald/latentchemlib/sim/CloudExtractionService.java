package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class CloudExtractionService {
    public static final CloudExtractionService INSTANCE = new CloudExtractionService();

    private CloudExtractionService() {}

    public int extractAdpotherUnits(ServerLevel level, BlockPos pos, String chemicalId, int requestedUnits) {
        int boundedUnits = Math.max(0, requestedUnits);
        if (boundedUnits == 0) return 0;
        if (!(level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity cloud)) return 0;
        ChemicalState state = cloud.chemicalState();
        int availableUnits = (int) Math.floor(state.massOf(chemicalId) / CloudInsertionService.MASS_PER_ADPOTHER_UNIT);
        int extractedUnits = Math.min(boundedUnits, availableUnits);
        if (extractedUnits <= 0) return 0;
        cloud.extractChemicalMass(chemicalId, extractedUnits * CloudInsertionService.MASS_PER_ADPOTHER_UNIT);
        if (cloud.chemicalState().mass() <= 0.0) level.removeBlock(pos, false);
        return extractedUnits;
    }
}
