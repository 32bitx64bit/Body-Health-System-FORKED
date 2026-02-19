package xyz.srgnis.bodyhealthsystem.items;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.client.screen.BodyOperationsScreenHandler;
import xyz.srgnis.bodyhealthsystem.registry.ScreenHandlers;

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
			if (body == null) {
				return ActionResult.PASS;
			}
			// If target is downed, start revival channel instead of opening UI
			if (body.isDowned()) {
				// Only allow one reviver
				if (!body.tryBeginRevive(user)) {
					return ActionResult.FAIL;
				}
				// Inform all clients that revival has started (pauses timer UI)
				if (!user.getWorld().isClient) {
					xyz.srgnis.bodyhealthsystem.network.ServerNetworking.broadcastBody(entity);
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
				if (e instanceof LivingEntity le && le instanceof BodyProvider bp) {
					var body = bp.getBody();
					if (body != null) {
						body.endRevive(pe);
						// Notify all clients that revival has stopped (resume timer)
						xyz.srgnis.bodyhealthsystem.network.ServerNetworking.broadcastBody(le);
					}
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
			if (target instanceof BodyProvider bp) {
				var body = bp.getBody();
				if (body == null) {
					return stack;
				}
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
				return new BodyOperationsScreenHandler(syncId, inventory, stack, entity);
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
