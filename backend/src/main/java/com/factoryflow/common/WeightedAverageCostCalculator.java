package com.factoryflow.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Weighted Average Cost calculator.
 *
 * Formula for a new receipt:
 *   newAvgCost = (currentQty * currentAvgCost + receiptQty * receiptCost) / (currentQty + receiptQty)
 *
 * Issues are valued at the current weighted average cost.
 * This is the method used throughout FactoryFlow for consistency.
 * See DESIGN.md section "Inventory Valuation" for the FIFO comparison note.
 */
public class WeightedAverageCostCalculator {

    private static final int COST_SCALE = 6; // 6 decimal places for unit cost
    private static final int VALUE_SCALE = 2; // 2 decimal places for total value
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Compute new weighted average cost after a purchase receipt.
     *
     * Example (benchmark):
     *   Opening: 100 kg @ ₹42 = ₹4,200
     *   Purchase: 60 kg @ ₹45 = ₹2,700
     *   New avg = (4200 + 2700) / (100 + 60) = 6900 / 160 = ₹43.125/kg
     *
     * @param currentQty      current stock quantity before receipt
     * @param currentAvgCost  current weighted average cost per unit
     * @param receiptQty      quantity being received
     * @param receiptUnitCost cost per unit of received goods
     * @return new weighted average cost per unit
     */
    public static BigDecimal computeNewAverage(BigDecimal currentQty, BigDecimal currentAvgCost,
                                               BigDecimal receiptQty, BigDecimal receiptUnitCost) {
        BigDecimal currentValue = currentQty.multiply(currentAvgCost);
        BigDecimal receiptValue = receiptQty.multiply(receiptUnitCost);
        BigDecimal totalQty = currentQty.add(receiptQty);

        if (totalQty.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        return currentValue.add(receiptValue)
                           .divide(totalQty, COST_SCALE, ROUNDING);
    }

    /**
     * Value of a quantity at a given cost (for issues and dispatch).
     */
    public static BigDecimal computeValue(BigDecimal quantity, BigDecimal unitCost) {
        return quantity.multiply(unitCost).setScale(VALUE_SCALE, ROUNDING);
    }

    /**
     * Closing stock value.
     * closingQty * weightedAvgCost (rounded to 2 dp)
     *
     * Benchmark: 120 kg × ₹43.125 = ₹5,175.00 ✓
     */
    public static BigDecimal closingValue(BigDecimal closingQty, BigDecimal weightedAvgCost) {
        return closingQty.multiply(weightedAvgCost).setScale(VALUE_SCALE, ROUNDING);
    }
}
