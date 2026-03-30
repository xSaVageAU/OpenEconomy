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
import savage.openeconomy.EconomyManager;
import savage.openeconomy.config.ConfigManager;
import savage.openeconomy.config.EconomyConfig;
import savage.openeconomy.util.PermissionsHelper;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.UUID;

/**
 * All economy commands for OpenEconomy (Player & Admin).
 */
public class EconomyCommands {

    public static final SuggestionProvider<CommandSourceStack> PLAYER_SUGGESTIONS = (context, builder) -> {
        var suggestions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        suggestions.addAll(EconomyManager.getInstance().getKnownNames());
        suggestions.addAll(Arrays.asList(context.getSource().getServer().getPlayerNames()));
        return SharedSuggestionProvider.suggest(suggestions, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // --- Player Commands ---
        var balNode = dispatcher.register(Commands.literal("bal")
                .requires(src -> PermissionsHelper.check(src, "openeconomy.command.balance", true))
                .executes(EconomyCommands::checkSelfBal)
                .then(Commands.argument("target", StringArgumentType.string())
                        .requires(src -> PermissionsHelper.check(src, "openeconomy.command.balance.others", true))
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(EconomyCommands::checkOtherBal)));

        dispatcher.register(Commands.literal("balance").redirect(balNode));

        dispatcher.register(Commands.literal("pay")
                .requires(src -> PermissionsHelper.check(src, "openeconomy.command.pay", true))
                .then(Commands.argument("target", StringArgumentType.string())
                        .suggests(PLAYER_SUGGESTIONS)
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                .executes(EconomyCommands::pay))));

        var baltopNode = dispatcher.register(Commands.literal("baltop")
                .requires(src -> PermissionsHelper.check(src, "openeconomy.command.baltop", true))
                .executes(EconomyCommands::balTop));

        dispatcher.register(Commands.literal("balancetop").redirect(baltopNode));

        // --- Admin Commands ---
        dispatcher.register(Commands.literal("eco")
                .requires(src -> PermissionsHelper.check(src, "openeconomy.admin", 2))
                .then(Commands.literal("give")
                        .then(Commands.argument("target", StringArgumentType.string()).suggests(PLAYER_SUGGESTIONS)
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0)).executes(EconomyCommands::adminGive))))
                .then(Commands.literal("take")
                        .then(Commands.argument("target", StringArgumentType.string()).suggests(PLAYER_SUGGESTIONS)
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0)).executes(EconomyCommands::adminTake))))
                .then(Commands.literal("set")
                        .then(Commands.argument("target", StringArgumentType.string()).suggests(PLAYER_SUGGESTIONS)
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0)).executes(EconomyCommands::adminSet))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("target", StringArgumentType.string()).suggests(PLAYER_SUGGESTIONS).executes(EconomyCommands::adminReset)))
                .then(Commands.literal("reload").executes(EconomyCommands::adminReload)));
    }

    // --- Logic ---

    private static int checkSelfBal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var p = ctx.getSource().getPlayerOrException();
        var eco = EconomyManager.getInstance();
        var acc = eco.getOrCreateAccount(p.getUUID(), p.getGameProfile().name());
        ctx.getSource().sendSuccess(() -> Component.literal("Your balance: " + eco.format(acc.balance())), false);
        return 1;
    }

    private static int checkOtherBal(CommandContext<CommandSourceStack> ctx) {
        var name = StringArgumentType.getString(ctx, "target");
        var uuid = lookupUUID(ctx, name);
        if (uuid == null) return fail(ctx, "Player not found.");

        var eco = EconomyManager.getInstance();
        var acc = eco.getOrCreateAccount(uuid, null);
        ctx.getSource().sendSuccess(() -> Component.literal(acc.name() + "'s balance: " + eco.format(acc.balance())), false);
        return 1;
    }

    private static int pay(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var sender = ctx.getSource().getPlayerOrException();
        var targetName = StringArgumentType.getString(ctx, "target");
        var amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(ctx, "amount"));
        var targetUUID = lookupUUID(ctx, targetName);

        if (targetUUID == null) return fail(ctx, "Player not found.");
        if (sender.getUUID().equals(targetUUID)) return fail(ctx, "You cannot pay yourself.");

        var eco = EconomyManager.getInstance();
        if (!eco.removeBalance(sender.getUUID(), amount)) return fail(ctx, "Insufficient funds.");

        eco.addBalance(targetUUID, amount);
        var f = eco.format(amount);
        ctx.getSource().sendSuccess(() -> Component.literal("Paid " + f + " to " + targetName), false);
        notify(ctx, targetUUID, "Received " + f + " from " + sender.getName().getString());
        return 1;
    }

    private static int balTop(CommandContext<CommandSourceStack> ctx) {
        var eco = EconomyManager.getInstance();
        var top = eco.getTopAccounts(10);
        ctx.getSource().sendSuccess(() -> Component.literal("§6--- Top 10 Balances ---"), false);
        for (int i = 0; i < top.size(); i++) {
            final var acc = top.get(i);
            final int rank = i + 1;
            ctx.getSource().sendSuccess(() -> Component.literal("§e" + rank + ". §f" + acc.name() + ": §a" + eco.format(acc.balance())), false);
        }
        return 1;
    }

    // --- Admin Logic ---

    private static int adminGive(CommandContext<CommandSourceStack> ctx) {
        var name = StringArgumentType.getString(ctx, "target");
        var amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(ctx, "amount"));
        var uuid = lookupUUID(ctx, name);
        if (uuid == null) return fail(ctx, "Player not found.");

        EconomyManager.getInstance().addBalance(uuid, amount);
        var f = EconomyManager.getInstance().format(amount);
        ctx.getSource().sendSuccess(() -> Component.literal("§aGave " + f + " to " + name), true);
        notify(ctx, uuid, "§eReceived " + f + " (Admin)");
        return 1;
    }

    private static int adminTake(CommandContext<CommandSourceStack> ctx) {
        var name = StringArgumentType.getString(ctx, "target");
        var amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(ctx, "amount"));
        var uuid = lookupUUID(ctx, name);
        if (uuid == null) return fail(ctx, "Player not found.");

        if (!EconomyManager.getInstance().removeBalance(uuid, amount)) return fail(ctx, "Target has insufficient funds.");
        var f = EconomyManager.getInstance().format(amount);
        ctx.getSource().sendSuccess(() -> Component.literal("§cTook " + f + " from " + name), true);
        return 1;
    }

    private static int adminSet(CommandContext<CommandSourceStack> ctx) {
        var name = StringArgumentType.getString(ctx, "target");
        var amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(ctx, "amount"));
        var uuid = lookupUUID(ctx, name);
        if (uuid == null) return fail(ctx, "Player not found.");

        EconomyManager.getInstance().setBalance(uuid, amount);
        var f = EconomyManager.getInstance().format(amount);
        ctx.getSource().sendSuccess(() -> Component.literal("§eSet " + name + "'s balance to " + f), true);
        notify(ctx, uuid, "§eYour balance has been set to " + f + " by an admin.");
        return 1;
    }

    private static int adminReset(CommandContext<CommandSourceStack> ctx) {
        var name = StringArgumentType.getString(ctx, "target");
        var uuid = lookupUUID(ctx, name);
        if (uuid == null) return fail(ctx, "Player not found.");

        EconomyManager.getInstance().resetBalance(uuid);
        var f = EconomyManager.getInstance().format(EconomyConfig.instance().defaultBalanceDecimal());
        ctx.getSource().sendSuccess(() -> Component.literal("§eReset " + name + "'s balance to " + f), true);
        notify(ctx, uuid, "§eYour balance has been reset to " + f + " by an admin.");
        return 1;
    }

    private static int adminReload(CommandContext<CommandSourceStack> ctx) {
        ConfigManager.reload();
        ctx.getSource().sendSuccess(() -> Component.literal("§aConfig reloaded."), true);
        return 1;
    }

    // --- Helpers ---

    public static UUID lookupUUID(CommandContext<CommandSourceStack> ctx, String name) {
        var p = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
        return p != null ? p.getUUID() : EconomyManager.getInstance().getUUIDFromName(name);
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendFailure(Component.literal(msg));
        return 0;
    }

    private static void notify(CommandContext<CommandSourceStack> ctx, UUID uuid, String msg) {
        var p = ctx.getSource().getServer().getPlayerList().getPlayer(uuid);
        if (p != null) p.sendSystemMessage(Component.literal(msg));
    }
}
