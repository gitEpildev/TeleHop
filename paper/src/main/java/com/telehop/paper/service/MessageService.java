package com.telehop.paper.service;

import com.telehop.paper.config.CommandNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

public class MessageService {
    private final FileConfiguration messages;
    private final FileConfiguration fallback;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final CommandNames commands;

    public MessageService(FileConfiguration messages, FileConfiguration fallback, CommandNames commands) {
        this.commands = commands;
        this.messages = messages;
        this.fallback = fallback;
    }

    private String resolve(String key) {
        String val = messages.getString(key);
        if (val == null) val = fallback.getString(key);
        return commands.inMessage(val != null ? val : "<red>Missing message: " + key);
    }

    private String prefix() {
        String val = messages.getString("prefix");
        if (val != null) return val;
        return fallback.getString("prefix", "");
    }

    public Component raw(String key) {
        return miniMessage.deserialize(resolve(key));
    }

    public Component format(String key, Map<String, String> replacements) {
        String base = prefix() + resolve(key);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            base = base.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return miniMessage.deserialize(base);
    }

    public Component format(String key) {
        String base = prefix() + resolve(key);
        return miniMessage.deserialize(base);
    }

    public String rawString(String key) {
        return resolve(key);
    }

    public Component rawFormat(String key, Map<String, String> replacements) {
        String base = resolve(key);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            base = base.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return miniMessage.deserialize(base);
    }

    /** Deserialise an arbitrary MiniMessage string (not a language key). */
    public Component deserialize(String miniMessageString) {
        return miniMessage.deserialize(miniMessageString);
    }
}
