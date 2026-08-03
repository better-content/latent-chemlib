package com.gerald.latentchemlib.data;

import com.gerald.latentchemlib.api.IsotopeEnsemble;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsotopeCatalogTest {
    @Test
    void catalogueEnumeratesAndResolvesNaturalVector() {
        IsotopeCatalog catalog = new IsotopeCatalog(List.of(
            new IsotopeDefinition("chemlib:uranium", 238, "U-238", 99.2742, 0.0, "", false),
            new IsotopeDefinition("chemlib:hydrogen", 1, "H-1", 99.9885, 0.0, "", true),
            new IsotopeDefinition("chemlib:uranium", 235, "U-235", 0.7204, 0.0, "", false)
        ));

        assertEquals(3, catalog.allKnown().size());
        assertEquals(2, catalog.knownFor("chemlib:uranium").size());
        assertEquals(235, catalog.find("chemlib:uranium", 235).orElseThrow().massNumber());
        assertTrue(catalog.find("chemlib:uranium", 234).isEmpty());
        IsotopeEnsemble natural = catalog.naturalEnsemble("chemlib:uranium");
        assertEquals(IsotopeEnsemble.PARTS_PER_MILLION,
            natural.abundancePpm().values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(catalog.naturalEnsemble("chemlib:unknown").isNatural());
    }
}
