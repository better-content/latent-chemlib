package com.gerald.latentchemlib.api;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsotopeEnsembleTest {
    @Test
    void naturalCompositionIsImplicitAndEmpty() {
        IsotopeEnsemble natural = IsotopeEnsemble.natural();

        assertTrue(natural.isNatural());
        assertEquals(0, natural.save().getIntArray("a").length);
        assertEquals(natural, IsotopeEnsemble.load(new CompoundTag()));
    }

    @Test
    void weightsNormalizeToCompactFixedPointAndRoundTrip() {
        IsotopeEnsemble uranium = IsotopeEnsemble.of(
            Map.of(235, 0.007204, 238, 0.992742, 234, 0.000054),
            IsotopeEnsemble.Binding.REVERSIBLE
        );

        assertFalse(uranium.isNatural());
        assertEquals(IsotopeEnsemble.PARTS_PER_MILLION,
            uranium.abundancePpm().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(IsotopeEnsemble.Binding.REVERSIBLE, uranium.binding());
        assertEquals(uranium, IsotopeEnsemble.load(uranium.save()));
        assertEquals(234, uranium.select(0.0));
        assertEquals(238, uranium.select(0.999999));
    }

    @Test
    void purePermanentBindingAndMalformedNbtAreSafe() {
        IsotopeEnsemble pure = IsotopeEnsemble.pure(235, IsotopeEnsemble.Binding.PERMANENT);
        CompoundTag malformed = new CompoundTag();
        malformed.putIntArray("a", new int[] {235, 238});
        malformed.putIntArray("p", new int[] {1_000_000});

        assertTrue(pure.isPure());
        assertEquals(1.0, pure.fraction(235));
        assertEquals(0.0, pure.fraction(238));
        assertEquals(IsotopeEnsemble.Binding.PERMANENT, IsotopeEnsemble.load(pure.save()).binding());
        assertTrue(IsotopeEnsemble.load(malformed).isNatural());
    }
}
