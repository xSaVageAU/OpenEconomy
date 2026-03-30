package savage.openeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import savage.openeconomy.EconomyManager;
import savage.openeconomy.config.ConfigManager;
import savage.openeconomy.util.PermissionsHelper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Admin economy commands grouped under /eco.
 * /eco give|take|set|reset|reload
 */
public class AdminCommands {

    private static final SuggestionProvider<CommandSourceStack> PLAYER_SUGGESTIONS = (context, builder) -> {
        List<String> suggestions = new ArrayList<>(EconomyManager.getInstance().getAllPlayerNames());
        suggestions.addAll(Arrays.asList(context.getSource().getServer().getPlayerNames()));
        return SharedSuggestionProvider.suggest(suggestions, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("eco")
                .requires(source -> PermissionsHelper.check(source, "openeconomy.admin", 2))

                // /eco give <target> <amount>
                .then(Commands.literal("give")
                        .then(Commands.argument("target", StringArgumentType.string())
                                .suggests(PLAYER_SUGGESTIONS)
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(AdminCommands::give))))

                // /eco take <target> <amount>
                .then(Commands.literal("take")
                        .then(Commands.argument("target", StringArgumentType.string())
                                .suggests(PLAYER_SUGGESTIONS)
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(AdminCommands::take))))

                // /eco set <target> <amount>
                .then(Commands.literal("set")
                        .then(Commands.argument("target", StringArgumentType.string())
                                .suggests(PLAYER_SUGGESTIONS)
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(AdminCommands::set))))

                // /eco reset <target>
                .then(Commands.literal("reset")
                        .then(Commands.argument("target", StringArgumentType.string())
                                .suggests(PLAYER_SUGGESTIONS)
                                .executes(AdminCommands::reset)))

                // /eco reload
                .then(Commands.literal("reload")
                        .executes(AdminCommands::reload)));
    }

    private static int give(CommandContext<CommandSourceStack> context) {
        String targetName = StringArgumentType.getString(context, "target");
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));

        UUID targetUUID = lookupUUID(context, targetName);
        if (targetUUID == null) {
            context.getSource().sendFailure(Component.literal("Player not found."));
            return 0;
        }

        EconomyManager eco = EconomyManager.getInstance();
        eco.addBalance(targetUUID, amount);
        String formatted = eco.format(amount);
        context.getSource().sendSuccess(() -> Component.literal("§aGave " + formatted + " to " + targetName), true);

        notifyTarget(context, targetUUID, "§eReceived " + formatted + " (Admin)");
        return 1;
    }

    private static int take(CommandContext<CommandSourceStack> context) {
        String targetName = StringArgumentType.getString(context, "target");
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));

        UUID targetUUID = lookupUUID(context, targetName);
        if (targetUUID == null) {
            context.getSource().sendFailure(Component.literal("Player not found."));
            return 0;
        }

        EconomyManager eco = EconomyManager.getInstance();
        if (eco.removeBalance(targetUUID, amount)) {
            String formatted = eco.format(amount);
            context.getSource().sendSuccess(() -> Component.literal("§cTook " + formatted + " from " + targetName), true);
        } else {
            context.getSource().sendFailure(Component.literal("Target has insufficient funds."));
            return 0;
        }

        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        String targetName = StringArgumentType.getString(context, "target");
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));

        UUID targetUUID = lookupUUID(context, targetName);
        if (targetUUID == null) {
            context.getSource().sendFailure(Component.literal("Player not found."));
            return 0;
        }

        EconomyManager eco = EconomyManager.getInstance();
        eco.setBalance(targetUUID, amount);
        String formatted = eco.format(amount);
        context.getSource().sendSuccess(() -> Component.literal("§eSet " + targetName + "'s balance to " + formatted), true);

        notifyTarget(context, targetUUID, "§eYour balance has been set to " + formatted + " by an admin.");
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        String targetName = StringArgumentType.getString(context, "target");

        UUID targetUUID = lookupUUID(context, targetName);
        if (targetUUID == null) {
            context.getSource().sendFailure(Component.literal("Player not found."));
            return 0;
        }

        EconomyManager eco = EconomyManager.getInstance();
        eco.resetBalance(targetUUID);
        BigDecimal defaultBal = ConfigManager.getConfig().getDefaultBalanceDecimal();
        String formatted = eco.format(defaultBal);
        context.getSource().sendSuccess(() -> Component.literal("§eReset " + targetName + "'s balance to " + formatted), true);

        notifyTarget(context, targetUUID, "§eYour balance has been reset to " + formatted + " by an admin.");
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        ConfigManager.reload();
        context.getSource().sendSuccess(() -> Component.literal("§aOpenEconomy config reloaded."), true);
        return 1;
    }

    private static UUID lookupUUID(CommandContext<CommandSourceStack> context, String name) {
        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (target != null) return target.getUUID();
        return EconomyManager.getInstance().getUUIDFromName(name);
    }

    private static void notifyTarget(CommandContext<CommandSourceStack> context, UUID targetUUID, String message) {
        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayer(targetUUID);
        if (target != null) {
            target.sendSystemMessage(Component.literal(message));
        }
    }
}
