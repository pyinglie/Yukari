package club.nankai.mc.yukari.client.screen;

import club.nankai.mc.yukari.net.payload.OpenSelectionS2CPayload;
import club.nankai.mc.yukari.net.payload.SelectModC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Custom rendering + re-render via post event (to avoid being blurred by third-party mods).
 */
@OnlyIn(Dist.CLIENT)
public class ModSelectScreen extends Screen {

    private static final int LIST_TOP_PAD = 42;
    private static final int FOOTER_HEIGHT = 50;
    private static final int ENTRY_HEIGHT = 20;
    private static final int LIST_WIDTH_PERCENT = 70;
    private static final int ENTRY_PADDING_X = 6;
    private final List<OpenSelectionS2CPayload.Entry> mods;
    private final Set<String> occupied;
    private final boolean allowMultiple;
    private final List<Row> rows = new ArrayList<>();
    private String selectedModId = null;
    private int scrollOffsetPx = 0;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private Button confirmBtn;
    private Button cancelBtn;
    private Button spectateBtn;

    public ModSelectScreen(List<OpenSelectionS2CPayload.Entry> mods,
                           List<String> occupied,
                           boolean allowMultiple) {
        super(Component.translatable("yukari.select.title"));
        this.mods = mods;
        this.occupied = new HashSet<>(occupied);
        this.allowMultiple = allowMultiple;
    }

    @Override
    protected void init() {
        rows.clear();
        for (OpenSelectionS2CPayload.Entry e : mods) {
            boolean occ = occupied.contains(e.modId());
            rows.add(new Row(e.modId(), e.displayName(), occ && !allowMultiple));
        }
        int listWidth = this.width * LIST_WIDTH_PERCENT / 100;
        listLeft = (this.width - listWidth) / 2;
        listRight = listLeft + listWidth;
        listTop = LIST_TOP_PAD;
        listBottom = this.height - FOOTER_HEIGHT;

        int btnWidth = 90;
        int spacing = 10;
        int totalWidth = btnWidth * 3 + spacing * 2;
        int startX = (this.width - totalWidth) / 2;
        int y = this.height - FOOTER_HEIGHT + 15;

        confirmBtn = Button.builder(Component.translatable("yukari.select.confirm"),
                b -> {
                    if (selectedModId != null) {
                        sendSelection(selectedModId);
                        onClose();
                    }
                }).pos(startX, y).size(btnWidth, 20).build();
        addRenderableWidget(confirmBtn);

        cancelBtn = Button.builder(Component.translatable("yukari.select.cancel"),
                        b -> onClose())
                .pos(startX + btnWidth + spacing, y).size(btnWidth, 20).build();
        addRenderableWidget(cancelBtn);

        spectateBtn = Button.builder(Component.translatable("yukari.select.spectate"),
                        b -> {
                            sendSelection(SelectModC2SPayload.SPECTATE);
                            onClose();
                        })
                .pos(startX + (btnWidth + spacing) * 2, y).size(btnWidth, 20).build();
        addRenderableWidget(spectateBtn);

        updateButtons();
    }

    private void updateButtons() {
        if (confirmBtn != null) confirmBtn.active = selectedModId != null;
    }

    private void sendSelection(String id) {
        if (Minecraft.getInstance().getConnection() != null) {
            Minecraft.getInstance().getConnection().send(new SelectModC2SPayload(id));
        }
    }

    private int contentHeight() {
        return rows.size() * ENTRY_HEIGHT;
    }

    private int visibleListHeight() {
        return listBottom - listTop;
    }

    private void clampScroll() {
        int max = Math.max(0, contentHeight() - visibleListHeight());
        if (scrollOffsetPx < 0) scrollOffsetPx = 0;
        if (scrollOffsetPx > max) scrollOffsetPx = max;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (mouseY >= listTop && mouseY <= listBottom && mouseX >= listLeft && mouseX <= listRight) {
            int step = (int) (dy * 16);
            scrollOffsetPx -= step;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY >= listTop && mouseY <= listBottom && mouseX >= listLeft && mouseX <= listRight) {
            int relY = (int) (mouseY - listTop + scrollOffsetPx);
            int index = relY / ENTRY_HEIGHT;
            if (index >= 0 && index < rows.size()) {
                Row r = rows.get(index);
                if (!r.disabled) {
                    selectedModId = r.modId;
                    updateButtons();
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // Main render (may be blurred by other mods; we redraw afterwards)
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        drawBaseAndList(gfx, mouseX, mouseY);
        super.render(gfx, mouseX, mouseY, partialTick);
        drawSpectatorHint(gfx);
    }

    // Post-blur repaint (called from event listener)
    public void repaintAfterBlur(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        drawBaseAndList(gfx, mouseX, mouseY);
        drawSpectatorHint(gfx);
    }

    private void drawBaseAndList(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.fill(0, 0, this.width, this.height, 0xFF101018); // fully opaque background
        gfx.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        if (!allowMultiple) {
            gfx.drawCenteredString(this.font, Component.translatable("yukari.select.gray_hint"), this.width / 2, 24, 0xAAAAAA);
        } else {
            gfx.drawCenteredString(this.font, Component.translatable("yukari.select.allow_multi_hint"), this.width / 2, 24, 0xAAAAAA);
        }
        gfx.fill(listLeft - 2, listTop - 2, listRight + 2, listBottom + 2, 0xFF202028);

        int startIndex = scrollOffsetPx / ENTRY_HEIGHT;
        int yStart = listTop - (scrollOffsetPx % ENTRY_HEIGHT);

        for (int rowIdx = startIndex; rowIdx < rows.size(); rowIdx++) {
            int y = yStart + (rowIdx - startIndex) * ENTRY_HEIGHT;
            if (y > listBottom) break;
            Row row = rows.get(rowIdx);
            boolean hovered = mouseX >= listLeft && mouseX <= listRight && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
            boolean selected = selectedModId != null && selectedModId.equals(row.modId);
            int entryBg = 0;
            if (selected) entryBg = 0x6633AAFF;
            else if (hovered && !row.disabled) entryBg = 0x3322AAFF;
            else if (row.disabled) entryBg = 0x33101010;
            if (entryBg != 0) gfx.fill(listLeft, y, listRight, y + ENTRY_HEIGHT, entryBg);

            int color = row.disabled ? 0x777777 : 0xFFFFFF;
            String text = row.display + " (" + row.modId + ")";
            if (row.disabled) text += Component.translatable("yukari.select.occupied_suffix").getString();
            gfx.drawString(this.font, text, listLeft + ENTRY_PADDING_X, y + (ENTRY_HEIGHT - 8) / 2, color);
        }
        drawScrollbar(gfx);
    }

    private void drawSpectatorHint(GuiGraphics gfx) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.isSpectator()) {
            gfx.drawCenteredString(this.font,
                    Component.translatable("yukari.select.spectator_hint"),
                    this.width / 2, listBottom + 6, 0xBBBBBB);
        }
    }

    private void drawScrollbar(GuiGraphics gfx) {
        int total = contentHeight();
        int visible = visibleListHeight();
        if (total <= visible) return;
        int barHeight = Math.max(24, (int) (visible * (visible / (float) total)));
        int maxScroll = total - visible;
        int barY = listTop + (int) ((scrollOffsetPx / (float) maxScroll) * (visible - barHeight));
        int barX1 = listRight - 6;
        int barX2 = listRight - 2;
        gfx.fill(barX1, listTop, barX2, listBottom, 0x44000000);
        gfx.fill(barX1, barY, barX2, barY + barHeight, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class Row {
        final String modId;
        final String display;
        final boolean disabled;

        Row(String id, String disp, boolean disabled) {
            this.modId = id;
            this.display = disp;
            this.disabled = disabled;
        }
    }
}

