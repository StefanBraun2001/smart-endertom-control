package eu.stefanbraun612.smartendertomcontrol.mixin;

import eu.stefanbraun612.smartendertomcontrol.SmartEndertomConfig;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the loot-generation portion of Creeper explosions for
 * configured blocks, guaranteeing either their normal drop or their own
 * block item (as if Silk Touch mined) instead of vanilla's distance-based
 * decay roll - see docs/GUIDE.md.
 */
@Mixin(BlockBehaviour.class)
public abstract class CreeperExplosionRecoveryMixin {
	@Inject(method = "onExplosionHit", at = @At("HEAD"), cancellable = true)
	private void smartendertomcontrol$recoverConfiguredBlocks(
			BlockState state,
			ServerLevel level,
			BlockPos pos,
			Explosion explosion,
			BiConsumer<ItemStack, BlockPos> drops,
			CallbackInfo ci) {
		SmartEndertomConfig.Data config = SmartEndertomConfig.get();
		if (!config.creeperBlockRecovery || !(explosion.getDirectSourceEntity() instanceof Creeper creeper)) {
			return;
		}
		if (creeper.isPowered() && !config.creeperRecoveryAppliesToCharged) {
			return;
		}

		SmartEndertomConfig.CreeperDropMode mode = config.creeperModeFor(state);
		if (mode == SmartEndertomConfig.CreeperDropMode.VANILLA) {
			return;
		}

		// This is vanilla's own onExplosionHit, with LootContextParams.EXPLOSION_RADIUS
		// deliberately never set - that context value is what the block's loot
		// table's explosion-decay function reads to roll a chance of dropping
		// nothing, so omitting it makes every configured drop succeed.
		if (!state.isAir() && explosion.getBlockInteraction() != Explosion.BlockInteraction.TRIGGER_BLOCK) {
			Block block = state.getBlock();
			boolean dropExperience = explosion.getIndirectSourceEntity() instanceof Player;
			if (block.dropFromExplosion(explosion)) {
				BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
				ItemStack tool = mode == SmartEndertomConfig.CreeperDropMode.SILK_TOUCH
						? smartendertomcontrol$silkTouchTool(level)
						: ItemStack.EMPTY;
				LootParams.Builder params = new LootParams.Builder(level)
						.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
						.withParameter(LootContextParams.TOOL, tool)
						.withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
						.withOptionalParameter(LootContextParams.THIS_ENTITY, explosion.getDirectSourceEntity());

				state.spawnAfterBreak(level, pos, tool, dropExperience);
				state.getDrops(params).forEach(stack -> drops.accept(stack, pos));
			}

			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			block.wasExploded(level, pos, explosion);
		}

		ci.cancel();
	}

	private static ItemStack smartendertomcontrol$silkTouchTool(ServerLevel level) {
		ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
		Holder<Enchantment> silkTouch = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.SILK_TOUCH);
		tool.enchant(silkTouch, 1);
		return tool;
	}
}
