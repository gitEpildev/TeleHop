package com.telehop.common.service;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.HomeRepository;
import com.telehop.common.model.HomeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomeServiceTest {

    private DatabaseManager databaseManager;
    private HomeRepository repository;
    private HomeService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        databaseManager = mock(DatabaseManager.class);
        repository = mock(HomeRepository.class);

        when(databaseManager.supplyAsync(any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(0);
                    return CompletableFuture.completedFuture(supplier.get());
                });
        when(databaseManager.runAsync(any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(0);
                    runnable.run();
                    return CompletableFuture.completedFuture(null);
                });

        service = new HomeService(databaseManager, repository);
    }

    @Test
    void listByPlayerDelegatesToRepository() throws Exception {
        HomeRecord home = new HomeRecord("uuid-1", 1, "eu", "world", 10, 64, 20, 0f, 0f);
        when(repository.listByPlayer("uuid-1")).thenReturn(List.of(home));

        List<HomeRecord> result = service.listByPlayer("uuid-1").get();

        assertEquals(1, result.size());
        assertEquals("eu", result.get(0).server());
        verify(repository).listByPlayer("uuid-1");
    }

    @Test
    void findDelegatesToRepository() throws Exception {
        HomeRecord home = new HomeRecord("uuid-2", 3, "usa", "world_nether", 0, 100, 0, 90f, 0f);
        when(repository.find("uuid-2", 3)).thenReturn(Optional.of(home));

        Optional<HomeRecord> result = service.find("uuid-2", 3).get();

        assertTrue(result.isPresent());
        assertEquals("world_nether", result.get().world());
    }

    @Test
    void findReturnsEmptyWhenNotExists() throws Exception {
        when(repository.find("uuid-3", 5)).thenReturn(Optional.empty());

        Optional<HomeRecord> result = service.find("uuid-3", 5).get();

        assertTrue(result.isEmpty());
    }

    @Test
    void upsertDelegatesToRepository() throws Exception {
        HomeRecord home = new HomeRecord("uuid-4", 2, "lobby", "world", 1, 2, 3, 0f, 0f);

        service.upsert(home).get();

        ArgumentCaptor<HomeRecord> captor = ArgumentCaptor.forClass(HomeRecord.class);
        verify(repository).upsert(captor.capture());
        assertEquals(2, captor.getValue().slot());
    }

    @Test
    void deleteDelegatesToRepository() throws Exception {
        service.delete("uuid-5", 4).get();

        verify(repository).delete("uuid-5", 4);
    }
}
