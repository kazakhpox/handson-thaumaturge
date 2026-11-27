package net.handsonthaumaturge.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;

import net.handsonthaumaturge.station.ResearchStation;

/**
 * Thaumaturge's research table worked in the world. The scribing tools and the note lie on the table in their slots; while a
 * note is there its puzzle lies open on the table as a sheet of parchment, Thaumaturge's own hex grid drawn on it: the hexes, the
 * aspects written in, the lines of the chains that connect. The aspects the player knows hang over the far edge as a panel of
 * icons in the air (five by five, the screen's pages, the wheel or the arrows turn them): a click takes one up, a click on a
 * blank hex writes it in, a click on a written one rubs it out. A right click puts an aspect in the mixing tray beside the panel,
 * whose button combines the two; shift on a compound fills the tray with its parts (with the research for it). The book on
 * the table opens the guide to mixtures as a book in the air beside it ({@link FloatingBook}), its leaves turned like Hands-On
 * Vanilla's recipe book.
 * What is done goes to the server as the station's actions, which call the table's own methods.
 */
public final class ResearchBehavior implements StationBehavior {
    private static final ResourceLocation PARCHMENT = ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/misc/parchment3.png");
    private static final ResourceLocation HEX_IDLE = ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/gui/hex1.png");
    private static final ResourceLocation HEX_HOVER = ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/gui/hex2.png");
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/misc/white.png");
    private static final ResourceLocation UNKNOWN = ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/aspects/_unknown.png");

    /** The screen's sheet: 150 of its pixels wide, the hex grid's middle at its centre, hexes 9 of its pixels in size. */
    private static final float GUI_SHEET = 150.0F;
    private static final float GUI_HEX = 9.0F;
    /** The sheet on the table, scene pixels: its side, and the height it lies at over the node. */
    private static final float SHEET = 12.0F;
    /** The parchment fills the top left 150 of its image's 256 pixels. */
    private static final float PARCHMENT_UV = 150.0F / 256.0F;
    private static final float K = SHEET / GUI_SHEET;
    private static final float LIFT = 0.04F;
    /** The vials in the drawer's rack: four across, five deep; a page turns one row. */
    private static final int CELLS = 20;
    private static final int ROWS = 5;
    private static final int PAGE_STEP = 4;
    /** Mixtures written on one page of the guide; two pages to a spread. */
    private static final int HELP_ROWS = 5;
    private static final long COMBINE_COOLDOWN_MS = 200L;

    private int page;
    private final FloatingBook guide = new FloatingBook();
    private boolean helperOpen;
    private Holder<?> held;
    private Holder<?> select1;
    private Holder<?> select2;
    private long combineCooldownUntil;
    private List<Holder<?>> palette = List.of();
    private float clock;

    public ResearchBehavior(StationView view) {
    }

    // ---- Where things are.

    private static Player player() {
        return Minecraft.getInstance().player;
    }

    private static net.minecraft.world.level.block.entity.BlockEntity table(StationView view) {
        return net.handsonthaumaturge.station.Thaum.researchTableAt(view.level(), view.pos());
    }

    /** Everything laid out on the table moves with the sheet to the middle of the two-block table. */
    private static final String[] ON_TABLE = {"note", "note_take", "sheet", "helper", "book", "help_prev", "help_next", "page_prev",
            "page_next", "duplicate"};
    /** How far the middle of the whole table lies from the middle of the half the station stands on, scene pixels. */
    private static final Vector3f SHIFT = new Vector3f();

    /**
     * The table is two blocks long and the scene stands on the half that was used: the other half lies the way that half's
     * {@code facing} points. Seen from the front, the scene's +X is the front's clockwise side.
     */
    /** A world position in the scene's pixels: the inverse of {@code view.toWorld}, which only turns and moves. */
    private static Vector3f toScene(StationView view, net.minecraft.world.phys.Vec3 world) {
        net.minecraft.world.phys.Vec3 origin = view.toWorld(0.0F, 0.0F, 0.0F);
        net.minecraft.world.phys.Vec3 ax = view.toWorld(1.0F, 0.0F, 0.0F).subtract(origin);
        net.minecraft.world.phys.Vec3 az = view.toWorld(0.0F, 0.0F, 1.0F).subtract(origin);
        net.minecraft.world.phys.Vec3 d = world.subtract(origin);
        // ax and az are square to each other and of one length (a pixel): project on each.
        return new Vector3f((float) (d.dot(ax) / ax.lengthSqr()), 0.0F, (float) (d.dot(az) / az.lengthSqr()));
    }

    private static void centreOnTable(StationView view) {
        net.minecraft.core.BlockPos other = net.handsonthaumaturge.station.Thaum.otherHalf(view.level().getBlockState(view.pos()), view.pos());
        net.minecraft.core.Direction front = view.facing();
        SHIFT.set(0.0F);
        if (!other.equals(view.pos())) {
            net.minecraft.core.Direction towards = net.minecraft.core.Direction.getNearest(other.getX() - view.pos().getX(), 0,
                    other.getZ() - view.pos().getZ());
            if (towards == front.getClockWise()) {
                SHIFT.x = 8.0F;
            } else if (towards == front.getCounterClockWise()) {
                SHIFT.x = -8.0F;
            } else if (towards == front) {
                SHIFT.z = -8.0F;
            } else {
                SHIFT.z = 8.0F;
            }
        }
        for (String id : ON_TABLE) {
            view.node(id).ifPresent(node -> node.moveTo(SHIFT.x, SHIFT.y, SHIFT.z, 0.0F));
        }
        for (int i = 0; i < CELLS; i++) {
            view.node("aspect_" + i).ifPresent(node -> node.moveTo(SHIFT.x, SHIFT.y, SHIFT.z, 0.0F));
        }
        view.moveCamera(SHIFT.x, SHIFT.y, SHIFT.z);
        // The scribing tools go where Thaumaturge's renderer draws its inkwell, worked out with its own pose of the table top.
        net.minecraft.world.level.block.entity.BlockEntity table = table(view);
        if (table != null) {
            net.minecraft.world.level.block.state.BlockState state = table.getBlockState();
            net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
            for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
                if ("facing".equals(property.getName()) && state.getValue(property) instanceof net.minecraft.core.Direction d) {
                    facing = d;
                }
            }
            Vector3f local = ThaumClient.inkwell(facing);
            net.minecraft.world.phys.Vec3 world = new net.minecraft.world.phys.Vec3(table.getBlockPos().getX() + local.x,
                    table.getBlockPos().getY() + 1.0, table.getBlockPos().getZ() + local.z);
            Vector3f scene = toScene(view, world);
            view.node("tools").ifPresent(node -> node.moveTo(scene.x - 8.0F, 0.0F, scene.z - 8.0F, 0.0F));
            // The mixing tray and its button stand on the table just in front of the inkwell (authored round 8, 8).
            for (String id : new String[]{"select_1", "select_2", "combine"}) {
                view.node(id).ifPresent(node -> node.moveTo(scene.x - 8.0F, 0.0F, scene.z - 8.0F, 0.0F));
            }
        }
    }

    private static ItemStack slot(StationView view, String group) {
        return view.state().getItems(group).length == 0 ? ItemStack.EMPTY : view.state().getItems(group)[0];
    }

    private static Object note(StationView view) {
        return ThaumClient.note(slot(view, ResearchStation.NOTE));
    }

    private static boolean inkReady(StationView view) {
        ItemStack tools = slot(view, ResearchStation.TOOLS);
        return !tools.isEmpty() && tools.isDamageableItem() && tools.getDamageValue() < tools.getMaxDamage();
    }

    private int available(StationView view, Holder<?> aspect) {
        return ThaumClient.amount(player(), aspect) + ThaumClient.bonus(table(view), aspect);
    }

    private Holder<?> shown(int cell) {
        int index = page * PAGE_STEP + cell;
        return index < palette.size() ? palette.get(index) : null;
    }

    private int lastPage() {
        return Math.max(0, (palette.size() - (CELLS - PAGE_STEP)) / PAGE_STEP);
    }

    private List<Holder<?>> compounds() {
        List<Holder<?>> result = new ArrayList<>();
        for (Holder<?> aspect : palette) {
            if (ThaumClient.components(aspect).size() == 2) {
                result.add(aspect);
            }
        }
        return result;
    }

    // ---- The scene.

    @Override
    public void onSceneReady(StationView view) {
        if (!ThaumClient.ready()) {
            return;
        }
        centreOnTable(view);
        view.node("sheet").ifPresent(node -> node.setPainter((pose, buffers, light) -> paintSheet(view, pose, buffers)));
        for (int i = 0; i < CELLS; i++) {
            int cell = i;
            view.node("aspect_" + i).ifPresent(node -> node.setPainter((pose, buffers, light) -> paintCell(view, cell, pose, buffers)));
        }
        view.node("select_1").ifPresent(node -> node.setPainter((pose, buffers, light) -> paintTray(select1, pose, buffers)));
        view.node("select_2").ifPresent(node -> node.setPainter((pose, buffers, light) -> paintTray(select2, pose, buffers)));
        view.node("combine").ifPresent(node -> node.setPainter((pose, buffers, light) -> TextPainter.draw(pose, buffers,
                Component.translatable("handson_thaumaturge.research.combine"), 0.9F, 0.0F,
                select1 != null && select2 != null ? 0xFFFFE27A : 0xFFC8A870)));
        view.node("drawer").ifPresent(node -> node.setPainter((pose, buffers, light) -> paintDrawer(view, pose, buffers)));
        view.node("page_prev").ifPresent(node -> node.setPainter((pose, buffers, light) -> TextPainter.drawFlat(pose, buffers,
                Component.literal("<"), 1.6F, page > 0 ? 0xFFF0E0B0 : 0x50F0E0B0)));
        view.node("page_next").ifPresent(node -> node.setPainter((pose, buffers, light) -> TextPainter.drawFlat(pose, buffers,
                Component.literal(">"), 1.6F, page < lastPage() ? 0xFFF0E0B0 : 0x50F0E0B0)));
        view.node("duplicate").ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            if (canDuplicate(view)) {
                TextPainter.draw(pose, buffers, Component.translatable("handson_thaumaturge.research.copy"), 0.9F, 0.0F, 0xFFFFE8A0);
            }
        }));
        view.node("helper").ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            guide.renderClosed(pose, buffers);
            paintGuide(pose, buffers);
        }));
        view.node("held").ifPresent(node -> node.setPainter((pose, buffers, light) -> paintHeld(view, pose, buffers)));
    }

    @Override
    public void onOpen(StationView view) {
        centreOnTable(view);
        held = null;
        helperOpen = false;
    }

    @Override
    public void tick(StationView view) {
        if (ThaumClient.ready() && player() != null && view.isInUse()) {
            palette = ThaumClient.discoveredAspects(player());
            page = Math.min(page, lastPage());
            guide.jumpTo(Math.min(guide.spread(), lastHelperPage()));
        }
    }

    @Override
    public void frame(StationView view, float dt) {
        clock += dt;
        watchNote(view, dt);
        // One slot, two ways to it: the whole sheet's place while there is no note (put one down) or the note is done (take the
        // sealed scroll); only the seal in the sheet's corner while it is worked on, so the hexes stay free for the aspects.
        Object worked = note(view);
        boolean working = worked != null && !ThaumClient.complete(worked);
        boolean inUse = view.isInUse();
        view.node(ResearchStation.NOTE).ifPresent(node -> node.setEnabled(inUse && !working));
        view.node("note_take").ifPresent(node -> node.setEnabled(inUse && working));
        trail(view, dt);
        slideDrawer(view, dt);
        guide.frame(dt, helperOpen && view.isInUse());
        boolean using = view.isInUse() && ThaumClient.ready();
        for (int i = 0; i < CELLS; i++) {
            boolean there = using && shown(i) != null;
            view.node("aspect_" + i).ifPresent(node -> node.setEnabled(there));
        }
        view.node("book").ifPresent(node -> node.setVisible(using && (helperOpen || guide.shown())));
        // Hidden as well as disabled while the book is shut: a disabled node still stands in the way of clicks.
        view.node("help_prev").ifPresent(node -> {
            node.setEnabled(using && helperOpen);
            node.setVisible(using && helperOpen);
        });
        view.node("help_next").ifPresent(node -> {
            node.setEnabled(using && helperOpen);
            node.setVisible(using && helperOpen);
        });
        view.node("duplicate").ifPresent(node -> node.setEnabled(using && canDuplicate(view)));
    }

    // ---- Clicks.

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (!ThaumClient.ready() || player() == null) {
            return;
        }
        if (event instanceof SceneEvent.Hover hover) {
            hover(view, hover.node());
        } else if (event instanceof SceneEvent.Click click) {
            click(view, click.node(), click.button());
        }
    }

    private void hover(StationView view, String node) {
        if (node != null && node.startsWith("aspect_")) {
            Holder<?> aspect = shown(Integer.parseInt(node.substring(7)));
            if (aspect != null) {
                Component name = ThaumClient.name(aspect);
                if (!ThaumClient.primal(aspect)) {
                    List<Holder<?>> parts = ThaumClient.components(aspect);
                    if (parts.size() == 2) {
                        name = Component.empty().append(name).append(Component.literal("  (")).append(ThaumClient.name(parts.get(0)))
                                .append(" + ").append(ThaumClient.name(parts.get(1))).append(")");
                    }
                }
                view.showCaption(name, 0.0F);
                return;
            }
        }
        if (ResearchStation.TOOLS.equals(node) && slot(view, ResearchStation.TOOLS).isEmpty()) {
            view.showCaption(Component.translatable("gui.thaumaturge.research_table.slot_tools"), 0.0F);
        } else if (ResearchStation.NOTE.equals(node) && slot(view, ResearchStation.NOTE).isEmpty()) {
            view.showCaption(Component.translatable("gui.thaumaturge.research_table.slot_note"), 0.0F);
        } else if (ResearchStation.NOTE.equals(node)) {
            view.showCaption(Component.translatable("handson_thaumaturge.research.take_done"), 0.0F);
        } else if ("note_take".equals(node)) {
            view.showCaption(Component.translatable("handson_thaumaturge.research.take_note"), 0.0F);
        } else if ("helper".equals(node)) {
            view.showCaption(Component.translatable("gui.thaumaturge.research_table.helper"), 0.0F);
        } else {
            view.showCaption(null, 0.0F);
        }
    }

    private static final boolean TRACE = System.getProperty("handson.shots") != null;

    private void click(StationView view, String node, int button) {
        if (TRACE) {
            System.out.println("[shots] research table click on " + node);
        }
        if (node == null) {
            return;
        }
        if (node.startsWith("aspect_")) {
            Holder<?> aspect = shown(Integer.parseInt(node.substring(7)));
            if (aspect != null) {
                clickAspect(view, aspect, button);
            }
            return;
        }
        switch (node) {
            case "sheet" -> clickSheet(view, button);
            case "page_prev" -> turnPalette(view, -1);
            case "page_next" -> turnPalette(view, 1);
            case "help_prev" -> turnGuide(view, -1);
            case "help_next" -> turnGuide(view, 1);
            case "select_1" -> {
                select1 = button == 0 && held != null ? held : null;
                sound(view, SoundEvents.BOOK_PAGE_TURN, 0.4F);
            }
            case "select_2" -> {
                select2 = button == 0 && held != null ? held : null;
                sound(view, SoundEvents.BOOK_PAGE_TURN, 0.4F);
            }
            case "combine" -> combine(view);
            case "duplicate" -> {
                if (canDuplicate(view)) {
                    view.sendAction(ResearchStation.ACTION_COPY, new net.minecraft.nbt.CompoundTag());
                    sound(view, SoundEvents.BOOK_PUT, 0.6F);
                }
            }
            case "helper" -> {
                helperOpen = !helperOpen;
                sound(view, helperOpen ? SoundEvents.BOOK_PAGE_TURN : SoundEvents.BOOK_PUT, 0.6F);
            }
            default -> {
            }
        }
    }

    private void clickAspect(StationView view, Holder<?> aspect, int button) {
        if (button == 1) {
            // Into the mixing tray, as a click in the screen's palette.
            if (select1 == null) {
                select1 = aspect;
            } else {
                select2 = aspect;
            }
            sound(view, SoundEvents.BOOK_PAGE_TURN, 0.4F);
            return;
        }
        if (Screen.hasShiftDown() && !ThaumClient.primal(aspect) && ThaumClient.completed(player(), ThaumClient.MASTERY)) {
            List<Holder<?>> parts = ThaumClient.components(aspect);
            if (parts.size() == 2) {
                select1 = parts.get(0);
                select2 = parts.get(1);
                combine(view);
                return;
            }
        }
        if (held != null && held.equals(aspect)) {
            held = null;
        } else if (available(view, aspect) > 0) {
            held = aspect;
            sound(view, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.3F);
        }
    }

    private void clickSheet(StationView view, int button) {
        Object note = note(view);
        Object hex = hexUnderCursor(view);
        if (note == null || hex == null || ThaumClient.complete(note)) {
            return;
        }
        Object cell = cellAt(note, hex);
        if (cell == null) {
            return;
        }
        int type = ThaumClient.type(cell);
        if (button == 0 && held != null && type == ThaumClient.TYPE_BLANK) {
            if (!inkReady(view)) {
                view.showCaption(Component.translatable("gui.thaumaturge.research_table.no_ink.0"), 2.0F);
                return;
            }
            place(view, hex, held);
            sound(view, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 0.5F);
            if (available(view, held) <= 1) {
                held = null;
            }
        } else if (type == ThaumClient.TYPE_PLACED) {
            place(view, hex, null);
            sound(view, SoundEvents.BRUSH_GENERIC, 0.5F);
        } else if (button == 1) {
            held = null;
        }
    }

    private void combine(StationView view) {
        if (select1 == null || select2 == null || System.currentTimeMillis() < combineCooldownUntil) {
            return;
        }
        combineCooldownUntil = System.currentTimeMillis() + COMBINE_COOLDOWN_MS;
        Player player = player();
        var menu = table(view);
        // As the screen: what the pool lacks comes from the table's aids.
        boolean bonus1 = ThaumClient.amount(player, select1) <= 0 && ThaumClient.bonus(menu, select1) > 0;
        int second = ThaumClient.amount(player, select2) - (select1.equals(select2) && !bonus1 ? 1 : 0);
        boolean bonus2 = second <= 0 && ThaumClient.bonus(menu, select2) > 0;
        net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
        data.putString(ResearchStation.FIRST, ThaumClient.id(select1).toString());
        data.putString(ResearchStation.SECOND, ThaumClient.id(select2).toString());
        data.putBoolean(ResearchStation.BONUS_FIRST, bonus1);
        data.putBoolean(ResearchStation.BONUS_SECOND, bonus2);
        view.sendAction(ResearchStation.ACTION_COMBINE, data);
        sound(view, SoundEvents.BREWING_STAND_BREW, 0.4F);
    }

    private static void place(StationView view, Object hex, Holder<?> aspect) {
        net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
        data.putInt(ResearchStation.Q, ThaumClient.q(hex));
        data.putInt(ResearchStation.R, ThaumClient.r(hex));
        if (aspect != null) {
            data.putString(ResearchStation.ASPECT, ThaumClient.id(aspect).toString());
        }
        view.sendAction(ResearchStation.ACTION_PLACE, data);
    }

    private boolean canDuplicate(StationView view) {
        Object note = note(view);
        return note != null && ThaumClient.complete(note) && player() != null
                && ThaumClient.completed(player(), ThaumClient.DUPLICATION);
    }

    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        if (node == null) {
            return false;
        }
        int step = amount < 0 ? 1 : -1;
        if (node.startsWith("aspect_") || node.startsWith("page_")) {
            turnPalette(view, step);
            return true;
        }
        if (helperOpen && (node.startsWith("help_") || "book".equals(node))) {
            turnGuide(view, step);
            return true;
        }
        return false;
    }

    private void turnPalette(StationView view, int step) {
        int target = page + step;
        if (target >= 0 && target <= lastPage()) {
            page = target;
            sound(view, SoundEvents.BOOK_PAGE_TURN, 0.3F);
        }
    }

    /** The guide's last spread (two pages of {@link #HELP_ROWS} mixtures). */
    private int lastHelperPage() {
        return Math.max(0, (compounds().size() - 1) / (2 * HELP_ROWS));
    }

    private void turnGuide(StationView view, int step) {
        int target = guide.spread() + step;
        if (target >= 0 && target <= lastHelperPage()) {
            guide.turnTo(target);
            sound(view, SoundEvents.BOOK_PAGE_TURN, 0.5F);
        }
    }

    private static void sound(StationView view, SoundEvent sound, float volume) {
        view.playSound(sound, "sheet", volume, 1.0F);
    }

    // ---- The sheet's geometry: scene pixels on the table to the screen's sheet, and back.

    private static Vector3f sheetCentre(StationView view) {
        return new Vector3f(8.0F, 16.0F, 9.8F).add(SHIFT);
    }

    private static Object hexUnderCursor(StationView view) {
        Vector3f centre = sheetCentre(view);
        return view.cursorOn(centre.y + LIFT).map(point -> {
            float lx = point.x - centre.x;
            float lz = point.z - centre.z;
            if (Math.abs(lx) > SHEET / 2.0F || Math.abs(lz) > SHEET / 2.0F) {
                return null;
            }
            // The screen's x runs to the screen's right (-X), its y down the sheet towards the player (-Z).
            return ThaumClient.pixelToHex(-lx / K, -lz / K, GUI_HEX);
        }).orElse(null);
    }

    private static Object cellAt(Object note, Object hex) {
        for (Object cell : ThaumClient.cells(note)) {
            if (ThaumClient.hexOf(cell).equals(hex)) {
                return cell;
            }
        }
        return null;
    }

    private static float sx(Object hex) {
        return -ThaumClient.pixelX(hex, GUI_HEX) * K;
    }

    private static float sz(Object hex) {
        return -ThaumClient.pixelY(hex, GUI_HEX) * K;
    }

    // ---- Painting.

    private void paintSheet(StationView view, PoseStack pose, MultiBufferSource buffers) {
        Object note = note(view);
        if (note == null && view.isInUse()) {
            // No note: a pale sheet where one goes, breathing, so the place to put it is plain.
            int alpha = (int) (0x30 + 0x28 * (0.5F + 0.5F * (float) Math.sin(clock * 2.5F)));
            Quads.flatRect(pose, buffers, PARCHMENT, -SHEET / 2.0F, -SHEET / 2.0F, SHEET / 2.0F, SHEET / 2.0F, LIFT, PARCHMENT_UV,
                    alpha << 24 | 0xFFFFFF);
            return;
        }
        if (note == null || !ThaumClient.ready()) {
            return;
        }
        Player player = player();
        boolean complete = ThaumClient.complete(note);
        if (complete && sealTime >= 0) {
            paintSealing(pose, buffers, ThaumClient.noteColour(note));
            return;
        }
        Quads.flatRect(pose, buffers, PARCHMENT, -SHEET / 2.0F, -SHEET / 2.0F, SHEET / 2.0F, SHEET / 2.0F, LIFT, PARCHMENT_UV, 0xFFFFFFFF);
        Object hovered = view.isInUse() ? hexUnderCursor(view) : null;
        List<?> cells = ThaumClient.cells(note);
        Map<Object, Object> byHex = new HashMap<>();
        for (Object cell : cells) {
            byHex.put(ThaumClient.hexOf(cell), cell);
        }
        // The chains: a line from each written aspect to each neighbour it connects with.
        for (Object cell : cells) {
            if (!ThaumClient.active(cell)) {
                continue;
            }
            Object hex = ThaumClient.hexOf(cell);
            for (int direction = 0; direction < 3; direction++) {
                Object next = ThaumClient.neighbour(hex, direction);
                Object other = byHex.get(next);
                if (other == null || !ThaumClient.active(other)) {
                    continue;
                }
                if (ThaumClient.connects(ThaumClient.aspectOf(cell), ThaumClient.aspectOf(other), player)) {
                    Quads.flatLine(pose, buffers, WHITE, sx(hex), sz(hex), sx(next), sz(next), LIFT + 0.01F, 2.0F * K, 0x990099CC);
                }
            }
        }
        float half = 8.0F * K;
        for (Object cell : cells) {
            Object hex = ThaumClient.hexOf(cell);
            float x = sx(hex);
            float z = sz(hex);
            if (!complete && ThaumClient.type(cell) != ThaumClient.TYPE_ROOT) {
                boolean hover = hex.equals(hovered);
                Quads.flat(pose, buffers, hover ? HEX_HOVER : HEX_IDLE, x, LIFT + 0.005F, z, half, hover ? 0xFFFFFFFF : 0x40FFFFFF);
            }
            Holder<?> aspect = ThaumClient.aspectOf(cell);
            if (aspect != null) {
                if (ThaumClient.discovered(player, aspect)) {
                    Quads.flat(pose, buffers, ThaumClient.texture(aspect), x, LIFT + 0.02F, z, half, 0xFF000000 | ThaumClient.colour(aspect));
                } else {
                    Quads.flat(pose, buffers, UNKNOWN, x, LIFT + 0.02F, z, half, 0x80000000);
                }
            }
        }
        if (!inkReady(view)) {
            pose.pushPose();
            pose.translate(0.0F, LIFT + 0.03F, 0.6F);
            TextPainter.drawFlat(pose, buffers, Component.translatable("gui.thaumaturge.research_table.no_ink.0"), 0.7F, 0xFFFF5555);
            pose.translate(0.0F, 0.0F, -1.0F);
            TextPainter.drawFlat(pose, buffers, Component.translatable("gui.thaumaturge.research_table.no_ink.1"), 0.7F, 0xFFFF5555);
            pose.popPose();
        }
    }

    // ---- The note: rolled up in its slot, rolled up and sealed when its research is done.

    private static final ResourceLocation SCROLL = ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/misc/parchment3.png");
    /** Seconds since the note on the table was completed, while it is rolled up and sealed; negative when there is none to roll. */
    private float sealTime = -1.0F;
    private boolean sawIncomplete;
    /** A piece of the parchment with no seal drawn on it, for the rolls. */
    private static final float CLEAN_UV = 0.3F;
    private static final float ROLL_TIME = 1.0F;
    /** Dev only: the dev client's frame recorder slows the sealing down to film it (handson.shots.slowSeal). */
    private static final float SEAL_PACE = Boolean.getBoolean("handson.shots.slowSeal") ? 0.25F : 1.0F;
    private static final float SEAL_AT = 1.1F;
    private static final float SEAL_TIME = 0.4F;
    private boolean sealSounded;

    /** Follows the note: a note seen being worked on that comes out complete is rolled up and sealed on the table. */
    private void watchNote(StationView view, float dt) {
        Object note = note(view);
        if (note == null || !ThaumClient.complete(note)) {
            sawIncomplete = note != null;
            sealTime = -1.0F;
            sealSounded = false;
            return;
        }
        if (sealTime < 0) {
            // Done just now: roll it up as the player watches. Done before the table was opened: rolled and sealed already.
            sealTime = sawIncomplete ? 0.0F : SEAL_AT + SEAL_TIME;
            sealSounded = !sawIncomplete;
            if (sawIncomplete) {
                sound(view, SoundEvents.BOOK_PAGE_TURN, 0.7F);
            }
        } else {
            sealTime += dt * SEAL_PACE;
        }
        if (!sealSounded && sealTime >= SEAL_AT + SEAL_TIME) {
            sealSounded = true;
            view.playSound(SoundEvents.WOOD_PLACE, "sheet", 0.8F, 0.7F);
            view.spawnParticles(com.pockyl.diegesis.api.client.DiegesisParticles.SMOKE, new Vector3f(sheetCentre(view)).add(0.0F, 1.2F, 0.0F),
                    new Vector3f(0.0F, 2.0F, 0.0F), 0.6F, 4, 0);
        }
    }

    /** Thaumaturge's table model texture: its scroll is a box at (0, 0), its ribbon at (0, 4), of 128 by 64. */
    private static final ResourceLocation TABLE_MODEL = ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/entity/restable2.png");

    /**
     * Thaumaturge's scroll from its table model, its texture and its box: eight long and two thick along X, lying on Y = 0 round
     * Z = 0, the ribbon of the note's colour tied round its middle (the model's one by two by two, at 1.2 times).
     */
    private static void paintScroll(PoseStack pose, MultiBufferSource buffers, int colour) {
        Quads.modelBox(pose, buffers, TABLE_MODEL, -4.0F, 0.0F, -1.0F, 4.0F, 2.0F, 1.0F, 0.0F, 0.0F, 128.0F, 64.0F, 0xFFFFFFFF);
        pose.pushPose();
        pose.translate(0.0F, 1.0F, 0.0F);
        pose.scale(1.2F, 1.2F, 1.2F);
        Quads.modelBox(pose, buffers, TABLE_MODEL, -0.5F, -1.0F, -1.0F, 0.5F, 1.0F, 1.0F, 0.0F, 4.0F, 128.0F, 64.0F, 0xFF000000 | colour);
        pose.popPose();
    }

    /**
     * The completed note rolled up in the middle of the table: the parchment curls from the near edge into a roll, which ends
     * lying across the sheet's place; then a wax seal drops onto it.
     */
    private void paintSealing(PoseStack pose, MultiBufferSource buffers, int colour) {
        float roll = Math.min(1.0F, sealTime / ROLL_TIME);
        roll = roll * roll * (3 - 2 * roll);
        float half = SHEET / 2.0F;
        // The roll thickens and draws in to the size of Thaumaturge's scroll, and, rolled, is that scroll, tied with the note's ribbon.
        float radius = 0.2F + 0.8F * roll;
        float length = half * 0.85F + (4.0F - half * 0.85F) * roll;
        float edge = -half + roll * (half + 0.0F);
        if (roll < 1.0F) {
            // What is not yet rolled: cut from the sheet, not squeezed, its sides drawn in with the roll's ends, and gone into the
            // roll before the roll is done (nothing showing under it at the last).
            float far = half - roll * half;
            float flatFrom = edge + radius;
            if (far > flatFrom) {
                float side = half - (half - length) * Math.min(1.0F, roll * 1.25F);
                Quads.flatPiece(pose, buffers, PARCHMENT, -side, flatFrom, side, far, LIFT, half, PARCHMENT_UV, 0xFFFFFFFF);
            }
            Quads.rollX(pose, buffers, SCROLL, -length, length, LIFT + radius, edge, radius, CLEAN_UV, 0xFFFFFFFF);
        } else {
            pose.pushPose();
            pose.translate(0.0F, LIFT, edge);
            paintScroll(pose, buffers, colour);
            pose.popPose();
        }
        float seal = Math.max(0.0F, Math.min(1.0F, (sealTime - SEAL_AT) / SEAL_TIME));
        if (seal > 0) {
            // A flat blob of wax pressed down on the roll's top: it drops, squashes wide, and settles.
            float drop = (1 - seal) * (1 - seal) * 3.0F;
            float squash = 1.0F + 0.35F * (float) Math.sin(seal * Math.PI);
            float size = 0.85F * squash;
            // On the ribbon's knot, which stands a little proud of the roll.
            float top = LIFT + 2.2F + drop;
            // Round-ish, as a seal is: a long and a wide bar crossed make an octagon of wax, a smaller one its raised rim.
            sealDisc(pose, buffers, edge, top - 0.05F, top + 0.06F, size, 0xFF8E1010);
            sealDisc(pose, buffers, edge, top + 0.06F, top + 0.12F, size * 0.8F, 0xFFB82420);
            sealDisc(pose, buffers, edge, top + 0.12F, top + 0.14F, size * 0.4F, 0xFF7A0C0C);
        }
    }

    private static void sealDisc(PoseStack pose, MultiBufferSource buffers, float z, float y0, float y1, float r, int argb) {
        float cut = r * 0.42F;
        Quads.box(pose, buffers, WHITE, -r, y0, z - cut, r, y1, z + cut, 0.0F, 0.0F, 1.0F, 1.0F, argb);
        Quads.box(pose, buffers, WHITE, -cut, y0, z - r, cut, y1, z + r, 0.0F, 0.0F, 1.0F, 1.0F, argb);
        Quads.box(pose, buffers, WHITE, -r * 0.72F, y0, z - r * 0.72F, r * 0.72F, y1, z + r * 0.72F, 0.0F, 0.0F, 1.0F, 1.0F, argb);
    }

    // ---- The drawer of vials.

    private static final ResourceLocation PLANK = ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/block/plank_greatwood.png");
    private static final ResourceLocation DARK_WOOD = ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/block/base_wood_grain.png");
    private static final ResourceLocation BRASS = ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/block/metal_brass.png");
    /** How far the drawer slides out from under the tabletop towards the player, scene pixels, and how long it takes, seconds. */
    private static final float DRAWER_TRAVEL = 11.5F;
    private static final float DRAWER_TIME = 0.4F;
    private static final String[] IN_DRAWER = {"drawer", "page_prev", "page_next"};
    private float drawer;

    /** Slides the drawer (and everything in it) out while the table is worked, back in after. */
    private void slideDrawer(StationView view, float dt) {
        float target = view.isInUse() && ThaumClient.ready() ? 1.0F : 0.0F;
        float before = drawer;
        drawer = Math.max(0.0F, Math.min(1.0F, drawer + (target > drawer ? dt : -dt) / DRAWER_TIME));
        if (drawer == before && drawer != 1.0F && drawer != 0.0F) {
            return;
        }
        float eased = drawer * drawer * (3 - 2 * drawer);
        float out = -eased * DRAWER_TRAVEL;
        boolean shown = drawer > 0;
        for (String id : IN_DRAWER) {
            view.node(id).ifPresent(node -> {
                node.moveTo(SHIFT.x, SHIFT.y, SHIFT.z + out, 0.0F);
                node.setVisible(shown);
            });
        }
        for (int i = 0; i < CELLS; i++) {
            view.node("aspect_" + i).ifPresent(node -> {
                node.moveTo(SHIFT.x, SHIFT.y, SHIFT.z + out, 0.0F);
                node.setVisible(shown);
            });
        }
    }

    /** The drawer: a tray of greatwood with a dark front and a brass pull, and the rack the vials stand in. */
    private void paintDrawer(StationView view, PoseStack pose, MultiBufferSource buffers) {
        if (drawer <= 0) {
            return;
        }
        int wood = 0xFFFFFFFF;
        Quads.box(pose, buffers, PLANK, -4.0F, -0.6F, -4.8F, 4.0F, 0.0F, 4.8F, 0.0F, 0.0F, 0.5F, 0.6F, wood);
        Quads.box(pose, buffers, PLANK, -4.0F, 0.0F, 4.5F, 4.0F, 1.2F, 4.8F, 0.0F, 0.0F, 0.5F, 0.08F, wood);
        Quads.box(pose, buffers, PLANK, -4.0F, 0.0F, -4.8F, 4.0F, 1.2F, -4.5F, 0.0F, 0.0F, 0.5F, 0.08F, wood);
        Quads.box(pose, buffers, PLANK, -4.0F, 0.0F, -4.5F, -3.7F, 1.2F, 4.5F, 0.0F, 0.0F, 0.08F, 0.6F, wood);
        Quads.box(pose, buffers, PLANK, 3.7F, 0.0F, -4.5F, 4.0F, 1.2F, 4.5F, 0.0F, 0.0F, 0.08F, 0.6F, wood);
        // The front towards the player: the table's own drawer face (seven wide, two high) with its pull, which leaves with it.
        Quads.box(pose, buffers, DARK_WOOD, -3.5F, 0.0F, -5.4F, 3.5F, 2.0F, -4.8F, 0.0F, 0.0F, 0.45F, 0.15F, wood);
        Quads.box(pose, buffers, BRASS, -0.5F, 0.6F, -5.9F, 0.5F, 1.4F, -5.4F, 0.0F, 0.0F, 0.06F, 0.05F, wood);
        // The rack: a board at half the vials' height, a hole for each.
        for (float z = 3.8F; z >= -3.8F; z -= 1.9F) {
            Quads.box(pose, buffers, PLANK, -3.7F, 1.0F, z - 0.15F, 4.0F, 1.25F, z + 0.15F, 0.0F, 0.0F, 0.5F, 0.02F, wood);
        }
    }

    /** One vial in the rack: glass, the aspect's colour inside (fuller the more the player has), a cork, the aspect's sign on top. */
    private void paintCell(StationView view, int cell, PoseStack pose, MultiBufferSource buffers) {
        Holder<?> aspect = shown(cell);
        if (aspect == null || drawer <= 0) {
            return;
        }
        int pool = ThaumClient.amount(player(), aspect);
        int bonus = ThaumClient.bonus(table(view), aspect);
        boolean ready = pool + bonus > 0;
        int colour = ThaumClient.colour(aspect);
        float fill = Math.min(1.0F, 0.12F + (pool + bonus) / 120.0F);
        Quads.box(pose, buffers, WHITE, -0.28F, 0.05F, -0.28F, 0.28F, 0.05F + 2.2F * fill, 0.28F, 0.0F, 0.0F, 1.0F, 1.0F,
                (ready ? 0xE0000000 : 0x70000000) | colour);
        Quads.box(pose, buffers, WHITE, -0.38F, 0.0F, -0.38F, 0.38F, 2.5F, 0.38F, 0.0F, 0.0F, 1.0F, 1.0F, 0x40D8F0FF);
        Quads.box(pose, buffers, WHITE, -0.33F, 2.5F, -0.33F, 0.33F, 2.95F, 0.33F, 0.0F, 0.0F, 1.0F, 1.0F, 0xFF8A6438);
        if (aspect.equals(held)) {
            Quads.flat(pose, buffers, HEX_HOVER, 0.0F, 2.97F, 0.0F, 0.85F, 0xFFFFFFFF);
        }
        Quads.flat(pose, buffers, ThaumClient.texture(aspect), 0.0F, 3.0F, 0.0F, 0.62F, (ready ? 0xFF000000 : 0x60000000) | colour);
        pose.pushPose();
        pose.translate(0.0F, 0.35F, -0.62F);
        TextPainter.draw(pose, buffers, Component.literal(Integer.toString(pool) + (bonus > 0 ? "+" + bonus : "")), 0.6F, 0.0F,
                ready ? 0xFFFFFFFF : 0x80FFFFFF);
        pose.popPose();
    }


    private static void paintTray(Holder<?> aspect, PoseStack pose, MultiBufferSource buffers) {
        Quads.upright(pose, buffers, HEX_HOVER, 0.0F, 0.0F, 0.02F, 1.0F, 0xFFF0C860);
        if (aspect != null) {
            Quads.upright(pose, buffers, ThaumClient.texture(aspect), 0.0F, 0.0F, -0.01F, 0.9F, 0xFF000000 | ThaumClient.colour(aspect));
        }
    }

    /** The aspect taken up, following the cursor over the table. */
    static final ResourceLocation SPECK = ResourceLocation.fromNamespaceAndPath("handson_thaumaturge", "aspect_speck");

    /** Where the held aspect was when the trail last left a speck, and how long ago, seconds. */
    private Vector3f trailFrom;
    private float trailWait;

    /**
     * A few specks of the held aspect's colour left behind the cursor while it is carried over the table: one now and then while
     * it moves (at most fifteen a second), small, drifting up and gone in a moment.
     */
    private void trail(StationView view, float dt) {
        trailWait -= dt;
        if (held == null || !view.isInUse()) {
            trailFrom = null;
            return;
        }
        Vector3f centre = sheetCentre(view);
        view.cursorOn(centre.y + 0.6F).ifPresent(point -> {
            boolean moved = trailFrom == null || trailFrom.distance(point) > 0.25F;
            if (!moved || trailWait > 0) {
                return;
            }
            trailFrom = new Vector3f(point);
            trailWait = 1.0F / 15.0F;
            if (TRACE) {
                System.out.println("[shots] trail speck at " + point);
            }
            // Diegesis's own scene particles: world particles are not drawn while a station is worked.
            view.spawnParticles(SPECK, new Vector3f(point.x, point.y + 0.2F, point.z), new Vector3f(0.0F, 2.0F, 0.0F), 0.5F, 2,
                    0xFF000000 | ThaumClient.colour(held));
        });
    }

    private void paintHeld(StationView view, PoseStack pose, MultiBufferSource buffers) {
        if (held == null || !view.isInUse()) {
            return;
        }
        Vector3f centre = sheetCentre(view);
        view.cursorOn(centre.y + 0.6F).ifPresent(point -> {
            float bob = 0.1F * (float) Math.sin(clock * 3.0F);
            Quads.flat(pose, buffers, ThaumClient.texture(held), point.x, point.y + bob, point.z, 0.75F, 0xE0000000 | ThaumClient.colour(held));
        });
    }

    /**
     * The guide to mixtures: the recipe book lying open on the table ({@link FloatingBook}), as Hands-On Vanilla's crafting table
     * shows its recipe book. Each page lists {@link #HELP_ROWS} known compounds, "part + part = compound"; the leaves turn.
     */
    private void paintGuide(PoseStack pose, MultiBufferSource buffers) {
        if (player() == null) {
            return;
        }
        guide.render(pose, buffers, this::paintGuidePage, 0.25F * (float) Math.sin(clock * 1.6F));
    }

    private void paintGuidePage(PoseStack pose, MultiBufferSource buffers, int pageNumber) {
        List<Holder<?>> compounds = compounds();
        int start = pageNumber * HELP_ROWS;
        float rowHeight = 1.3F;
        for (int row = 0; row < HELP_ROWS && start + row < compounds.size(); row++) {
            Holder<?> compound = compounds.get(start + row);
            List<Holder<?>> parts = ThaumClient.components(compound);
            float y = 2.7F - row * rowHeight;
            guideTag(pose, buffers, parts.get(0), 2.0F, y);
            pose.pushPose();
            pose.translate(1.0F, y, 0.0F);
            TextPainter.draw(pose, buffers, Component.literal("+"), 0.6F, 0.0F, 0xFF3A2A1A);
            pose.popPose();
            guideTag(pose, buffers, parts.get(1), 0.0F, y);
            pose.pushPose();
            pose.translate(-1.0F, y, 0.0F);
            TextPainter.draw(pose, buffers, Component.literal("="), 0.6F, 0.0F, 0xFF3A2A1A);
            pose.popPose();
            guideTag(pose, buffers, compound, -2.0F, y);
        }
        // The page's foot, as the recipe book's: an arrow back at the left page's outer corner, on at the right page's.
        float outer = pageNumber % 2 == 0 ? FloatingBook.PAGE_WIDE / 2 - 0.55F : -(FloatingBook.PAGE_WIDE / 2 - 0.55F);
        boolean can = pageNumber % 2 == 0 ? guide.spread() > 0 : guide.spread() < lastHelperPage();
        pose.pushPose();
        pose.translate(outer, -3.25F, 0.0F);
        TextPainter.draw(pose, buffers, Component.literal(pageNumber % 2 == 0 ? "<" : ">"), 0.8F, 0.0F, can ? 0xFF3A2A1A : 0x403A2A1A);
        pose.popPose();
        int pages = Math.max(1, (compounds.size() + HELP_ROWS - 1) / HELP_ROWS);
        if (pageNumber < pages) {
            pose.pushPose();
            pose.translate(0.0F, -3.3F, 0.0F);
            TextPainter.draw(pose, buffers, Component.literal(String.valueOf(pageNumber + 1)), 0.5F, 0.0F, 0xFF6A5A3A);
            pose.popPose();
        }
    }

    /**
     * A small hexagon behind each aspect in the guide, ink-rimmed: dark behind a pale aspect, light behind a dark one, so every
     * icon stands out from the paper.
     */
    private static final ResourceLocation GUIDE_HEX = ResourceLocation.fromNamespaceAndPath("handson_thaumaturge", "textures/gui/aspect_hex.png");

    private static void guideTag(PoseStack pose, MultiBufferSource buffers, Holder<?> aspect, float x, float y) {
        int colour = ThaumClient.colour(aspect);
        float bright = (0.299F * (colour >> 16 & 0xFF) + 0.587F * (colour >> 8 & 0xFF) + 0.114F * (colour & 0xFF)) / 255.0F;
        Quads.upright(pose, buffers, GUIDE_HEX, x, y, 0.01F, 0.5F, bright > 0.45F ? 0xFF4A3828 : 0xFFE4D4AE);
        if (ThaumClient.discovered(player(), aspect)) {
            Quads.upright(pose, buffers, ThaumClient.texture(aspect), x, y, -0.01F, 0.38F, 0xFF000000 | ThaumClient.colour(aspect));
        } else {
            Quads.upright(pose, buffers, UNKNOWN, x, y, -0.01F, 0.38F, 0xA0FFFFFF);
        }
    }
}
