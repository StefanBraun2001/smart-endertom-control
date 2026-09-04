package eu.stefanbraun612.smartendertomcontrol.mixin;

import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PhantomSpawner.class)
public interface PhantomSpawnerAccessor {
	@Accessor("nextTick")
	int smartendertomcontrol$getNextTick();

	@Accessor("nextTick")
	void smartendertomcontrol$setNextTick(int value);
}
