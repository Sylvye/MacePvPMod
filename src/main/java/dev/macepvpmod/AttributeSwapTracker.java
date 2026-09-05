package dev.macepvpmod;

/** Input events since the previous client tick belong to the next processed tick. */
public final class AttributeSwapTracker {
    private int clickedSlot = -1;
    private boolean successfulHit;
    public void click(int slot) { clickedSlot = slot; successfulHit = false; }
    public void successfulHit() { if (clickedSlot >= 0) successfulHit = true; }
    public boolean select(int slot) { return select(slot, false, true); }
    public boolean select(int slot, boolean requireSuccessfulHit, boolean destinationIsWeapon) {
        if (clickedSlot < 0 || slot == clickedSlot || requireSuccessfulHit && !successfulHit || !destinationIsWeapon) return false;
        reset();
        return true;
    }
    public void reset() { clickedSlot = -1; successfulHit = false; }
}
