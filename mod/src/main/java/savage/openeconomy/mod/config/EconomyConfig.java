package savage.openeconomy.mod.config;

import savage.openeconomy.core.EconomyCoreConfig;
import java.math.BigDecimal;

public class EconomyConfig implements EconomyCoreConfig {
    public String defaultBalance = "100.00";
    public String currencySymbol = "$";
    public boolean symbolBeforeAmount = true;
    public String currencyName = "Dollar";
    public String currencyNamePlural = "Dollars";
    public String storageType = "json";
    public String messagingType = "none";
    public int economyScale = 100;
    public boolean enableDiffMessages = true;
    public String notificationMode = "ACTION_BAR";

    private transient BigDecimal cachedDefaultBalance;

    @Override
    public NotificationMode getNotificationMode() {
        try {
            return NotificationMode.valueOf(notificationMode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NotificationMode.CHAT;
        }
    }

    @Override
    public boolean isDiffMessageEnabled(java.util.UUID uuid) {
        return enableDiffMessages;
    }

    @Override
    public BigDecimal getDefaultBalance() {
        if (cachedDefaultBalance == null) {
            try {
                cachedDefaultBalance = new BigDecimal(defaultBalance);
            } catch (NumberFormatException e) {
                cachedDefaultBalance = new BigDecimal("100.00");
            }
        }
        return cachedDefaultBalance;
    }

    @Override public String getCurrencySymbol() { return currencySymbol; }
    @Override public boolean isSymbolBeforeAmount() { return symbolBeforeAmount; }
    @Override public String getCurrencyName() { return currencyName; }
    @Override public String getCurrencyNamePlural() { return currencyNamePlural; }
    @Override public int getEconomyScale() { return economyScale; }
    @Override public String getStorageType() { return storageType; }
    @Override public String getMessagingType() { return messagingType; }
}
