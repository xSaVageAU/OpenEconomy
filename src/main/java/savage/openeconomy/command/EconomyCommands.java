package savage.openeconomy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import savage.openeconomy.core.EconomyManager;
import savage.openeconomy.util.CurrencyFormatter;

/**
 * Economy commands for OpenEconomy.
 */
public class EconomyCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Define the balance logic once
        Command<CommandSourceStack> balanceAction = context -> {
            var source = context.getSource();
            var player = source.getPlayerOrException();
            
            var balance = EconomyManager.getInstance().getBalance(player.getUUID());
            source.sendSuccess(() -> Component.empty()
                    .append(Component.literal("Your balance: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(CurrencyFormatter.format(balance)).withStyle(ChatFormatting.YELLOW)), false);
            
            return 1;
        };

        // Register both literals with the same action
        dispatcher.register(Commands.literal("bal").executes(balanceAction));
        dispatcher.register(Commands.literal("balance").executes(balanceAction));
    }
}
