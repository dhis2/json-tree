package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the splitting of {@link JsonPath} into segments.
 *
 * @author Jan Bernitt
 * @since 1.1
 */
class JsonPathTest {

    @Test
    void testEscape() {
        assertEquals( "{/api/openapi/{path}/openapi.html}", JsonPath.escape( Text.of("/api/openapi/{path}/openapi.html" ) ));
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
        //but... (first char after . is never a segment start!)
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
        assertSegments(".aaa{.suffix}", List.of("aaa",".suffix"));
        assertSegments(".aaa{hello.world}", List.of("aaa","hello.world"));
        assertSegments(".aaa{prefix.}", List.of("aaa","prefix."));
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
    void testSegments_DotCurly_Trivial() {
        assertSegments(".xxx{1}", List.of("xxx", "1"));
        assertSegments(".xxx{1}{2}", List.of("xxx", "1", "2"));
        assertSegments(".xxx{1}.y{2}", List.of("xxx", "1", "y", "2"));
    }

    @Test
    void testParent_Root() {
        JsonPathException ex = assertThrowsExactly( JsonPathException.class, JsonPath.ROOT::parentPath );
        assertEquals( "Root/self path does not have a parent.", ex.getMessage() );
    }

    @Test
    void testParent_Dot_Uniform() {
        assertParent( "", ".x" );
        assertParent( ".x", ".x.y" );
        assertParent( ".x.yy", ".x.yy.zzz" );
    }

    @Test
    void testParent_Curly_Uniform() {
        assertParent( "", ".{x}" );
        assertParent( ".x", "{x}{y}" );
        assertParent( ".x.yy", "{x}{yy}{zzz}" );
    }

    @Test
    void testParent_Square_Uniform() {
        assertParent( "", "[1]" );
        assertParent( ".1", "[1][22]" );
        assertParent( ".1.22", "[1][22][333]" );
    }

    @Test
    void testParent_Mixed() {
        assertParent( ".1.xv", "[1].xv{.}" );
        assertParent( ".1.xv", "[1]{xv}.{h}" );
    }

    @Test
    void testEmpty() {
        assertTrue( JsonPath.of( "" ).isEmpty() );
        assertTrue( JsonPath.ROOT.isEmpty() );
        assertFalse( JsonPath.of( ".x" ).isEmpty() );
        assertFalse( JsonPath.of( "[0]" ).isEmpty() );
        assertFalse( JsonPath.of( "{x}").isEmpty() );
    }

    @Test
    void testSize() {
        assertEquals( 0, JsonPath.ROOT.size() );
        assertEquals( 0, JsonPath.of( "" ).size() );
        assertEquals( 1, JsonPath.of( ".yeah" ).size() );
        assertEquals( 1, JsonPath.of( "[1234]" ).size() );
        assertEquals( 1, JsonPath.of( "{dotty.}" ).size() );
        assertEquals( 2, JsonPath.of( ".yeah.yeah" ).size() );
        assertEquals( 2, JsonPath.of( ".links[1234]" ).size() );
        assertEquals( 2, JsonPath.of( "{dotty.}.dot" ).size() );
        assertEquals( 3, JsonPath.of( ".yeah.yeah.yeahs" ).size() );
    }

    @Test
    void testParentPath() {
        assertEquals( JsonPath.of( ".one" ), JsonPath.of( ".one.two" ).parentPath() );
        assertEquals( JsonPath.of( ".yeah.yeah" ), JsonPath.of( ".yeah.yeah.yeahs" ).parentPath() );
    }

    @Test
    void testParentPath_Empty() {
        JsonPathException ex = assertThrowsExactly( JsonPathException.class,
            JsonPath.ROOT::parentPath );
        assertEquals( "Root/self path does not have a parent.", ex.getMessage() );
    }

    @Test
    void testParentPath_One() {
        assertSame( JsonPath.ROOT, JsonPath.of( ".hello" ).parentPath() );
    }

    @Test
    void testExtendedWith_Name() {
        assertEquals( JsonPath.of(".abc.def"), JsonPath.of( ".abc" ).extendedWith(Text.of("def") ) );
        assertEquals( JsonPath.of(".abc{.}"), JsonPath.of( ".abc" ).extendedWith( Text.of(".") ) );
        assertEquals( JsonPath.of(".abc.[42]"), JsonPath.of( ".abc" ).extendedWith( Text.of("[42]") ) );
    }

    @Test
    void testExtendedWith_Index() {
        assertEquals( JsonPath.of(".answer[42]"), JsonPath.of( ".answer" ).extendedWith( 42 ) );
    }

    @Test
    void testExtendedWith_Path() {
        assertEquals( JsonPath.of( ".answer[42]" ), JsonPath.of( ".answer" ).extendedWith( JsonPath.of( 42 ) ) );
    }

    @Test
    void testExtendedWith_Index_Negative() {
        JsonPathException ex = assertThrowsExactly( JsonPathException.class,
            () -> JsonPath.of( ".x" ).extendedWith( -1 ) );
        assertEquals( "Path array index must be zero or positive but was: -1", ex.getMessage() );
    }

    private void assertSegments( String path, List<String> expected ) {
        assertEquals( expected.stream().map( Text::of ).toList(), JsonPath.of( path ).segments());
    }

    private void assertParent(String expected, String actual) {
        assertEquals( expected, JsonPath.of( actual ).parentPath().toString() );
    }
}
