package com.factoryflow.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Unit conversion service. Converts between compatible units using base-unit approach.
 * Base units: gram (g) for weight, millilitre (ml) for volume, piece (pcs) for count.
 *
 * Compatible conversions: kg↔g, L↔ml, pcs↔pcs
 * Incompatible: kg to L (throws exception)
 *
 * All arithmetic uses BigDecimal with HALF_UP rounding to 10 decimal places during calculation.
 */
public class UnitConverter {

    private static final int CALC_SCALE = 10;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    // Weight conversions (base: gram)
    public static final BigDecimal KG_TO_G = new BigDecimal("1000");
    public static final BigDecimal G_TO_KG = new BigDecimal("0.001");

    // Volume conversions (base: millilitre)
    public static final BigDecimal L_TO_ML = new BigDecimal("1000");
    public static final BigDecimal ML_TO_L = new BigDecimal("0.001");

    /**
     * Convert a quantity from one unit symbol to another.
     * Both units must be of the same type (WEIGHT, VOLUME, or COUNT).
     *
     * @param quantity the amount to convert
     * @param fromSymbol source unit symbol (e.g. "kg", "L", "pcs")
     * @param toSymbol target unit symbol
     * @return converted quantity
     * @throws BusinessException if units are incompatible
     */
    public static BigDecimal convert(BigDecimal quantity, String fromSymbol, String toSymbol) {
        if (fromSymbol.equalsIgnoreCase(toSymbol)) {
            return quantity; // no conversion needed
        }

        String from = fromSymbol.toLowerCase();
        String to = toSymbol.toLowerCase();

        // Weight conversions
        if (isWeight(from) && isWeight(to)) {
            BigDecimal inGrams = toGrams(quantity, from);
            return fromGrams(inGrams, to);
        }

        // Volume conversions
        if (isVolume(from) && isVolume(to)) {
            BigDecimal inMl = toMillilitres(quantity, from);
            return fromMillilitres(inMl, to);
        }

        // Count conversions (pcs, pkt, ctn all map to count base)
        if (isCount(from) && isCount(to)) {
            BigDecimal inPieces = toPieces(quantity, from);
            return fromPieces(inPieces, to);
        }

        throw new BusinessException(
            "Cannot convert " + fromSymbol + " to " + toSymbol + " — incompatible unit types.");
    }

    private static BigDecimal toGrams(BigDecimal qty, String symbol) {
        return switch (symbol) {
            case "kg" -> qty.multiply(KG_TO_G).setScale(CALC_SCALE, ROUNDING);
            case "g" -> qty;
            default -> throw new BusinessException("Unknown weight unit: " + symbol);
        };
    }

    private static BigDecimal fromGrams(BigDecimal grams, String symbol) {
        return switch (symbol) {
            case "kg" -> grams.multiply(G_TO_KG).setScale(CALC_SCALE, ROUNDING);
            case "g" -> grams;
            default -> throw new BusinessException("Unknown weight unit: " + symbol);
        };
    }

    private static BigDecimal toMillilitres(BigDecimal qty, String symbol) {
        return switch (symbol) {
            case "l", "litre", "litres" -> qty.multiply(L_TO_ML).setScale(CALC_SCALE, ROUNDING);
            case "ml" -> qty;
            default -> throw new BusinessException("Unknown volume unit: " + symbol);
        };
    }

    private static BigDecimal fromMillilitres(BigDecimal ml, String symbol) {
        return switch (symbol) {
            case "l", "litre", "litres" -> ml.multiply(ML_TO_L).setScale(CALC_SCALE, ROUNDING);
            case "ml" -> ml;
            default -> throw new BusinessException("Unknown volume unit: " + symbol);
        };
    }

    private static BigDecimal toPieces(BigDecimal qty, String symbol) {
        return switch (symbol) {
            case "pcs", "piece", "pkt", "packet" -> qty;
            case "ctn", "carton" -> qty.multiply(new BigDecimal("20")).setScale(CALC_SCALE, ROUNDING);
            default -> throw new BusinessException("Unknown count unit: " + symbol);
        };
    }

    private static BigDecimal fromPieces(BigDecimal pieces, String symbol) {
        return switch (symbol) {
            case "pcs", "piece", "pkt", "packet" -> pieces;
            case "ctn", "carton" -> pieces.divide(new BigDecimal("20"), CALC_SCALE, ROUNDING);
            default -> throw new BusinessException("Unknown count unit: " + symbol);
        };
    }

    private static boolean isWeight(String symbol) {
        return symbol.equals("kg") || symbol.equals("g");
    }

    private static boolean isVolume(String symbol) {
        return symbol.equals("l") || symbol.equals("ml") || symbol.equals("litre") || symbol.equals("litres");
    }

    private static boolean isCount(String symbol) {
        return symbol.equals("pcs") || symbol.equals("piece") || symbol.equals("pkt")
            || symbol.equals("packet") || symbol.equals("ctn") || symbol.equals("carton");
    }

    /**
     * Compute variance % with BigDecimal precision.
     * Formula: ((actual - planned) / planned) * 100
     * Returns 0 if planned is 0.
     */
    public static BigDecimal variancePercent(BigDecimal actual, BigDecimal planned) {
        if (planned == null || planned.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return actual.subtract(planned)
                     .divide(planned, CALC_SCALE, ROUNDING)
                     .multiply(new BigDecimal("100"))
                     .setScale(4, ROUNDING);
    }

    /**
     * Returns true if |variance%| > tolerancePercent (STRICTLY greater than).
     * Exactly at tolerance is NOT flagged per spec section 8.
     */
    public static boolean isFlagged(BigDecimal variancePercent, BigDecimal tolerancePercent) {
        BigDecimal absVariance = variancePercent.abs();
        return absVariance.compareTo(tolerancePercent) > 0; // strictly greater than
    }
}
