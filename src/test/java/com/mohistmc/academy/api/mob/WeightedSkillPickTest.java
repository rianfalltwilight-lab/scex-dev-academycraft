package com.mohistmc.academy.api.mob;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WeightedSkillPickTest {
    private record W(String id, double w) {}

    @Test void rollsMapOntoCumulativeWeights() {
        List<W> items = List.of(new W("a", 1), new W("skip", 0), new W("b", 3));
        assertEquals("a", WeightedSkillPick.pick(items, W::w, 0.0).id());
        assertEquals("a", WeightedSkillPick.pick(items, W::w, 0.2499).id());
        assertEquals("b", WeightedSkillPick.pick(items, W::w, 0.25).id());
        assertEquals("b", WeightedSkillPick.pick(items, W::w, 1.0).id());
        assertEquals("a", WeightedSkillPick.pick(items, W::w, -3).id());
    }

    @Test void nothingPickableGivesNull() {
        assertNull(WeightedSkillPick.pick(List.<W>of(), W::w, 0.5));
        assertNull(WeightedSkillPick.pick(List.of(new W("z", 0), new W("n", Double.NaN), new W("neg", -1)), W::w, 0.5));
    }

    @Test void frequenciesFollowWeights() {
        List<W> items = List.of(new W("a", 1), new W("b", 3), new W("inf", Double.POSITIVE_INFINITY));
        Random random = new Random(42);
        int a = 0;
        int n = 40000;
        for (int i = 0; i < n; i++) {
            String id = WeightedSkillPick.pick(items, W::w, random.nextDouble()).id();
            assertNotEquals("inf", id);
            if (id.equals("a")) a++;
        }
        assertEquals(0.25, a / (double) n, 0.015);
    }
}
