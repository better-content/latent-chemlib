package com.bettercontent.latentchemlib.data;

public record IsotopeDefinition(
    String elementId,
    int massNumber,
    String symbol,
    double naturalAbundance,
    double halfLifeSeconds,
    String daughterChemical,
    boolean stable
) {
    public IsotopeDefinition {
        elementId = elementId == null ? "" : elementId;
        massNumber = Math.max(0, massNumber);
        symbol = symbol == null ? "" : symbol;
        naturalAbundance = Math.max(0.0, naturalAbundance);
        halfLifeSeconds = Math.max(0.0, halfLifeSeconds);
        daughterChemical = daughterChemical == null ? "" : daughterChemical;
    }
}
