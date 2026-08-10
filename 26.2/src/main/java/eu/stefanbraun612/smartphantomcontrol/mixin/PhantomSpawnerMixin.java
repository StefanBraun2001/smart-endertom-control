package eu.stefanbraun612.smartphantomcontrol.mixin;

import eu.stefanbraun612.smartphantomcontrol.PhantomSpawnLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PhantomSpawner.class)
public abstract class PhantomSpawnerMixin {

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void smartphantomcontrol$onTick(ServerLevel level, boolean spawnEnemies, CallbackInfo ci) {
		ci.cancel();
		PhantomSpawnLogic.tick((PhantomSpawnerAccessor) this, level, spawnEnemies);
	}
}
