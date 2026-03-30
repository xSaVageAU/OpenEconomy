package savage.openeconomy.config;

import java.math.BigDecimal;

/**
 * Configuration for OpenEconomy. 
 * Using public fields to minimize boilerplate.
 */
public class EconomyConfig {
    public static final String PROVIDER_ID = "open_economy";
    public static final String CURRENCY_ID = "dollar";

    public String defaultBalance = "1000.00";
    public String currencySymbol = "$";
    public boolean symbolBeforeAmount = true;
    public String currencyName = "Dollar";
    public String currencyNamePlural = "Dollars";

    public BigDecimal defaultBalanceDecimal() {
        return new BigDecimal(defaultBalance);
    }

    public static EconomyConfig instance() {
        return ConfigManager.getConfig();
    }
}
