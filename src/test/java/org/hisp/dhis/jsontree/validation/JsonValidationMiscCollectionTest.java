package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonInteger;
import org.hisp.dhis.jsontree.JsonList;
import org.hisp.dhis.jsontree.JsonMap;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.hisp.dhis.jsontree.Assertions.assertValidationError;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test to demonstrate how annotations can be used to validate types like @{@link JsonIntList} as the automatic Java
 * type analysis will not provide full recursive validation for the items.
 *
 * @author Jan Bernitt
 */
class JsonValidationMiscCollectionTest {

    @Validation( type = NodeType.ARRAY )
    @Validation.Items( @Validation( type = NodeType.INTEGER ) )
    public interface JsonIntList extends JsonList<JsonInteger> {

    }

    public interface JsonPage extends JsonObject {

        default JsonIntList getEntries() {
            return get( "entries", JsonIntList.class );
        }
    }

    public interface JsonBook extends JsonObject {

        default JsonMap<JsonPage> pages() {
            return getMap( "pages", JsonPage.class );
        }
    }

    @Test
    void testCollection_OK() {
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"entries": []}""" ).validate( JsonPage.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"entries": [1,2,3]}""" ).validate( JsonPage.class ) );
    }

    @Test
    void testCollection_NotAnArray() {
        assertValidationError( """
            {"entries": {}}""", JsonPage.class, Rule.TYPE, Set.of( NodeType.ARRAY ), NodeType.OBJECT );
    }

    @Test
    void testCollection_NotAnIntElement() {
        assertValidationError( """
            {"entries": [true]}""", JsonPage.class, Rule.TYPE, Set.of( NodeType.INTEGER ), NodeType.BOOLEAN );
    }

    @Test
    void testCollection_NotAnIntElementDeep() {
        Validation.Error error = assertValidationError( """
                {"pages": { "title": {"entries": [13, 42.5]}}}""", JsonBook.class, Rule.TYPE, Set.of( NodeType.INTEGER ),
            NodeType.NUMBER );
        assertEquals( "$.pages.title.entries[1]", error.path() );
    }
}
