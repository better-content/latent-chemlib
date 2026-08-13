package com.bettercontent.latentchemlib.data;

public record NumericCurve(PresetCurve type, double offset, double scale, double exponent, double midpoint, double max) {
    public static NumericCurve linear(double offset, double scale) {
        return new NumericCurve(PresetCurve.LINEAR, offset, scale, 1.0, 0.0, Double.MAX_VALUE);
    }

    public double sample(double x) {
        double value = switch (type) {
            case LINEAR -> offset + scale * x;
            case QUADRATIC -> offset + scale * x * x;
            case EXPONENTIAL -> offset + scale * Math.pow(Math.max(0.0, x), Math.max(0.01, exponent));
            case LOGISTIC -> offset + max / (1.0 + Math.exp(-scale * (x - midpoint)));
            case INVERSE -> offset + scale / Math.max(0.001, Math.pow(Math.max(0.001, x), Math.max(0.01, exponent)));
        };
        return Double.isFinite(value) ? value : max;
    }
}
