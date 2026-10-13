package com.reachingrandom.mc.poi.gui;

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
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Asks for a name (a new world, a new group, renaming a group), hands it back and
 * returns to the screen it came from. An optional extra button sits under the field,
 * for a second action on the same thing, like deleting the group being renamed.
 */
class NamePromptScreen extends Screen {

    private static final int MAX_NAME_LENGTH = 64;

    private final Screen parent;
    private final Consumer<String> onConfirm;
    private final LinearLayout layout = LinearLayout.vertical().spacing(6);
    private final EditBox nameEdit;
    private final Button confirmButton;

    /**
     * @param note        a gray line under the field, or null
     * @param extraLabel  label of the extra button, or null for none
     * @param extraAction what it does; it's responsible for leaving this screen
     */
    NamePromptScreen(Screen parent, String title, String initialName, String note, String confirmLabel,
                     Consumer<String> onConfirm, String extraLabel, Runnable extraAction) {
        super(Component.literal(title));
        this.parent = parent;
        this.onConfirm = onConfirm;

        Font font = Minecraft.getInstance().font;
        layout.addChild(new StringWidget(this.title, font));
        nameEdit = layout.addChild(new EditBox(font, 200, 20, this.title));
        nameEdit.setMaxLength(MAX_NAME_LENGTH);
        nameEdit.setValue(initialName != null ? initialName : "");
        nameEdit.setHint(Component.literal("Name").withStyle(ChatFormatting.DARK_GRAY));
        if (note != null) layout.addChild(new StringWidget(Component.literal(note).withStyle(ChatFormatting.GRAY), font));

        LinearLayout buttons = LinearLayout.horizontal().spacing(4);
        confirmButton = buttons.addChild(Button.builder(Component.literal(confirmLabel), b -> confirm()).width(98).build());
        buttons.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).width(98).build());
        layout.addChild(buttons);
        if (extraLabel != null) {
            layout.addChild(Button.builder(Component.literal(extraLabel), b -> extraAction.run()).width(200).build());
        }
        confirmButton.active = !nameEdit.getValue().isBlank();
        nameEdit.setResponder(value -> confirmButton.active = !value.isBlank());
    }

    NamePromptScreen(Screen parent, String title, String initialName, String note, String confirmLabel,
                     Consumer<String> onConfirm) {
        this(parent, title, initialName, note, confirmLabel, onConfirm, null, null);
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
            confirm();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void confirm() {
        String name = nameEdit.getValue().trim();
        if (name.isEmpty()) return;
        onClose();
        onConfirm.accept(name);
    }
}
