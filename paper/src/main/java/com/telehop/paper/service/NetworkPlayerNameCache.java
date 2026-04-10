package com.telehop.paper.service;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

public class NetworkPlayerNameCache {
    private final Set<String> names = new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);

    public void replace(List<String> freshNames) {
        names.clear();
        names.addAll(freshNames);
    }

    public List<String> list() {
        Set<String> merged = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        merged.addAll(names);
        Bukkit.getOnlinePlayers().forEach(p -> merged.add(p.getName()));
        return new ArrayList<>(merged);
    }
}
