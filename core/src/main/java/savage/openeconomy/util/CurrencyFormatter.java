package savage.openeconomy.util;

import savage.openeconomy.config.EconomyConfig;

import java.math.BigDecimal;
import java.text.DecimalFormat;

/**
 * Utility for formatting currency values.
 */
public class CurrencyFormatter {
    private static final ThreadLocal<DecimalFormat> FORMATTER = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));

    public static String format(BigDecimal amount) {
        EconomyConfig cfg = EconomyConfig.instance();
        String formattedNumber = FORMATTER.get().format(amount);
        
        if (cfg.symbolBeforeAmount) {
            return cfg.currencySymbol + formattedNumber;
        } else {
            return formattedNumber + cfg.currencySymbol;
        }
    }
}
