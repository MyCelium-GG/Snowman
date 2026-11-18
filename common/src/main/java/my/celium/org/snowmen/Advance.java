package my.celium.org.snowmen;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The Snowman advancements (data/snowmen/advancement). Each has one "done" criterion that
 * only the mod grants.
 */
final class Advance {
    static final String ROOT = "root";
    private static final String CRITERION = "done";

    private Advance() {
    }

    /** Grants an advancement, and the root so the tab shows up. Does nothing for null or non-server players. */
    static void award(Player player, String name) {
        if (!(player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) {
            return;
        }
        grant(level, sp, ROOT);
        if (!ROOT.equals(name)) {
            grant(level, sp, name);
        }
    }

    private static void grant(ServerLevel level, ServerPlayer player, String name) {
        AdvancementHolder holder = level.getServer().getAdvancements()
                .get(Identifier.fromNamespaceAndPath(SnowmenMod.ID, name));
        if (holder != null) {
            player.getAdvancements().award(holder, CRITERION);
        }
    }
}
