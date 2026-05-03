package savage.openeconomy.util;

import savage.openeconomy.core.EconomyManager;
import savage.openeconomy.core.EconomyCoreConfig;

import java.math.BigDecimal;
import java.text.DecimalFormat;

/**
 * Utility for formatting currency values.
 */
public class CurrencyFormatter {

    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.00");

    public static String format(BigDecimal amount) {
        EconomyCoreConfig cfg = EconomyManager.getConfig();
        String formatted = FORMAT.format(amount);
        
        if (cfg.isSymbolBeforeAmount()) {
            return cfg.getCurrencySymbol() + formatted;
        } else {
            return formatted + " " + cfg.getCurrencySymbol();
        }
    }
}
