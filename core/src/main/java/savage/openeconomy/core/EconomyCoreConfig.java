package savage.openeconomy.core;

import java.math.BigDecimal;

/**
 * Interface for configuration values required by the economy engine.
 * Implementations (Mods) provide these values to the core.
 */
public interface EconomyCoreConfig {
    BigDecimal getDefaultBalance();
    String getCurrencySymbol();
    boolean isSymbolBeforeAmount();
    String getCurrencyName();
    String getCurrencyNamePlural();
    int getEconomyScale();
    String getStorageType();
    String getMessagingType();

    /**
     * Whether the engine should automatically send balance update notifications (diff messages) to the player.
     */
    default boolean isDiffMessageEnabled(java.util.UUID uuid) {
        return false;
    }

    /**
     * Where to display the balance update notification.
     */
    default NotificationMode getNotificationMode() {
        return NotificationMode.ACTION_BAR;
    }

    enum NotificationMode {
        CHAT,
        ACTION_BAR
    }

    // Technical constants that shouldn't change
    default String getProviderId() { return "open_economy"; }
    default String getCurrencyId() { return "dollar"; }
}
