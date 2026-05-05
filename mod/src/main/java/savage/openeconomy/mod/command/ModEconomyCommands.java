package savage.openeconomy.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import savage.openeconomy.core.EconomyManager;
import savage.openeconomy.util.CurrencyFormatter;
import savage.openeconomy.mod.util.ModMessages;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Commands provided by the standard implementation mod.
 */
public class ModEconomyCommands {
    private static final SuggestionProvider<CommandSourceStack> PLAYER_SUGGESTIONS = (context, builder) -> 
            SharedSuggestionProvider.suggest(EconomyManager.getInstance().getAllNames(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Register /bal and /balance
        dispatcher.register(Commands.literal("bal")
                .executes(ModEconomyCommands::executeBalance));
        dispatcher.register(Commands.literal("balance")
                .executes(ModEconomyCommands::executeBalance));

        // Register the /pay command
        dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("target", StringArgumentType.word())
                .suggests(PLAYER_SUGGESTIONS)
                .then(Commands.argument("amount", StringArgumentType.string())
                .executes(ModEconomyCommands::executePay))));

        // Register the /eco command (Admin commands)
        dispatcher.register(Commands.literal("eco")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.literal("give")
                        .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .then(Commands.argument("amount", StringArgumentType.string())
                        .executes(ModEconomyCommands::executeGive))))
                .then(Commands.literal("take")
                        .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .then(Commands.argument("amount", StringArgumentType.string())
                        .executes(ModEconomyCommands::executeTake))))
                .then(Commands.literal("set")
                        .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .then(Commands.argument("amount", StringArgumentType.string())
                        .executes(ModEconomyCommands::executeSet)))));
    }

    private static int executeBalance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayerOrException();
        
        
        var balance = EconomyManager.getInstance().getBalance(player.getUUID());
        source.sendSuccess(() -> Component.empty()
                .append(Component.literal("Your balance: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(CurrencyFormatter.format(balance)).withStyle(ChatFormatting.YELLOW)), false);
        
        return 1;
    }

    private static int executePay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var sender = source.getPlayerOrException();
        
        
        String targetName = StringArgumentType.getString(context, "target");
        UUID targetUuid = resolveTarget(context);
        BigDecimal amount = resolveAmount(context);

        if (sender.getUUID().equals(targetUuid)) {
            throw new SimpleCommandExceptionType(Component.literal("You cannot pay yourself!")).create();
        }

        EconomyManager.getInstance().transfer(sender.getUUID(), targetUuid, amount).thenAccept(success -> {
            if (success) {
                source.sendSuccess(() -> ModMessages.paySent(targetName, amount), false);
                
                var server = source.getServer();
                var onlineTarget = server.getPlayerList().getPlayer(targetUuid);
                if (onlineTarget != null) {
                    onlineTarget.sendSystemMessage(ModMessages.payReceived(sender.getGameProfile().name(), amount));
                }
            } else {
                source.sendFailure(Component.literal("Transaction failed! (Check your balance or try again)"));
            }
        });

        return 1;
    }

    private static int executeGive(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        
        
        String targetName = StringArgumentType.getString(context, "target");
        UUID targetUuid = resolveTarget(context);
        BigDecimal amount = resolveAmount(context);

        EconomyManager.getInstance().addBalance(targetUuid, amount).thenAccept(success -> {
            if (success) {
                source.sendSuccess(() -> ModMessages.giveSuccess(targetName, amount), true);
            } else {
                source.sendFailure(Component.literal("Failed to add balance. Storage error."));
            }
        });
        return 1;
    }

    private static int executeTake(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        
        
        String targetName = StringArgumentType.getString(context, "target");
        UUID targetUuid = resolveTarget(context);
        BigDecimal amount = resolveAmount(context);

        EconomyManager.getInstance().removeBalance(targetUuid, amount).thenAccept(success -> {
            if (success) {
                source.sendSuccess(() -> ModMessages.takeSuccess(targetName, amount), true);
            } else {
                source.sendFailure(Component.literal("Failed to remove balance. (Insufficient funds?)"));
            }
        });
        return 1;
    }

    private static int executeSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        
        
        String targetName = StringArgumentType.getString(context, "target");
        UUID targetUuid = resolveTarget(context);
        BigDecimal amount = resolveAmount(context);

        EconomyManager.getInstance().setBalance(targetUuid, amount).thenAccept(success -> {
            if (success) {
                source.sendSuccess(() -> ModMessages.setSuccess(targetName, amount), true);
            } else {
                source.sendFailure(Component.literal("Failed to set balance. Storage error."));
            }
        });
        return 1;
    }

    private static BigDecimal resolveAmount(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            BigDecimal amount = new BigDecimal(StringArgumentType.getString(context, "amount"));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new Exception();
            return amount;
        } catch (Exception e) {
            throw new SimpleCommandExceptionType(Component.literal("Invalid amount! Use a positive number.")).create();
        }
    }

    private static UUID resolveTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String targetName = StringArgumentType.getString(context, "target");
        UUID targetUuid = EconomyManager.getInstance().getUUIDByName(targetName);
        if (targetUuid == null) {
            throw new SimpleCommandExceptionType(Component.literal("Player not found!")).create();
        }
        return targetUuid;
    }
}
