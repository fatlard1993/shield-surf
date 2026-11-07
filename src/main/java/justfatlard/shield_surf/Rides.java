package justfatlard.shield_surf;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Who is riding a shield, and what the ride does to them each tick.
 *
 * <p>A ride begins the tick a player is in the air with a shield raised, and lasts while the shield
 * stays up and they keep moving. The player's own client moves them, so the ride is built out of
 * the three movement attributes the server syncs to it: friction, step height and air drag. Nothing
 * here moves a player directly except the kick on landing, which is one velocity packet.
 */
public final class Rides {
	private Rides() {}

	public static final Identifier RIDING = Identifier.fromNamespaceAndPath(ShieldSurf.MOD_ID, "riding");

	/** The ground holds nothing back: whatever the block, it slides like a frictionless one. */
	private static final AttributeModifier SLICK_GROUND =
		new AttributeModifier(RIDING, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	/** No stepping up. A rise is a wall, so a slope is only ever ridden down. */
	private static final AttributeModifier NO_STEP =
		new AttributeModifier(RIDING, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	/** Thinner air, so a hop or a drop keeps most of its speed instead of losing a tenth a tick. */
	private static final AttributeModifier THIN_AIR =
		new AttributeModifier(RIDING, -0.65, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

	/** Blocks a tick, the most a shield will carry anyone. Sprinting is about 0.28. */
	private static final double TOP_SPEED = 0.75;
	/** Speed gained per block dropped, paid out on landing. */
	private static final double GAIN_PER_BLOCK = 0.1;
	/** Below this on the ground, the rider is standing on a shield rather than riding one. */
	private static final double CRAWL = 0.06;
	/** How long they may stand still before the ride is over. */
	private static final int STILL_TICKS = 10;
	/** Faster than this on the ground kicks the ground up behind. */
	private static final double DUST_SPEED = 0.2;
	private static final int WEAR_EVERY_TICKS = 10;
	/** A drop this far lands with a thud even mid-ride. */
	private static final double HARD_LANDING = 1.5;
	/** One ride this long earns the advancement. */
	private static final double LONG_RIDE = 40.0;

	private static final class Ride {
		boolean wasOnGround;
		boolean landed;
		/**
		 * Where the ground was last under them, or where the shield went up if it went up in the
		 * air. A drop is measured from here, so a hop on the flat gains nothing and a hop off a
		 * ledge gains the ledge.
		 */
		double takeoffY;
		int still;
		int groundTicks;
		double distance;
		Vec3 lastPos;
	}

	private static final Map<UUID, Ride> rides = new ConcurrentHashMap<>();

	public static boolean riding(UUID player) {
		return rides.containsKey(player);
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Ride ride = rides.get(player.getUUID());
			if (ride == null) {
				if (shieldUp(player) && !player.onGround() && fit(player)) begin(player);
				continue;
			}
			if (!shieldUp(player) || !fit(player)) {
				end(player);
				continue;
			}
			step(player, ride);
		}
	}

	public static void forget(ServerPlayer player) {
		end(player);
	}

	private static boolean shieldUp(ServerPlayer player) {
		return player.isUsingItem() && player.getUseItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	/** Whether a shield can be ridden from where the player is. */
	private static boolean fit(ServerPlayer player) {
		return player.isAlive()
			&& !player.isSpectator()
			&& !player.getAbilities().flying
			&& !player.isPassenger()
			&& !player.onClimbable()
			&& !player.isInLiquid()
			&& !player.isSwimming()
			&& !player.isFallFlying();
	}

	private static void begin(ServerPlayer player) {
		hold(player, Attributes.FRICTION_MODIFIER, SLICK_GROUND);
		hold(player, Attributes.STEP_HEIGHT, NO_STEP);
		hold(player, Attributes.AIR_DRAG_MODIFIER, THIN_AIR);

		Ride ride = new Ride();
		ride.wasOnGround = false;
		ride.takeoffY = player.getY();
		ride.lastPos = player.position();
		rides.put(player.getUUID(), ride);
	}

	private static void end(ServerPlayer player) {
		if (rides.remove(player.getUUID()) == null) return;
		release(player, Attributes.FRICTION_MODIFIER);
		release(player, Attributes.STEP_HEIGHT);
		release(player, Attributes.AIR_DRAG_MODIFIER);
	}

	private static void hold(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			AttributeModifier modifier) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null && !instance.hasModifier(RIDING)) instance.addTransientModifier(modifier);
	}

	private static void release(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) instance.removeModifier(RIDING);
	}

	private static void step(ServerPlayer player, Ride ride) {
		Vec3 pos = player.position();
		ride.distance += Math.hypot(pos.x - ride.lastPos.x, pos.z - ride.lastPos.z);
		ride.lastPos = pos;
		if (ride.distance >= LONG_RIDE) Awards.longRide(player);

		boolean onGround = player.onGround();
		if (!onGround) {
			ride.still = 0;
		} else {
			if (!ride.wasOnGround) land(player, ride, Math.max(0, ride.takeoffY - pos.y));
			ride.takeoffY = pos.y;

			Vec3 motion = player.getKnownMovement();
			double speed = Math.hypot(motion.x, motion.z);
			if (speed < CRAWL) {
				if (++ride.still > STILL_TICKS) {
					end(player);
					return;
				}
			} else {
				ride.still = 0;
				BlockState ground = player.getBlockStateOn();
				if (speed > DUST_SPEED) dust(player, ground, motion);
				if (++ride.groundTicks % WEAR_EVERY_TICKS == 0) wear(player, ground);
			}
		}
		ride.wasOnGround = onGround;
	}

	/**
	 * The fall becomes speed. Each block dropped adds a fixed amount along the direction of travel,
	 * which is what makes a slope faster than the flat: on the flat nothing is dropped and the
	 * ride only ever slows. Straight down, with no travel to speak of, there is no direction to
	 * pay it out in, and a rider has to be moving to be carried.
	 */
	private static void land(ServerPlayer player, Ride ride, double drop) {
		Vec3 motion = player.getKnownMovement();
		Vec3 along = new Vec3(motion.x, 0, motion.z);
		double speed = along.length();

		double gained = Math.min(TOP_SPEED, speed + GAIN_PER_BLOCK * drop);
		if (speed > 0.02 && gained > speed + 1e-3) {
			Vec3 kicked = along.scale(gained / speed);
			player.setDeltaMovement(kicked.x, 0, kicked.z);
			player.needsSync = true;
		}

		if (!ride.landed || drop >= HARD_LANDING) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6F, 0.8F);
		}
		ride.landed = true;
	}

	private static void dust(ServerPlayer player, BlockState ground, Vec3 motion) {
		if (ground.isAir() || !(player.level() instanceof ServerLevel level)) return;
		// Behind the rider, where the shield's trailing edge scrapes.
		double x = player.getX() - motion.x * 1.5;
		double z = player.getZ() - motion.z * 1.5;
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
			x, player.getY() + 0.1, z, 3, 0.2, 0.05, 0.2, 0.05);
	}

	private static void wear(ServerPlayer player, BlockState ground) {
		if (!ShieldSurf.wears(player)) return;
		if (ground.is(BlockTags.SAND) || ground.is(BlockTags.SNOW) || ground.is(BlockTags.ICE)) return;

		ItemStack shield = player.getUseItem();
		shield.hurtAndBreak(1, player, player.getUsedItemHand());
		if (shield.isEmpty()) Awards.wornThrough(player);
	}
}
