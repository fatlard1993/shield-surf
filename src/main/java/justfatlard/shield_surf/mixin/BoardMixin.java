package justfatlard.shield_surf.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import justfatlard.shield_surf.Boards;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What a board needs from the entity rules: to ride a player, and to be seen only by its viewers.
 *
 * <p>The game will not seat anything on an entity it does not save, which is every player. A board
 * is never saved either, so nothing is lost by letting it ride one.
 */
@Mixin(Entity.class)
public abstract class BoardMixin {

	@ModifyExpressionValue(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z"))
	private boolean shieldSurf$boardRidesAPlayer(boolean savable) {
		return savable || Boards.isBoard((Entity) (Object) this);
	}

	@Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
	private void shieldSurf$boardForItsViewers(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof Display.ItemDisplay && !Boards.shows(self, player)) cir.setReturnValue(false);
	}
}
