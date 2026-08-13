package com.bettercontent.latentchemlib.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericCurveTest {
    @Test
    void presetCurvesReturnFiniteValues() {
        for (PresetCurve type : PresetCurve.values()) {
            NumericCurve curve = new NumericCurve(type, 1.0, 2.0, 1.3, 4.0, 100.0);
            assertTrue(Double.isFinite(curve.sample(8.0)), type.name());
        }
    }

    @Test
    void linearFactoryUsesUnitExponentAndUnboundedMax() {
        NumericCurve curve = NumericCurve.linear(5.0, 3.0);
        assertEquals(PresetCurve.LINEAR, curve.type());
        assertEquals(1.0, curve.exponent());
        assertEquals(Double.MAX_VALUE, curve.max());
        assertEquals(17.0, curve.sample(4.0));
    }

    @Test
    void presetCurvesUseTheirDistinctShapes() {
        assertEquals(13.0, new NumericCurve(PresetCurve.LINEAR, 1.0, 3.0, 1.0, 0.0, 100.0).sample(4.0));
        assertEquals(49.0, new NumericCurve(PresetCurve.QUADRATIC, 1.0, 3.0, 1.0, 0.0, 100.0).sample(4.0));
        assertEquals(33.0, new NumericCurve(PresetCurve.EXPONENTIAL, 1.0, 2.0, 2.0, 0.0, 100.0).sample(4.0));
        assertEquals(51.0, new NumericCurve(PresetCurve.LOGISTIC, 1.0, 2.0, 1.0, 4.0, 100.0).sample(4.0));
        assertEquals(3.0, new NumericCurve(PresetCurve.INVERSE, 1.0, 8.0, 1.0, 0.0, 100.0).sample(4.0));
    }

    @Test
    void curvesClampUnsafeInputsAndReturnMaxForNonFiniteResults() {
        assertEquals(3.0, new NumericCurve(PresetCurve.EXPONENTIAL, 1.0, 2.0, -5.0, 0.0, 100.0).sample(1.0));
        assertTrue(new NumericCurve(PresetCurve.INVERSE, 0.0, 1.0, 1.0, 0.0, 100.0).sample(0.0) > 900.0);
        assertEquals(42.0, new NumericCurve(PresetCurve.QUADRATIC, 0.0, Double.MAX_VALUE, 1.0, 0.0, 42.0).sample(Double.MAX_VALUE));
    }
}
