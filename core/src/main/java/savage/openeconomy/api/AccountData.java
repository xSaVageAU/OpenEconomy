package savage.openeconomy.api;

import java.math.BigDecimal;

/**
 * Represents a player's economy account data.
 */
public record AccountData(String name, BigDecimal balance, long revision) {
    public AccountData(String name, BigDecimal balance) {
        this(name, balance, 0L);
    }

    /**
     * Roughly estimates the memory footprint of this object in bytes.
     */
    public long estimateSize() {
        long size = 32; // Object header and references
        if (name != null) size += 24 + (name.length() * 2L); // String object + char array
        if (balance != null) size += 80; // BigDecimal + BigInteger overhead
        size += 8; // long revision
        return size;
    }
}
