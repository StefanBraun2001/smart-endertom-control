package eu.stefanbraun612.smartendertomcontrol.mixin;

import eu.stefanbraun612.smartendertomcontrol.PhantomRetaliateGoal;
import eu.stefanbraun612.smartendertomcontrol.SmartEndertomConfig;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Phantom.class)
public abstract class PhantomRegisterGoalsMixin {

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void smartendertomcontrol$addRetaliation(CallbackInfo ci) {
		SmartEndertomConfig.Data config = SmartEndertomConfig.get();
		if (config.neutralUntilEligible && config.retaliateWhenAttacked) {
			((MobTargetSelectorAccessor) (Object) this).smartendertomcontrol$getTargetSelector()
					.addGoal(0, new PhantomRetaliateGoal((Phantom) (Object) this));
		}
	}
}
