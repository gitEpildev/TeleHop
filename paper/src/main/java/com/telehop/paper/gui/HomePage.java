package com.telehop.paper.gui;

import com.telehop.common.model.HomeRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Ten beds per page. Only occupied slots need storage, even for an unlimited player. */
record HomePage(int index, int allowance, Map<Long, HomeRecord> occupied) {
    static final int SIZE = 10;
    private static final Pattern NUMBERED = Pattern.compile("Home ([1-9][0-9]{0,9})", Pattern.CASE_INSENSITIVE);

    static HomePage of(List<HomeRecord> homes, int requested, int allowance) {
        int capacity = Math.max(homes.size(), allowance);
        var occupied = new HashMap<Long, HomeRecord>();
        var unplaced = new ArrayList<HomeRecord>();
        for (var home : homes) {
            var match = NUMBERED.matcher(home.name());
            if (match.matches()) {
                long number = Long.parseLong(match.group(1));
                if (number <= capacity && occupied.putIfAbsent(number, home) == null) continue;
            }
            unplaced.add(home);
        }
        // Fill gaps without moving numbered homes that already have a slot.
        long free = 1;
        for (var home : unplaced) {
            while (occupied.containsKey(free)) free++;
            occupied.put(free++, home);
        }
        return new HomePage(0, allowance, Map.copyOf(occupied)).turn(requested);
    }

    int last() {
        return Math.max(0, allowance - 1) / SIZE;
    }

    HomePage turn(int next) {
        return new HomePage(Math.clamp(next, 0, last()), allowance, occupied);
    }

    long number(int slot) { return (long) index * SIZE + slot + 1; }

    HomeRecord bed(int slot) {
        long number = number(slot);
        return number <= allowance ? occupied.get(number) : null;
    }

    boolean canCreate(int slot) {
        return number(slot) <= allowance && occupied.size() < allowance && bed(slot) == null;
    }

    static int bedSlot(int index, int rows) {
        int first = rows >= 4 ? 1 : 0;
        int row = index < 5 ? first : Math.min(first + 2, rows - 1);
        return row * 9 + 2 + index % 5;
    }
}
