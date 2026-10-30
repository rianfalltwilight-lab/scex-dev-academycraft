package com.mohistmc.academy.client;

/** Client-tick interpolation only. This state never authorizes or charges a skill. */
public final class ChargePresentationState {
    private int ticks, maximum, ticksSinceUpdate;
    private String skill = "";
    private boolean active;

    public void accept(int serverTicks, int maxTicks, String skillId) {
        if (serverTicks < 0) { clear(); return; }
        ticks = serverTicks;
        maximum = Math.max(0, maxTicks);
        skill = skillId;
        active = true;
        ticksSinceUpdate = 0;
    }

    public void tick() {
        if (active && ++ticksSinceUpdate > 100) clear();
    }

    public void clear() {
        ticks = maximum = ticksSinceUpdate = 0;
        skill = "";
        active = false;
    }

    public boolean active() { return active; }
    public String skill() { return skill; }
    public int ticks() { return active ? ticks + ticksSinceUpdate : 0; }
    public float progress(float partialTick) {
        return active && maximum > 0
                ? Math.min(1f, (ticks() + Math.clamp(partialTick, 0f, 1f)) / maximum) : 0f;
    }
}
