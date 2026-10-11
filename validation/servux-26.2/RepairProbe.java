package probe;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.handler.IPermissionHandler;
import team.cagayakegirls.servux.permissions.PermissionsHelper;

@Mod("servux_repair_probe")
public class RepairProbe {
    private static final String NODE = "servux.commands.info";
    private static int assertions;
    public RepairProbe() { NeoForge.EVENT_BUS.addListener(this::started); }
    private static void expect(boolean actual, boolean expected, String label) {
        if (actual != expected) throw new AssertionError(label + ": " + actual);
        assertions++;
    }
    private static class LevelPlayer extends FakePlayer {
        private PermissionLevel level;
        LevelPlayer(ServerLevel world, PermissionLevel level) {
            super(world, new GameProfile(UUID.randomUUID(), "RepairProbe"));
            this.level = level;
        }
        @Override public PermissionSet permissions() {
            return LevelBasedPermissionSet.forLevel(level == null ? PermissionLevel.ALL : level);
        }
    }
    private void started(ServerStartedEvent event) {
        try {
            var server = event.getServer();
            var world = server.overworld();
            var minecraft = FakePlayerFactory.getMinecraft(world);
            var machine = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "AE2Probe"));
            expect(machine.connection.getConnection().channel() == null, true, "null Netty channel");
            expect(FakePlayerFactory.getMinecraft(world) == minecraft, true, "factory cache");
            System.out.println("[PROBE] FAKEPLAYER_NETWORK_PASSED");

            // These are test-only player objects; no production OP list is edited.
            for (PermissionLevel level : PermissionLevel.values()) {
                ServerPlayer player = new LevelPlayer(world, level);
                for (PermissionLevel required : PermissionLevel.values()) {
                    expect(PermissionsHelper.check(player, NODE, required),
                        level.isEqualOrHigherThan(required), "null fallback " + level + "/" + required);
                    expect(PermissionsHelper.check(player, "servux.unregistered_probe", required),
                        level.isEqualOrHigherThan(required), "missing node " + level + "/" + required);
                }
                // Exercise the exact command-tree filtering that failed during real login.
                server.getCommands().sendCommands(player);
            }
            for (PermissionLevel required : PermissionLevel.values()) {
                expect(PermissionsHelper.check(server.createCommandSourceStack(), NODE, required),
                    true, "console " + required);
            }
            System.out.println("[PROBE] LEVELS_AND_COMMAND_TREES_PASSED");

            // Install a controlled test permission provider, and always restore the original.
            Field field = PermissionAPI.class.getDeclaredField("activeHandler");
            field.setAccessible(true);
            var original = (IPermissionHandler) field.get(null);
            Boolean[] override = { null };
            var provider = Proxy.newProxyInstance(IPermissionHandler.class.getClassLoader(),
                new Class<?>[] { IPermissionHandler.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getPermission")) return override[0];
                    return method.invoke(original, args);
                });
            try {
                field.set(null, provider);
                var ordinary = new LevelPlayer(world, PermissionLevel.ALL);
                var operator = new LevelPlayer(world, PermissionLevel.OWNERS);
                override[0] = true;
                expect(PermissionsHelper.check(ordinary, NODE, PermissionLevel.OWNERS), true, "explicit allow");
                override[0] = false;
                expect(PermissionsHelper.check(operator, NODE, PermissionLevel.ALL), false, "explicit deny");
                override[0] = null;
                expect(PermissionsHelper.check(ordinary, NODE, PermissionLevel.GAMEMASTERS), false, "ordinary fallback");
                expect(PermissionsHelper.check(operator, NODE, PermissionLevel.GAMEMASTERS), true, "OP fallback");
            } finally {
                field.set(null, original);
            }
            System.out.println("[PROBE] REPAIR_PASSED assertions=" + assertions);
        } catch (Throwable e) {
            System.out.println("[PROBE] REPAIR_FAILED " + e);
            e.printStackTrace();
        }
    }
}
