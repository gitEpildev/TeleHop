package com.telehop.paper.gui;

import com.telehop.common.model.HomeRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class HomePageTest {
    private static HomeRecord home(String name) {
        return new HomeRecord("owner", name, "lobby", "world", 0, 70, 0, 0, 0);
    }

    @Test void tenSlotAllowanceStopsAtOnePage() {
        var homes = IntStream.rangeClosed(1, 10).mapToObj(i -> home("Base" + i)).toList();
        var page = HomePage.of(homes, 3, 10);
        assertEquals(0, page.index());
        assertEquals(0, page.last());
        assertEquals(homes, IntStream.range(0, 10).mapToObj(page::bed).toList());
    }

    @Test void seventeenHomesFillTwoPagesAndKeepNumberedHomesInTheirBeds() {
        var homes = new ArrayList<HomeRecord>();
        homes.add(home("Home 17"));
        homes.add(home("Home 1"));
        IntStream.rangeClosed(1, 15).forEach(i -> homes.add(home("Town" + i)));
        var first = HomePage.of(homes, 0, 17);
        var second = HomePage.of(homes, 99, 17);
        assertEquals("Home 1", first.bed(0).name());
        assertEquals(1, second.last());
        assertEquals(1, second.index());
        assertEquals("Home 17", second.bed(6).name());
        assertEquals(7, IntStream.range(0, 10).mapToObj(second::bed).filter(java.util.Objects::nonNull).count());
    }

    @Test void sparseNumberedHomesDoNotCreateEmptyPagesAndEverySavedNameIsAccessible() {
        var names = List.of(home("Home 240"), home("Home 2147483648"), home("Home 2"));
        var page = HomePage.of(names, 23, 3);
        assertEquals(0, page.last());
        assertEquals(List.of(names.get(0), names.get(2), names.get(1)), IntStream.range(0, 3).mapToObj(page::bed).toList());
    }

    @Test void numberedRankCanBrowseUnusedSlotsWithoutExposingTheNextRank() {
        var page = HomePage.of(List.of(home("Home 17")), 1, 17);
        assertEquals(1, page.last());
        assertTrue(page.canCreate(0));
        assertEquals("Home 17", page.bed(6).name());
        assertFalse(page.canCreate(7));
    }

    @Test void downgradedRankCanOnlyBrowseItsCurrentSlots() {
        var homes = IntStream.rangeClosed(1, 23).mapToObj(i -> home("Home " + i)).toList();
        var ten = HomePage.of(homes, 2, 10);
        assertEquals(0, ten.last());
        assertEquals(0, ten.turn(1).index());
        assertEquals("Home 10", ten.bed(9).name());
        var seventeen = HomePage.of(homes, 2, 17);
        assertEquals(1, seventeen.last());
        assertEquals("Home 17", seventeen.bed(6).name());
        assertNull(seventeen.bed(7));
        assertFalse(seventeen.canCreate(7));
        assertEquals("Home 23", HomePage.of(homes, 2, 23).bed(2).name());
    }

    @Test void unlimitedPagesAllowCreationBeyondSavedHomesAndPreserveTheChosenSlot() {
        var homes = IntStream.rangeClosed(1, 20).mapToObj(i -> home("Home " + i)).toList();
        var third = HomePage.of(homes, 2, Integer.MAX_VALUE);
        assertEquals(21L, third.number(0));
        assertTrue(third.canCreate(0));
        assertTrue(third.turn(3).canCreate(0));
        var sparse = HomePage.of(List.of(home("Home 31")), 3, Integer.MAX_VALUE);
        assertEquals("Home 31", sparse.bed(0).name());
        assertEquals(1, sparse.occupied().size());
        var boundary = third.turn(Integer.MAX_VALUE);
        assertTrue(boundary.canCreate(6));
        assertFalse(boundary.canCreate(7));
    }

    @Test void allInventoryHeightsLeaveTheFooterCornersFree() {
        for (int rows = 2; rows <= 6; rows++) {
            int height = rows;
            var slots = IntStream.range(0, 10).map(i -> HomePage.bedSlot(i, height)).boxed().distinct().toList();
            assertEquals(10, slots.size());
            assertFalse(slots.contains((rows - 1) * 9));
            assertFalse(slots.contains(rows * 9 - 1));
        }
    }
}
