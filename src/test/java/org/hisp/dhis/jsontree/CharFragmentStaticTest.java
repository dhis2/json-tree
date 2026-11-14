package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.*;

import org.hisp.dhis.jsontree.internal.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the static {@link CharFragment#concat(CharFragment, CharSequence)} method directly.
 *
 * <p>That method is the shared low-level implementation behind the instance-level {@code concat}
 * overrides of concrete fragments such as {@link Text}. Exercising it directly (rather than only
 * via an instance's {@code concat(suffix)}) lets us pin down:
 *
 * <ul>
 *   <li>correct use of the offset-aware {@code copyTo} on both operands,
 *   <li>the choice between the {@code CharSeq} fast path ({@code cs.copyTo}) and the generic
 *       {@code charAt} loop for non-{@code CharSeq} suffixes,
 *   <li>the suffix length being captured once (mutation-safe), and
 *   <li>the result being produced by {@code self.wrap(...)} (concrete subtype preserved).
 * </ul>
 *
 * @author AI
 */
class CharFragmentStaticTest {

  // ---------------------------------------------------------------------------
  // minimal CharFragment: exercises the generic T path
  // ---------------------------------------------------------------------------

  private static final class TestFragment implements CharFragment<TestFragment> {

    private final char[] buffer;
    private final int offset;
    private final int length;

    TestFragment(String s) {
      this(s.toCharArray(), 0, s.length());
    }

    TestFragment(char[] buffer, int offset, int length) {
      this.buffer = buffer;
      this.offset = offset;
      this.length = length;
    }

    @Override
    public int length() {
      return length;
    }

    @Override
    public char charAt(int index) {
      if (index < 0 || index >= length)
        throw new IndexOutOfBoundsException("index " + index);
      return buffer[offset + index];
    }

    @Override
    public TestFragment self() {
      return this;
    }

    @Override
    public TestFragment wrap(char[] buffer, int offset, int length) {
      return new TestFragment(buffer, offset, length);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof TestFragment t && contentEquals(t);
    }

    @Override
    public int hashCode() {
      return CharSeq.hashCode(this);
    }

    @Override
    public String toString() {
      return new String(buffer, offset, length);
    }
  }

  // ---------------------------------------------------------------------------
  // minimal CharSeq (not a CharFragment): used to prove the CharSeq fast path
  // ---------------------------------------------------------------------------

  private static final class TrackingCharSeq implements CharSeq {

    private final String str;
    boolean copyToCalled;

    TrackingCharSeq(String str) {
      this.str = str;
    }

    @Override
    public int length() {
      return str.length();
    }

    @Override
    public char charAt(int index) {
      return str.charAt(index);
    }

    @Override
    public @NotNull CharSequence subSequence(int s, int e) {
      return this.str.substring(s, e);
    }

    @Override
    public void copyTo(char[] dest, int offset) {
      copyToCalled = true;
      str.getChars(0, str.length(), dest, offset);
    }

    @Override
    public @NotNull String toString() {
      return str;
    }
  }

  private static TestFragment frag(String s) {
    return new TestFragment(s);
  }

  // ---------------------------------------------------------------------------
  // basic correctness: both suffix kinds
  // ---------------------------------------------------------------------------

  @DisplayName("static concat: generic T with String suffix")
  @Test
  void testConcat_GenericWithStringSuffix() {
    TestFragment hello = frag("hello");
    TestFragment result = CharFragment.concat(hello, " world");
    assertEquals(frag("hello world"), result);
    assertEquals("hello world", result.toString());
    assertEquals("hello", hello.toString(), "input must not be mutated");
  }

  @DisplayName("static concat: generic T with CharFragment suffix")
  @Test
  void testConcat_GenericWithFragmentSuffix() {
    assertEquals("hello world", CharFragment.concat(frag("hello"), frag(" world")).toString());
  }

  @DisplayName("static concat: Text with String suffix")
  @Test
  void testConcat_TextWithStringSuffix() {
    Text result = CharFragment.concat(Text.of("hello"), " world");
    assertEquals(Text.of("hello world"), result);
    assertInstanceOf(Text.class, result);
  }

  // ---------------------------------------------------------------------------
  // empty operands: content correctness (no shortcut semantics required)
  // ---------------------------------------------------------------------------

  @DisplayName("static concat: empty suffix")
  @Test
  void testConcat_EmptySuffix() {
    assertEquals(frag("hello"), CharFragment.concat(frag("hello"), ""));
    assertEquals(frag("hello"), CharFragment.concat(frag("hello"), frag("")));
    assertEquals(Text.of("hello"), CharFragment.concat(Text.of("hello"), ""));
    assertEquals(Text.of("hello"), CharFragment.concat(Text.of("hello"), Text.EMPTY));
  }

  @DisplayName("static concat: empty self")
  @Test
  void testConcat_EmptySelf() {
    assertEquals(frag("hello"), CharFragment.concat(frag(""), "hello"));
    assertEquals(frag("hello"), CharFragment.concat(frag(""), frag("hello")));
    assertEquals(Text.of("hello"), CharFragment.concat(Text.of(""), "hello"));
  }

  // ---------------------------------------------------------------------------
  // non-zero offset on self / suffix (slice as operand)
  // ---------------------------------------------------------------------------

  @DisplayName("static concat: self with non-zero offset")
  @Test
  void testConcat_SelfWithOffset() {
    Text helloWorld = Text.of("hello world");
    Text world = helloWorld.subSequence(6); // offset 6 into the parent buffer
    assertEquals("world", world.toString());

    assertEquals(Text.of("world!"), CharFragment.concat(world, "!"));

    // generic fragment constructed with an explicit offset (subSequence of a plain
    // CharFragment allocates offset 0, so we need the constructor to hit the offset path)
    TestFragment worldFrag = new TestFragment("hello world".toCharArray(), 6, 5);
    assertEquals("world!", CharFragment.concat(worldFrag, "!").toString());
  }

  @DisplayName("static concat: suffix with non-zero offset (CharSeq fast path)")
  @Test
  void testConcat_SuffixWithOffset() {
    Text helloWorld = Text.of("hello world");
    Text world = helloWorld.subSequence(6); // offset 6, length 5

    // TextSlice.copyTo uses System.arraycopy from its own offset, so this exercises
    // cs.copyTo with a non-zero suffix offset.
    assertEquals(Text.of("hello world"), CharFragment.concat(Text.of("hello "), world));

    // same slice as suffix with a generic fragment on the left
    assertEquals("hello world", CharFragment.concat(frag("hello "), world).toString());
  }

  @DisplayName("static concat: both operands are slices of the same parent")
  @Test
  void testConcat_BothOperandsFromSameParent() {
    Text helloWorld = Text.of("hello world");
    Text hello = helloWorld.subSequence(0, 5);
    Text world = helloWorld.subSequence(6); // skips the space at index 5
    assertEquals(Text.of("helloworld"), CharFragment.concat(hello, world));
  }

  @DisplayName("static concat: self from the 0-999 int cache")
  @Test
  void testConcat_SelfFromIntCache() {
    // Text.of(123) has a non-zero offset into ZERO_TO_999
    assertEquals(Text.of("1234"), CharFragment.concat(Text.of(123), "4"));
    // a sub-slice of the int cache keeps its offset
    Text n23 = Text.of(123).subSequence(1); // "23" at offset 1 within the "123" block
    assertEquals(Text.of("234"), CharFragment.concat(n23, "4"));
  }

  // ---------------------------------------------------------------------------
  // suffix kinds: CharSeq fast path vs generic CharSequence
  // ---------------------------------------------------------------------------

  @DisplayName("static concat: CharSeq suffix uses the copyTo fast path")
  @Test
  void testConcat_CharSeqFastPathUsed() {
    TrackingCharSeq suffix = new TrackingCharSeq(" world");
    TestFragment result = CharFragment.concat(frag("hello"), suffix);
    assertEquals("hello world", result.toString());
    assertTrue(suffix.copyToCalled, "CharSeq.copyTo should be used for CharSeq suffixes");
  }

  @DisplayName("static concat: StringBuilder suffix uses the generic charAt path")
  @Test
  void testConcat_StringBuilderSuffix() {
    StringBuilder sb = new StringBuilder(" world");
    TestFragment result = CharFragment.concat(frag("hello"), sb);
    assertEquals("hello world", result.toString());

    // result is a copy: mutating the StringBuilder afterwards has no effect
    sb.setCharAt(0, '!');
    assertEquals("hello world", result.toString());
  }

  @DisplayName("static concat: suffix length is captured once")
  @Test
  void testConcat_SuffixLengthCapturedOnce() {
    StringBuilder sb = new StringBuilder("abc");
    TestFragment result = CharFragment.concat(frag("x"), sb);
    assertEquals("xabc", result.toString());
    sb.append("def"); // must not affect the result
    assertEquals("xabc", result.toString());
  }

  // ---------------------------------------------------------------------------
  // aliasing: self and suffix share storage
  // ---------------------------------------------------------------------------

  @DisplayName("static concat: self and suffix are the same instance")
  @Test
  void testConcat_SelfIsSuffix() {
    TestFragment ab = frag("ab");
    TestFragment result = CharFragment.concat(ab, ab);
    assertEquals("abab", result.toString());
    assertNotSame(ab, result);

    Text textAb = Text.of("ab");
    assertEquals(Text.of("abab"), CharFragment.concat(textAb, textAb));
  }

  @DisplayName("static concat: self and suffix are slices of the same buffer")
  @Test
  void testConcat_SlicesOfSameBufferAliased() {
    Text hello = Text.of("hello world");
    Text prefix = hello.subSequence(0, 5); // "hello"
    Text suffix = hello.subSequence(6);    // "world"
    assertEquals("helloworld", CharFragment.concat(prefix, suffix).toString());

    // same slice instance used as both operands
    Text self = hello.subSequence(0, 5);
    assertEquals("hellohello", CharFragment.concat(self, self).toString());
  }

  // ---------------------------------------------------------------------------
  // wrap / concrete subtype preservation
  // ---------------------------------------------------------------------------

  @DisplayName("static concat: result comes from T.wrap (concrete subtype preserved)")
  @Test
  void testConcat_PreservesConcreteType() {
    assertInstanceOf(TestFragment.class, CharFragment.concat(frag("hello"), " world"));
    // TextSlice is the only permitted Text implementation; its wrap keeps it a TextSlice
    assertInstanceOf(TextSlice.class, CharFragment.concat(Text.of("hello"), " world") );
  }

  // ---------------------------------------------------------------------------
  // growth / repeated application
  // ---------------------------------------------------------------------------

  @DisplayName("static concat: result spans two contiguous regions")
  @Test
  void testConcat_LongerResult() {
    Text result = CharFragment.concat(Text.of("aaa"), Text.of("bbbbbbbbbb"));
    assertEquals("aaabbbbbbbbbb", result.toString());
    assertEquals(13, result.length());
  }

  @DisplayName("static concat: repeated concatenation")
  @Test
  void testConcat_Repeated() {
    Text result = Text.of("x");
    for (int i = 0; i < 5; i++) result = CharFragment.concat(result, "x");
    assertEquals(Text.of("xxxxxx"), result);
  }

  // ---------------------------------------------------------------------------
  // empty suffix short-circuit: identity is preserved (NOT just equal)
  // ---------------------------------------------------------------------------

  @DisplayName("static concat: empty suffix returns self (identity)")
  @Test
  void testConcat_EmptySuffixReturnsSelf() {
    TestFragment generic = frag("hello");
    assertSame(generic, CharFragment.concat(generic, ""),
        "generic: empty suffix must return self");

    TestFragment genericEmptySuffix = frag("");
    assertSame(genericEmptySuffix, CharFragment.concat(genericEmptySuffix, frag("")),
        "generic: empty CharFragment suffix must return self");

    Text t = Text.of("hello");
    assertSame(t, CharFragment.concat(t, ""),
        "Text: empty suffix must return self");

    assertSame(Text.EMPTY, CharFragment.concat(Text.EMPTY, ""),
        "Text.EMPTY: empty suffix must return self");
    assertSame(Text.EMPTY, CharFragment.concat(Text.EMPTY, Text.EMPTY),
        "Text.EMPTY + Text.EMPTY must return Text.EMPTY");
  }

  @DisplayName("static concat: empty suffix preserves slice offset")
  @Test
  void testConcat_EmptySuffixPreservesSlice() {
    Text parent = Text.of("hello world");
    Text slice = parent.subSequence(6); // "world" at offset 6
    Text result = CharFragment.concat(slice, "");
    assertSame(slice, result);
    assertEquals(6, result.offsetIn(parent));
    assertTrue(result.contentMemoryEquals(parent));
  }

  @DisplayName("static concat: empty suffix preserves 0-999 int cache offset")
  @Test
  void testConcat_EmptySuffixPreservesIntCacheSlice() {
    Text n23 = Text.of(123).subSequence(1); // "23" at offset 1 of the "123" block
    assertSame(n23, CharFragment.concat(n23, ""));
    // sub-slice of a value from the int cache can still be located
    Text parent = Text.of(123);
    assertEquals(1, n23.offsetIn(parent));
  }

  // ---------------------------------------------------------------------------
  // both operands empty
  // ---------------------------------------------------------------------------

  @DisplayName("static concat: both operands empty")
  @Test
  void testConcat_BothEmpty() {
    TestFragment generic = CharFragment.concat(frag(""), "");
    assertNotNull(generic);
    assertTrue(generic.isEmpty());

    Text textResult = CharFragment.concat(Text.EMPTY, Text.EMPTY);
    assertSame(Text.EMPTY, textResult,
        "empty suffix short-circuit should return self (which is Text.EMPTY)");
    assertEquals(Text.EMPTY, textResult);
  }

  // ---------------------------------------------------------------------------
  // consistency with instance concat
  // ---------------------------------------------------------------------------

  @DisplayName("static concat: agrees with instance concat (content)")
  @Test
  void testConcat_AgreesWithInstanceMethod() {
    Text hello = Text.of("hello");
    assertEquals(hello.concat(" world"), CharFragment.concat(hello, " world"));
    assertEquals(hello.concat(Text.of(" world")), CharFragment.concat(hello, Text.of(" world")));
    assertEquals(hello.concat(""), CharFragment.concat(hello, ""));

    TestFragment frag = frag("hello");
    assertEquals(frag.concat(" world"), CharFragment.concat(frag, " world"));
    assertEquals(frag.concat(frag(" world")), CharFragment.concat(frag, frag(" world")));
    assertEquals(frag.concat(""), CharFragment.concat(frag, ""));
  }

  @DisplayName("static concat: agrees with instance concat (identity on empty suffix)")
  @Test
  void testConcat_AgreesWithInstanceMethod_Identity() {
    Text hello = Text.of("hello");
    assertSame(hello, CharFragment.concat(hello, ""));
    assertSame(hello, hello.concat(""));

    // empty slice: both paths preserve identity + offset
    Text empty = Text.of("hello world").subSequence(2, 2);
    assertSame(empty, CharFragment.concat(empty, ""));
    assertSame(empty, empty.concat(""));
    assertEquals(2, empty.concat("").offsetIn(Text.of("hello world").subSequence(0)));
  }
}