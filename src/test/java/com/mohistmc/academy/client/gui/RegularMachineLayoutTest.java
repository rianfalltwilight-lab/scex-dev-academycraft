package com.mohistmc.academy.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegularMachineLayoutTest {
    @Test void commonGuiScaleWidthsKeepSidebarMachineAndInfoCardTogether() {
        for (int width : new int[] {320, 426, 640}) {
            int left = RegularMachineLayout.machineLeft(width, true);
            int compositionLeft = left - RegularMachineLayout.SIDEBAR_WIDTH;
            int compositionRight = left + RegularMachineLayout.INFO_X + RegularMachineLayout.INFO_WIDTH;
            assertTrue(compositionLeft >= 0, Integer.toString(width));
            assertTrue(compositionRight <= width, Integer.toString(width));
            assertEquals(RegularMachineLayout.COMPOSITION_WIDTH,
                    compositionRight - compositionLeft, Integer.toString(width));
        }
        assertEquals(28, RegularMachineLayout.machineLeft(320, true));
        assertEquals(81, RegularMachineLayout.machineLeft(426, true));
        assertEquals(188, RegularMachineLayout.machineLeft(640, true));
    }

    @Test void panelElementRenderAndHitTestShareTheShiftedOrigin() {
        for (int width : new int[] {320, 426, 640}) {
            int left = RegularMachineLayout.machineLeft(width, true);
            assertEquals(left + 8,
                    RegularMachineLayout.centeredElementX(left, 160, 0));
            assertEquals(left + 155,
                    RegularMachineLayout.centeredElementX(left, 15, 75));
            assertEquals(left + 8,
                    RegularMachineLayout.centeredElementX(left, 150, -5));
        }
    }

    @Test void narrowViewFallsBackToCenteredMachineWithoutPretendingInfoFits() {
        assertEquals((302 - 176) / 2, RegularMachineLayout.machineLeft(302, true));
        assertEquals((320 - 176) / 2, RegularMachineLayout.machineLeft(320, false));
    }

    @Test void matrixSidebarAndWidePanelFitAtThreeHundredTwenty() {
        int left = RegularMachineLayout.contentLeftWithSidebar(320, 295);
        assertEquals(22, left);
        assertEquals(2, left - RegularMachineLayout.SIDEBAR_WIDTH);
        assertEquals(317, left + 295);
    }

    @Test void developerSidebarCanvasAndMenuStayWithinTheFullLayout() {
        for (int width : new int[] {427, 640, 854}) {
            int canvas = RegularMachineLayout.developerCanvasLeft(width);
            int sidebar = RegularMachineLayout.developerSidebarLeft(width);
            int menu = RegularMachineLayout.developerMenuLeft(width);
            assertTrue(sidebar >= 0, Integer.toString(width));
            assertTrue(sidebar + RegularMachineLayout.SIDEBAR_WIDTH <= canvas, Integer.toString(width));
            assertTrue(canvas + RegularMachineLayout.DEVELOPER_CANVAS_WIDTH <= width, Integer.toString(width));
            assertTrue(menu >= canvas, Integer.toString(width));
            assertTrue(menu + RegularMachineLayout.MACHINE_WIDTH
                    <= canvas + RegularMachineLayout.DEVELOPER_CANVAS_WIDTH, Integer.toString(width));
        }
        assertEquals(130, RegularMachineLayout.developerCanvasLeft(640));
        assertEquals(110, RegularMachineLayout.developerSidebarLeft(640));
        assertEquals(242, RegularMachineLayout.developerMenuLeft(640));
    }
}
