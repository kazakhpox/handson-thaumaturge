package net.handsonthaumaturge.station;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

/**
 * Thaumaturge's own code, reached by name: it is published on no maven this addon can build against, so the addon compiles
 * without it and calls it at run time. Everything that crafts, pays and checks goes through Thaumaturge itself (its workbench
 * menu, its payment plan, its research gate); this only finds the methods. When a name is missing (another Thaumaturge version)
 * the station opens nothing and Thaumaturge's screen stays as it was.
 */
public final class Thaum {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PACKAGE = "com.leclowndu93150.thaumaturge.";
    public static final ResourceLocation WORKBENCH = ResourceLocation.fromNamespaceAndPath("thaumaturge", "arcane_workbench");
    /** The research that lets a player understand the workbench (Thaumaturge's own gate on its use). */
    private static final ResourceLocation WORKBENCH_GATE = ResourceLocation.fromNamespaceAndPath("thaumaturge", "gotdream");
    /** The primal aspects in the order of the workbench's crystal slots (its menu's PRIMAL_ORDER). */
    public static final List<String> PRIMALS = List.of("aer", "ignis", "aqua", "terra", "ordo", "perditio");

    private static boolean failed;
    private static Class<?> workbenchEntity;
    private static Method getInventory;
    private static Method refreshAura;
    private static Field auraVis;
    private static Constructor<?> menu;
    private static Method takeCraft;
    private static Method asArcaneCraftInput;
    private static Method findRecipe;
    private static Method plan;
    private static Method crystalsToConsume;
    private static Method auraVisOfPlan;
    private static Method entries;
    private static Method aspectOf;
    private static Method passes;
    private static Method isValidCrystal;
    private static final Object[] primalKeys = new Object[6];

    private Thaum() {
    }

    private static boolean ready() {
        if (workbenchEntity != null) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            workbenchEntity = Class.forName(PACKAGE + "content.workbench.BlockEntityArcaneWorkbench");
            getInventory = workbenchEntity.getMethod("getInventory");
            refreshAura = workbenchEntity.getMethod("refreshAura");
            auraVis = workbenchEntity.getField("auraVis");
            Class<?> menuClass = Class.forName(PACKAGE + "content.workbench.MenuArcaneWorkbench");
            menu = menuClass.getConstructor(int.class, Inventory.class, workbenchEntity);
            takeCraft = menuClass.getDeclaredMethod("takeCraft");
            takeCraft.setAccessible(true);
            Class<?> inventory = Class.forName(PACKAGE + "content.workbench.InventoryArcaneWorkbench");
            asArcaneCraftInput = inventory.getMethod("asArcaneCraftInput");
            Class<?> input = Class.forName(PACKAGE + "content.recipe.workbench.ArcaneCraftingInput");
            findRecipe = Class.forName(PACKAGE + "content.recipe.ThaumaturgeCraftingManager")
                    .getMethod("findMatchingArcaneRecipe", net.minecraft.world.level.Level.class, input, Player.class);
            Class<?> recipe = Class.forName(PACKAGE + "api.recipe.IArcaneRecipe");
            Class<?> workbench = Class.forName(PACKAGE + "api.recipe.IArcaneWorkbench");
            Class<?> payment = Class.forName(PACKAGE + "content.workbench.WorkbenchPayment");
            plan = payment.getMethod("plan", recipe, workbench, Player.class);
            Class<?> planClass = plan.getReturnType();
            crystalsToConsume = planClass.getMethod("crystalsToConsume");
            auraVisOfPlan = planClass.getMethod("auraVis");
            entries = Class.forName(PACKAGE + "api.aspect.AspectList").getMethod("entries");
            aspectOf = Class.forName(PACKAGE + "api.aspect.AspectInstance").getMethod("aspect");
            passes = Class.forName(PACKAGE + "content.research.DeviceGate")
                    .getMethod("passes", Player.class, ResourceLocation.class);
            isValidCrystal = Class.forName(PACKAGE + "content.workbench.SlotCrystalEssentia")
                    .getMethod("isValidCrystal", ItemStack.class, ResourceKey.class);
            Class<?> aspects = Class.forName(PACKAGE + "api.aspect.TTAspects");
            for (int i = 0; i < PRIMALS.size(); i++) {
                primalKeys[i] = aspects.getField(PRIMALS.get(i).toUpperCase(java.util.Locale.ROOT)).get(null);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            workbenchEntity = null;
            LOGGER.error("Hands-On Thaumaturge: this Thaumaturge does not have what the workbench station needs", e);
            return false;
        }
    }

    /** Thaumaturge's arcane workbench block (air until Thaumaturge has registered it). */
    public static Block workbench() {
        Block block = BuiltInRegistries.BLOCK.get(WORKBENCH);
        return block == null ? Blocks.AIR : block;
    }

    public static boolean isWorkbench(BlockEntity entity) {
        return ready() && workbenchEntity.isInstance(entity);
    }

    /** The workbench's own inventory: grid 0-8, the six primal crystals 9-14, the wand 15. */
    public static Container inventory(BlockEntity workbench) {
        try {
            return (Container) getInventory.invoke(workbench);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Whether the player has the research that lets them use the workbench (tells them if not, as the block does). */
    public static boolean understands(Player player) {
        if (!ready()) {
            return false;
        }
        try {
            return (boolean) passes.invoke(null, player, WORKBENCH_GATE);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Reads the aura again (Thaumaturge's menu does it every half second) and returns the vis there. */
    public static int refreshAura(BlockEntity workbench) {
        try {
            refreshAura.invoke(workbench);
            return auraVis.getInt(workbench);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    /**
     * A menu of Thaumaturge's own on the workbench for the player, as its screen would have: it works out the result (an arcane
     * recipe with its payment, else a vanilla one) and crafts it. Closed again with {@link #close}.
     */
    public static AbstractContainerMenu openMenu(ServerPlayer player, BlockEntity workbench) {
        try {
            return (AbstractContainerMenu) menu.newInstance(0, player.getInventory(), workbench);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void close(AbstractContainerMenu open, Player player) {
        open.removed(player);
    }

    /** Crafts what the menu's result slot shows, paying as Thaumaturge does; empty when it cannot. */
    public static ItemStack craft(AbstractContainerMenu open) {
        try {
            return (ItemStack) takeCraft.invoke(open);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Hands-On Thaumaturge: crafting at the workbench failed", e);
            return ItemStack.EMPTY;
        }
    }

    /** What the recipe on the grid costs: aura vis, and which primal crystals (bits in {@link #PRIMALS} order) it burns. */
    public record Cost(int vis, int crystals) {
        public static final Cost NONE = new Cost(-1, 0);
    }

    public static Cost cost(ServerPlayer player, BlockEntity workbench) {
        try {
            Object inventory = getInventory.invoke(workbench);
            Object recipe = findRecipe.invoke(null, player.level(), asArcaneCraftInput.invoke(inventory), player);
            if (recipe == null) {
                return Cost.NONE;
            }
            Object found = plan.invoke(null, recipe, inventory, player);
            int crystals = 0;
            for (Object entry : (List<?>) entries.invoke(crystalsToConsume.invoke(found))) {
                Optional<? extends ResourceKey<?>> key = ((Holder<?>) aspectOf.invoke(entry)).unwrapKey();
                int index = key.map(k -> PRIMALS.indexOf(k.location().getPath())).orElse(-1);
                if (index >= 0) {
                    crystals |= 1 << index;
                }
            }
            return new Cost((int) auraVisOfPlan.invoke(found), crystals);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Cost.NONE;
        }
    }

    /** Whether the stack is an essentia crystal of the primal at {@code index} in {@link #PRIMALS} (the screen's crystal slots). */
    public static boolean isCrystal(ItemStack stack, int index) {
        if (!ready()) {
            return false;
        }
        try {
            return (boolean) isValidCrystal.invoke(null, stack, primalKeys[index]);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    // ---- The research table.

    public static final ResourceLocation RESEARCH_TABLE = ResourceLocation.fromNamespaceAndPath("thaumaturge", "research_table");
    private static boolean tableFailed;
    private static Class<?> tableEntity;
    private static Method items;
    private static Method ensurePuzzle;
    private static Method placeAspect;
    private static Method combineAspects;
    private static Method duplicateNote;
    private static Constructor<?> newHex;
    private static ResourceKey<? extends net.minecraft.core.Registry<?>> aspectRegistry;

    private static boolean tableReady() {
        if (tableEntity != null) {
            return true;
        }
        if (tableFailed) {
            return false;
        }
        try {
            Class<?> entity = Class.forName(PACKAGE + "content.research.table.BlockEntityResearchTable");
            Class<?> hex = Class.forName(PACKAGE + "content.research.note.HexGrid$Hex");
            items = entity.getMethod("items");
            ensurePuzzle = entity.getMethod("ensureNotePuzzle");
            placeAspect = entity.getMethod("placeAspect", ServerPlayer.class, hex, Holder.class);
            combineAspects = entity.getMethod("combineAspects", ServerPlayer.class, Holder.class, Holder.class, boolean.class, boolean.class);
            duplicateNote = entity.getMethod("duplicateNote", ServerPlayer.class);
            newHex = hex.getConstructor(int.class, int.class);
            aspectRegistry = (ResourceKey<? extends net.minecraft.core.Registry<?>>) Class.forName(PACKAGE + "api.aspect.IAspect")
                    .getField("REGISTRY_KEY").get(null);
            tableEntity = entity;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            tableFailed = true;
            LOGGER.error("Hands-On Thaumaturge: this Thaumaturge does not have what the research table station needs", e);
            return false;
        }
    }

    public static Block researchTable() {
        Block block = BuiltInRegistries.BLOCK.get(RESEARCH_TABLE);
        return block == null ? Blocks.AIR : block;
    }

    public static boolean isResearchTable(BlockEntity entity) {
        return tableReady() && tableEntity.isInstance(entity);
    }

    /** The table's two slots: the scribing tools, the note. */
    public static net.neoforged.neoforge.items.IItemHandlerModifiable tableItems(BlockEntity table) {
        return (net.neoforged.neoforge.items.IItemHandlerModifiable) invoke(items, table);
    }

    /** Draws the note's puzzle when it has none yet, and shows the table as it is now to everyone (its menu does both). */
    public static void notesChanged(BlockEntity table) {
        invoke(ensurePuzzle, table);
        table.setChanged();
        if (table.getLevel() != null) {
            table.getLevel().sendBlockUpdated(table.getBlockPos(), table.getBlockState(), table.getBlockState(), 3);
        }
    }

    private static Holder<?> aspect(ServerPlayer player, ResourceLocation id) {
        if (id == null) {
            return null;
        }
        Optional<?> found = player.registryAccess().lookupOrThrow((ResourceKey) aspectRegistry)
                .get(ResourceKey.create((ResourceKey) aspectRegistry, id));
        return (Holder<?>) found.orElse(null);
    }

    /** Writes an aspect into a hex of the note (or rubs it out, {@code aspect} null), as the table's screen asks. */
    public static void place(BlockEntity table, ServerPlayer player, int q, int r, ResourceLocation aspect) {
        Holder<?> holder = aspect(player, aspect);
        if (aspect != null && holder == null) {
            return;
        }
        try {
            invoke(placeAspect, table, player, newHex.newInstance(q, r), holder);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void combine(BlockEntity table, ServerPlayer player, ResourceLocation first, ResourceLocation second,
            boolean bonusFirst, boolean bonusSecond) {
        Holder<?> a = aspect(player, first);
        Holder<?> b = aspect(player, second);
        if (a != null && b != null) {
            invoke(combineAspects, table, player, a, b, bonusFirst, bonusSecond);
        }
    }

    public static void duplicate(BlockEntity table, ServerPlayer player) {
        invoke(duplicateNote, table, player);
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isWorkbenchAt(net.minecraft.world.level.Level level, BlockPos pos) {
        return level.getBlockState(pos).is(workbench());
    }
}
