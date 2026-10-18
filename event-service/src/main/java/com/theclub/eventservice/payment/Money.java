package com.theclub.eventservice.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts between decimal amounts and the minor units (paise, cents) payment providers use.
 */
public final class Money {

    private Money() {
    }

    public static long toMinor(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
