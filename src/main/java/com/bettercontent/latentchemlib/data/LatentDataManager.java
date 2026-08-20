package com.bettercontent.latentchemlib.data;

import com.bettercontent.latentchemlib.LatentChemlibMod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.smashingmods.chemlib.api.Chemical;
import com.smashingmods.chemlib.api.Element;
import com.smashingmods.chemlib.api.MatterState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class LatentDataManager implements PreparableReloadListener {
    public static final LatentDataManager INSTANCE = new LatentDataManager();
    private static final Gson GSON = new Gson();

    private volatile Map<String, ChemicalTraits> traits = Map.of();
    private volatile SchedulerProfile schedulerProfile = SchedulerProfile.defaults();
    private volatile MachineProfile machineProfile = MachineProfile.defaults();
    private volatile NuclearPhenomenaProfile nuclearPhenomenaProfile = NuclearPhenomenaProfile.defaults();
    private volatile List<ReactionRule> reactionRules = List.of();
    private volatile List<NuclearDecayRule> nuclearDecayRules = List.of();
    private volatile List<NuclearFormRule> nuclearFormRules = List.of();
    private volatile IsotopeCatalog isotopeCatalog = IsotopeCatalog.empty();

    public ChemicalTraits traits(String chemicalId) {
        ChemicalTraits configured = traits.get(chemicalId);
        return configured == null ? deriveFromRegistry(chemicalId) : configured;
    }

    public SchedulerProfile schedulerProfile() {
        return schedulerProfile;
    }

    public MachineProfile machineProfile() {
        return machineProfile;
    }

    public NuclearPhenomenaProfile nuclearPhenomenaProfile() {
        return nuclearPhenomenaProfile;
    }

    public List<ReactionRule> reactionRules() {
        return reactionRules;
    }

    public List<NuclearDecayRule> nuclearDecayRules() {
        return nuclearDecayRules;
    }

    public List<NuclearFormRule> nuclearFormRules() {
        return nuclearFormRules;
    }

    public IsotopeCatalog isotopeCatalog() {
        return isotopeCatalog;
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager, ProfilerFiller prepProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> load(resourceManager), backgroundExecutor)
            .thenCompose(barrier::wait)
            .thenAcceptAsync(snapshot -> {
                traits = snapshot.traits();
                schedulerProfile = snapshot.schedulerProfile();
                machineProfile = snapshot.machineProfile();
                nuclearPhenomenaProfile = snapshot.nuclearPhenomenaProfile();
                reactionRules = snapshot.reactionRules();
                nuclearDecayRules = snapshot.nuclearDecayRules();
                nuclearFormRules = snapshot.nuclearFormRules();
                isotopeCatalog = snapshot.isotopeCatalog();
                LatentChemlibMod.LOGGER.info(
                    "Loaded {} latent chemical trait overrides, {} reaction rules, {} nuclear decay rules, {} nuclear form rules, and {} known isotopes",
                    traits.size(),
                    reactionRules.size(),
                    nuclearDecayRules.size(),
                    nuclearFormRules.size(),
                    isotopeCatalog.allKnown().size()
                );
            }, gameExecutor);
    }

    private Snapshot load(ResourceManager resourceManager) {
        Map<String, ChemicalTraits> loadedTraits = new HashMap<>();
        java.util.ArrayList<ReactionRule> loadedRules = new java.util.ArrayList<>();
        java.util.ArrayList<NuclearDecayRule> loadedDecayRules = new java.util.ArrayList<>();
        java.util.ArrayList<NuclearFormRule> loadedFormRules = new java.util.ArrayList<>();
        Map<String, IsotopeDefinition> loadedIsotopes = new HashMap<>();
        resourceManager.listResources("chemical_traits", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (var reader = resource.openAsReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                String chemicalId = text(json, "chemical", id.getNamespace() + ":" + id.getPath());
                loadedTraits.put(chemicalId, traitsFromJson(json, deriveFromRegistry(chemicalId)));
            } catch (Exception ex) {
                LatentChemlibMod.LOGGER.warn("Ignoring invalid latent chemical trait file {}", id, ex);
            }
        });

        SchedulerProfile profile = SchedulerProfile.defaults();
        for (var entry : resourceManager.listResources("scheduler_profiles", id -> id.getPath().endsWith("default.json")).entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                profile = new SchedulerProfile(
                    integer(json, "machine_updates_per_second", profile.machineUpdatesPerSecond()),
                    integer(json, "neighbor_ops_per_second", profile.neighborOpsPerSecond()),
                    integer(json, "escape_scans_per_second", profile.escapeScansPerSecond()),
                    integer(json, "nuclear_surface_scans_per_second", integer(json, "nuclear_inventory_scans_per_second", profile.nuclearSurfaceScansPerSecond())),
                    integer(json, "nuclear_stack_evaluations_per_second", profile.nuclearStackEvaluationsPerSecond()),
                    integer(json, "nuclear_state_evaluations_per_second", profile.nuclearStateEvaluationsPerSecond()),
                    integer(json, "nuclear_mutations_per_second", integer(json, "stack_mutations_per_second", profile.nuclearMutationsPerSecond())),
                    integer(json, "nuclear_radiation_emissions_per_second", integer(json, "heat_radiation_emissions_per_second", profile.nuclearRadiationEmissionsPerSecond())),
                    integer(json, "nuclear_heat_emissions_per_second", integer(json, "heat_radiation_emissions_per_second", profile.nuclearHeatEmissionsPerSecond()))
                );
            } catch (Exception ex) {
                LatentChemlibMod.LOGGER.warn("Ignoring invalid latent scheduler profile {}", entry.getKey(), ex);
            }
        }
        MachineProfile loadedMachineProfile = MachineProfile.defaults();
        for (var entry : resourceManager.listResources("machine_profiles", id -> id.getPath().endsWith("default.json")).entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (MachineProfile.hasSupportedSchema(json)) {
                    loadedMachineProfile = MachineProfile.fromJson(json);
                } else {
                    LatentChemlibMod.LOGGER.warn("Ignoring latent machine profile with unsupported or missing schema {}", entry.getKey());
                }
            } catch (Exception ex) {
                LatentChemlibMod.LOGGER.warn("Ignoring invalid latent machine profile {}", entry.getKey(), ex);
            }
        }
        NuclearPhenomenaProfile loadedNuclearPhenomenaProfile = NuclearPhenomenaProfile.defaults();
        for (var entry : resourceManager.listResources("nuclear_phenomena", id -> id.getPath().endsWith("default.json")).entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (NuclearPhenomenaProfile.hasSupportedSchema(json)) {
                    loadedNuclearPhenomenaProfile = NuclearPhenomenaProfile.fromJson(json);
                } else {
                    LatentChemlibMod.LOGGER.warn("Ignoring latent nuclear phenomena profile with unsupported or missing schema {}", entry.getKey());
                }
            } catch (Exception ex) {
                LatentChemlibMod.LOGGER.warn("Ignoring invalid latent nuclear phenomena profile {}", entry.getKey(), ex);
            }
        }
        resourceManager.listResources("reaction_rules", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (var reader = resource.openAsReader()) {
                JsonElement root = GSON.fromJson(reader, JsonElement.class);
                JsonArray rules = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("rules");
                if (rules == null) return;
                for (JsonElement element : rules) {
                    if (element != null && element.isJsonObject()) loadedRules.add(ruleFromJson(element.getAsJsonObject(), id.toString()));
                }
            } catch (Exception ex) {
                LatentChemlibMod.LOGGER.warn("Ignoring invalid latent reaction rule file {}", id, ex);
            }
        });
        resourceManager.listResources("nuclear_decay", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (var reader = resource.openAsReader()) {
                JsonElement root = GSON.fromJson(reader, JsonElement.class);
                JsonArray rules = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("rules");
                if (rules == null) return;
                for (JsonElement element : rules) {
                    if (element != null && element.isJsonObject()) loadedDecayRules.add(decayRuleFromJson(element.getAsJsonObject(), id.toString()));
                }
            } catch (Exception ex) {
                LatentChemlibMod.LOGGER.warn("Ignoring invalid latent nuclear decay file {}", id, ex);
            }
        });
        resourceManager.listResources("isotope_catalog", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (var reader = resource.openAsReader()) {
                JsonElement root = GSON.fromJson(reader, JsonElement.class);
                JsonArray isotopes = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("isotopes");
                if (isotopes == null) return;
                for (JsonElement element : isotopes) {
                    if (element == null || !element.isJsonObject()) continue;
                    IsotopeDefinition definition = isotopeFromJson(element.getAsJsonObject());
                    if (!definition.elementId().isBlank() && definition.massNumber() > 0) {
                        loadedIsotopes.put(isotopeKey(definition.elementId(), definition.massNumber()), definition);
                    }
                }
            } catch (Exception ex) {
                LatentChemlibMod.LOGGER.warn("Ignoring invalid latent isotope catalogue file {}", id, ex);
            }
        });
        resourceManager.listResources("nuclear_forms", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (var reader = resource.openAsReader()) {
                JsonElement root = GSON.fromJson(reader, JsonElement.class);
                JsonArray forms = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("forms");
                if (forms == null) return;
                for (JsonElement element : forms) {
                    if (element == null || !element.isJsonObject()) continue;
                    JsonObject json = element.getAsJsonObject();
                    NuclearFormRule rule = new NuclearFormRule(
                        text(json, "suffix", ""), number(json, "material_units", 1.0),
                        text(json, "item", ""), text(json, "item_tag", ""),
                        text(json, "block", ""), text(json, "block_tag", ""),
                        text(json, "family", ""), number(json, "radiation_strength", 0.0),
                        number(json, "heat_strength", 0.0), bool(json, "natural_worldgen_inert", false),
                        bool(json, "placed_always_active", false)
                    );
                    if (!rule.suffix().isBlank() || rule.fixedProfile()) loadedFormRules.add(rule);
                }
            } catch (Exception ex) {
                LatentChemlibMod.LOGGER.warn("Ignoring invalid latent nuclear form file {}", id, ex);
            }
        });
        for (NuclearDecayRule rule : loadedDecayRules) {
            int massNumber = rule.isotopeMassNumber();
            if (massNumber <= 0) continue;
            loadedIsotopes.putIfAbsent(
                isotopeKey(rule.inputChemical(), massNumber),
                new IsotopeDefinition(rule.inputChemical(), massNumber, rule.isotope(), 0.0, rule.halfLifeSeconds(), rule.outputChemical(), false)
            );
        }
        return new Snapshot(
            Map.copyOf(loadedTraits), profile, loadedMachineProfile, loadedNuclearPhenomenaProfile,
            List.copyOf(loadedRules), List.copyOf(loadedDecayRules), List.copyOf(loadedFormRules),
            new IsotopeCatalog(List.copyOf(loadedIsotopes.values()))
        );
    }

    private ChemicalTraits traitsFromJson(JsonObject json, ChemicalTraits fallback) {
        return new ChemicalTraits(
            number(json, "volatility", fallback.volatility()),
            number(json, "cohesion", fallback.cohesion()),
            number(json, "heat_capacity", fallback.heatCapacity()),
            number(json, "conductivity", fallback.conductivity()),
            number(json, "ionization_tendency", fallback.ionizationTendency()),
            number(json, "neutron_instability", fallback.neutronInstability()),
            number(json, "neutron_absorption", fallback.neutronAbsorption()),
            number(json, "scattering", fallback.scattering()),
            number(json, "containment_strength", fallback.containmentStrength()),
            curve(json == null ? null : json.getAsJsonObject("fusion_barrier"), fallback.fusionBarrier())
        );
    }

    public ChemicalTraits deriveFromRegistry(String chemicalId) {
        ChemicalTraits fallback = ChemicalTraits.fallback();
        ResourceLocation id = ResourceLocation.tryParse(chemicalId);
        if (id == null || !(ForgeRegistries.ITEMS.getValue(id) instanceof Chemical chemical)) {
            return fallback;
        }
        Object item = ForgeRegistries.ITEMS.getValue(id);
        double mass = item instanceof Element element ? Math.max(1.0, element.getAtomicNumber()) : Math.max(1.0, chemical.getAbbreviation().length() * 4.0);
        double volatilitySeed = chemical.getMatterState() == MatterState.GAS ? 2.4 : chemical.getMatterState() == MatterState.LIQUID ? 0.65 : 0.12;
        double instability = item instanceof Element element ? Math.max(0.0, Math.pow(Math.max(0, element.getAtomicNumber() - 82), 2.0) / 144.0) : 0.0;
        return new ChemicalTraits(
            volatilitySeed / Math.sqrt(mass),
            Math.sqrt(mass) * 0.18,
            0.8 + mass * 0.015,
            0.06 + chemical.getEffects().size() * 0.02,
            0.04 + 1.0 / Math.sqrt(mass),
            instability,
            0.04 + mass * 0.002,
            0.03 + Math.log1p(mass) * 0.01,
            0.0,
            new NumericCurve(PresetCurve.EXPONENTIAL, 1_400.0, 55.0, 1.35, 0.0, 120_000.0)
        );
    }

    private static NumericCurve curve(JsonObject json, NumericCurve fallback) {
        if (json == null) return fallback;
        PresetCurve type = PresetCurve.valueOf(text(json, "type", fallback.type().name()).toUpperCase());
        return new NumericCurve(
            type,
            number(json, "offset", fallback.offset()),
            number(json, "scale", fallback.scale()),
            number(json, "exponent", fallback.exponent()),
            number(json, "midpoint", fallback.midpoint()),
            number(json, "max", fallback.max())
        );
    }

    private static String text(JsonObject json, String key, String fallback) {
        JsonElement value = json == null ? null : json.get(key);
        return value == null ? fallback : value.getAsString();
    }

    private static double number(JsonObject json, String key, double fallback) {
        JsonElement value = json == null ? null : json.get(key);
        return value == null ? fallback : value.getAsDouble();
    }

    private static int integer(JsonObject json, String key, int fallback) {
        JsonElement value = json == null ? null : json.get(key);
        return value == null ? fallback : value.getAsInt();
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        JsonElement value = json == null ? null : json.get(key);
        return value == null ? fallback : value.getAsBoolean();
    }

    private static IsotopeDefinition isotopeFromJson(JsonObject json) {
        return new IsotopeDefinition(
            text(json, "element", text(json, "element_id", "")),
            integer(json, "mass_number", 0),
            text(json, "symbol", ""),
            number(json, "natural_abundance", 0.0),
            number(json, "half_life_seconds", 0.0),
            text(json, "daughter_chemical", ""),
            bool(json, "stable", false)
        );
    }

    private static String isotopeKey(String elementId, int massNumber) {
        return elementId + '#' + massNumber;
    }

    private static ReactionRule ruleFromJson(JsonObject json, String fallbackId) {
        return new ReactionRule(
            text(json, "id", fallbackId),
            text(json, "input_chemical", "minecraft:air"),
            text(json, "output_chemical", ""),
            text(json, "output_item", ""),
            number(json, "min_mass", 1.0),
            number(json, "min_temperature", 293.0),
            number(json, "min_charge", 0.0),
            number(json, "min_energy", 0.0),
            number(json, "output_mass_ratio", 1.0),
            number(json, "temperature_delta", 0.0),
            number(json, "charge_delta", 0.0),
            number(json, "energy_delta", 0.0),
            (float) number(json, "heat_cost", 0.0),
            (float) number(json, "heat_emission", 0.0)
        );
    }

    private static NuclearDecayRule decayRuleFromJson(JsonObject json, String fallbackId) {
        return new NuclearDecayRule(
            text(json, "id", fallbackId),
            text(json, "input_chemical", "minecraft:air"),
            text(json, "output_chemical", ""),
            text(json, "output_item", ""),
            text(json, "isotope", ""),
            text(json, "output_isotope", ""),
            number(json, "half_life_seconds", 0.0),
            number(json, "output_mass_ratio", 1.0),
            number(json, "temperature_delta", 0.0),
            number(json, "charge_delta", 0.0),
            number(json, "energy_delta", 0.0),
            (float) number(json, "heat_emission", 0.0)
        );
    }

    private record Snapshot(
        Map<String, ChemicalTraits> traits,
        SchedulerProfile schedulerProfile,
        MachineProfile machineProfile,
        NuclearPhenomenaProfile nuclearPhenomenaProfile,
        List<ReactionRule> reactionRules,
        List<NuclearDecayRule> nuclearDecayRules,
        List<NuclearFormRule> nuclearFormRules,
        IsotopeCatalog isotopeCatalog
    ) {}
}
