package dev.relay.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

import dev.relay.chat.RelayAdventure;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;

import net.kyori.adventure.text.Component;

/**
 * Compact overlay widgets. Layout only — copy is Lunar Adventure, state stays in Java.
 */
final class RelayUi {
	enum Kind {
		PRIMARY,
		SUCCESS,
		GHOST,
		DANGER,
		PAD
	}

	@FunctionalInterface
	interface CoordHandler {
		void submit(int x, int y, int z);
	}

	private static final int CONTROL = 18;
	private static final int INSET = 6;
	private static final int TITLE_LINE = 14;
	private static final int HEADING_LINE = 12;
	private static final int RADIUS = 5;
	private static final int ROW_RADIUS = 6;
	private static final List<LiveField> LIVE = new ArrayList<>();

	private RelayUi() {
	}

	static LabelComponent label(String text, int color) {
		return label(RelayAdventure.overlay(text, color));
	}

	static LabelComponent label(Component adventure) {
		return UIComponents.label(RelayAdventure.nativeComponent(adventure)).shadow(false);
	}

	static LabelComponent title(String text) {
		LabelComponent node = label(RelayAdventure.overlayTitle(text, RelayTheme.TEXT));
		node.lineHeight(TITLE_LINE);
		return node;
	}

	static LabelComponent heading(String text) {
		LabelComponent node = label(RelayAdventure.overlayHeading(text, RelayTheme.TEXT));
		node.lineHeight(HEADING_LINE);
		return node;
	}

	static LabelComponent muted(String text) {
		return label(text, RelayTheme.MUTED);
	}

	static LabelComponent section(String text) {
		LabelComponent node = heading(text);
		node.margins(Insets.top(8));
		return node;
	}

	static FlowLayout spacer() {
		return UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(1));
	}

	static FlowLayout hairline(boolean vertical) {
		if (vertical) {
			FlowLayout line = UIContainers.verticalFlow(Sizing.fixed(1), Sizing.fill());
			line.surface(RelayTheme.hairline());
			return line;
		}
		FlowLayout line = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(1));
		line.surface(RelayTheme.hairline());
		return line;
	}

	static FlowLayout button(String text, Kind kind, boolean enabled, String tooltip, Runnable action) {
		return button(text, kind, enabled, tooltip, action, Sizing.content(), Sizing.fixed(CONTROL),
				Insets.of(0, 0, INSET, INSET));
	}

	static FlowLayout button(String text, Kind kind, boolean enabled, String tooltip, Runnable action,
			Sizing width, Sizing height) {
		return button(text, kind, enabled, tooltip, action, width, height, Insets.none());
	}

	private static FlowLayout button(String text, Kind kind, boolean enabled, String tooltip, Runnable action,
			Sizing width, Sizing height, Insets pad) {
		int fill = fill(kind, false, false, enabled);
		int hoverFill = fill(kind, true, false, enabled);
		int pressFill = fill(kind, true, true, enabled);
		int edge = edge(kind, enabled);
		int textColor = !enabled ? RelayTheme.MUTED
				: kind == Kind.DANGER ? RelayTheme.BAD
				: kind == Kind.SUCCESS ? RelayTheme.GOOD
				: RelayTheme.TEXT;
		HoverPane node = HoverPane.horizontal(width, height);
		node.padding(pad);
		node.horizontalAlignment(HorizontalAlignment.CENTER);
		node.verticalAlignment(VerticalAlignment.CENTER);
		node.child(controlLabel(text, textColor));
		tooltip(node, tooltip);
		bind(node, fill, hoverFill, pressFill, edge, RADIUS, null, enabled ? action : null, null,
				kind == Kind.PAD && enabled, enabled);
		return node;
	}

	static FlowLayout padIcon(int size, boolean enabled, String tooltip, Runnable action, Surface icon) {
		int fill = fill(Kind.PAD, false, false, enabled);
		int hoverFill = fill(Kind.PAD, true, false, enabled);
		int pressFill = fill(Kind.PAD, true, true, enabled);
		HoverPane node = HoverPane.horizontal(Sizing.fixed(size), Sizing.fixed(size));
		tooltip(node, tooltip);
		bind(node, fill, hoverFill, pressFill, edge(Kind.PAD, enabled), RADIUS, icon, enabled ? action : null, null,
				enabled, enabled);
		return node;
	}

	static FlowLayout actionRow(String title, String value, boolean enabled, String tooltip, Runnable action) {
		int titleColor = enabled ? RelayTheme.TEXT : RelayTheme.MUTED;
		int valueColor = enabled ? RelayTheme.TEXT_DIM : RelayTheme.MUTED;
		HoverPane node = HoverPane.horizontal(Sizing.fill(), Sizing.fixed(22));
		node.padding(Insets.of(0, 0, 8, 8));
		node.gap(6);
		node.verticalAlignment(VerticalAlignment.CENTER);
		node.child(label(title, titleColor));
		if (!value.isEmpty()) {
			node.child(spacer());
			node.child(label(value, valueColor));
		}
		tooltip(node, tooltip);
		int fill = fill(Kind.PAD, false, false, enabled);
		int hoverFill = fill(Kind.PAD, true, false, enabled);
		int pressFill = fill(Kind.PAD, true, true, enabled);
		bind(node, fill, hoverFill, pressFill, edge(Kind.PAD, enabled), RADIUS, null, enabled ? action : null, null,
				false, enabled);
		return node;
	}

	static FlowLayout field(Sizing width, String placeholder, String value, int maxLength,
			Predicate<String> allowed, Consumer<String> changed, Runnable submit) {
		TextBoxComponent box = UIComponents.textBox(Sizing.fill(), value == null ? "" : value);
		box.setMaxLength(maxLength);
		box.setHint(RelayAdventure.nativeComponent(
				RelayAdventure.overlay(placeholder, RelayTheme.MUTED)));
		box.setBordered(false);
		box.setTextColor(RelayTheme.TEXT);
		box.setTextColorUneditable(RelayTheme.MUTED);
		box.cursorStyle(CursorStyle.TEXT);
		if (allowed != null) {
			box.setFilter(allowed);
		}
		box.onChanged().subscribe(changed::accept);
		if (submit != null) {
			box.keyPress().subscribe(event -> {
				if (event.isConfirmation()) {
					submit.run();
					return true;
				}
				return false;
			});
		}

		FlowLayout wrap = UIContainers.verticalFlow(width, Sizing.fixed(CONTROL));
		wrap.surface(RelayTheme.input());
		wrap.padding(Insets.of(0, 0, INSET, INSET));
		wrap.verticalAlignment(VerticalAlignment.CENTER);
		wrap.child(box);
		box.focusGained().subscribe(source -> wrap.surface(RelayTheme.inputFocus()));
		box.focusLost().subscribe(() -> wrap.surface(RelayTheme.input()));
		return wrap;
	}

	static FlowLayout nav(RelayModule module, boolean selected, Runnable action) {
		HoverPane node = HoverPane.horizontal(Sizing.fill(), Sizing.fixed(22));
		node.padding(Insets.of(0, 0, INSET, INSET));
		node.gap(6);
		node.verticalAlignment(VerticalAlignment.CENTER);
		boolean enabled = module.available();
		int titleColor = !enabled ? RelayTheme.MUTED
				: selected ? RelayTheme.TEXT : RelayTheme.TEXT_DIM;
		node.child(mark(module.id(), titleColor));
		node.child(label(module.name(), titleColor));
		node.child(spacer());
		if (module.badge() > 0) {
			node.child(badge(module.badge()));
		}

		if (!enabled) {
			tooltip(node, "Coming soon.");
			bind(node, 0, 0, 0, 0, ROW_RADIUS, null, null, null, false, false);
		} else if (selected) {
			bind(node, RelayTheme.SELECTED, RelayTheme.SELECTED_HOVER, RelayTheme.PRESS, 0, ROW_RADIUS,
					RelayTheme.selectedNav(), action, null, false, true);
		} else {
			bind(node, 0, RelayTheme.HOVER, RelayTheme.PRESS, 0, ROW_RADIUS, null, action, null, false, true);
		}
		return node;
	}

	static FlowLayout tab(String text, boolean selected, int badge, Runnable action) {
		HoverPane node = HoverPane.horizontal(Sizing.content(), Sizing.fixed(18));
		node.padding(Insets.of(2, 3, 6, 6));
		node.gap(4);
		node.verticalAlignment(VerticalAlignment.CENTER);
		int color = selected ? RelayTheme.TEXT : RelayTheme.MUTED;
		node.child(label(text, color));
		if (badge > 0) {
			node.child(badge(badge));
		}
		if (selected) {
			bind(node, 0, RelayTheme.HOVER, RelayTheme.PRESS, 0, RADIUS, RelayTheme.tabUnderline(),
					action, null, false, true);
		} else {
			bind(node, 0, RelayTheme.HOVER, RelayTheme.PRESS, 0, RADIUS, null, action, null, false, true);
		}
		return node;
	}

	static FlowLayout listRow(String title, String meta, int color, boolean selected, Runnable select,
			Runnable activate) {
		return listRow(title, meta.isEmpty() ? null : label(meta, RelayTheme.MUTED), color, selected, select,
				activate);
	}

	static FlowLayout listRow(String title, UIComponent trailing, int color, boolean selected, Runnable select,
			Runnable activate) {
		HoverPane node = HoverPane.horizontal(Sizing.fill(), Sizing.content());
		node.padding(Insets.of(3, 3, INSET, INSET));
		node.gap(8);
		node.verticalAlignment(VerticalAlignment.CENTER);
		LabelComponent titleLabel = label(title, color);
		titleLabel.horizontalSizing(Sizing.expand());
		titleLabel.maxWidth(280);
		node.child(titleLabel);
		if (trailing != null) {
			node.child(trailing);
		}
		if (selected) {
			bind(node, RelayTheme.SELECTED, RelayTheme.SELECTED_HOVER, RelayTheme.PRESS, 0, ROW_RADIUS, null,
					select, activate, false, true);
		} else {
			bind(node, 0, RelayTheme.HOVER, RelayTheme.PRESS, 0, ROW_RADIUS, null, select, activate, false, true);
		}
		return node;
	}

	static FlowLayout placementRow(String title, IntSupplier x, IntSupplier y, IntSupplier z, int color,
			boolean selected, Runnable select, CoordHandler move) {
		HoverPane node = HoverPane.vertical(Sizing.fill(), Sizing.content());
		node.padding(Insets.of(4, 4, INSET, INSET));
		node.gap(4);
		LabelComponent titleLabel = label(title, color);
		titleLabel.horizontalSizing(Sizing.fill());
		node.child(titleLabel);
		node.child(coordFields(x, y, z, move));
		if (selected) {
			bind(node, RelayTheme.SELECTED, RelayTheme.SELECTED_HOVER, RelayTheme.PRESS, 0, ROW_RADIUS, null,
					select, null, false, true);
		} else {
			bind(node, 0, RelayTheme.HOVER, RelayTheme.PRESS, 0, ROW_RADIUS, null, select, null, false, true);
		}
		return node;
	}

	static FlowLayout coordFields(IntSupplier x, IntSupplier y, IntSupplier z, CoordHandler handler) {
		String[] drafts = {Integer.toString(x.getAsInt()), Integer.toString(y.getAsInt()), Integer.toString(z.getAsInt())};
		Runnable submit = () -> {
			OptionalInt px = Coords.parse(drafts[0]);
			OptionalInt py = Coords.parse(drafts[1]);
			OptionalInt pz = Coords.parse(drafts[2]);
			if (px.isPresent() && py.isPresent() && pz.isPresent()) {
				handler.submit(px.getAsInt(), py.getAsInt(), pz.getAsInt());
			}
		};
		FlowLayout row = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		row.gap(6);
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.child(coordField("X", RelayTheme.AXIS_X, drafts, 0, x, submit));
		row.child(coordField("Y", RelayTheme.AXIS_Y, drafts, 1, y, submit));
		row.child(coordField("Z", RelayTheme.AXIS_Z, drafts, 2, z, submit));
		tooltip(row, "Click a number, type a coordinate, press Enter.");
		return row;
	}

	static ScrollContainer<FlowLayout> list(FlowLayout rows, int height) {
		return list(rows, Sizing.fixed(height));
	}

	static ScrollContainer<FlowLayout> list(FlowLayout rows, Sizing height) {
		rows.horizontalSizing(Sizing.fill());
		ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(Sizing.fill(), height, rows);
		scroll.surface(RelayTheme.blank());
		scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(RelayTheme.MUTED)));
		scroll.scrollbarThiccness(2);
		return scroll;
	}

	static ScrollContainer<FlowLayout> pageScroll(FlowLayout inner) {
		inner.horizontalSizing(Sizing.fill());
		ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(Sizing.fill(), Sizing.expand(), inner);
		scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(RelayTheme.MUTED)));
		scroll.scrollbarThiccness(2);
		return scroll;
	}

	static FlowLayout toggleRow(String title, String help, String hint, boolean on, boolean usable,
			Consumer<Boolean> setter) {
		HoverPane node = HoverPane.horizontal(Sizing.fill(), Sizing.fixed(22));
		node.padding(Insets.of(0, 0, INSET, INSET));
		node.gap(8);
		node.verticalAlignment(VerticalAlignment.CENTER);
		tooltip(node, help);
		node.child(label(title, usable ? RelayTheme.TEXT : RelayTheme.MUTED));
		if (!hint.isEmpty()) {
			node.child(keycap(hint, false));
		}
		node.child(spacer());
		node.child(toggle(on, usable));
		if (usable) {
			bind(node, 0, RelayTheme.HOVER, RelayTheme.PRESS, 0, ROW_RADIUS, null, () -> setter.accept(!on),
					null, false, true);
		} else {
			bind(node, 0, 0, 0, 0, ROW_RADIUS, null, null, null, false, false);
		}
		return node;
	}

	static FlowLayout stepperRow(String title, String help, String value, boolean minusEnabled,
			boolean plusEnabled, Runnable minus, Runnable plus) {
		return stepperRow(title, help, value, minusEnabled, plusEnabled, minus, plus, 52);
	}

	static FlowLayout stepperRow(String title, String help, String value, boolean minusEnabled,
			boolean plusEnabled, Runnable minus, Runnable plus, int valueWidth) {
		FlowLayout node = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(22));
		node.padding(Insets.of(0, 0, INSET, INSET));
		node.gap(6);
		node.verticalAlignment(VerticalAlignment.CENTER);
		tooltip(node, help);
		node.child(label(title, RelayTheme.TEXT));
		node.child(spacer());
		node.child(button("−", Kind.PAD, minusEnabled, "", minus, Sizing.fixed(CONTROL), Sizing.fixed(CONTROL)));
		FlowLayout shown = UIContainers.horizontalFlow(Sizing.fixed(valueWidth), Sizing.fixed(14));
		shown.horizontalAlignment(HorizontalAlignment.CENTER);
		shown.verticalAlignment(VerticalAlignment.CENTER);
		shown.child(label(value, RelayTheme.TEXT));
		node.child(shown);
		node.child(button("+", Kind.PAD, plusEnabled, "", plus, Sizing.fixed(CONTROL), Sizing.fixed(CONTROL)));
		return node;
	}

	static FlowLayout hotkeyRow(String title, String binding, boolean listening, Runnable action) {
		HoverPane node = HoverPane.horizontal(Sizing.fill(), Sizing.fixed(22));
		node.padding(Insets.of(0, 0, INSET, INSET));
		node.gap(8);
		node.verticalAlignment(VerticalAlignment.CENTER);
		tooltip(node, listening ? "Press a key, or Esc to unbind." : "Click, then press a key.");
		node.child(label(title, RelayTheme.TEXT));
		node.child(spacer());
		String shown = listening ? "..." : binding.isEmpty() ? "None" : binding.toUpperCase();
		node.child(keycap(shown, listening));
		bind(node, 0, RelayTheme.HOVER, RelayTheme.PRESS, 0, ROW_RADIUS, null, action, null, false, true);
		return node;
	}

	static FlowLayout keycap(String text, boolean listening) {
		int color = listening ? RelayTheme.ACCENT : "None".equals(text) ? RelayTheme.MUTED : RelayTheme.TEXT_DIM;
		FlowLayout key = UIContainers.horizontalFlow(Sizing.content(), Sizing.fixed(14));
		key.padding(Insets.of(0, 0, INSET, INSET));
		key.surface(RelayTheme.keycap(listening));
		key.horizontalAlignment(HorizontalAlignment.CENTER);
		key.verticalAlignment(VerticalAlignment.CENTER);
		key.child(controlLabel(text, color));
		return key;
	}

	static FlowLayout headerLink(String text, boolean active, String tooltip, Runnable action) {
		HoverPane node = HoverPane.horizontal(Sizing.content(), Sizing.fixed(16));
		node.padding(Insets.of(1, 2, 6, 6));
		node.horizontalAlignment(HorizontalAlignment.CENTER);
		node.verticalAlignment(VerticalAlignment.CENTER);
		node.child(controlLabel(text, active ? RelayTheme.TEXT : RelayTheme.TEXT_DIM));
		tooltip(node, tooltip);
		if (active) {
			bind(node, RelayTheme.SELECTED, RelayTheme.SELECTED_HOVER, RelayTheme.PRESS, 0, RADIUS, null,
					action, null, false, true);
		} else {
			bind(node, 0, RelayTheme.HOVER, RelayTheme.PRESS, 0, RADIUS, null, action, null, false, true);
		}
		return node;
	}

	static FlowLayout status(String text, int dotColor) {
		FlowLayout node = UIContainers.horizontalFlow(Sizing.content(), Sizing.fixed(16));
		node.gap(6);
		node.verticalAlignment(VerticalAlignment.CENTER);
		node.child(dot(dotColor));
		node.child(label(text, RelayTheme.TEXT_DIM));
		return node;
	}

	static FlowLayout dot(int color) {
		FlowLayout mark = UIContainers.horizontalFlow(Sizing.fixed(6), Sizing.fixed(6));
		mark.surface(RelayTheme.disc(color));
		return mark;
	}

	static void beginRebuild() {
		LIVE.clear();
	}

	static void tickLive() {
		LIVE.removeIf(field -> !field.box.hasParent());
		for (LiveField field : LIVE) {
			field.sync();
		}
	}

	private static LabelComponent controlLabel(String text, int color) {
		LabelComponent node = label(text, color);
		node.lineHeight(8);
		return node;
	}

	private static FlowLayout coordField(String axis, int color, String[] drafts, int index,
			IntSupplier live, Runnable submit) {
		TextBoxComponent box = UIComponents.textBox(Sizing.fill(), drafts[index]);
		box.setMaxLength(11);
		box.setBordered(false);
		box.setTextColor(RelayTheme.TEXT);
		box.setTextColorUneditable(RelayTheme.MUTED);
		box.setFilter(Coords::typing);
		box.cursorStyle(CursorStyle.TEXT);
		box.onChanged().subscribe(value -> drafts[index] = value);
		box.keyPress().subscribe(event -> {
			if (event.isConfirmation()) {
				submit.run();
				return true;
			}
			return false;
		});

		FlowLayout boxWrap = UIContainers.verticalFlow(Sizing.fixed(58), Sizing.fixed(16));
		boxWrap.surface(RelayTheme.input());
		boxWrap.padding(Insets.of(0, 0, 4, 3));
		boxWrap.verticalAlignment(VerticalAlignment.CENTER);
		boxWrap.child(box);
		box.focusGained().subscribe(source -> boxWrap.surface(RelayTheme.inputFocus()));
		box.focusLost().subscribe(() -> {
			boxWrap.surface(RelayTheme.input());
			submit.run();
		});
		LIVE.add(new LiveField(box, live, drafts, index));

		FlowLayout node = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		node.gap(3);
		node.verticalAlignment(VerticalAlignment.CENTER);
		node.child(label(axis, color));
		node.child(boxWrap);
		return node;
	}

	private static FlowLayout mark(String moduleId, int color) {
		FlowLayout icon = UIContainers.horizontalFlow(Sizing.fixed(RelayIcons.SIZE), Sizing.fixed(RelayIcons.SIZE));
		icon.surface((context, component) ->
				RelayIcons.paint(context, component.x(), component.y(), moduleId, color));
		return icon;
	}

	private static FlowLayout badge(int count) {
		String markText = count > 9 ? "9+" : Integer.toString(count);
		FlowLayout pill = UIContainers.horizontalFlow(Sizing.content(), Sizing.fixed(11));
		pill.padding(Insets.of(0, 0, 4, 4));
		pill.surface(RelayTheme.pill(0x337EB8D4, 0));
		pill.horizontalAlignment(HorizontalAlignment.CENTER);
		pill.verticalAlignment(VerticalAlignment.CENTER);
		pill.child(label(markText, RelayTheme.ACCENT));
		return pill;
	}

	private static FlowLayout toggle(boolean on, boolean usable) {
		int fill = !usable ? RelayTheme.TRACK_OFF : on ? 0xA07EB8D4 : RelayTheme.TRACK_OFF;
		int edge = on && usable ? 0x667EB8D4 : RelayTheme.BORDER;
		int knob = usable ? RelayTheme.KNOB : RelayTheme.MUTED;
		FlowLayout track = UIContainers.horizontalFlow(Sizing.fixed(22), Sizing.fixed(12));
		track.surface((context, component) -> {
			int x = component.x();
			int y = component.y();
			int w = component.width();
			int h = component.height();
			RelayTheme.drawRoundRect(context, x, y, w, h, h / 2, fill, edge);
			int radius = Math.max(1, (h / 2) - 2);
			int cx = on ? x + w - radius - 2 : x + radius + 2;
			int cy = y + h / 2;
			context.drawCircle(cx, cy, Math.max(10, radius * 4), radius, Color.ofArgb(knob));
		});
		return track;
	}

	private static void tooltip(UIComponent node, String tooltip) {
		if (tooltip == null || tooltip.isEmpty()) {
			return;
		}
		node.tooltip(RelayAdventure.nativeComponent(
				RelayAdventure.overlay(tooltip, RelayTheme.TEXT)));
	}

	private static void bind(HoverPane node, int idle, int hover, int press, int edge, int radius,
			Surface extra, Runnable action, Runnable activate, boolean repeat, boolean enabled) {
		if (enabled) {
			node.interact(idle, hover, press, edge, radius, extra, action, activate, repeat);
			cursors(node, true);
			return;
		}

		Surface painted = RelayTheme.roundRect(idle, edge, radius);
		if (extra != null) {
			painted = painted.and(extra);
		}
		node.surface(painted);
		cursors(node, false);
	}

	private static void cursors(UIComponent node, boolean enabled) {
		if (node instanceof TextBoxComponent) {
			node.cursorStyle(CursorStyle.TEXT);
			return;
		}

		node.cursorStyle(enabled ? CursorStyle.HAND : CursorStyle.POINTER);
		if (node instanceof ParentUIComponent parent) {
			for (UIComponent child : parent.children()) {
				cursors(child, enabled);
			}
		}
	}

	private static int fill(Kind kind, boolean hover, boolean press, boolean enabled) {
		int base = switch (kind) {
			case PRIMARY -> press ? 0x667EB8D4 : hover ? 0x4D7EB8D4 : 0x337EB8D4;
			case SUCCESS -> press ? 0x667DDAA5 : hover ? 0x4D7DDAA5 : 0x337DDAA5;
			case DANGER -> press ? 0x66FF8B93 : hover ? 0x4DFF8B93 : 0x33FF8B93;
			case PAD, GHOST -> press ? RelayTheme.PRESS : hover ? RelayTheme.HOVER : 0x0AFFFFFF;
		};
		if (!enabled) {
			return RelayTheme.blend(base, RelayTheme.WINDOW, 0.55F);
		}
		return base;
	}

	private static int edge(Kind kind, boolean enabled) {
		if (!enabled) {
			return RelayTheme.BORDER;
		}
		return switch (kind) {
			case PRIMARY -> 0x667EB8D4;
			case SUCCESS -> 0x667DDAA5;
			case DANGER -> 0x66FF8B93;
			case PAD, GHOST -> RelayTheme.BORDER;
		};
	}

	private record LiveField(TextBoxComponent box, IntSupplier live, String[] drafts, int index) {
		void sync() {
			if (box.isFocused()) {
				return;
			}

			String next = Integer.toString(live.getAsInt());
			if (next.equals(box.getValue())) {
				return;
			}

			drafts[index] = next;
			box.text(next);
		}
	}
}
