package dev.macepvpmod;

/** Input events since the previous client tick belong to the next processed tick. */
public final class AttributeSwapTracker {
    private int clickedSlot = -1;
    public void click(int slot) { clickedSlot = slot; }
    public boolean select(int slot) {
        if (clickedSlot < 0 || slot == clickedSlot) return false;
        reset();
        return true;
    }
    public void reset() { clickedSlot = -1; }
}
