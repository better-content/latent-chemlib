package com.bettercontent.latentchemlib.data;

/** Data-driven mapping for either legacy isotope forms or fixed radioactive families. */
public record NuclearFormRule(
    String suffix, double materialUnits, String item, String itemTag, String block, String blockTag,
    String family, double radiationStrength, double heatStrength,
    boolean naturalWorldgenInert, boolean placedAlwaysActive
) {
    public NuclearFormRule {
        suffix = normalize(suffix);
        materialUnits = Double.isFinite(materialUnits) && materialUnits > 0.0 ? materialUnits : 1.0;
        item = normalize(item);
        itemTag = normalizeTag(itemTag);
        block = normalize(block);
        blockTag = normalizeTag(blockTag);
        family = normalize(family);
        radiationStrength = Double.isFinite(radiationStrength) ? Math.max(0.0, radiationStrength) : 0.0;
        heatStrength = Double.isFinite(heatStrength) ? Math.max(0.0, heatStrength) : 0.0;
    }

    public NuclearFormRule(String suffix, double materialUnits) {
        this(suffix, materialUnits, "", "", "", "", "", 0.0, 0.0, false, false);
    }

    public boolean fixedProfile() {
        return !family.isBlank() && (radiationStrength > 0.0 || heatStrength > 0.0)
            && (!item.isBlank() || !itemTag.isBlank() || !block.isBlank() || !blockTag.isBlank());
    }

    public int specificity() {
        if (!item.isBlank() || !block.isBlank()) return 3;
        if (!itemTag.isBlank() || !blockTag.isBlank()) return 2;
        return suffix.isBlank() ? 0 : 1;
    }

    private static String normalize(String value) { return value == null ? "" : value.trim(); }
    private static String normalizeTag(String value) {
        String normalized = normalize(value);
        return normalized.startsWith("#") ? normalized.substring(1) : normalized;
    }
}
