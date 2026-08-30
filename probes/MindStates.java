import matrix.Simulation;
import matrix.causal.CausalId;
import matrix.causal.CausalRecord;
import matrix.core.Position;
import matrix.core.EventBus;
import matrix.core.PlaceGraph;
import matrix.core.Rng;
import matrix.core.StateSink;
import matrix.core.World;
import matrix.entities.Avatar;
import matrix.entities.Pill;
import matrix.realworld.Brain;
import matrix.realworld.Human;
import matrix.realworld.LinkKind;
import matrix.realworld.MindState;
import matrix.realworld.NeuralLink;
import matrix.realworld.RealWorld;
import matrix.zion.BroadcastRig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Adversarial executable census for #1694's persistent Human-owned mind state. */
public final class MindStates {
    private static int cases, lifecycleFail, boundsFail, canonicalFail, rosterFail;

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        CausalRecord.Subject s = new CausalRecord.Subject("human-7");
        MindState genesis = MindState.initial(s);
        held(genesis.subject().equals(s) && genesis.revision() == 0 && genesis.history().isEmpty(), "genesis");

        MindState.MemoryTrace a = trace(s, 1, 0, 12, 0, "door looked open");
        MindState.MemoryTrace b = trace(s, 1, 1, 12, 1, "voice said stay");
        ArrayList<MindState.MemoryTrace> caller = new ArrayList<>(List.of(a, b));
        MindState lived = new MindState(s, 1, caller);
        byte[] sealed = lived.canonicalBytes();
        caller.clear();
        bounds(lived.history().size() == 2, "caller-list-copy");
        try { lived.history().clear(); bounds(false, "returned-list-immutable"); }
        catch (UnsupportedOperationException expected) { bounds(true, "returned-list-immutable"); }
        byte first = sealed[0]; sealed[0] ^= 0x7f;
        canonical(lived.canonicalBytes()[0] == first, "fresh-canonical-array");
        canonical(Arrays.equals(lived.canonicalBytes(), new MindState(s, 1, List.of(a, b)).canonicalBytes()), "equal-bytes");
        canonical(!Arrays.equals(lived.canonicalBytes(), new MindState(s, 1, List.of(a)).canonicalBytes()), "history-covered");
        canonical(!Arrays.equals(lived.canonicalBytes(), new MindState(s, 2, List.of(a, b)).canonicalBytes()), "revision-covered");
        MindState swappedMeaning = new MindState(s, 1, List.of(
                trace(s, 1, 0, 12, 0, "voice said stay"),
                trace(s, 1, 1, 12, 1, "door looked open")));
        canonical(!Arrays.equals(lived.canonicalBytes(), swappedMeaning.canonicalBytes()), "ordered-values-covered");
        MindState.MemoryTrace changedChannel = trace(s, 1, 0, 12, 0, "door looked open",
                CausalRecord.Channel.VISION, CausalRecord.Principal.unknown(), 5_000,
                CausalRecord.Fidelity.PARTIAL);
        MindState.MemoryTrace changedSource = trace(s, 1, 0, 12, 0, "door looked open",
                CausalRecord.Channel.TEXT,
                new CausalRecord.Principal(CausalRecord.PrincipalKind.PLACE, "room-303"),
                5_000, CausalRecord.Fidelity.PARTIAL);
        MindState.MemoryTrace changedUncertainty = trace(s, 1, 0, 12, 0, "door looked open",
                CausalRecord.Channel.TEXT, CausalRecord.Principal.unknown(), 4_999,
                CausalRecord.Fidelity.PARTIAL);
        MindState.MemoryTrace changedFidelity = trace(s, 1, 0, 12, 0, "door looked open",
                CausalRecord.Channel.TEXT, CausalRecord.Principal.unknown(), 5_000,
                CausalRecord.Fidelity.FULL);
        canonical(!Arrays.equals(new MindState(s, 1, List.of(a)).canonicalBytes(),
                new MindState(s, 1, List.of(changedChannel)).canonicalBytes()), "channel-covered");
        canonical(!Arrays.equals(new MindState(s, 1, List.of(a)).canonicalBytes(),
                new MindState(s, 1, List.of(changedSource)).canonicalBytes()), "source-covered");
        canonical(!Arrays.equals(new MindState(s, 1, List.of(a)).canonicalBytes(),
                new MindState(s, 1, List.of(changedUncertainty)).canonicalBytes()), "uncertainty-covered");
        canonical(!Arrays.equals(new MindState(s, 1, List.of(a)).canonicalBytes(),
                new MindState(s, 1, List.of(changedFidelity)).canonicalBytes()), "fidelity-covered");
        canonical(Arrays.equals(new MindState(s, 1, List.of(a)).canonicalBytes(),
                independentV3(s, a)), "independent-complete-v3-frame");
        canonical(!Arrays.equals(genesis.canonicalBytes(), MindState.initial(new CausalRecord.Subject("human-8")).canonicalBytes()), "subject-covered");

        refused(() -> new MindState(s, -1, List.of()), "negative-revision");
        refused(() -> new MindState(s, 0, List.of(a)), "future-memory");
        refused(() -> new MindState(s, 0, List.of(new MindState.MemoryTrace(
                new CausalRecord.MemoryRef(otherSubject(), 1, 0), a.basis(), a.interpretation()))), "foreign-memory-id");
        refused(() -> new MindState(s, 1, List.of(a, a)), "duplicate-memory");
        refused(() -> new MindState(s, 2, List.of(
                trace(s, 1, 0, 100, 0, "later"),
                trace(s, 2, 0, 10, 0, "earlier"))), "basis-time-cannot-reverse");
        refused(() -> new MindState(s, 2, List.of(
                trace(s, 1, 0, 12, 1, "second"),
                trace(s, 2, 0, 12, 0, "first"))), "basis-sequence-cannot-reverse");
        CausalRecord.Subject other = new CausalRecord.Subject("human-8");
        refused(() -> new MindState(s, 1, List.of(trace(other, 1, 0, 12, 0, "foreign"))), "foreign-basis");
        ArrayList<MindState.MemoryTrace> tooMany = new ArrayList<>();
        for (int i = 0; i <= MindState.MAX_HISTORY_V1; i++) tooMany.add(trace(s, 1, i, 12, i, "m" + i));
        refused(() -> new MindState(s, 1, tooMany), "bounded-history");

        Human sameNameA = human("Alex", 70); Human sameNameB = human("Alex", 71);
        lifecycle(!sameNameA.subject.equals(sameNameB.subject), "duplicate-name-distinct-subject");
        MindState owned = sameNameA.mindState();
        NeuralLink firstLink = link(sameNameA, 1);
        firstLink.closeClean();
        lifecycle(sameNameA.mindState() == owned && sameNameA.link() == null, "clean-disconnect-preserves");
        NeuralLink secondLink = link(sameNameA, 2);
        lifecycle(sameNameA.mindState() == owned && sameNameA.link() == secondLink, "reinsert-preserves");
        secondLink.severUnclean();
        lifecycle(sameNameA.mindState() == owned && !sameNameA.alive(), "unclean-death-preserves");
        Human killed = human("Killed", 72); MindState killedState = killed.mindState(); NeuralLink death = link(killed, 3);
        death.avatar.alive = false;
        lifecycle(death.observeDeath() && killed.mindState() == killedState, "avatar-death-preserves");

        World rigWorld = new World(new Rng(8), new EventBus(), new PlaceGraph(1_000, 1_000));
        BroadcastRig rig = new BroadcastRig();
        rig.beginSession(rigWorld, rigWorld.places().zones().get(0));
        Human crew = human("Crew", 73); MindState crewState = crew.mindState();
        NeuralLink pirate = rig.open(crew, rigWorld); rigWorld.flush();
        lifecycle(pirate != null && crew.mindState() == crewState, "broadcast-rig-reinsert-preserves");

        Human spared = human("Spared", 74); MindState sparedState = spared.mindState(); NeuralLink clause = link(spared, 4);
        java.lang.reflect.Field mark = NeuralLink.class.getDeclaredField("clause303"); mark.setAccessible(true); mark.setBoolean(clause, true);
        clause.avatar.alive = false;
        lifecycle(!clause.observeDeath() && clause.avatar.alive && spared.mindState() == sparedState, "room303-preserves");

        Simulation sim = new Simulation(42, null, null);
        Human resident = Probes.realWorld(sim).humans().get(0); MindState beforeReload = resident.mindState();
        try { java.util.Collections.reverse(Probes.realWorld(sim).humans()); lifecycle(false, "human-census-immutable"); }
        catch (UnsupportedOperationException expected) { lifecycle(true, "human-census-immutable"); }
        sim.commandReload();
        lifecycle(resident.mindState() == beforeReload, "matrix-reload-preserves");

        World viseWorld = new World(new Rng(9), new EventBus(), new PlaceGraph(1_000, 1_000));
        RealWorld vise = new RealWorld(viseWorld);
        Frames emptyFrames = new Frames(); vise.digestInto(emptyFrames);
        Human grown = vise.grow(); Frames grownFrames = new Frames(); vise.digestInto(grownFrames);
        canonical(emptyFrames.values.equals(List.of(0, 0)), "empty-census-framed");
        List<Integer> expectedFrames = new ArrayList<>(List.of(0, 1, grown.id,
                grown.mindState().canonicalBytes().length));
        for (byte value : grown.mindState().canonicalBytes()) expectedFrames.add(Byte.toUnsignedInt(value));
        canonical(grownFrames.values.equals(expectedFrames), "production-digest-covers-exact-mind-bytes");
        Human grown2 = vise.grow(); Frames ordered = new Frames(); vise.digestInto(ordered);
        List<Human> privateCensus = privateHumans(vise); java.util.Collections.reverse(privateCensus);
        Frames reversed = new Frames(); vise.digestInto(reversed);
        canonical(ordered.values.equals(reversed.values), "digest-normalizes-census-order");
        privateCensus.add(human("Duplicate", grown2.id));
        try { vise.digestInto(new Frames()); canonical(false, "digest-refuses-duplicate-id"); }
        catch (IllegalStateException expected) { canonical(true, "digest-refuses-duplicate-id"); }

        roster(MindState.class.isRecord() && java.lang.reflect.Modifier.isFinal(MindState.class.getModifiers()), "state-final-record");
        roster(MindState.class.getRecordComponents().length == 3, "state-components");
        List<java.lang.reflect.Method> decodeDoors = Arrays.stream(MindState.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("fromCanonicalBytes"))
                .toList();
        roster(decodeDoors.size() == 1
                && java.lang.reflect.Modifier.isPublic(decodeDoors.get(0).getModifiers())
                && java.lang.reflect.Modifier.isStatic(decodeDoors.get(0).getModifiers())
                && decodeDoors.get(0).getReturnType() == MindState.class
                && Arrays.equals(decodeDoors.get(0).getParameterTypes(),
                        new Class<?>[]{byte[].class}),
                "exact-v3-decoder-door");
        Set<String> humanApi = new TreeSet<>();
        for (java.lang.reflect.Method method : Human.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                humanApi.add(method.getName() + Arrays.toString(method.getParameterTypes()));
            }
        }
        roster(humanApi.equals(Set.of("alive[]", "link[]", "mindState[]", "sheet[]")), "exact-human-api-no-writer");
        java.lang.reflect.Field mindSlot = Human.class.getDeclaredField("mindState");
        java.lang.reflect.Field subjectSlot = Human.class.getDeclaredField("subject");
        roster(mindSlot.getType() == MindState.class
                && java.lang.reflect.Modifier.isPrivate(mindSlot.getModifiers())
                && !java.lang.reflect.Modifier.isStatic(mindSlot.getModifiers())
                && subjectSlot.getType() == CausalRecord.Subject.class
                && java.lang.reflect.Modifier.isPublic(subjectSlot.getModifiers())
                && java.lang.reflect.Modifier.isFinal(subjectSlot.getModifiers())
                && !java.lang.reflect.Modifier.isStatic(subjectSlot.getModifiers()),
                "exact-human-mind-and-subject-slots");
        roster(Human.class.getDeclaredConstructors().length == 1
                && Arrays.equals(Human.class.getDeclaredConstructors()[0].getParameterTypes(),
                        new Class<?>[]{String.class, Brain.class, matrix.realworld.Pod.class,
                                int.class, long.class, long.class}),
                "exact-human-constructor-roster");

        System.out.println("MIND_STATE_CENSUS cases=" + cases + " lifecycle=" + (cases - lifecycleFail)
                + " bounds_fail=" + boundsFail + " canonical_fail=" + canonicalFail + " roster_fail=" + rosterFail);
        Probes.leave("VERDICT MIND_STATES_HELD cases=" + cases + " cases_none=" + (cases == 0 ? 1 : 0)
                + " lifecycle_fail=" + lifecycleFail + " bounds_fail=" + boundsFail
                + " canonical_fail=" + canonicalFail + " roster_fail=" + rosterFail,
                cases > 0 && lifecycleFail + boundsFail + canonicalFail + rosterFail == 0);
    }

    private static MindState.MemoryTrace trace(CausalRecord.Subject s, long revision, int sequence,
            long tick, int percept, String text) {
        return trace(s, revision, sequence, tick, percept, text, CausalRecord.Channel.TEXT,
                CausalRecord.Principal.unknown(), 5_000, CausalRecord.Fidelity.PARTIAL);
    }
    private static MindState.MemoryTrace trace(CausalRecord.Subject s, long revision, int sequence,
            long tick, int percept, String text, CausalRecord.Channel channel,
            CausalRecord.Principal source, int uncertainty, CausalRecord.Fidelity fidelity) {
        return new MindState.MemoryTrace(new CausalRecord.MemoryRef(s, revision, sequence),
                new CausalRecord.PerceptRef(s, new CausalId.Percept(tick, percept)),
                new MindState.InterpretationV1(channel, new CausalRecord.Payload(text), source,
                        uncertainty, fidelity,
                        MindState.EpistemicStatus.UNRESOLVED,
                        CausalRecord.PresentedClaim.structured(
                                "fixture.memory", "presented")));
    }
    private static CausalRecord.Subject otherSubject() { return new CausalRecord.Subject("human-999"); }
    private static byte[] independentV3(CausalRecord.Subject subject, MindState.MemoryTrace trace) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        putInt(out, 3); putWord(out, subject.key().value()); putLong(out, 1); putInt(out, 1);
        putWord(out, trace.id().subject().key().value()); putLong(out, trace.id().revision());
        putInt(out, trace.id().sequence()); putWord(out, trace.basis().subject().key().value());
        putLong(out, trace.basis().id().tick()); putInt(out, trace.basis().id().sequence());
        MindState.InterpretationV1 value = trace.interpretation();
        putWord(out, value.channel().name()); putWord(out, value.presentedContent().text());
        putWord(out, value.perceivedSource().kind().name());
        putWord(out, value.perceivedSource().key().value());
        putInt(out, value.uncertaintyBasisPoints()); putWord(out, value.presentedFidelity().name());
        putWord(out, value.status().name());
        putWord(out, value.presentedClaim().claimClass().name());
        putWord(out, value.presentedClaim().claim().key().value());
        putWord(out, value.presentedClaim().position().key().value());
        return out.toByteArray();
    }
    private static void putWord(java.io.ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        putInt(out, bytes.length); out.writeBytes(bytes);
    }
    private static void putInt(java.io.ByteArrayOutputStream out, int value) {
        out.write(value >>> 24); out.write(value >>> 16); out.write(value >>> 8); out.write(value);
    }
    private static void putLong(java.io.ByteArrayOutputStream out, long value) {
        putInt(out, (int) (value >>> 32)); putInt(out, (int) value);
    }
    @SuppressWarnings("unchecked")
    private static List<Human> privateHumans(RealWorld real) throws Exception {
        java.lang.reflect.Field field = RealWorld.class.getDeclaredField("humans");
        field.setAccessible(true);
        return (List<Human>) field.get(real);
    }
    private static Human human(String name, int id) { return new Human(name, new Brain(name), null, id, 1, 0); }
    private static NeuralLink link(Human h, int id) {
        return new NeuralLink(h, new Avatar(id, new Position(0, 0), h.name, Pill.RED), LinkKind.PIRATE);
    }
    private static final class Frames implements StateSink {
        final List<Integer> values = new ArrayList<>();
        public void putInt(int value) { values.add(value); }
        public void putLong(long value) { values.add((int) (value ^ (value >>> 32))); }
        public void putCount(int value) { values.add(value); }
    }
    private static void held(boolean ok, String name) { cases++; if (!ok) boundsFail++; }
    private static void bounds(boolean ok, String name) { cases++; if (!ok) boundsFail++; }
    private static void lifecycle(boolean ok, String name) { cases++; if (!ok) lifecycleFail++; }
    private static void canonical(boolean ok, String name) { cases++; if (!ok) canonicalFail++; }
    private static void roster(boolean ok, String name) { cases++; if (!ok) rosterFail++; }
    private static void refused(Runnable r, String name) { cases++; try { r.run(); boundsFail++; } catch (IllegalArgumentException expected) { } }
}
