package savage.openeconomy.api;

import java.math.BigDecimal;

/**
 * Represents a player's economy account data.
 */
public record AccountData(String name, BigDecimal balance) {
    public static final AccountData EMPTY = new AccountData("Unknown", BigDecimal.ZERO);
}
