import matrix.Simulation;
import matrix.core.Config;
import matrix.core.World;
import matrix.entities.MatrixEntity;
import matrix.entities.Pill;
import matrix.entities.SmithCopy;
import matrix.machine.Source;
import matrix.realworld.Human;
import matrix.realworld.NeuralLink;
import matrix.realworld.RealWorld;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Probe: the four Confirmation clauses that were only ever prose (#819).
 *
 * <p>Most ADRs close with a Confirmation — the sentence that says how you
 * would know the decision held. Four of them describe a scripted scenario
 * cheap enough to run and nobody ever ran one: D-001's mass restore,
 * D-011's open door, D-021's perception stream, D-025's exile lifecycle.
 * The coverage matrix (#361) carried all four as NONE. Each clause is
 * quoted verbatim in the javadoc of the scenario that proves it, so a
 * reader can check the instrument against the sentence and not against a
 * paraphrase of it.
 *
 * <p><b>One universe, one pass.</b> The four scenarios are not four runs
 * and could not honestly be: at seed 42 the delete broadcast and the
 * treaty's open door are the SAME tick (4329) — D-001's restore is the
 * event D-011's opt-out rides on — the exile lifecycle D-025 describes
 * runs eight times before it, and D-021's stream runs the whole arc under
 * both. Four separate boots would be the same film screened four times to
 * read four of its frames.
 *
 * <p><b>What it reads.</b> The probe hands the Simulation its own sink and
 * drains it once per tick, so every clause is judged from the event log
 * the daemon actually printed — D-001's clause asks for exactly that ("event
 * log count match") — cross-checked against object identity where the log
 * cannot be trusted to be unambiguous: names are not unique (196 humans wear
 * 154 of them at seed 42), so the humans that walked out are matched by
 * reference, never by the name in their door line.
 *
 * <p>Read-only under the probe contract: the probe owns this Simulation and
 * never steers it. It ticks, it reads, it opens three private fields
 * through {@link Probes}, and it prints. It queues no WorldEvent and calls
 * no command.
 *
 * <p><b>Budget.</b> The scenarios need the full arc: below the treaty tick
 * two clauses have no event to judge and the verdict is
 * {@code CONFIRMATIONS_UNMET}, never a quiet pass. A scenario probe that
 * cannot tell "held" from "never happened" is worse than no probe.
 *
 * <p>Usage: java -cp out:probes/out ConfirmationSweep [ticks] [seed]
 */
public final class ConfirmationSweep {

    /**
     * D-021's subject, and the choice is not free. The clause promises a
     * frame per 100 ticks; the tap delivers that only for a mind nobody
     * hijacks and nobody frees. Following "Thomas A. Anderson" through the
     * same run prints 1,300 ticks of silence before the One is born and
     * 1,000 more after Smith takes him — the stream says nothing at all in
     * either window (#767).
     *
     * <p>"Nadia" binds to Nadia Petrov at seed 42, and she is the one
     * subject in this tree the documents already vouch for: the field
     * manual's case study follows her because on `main` she is never worn
     * and never freed. It was "Otto" — Otto Anderson, a sleeper in the film
     * as it stood — until #377 moved the film and Smith wrapped him at
     * t=3800, and the clause's own probe read 57 frames with a 400-tick
     * hole. The subject is a property of the universe and not of the
     * clause, so it is re-argued when the universe moves. Nadia is BLUE for
     * all 6,000 ticks in BOTH films, which is the strongest form of that
     * argument available without a second boot.
     */
    private static final String FOLLOW = "Nadia";

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        Tap tap = new Tap();
        Simulation sim = new Simulation(seed, tap, FOLLOW);
        World world = Probes.world(sim);
        RealWorld realWorld = Probes.realWorld(sim);
        Source source = Probes.source(sim);
        List<NeuralLink> links = Probes.links(realWorld);

        Restore restore = new Restore();
        OpenDoor door = new OpenDoor();
        Stream stream = new Stream(ticks);
        Lifecycle lifecycle = new Lifecycle();

        stream.read(tap.drain());
        for (long t = 1; t <= ticks; t++) {
            restore.before(world);
            door.before(links);
            sim.tickOnce();
            List<String> emitted = tap.drain();
            // The door is read FIRST, and the order is the finding of this
            // probe's own first run: at seed 42 the delete broadcast and the
            // treaty's open door land on the same tick (4329), and four of
            // the six minds that walk out are minds the broadcast restored
            // moments earlier. Restore has to know which avatars the door
            // took, or it reads four lawful liberations as four lost
            // originals — which is exactly what it did, first time out.
            door.after(links, realWorld, emitted);
            restore.after(world, emitted, door.walked());
            stream.read(emitted);
            lifecycle.read(emitted, t, source);
        }
        lifecycle.report();

        boolean restoreHeld = restore.print();
        boolean doorHeld = door.print();
        boolean streamHeld = stream.print();
        boolean lifecycleHeld = lifecycle.print();
        System.out.println("CONFIRMATIONS clauses=4 seed=" + seed + " ticks=" + ticks
                + " follow=\"" + FOLLOW + "\"");
        // A clause that was tested and failed outranks a clause that was never
        // reached: BROKEN is the louder fact, and a run that is both must not
        // report the quieter one. (The D-025 falsifier is exactly such a run —
        // moving the grace window moves the whole arc, so two rows lose their
        // scenario in the same pass that breaks the third.)
        boolean held = restoreHeld && doorHeld && streamHeld && lifecycleHeld;
        boolean broken = (restore.fired && !restoreHeld) || (door.fired && !doorHeld)
                || (stream.frames > 0 && !streamHeld)
                || (lifecycle.collections > 0 && !lifecycleHeld);
        System.out.println("VERDICT " + (held ? "CONFIRMATIONS_HELD"
                : broken ? "CONFIRMATIONS_BROKEN" : "CONFIRMATIONS_UNMET"));
    }

    // -----------------------------------------------------------------
    // D-001
    // -----------------------------------------------------------------

    /**
     * D-001's Confirmation, verbatim: "v2.0 DoD run: infect N entities,
     * delete all Smiths, assert every original returns (event log count
     * match); code review confirms no infection checks outside the Smith
     * classes."
     *
     * <p>The first half is a scenario and is mechanized here. The second
     * half is a grep over the tree, not a program pointed at a running
     * universe, and #361 rules that family out of the instrument matrix by
     * construction; it stays a lint's job.
     *
     * <p>The run supplies its own N: the probe does not stage infections,
     * it watches the ones the film produces and photographs the wrap set on
     * the tick before the delete broadcast. That photograph is exact rather
     * than lucky — the world does not step during the negotiation freeze
     * (MachineSystem calls advanceFrozen, not step), so no infection can
     * land between the last snapshot and the treaty.
     *
     * <p>Three numbers, and all three must agree: how many originals were
     * wrapped, how many of those very objects came back (identity, not
     * equality — the whole point of the Decorator is that the object
     * survives), and what the event log said it restored.
     *
     * <p><b>The tick is shared, so the accounting has to be.</b> This row
     * printed BROKEN on its first run — 409 of 413 — and the tree was
     * right and the instrument was wrong. The delete broadcast and the
     * treaty's open door are the same tick: the root opens the door the
     * moment the world enters PEACE, which the broadcast is what causes.
     * Four of the six minds that walked out at 4329 were minds the
     * broadcast had restored a few statements earlier, so four originals
     * returned to the world and left it again before the probe could look.
     * An original is therefore accounted for if it is PRESENT or if the
     * door took it on this tick; anything else is a lost original and
     * breaks the row.
     */
    private static final class Restore {
        private final List<MatrixEntity> wrapped = new ArrayList<>();
        boolean fired;
        private boolean held;
        private int present;
        private int viaDoor;
        private int count;
        private int logged;

        void before(World world) {
            if (fired) {
                return;
            }
            wrapped.clear();
            for (MatrixEntity e : world.entities()) {
                if (e.alive && e instanceof SmithCopy c) {
                    wrapped.add(c.original);
                }
            }
        }

        void after(World world, List<String> emitted, Map<MatrixEntity, Boolean> throughTheDoor) {
            if (fired) {
                return;
            }
            String line = messageContaining(emitted, "delete broadcast complete");
            if (line == null) {
                return;
            }
            fired = true;
            logged = intBefore(line, " originals restored");
            Map<MatrixEntity, Boolean> inWorld = new IdentityHashMap<>();
            for (MatrixEntity e : world.entities()) {
                inWorld.put(e, Boolean.TRUE);
            }
            count = wrapped.size();
            for (MatrixEntity original : wrapped) {
                if (inWorld.containsKey(original)) {
                    present++;
                } else if (throughTheDoor.containsKey(original)) {
                    viaDoor++;
                }
            }
            held = count > 0 && present + viaDoor == count && logged == count;
        }

        boolean print() {
            System.out.println("RESTORE wrapped=" + count + " present=" + present
                    + " walked_out=" + viaDoor + " log=" + logged);
            System.out.println("CONFIRM clause=D-001 restored=" + (present + viaDoor) + "/" + count
                    + " log=" + logged + " " + status(fired, held));
            return held;
        }
    }

    // -----------------------------------------------------------------
    // D-011
    // -----------------------------------------------------------------

    /**
     * D-011's Confirmation, verbatim: "After the treaty in a v3.0 run,
     * opted-out humans appear in the RealWorld registry (log line with
     * count) and no deletion events fire; unit check: jackOut leaves Human
     * alive with link=null."
     *
     * <p>{@code jackOut} is the one name in the four clauses that the tree
     * has never carried: the shipped door is {@link NeuralLink#closeClean},
     * which is what {@code RealWorld.optOut} calls. The behaviour the clause
     * describes is present under the other name, so this is a naming errata
     * rather than a missing mechanism — and it is checked here on the minds
     * that actually walked, not on a hand-built pair in a vise, because the
     * clause is about the treaty's door and not about the method in
     * isolation.
     *
     * <p>Who walked is decided by reference: the links that were open at the
     * top of the tick and are closed at the bottom of it with a living
     * avatar. The door's own tally line supplies the count the clause asks
     * for, and the two must agree — the log is evidence here, not authority,
     * because a door line names a human and 196 humans wear 154 names.
     *
     * <p>"No deletion events fire" is read literally, in the log: the count
     * of flatline lines on the tick the door opened. It is zero at seed 42,
     * and it is the number that would move if opt-out ever went back to
     * severing instead of closing clean.
     */
    private static final class OpenDoor {
        private final Map<NeuralLink, Boolean> openBefore = new IdentityHashMap<>();
        /** The avatars the door took this tick, by reference — D-001's row needs them (see {@link Restore}). */
        private final Map<MatrixEntity, Boolean> tookAvatars = new IdentityHashMap<>();
        boolean fired;
        private boolean held;
        private int walked;
        private int tally;
        private int registry;
        private int living;
        private int nullLinks;
        private int deletions;

        void before(List<NeuralLink> links) {
            if (fired) {
                return;
            }
            openBefore.clear();
            for (NeuralLink link : links) {
                openBefore.put(link, !link.closed());
            }
        }

        void after(List<NeuralLink> links, RealWorld realWorld, List<String> emitted) {
            if (fired) {
                return;
            }
            String line = messageContaining(emitted, "open door tally:");
            if (line == null) {
                return;
            }
            fired = true;
            tally = intBefore(line, " walked out");
            List<Human> out = new ArrayList<>();
            for (NeuralLink link : links) {
                if (Boolean.TRUE.equals(openBefore.get(link)) && link.closed()
                        && link.avatar.alive && link.avatar.pill == Pill.BLUE) {
                    out.add(link.human);
                    tookAvatars.put(link.avatar, Boolean.TRUE);
                }
            }
            walked = out.size();
            Map<Human, Boolean> census = new IdentityHashMap<>();
            for (Human h : realWorld.humans()) {
                census.put(h, Boolean.TRUE);
            }
            for (Human h : out) {
                if (census.containsKey(h)) {
                    registry++;
                }
                if (h.alive()) {
                    living++;
                }
                if (h.link() == null) {
                    nullLinks++;
                }
            }
            for (String emittedLine : emitted) {
                String msg = message(emittedLine);
                if (msg != null && msg.contains("the body cannot live without the mind")) {
                    deletions++;
                }
            }
            held = walked > 0 && walked == tally && registry == walked
                    && living == walked && nullLinks == walked && deletions == 0;
        }

        Map<MatrixEntity, Boolean> walked() {
            return tookAvatars;
        }

        boolean print() {
            System.out.println("CONFIRM clause=D-011 optouts=" + walked + " deletions=" + deletions
                    + " null_links=" + nullLinks + " tally=" + tally + " registry=" + registry
                    + " alive=" + living + " " + status(fired, held));
            return held;
        }
    }

    // -----------------------------------------------------------------
    // D-021
    // -----------------------------------------------------------------

    /**
     * D-021's Confirmation, verbatim: "--follow on a v1 run emits at least
     * one frame per 100 ticks with tick, pilot, pill, position and
     * nearest-agent/exit distances; events-in-earshot is deferred to the
     * v4.0 full stream (errata 2026-08-10, skeptic finding: the v1 design
     * has no event positions to hear from — promising it was a lie in a
     * Confirmation)."
     *
     * <p>"At least one frame per 100 ticks" is a claim about GAPS, so gaps
     * are what this measures — including the two a frame count cannot see.
     * The head gap runs from tick 0 to the first frame: a tap that arms late
     * emitted nothing for that whole window. The tail gap runs from the last
     * frame to the end of the run: a stream that stops at 4,300 and is never
     * heard from again passes any count test and fails the clause. Both are
     * folded into max_gap, so one number carries the whole sentence.
     *
     * <p>The five fields are checked on every frame rather than on the first.
     * {@code nearestAgentCm} is the one that can vanish — PerceptionFrame
     * omits it when the world holds no agent — so a frame missing it is a
     * malformed frame here, which is what the clause says it is.
     *
     * <p>The signal lines ("ended", "lost") are counted and are deliberately
     * NOT frames: they carry neither a pill nor a position, and treating
     * them as frames would let a dead stream satisfy a clause about a live
     * one.
     */
    private static final class Stream {
        private final long ticks;
        int frames;
        private long last;
        private long maxGap;
        private int signals;
        private int malformed;
        private String subject = "-";

        Stream(long ticks) {
            this.ticks = ticks;
        }

        void read(List<String> emitted) {
            for (String line : emitted) {
                if (!line.startsWith("{\"tick\":")) {
                    continue;
                }
                if (line.contains("\"signal\":")) {
                    signals++;
                    continue;
                }
                frames++;
                if (subject.equals("-")) {
                    subject = between(line, "\"who\":\"", "\"");
                }
                if (!(line.contains("\"who\":\"") && line.contains("\"pill\":\"")
                        && line.contains("\"pos\":[") && line.contains("\"nearestAgentCm\":")
                        && line.contains("\"nearestExitCm\":"))) {
                    malformed++;
                }
                long tick = Long.parseLong(between(line, "{\"tick\":", ","));
                maxGap = Math.max(maxGap, tick - last);
                last = tick;
            }
        }

        boolean print() {
            long gap = Math.max(maxGap, ticks - last);
            boolean held = frames > 0 && malformed == 0 && gap <= Config.FOLLOW_EVERY_TICKS;
            System.out.println("CONFIRM clause=D-021 frames=" + frames + " max_gap=" + gap
                    + " signals=" + signals + " malformed=" + malformed
                    + " subject=\"" + subject + "\" " + status(frames > 0, held));
            return held;
        }
    }

    // -----------------------------------------------------------------
    // D-025
    // -----------------------------------------------------------------

    /**
     * D-025's Confirmation, verbatim: "A v2.0 run shows: deprecation notice
     * → grace window → compliance (GC line) or refusal (orphan registered);
     * the mythology event cites the registry count."
     *
     * <p>Every SIGTERM in the run is matched to its ending, and the ending
     * must arrive exactly {@code GRACE_TICKS} later — not "at least", exactly,
     * because the notice announces the number and an announced grace period
     * that is not the enforced one is the failure this clause exists to
     * catch. The announced number is parsed out of the notice line itself
     * and compared with {@link Config#GRACE_TICKS}, so the sentence the log
     * speaks and the constant the Source obeys are checked against each
     * other rather than both against nothing.
     *
     * <p>The clause names two endings; the tree has four. Compliance prints
     * the GC line, quiet survival registers an orphan, a thrown refusal
     * forks (D-003 — the case the constitution said could never happen), and
     * a collection whose target is no longer itself is voided. All four are
     * accepted as endings and counted separately; an ending nobody can
     * classify is what fails the row.
     *
     * <p>"The mythology event cites the registry count" is checked against
     * the live registry: the number in the MYTH line must equal
     * {@code registry.count()} read at the end of that same tick. It does.
     *
     * <p>The other side of that equality is whether every citation that was
     * DUE was made, and the {@code cite} column could not ask it while one
     * blank stood for three unrelated situations: an ending with no ledger
     * number to cite (gc, voided), an ending that added nothing to the ledger
     * (a name already on it), and an ending that added an entry and cited
     * nothing. Only the third is a defect, and it is #951 — which survived a
     * full run of this probe because its row was indistinguishable from its
     * two lawful neighbours. The column now separates them: {@code -} is
     * nothing was due, {@code repeat} is nothing NEW was due, and
     * {@code MISSING} is the defect, wearing a word rather than a blank.
     *
     * <p>Due-ness is read from the ENDING and not from the citation whose
     * absence it is asking about: orphan and refusal are the two paths that
     * register, and the ledger admits a name once ({@code OrphanRegistry} is
     * a census, not an event tally), so this probe keeps its own copy of that
     * rule and a name already in it is due nothing. The summary then counts
     * citations due against citations made. It used to subtract citations
     * from the FINAL registry size, which a repeat is invisible to in both
     * terms — that difference reached zero on this film by arithmetic rather
     * than by construction, and would have kept reaching zero on a run where
     * a repeat masked a genuinely uncited fresh registration. {@code orphans}
     * and {@code due} printed side by side are the same question asked of the
     * live ledger and of this probe's model of it, and the row demands they
     * agree.
     */
    private static final class Lifecycle {
        private record Notice(String purpose, long tick, int announced) {}

        private final List<Notice> pending = new ArrayList<>();
        /** This probe's copy of the ledger's own rule: one entry per name, however often it survives. */
        private final Set<String> ledger = new LinkedHashSet<>();
        int collections;
        private int gc;
        private int orphans;
        private int refusals;
        private int voided;
        private int graceWrong;
        private int cites;
        private int citesOk;
        private int repeats;
        private int missing;
        private long registryEnd;

        void read(List<String> emitted, long t, Source source) {
            for (String line : emitted) {
                String msg = message(line);
                if (msg == null) {
                    continue;
                }
                resolve(msg, t, source);
            }
            for (String line : emitted) {
                String msg = message(line);
                if (msg != null && msg.startsWith("the Source: SIGTERM sent to \"")) {
                    collections++;
                    pending.add(new Notice(between(msg, "sent to \"", "\""), t,
                            intBefore(msg, " ticks")));
                }
            }
            registryEnd = source.registry().count();
        }

        /** One tick's messages against the open notices: the first ending that names a target closes it. */
        private void resolve(String msg, long t, Source source) {
            for (int i = 0; i < pending.size(); i++) {
                Notice n = pending.get(i);
                String ending = classify(msg, n.purpose(), pending.size() == 1);
                if (ending == null) {
                    continue;
                }
                pending.remove(i);
                long grace = t - n.tick();
                if (grace != Config.GRACE_TICKS || n.announced() != Config.GRACE_TICKS) {
                    graceWrong++;
                }
                long registry = source.registry().count();
                // Which blank this is, decided before the citation is looked
                // for: the two registering endings owe a number the first time
                // they name a target and owe nothing every time after.
                boolean registers = ending.equals("orphan") || ending.equals("refusal");
                boolean fresh = registers && ledger.add(n.purpose());
                String cite;
                if (msg.contains("orphan #")) {
                    cites++;
                    long cited = intBefore(msg, " registered");
                    cite = Long.toString(cited);
                    if (cited == registry) {
                        citesOk++;
                    }
                } else if (fresh) {
                    cite = "MISSING";
                    missing++;
                } else if (registers) {
                    cite = "repeat";
                    repeats++;
                } else {
                    cite = "-";
                }
                switch (ending) {
                    case "gc" -> gc++;
                    case "orphan" -> orphans++;
                    case "refusal" -> refusals++;
                    default -> voided++;
                }
                System.out.println("LIFECYCLE notice=" + n.tick() + " ending=" + t
                        + " grace=" + grace + " announced=" + n.announced()
                        + " target=\"" + n.purpose() + "\" outcome=" + ending
                        + " cite=" + cite + " registry=" + registry);
                return;
            }
        }

        /**
         * The four endings, each recognized by the sentence the tree prints.
         * The refusal that forks names nobody — the three BAD lines are about
         * Smith, not about a purpose string — so it is claimed only when a
         * single collection is open, which is the only condition under which
         * the attribution is not a guess.
         */
        private static String classify(String msg, String purpose, boolean alone) {
            if (msg.startsWith("GC: \"" + purpose + "\" returned to the Source")) {
                return "gc";
            }
            if (msg.contains("\"" + purpose + "\" survived collection")) {
                return "orphan";
            }
            if (msg.startsWith("deletion refused by \"" + purpose + "\"")) {
                return "refusal";
            }
            if (msg.contains("collection of \"" + purpose + "\" voided")) {
                return "voided";
            }
            if (alone && msg.startsWith("DeletionRefusedException reached the Source")) {
                return "refusal";
            }
            return null;
        }

        void report() {
            System.out.println("REGISTRY orphans=" + registryEnd + " due=" + ledger.size()
                    + " cited=" + cites + " repeat=" + repeats + " missing=" + missing);
        }

        boolean print() {
            // Citations due against citations made, and both against the live
            // ledger. The two directions are demanded separately rather than as
            // one difference: a repeat that printed a number would inflate
            // `cited` exactly as far as a missing one deflates it, which is how
            // a single subtraction can read zero over two defects.
            boolean citations = missing == 0 && cites == ledger.size()
                    && registryEnd == ledger.size() && citesOk == cites;
            boolean held = collections > 0 && pending.isEmpty() && graceWrong == 0
                    && cites > 0 && citations;
            System.out.println("CONFIRM clause=D-025 collections=" + collections
                    + " order=notice,grace,outcome gc=" + gc + " orphan=" + orphans
                    + " refusal=" + refusals + " voided=" + voided
                    + " unmatched=" + pending.size() + " grace_wrong=" + graceWrong
                    + " cites=" + citesOk + "/" + cites + " missing=" + missing
                    + " " + status(collections > 0, held));
            return held;
        }
    }

    // -----------------------------------------------------------------
    // The sink, and the small readers over it
    // -----------------------------------------------------------------

    /**
     * The Simulation's own output, taken a line at a time. The daemon owns
     * its encoding end to end (EventLog: UTF-8, Locale.ROOT, explicit \n),
     * so splitting on the newline byte is safe — 0x0A cannot occur inside a
     * UTF-8 continuation byte. Nothing here reaches a file or the console:
     * the probe prints its own lines and only its own.
     */
    private static final class Tap extends OutputStream {
        private final ByteArrayOutputStream line = new ByteArrayOutputStream(256);
        private final List<String> lines = new ArrayList<>();

        @Override
        public void write(int b) {
            if (b == '\n') {
                lines.add(new String(line.toByteArray(), StandardCharsets.UTF_8));
                line.reset();
            } else {
                line.write(b);
            }
        }

        List<String> drain() {
            List<String> out = List.copyOf(lines);
            lines.clear();
            return out;
        }
    }

    /** An event line's message, or null for the instrument lines and the JSONL frames. */
    private static String message(String line) {
        return line.length() > 15 && line.charAt(0) == '[' && line.charAt(7) == ']'
                ? line.substring(15) : null;
    }

    private static String messageContaining(List<String> emitted, String needle) {
        for (String line : emitted) {
            String msg = message(line);
            if (msg != null && msg.contains(needle)) {
                return msg;
            }
        }
        return null;
    }

    /** The integer immediately left of a phrase — the shape every count in these lines takes. */
    private static int intBefore(String text, String phrase) {
        int end = text.indexOf(phrase);
        if (end < 0) {
            return -1;
        }
        int start = end;
        while (start > 0 && Character.isDigit(text.charAt(start - 1))) {
            start--;
        }
        return start == end ? -1 : Integer.parseInt(text.substring(start, end));
    }

    private static String between(String text, String open, String close) {
        int from = text.indexOf(open);
        if (from < 0) {
            return "-";
        }
        from += open.length();
        int to = text.indexOf(close, from);
        return to < 0 ? "-" : text.substring(from, to);
    }

    /** A row that never met its scenario is unmet, not held — and never quietly ok. */
    private static String status(boolean reached, boolean held) {
        return !reached ? "unmet" : held ? "ok" : "BROKEN";
    }

    private ConfirmationSweep() {}
}
