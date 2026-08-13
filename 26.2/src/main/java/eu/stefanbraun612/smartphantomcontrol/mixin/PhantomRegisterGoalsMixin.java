package eu.stefanbraun612.smartphantomcontrol.mixin;

import eu.stefanbraun612.smartphantomcontrol.PhantomRetaliateGoal;
import eu.stefanbraun612.smartphantomcontrol.PhantomTuningConfig;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Phantom.class)
public abstract class PhantomRegisterGoalsMixin {

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void smartphantomcontrol$addRetaliation(CallbackInfo ci) {
		PhantomTuningConfig.Data config = PhantomTuningConfig.get();
		if (config.neutralUntilEligible && config.retaliateWhenAttacked) {
			((MobTargetSelectorAccessor) (Object) this).smartphantomcontrol$getTargetSelector()
					.addGoal(0, new PhantomRetaliateGoal((Phantom) (Object) this));
		}
	}
}
