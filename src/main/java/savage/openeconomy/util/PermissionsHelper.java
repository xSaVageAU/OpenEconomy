package savage.openeconomy.util;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Thin wrapper around the Fabric Permissions API.
 * Falls back to vanilla OP levels when no permissions mod is installed.
 */
public class PermissionsHelper {

    private PermissionsHelper() {}

    /**
     * Checks if a command source has a permission, with fallback OP level.
     */
    public static boolean check(CommandSourceStack source, String permission, int fallbackOpLevel) {
        boolean vanillaFallback = fallbackOpLevel <= 0 || 
            source.permissions().hasPermission(fallbackOpLevel <= 2 
                ? net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER 
                : net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN);
        
        return Permissions.check(source, permission, vanillaFallback);
    }

    /**
     * Checks if a player has a permission, with fallback OP level.
     */
    public static boolean check(ServerPlayer player, String permission, int fallbackOpLevel) {
        boolean vanillaFallback = fallbackOpLevel <= 0 || 
            player.permissions().hasPermission(fallbackOpLevel <= 2 
                ? net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER 
                : net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN);
        
        return Permissions.check(player, permission, vanillaFallback);
    }

    /**
     * Boolean-friendly overload: true = everyone (level 0), false = OP only (level 2).
     */
    public static boolean check(CommandSourceStack source, String permission, boolean anyone) {
        return check(source, permission, anyone ? 0 : 2);
    }
}
