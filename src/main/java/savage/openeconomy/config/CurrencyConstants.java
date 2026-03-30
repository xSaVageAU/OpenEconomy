package savage.openeconomy.config;

/**
 * Internal constants for the Common Economy API identity.
 * These are not exposed in the YAML config — change them here if needed.
 */
public final class CurrencyConstants {

    private CurrencyConstants() {}

    /** The provider ID registered with CommonEconomy.register(). */
    public static final String PROVIDER_ID = "open_economy";

    /** The currency ID used in the Common Economy API (e.g., open_economy:dollar). */
    public static final String CURRENCY_ID = "dollar";
}
