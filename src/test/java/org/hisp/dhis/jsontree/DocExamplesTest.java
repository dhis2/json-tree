package org.hisp.dhis.jsontree;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compiles and runs every executable example shown in {@code README.md} and the
 * files under {@code docs/}. One test method per snippet.
 *
 * <p>If a snippet in the docs changes, the matching test must change too. If a
 * test fails to <em>compile</em>, it means the docs example is not valid Java
 * as written — that's the point of this suite.
 */
class DocExamplesTest {

    // =========================================================================
    // README.md  +  docs/getting-started.md
    // =========================================================================

    @Test
    void readme_quickStart() {
        JsonString  s = Json.of( "hello" );
        JsonInteger i = Json.of( 42 );

        JsonObject o = Json.object( obj ->
            obj.addNumber( "x", 1 ).addNumber( "y", 5 ) );

        assertEquals( "{\"x\":1,\"y\":5}", o.toJson() );
        assertNotNull( s );
        assertNotNull( i );
    }

    @Test
    void gettingStarted_firstExample() {
        JsonString  s = Json.of( "hello" );
        JsonInteger i = Json.of( 42 );
        assertNotNull( s );
        assertNotNull( i );

        JsonObject o = Json.object( obj ->
            obj.addNumber( "x", 1 ).addNumber( "y", 5 ) );
        assertEquals( "{\"x\":1,\"y\":5}", o.toJson() );
    }

    @Test
    void gettingStarted_jsonDocument() {
        JsonObject o = JsonMixed.of( """
            {
                "key": "points",
                "value": [1,2,3]
            }
            """ );

        assertEquals( "points", o.getString( "key" ).string() );
    }

    // =========================================================================
    // docs/java-api.md
    // =========================================================================

    @Test
    void javaApi_creationFromJava() {
        JsonString  s = Json.of( "hello" );
        JsonBoolean b = Json.of( true );
        JsonInteger i = Json.of( 42 );
        JsonNumber  d = Json.of( 42.01d );
        JsonArray   a = Json.array( Json::of, List.of( 1, 2, 3 ) );
        JsonObject  o = Json.object( Json::of, Map.of( "a", 4, "b", 2 ) );

        assertNotNull( s );
        assertNotNull( b );
        assertNotNull( i );
        assertNotNull( d );
        assertNotNull( a );
        assertNotNull( o );
    }

    @Test
    void javaApi_builder() {
        JsonArray a = Json.array( arr -> arr.addNumber( 1 ).addString( "yes" ) );
        assertEquals( "[1,\"yes\"]", a.toJson() );

        JsonObject o = Json.object( obj -> obj.addNumber( "x", 1 ).addNumber( "y", 5 ) );
        assertEquals( "{\"x\":1,\"y\":5}", o.toJson() );

        JsonObject o2 = Json.object( obj -> obj.addArray( "points",
                arr -> arr.addNumbers( IntStream.range( 1, 10 ) ) ) );
        assertEquals( "{\"points\":[1,2,3,4,5,6,7,8,9]}", o2.toJson() );
    }

    // =========================================================================
    // docs/json-api.md
    // =========================================================================

    @Test
    void jsonApi_stringQuoting() {
        JsonString s = JsonMixed.of( "\"null\"" );
        assertTrue( s.isString() );
        assertEquals( "null", s.string() );
        assertEquals( JsonNodeType.STRING, s.type() );
    }

    @Test
    void jsonApi_complexInput() {
        String json = """
            {
                "key": "points",
                "value": [1,2,3]
            }
            """;
        JsonObject o = JsonMixed.of( json );
        assertNotNull( o );
        assertEquals( "points", o.getString( "key" ).string() );
    }

    // =========================================================================
    // docs/null-and-undefined.md
    // =========================================================================

    @Test
    void nullAndUndefined_jsonNull() {
        JsonString s = JsonMixed.of( "null" );
        assertTrue( s.exists() );
        assertTrue( s.isUndefined() );
        assertTrue( s.isNull() );
        assertFalse( s.isString() );
        assertEquals( JsonNodeType.NULL, s.type() );
        assertNull( s.string() );
    }

    @Test
    void nullAndUndefined_undefined() {
        JsonString s = JsonMixed.of( "{}" ).getString( "x" );
        assertFalse( s.exists() );
        assertTrue( s.isUndefined() );
        assertNull( s.type() );
        assertNull( s.string() );
    }

    // =========================================================================
    // docs/user-types.md
    // =========================================================================

    interface JsonAddress extends JsonObject {
        default String street() {
            return getString( "street" ).string();
        }

        default Integer no() {
            return getNumber( "no" ).integer();
        }

        default JsonString city() {
            return getString( "city" );
        }
    }

    interface JsonUser extends JsonObject {
        default JsonAddress address() {
            return get( "address", JsonAddress.class );
        }

        default JsonList<JsonAddress> alternativeAddresses() {
            return getList( "alternativeAddresses", JsonAddress.class );
        }
    }

    @Test
    void userTypes_viewingAsDomainType() {
        JsonObject obj = JsonMixed.of( """
            {
                "street": "Elm",
                "no": 11,
                "city": "Oslo"
            }""" );

        JsonAddress a = obj.as( JsonAddress.class );
        assertEquals( "Elm", a.street() );
        assertEquals( Integer.valueOf( 11 ), a.no() );
        assertTrue( a.city().exists() );
        assertEquals( "Oslo", a.city().string() );
    }

    // =========================================================================
    // docs/validation.md
    // =========================================================================

    @Test
    void validation_typesMatch() {
        JsonObject obj = JsonMixed.of( """
            {
                "city": "Oslo",
                "no": 42
            }""" );
        // node types match — must not throw
        obj.validate( JsonAddress.class );
    }

    interface ValidatedAddress extends JsonObject {
        @Required
        @Validation( maxLength = 50 )
        default String street() {
            return getString( "street" ).string();
        }

        @Validation( minimum = 1 )
        default Integer no() {
            return getNumber( "no" ).integer();
        }

        @Validation( maxLength = 50 )
        default JsonString city() {
            return getString( "city" );
        }
    }

    @Test
    void validation_isA_asA() {
        JsonObject obj = JsonMixed.of( """
            {
                "city": "Oslo",
                "no": 0,
                "street": "Elm"
            }""" );

        assertFalse( obj.isA( ValidatedAddress.class ) );
        assertThrows( Exception.class, () -> obj.asA( ValidatedAddress.class ) );
    }

    // A user-authored meta-annotation, mirroring the docs snippet.
    @Target( ElementType.METHOD )
    @Retention( RetentionPolicy.RUNTIME )
    @Validation( minimum = 1 )
    @interface Positive {}

    // =========================================================================
    // docs/diff.md
    // =========================================================================

    @Test
    void diff_simple() {
        JsonValue expected = JsonValue.of( "[1,2,3]" );
        JsonValue actual   = JsonValue.of( "[1,3,2]" );

        JsonDiff diff1 = expected.diff( actual );
        JsonDiff diff2 = expected.diff( actual, JsonDiff.Mode.LENIENT );

        assertNotNull( diff1 );
        assertNotNull( diff2 );
    }

    interface TaggedObject extends JsonObject {
        @JsonDiff.AnyOrder
        default JsonArray tags() {
            return getArray( "tags" );
        }
    }

    @Test
    void diff_perProperty() {
        TaggedObject expected = JsonValue.of( """
            {"tags": ["hello", "intro"]}""" ).as( TaggedObject.class );
        TaggedObject actual = JsonValue.of( """
            {"tags": ["intro", "hello"]}""" ).as( TaggedObject.class );

        JsonDiff diff1 = expected.diff( actual );
        assertNotNull( diff1 );
    }

    // =========================================================================
    // docs/tree-navigation.md
    // =========================================================================

    @Test
    void treeNavigation_oneLevel() {
        JsonObject root = JsonMixed.of( """
            {
                "name": "John",
                "points": [10, 20, 30]
            }""" );

        JsonMixed name = root.get( "name" );
        JsonMixed p20  = root.getArray( "points" ).get( 1 );
        assertNotNull( name );
        assertNotNull( p20 );
    }

    @Test
    void treeNavigation_multiLevel() {
        JsonObject root = JsonMixed.of( """
            {
                "address": { "city": "Oslo" },
                "points": [10, 20, 30]
            }""" );

        String city  = root.get( "address.city" ).as( JsonString.class ).string();
        String city2 = root.get( "{address}{city}" ).as( JsonString.class ).string();
        int    p20   = root.get( "points[1]" ).as( JsonNumber.class ).intValue();

        assertEquals( "Oslo", city );
        assertEquals( "Oslo", city2 );
        assertEquals( 20, p20 );
    }

    @Test
    void treeNavigation_jsonPath() {
        JsonObject root = JsonMixed.of( """
            {
                "address": { "city": "Oslo" },
                "points": [10, 20, 30]
            }""" );

        JsonPath path = JsonPath.of( "address", "city" );
        JsonMixed city = root.get( path, JsonMixed.class );
        assertEquals( "Oslo", city.as( JsonString.class ).string() );

        JsonPath firstPoint = JsonPath.of( "points" ).chain( 1 );
        JsonMixed p20 = root.get( firstPoint, JsonMixed.class );
        assertEquals( 20, p20.as( JsonNumber.class ).integer() );
    }

    // =========================================================================
    // docs/query.md
    // =========================================================================

    private static JsonMixed queryRoot() {
        return JsonMixed.of( """
            {
                "team": "compilers",
                "people": [
                    { "name": "Ada",   "age": 36 },
                    { "name": "Grace", "age": 85 },
                    { "name": "Alan",  "age": 41 }
                ]
            }""" );
    }

    @Test
    void query_countAndExists() {
        JsonMixed root = queryRoot();

        int  n      = root.queryCount( JsonSelector.of( "$.people[*]" ) );
        boolean any = root.queryExists( JsonSelector.of( "$..['name']" ) );

        assertEquals( 3, n );
        assertTrue( any );
    }

    @Test
    void query_first() {
        JsonMixed root = queryRoot();

        String first = root.queryFirst( JsonSelector.of( "$.people[0].name" ) )
            .map( node -> node.as( JsonString.class ).string() )
            .orElse( null );

        assertEquals( "Ada", first );
    }

    @Test
    void query_reduce() {
        JsonMixed root = queryRoot();

        int totalAge = root.queryReduce(
            JsonSelector.of( "$.people[*].age" ),
            node -> node.as( JsonNumber.class ).integer(),
            0,
            Integer::sum );

        assertEquals( 36 + 85 + 41, totalAge );
    }

    @Test
    void query_top() {
        JsonMixed root = queryRoot();

        List<String> top2 = root.queryTop( 2,
            JsonSelector.of( "$.people[*]" ),
            node -> node.getNumber( "age" ).integer() )
            .map( node -> node.getString( "name" ).string() )
            .toList();

        assertEquals( List.of( "Grace", "Alan" ), top2 );
    }
}