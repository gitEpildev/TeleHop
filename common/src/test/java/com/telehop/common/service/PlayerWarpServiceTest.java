package com.telehop.common.service;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.PlayerWarpRepository;
import com.telehop.common.model.PlayerWarpRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerWarpServiceTest {

    private static final String OWNER = "owner-uuid-1";
    private static final String OTHER = "owner-uuid-2";

    private DatabaseManager databaseManager;
    private PlayerWarpRepository repository;
    private PlayerWarpService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        databaseManager = mock(DatabaseManager.class);
        repository = mock(PlayerWarpRepository.class);

        when(databaseManager.supplyAsync(any(Supplier.class)))
                .thenAnswer(inv -> {
                    Supplier<?> s = inv.getArgument(0);
                    return CompletableFuture.completedFuture(s.get());
                });
        when(databaseManager.runAsync(any(Runnable.class)))
                .thenAnswer(inv -> {
                    Runnable r = inv.getArgument(0);
                    r.run();
                    return CompletableFuture.completedFuture(null);
                });

        service = new PlayerWarpService(databaseManager, repository);
    }

    // ------------------------------------------------------------------
    // upsert()
    // ------------------------------------------------------------------

    @Test
    void upsertDelegatesToRepository() throws Exception {
        PlayerWarpRecord warp = warp(OWNER, "base", false);

        service.upsert(warp).get();

        verify(repository).upsert(warp);
    }

    // ------------------------------------------------------------------
    // find()
    // ------------------------------------------------------------------

    @Test
    void findReturnsRecordWhenPresent() throws Exception {
        PlayerWarpRecord warp = warp(OWNER, "base", false);
        when(repository.find(OWNER, "base")).thenReturn(Optional.of(warp));

        Optional<PlayerWarpRecord> result = service.find(OWNER, "base").get();

        assertTrue(result.isPresent());
        assertEquals("base", result.get().name());
        verify(repository).find(OWNER, "base");
    }

    @Test
    void findReturnsEmptyWhenNotPresent() throws Exception {
        when(repository.find(OWNER, "ghost")).thenReturn(Optional.empty());

        Optional<PlayerWarpRecord> result = service.find(OWNER, "ghost").get();

        assertTrue(result.isEmpty());
    }

    // ------------------------------------------------------------------
    // findPublic()
    // ------------------------------------------------------------------

    @Test
    void findPublicReturnsPublicWarp() throws Exception {
        PlayerWarpRecord publicWarp = warp(OWNER, "shop", true);
        when(repository.findPublic(OWNER, "shop")).thenReturn(Optional.of(publicWarp));

        Optional<PlayerWarpRecord> result = service.findPublic(OWNER, "shop").get();

        assertTrue(result.isPresent());
        assertTrue(result.get().isPublic());
        verify(repository).findPublic(OWNER, "shop");
    }

    @Test
    void findPublicReturnsEmptyForPrivateWarp() throws Exception {
        when(repository.findPublic(OWNER, "private")).thenReturn(Optional.empty());

        Optional<PlayerWarpRecord> result = service.findPublic(OWNER, "private").get();

        assertTrue(result.isEmpty());
    }

    // ------------------------------------------------------------------
    // delete()
    // ------------------------------------------------------------------

    @Test
    void deleteDelegatesToRepository() throws Exception {
        service.delete(OWNER, "base").get();

        verify(repository).delete(OWNER, "base");
    }

    // ------------------------------------------------------------------
    // listByOwner()
    // ------------------------------------------------------------------

    @Test
    void listByOwnerReturnsList() throws Exception {
        List<PlayerWarpRecord> warps = List.of(warp(OWNER, "base", false), warp(OWNER, "shop", true));
        when(repository.listByOwner(OWNER)).thenReturn(warps);

        List<PlayerWarpRecord> result = service.listByOwner(OWNER).get();

        assertEquals(2, result.size());
        verify(repository).listByOwner(OWNER);
    }

    @Test
    void listByOwnerReturnsEmptyListWhenOwnerHasNone() throws Exception {
        when(repository.listByOwner(OTHER)).thenReturn(List.of());

        List<PlayerWarpRecord> result = service.listByOwner(OTHER).get();

        assertTrue(result.isEmpty());
    }

    // ------------------------------------------------------------------
    // countByOwner()
    // ------------------------------------------------------------------

    @Test
    void countByOwnerReturnsCount() throws Exception {
        when(repository.countByOwner(OWNER)).thenReturn(3);

        int count = service.countByOwner(OWNER).get();

        assertEquals(3, count);
        verify(repository).countByOwner(OWNER);
    }

    @Test
    void countByOwnerReturnsZeroWhenNone() throws Exception {
        when(repository.countByOwner(OTHER)).thenReturn(0);

        assertEquals(0, service.countByOwner(OTHER).get());
    }

    // ------------------------------------------------------------------
    // listAll()
    // ------------------------------------------------------------------

    @Test
    void listAllDelegatesToRepository() throws Exception {
        List<PlayerWarpRecord> all = List.of(
                warp(OWNER, "base", false),
                warp(OTHER, "shop", true)
        );
        when(repository.listAll()).thenReturn(all);

        List<PlayerWarpRecord> result = service.listAll().get();

        assertEquals(2, result.size());
        verify(repository).listAll();
    }

    // ------------------------------------------------------------------
    // setPublic()
    // ------------------------------------------------------------------

    @Test
    void setPublicTrueDelegatesToRepository() throws Exception {
        service.setPublic(OWNER, "shop", true).get();

        verify(repository).setPublic(OWNER, "shop", true);
    }

    @Test
    void setPublicFalseDelegatesToRepository() throws Exception {
        service.setPublic(OWNER, "shop", false).get();

        verify(repository).setPublic(OWNER, "shop", false);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static PlayerWarpRecord warp(String owner, String name, boolean isPublic) {
        return new PlayerWarpRecord(owner, name, "eu", "world", 0, 64, 0, 0f, 0f, isPublic);
    }
}
