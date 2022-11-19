package arm32x.minecraft.commandblockide.client.gui.screen;

import arm32x.minecraft.commandblockide.client.Dirtyable;
import arm32x.minecraft.commandblockide.client.gui.ToolbarSeparator;
import arm32x.minecraft.commandblockide.client.gui.button.SimpleIconButton;
import arm32x.minecraft.commandblockide.client.gui.editor.CommandEditor;
import arm32x.minecraft.commandblockide.client.storage.MultilineCommandStorage;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public abstract class CommandIDEScreen<E extends CommandEditor> extends Screen implements Dirtyable {
	protected final List<E> editors = new ArrayList<>();
	protected int combinedEditorHeight = Integer.MAX_VALUE;
	private boolean initialized = false;

	private SimpleIconButton saveButton;

	private int scrollOffset = 0, maxScrollOffset = Integer.MAX_VALUE;
	public static final double SCROLL_SENSITIVITY = 50.0;

	private boolean draggingScrollbar = false;
	private double mouseYAtScrollbarDragStart = 0;
	private int scrollOffsetAtScrollbarDragStart = 0;

	protected @Nullable OrderedText statusText = null;
	private int statusTextX = 0;

	public CommandIDEScreen() {
		super(Text.empty());
	}

	@Override
	protected void init() {
		assert client != null;
		client.keyboard.setRepeatEvents(true);

		statusTextX = addToolbarWidgets(List.of(
			saveButton = new SimpleIconButton(0, 0, "save", this, List.of(Text.translatable("commandBlockIDE.save")), b -> save()),
			new ToolbarSeparator()
		));

		// Done button
		addDrawableChild(new ButtonWidget(width - 216, height - 28, 100, 20, ScreenTexts.DONE, b -> { save(); close(); }));
		// Cancel button
		addDrawableChild(new ButtonWidget(width - 108, height - 28, 100, 20, ScreenTexts.CANCEL, b -> close()));

		if (!initialized) {
			firstInit();
			initialized = true;
		} else {
			initAfterFirst();
		}
	}

	protected void firstInit() {
		setLoaded(false);

		// Make sure 'combinedEditorHeight' is set.
		repositionEditors();
		maxScrollOffset = Math.max(combinedEditorHeight - (height - 50), 0);
		// Make sure the scroll offset is in range.
		setScrollOffset(getScrollOffset());

		MultilineCommandStorage.load();
	}

	protected void initAfterFirst() {
		for (CommandEditor editor : editors) {
			addSelectableChild(editor);
			editor.setWidth(width - 16);
		}

		maxScrollOffset = Math.max(combinedEditorHeight - (height - 50), 0);
		Element element = getFocused();
		if (element instanceof CommandEditor) {
			setFocusedEditor((CommandEditor)element);
		}
	}

	protected void addEditor(E editor) {
		editor.setHeightChangedListener(height -> repositionEditors());
		editors.add(editor);
		addSelectableChild(editor);
	}

	public void save() {
		MultilineCommandStorage.save();
	}

	@Override
	public boolean shouldCloseOnEsc() { return false; }

	@Override
	public void close() {
		assert client != null;
		client.keyboard.setRepeatEvents(false);
		super.close();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (handleSpecialKey(keyCode)) {
			return true;
		} else if (getFocused() != null) {
			return getFocused().keyPressed(keyCode, scanCode, modifiers);
		} else {
			// Bypass the special cases for Escape and Tab added in the Screen
			// class to maintain full control over keyboard shortcuts.
			return false;
		}
	}

	private boolean handleSpecialKey(int keyCode) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			Element focused = getFocused();
			if (focused == null) {
				// TODO: Warn about unsaved changes.
				close();
				return true;
			}
			if (focused instanceof CommandEditor editor) {
				if (editor.isSuggestorActive()) {
					editor.setSuggestorActive(false);
					return true;
				} else {
					editor.setFocused(false);
				}
			}
			setFocused(null);
			return true;
		} else if (keyCode == GLFW.GLFW_KEY_UP && Screen.hasControlDown()) {
			return changeFocus(false);
		} else if (keyCode == GLFW.GLFW_KEY_DOWN && Screen.hasControlDown()) {
			return changeFocus(true);
		} else if (keyCode == GLFW.GLFW_KEY_S && Screen.hasControlDown()) {
			saveButton.playDownSound(MinecraftClient.getInstance().getSoundManager());
			save();
			return true;
		} else {
			return false;
		}
	}

	// This must be overridden because the superclass' implementation
	// short-circuits on success, which breaks text field focus.
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseX > width - 4 && button == 0) {
			int virtualHeight = maxScrollOffset + height;
			int scrollbarHeight = Math.round((float)height / virtualHeight * height);
			int scrollbarPosition = Math.round((float)getScrollOffset() / height * scrollbarHeight);

			if (mouseY >= scrollbarPosition && mouseY <= scrollbarPosition + scrollbarHeight) {
				setDragging(true);
				draggingScrollbar = true;
				mouseYAtScrollbarDragStart = mouseY;
				scrollOffsetAtScrollbarDragStart = getScrollOffset();
			} else if (mouseY < scrollbarPosition) {
				setScrollOffset((int)Math.round(getScrollOffset() - SCROLL_SENSITIVITY * 5));
			} else if (mouseY > scrollbarPosition + scrollbarHeight) {
				setScrollOffset((int)Math.round(getScrollOffset() + SCROLL_SENSITIVITY * 5));
			}
			return true;
		}

		Element focusedChild = null;
		for (Element child : children()) {
			if (child.mouseClicked(mouseX, mouseY, button) && focusedChild == null) {
				focusedChild = child;
			}
		}
		setFocused(focusedChild);
		if (button == 0) {
			setDragging(true);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && draggingScrollbar) {
			draggingScrollbar = false;
			return true;
		} else {
			return super.mouseReleased(mouseX, mouseY, button);
		}
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == 0 && draggingScrollbar) {
			int virtualHeight = maxScrollOffset + height;
			int scrollbarHeight = Math.round((float)height / virtualHeight * height);
			int scrollOffsetDelta = (int)Math.round((mouseY - mouseYAtScrollbarDragStart) / scrollbarHeight * height);
			setScrollOffset(scrollOffsetAtScrollbarDragStart + scrollOffsetDelta);
			return true;
		} else {
			return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		for (CommandEditor editor : editors) {
			if (editor.mouseScrolled(mouseX, mouseY, amount)) return true;
		}
		if (maxScrollOffset != 0 && amount != 0 && mouseY < height - 36 && !Screen.hasShiftDown()) {
			setScrollOffset(getScrollOffset() - (int)Math.round(amount * SCROLL_SENSITIVITY));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	public int getScrollOffset() {
		return scrollOffset;
	}

	public void setScrollOffset(int offset) {
		scrollOffset = MathHelper.clamp(offset, 0, maxScrollOffset);
		repositionEditors();
	}

	protected void repositionEditors() {
		int heightAccumulator = 8;
		for (CommandEditor editor : editors) {
			editor.setY(heightAccumulator - scrollOffset);
			heightAccumulator += editor.getHeight() + 4;
		}
		combinedEditorHeight = heightAccumulator - 12;
		// This potentially leaves the scroll offset at an out-of-range value
		// to avoid scrolling without the user intending to. The scroll offset
		// will be clamped when the user next scrolls.
		maxScrollOffset = Math.max(combinedEditorHeight - (height - 50), 0);
	}

	/**
	 * Adds the provided toolbar widgets to the screen in order.
	 * @param widgets The list of widgets to add.
	 * @return The X coordinate at which the next widget would have been placed.
	 */
	private int addToolbarWidgets(List<ClickableWidget> widgets) {
		int x = 8;
		for (ClickableWidget widget : widgets) {
			widget.x = x;
			widget.y = height - 28;
			x += widget.getWidth() + 4;
			addDrawableChild(widget);
		}
		return x;
	}

	@Override
	public boolean changeFocus(boolean lookForwards) {
		Element element = getFocused();
		if (element != null) {
			for (int index = 0; index < editors.size(); index++) {
				if (element instanceof CommandEditor && element.equals(editors.get(index))) {
					CommandEditor editor;
					do {
						index = index + (lookForwards ? 1 : -1);
						if (index < 0) {
							index = editors.size() - 1;
						} else if (index >= editors.size()) {
							index = 0;
						}
						editor = editors.get(index);
					} while (!editor.isLoaded());
					((CommandEditor)element).setFocused(false);
					setFocusedEditor(editor);
					return true;
				}
			}
		}
		CommandEditor editor = editors.get(0);
		setFocusedEditor(editor);
		return true;
	}

	public void setFocusedEditor(CommandEditor editor) {
		setFocused(editor);
		editor.setFocused(true);

		// Ensure the focused editor is on-screen
		repositionEditors();
		int top = editor.getY() + scrollOffset;
		int bottom = top + editor.getHeight();
		setScrollOffset(MathHelper.clamp(getScrollOffset(), bottom - height + 36, top - 8));
	}

	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		renderBackground(matrices);

		for (CommandEditor editor : editors) {
			editor.render(matrices, mouseX, mouseY, delta);
		}
		for (CommandEditor editor : editors) {
			// This is done in a separate loop to ensure it's rendered on top.
			editor.renderSuggestions(matrices, mouseX, mouseY);
		}

		if (maxScrollOffset > 0) {
			int virtualHeight = maxScrollOffset + height;
			int scrollbarHeight = Math.round((float)height / virtualHeight * height);
			int scrollbarPosition = Math.round((float)getScrollOffset() / height * scrollbarHeight);
			fill(matrices, width - 3, scrollbarPosition + 1, width - 1, scrollbarPosition + scrollbarHeight - 1, 0x3FFFFFFF);
		}

		matrices.push();
		matrices.translate(0.0, 0.0, 10.0);

		super.render(matrices, mouseX, mouseY, delta);
		if (statusText != null) {
			renderOrderedTooltip(matrices, List.of(statusText), statusTextX - 7, height - 10);
		}

		matrices.pop();
	}

	@Override
	public boolean isDirty() {
		return editors.stream().anyMatch(Dirtyable::isDirty);
	}

	public boolean isLoaded() {
		return saveButton.active;
	}

	protected void setLoaded(boolean loaded) {
		saveButton.active = loaded;
	}

	private static final Logger LOGGER = LogManager.getLogger();
}
