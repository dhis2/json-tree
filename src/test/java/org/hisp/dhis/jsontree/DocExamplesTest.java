package org.hisp.dhis.jsontree;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.YesNo;
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

    // =========================================================================
    // docs/validation-reference.md
    // =========================================================================

    interface RefUser extends JsonObject {
        @Validation( pattern = "+[l]", minLength = 8, maxLength = 20 )
        default String username() { return getString( "username" ).string(); }
    }

    @Test
    void refValidation_basicAttributes() {
        JsonObject ok = JsonMixed.of( "{\"username\": \"alicewond\"}" );
        assertTrue( ok.isA( RefUser.class ) );

        JsonObject tooShort = JsonMixed.of( "{\"username\": \"alice\"}" );
        assertFalse( tooShort.isA( RefUser.class ) );

        JsonObject badPattern = JsonMixed.of( "{\"username\": \"AliceWond\"}" );
        assertFalse( badPattern.isA( RefUser.class ) );
    }

    @Target( ElementType.METHOD )
    @Retention( RetentionPolicy.RUNTIME )
    @Validation( required = YesNo.NO )
    @interface Optional {}

    interface RefUserOptional extends JsonObject {
        @Optional
        @Validation( minLength = 8 )
        default String username() { return getString( "username" ).string(); }
    }

    @Test
    void refValidation_metaAnnotation_optional() {
        // minLength would implicitly require the property; @Optional undoes that
        assertTrue( JsonMixed.of( "{}" ).isA( RefUserOptional.class ) );
        // but if present it must still satisfy minLength
        assertFalse( JsonMixed.of( "{\"username\": \"short\"}" ).isA( RefUserOptional.class ) );
    }

    interface RefUserRequiredList extends JsonObject {
        @Required
        default List<@Validation( maxLength = 20 ) String> names() {
            return getArray( "names" ).stringValues();
        }
    }

    @Test
    void refValidation_requiredList() {
        assertTrue( JsonMixed.of( "{\"names\": [\"a\", \"b\"]}" ).isA( RefUserRequiredList.class ) );
        // required
        assertFalse( JsonMixed.of( "{}" ).isA( RefUserRequiredList.class ) );
        // element maxLength
        assertFalse(
            JsonMixed.of( "{\"names\": [\"thisstringislongerthan20\"]}" )
                .isA( RefUserRequiredList.class ) );
    }

    @Validation( type = NodeType.STRING, pattern = "9[a]" )
    record RefId( String id ) {}

    interface RefUserWithId extends JsonObject {
        default RefId id() { return getString( "id" ).parsed( RefId::new ); }
    }

    @Test
    void refValidation_recordType() {
        assertTrue( JsonMixed.of( "{\"id\": \"abcdef123\"}" ).isA( RefUserWithId.class ) );
        assertFalse( JsonMixed.of( "{\"id\": \"tooshort\"}" ).isA( RefUserWithId.class ) );
    }

    interface RefNamesWithItems extends JsonObject {
        @Validation.Items( @Validation( maxLength = 20 ) )
        default List<String> names() { return getArray( "names" ).stringValues(); }
    }

    interface RefNamesWithReturnType extends JsonObject {
        default List<@Validation( maxLength = 20 ) String> names() {
            return getArray( "names" ).stringValues();
        }
    }

    @Test
    void refValidation_itemsEquivalence() {
        String good = "{\"names\": [\"a\", \"b\"]}";
        String bad  = "{\"names\": [\"thisstringislongerthan20\"]}";
        assertTrue( JsonMixed.of( good ).isA( RefNamesWithItems.class ) );
        assertTrue( JsonMixed.of( good ).isA( RefNamesWithReturnType.class ) );
        assertFalse( JsonMixed.of( bad ).isA( RefNamesWithItems.class ) );
        assertFalse( JsonMixed.of( bad ).isA( RefNamesWithReturnType.class ) );
    }

    @Validation( type = NodeType.ARRAY )
    @Validation.Items( @Validation( type = NodeType.INTEGER ) )
    interface RefIntList extends JsonList<JsonInteger> {}

    interface RefUserWithIntList extends JsonObject {
        default RefIntList values() { return get( "values", RefIntList.class ); }
    }

    @Test
    void refValidation_customArrayType() {
        assertTrue( JsonMixed.of( "{\"values\": [1, 2, 3]}" ).isA( RefUserWithIntList.class ) );
        assertFalse( JsonMixed.of( "{\"values\": [1, \"two\"]}" ).isA( RefUserWithIntList.class ) );
    }

    interface DocMembership extends JsonObject {
        String getGroup();
        LocalDate getJoinDate();
    }

    interface DocAccount extends JsonObject {
        boolean isEnabled();
        String getUsername();
        List<DocMembership> memberships();
    }

    @Test
    void userTypes_abstractProperties() {
        JsonObject obj = JsonMixed.of( """
            {
                "enabled": true,
                "username": "TomTraubert",
                "memberships": [
                    { "group": "BigTimers", "joinDate": "1973-02-05" }
                ]
            }""" );

        DocAccount account = obj.as( DocAccount.class );
        assertTrue( account.isEnabled() );
        assertEquals( "TomTraubert", account.getUsername() );
        assertEquals( 1, account.memberships().size() );
        assertEquals( "BigTimers", account.memberships().get( 0 ).getGroup() );
        assertEquals( LocalDate.of( 1973, 2, 5 ),
            account.memberships().get( 0 ).getJoinDate() );
    }

    @Test
    void userTypes_javaTimeAccessors() {
        interface WithDates extends JsonObject {
            LocalDate date();
            LocalDateTime dateTime();
            LocalTime time();
        }

        JsonObject obj = JsonMixed.of( """
            {
                "date":     "1973-02-05",
                "dateTime": "1973-02-05T13:45:00",
                "time":     "13:45:00"
            }""" );

        WithDates d = obj.as( WithDates.class );
        assertEquals( LocalDate.of( 1973, 2, 5 ), d.date() );
        assertEquals( LocalDateTime.of( 1973, 2, 5, 13, 45 ), d.dateTime() );
        assertEquals( LocalTime.of( 13, 45 ), d.time() );
    }

    // =========================================================================
    // docs/json-diff.md
    // =========================================================================

    @Test
    void jsonDiff_quickExample() {
        JsonValue expected = JsonValue.of( "[1,2,3]" );
        JsonValue actual   = JsonValue.of( "[1,5,3]" );

        JsonDiff diff = expected.diff( actual );
        assertEquals(
            List.of( "!= $.1: 2 <> 5" ),
            diff.differences().stream()
                .map( JsonDiff.Difference::toString )
                .toList() );
    }

    @Test
    void jsonDiff_modes_reorderedArray() {
        // DEFAULT: arrays are strict-order → one NEQ per mismatched index
        JsonDiff d1 = JsonValue.of( "[1,2,3]" ).diff( JsonValue.of( "[1,3,2]" ) );
        assertEquals(
            List.of( "!= $.1: 2 <> 3", "!= $.2: 3 <> 2" ),
            d1.differences().stream().map( JsonDiff.Difference::toString ).toList() );

        // LENIENT: arrays are any-order → no diff
        JsonDiff d2 = JsonValue.of( "[1,2,3]" )
            .diff( JsonValue.of( "[1,3,2]" ), JsonDiff.Mode.LENIENT );
        assertTrue( d2.differences().isEmpty() );
    }

    @Test
    void jsonDiff_modes_numberEquality() {
        // DEFAULT uses numeric equivalence: 4 == 4.0
        assertTrue( JsonValue.of( "4" ).diff( JsonValue.of( "4.0" ) ).differences().isEmpty() );

        // STRICT uses textual equivalence
        assertEquals(
            List.of( "!= $: 4 <> 4.0" ),
            JsonValue.of( "4" )
                .diff( JsonValue.of( "4.0" ), JsonDiff.Mode.STRICT )
                .differences().stream().map( JsonDiff.Difference::toString ).toList() );
    }

    @Test
    void jsonDiff_modeBuilder() {
        JsonDiff.Mode m = JsonDiff.Mode.STRICT
            .anyOrder()
            .numbers( JsonDiff.Equivalence.NUMERIC );
        assertTrue(
            JsonValue.of( "[1,2,3]" )
                .diff( JsonValue.of( "[3,1,2]" ), m )
                .differences().isEmpty() );
    }

    interface DocStrictTags extends JsonObject {
        default JsonArray tags() {
            return getArray( "tags" );
        }
    }

    interface DocAnyOrderTags extends JsonObject {
        @JsonDiff.AnyOrder
        default JsonArray tags() {
            return getArray( "tags" );
        }
    }

    @Test
    void jsonDiff_perProperty_strict() {
        DocStrictTags expected = JsonValue.of( """
            {"tags": ["hello", "intro"]}""" ).as( DocStrictTags.class );
        DocStrictTags actual = JsonValue.of( """
            {"tags": ["intro", "hello"]}""" ).as( DocStrictTags.class );

        List<String> diffs = expected.diff( actual ).differences().stream()
            .map( JsonDiff.Difference::toString ).toList();
        assertEquals( 2, diffs.size() );
        assertTrue( diffs.contains( "!= $.tags.0: \"hello\" <> \"intro\"" ) );
        assertTrue( diffs.contains( "!= $.tags.1: \"intro\" <> \"hello\"" ) );
    }

    @Test
    void jsonDiff_perProperty_anyOrder() {
        DocAnyOrderTags expected = JsonValue.of( """
            {"tags": ["hello", "intro"]}""" ).as( DocAnyOrderTags.class );
        DocAnyOrderTags actual = JsonValue.of( """
            {"tags": ["intro", "hello"]}""" ).as( DocAnyOrderTags.class );

        assertTrue( expected.diff( actual ).differences().isEmpty() );
    }
}