package com.factoryflow.common;

import java.math.BigDecimal;

/**
 * Converts numbers to Indian number system words (for invoice "Amount in Words").
 * Uses Rupees and Paise format.
 */
public class NumberToWordsConverter {

    private static final String[] ones = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen",
        "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };

    private static final String[] tens = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty",
        "Sixty", "Seventy", "Eighty", "Ninety"
    };

    public static String convert(BigDecimal amount) {
        long rupees = amount.longValue();
        long paise = amount.remainder(BigDecimal.ONE)
                           .multiply(new BigDecimal("100"))
                           .setScale(0, java.math.RoundingMode.HALF_UP)
                           .longValue();

        String result = "Rupees " + convertToWords(rupees);
        if (paise > 0) {
            result += " and " + convertToWords(paise) + " Paise";
        }
        return result + " Only";
    }

    private static String convertToWords(long number) {
        if (number == 0) return "Zero";
        if (number < 0) return "Minus " + convertToWords(-number);

        String words = "";
        if (number / 10000000 > 0) {
            words += convertToWords(number / 10000000) + " Crore ";
            number %= 10000000;
        }
        if (number / 100000 > 0) {
            words += convertToWords(number / 100000) + " Lakh ";
            number %= 100000;
        }
        if (number / 1000 > 0) {
            words += convertToWords(number / 1000) + " Thousand ";
            number %= 1000;
        }
        if (number / 100 > 0) {
            words += ones[(int)(number / 100)] + " Hundred ";
            number %= 100;
        }
        if (number > 0) {
            if (number < 20) {
                words += ones[(int)number];
            } else {
                words += tens[(int)(number / 10)];
                if (number % 10 > 0) words += " " + ones[(int)(number % 10)];
            }
        }
        return words.trim();
    }
}
