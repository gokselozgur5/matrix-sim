package matrix.machine;

import matrix.core.Config;

/**
 * The render budget (crown #124, D-008): turns tick-start pod occupancy
 * into the tick's render capacity, once per tick, so every entity sees
 * the same number. MachineSystem owns one (#102) and recounts BEFORE
 * world.step; the count arrives through a named IntSupplier port wired by
 * Simulation at boot (#88) — the D-031-legitimate read: the farm is
 * realworld-physical but machine-commanded, and only scalars cross the
 * boundary (D-008 hygiene, no A1 breach).
 *
 * <p>Refuses, by crown law: deciding WHICH regions demote (RegionMap
 * ranks), touching pods (one count, one port), any randomness, any wall
 * clock, any mid-tick change. Pure function of tick-start state — same
 * seed, same budget curve; integers only, permille end to end, so
 * nothing here can ever feed the chain a float.
 *
 * <p>Curve: linear, clamped — plugged * 1000 / PODS_REFERENCE (the crew
 * recommendation at the #19 gate; stepped tiers with hysteresis stay the
 * recorded alternative). Coupling exists only under PROCESSOR
 * (ComputeModel): under BATTERY no budget is ever constructed.
 */
public final class SubstrateBudget {
    private final int maxSlots;
    private int plugged;
    private int fidelityPermille;

    /** @param maxSlots region count — the most HOT slots a full farm can buy. */
    public SubstrateBudget(int maxSlots) {
        this.maxSlots = maxSlots;
    }

    /** Once per tick, before world.step (#102): the only write this class accepts. */
    public void recount(int plugged) {
        this.plugged = plugged;
        int f = plugged * 1000 / Config.PODS_REFERENCE;
        this.fidelityPermille = Math.max(0, Math.min(1000, f));
    }

    /** Render fidelity, 0..1000 — integer permille by crown law. */
    public int fidelityPermille() {
        return fidelityPermille;
    }

    /** HOT-slot count S the budget buys: floor(budget x maxSlots), never below SLOTS_FLOOR. */
    public int hotSlots() {
        return Math.max(Config.SLOTS_FLOOR, fidelityPermille * maxSlots / 1000);
    }

    /**
     * The emergency floor (the dossier's option 1, demoted to a floor):
     * only below PODS_MIN does the eco cadence stretch uniformly, by
     * ceil(1000 / budget) — punishment for a dying farm, 1 otherwise.
     */
    public int cadenceStretch() {
        if (plugged >= Config.PODS_MIN) {
            return 1;
        }
        int f = Math.max(1, fidelityPermille);
        return (1000 + f - 1) / f;
    }

    /**
     * The SUBSTRATE instrument line — returned as a string; only the root
     * emits (D-020, additive grammar per the ECO precedent). The glitch
     * count is the RegionMap's — it decides which regions demote, so it
     * counts the demotions; the root carries the number across. budget=
     * prints the permille integer: the dossier's 0.94 example predates
     * the crown's integers-only ruling.
     */
    public String line(long glitches) {
        return String.format(java.util.Locale.ROOT,
                "SUBSTRATE pods=%d/%d budget=%d slots=%d stretch=%d glitches=%d",
                plugged, Config.PODS_REFERENCE, fidelityPermille, hotSlots(),
                cadenceStretch(), glitches);
    }
}
