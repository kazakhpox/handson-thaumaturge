package net.handsonthaumaturge.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.MenuBinding;
import com.pockyl.diegesis.api.station.StationType;

import net.handsonthaumaturge.HandsOnThaumaturge;

/**
 * Thaumaturge's research table as a station worked through its own menu: the use goes to the server as Thaumaturge has it
 * (its research gate, its two-block table), the menu it opens is taken without its screen, and the scribing tools and the
 * note are that menu's two slots. Everything done on the note goes to the server as the screen's own packets.
 */
public final class ResearchStation {
    public static final ResourceLocation ID = HandsOnThaumaturge.id("research_table");
    static final ResourceLocation BLOCK = ResourceLocation.fromNamespaceAndPath("thaumaturge", "research_table");
    static final ResourceLocation MENU = ResourceLocation.fromNamespaceAndPath("thaumaturge", "research_table");
    static final String TOOLS = "tools";
    static final String NOTE = "note";

    private ResearchStation() {
    }

    /** From the client entry point, once Thaumaturge's menu type is registered. */
    static void register() {
        MenuType<?> menu = BuiltInRegistries.MENU.get(MENU);
        if (menu == null) {
            return;
        }
        StationSchema schema = StationSchema.builder()
                .slots(TOOLS, 1, SlotRules.DEFAULT.kept().maxStack(1))
                .slots(NOTE, 1, SlotRules.DEFAULT.kept())
                .build();
        StationType type = Diegesis.station(ID)
                .block(ResearchStation::block)
                .schema(schema)
                .menu(MenuBinding.of(menu).alsoAccepts(MENU).slots(TOOLS, 0, 1).slots(NOTE, 1, 1).build())
                .openWhen(ResearchStation::opens)
                .orientation(StationType.Orientation.PLAYER)
                .build();
        Diegesis.register(type);
    }

    private static Block block() {
        Block block = BuiltInRegistries.BLOCK.get(BLOCK);
        return block == null ? Blocks.AIR : block;
    }

    /** Sneaking keeps Thaumaturge's screen; the station stands on the table's main half, where its note and tools lie. */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        if (player.isSecondaryUseActive()) {
            return false;
        }
        for (Property<?> property : state.getProperties()) {
            if ("part".equals(property.getName())) {
                return "main".equals(state.getValue(property).toString().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return true;
    }
}
