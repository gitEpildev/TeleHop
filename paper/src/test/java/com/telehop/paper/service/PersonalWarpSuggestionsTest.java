package com.telehop.paper.service;

import com.telehop.common.model.PlayerWarpRecord;
import com.telehop.common.service.PlayerWarpService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PersonalWarpSuggestionsTest {
    @Test void suggestionsShareAnInflightReadAndDiscardTheDisconnectedSession() {
        var service = mock(PlayerWarpService.class);
        var suggestions = new PersonalWarpSuggestions(service);
        var member = UUID.randomUUID();
        var records = new CompletableFuture<List<PlayerWarpRecord>>();
        when(service.listByOwner(member.toString())).thenReturn(records);
        assertEquals(List.of(), suggestions.list(member));
        assertEquals(List.of(), suggestions.list(member));
        verify(service).listByOwner(member.toString());
        records.complete(List.of(new PlayerWarpRecord(member.toString(), "market", "town", "harbour", 0, 0, 0, 0, 0, false)));
        assertEquals(List.of("market"), suggestions.list(member));

        suggestions.forget(member);
        when(service.listByOwner(member.toString())).thenReturn(new CompletableFuture<>());
        assertEquals(List.of(), suggestions.list(member));
    }
}
