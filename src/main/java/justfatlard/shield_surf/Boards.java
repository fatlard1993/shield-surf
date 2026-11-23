package justfatlard.shield_surf;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The shield a rider stands on, drawn under their feet so the ride can be seen, by the rider most of all.
 *
 * <p>It is an item display riding the player, so a vanilla client draws it and keeps it under them
 * frame by frame, however fast they go. A passenger hangs from the top of its vehicle, so the board
 * is pulled down by the rider's height - and that height is not the same for everyone: the rest of
 * the server sees the rider crouched, while the rider's own client crouches them only when it shares
 * the server's process. So there are two boards, one shown only to the rider and one shown to
 * everyone else, each pulled down by the height its viewers see.
 *
 * <p>Boards are never saved: one read back from disk is a stray and is dropped on sight.
 */
public final class Boards {
	private Boards() {}

	private static final String TAG = ShieldSurf.MOD_ID + ":board";

	private static final float STANDING = 1.8F;
	private static final float CROUCHING = 1.5F;
	private static final float SCALE = 0.9F;
	/**
	 * Where the middle of the shield's face is from the display's origin once laid face up, times
	 * the scale: the model is drawn half a block off to one side and back, and its face ten
	 * sixteenths down. Taking it away puts the face at the soles, the handle down in the ground.
	 */
	private static final Vector3f FACE = new Vector3f(0.5F, -0.625F, -0.5F).mul(SCALE);

	private record Pair(Display.ItemDisplay own, Display.ItemDisplay seen) {}

	/** Who may see a board: only its rider, or everyone but. */
	private record Viewers(UUID rider, boolean riderOnly) {
		boolean include(ServerPlayer player) {
			return player.getUUID().equals(rider) == riderOnly;
		}
	}

	private static final Map<UUID, Pair> boards = new ConcurrentHashMap<>();
	private static final Map<Entity, Viewers> viewers = new ConcurrentHashMap<>();

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (isBoard(entity) && !viewers.containsKey(entity)) entity.discard();
		});
	}

	public static boolean isBoard(Entity entity) {
		return entity instanceof Display.ItemDisplay && entity.entityTags().contains(TAG);
	}

	/** Whether this player's client is told the entity exists. */
	public static boolean shows(Entity entity, ServerPlayer player) {
		Viewers who = viewers.get(entity);
		return who == null || who.include(player);
	}

	public static void lay(ServerPlayer player, ItemStack shield) {
		lift(player);
		float scale = player.getScale();
		float ownHeight = (sharesProcess(player) ? CROUCHING : STANDING) * scale;
		Pair pair = new Pair(
			spawn(player, shield, ownHeight, true),
			spawn(player, shield, CROUCHING * scale, false));
		if (lost(pair.own, player) || lost(pair.seen, player)) {
			discard(pair.own);
			discard(pair.seen);
			return;
		}
		boards.put(player.getUUID(), pair);
	}

	/**
	 * Pointed the way the rider is going, and laid again if the world took it from under them. A
	 * board that would not take its seat is not tried again until the next ride.
	 */
	public static void follow(ServerPlayer player, Vec3 motion, ItemStack shield) {
		Pair pair = boards.get(player.getUUID());
		if (pair == null) return;
		if (lost(pair.own, player) || lost(pair.seen, player)) {
			lay(player, shield);
			pair = boards.get(player.getUUID());
			if (pair == null) return;
		}
		if (Math.hypot(motion.x, motion.z) < 0.02) return;
		float yaw = (float) Math.toDegrees(Math.atan2(-motion.x, motion.z));
		pair.own.setYRot(yaw);
		pair.seen.setYRot(yaw);
	}

	public static void lift(ServerPlayer player) {
		Pair pair = boards.remove(player.getUUID());
		if (pair == null) return;
		discard(pair.own);
		discard(pair.seen);
	}

	public static void clear() {
		boards.values().forEach(pair -> {
			discard(pair.own);
			discard(pair.seen);
		});
		boards.clear();
	}

	private static boolean lost(Display.ItemDisplay board, ServerPlayer player) {
		return board.isRemoved() || board.getVehicle() != player;
	}

	private static void discard(Display.ItemDisplay board) {
		viewers.remove(board);
		board.discard();
	}

	/** The host of a singleplayer or LAN world, whose client runs this mod's pose with the server's rides. */
	private static boolean sharesProcess(ServerPlayer player) {
		return player.level().getServer().isSingleplayerOwner(player.nameAndId());
	}

	private static Display.ItemDisplay spawn(ServerPlayer player, ItemStack shield, float height, boolean riderOnly) {
		ServerLevel level = player.level();
		Display.ItemDisplay board = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
		board.setPos(player.getX(), player.getY() + height, player.getZ());
		board.setYRot(player.getYRot());
		board.getSlot(0).set(shield.copyWithCount(1));
		board.setItemTransform(ItemDisplayContext.NONE);
		board.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		board.setPosRotInterpolationDuration(2);
		board.setTransformation(pose(height));
		board.addTag(TAG);
		viewers.put(board, new Viewers(player.getUUID(), riderOnly));
		// Seated before it is added: the rider's client hears of a seat only in the board's own
		// arrival, since a seat taken later is announced to everyone watching the rider but them.
		board.startRiding(player, true, false);
		level.addFreshEntity(board);
		return board;
	}

	/** Face up at the rider's soles, its length along the way they go. */
	private static Transformation pose(float height) {
		Quaternionf flat = new Quaternionf().rotateX((float) Math.toRadians(90));
		Vector3f soles = new Vector3f(0, -height, 0).sub(FACE);
		return new Transformation(soles, flat, new Vector3f(SCALE, SCALE, SCALE), null);
	}
}
