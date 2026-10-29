package org.hisp.dhis.jsontree;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Tests the splitting of {@link JsonPath} into segments.
 *
 * @author Jan Bernitt
 * @since 1.1
 */
class JsonPathTest {

  @Test
  void testEscape() {
    assertEquals(
        "{/api/openapi/{path}/openapi.html}",
        JsonPath.escape(Text.of("/api/openapi/{path}/openapi.html")));
  }

  @Test
  void testSegments_Dot_Uniform() {
    assertSegments(".xxx", List.of("xxx"));
    assertSegments(".xxx.yyy", List.of("xxx", "yyy"));
    assertSegments(".xxx.yy.z", List.of("xxx", "yy", "z"));
  }

  @Test
  void testSegments_Dot_Empty() {
    assertSegments(".xxx.", List.of("xxx", "."));
    assertSegments(".xxx..a", List.of("xxx", ".", "a"));
    // but... (first char after . is never a segment start!)
    assertSegments(".xxx.[1]", List.of("xxx", "[1]"));
    assertSegments(".xxx.{1}", List.of("xxx", "{1}"));
  }

  @Test
  void testSegments_Dot_CurlyProperty() {
    assertSegments(".xxx.{curl}", List.of("xxx", "{curl}"));
    assertSegments(".xxx.{curl}.y", List.of("xxx", "{curl}", "y"));
    assertSegments(".xxx.{curl}[42]", List.of("xxx", "{curl}", "42"));
    assertSegments(".xxx.{curl}{42}", List.of("xxx", "{curl}", "42"));
    // closing } is only recognised as such if followed by a segment start (or end of path)
    assertSegments(".xxx.{curl}}", List.of("xxx", "{curl}}"));
    assertSegments(".xxx.{curl}}.y", List.of("xxx", "{curl}}", "y"));
    assertSegments(".xxx.{curl}}[42]", List.of("xxx", "{curl}}", "42"));
    assertSegments(".xxx.{curl}}{42}", List.of("xxx", "{curl}}", "42"));
  }

  @Test
  void testSegments_Curly_Uniform() {
    assertSegments("{xxx}", List.of("xxx"));
    assertSegments("{xxx}{yyy}", List.of("xxx", "yyy"));
    assertSegments("{xxx}{yy}{z}", List.of("xxx", "yy", "z"));
  }

  @Test
  void testSegments_Curly_DotProperty() {
    assertSegments("{.suffix}", List.of(".suffix"));
    assertSegments("{hello.world}", List.of("hello.world"));
    assertSegments("{prefix.}", List.of("prefix."));
    assertSegments("{.suffix}.xxx", List.of(".suffix", "xxx"));
    assertSegments("{hello.world}{curl}", List.of("hello.world", "curl"));
    assertSegments("{prefix.}[42]", List.of("prefix.", "42"));
    assertSegments(".aaa{.suffix}", List.of("aaa", ".suffix"));
    assertSegments(".aaa{hello.world}", List.of("aaa", "hello.world"));
    assertSegments(".aaa{prefix.}", List.of("aaa", "prefix."));
  }

  @Test
  void testSegments_Square_Uniform() {
    assertSegments("[111]", List.of("111"));
    assertSegments("[111][222]", List.of("111", "222"));
    assertSegments("[111][22][3]", List.of("111", "22", "3"));
  }

  @Test
  void testSegments_DotSquare_Trivial() {
    assertSegments(".xxx[1]", List.of("xxx", "1"));
    assertSegments(".xxx[1][2]", List.of("xxx", "1", "2"));
    assertSegments(".xxx[1].y[2]", List.of("xxx", "1", "y", "2"));
  }

  @Test
  void testSegments_DotSquare_CornerCases() {
    // array syntax
    assertSegments("[0]", List.of("0"));
    assertSegments("foo[0]", List.of("foo", "0"));

    // literally
    assertSegments("[-1]", List.of("[-1]"));
    assertSegments("foo[-1]", List.of("foo[-1]"));
    assertSegments(".xxx[:1:5]", List.of("xxx[:1:5]"));
    assertSegments("[1:5]", List.of("[1:5]"));
    assertSegments(".xxx[1:5]", List.of("xxx[1:5]"));
    assertSegments("[-]", List.of("[-]"));
    assertSegments("foo[-]", List.of("foo[-]"));
    assertSegments("[]", List.of("[]"));
    assertSegments("foo[]", List.of("foo[]"));
    assertSegments("[a]", List.of("[a]"));
    assertSegments("foo[a]", List.of("foo[a]"));
    assertSegments("[:]", List.of("[:]"));
    assertSegments("foo[:]", List.of("foo[:]"));
    assertSegments("[?]", List.of("[?]"));
    assertSegments("foo[?]", List.of("foo[?]"));
    assertSegments("[?id=foo]", List.of("[?id=foo]"));
    assertSegments("foo[?id=bar]", List.of("foo[?id=bar]"));
  }

  @Test
  void testSegments_DotCurly_Trivial() {
    assertSegments(".xxx{1}", List.of("xxx", "1"));
    assertSegments(".xxx{1}{2}", List.of("xxx", "1", "2"));
    assertSegments(".xxx{1}.y{2}", List.of("xxx", "1", "y", "2"));
  }

  @Test
  void testParent_Dot_Uniform() {
    assertParent("", ".x");
    assertParent(".x", ".x.y");
    assertParent(".x.yy", ".x.yy.zzz");
  }

  @Test
  void testParent_Curly_Uniform() {
    assertParent("", ".{x}");
    assertParent(".x", "{x}{y}");
    assertParent(".x.yy", "{x}{yy}{zzz}");
  }

  @Test
  void testParent_Square_Uniform() {
    assertParent("", "[1]");
    assertParent(".1", "[1][22]");
    assertParent(".1.22", "[1][22][333]");
  }

  @Test
  void testParent_Mixed() {
    assertParent(".1.xv", "[1].xv{.}");
    assertParent(".1.xv", "[1]{xv}.{h}");
  }

  @Test
  void testEmpty() {
    assertTrue(JsonPath.of("").isEmpty());
    assertTrue(JsonPath.SELF.isEmpty());
    assertFalse(JsonPath.of(".x").isEmpty());
    assertFalse(JsonPath.of("[0]").isEmpty());
    assertFalse(JsonPath.of("{x}").isEmpty());
  }

  @Test
  void testSize() {
    assertEquals(0, JsonPath.SELF.length());
    assertEquals(0, JsonPath.of("").length());
    assertEquals(1, JsonPath.of(".yeah").length());
    assertEquals(1, JsonPath.of("[1234]").length());
    assertEquals(1, JsonPath.of("{dotty.}").length());
    assertEquals(2, JsonPath.of(".yeah.yeah").length());
    assertEquals(2, JsonPath.of(".links[1234]").length());
    assertEquals(2, JsonPath.of("{dotty.}.dot").length());
    assertEquals(3, JsonPath.of(".yeah.yeah.yeahs").length());
  }

  @Test
  void testParentPath() {
    assertEquals(JsonPath.of(".one"), JsonPath.of(".one.two").parentPath());
    assertEquals(JsonPath.of(".yeah.yeah"), JsonPath.of(".yeah.yeah.yeahs").parentPath());
  }

  @Test
  void testParentPath_Empty() {
    JsonPathException ex = assertThrowsExactly(JsonPathException.class, JsonPath.SELF::parentPath);
    assertEquals("Root/self path does not have a parent.", ex.getMessage());
  }

  @Test
  void testParentPath_One() {
    assertSame(JsonPath.SELF, JsonPath.of(".hello").parentPath());
  }

  @Test
  void testChain_Name() {
    assertEquals(JsonPath.of(".abc.def"), JsonPath.of(".abc").chain(Text.of("def")));
    assertEquals(JsonPath.of(".abc{.}"), JsonPath.of(".abc").chain(Text.of(".")));
    assertEquals(JsonPath.of(".abc.[42]"), JsonPath.of(".abc").chain(Text.of("[42]")));
  }

  @Test
  void testChain_Index() {
    assertEquals(JsonPath.of(".answer[42]"), JsonPath.of(".answer").chain(42));
  }

  @Test
  void testConcat_Path() {
    assertEquals(JsonPath.of(".answer[42]"), JsonPath.of(".answer").concat(JsonPath.of(42)));
  }

  @Test
  void testConcat_PathDeep() {
    assertEquals(JsonPath.of(".foo.bar.x.y"), JsonPath.of(".foo.bar").concat(JsonPath.of(".x.y")));
  }

  @Test
  void testConcat_StringPath() {
    assertEquals(JsonPath.of(".foo.bar"), JsonPath.of(".foo.bar").concat(""));
    assertEquals(JsonPath.of(".foo.bar.x"), JsonPath.of(".foo.bar").concat(".x"));
    assertEquals(JsonPath.of(".foo.bar.x.y"), JsonPath.of(".foo.bar").concat(".x.y"));
  }

  @Test
  void testChain_Index_Negative() {
    JsonPathException ex =
        assertThrowsExactly(JsonPathException.class, () -> JsonPath.of(".x").chain(-1));
    assertEquals("Path array index must be zero or positive but was: -1", ex.getMessage());
  }

  @Test
  void testCompareTo() {
    List<JsonPath> unsorted =
        List.of(
            JsonPath.of("foo", "bar", "baz"),
            JsonPath.of("fool", "bar", "baz"),
            JsonPath.of("a"),
            JsonPath.of("ear", "bar", "baz"),
            JsonPath.SELF,
            JsonPath.of("zoo", "keeeeeeeeeeeeperrrrrrrr"),
            JsonPath.of("form"),
            JsonPath.of("ear", "bar"));
    TreeSet<JsonPath> sorted = new TreeSet<>(unsorted);
    assertEquals(
        """
        []
        [a]
        [form]
        [ear, bar]
        [ear, bar, baz]
        [foo, bar, baz]
        [fool, bar, baz]
        [zoo, keeeeeeeeeeeeperrrrrrrr]""",
        sorted.stream().map(p -> p.segments().toString()).collect(joining("\n")));
  }

  @Test
  void testEquals() {
    assertEquals(JsonPath.SELF, JsonPath.SELF);
    assertEquals(JsonPath.of("foo"), JsonPath.of("foo"));
    assertEquals(JsonPath.of("foo", "bar"), JsonPath.of("foo", "bar"));
  }

  @Test
  void relativeTo_simplePrefix() {
    assertEquals(
        List.of("b", "c"), segments(JsonPath.of("a", "b", "c").relativeTo(JsonPath.of("a"))));
  }

  @Test
  void relativeTo_multilevelPrefix() {
    assertEquals(
        List.of("c"), segments(JsonPath.of("a", "b", "c").relativeTo(JsonPath.of("a", "b"))));
  }

  @Test
  void relativeTo_fullMatch_yieldsEmpty() {
    // Stripping the whole path gives an empty path (= SELF)
    assertTrue(JsonPath.of("a", "b").relativeTo(JsonPath.of("a", "b")).isEmpty());
  }

  @Test
  void relativeTo_emptyPrefix_isIdentity() {
    JsonPath path = JsonPath.of("a", "b");
    assertEquals(segments(path), segments(path.relativeTo(JsonPath.SELF)));
  }

  @Test
  void relativeTo_notAncestor_throws() {
    assertThrowsExactly(
        JsonPathException.class, () -> JsonPath.of("a", "b").relativeTo(JsonPath.of("x")));
  }

  @Test
  void relativeTo_partialSegmentMatch_throws() {
    // "ab" is not a prefix of "abc" — segments are whole, not string prefixes
    assertThrowsExactly(
        JsonPathException.class, () -> JsonPath.of("abc").relativeTo(JsonPath.of("ab")));
  }

  @Test
  void relativeTo_selfRelativeToSelf() {
    assertTrue(JsonPath.SELF.relativeTo(JsonPath.SELF).isEmpty());
  }

  @Test
  void relativeTo_deeperThanSelf_throws() {
    // Prefix is longer than this — impossible prefix
    assertThrowsExactly(
        JsonPathException.class, () -> JsonPath.of("a").relativeTo(JsonPath.of("a", "b")));
  }

  @Test
  void startsWith_selfIsPrefixOfEverything() {
    for (JsonPath p : List.of(JsonPath.SELF, JsonPath.of("a"), JsonPath.of("a", "b"))) {
      assertTrue(p.startsWith(JsonPath.SELF), "SELF must be prefix of: " + p);
    }
  }

  @Test
  void relativeTo_fullMatch_yieldsSelf() {
    // Stripping the whole path gives SELF
    assertTrue(JsonPath.of("a", "b").relativeTo(JsonPath.of("a", "b")).isEmpty());
  }

  @Test
  void relativeTo_selfRelativeToSelf_yieldsSelf() {
    assertTrue(JsonPath.SELF.relativeTo(JsonPath.SELF).isEmpty());
  }

  @Test
  void relativeTo_selfRelativeToSelf_preservesIdentity() {
    // SELF.relativeTo(SELF) should return the SELF singleton, not a copy
    assertSame(JsonPath.SELF, JsonPath.SELF.relativeTo(JsonPath.SELF));
  }

  @Test
  void relativeTo_selfPrefix_isStructuralIdentity() {
    JsonPath original = JsonPath.of("a", "b");
    JsonPath result = original.relativeTo(JsonPath.SELF);
    assertEquals(original, result); // record equality, structural
    // note: not assertSame — a fresh instance with equal content is fine
  }

  @Test
  void relativeTo_singleSegmentRelativeToSelf() {
    JsonPath path = JsonPath.of("a");
    assertEquals(segments(path), segments(path.relativeTo(JsonPath.SELF)));
  }

  @Test
  void relativeTo_singleSegmentRelativeToSelf_isEqual() {
    assertEquals(JsonPath.of("a"), JsonPath.of("a").relativeTo(JsonPath.SELF));
  }

  private void assertSegments(String path, List<String> expected) {
    assertEquals(expected.stream().map(Text::of).toList(), JsonPath.of(path).segments());
  }

  private void assertParent(String expected, String actual) {
    assertEquals(expected, JsonPath.of(actual).parentPath().toString());
  }

  private static List<String> segments(JsonPath path) {
    return path.segments().stream().map(Text::toString).toList();
  }
}
