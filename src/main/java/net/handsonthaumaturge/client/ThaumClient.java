package net.handsonthaumaturge.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * What the research table's 3D sheet needs from Thaumaturge on the client, reached by name (the addon compiles without it): the
 * note's puzzle, the hex grid's geometry, and the player's aspect pool, the aspects' looks and names. Everything the server checks stays Thaumaturge's.
 */
final class ThaumClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String P = "com.leclowndu93150.thaumaturge.";
    static final ResourceLocation DUPLICATION = ResourceLocation.fromNamespaceAndPath("thaumaturge", "research_duplication");
    static final ResourceLocation MASTERY = ResourceLocation.fromNamespaceAndPath("thaumaturge", "research_mastery");
    static final int TYPE_BLANK = 0;
    static final int TYPE_ROOT = 1;
    static final int TYPE_PLACED = 2;

    private static boolean failed;
    private static boolean ready;
    private static ResourceKey<? extends Registry<?>> aspectRegistry;
    private static Method dataOf;
    private static Method complete;
    private static Method cells;
    private static Method cellHex;
    private static Method cellType;
    private static Method cellAspect;
    private static Method cellActive;
    private static Method hexQ;
    private static Method hexR;
    private static Constructor<?> newHex;
    private static Method neighbour;
    private static Method pixelX;
    private static Method pixelY;
    private static Method pixelToHex;
    private static Method connects;
    private static Method poolData;
    private static Method poolMap;
    private static Method poolAmount;
    private static Method isDiscovered;
    private static Method idOf;
    private static Method aspectColour;
    private static Method aspectTexture;
    private static Method aspectComponents;
    private static Method aspectPrimal;
    private static Method aspectName;
    private static Method researchOf;
    private static Method hasCompleted;
    private static Method bonusAspects;
    private static Method amountOf;

    private ThaumClient() {
    }

    static boolean ready() {
        if (ready) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            Class<?> aspect = Class.forName(P + "api.aspect.IAspect");
            aspectRegistry = (ResourceKey<? extends Registry<?>>) aspect.getField("REGISTRY_KEY").get(null);
            aspectColour = aspect.getMethod("color");
            aspectTexture = aspect.getMethod("texture");
            aspectComponents = aspect.getMethod("components");
            aspectPrimal = aspect.getMethod("isPrimal");
            aspectName = Class.forName(P + "api.aspect.AspectComponents").getMethod("name", Holder.class);
            dataOf = Class.forName(P + "content.research.note.ResearchNotes").getMethod("dataOf", ItemStack.class);
            Class<?> data = Class.forName(P + "content.research.note.ResearchNoteData");
            complete = data.getMethod("complete");
            cells = data.getMethod("cells");
            Class<?> cell = Class.forName(P + "content.research.note.ResearchNoteData$Cell");
            cellHex = cell.getMethod("hex");
            cellType = cell.getMethod("type");
            cellAspect = cell.getMethod("aspectOrNull");
            cellActive = cell.getMethod("active");
            Class<?> grid = Class.forName(P + "content.research.note.HexGrid");
            Class<?> hex = Class.forName(P + "content.research.note.HexGrid$Hex");
            hexQ = hex.getMethod("q");
            hexR = hex.getMethod("r");
            newHex = hex.getConstructor(int.class, int.class);
            neighbour = hex.getMethod("neighbour", int.class);
            pixelX = hex.getMethod("pixelX", float.class);
            pixelY = hex.getMethod("pixelY", float.class);
            pixelToHex = grid.getMethod("pixelToHex", float.class, float.class, float.class);
            connects = Class.forName(P + "content.research.note.NoteRules")
                    .getMethod("connects", Holder.class, Holder.class, Predicate.class);
            Class<?> pools = Class.forName(P + "content.research.pool.AspectPools");
            poolData = pools.getMethod("data", Player.class);
            poolAmount = pools.getMethod("amount", Player.class, Holder.class);
            isDiscovered = pools.getMethod("isDiscovered", Player.class, Holder.class);
            idOf = pools.getMethod("idOf", Holder.class);
            poolMap = Class.forName(P + "content.research.pool.AspectPoolData").getMethod("pool");
            researchOf = Class.forName(P + "api.capability.PlayerResearchAccess").getMethod("of", Player.class);
            hasCompleted = researchOf.getReturnType().getMethod("hasCompleted", ResourceLocation.class);
            Class<?> table = Class.forName(P + "content.research.table.BlockEntityResearchTable");
            bonusAspects = table.getMethod("bonusAspects");
            amountOf = Class.forName(P + "api.aspect.AspectList").getMethod("amountOf", Holder.class);
            ready = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            LOGGER.error("Hands-On Thaumaturge: this Thaumaturge does not have what the research table needs", e);
            return false;
        }
    }

    private static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- The note.

    /** The note's puzzle, or null for no note (or one without data). */
    static Object note(ItemStack stack) {
        return stack.isEmpty() ? null : call(dataOf, null, stack);
    }

    static boolean complete(Object note) {
        return (boolean) call(complete, note);
    }

    static List<?> cells(Object note) {
        return (List<?>) call(cells, note);
    }

    static Object hexOf(Object cell) {
        return call(cellHex, cell);
    }

    static int type(Object cell) {
        return (int) call(cellType, cell);
    }

    static Holder<?> aspectOf(Object cell) {
        return (Holder<?>) call(cellAspect, cell);
    }

    static boolean active(Object cell) {
        return (boolean) call(cellActive, cell);
    }

    static int q(Object hex) {
        return (int) call(hexQ, hex);
    }

    static int r(Object hex) {
        return (int) call(hexR, hex);
    }

    static Object hex(int q, int r) {
        try {
            return newHex.newInstance(q, r);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static Object neighbour(Object hex, int direction) {
        return call(neighbour, hex, direction);
    }

    static float pixelX(Object hex, float size) {
        return (float) call(pixelX, hex, size);
    }

    static float pixelY(Object hex, float size) {
        return (float) call(pixelY, hex, size);
    }

    static Object pixelToHex(float x, float y, float size) {
        return call(pixelToHex, null, x, y, size);
    }

    static boolean connects(Holder<?> a, Holder<?> b, Player player) {
        Predicate<Holder<?>> discovered = aspect -> discovered(player, aspect);
        return (boolean) call(connects, null, a, b, discovered);
    }

    // ---- The player's aspects.

    static boolean discovered(Player player, Holder<?> aspect) {
        return (boolean) call(isDiscovered, null, player, aspect);
    }

    static int amount(Player player, Holder<?> aspect) {
        return (int) call(poolAmount, null, player, aspect);
    }

    static ResourceLocation id(Holder<?> aspect) {
        return (ResourceLocation) call(idOf, null, aspect);
    }

    /** The aspects the player has discovered, sorted by id, as the screen's palette lists them. */
    static List<Holder<?>> discoveredAspects(Player player) {
        Map<?, ?> pool = (Map<?, ?>) call(poolMap, call(poolData, null, player));
        List<ResourceLocation> ids = new ArrayList<>();
        for (Object key : pool.keySet()) {
            ids.add((ResourceLocation) key);
        }
        Collections.sort(ids);
        List<Holder<?>> result = new ArrayList<>();
        HolderLookup.RegistryLookup<?> lookup = Minecraft.getInstance().level.registryAccess().lookupOrThrow((ResourceKey) aspectRegistry);
        for (ResourceLocation id : ids) {
            lookup.get(ResourceKey.create((ResourceKey) aspectRegistry, id)).ifPresent(found -> result.add((Holder<?>) found));
        }
        return result;
    }

    static int colour(Holder<?> aspect) {
        return (int) call(aspectColour, aspect.value());
    }

    static ResourceLocation texture(Holder<?> aspect) {
        return (ResourceLocation) call(aspectTexture, aspect.value());
    }

    static List<Holder<?>> components(Holder<?> aspect) {
        return (List<Holder<?>>) call(aspectComponents, aspect.value());
    }

    static boolean primal(Holder<?> aspect) {
        return (boolean) call(aspectPrimal, aspect.value());
    }

    static Component name(Holder<?> aspect) {
        return (Component) call(aspectName, null, aspect);
    }

    static boolean completed(Player player, ResourceLocation research) {
        return (boolean) call(hasCompleted, call(researchOf, null, player), research);
    }

    /** What the table gives on top of the player's pool (the aids round it): the screen counts it as available too. */
    static int bonus(net.minecraft.world.level.block.entity.BlockEntity table, Holder<?> aspect) {
        if (table == null || !bonusAspects.getDeclaringClass().isInstance(table)) {
            return 0;
        }
        return (int) call(amountOf, call(bonusAspects, table), aspect);
    }
}
