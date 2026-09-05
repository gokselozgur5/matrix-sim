import matrix.causal.CausalId;
import matrix.causal.CausalRecord;
import matrix.causal.PerceptInputs;
import matrix.realworld.MindReducer;
import matrix.realworld.MindState;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Exact V3 value-codec keeper for #1768; this is not a whole-world restore claim. */
public final class MindStateCodecs {
    private static int cases, roundtripFail, refusalFail, futureFail, sourceFail, sourceRed;

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        if (args.length != 0) {
            System.err.println("FATAL unknown argument: " + args[0]
                    + " (this probe takes no arguments)");
            System.exit(Probes.Outcome.REFUSED.code());
        }
        Path root = Path.of(".").toAbsolutePath().normalize();
        roundTrips();
        independentAndFields();
        refusals();
        futureTransition();
        checkSource(sourceProof(root), "production-source-graph");
        sourceMutants(root);
        System.out.println("MIND_STATE_CODEC_CENSUS cases=" + cases
                + " roundtrip_fail=" + roundtripFail + " refusal_fail=" + refusalFail
                + " future_fail=" + futureFail + " source_fail=" + sourceFail
                + " source_red=" + sourceRed);
        Probes.leave("VERDICT MIND_STATE_CODECS_HELD cases=" + cases
                        + " cases_none=" + (cases == 0 ? 1 : 0)
                        + " roundtrip_fail=" + roundtripFail
                        + " refusal_fail=" + refusalFail
                        + " future_fail=" + futureFail + " source_fail=" + sourceFail
                        + " source_red=" + sourceRed,
                cases > 0 && roundtripFail + refusalFail + futureFail + sourceFail == 0
                        && sourceRed == 11);
    }

    private static void roundTrips() {
        CausalRecord.Subject subject = new CausalRecord.Subject("human-codec");
        roundtrip("empty", MindState.initial(subject));
        roundtrip("nonempty", state(subject, 1, List.of(trace(subject, 1, 0, 7, 0,
                CausalRecord.Channel.TEXT, "gördüm", CausalRecord.Principal.unknown(),
                0, CausalRecord.Fidelity.FULL,
                CausalRecord.PresentedClaim.structured("door.state", "open")))));
        ArrayList<MindState.MemoryTrace> full = new ArrayList<>();
        for (int i = 0; i < MindState.MAX_HISTORY_V1; i++) {
            full.add(trace(subject, 1, i, 8, i, CausalRecord.Channel.INTERNAL, "m" + i,
                    new CausalRecord.Principal(CausalRecord.PrincipalKind.HUMAN, "friend"),
                    10_000, CausalRecord.Fidelity.PARTIAL,
                    CausalRecord.PresentedClaim.legacyUnclassified()));
        }
        roundtrip("max64", state(subject, 1, full));
        byte[] caller = state(subject, 1, List.of(full.get(0))).canonicalBytes();
        MindState restored = MindState.fromCanonicalBytes(caller);
        caller[0] ^= 0x7f;
        checkRoundtrip(restored.equals(MindState.fromCanonicalBytes(restored.canonicalBytes())),
                "caller-byte-mutation-isolated");
    }

    private static void independentAndFields() {
        Frame base = Frame.base();
        byte[] independent = frame(base);
        MindState decoded = MindState.fromCanonicalBytes(independent);
        checkRoundtrip(Arrays.equals(independent, decoded.canonicalBytes()),
                "independent-full-v3-reencode");
        checkRoundtrip(decoded.equals(base.state()), "independent-full-v3-value");

        twin(base.withSubject("human-other"), "subject");
        twin(base.withRevision(2), "revision");
        twin(base.withMemorySubject("human-other"), "memory-subject-invalid");
        twin(base.withRevision(2).withMemoryRevision(2), "memory-revision");
        twin(base.withMemorySequence(1), "memory-sequence");
        twin(base.withBasisSubject("human-other"), "basis-subject-invalid");
        twin(base.withTick(10), "percept-tick");
        twin(base.withPerceptSequence(1), "percept-sequence");
        twin(base.withChannel(CausalRecord.Channel.VISION), "channel");
        twin(base.withContent("başka"), "content");
        twin(base.withPrincipal(CausalRecord.PrincipalKind.PLACE, "friend"), "principal-kind");
        twin(base.withPrincipal(CausalRecord.PrincipalKind.HUMAN, "other-friend"),
                "principal-key");
        twin(base.withUncertainty(9999), "uncertainty");
        twin(base.withFidelity(CausalRecord.Fidelity.FULL), "fidelity");
        twin(base.withClaim(CausalRecord.PresentedClaim.structured("door.state", "closed")),
                "claim-position");
        twin(base.withClaim(CausalRecord.PresentedClaim.structured("window.state", "open")),
                "claim-key");
        twin(base.withClaim(CausalRecord.PresentedClaim.legacyUnclassified()), "claim-class");
    }

    private static void refusals() {
        Frame base = Frame.base();
        byte[] valid = frame(base);
        refused("null", () -> MindState.fromCanonicalBytes(null));
        refused("empty", () -> MindState.fromCanonicalBytes(new byte[0]));
        byte[] old = valid.clone(); putInt(old, 0, 2); refused("schema-old", () -> decode(old));
        byte[] future = valid.clone(); putInt(future, 0, 4); refused("schema-future", () -> decode(future));
        refused("trailing", () -> decode(Arrays.copyOf(valid, valid.length + 1)));
        for (int cut : new int[]{1, 4, 8, valid.length - 1}) {
            refused("truncated-" + cut, () -> decode(Arrays.copyOf(valid, cut)));
        }
        byte[] negativeSubject = valid.clone(); putInt(negativeSubject, 4, -1);
        refused("negative-word-length", () -> decode(negativeSubject));
        byte[] hugeSubject = valid.clone(); putInt(hugeSubject, 4, 65);
        refused("oversized-symbol-length", () -> decode(hugeSubject));
        byte[] beyond = valid.clone(); putInt(beyond, 4, valid.length);
        refused("length-beyond-remaining", () -> decode(beyond));
        int contentLength = contentLengthOffset(valid);
        byte[] negativePayload = valid.clone(); putInt(negativePayload, contentLength, -1);
        refused("negative-payload-length", () -> decode(negativePayload));
        byte[] hugePayload = valid.clone(); putInt(hugePayload, contentLength, 12_289);
        refused("oversized-payload-length", () -> decode(hugePayload));
        byte[] payloadBeyond = valid.clone(); putInt(payloadBeyond, contentLength, valid.length);
        refused("payload-beyond-remaining", () -> decode(payloadBeyond));
        byte[] malformed = valid.clone(); malformed[8] = (byte) 0xc0;
        refused("malformed-utf8", () -> decode(malformed));
        byte[] overlong = valid.clone(); malformedSymbol(overlong, (byte) 0xc0, (byte) 0xaf);
        refused("overlong-utf8", () -> decode(overlong));
        byte[] malformedPayload = valid.clone(); malformedPayload[contentLength + 4] = (byte) 0xc0;
        refused("malformed-payload-utf8", () -> decode(malformedPayload));
        refusedFrame("negative-history-count", base, -1);
        refusedFrame("oversized-history-count", base, 65);
        refusedFrame("hostile-history-count-before-allocation", base, Integer.MAX_VALUE);
        refused("unknown-channel", () -> decode(frame(base, "CHANNEL_UNKNOWN", null, null)));
        refused("wrong-case-channel", () -> decode(frame(base, "text", null, null)));
        refused("unknown-principal-kind", () -> decode(replaceWord(valid,
                CausalRecord.PrincipalKind.HUMAN.name(), "ROBOT")));
        refused("unknown-fidelity", () -> decode(replaceWord(valid,
                CausalRecord.Fidelity.PARTIAL.name(), "UNKNOWN")));
        refused("unknown-status", () -> decode(replaceWord(valid,
                MindState.EpistemicStatus.UNRESOLVED.name(), "MYSTERIOUS")));
        refused("unknown-claim-class", () -> decode(replaceWord(valid,
                CausalRecord.ClaimClass.STRUCTURED.name(), "UNKNOWNSXX")));
        refused("invalid-symbol", () -> decode(frame(base.withSubject("Human"))));
        refused("invalid-text-control", () -> decode(frame(base.withContent("bad\ntext"))));
        refused("invalid-unknown-principal", () -> decode(frame(base.withPrincipal(
                CausalRecord.PrincipalKind.UNKNOWN, "friend"))));
        refused("invalid-uncertainty", () -> decode(frame(base.withUncertainty(10_001))));
        refused("invalid-legacy-pair", () -> decode(frame(base, null,
                "LEGACY_UNCLASSIFIED", "door.state")));
        refused("foreign-memory-subject", () -> decode(frame(base.withMemorySubject("human-x"))));
        refused("foreign-basis-subject", () -> decode(frame(base.withBasisSubject("human-x"))));
        refused("zero-memory-revision", () -> decode(frame(base.withMemoryRevision(0))));
        refused("future-memory-revision", () -> decode(frame(base.withMemoryRevision(2))));
        refused("negative-percept-tick", () -> decode(frame(base.withTick(-1))));
        refused("negative-percept-sequence", () -> decode(frame(base.withPerceptSequence(-1))));
        refused("duplicate-memory", () -> decode(frame(List.of(base, base))));
        refused("reversed-memory", () -> decode(frame(List.of(
                base.withRevision(2).withMemoryRevision(2).withMemorySequence(1),
                base.withRevision(2).withMemoryRevision(1).withMemorySequence(0)))));
        refused("reversed-basis", () -> decode(frame(List.of(
                base.withRevision(2).withMemoryRevision(1).withMemorySequence(0).withTick(10),
                base.withRevision(2).withMemoryRevision(2).withMemorySequence(0).withTick(9)))));
    }

    private static void futureTransition() {
        CausalRecord.Subject subject = new CausalRecord.Subject("human-future");
        ArrayList<MindState.MemoryTrace> history = new ArrayList<>();
        for (int i = 0; i < 64; i++) history.add(trace(subject, 1, i, 20, i,
                CausalRecord.Channel.TEXT, "old" + i, CausalRecord.Principal.unknown(), 5000,
                CausalRecord.Fidelity.PARTIAL,
                CausalRecord.PresentedClaim.structured("memory.line", "p" + i)));
        MindState original = new MindState(subject, 1, history);
        MindState restored = MindState.fromCanonicalBytes(original.canonicalBytes());
        CausalRecord.PerceptReceipt receipt = new CausalRecord.PerceptReceipt(
                new CausalId.Percept(21, 0), subject, CausalRecord.Channel.TEXT,
                new CausalRecord.Payload("new"),
                CausalRecord.PresentedClaim.structured("memory.line", "new"),
                CausalRecord.Principal.unknown(), 100, CausalRecord.Fidelity.FULL);
        PerceptInputs.MindInput input = new PerceptInputs.MindInput(21, subject, List.of(receipt));
        MindState left = MindReducer.reduce(original, input);
        MindState right = MindReducer.reduce(restored, input);
        checkFuture(left.equals(right) && Arrays.equals(left.canonicalBytes(), right.canonicalBytes()),
                "equal-next-reducer-at-eviction-boundary");
        checkFuture(left.history().size() == 64
                && left.history().get(0).id().sequence() == 1,
                "eviction-boundary-was-exercised");
    }

    private static void sourceMutants(Path root) throws Exception {
        sourceRed(root, "system-property", "int schema = in.readInt(\"schema\");",
                "System.getProperty(\"matrix.codec\");\n        int schema = in.readInt(\"schema\");", "");
        sourceRed(root, "nano-time", "int schema = in.readInt(\"schema\");",
                "System.nanoTime();\n        int schema = in.readInt(\"schema\");", "");
        sourceRed(root, "same-class-helper", "int schema = in.readInt(\"schema\");",
                "escape();\n        int schema = in.readInt(\"schema\");",
                "\n    private static void escape() { System.getProperty(\"matrix.codec\"); }\n");
        sourceRed(root, "external-helper", "int schema = in.readInt(\"schema\");",
                "MindStateCodecEscape.escape();\n        int schema = in.readInt(\"schema\");",
                "\nfinal class MindStateCodecEscape { static void escape() { System.nanoTime(); } }\n");
        sourceRed(root, "class-initializer", "int schema = in.readInt(\"schema\");",
                "int schema = in.readInt(\"schema\");",
                "\n    static { System.getProperty(\"matrix.codec\"); }\n");
        targetSourceRed(root, "payload-constructor-property", "matrix/causal/CausalRecord.java",
                "public Payload {", "public Payload {\n            System.getProperty(\"matrix.codec.payload\");");
        targetSourceRed(root, "payload-delegated-native", "matrix/causal/CausalRecord.java",
                "record Payload(String text) {\n        public Payload {",
                "record Payload(String text) {\n        private static native void escape();\n"
                        + "        public Payload {\n            escape();");
        targetSourceRed(root, "payload-owner-initializer", "matrix/causal/CausalRecord.java",
                "record Payload(String text) {",
                "record Payload(String text) {\n        private static final String ESCAPE = "
                        + "System.getProperty(\"matrix.codec.payload\");");
        targetSourceRed(root, "percept-accessor-dispatch", "matrix/causal/CausalId.java",
                "public Percept {\n            validate(tick, sequence);\n        }",
                "public Percept {\n            validate(tick, sequence);\n        }\n\n"
                        + "        @Override public long tick() {\n"
                        + "            System.getProperty(\"matrix.codec.percept\");\n"
                        + "            return tick;\n        }");
        twoFileSourceRed(root, "object-virtual-override",
                "matrix/realworld/MindState.java", "long revision = in.readLong(\"mind revision\");",
                "((Object) subject).toString();\n        long revision = in.readLong(\"mind revision\");",
                "matrix/causal/CausalRecord.java", "public Subject(String key) {\n            this(new Symbol(key));\n        }",
                "public Subject(String key) {\n            this(new Symbol(key));\n        }\n\n"
                        + "        @Override public String toString() {\n"
                        + "            System.getProperty(\"matrix.codec.subject\");\n"
                        + "            return \"subject\";\n        }");
        targetSourceRed(root, "channel-enum-initializer", "matrix/causal/CausalRecord.java",
                "INTERNAL,\n        NO_SIGNAL\n    }",
                "INTERNAL,\n        NO_SIGNAL;\n\n        private static final String ESCAPE = "
                        + "System.getProperty(\"matrix.codec.channel\");\n    }");
    }

    private static void twoFileSourceRed(Path root, String name,
            String firstRelative, String firstNeedle, String firstReplacement,
            String secondRelative, String secondNeedle, String secondReplacement) throws Exception {
        cases++;
        Path mutant = Files.createTempDirectory("mind-codec-mutant-");
        Path target = mutant.resolve("src");
        try (var paths = Files.walk(root.resolve("src"))) {
            for (Path source : paths.toList()) {
                Path copy = target.resolve(root.resolve("src").relativize(source));
                if (Files.isDirectory(source)) Files.createDirectories(copy);
                else Files.copy(source, copy);
            }
        }
        Path first = target.resolve(firstRelative);
        Path second = target.resolve(secondRelative);
        String firstSource = Files.readString(first);
        String secondSource = Files.readString(second);
        if (!firstSource.contains(firstNeedle) || !secondSource.contains(secondNeedle)) return;
        Files.writeString(first, firstSource.replace(firstNeedle, firstReplacement));
        Files.writeString(second, secondSource.replace(secondNeedle, secondReplacement));
        if (!sourceProof(mutant)) {
            sourceRed++;
            System.out.println("MIND_STATE_CODEC_MUTANT_RED " + name);
        }
    }

    private static void sourceRed(Path root, String name, String needle,
            String replacement, String suffix) throws Exception {
        sourceRed(root, name, "matrix/realworld/MindState.java", needle, replacement, suffix);
    }

    private static void targetSourceRed(Path root, String name, String relative,
            String needle, String replacement) throws Exception {
        sourceRed(root, name, relative, needle, replacement, "");
    }

    private static void sourceRed(Path root, String name, String relative, String needle,
            String replacement, String suffix) throws Exception {
        cases++;
        Path mutant = Files.createTempDirectory("mind-codec-mutant-");
        Path target = mutant.resolve("src");
        try (var paths = Files.walk(root.resolve("src"))) {
            for (Path source : paths.toList()) {
                Path copy = target.resolve(root.resolve("src").relativize(source));
                if (Files.isDirectory(source)) Files.createDirectories(copy);
                else Files.copy(source, copy);
            }
        }
        Path state = target.resolve(relative);
        String source = Files.readString(state);
        if (!source.contains(needle)) return;
        int lastBrace = source.lastIndexOf('}');
        if (suffix.contains("final class MindStateCodecEscape")) {
            source = source.replace(needle, replacement) + suffix;
        } else if (!suffix.isEmpty()) {
            source = source.substring(0, lastBrace) + suffix + source.substring(lastBrace);
            source = source.replace(needle, replacement);
        } else source = source.replace(needle, replacement);
        Files.writeString(state, source);
        if (!sourceProof(mutant)) {
            sourceRed++;
            System.out.println("MIND_STATE_CODEC_MUTANT_RED " + name);
        }
    }

    private static boolean sourceProof(Path root) {
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null) return false;
        try {
            Path out = Files.createTempDirectory("mind-codec-proof-");
            List<java.io.File> files;
            try (var paths = Files.walk(root.resolve("src"))) {
                files = paths.filter(path -> path.toString().endsWith(".java"))
                        .map(Path::toFile).toList();
            }
            var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
            try (var manager = compiler.getStandardFileManager(diagnostics,
                    java.util.Locale.ROOT, StandardCharsets.UTF_8)) {
                var task = (com.sun.source.util.JavacTask) compiler.getTask(null, manager,
                        diagnostics, List.of("-proc:none", "--release", "17", "-classpath", "",
                                "-sourcepath", "", "-d", out.toString()), null,
                        manager.getJavaFileObjectsFromFiles(files));
                List<com.sun.source.tree.CompilationUnitTree> units = new ArrayList<>();
                task.parse().forEach(units::add); task.analyze();
                if (diagnostics.getDiagnostics().stream().anyMatch(d ->
                        d.getKind() == javax.tools.Diagnostic.Kind.ERROR)) return false;
                return inspect(task, units);
            }
        } catch (Exception failure) { return false; }
    }

    private static boolean inspect(com.sun.source.util.JavacTask task,
            List<com.sun.source.tree.CompilationUnitTree> units) {
        var trees = com.sun.source.util.Trees.instance(task);
        var types = task.getTypes();
        var mind = task.getElements().getTypeElement("matrix.realworld.MindState");
        if (mind == null) return false;
        List<javax.lang.model.element.ExecutableElement> doors = mind.getEnclosedElements().stream()
                .filter(e -> e.getKind() == javax.lang.model.element.ElementKind.METHOD
                        && e.getSimpleName().contentEquals("fromCanonicalBytes"))
                .map(javax.lang.model.element.ExecutableElement.class::cast).toList();
        if (doors.size() != 1 || !key(doors.get(0), types).equals(
                "matrix.realworld.MindState#fromCanonicalBytes(byte[])")
                || !doors.get(0).getModifiers().equals(Set.of(
                javax.lang.model.element.Modifier.PUBLIC,
                javax.lang.model.element.Modifier.STATIC))) return false;

        java.util.Map<String, Facts> facts = new java.util.HashMap<>();
        java.util.Map<String, javax.lang.model.element.ExecutableElement> methods =
                new java.util.HashMap<>();
        java.util.Map<String, Set<String>> classInitializers = new java.util.HashMap<>();
        java.util.Map<String, Set<String>> instanceInitializers = new java.util.HashMap<>();
        for (var unit : units) new com.sun.source.util.TreePathScanner<Void, Void>() {
            private String current;
            @Override public Void visitMethod(com.sun.source.tree.MethodTree node, Void unused) {
                var element = trees.getElement(getCurrentPath());
                String prior = current;
                if (element instanceof javax.lang.model.element.ExecutableElement executable) {
                    current = key(executable, types); facts.putIfAbsent(current, new Facts());
                    methods.put(current, executable);
                    if (node.getBody() == null) facts.get(current).bodyless = true;
                    if (node.getModifiers().getFlags().contains(
                            javax.lang.model.element.Modifier.NATIVE)) facts.get(current).bad = true;
                } else current = null;
                super.visitMethod(node, unused); current = prior; return null;
            }
            @Override public Void visitMethodInvocation(com.sun.source.tree.MethodInvocationTree node,
                    Void unused) {
                if (current != null) {
                    var element = trees.getElement(new com.sun.source.util.TreePath(
                            getCurrentPath(), node.getMethodSelect()));
                    if (!(element instanceof javax.lang.model.element.ExecutableElement method))
                        facts.get(current).bad = true;
                    else {
                        String call = key(method, types);
                        facts.get(current).calls.add(call);
                        if (call.equals("matrix.realworld.MindState.Reader#readEnum(java.lang.Class,java.lang.String)")) {
                            if (node.getArguments().isEmpty()
                                    || !(node.getArguments().get(0) instanceof com.sun.source.tree.MemberSelectTree literal)
                                    || !literal.getIdentifier().contentEquals("class")) {
                                facts.get(current).bad = true;
                            } else {
                                var literalPath = new com.sun.source.util.TreePath(
                                        getCurrentPath(), literal);
                                var owner = trees.getElement(new com.sun.source.util.TreePath(
                                        literalPath, literal.getExpression()));
                                if (owner instanceof javax.lang.model.element.TypeElement type)
                                    facts.get(current).enumOwners.add(type.getQualifiedName().toString());
                                else facts.get(current).bad = true;
                            }
                        }
                    }
                }
                return super.visitMethodInvocation(node, unused);
            }
            @Override public Void visitNewClass(com.sun.source.tree.NewClassTree node, Void unused) {
                if (current != null) {
                    var element = trees.getElement(getCurrentPath());
                    if (!(element instanceof javax.lang.model.element.ExecutableElement constructor))
                        facts.get(current).bad = true;
                    else facts.get(current).news.add(key(constructor, types));
                }
                return super.visitNewClass(node, unused);
            }
            @Override public Void visitIdentifier(com.sun.source.tree.IdentifierTree node, Void u) {
                field(getCurrentPath()); return super.visitIdentifier(node, u); }
            @Override public Void visitMemberSelect(com.sun.source.tree.MemberSelectTree node, Void u) {
                field(getCurrentPath()); return super.visitMemberSelect(node, u); }
            private void field(com.sun.source.util.TreePath path) {
                if (current == null) return;
                var element = trees.getElement(path);
                if (element != null && (element.getSimpleName().contentEquals("this")
                        || element.getSimpleName().contentEquals("super"))) return;
                if (element != null && (element.getKind().isField()
                        || element.getKind() == javax.lang.model.element.ElementKind.ENUM_CONSTANT)
                        && element.getEnclosingElement() instanceof javax.lang.model.element.TypeElement type)
                    facts.get(current).fields.add(type.getQualifiedName() + "#" + element.getSimpleName());
            }
            @Override public Void visitLambdaExpression(com.sun.source.tree.LambdaExpressionTree n, Void u) {
                if (current != null) facts.get(current).bad = true; return super.visitLambdaExpression(n,u); }
            @Override public Void visitMemberReference(com.sun.source.tree.MemberReferenceTree n, Void u) {
                if (current != null) facts.get(current).bad = true; return super.visitMemberReference(n,u); }
            @Override public Void visitAssert(com.sun.source.tree.AssertTree n, Void u) {
                if (current != null) facts.get(current).bad = true; return super.visitAssert(n,u); }
            @Override public Void visitSynchronized(com.sun.source.tree.SynchronizedTree n, Void u) {
                if (current != null) facts.get(current).bad = true; return super.visitSynchronized(n,u); }
            @Override public Void visitBlock(com.sun.source.tree.BlockTree node, Void unused) {
                var parent = getCurrentPath().getParentPath();
                if (parent != null && parent.getLeaf() instanceof com.sun.source.tree.ClassTree) {
                    var element = trees.getElement(parent);
                    if (element instanceof javax.lang.model.element.TypeElement type) {
                        String owner = type.getQualifiedName().toString();
                        String pseudo = owner + (node.isStatic() ? "#<clinit>()" : "#<iinit>()");
                        (node.isStatic() ? classInitializers : instanceInitializers)
                                .computeIfAbsent(owner, ignored -> new java.util.LinkedHashSet<>())
                                .add(pseudo);
                        String prior = current; current = pseudo;
                        facts.putIfAbsent(current, new Facts());
                        super.visitBlock(node, unused); current = prior; return null;
                    }
                }
                return super.visitBlock(node, unused);
            }
            @Override public Void visitVariable(com.sun.source.tree.VariableTree node, Void unused) {
                var element = trees.getElement(getCurrentPath());
                if (element != null && element.getKind().isField() && node.getInitializer() != null
                        && element.getEnclosingElement() instanceof javax.lang.model.element.TypeElement type) {
                    String owner = type.getQualifiedName().toString();
                    boolean isStatic = element.getModifiers().contains(
                            javax.lang.model.element.Modifier.STATIC);
                    String pseudo = owner + (isStatic ? "#<clinit>()" : "#<iinit>()");
                    (isStatic ? classInitializers : instanceInitializers)
                            .computeIfAbsent(owner, ignored -> new java.util.LinkedHashSet<>())
                            .add(pseudo);
                    String prior = current; current = pseudo;
                    facts.putIfAbsent(current, new Facts());
                    scan(node.getInitializer(), unused); current = prior; return null;
                }
                return super.visitVariable(node, unused);
            }
        }.scan(unit, null);

        Set<String> allowedCalls = Set.of(
                "java.lang.Byte#toUnsignedInt(byte)", "java.lang.Enum#valueOf(java.lang.Class,java.lang.String)",
                "java.lang.Integer#toUnsignedLong(int)",
                "java.lang.Integer#compare(int,int)", "java.lang.Long#compare(long,long)",
                "java.lang.Object#<init>()", "java.lang.Record#<init>()",
                "java.lang.Enum#<init>(java.lang.String,int)",
                "java.lang.String#equals(java.lang.Object)",
                "java.lang.String#isEmpty()", "java.lang.String#length()",
                "java.lang.String#strip()", "java.lang.String#charAt(int)",
                "java.lang.String#compareTo(java.lang.String)",
                "java.lang.Character#isHighSurrogate(char)",
                "java.lang.Character#isLowSurrogate(char)",
                "java.lang.Character#isISOControl(char)",
                "matrix.causal.CausalRecord.Symbol#value()",
                "matrix.causal.CausalRecord.ClaimKey#key()",
                "matrix.causal.CausalRecord.ClaimPosition#key()",
                "matrix.causal.CausalRecord.Subject#equals(java.lang.Object)",
                "matrix.causal.CausalRecord.PerceptRef#subject()",
                "matrix.causal.CausalRecord.PerceptRef#id()",
                "matrix.causal.CausalRecord.MemoryRef#subject()",
                "matrix.causal.CausalRecord.MemoryRef#revision()",
                "matrix.realworld.MindState.MemoryTrace#basis()",
                "matrix.realworld.MindState.MemoryTrace#id()",
                "java.nio.ByteBuffer#wrap(byte[],int,int)", "java.nio.CharBuffer#toString()",
                "java.nio.charset.Charset#newDecoder()", "java.nio.charset.CharsetDecoder#decode(java.nio.ByteBuffer)",
                "java.nio.charset.CharsetDecoder#onMalformedInput(java.nio.charset.CodingErrorAction)",
                "java.nio.charset.CharsetDecoder#onUnmappableCharacter(java.nio.charset.CodingErrorAction)",
                "java.util.ArrayList#add(java.lang.Object)",
                "java.util.List#size()", "java.util.List#copyOf(java.util.Collection)",
                "java.util.Objects#requireNonNull(java.lang.Object,java.lang.String)");
        Set<String> allowedNews = Set.of(
                "java.lang.IllegalArgumentException#<init>(java.lang.String)",
                "java.lang.IllegalArgumentException#<init>(java.lang.String,java.lang.Throwable)",
                "java.util.ArrayList#<init>(int)");
        Set<String> allowedFields = Set.of(
                "matrix.realworld.MindState#SCHEMA_V3", "matrix.realworld.MindState#MAX_HISTORY_V1",
                "matrix.realworld.MindState.Reader#MAX_SYMBOL_BYTES",
                "matrix.realworld.MindState.Reader#MAX_PAYLOAD_BYTES",
                "matrix.realworld.MindState.Reader#MAX_ENUM_BYTES",
                "matrix.realworld.MindState.Reader#source", "matrix.realworld.MindState.Reader#cursor",
                "java.lang.Integer#BYTES", "java.nio.charset.StandardCharsets#UTF_8",
                "java.nio.charset.CodingErrorAction#REPORT",
                "matrix.causal.CausalRecord.Channel#class",
                "matrix.causal.CausalRecord.PrincipalKind#class",
                "matrix.causal.CausalRecord.Fidelity#class",
                "matrix.realworld.MindState.EpistemicStatus#class",
                "matrix.causal.CausalRecord.ClaimClass#class",
                "matrix.causal.CausalRecord.ClaimClass#LEGACY_UNCLASSIFIED",
                "matrix.causal.CausalRecord.ClaimClass#STRUCTURED",
                "matrix.causal.CausalRecord.PrincipalKind#UNKNOWN",
                "matrix.causal.CausalRecord.MemoryRef#subject",
                "matrix.causal.CausalRecord.MemoryRef#revision",
                "matrix.causal.CausalRecord.MemoryRef#sequence",
                "matrix.causal.CausalRecord.Subject#key",
                "matrix.causal.CausalRecord.Symbol#value",
                "Array#length");
        java.util.ArrayDeque<String> todo = new java.util.ArrayDeque<>();
        Set<String> seen = new java.util.HashSet<>();
        Set<String> observedTerminalCalls = new java.util.LinkedHashSet<>();
        Set<String> observedTerminalNews = new java.util.LinkedHashSet<>();
        Set<String> observedFields = new java.util.LinkedHashSet<>();
        Set<String> exactEnums = Set.of("matrix.causal.CausalRecord.Channel",
                "matrix.causal.CausalRecord.PrincipalKind",
                "matrix.causal.CausalRecord.Fidelity",
                "matrix.realworld.MindState.EpistemicStatus",
                "matrix.causal.CausalRecord.ClaimClass");
        Set<String> observedEnums = new java.util.LinkedHashSet<>();
        Set<javax.lang.model.element.ExecutableElement> virtualTargets =
                new java.util.LinkedHashSet<>();
        todo.add(key(doors.get(0), types));
        while (!todo.isEmpty()) {
            String method = todo.remove(); if (!seen.add(method)) continue;
            Facts f = facts.get(method); if (f == null || f.bad) return false;
            if (f.bodyless) {
                if (!method.equals("matrix.causal.CausalId#tick()")
                        && !method.equals("matrix.causal.CausalId#sequence()")) return false;
                String suffix = method.substring(method.indexOf('#'));
                for (String candidate : facts.keySet()) {
                    if (candidate.startsWith("matrix.causal.CausalId.")
                            && candidate.endsWith(suffix)) todo.add(candidate);
                }
                continue;
            }
            int separator = method.indexOf('#');
            String methodOwner = separator < 0 ? "" : method.substring(0, separator);
            Set<String> clinit = classInitializers.get(methodOwner);
            if (clinit != null) todo.addAll(clinit);
            if (method.contains("#<init>(")) {
                Set<String> iinit = instanceInitializers.get(methodOwner);
                if (iinit != null) todo.addAll(iinit);
            }
            for (String enumOwner : f.enumOwners) {
                if (!exactEnums.contains(enumOwner)) return false;
                observedEnums.add(enumOwner);
                Set<String> initializer = classInitializers.get(enumOwner);
                if (initializer != null) todo.addAll(initializer);
            }
            for (String call : f.calls) {
                if (facts.containsKey(call)) todo.add(call);
                else if (!allowedCalls.contains(call)) return false;
                else {
                    observedTerminalCalls.add(call);
                    var target = methods.get(call);
                    if (target != null) return false;
                    javax.lang.model.element.ExecutableElement declaration = findExecutable(
                            task, call, types);
                    if (declaration != null && isVirtual(declaration)) {
                        virtualTargets.add(declaration);
                    }
                }
            }
            for (String constructor : f.news) {
                if (facts.containsKey(constructor)) todo.add(constructor);
                else if (!allowedNews.contains(constructor)) return false;
                else observedTerminalNews.add(constructor);
            }
            if (!allowedFields.containsAll(f.fields)) return false;
            observedFields.addAll(f.fields);
            for (var declaration : virtualTargets) {
                for (var candidate : methods.values()) {
                    String candidateOwnerName = owner(candidate);
                    boolean constructed = seen.stream().anyMatch(value ->
                            value.startsWith(candidateOwnerName + "#<init>("));
                    var candidateOwner = (javax.lang.model.element.TypeElement)
                            candidate.getEnclosingElement();
                    if (constructed && task.getElements().overrides(
                            candidate, declaration, candidateOwner)) todo.add(key(candidate, types));
                }
            }
        }
        return observedTerminalCalls.equals(allowedCalls)
                && observedTerminalNews.equals(allowedNews)
                && observedFields.equals(allowedFields)
                && observedEnums.equals(exactEnums)
                && seen.containsAll(Set.of("matrix.realworld.MindState.Reader#readInt(java.lang.String)",
                "matrix.realworld.MindState.Reader#readLong(java.lang.String)",
                "matrix.realworld.MindState.Reader#readWord(int,java.lang.String)",
                "matrix.realworld.MindState.Reader#requireEnd()"));
    }

    private static javax.lang.model.element.ExecutableElement findExecutable(
            com.sun.source.util.JavacTask task, String wanted,
            javax.lang.model.util.Types types) {
        int hash = wanted.indexOf('#');
        var owner = task.getElements().getTypeElement(wanted.substring(0, hash));
        if (owner == null) return null;
        return owner.getEnclosedElements().stream()
                .filter(element -> element instanceof javax.lang.model.element.ExecutableElement)
                .map(javax.lang.model.element.ExecutableElement.class::cast)
                .filter(element -> key(element, types).equals(wanted)).findFirst().orElse(null);
    }

    private static boolean isVirtual(javax.lang.model.element.ExecutableElement method) {
        Set<javax.lang.model.element.Modifier> modifiers = method.getModifiers();
        return method.getKind() == javax.lang.model.element.ElementKind.METHOD
                && !modifiers.contains(javax.lang.model.element.Modifier.STATIC)
                && !modifiers.contains(javax.lang.model.element.Modifier.PRIVATE)
                && !modifiers.contains(javax.lang.model.element.Modifier.FINAL)
                && !method.getEnclosingElement().getModifiers().contains(
                        javax.lang.model.element.Modifier.FINAL);
    }

    private static String owner(javax.lang.model.element.ExecutableElement e) {
        return ((javax.lang.model.element.TypeElement)e.getEnclosingElement()).getQualifiedName().toString();
    }
    private static String key(javax.lang.model.element.ExecutableElement e,
            javax.lang.model.util.Types types) {
        String name = e.getKind() == javax.lang.model.element.ElementKind.CONSTRUCTOR
                ? "<init>" : e.getSimpleName().toString();
        String parameters = e.getParameters().stream().map(p -> types.erasure(p.asType()).toString()
                .replace('$','.')).reduce((a,b) -> a + "," + b).orElse("");
        return owner(e) + "#" + name + "(" + parameters + ")";
    }
    private static final class Facts {
        final Set<String> calls = new java.util.LinkedHashSet<>();
        final Set<String> news = new java.util.LinkedHashSet<>();
        final Set<String> fields = new java.util.LinkedHashSet<>();
        final Set<String> enumOwners = new java.util.LinkedHashSet<>();
        boolean bad, bodyless;
    }

    private static void roundtrip(String name, MindState state) {
        byte[] bytes = state.canonicalBytes(); MindState decoded = MindState.fromCanonicalBytes(bytes);
        checkRoundtrip(state.equals(decoded) && Arrays.equals(bytes, decoded.canonicalBytes()), name);
    }
    private static void twin(Frame twin, String name) {
        cases++;
        try {
            byte[] encoded = frame(twin); MindState decoded = MindState.fromCanonicalBytes(encoded);
            if (!decoded.equals(twin.state()) || !Arrays.equals(encoded, decoded.canonicalBytes())) roundtripFail++;
        } catch (IllegalArgumentException expectedForInvalidTwin) {
            if (!name.endsWith("-invalid")) roundtripFail++;
        }
    }
    private static void refusedFrame(String name, Frame frame, int count) {
        byte[] bytes = frame(frame); int offset = 4 + 4 + frame.subject().getBytes(StandardCharsets.UTF_8).length + 8;
        putInt(bytes, offset, count); refused(name, () -> decode(bytes));
    }
    private static void malformedSymbol(byte[] bytes, byte first, byte second) {
        putInt(bytes, 4, 2); bytes[8] = first; bytes[9] = second;
    }
    private static int contentLengthOffset(byte[] bytes) {
        int cursor = skipWord(bytes, 4) + 8 + 4;
        cursor = skipWord(bytes, cursor) + 8 + 4;
        cursor = skipWord(bytes, cursor) + 8 + 4;
        return skipWord(bytes, cursor);
    }
    private static int skipWord(byte[] bytes, int lengthOffset) {
        int length = ((bytes[lengthOffset] & 0xff) << 24)
                | ((bytes[lengthOffset + 1] & 0xff) << 16)
                | ((bytes[lengthOffset + 2] & 0xff) << 8)
                | (bytes[lengthOffset + 3] & 0xff);
        return lengthOffset + 4 + length;
    }
    private static byte[] replaceWord(byte[] source, String oldValue, String replacement) {
        byte[] oldBytes = oldValue.getBytes(StandardCharsets.UTF_8);
        byte[] newBytes = replacement.getBytes(StandardCharsets.UTF_8);
        if (oldBytes.length != newBytes.length) throw new IllegalArgumentException("fixture width");
        byte[] copy = source.clone();
        outer: for (int i = 4; i <= copy.length - oldBytes.length; i++) {
            for (int j = 0; j < oldBytes.length; j++) if (copy[i + j] != oldBytes[j]) continue outer;
            System.arraycopy(newBytes, 0, copy, i, newBytes.length); return copy;
        }
        throw new IllegalArgumentException("fixture word absent");
    }
    private static MindState decode(byte[] bytes) { return MindState.fromCanonicalBytes(bytes); }
    private static void checkRoundtrip(boolean ok, String name) {
        cases++; if (!ok) { roundtripFail++; System.out.println("MIND_STATE_CODEC_BROKEN " + name); }
    }
    private static void checkFuture(boolean ok, String name) {
        cases++; if (!ok) { futureFail++; System.out.println("MIND_STATE_CODEC_BROKEN " + name); }
    }
    private static void checkSource(boolean ok, String name) {
        cases++; if (!ok) { sourceFail++; System.out.println("MIND_STATE_CODEC_BROKEN " + name); }
    }
    private static void refused(String name, Runnable action) {
        cases++; try { action.run(); refusalFail++; }
        catch (IllegalArgumentException | NullPointerException expected) { }
    }

    private static MindState state(CausalRecord.Subject s, long revision,
            List<MindState.MemoryTrace> history) { return new MindState(s, revision, history); }
    private static MindState.MemoryTrace trace(CausalRecord.Subject s, long revision, int sequence,
            long tick, int percept, CausalRecord.Channel channel, String content,
            CausalRecord.Principal source, int uncertainty, CausalRecord.Fidelity fidelity,
            CausalRecord.PresentedClaim claim) {
        return new MindState.MemoryTrace(new CausalRecord.MemoryRef(s, revision, sequence),
                new CausalRecord.PerceptRef(s, new CausalId.Percept(tick, percept)),
                new MindState.InterpretationV1(channel, new CausalRecord.Payload(content), source,
                        uncertainty, fidelity, MindState.EpistemicStatus.UNRESOLVED, claim));
    }

    private record Frame(String subject, long revision, String memorySubject,
                         long memoryRevision, int memorySequence, String basisSubject,
                         long tick, int perceptSequence, CausalRecord.Channel channel,
                         String content, CausalRecord.PrincipalKind principalKind,
                         String principalKey, int uncertainty, CausalRecord.Fidelity fidelity,
                         CausalRecord.PresentedClaim claim) {
        static Frame base() { return new Frame("human-codec", 1, "human-codec", 1, 0,
                "human-codec", 9, 0, CausalRecord.Channel.TEXT, "hello",
                CausalRecord.PrincipalKind.HUMAN, "friend", 321,
                CausalRecord.Fidelity.PARTIAL,
                CausalRecord.PresentedClaim.structured("door.state", "open")); }
        MindState state() { var s = new CausalRecord.Subject(subject); return new MindState(s,
                revision, List.of(trace(s, memoryRevision, memorySequence, tick, perceptSequence,
                channel, content, new CausalRecord.Principal(principalKind, principalKey),
                uncertainty, fidelity, claim))); }
        Frame withSubject(String v){return new Frame(v,revision,v,memoryRevision,memorySequence,v,tick,perceptSequence,channel,content,principalKind,principalKey,uncertainty,fidelity,claim);}
        Frame withRevision(long v){return new Frame(subject,v,memorySubject,memoryRevision,memorySequence,basisSubject,tick,perceptSequence,channel,content,principalKind,principalKey,uncertainty,fidelity,claim);}
        Frame withMemorySubject(String v){return new Frame(subject,revision,v,memoryRevision,memorySequence,basisSubject,tick,perceptSequence,channel,content,principalKind,principalKey,uncertainty,fidelity,claim);}
        Frame withMemoryRevision(long v){return new Frame(subject,revision,memorySubject,v,memorySequence,basisSubject,tick,perceptSequence,channel,content,principalKind,principalKey,uncertainty,fidelity,claim);}
        Frame withMemorySequence(int v){return new Frame(subject,revision,memorySubject,memoryRevision,v,basisSubject,tick,perceptSequence,channel,content,principalKind,principalKey,uncertainty,fidelity,claim);}
        Frame withBasisSubject(String v){return new Frame(subject,revision,memorySubject,memoryRevision,memorySequence,v,tick,perceptSequence,channel,content,principalKind,principalKey,uncertainty,fidelity,claim);}
        Frame withTick(long v){return new Frame(subject,revision,memorySubject,memoryRevision,memorySequence,basisSubject,v,perceptSequence,channel,content,principalKind,principalKey,uncertainty,fidelity,claim);}
        Frame withPerceptSequence(int v){return new Frame(subject,revision,memorySubject,memoryRevision,memorySequence,basisSubject,tick,v,channel,content,principalKind,principalKey,uncertainty,fidelity,claim);}
        Frame withChannel(CausalRecord.Channel v){return new Frame(subject,revision,memorySubject,memoryRevision,memorySequence,basisSubject,tick,perceptSequence,v,content,principalKind,principalKey,uncertainty,fidelity,claim);}
        Frame withContent(String v){return new Frame(subject,revision,memorySubject,memoryRevision,memorySequence,basisSubject,tick,perceptSequence,channel,v,principalKind,principalKey,uncertainty,fidelity,claim);}
        Frame withPrincipal(CausalRecord.PrincipalKind k,String v){return new Frame(subject,revision,memorySubject,memoryRevision,memorySequence,basisSubject,tick,perceptSequence,channel,content,k,v,uncertainty,fidelity,claim);}
        Frame withUncertainty(int v){return new Frame(subject,revision,memorySubject,memoryRevision,memorySequence,basisSubject,tick,perceptSequence,channel,content,principalKind,principalKey,v,fidelity,claim);}
        Frame withFidelity(CausalRecord.Fidelity v){return new Frame(subject,revision,memorySubject,memoryRevision,memorySequence,basisSubject,tick,perceptSequence,channel,content,principalKind,principalKey,uncertainty,v,claim);}
        Frame withClaim(CausalRecord.PresentedClaim v){return new Frame(subject,revision,memorySubject,memoryRevision,memorySequence,basisSubject,tick,perceptSequence,channel,content,principalKind,principalKey,uncertainty,fidelity,v);}
    }

    private static byte[] frame(Frame f) { return frame(List.of(f)); }
    private static byte[] frame(List<Frame> frames) {
        Frame h=frames.get(0); ByteArrayOutputStream out=new ByteArrayOutputStream();
        putInt(out,3); putWord(out,h.subject); putLong(out,h.revision); putInt(out,frames.size());
        for(Frame f:frames) writeTrace(out,f,null,null,null); return out.toByteArray();
    }
    private static byte[] frame(Frame f, String channel, String claimClass, String claimKey) {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); putInt(out,3); putWord(out,f.subject);
        putLong(out,f.revision); putInt(out,1); writeTrace(out,f,channel,claimClass,claimKey); return out.toByteArray();
    }
    private static void writeTrace(ByteArrayOutputStream out, Frame f, String channel,
            String claimClass, String claimKey) {
        putWord(out,f.memorySubject); putLong(out,f.memoryRevision); putInt(out,f.memorySequence);
        putWord(out,f.basisSubject); putLong(out,f.tick); putInt(out,f.perceptSequence);
        putWord(out,channel==null?f.channel.name():channel); putWord(out,f.content);
        putWord(out,f.principalKind.name()); putWord(out,f.principalKey); putInt(out,f.uncertainty);
        putWord(out,f.fidelity.name()); putWord(out,MindState.EpistemicStatus.UNRESOLVED.name());
        putWord(out,claimClass==null?f.claim.claimClass().name():claimClass);
        putWord(out,claimKey==null?f.claim.claim().key().value():claimKey);
        putWord(out,f.claim.position().key().value());
    }
    private static void putWord(ByteArrayOutputStream out,String v){byte[] b=v.getBytes(StandardCharsets.UTF_8);putInt(out,b.length);out.writeBytes(b);}
    private static void putInt(ByteArrayOutputStream out,int v){out.write(v>>>24);out.write(v>>>16);out.write(v>>>8);out.write(v);}
    private static void putLong(ByteArrayOutputStream out,long v){putInt(out,(int)(v>>>32));putInt(out,(int)v);}
    private static void putInt(byte[] b,int p,int v){b[p]=(byte)(v>>>24);b[p+1]=(byte)(v>>>16);b[p+2]=(byte)(v>>>8);b[p+3]=(byte)v;}
    private MindStateCodecs() {}
}
