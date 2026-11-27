package com.telehop.paper.command.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HomeCommandArgumentTest {
    @Test
    void simpleNamesKeepExistingStaffCommandSyntax() {
        assertEquals("Harbour_2", HomeCommandArgument.encode("Harbour_2"));
        assertEquals("Harbour_2", HomeCommandArgument.decode("Harbour_2"));
    }

    @Test
    void generatedAndLegacyNamesRoundTripAsASingleArgument() {
        for (String name : new String[]{"Home 1", "Harbour confirm", "São Paulo", "<green>Farm", "~Old"}) {
            String argument = HomeCommandArgument.encode(name);
            assertFalse(argument.contains(" "));
            assertEquals(name, HomeCommandArgument.decode(argument));
        }
    }
}
