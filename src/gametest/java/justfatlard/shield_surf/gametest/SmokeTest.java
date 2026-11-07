package justfatlard.shield_surf.gametest;

import java.util.concurrent.atomic.AtomicBoolean;

import justfatlard.pandorical.gametest.Smoke;
import justfatlard.shield_surf.Rides;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * This mod loads, its mixin binds, and a world comes up with it installed.
 *
 * <p>At full level, a player dropped from a height who holds the use key on a shield is riding
 * before they land, with the ground made slick for them. Standing still on the shield ends the ride
 * a moment after landing, and letting go of the key ends it at once, slick ground and all.
 *
 * <p>The shield is raised through the client's own use key, as a player raises it, and not by
 * starting the use on the server: a client lets go of anything it did not raise itself the moment
 * it notices, which is what a player's release of the key looks like from the server.
 */
public final class SmokeTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "shield-surf", session -> {
			raiseInTheAir(session);
			waitForTheGround(session);
			session.onServer(server -> {
				if (!riding(session.player())) {
					throw new AssertionError("the ride ended on landing, before the rider had stood still");
				}
			});
			session.waitTicks(20);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				if (riding(player)) throw new AssertionError("standing still on the shield did not end the ride");
				if (slick(player)) throw new AssertionError("the ride is over but the ground is still slick");
			});
			session.context().getInput().releaseKey(options -> options.keyUse);
			session.waitTicks(5);

			raiseInTheAir(session);
			session.context().getInput().releaseKey(options -> options.keyUse);
			session.waitTicks(5);
			session.onServer(server -> {
				ServerPlayer player = session.player();
				if (riding(player)) throw new AssertionError("lowering the shield did not end the ride: " + state(player));
				if (slick(player)) throw new AssertionError("the shield is down but the ground is still slick");
			});
		});
	}

	/** Up in the air with the use key held on a shield: riding, by the time it has been seen falling. */
	private static void raiseInTheAir(Smoke.Session session) {
		session.onServer(server -> {
			ServerPlayer player = session.player();
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
			player.teleportTo(player.getX(), player.getY() + 8, player.getZ());
		});
		session.waitTicks(2);
		session.context().getInput().holdKey(options -> options.keyUse);
		session.waitTicks(10);
		session.onServer(server -> {
			ServerPlayer player = session.player();
			if (player.onGround()) throw new AssertionError("still on the ground after being lifted eight blocks");
			if (!riding(player)) throw new AssertionError("a shield raised in the air did not start a ride: " + state(player));
			if (!slick(player)) throw new AssertionError("riding, but the ground was not made slick");
		});
	}

	private static void waitForTheGround(Smoke.Session session) {
		AtomicBoolean down = new AtomicBoolean();
		for (int tick = 0; tick < 100 && !down.get(); tick++) {
			session.waitTicks(1);
			session.onServer(server -> down.set(session.player().onGround()));
		}
		if (!down.get()) throw new AssertionError("still falling after five seconds");
	}

	private static String state(ServerPlayer player) {
		return "using=" + player.isUsingItem() + " useItem=" + player.getUseItem() + " hand=" + player.getMainHandItem()
			+ " ground=" + player.onGround() + " liquid=" + player.isInLiquid() + " climbing=" + player.onClimbable()
			+ " passenger=" + player.isPassenger() + " swimming=" + player.isSwimming() + " flying=" + player.getAbilities().flying
			+ " alive=" + player.isAlive() + " health=" + player.getHealth() + " y=" + player.getY();
	}

	private static boolean riding(ServerPlayer player) {
		return Rides.riding(player.getUUID());
	}

	private static boolean slick(ServerPlayer player) {
		AttributeInstance friction = player.getAttribute(Attributes.FRICTION_MODIFIER);
		return friction != null && friction.hasModifier(Rides.RIDING);
	}
}
