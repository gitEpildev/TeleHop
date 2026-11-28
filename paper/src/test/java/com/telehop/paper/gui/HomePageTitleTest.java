package com.telehop.paper.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HomePageTitleTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test void keepsTheConfiguredGradientAndAlignsSingleAndDoubleDigitLabels() {
        var name = MiniMessage.miniMessage().deserialize("<gradient:red:gold>Your Homes</gradient>");
        var one = HomePageTitle.format(name, Component.text("Page 1", NamedTextColor.DARK_GRAY));
        var ten = HomePageTitle.format(name, Component.text("Page 10", NamedTextColor.DARK_GRAY));
        assertEquals(name, one.children().getFirst());
        assertEquals("Your Homes" + " ".repeat(17) + "Page 1", PLAIN.serialize(one));
        assertEquals("Your Homes" + " ".repeat(15) + "Page 10", PLAIN.serialize(ten));
    }

    @Test void longBoldNameMakesRoomForTheCompleteTranslatedPageLabel() {
        var name = Component.text("W".repeat(40)).decorate(TextDecoration.BOLD);
        var title = HomePageTitle.format(name, Component.text("Seite 12"));
        // 16 bold Ws at seven pixels each, followed by the gap and a 40-pixel label.
        assertEquals("W".repeat(16) + "  " + "Seite 12", PLAIN.serialize(title));
        assertEquals(TextDecoration.State.TRUE,
                title.children().getFirst().children().getFirst().decoration(TextDecoration.BOLD));
    }
}
