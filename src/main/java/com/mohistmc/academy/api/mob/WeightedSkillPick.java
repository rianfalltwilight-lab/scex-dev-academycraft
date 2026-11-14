package com.mohistmc.academy.api.mob;

import java.util.List;
import java.util.function.ToDoubleFunction;

/** Weighted random choice, kept free of game classes so it can be unit-tested. */
public final class WeightedSkillPick {
    private WeightedSkillPick() {}

    /**
     * Picks an item with probability proportional to its weight; non-positive or non-finite weights are
     * never picked.  {@code roll} is a uniform value in [0, 1).  Returns null when nothing can be picked.
     */
    public static <T> T pick(List<T> items, ToDoubleFunction<T> weight, double roll) {
        double total = 0;
        for (T item : items) total += usable(weight.applyAsDouble(item));
        if (!(total > 0)) return null;
        double point = Math.min(Math.max(roll, 0.0), Math.nextDown(1.0)) * total;
        T last = null;
        for (T item : items) {
            double w = usable(weight.applyAsDouble(item));
            if (w <= 0) continue;
            last = item;
            if (point < w) return item;
            point -= w;
        }
        return last;
    }

    private static double usable(double w) {
        return Double.isFinite(w) && w > 0 ? w : 0;
    }
}
