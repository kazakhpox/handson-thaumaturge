package net.handsonthaumaturge.client;

/** Whether the research table being drawn right now is worked as a station (its renderer then draws only the inkwell and quill). */
public final class TableRendering {
    public static final ThreadLocal<Boolean> WORKED = ThreadLocal.withInitial(() -> false);

    private TableRendering() {
    }
}
