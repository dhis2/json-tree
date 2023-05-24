package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the {@link org.hisp.dhis.jsontree.JsonBuilder.PrettyPrint} aspect of {@link JsonBuilder}.
 *
 * @author Jan Bernitt
 */
class JsonPrettyPrintTest {

    private static final JsonBuilder.PrettyPrint PRETTY = new JsonBuilder.PrettyPrint( 2, 0, true, false );

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
            .addBoolean( "c", null ) ).getDeclaration() );
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
            .addObject( "b", sub -> sub.addNumber( "x", 42 ) ) ).getDeclaration() );
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
            .addElement( JsonNode.of( "[\"a\",[\"b\"]]" ) ) ).getDeclaration() );
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
            .addMember( "test", JsonNode.of( "{\"a\":{\"b\":1}}" ) ) ).getDeclaration() );
    }
}
