package org.hisp.dhis.jsontree;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

import org.hisp.dhis.jsontree.JsonBuilder;

/**
 * Profiling helper for {@link JsonBuilder#streamArray(OutputStream, java.util.function.Consumer)}
 * using the "many dataValues" shape that motivated the Appender refactor.
 *
 * <p><b>Not a unit test.</b> Run via {@link #main(String[])}, attach a profiler (JFR, JMC,
 * async-profiler, ...), and inspect the profile for allocation hotspots and CPU sampling. The
 * output is written to a temp file; the file path, byte size, and elapsed wall-clock time are
 * printed at the end.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * java -cp <classpath> org.hisp.dhis.jsontree.perf.DataExportOutputProfile [count] [--keep]
 * }</pre>
 *
 * <p>{@code count} is the number of data value objects to write (default {@code 1_000_000}).
 * {@code --keep} leaves the temp file in place so it can be inspected; without it the file is
 * scheduled for deletion on JVM exit.
 *
 * <p>Example profiler invocations:
 *
 * <pre>{@code
 * # JFR (bundle with JDK)
 * java -XX:StartFlightRecording=duration=60s,filename=profile.jfr \
 *      -cp <classpath> org.hisp.dhis.jsontree.perf.DataExportOutputProfile 1000000
 *
 * # async-profiler
 * java -agentpath:/path/to/libasyncProfiler.so=start,event=alloc,file=alloc.html \
 *      -cp <classpath> org.hisp.dhis.jsontree.perf.DataExportOutputProfile 1000000
 * }</pre>
 *
 * <h2>What is measured</h2>
 *
 * <p>Only the JSON generation path is measured. The target {@link OutputStream} is passed
 * straight to {@link JsonBuilder#streamArray}, and the {@code Utf8OutputStreamAppender}'s own
 * 8 KiB buffer provides the batching — there is no additional {@code BufferedOutputStream} on
 * top. If you want to profile the syscall layer as well, that is what you get here.
 *
 * <h2>What is <em>not</em> measured</h2>
 *
 * <p>Data retrieval, sorting, filtering, serialisation of audit fields from the database — none
 * of that exists. The workload is deliberately limited to constructing the JSON, which is the
 * step the original {@code DataExportOutput} loop showed as a bottleneck.
 *
 * <h2>Warmup</h2>
 *
 * <p>The profile includes JIT warmup. For steady-state sampling, either increase {@code count},
 * or ignore samples from the first second of execution. Allocation profiles generally do not
 * need warmup — cold-path allocations matter just as much as hot-path ones.
 *
 * <h2>Shape of each object</h2>
 *
 * <p>The field set mirrors what {@code DataExportOutput} writes for a data value. The
 * {@code value} member is the per-object counter, everything else is a constant. Only the
 * {@code categoryOptionCombo} branch is exercised (the alternative that writes a nested object
 * of category options is not part of this profile). {@code deleted} is present only on every
 * 100th object, matching the {@code if (dv.deleted())} shape of the original. {@code comment}
 * is always {@code null}, which the {@code MINIMIZED} pretty-print config drops — matching the
 * common case in real data.
 */
public final class DataExportOutputProfile {

  // Constants chosen to look like realistic DHIS2 data.
  private static final String DATA_ELEMENT         = "fbfJHSPpUQD";
  private static final String PERIOD               = "202401";
  private static final String ORG_UNIT             = "DiszpKrYNg8";
  private static final String CATEGORY_OPTION_COMBO = "HllvX50cXC0";
  private static final String ATTRIBUTE_OPTION_COMBO = "HllvX50cXC0";
  private static final String STORED_BY            = "admin";
  private static final String CREATED              = "2024-01-15T10:30:00.000";
  private static final String LAST_UPDATED         = "2024-01-15T10:30:00.000";

  private static final int DEFAULT_COUNT = 1_000_000;

  private DataExportOutputProfile() {}

  public static void main(String[] args) throws IOException {
    int count = DEFAULT_COUNT;
    boolean keep = false;
    for (String arg : args) {
      if ("--keep".equals(arg)) keep = true;
      else if (!arg.startsWith("--")) count = Integer.parseInt(arg);
    }

    Path file = Files.createTempFile("data-values-", ".json");
    if (!keep) file.toFile().deleteOnExit();

    System.out.printf(Locale.ROOT,
        "Writing %,d data value objects to %s%n", count, file);

    long elapsed = writeDataValues(file, count);

    long bytes = Files.size(file);
    double seconds = elapsed / 1_000_000_000d;
    double mib = bytes / (1024d * 1024d);

    System.out.printf(Locale.ROOT,
        "Wrote %,d bytes (%.2f MiB) in %.3f s (%,.0f values/s, %.1f MiB/s)%n",
        bytes, mib, seconds, count / seconds, mib / seconds);
    System.out.printf(Locale.ROOT,
        "Average %.1f bytes per data value object%n", (double) bytes / count);
    if (keep) System.out.printf(Locale.ROOT, "Output kept at %s%n", file);
  }

  private static long writeDataValues(Path file, int count) throws IOException {
    long start = System.nanoTime();
    JsonBuilder.streamArray(Files.newOutputStream(file), arr -> writeDataValues(arr, count));
    return System.nanoTime() - start;
  }

  private static void writeDataValues(JsonBuilder.JsonArrayBuilder arr, int count) {
    DataValueEncoder encoder = new DataValueEncoder();
    for (int i = 0; i < count; i++) {
      encoder.set(i, i % 100 == 0);
      arr.addObject(encoder);
    }
  }

  /**
   * A box to avoid 1 lambda allocation per loop.
   * A similar fix should be applied to DHIS2 data value export. 
   */
  private static final class DataValueEncoder implements Consumer<JsonBuilder.JsonObjectBuilder> {

    private int value;
    private boolean deleted;

    void set(int value, boolean deleted) {
      this.value = value;
      this.deleted = deleted;
    }

    @Override
    public void accept(JsonBuilder.JsonObjectBuilder val) {
      val.addString("dataElement",          DATA_ELEMENT);
      val.addString("period",               PERIOD);
      val.addString("orgUnit",              ORG_UNIT);
      val.addString("categoryOptionCombo",  CATEGORY_OPTION_COMBO);
      val.addString("attributeOptionCombo", ATTRIBUTE_OPTION_COMBO);
      val.addNumber("value",                value);
      val.addString("storedBy",             STORED_BY);
      val.addString("created",              CREATED);
      val.addString("lastUpdated",          LAST_UPDATED);
      val.addString("comment",              null);
      val.addBoolean("followup",            false);
      if (deleted) val.addBoolean("deleted", true);
    }
  }

}