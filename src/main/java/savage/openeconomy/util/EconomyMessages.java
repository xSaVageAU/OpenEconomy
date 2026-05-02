package savage.openeconomy.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import savage.openeconomy.OpenEconomy;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Utility for sending economy-related messages to players.
 */
public class EconomyMessages {

    /**
     * Notifies a player about a balance update.
     */
    public static void sendBalanceUpdate(UUID uuid, BigDecimal diff, BigDecimal newBalance) {
        var server = OpenEconomy.getServer();
        if (server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) return;

        String color = diff.compareTo(BigDecimal.ZERO) >= 0 ? "§a+" : "§c";
        String message = String.format("§7[§6Economy§7] Your balance updated: %s%s §7(New: §e%s§7)",
                color,
                CurrencyFormatter.format(diff.abs()),
                CurrencyFormatter.format(newBalance));

        player.sendSystemMessage(Component.literal(message));
    }
}
