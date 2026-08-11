import matrix.character.Contest;
import matrix.character.Family;
import matrix.character.Sheet;
import matrix.character.Sheets;

import java.util.List;
import java.util.Locale;

/**
 * Probe: what would the D-042 sheets BE? The stat kernel is imported by
 * nothing in the domain (the gate in #212 is open), so this bench is the
 * only place its numbers exist. Derives the named cast's sheets and plays
 * five famous cross-family contests as pure arithmetic — the rooftop, the
 * Architect's room, the overflow — so the gate thread argues over derived
 * values instead of prose. No Simulation, no seed, no draw: identity in,
 * fate out. Deterministic output — run it twice, diff nothing.
 *
 * Usage: java -cp out:probes/out SheetBench
 */
public final class SheetBench {

    public static void main(String[] args) {
        Sheet trinity = Sheets.derive("Trinity", Family.HUMAN);
        Sheet morpheus = Sheets.derive("Morpheus", Family.HUMAN);
        Sheet niobe = Sheets.derive("Niobe", Family.HUMAN);
        Sheet cypher = Sheets.derive("Cypher", Family.HUMAN);
        Sheet thomas = Sheets.derive("Thomas A. Anderson", Family.HUMAN);
        Sheet architect = Sheets.derive("the Architect", Family.SYSTEM);
        Sheet oracle = Sheets.derive("the Oracle", Family.PROGRAM);
        Sheet otto = Sheets.derive("Otto Aydin", Family.MACHINE);
        Sheet smith = Sheets.derive("Agent Smith", Family.PROGRAM);
        Sheet jones = Sheets.derive("Agent Jones", Family.PROGRAM);

        List<Sheet> cast = List.of(trinity, morpheus, niobe, cypher, thomas,
                architect, oracle, otto, smith, jones);
        for (Sheet sheet : cast) {
            System.out.println("SHEET " + sheet.line());
        }

        // Five cross-family scenes, as arithmetic. Every family appears at
        // least once; the two canonical ones (#212's own examples) lead.
        contest("rooftop", trinity, "evasion", jones, "privilege");
        contest("the-room", architect, "authority", thomas, "will");
        contest("the-overflow", smith, "replication", architect, "tolerance");
        contest("mechanical-line", niobe, "evasion", otto, "precision");
        contest("interrogation", morpheus, "will", smith, "purposeIntegrity");

        System.out.println("BENCH cast=" + cast.size() + " contests=5");
    }

    private static void contest(String scene, Sheet a, String axisA, Sheet b, String axisB) {
        System.out.println(String.format(Locale.ROOT,
                "CONTEST %s %s.%s=%d vs %s.%s=%d margin=%+d %s",
                scene, a.name(), axisA, a.stat(axisA), b.name(), axisB, b.stat(axisB),
                Contest.margin(a, axisA, b, axisB), Contest.resolve(a, axisA, b, axisB)));
    }

    private SheetBench() {}
}
