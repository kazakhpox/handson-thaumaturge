package net.handsonthaumaturge;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;

import net.handsonthaumaturge.station.WorkbenchStation;

/**
 * Hands-On Thaumaturge: Thaumaturge's arcane workbench opens in the world as a Diegesis station. Adds nothing to the registries,
 * so a player or a server without it keeps Thaumaturge's own screens.
 */
@Mod(HandsOnThaumaturge.MOD_ID)
public final class HandsOnThaumaturge {
    public static final String MOD_ID = "handson_thaumaturge";

    public HandsOnThaumaturge() {
        WorkbenchStation.register();
        net.handsonthaumaturge.station.ResearchStation.register();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
