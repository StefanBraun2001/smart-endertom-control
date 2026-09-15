package eu.stefanbraun612.smartendertomcontrol.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Phantom.class)
public interface PhantomCanAttackInvoker {
	@Invoker("canAttack")
	boolean smartendertomcontrol$canAttack(ServerLevel level, LivingEntity target, TargetingConditions conditions);
}
