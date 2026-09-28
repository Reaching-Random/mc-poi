package com.reachingrandom.mc.poi.campsite;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Sign-style prompt for naming a campfire. Done saves the name (blank means
 * "no campsite"); Cancel or Esc changes nothing.
 */
public class CampsiteNameScreen extends Screen {

    private static final int MAX_NAME_LENGTH = 64;

    private final String dimension;
    private final BlockPos pos;
    private final LinearLayout layout = LinearLayout.vertical().spacing(6);
    private final EditBox nameEdit;

    public CampsiteNameScreen(String dimension, BlockPos pos, String initialName) {
        super(Component.literal(initialName == null ? "Name this campsite" : "Rename campsite"));
        this.dimension = dimension;
        this.pos = pos;

        Font font = Minecraft.getInstance().font;
        layout.addChild(new StringWidget(title, font));
        nameEdit = layout.addChild(new EditBox(font, 200, 20, title));
        nameEdit.setMaxLength(MAX_NAME_LENGTH);
        nameEdit.setHint(Component.literal(initialName == null
                ? "Leave blank to skip"
                : "Leave blank to remove the campsite").withStyle(ChatFormatting.DARK_GRAY));
        if (initialName != null) nameEdit.setValue(initialName);

        LinearLayout buttons = LinearLayout.horizontal().spacing(4);
        buttons.addChild(Button.builder(CommonComponents.GUI_DONE, b -> done()).width(98).build());
        buttons.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).width(98).build());
        layout.addChild(buttons);
    }

    @Override
    protected void init() {
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(nameEdit);
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, getRectangle());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (nameEdit.isFocused() && event.isConfirmation()) {
            done();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void done() {
        String name = nameEdit.getValue();
        onClose();
        CampsiteTracker.saveName(dimension, pos, name);
    }
}
