/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.srgnis.bodyhealthsystem.items;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.client.screen.HealScreenHandler;

//FIXME: null pointers
public class MedkitItem extends Item {
	private static final String TARGET_NBT = "MedkitTargetId";
	public MedkitItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		user.openHandledScreen(createScreenHandlerFactory(stack, user));
		return TypedActionResult.success(stack);
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		if(entity instanceof BodyProvider bp){
			var body = bp.getBody();
			// If target is downed, start revival channel instead of opening UI
			if (body.isDowned()) {
				// Only allow one reviver
				if (!body.tryBeginRevive(user)) {
					return ActionResult.FAIL;
				}
				// Bind target id into item NBT for finish/cancel
				var tag = stack.getOrCreateNbt();
				tag.putInt(TARGET_NBT, entity.getId());
				// Use vanilla use-duration channeling
				user.setCurrentHand(hand);
				return ActionResult.CONSUME;
			}
			user.openHandledScreen(createScreenHandlerFactory(stack, entity));
			return ActionResult.CONSUME;
		}
		return ActionResult.PASS;
	}


	@Override
	public int getMaxUseTime(ItemStack stack) {
		return 8 * 20; // 8 seconds
	}

	@Override
	public net.minecraft.util.UseAction getUseAction(ItemStack stack) {
		return net.minecraft.util.UseAction.BRUSH;
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if (!world.isClient && user instanceof PlayerEntity pe) {
			var tag = stack.getNbt();
			if (tag != null && tag.contains(TARGET_NBT)) {
				var e = world.getEntityById(tag.getInt(TARGET_NBT));
				if (e instanceof LivingEntity le && le instanceof BodyProvider) {
					((BodyProvider) le).getBody().endRevive(pe);
				}
				tag.remove(TARGET_NBT);
				if (tag.isEmpty()) stack.setNbt(null);
			}
		}
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (!world.isClient && user instanceof PlayerEntity pe) {
			var tag = stack.getNbt();
			LivingEntity target = null;
			if (tag != null && tag.contains(TARGET_NBT)) {
				var e = world.getEntityById(tag.getInt(TARGET_NBT));
				if (e instanceof LivingEntity) target = (LivingEntity) e;
				tag.remove(TARGET_NBT);
				if (tag.isEmpty()) stack.setNbt(null);
			}
			if (target instanceof BodyProvider) {
				var body = ((BodyProvider) target).getBody();
				body.endRevive(pe);
				// Eligibility: head not destroyed; if torso destroyed, not allowed for base medkit
				var head = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD);
				var torso = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.TORSO);
				if (head != null && head.getHealth() <= 0.0f) return stack;
				if (torso != null && torso.getHealth() <= 0.0f) return stack;
				if (body.isDowned()) {
					body.applyRevival(1, 0);
					if (target instanceof PlayerEntity) {
						xyz.srgnis.bodyhealthsystem.network.ServerNetworking.syncBody((PlayerEntity) target);
					}
					stack.decrement(1);
				}
			}
		}
		return stack;
	}

	private ExtendedScreenHandlerFactory createScreenHandlerFactory(ItemStack stack, LivingEntity entity) {
		return new ExtendedScreenHandlerFactory() {

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
				return new HealScreenHandler(syncId, inventory, stack, entity);
			}

			@Override
			public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
				buf.writeItemStack(stack);
				buf.writeInt(entity.getId());
			}

			@Override
			public Text getDisplayName() {
				//FIXME
				return Text.literal("Medkit");
			}
		};
	}
}
