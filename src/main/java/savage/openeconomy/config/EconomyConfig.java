package savage.openeconomy.config;

import java.math.BigDecimal;

/**
 * Configuration POJO for OpenEconomy.
 * Serialized to/from config/open-economy/config.yml via SnakeYAML.
 */
public class EconomyConfig {

    /** Starting balance for new players. */
    private double defaultBalance = 1000.0;

    /** Currency symbol displayed in formatted balances. */
    private String currencySymbol = "$";

    /** If true, symbol appears before the amount ($100). If false, after (100$). */
    private boolean symbolBeforeAmount = true;

    /** Display name for the currency (used in Common Economy API). */
    private String currencyName = "Dollar";

    /** Plural display name for the currency. */
    private String currencyNamePlural = "Dollars";

    // --- Getters ---

    public double getDefaultBalance() {
        return defaultBalance;
    }

    public BigDecimal getDefaultBalanceDecimal() {
        return BigDecimal.valueOf(defaultBalance);
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public boolean isSymbolBeforeAmount() {
        return symbolBeforeAmount;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public String getCurrencyNamePlural() {
        return currencyNamePlural;
    }

    // --- Setters (for SnakeYAML deserialization) ---

    public void setDefaultBalance(double defaultBalance) {
        this.defaultBalance = defaultBalance;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }

    public void setSymbolBeforeAmount(boolean symbolBeforeAmount) {
        this.symbolBeforeAmount = symbolBeforeAmount;
    }

    public void setCurrencyName(String currencyName) {
        this.currencyName = currencyName;
    }

    public void setCurrencyNamePlural(String currencyNamePlural) {
        this.currencyNamePlural = currencyNamePlural;
    }
}
