package com.bettercontent.latentchemlib.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Dimension-local sidecar distinguishing naturally generated inert ore from disturbed replacement. */
public final class DisturbedRadioactiveData extends SavedData {
    public static final String DATA_NAME = "latent_chemlib_disturbed_radioactive";
    private final Map<Long, Entry> entries = new LinkedHashMap<>();

    public static DisturbedRadioactiveData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DisturbedRadioactiveData::load, DisturbedRadioactiveData::new, DATA_NAME);
    }

    public Optional<Entry> get(BlockPos pos) { return Optional.ofNullable(entries.get(pos.asLong())); }

    public Entry put(BlockPos pos, RadioactiveFormResolver.ResolvedBlock resolved) {
        var form = resolved.form();
        Entry entry = new Entry(resolved.blockId(), form.formId(), form.family(),
            form.radiationStrength(), form.heatStrength());
        entries.put(pos.asLong(), entry);
        setDirty();
        return entry;
    }

    public Optional<Entry> remove(BlockPos pos) {
        Entry removed = entries.remove(pos.asLong());
        if (removed != null) setDirty();
        return Optional.ofNullable(removed);
    }

    public boolean matches(BlockPos pos, RadioactiveFormResolver.ResolvedBlock resolved) {
        return get(pos).map(entry -> entry.blockId().equals(resolved.blockId())
            && entry.family().equals(resolved.form().family())).orElse(false);
    }

    public Collection<BlockPos> positionsInChunk(ChunkPos chunk) {
        Collection<BlockPos> positions = new ArrayList<>();
        entries.keySet().forEach(packed -> {
            BlockPos pos = BlockPos.of(packed);
            if (new ChunkPos(pos).equals(chunk)) positions.add(pos);
        });
        return positions;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", 1);
        ListTag list = new ListTag();
        entries.forEach((packed, entry) -> {
            CompoundTag encoded = entry.save();
            encoded.putLong("pos", packed);
            list.add(encoded);
        });
        tag.put("entries", list);
        return tag;
    }

    public static DisturbedRadioactiveData load(CompoundTag tag) {
        DisturbedRadioactiveData data = new DisturbedRadioactiveData();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag encoded = list.getCompound(index);
            Entry entry = Entry.load(encoded);
            if (entry.valid()) data.entries.put(encoded.getLong("pos"), entry);
        }
        return data;
    }

    public record Entry(String blockId, String formId, String family, double radiationStrength, double heatStrength) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("block", blockId);
            tag.putString("form", formId);
            tag.putString("family", family);
            tag.putDouble("radiation", radiationStrength);
            tag.putDouble("heat", heatStrength);
            return tag;
        }

        static Entry load(CompoundTag tag) {
            return new Entry(tag.getString("block"), tag.getString("form"), tag.getString("family"),
                Math.max(0.0, tag.getDouble("radiation")), Math.max(0.0, tag.getDouble("heat")));
        }

        boolean valid() { return !blockId.isBlank() && !family.isBlank(); }
    }
}
