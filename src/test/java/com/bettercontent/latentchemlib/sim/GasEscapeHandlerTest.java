package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.data.ChemicalTraits;
import com.smashingmods.chemlib.api.Chemical;
import com.smashingmods.chemlib.api.MatterState;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GasEscapeHandlerTest {
    @Test
    void onlyGasMatterStateCanEscapeAsGas() {
        assertTrue(GasEscapeHandler.canEscapeAsGas(new FakeChemical(MatterState.GAS)));
        assertFalse(GasEscapeHandler.canEscapeAsGas(new FakeChemical(MatterState.LIQUID)));
        assertFalse(GasEscapeHandler.canEscapeAsGas(new FakeChemical(MatterState.SOLID)));
        assertFalse(GasEscapeHandler.canEscapeAsGas(null));
    }

    @Test
    void escapedStateIsFarLessDenseThanOldWholeStackBlob() {
        ChemicalTraits traits = ChemicalTraits.fallback();
        ChemicalState escaped = GasEscapeHandler.escapedState("chemlib:chlorine", 16, traits);

        assertEquals(256.0, escaped.mass());
        assertEquals(0.576, escaped.density(), 0.0001);
        assertEquals(96.0, escaped.energy());
    }

    @Test
    void escapedStateClampsNegativeCountsToEmptyMassAndEnergy() {
        ChemicalTraits traits = ChemicalTraits.fallback();
        ChemicalState escaped = GasEscapeHandler.escapedState("chemlib:chlorine", -4, traits);

        assertEquals(0.0, escaped.mass());
        assertEquals(0.03, escaped.density(), 0.0001);
        assertEquals(0.0, escaped.energy());
    }

    @Test
    void escapedStateRespectsMinimumDensityForLowVolatilityChemicals() {
        ChemicalTraits sluggish = new ChemicalTraits(
            0.01,
            0.7,
            1.0,
            0.12,
            0.1,
            0.0,
            0.05,
            0.05,
            0.0,
            ChemicalTraits.fallback().fusionBarrier()
        );
        ChemicalState escaped = GasEscapeHandler.escapedState("chemlib:xenon", 1, sluggish);

        assertEquals(16.0, escaped.mass());
        assertEquals(0.03, escaped.density(), 0.0001);
        assertEquals(6.0, escaped.energy());
    }

    private record FakeChemical(MatterState state) implements Chemical {
        @Override
        public Item asItem() {
            return Items.AIR;
        }

        @Override
        public String getChemicalName() {
            return "fake";
        }

        @Override
        public String getAbbreviation() {
            return "Fk";
        }

        @Override
        public MatterState getMatterState() {
            return state;
        }

        @Override
        public String getChemicalDescription() {
            return "";
        }

        @Override
        public List<MobEffectInstance> getEffects() {
            return List.of();
        }

        @Override
        public int getColor() {
            return 0xFFFFFF;
        }
    }
}
