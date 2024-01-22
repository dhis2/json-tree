package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonList;
import org.hisp.dhis.jsontree.JsonMap;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.JsonString;
import org.hisp.dhis.jsontree.Required;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.hisp.dhis.jsontree.Validation.YesNo.YES;
import static org.hisp.dhis.jsontree.validation.Assertions.assertValidationError;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test the validation with an example that has multiple levels.
 *
 * @author Jan Bernitt
 */
class JsonValidationMiscDeepGraphTest {

    public interface JsonPage extends JsonObject {

        @Required
        default JsonPager pager() {
            return get( "pager", JsonPager.class );
        }

        @Required
        default JsonList<JsonEntry> getEntries() {
            return getList( "entries", JsonEntry.class );
        }
    }

    public interface JsonPager extends JsonObject {

        @Validation( minimum = 1 )
        default int size() {
            return getNumber( "size" ).intValue();
        }

        @Validation( minimum = 0 )
        default int page() {
            return getNumber( "page" ).intValue();
        }

        @Validation( minimum = 0 )
        default Integer total() {
            return getNumber( "total" ).integer();
        }
    }

    public interface JsonEntry extends JsonObject {

        @Validation( minLength = 11, maxLength = 11 )
        default String id() {
            return getString( "id" ).string();
        }

        @Validation( uniqueItems = YES )
        default JsonList<JsonAttribute> attributes() {
            return getList( "attributes", JsonAttribute.class );
        }

        default JsonMap<JsonString> getValues() {
            return getMap( "values", JsonString.class );
        }
    }

    public interface JsonAttribute extends JsonObject {

        @Required
        default String name() {
            return getString( "name" ).string();
        }

        @Validation( dependentRequired = "val^" )
        default String text() {
            return getString( "text" ).string();
        }

        @Validation( dependentRequired = "val^" )
        default Number value() {
            return getNumber( "value" ).number();
        }
    }

    @Test
    void testDeep_Valid_EmptyPage() {
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"pager": {"size": 20, "page": 0, "total": 0}, "entries": []}""" ).validate( JsonPage.class ) );
    }

    @Test
    void testDeep_Valid_Entry() {
        String json = """
            {"pager": {"size": 20, "page": 0},
            "entries": [{"id": "a0123456789"}]}""";
        assertDoesNotThrow( () -> JsonMixed.of( json ).validate( JsonPage.class ) );
    }

    @Test
    void testDeep_Valid_Attributes() {
        String json = """
            {"pager": {"size": 20, "page": 0},
            "entries": [{"id": "a0123456789"}, {"id": "b0123456789", "attributes":  [
                {"name": "age", "value": 12},
                {"name": "name", "text": "Peter"}
            ]}]}""";
        assertDoesNotThrow( () -> JsonMixed.of( json ).validate( JsonPage.class ) );
    }

    @Test
    void testDeep_Error_NoPager() {
        assertValidationError( """
            {"entries": []}""", JsonPage.class, Rule.REQUIRED, "pager" );
    }

    @Test
    void testDeep_Error_PagerSizeZero() {
        Validation.Error error = assertValidationError( """
            {"pager": {"size": 0, "page": 0}, "entries":[]}""", JsonPage.class, Rule.MINIMUM, 1d, 0d );
        assertEquals( "$.pager.size", error.path() );
    }

    @Test
    void testDeep_Error_PagerPageNegative() {
        Validation.Error error = assertValidationError( """
            {"pager": {"size": 20, "page": -1}, "entries":[]}""", JsonPage.class, Rule.MINIMUM, 0d, -1d );
        assertEquals( "$.pager.page", error.path() );
    }

    @Test
    void testDeep_Error_PagerPageNoInteger() {
        Validation.Error error = assertValidationError( """
                {"pager": {"size": 20, "page": true}, "entries":[]}""", JsonPage.class, Rule.TYPE,
            Set.of( NodeType.INTEGER ), NodeType.BOOLEAN );
        assertEquals( "$.pager.page", error.path() );
    }

    @Test
    void testDeep_Error_PagerTotalWrongType() {
        Validation.Error error = assertValidationError( """
                {"pager": {"size": 20, "page": 0, "total": "yes"}, "entries":[]}""", JsonPage.class, Rule.TYPE,
            Set.of( NodeType.INTEGER ), NodeType.STRING );
        assertEquals( "$.pager.total", error.path() );
    }

    @Test
    void testDeep_Error_NoEntries() {
        assertValidationError( """
            {"pager": {"size": 20, "page": 0}}""", JsonPage.class, Rule.REQUIRED, "entries" );
        assertValidationError( """
            {"pager": {"size": 20, "page": 0}, "entries":null}""", JsonPage.class, Rule.REQUIRED, "entries" );
    }

    @Test
    void testDeep_Error_EntriesNoId() {
        assertValidationError( """
            {"pager": {"size": 20, "page": 0}, "entries":[{}]}""", JsonPage.class, Rule.REQUIRED, "id" );
    }

    @Test
    void testDeep_Error_IdWrongFormat() {
        String json = """
            {"pager": {"size": 20, "page": 0}, "entries":[
            {"id": "ABC"}]}""";
        Validation.Error error = assertValidationError( json, JsonPage.class, Rule.MIN_LENGTH, 11, 3 );
        assertEquals( "$.entries[0].id", error.path() );
    }

    @Test
    void testDeep_Error_IdWrongType() {
        String json = """
            {"pager": {"size": 20, "page": 0}, "entries":[
            {"id": 42}]}""";
        Validation.Error error = assertValidationError( json, JsonPage.class, Rule.TYPE, Set.of( NodeType.STRING ),
            NodeType.NUMBER );
        assertEquals( "$.entries[0].id", error.path() );
    }

    @Test
    void testDeep_Error_ValuesWrongType() {
        String json = """
            {"pager": {"size": 20, "page": 0}, "entries":[
            {"id": "a0123456789", "values": false}]}""";
        Validation.Error error = assertValidationError( json, JsonPage.class, Rule.TYPE, Set.of( NodeType.OBJECT ),
            NodeType.BOOLEAN );
        assertEquals( "$.entries[0].values", error.path() );
    }

    @Test
    void testDeep_Error_AttributesAsMap() {
        String json = """
            {"pager": {"size": 20, "page": 0}, "entries":[
            {"id": "a0123456789", "attributes": {"name": "foo", "value": 1}}]}""";
        Validation.Error error = assertValidationError( json, JsonPage.class, Rule.TYPE, Set.of( NodeType.ARRAY ),
            NodeType.OBJECT );
        assertEquals( "$.entries[0].attributes", error.path() );
    }

    @Test
    void testDeep_Error_AttributeNoName() {
        String json = """
            {"pager": {"size": 20, "page": 0}, "entries":[
            {"id": "a0123456789", "attributes": [{"value": 1}]}]}""";
        Validation.Error error = assertValidationError( json, JsonPage.class, Rule.REQUIRED, "name" );
        assertEquals( "$.entries[0].attributes[0].name", error.path() );
    }

    @Test
    void testDeep_Error_AttributeNoTextOrValue() {
        String json = """
            {"pager": {"size": 20, "page": 0}, "entries":[
            {"id": "a0123456789", "attributes": [{"name": "foo"}]}]}""";

        Validation.Error error = assertValidationError( json, JsonPage.class, Rule.DEPENDENT_REQUIRED,
            Set.of( "text", "value" ), Set.of() );
        assertEquals( "$.entries[0].attributes[0]", error.path() );
    }

    @Test
    void testDeep_Error_AttributeNotUnique() {
        String json = """
            {"pager": {"size": 20, "page": 0}, "entries":[
            {"id": "a0123456789", "attributes": [
            {"name": "foo", "value": 1},
            {"name":"bar","value": 1},
            {"name":"foo","value":1}
            ]}]}""";

        Validation.Error error = assertValidationError( json, JsonPage.class, Rule.UNIQUE_ITEMS,
            "{\"name\":\"foo\",\"value\":1}", 0, 2 );
        assertEquals( "$.entries[0].attributes", error.path() );
    }
}
