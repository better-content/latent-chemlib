package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.data.ChemicalTraits;
import com.gerald.latentchemlib.data.LatentDataManager;
import com.gerald.latentchemlib.data.NuclearDecayRule;
import com.gerald.latentchemlib.data.ReactionRule;
import com.gerald.latentchemlib.item.ChemicalCellItem;
import com.gerald.heatsync.api.HeatBlockEntity;
import com.gerald.heatsync.api.HeatCapabilities;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.function.Consumer;

public class NuclearSimulationService {
    public static final NuclearSimulationService INSTANCE = new NuclearSimulationService();
    private static final double INDUCED_CAPTURE_FLUX = 3_000.0;
    private static final double INDUCED_FISSION_FLUX = 18_000.0;

    public enum NuclearEventType {
        DECAY,
        CAPTURE,
        FISSION
    }

    public enum ProcessStatus {
        SKIPPED,
        UNCHANGED,
        MUTATED,
        BUDGET_EXHAUSTED
    }

    public record NuclearEnvironment(double moderation, double absorption, double externalFlux) {
        public static final NuclearEnvironment EMPTY = new NuclearEnvironment(0.0, 0.0, 0.0);
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
        NuclearEventType type
    ) {}

    public record StateProcessResult(ProcessStatus status, ChemicalState state) {
        public boolean mutated() {
            return status == ProcessStatus.MUTATED;
        }

        public boolean budgetExhausted() {
            return status == ProcessStatus.BUDGET_EXHAUSTED;
        }
    }

    public ProcessStatus processStack(ServerLevel level, BlockPos pos, ItemStack stack, double elapsedSeconds, HeatBlockEntity heatSink, Consumer<ItemStack> outputSink) {
        return processStack(level, pos, stack, elapsedSeconds, environment(level, pos), heatSink, outputSink);
    }

    public ProcessStatus processStack(ServerLevel level, BlockPos pos, ItemStack stack, double elapsedSeconds, NuclearEnvironment environment, HeatBlockEntity heatSink, Consumer<ItemStack> outputSink) {
        if (!canProcessStack(stack, environment)) return ProcessStatus.SKIPPED;
        if (stack.is(LatentChemlibMod.SEALED_CHEMICAL_CELL.get())) {
            return processCellStack(level, pos, stack, elapsedSeconds, environment, heatSink, outputSink);
        }
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_STACK_EVALUATIONS, 1)) {
            return ProcessStatus.BUDGET_EXHAUSTED;
        }
        Optional<NuclearStackEvent> event = evaluateStack(stack, elapsedSeconds, environment, level.getRandom());
        if (event.isEmpty()) return ProcessStatus.UNCHANGED;
        NuclearStackEvent nuclearEvent = event.get();
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_MUTATIONS, 1)) {
            return ProcessStatus.BUDGET_EXHAUSTED;
        }
        int consumed = Math.min(stack.getCount(), Math.max(1, nuclearEvent.inputCount()));
        stack.shrink(consumed);
        ItemStack output = new ItemStack(nuclearEvent.outputItem(), nuclearEvent.outputCount());
        if (outputSink != null && !output.isEmpty()) outputSink.accept(output);
        emit(level, pos, heatSink, nuclearEvent.heatEmission(), nuclearEvent.radiationLevel());
        return ProcessStatus.MUTATED;
    }

    public ProcessStatus applyStackEvent(ServerLevel level, BlockPos pos, ItemStack stack, NuclearStackEvent nuclearEvent, HeatBlockEntity heatSink, Consumer<ItemStack> outputSink) {
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_MUTATIONS, 1)) {
            return ProcessStatus.BUDGET_EXHAUSTED;
        }
        int consumed = Math.min(stack.getCount(), Math.max(1, nuclearEvent.inputCount()));
        stack.shrink(consumed);
        ItemStack output = new ItemStack(nuclearEvent.outputItem(), nuclearEvent.outputCount());
        if (outputSink != null && !output.isEmpty()) outputSink.accept(output);
        emit(level, pos, heatSink, nuclearEvent.heatEmission(), nuclearEvent.radiationLevel());
        return ProcessStatus.MUTATED;
    }

    public StateProcessResult processChemicalState(ServerLevel level, BlockPos pos, ChemicalState state, double elapsedSeconds, HeatBlockEntity heatSink, Consumer<ItemStack> outputSink) {
        if (!isNuclearRelevant(state)) return new StateProcessResult(ProcessStatus.SKIPPED, state);
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_STATE_EVALUATIONS, 1)) {
            return new StateProcessResult(ProcessStatus.BUDGET_EXHAUSTED, state);
        }
        NuclearEnvironment environment = environment(level, pos);
        Optional<NuclearStateEvent> event = evaluateState(state, elapsedSeconds, environment, level.getRandom());
        if (event.isEmpty()) return new StateProcessResult(ProcessStatus.UNCHANGED, state);
        NuclearStateEvent nuclearEvent = event.get();
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_MUTATIONS, 1)) {
            return new StateProcessResult(ProcessStatus.BUDGET_EXHAUSTED, state);
        }
        if (outputSink != null && nuclearEvent.outputItem() != null && !nuclearEvent.outputItem().isEmpty()) outputSink.accept(nuclearEvent.outputItem().copy());
        emit(level, pos, heatSink, nuclearEvent.heatEmission(), nuclearEvent.radiationLevel());
        return new StateProcessResult(ProcessStatus.MUTATED, nuclearEvent.outputState());
    }

    public Optional<NuclearStateEvent> evaluateState(ChemicalState state, double elapsedSeconds, NuclearEnvironment environment, RandomSource random) {
        if (!isNuclearRelevant(state)) return Optional.empty();
        for (NuclearDecayRule rule : LatentDataManager.INSTANCE.nuclearDecayRules()) {
            if (!rule.matches(state)) continue;
            if (random.nextDouble() < rule.decayProbability(elapsedSeconds)) {
                return Optional.of(new NuclearStateEvent(
                    rule.apply(state),
                    null,
                    rule.heatEmission(),
                    radiationFromHeat(rule.heatEmission()),
                    NuclearEventType.DECAY
                ));
            }
            break;
        }
        return inducedStateEvent(state, environment, random);
    }

    public Optional<NuclearStackEvent> evaluateStack(ItemStack stack, double elapsedSeconds, NuclearEnvironment environment, RandomSource random) {
        if (stack.isEmpty() || !(stack.getItem() instanceof Element element)) return Optional.empty();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return Optional.empty();
        ChemicalState state = stackState(id.toString(), element, stack.getCount());
        for (NuclearDecayRule rule : LatentDataManager.INSTANCE.nuclearDecayRules()) {
            if (!rule.matches(state)) continue;
            if (random.nextDouble() < rule.decayProbability(elapsedSeconds)) {
                Item daughter = rule.outputChemicalItemValue();
                if (isMissing(daughter)) return Optional.empty();
                return Optional.of(new NuclearStackEvent(
                    rule.outputChemical(),
                    daughter,
                    1,
                    1,
                    rule.heatEmission(),
                    radiationFromHeat(rule.heatEmission()),
                    NuclearEventType.DECAY
                ));
            }
            break;
        }
        return inducedStackEvent(state, environment, random);
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
        if (stack.is(LatentChemlibMod.SEALED_CHEMICAL_CELL.get())) return ChemicalCellItem.hasState(stack) && isNuclearRelevant(ChemicalCellItem.state(stack));
        if (stack.getItem() instanceof Element element) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return id != null && (hasDecayRule(id.toString()) || element.getAtomicNumber() >= 82 || isConfiguredNuclearItem(id.toString()));
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && isConfiguredNuclearItem(id.toString());
    }

    public boolean isNuclearRelevant(ChemicalState state) {
        if (state.mass() <= 0.0) return false;
        for (String chemicalId : state.components().keySet()) {
            if (hasDecayRule(chemicalId) || isConfiguredNuclearItem(chemicalId)) return true;
        }
        for (String chemicalId : state.components().keySet()) {
            ResourceLocation id = ResourceLocation.tryParse(chemicalId);
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            if (item instanceof Element element && element.getAtomicNumber() >= 82) return true;
        }
        return false;
    }

    public boolean canProcessStack(ItemStack stack, NuclearEnvironment environment) {
        if (isNuclearRelevant(stack)) return true;
        return environment.externalFlux() > 0.0 && hasCaptureProduct(stack);
    }

    public boolean hasCaptureProduct(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && captureRule(id.toString()).isPresent();
    }

    public double intrinsicFlux(ItemStack stack, NuclearEnvironment environment) {
        if (stack.isEmpty() || !(stack.getItem() instanceof Element element)) return 0.0;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return 0.0;
        return neutronFlux(stackState(id.toString(), element, stack.getCount()), new NuclearEnvironment(environment.moderation(), environment.absorption(), 0.0));
    }

    public static NuclearEnvironment environment(ServerLevel level, BlockPos pos) {
        if (pos == null) return NuclearEnvironment.EMPTY;
        double moderation = 0.0;
        double absorption = 0.0;
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
        }
        return new NuclearEnvironment(Math.min(4.0, moderation), Math.min(0.95, absorption), 0.0);
    }

    private ProcessStatus processCellStack(ServerLevel level, BlockPos pos, ItemStack stack, double elapsedSeconds, NuclearEnvironment environment, HeatBlockEntity heatSink, Consumer<ItemStack> outputSink) {
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_STATE_EVALUATIONS, 1)) {
            return ProcessStatus.BUDGET_EXHAUSTED;
        }
        ChemicalState state = ChemicalCellItem.state(stack);
        Optional<NuclearStateEvent> event = evaluateState(state, elapsedSeconds, environment, level.getRandom());
        if (event.isEmpty()) return ProcessStatus.UNCHANGED;
        NuclearStateEvent nuclearEvent = event.get();
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_MUTATIONS, 1)) {
            return ProcessStatus.BUDGET_EXHAUSTED;
        }
        ItemStack updated = ChemicalCellItem.withState(stack, nuclearEvent.outputState());
        stack.setTag(updated.getTag());
        if (outputSink != null && nuclearEvent.outputItem() != null && !nuclearEvent.outputItem().isEmpty()) outputSink.accept(nuclearEvent.outputItem().copy());
        emit(level, pos, heatSink, nuclearEvent.heatEmission(), nuclearEvent.radiationLevel());
        return ProcessStatus.MUTATED;
    }

    private Optional<NuclearStateEvent> inducedStateEvent(ChemicalState state, NuclearEnvironment environment, RandomSource random) {
        double flux = neutronFlux(state, environment);
        String reactant = state.components().keySet().stream()
            .filter(id -> !inducedProduct(id, flux).isEmpty())
            .findFirst()
            .orElse(state.chemicalId());
        InducedProduct product = inducedProduct(reactant, flux);
        if (product.isEmpty()) return Optional.empty();
        double probability = inducedProbability(flux, product.type());
        if (probability <= 0.0 || random.nextDouble() >= probability) return Optional.empty();
        NuclearEventType type = product.type();
        float heat = product.heatEmission() > 0.0f ? product.heatEmission() : type == NuclearEventType.FISSION ? 3_200.0f : 900.0f;
        ChemicalState output = state
            .transmute(reactant, product.chemicalId(), type == NuclearEventType.FISSION ? 0.52 : 0.995)
            .withConditions(
            Math.max(90.0, state.temperature() + (type == NuclearEventType.FISSION ? 1800.0 : 450.0)),
            Math.max(0.0, state.charge() + (type == NuclearEventType.FISSION ? 0.35 : 0.08)),
            Math.max(0.0, state.energy() + (type == NuclearEventType.FISSION ? 8_000.0 : 1_200.0))
        );
        return Optional.of(new NuclearStateEvent(output, null, heat, radiationFromFlux(flux), type));
    }

    private Optional<NuclearStackEvent> inducedStackEvent(ChemicalState state, NuclearEnvironment environment, RandomSource random) {
        double flux = neutronFlux(state, environment);
        InducedProduct product = inducedProduct(state.chemicalId(), flux);
        if (product.isEmpty()) return Optional.empty();
        double probability = inducedProbability(flux, product.type());
        if (probability <= 0.0 || random.nextDouble() >= probability) return Optional.empty();
        Item output = product.outputItem();
        if (isMissing(output)) return Optional.empty();
        float heat = product.heatEmission() > 0.0f ? product.heatEmission() : product.type() == NuclearEventType.FISSION ? 3_200.0f : 900.0f;
        return Optional.of(new NuclearStackEvent(product.chemicalId(), output, 1, 1, heat, radiationFromFlux(flux), product.type()));
    }

    private static ChemicalState stackState(String chemicalId, Element element, int count) {
        return new ChemicalState(chemicalId, count * Math.max(1.0, element.getAtomicNumber()), 1.0, 293.0, 0.0, 0.0);
    }

    private static InducedProduct inducedProduct(String chemicalId, double flux) {
        if (flux >= INDUCED_FISSION_FLUX) {
            String fissionProduct = switch (chemicalId) {
                case "chemlib:uranium" -> "chemlib:barium";
                case "chemlib:thorium" -> "chemlib:radium";
                case "chemlib:plutonium" -> "chemlib:krypton";
                default -> "";
            };
            if (!fissionProduct.isBlank()) {
                return new InducedProduct(NuclearEventType.FISSION, fissionProduct, item(fissionProduct), 3_200.0f);
            }
        }
        Optional<ReactionRule> captureRule = captureRule(chemicalId);
        if (captureRule.isEmpty()) {
            String legacyCaptureProduct = switch (chemicalId) {
                case "chemlib:uranium" -> "chemlib:neptunium";
                case "chemlib:thorium" -> "chemlib:protactinium";
                case "chemlib:plutonium" -> "chemlib:americium";
                default -> "";
            };
            if (legacyCaptureProduct.isBlank()) return InducedProduct.EMPTY;
            return new InducedProduct(NuclearEventType.CAPTURE, legacyCaptureProduct, item(legacyCaptureProduct), 900.0f);
        }
        ReactionRule rule = captureRule.get();
        Item captureItem = rule.outputItemValue();
        if (isMissing(captureItem)) captureItem = item(rule.outputChemical());
        return new InducedProduct(NuclearEventType.CAPTURE, rule.outputChemical(), captureItem, rule.heatEmission());
    }

    private static double inducedProbability(double flux, NuclearEventType type) {
        double threshold = type == NuclearEventType.FISSION ? INDUCED_FISSION_FLUX : INDUCED_CAPTURE_FLUX;
        if (flux < threshold) return 0.0;
        return Math.min(1.0, (flux - threshold) / threshold);
    }

    private static Optional<ReactionRule> captureRule(String chemicalId) {
        for (ReactionRule rule : LatentDataManager.INSTANCE.reactionRules()) {
            if (!rule.inputChemical().equals(chemicalId)) continue;
            if (rule.id().contains(":capture/")) return Optional.of(rule);
        }
        return Optional.empty();
    }

    private void emit(ServerLevel level, BlockPos pos, HeatBlockEntity heatSink, float heatEmission, int radiationLevel) {
        if (heatSink != null && heatEmission > 0.0f && SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_HEAT_EMISSIONS, 1)) {
            heatSink.addHeat(heatEmission);
        }
        if (pos != null && radiationLevel > 0 && SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_RADIATION_EMISSIONS, 1)) {
            LatentRadiationService.emit(level, pos, radiationLevel);
        }
    }

    private static boolean hasDecayRule(String chemicalId) {
        for (NuclearDecayRule rule : LatentDataManager.INSTANCE.nuclearDecayRules()) {
            if (rule.inputChemical().equals(chemicalId)) return true;
        }
        return false;
    }

    private static ChemicalTraits traits(String chemicalId) {
        try {
            return LatentDataManager.INSTANCE.traits(chemicalId);
        } catch (Throwable ex) {
            return ChemicalTraits.fallback();
        }
    }

    private static boolean isConfiguredNuclearItem(String itemId) {
        return itemId.contains("uranium") || itemId.contains("thorium") || itemId.contains("plutonium") || itemId.contains("radioactive") || itemId.contains("nuclear") || itemId.contains("fissile");
    }

    private static Item item(String itemId) {
        try {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            return id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        } catch (Throwable ex) {
            return null;
        }
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

    private static int radiationFromFlux(double flux) {
        if (flux <= 0.0) return 0;
        return (int) Math.min(24.0, Math.max(1.0, flux / 1_200.0));
    }

    public static HeatBlockEntity heatSink(BlockEntity entity) {
        if (entity instanceof HeatBlockEntity heatBlockEntity) return heatBlockEntity;
        return entity.getCapability(HeatCapabilities.INSTANCE.getHEAT())
            .map(storage -> storage instanceof HeatBlockEntity heatBlockEntity ? heatBlockEntity : null)
            .orElse(null);
    }

    private record InducedProduct(
        NuclearEventType type,
        String chemicalId,
        Item outputItem,
        float heatEmission
    ) {
        private static final InducedProduct EMPTY = new InducedProduct(NuclearEventType.CAPTURE, "", null, 0.0f);

        private boolean isEmpty() {
            return chemicalId == null || chemicalId.isBlank();
        }
    }
}
