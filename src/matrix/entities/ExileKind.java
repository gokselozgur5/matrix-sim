package matrix.entities;

/** The mythology roster: deprecated features of earlier Matrix versions, still running. */
public enum ExileKind {
    MEROVINGIAN("the Merovingian"),
    TWIN("the Twins"),
    VAMPIRE("vampire v2.1"),
    WEREWOLF("werewolf v1.9"),
    KEYMAKER("the Keymaker"),
    TRAINMAN("the Trainman");

    public final String label;

    ExileKind(String label) {
        this.label = label;
    }
}
