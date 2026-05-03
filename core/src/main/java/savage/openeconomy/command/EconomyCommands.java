package savage.openeconomy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import savage.openeconomy.core.EconomyManager;
import savage.openeconomy.util.CurrencyFormatter;
import java.math.BigDecimal;

/**
 * Economy commands for OpenEconomy.
 */
public class EconomyCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Register the /pay command
        dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                .then(Commands.argument("amount", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.01))
                .executes(context -> {
                    var source = context.getSource();
                    var sender = source.getPlayerOrException();
                    var target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "target");
                    var amount = BigDecimal.valueOf(com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "amount"));

                    if (sender.getUUID().equals(target.getUUID())) {
                        source.sendFailure(Component.literal("You cannot pay yourself!").withStyle(ChatFormatting.RED));
                        return 0;
                    }

                    var economy = EconomyManager.getInstance();
                    var senderBalance = economy.getBalance(sender.getUUID());

                    if (senderBalance.compareTo(amount) < 0) {
                        source.sendFailure(Component.literal("Insufficient funds!").withStyle(ChatFormatting.RED));
                        return 0;
                    }

                    // Perform transaction
                    economy.removeBalance(sender.getUUID(), amount);
                    economy.addBalance(target.getUUID(), amount);

                    // Notify
                    source.sendSuccess(() -> savage.openeconomy.util.EconomyMessages.paySent(target.getGameProfile().name(), amount), false);
                    target.sendSystemMessage(savage.openeconomy.util.EconomyMessages.payReceived(sender.getGameProfile().name(), amount));

                    return 1;
                }))));
    }
}
