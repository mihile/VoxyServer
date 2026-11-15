package com.dripps.voxyserver.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class VoxyConfigScreen extends Screen {
    private final Screen parent;
    private boolean enabled;
    private EditBox radius;
    private EditBox sections;
    private int maxRadius;
    private int maxSections;

    public VoxyConfigScreen(Screen parent) {
        super(Component.literal("VoxyServer Client Settings"));
        this.parent = parent;
        this.enabled = ClientLodSettings.isEnabled();
    }

    @Override protected void init() {
        int left = width / 2 - 120;
        boolean active = ClientLodSettings.hasActiveServerProfile();
        maxRadius = Math.max(0, ClientLodSettings.getServerMaxRadius());
        maxSections = Math.max(0, ClientLodSettings.getServerMaxSections());
        if (maxRadius == 0) maxRadius = 256;
        if (maxSections == 0) maxSections = 50;
        Button toggle = addRenderableWidget(Button.builder(toggleText(), b -> {
            enabled = !enabled; b.setMessage(toggleText());
        }).bounds(left, 55, 240, 20).build());
        toggle.active = active;
        radius = addRenderableWidget(new EditBox(font, left, 103, 240, 20, Component.literal("LOD stream radius")));
        radius.setFilter(s -> s.isEmpty() || s.matches("[0-9]{1,6}"));
        radius.setValue(Integer.toString(ClientLodSettings.getPreferredRadius()));
        radius.setEditable(active);
        sections = addRenderableWidget(new EditBox(font, left, 151, 240, 20, Component.literal("Maximum sections per tick")));
        sections.setFilter(s -> s.isEmpty() || s.matches("[0-9]{1,6}"));
        sections.setValue(Integer.toString(ClientLodSettings.getPreferredMaxSections()));
        sections.setEditable(active);
        Button save = addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(left, 190, 116, 20).build());
        save.active = active;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(left + 124, 190, 116, 20).build());
    }
    private Component toggleText() { return Component.literal("LOD streaming: " + (enabled ? "ON" : "OFF")); }
    private static int value(EditBox box, int max) {
        try { return Math.min(max, Integer.parseInt(box.getValue())); }
        catch (NumberFormatException ignored) { return 0; }
    }
    private void save() {
        ClientLodSettings.setEnabled(enabled);
        ClientLodSettings.setPreferredRadius(value(radius, maxRadius));
        ClientLodSettings.setPreferredMaxSections(value(sections, maxSections));
        ClientLodSettings.saveAndSendPreferences();
        onClose();
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xffffff);
        int left = width / 2 - 120;
        graphics.drawString(font, "Stream radius (0 = server default, max " + maxRadius + ")", left, 91, 0xffffff);
        graphics.drawString(font, "Sections per tick (0 = default, max " + maxSections + ")", left, 139, 0xffffff);
        if (!ClientLodSettings.hasActiveServerProfile())
            graphics.drawCenteredString(font, "Connect to a server to change its settings", width / 2, 221, 0xffaaaa);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
