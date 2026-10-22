package profiler.gen;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A JUnit 5 extension that records every executed {@code @Test} invocation (including each
 * invocation of a {@code @ParameterizedTest}) and, on JVM shutdown, generates a small set of
 * plain Java runner classes that re-invoke those tests <em>without</em> going through the JUnit
 * engine.
 *
 * <h2>Why</h2>
 * When profiling a test suite, JUnit's own machinery (extension resolution, lifecycle callbacks,
 * reflection, argument conversion, assertion infrastructure) shows up as a large, diffuse cost that
 * hides the code paths you actually want to see. The generated runners call each test method
 * directly — one call site per recorded invocation — so under a profiler the JUnit overhead
 * disappears and the profile reflects the code under test.
 *
 * <h2>Usage</h2>
 * The extension is registered via {@link java.util.ServiceLoader} through
 * {@code META-INF/services/org.junit.jupiter.api.extension.Extension} and only takes effect when
 * JUnit's extension auto-detection is enabled. It is intentionally <b>off by default</b> so normal
 * test runs are unaffected:
 *
 * <pre>{@code
 * # src/test/resources/junit-platform.properties
 * junit.jupiter.extensions.autodetection.enabled=false
 * }</pre>
 *
 * To profile, follow this workflow:
 * <ol>
 *   <li>Set {@code junit.jupiter.extensions.autodetection.enabled=true} (in
 *       {@code junit-platform.properties} or via the IDE run configuration).</li>
 *   <li>Run your test set as usual from the IDE. The extension silently records each test
 *       invocation.</li>
 *   <li>When the JVM exits, the generated runner sources are written to
 *       {@code testprof-generated/} (override with {@code -Dtestprof.outdir=...}). The directory
 *       mirrors the package structure of your test classes.</li>
 *   <li>Add {@code testprof-generated/} as a <em>test source folder</em> in your IDE project
 *       settings. This makes the generated classes compile and appear as runnable classes.</li>
 *   <li>Run the root {@code TestProfRunner} class (in the default package) under your profiler,
 *       exactly as you would run any other {@code main}.</li>
 * </ol>
 *
 * <h2>Generated output</h2>
 * Two levels of runner are produced:
 * <ul>
 *   <li><b>Per-package runners</b> ({@code <pkg>.TestProfRunner}) — one {@code runAll()} method
 *       per package that invokes every recorded test in that package. Because they live in the
 *       test's own package, package-private test classes and methods are callable directly; no
 *       reflection is used.</li>
 *   <li><b>Root runner</b> ({@code TestProfRunner}, default package) — calls each per-package
 *       runner in turn, measures total wall time, and prints a summary.</li>
 * </ul>
 * Each test invocation is wrapped by a small helper that times it, prints
 * {@code ✓ <ClassName>.<methodName> (<ms>)} or {@code ✗ ...} to {@code System.out}, and never lets
 * an exception escape. The intent is to profile an already-passing suite; failures are reported
 * but do not abort the run.
 *
 * <h2>Limitations</h2>
 * This is a profiling aid, not a replacement for JUnit. The generated code intentionally does
 * <b>not</b> replicate:
 * <ul>
 *   <li>{@code @BeforeEach} / {@code @AfterEach} / {@code @BeforeAll} / {@code @AfterAll} —
 *       tests must be self-contained, or setup must be performed explicitly.</li>
 *   <li>{@code @Nested} classes — they cannot be instantiated this way and are skipped during
 *       recording.</li>
 *   <li>Classes using {@code @TempDir} — the injected path is only populated by JUnit, so any
 *       class with a {@code @TempDir} field or parameter is excluded at generation time.</li>
 *   <li>Non-no-arg constructors on test classes, and arbitrary argument types that cannot be
 *       expressed as source literals (see {@link #argLiteral(Object, Class)}).</li>
 * </ul>
 *
 * @see InvocationInterceptor
 */
public class RecordingInterceptor implements InvocationInterceptor {

    public static final String OUT_DIR_PROP = "testprof.outdir";
    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static volatile boolean hookInstalled = false;

    private record Call(Class<?> testClass, Method method, Object[] args) {}

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                    ReflectiveInvocationContext<Method> ctx,
                                    ExtensionContext extensionContext) throws Throwable {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        if (!testClass.isAnnotationPresent(Nested.class)) {
            CALLS.add(new Call(testClass, ctx.getExecutable(), ctx.getArguments().toArray()));
        }
        ensureShutdownHook();
        invocation.proceed();
    }

    private static void ensureShutdownHook() {
        if (!hookInstalled) {
            synchronized (RecordingInterceptor.class) {
                if (!hookInstalled) {
                    Runtime.getRuntime().addShutdownHook(
                        new Thread(RecordingInterceptor::generateAll, "testprof-generate"));
                    hookInstalled = true;
                }
            }
        }
    }

    // ---------- generation ----------

    private static void generateAll() {
        if (CALLS.isEmpty()) {
            System.out.println("[testprof] No test calls were recorded — nothing to generate.");
            return;
        }
        Path outDir = Paths.get(System.getProperty(OUT_DIR_PROP, "testprof-generated"));
        try {
            Files.createDirectories(outDir);

            Map<String, List<Call>> byPackage = new LinkedHashMap<>();
            Set<Class<?>> excluded = new HashSet<>();
            int excludedCalls = 0;

            for (Call c : CALLS) {
                Class<?> tc = c.testClass();
                if (excluded.contains(tc)) { excludedCalls++; continue; }
                if (usesTempDir(tc)) {
                    excluded.add(tc);
                    excludedCalls++;
                    System.out.println("[testprof] excluding " + tc.getName() + " (uses @TempDir)");
                    continue;
                }
                byPackage.computeIfAbsent(tc.getPackageName(), k -> new ArrayList<>()).add(c);
            }

            if (byPackage.isEmpty()) {
                System.out.println("[testprof] All recorded test classes were excluded — nothing to generate.");
                return;
            }

            List<String> packageRunnerFqns = new ArrayList<>();
            for (var e : byPackage.entrySet()) {
                packageRunnerFqns.add(writePackageRunner(outDir, e.getKey(), e.getValue()));
            }
            writeRootRunner(outDir, packageRunnerFqns);

            System.out.println("[testprof] wrote " + (CALLS.size() - excludedCalls) + " calls across "
                + byPackage.size() + " packages" + (excludedCalls > 0
                ? " (" + excludedCalls + " calls / " + excluded.size()
                + " classes excluded due to @TempDir)" : "")
                + " -> " + outDir.toAbsolutePath());
        } catch (Exception ex) {
            System.err.println("[testprof] generation failed: " + ex);
            ex.printStackTrace();
        }
    }

    /**
     * True if any field, or any method parameter, in the class or its superclasses is
     * annotated with {@code @TempDir}. Such state is only populated by JUnit's own
     * injection, so invoking the class outside JUnit will see null.
     */
    private static boolean usesTempDir(Class<?> testClass) {
        for (Class<?> k = testClass; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (f.isAnnotationPresent(TempDir.class)) return true;
            }
            for (Method m : k.getDeclaredMethods()) {
                for (Parameter p : m.getParameters()) {
                    if (p.isAnnotationPresent(TempDir.class)) return true;
                }
            }
        }
        return false;
    }

    /** Writes {@code <pkg>/TestProfRunner.java}; returns its FQN. */
    private static String writePackageRunner(Path outDir, String pkg, List<Call> calls) throws Exception {
        String simple = pkg.isEmpty() ? "TestProfRunnerDefaultPackage" : "TestProfRunner";
        String fqn    = pkg.isEmpty() ? simple : pkg + "." + simple;
        Path dir      = pkg.isEmpty() ? outDir : outDir.resolve(pkg.replace('.', '/'));
        Files.createDirectories(dir);
        Path file = dir.resolve(simple + ".java");

        StringBuilder sb = new StringBuilder();
        if (!pkg.isEmpty()) sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import java.util.function.Supplier;\n\n");
        sb.append("public class ").append(simple).append(" {\n\n");

        sb.append("    @FunctionalInterface\n");
        sb.append("    private interface TestBody<T> { void run(T instance) throws Throwable; }\n\n");
        sb.append("    @FunctionalInterface\n");
        sb.append("    private interface StaticBody { void run() throws Throwable; }\n\n");

        sb.append("    private static <T> boolean run(String label, Supplier<T> factory, TestBody<T> body) {\n");
        sb.append("        long start = System.nanoTime();\n");
        sb.append("        boolean ok = true;\n");
        sb.append("        try { body.run(factory.get()); }\n");
        sb.append("        catch (Throwable t) { ok = false; }\n");
        sb.append("        long nanos = System.nanoTime() - start;\n");
        sb.append("        System.out.printf(\"%s %s (%.2f ms)%n\", ok ? \"\\u2713\" : \"\\u2717\", label, nanos / 1e6);\n");
        sb.append("        return ok;\n");
        sb.append("    }\n\n");

        sb.append("    private static boolean runStatic(String label, StaticBody body) {\n");
        sb.append("        long start = System.nanoTime();\n");
        sb.append("        boolean ok = true;\n");
        sb.append("        try { body.run(); }\n");
        sb.append("        catch (Throwable t) { ok = false; }\n");
        sb.append("        long nanos = System.nanoTime() - start;\n");
        sb.append("        System.out.printf(\"%s %s (%.2f ms)%n\", ok ? \"\\u2713\" : \"\\u2717\", label, nanos / 1e6);\n");
        sb.append("        return ok;\n");
        sb.append("    }\n\n");

        sb.append("    public static int[] runAll() {\n");
        sb.append("        int total = 0, failed = 0;\n");
        for (Call c : calls) {
            Method m = c.method();
            Class<?> tc = c.testClass();
            String classSimple = tc.getSimpleName();
            String methodName  = m.getName();
            String label       = classSimple + "." + methodName;
            boolean isStatic   = Modifier.isStatic(m.getModifiers());

            Object[] args = c.args();
            Class<?>[] ptypes = m.getParameterTypes();
            StringBuilder argsList = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) argsList.append(", ");
                argsList.append(argLiteral(args[i], ptypes[i]));
            }

            sb.append("        total++; ");
            if (isStatic) {
                String body = args.length == 0
                    ? classSimple + "::" + methodName
                    : "() -> " + classSimple + "." + methodName + "(" + argsList + ")";
                sb.append("if (!runStatic(\"").append(escString(label)).append("\", ")
                    .append(body).append(")) failed++;\n");
            } else {
                String body = args.length == 0
                    ? classSimple + "::" + methodName
                    : "t -> t." + methodName + "(" + argsList + ")";
                sb.append("if (!run(\"").append(escString(label)).append("\", ")
                    .append(classSimple).append("::new, ").append(body).append(")) failed++;\n");
            }
        }
        sb.append("        return new int[]{ total, failed };\n");
        sb.append("    }\n}\n");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("[testprof] wrote " + file);
        return fqn;
    }

    private static void writeRootRunner(Path outDir, List<String> packageRunnerFqns) throws Exception {
        Path file = outDir.resolve("TestProfRunner.java");
        StringBuilder sb = new StringBuilder();
        sb.append("public class TestProfRunner {\n");
        sb.append("    public static void main(String[] args) throws Exception {\n");
        sb.append("        long start = System.nanoTime();\n");
        sb.append("        int total = 0, failed = 0;\n");
        for (String fqn : packageRunnerFqns) {
            sb.append("        { var r = ").append(fqn)
                .append(".runAll(); total += r[0]; failed += r[1]; }\n");
        }
        sb.append("        long nanos = System.nanoTime() - start;\n");
        sb.append("        System.out.printf(\"%nTOTAL: %d tests, %d failed, %.2f ms%n\", total, failed, nanos / 1e6);\n");
        sb.append("    }\n}\n");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("[testprof] wrote " + file);
    }

    // ---------- literals ----------

    private static String argLiteral(Object arg, Class<?> paramType) {
        if (arg == null) return "(" + paramType.getCanonicalName() + ") null";

        if (arg instanceof String s)    return "\"" + escString(s) + "\"";
        if (arg instanceof Character c) return "'" + escChar(c) + "'";
        if (arg instanceof Boolean)     return arg.toString();
        if (arg instanceof Byte)        return "(byte) " + arg;
        if (arg instanceof Short)       return "(short) " + arg;
        if (arg instanceof Integer)     return arg.toString();
        if (arg instanceof Long)        return arg + "L";

        if (arg instanceof Float f) {
            if (f.isNaN()) return "Float.NaN";
            if (f == Float.POSITIVE_INFINITY) return "Float.POSITIVE_INFINITY";
            if (f == Float.NEGATIVE_INFINITY) return "Float.NEGATIVE_INFINITY";
            return f + "f";
        }
        if (arg instanceof Double d) {
            if (d.isNaN()) return "Double.NaN";
            if (d == Double.POSITIVE_INFINITY) return "Double.POSITIVE_INFINITY";
            if (d == Double.NEGATIVE_INFINITY) return "Double.NEGATIVE_INFINITY";
            return d + "d";
        }
        if (arg instanceof Enum<?> e) {
            return e.getDeclaringClass().getCanonicalName() + "." + e.name();
        }
        if (arg instanceof Class<?> k) return k.getCanonicalName() + ".class";
        if (arg.getClass().isArray()) {
            int len = Array.getLength(arg);
            Class<?> comp = arg.getClass().getComponentType();
            StringBuilder sb = new StringBuilder("new ").append(comp.getCanonicalName()).append("[]{");
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(", ");
                sb.append(argLiteral(Array.get(arg, i), comp));
            }
            return sb.append('}').toString();
        }

        throw new IllegalArgumentException(
            "Cannot serialize argument of type " + arg.getClass().getName()
                + " for parameter type " + paramType.getCanonicalName()
                + ". Extend RecordingInterceptor.argLiteral(...) to support it.");
    }

    private static String escString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static String escChar(char c) {
        return switch (c) {
            case '\\' -> "\\\\";
            case '\'' -> "\\'";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            default   -> c < 0x20 ? String.format("\\u%04x", (int) c) : Character.toString(c);
        };
    }
}