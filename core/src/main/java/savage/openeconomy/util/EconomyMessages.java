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

    public static Component paySent(String target, BigDecimal amount) {
        return Component.empty()
                .append(Component.literal("You sent ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(CurrencyFormatter.format(amount)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" to ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(target).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY));
    }

    public static Component payReceived(String sender, BigDecimal amount) {
        return Component.empty()
                .append(Component.literal("You received ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(CurrencyFormatter.format(amount)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" from ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(sender).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY));
    }
}
