package my.celium.org.snowmen;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import my.celium.org.snowmen.mixin.BlockDisplayAccessor;
import my.celium.org.snowmen.mixin.DisplayAccessor;
import my.celium.org.snowmen.mixin.InteractionAccessor;
import my.celium.org.snowmen.mixin.ItemDisplayAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Everything players do with snowballs and snowmen, on the server.
 *
 * <p>A snowball or snowman is one invisible {@link Interaction} entity (the "root", which
 * players click) plus vanilla display entities at the same spot for the looks. What it is
 * made of is stored as tags on the root ({@link State}); each display is tagged with its
 * root's UUID. Displays are not passengers, so they can glide smoothly when the root moves.
 */
public final class Snowman {
    /** Ticks clients take to glide the displays to where the server moved them. */
    private static final int MOVE_LERP = 2;
    /** Two hits on a snowman within this many ticks knock it over. */
    private static final int DOUBLE_HIT_TICKS = 12;
    /** How often melting is checked, in ticks. */
    private static final int MELT_TICKS = 300;
    /** Item data key of a picked-up snowball. */
    private static final String ITEM_SIZE = "snowmen_size";
    private static final String OWNER = State.PREFIX + "of=";
    /** How often the rotate hint is refreshed, and from how far away. */
    private static final int HINT_TICKS = 20;
    private static final double HINT_RANGE = 3.5;
    /** How far away the rotate key reaches, and how far one press turns. */
    private static final double ROTATE_RANGE = 5;
    private static final float ROTATE_STEP = 45;
    /** Snowballs dropped when a giant snowman falls apart, at most. */
    private static final int MAX_SNOW_DROPS = 64;

    private static final Map<Interaction, Motion> MOTION = new WeakHashMap<>();
    private static final Map<Interaction, Quaternionf> SPIN = new WeakHashMap<>();
    private static final Map<Interaction, Long> LAST_HIT = new WeakHashMap<>();
    private static final Map<Interaction, int[]> WOBBLE = new WeakHashMap<>();
    private static final Map<Interaction, List<Display>> PARTS = new WeakHashMap<>();
    /** A hat waiting for the next rebuild, because the display that holds it is created there. */
    private static final Map<Interaction, ItemStack> NEW_HAT = new WeakHashMap<>();

    private Snowman() {
    }

    public static boolean isRoot(Entity entity) {
        return entity instanceof Interaction && entity.entityTags().contains(State.ROOT);
    }

    private static boolean isPart(Entity entity) {
        return entity instanceof Display && entity.entityTags().contains(State.PART);
    }

    // ---------------------------------------------------------------- the snowball item

    /** A picked-up snowball: the rolled snowball item, remembering its size. */
    public static ItemStack ballItem(int size) {
        ItemStack stack = new ItemStack(SnowmenMod.rolledSnowball.get());
        CompoundTag tag = new CompoundTag();
        tag.putInt(ITEM_SIZE, Ball.clamp(size));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        // The item model picks a bigger or smaller ball from this number (assets/snowmen/items).
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of((float) Ball.clamp(size)), List.of(), List.of(), List.of()));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.snowmen.rolled_snowball.size", Ball.clamp(size))
                        .withStyle(st -> st.withItalic(false).withColor(Ice.ICE)),
                Component.translatable("item.snowmen.rolled_snowball.hint")
                        .withStyle(st -> st.withItalic(false).withColor(Ice.DEEP)))));
        return stack;
    }

    /** The Top Hat and a range of snowball sizes for the creative menu. */
    public static List<ItemStack> creativeItems() {
        List<ItemStack> out = new ArrayList<>();
        if (SnowmenMod.topHat == null || SnowmenMod.rolledSnowball == null) {
            return out;
        }
        out.add(new ItemStack(SnowmenMod.topHat.get()));
        for (int size = Ball.MIN_SIZE; size <= Ball.MAX_SIZE; size++) {
            out.add(ballItem(size));
        }
        for (int size : new int[] {20, 24, 32, 48, 64, 100}) {
            out.add(ballItem(size));
        }
        return out;
    }

    /** Size of a picked-up snowball, or 0 for any other item. */
    public static int ballSize(ItemStack stack) {
        if (SnowmenMod.rolledSnowball == null || !stack.is(SnowmenMod.rolledSnowball.get())) {
            return 0;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 0;
        }
        int size = data.copyTag().getIntOr(ITEM_SIZE, 0);
        return size > 0 ? Ball.clamp(size) : 0;
    }

    // ---------------------------------------------------------------- blocks

    /** The rolled snowball item used on a block: put the snowball down there. */
    public static InteractionResult place(Level level, BlockPos pos, Direction face, Player player, ItemStack held) {
        int size = ballSize(held);
        if (size <= 0 || !SnowmenMod.isModEnabled()) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        BlockState state = level.getBlockState(pos);
        BlockPos at = state.canBeReplaced() || state.getBlock() instanceof SnowLayerBlock ? pos : pos.relative(face);
        if (!canOccupy(server, at) || rootAt(server, at, null) != null) {
            sound(server, Vec3.atCenterOf(at), "deny", 1.0f);
            return InteractionResult.FAIL;
        }
        spawn(server, Vec3.atBottomCenterOf(at), State.ball(size), 0);
        if (player == null || !player.hasInfiniteMaterials()) {
            held.shrink(1);
        }
        sound(server, Vec3.atCenterOf(at), "ball.place", 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Right-click on a block. Returns null to let vanilla handle it. */
    public static InteractionResult useBlock(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !SnowmenMod.isModEnabled() || player.isSpectator()) {
            return null;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            return null;
        }
        BlockPos at;
        if (state.getBlock() instanceof SnowLayerBlock) {
            at = pos;
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers > 1) {
                level.setBlock(pos, state.setValue(SnowLayerBlock.LAYERS, layers - 1), Block.UPDATE_ALL);
            } else {
                level.removeBlock(pos, false);
            }
        } else if (state.is(Blocks.SNOW_BLOCK) && hit.getDirection() == Direction.UP
                && level.getBlockState(pos.above()).canBeReplaced()) {
            at = pos.above();
        } else {
            return null;
        }
        if (rootAt(server, at, null) != null) {
            return InteractionResult.FAIL;
        }
        spawn(server, Vec3.atBottomCenterOf(at), State.ball(Ball.MIN_SIZE), 0);
        sound(server, Vec3.atCenterOf(at), "ball.pack", 1.0f);
        return InteractionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- clicks

    /** Right-click on an interaction entity. Returns null when it is not one of ours. */
    public static InteractionResult interact(Interaction root, Player player, InteractionHand hand) {
        if (!isRoot(root)) {
            return null;
        }
        if (!(root.level() instanceof ServerLevel server) || player.isSpectator()) {
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        State s = State.read(root.entityTags());
        ItemStack item = player.getItemInHand(hand);
        int carried = ballSize(item);
        if (carried > 0) {
            return stack(server, root, s, item, carried, player);
        }
        if (item.isEmpty()) {
            if (player.isSecondaryUseActive()) {
                return takeOff(server, root, s, player);
            }
            if (s.sizes.length == 1) {
                give(player, ballItem(s.top()));
                discard(root);
                sound(server, root.position(), "ball.pickup", 1.0f);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        if (!s.complete()) {
            return InteractionResult.PASS;
        }
        return decorate(server, root, s, player, item);
    }

    /** Left-click on an interaction entity. True when it was one of ours. */
    public static boolean attack(Interaction root, Entity attacker) {
        if (!isRoot(root)) {
            return false;
        }
        if (!(root.level() instanceof ServerLevel server) || !(attacker instanceof Player player)
                || player.isSpectator()) {
            return true;
        }
        State s = State.read(root.entityTags());
        if (s.sizes.length == 1 && !player.isSecondaryUseActive()) {
            kick(server, root, s, player);
            return true;
        }
        long now = server.getGameTime();
        Long last = LAST_HIT.get(root);
        if (s.sizes.length == 1 || (last != null && now - last <= DOUBLE_HIT_TICKS)) {
            breakApart(server, root, s, true);
        } else {
            LAST_HIT.put(root, now);
            wobble(root, s);
            sound(server, root.position().add(0, 0.5, 0), "snowman.hit", 1.0f);
        }
        return true;
    }

    // ---------------------------------------------------------------- rolling

    /** How a lone snowball is moving right now. Not saved: a ball at rest has none. */
    private static final class Motion {
        double vx;
        double vz;
        double sinceSound;
        BlockPos lastCell;
        /** Blocks whose snow this ball picked up recently, oldest first. */
        final java.util.LinkedHashSet<Long> picked = new java.util.LinkedHashSet<>();
    }

    /** A hit kicks the ball away from the player, in the direction of the hit. Every hit adds speed. */
    private static void kick(ServerLevel level, Interaction root, State s, Player player) {
        double dx = root.getX() - player.getX();
        double dz = root.getZ() - player.getZ();
        double len = Math.hypot(dx, dz);
        if (len < 1e-3) {
            Vec3 look = player.getLookAngle();
            dx = look.x;
            dz = look.z;
            len = Math.hypot(dx, dz);
            if (len < 1e-3) {
                return;
            }
        }
        double speed = Roll.kick(s.top());
        Motion m = MOTION.computeIfAbsent(root, r -> new Motion());
        m.vx += dx / len * speed;
        m.vz += dz / len * speed;
        double v = Math.hypot(m.vx, m.vz);
        if (v > Roll.MAX_SPEED) {
            m.vx *= Roll.MAX_SPEED / v;
            m.vz *= Roll.MAX_SPEED / v;
        }
        sound(level, root.position(), "snowman.hit", Math.max(0.6f, 1.2f - s.top() * 0.02f));
    }

    /** Players walking into a lone snowball push it ahead of them. */
    private static void push(ServerLevel level, Interaction root, State s) {
        float r = Ball.scale(s.top()) / 2;
        double reach = r + 0.5;
        for (Player p : level.getEntitiesOfClass(Player.class,
                root.getBoundingBox().inflate(reach + 0.5, 1.5, reach + 0.5))) {
            if (p.isSpectator() || Math.abs(p.getY() - root.getY()) > Math.max(1.2, r * 2)) {
                continue;
            }
            double dx = root.getX() - p.getX();
            double dz = root.getZ() - p.getZ();
            double d = Math.hypot(dx, dz);
            if (d > reach || d < 1e-3) {
                continue;
            }
            Vec3 movement = p.getKnownMovement();
            double toward = (movement.x * dx + movement.z * dz) / d;
            if (toward <= 0.02) {
                continue;
            }
            // Keep pace with the player, and get back out in front if they caught up, so the
            // ball always rolls ahead of you instead of you walking through it.
            double want = toward * Roll.pushFactor(s.top()) + Math.max(0, reach - d) * Roll.CATCH_UP;
            Motion m = MOTION.computeIfAbsent(root, k -> new Motion());
            double along = (m.vx * dx + m.vz * dz) / d;
            if (along < want) {
                m.vx += dx / d * (want - along);
                m.vz += dz / d * (want - along);
            }
        }
    }

    /** One tick of rolling: move, follow the ground, grow on snow, spin, slow down. */
    private static void step(ServerLevel level, Interaction root, State s) {
        Motion m = MOTION.get(root);
        if (m == null) {
            return;
        }
        if (Math.hypot(m.vx, m.vz) < Roll.MIN_SPEED) {
            MOTION.remove(root);
            settle(root, s);
            return;
        }
        Vec3 from = root.position();
        // X and Z separately, so the ball slides along a wall instead of stopping dead.
        Vec3 pos = from;
        Vec3 next = move(level, root, s, pos, m.vx, 0);
        if (next == null) {
            bounce(level, root, s, m.vx);
            m.vx = -m.vx * Roll.BOUNCE;
        } else {
            pos = next;
        }
        next = move(level, root, s, pos, 0, m.vz);
        if (next == null) {
            bounce(level, root, s, m.vz);
            m.vz = -m.vz * Roll.BOUNCE;
        } else {
            pos = next;
        }
        double moved = Math.hypot(pos.x - from.x, pos.z - from.z);
        int size = s.top();
        BlockPos cell = BlockPos.containing(pos.x, pos.y + 0.01, pos.z);
        boolean onSnow = level.getBlockState(cell).getBlock() instanceof SnowLayerBlock
                || level.getBlockState(cell.below()).is(Blocks.SNOW_BLOCK)
                || level.getBlockState(cell.below()).is(Blocks.POWDER_SNOW);
        if (SnowmenMod.snowTrail == null || SnowmenMod.snowTrail.get()) {
            clearTrail(level, pos, trailRadius(Ball.scale(size) / 2), m.picked);
        }
        // One size for every block of snow the ball rolls onto. That block counts when it still
        // has snow, or when this ball's own (wider) trail picked its snow up a moment earlier;
        // each picked block counts once, so rolling back over the trail adds nothing.
        if (!cell.equals(m.lastCell)) {
            m.lastCell = cell;
            if (m.picked.remove(cell.asLong()) || onSnow) {
                size = Math.min(SnowmenMod.maxBallSize(), size + 1);
            }
        }
        if (moved > 1e-4) {
            Vector3f axis = new Vector3f((float) (pos.z - from.z), 0, (float) -(pos.x - from.x)).normalize();
            Quaternionf spin = SPIN.computeIfAbsent(root, r -> new Quaternionf());
            new Quaternionf().rotateAxis(Roll.spinStep(moved, size), axis).mul(spin, spin);
        }
        if (size != s.top()) {
            s.sizes[0] = size;
            writeState(root, s);
            resize(root, s);
            sound(level, pos, "ball.grow", Math.max(0.5f, 1.4f - size * 0.02f));
        }
        root.snapTo(pos);
        for (Display part : parts(root)) {
            part.snapTo(pos.x, pos.y, pos.z, 0, 0);
        }
        layout(root, s, MOVE_LERP, 0);
        m.sinceSound += moved;
        if (m.sinceSound >= Roll.SOUND_STEP) {
            m.sinceSound = 0;
            sound(level, pos, "ball.roll", Math.max(0.55f, 1.15f - size * 0.025f)
                    + level.getRandom().nextFloat() * 0.1f);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()),
                    pos.x, pos.y + 0.1, pos.z, 4, Ball.scale(size) * 0.3, 0.05, Ball.scale(size) * 0.3, 0.05);
        }
        double friction = onSnow ? Roll.FRICTION_SNOW : Roll.FRICTION;
        m.vx *= friction;
        m.vz *= friction;
    }

    /** Where the ball ends up after moving (dx, dz), following the ground; null when something is in the way. */
    private static Vec3 move(ServerLevel level, Interaction root, State s, Vec3 from, double dx, double dz) {
        if (dx == 0 && dz == 0) {
            return from;
        }
        double nx = from.x + dx;
        double nz = from.z + dz;
        BlockPos cell = BlockPos.containing(nx, from.y + 0.01, nz);
        double y;
        if (canOccupy(level, cell)) {
            y = cell.getY();
        } else if (free(level, cell)) {
            // Nothing to stand on: roll down, up to four blocks. Snowballs never roll uphill.
            int drop = 0;
            for (int i = 1; i <= 4; i++) {
                if (canOccupy(level, cell.below(i))) {
                    drop = i;
                    break;
                }
                if (!free(level, cell.below(i))) {
                    break;
                }
            }
            if (drop == 0) {
                return null;
            }
            y = cell.getY() - drop;
        } else {
            return null;
        }
        // The front of the ball needs room too, at every height the ball covers.
        float width = Ball.scale(s.top());
        float r = width / 2;
        double edgeX = nx + Math.signum(dx) * r * 0.85;
        double edgeZ = nz + Math.signum(dz) * r * 0.85;
        int levels = Math.max(1, (int) Math.ceil(width * 0.85));
        for (int i = 0; i < levels; i++) {
            if (!free(level, BlockPos.containing(edgeX, y + 0.01 + i, edgeZ))) {
                return null;
            }
        }
        for (Interaction other : level.getEntitiesOfClass(Interaction.class,
                new AABB(nx - 3 - r, y - 2, nz - 3 - r, nx + 3 + r, y + 3 + width, nz + 3 + r))) {
            if (other == root || !isRoot(other)) {
                continue;
            }
            int[] sizes = State.read(other.entityTags()).sizes;
            float or = Ball.scale(sizes.length == 0 ? 1 : sizes[0]) / 2;
            if (Math.hypot(other.getX() - nx, other.getZ() - nz) < (r + or) * 0.85
                    && Math.abs(other.getY() - y) < 1.5) {
                return null;
            }
        }
        return new Vec3(nx, y, nz);
    }

    /** Space a snowball can roll through: air, plants, any depth of snow. */
    private static boolean free(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || state.getBlock() instanceof SnowLayerBlock;
    }

    /**
     * When a ball stops, it tips onto its nearest flat side. The ball looks the same from all
     * six sides, so that side is the nearest quarter-turn orientation, and the tip is small.
     */
    private static void settle(Interaction root, State s) {
        Quaternionf spin = SPIN.get(root);
        if (spin == null) {
            return;
        }
        spin.set(Roll.nearestQuarterTurn(spin));
        layout(root, s, 4, 0);
    }

    /** A thump when a fast ball hits a wall. */
    private static void bounce(ServerLevel level, Interaction root, State s, double speed) {
        if (Math.abs(speed) > 0.12) {
            sound(level, root.position(), "deny", 1.0f);
        }
    }

    /** Half the width of the trail a ball of this radius leaves: from half a block up to 1.5 blocks. */
    private static float trailRadius(float ballRadius) {
        return Math.max(0.5f, Math.min(1.5f, ballRadius * 0.8f));
    }

    /** Picks up the snow within {@code r} of the ball's centre. Returns how many blocks of snow. */
    private static int clearTrail(ServerLevel level, Vec3 pos, float r, java.util.LinkedHashSet<Long> picked) {
        int cleared = 0;
        int reach = (int) Math.ceil(r);
        BlockPos centre = BlockPos.containing(pos.x, pos.y + 0.01, pos.z);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                BlockPos at = centre.offset(dx, 0, dz);
                if (Math.hypot(at.getX() + 0.5 - pos.x, at.getZ() + 0.5 - pos.z) <= r
                        && level.getBlockState(at).getBlock() instanceof SnowLayerBlock) {
                    level.removeBlock(at, false);
                    cleared++;
                    picked.add(at.asLong());
                    if (picked.size() > 64) {
                        picked.remove(picked.iterator().next());
                    }
                }
            }
        }
        return cleared;
    }

    /** Free space with something solid (or deep snow) to rest on. */
    private static boolean canOccupy(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean free = state.isAir() || state.canBeReplaced() || state.getBlock() instanceof SnowLayerBlock
                && state.getValue(SnowLayerBlock.LAYERS) <= 2;
        if (!free) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        return ground.isFaceSturdy(level, below, Direction.UP) || ground.is(Blocks.POWDER_SNOW)
                || ground.getBlock() instanceof SnowLayerBlock && ground.getValue(SnowLayerBlock.LAYERS) == 8;
    }

    // ---------------------------------------------------------------- stacking and decorating

    private static InteractionResult stack(ServerLevel level, Interaction root, State s, ItemStack item, int size,
            Player player) {
        if (s.decorated() || !Ball.canStack(s.sizes, size)) {
            sound(level, root.position(), "deny", 1.0f);
            return InteractionResult.FAIL;
        }
        int[] sizes = new int[s.sizes.length + 1];
        System.arraycopy(s.sizes, 0, sizes, 0, s.sizes.length);
        sizes[s.sizes.length] = size;
        s.sizes = sizes;
        if (!player.hasInfiniteMaterials()) {
            item.shrink(1);
        }
        if (s.complete()) {
            // A finished snowman turns to face whoever built it.
            root.setYRot(player.getYRot() + 180);
        }
        SPIN.remove(root);
        writeState(root, s);
        rebuild(level, root);
        Vec3 top = root.position().add(0, Ball.height(s.sizes), 0);
        sound(level, top, "ball.stack", 1.0f);
        if (s.complete()) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, top.x, top.y, top.z, 24, 0.4, 0.4, 0.4, 0.02);
            sound(level, top, "snowman.complete", 1.0f);
            sound(level, top, "snowman.complete", 1.5f);
            sound(level, top, "snowman.sparkle", 1.0f);
        }
        return InteractionResult.SUCCESS;
    }

    /** Sneak + empty hand: take off the last decoration, or the top snowball. */
    private static InteractionResult takeOff(ServerLevel level, Interaction root, State s, Player player) {
        ItemStack back;
        if (s.hat) {
            back = hatOf(root);
            s.hat = false;
        } else if (s.scarf != null) {
            back = new ItemStack(carpet(s.scarf));
            s.scarf = null;
        } else if (s.arms > 0) {
            s.arms--;
            back = new ItemStack(Items.STICK);
        } else if (s.nose) {
            s.nose = false;
            back = new ItemStack(Items.CARROT);
        } else if (s.buttons > 0) {
            s.buttons--;
            back = new ItemStack(Items.COAL);
        } else if (s.eyes) {
            s.eyes = false;
            back = new ItemStack(Items.COAL);
        } else {
            back = ballItem(s.top());
            if (s.sizes.length == 1) {
                give(player, back);
                discard(root);
                sound(level, root.position(), "ball.pickup", 1.0f);
                return InteractionResult.SUCCESS;
            }
            int[] sizes = new int[s.sizes.length - 1];
            System.arraycopy(s.sizes, 0, sizes, 0, sizes.length);
            s.sizes = sizes;
        }
        writeState(root, s);
        rebuild(level, root);
        give(player, back);
        sound(level, root.position().add(0, Ball.height(s.sizes), 0), "snowman.decorate", 0.8f);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult decorate(ServerLevel level, Interaction root, State s, Player player,
            ItemStack item) {
        ItemStack back = ItemStack.EMPTY;
        String sound = "snowman.decorate";
        if (item.is(Items.COAL) || item.is(Items.CHARCOAL)) {
            if (!s.eyes) {
                s.eyes = true;
            } else if (s.buttons < 3) {
                s.buttons++;
            } else {
                return InteractionResult.PASS;
            }
        } else if (item.is(Items.CARROT) && !s.nose) {
            s.nose = true;
        } else if (item.is(Items.STICK) && s.arms < 2) {
            s.arms++;
            sound = "snowman.decorate";
        } else if (item.getItem() instanceof BlockItem block && block.getBlock() instanceof WoolCarpetBlock carpet) {
            if (s.scarf != null) {
                back = new ItemStack(carpet(s.scarf));
            }
            s.scarf = carpet.getColor().getSerializedName();
            sound = "snowman.scarf";
        } else if (item.is(Items.COAL) || item.is(Items.CHARCOAL) || item.is(Items.CARROT) || item.is(Items.STICK)
                || item.is(Items.SNOWBALL)) {
            return InteractionResult.PASS;
        } else {
            if (s.hat) {
                back = hatOf(root);
            }
            s.hat = true;
            NEW_HAT.put(root, item.copyWithCount(1));
            sound = "snowman.hat";
        }
        if (!player.hasInfiniteMaterials()) {
            item.shrink(1);
        }
        writeState(root, s);
        rebuild(level, root);
        give(player, back);
        sound(level, root.position().add(0, Ball.height(s.sizes), 0), sound, 1.0f);
        return InteractionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- ticking

    public static void tick(Interaction root) {
        if (!(root.level() instanceof ServerLevel level) || !isRoot(root)) {
            return;
        }
        State now = State.read(root.entityTags());
        if (now.sizes.length == 1) {
            push(level, root, now);
            step(level, root, now);
        }
        int[] wobble = WOBBLE.get(root);
        if (wobble != null && --wobble[0] <= 0) {
            WOBBLE.remove(root);
            layout(root, State.read(root.entityTags()), 3, 0);
        }
        if (root.tickCount % MELT_TICKS == 0 && root.tickCount > 0
                && (SnowmenMod.melting == null || SnowmenMod.melting.get()) && melts(level, root)
                && !isTopHat(hatOf(root))) {
            melt(level, root);
        }
        if (root.tickCount % HINT_TICKS == 0 && (SnowmenMod.rotateHint == null || SnowmenMod.rotateHint.get())) {
            hint(level, root);
        }
    }

    /** Tells players standing next to a finished snowman that they can turn it. */
    private static void hint(ServerLevel level, Interaction root) {
        if (!State.read(root.entityTags()).complete() || SnowmenMod.rotate == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(root) <= HINT_RANGE * HINT_RANGE && !player.isSpectator()) {
                player.sendOverlayMessage(Component.translatableWithFallback("snowmen.hint.rotate",
                        "Press %s to turn the snowman", Component.keybind("key.snowmen.rotate")));
            }
        }
    }

    /** The rotate key: turns the finished snowman the player looks at, or the closest one. */
    public static void rotateNear(ServerPlayer player, boolean back) {
        if (player == null || !(player.level() instanceof ServerLevel level) || player.isSpectator()) {
            return;
        }
        Interaction best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (Interaction root : level.getEntitiesOfClass(Interaction.class,
                player.getBoundingBox().inflate(ROTATE_RANGE))) {
            if (!isRoot(root) || !State.read(root.entityTags()).complete()) {
                continue;
            }
            Vec3 to = root.position().add(0, 1, 0).subtract(eye);
            double distance = to.length();
            if (distance > ROTATE_RANGE + 1) {
                continue;
            }
            // Prefer the snowman in the middle of the view, then the closest.
            double score = distance * (2 - to.normalize().dot(look));
            if (score < bestScore) {
                bestScore = score;
                best = root;
            }
        }
        if (best == null) {
            return;
        }
        best.setYRot(best.getYRot() + (back ? -ROTATE_STEP : ROTATE_STEP));
        layout(best, State.read(best.entityTags()), 6, 0);
        sound(level, best.position().add(0, 1, 0), "snowman.rotate", 1.0f);
    }

    private static boolean isTopHat(ItemStack stack) {
        return SnowmenMod.topHat != null && !stack.isEmpty() && stack.is(SnowmenMod.topHat.get());
    }

    /** Displays whose snowman is gone (killed by a command, say) clean themselves up. */
    public static void partTick(Display display) {
        if (display.tickCount % 100 != 50 || !(display.level() instanceof ServerLevel level) || !isPart(display)) {
            return;
        }
        for (String tag : display.entityTags()) {
            if (tag.startsWith(OWNER)) {
                try {
                    Entity root = level.getEntity(UUID.fromString(tag.substring(OWNER.length())));
                    if (root != null && isRoot(root) && !root.isRemoved()) {
                        return;
                    }
                } catch (IllegalArgumentException ignored) {
                    // A broken tag: the display belongs to nothing.
                }
            }
        }
        display.discard();
    }

    private static boolean melts(ServerLevel level, Interaction root) {
        Boolean melts = level.environmentAttributes()
                .getValue(net.minecraft.world.attribute.EnvironmentAttributes.SNOW_GOLEM_MELTS, root.position());
        return melts != null && melts;
    }

    private static void melt(ServerLevel level, Interaction root) {
        State s = State.read(root.entityTags());
        for (int i = 0; i < s.sizes.length; i++) {
            if (--s.sizes[i] < Ball.MIN_SIZE) {
                breakApart(level, root, s, false);
                return;
            }
        }
        writeState(root, s);
        resize(root, s);
        layout(root, s, 20, 0);
        level.sendParticles(ParticleTypes.FALLING_WATER, root.getX(), root.getY() + Ball.height(s.sizes) * 0.5,
                root.getZ(), 6, 0.3, 0.3, 0.3, 0);
        sound(level, root.position().add(0, Ball.height(s.sizes) * 0.5, 0), "snowman.melt", 1.0f);
    }

    // ---------------------------------------------------------------- building the looks

    private static Interaction spawn(ServerLevel level, Vec3 at, State s, float yaw) {
        Interaction root = new Interaction(Compat.interaction(), level);
        root.snapTo(at.x, at.y, at.z, yaw, 0);
        root.addTag(State.ROOT);
        writeState(root, s);
        ((InteractionAccessor) root).snowmen$setResponse(true);
        level.addFreshEntity(root);
        rebuild(level, root);
        return root;
    }

    private static void writeState(Interaction root, State s) {
        for (String tag : new ArrayList<>(root.entityTags())) {
            if (State.isStateTag(tag)) {
                root.removeTag(tag);
            }
        }
        for (String tag : s.tags()) {
            root.addTag(tag);
        }
    }

    private static void resize(Interaction root, State s) {
        InteractionAccessor box = (InteractionAccessor) root;
        box.snowmen$setWidth(s.sizes.length == 0 ? 0 : Ball.scale(s.sizes[0]));
        box.snowmen$setHeight(Ball.height(s.sizes));
        root.refreshDimensions();
    }

    /** The displays of a root, found by their owner tag once and then remembered. */
    private static List<Display> parts(Interaction root) {
        List<Display> cached = PARTS.get(root);
        if (cached != null) {
            cached.removeIf(Entity::isRemoved);
            if (!cached.isEmpty()) {
                return cached;
            }
        }
        String owner = OWNER + root.getUUID();
        List<Display> found = new ArrayList<>(root.level().getEntitiesOfClass(Display.class,
                new AABB(root.getX() - 1, root.getY() - 1, root.getZ() - 1, root.getX() + 1, root.getY() + 1,
                        root.getZ() + 1),
                d -> d.entityTags().contains(owner)));
        PARTS.put(root, found);
        return found;
    }

    /** Throws away the displays of a root and creates them again from its state. */
    static void rebuild(ServerLevel level, Interaction root) {
        State s = State.read(root.entityTags());
        ItemStack hat = NEW_HAT.remove(root);
        if (hat == null) {
            hat = hatOf(root);
        }
        s.hatBlock = hat.getItem() instanceof BlockItem;
        s.hatTop = isTopHat(hat);
        for (Display part : parts(root)) {
            part.discard();
        }
        resize(root, s);
        List<Display> made = new ArrayList<>();
        for (Layout.Piece piece : Layout.pieces(s, 0, root.getYRot(), SPIN.get(root))) {
            Display display;
            if (piece.block()) {
                Display.BlockDisplay block = new Display.BlockDisplay(Compat.blockDisplay(), level);
                ((BlockDisplayAccessor) block).snowmen$setBlockState(blockFor(piece.kind(), s));
                display = block;
            } else {
                Display.ItemDisplay item = new Display.ItemDisplay(Compat.itemDisplay(), level);
                ItemStack stack = piece.kind() == Layout.Kind.HAT ? hat : new ItemStack(Items.STICK);
                ((ItemDisplayAccessor) item).snowmen$setItemStack(stack);
                ((ItemDisplayAccessor) item).snowmen$setItemTransform(
                        piece.kind() == Layout.Kind.HAT && s.hatBlock ? ItemDisplayContext.HEAD : ItemDisplayContext.NONE);
                display = item;
            }
            display.snapTo(root.getX(), root.getY(), root.getZ(), 0, 0);
            display.addTag(State.PART);
            display.addTag(OWNER + root.getUUID());
            display.addTag(partTag(piece));
            DisplayAccessor d = (DisplayAccessor) display;
            d.snowmen$setTransformation(new Transformation(piece.matrix()));
            d.snowmen$setPosRotInterpolationDuration(MOVE_LERP);
            if (piece.kind() == Layout.Kind.BALL && piece.index() == 0) {
                d.snowmen$setShadowRadius(Ball.scale(s.sizes[0]) * 0.45f);
            }
            level.addFreshEntity(display);
            made.add(display);
        }
        PARTS.put(root, made);
    }

    /** Moves the existing displays to a new layout, animated over {@code ticks}. */
    private static void layout(Interaction root, State s, int ticks, float extraYaw) {
        Map<String, Display> byTag = new HashMap<>();
        for (Display part : parts(root)) {
            for (String tag : part.entityTags()) {
                if (tag.startsWith(State.PART + ".")) {
                    byTag.put(tag, part);
                }
            }
        }
        ItemStack hat = hatOf(root);
        s.hatBlock = hat.getItem() instanceof BlockItem;
        s.hatTop = isTopHat(hat);
        for (Layout.Piece piece : Layout.pieces(s, 0, root.getYRot() + extraYaw, SPIN.get(root))) {
            Display display = byTag.get(partTag(piece));
            if (display != null) {
                DisplayAccessor d = (DisplayAccessor) display;
                d.snowmen$setInterpolationDelay(0);
                d.snowmen$setInterpolationDuration(ticks);
                d.snowmen$setTransformation(new Transformation(piece.matrix()));
                if (piece.kind() == Layout.Kind.BALL && piece.index() == 0) {
                    d.snowmen$setShadowRadius(Ball.scale(s.sizes[0]) * 0.45f);
                }
            }
        }
    }

    /** A quick sideways shake when a snowman is hit once; {@link #tick} swings it back. */
    private static void wobble(Interaction root, State s) {
        layout(root, s, 2, 9);
        WOBBLE.put(root, new int[] {3});
    }

    private static String partTag(Layout.Piece piece) {
        return State.PART + "." + piece.kind().name().toLowerCase(Locale.ROOT) + "." + piece.index();
    }

    private static BlockState blockFor(Layout.Kind kind, State s) {
        return switch (kind) {
            case EYE, BUTTON -> Blocks.COAL_BLOCK.defaultBlockState();
            case NOSE -> Compat.orangeConcrete().defaultBlockState();
            case SCARF -> Compat.wool(dye(s.scarf)).defaultBlockState();
            default -> Blocks.SNOW_BLOCK.defaultBlockState();
        };
    }

    private static ItemStack hatOf(Interaction root) {
        for (Display part : parts(root)) {
            if (part instanceof Display.ItemDisplay item && part.entityTags().contains(State.PART + ".hat.0")) {
                return ((ItemDisplayAccessor) item).snowmen$getItemStack().copy();
            }
        }
        return ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- breaking

    private static void breakApart(ServerLevel level, Interaction root, State s, boolean dropSnow) {
        List<ItemStack> drops = new ArrayList<>();
        if (dropSnow) {
            int snow = 0;
            for (int size : s.sizes) {
                snow += size;
            }
            snow = Math.min(snow, MAX_SNOW_DROPS);
            while (snow > 0) {
                int n = Math.min(16, snow);
                drops.add(new ItemStack(Items.SNOWBALL, n));
                snow -= n;
            }
        }
        if (s.eyes) {
            drops.add(new ItemStack(Items.COAL));
        }
        if (s.buttons > 0) {
            drops.add(new ItemStack(Items.COAL, s.buttons));
        }
        if (s.nose) {
            drops.add(new ItemStack(Items.CARROT));
        }
        if (s.arms > 0) {
            drops.add(new ItemStack(Items.STICK, s.arms));
        }
        if (s.scarf != null) {
            drops.add(new ItemStack(carpet(s.scarf)));
        }
        if (s.hat) {
            drops.add(hatOf(root));
        }
        float h = Ball.height(s.sizes);
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                root.spawnAtLocation(level, drop, h * 0.5f);
            }
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()),
                root.getX(), root.getY() + h * 0.5, root.getZ(), 40, 0.35, h * 0.3, 0.35, 0.1);
        sound(level, root.position().add(0, h * 0.5, 0), "snowman.break", 1.0f);
        discard(root);
    }

    private static void discard(Interaction root) {
        for (Display part : parts(root)) {
            part.discard();
        }
        PARTS.remove(root);
        MOTION.remove(root);
        SPIN.remove(root);
        root.discard();
    }

    // ---------------------------------------------------------------- helpers

    private static Interaction rootAt(ServerLevel level, BlockPos pos, Interaction except) {
        for (Interaction other : level.getEntitiesOfClass(Interaction.class, new AABB(pos).inflate(-0.05))) {
            if (other != except && isRoot(other)) {
                return other;
            }
        }
        return null;
    }

    private static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            Compat.drop(player, stack);
        }
    }

    /** Plays one of the mod's sounds (see assets/snowmen/sounds.json); they reuse vanilla sound files. */
    private static void sound(ServerLevel level, Vec3 at, String name, float pitch) {
        SoundEvent event = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(SnowmenMod.ID, name));
        level.playSound(null, at.x, at.y, at.z, Holder.direct(event), SoundSource.BLOCKS, 1.0f, pitch);
    }

    private static DyeColor dye(String name) {
        for (DyeColor color : DyeColor.values()) {
            if (color.getSerializedName().equals(name)) {
                return color;
            }
        }
        return DyeColor.RED;
    }

    private static Block carpet(String name) {
        return Compat.carpet(dye(name));
    }
}
