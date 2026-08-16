package matrix.character;

import java.util.ArrayList;
import java.util.List;

/**
 * The one door every wing walks through to ask what a resident is: an
 * identity in, a sheet out, derived on read and stored nowhere (#656).
 *
 * <h2>Why a door at all, when {@link Sheets#derive} is already public</h2>
 *
 * Because a law with no door has no place to be broken, and therefore no
 * place to be watched. The sheets-cached-nowhere rule was written in #350
 * and has been true since — {@code git grep} finds zero stored sheets in
 * {@code realworld}, {@code entities} and {@code machine} today — but it
 * was true the way an empty room is tidy. Nothing in the tree would have
 * noticed the first {@code private final Sheet sheet} to land in a wing,
 * and the deleted {@code Human.threshold} field is the precedent for what
 * happens next: a cached derived value is a second source of truth, and it
 * drifts the moment anything authors an override.
 *
 * <p>So this class is two things and deliberately nothing else: the single
 * named entry point that a grep can anchor on, and the crossing that keeps
 * D-013's bridge single. {@code probes/SheetFence} reads both.
 *
 * <h2>Derived on read</h2>
 *
 * {@link #at} holds no map, no array, no field — the class has no state at
 * all, and {@code SheetFence} judges that by reading this file. Memoizing
 * here would be the cheapest possible optimization and the most expensive
 * possible mistake: a per-name cache is exactly the second source of truth
 * the law forbids, and it would be invisible at every call site.
 *
 * <p>The cost is bounded and measured rather than assumed: one sheet is
 * one FNV-1a fold over a short name plus one multiply-shift chain per axis,
 * five axes at the widest vocabulary. {@code probes/SheetBench} prices it,
 * and D-018's regime applies unchanged — a resident nobody asks about
 * derives nothing, because nobody called the door.
 *
 * <h2>Crossing as a hash, never a reference</h2>
 *
 * D-013 keeps one bridge between the dream and the world. A sheet is a
 * value in this package, but a {@code Sheet} handed across the jack is a
 * handle into the character layer, and handles are how a single bridge
 * becomes two. {@link #crossing} answers with an {@code int} instead: the
 * whole sheet folded to one word, byte-defined, reproducible outside the
 * JVM, and useless as a way back in. What crosses can be compared and
 * logged; it cannot be dereferenced.
 */
public final class SheetDoor {

    private SheetDoor() {}

    /**
     * The sheet {@code nameAtBirth} answers for as a member of
     * {@code family} — derived here, now, and forgotten on return.
     *
     * <p>The name at birth, never the current name: renaming is not
     * rebirth (see {@link Sheets}). This door states that invariant in its
     * parameter name and takes it on trust, exactly as the derivation does;
     * the birth record that would make it structural is #342's.
     */
    public static Sheet at(String nameAtBirth, Family family) {
        return Sheets.derive(nameAtBirth, family);
    }

    /**
     * The Matrix's own sheet: derived like every other resident's, except
     * for the one axis the world already knows the answer to (#661).
     *
     * <p>{@code stability}, {@code tolerance} and {@code authority} come
     * from the name, as a christening should. {@code versionFatigue} does
     * not: the system has LIVED through its reloads, the count has been in
     * the digest since v1, and folding the string {@code "the Matrix"} into
     * a number to describe how tired it is would be inventing a fact the
     * world is already holding. Six lived versions read 6.
     *
     * <p>The charm of the row is that the retroactivity is free. Nothing
     * new is recorded, no field is added, no tick is spent — an old counter
     * is finally read as the character trait it always was. An in-run
     * reboot raises it on the same page, because there is nothing between
     * the counter and the sheet to go stale.
     *
     * <p>The parameter is an {@code int} and not a {@code World}, which is
     * D-013 doing its job: this layer stays type-blind, the caller does the
     * reading, and the bridge stays single.
     *
     * @param livedVersions the world's version counter, as it stands right now
     */
    public static Sheet system(String nameAtBirth, int livedVersions) {
        Sheet derived = at(nameAtBirth, Family.SYSTEM);
        List<Integer> values = new ArrayList<>(derived.values());
        values.set(Family.SYSTEM.axisIndex(Family.FATIGUE_AXIS), band(livedVersions));
        return new Sheet(Family.SYSTEM, nameAtBirth, values);
    }

    /**
     * A lived-version count as a stat: the 1..10 band every axis lives in.
     *
     * <p>A version counter is unbounded and a stat is not, so the two meet
     * somewhere. They meet by SATURATION rather than by modulo, and the
     * choice is the whole meaning of the axis: {@code Math.floorMod} would
     * make v11 as fresh as v1, and a system on its eleventh reload being
     * reported as brand new is the one reading that is definitely wrong. A
     * saturating band says "past exhausted, still exhausted", which is what
     * fatigue is.
     *
     * <p>v0 bands to 1 rather than 0 because 0 is outside the stat range
     * and {@link Sheet} refuses it — a world that has never reloaded is at
     * the bottom of the axis, not off it.
     */
    static int band(int livedVersions) {
        return Math.max(1, Math.min(10, livedVersions));
    }

    /**
     * The word a sheet crosses the jack as: family, name and every value
     * folded to one {@code int} through this package's own mixer.
     *
     * <p>Not {@code Sheet.hashCode}. The record's generated hash is a
     * platform number — unspecified across implementations, and the very
     * shape the birth-seed ruling's hygiene clause keeps out of this
     * package. This fold is arithmetic over bytes we define, so two boxes
     * agree forever and a stranger's implementation can reproduce it from
     * the spec without owning a JVM at all.
     *
     * <p>Values are folded in canonical axis order, which is why {@link
     * Family} forbids reordering a vocabulary: a reorder re-rolls the
     * sheets AND remakes every crossing ever recorded.
     */
    public static int crossing(Sheet sheet) {
        int h = Sheets.fnv1a(sheet.family().name() + '/' + sheet.name());
        for (int value : sheet.values()) {
            h ^= value;
            h *= 0x01000193;
        }
        return Sheets.avalanche(h);
    }
}
