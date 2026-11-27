package com.telehop.paper.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafeColumnSearchTest {
    @Test
    void aMovedBorderLimitsTheSampleRangeWithinTheRadius() {
        for (int i = 0; i < 128; i++) {
            int coordinate = SafeColumnSearch.coordinate(80, 75.5, 20);
            assertTrue(coordinate >= 56 && coordinate <= 80);
        }
    }

    @Test
    void thereIsNoCandidateIfTheWorldBorderDoesNotIntersectTheRadius() {
        assertEquals(Integer.MIN_VALUE, SafeColumnSearch.coordinate(3, 400, 10));
    }

    @Test
    void maximumRadiusDoesNotOverflowAndCanSelectASingleColumn() {
        assertEquals(4, SafeColumnSearch.coordinate(Integer.MAX_VALUE, 4.5, 1));
    }
}
