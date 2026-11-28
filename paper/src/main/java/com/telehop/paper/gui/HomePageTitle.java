package com.telehop.paper.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayDeque;
import java.util.Deque;

/** Lays out the two ends of the chest's single title line in the default Minecraft font. */
final class HomePageTitle {
    // The vanilla chest is 176 GUI pixels wide, with eight pixels of padding on either side.
    private static final int TITLE_WIDTH = 160;
    private static final int SPACE = 4;

    private HomePageTitle() {}

    static Component format(Component name, Component page) {
        var right = new FittedText(page, TITLE_WIDTH);
        var left = new FittedText(name, Math.max(0, TITLE_WIDTH - right.width - SPACE));
        int spaces = (TITLE_WIDTH - right.width - left.width) / SPACE;
        return Component.empty().append(left.text()).append(Component.text(" ".repeat(spaces))).append(right.text());
    }

    private static final class FittedText implements FlattenerListener {
        private final Component original;
        private final int limit;
        private final Deque<Style> styles = new ArrayDeque<>();
        private final TextComponent.Builder prefix = Component.text();
        private boolean clipped;
        private int width;

        FittedText(Component original, int limit) {
            this.original = original;
            this.limit = limit;
            styles.push(Style.empty());
            ComponentFlattener.basic().flatten(original, this);
        }

        Component text() { return clipped ? prefix.build() : original; }

        @Override public void pushStyle(Style style) {
            styles.push(style.merge(styles.element(), Style.Merge.Strategy.IF_ABSENT_ON_TARGET));
        }

        @Override public void popStyle(Style style) { styles.pop(); }

        @Override public void component(String text) {
            if (clipped || text.isEmpty()) return;
            Style style = styles.element();
            boolean bold = style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
            int end = 0;
            while (end < text.length()) {
                int glyph = text.codePointAt(end);
                int advance = advance(glyph) + (bold ? 1 : 0);
                if (width + advance > limit || glyph == '\n') {
                    clipped = true;
                    break;
                }
                width += advance;
                end += Character.charCount(glyph);
            }
            if (end > 0) prefix.append(Component.text(text.substring(0, end), style));
        }
    }

    // Advances include the one-pixel gap after a glyph. The client chooses the final font.
    private static int advance(int glyph) {
        return switch (glyph) {
            case '!', '\'', ',', '.', ':', ';', 'i', '|' -> 2;
            case '`', 'l' -> 3;
            case ' ', '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> switch (Character.UnicodeScript.of(glyph)) {
                case HAN, HANGUL, HIRAGANA, KATAKANA -> 9;
                default -> 6;
            };
        };
    }
}
