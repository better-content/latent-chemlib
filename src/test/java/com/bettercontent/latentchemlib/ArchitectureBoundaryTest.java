package com.bettercontent.latentchemlib;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureBoundaryTest {
    @Test
    void removedContainmentApiAndMachinesAreNotPackaged() throws Exception {
        ClassLoader loader = ArchitectureBoundaryTest.class.getClassLoader();
        assertFalse(loader.resources("com/bettercontent/latentchemlib/api/IChemicalStateHandler.class").findAny().isPresent());
        assertFalse(loader.resources("com/bettercontent/latentchemlib/api/LatentCapabilities.class").findAny().isPresent());
        assertFalse(loader.resources("com/bettercontent/latentchemlib/blockentity/LatentMachineBlockEntity.class").findAny().isPresent());
        assertFalse(Files.exists(Path.of("src/main/resources/assets/latent_chemlib/blockstates/gas_tank.json")));
        assertFalse(Files.exists(Path.of("src/main/resources/assets/latent_chemlib/models/item/sealed_chemical_cell.json")));
    }

    @Test
    void metadataKeepsOnlyTheOwningRuntimeBoundaries() throws Exception {
        String metadata = Files.readString(Path.of("src/main/resources/META-INF/mods.toml"));
        assertTrue(metadata.contains("modId=\"heat_sync\""));
        assertTrue(metadata.contains("modId=\"chemlib\""));
        assertTrue(metadata.contains("modId=\"adpother\""));
        assertFalse(metadata.contains("modId=\"create\""));
        assertFalse(metadata.contains("modId=\"pneumaticcraft\""));
        assertFalse(metadata.contains("modId=\"kotlinforforge\""));
    }
}
