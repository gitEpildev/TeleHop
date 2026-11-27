package com.telehop.paper.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RtpMenuPageTest {
    @Test void everyDestinationIsReachableWithNavigationSpace() {
        assertEquals(new RtpGui.Page(0, 54, 6, 0, 0), RtpGui.Page.forItems(54, 3, false, 0));
        assertEquals(new RtpGui.Page(45, 54, 6, 1, 1), RtpGui.Page.forItems(54, 3, true, 1));
        assertEquals(new RtpGui.Page(90, 101, 6, 2, 2), RtpGui.Page.forItems(101, 3, true, 999));
    }
}
