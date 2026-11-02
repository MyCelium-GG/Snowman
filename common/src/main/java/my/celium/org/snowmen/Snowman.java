package my.celium.org.snowmen;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import my.celium.org.snowmen.mixin.BlockDisplayAccessor;
import my.celium.org.snowmen.mixin.DisplayAccessor;
import my.celium.org.snowmen.mixin.InteractionAccessor;
import my.celium.org.snowmen.mixin.ItemDisplayAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
 * players click) with vanilla display entities riding it for the looks. What it is made of
 * is stored as tags on the root ({@link State}), so it needs no save data of its own.
 */
public final class Snowman {
    /** Ticks a roll takes from one block to the next. */
    private static final int ROLL_TICKS = 4;
    /** Two hits on a snowman within this many ticks knock it over. */
    private static final int DOUBLE_HIT_TICKS = 12;
    /** How often melting is checked, in ticks. */
    private static final int MELT_TICKS = 300;

    private record Motion(Vec3 from, Vec3 to, int[] step) {
    }

    private static final Map<Interaction, Motion> MOTION = new WeakHashMap<>();
    private static final Map<Interaction, Quaternionf> SPIN = new WeakHashMap<>();
    private static final Map<Interaction, Long> LAST_HIT = new WeakHashMap<>();
    private static final Map<Interaction, int[]> WOBBLE = new WeakHashMap<>();
    /** Who carries which snowball. Minecraft does not let entities ride players, so it follows instead. */
    private static final Map<java.util.UUID, Interaction> CARRYING = new HashMap<>();
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

    // ---------------------------------------------------------------- blocks

    /** Right-click on a block. Returns null to let vanilla handle it. */
    public static InteractionResult useBlock(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !SnowmenMod.isModEnabled() || player.isSpectator()) {
            return null;
        }
        Interaction carried = carriedBy(player);
        if (carried != null) {
            BlockPos target = state.canBeReplaced() || state.getBlock() instanceof SnowLayerBlock
                    ? pos : pos.relative(hit.getDirection());
            return putDown(server, carried, target) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (!player.getMainHandItem().isEmpty()) {
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
        sound(server, Vec3.atCenterOf(at), SoundEvents.SNOW_PLACE, 1.2f);
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
        if (hand != InteractionHand.MAIN_HAND || root.entityTags().contains(State.CARRIED)) {
            return InteractionResult.PASS;
        }
        State s = State.read(root.entityTags());
        Interaction carried = carriedBy(player);
        if (carried != null) {
            return stack(server, root, s, carried, player);
        }
        ItemStack item = player.getItemInHand(hand);
        if (item.isEmpty()) {
            if (player.isSecondaryUseActive()) {
                return takeOff(server, root, s, player);
            }
            if (s.sizes.length == 1) {
                if (!Ball.canCarry(s.top())) {
                    sound(server, root.position(), SoundEvents.SNOW_STEP, 0.6f);
                    return InteractionResult.FAIL;
                }
                carry(server, root, player);
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
                || player.isSpectator() || root.entityTags().contains(State.CARRIED)) {
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
            sound(server, root.position().add(0, 0.5, 0), SoundEvents.SNOW_HIT, 1.0f);
        }
        return true;
    }

    // ---------------------------------------------------------------- rolling

    private static void roll(ServerLevel level, Interaction root, State s, Player player) {
        if (MOTION.containsKey(root)) {
            return;
        }
        Direction dir = Direction.fromYRot(player.getYRot());
        BlockPos from = root.blockPosition();
        BlockPos to = from.relative(dir);
        if (!canOccupy(level, to)) {
            if (canOccupy(level, to.below()) && level.getBlockState(to).canBeReplaced()) {
                to = to.below();
            } else {
                sound(level, root.position(), SoundEvents.SNOW_HIT, 0.8f);
                return;
            }
        } else if (canOccupy(level, to.below()) && level.getBlockState(to.below()).canBeReplaced()
                && !(level.getBlockState(to).getBlock() instanceof SnowLayerBlock)) {
            to = to.below();
        }
        if (rootAt(level, to, root) != null) {
            sound(level, root.position(), SoundEvents.SNOW_HIT, 0.8f);
            return;
        }
        int size = s.top();
        BlockState there = level.getBlockState(to);
        int max = SnowmenMod.maxBallSize();
        if (there.getBlock() instanceof SnowLayerBlock) {
            size = Math.min(max, size + 1);
            if (SnowmenMod.snowTrail == null || SnowmenMod.snowTrail.get()) {
                level.removeBlock(to, false);
            }
        } else if (level.getBlockState(to.below()).is(Blocks.SNOW_BLOCK)
                || level.getBlockState(to.below()).is(Blocks.POWDER_SNOW)) {
            size = Math.min(max, size + 1);
        }
        Vec3 target = Vec3.atBottomCenterOf(to);
        float radius = Ball.scale(size) / 2;
        Vector3f axis = new Vector3f(dir.getStepZ(), 0, -dir.getStepX());
        Quaternionf spin = SPIN.computeIfAbsent(root, r -> new Quaternionf());
        new Quaternionf().rotateAxis(1.0f / radius, axis).mul(spin, spin);
        if (size != s.top()) {
            s.sizes[0] = size;
            writeState(root, s);
            rebuild(level, root);
        }
        layout(root, State.read(root.entityTags()), ROLL_TICKS, 0);
        MOTION.put(root, new Motion(root.position(), target, new int[] {0}));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()),
                target.x, target.y + 0.1, target.z, 6, 0.25, 0.05, 0.25, 0.05);
        sound(level, target, SoundEvents.SNOW_STEP, 0.9f + level.getRandom().nextFloat() * 0.2f);
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

    // ---------------------------------------------------------------- carrying and stacking

    private static Interaction carriedBy(Player player) {
        Interaction root = CARRYING.get(player.getUUID());
        if (root != null && (root.isRemoved() || !root.entityTags().contains(State.CARRIED))) {
            CARRYING.remove(player.getUUID());
            return null;
        }
        return root;
    }

    private static void carry(ServerLevel level, Interaction root, Player player) {
        MOTION.remove(root);
        SPIN.remove(root);
        root.addTag(State.CARRIED);
        CARRYING.put(player.getUUID(), root);
        follow(root, player);
        rebuild(level, root);
        sound(level, root.position(), SoundEvents.SNOW_BREAK, 1.3f);
    }

    /** Puts a carried snowball on top of its carrier's head. */
    private static void follow(Interaction root, Player player) {
        root.snapTo(player.getX(), player.getY() + player.getBbHeight() + 0.05, player.getZ());
    }

    private static Player carrierOf(ServerLevel level, Interaction root) {
        for (Map.Entry<java.util.UUID, Interaction> e : CARRYING.entrySet()) {
            if (e.getValue() == root) {
                Player player = level.getPlayerByUUID(e.getKey());
                return player != null && player.isAlive() && !player.isRemoved() ? player : null;
            }
        }
        return null;
    }

    private static boolean putDown(ServerLevel level, Interaction carried, BlockPos pos) {
        if (!canOccupy(level, pos) || rootAt(level, pos, carried) != null) {
            sound(level, Vec3.atCenterOf(pos), SoundEvents.SNOW_STEP, 0.6f);
            return false;
        }
        CARRYING.values().remove(carried);
        carried.removeTag(State.CARRIED);
        carried.snapTo(Vec3.atBottomCenterOf(pos));
        rebuild(level, carried);
        sound(level, Vec3.atCenterOf(pos), SoundEvents.SNOW_PLACE, 1.0f);
        return true;
    }

    private static InteractionResult stack(ServerLevel level, Interaction root, State s, Interaction carried,
            Player player) {
        State c = State.read(carried.entityTags());
        if (c.sizes.length != 1 || s.decorated() || !Ball.canStack(s.sizes, c.top())) {
            sound(level, root.position(), SoundEvents.SNOW_STEP, 0.6f);
            return InteractionResult.FAIL;
        }
        int[] sizes = new int[s.sizes.length + 1];
        System.arraycopy(s.sizes, 0, sizes, 0, s.sizes.length);
        sizes[s.sizes.length] = c.top();
        s.sizes = sizes;
        discard(carried);
        if (s.complete()) {
            // A finished snowman turns to face whoever built it.
            root.setYRot(player.getYRot() + 180);
        }
        writeState(root, s);
        rebuild(level, root);
        Vec3 top = root.position().add(0, Ball.height(s.sizes), 0);
        sound(level, top, SoundEvents.SNOW_PLACE, 0.9f);
        if (s.complete()) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, top.x, top.y, top.z, 24, 0.4, 0.4, 0.4, 0.02);
            sound(level, top, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f);
        }
        return InteractionResult.SUCCESS;
    }

    /** Sneak + empty hand: take off the last decoration, or lift the top snowball off. */
    private static InteractionResult takeOff(ServerLevel level, Interaction root, State s, Player player) {
        ItemStack back = ItemStack.EMPTY;
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
        } else if (s.sizes.length > 1) {
            if (carriedBy(player) != null || !Ball.canCarry(s.top())) {
                return InteractionResult.FAIL;
            }
            int top = s.top();
            int[] sizes = new int[s.sizes.length - 1];
            System.arraycopy(s.sizes, 0, sizes, 0, sizes.length);
            s.sizes = sizes;
            writeState(root, s);
            rebuild(level, root);
            Interaction ball = spawn(level, root.position(), State.ball(top), 0);
            carry(level, ball, player);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
        writeState(root, s);
        rebuild(level, root);
        give(player, back);
        sound(level, root.position().add(0, Ball.height(s.sizes), 0), SoundEvents.ITEM_PICKUP, 1.0f);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult decorate(ServerLevel level, Interaction root, State s, Player player,
            ItemStack item) {
        ItemStack back = ItemStack.EMPTY;
        SoundEvent sound = SoundEvents.SNOW_PLACE;
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
            sound = SoundEvents.WOOD_PLACE;
        } else if (item.getItem() instanceof BlockItem block && block.getBlock() instanceof WoolCarpetBlock carpet) {
            if (s.scarf != null) {
                back = new ItemStack(carpet(s.scarf));
            }
            s.scarf = carpet.getColor().getSerializedName();
            sound = SoundEvents.WOOL_PLACE;
        } else if (item.is(Items.COAL) || item.is(Items.CARROT) || item.is(Items.STICK) || item.is(Items.SNOWBALL)) {
            return InteractionResult.PASS;
        } else {
            if (s.hat) {
                back = hatOf(root);
            }
            s.hat = true;
            NEW_HAT.put(root, item.copyWithCount(1));
            sound = SoundEvents.ARMOR_EQUIP_GENERIC.value();
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
        Motion motion = MOTION.get(root);
        if (motion != null) {
            int step = ++motion.step()[0];
            double t = Math.min(1.0, step / (double) ROLL_TICKS);
            root.snapTo(motion.from().lerp(motion.to(), t));
            if (step >= ROLL_TICKS) {
                MOTION.remove(root);
            }
        }
        int[] wobble = WOBBLE.get(root);
        if (wobble != null && --wobble[0] <= 0) {
            WOBBLE.remove(root);
            layout(root, State.read(root.entityTags()), 3, 0);
        }
        if (root.entityTags().contains(State.CARRIED)) {
            Player carrier = carrierOf(level, root);
            if (carrier != null) {
                follow(root, carrier);
            } else {
                // The carrier is gone (logged out, died, other dimension, restart): set it down here.
                CARRYING.values().remove(root);
                root.removeTag(State.CARRIED);
                root.snapTo(Vec3.atBottomCenterOf(ground(level, root.blockPosition())));
                rebuild(level, root);
            }
            return;
        }
        if (root.tickCount % MELT_TICKS == 0 && root.tickCount > 0
                && (SnowmenMod.melting == null || SnowmenMod.melting.get()) && melts(level, root)) {
            melt(level, root);
        }
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
        rebuild(level, root);
        level.sendParticles(ParticleTypes.FALLING_WATER, root.getX(), root.getY() + Ball.height(s.sizes) * 0.5,
                root.getZ(), 6, 0.3, 0.3, 0.3, 0);
    }

    private static BlockPos ground(ServerLevel level, BlockPos start) {
        BlockPos pos = start;
        for (int i = 0; i < 16 && !canOccupy(level, pos); i++) {
            pos = pos.below();
        }
        return canOccupy(level, pos) ? pos : start;
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

    /** Throws away the displays of a root and creates them again from its state. */
    static void rebuild(ServerLevel level, Interaction root) {
        State s = State.read(root.entityTags());
        ItemStack hat = NEW_HAT.remove(root);
        if (hat == null) {
            hat = hatOf(root);
        }
        s.hatBlock = hat.getItem() instanceof BlockItem;
        for (Entity part : new ArrayList<>(root.getPassengers())) {
            if (isPart(part)) {
                part.stopRiding();
                part.discard();
            }
        }
        boolean carried = root.entityTags().contains(State.CARRIED);
        float height = carried ? 0 : Ball.height(s.sizes);
        InteractionAccessor box = (InteractionAccessor) root;
        box.snowmen$setWidth(carried || s.sizes.length == 0 ? 0 : Ball.scale(s.sizes[0]));
        box.snowmen$setHeight(height);
        root.refreshDimensions();
        for (Layout.Piece piece : Layout.pieces(s, -height, root.getYRot(), SPIN.get(root))) {
            Display display;
            if (piece.block()) {
                Display.BlockDisplay block = new Display.BlockDisplay(Compat.blockDisplay(), level);
                ((BlockDisplayAccessor) block).snowmen$setBlockState(blockFor(piece.kind(), s));
                display = block;
            } else {
                Display.ItemDisplay item = new Display.ItemDisplay(Compat.itemDisplay(), level);
                ItemStack stack = piece.kind() == Layout.Kind.HAT ? hat : new ItemStack(Items.STICK);
                ((ItemDisplayAccessor) item).snowmen$setItemStack(stack == null ? ItemStack.EMPTY : stack);
                ((ItemDisplayAccessor) item).snowmen$setItemTransform(
                        piece.kind() == Layout.Kind.HAT && s.hatBlock ? ItemDisplayContext.HEAD : ItemDisplayContext.NONE);
                display = item;
            }
            display.snapTo(root.getX(), root.getY() + height, root.getZ(), 0, 0);
            display.addTag(State.PART);
            display.addTag(partTag(piece));
            DisplayAccessor d = (DisplayAccessor) display;
            d.snowmen$setTransformation(new Transformation(piece.matrix()));
            if (piece.kind() == Layout.Kind.BALL && piece.index() == 0 && !carried) {
                d.snowmen$setShadowRadius(Ball.scale(s.sizes[0]) * 0.45f);
            }
            level.addFreshEntity(display);
            display.startRiding(root, true, true);
        }
    }

    /** Moves the existing displays to a new layout, animated over {@code ticks}. */
    private static void layout(Interaction root, State s, int ticks, float extraYaw) {
        Map<String, Display> parts = new HashMap<>();
        for (Entity part : root.getPassengers()) {
            if (part instanceof Display display && isPart(part)) {
                for (String tag : part.entityTags()) {
                    if (tag.startsWith(State.PART + ".")) {
                        parts.put(tag, display);
                    }
                }
            }
        }
        float height = root.entityTags().contains(State.CARRIED) ? 0 : Ball.height(s.sizes);
        s.hatBlock = hatOf(root).getItem() instanceof BlockItem;
        for (Layout.Piece piece : Layout.pieces(s, -height, root.getYRot() + extraYaw, SPIN.get(root))) {
            Display display = parts.get(partTag(piece));
            if (display != null) {
                DisplayAccessor d = (DisplayAccessor) display;
                d.snowmen$setInterpolationDelay(0);
                d.snowmen$setInterpolationDuration(ticks);
                d.snowmen$setTransformation(new Transformation(piece.matrix()));
            }
        }
    }

    /** A quick sideways shake when a snowman is hit once; {@link #tick} swings it back. */
    private static void wobble(Interaction root, State s) {
        layout(root, s, 2, 9);
        WOBBLE.put(root, new int[] {3});
    }

    private static String partTag(Layout.Piece piece) {
        return State.PART + "." + piece.kind().name().toLowerCase(java.util.Locale.ROOT) + "." + piece.index();
    }

    private static BlockState blockFor(Layout.Kind kind, State s) {
        return switch (kind) {
            case EYE, BUTTON -> Blocks.COAL_BLOCK.defaultBlockState();
            case NOSE -> Compat.orangeConcrete().defaultBlockState();
            case SCARF -> wool(s.scarf);
            default -> Blocks.SNOW_BLOCK.defaultBlockState();
        };
    }

    private static ItemStack hatOf(Interaction root) {
        for (Entity part : root.getPassengers()) {
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
        sound(level, root.position().add(0, h * 0.5, 0), SoundEvents.SNOW_BREAK, 0.9f);
        discard(root);
    }

    private static void discard(Interaction root) {
        for (Entity part : new ArrayList<>(root.getPassengers())) {
            if (isPart(part)) {
                part.stopRiding();
                part.discard();
            }
        }
        CARRYING.values().remove(root);
        MOTION.remove(root);
        SPIN.remove(root);
        root.discard();
    }

    // ---------------------------------------------------------------- helpers

    private static Interaction rootAt(ServerLevel level, BlockPos pos, Interaction except) {
        for (Interaction other : level.getEntitiesOfClass(Interaction.class, new AABB(pos).inflate(-0.05))) {
            if (other != except && isRoot(other) && !other.entityTags().contains(State.CARRIED)) {
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

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, 1.0f, pitch);
    }

    private static DyeColor dye(String name) {
        for (DyeColor color : DyeColor.values()) {
            if (color.getSerializedName().equals(name)) {
                return color;
            }
        }
        return DyeColor.RED;
    }

    private static BlockState wool(String name) {
        return Compat.wool(dye(name)).defaultBlockState();
    }

    private static Block carpet(String name) {
        return Compat.carpet(dye(name));
    }
}
