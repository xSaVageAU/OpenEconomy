package savage.openeconomy.config;

import java.math.BigDecimal;

/**
 * Configuration for OpenEconomy.
 * Using public fields to minimize boilerplate.
 */
public class EconomyConfig {
    public static final String PROVIDER_ID = "open_economy";
    public static final String CURRENCY_ID = "dollar";

    public String defaultBalance = "100.00";
    public String currencySymbol = "$";
    public boolean symbolBeforeAmount = true;
    public String currencyName = "Dollar";
    public String currencyNamePlural = "Dollars";
    public String storageType = "json"; // "json" is the default internal storage
    public int economyScale = 100; // Multiplier for smallest unit (e.g. 100 = cents to dollars)

    private transient BigDecimal cachedDefaultBalance;

    public BigDecimal defaultBalanceDecimal() {
        if (cachedDefaultBalance == null) {
            try {
                cachedDefaultBalance = new BigDecimal(defaultBalance);
            } catch (NumberFormatException e) {
                cachedDefaultBalance = new BigDecimal("100.00");
            }
        }
        return cachedDefaultBalance;
    }

    public static EconomyConfig instance() {
        return ConfigManager.getConfig();
    }
}
