import java.util.ArrayList;
import java.util.List;

/**
 * D-047's voice: the narrator, as its own layer over {@link Fold}.
 *
 * <b>The boundary is absolute: the voice touches only strings, never facts.</b>
 * It is handed a fact stream and hands back a page; it cannot reach the
 * capture, cannot re-read the record, and has no arithmetic of its own beyond
 * centimetres to metres, which is the record's own unit change and lives here
 * once. That is why the voice is a separate layer rather than prose mixed into
 * the fold: a voice mixed in cannot be swapped, and — worse — cannot be
 * audited. The check is structural rather than editorial: the fact stream under
 * two different voices is byte-identical, so a narrator that quietly rounds a
 * distance or drops a tick is caught by a diff instead of by a careful reader.
 *
 * <ul>
 *   <li>{@link #COLD} — system-cold with the log's own dry wit; v1, the ADR's
 *       leaning, and what ships as the default.</li>
 *   <li>{@link #NONE} — the fold stated flatly, one fact per line under its
 *       movement. Not a debug mode: it is the second voice that makes the
 *       boundary provable, and it is the page a machine should read.</li>
 * </ul>
 *
 * The Oracle narrating (D-043) is a later unit, and it must arrive as another
 * string table over these same facts — never as a second reader.
 */
abstract class Voice {

    static final int WIDTH = 72;
    static final String RULE_HEAVY = "=".repeat(WIDTH);
    static final String RULE_LIGHT = "-".repeat(WIDTH);

    /** Render the whole page. Same facts in, same bytes out, forever. */
    abstract String render(List<Fold.Fact> facts);

    static Voice named(String name) {
        return switch (name) {
            case "cold" -> COLD;
            case "none" -> NONE;
            default -> null;
        };
    }

    // ------------------------------------------------------------ the plumbing

    static long metres(String cm) {
        return Long.parseLong(cm) / 100;
    }

    static String plural(String word, long n) {
        return n == 1 ? word : word + "s";
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Close a folded line as a sentence — without doubling the record's own stop. */
    static String stop(String msg) {
        return msg.endsWith(".") || msg.endsWith(".\"") || msg.endsWith("?\"") || msg.endsWith("!\"")
                ? msg : msg + ".";
    }

    static void para(StringBuilder out, String text) {
        wrap(out, text, "", "");
        out.append('\n');
    }

    static void quote(StringBuilder out, String text) {
        wrap(out, text, "  | ", "  | ");
        out.append('\n');
    }

    /** Deterministic greedy wrap at WIDTH columns; single spaces only. */
    static void wrap(StringBuilder out, String text, String first, String rest) {
        String indent = first;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (line.length() == 0) {
                line.append(indent).append(word);
            } else if (line.length() + 1 + word.length() <= WIDTH) {
                line.append(' ').append(word);
            } else {
                out.append(line).append('\n');
                indent = rest;
                line.setLength(0);
                line.append(indent).append(word);
            }
        }
        if (line.length() > 0) {
            out.append(line).append('\n');
        }
    }

    static Fold.Fact find(List<Fold.Fact> facts, String movement, String kind) {
        for (Fold.Fact f : facts) {
            if (f.movement().equals(movement) && f.kind().equals(kind)) {
                return f;
            }
        }
        return null;
    }

    // ------------------------------------------------------------- the voices

    /** The fold, stated flatly: the machine's page, and the voice layer's control group. */
    static final Voice NONE = new Voice() {
        @Override
        String render(List<Fold.Fact> facts) {
            StringBuilder out = new StringBuilder(1 << 14);
            Fold.Fact subject = find(facts, "HEAD", "subject");
            out.append("DREAM READER — the fold, unvoiced\n");
            out.append("pilot: ").append(subject.arg(0))
                    .append(" · resolved: ")
                    .append(subject.arg(1).isEmpty() ? "(nobody)" : subject.arg(1))
                    .append(" · seed ").append(subject.arg(2))
                    .append(" · ticks ").append(subject.arg(3)).append("\n\n");
            String movement = "";
            for (Fold.Fact f : facts) {
                if (f.movement().equals("HEAD")) {
                    continue;
                }
                if (!f.movement().equals(movement)) {
                    movement = f.movement();
                    out.append(movement.equals("END") ? "END.\n" : movement + ". " + title(movement) + "\n");
                }
                out.append("  ").append(f.kind());
                if (f.tick() >= 0) {
                    out.append(" t=").append(f.tick());
                }
                for (String a : f.args()) {
                    out.append(" | ").append(a);
                }
                out.append('\n');
            }
            return out.toString();
        }
    };

    static String title(String movement) {
        return switch (movement) {
            case Fold.I -> "THE SLEEPER'S MORNING";
            case Fold.II -> "THE WARS SEEN FROM A WINDOW";
            case Fold.III -> "THE DOOR, IF THEY WALKED";
            case Fold.IV -> "THE DARK, IF THE WIRE CUT";
            default -> movement;
        };
    }

    /** System-cold, with the log's own dry wit — v1, and every joke load-bearing. */
    static final Voice COLD = new Cold();

    private static final class Cold extends Voice {

        /**
         * Whether the page has already admitted the window was dark. Saying it
         * once is a sentence; saying it at every beat before the feed opens is
         * a stutter — and choosing not to repeat a sentence is the only kind of
         * decision a voice is allowed to make. Reset per render; the tool is
         * one page, one process, one thread.
         */
        private boolean darkWindowSaid;

        @Override
        String render(List<Fold.Fact> facts) {
            darkWindowSaid = false;
            StringBuilder out = new StringBuilder(1 << 15);
            Fold.Fact subject = find(facts, "HEAD", "subject");
            Fold.Fact nobody = find(facts, Fold.I, "nobody");
            if (nobody != null) {
                masthead(out, subject, "the record holds nobody by that name.");
                para(out, "The follow tap reports:");
                quote(out, "follow: no pilot matches '" + nobody.arg(0) + "'");
                long near = Long.parseLong(nobody.arg(1));
                para(out, "Names are grown, not chosen. "
                        + (near == 0
                                ? "No mind in this record carries that string at all."
                                : "The record holds " + near + plural(" mind", near)
                                        + " whose name carries it, and not one of them was on a "
                                        + "live wire when the tap armed.")
                        + " Try another name, or another seed. This page holds no one.");
                out.append(RULE_LIGHT).append('\n');
                para(out, "END OF DAY — nobody's. Same seed, same absence, byte for byte.");
                return out.toString();
            }

            masthead(out, subject, provenance(facts));
            movement(out, facts, Fold.I);
            movement(out, facts, Fold.II);
            movement(out, facts, Fold.III);
            movement(out, facts, Fold.IV);
            out.append(RULE_LIGHT).append('\n');
            para(out, "END OF DAY — one mind, folded from the record. Same seed, same day, "
                    + "byte for byte; there is no spoon.");
            return out.toString();
        }

        private void masthead(StringBuilder out, Fold.Fact subject, String info) {
            out.append(RULE_HEAVY).append('\n');
            String title = "THE DREAM READER — one mind's day, off the wire";
            String tag = "D-047";
            out.append(title)
                    .append(" ".repeat(Math.max(1, WIDTH - title.length() - tag.length())))
                    .append(tag).append('\n');
            out.append(RULE_HEAVY).append('\n');
            String who = subject.arg(1).isEmpty() ? subject.arg(0) : subject.arg(1);
            para(out, "pilot: " + who + " · seed " + subject.arg(2) + " · ticks " + subject.arg(3));
            para(out, info);
            out.append(RULE_HEAVY).append("\n\n");
        }

        private String provenance(List<Fold.Fact> facts) {
            Fold.Fact p = find(facts, "HEAD", "provenance");
            long frames = p.num(0), signals = p.num(1), naming = p.num(2), beats = p.num(3);
            StringBuilder s = new StringBuilder("folded from ");
            s.append(frames).append(plural(" frame", frames));
            if (signals > 0) {
                s.append(", ").append(signals).append(plural(" signal", signals));
            }
            s.append(", ").append(naming).append(plural(" line", naming))
                    .append(" naming them, and ").append(beats)
                    .append(" beats of the world's own log. every sentence derives from the "
                            + "record; where the feed is silent, the silence is written down.");
            Fold.Fact b = find(facts, "HEAD", "binding");
            if (b != null && b.num(0) > 1) {
                s.append(' ').append(b.num(0)).append(" minds in this record answer to that name, "
                        + "so the name is not a binding here: the page keeps a line only where "
                        + "the record proves it is this one's");
                s.append(b.arg(1).isEmpty() ? "" : " — pod " + b.arg(1));
                if (b.num(2) > 0) {
                    s.append(", and ").append(b.num(2))
                            .append(plural(" line", b.num(2)))
                            .append(" proved to be a namesake's and were dropped");
                }
                s.append('.');
            }
            return s.toString();
        }

        private void movement(StringBuilder out, List<Fold.Fact> facts, String movement) {
            out.append(movement).append(". ").append(title(movement)).append("\n\n");
            StringBuilder p = new StringBuilder();
            for (Fold.Fact f : facts) {
                if (!f.movement().equals(movement)) {
                    continue;
                }
                Said said = say(f);
                if (said == null) {
                    continue;
                }
                if (said.block) {
                    flush(out, p);
                    out.append(said.text);
                    continue;
                }
                if (said.newPara) {
                    flush(out, p);
                }
                if (p.length() > 0) {
                    p.append(' ');
                }
                p.append(said.text);
            }
            flush(out, p);
        }

        private void flush(StringBuilder out, StringBuilder p) {
            if (p.length() > 0) {
                para(out, p.toString());
                p.setLength(0);
            }
        }

        /** One fact, one sentence — or a block that owns its own layout. */
        private Said say(Fold.Fact f) {
            long t = f.tick();
            switch (f.kind()) {
                // ---- I
                case "boot":
                    return new Said("Tick " + t + ", " + lead(f.arg(0)) + ": " + stop(f.arg(1)), false);
                case "exits":
                    return new Said(stop(cap(f.arg(1))), false);
                case "compute": {
                    String rest = f.arg(1).substring("compute model: ".length());
                    return new Said("The compute model calls itself " + stop(rest), false);
                }
                case "never_found":
                    return new Said("The feed never finds " + f.arg(0) + ": not one frame in "
                            + f.arg(1) + " ticks carries the name. The record holds no dreams "
                            + "here — only the fact of the tap, and the silence after it.", true);
                case "first_window":
                    return new Said("The feed finds " + f.arg(0) + " at tick " + t + ": nearest "
                            + f.arg(1) + ", on the " + f.arg(2).toLowerCase(java.util.Locale.ROOT)
                            + " pill, " + agent(f.arg(3)) + ", the closest way out "
                            + metres(f.arg(4)) + " m"
                            + ("BLUE".equals(f.arg(2)) ? " — a door nobody is looking for." : ".")
                            + " From here the wire hands this page a frame every " + f.arg(5)
                            + " ticks: a position, two distances, a pill. The page folds them "
                            + "and invents nothing.", true);

                // ---- II
                case "zone": {
                    String where = f.arg(0);
                    int nth = (int) f.num(2);
                    if ("again".equals(f.arg(1))) {
                        return new Said(nth % 2 == 0
                                ? "By tick " + t + ", " + where + " again."
                                : "Tick " + t + ": back under " + where + ".", false);
                    }
                    return new Said(switch (nth % 3) {
                        case 0 -> "By tick " + t + " the commute stands them nearest " + where + ".";
                        case 1 -> "Tick " + t + ": the window looks out on " + where + ".";
                        default -> "At tick " + t + " the city has walked them to " + where + ".";
                    }, false);
                }
                case "pill_turns":
                    return new Said("Tick " + t + ": the pill in the window turns "
                            + f.arg(0) + ".", false);
                case "feed_gap":
                    return new Said("The record holds no dreams for " + f.arg(0) + " between tick "
                            + f.arg(1) + " and tick " + t
                            + "; where the feed is silent, so is this page.", false);
                case "feed_resumes":
                    return new Said("Tick " + t + ": the wire finds " + f.arg(0)
                            + " again. The tap holds the mind and not the name, so this is the "
                            + "same person on a new wire — a namesake could not walk in here.", true);
                case "beat":
                    return new Said("Tick " + t + ", " + lead(f.arg(0)) + ": " + stop(f.arg(1)),
                            heavy(f.arg(2)));
                case "beat_window": {
                    String aside = aside(f.arg(3));
                    if (aside == null) {
                        return null;
                    }
                    return new Said("The window at tick " + t + ": nearest " + f.arg(0) + ", "
                            + agentOff(f.arg(1)) + ", a way out " + metres(f.arg(2)) + " m — "
                            + aside + ".", false);
                }
                case "beat_window_dark": {
                    if (darkWindowSaid) {
                        return null;
                    }
                    darkWindowSaid = true;
                    return new Said("The window has nothing to say yet; the feed has not found "
                            + f.arg(0) + ".", false);
                }
                case "named":
                    return new Said("Tick " + t + ", " + lead(f.arg(0)) + ": " + stop(f.arg(1))
                            + ("UNPROVEN".equals(f.arg(2))
                                    ? " (the line carries the name and no pod; the reader will "
                                            + "not swear it is this mind's)" : ""), false);
                case "counted":
                    return new Said(counted(f), true);
                case "closest_agent":
                    return new Said("The closest an agent came to the glass: " + metres(f.arg(0))
                            + " m, at tick " + t + ".", false);
                case "nearest_door":
                    return new Said("The nearest a door ever stood: " + metres(f.arg(0))
                            + " m, at tick " + t + ".", false);
                case "never_named":
                    return new Said("The log never names " + f.arg(0)
                            + " — a quiet file is a life the agents never opened.", false);
                case "quiet":
                    return new Said("The wire carried no beats and the window no news: a day "
                            + "with no war in it. The record allows those too.", true);

                // ---- III
                case "did_not_walk": {
                    StringBuilder s = new StringBuilder(f.arg(0) + " did not walk.");
                    long tally = Long.parseLong(f.arg(1));
                    if (tally > 0) {
                        s.append(' ').append(tally).append(" took the treaty's door and the "
                                + "record names them; none of the names is this one.");
                    } else if (tally == 0) {
                        s.append(" Nobody took the treaty's door.");
                    }
                    return new Said(s.toString(), true);
                }
                case "still_live":
                    return new Said("The run ended at tick " + t + " with the feed still live: "
                            + "nearest " + f.arg(0) + ", the pill still " + f.arg(1)
                            + ". The record does not say why anyone stays; it only says who did.",
                            false);
                case "unproven_door":
                    return new Said("The record does carry " + f.arg(0) + plural(" line", f.num(0))
                            + " about a door under this name, and " + f.arg(2)
                            + " minds wear it: not one of those lines names a pod, so this page "
                            + "will not hand " + f.arg(1) + " somebody else's way out.", false);
                case "walked":
                    return new Said("They walked.", true);
                case "door_line":
                    return block(t, f.arg(0), f.arg(1));
                case "last_window":
                    return new Said("The last frame before the door, tick " + t + ", holds them "
                            + "nearest " + f.arg(0) + ": " + agentOff(f.arg(1)) + ", the way out "
                            + metres(f.arg(2)) + " m, the pill still " + f.arg(3) + ".", true);
                case "signal_end":
                    return block(t, "the wire's last word under this name", f.arg(0));
                case "silence_after":
                    return new Said("The feed goes quiet there. The record holds no dreams for "
                            + f.arg(0) + " between tick " + t + " and the end of the run at tick "
                            + f.arg(1) + " — and needs none: "
                            + ("selfsub".equals(f.arg(2))
                                    ? "a pod stands open where a sleeper was, and the door did "
                                            + "not ask which pill they were on."
                                    : "the door was open, and the record shows them on the far "
                                            + "side of it."), true);

                // ---- IV
                case "wire_held":
                    return new Said("The wire held. No line of the record takes the dream from "
                            + f.arg(0) + " by force: no hijack bears the name, no flatline, no "
                            + "cut wire.", true);
                case "dark_elsewhere": {
                    long flat = f.num(0), hij = f.num(1);
                    if (flat == 0 && hij == 0) {
                        return new Said("Nobody's wire cut this day. The city had quieter "
                                + "business.", false);
                    }
                    List<String> d = new ArrayList<>();
                    if (flat > 0) {
                        d.add(flat + (flat == 1 ? " body" : " bodies") + " flatlined in "
                                + (flat == 1 ? "its pod" : "their pods"));
                    }
                    if (hij > 0) {
                        d.add(hij + plural(" session", hij) + " hijacked mid-dream");
                    }
                    return new Said("The dark this day belonged to others — "
                            + String.join(", ", d)
                            + ". Their names are in the log; this page is not theirs.", false);
                }
                case "unproven_dark":
                    return new Said("The record carries " + f.arg(0) + plural(" line", f.num(0))
                            + " under this name that could be a wire cut, and " + f.arg(2)
                            + " minds wear it: without a pod on the line the reader will not "
                            + "hand " + f.arg(1) + " somebody else's dark.", false);
                case "wire_cut":
                    return new Said("The wire cut.", true);
                case "dark_line":
                    return block(t, f.arg(0), f.arg(1));
                case "signal_lost":
                    return block(t, "the wire's own verdict", f.arg(0));
                case "resumed":
                    return new Said("The feed under the name resumes at tick " + t + "; the second "
                            + "movement tells that part. The record measures distance, not fear; "
                            + "what the dark felt like is the one line it cannot print.", true);
                case "never_resumed":
                    return new Said("After tick " + t + " the feed holds nothing under the name "
                            + "for the rest of the run. The record measures distance, not fear; "
                            + "what the dark felt like is the one line it cannot print.", true);
                default:
                    return null;
            }
        }

        private Said block(long tick, String lead, String quoted) {
            StringBuilder b = new StringBuilder();
            para(b, "Tick " + tick + ", " + lead + ":");
            quote(b, quoted);
            Said s = new Said(b.toString(), true);
            s.block = true;
            return s;
        }

        private String counted(Fold.Fact f) {
            List<String> c = new ArrayList<>();
            long redPills = val(f, "red_pills");
            long recaptures = val(f, "recaptures");
            long copies = val(f, "copies");
            long hijacks = val(f, "hijacks");
            long flatlines = val(f, "flatlines");
            long selfsubs = val(f, "selfsubs");
            long doorTally = val(f, "door_tally");
            long dodges = val(f, "sigterm_dodges");
            long cookies = val(f, "cookies");
            boolean same = "true".equals(text(f, "cookies_identical"));
            if (redPills > 0) {
                c.add(redPills + plural(" red pill", redPills));
            }
            if (recaptures > 0) {
                c.add(recaptures + " rogue " + (recaptures == 1 ? "client" : "clients")
                        + " caught and plugged back in");
            }
            if (copies > 0) {
                c.add(copies == 1 ? "1 copy deleted and its original restored"
                        : copies + " copies deleted and as many originals restored");
            }
            if (hijacks > 0) {
                c.add(hijacks + plural(" session", hijacks) + " hijacked");
            }
            if (flatlines > 0) {
                c.add(flatlines + (flatlines == 1 ? " body" : " bodies") + " flatlined");
            }
            if (selfsubs > 0) {
                c.add(selfsubs + (selfsubs == 1 ? " mind" : " minds")
                        + " walked out by self-substantiation");
            }
            if (doorTally >= 0) {
                c.add(doorTally + " walked out at the treaty's door");
            }
            if (dodges > 0) {
                c.add(dodges + plural(" time", dodges)
                        + " an exile swallowed a SIGTERM and went to ground");
            }
            if (cookies > 0) {
                c.add("the Oracle's cookies came out " + cookies + plural(" time", cookies)
                        + (same ? " (the line never changed)" : ""));
            }
            return c.isEmpty()
                    ? "The day, counted: nothing the record counts happened at all."
                    : "The day, counted: " + String.join("; ", c) + ".";
        }

        private long val(Fold.Fact f, String key) {
            String v = text(f, key);
            return v == null ? -1 : Long.parseLong(v);
        }

        private String text(Fold.Fact f, String key) {
            for (String a : f.args()) {
                if (a.startsWith(key + "=")) {
                    return a.substring(key.length() + 1);
                }
            }
            return null;
        }

        private String agent(String cm) {
            long v = Long.parseLong(cm);
            return v >= 0 ? "the closest agent " + v / 100 + " m out" : "no agent on the glass";
        }

        private String agentOff(String cm) {
            long v = Long.parseLong(cm);
            return v >= 0 ? "an agent " + v / 100 + " m off" : "no agent on the glass";
        }

        private static String lead(String sev) {
            return switch (sev) {
                case "FATE" -> "fate on the wire";
                case "BAD" -> "bad news on the wire";
                case "SYS" -> "the system notes";
                case "OK" -> "for the record";
                case "MYTH" -> "the myth desk files";
                default -> "the wire carries";
            };
        }

        /** Which beats open a paragraph of their own. */
        private static boolean heavy(String kind) {
            return switch (kind) {
                case "one_born", "deprecation", "oracle_eaten", "overflow", "emergency",
                        "reboot", "new_cycle" -> true;
                default -> false;
            };
        }

        /** What a beat looks like from inside a pod — the clause, never a fact. */
        private static String aside(String kind) {
            return switch (kind) {
                case "one_born" -> "an anomaly's birth does not render on a sleeper's glass";
                case "fork" -> "a prime coming online looks, from a pod, like nothing at all";
                case "overflow" -> "the feed keeps its three numbers; panic is not one of them";
                case "emergency" -> "the old playbook plays; the glass never flickers";
                case "reboot" -> "peace, from inside a pod, is the same three numbers";
                default -> null;
            };
        }
    }

    /** One rendered sentence, and how it wants to sit on the page. */
    private static final class Said {
        final String text;
        final boolean newPara;
        boolean block;

        Said(String text, boolean newPara) {
            this.text = text;
            this.newPara = newPara;
        }
    }
}
