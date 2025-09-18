package xyz.srgnis.bodyhealthsystem.client.screen;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.world.World;
import xyz.srgnis.bodyhealthsystem.body.Body;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.registry.ScreenHandlers;

import static xyz.srgnis.bodyhealthsystem.network.ClientNetworking.requestBodyData;

/**
 * Unified handler for all Health Operations screens (overview + treatment).
 * Accepts an optional itemStack (healing/treatment item) and a target entity.
 */
public class BodyOperationsScreenHandler extends net.minecraft.screen.ScreenHandler {

	protected final PlayerEntity user;
	protected final LivingEntity entity;
	protected final ItemStack itemStack;

	public BodyOperationsScreenHandler(int syncId, PlayerInventory inventory, ItemStack itemStack, LivingEntity entity) {
		super(ScreenHandlers.BODY_OPS_SCREEN_HANDLER, syncId);
		this.user = inventory.player;
		this.entity = entity;
		this.itemStack = itemStack;
		// Request up-to-date body data clientside when screen opens
		if (entity.getWorld().isClient()) {
			requestBodyData(entity);
		}
	}

	// Client constructor
	public BodyOperationsScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
		this(syncId, playerInventory, readItemStack(buf), readEntity(buf, playerInventory.player.getWorld()));
	}

	private static ItemStack readItemStack(PacketByteBuf buf) {
		return buf.readItemStack();
	}

	private static LivingEntity readEntity(PacketByteBuf buf, World world) {
		int id = buf.readInt();
		return (LivingEntity) world.getEntityById(id);
	}

	public LivingEntity getEntity() { return entity; }

	public Body getBody() { return ((BodyProvider) entity).getBody(); }

	public PlayerEntity getUser() { return user; }

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }

	@Override
	public boolean canUse(PlayerEntity player) { return true; }

	public ItemStack getItemStack() { return itemStack; }
}
