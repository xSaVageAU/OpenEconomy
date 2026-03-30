package savage.openeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import savage.openeconomy.EconomyManager;
import savage.openeconomy.model.AccountData;
import savage.openeconomy.util.PermissionsHelper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Player-facing economy commands: /bal, /balance, /pay, /baltop, /balancetop.
 */
public class EconomyCommands {

    private static final SuggestionProvider<CommandSourceStack> PLAYER_SUGGESTIONS = (context, builder) -> {
        List<String> suggestions = new ArrayList<>(EconomyManager.getInstance().getAllPlayerNames());
        suggestions.addAll(Arrays.asList(context.getSource().getServer().getPlayerNames()));
        return SharedSuggestionProvider.suggest(suggestions, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /bal and /balance
        var balCommand = Commands.literal("bal")
                .requires(source -> PermissionsHelper.check(source, "openeconomy.command.balance", true))
                .executes(EconomyCommands::checkSelfBalance)
                .then(Commands.argument("target", StringArgumentType.string())
                        .requires(source -> PermissionsHelper.check(source, "openeconomy.command.balance.others", true))
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(EconomyCommands::checkOtherBalance));

        dispatcher.register(balCommand);
        dispatcher.register(Commands.literal("balance")
                .requires(balCommand.getRequirement())
                .executes(EconomyCommands::checkSelfBalance)
                .redirect(balCommand.build()));

        // /pay <target> <amount>
        dispatcher.register(Commands.literal("pay")
                .requires(source -> PermissionsHelper.check(source, "openeconomy.command.pay", true))
                .then(Commands.argument("target", StringArgumentType.string())
                        .suggests(PLAYER_SUGGESTIONS)
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                .executes(EconomyCommands::pay))));

        // /baltop and /balancetop
        var baltopCommand = Commands.literal("baltop")
                .requires(source -> PermissionsHelper.check(source, "openeconomy.command.baltop", true))
                .executes(EconomyCommands::balTop);

        dispatcher.register(baltopCommand);
        dispatcher.register(Commands.literal("balancetop")
                .requires(baltopCommand.getRequirement())
                .executes(EconomyCommands::balTop));
    }

    private static int checkSelfBalance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        EconomyManager eco = EconomyManager.getInstance();
        AccountData account = eco.getOrCreateAccount(player.getUUID(), player.getGameProfile().name());
        String formatted = eco.format(account.getBalance());
        context.getSource().sendSuccess(() -> Component.literal("Your balance: " + formatted), false);
        return 1;
    }

    private static int checkOtherBalance(CommandContext<CommandSourceStack> context) {
        String targetName = StringArgumentType.getString(context, "target");
        UUID targetUUID = lookupUUID(context, targetName);

        if (targetUUID == null) {
            context.getSource().sendFailure(Component.literal("Player not found."));
            return 0;
        }

        EconomyManager eco = EconomyManager.getInstance();
        AccountData account = eco.getOrCreateAccount(targetUUID, null);
        String formatted = eco.format(account.getBalance());
        context.getSource().sendSuccess(() -> Component.literal(account.getName() + "'s balance: " + formatted), false);
        return 1;
    }

    private static int pay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayerOrException();
        String targetName = StringArgumentType.getString(context, "target");
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));

        UUID targetUUID = lookupUUID(context, targetName);
        if (targetUUID == null) {
            context.getSource().sendFailure(Component.literal("Player not found."));
            return 0;
        }

        if (sender.getUUID().equals(targetUUID)) {
            context.getSource().sendFailure(Component.literal("You cannot pay yourself."));
            return 0;
        }

        EconomyManager eco = EconomyManager.getInstance();
        if (eco.removeBalance(sender.getUUID(), amount)) {
            eco.addBalance(targetUUID, amount);
            String formatted = eco.format(amount);
            context.getSource().sendSuccess(() -> Component.literal("Paid " + formatted + " to " + targetName), false);

            // Notify the target if they're online
            ServerPlayer targetPlayer = context.getSource().getServer().getPlayerList().getPlayer(targetUUID);
            if (targetPlayer != null) {
                targetPlayer.sendSystemMessage(Component.literal("Received " + formatted + " from " + sender.getName().getString()));
            }
        } else {
            context.getSource().sendFailure(Component.literal("Insufficient funds."));
            return 0;
        }

        return 1;
    }

    private static int balTop(CommandContext<CommandSourceStack> context) {
        EconomyManager eco = EconomyManager.getInstance();
        List<AccountData> top = eco.getTopAccounts(10);

        context.getSource().sendSuccess(() -> Component.literal("§6--- Top 10 Balances ---"), false);
        for (int i = 0; i < top.size(); i++) {
            AccountData account = top.get(i);
            int rank = i + 1;
            String formatted = eco.format(account.getBalance());
            context.getSource().sendSuccess(() -> Component.literal("§e" + rank + ". §f" + account.getName() + ": §a" + formatted), false);
        }
        return 1;
    }

    private static UUID lookupUUID(CommandContext<CommandSourceStack> context, String name) {
        // Check online players first
        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (target != null) return target.getUUID();
        // Fall back to stored accounts
        return EconomyManager.getInstance().getUUIDFromName(name);
    }
}
