package com.factoryflow.common;

import java.math.BigDecimal;

/**
 * GST calculation logic per Indian GST rules.
 *
 * Rules:
 * - Factory is in Tamil Nadu (state code 33)
 * - Intra-state supply (customer in Tamil Nadu): CGST + SGST (each = gstRate/2)
 * - Inter-state supply (customer outside Tamil Nadu): IGST (= gstRate)
 *
 * Benchmark verification:
 * - Retailer A (Tamil Nadu, 5% GST): 7500 * 2.5% CGST = 187.50, SGST = 187.50, Total = 7875 ✓
 * - Distributor B (Karnataka, 5% GST): 6000 * 5% IGST = 300, Total = 6300 ✓
 */
public class GstCalculator {

    private static final String FACTORY_STATE_CODE = "33"; // Tamil Nadu

    /**
     * Check if a supply is inter-state (IGST applies).
     * @param customerStateCode 2-digit state code
     */
    public static boolean isInterstate(String customerStateCode) {
        return !FACTORY_STATE_CODE.equals(customerStateCode);
    }

    /**
     * Compute GST components for a taxable amount.
     * @param taxableAmount the taxable value
     * @param gstRatePercent GST rate (e.g. 5 for 5%)
     * @param customerStateCode customer's state code
     */
    public static GstResult compute(BigDecimal taxableAmount, BigDecimal gstRatePercent, String customerStateCode) {
        boolean interstate = isInterstate(customerStateCode);
        BigDecimal rate = gstRatePercent.divide(new BigDecimal("100"), 10, java.math.RoundingMode.HALF_UP);

        if (interstate) {
            // IGST = taxable * gstRate
            BigDecimal igst = taxableAmount.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP);
            return new GstResult(BigDecimal.ZERO, BigDecimal.ZERO, igst, taxableAmount.add(igst));
        } else {
            // CGST = SGST = taxable * (gstRate/2)
            BigDecimal halfRate = rate.divide(new BigDecimal("2"), 10, java.math.RoundingMode.HALF_UP);
            BigDecimal cgst = taxableAmount.multiply(halfRate).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal sgst = taxableAmount.multiply(halfRate).setScale(2, java.math.RoundingMode.HALF_UP);
            return new GstResult(cgst, sgst, BigDecimal.ZERO, taxableAmount.add(cgst).add(sgst));
        }
    }

    public record GstResult(BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal total) {}
}
