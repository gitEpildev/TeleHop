package com.telehop.paper.service;

import com.telehop.paper.config.CommandNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MessageServiceTest {
    @Test void literalNamesKeepTheirContentsAndTrustedLanguageActionsKeepTheirStyle() {
        var names = mock(CommandNames.class);
        when(names.inMessage(anyString())).thenAnswer(call -> call.getArgument(0));
        var locale = new YamlConfiguration();
        locale.set("prefix", "<gray>Network </gray>");
        locale.set("line", "<click:suggest_command:'/home'><gold><name></gold></click> <badge>");
        var service = new MessageService(locale, locale, names);
        String typed = "<red>Market</red> <badge>";
        var badge = Component.text("Owner", NamedTextColor.AQUA);
        var full = service.format("line", Map.of("name", typed), Map.of("badge", badge));
        var raw = service.rawFormat("line", Map.of("name", typed), Map.of("badge", badge));
        var plain = PlainTextComponentSerializer.plainText();
        assertEquals("Network " + typed + " Owner", plain.serialize(full));
        assertEquals(typed + " Owner", plain.serialize(raw));
        var serialised = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(raw);
        assertTrue(serialised.contains("gold"));
        assertTrue(serialised.contains("aqua"));
        assertTrue(serialised.contains(ClickEvent.suggestCommand("/home").value()));
    }
}
