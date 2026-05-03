package savage.openeconomy.mod.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import savage.openeconomy.util.CurrencyFormatter;
import java.math.BigDecimal;

/**
 * Message utilities for the standard OpenEconomy implementation.
 */
public class ModMessages {

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

    public static Component giveSuccess(String target, BigDecimal amount) {
        return Component.empty()
                .append(Component.literal("Gave ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(CurrencyFormatter.format(amount)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" to ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(target).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY));
    }

    public static Component takeSuccess(String target, BigDecimal amount) {
        return Component.empty()
                .append(Component.literal("Took ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(CurrencyFormatter.format(amount)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" from ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(target).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY));
    }

    public static Component setSuccess(String target, BigDecimal amount) {
        return Component.empty()
                .append(Component.literal("Set ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(target).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("'s balance to ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(CurrencyFormatter.format(amount)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY));
    }
}
