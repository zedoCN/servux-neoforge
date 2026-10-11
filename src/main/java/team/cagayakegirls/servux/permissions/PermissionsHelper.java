package team.cagayakegirls.servux.permissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

public class PermissionsHelper {
    public static boolean check(@NotNull Entity entity, @NotNull String permission, PermissionLevel defaultRequiredLevel) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(defaultRequiredLevel, "permissionLevel");
        return check(commandSourceFromEntity(entity), permission, defaultRequiredLevel);
    }

    public static @NotNull Predicate<CommandSourceStack> require(@NotNull String permission, PermissionLevel defaultRequiredLevel) {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(defaultRequiredLevel, "defaultRequiredLevel");
        return player -> check(player, permission, defaultRequiredLevel);
    }

    public static boolean check(CommandSourceStack source, String node, @NotNull PermissionLevel defaultRequiredLevel) {
        ServerPlayer player = source.getPlayer();

        if (player != null) {
            return checkPlayerPermission(player, node, defaultRequiredLevel);
        }

        // For non-player command sources (e.g. command blocks, server console),
        // use the permission level directly
        Permission permission = new Permission.HasCommandLevel(defaultRequiredLevel);
        return source.permissions().hasPermission(permission);
    }

    private static boolean checkPlayerPermission(ServerPlayer player, String node, @NotNull PermissionLevel defaultRequiredLevel) {
        @Nullable PermissionNode<Boolean> permNode = PermissionHandler.getNode(node);

        if (permNode != null) {
            Boolean override = PermissionAPI.getPermission(player, permNode);
            if (override != null) {
                return override;
            }
        }

        // Fall back to level-based permission check
        Permission permission = new Permission.HasCommandLevel(defaultRequiredLevel);
        return player.permissions().hasPermission(permission);
    }

    private static CommandSourceStack commandSourceFromEntity(Entity entity) {
        if (entity instanceof ServerPlayer) {
            return ((ServerPlayer) entity).createCommandSourceStack();
        }
        Level world = entity.level();
        if (world instanceof ServerLevel) {
            return entity.createCommandSourceStackForNameResolution((ServerLevel) world);
        } else {
            throw new IllegalArgumentException("Entity '" + entity + "' is not a server entity. Try passing a CommandSource directly instead.");
        }
    }
}
