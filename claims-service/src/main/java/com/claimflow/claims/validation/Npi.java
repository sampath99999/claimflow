package com.claimflow.claims.validation;

/**
 * National Provider Identifier check digit. CMS defines it as the Luhn check over the
 * 9 leading digits prefixed with the health industry number 80840.
 */
public final class Npi {

    private Npi() {
    }

    public static boolean isValid(String npi) {
        if (npi == null || !npi.matches("\\d{10}")) {
            return false;
        }
        return luhnValid("80840" + npi);
    }

    static boolean luhnValid(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
