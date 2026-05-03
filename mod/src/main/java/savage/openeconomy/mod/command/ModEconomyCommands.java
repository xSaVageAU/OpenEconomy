package savage.openeconomy.mod.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import savage.openeconomy.core.EconomyManager;
import savage.openeconomy.util.CurrencyFormatter;
import savage.openeconomy.mod.util.ModMessages;
import java.math.BigDecimal;

/**
 * Commands provided by the standard implementation mod.
 */
public class ModEconomyCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Define the balance logic
        Command<CommandSourceStack> balanceAction = context -> {
            var source = context.getSource();
            var player = source.getPlayerOrException();
            
            var balance = EconomyManager.getInstance().getBalance(player.getUUID());
            source.sendSuccess(() -> Component.empty()
                    .append(Component.literal("Your balance: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(CurrencyFormatter.format(balance)).withStyle(ChatFormatting.YELLOW)), false);
            
            return 1;
        };

        // Register /bal and /balance
        dispatcher.register(Commands.literal("bal").executes(balanceAction));
        dispatcher.register(Commands.literal("balance").executes(balanceAction));

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
                    source.sendSuccess(() -> ModMessages.paySent(target.getGameProfile().name(), amount), false);
                    target.sendSystemMessage(ModMessages.payReceived(sender.getGameProfile().name(), amount));

                    return 1;
                }))));

        // Register the /eco command (Admin)
        dispatcher.register(Commands.literal("eco")
                .then(Commands.literal("give")
                        .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                        .then(Commands.argument("amount", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> {
                            var source = context.getSource();
                            var target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "target");
                            var amount = BigDecimal.valueOf(com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "amount"));

                            EconomyManager.getInstance().addBalance(target.getUUID(), amount);
                            
                            source.sendSuccess(() -> ModMessages.giveSuccess(target.getGameProfile().name(), amount), true);
                            return 1;
                        }))))
                .then(Commands.literal("take")
                        .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                        .then(Commands.argument("amount", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> {
                            var source = context.getSource();
                            var target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "target");
                            var amount = BigDecimal.valueOf(com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "amount"));

                            EconomyManager.getInstance().removeBalance(target.getUUID(), amount);
                            
                            source.sendSuccess(() -> ModMessages.takeSuccess(target.getGameProfile().name(), amount), true);
                            return 1;
                        })))));
    }
}
