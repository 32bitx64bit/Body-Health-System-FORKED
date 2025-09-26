package xyz.srgnis.bodyhealthsystem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.srgnis.bodyhealthsystem.command.DevCommands;
import xyz.srgnis.bodyhealthsystem.config.Config;
import xyz.srgnis.bodyhealthsystem.network.ServerNetworking;
import xyz.srgnis.bodyhealthsystem.registry.ScreenHandlers;
import xyz.srgnis.bodyhealthsystem.registry.ModItems;
import xyz.srgnis.bodyhealthsystem.registry.ModStatusEffects;
import xyz.srgnis.bodyhealthsystem.registry.ModBlocks;

// TemperatureAPI: block-based thermal effects
import gavinx.temperatureapi.api.BlockThermalAPI;
import gavinx.temperatureapi.api.TemperatureAPI;
import gavinx.temperatureapi.api.TemperatureResistanceAPI;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.state.property.Properties;
import xyz.srgnis.bodyhealthsystem.items.WoolClothingItem;
import xyz.srgnis.bodyhealthsystem.items.StrawHatItem;
import net.minecraft.util.math.Direction;

import static net.minecraft.client.util.InputUtil.GLFW_CURSOR;
import static net.minecraft.client.util.InputUtil.GLFW_CURSOR_NORMAL;

public class BHSMain implements ModInitializer {
	public static final String MOD_ID = "bodyhealthsystem";
	public  static Identifier MOD_IDENTIFIER = new Identifier(BHSMain.MOD_ID);
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final RegistryKey<ItemGroup> BHS_GROUP = RegistryKey.of(RegistryKeys.ITEM_GROUP, new Identifier(MOD_ID, "general"));

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		Registry.register(Registries.ITEM_GROUP, BHS_GROUP, FabricItemGroup.builder()
				.icon(() -> new ItemStack(ModItems.PLASTER_ITEM))
				.displayName(Text.translatable("bodyhealthsystem.group.general"))
				.build());

		ServerNetworking.initialize();
		DevCommands.initialize();
		ModItems.registerItems();
		ModBlocks.registerBlocks();
		ModStatusEffects.registerStatusEffects();
		Config.init(MOD_ID, Config.class);

		// Register a warming effect for lit campfires: +5°C within 5 blocks
		// Use FLOOD_FILL occlusion and dropoff (7)
		BlockThermalAPI.register((world, pos, state) -> {
			if (!state.isOf(Blocks.CAMPFIRE)) return null; // only vanilla campfire
			if (!state.contains(Properties.LIT) || !state.get(Properties.LIT)) return null; // only when lit
			return new BlockThermalAPI.ThermalSource(
					5.0, 5,
					BlockThermalAPI.OcclusionMode.FLOOD_FILL,
					7,
					BlockThermalAPI.FalloffCurve.COSINE
			);
		}, 5);

		// TemperatureResistanceAPI provider:
		TemperatureResistanceAPI.registerProvider(player -> {
			if (player == null) return null;
			double heat = 0.0;
			double cold = 0.0;
			var head = player.getEquippedStack(EquipmentSlot.HEAD);
			var chest = player.getEquippedStack(EquipmentSlot.CHEST);
			var legs = player.getEquippedStack(EquipmentSlot.LEGS);
			var feet = player.getEquippedStack(EquipmentSlot.FEET);

			if (!head.isEmpty() && head.getItem() instanceof StrawHatItem) {
				heat += TemperatureResistanceAPI.tierToDegrees(+3);
			}
			for (var stack : new net.minecraft.item.ItemStack[]{head, chest, legs, feet}) {
				if (stack == null || stack.isEmpty()) continue;
				if (stack.getItem() instanceof WoolClothingItem) {
					cold += TemperatureResistanceAPI.tierToDegrees(-3); // Tier 3 cold for the wool clothing
					continue;
				}
				if (stack.getItem() instanceof ArmorItem armor && armor.getMaterial() == ArmorMaterials.LEATHER) {
					cold += TemperatureResistanceAPI.tierToDegrees(-2); // Tier 2 cold per other leather piece
				}
			}
			if (heat == 0.0 && cold == 0.0) return null;
			return new TemperatureResistanceAPI.Resistance(heat, cold);
		});

		// Fire Resistance -> Heat resistance tier 6 while active
		TemperatureResistanceAPI.registerProvider(player -> {
			if (player == null) return null;
			if (player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE)) {
				return new TemperatureResistanceAPI.Resistance(TemperatureResistanceAPI.tierToDegrees(6), 0.0);
			}
			return null;
		});

		// If configured, force the actual game rule to false on server start
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (Config.forceDisableVanillaRegen) {
				server.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false, server);
				LOGGER.info("[{}] Disabled natural regeneration gamerule due to config", MOD_ID);
			}
		});

		ScreenHandlers.registerScreenHandlers();

		// Air Conditioner: dynamic thermal provider
		BlockThermalAPI.register((world, pos, state) -> {
			if (!state.isOf(ModBlocks.AIR_CONDITIONER)) return null;
			var be = world.getBlockEntity(pos);
			if (!(be instanceof xyz.srgnis.bodyhealthsystem.block.AirConditionerBlockEntity ac)) return null;
			// Require fuel
			if (ac.getBurnTime() <= 0) return null;
			// Directional emission out of the block's front
			Direction face = state.get(Properties.HORIZONTAL_FACING);

			double deltaC;
			if (ac.isRegulating()) {
				// Regulate to 26°C against the environment (exclude block sources)
				double env = TemperatureAPI.getEnvironmentCelsius(world, pos);
				if (Double.isNaN(env)) return null;
				double error = 26.0 - env; // negative when too hot, positive when too cold
				// Small deadband to prevent flicker
				if (Math.abs(error) <= 0.25) return null;
				// AC only cools: emit when too hot (error < 0). Use the full error (no artificial clamp).
				if (error >= 0.0) return null;
				deltaC = error; // negative
			} else {
				// Constant breeze
				deltaC = -6.0;
			}

			return new BlockThermalAPI.ThermalSource(
					deltaC, 8,
					BlockThermalAPI.OcclusionMode.FLOOD_FILL,
					7,
					BlockThermalAPI.FalloffCurve.COSINE,
					face
			);
		}, 15);

		// Space Heater: dynamic thermal provider
		BlockThermalAPI.register((world, pos, state) -> {
			if (!state.isOf(ModBlocks.SPACE_HEATER)) return null;
			var be = world.getBlockEntity(pos);
			if (!(be instanceof xyz.srgnis.bodyhealthsystem.block.SpaceHeaterBlockEntity sh)) return null;
			if (sh.getBurnTime() <= 0) return null;
			Direction face = state.get(Properties.HORIZONTAL_FACING);

			double deltaC;
			if (sh.isRegulating()) {
				// Regulate to 26°C against the environment (exclude block sources)
				double env = TemperatureAPI.getEnvironmentCelsius(world, pos);
				if (Double.isNaN(env)) return null;
				double error = 26.0 - env; // positive when too cold
				// Small deadband to prevent flicker
				if (Math.abs(error) <= 0.25) return null;
				// Heater only heats: emit when too cold (error > 0). Use the full error (no artificial clamp).
				if (error <= 0.0) return null;
				deltaC = error; // positive
			} else {
				deltaC = +6.0;
			}

			return new BlockThermalAPI.ThermalSource(
					deltaC, 8,
					BlockThermalAPI.OcclusionMode.FLOOD_FILL,
					7,
					BlockThermalAPI.FalloffCurve.COSINE,
					face
			);
		}, 15);
	}

	public static boolean debuggerReleaseControl() {
		GLFW.glfwSetInputMode(MinecraftClient.getInstance().getWindow().getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
		return true;
	}
}