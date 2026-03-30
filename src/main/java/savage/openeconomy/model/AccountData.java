package savage.openeconomy.model;

import java.math.BigDecimal;

/**
 * Represents a player's economy account data.
 */
public class AccountData {

    private String name;
    private BigDecimal balance;

    public AccountData() {
        this("Unknown", BigDecimal.ZERO);
    }

    public AccountData(String name, BigDecimal balance) {
        this.name = name;
        this.balance = balance;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
