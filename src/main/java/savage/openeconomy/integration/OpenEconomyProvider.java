package savage.openeconomy.integration;

import com.mojang.authlib.GameProfile;
import eu.pb4.common.economy.api.EconomyAccount;
import eu.pb4.common.economy.api.EconomyCurrency;
import eu.pb4.common.economy.api.EconomyProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import savage.openeconomy.config.CurrencyConstants;

import java.util.Collection;
import java.util.Collections;

/**
 * Common Economy API provider for OpenEconomy.
 */
public class OpenEconomyProvider implements EconomyProvider {

    public static final OpenEconomyProvider INSTANCE = new OpenEconomyProvider();
    private final OpenEconomyCurrency currency = new OpenEconomyCurrency(this);

    private OpenEconomyProvider() {}

    @Override
    public Component name() {
        return Component.literal("OpenEconomy");
    }

    @Override
    public ItemStack icon() {
        return Items.GOLD_INGOT.getDefaultInstance();
    }

    @Override
    public Collection<EconomyCurrency> getCurrencies(MinecraftServer server) {
        return Collections.singletonList(currency);
    }

    @Override
    public EconomyCurrency getCurrency(MinecraftServer server, String id) {
        if (id.equalsIgnoreCase(CurrencyConstants.CURRENCY_ID)
                || id.equalsIgnoreCase(CurrencyConstants.PROVIDER_ID + ":" + CurrencyConstants.CURRENCY_ID)) {
            return currency;
        }
        return null;
    }

    @Override
    public String defaultAccount(MinecraftServer server, GameProfile profile, EconomyCurrency currency) {
        if (currency == this.currency) {
            return profile.id().toString();
        }
        return null;
    }

    @Override
    public Collection<EconomyAccount> getAccounts(MinecraftServer server, GameProfile profile) {
        return Collections.singletonList(new OpenEconomyAccount(profile, currency, this));
    }

    @Override
    public EconomyAccount getAccount(MinecraftServer server, GameProfile profile, String id) {
        if (id.equals(profile.id().toString())) {
            return new OpenEconomyAccount(profile, currency, this);
        }
        return null;
    }

    public OpenEconomyCurrency getDefaultCurrency() {
        return currency;
    }
}
