package dev.macepvpmod;

/** Input events since the previous client tick belong to the next processed tick. */
public final class AttributeSwapTracker {
    private int clickedSlot = -1;
    private boolean successfulHit;
    private boolean pendingSwap;
    public void click(int slot) { clickedSlot = slot; successfulHit = false; pendingSwap = false; }
    public boolean successfulHit() {
        if (clickedSlot < 0) return false;
        successfulHit = true;
        if (!pendingSwap) return false;
        reset();
        return true;
    }
    public boolean select(int slot) { return select(slot, false, true); }
    public boolean select(int slot, boolean requireSuccessfulHit, boolean destinationIsWeapon) {
        if (clickedSlot < 0 || slot == clickedSlot || !destinationIsWeapon) return false;
        if (requireSuccessfulHit && !successfulHit) {
            pendingSwap = true;
            return false;
        }
        reset();
        return true;
    }
    public void reset() { clickedSlot = -1; successfulHit = false; pendingSwap = false; }
}
