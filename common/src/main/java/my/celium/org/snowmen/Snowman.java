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
    /** Ticks a roll takes from one block to the next; clients glide the displays over this time. */
    private static final int ROLL_TICKS = 5;
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

    private static final Map<Interaction, Long> BUSY = new WeakHashMap<>();
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
                Component.literal("\u2744 ").withStyle(st -> st.withItalic(false).withColor(Ice.FROST))
                        .append(Component.translatable("item.snowmen.rolled_snowball.size", Ball.clamp(size))
                                .withStyle(st -> st.withColor(Ice.ICE))),
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
            roll(server, root, s, player);
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

    private static void roll(ServerLevel level, Interaction root, State s, Player player) {
        long now = level.getGameTime();
        Long busy = BUSY.get(root);
        if (busy != null && now < busy) {
            return;
        }
        Direction dir = Direction.fromYRot(player.getYRot());
        BlockPos to = root.blockPosition().relative(dir);
        if (!canOccupy(level, to)) {
            if (canOccupy(level, to.below()) && level.getBlockState(to).canBeReplaced()) {
                to = to.below();
            } else {
                sound(level, root.position(), "deny", 1.0f);
                return;
            }
        } else if (canOccupy(level, to.below()) && level.getBlockState(to.below()).canBeReplaced()
                && !(level.getBlockState(to).getBlock() instanceof SnowLayerBlock)) {
            to = to.below();
        }
        if (rootAt(level, to, root) != null) {
            sound(level, root.position(), "deny", 1.0f);
            return;
        }
        int size = s.top();
        int max = SnowmenMod.maxBallSize();
        if (level.getBlockState(to).getBlock() instanceof SnowLayerBlock) {
            size = Math.min(max, size + 1);
            if (SnowmenMod.snowTrail == null || SnowmenMod.snowTrail.get()) {
                level.removeBlock(to, false);
            }
        } else if (level.getBlockState(to.below()).is(Blocks.SNOW_BLOCK)
                || level.getBlockState(to.below()).is(Blocks.POWDER_SNOW)) {
            size = Math.min(max, size + 1);
        }
        // Spin around the axis across the direction of travel: one block of travel turns the
        // ball by (distance / radius) radians, like a real ball rolling.
        Vector3f axis = new Vector3f(dir.getStepZ(), 0, -dir.getStepX());
        Quaternionf spin = SPIN.computeIfAbsent(root, r -> new Quaternionf());
        new Quaternionf().rotateAxis(1.0f / (Ball.scale(size) / 2), axis).mul(spin, spin);
        boolean grew = size != s.sizes[0];
        s.sizes[0] = size;
        writeState(root, s);
        Vec3 target = Vec3.atBottomCenterOf(to);
        if (grew) {
            sound(level, target, "ball.grow", Math.max(0.5f, 1.4f - size * 0.02f));
        }
        root.snapTo(target);
        resize(root, s);
        for (Display part : parts(root)) {
            part.snapTo(target.x, target.y, target.z, 0, 0);
        }
        layout(root, s, ROLL_TICKS, 0);
        BUSY.put(root, now + ROLL_TICKS - 1);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()),
                target.x, target.y + 0.1, target.z, 6, 0.25, 0.05, 0.25, 0.05);
        sound(level, target, "ball.roll", 0.9f + level.getRandom().nextFloat() * 0.2f);
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
            d.snowmen$setPosRotInterpolationDuration(ROLL_TICKS);
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
        BUSY.remove(root);
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
