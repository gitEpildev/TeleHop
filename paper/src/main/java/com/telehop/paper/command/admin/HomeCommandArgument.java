package com.telehop.paper.command.admin;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Represents stored home names as one argument in staff chat buttons, including names with spaces. */
final class HomeCommandArgument {
    private HomeCommandArgument() {}

    static String encode(String name) {
        if (name.matches("[A-Za-z0-9_]+")) return name;
        return "~" + Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8));
    }

    static String decode(String argument) {
        if (!argument.startsWith("~")) return argument;
        try {
            String name = new String(Base64.getUrlDecoder().decode(argument.substring(1)), StandardCharsets.UTF_8);
            return encode(name).equals(argument) ? name : argument;
        } catch (IllegalArgumentException invalidEncoding) {
            return argument;
        }
    }
}
