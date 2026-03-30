package savage.openeconomy.model;

import java.math.BigDecimal;

/**
 * Represents a player's economy account data.
 * Uses a record for immutability and modern Java style.
 */
public record AccountData(String name, BigDecimal balance) {
    public static final AccountData EMPTY = new AccountData("Unknown", BigDecimal.ZERO);
}
