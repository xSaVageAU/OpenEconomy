package savage.openeconomy.util;

import net.minecraft.ChatFormatting;
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

        boolean isPositive = diff.compareTo(BigDecimal.ZERO) >= 0;
        ChatFormatting diffColor = isPositive ? ChatFormatting.GREEN : ChatFormatting.RED;
        String diffPrefix = isPositive ? "+" : "";

        Component message = Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Economy").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Your balance updated: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(diffPrefix + CurrencyFormatter.format(diff.abs())).withStyle(diffColor))
                .append(Component.literal(" (New: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(CurrencyFormatter.format(newBalance)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(")").withStyle(ChatFormatting.GRAY));

        player.sendSystemMessage(message);
    }
}
