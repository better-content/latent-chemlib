package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.api.IsotopeEnsemble;
import com.bettercontent.latentchemlib.api.IsotopeItemData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Dimension-local authoritative ledger for nuclear matter in foreign placed blocks and phases. */
public final class PlacedNuclearData extends SavedData {
    public static final String DATA_NAME = "latent_chemlib_placed_nuclear";
    private static final int VERSION = 1;

    private final Map<Long, Entry> entries = new LinkedHashMap<>();

    public static PlacedNuclearData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PlacedNuclearData::load, PlacedNuclearData::new, DATA_NAME);
    }

    public Optional<Entry> get(BlockPos pos) {
        return Optional.ofNullable(entries.get(pos.asLong()));
    }

    public int size() {
        return entries.size();
    }

    public Collection<BlockPos> positionsInChunk(ChunkPos chunk) {
        Collection<BlockPos> positions = new ArrayList<>();
        entries.keySet().forEach(packed -> {
            BlockPos pos = BlockPos.of(packed);
            if (new ChunkPos(pos).equals(chunk)) positions.add(pos);
        });
        return positions;
    }

    public Optional<Entry> initialize(
        BlockPos pos,
        BlockState state,
        ItemStack source,
        long gameTime,
        long seedCandidate
    ) {
        Optional<PlacedNuclearResolver.ResolvedPlacement> resolved = PlacedNuclearResolver.INSTANCE.resolve(state);
        if (resolved.isEmpty()) return Optional.empty();
        ItemStack material = source == null || source.isEmpty()
            ? stackForForm(resolved.get().form().formId())
            : source.copy();
        material.setCount(1);
        Entry entry = Entry.initialize(resolved.get(), material, gameTime, seedCandidate);
        entries.put(pos.asLong(), entry);
        setDirty();
        return Optional.of(entry);
    }

    public void put(BlockPos pos, Entry entry) {
        entries.put(pos.asLong(), entry);
        setDirty();
    }

    public Optional<Entry> remove(BlockPos pos) {
        Entry removed = entries.remove(pos.asLong());
        if (removed != null) setDirty();
        return Optional.ofNullable(removed);
    }

    public void touch(BlockPos pos, long gameTime) {
        Entry current = entries.get(pos.asLong());
        if (current == null || current.processedGameTime() == gameTime) return;
        entries.put(pos.asLong(), current.withProcessedGameTime(gameTime));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", VERSION);
        ListTag list = new ListTag();
        entries.forEach((packed, entry) -> {
            CompoundTag encoded = entry.save();
            encoded.putLong("pos", packed);
            list.add(encoded);
        });
        tag.put("entries", list);
        return tag;
    }

    public static PlacedNuclearData load(CompoundTag tag) {
        PlacedNuclearData data = new PlacedNuclearData();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag encoded = list.getCompound(index);
            Entry entry = Entry.load(encoded);
            if (entry.valid()) data.entries.put(encoded.getLong("pos"), entry);
        }
        return data;
    }

    private static ItemStack stackForForm(String formId) {
        ResourceLocation id = ResourceLocation.tryParse(formId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    public record Entry(
        String blockId,
        String formId,
        int isotopeMassNumber,
        double materialUnits,
        double unitMass,
        boolean nativePhase,
        ChemicalState state,
        IsotopeEnsemble isotopes,
        String provenance,
        long processedGameTime,
        long loadedExposureTicks,
        long seed
    ) {
        static Entry initialize(
            PlacedNuclearResolver.ResolvedPlacement resolved,
            ItemStack source,
            long gameTime,
            long seedCandidate
        ) {
            RadioactiveFormResolver.ResolvedForm form = resolved.form();
            ItemStack working = source.isEmpty() ? stackForForm(form.formId()) : source.copy();
            working.setCount(1);
            ChemicalState state = NuclearStackData.state(working, form);
            LoadedExposureClock.Window exposure = LoadedExposureClock.preview(working.getTag(), 0L, seedCandidate);
            return new Entry(
                resolved.blockId(), form.formId(), form.isotopeMassNumber(), form.materialUnits(), form.unitMass(),
                resolved.nativePhase(), state, NuclearStackData.isotopes(working), NuclearStackData.provenance(working),
                Math.max(0L, gameTime), exposure.endTick(), exposure.seed()
            );
        }

        public Entry advance(ChemicalState nextState, long elapsedTicks, long gameTime) {
            long bounded = Math.max(0L, elapsedTicks);
            long exposure = loadedExposureTicks > Long.MAX_VALUE - bounded ? Long.MAX_VALUE : loadedExposureTicks + bounded;
            IsotopeEnsemble nextIsotopes = nextState.isotopesOf(nextState.chemicalId());
            int nextMassNumber = nextIsotopes.isNatural() ? isotopeMassNumber : nextIsotopes.select(0.0);
            return new Entry(
                blockId, formId, nextMassNumber, materialUnits, unitMass, nativePhase,
                nextState, nextIsotopes, provenance, Math.max(processedGameTime, gameTime), exposure, seed
            );
        }

        public Entry withProcessedGameTime(long gameTime) {
            return new Entry(
                blockId, formId, isotopeMassNumber, materialUnits, unitMass, nativePhase,
                state, isotopes, provenance, Math.max(0L, gameTime), loadedExposureTicks, seed
            );
        }

        public LoadedExposureClock.Window exposureWindow(long elapsedTicks) {
            long bounded = Math.max(0L, elapsedTicks);
            long end = loadedExposureTicks > Long.MAX_VALUE - bounded ? Long.MAX_VALUE : loadedExposureTicks + bounded;
            return new LoadedExposureClock.Window(loadedExposureTicks, end, seed);
        }

        public ItemStack toStack() {
            ItemStack stack = stackForForm(formId);
            if (stack.isEmpty()) return stack;
            NuclearStackData.setState(stack, state);
            CompoundTag tag = stack.getOrCreateTag();
            tag.putString(NuclearStackData.PROVENANCE_KEY, provenance);
            tag.put(NuclearStackData.ISOTOPES_KEY, isotopes.save());
            tag.put(IsotopeItemData.TAG_KEY, isotopes.save());
            NuclearStackData.syncIdentity(stack, state);
            LoadedExposureClock.commit(tag, new LoadedExposureClock.Window(loadedExposureTicks, loadedExposureTicks, seed));
            return stack;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("block", blockId);
            tag.putString("form", formId);
            tag.putInt("isotope", isotopeMassNumber);
            tag.putDouble("units", materialUnits);
            tag.putDouble("unit_mass", unitMass);
            tag.putBoolean("phase", nativePhase);
            tag.put("state", state.save());
            tag.put("isotopes", isotopes.save());
            tag.putString("provenance", provenance);
            tag.putLong("processed", processedGameTime);
            tag.putLong("exposure", loadedExposureTicks);
            tag.putLong("seed", seed);
            return tag;
        }

        static Entry load(CompoundTag tag) {
            return new Entry(
                tag.getString("block"), tag.getString("form"), tag.getInt("isotope"),
                tag.getDouble("units"), tag.getDouble("unit_mass"), tag.getBoolean("phase"),
                ChemicalState.load(tag.getCompound("state")), IsotopeEnsemble.load(tag.getCompound("isotopes")),
                tag.getString("provenance"), Math.max(0L, tag.getLong("processed")),
                Math.max(0L, tag.getLong("exposure")), tag.getLong("seed")
            );
        }

        boolean valid() {
            return !blockId.isBlank() && !formId.isBlank() && isotopeMassNumber > 0
                && materialUnits > 0.0 && unitMass > 0.0 && state.mass() > 0.0;
        }
    }
}
