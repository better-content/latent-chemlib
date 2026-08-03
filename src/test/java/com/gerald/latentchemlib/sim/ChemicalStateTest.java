package com.gerald.latentchemlib.sim;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChemicalStateTest {
    @Test
    void emptyStateIsSafeAmbientAir() {
        ChemicalState state = ChemicalState.empty();
        assertEquals("minecraft:air", state.chemicalId());
        assertEquals(0.0, state.mass());
        assertEquals(0.0, state.density());
        assertEquals(293.0, state.temperature());
        assertEquals(0.0, state.charge());
        assertEquals(0.0, state.energy());
    }

    @Test
    void saveLoadRoundTripPreservesFields() {
        ChemicalState original = new ChemicalState("chemlib:hydrogen", 125.0, 2.5, 600.0, 0.75, 9_000.0);
        ChemicalState loaded = ChemicalState.load(original.save());
        assertEquals(original, loaded);
        assertEquals(ChemicalState.STATE_VERSION, original.save().getInt("state_version"));
        assertEquals(1, original.save().getList("components", Tag.TAG_COMPOUND).size());
    }

    @Test
    void loadFallsBackToAirWhenIdIsBlank() {
        CompoundTag tag = new CompoundTag();
        tag.putString("chemical_id", "");
        tag.putDouble("temperature", 123.0);
        ChemicalState loaded = ChemicalState.load(tag);
        assertEquals("minecraft:air", loaded.chemicalId());
        assertEquals(123.0, loaded.temperature());
    }

    @Test
    void mergeHandlesEmptySidesAndWeightedFields() {
        ChemicalState hydrogen = new ChemicalState("chemlib:hydrogen", 100.0, 4.0, 400.0, 0.2, 50.0);
        ChemicalState helium = new ChemicalState("chemlib:helium", 300.0, 1.0, 800.0, 0.6, 90.0);

        assertSame(hydrogen, hydrogen.merge(ChemicalState.empty()));
        assertSame(hydrogen, ChemicalState.empty().merge(hydrogen));

        ChemicalState merged = hydrogen.merge(helium);
        assertEquals("chemlib:helium", merged.chemicalId());
        assertEquals(100.0, merged.massOf("chemlib:hydrogen"));
        assertEquals(300.0, merged.massOf("chemlib:helium"));
        assertEquals(400.0, merged.mass());
        assertEquals(5.0, merged.density());
        assertEquals(700.0, merged.temperature());
        assertEquals(0.5, merged.charge());
        assertEquals(140.0, merged.energy());
    }

    @Test
    void withMassScalesEnergyAndClampsEmptyMatter() {
        ChemicalState state = new ChemicalState("chemlib:radon", 100.0, 4.0, 700.0, 1.0, 500.0);
        ChemicalState half = state.withMass(50.0);
        assertEquals(50.0, half.mass());
        assertEquals(2.0, half.density());
        assertEquals(250.0, half.energy());

        ChemicalState empty = state.withMass(-1.0);
        assertEquals(0.0, empty.mass());
        assertEquals(0.0, empty.density());
        assertEquals(0.0, empty.energy());

        ChemicalState fromEmpty = ChemicalState.empty().withMass(10.0);
        assertEquals(0.0, fromEmpty.mass());
        assertEquals(0.0, fromEmpty.energy());
    }

    @Test
    void splitAndRecombinePreservesOriginalConcentration() {
        ChemicalState state = new ChemicalState("chemlib:chlorine", 120.0, 6.0, 300.0, 0.2, 240.0);
        ChemicalState firstHalf = state.withMass(60.0);
        ChemicalState secondHalf = state.withMass(60.0);
        ChemicalState recombined = firstHalf.merge(secondHalf);

        assertEquals(3.0, firstHalf.density());
        assertEquals(120.0, recombined.mass());
        assertEquals(6.0, recombined.density());
        assertEquals(240.0, recombined.energy());
    }

    @Test
    void legacySingleSpeciesNbtMigratesIntoVersionedLedger() {
        CompoundTag legacy = new CompoundTag();
        legacy.putString("chemical_id", "chemlib:radon");
        legacy.putDouble("mass", 80.0);
        legacy.putDouble("density", 4.0);
        legacy.putDouble("temperature", 500.0);

        ChemicalState migrated = ChemicalState.load(legacy);

        assertEquals(80.0, migrated.massOf("chemlib:radon"));
        assertEquals(ChemicalState.STATE_VERSION, migrated.save().getInt("state_version"));
    }

    @Test
    void transmutationPreservesUnrelatedComponentsAndAccountsForMassDefect() {
        ChemicalState mixture = new ChemicalState("chemlib:hydrogen", 100.0, 2.0, 900.0, 1.0, 500.0)
            .merge(new ChemicalState("chemlib:nitrogen", 40.0, 1.0, 900.0, 1.0, 200.0));

        ChemicalState product = mixture.transmute("chemlib:hydrogen", "chemlib:helium", 0.75);

        assertEquals(0.0, product.massOf("chemlib:hydrogen"));
        assertEquals(75.0, product.massOf("chemlib:helium"));
        assertEquals(40.0, product.massOf("chemlib:nitrogen"));
        assertEquals(115.0, product.mass());
    }

    @Test
    void componentSplitCannotRemoveOrDuplicateOtherSpecies() {
        ChemicalState mixture = new ChemicalState("chemlib:oxygen", 64.0, 4.0, 293.0, 0.0, 80.0)
            .merge(new ChemicalState("chemlib:argon", 32.0, 2.0, 293.0, 0.0, 40.0));

        ChemicalState.Split split = mixture.splitChemical("chemlib:oxygen", 16.0);

        assertEquals(16.0, split.extracted().massOf("chemlib:oxygen"));
        assertEquals(48.0, split.remainder().massOf("chemlib:oxygen"));
        assertEquals(32.0, split.remainder().massOf("chemlib:argon"));
        assertEquals(mixture.mass(), split.extracted().mass() + split.remainder().mass());
        assertEquals(mixture.energy(), split.extracted().energy() + split.remainder().energy());
    }

    @Test
    void ledgerQueriesAndPurityExposeCompositionWithoutMutation() {
        ChemicalState pure = new ChemicalState("chemlib:oxygen", 16.0, 1.0, 293.0, 0.0, 0.0);
        ChemicalState mixed = pure.merge(new ChemicalState("chemlib:nitrogen", 14.0, 1.0, 293.0, 0.0, 0.0));

        assertTrue(pure.isPure());
        assertFalse(mixed.isPure());
        assertTrue(mixed.contains("chemlib:oxygen"));
        assertFalse(mixed.contains("chemlib:argon"));
    }

    @Test
    void invalidComponentsAreIgnoredAndIdsNormalizeToAir() {
        ChemicalState normalized = new ChemicalState(
            java.util.Map.of("chemlib:oxygen", -1.0, "", 2.0),
            -1.0, -1.0, -1.0, -1.0
        );
        ChemicalState nullLedger = new ChemicalState((java.util.Map<String, Double>) null, 0.0, 293.0, 0.0, 0.0);

        assertEquals("minecraft:air", normalized.chemicalId());
        assertEquals(2.0, normalized.mass());
        assertEquals(0.0, normalized.density());
        assertEquals(0.0, nullLedger.mass());
    }

    @Test
    void noOpAndZeroYieldTransmutationsRemainConservative() {
        ChemicalState state = new ChemicalState("chemlib:oxygen", 16.0, 1.0, 293.0, 0.0, 0.0);

        assertSame(state, state.transmute("chemlib:argon", "chemlib:chlorine", 1.0));
        ChemicalState annihilated = state.transmute("chemlib:oxygen", "chemlib:chlorine", 0.0);
        assertEquals(0.0, annihilated.mass());
        assertFalse(annihilated.contains("chemlib:chlorine"));
    }

    @Test
    void splitBoundsRequestsAndReportsNoOpRemainders() {
        ChemicalState state = new ChemicalState("chemlib:oxygen", 16.0, 1.0, 293.0, 0.0, 8.0);

        ChemicalState.Split none = state.split(-2.0);
        ChemicalState.Split all = state.split(100.0);
        ChemicalState.Split missing = state.splitChemical("chemlib:argon", 3.0);
        ChemicalState.Split componentAll = state.splitChemical("chemlib:oxygen", 100.0);

        assertSame(state, none.remainder());
        assertEquals(16.0, all.extracted().mass());
        assertEquals(0.0, all.remainder().mass());
        assertSame(state, missing.remainder());
        assertEquals(0.0, componentAll.remainder().mass());
    }

    @Test
    void equalityIncludesCompositionAndEveryPhysicalField() {
        ChemicalState state = new ChemicalState("chemlib:oxygen", 16.0, 1.0, 293.0, 0.0, 8.0);

        assertEquals(state, state);
        assertNotEquals(state, "not a state");
        assertNotEquals(state, new ChemicalState("chemlib:nitrogen", 16.0, 1.0, 293.0, 0.0, 8.0));
        assertNotEquals(state, new ChemicalState("chemlib:oxygen", 16.0, 2.0, 293.0, 0.0, 8.0));
        assertNotEquals(state, new ChemicalState("chemlib:oxygen", 16.0, 1.0, 300.0, 0.0, 8.0));
        assertNotEquals(state, new ChemicalState("chemlib:oxygen", 16.0, 1.0, 293.0, 1.0, 8.0));
        assertNotEquals(state, new ChemicalState("chemlib:oxygen", 16.0, 1.0, 293.0, 0.0, 9.0));
        assertEquals(state.hashCode(), new ChemicalState("chemlib:oxygen", 16.0, 1.0, 293.0, 0.0, 8.0).hashCode());
        assertTrue(state.toString().contains("chemlib:oxygen"));
    }

    @Test
    void withEnergyClampsNegativeEnergy() {
        ChemicalState state = new ChemicalState("chemlib:argon", 10.0, 1.0, 300.0, 0.0, 20.0);
        assertEquals(0.0, state.withEnergy(-200.0).energy());
        assertEquals(75.0, state.withEnergy(75.0).energy());
    }

    @Test
    void ambientEvolutionCannotCollapseAMixtureToItsDominantSpecies() {
        ChemicalState mixture = new ChemicalState("chemlib:hydrogen", 100.0, 2.0, 700.0, 0.2, 100.0)
            .merge(new ChemicalState("chemlib:helium", 50.0, 1.0, 700.0, 0.2, 50.0));

        ChemicalState settled = EmergentMath.coolAndSettle(mixture, com.gerald.latentchemlib.data.ChemicalTraits.fallback());
        ChemicalState agitated = EmergentMath.chamberAgitation(mixture, com.gerald.latentchemlib.data.MachineProfile.defaults());

        assertEquals(2, settled.components().size());
        assertEquals(2, agitated.components().size());
        assertEquals(settled.mass(), settled.massOf("chemlib:hydrogen") + settled.massOf("chemlib:helium"));
        assertEquals(mixture.massOf("chemlib:hydrogen"), agitated.massOf("chemlib:hydrogen"));
        assertEquals(mixture.massOf("chemlib:helium"), agitated.massOf("chemlib:helium"));
    }

    @Test
    void cloudDiffusionTierBecomesMoreTransparentAsDensityFalls() {
        assertEquals(0, ChemicalCloudVisuals.diffusionTier(new ChemicalState("chemlib:chlorine", 100.0, 3.0, 293.0, 0.0, 0.0)));
        assertEquals(1, ChemicalCloudVisuals.diffusionTier(new ChemicalState("chemlib:chlorine", 100.0, 2.0, 293.0, 0.0, 0.0)));
        assertEquals(2, ChemicalCloudVisuals.diffusionTier(new ChemicalState("chemlib:chlorine", 100.0, 0.5, 293.0, 0.0, 0.0)));
        assertEquals(3, ChemicalCloudVisuals.diffusionTier(new ChemicalState("chemlib:chlorine", 100.0, 0.1, 293.0, 0.0, 0.0)));
    }
}
