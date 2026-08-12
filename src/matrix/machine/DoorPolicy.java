package matrix.machine;

import matrix.core.Config;
import matrix.core.Severity;
import matrix.core.World;

import java.util.function.IntSupplier;

/**
 * The inward door's far bank (D-046 step two, #338): the half that answers
 * WHETHER, and nothing else.
 *
 * <p>A1 is held STRUCTURALLY here, not by discipline. This class has no
 * import from {@code matrix.realworld} and cannot acquire one without the
 * compiler saying so: a petition arrives as a NAME, the farm arrives as a
 * COUNT, and the answer leaves as a boolean the composition root carries
 * back. The two halves of the door converse in events across the bridge and
 * never see each other's types — which is why this class can decide the fate
 * of a mind it has no word for.
 *
 * <p>Two clauses, checked in order, both of which can refuse:
 *
 * <ol>
 * <li><b>The treaty's quota.</b> {@code REINSERTION_QUOTA} returns per run.
 *     A D-006 knob until D-049's regime gives the treaty its own text — the
 *     Machines bargain, they do not surrender the farm.
 * <li><b>The substrate.</b> A pod must be free AND the budget must still be
 *     selling render capacity above its emergency floor. <b>No slot, no
 *     entry — invariant I-1.</b> D-008 stops being a fidelity curve and
 *     becomes the door's bouncer: the door is substrate-bounded, and a
 *     starving farm cannot be talked into seating one more dreamer.
 * </ol>
 *
 * <p>Both counts cross as scalars through named ports (the D-031-legitimate
 * read, the {@link SubstrateBudget} precedent): the free-pod port is an
 * {@code IntSupplier} wired by the root at boot (#88/#440, D-012), and the
 * HOT-slot count is this wing's own budget, read directly because it is
 * already a neighbour in this package.
 *
 * <p>Refuses, by crown law: performing anything (pods, scrubs, avatars and
 * scars are {@code realworld.Reinsertion}'s, #340), naming a Human, holding
 * one, any randomness, any wall clock. Pure function of two integers, a
 * string and one counter — same seed, same verdicts. It prints nothing: it
 * speaks through {@code world.log}, exactly like {@link Source}, and the
 * root owns the stream.
 *
 * <p><b>Why the split earns its keep:</b> because this class refuses
 * everything the farm cannot seat, #340's pod allocation is unconditional by
 * construction. I-1 lives here rather than there for that reason alone.
 */
public final class DoorPolicy {

    /**
     * The treaty's reinsertion clause (#438). Parked here rather than in
     * {@code core.Config} for the reason #862 records: D-006 wants it there,
     * {@code core/} was another crew's floor this season, and a note beats a
     * smuggled edit.
     *
     * <p>Two is not a placeholder. The whole census at canonical scale is six
     * citizens; a quota that could take them all is not a bargain, it is a
     * harvest, and the door would stop being a door.
     */
    public static final int REINSERTION_QUOTA = 2;

    private final World world;
    private final IntSupplier freePods;
    private final SubstrateBudget budget;
    private long granted = 0;
    private long denied = 0;

    /**
     * @param freePods the named port to the farm's spare rack units — one
     *     scalar, wired at the root, never an object (D-012/D-031 hygiene)
     * @param budget this wing's own render budget, or null under BATTERY,
     *     where no substrate coupling exists at all (D-008) and the bouncer
     *     therefore has no floor to enforce
     */
    public DoorPolicy(World world, IntSupplier freePods, SubstrateBudget budget) {
        this.world = world;
        this.freePods = freePods;
        this.budget = budget;
    }

    /**
     * WHETHER, for one filed petition. Every answer speaks — a door that
     * refuses in silence is a door nobody can audit.
     *
     * <p>Clause order is the contract, not an implementation detail: the
     * quota is the treaty's word and the substrate is physics, so a run that
     * has spent its quota reports THAT, even on a farm with room to spare.
     * Reading the physics first would let a full farm mask a spent treaty and
     * the two refusals would stop meaning different things.
     *
     * @return true exactly when the mind may come back — the grant the
     *     performer consumes, carried across the bridge by the root
     */
    public boolean decide(String name) {
        if (granted >= REINSERTION_QUOTA) {
            denied++;
            world.log(Severity.SYS, "DOOR policy: denied " + name + " — quota spent");
            return false;
        }
        if (!substrateSeats()) {
            denied++;
            world.log(Severity.BAD, "DOOR policy: denied " + name
                    + " — no slot; the door is substrate-bounded");
            return false;
        }
        granted++;
        world.log(Severity.FATE, "DOOR policy: granted " + name
                + " — quota " + granted + "/" + REINSERTION_QUOTA);
        return true;
    }

    /**
     * Invariant I-1, as one predicate (#439): a pod AND a slot, or the door
     * stays shut.
     *
     * <p>The pod half is the rack's arithmetic — {@code PODS_REFERENCE} is
     * the farm's physical size, so the spare units are the ones death and
     * liberation vacated. Nobody returns into a rack that is full, which is
     * also what keeps the SUBSTRATE line's {@code pods=n/196} honest: a
     * reinsertion can never push the numerator past the denominator.
     *
     * <p>The slot half is D-008 with teeth. {@link SubstrateBudget#hotSlots}
     * never drops below {@code SLOTS_FLOOR}, so "slots > 0" would be a test
     * that cannot fail — the floor is the budget's promise to keep SOMETHING
     * rendered, not spare capacity. AT the floor the map is already starving
     * and the honest answer to one more dreamer is no.
     *
     * <p>Under BATTERY there is no budget object at all and the substrate
     * clause is vacuous by design: that model buys no coupling, so it sells
     * no refusals either.
     */
    private boolean substrateSeats() {
        if (freePods.getAsInt() <= 0) {
            return false;
        }
        return budget == null || budget.hotSlots() > Config.SLOTS_FLOOR;
    }

    /** Grants ever given, monotone — the quota's own count, and DoorFlux's ceiling check (#347). */
    public long granted() {
        return granted;
    }

    /** Refusals ever spoken, monotone — a door that only ever says yes has not been tested. */
    public long denied() {
        return denied;
    }
}
