package savage.openeconomy.integration;

import com.mojang.authlib.GameProfile;
import eu.pb4.common.economy.api.EconomyAccount;
import eu.pb4.common.economy.api.EconomyCurrency;
import eu.pb4.common.economy.api.EconomyProvider;
import eu.pb4.common.economy.api.EconomyTransaction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import savage.openeconomy.api.TransactionContext;
import savage.openeconomy.core.EconomyManager;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Common Economy API account implementation.
 * Bridges API calls to the EconomyManager.
 */
public class OpenEconomyAccount implements EconomyAccount {

    private final GameProfile profile;
    private final EconomyCurrency currency;
    private final EconomyProvider provider;

    public OpenEconomyAccount(GameProfile profile, EconomyCurrency currency, EconomyProvider provider) {
        this.profile = profile;
        this.currency = currency;
        this.provider = provider;
    }

    @Override
    public Component name() {
        return Component.literal(profile.name());
    }

    @Override
    public UUID owner() {
        return profile.id();
    }

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(EconomyManager.getConfig().getProviderId(), profile.id().toString());
    }

    @Override
    public EconomyCurrency currency() {
        return currency;
    }

    @Override
    public EconomyProvider provider() {
        return provider;
    }

    @Override
    public BigInteger balance() {
        BigDecimal dollars = EconomyManager.getInstance().getBalance(profile.id());
        return dollars.multiply(new BigDecimal(EconomyManager.getConfig().getEconomyScale())).toBigInteger();
    }

    @Override
    public void setBalance(BigInteger value) {
        BigDecimal dollars = new BigDecimal(value).divide(new BigDecimal(EconomyManager.getConfig().getEconomyScale()), 2, RoundingMode.HALF_UP);
        EconomyManager.getInstance().setBalance(TransactionContext.system("api"), profile.id(), dollars);
    }

    @Override
    public EconomyTransaction increaseBalance(BigInteger value) {
        BigInteger current = balance();
        BigInteger next = current.add(value);
        setBalance(next);
        return new EconomyTransaction.Simple(true, Component.literal("Success"), next, current, value, this);
    }

    @Override
    public EconomyTransaction decreaseBalance(BigInteger value) {
        BigInteger current = balance();
        if (current.compareTo(value) >= 0) {
            BigInteger next = current.subtract(value);
            setBalance(next);
            return new EconomyTransaction.Simple(true, Component.literal("Success"), next, current, value.negate(), this);
        }
        return new EconomyTransaction.Simple(false, Component.literal("Insufficient funds"), current, current, value.negate(), this);
    }

    @Override
    public EconomyTransaction canIncreaseBalance(BigInteger value) {
        BigInteger current = balance();
        return new EconomyTransaction.Simple(true, Component.literal("Success"), current.add(value), current, value, this);
    }

    @Override
    public EconomyTransaction canDecreaseBalance(BigInteger value) {
        BigInteger current = balance();
        if (current.compareTo(value) >= 0) {
            return new EconomyTransaction.Simple(true, Component.literal("Success"), current.subtract(value), current, value.negate(), this);
        }
        return new EconomyTransaction.Simple(false, Component.literal("Insufficient funds"), current, current, value.negate(), this);
    }
}
