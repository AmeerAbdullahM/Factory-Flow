package com.factoryflow;

import com.factoryflow.common.GstCalculator;
import com.factoryflow.common.NumberToWordsConverter;
import com.factoryflow.common.UnitConverter;
import com.factoryflow.common.WeightedAverageCostCalculator;
import com.factoryflow.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CoreCalculationUnitTest {

    @Test
    void unitConversionWorksAndRejectsIncompatible() {
        assertEquals(0, new BigDecimal("1000").compareTo(UnitConverter.convert(BigDecimal.ONE, "kg", "g")));
        assertEquals(0, new BigDecimal("1.5").compareTo(UnitConverter.convert(new BigDecimal("1500"), "ml", "L")));
        assertEquals(0, new BigDecimal("200").compareTo(UnitConverter.convert(new BigDecimal("10"), "ctn", "pcs")));
        assertThrows(BusinessException.class, () -> UnitConverter.convert(BigDecimal.ONE, "kg", "L"));
    }

    @Test
    void varianceStrictGreaterThanToleranceRule() {
        BigDecimal tolerance = new BigDecimal("3.0");
        // Exactly -3.0% (pouches: 194 vs 200) -> NOT flagged
        BigDecimal pouchVar = UnitConverter.variancePercent(new BigDecimal("194"), new BigDecimal("200"));
        assertEquals(0, new BigDecimal("-3.0000").compareTo(pouchVar));
        assertFalse(UnitConverter.isFlagged(pouchVar, tolerance));

        // +5.0% (oil: 10.5 vs 10) -> FLAGGED
        BigDecimal oilVar = UnitConverter.variancePercent(new BigDecimal("10.5"), new BigDecimal("10"));
        assertEquals(0, new BigDecimal("5.0000").compareTo(oilVar));
        assertTrue(UnitConverter.isFlagged(oilVar, tolerance));

        // -6.0% (yield: 188 vs 200) -> FLAGGED
        BigDecimal yieldVar = UnitConverter.variancePercent(new BigDecimal("188"), new BigDecimal("200"));
        assertEquals(0, new BigDecimal("-6.0000").compareTo(yieldVar));
        assertTrue(UnitConverter.isFlagged(yieldVar, tolerance));
    }

    @Test
    void weightedAverageCostBenchmark() {
        BigDecimal newAvg = WeightedAverageCostCalculator.computeNewAverage(
                new BigDecimal("100"), new BigDecimal("42"),
                new BigDecimal("60"), new BigDecimal("45"));
        assertEquals(0, new BigDecimal("43.125").compareTo(newAvg));

        BigDecimal closingVal = WeightedAverageCostCalculator.closingValue(new BigDecimal("120"), newAvg);
        assertEquals(0, new BigDecimal("5175.00").compareTo(closingVal));
    }

    @Test
    void gstIntraAndInterState() {
        // Retailer A (Tamil Nadu '33'): 7500 @ 5% -> 187.50 CGST + 187.50 SGST = 7875
        var tn = GstCalculator.compute(new BigDecimal("7500"), new BigDecimal("5"), "33");
        assertEquals(0, new BigDecimal("187.50").compareTo(tn.cgst()));
        assertEquals(0, new BigDecimal("187.50").compareTo(tn.sgst()));
        assertEquals(0, BigDecimal.ZERO.compareTo(tn.igst()));
        assertEquals(0, new BigDecimal("7875.00").compareTo(tn.total()));

        // Distributor B (Karnataka '29'): 6000 @ 5% -> 300 IGST = 6300
        var ka = GstCalculator.compute(new BigDecimal("6000"), new BigDecimal("5"), "29");
        assertEquals(0, BigDecimal.ZERO.compareTo(ka.cgst()));
        assertEquals(0, BigDecimal.ZERO.compareTo(ka.sgst()));
        assertEquals(0, new BigDecimal("300.00").compareTo(ka.igst()));
        assertEquals(0, new BigDecimal("6300.00").compareTo(ka.total()));
    }

    @Test
    void numberToWordsIndianFormat() {
        assertEquals("Rupees Seven Thousand Eight Hundred Seventy Five Only",
                NumberToWordsConverter.convert(new BigDecimal("7875.00")));
    }
}
