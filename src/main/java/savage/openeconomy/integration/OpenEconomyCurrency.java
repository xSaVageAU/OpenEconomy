package savage.openeconomy.integration;

import eu.pb4.common.economy.api.EconomyCurrency;
import eu.pb4.common.economy.api.EconomyProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import savage.openeconomy.config.EconomyConfig;

import savage.openeconomy.util.CurrencyFormatter;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Common Economy API currency implementation.
 * Uses a 100-scale (cents) representation for the BigInteger API values.
 */
public class OpenEconomyCurrency implements EconomyCurrency {

    private final EconomyProvider provider;

    public OpenEconomyCurrency(EconomyProvider provider) {
        this.provider = provider;
    }

    @Override
    public Component name() {
        return Component.literal(EconomyConfig.instance().currencyName);
    }

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(EconomyConfig.PROVIDER_ID, EconomyConfig.CURRENCY_ID);
    }

    @Override
    public String formatValue(BigInteger value, boolean full) {
        BigDecimal dollars = new BigDecimal(value).divide(new BigDecimal("100"));
        return CurrencyFormatter.format(dollars);
    }

    @Override
    public Component formatValueComponent(BigInteger value, boolean full) {
        return Component.literal(formatValue(value, full));
    }

    @Override
    public BigInteger parseValue(String value) {
        try {
            if (value == null || value.isEmpty()) return BigInteger.ZERO;
            String sanitized = value.replaceAll("[^0-9.\\-]", "");
            if (sanitized.isEmpty() || sanitized.equals("-") || sanitized.equals(".")) return BigInteger.ZERO;
            return new BigDecimal(sanitized).multiply(new BigDecimal("100")).toBigInteger();
        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }

    @Override
    public EconomyProvider provider() {
        return provider;
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.GOLD_INGOT);
    }
}
