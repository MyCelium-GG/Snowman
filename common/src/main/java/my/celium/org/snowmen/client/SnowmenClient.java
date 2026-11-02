package my.celium.org.snowmen.client;

import com.mojang.blaze3d.platform.InputConstants;
import my.celium.org.network.MycelNetwork;
import my.celium.org.snowmen.RotatePacket;
import my.celium.org.snowmen.SnowmenMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Client side: the rotate key. Each loader registers the key and calls {@link #tick}. */
public final class SnowmenClient {
    public static final String ROTATE_KEY_NAME = "key.snowmen.rotate";
    public static final KeyMapping ROTATE_KEY = new KeyMapping(ROTATE_KEY_NAME, InputConstants.KEY_R,
            KeyMapping.Category.GAMEPLAY);

    private SnowmenClient() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        while (ROTATE_KEY.consumeClick()) {
            if (SnowmenMod.rotate != null && MycelNetwork.canSendToServer(SnowmenMod.rotate)) {
                MycelNetwork.sendToServer(SnowmenMod.rotate, new RotatePacket(minecraft.player.isShiftKeyDown()));
            }
        }
    }
}
