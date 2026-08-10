package eu.stefanbraun612.smartphantomcontrol.mixin;

import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PhantomSpawner.class)
public interface PhantomSpawnerAccessor {
	@Accessor("nextTick")
	int smartphantomcontrol$getNextTick();

	@Accessor("nextTick")
	void smartphantomcontrol$setNextTick(int value);
}
