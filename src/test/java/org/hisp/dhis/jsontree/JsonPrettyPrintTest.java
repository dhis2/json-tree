package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests the {@link org.hisp.dhis.jsontree.JsonBuilder.PrettyPrint} aspect of {@link JsonBuilder}.
 *
 * @author Jan Bernitt
 */
class JsonPrettyPrintTest {

    private static final JsonBuilder.PrettyPrint PRETTY = new JsonBuilder.PrettyPrint( 2, 0, true, false, false );
    private static final JsonBuilder.PrettyPrint PRETTY_NO_NULL = new JsonBuilder.PrettyPrint( 2, 0, true, false,
        true );

    @Test
    void testObject_BooleanPretty() {
        //language=JSON
        String expected = """
            {
              "a": true,
              "b": false,
              "c": null
            }""";
        assertEquals( expected, JsonBuilder.createObject( PRETTY, obj -> obj
            .addBoolean( "a", true )
            .addBoolean( "b", false )
            .addBoolean( "c", null ) ).getDeclaration().toString() );
    }

    @Test
    void testObject_ObjectPretty() {
        //language=JSON
        String expected = """
            {
              "a": {},
              "c": [],
              "b": {
                "x": 42
              }
            }""";
        assertEquals( expected, JsonBuilder.createObject( PRETTY, obj -> obj
            .addObject( "a", sub -> {} )
            .addArray( "c", arr -> {} )
            .addObject( "b", sub -> sub.addNumber( "x", 42 ) ) ).getDeclaration().toString() );
    }

    @Test
    void testArray_JsonNodePretty() {
        //language=JSON
        String expected = """
            [
              [
                "a",
                [
                  "b"
                ]
              ]
            ]""";
        assertEquals( expected, JsonBuilder.createArray( PRETTY, arr -> arr
            .addElement( JsonNode.of( "[\"a\",[\"b\"]]" ) ) ).getDeclaration().toString() );
    }

    @Test
    void testObject_JsonNodePretty() {
        //language=JSON
        String expected = """
            {
              "test": {
                "a": {
                  "b": 1
                }
              }
            }""";
        assertEquals( expected, JsonBuilder.createObject( PRETTY, obj -> obj
            .addMember( "test", JsonNode.of( "{\"a\":{\"b\":1}}" ) ) ).getDeclaration().toString() );
    }

    @Test
    void testObject_JsonNodePrettyNull() {
        assertEquals( "{}", JsonBuilder.createObject( PRETTY_NO_NULL, obj -> obj
            .addMember( "test", JsonNode.NULL ) ).getDeclaration().toString() );
    }

    @Test
    void testObject_Null() {
        assertEquals( "{}", JsonBuilder.createObject( PRETTY_NO_NULL, obj -> obj
            .addNumber( "num", null )
            .addString( "str", null )
            .addBoolean( "boo", null ) ).getDeclaration().toString() );
    }
}
