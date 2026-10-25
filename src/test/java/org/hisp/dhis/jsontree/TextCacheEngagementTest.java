package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the {@link Text} short-string cache deduplicates repeated occurrences of the
 * same constant, when that constant flows through {@link JsonAppender} and reaches the {@link
 * Appender} sink.
 *
 * <p><b>What these tests demonstrate about the current design:</b>
 *
 * <ul>
 *   <li>When the caller wraps the constant in {@link Text#of(CharSequence)} — typically once
 *       before the loop, or on each iteration if the string is short enough to be cached — every
 *       occurrence at the {@code Appender} level is the same {@code Text} instance.</li>
 *   <li>When the caller uses a {@link String} literal, as the {@code DataExportOutput} profile
 *       helper does, the {@code Appender} sees the interned {@code String}, not a {@code Text}.
 *       No allocation per iteration either way — but the mechanism is JVM string interning, not
 *       the {@code Text} cache. The cache plays no role for this pattern.</li>
 * </ul>
 *
 * <p>The practical consequence: for constant member names the {@code Text} cache is redundant
 * with string interning. The cache only matters when a caller has a dynamic {@code String}
 * whose content repeats across iterations — in which case routing it through {@code Text.of}
 * would avoid re-allocating the backing {@code char[]}.
 */
class TextCacheEngagementTest {

  // ——— Direct cache behaviour ———

  @Test
  void testTextOf_CacheHitForShortLowercase() {
    Text a = Text.of("dataElement");
    Text b = Text.of("dataElement");
    assertSame(a, b, "Text.of must return the same cached instance for short lowercase strings");
  }

  @Test
  void testTextOf_CacheHitForAllCommonMemberNames() {
    for (String name : List.of(
        "dataElement", "period", "orgUnit", "value", "storedBy", "created",
        "lastUpdated", "comment", "followup", "deleted")) {
      assertSame(Text.of(name), Text.of(name), "cache miss for: " + name);
    }
    for (String name : List.of("categoryOptionCombo", "attributeOptionCombo")) {
      assertNotSame(Text.of(name), Text.of(name), "cache miss for: " + name);
    }
  }

  @Test
  void testTextOf_NoCacheForUppercaseStart() {
    Text a = Text.of("DataElement");
    Text b = Text.of("DataElement");
    assertFalse(a == b, "uppercase-starting strings are not cached");
  }

  @Test
  void testTextOf_NoCacheForLongStrings() {
    String longName = "dataElementLongName";  // 20 chars > cache limit of 16
    Text a = Text.of(longName);
    Text b = Text.of(longName);
    assertFalse(a == b, "strings longer than 16 characters are not cached");
  }

  // ——— Cache through JsonAppender ———

  @Test
  void testJsonAppender_TextOfArg_CacheIsEngaged() {
    RecordingAppender spy = new RecordingAppender();
    JsonAppender appender = new JsonAppender(JsonBuilder.MINIMIZED, spy);

    appender.visitObject(obj -> {
      for (int i = 0; i < 10; i++) {
        obj.addString(Text.of("dataElement"), "v" + i);
      }
    });

    List<Text> nameArgs = spy.jsonEscapedArgs.stream()
        .filter(a -> a instanceof Text)
        .map(a -> (Text) a)
        .toList();

    assertFalse(nameArgs.isEmpty(), "spy saw no name arguments");
    Text first = nameArgs.get(0);
    for (Text t : nameArgs) {
      assertSame(first, t, "same Text.of(...) call must produce the same instance at the sink");
    }
  }

  @Test
  void testJsonAppender_StringLiteralArg_CacheIsNotEngaged() {
    RecordingAppender spy = new RecordingAppender();
    JsonAppender appender = new JsonAppender(JsonBuilder.MINIMIZED, spy);

    appender.visitObject(obj -> {
      for (int i = 0; i < 10; i++) {
        obj.addString("dataElement", "v" + i);
      }
    });

    List<CharSequence> nameArgs = spy.jsonEscapedArgs.stream()
        .filter(a -> "dataElement".equals(a.toString()))
        .toList();

    assertFalse(nameArgs.isEmpty(), "spy saw no name arguments");
    for (CharSequence arg : nameArgs) {
      assertFalse(arg instanceof Text,
          "String literal must not be converted to Text in the builder path");
      assertSame("dataElement", arg,
          "the same interned String instance is passed every iteration (JVM interning)");
    }
  }

  // ——— Spy ———

  /**
   * Minimal {@link Appender} that records every {@link #appendJsonEscaped(CharSequence)}
   * argument and silently swallows everything else. The JSON structure (braces, colons, commas)
   * is irrelevant for the assertions, so its methods are no-ops.
   */
  static final class RecordingAppender implements Appender {

    final List<CharSequence> jsonEscapedArgs = new ArrayList<>();

    @Override
    public void appendJsonEscaped(CharSequence value) {
      jsonEscapedArgs.add(value);
    }

    @Override public void appendAscii(char c) {}
    @Override public void appendAscii(CharSequence text, int start, int end) {}
    @Override public void appendEncoded(char c) {}
    @Override public void appendEncoded(CharSequence text, int start, int end) {}
    @Override public void appendCodePoint(int cp) {}
  }
}