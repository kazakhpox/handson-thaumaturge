package net.handsonthaumaturge.devtools;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationView;


/**
 * Builds a flat test world with Thaumaturge's arcane workbench and research table, opens the stations and works them with the mouse, saving screenshots along the way to the folder in the system property {@code handson.shots}. Then quits.
 */
@Mod("handson_thaumaturge_devtools")
public final class ShotDriver {
    private static final String WORLD = "handson_shots";
    
    private final Deque<Step> steps = new ArrayDeque<>();
    private int wait;
    
    private record Step(String name, BooleanSupplier until, Runnable action) {
    }

    public ShotDriver(IEventBus modBus) {
        if (System.getProperty("handson.shots") == null) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
        plan();
    }

    private void then(String name, int ticks, Runnable action) {
        steps.add(new Step(name, () -> true, action));
        steps.add(new Step(name + " wait", null, () -> wait = ticks));
    }

    private void when(String name, BooleanSupplier until, Runnable action) {
        steps.add(new Step(name, until, action));
    }

    private static final BlockPos BENCH = new BlockPos(0, -60, 0);
    private static final BlockPos TABLE = new BlockPos(4, -60, 0);

    private void plan() {
        when("title", () -> Minecraft.getInstance().screen instanceof TitleScreen, this::createWorld);
        when("in world", () -> Minecraft.getInstance().player != null && Minecraft.getInstance().getSingleplayerServer() != null
                && Minecraft.getInstance().screen == null, () -> wait = 60);
        then("build", 40, this::build);
        then("research", 40, () -> server(player -> {
            var server = player.getServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "thaumaturge research @a everything");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "thaumaturge aspects @a give all 20");
        }));
        then("look", 20, () -> look(2));
        then("world shot", 5, () -> shot("00_world"));

        // The arcane workbench: mirrored glass (quicksilver and a glass pane, an aqua and an ordo crystal, 50 vis).
        then("stand at bench", 20, () -> standBefore(BENCH));
        then("fill bench", 10, this::fillBench);
        then("aim bench", 5, () -> lookAt(BENCH));
        then("open bench", 80, ShotDriver::use);
        then("bench shot", 5, () -> shot("01_workbench"));
        then("aim result", 10, () -> aimAt(1.5F, 17.6F, 8.0F));
        then("hover shot", 5, () -> shot("02_workbench_hover"));
        then("take result", 30, ShotDriver::click);
        then("crafted shot", 5, () -> shot("03_workbench_crafted"));
        then("leave bench", 40, ShotDriver::leave);

        // The research table: scribing tools and a note to solve.
        then("stand at table", 20, () -> standBefore(TABLE));
        then("fill table", 10, this::fillTable);
        then("aim table", 5, () -> lookAt(TABLE));
        then("open table", 90, ShotDriver::use);
        then("aim inkwell", 10, () -> aimAt(3.0F, 17.0F, 8.0F));
        then("table shot", 5, () -> {
            shot("04_research");
            station().ifPresent(view -> System.out.println("[shots] research station " + view.type().id()));
        });
        then("aim aspect", 10, () -> aimAt(19.6F, 26.6F, 16.6F));
        then("take aspect", 20, ShotDriver::click);
        then("aim sheet", 10, () -> aimAt(16.6F, 16.05F, 9.8F));
        then("held shot", 5, () -> shot("05_research_held"));
        then("write aspect", 30, ShotDriver::click);
        then("written shot", 5, () -> shot("06_research_written"));
        then("aim book", 10, () -> aimAt(23.0F, 16.4F, 13.0F));
        then("open book", 30, ShotDriver::click);
        then("book shot", 5, () -> shot("07_research_book"));
        then("aim next page", 10, () -> aimAt(14.4F, 17.4F, 10.6F));
        then("turn page", 4, ShotDriver::click);
        then("turning shot", 5, () -> shot("07b_research_book_turning"));
        then("turned wait", 20, () -> { });
        then("turned shot", 5, () -> shot("07c_research_book_turned"));
        then("leave table", 40, ShotDriver::leave);
        // The other half of the table opens the same station, laid out on the same middle.
        then("stand at other half", 20, () -> standBefore(TABLE.east()));
        then("aim other half", 5, () -> lookAt(TABLE.east()));
        then("open other half", 90, ShotDriver::use);
        then("other half shot", 5, () -> shot("08_research_other_half"));
        then("leave other half", 40, ShotDriver::leave);
        // Taken out of its slot, the scribing tools leave the table.
        then("stand again", 20, () -> standBefore(TABLE));
        then("aim again", 5, () -> lookAt(TABLE));
        then("open again", 90, ShotDriver::use);
        then("empty tools", 30, () -> server(player -> {
            if (player.level().getBlockEntity(TABLE) instanceof com.leclowndu93150.thaumaturge.content.research.table.BlockEntityResearchTable table) {
                table.items().setStackInSlot(0, ItemStack.EMPTY);
                table.setChanged();
                player.level().sendBlockUpdated(TABLE, table.getBlockState(), table.getBlockState(), 3);
            }
        }));
        then("no tools shot", 5, () -> shot("09_research_no_tools"));
        then("refill tools", 30, () -> server(player -> {
            if (player.level().getBlockEntity(TABLE) instanceof com.leclowndu93150.thaumaturge.content.research.table.BlockEntityResearchTable table) {
                table.items().setStackInSlot(0, new ItemStack(com.leclowndu93150.thaumaturge.registry.TTItems.SCRIBING_TOOLS.get()));
                table.setChanged();
                player.level().sendBlockUpdated(TABLE, table.getBlockState(), table.getBlockState(), 3);
            }
        }));
        then("hover east tools", 10, ShotDriver::hoverTools);
        then("east shot", 5, () -> shot("10_table_east"));
        then("leave again", 40, ShotDriver::leave);
        // The table turned every way, the player always north of it looking south.
        for (Direction facing : new Direction[]{Direction.WEST, Direction.SOUTH, Direction.NORTH}) {
            then("turn " + facing, 30, () -> placeTable(facing));
            // From the south this time: the station is worked from the same side whichever way the player comes.
            then("stand " + facing, 20, () -> server(player -> player.teleportTo(player.serverLevel(), TABLE.getX() + 0.5 + (facing == Direction.SOUTH ? 1.6 : 0),
                    TABLE.getY(), TABLE.getZ() + (facing == Direction.SOUTH ? 0.5 : 2.6), facing == Direction.SOUTH ? 90.0F : 180.0F, 45.0F)));
            then("aim " + facing, 5, () -> lookAt(TABLE));
            then("open " + facing, 90, ShotDriver::use);
            then("hover tools " + facing, 10, ShotDriver::hoverTools);
            then("shot " + facing, 5, () -> shot("10_table_" + facing.getName()));
            then("leave " + facing, 40, ShotDriver::leave);
        }
        then("quit", 5, () -> Minecraft.getInstance().stop());
    }

    private void build() {
        server(player -> {
            ServerLevel level = player.serverLevel();
            level.setDayTime(6000);
            level.setBlockAndUpdate(BENCH, com.leclowndu93150.thaumaturge.registry.TTBlocks.ARCANE_WORKBENCH.get().defaultBlockState());
            var table = com.leclowndu93150.thaumaturge.registry.TTBlocks.RESEARCH_TABLE.get().defaultBlockState();
            var facing = com.leclowndu93150.thaumaturge.content.research.table.BlockResearchTable.FACING;
            var part = com.leclowndu93150.thaumaturge.content.research.table.BlockResearchTable.PART;
            // A player standing south of it looking north places it facing east, its second half to the east.
            level.setBlockAndUpdate(TABLE, table.setValue(facing, Direction.EAST)
                    .setValue(part, com.leclowndu93150.thaumaturge.content.research.table.ResearchTablePart.MAIN));
            level.setBlockAndUpdate(TABLE.east(), table.setValue(facing, Direction.WEST)
                    .setValue(part, com.leclowndu93150.thaumaturge.content.research.table.ResearchTablePart.EXT));
        });
    }

    private static ItemStack crystal(String aspect, int count) {
        ItemStack stack = new ItemStack(com.leclowndu93150.thaumaturge.registry.TTItems.ESSENTIA_CRYSTAL.get(), count);
        var lookup = Minecraft.getInstance().getSingleplayerServer().registryAccess()
                .lookupOrThrow(com.leclowndu93150.thaumaturge.api.aspect.IAspect.REGISTRY_KEY);
        var holder = lookup.getOrThrow(net.minecraft.resources.ResourceKey.create(
                com.leclowndu93150.thaumaturge.api.aspect.IAspect.REGISTRY_KEY,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumaturge", aspect)));
        stack.set(com.leclowndu93150.thaumaturge.registry.TTDataComponents.CRYSTAL_ASPECT.get(),
                new com.leclowndu93150.thaumaturge.api.aspect.AspectInstance(holder, 1));
        return stack;
    }

    private void fillBench() {
        server(player -> {
            if (player.level().getBlockEntity(BENCH) instanceof com.leclowndu93150.thaumaturge.content.workbench.BlockEntityArcaneWorkbench bench) {
                var inventory = bench.getInventory();
                inventory.setItem(0, new ItemStack(com.leclowndu93150.thaumaturge.registry.TTItems.QUICKSILVER.get(), 4));
                inventory.setItem(1, new ItemStack(net.minecraft.world.item.Items.GLASS_PANE, 8));
                String[] primals = {"aer", "ignis", "aqua", "terra", "ordo", "perditio"};
                for (int i = 0; i < 6; i++) {
                    inventory.setItem(9 + i, crystal(primals[i], 4));
                }
                inventory.setItem(15, com.leclowndu93150.thaumaturge.content.wands.ItemWand.create(
                        com.leclowndu93150.thaumaturge.registry.TTItems.WAND.get(),
                        com.leclowndu93150.thaumaturge.registry.TTWandParts.CAP_IRON.get(),
                        com.leclowndu93150.thaumaturge.registry.TTWandParts.ROD_WOOD.get(), false));
                inventory.setChanged();
            }
        });
    }

    /** The research table with its main half at TABLE facing {@code facing}, its other half that way. */
    private void placeTable(Direction facing) {
        server(player -> {
            ServerLevel level = player.serverLevel();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                level.setBlockAndUpdate(TABLE.relative(d), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(TABLE, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            var table = com.leclowndu93150.thaumaturge.registry.TTBlocks.RESEARCH_TABLE.get().defaultBlockState();
            var f = com.leclowndu93150.thaumaturge.content.research.table.BlockResearchTable.FACING;
            var part = com.leclowndu93150.thaumaturge.content.research.table.BlockResearchTable.PART;
            BlockPos other = TABLE.relative(facing);
            level.setBlockAndUpdate(TABLE, table.setValue(f, facing)
                    .setValue(part, com.leclowndu93150.thaumaturge.content.research.table.ResearchTablePart.MAIN));
            level.setBlockAndUpdate(other, table.setValue(f, facing.getOpposite())
                    .setValue(part, com.leclowndu93150.thaumaturge.content.research.table.ResearchTablePart.EXT));
        });
        fillTable();
    }

    /** The mouse over the tools slot, wherever the station put it. */
    private static void hoverTools() {
        station().ifPresent(view -> view.node("tools").ifPresent(node -> {
            Vec3 world = node.worldPosition();
            Optional<Vector2f> screen = DiegesisClient.toScreen(world.add(0, 0.06, 0));
            screen.ifPresent(point -> {
                Minecraft mc = Minecraft.getInstance();
                double scale = mc.getWindow().getGuiScale();
                long window = mc.getWindow().getWindow();
                GLFW.glfwSetCursorPos(window, point.x * scale, point.y * scale);
                invoke("onMove", new Class<?>[]{long.class, double.class, double.class}, window, point.x * scale, point.y * scale);
            });
        }));
    }

    private void fillTable() {
        server(player -> {
            if (player.level().getBlockEntity(TABLE) instanceof com.leclowndu93150.thaumaturge.content.research.table.BlockEntityResearchTable table) {
                ItemStack note = new ItemStack(com.leclowndu93150.thaumaturge.registry.TTItems.RESEARCH_NOTE.get());
                note.set(com.leclowndu93150.thaumaturge.registry.TTDataComponents.RESEARCH_NOTE.get(),
                        new com.leclowndu93150.thaumaturge.content.research.note.ResearchNoteData(
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumaturge", "base_artifice"), 0, 0x8040C0,
                                false, 0, java.util.List.of()));
                table.items().setStackInSlot(0, new ItemStack(com.leclowndu93150.thaumaturge.registry.TTItems.SCRIBING_TOOLS.get()));
                table.items().setStackInSlot(1, note);
                table.ensureNotePuzzle();
                table.setChanged();
            }
        });
    }

    /** Puts stacks into a block's container: stack, slot, stack, slot... */
    private static void fill(BlockPos pos, Object... stacksAndSlots) {
        server(player -> {
            if (player.level().getBlockEntity(pos) instanceof net.minecraft.world.Container container) {
                for (int i = 0; i < stacksAndSlots.length; i += 2) {
                    container.setItem((Integer) stacksAndSlots[i + 1], ((ItemStack) stacksAndSlots[i]).copy());
                }
                container.setChanged();
            }
        });
    }

    private static void standBefore(BlockPos pos) {
        server(player -> player.teleportTo(player.serverLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.6, 0.0F, 45.0F));
    }

    /** Turns the player's eyes on the middle of a block's lower half. */
    private static void lookAt(BlockPos pos) {
        Minecraft.getInstance().player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5));
    }

    private static void leave() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        }
    }

    /** The player stands 3 blocks from the row of stations on one side, looking at it. */
    private void look(int side) {
        server(player -> {
            float[] yaw = {180.0F, 90.0F, 0.0F, -90.0F};
            double[][] at = {{3.5, -60, 4.0}, {7.5, -60, 0.5}, {3.5, -60, -3.0}, {-0.5, -60, 0.5}};
            player.teleportTo(player.serverLevel(), at[side][0], at[side][1], at[side][2], yaw[side], 30.0F);
        });
    }

    private void tick() {
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = steps.peek();
        if (step == null) {
            return;
        }
        if (step.until() == null) {
            steps.poll();
            step.action().run();
            return;
        }
        if (step.until().getAsBoolean()) {
            steps.poll();
            System.out.println("[shots] " + step.name());
            step.action().run();
        }
    }

    private void createWorld() {
        Minecraft mc = Minecraft.getInstance();
        Path saves = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
        try {
            if (Files.exists(saves)) {
                try (var walk = Files.walk(saves)) {
                    walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions(), mc.screen);
    }

    /** A right click at what the crosshair is on, with an empty hand: the player uses the anvil as anybody would. */
    private static void use() {
        Minecraft mc = Minecraft.getInstance();
        mc.player.getInventory().selected = 4;
        System.out.println("[shots] looking at " + mc.hitResult);
        try {
            Method method = Minecraft.class.getDeclaredMethod("startUseItem");
            method.setAccessible(true);
            method.invoke(mc);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void server(java.util.function.Consumer<ServerPlayer> action) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayers().get(0)));
    }

    private static Optional<StationView> station() {
        return DiegesisClient.focusedStation();
    }

    /** Puts the mouse over a point of the scene. */
    private static void aimAt(float x, float y, float z) {
        station().ifPresent(view -> {
            Vec3 world = view.toWorld(x, y, z);
            Optional<Vector2f> screen = DiegesisClient.toScreen(world);
            if (screen.isEmpty()) {
                System.out.println("[shots] point off screen");
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            double scale = mc.getWindow().getGuiScale();
            double px = screen.get().x * scale;
            double py = screen.get().y * scale;
            long window = mc.getWindow().getWindow();
            GLFW.glfwSetCursorPos(window, px, py);
            invoke("onMove", new Class<?>[]{long.class, double.class, double.class}, window, px, py);
        });
    }

    private static void click() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        invoke("onPress", new Class<?>[]{long.class, int.class, int.class, int.class}, window, 0, GLFW.GLFW_PRESS, 0);
        invoke("onPress", new Class<?>[]{long.class, int.class, int.class, int.class}, window, 0, GLFW.GLFW_RELEASE, 0);
    }

    private static void invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method method = Minecraft.getInstance().mouseHandler.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(Minecraft.getInstance().mouseHandler, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        File dir = new File(System.getProperty("handson.shots"));
        dir.mkdirs();
        Screenshot.grab(dir, name + ".png", mc.getMainRenderTarget(), message -> System.out.println("[shots] " + message.getString()));
    }
}
