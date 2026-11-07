package justfatlard.shield_surf.mixin;

import justfatlard.shield_surf.Rides;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A rider crouches on the shield.
 *
 * <p>The pose is the server's to set for everyone watching; the rider's own client keeps computing
 * its own, and sees itself standing unless it shares this process with the server. The fit check in
 * {@code updatePlayerPose} still runs on what is returned here, so a rider under a low ceiling is
 * not pushed into a block.
 */
@Mixin(Player.class)
public abstract class PlayerPoseMixin {

	@Inject(method = "getDesiredPose", at = @At("RETURN"), cancellable = true)
	private void shieldSurf$crouchOnTheShield(CallbackInfoReturnable<Pose> cir) {
		if (cir.getReturnValue() != Pose.STANDING) return;
		Player self = (Player) (Object) this;
		if (Rides.riding(self.getUUID())) cir.setReturnValue(Pose.CROUCHING);
	}
}
