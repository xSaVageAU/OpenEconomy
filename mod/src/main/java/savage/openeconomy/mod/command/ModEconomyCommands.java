package savage.openeconomy.mod.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.arguments.StringArgumentType;
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
                .then(Commands.argument("target", StringArgumentType.word())
                .suggests(PLAYER_SUGGESTIONS)
                .then(Commands.argument("amount", StringArgumentType.string())
                .executes(context -> {
                    var source = context.getSource();
                    var sender = source.getPlayerOrException();
                    String targetName = StringArgumentType.getString(context, "target");
                    UUID targetUuid = EconomyManager.getInstance().getUUIDByName(targetName);
                    
                    if (targetUuid == null) {
                        source.sendFailure(Component.literal("Player not found!").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    BigDecimal amount;
                    try {
                        amount = new BigDecimal(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "amount"));
                        if (amount.compareTo(new BigDecimal("0.01")) < 0) throw new NumberFormatException();
                    } catch (Exception e) {
                        source.sendFailure(Component.literal("Invalid amount! Use a positive number like 10.50").withStyle(ChatFormatting.RED));
                        return 0;
                    }

                    if (sender.getUUID().equals(targetUuid)) {
                        source.sendFailure(Component.literal("You cannot pay yourself!").withStyle(ChatFormatting.RED));
                        return 0;
                    }

                    boolean success = EconomyManager.getInstance().transfer(sender.getUUID(), targetUuid, amount);

                    if (!success) {
                        source.sendFailure(Component.literal("Transaction failed! (Check your balance)").withStyle(ChatFormatting.RED));
                        return 0;
                    }

                    // Notify
                    source.sendSuccess(() -> ModMessages.paySent(targetName, amount), false);
                    
                    // If target is online, send them a message too
                    var server = source.getServer();
                    var onlineTarget = server.getPlayerList().getPlayer(targetUuid);
                    if (onlineTarget != null) {
                        onlineTarget.sendSystemMessage(ModMessages.payReceived(sender.getGameProfile().name(), amount));
                    }

                    return 1;
                }))));

        // Register the /eco command (Admin commands)
        dispatcher.register(Commands.literal("eco")
                .then(Commands.literal("give")
                        .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .then(Commands.argument("amount", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(context -> {
                            var source = context.getSource();
                            String targetName = StringArgumentType.getString(context, "target");
                            UUID targetUuid = EconomyManager.getInstance().getUUIDByName(targetName);

                            if (targetUuid == null) {
                                source.sendFailure(Component.literal("Player not found!").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            
                            BigDecimal amount;
                            try {
                                amount = new BigDecimal(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "amount"));
                                if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
                            } catch (Exception e) {
                                source.sendFailure(Component.literal("Invalid amount!").withStyle(ChatFormatting.RED));
                                return 0;
                            }

                            EconomyManager.getInstance().addBalance(targetUuid, amount);
                            
                            source.sendSuccess(() -> ModMessages.giveSuccess(targetName, amount), true);
                            return 1;
                        }))))
                .then(Commands.literal("take")
                        .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .then(Commands.argument("amount", StringArgumentType.string())
                        .executes(context -> {
                            var source = context.getSource();
                            String targetName = StringArgumentType.getString(context, "target");
                            UUID targetUuid = EconomyManager.getInstance().getUUIDByName(targetName);

                            if (targetUuid == null) {
                                source.sendFailure(Component.literal("Player not found!").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            
                            BigDecimal amount;
                            try {
                                amount = new BigDecimal(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "amount"));
                                if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
                            } catch (Exception e) {
                                source.sendFailure(Component.literal("Invalid amount!").withStyle(ChatFormatting.RED));
                                return 0;
                            }

                            EconomyManager.getInstance().removeBalance(targetUuid, amount);
                            
                            source.sendSuccess(() -> ModMessages.takeSuccess(targetName, amount), true);
                            return 1;
                        }))))
                .then(Commands.literal("set")
                        .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .then(Commands.argument("amount", StringArgumentType.string())
                        .executes(context -> {
                            var source = context.getSource();
                            String targetName = StringArgumentType.getString(context, "target");
                            UUID targetUuid = EconomyManager.getInstance().getUUIDByName(targetName);

                            if (targetUuid == null) {
                                source.sendFailure(Component.literal("Player not found!").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            
                            BigDecimal amount;
                            try {
                                amount = new BigDecimal(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "amount"));
                                if (amount.compareTo(BigDecimal.ZERO) < 0) throw new NumberFormatException();
                            } catch (Exception e) {
                                source.sendFailure(Component.literal("Invalid amount!").withStyle(ChatFormatting.RED));
                                return 0;
                            }

                            EconomyManager.getInstance().setBalance(targetUuid, amount);
                            
                            source.sendSuccess(() -> ModMessages.setSuccess(targetName, amount), true);
                            return 1;
                        })))));
    }
}
