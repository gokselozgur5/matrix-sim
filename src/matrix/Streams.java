package matrix;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Who owns the charset the instrument lines are written in.
 *
 * <p>D-020 made the lines a BYTE contract — {@code LineLint} exists because
 * an instrument line must be the same line on every box — and until this
 * class nothing owned the encoding those bytes were produced in. A JVM
 * takes it from the environment: with no locale exported, JDK 17 resolves
 * the default charset to {@code ANSI_X3.4-1968}, and every non-ASCII
 * character in a printed line is silently replaced by {@code ?}. That is
 * D-027's reference box — the Debian cloud VM the budget table is measured
 * on — and any shell anywhere that has not exported one, which is why
 * {@code (the world holds its breath — zero draws)} arrives from
 * {@code DrawMeter} as {@code (the world holds its breath ? zero draws)}
 * there while a developer's UTF-8 shell shows the em dash. The line quoted
 * in a PR is then not the line the next box prints — the same defect class
 * the v1 skeptic round closed when it killed the {@code %n}/locale byte
 * instability, arriving through a different channel.
 *
 * <p>The hosted CI lane is deliberately NOT the witness here: GitHub's
 * {@code ubuntu-latest} image exports a UTF-8 locale, so the lane that has
 * to catch this defect is not itself a box that shows it. That is why the
 * charset lock in {@code locks.yml} forces {@code LC_ALL=C} rather than
 * trusting the runner's environment — it makes the hostile box instead of
 * hoping to be run on one.
 *
 * <p>{@link Simulation} was never exposed: it wraps its sink in a
 * {@code PrintStream} it constructs with an explicit UTF-8, so the METRIC,
 * DIGEST and event lines are already locale-proof. What was exposed is
 * everything printed by a {@code main} — the daemon's BENCH and PERF lines,
 * its {@code --help} page, its refusals on stderr, and every probe verdict.
 * Those all go through {@link System#out} and {@link System#err}, which the
 * JVM built before any of our code ran. So the fix is to rebuild them.
 *
 * <p>The replacement is the shape JDK 17 hands out for {@code System.out}
 * itself — a {@link BufferedOutputStream} of 8192 over the file descriptor,
 * autoflush on — with the charset passed in rather than inherited. Only the
 * charset changes: buffer size and flush points stay where the JDK put
 * them, so nothing here can move a timing measurement.
 *
 * <p>Not a {@code -Dfile.encoding} flag on the command line: every DoD in
 * this repository is a bare {@code java -cp out …} that a reader retypes,
 * and a contract that holds only when the reader remembers a flag is not a
 * contract. Not an ASCII-only line grammar either: the em dash and the
 * middle dot carry meaning in these lines (the district catalog separates
 * its columns with {@code ·}), and an encoding bug is not a reason to edit
 * the language.
 */
public final class Streams {

    /**
     * Pin {@code System.out} and {@code System.err} to UTF-8 for this
     * process. Call it as the first statement of a {@code main}, before
     * anything prints. Idempotent.
     */
    public static void utf8() {
        System.setOut(pin(FileDescriptor.out));
        System.setErr(pin(FileDescriptor.err));
    }

    private static PrintStream pin(FileDescriptor fd) {
        return new PrintStream(
                new BufferedOutputStream(new FileOutputStream(fd), 8192),
                true, StandardCharsets.UTF_8);
    }

    private Streams() {}
}
