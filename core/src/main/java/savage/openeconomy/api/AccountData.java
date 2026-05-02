package savage.openeconomy.api;

import java.math.BigDecimal;

/**
 * Represents a player's economy account data.
 */
public record AccountData(String name, BigDecimal balance) {}
