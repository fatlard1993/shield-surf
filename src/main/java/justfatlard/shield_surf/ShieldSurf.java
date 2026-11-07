package justfatlard.shield_surf;

import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.SettingsApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShieldSurf implements ModInitializer {
	public static final String MOD_ID = "shield-surf";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static SettingsApi.Setting<Boolean> wear;

	@Override
	public void onInitialize() {
		wear = PandoricalApi.settings().serverGroup(MOD_ID, "Shield Surf")
			.toggle("wear", "Shields wear while ridden", true)
			.describe("A point every half second on hard ground. Sand, snow and ice are free");

		ServerTickEvents.END_SERVER_TICK.register(Rides::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Rides.forget(handler.getPlayer()));

		LOGGER.info("Loaded shield-surf");
	}

	/** Whether riding costs the shield anything on this server. */
	public static boolean wears(ServerPlayer player) {
		return wear == null || wear.get(player);
	}
}
