package org.hisp.dhis.jsontree;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(2)
public class EscapingBenchmark {

    @Param({"clean", "sparse", "dense", "worst"})
    public String shape;

    @Param({"100", "1000", "10000", "100000"})
    public int length;

    private String input;

    // 1<<20 is larger than any single fixture's escaped output;
    // reused across invocations so we measure the escaper, not allocation.
    private StringBuilder out;
    private JsonAppender appender;
    private Appender oldAppender;

    @Setup(Level.Trial)
    public void setup() {
        input    = fixture(shape, length);
        out      = new StringBuilder(1 << 20);
        appender = new JsonAppender(JsonBuilder.MINIMIZED, Appender.of(out)); // adapt to the real constructor
        oldAppender = Appender.of(out);
    }

    @Benchmark
    public void oldNaive(Blackhole bh) {
        out.setLength(0);
        appendEscaped(oldAppender, input);
        bh.consume(out.length());
    }

    @Benchmark
    public void current(Blackhole bh) {
        out.setLength(0);
        appender.appendEscaped(input);
        bh.consume(out.length());
    }

    static final char[] ESCAPE_CHARS = { '"', '\\', '\n', '\r', '\t', '\b', '\f', '\u0001', '\u001F' };

    static String fixture(String shape, int targetLen) {
        Random rnd = new Random(42);
        StringBuilder sb = new StringBuilder(targetLen + 16);
        switch (shape) {
            case "clean" -> {
                // pure printable ASCII, no escapes at all → fast path
                while (sb.length() < targetLen) sb.append("abcdefghijklmnopqrstuvwxyz0123456789 ");
            }
            case "sparse" -> {
                // ~1 escape per 200 chars → fast path fails, escape path taken
                while (sb.length() < targetLen) {
                    if (rnd.nextInt(200) == 0) sb.append(ESCAPE_CHARS[rnd.nextInt(ESCAPE_CHARS.length)]);
                    else sb.append((char) ('a' + rnd.nextInt(26)));
                }
            }
            case "dense" -> {
                // ~30% escapes → escape path with a lot of per-char work
                while (sb.length() < targetLen) {
                    if (rnd.nextInt(10) < 3) sb.append(ESCAPE_CHARS[rnd.nextInt(ESCAPE_CHARS.length)]);
                    else sb.append((char) ('a' + rnd.nextInt(26)));
                }
            }
            case "worst" -> {
                // every char is \u0001 → 6× expansion, buffer flushes constantly
                while (sb.length() < targetLen) sb.append('\u0001');
            }
            default -> throw new IllegalArgumentException(shape);
        }
        return sb.toString();
    }

    static void appendEscaped(Appender json, CharSequence str) {
        if (str == null) { json.append("null"); return; }
        json.append('"');
        str.chars().forEachOrdered(c -> appendEscaped(json, c));
        json.append('"');
    }

    private static void appendEscaped(Appender json, int c) {
        switch (c) {
            case '\b' -> json.append("\\b");
            case '\f' -> json.append("\\f");
            case '\n' -> json.append("\\n");
            case '\r' -> json.append("\\r");
            case '\t' -> json.append("\\t");
            case '"'  -> json.append("\\\"");
            case '\\' -> json.append("\\\\");
            case 0x2028 -> json.append("\\u2028");
            case 0x2029 -> json.append("\\u2029");
            default -> {
                if (c < 0x20) json.append("\\u%04X".formatted(c));
                else json.append((char) c);
            }
        }
    }
}