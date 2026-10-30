package org.hisp.dhis.jsontree;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
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

    // =========================================================================
    // docs/json-search.md — Transforming matches
    // =========================================================================

    private static JsonMixed searchPeople() {
        return JsonMixed.of( """
            {
                "people": [
                    { "first": "James", "age": 30 },
                    { "first": "Jacob", "age": 25 },
                    { "first": "Jayden", "age": 41 }
                ]
            }""" );
    }

    private static JsonMixed searchFleet() {
        return JsonMixed.of( """
            {
                "reservations": [
                    { "instances": [ { "state": "running" }, { "state": "stopped" } ] },
                    { "instances": [ { "state": "terminated" }, { "state": "running" } ] }
                ]
            }""" );
    }

    private static JsonMixed searchMixedPeople() {
        return JsonMixed.of( """
            {"people": [
                {"name": "James"},
                {"nickname": "JJ"},
                {"name": "Jacob"}
            ]}""" );
    }

    @Test
    void jsonSearch_transformingMatches_basic() {
        JsonMixed root = searchPeople();

        List<String> firsts = root.queryMap(
            JsonSelector.of( "$.people[*]" ),
            p -> p.getString( "first" ).string() );

        assertEquals( List.of( "James", "Jacob", "Jayden" ), firsts );
    }

    @Test
    void jsonSearch_transformingMatches_constructedObject() {
        JsonMixed root = searchPeople();

        List<JsonObject> slimmed = root.queryMap(
            JsonSelector.of( "$.people[*]" ),
            p -> Json.object( o -> o
                .addString( "name", p.getString( "first" ).string() )
                .addNumber( "age",  p.getNumber( "age" ).integer() ) ) );

        assertEquals(
            List.of(
                "{\"name\":\"James\",\"age\":30}",
                "{\"name\":\"Jacob\",\"age\":25}",
                "{\"name\":\"Jayden\",\"age\":41}" ),
            slimmed.stream().map( JsonObject::toJson ).toList() );
    }

    @Test
    void jsonSearch_transformingMatches_constructedArray() {
        JsonMixed root = searchPeople();

        JsonArray materialised = Json.array( arr ->
            root.queryMap( JsonSelector.of( "$.people[*]" ),
                    p -> p.getString( "first" ).string() )
                .forEach( arr::addString ) );

        assertEquals( "[\"James\",\"Jacob\",\"Jayden\"]", materialised.toJson() );
    }

    @Test
    void jsonSearch_transformingMatches_nested() {
        JsonMixed fleet = searchFleet();

        List<List<String>> nested = fleet.queryMap(
            JsonSelector.of( "$.reservations[*]" ),
            res -> res.queryMap(
                JsonSelector.of( "@.instances[*]" ),
                inst -> inst.getString( "state" ).string() ) );

        assertEquals(
            List.of(
                List.of( "running", "stopped" ),
                List.of( "terminated", "running" ) ),
            nested );
    }

    @Test
    void jsonSearch_transformingMatches_flat() {
        JsonMixed fleet = searchFleet();

        List<String> flat = fleet.queryMap(
            JsonSelector.of( "$.reservations[*].instances[*]" ),
            inst -> inst.getString( "state" ).string() );

        assertEquals(
            List.of( "running", "stopped", "terminated", "running" ),
            flat );
    }

    @Test
    void jsonSearch_transformingMatches_flatten() {
        JsonMixed grid = JsonMixed.of( "{\"matrix\": [[1,2],[3,4]]}" );

        List<Integer> numbers = grid.queryMap(
            JsonSelector.of( "$.matrix" ).flatten(),
            n -> n.as( JsonNumber.class ).integer() );

        assertEquals( List.of( 1, 2, 3, 4 ), numbers );
    }

    @Test
    void jsonSearch_transformingMatches_nullSkipped() {
        JsonMixed mixed = searchMixedPeople();

        List<String> names = mixed.queryMap(
            JsonSelector.of( "$.people[*]" ),
            p -> p.getString( "name" ).isUndefined()
                ? null
                : p.getString( "name" ).string() );

        assertEquals( List.of( "James", "Jacob" ), names );
    }

    @Test
    void jsonSearch_transformingMatches_nullPreservedViaStream() {
        JsonMixed mixed = searchMixedPeople();

        List<String> withNulls = mixed
            .query( JsonSelector.of( "$.people[*]" ) )
            .map( p -> p.getString( "name" ).isUndefined()
                ? null
                : p.getString( "name" ).string() )
            .collect( java.util.stream.Collectors.toList() );

        assertEquals( java.util.Arrays.asList( "James", null, "Jacob" ), withNulls );
    }

    // =========================================================================
    // docs/json-equivalence.md
    // =========================================================================

    @Test
    void jsonEquivalence_equivalent_ignoresMemberOrder() {
        JsonMixed a = JsonMixed.of( "{\"a\": 1, \"b\": 2}" );
        JsonMixed b = JsonMixed.of( "{\"b\": 2, \"a\": 1}" );
        assertTrue( a.equivalentTo( b ) );
        assertFalse( a.identicalTo( b ) );
    }

    @Test
    void jsonEquivalence_equivalent_ignoresNumberForm() {
        JsonMixed c = JsonMixed.of( "1" );
        JsonMixed d = JsonMixed.of( "1.0" );
        assertTrue( c.equivalentTo( d ) );
        assertFalse( c.identicalTo( d ) );
    }

    @Test
    void jsonEquivalence_arrays_arePositional() {
        JsonMixed e = JsonMixed.of( "[1, 2]" );
        JsonMixed f = JsonMixed.of( "[2, 1]" );
        assertFalse( e.equivalentTo( f ) );
        assertFalse( e.identicalTo( f ) );
    }

    @Test
    void jsonEquivalence_whitespace_isIgnored() {
        JsonMixed a = JsonMixed.of( "{\"a\": 1, \"b\": 2}" );
        JsonMixed b = JsonMixed.of( "{ \"a\": 1, \"b\": 2 }" );
        assertTrue( a.equivalentTo( b ) );
        assertTrue( a.identicalTo( b ) );
    }

    @Test
    void jsonEquivalence_null_and_undefined_differ() {
        JsonMixed withNull = JsonMixed.of( "{\"x\": null}" ).get( "x" );
        JsonMixed undefined = JsonMixed.of( "{}" ).get( "x" );
        assertTrue( withNull.isNull() );
        assertTrue( undefined.isUndefined() );
        assertFalse( withNull.equivalentTo( undefined ) );
        assertFalse( withNull.identicalTo( undefined ) );
    }

    @Test
    void jsonEquivalence_undefined_matchesUndefined() {
        JsonMixed a = JsonMixed.of( "{}" ).get( "x" );
        JsonMixed b = JsonMixed.of( "{}" ).get( "x" );
        assertTrue( a.equivalentTo( b ) );
        assertTrue( a.identicalTo( b ) );
    }

    @Test
    void jsonEquivalence_identical_implies_equivalent() {
        JsonMixed a = JsonMixed.of( "{\"a\": 1, \"b\": [1, 2, 3]}" );
        JsonMixed b = JsonMixed.of( "{\"a\": 1, \"b\": [1, 2, 3]}" );
        assertTrue( a.identicalTo( b ) );
        assertTrue( a.equivalentTo( b ) );
    }

    @Test
    void jsonEquivalence_numberPrecision_distinctLargeIntegers() {
        // 2^53 + 1 and 2^53 round to the same double, but they are distinct
        // integer values — equivalentTo must not conflate them
        JsonMixed a = JsonMixed.of( "9007199254740993" );
        JsonMixed b = JsonMixed.of( "9007199254740992" );
        assertFalse( a.equivalentTo( b ) );
        assertFalse( a.identicalTo( b ) );
    }

    @Test
    void jsonEquivalence_numberPrecision_sameLargeInteger() {
        // the same integer in different textual forms
        JsonMixed a = JsonMixed.of( "9007199254740993" );
        JsonMixed b = JsonMixed.of( "9007199254740993.00" );
        assertTrue( a.equivalentTo( b ) );
        assertFalse( a.identicalTo( b ) );
    }

    @Test
    void jsonEquivalence_numberPrecision_negativeZero() {
        // -0 and 0 are the same integer value
        JsonMixed a = JsonMixed.of( "0" );
        JsonMixed b = JsonMixed.of( "-0" );
        assertTrue( a.equivalentTo( b ) );
        assertFalse( a.identicalTo( b ) );
    }

    // =========================================================================
    // docs/json-mask-overlay.md — Masking
    // =========================================================================

    private static JsonMixed maskOverlayUser() {
        return JsonMixed.of( """
            {
              "id": 42,
              "name": "Alice",
              "password": "secret",
              "contact": {
                "email": "alice@example.com",
                "phone": "+49-30-1234"
              }
            }""" );
    }

    @Test
    void masking_basic() {
        JsonMixed user = maskOverlayUser();

        JsonMixed safe = user.mask(
            JsonPath.of( "password" ),
            JsonPath.of( "contact", "email" ) );

        assertTrue( safe.equivalentTo( JsonMixed.of( """
            {"id": 42, "name": "Alice", "contact": { "phone": "+49-30-1234" }}""" ) ) );
    }

    @Test
    void masking_fromSubtree() {
        JsonMixed contact = maskOverlayUser().get( "contact" );

        JsonMixed masked = contact.mask( JsonPath.of( "phone" ) );

        assertTrue( masked.equivalentTo( JsonMixed.of( """
            {"email": "alice@example.com"}""" ) ) );
    }

    @Test
    void masking_bySelector() {
        JsonMixed masked = maskOverlayUser().mask( JsonSelector.of( "@.contact.*" ) );

        assertFalse( masked.exists( JsonPath.of( "contact", "email" ) ) );
        assertFalse( masked.exists( JsonPath.of( "contact", "phone" ) ) );
        assertTrue( masked.exists( JsonPath.of( "contact" ) ) );
    }

    @Test
    void masking_overlappingPaths_minimized() {
        JsonMixed user = maskOverlayUser();

        JsonMixed masked = user.mask(
            JsonPath.of( "contact" ),
            JsonPath.of( "contact", "email" ),
            JsonPath.of( "contact" ) );

        JsonMixed single = maskOverlayUser().mask( JsonPath.of( "contact" ) );

        assertFalse( masked.exists( JsonPath.of( "contact" ) ) );
        assertTrue( masked.equivalentTo( single ) );
    }

    @Test
    void masking_primitives() {
        JsonMixed result = JsonMixed.of( "5" ).mask( JsonPath.of( "a" ) );
        assertTrue( JsonMixed.of( "5" ).equivalentTo( result ) );

        JsonMixed nulled = JsonMixed.of( "5" ).mask( JsonPath.SELF );
        assertTrue( nulled.isNull() );
    }

    // =========================================================================
    // docs/json-mask-overlay.md — Overlaying
    // =========================================================================

    private static JsonMixed overlayCurrent() {
        return JsonMixed.of( """
            {
              "name": "Alice",
              "age": 30,
              "contact": {
                "email": "alice@example.com",
                "phone": "+49-30-1234"
              }
            }""" );
    }

    @Test
    void overlay_basic() {
        JsonMixed current = overlayCurrent();

        JsonMixed merged = current.overlay( JsonMixed.of( """
            {
              "age": 31,
              "contact": { "phone": "+49-30-5678" },
              "city": "Berlin"
            }""" ) );

        assertTrue( merged.equivalentTo( JsonMixed.of( """
            {
              "name": "Alice",
              "age": 31,
              "contact": { "email": "alice@example.com", "phone": "+49-30-5678" },
              "city": "Berlin"
            }""" ) ) );
    }

    @Test
    void overlay_nullRemoves() {
        JsonMixed merged = overlayCurrent().overlay( JsonMixed.of( "{\"age\": null}" ) );

        assertFalse( merged.exists( JsonPath.of( "age" ) ) );
        assertTrue( merged.exists( JsonPath.of( "name" ) ) );
    }

    @Test
    void overlay_nullReplaceMode() {
        JsonMixed merged = overlayCurrent().overlay(
            JsonMixed.of( "{\"age\": null}" ),
            JsonValue.OverlayNull.REPLACE );

        assertTrue( merged.exists( JsonPath.of( "age" ) ) );
        assertTrue( merged.get( JsonPath.of( "age" ) ).isNull() );
    }

    @Test
    void overlay_topLevelNonObject() {
        JsonMixed current = overlayCurrent();

        assertTrue( JsonMixed.of( "5" ).equivalentTo( current.overlay( JsonMixed.of( "5" ) ) ) );
        assertTrue( JsonMixed.of( "[1,2,3]" ).equivalentTo(
            current.overlay( JsonMixed.of( "[1,2,3]" ) ) ) );
        assertTrue( current.overlay( JsonMixed.of( "null" ) ).isNull() );
    }

    @Test
    void overlay_arraysAtomic() {
        JsonMixed result = JsonMixed.of( "{\"tags\": [1, 2, 3]}" )
            .overlay( JsonMixed.of( "{\"tags\": [9]}" ) );

        assertTrue( result.equivalentTo( JsonMixed.of( "{\"tags\": [9]}" ) ) );
    }

    @Test
    void overlay_emptyObject_noop() {
        JsonMixed current = overlayCurrent();
        JsonMixed merged = current.overlay( JsonMixed.of( "{\"contact\": {}}" ) );

        assertTrue( current.equivalentTo( merged ) );
    }

    @Test
    void overlay_undefinedSource_noop() {
        JsonMixed current = overlayCurrent();
        JsonMixed undefined = current.get( JsonPath.of( "missing" ) );

        assertTrue( current.equivalentTo( current.overlay( undefined ) ) );
    }

    @Test
    void overlay_maskComposition() {
        JsonMixed current = JsonMixed.of( """
            {"name": "Alice", "password": "x", "internal": "y"}""" );

        JsonMixed desired = current
            .mask( JsonPath.of( "password" ), JsonPath.of( "internal" ) )
            .overlay( JsonMixed.of( """
                { "name": "Bob", "status": "active" }""" ) );

        assertTrue( desired.equivalentTo( JsonMixed.of( """
            {"name": "Bob", "status": "active"}""" ) ) );
    }

    @Test
    void overlay_maskConsistency_nullMemberEqualsMask() {
        JsonMixed current = overlayCurrent();
        JsonMixed byOverlay = current.overlay( JsonMixed.of( "{\"age\": null}" ) );
        JsonMixed byMask    = current.mask( JsonPath.of( "age" ) );

        assertTrue( byOverlay.equivalentTo( byMask ) );
    }
}