/**
 * The third feed's vocabulary: which lines of the record are the WORLD's, as
 * opposed to the pilot's or the day's weather.
 *
 * A beat is history happening elsewhere — it plays out whether or not the
 * sleeper ever hears it, and it is the only reason a page about somebody to
 * whom nothing happened is worth reading at all. The kinds below are the film's
 * arc as the daemon currently writes it, in the order it writes them; the kind
 * is also what lets the fold hang an aside on a beat later ("an anomaly's birth
 * does not render on a sleeper's glass") without the voice ever touching a
 * fact.
 *
 * Matching is on the record's own words. That is exactly as brittle as it
 * sounds, and it is a known debt of this slice: under a later regime these
 * lines may not fire at all, and a reader whose backdrop is a closed list would
 * quietly narrate a day against a war that never happened. The parent unit
 * (#353) owns making the backdrop derive from the record instead of from this
 * table.
 */
final class Beats {

    /** The kind of world-beat this line is, or null when the line is not one. */
    static String kindOf(String msg) {
        if (msg.startsWith("The One is born — ")) return "one_born";
        if (msg.contains("agent Smith deprecated")) return "deprecation";
        if (msg.startsWith("Smith: \"I knew what I was supposed to do")) return "refusal";
        if (msg.startsWith("SmithPrime online")) return "fork";
        if (msg.startsWith("Smith consumed the Oracle")) return "oracle_eaten";
        if (msg.startsWith("SMITH OVERFLOW")) return "overflow";
        if (msg.startsWith("EMERGENCY RELOAD")) return "emergency";
        if (msg.startsWith("The One flies to Machine City")) return "flight";
        if (msg.startsWith("Deus Ex Machina:")) return "deus";
        if (msg.startsWith("The One: \"Peace.\"")) return "peace_word";
        if (msg.startsWith("the machines accept")) return "accept";
        if (msg.startsWith("delete broadcast complete")) return "broadcast";
        if (msg.startsWith("The One is carried to the Source")) return "one_carried";
        if (msg.startsWith("REBOOT v")) return "reboot";
        if (msg.contains("cycle begins again; nobody remembers")) return "new_cycle";
        if (msg.startsWith("Sati paints the sunrise")) return "sati";
        if (msg.startsWith("open door tally:")) return "door_tally";
        if (msg.contains("joins the fleet")) return "hull";
        if (msg.startsWith("the peace settles into routine")) return "peace_routine";
        return null;
    }

    private Beats() {}
}
