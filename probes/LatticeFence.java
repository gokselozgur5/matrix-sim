import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Probe: is the dependency direction still the direction, and does anything
 * check? (#1417)
 *
 * <p>{@code docs/ARCHITECTURE.md} states three structural laws and names the
 * instrument that keeps them:
 *
 * <blockquote>Dependency direction is law, <b>verified by grep</b>:
 * {@code entities} imports nothing from {@code realworld} (the only bridge is
 * {@code NeuralLink}, which lives on the real-world side and reaches in);
 * {@code World} holds no real-world objects; nothing depends on
 * {@code Main}.</blockquote>
 *
 * <p>There was no grep. Nothing in {@code probes/}, {@code tools/} or the
 * workflows read any of the three, and all three were true — which is the
 * whole reason for this file. {@code SheetFence} names the shape: a law that
 * is true by luck reads exactly like a law that is enforced, right up until
 * the first commit that breaks it, and this tree has found it five times
 * elsewhere (#1203, #1212, #1210, #1233, #1243).
 *
 * <p>This is the sixth and the widest. The other five guarded one wing or one
 * field; these three sentences are the shape of the whole domain. The thesis
 * this repository exists to demonstrate — <i>the mind is never uploaded</i> —
 * IS the {@code entities}/{@code realworld} split, and nothing would have
 * noticed the import that ends it.
 *
 * <p><b>Clause 2 is shallow, and says so.</b> <i>{@code World} holds no
 * real-world objects</i> is not an import question: a field typed
 * {@code Object}, or a collection of a shared supertype, holds one without
 * naming one. What is read is the DECLARED TYPE of each field, which catches
 * every way the law has ever been broken in this tree and does not pretend to
 * catch a laundered one. A deeper answer needs a parser and D-009 refuses the
 * dependency.
 *
 * <p><b>Comments are stripped before every count.</b> This file's own subject
 * is a sentence about imports, and {@code src/matrix/package-info.java} says
 * {@code {@link matrix.Main}} out loud while explaining what {@code Main} is
 * for — documentation, not a dependency. A checker that finds its own subject
 * is the shape {@code SheetFence} was bitten by four separate times.
 *
 * <p>{@code --root DIR} points the whole reading at a scratch tree, which is
 * how each clause was seen to go red before this row was added.
 */
public final class LatticeFence {

    private static final String DEFAULT_ROOT = "src";

    /** The real-world side, by name. A field of any of these types is clause 2's finding. */
    private static final List<String> REAL_WORLD_TYPES =
            List.of("Brain", "Pod", "PodFarm", "NeuralLink", "Human", "RealWorld");

    public static void main(String[] args) throws IOException {
        // Clause 7 of the probe contract, and lock 8 is its keeper: a probe's
        // first statement pins its streams, because a verdict quoted in a PR
        // must be the bytes another box prints (#836, #965). This file shipped
        // without it and the lane's scan named it — which is the check working
        // on the day the probe arrived.
        matrix.Streams.utf8();
        String root = DEFAULT_ROOT;
        for (int i = 0; i < args.length; i++) {
            if ("--root".equals(args[i])) {
                if (++i == args.length) {
                    System.exit(Probes.Outcome.REFUSED.code());
                }
                root = args[i];
            } else {
                System.exit(Probes.Outcome.REFUSED.code());
            }
        }

        List<Path> files = javaFiles(Path.of(root));
        int swept = 0;
        int entitiesReach = 0;
        int worldHolds = 0;
        int mainDepended = 0;
        int worldFields = 0;
        List<String> offences = new ArrayList<>();

        Path entitiesDir = Path.of(root, "matrix", "entities");
        for (Path file : files) {
            swept++;
            List<String> code = Probes.uncommentedLines(file);

            // CLAUSE 1 — the bridge is one-way. `entities` may not reach out.
            if (file.startsWith(entitiesDir)) {
                for (String line : code) {
                    if (line.contains("import matrix.realworld")) {
                        entitiesReach++;
                        offences.add("LATTICE reach " + file + " imports the real world");
                    }
                }
            }

            // CLAUSE 3 — nothing depends on the composition root's entry point.
            if (!file.getFileName().toString().equals("Main.java")) {
                for (String line : code) {
                    if (line.contains("matrix.Main") || line.matches(".*\\bMain\\s*\\..*")) {
                        mainDepended++;
                        offences.add("LATTICE main " + file + " depends on Main");
                    }
                }
            }

            // CLAUSE 2 — World holds no real-world object, read off declared types.
            if (file.endsWith(Path.of("core", "World.java"))) {
                for (String line : code) {
                    String field = fieldDeclaration(line);
                    if (field == null) {
                        continue;
                    }
                    worldFields++;
                    for (String type : REAL_WORLD_TYPES) {
                        if (field.matches(".*\\b" + type + "\\b.*")) {
                            worldHolds++;
                            offences.add("LATTICE world field of type " + type + ": " + field.trim());
                            break;
                        }
                    }
                }
            }
        }

        for (String offence : offences) {
            System.out.println(offence);
        }

        // The population rides the census and never the verdict (#1221): `swept=`
        // moves whenever a class is added, and a count on an exact-line row is a
        // number people edit until the lane is quiet.
        System.out.println("LATTICE_CENSUS swept=" + swept + " world_fields=" + worldFields);

        // `swept_none=` is on the VERDICT, because a reading that opened no file
        // must not print the same line as a tree with no offences — the emptiness
        // is the finding and the count is a description (#1207, #970).
        boolean held = entitiesReach == 0 && worldHolds == 0 && mainDepended == 0 && swept > 0;
        Probes.leave(String.format(
                "VERDICT LATTICE_HELD entities_reach=%d world_holds=%d main_depended=%d swept_none=%d",
                entitiesReach, worldHolds, mainDepended, swept == 0 ? 1 : 0), held);
    }

    /**
     * A field declaration's type-and-name, or null. Deliberately narrow: a
     * member line ending in {@code ;} that is not a call, a return or a local
     * inside a block. Locals are missed and that is correct — a local does not
     * hold anything past the method.
     */
    private static String fieldDeclaration(String line) {
        String trimmed = line.strip();
        if (!trimmed.endsWith(";") || trimmed.startsWith("return") || trimmed.startsWith("import")
                || trimmed.startsWith("package") || trimmed.contains("(")) {
            return null;
        }
        if (!trimmed.startsWith("private") && !trimmed.startsWith("public")
                && !trimmed.startsWith("protected") && !trimmed.startsWith("final")
                && !trimmed.startsWith("static")) {
            return null;
        }
        return trimmed;
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }


    private LatticeFence() {}
}
