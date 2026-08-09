package matrix.core;

public final class Ansi {
    public static final String RESET = "\033[0m";
    public static final String BOLD = "\033[1m";
    public static final String GREEN = "\033[32m";
    public static final String BGREEN = "\033[92m";
    public static final String DGREEN = "\033[2;32m";
    public static final String BLUE = "\033[94m";
    public static final String RED = "\033[91m";
    public static final String WHITE = "\033[97m";
    public static final String GOLD = "\033[93m";
    public static final String MAG = "\033[95m";
    public static final String GRAY = "\033[90m";
    public static final String HOME_CLEAR = "\033[H\033[2J";

    private Ansi() {}

    public static String paint(String color, String s) {
        return color + s + RESET;
    }
}
