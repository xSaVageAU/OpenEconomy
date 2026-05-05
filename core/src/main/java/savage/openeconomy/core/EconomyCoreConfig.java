package savage.openeconomy.core;

import java.math.BigDecimal;

/**
 * Interface for configuration values required by the economy engine.
 * Implementations (Mods) provide these values to the core.
 */
public interface EconomyCoreConfig {
    BigDecimal getDefaultBalance();
    default BigDecimal getMaxBalance() {
        return new BigDecimal("1000000000000000"); // 1 Quadrillion
    }
    String getCurrencySymbol();
    boolean isSymbolBeforeAmount();
    String getCurrencyName();
    String getCurrencyNamePlural();
    int getEconomyScale();
    String getStorageType();
    String getMessagingType();
    default int getCacheEvictionMinutes() {
        return -1;
    }

    default int getCacheMaximumSize() {
        return Integer.MAX_VALUE;
    }

    default int getLeaderboardCacheSeconds() {
        return 60;
    }

    default int getLeaderboardSize() {
        return 10;
    }

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
