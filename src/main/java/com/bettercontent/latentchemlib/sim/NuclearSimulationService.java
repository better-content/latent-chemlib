package com.bettercontent.latentchemlib.sim;


import com.bettercontent.latentchemlib.api.IsotopeEnsemble;
import com.bettercontent.latentchemlib.api.IsotopeItemData;
import com.bettercontent.latentchemlib.api.LatentEmissionProfiles;
import com.bettercontent.latentchemlib.data.ChemicalTraits;
import com.bettercontent.latentchemlib.data.LatentDataManager;
import com.bettercontent.latentchemlib.data.NuclearDecayRule;
import com.smashingmods.chemlib.api.Element;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;
import java.util.EnumMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class NuclearSimulationService {
    public static final NuclearSimulationService INSTANCE = new NuclearSimulationService();
    public enum NuclearEventType {
        DECAY,
        FISSION
    }

    public enum ProcessStatus {
        SKIPPED,
        UNCHANGED,
        MUTATED,
        BUDGET_EXHAUSTED
    }

    public record NuclearEnvironment(double moderation, double absorption, double externalFlux, double contactFraction) {
        public static final NuclearEnvironment EMPTY = new NuclearEnvironment(0.0, 0.0, 0.0, 0.0);

        public NuclearEnvironment(double moderation, double absorption, double externalFlux) {
            this(moderation, absorption, externalFlux, 1.0);
        }
    }

    public record NuclearStateEvent(
        ChemicalState outputState,
        ItemStack outputItem,
        float heatEmission,
        int radiationLevel,
        NuclearEventType type
    ) {}

    public record NuclearStackEvent(
        String outputChemical,
        Item outputItem,
        int inputCount,
        int outputCount,
        float heatEmission,
        int radiationLevel,
        NuclearEventType type,
        int inputIsotopeMassNumber,
        int outputIsotopeMassNumber
    ) {
        public NuclearStackEvent(
            String outputChemical, Item outputItem, int inputCount, int outputCount,
            float heatEmission, int radiationLevel, NuclearEventType type
        ) {
            this(outputChemical, outputItem, inputCount, outputCount, heatEmission, radiationLevel, type, 0, 0);
        }
    }

    public record StateProcessResult(ProcessStatus status, ChemicalState state) {
        public boolean mutated() {
            return status == ProcessStatus.MUTATED;
        }

        public boolean budgetExhausted() {
            return status == ProcessStatus.BUDGET_EXHAUSTED;
        }
    }

    public ProcessStatus processStack(ServerLevel level, BlockPos pos, ItemStack stack, double elapsedSeconds, Consumer<ItemStack> outputSink) {
        return processStack(level, pos, stack, elapsedSeconds, environment(level, pos), outputSink);
    }

    public ProcessStatus processStack(ServerLevel level, BlockPos pos, ItemStack stack, double elapsedSeconds, NuclearEnvironment environment, Consumer<ItemStack> outputSink) {
        return processStack(level, pos, stack, elapsedSeconds, environment,
            (ignored, output) -> { if (outputSink != null) outputSink.accept(output); });
    }

    public ProcessStatus processStack(ServerLevel level, BlockPos pos, ItemStack stack, double elapsedSeconds,
        NuclearEnvironment environment,
        BiConsumer<NuclearEventType, ItemStack> outputSink) {
        if (!canProcessStack(stack, environment)) return ProcessStatus.SKIPPED;
        Optional<RadioactiveFormResolver.ResolvedForm> fixedForm = RadioactiveFormResolver.INSTANCE.resolve(stack)
            .filter(RadioactiveFormResolver.ResolvedForm::fixedProfile);
        if (fixedForm.isPresent()) {
            return emitFixedProfile(level, pos, fixedForm.get(), stack.getCount())
                ? ProcessStatus.UNCHANGED : ProcessStatus.BUDGET_EXHAUSTED;
        }
        Optional<RadioactiveFormResolver.ResolvedForm> form = RadioactiveFormResolver.INSTANCE.resolve(stack);
        if (form.isPresent()) return processMaterialStack(level, pos, stack, form.get(), elapsedSeconds, environment);
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_STACK_EVALUATIONS, 1)) {
            return ProcessStatus.BUDGET_EXHAUSTED;
        }
        long elapsedTicks = Math.max(1L, Math.round(Math.max(0.0, elapsedSeconds) * 20.0));
        LoadedExposureClock.Window exposure = LoadedExposureClock.preview(stack.getTag(), elapsedTicks, level.getRandom().nextLong());
        Optional<NuclearStackEvent> event = evaluateStack(stack, environment, exposure);
        if (event.isEmpty()) {
            LoadedExposureClock.commit(stack.getOrCreateTag(), exposure);
            return ProcessStatus.UNCHANGED;
        }
        ProcessStatus status = applyStackEvent(level, pos, stack, event.get(), outputSink);
        if (status != ProcessStatus.BUDGET_EXHAUSTED && !stack.isEmpty()) {
            LoadedExposureClock.commit(stack.getOrCreateTag(), exposure);
        }
        return status;
    }

    public ProcessStatus applyStackEvent(ServerLevel level, BlockPos pos, ItemStack stack, NuclearStackEvent nuclearEvent, Consumer<ItemStack> outputSink) {
        return applyStackEvent(level, pos, stack, nuclearEvent,
            (ignored, output) -> { if (outputSink != null) outputSink.accept(output); });
    }

    public ProcessStatus applyStackEvent(ServerLevel level, BlockPos pos, ItemStack stack, NuclearStackEvent nuclearEvent,
        BiConsumer<NuclearEventType, ItemStack> outputSink) {
        if (!reserveConsequences(level, nuclearEvent.heatEmission(), nuclearEvent.radiationLevel())) {
            return ProcessStatus.BUDGET_EXHAUSTED;
        }
        int consumed = Math.min(stack.getCount(), Math.max(1, nuclearEvent.inputCount()));
        ItemStack inputSnapshot = stack.copy();
        inputSnapshot.setCount(consumed);
        stack.shrink(consumed);
        ItemStack output = new ItemStack(nuclearEvent.outputItem(), nuclearEvent.outputCount());
        float retained = emitReserved(level, pos, nuclearEvent.heatEmission(), nuclearEvent.radiationLevel());
        bindDiscreteOutputState(inputSnapshot, output, nuclearEvent, retained);
        if (outputSink != null && !output.isEmpty()) outputSink.accept(nuclearEvent.type(), output);
        return ProcessStatus.MUTATED;
    }

    public StateProcessResult processChemicalState(ServerLevel level, BlockPos pos, ChemicalState state, double elapsedSeconds, Consumer<ItemStack> outputSink) {
        NuclearEnvironment environment = environment(level, pos);
        long elapsedTicks = Math.max(1L, Math.round(Math.max(0.0, elapsedSeconds) * 20.0));
        long endTick = level.getGameTime();
        LoadedExposureClock.Window window = new LoadedExposureClock.Window(
            Math.max(0L, endTick - elapsedTicks), endTick,
            level.getSeed() ^ (pos == null ? 0L : pos.asLong())
        );
        return processChemicalState(
            level, pos, state, elapsedTicks / 20.0, environment, outputSink,
            RandomSource.create(LoadedExposureClock.deterministicSeed(window, "chemical-state"))
        );
    }

    /** Persistent placed matter supplies its own exactly-once exposure window and deterministic seed. */
    public StateProcessResult processPlacedState(
        ServerLevel level,
        BlockPos pos,
        ChemicalState state,
        double elapsedSeconds,
        NuclearEnvironment environment,
        RandomSource random
    ) {
        return processChemicalState(level, pos, state, elapsedSeconds, environment, null, random);
    }

    private StateProcessResult processChemicalState(
        ServerLevel level,
        BlockPos pos,
        ChemicalState state,
        double elapsedSeconds,
        NuclearEnvironment environment,
        Consumer<ItemStack> outputSink,
        RandomSource random
    ) {
        if (!isNuclearRelevant(state)) return new StateProcessResult(ProcessStatus.SKIPPED, state);
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_STATE_EVALUATIONS, 1)) {
            return new StateProcessResult(ProcessStatus.BUDGET_EXHAUSTED, state);
        }
        Optional<NuclearStateEvent> event = evaluateState(state, elapsedSeconds, environment, random);
        if (event.isEmpty()) return new StateProcessResult(ProcessStatus.UNCHANGED, state);
        NuclearStateEvent nuclearEvent = event.get();
        if (!reserveConsequences(level, nuclearEvent.heatEmission(), nuclearEvent.radiationLevel())) {
            return new StateProcessResult(ProcessStatus.BUDGET_EXHAUSTED, state);
        }
        if (outputSink != null && nuclearEvent.outputItem() != null && !nuclearEvent.outputItem().isEmpty()) outputSink.accept(nuclearEvent.outputItem().copy());
        float retained = emitReserved(level, pos, nuclearEvent.heatEmission(), nuclearEvent.radiationLevel());
        maybeMelt(level, pos, nuclearEvent.type(), nuclearEvent.heatEmission());
        return new StateProcessResult(ProcessStatus.MUTATED, retainHeat(nuclearEvent.outputState(), retained));
    }

    public Optional<NuclearStateEvent> evaluateState(ChemicalState state, double elapsedSeconds, NuclearEnvironment environment, RandomSource random) {
        ChemicalState identified = initializeMissingDecayIdentity(state);
        if (!isNuclearRelevant(identified)) return Optional.empty();
        Optional<NuclearStateEvent> fission = fissionStateEvent(identified, environment);
        if (fission.isPresent()) return fission;
        for (NuclearDecayRule rule : LatentDataManager.INSTANCE.nuclearDecayRules()) {
            if (!rule.matches(identified)) continue;
            var decay = NuclearPhenomenaMath.continuousDecay(
                identified, rule, elapsedSeconds, LatentDataManager.INSTANCE.nuclearPhenomenaProfile()
            );
            if (decay.isPresent()) {
                return Optional.of(new NuclearStateEvent(
                    decay.get().output(),
                    null,
                    decay.get().heatEmission(),
                    radiationFromHeat(decay.get().heatEmission()),
                    NuclearEventType.DECAY
                ));
            }
            break;
        }
        return Optional.empty();
    }

    public Optional<NuclearStackEvent> evaluateStack(ItemStack stack, double elapsedSeconds, NuclearEnvironment environment, RandomSource random) {
        Optional<RadioactiveFormResolver.ResolvedForm> form = RadioactiveFormResolver.INSTANCE.resolve(stack);
        if (form.isEmpty()) return Optional.empty();
        long elapsedTicks = Math.max(1L, Math.round(Math.max(0.0, elapsedSeconds) * 20.0));
        LoadedExposureClock.Window exposure = LoadedExposureClock.preview(stack.getTag(), elapsedTicks, random.nextLong());
        return evaluateStack(stack, environment, exposure);
    }

    private Optional<NuclearStackEvent> evaluateStack(ItemStack stack, NuclearEnvironment environment, LoadedExposureClock.Window exposure) {
        Optional<RadioactiveFormResolver.ResolvedForm> form = RadioactiveFormResolver.INSTANCE.resolve(stack);
        if (form.isEmpty()) return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(form.get().chemicalId());
        if (id == null) return Optional.empty();
        ChemicalState state = NuclearStackData.peekState(stack, form.get()).withMass(form.get().unitMass() * stack.getCount());
        double elapsedSeconds = Math.max(0L, exposure.endTick() - exposure.startTick()) / 20.0;
        IsotopeEnsemble ensemble = IsotopeItemData.explicit(stack);
        int selectedMass = ensemble.isNatural()
            ? 0
            : ensemble.select(LoadedExposureClock.deterministicRoll(exposure, "isotope:" + id));
        for (NuclearDecayRule rule : LatentDataManager.INSTANCE.nuclearDecayRules()) {
            if (!rule.matches(state)) continue;
            if (selectedMass > 0 && selectedMass != rule.isotopeMassNumber()) continue;
            double decayRoll = LoadedExposureClock.deterministicRoll(exposure, "decay:" + rule.id());
            if (decayRoll < rule.decayProbability(elapsedSeconds)) {
                Item daughter = rule.outputChemicalItemValue();
                if (isMissing(daughter)) return Optional.empty();
                return Optional.of(new NuclearStackEvent(
                    rule.outputChemical(),
                    daughter,
                    1,
                    1,
                    rule.heatEmission(),
                    radiationFromHeat(rule.heatEmission()),
                    NuclearEventType.DECAY,
                    rule.isotopeMassNumber(),
                    rule.daughterIsotopeMassNumber()
                ));
            }
            break;
        }
        return Optional.empty();
    }

    public double neutronFlux(ChemicalState state, NuclearEnvironment environment) {
        if (state.mass() <= 0.0) return Math.max(0.0, environment.externalFlux());
        double base = 0.0;
        for (var component : state.components().entrySet()) {
            ChemicalState componentState = new ChemicalState(
                component.getKey(), component.getValue(),
                state.density() * component.getValue() / state.mass(),
                state.temperature(), state.charge(),
                state.energy() * component.getValue() / state.mass()
            );
            base += EmergentMath.neutronFlux(componentState, traits(component.getKey()), environment.moderation());
        }
        double absorbed = Math.max(0.0, 1.0 - Math.min(0.95, environment.absorption()));
        return Math.max(0.0, (base + environment.externalFlux()) * absorbed);
    }

    public boolean isNuclearRelevant(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return RadioactiveFormResolver.INSTANCE.resolve(stack).isPresent();
    }

    public boolean isNuclearRelevant(ChemicalState state) {
        if (state.mass() <= 0.0) return false;
        for (NuclearDecayRule rule : LatentDataManager.INSTANCE.nuclearDecayRules()) {
            if (rule.matches(state)) return true;
        }
        for (String chemicalId : state.components().keySet()) {
            IsotopeEnsemble explicit = state.isotopesOf(chemicalId);
            if (explicit.isNatural()) continue;
            if (LatentDataManager.INSTANCE.isotopeCatalog().knownFor(chemicalId).stream()
                .anyMatch(isotope -> !isotope.stable() && isotope.halfLifeSeconds() > 0.0
                    && explicit.fraction(isotope.massNumber()) > 0.0)) return true;
        }
        return false;
    }

    public boolean canProcessStack(ItemStack stack, NuclearEnvironment environment) {
        return isNuclearRelevant(stack);
    }

    public double intrinsicFlux(ItemStack stack, NuclearEnvironment environment) {
        Optional<RadioactiveFormResolver.ResolvedForm> form = RadioactiveFormResolver.INSTANCE.resolve(stack);
        if (form.isEmpty() || form.get().fixedProfile()) return 0.0;
        return neutronFlux(NuclearStackData.peekState(stack, form.get()).withMass(form.get().unitMass() * stack.getCount()), new NuclearEnvironment(
            environment.moderation(), environment.absorption(), 0.0, environment.contactFraction()
        ));
    }

    public static NuclearEnvironment environment(ServerLevel level, BlockPos pos) {
        if (pos == null) return NuclearEnvironment.EMPTY;
        double moderation = 0.0;
        double absorption = 0.0;
        int contacts = 0;
        for (Direction direction : Direction.values()) {
            BlockState state = level.getBlockState(pos.relative(direction));
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            String key = id == null ? "" : id.toString();
            if (state.getFluidState().is(FluidTags.WATER) || key.contains("water") || key.contains("ice") || key.contains("graphite") || key.contains("moderator")) {
                moderation += 0.35;
            }
            if (key.contains("lead") || key.contains("boron") || key.contains("cadmium") || key.contains("absorber") || key.contains("concrete") || key.contains("obsidian")) {
                absorption += 0.12;
            }
            if (!state.isAir() && (!state.canBeReplaced() || !state.getFluidState().isEmpty())) contacts++;
        }
        return new NuclearEnvironment(Math.min(4.0, moderation), Math.min(0.95, absorption), 0.0, contacts / 6.0);
    }

    private ProcessStatus processMaterialStack(ServerLevel level, BlockPos pos, ItemStack stack,
        RadioactiveFormResolver.ResolvedForm form, double elapsedSeconds, NuclearEnvironment environment) {
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_STACK_EVALUATIONS, 1)) return ProcessStatus.BUDGET_EXHAUSTED;
        ChemicalState unitState = NuclearStackData.peekState(stack, form);
        Optional<NuclearStateEvent> event = evaluateState(unitState, elapsedSeconds, environment, level.getRandom());
        if (event.isEmpty()) return ProcessStatus.UNCHANGED;
        NuclearStateEvent nuclearEvent = event.get();
        float totalHeat = nuclearEvent.heatEmission() * Math.max(1, stack.getCount());
        if (!reserveConsequences(level, totalHeat, nuclearEvent.radiationLevel())) return ProcessStatus.BUDGET_EXHAUSTED;
        float retainedTotal = emitReserved(level, pos, totalHeat, nuclearEvent.radiationLevel());
        ChemicalState nextUnit = retainHeat(nuclearEvent.outputState(), retainedTotal / Math.max(1, stack.getCount()));
        NuclearStackData.setState(stack, nextUnit);
        NuclearStackData.bindIdentity(stack, form.formId(), form.isotopeMassNumber());
        NuclearStackData.syncIdentity(stack, nextUnit);
        maybeMelt(level, pos, nuclearEvent.type(), totalHeat);
        return ProcessStatus.MUTATED;
    }

    private Optional<NuclearStateEvent> fissionStateEvent(ChemicalState state, NuclearEnvironment environment) {
        double flux = neutronFlux(state, environment);
        var phenomena = LatentDataManager.INSTANCE.nuclearPhenomenaProfile();
        var fission = NuclearPhenomenaMath.fission(
            state, flux, environment.moderation(), environment.contactFraction(), phenomena, this::isFissileComponent
        );
        return fission.map(result -> new NuclearStateEvent(
            result.output(), null,
            result.heatEmission(), phenomena.fissionRadiationLevel(), NuclearEventType.FISSION
        ));
    }

    private boolean reserveConsequences(ServerLevel level, float heatEmission, int radiationLevel) {
        EnumMap<SimulationBudget, Integer> costs = new EnumMap<>(SimulationBudget.class);
        costs.put(SimulationBudget.NUCLEAR_MUTATIONS, 1);
        if (heatEmission > 0.0f) costs.put(SimulationBudget.NUCLEAR_HEAT_EMISSIONS, 1);
        if (radiationLevel > 0) costs.put(SimulationBudget.NUCLEAR_RADIATION_EMISSIONS, 1);
        return SimulationScheduler.INSTANCE.trySpendAll(level, costs);
    }

    /** Emits a fixed profile through Latent's bounded consequence budgets without touching isotope state. */
    public boolean emitFixedProfile(ServerLevel level, BlockPos pos,
        RadioactiveFormResolver.ResolvedForm form, int count) {
        if (form == null || !form.fixedProfile()) return true;
        int scale = Math.min(LatentEmissionProfiles.MAX_STACK_SCALE, Math.max(1, count));
        double radiation = form.radiationStrength() * scale;
        double heat = form.heatStrength() * scale;
        EnumMap<SimulationBudget, Integer> costs = new EnumMap<>(SimulationBudget.class);
        if (radiation > 0.0) costs.put(SimulationBudget.NUCLEAR_RADIATION_EMISSIONS, 1);
        if (heat > 0.0) costs.put(SimulationBudget.NUCLEAR_HEAT_EMISSIONS, 1);
        if (!SimulationScheduler.INSTANCE.trySpendAll(level, costs)) return false;
        if (pos != null && (radiation > 0.0 || heat > 0.0)) {
            LatentRadiationService.emit(level, pos, radiation, heat);
        }
        return true;
    }

    private float emitReserved(ServerLevel level, BlockPos pos, float heatEmission, int radiationLevel) {
        float retained = heatEmission > 0.0f ? distributeHeat(level, pos, heatEmission) : 0.0f;
        if (pos != null && radiationLevel > 0) LatentRadiationService.emit(level, pos, radiationLevel);
        return retained;
    }

    void emitAmbientHeat(ServerLevel level, BlockPos pos, float heatEmission) {
        if (heatEmission > 0.0f && SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_HEAT_EMISSIONS, 1)) {
            distributeHeat(level, pos, heatEmission);
        }
    }

    private void maybeMelt(ServerLevel level, BlockPos pos, NuclearEventType type, float heatEmission) {
        if (pos == null || type != NuclearEventType.FISSION
            || heatEmission < LatentDataManager.INSTANCE.nuclearPhenomenaProfile().surroundingMeltHeatThreshold()
            || !SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_MUTATIONS, 1)) return;
        int cursor = Math.floorMod((int) (level.getGameTime() ^ pos.asLong()), Direction.values().length);
        ThermalMelting.meltNext(level, pos, cursor);
    }

    private static ChemicalState initializeMissingDecayIdentity(ChemicalState state) {
        if (state.mass() <= 0.0) return state;
        java.util.Map<String, IsotopeEnsemble> identities = new java.util.LinkedHashMap<>(state.componentIsotopes());
        boolean changed = false;
        for (String component : state.components().keySet()) {
            if (identities.containsKey(component)) continue;
            List<NuclearDecayRule> candidates = LatentDataManager.INSTANCE.nuclearDecayRules().stream()
                .filter(rule -> rule.inputChemical().equals(component))
                .toList();
            IsotopeEnsemble ensemble = candidates.size() == 1
                ? IsotopeEnsemble.pure(candidates.get(0).isotopeMassNumber(), IsotopeEnsemble.Binding.PERMANENT)
                : LatentDataManager.INSTANCE.isotopeCatalog().naturalEnsemble(component);
            if (!ensemble.isNatural()) {
                identities.put(component, ensemble);
                changed = true;
            }
        }
        return changed ? state.withComponentIsotopes(identities) : state;
    }

    private boolean isFissileComponent(String chemicalId) {
        ResourceLocation id = ResourceLocation.tryParse(chemicalId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (!(item instanceof Element element)) return false;
        var profile = LatentDataManager.INSTANCE.nuclearPhenomenaProfile();
        double atomicNumber = element.getAtomicNumber();
        return LatentDataManager.INSTANCE.isotopeCatalog().knownFor(chemicalId).stream()
            .filter(isotope -> !isotope.stable() && isotope.halfLifeSeconds() > 0.0)
            .filter(isotope -> isotope.massNumber() >= profile.fissionMinimumIsotopeMassNumber())
            .anyMatch(isotope -> atomicNumber * atomicNumber / isotope.massNumber() >= profile.fissionMinimumFissilityIndex());
    }

    private static ChemicalTraits traits(String chemicalId) {
        try {
            return LatentDataManager.INSTANCE.traits(chemicalId);
        } catch (Throwable ex) {
            return ChemicalTraits.fallback();
        }
    }

    private static float distributeHeat(ServerLevel level, BlockPos pos, float heatEmission) {
        if (pos == null) return heatEmission;
        final double unaccepted = com.bettercontent.heatsync.api.ThermalApi.distributeHeat(level, pos, heatEmission);
        com.bettercontent.latentchemlib.api.event.ChemicalOutcomeEvent.publish(level, pos, com.bettercontent.latentchemlib.api.event.ChemicalOutcomeEvent.Kind.HEAT_ACCEPTED, null, heatEmission - unaccepted);
        // Heat Sync is the only environmental thermal authority. Unaccepted heat
        // remains in the simulated material so an event cannot silently destroy energy.
        return (float) Math.max(0.0, unaccepted);
    }

    private static ChemicalState retainHeat(ChemicalState state, float retained) {
        if (retained <= 0.0f || state.mass() <= 0.0) return state;
        double temperatureRise = retained / Math.max(1.0, state.mass() * traits(state.chemicalId()).heatCapacity());
        return state.withConditions(state.temperature() + temperatureRise, state.charge(), state.energy() + retained);
    }

    private static boolean isMissing(Item item) {
        return item == null || item == Items.AIR;
    }

    private static ItemStack outputStack(Item item, int count) {
        return isMissing(item) ? null : new ItemStack(item, count);
    }

    private static int radiationFromHeat(float heat) {
        if (heat <= 0.0f) return 0;
        return (int) Math.min(24.0f, Math.max(1.0f, heat / 800.0f));
    }

    private static void bindDiscreteOutputState(
        ItemStack input,
        ItemStack output,
        NuclearStackEvent event,
        float retainedHeat
    ) {
        if (input.isEmpty() || output.isEmpty()) return;
        Optional<RadioactiveFormResolver.ResolvedForm> inputForm = RadioactiveFormResolver.INSTANCE.resolve(input);
        if (inputForm.isEmpty()) return;
        RadioactiveFormResolver.ResolvedForm form = inputForm.get();
        NuclearDecayRule decay = LatentDataManager.INSTANCE.nuclearDecayRules().stream()
            .filter(rule -> rule.inputChemical().equals(form.chemicalId()))
            .filter(rule -> rule.outputChemical().equals(event.outputChemical()))
            .filter(rule -> event.inputIsotopeMassNumber() <= 0 || rule.isotopeMassNumber() == event.inputIsotopeMassNumber())
            .filter(rule -> event.outputIsotopeMassNumber() <= 0 || rule.daughterIsotopeMassNumber() == event.outputIsotopeMassNumber())
            .findFirst().orElse(null);
        double inputMass = form.unitMass() * Math.max(1, input.getCount());
        double outputMass = decay == null ? inputMass : inputMass * decay.outputMassRatio();
        ChemicalState daughter = new ChemicalState(
            event.outputChemical(), outputMass / Math.max(1, output.getCount()),
            form.materialUnits(), 293.0, 0.0,
            retainedHeat / Math.max(1, output.getCount())
        );
        int daughterMass = event.outputIsotopeMassNumber() > 0
            ? event.outputIsotopeMassNumber()
            : decay == null ? form.isotopeMassNumber() : decay.daughterIsotopeMassNumber();
        daughter = daughter.withPureIsotope(event.outputChemical(), daughterMass);
        NuclearStackData.setState(output, daughter);
        String provenance = NuclearStackData.provenance(input);
        if (provenance.isBlank()) provenance = form.formId();
        NuclearStackData.bindIdentity(output, provenance, daughterMass);
        NuclearStackData.syncIdentity(output, daughter);
    }

}
