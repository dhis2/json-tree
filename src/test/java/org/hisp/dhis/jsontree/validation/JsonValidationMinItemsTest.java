package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonInteger;
import org.hisp.dhis.jsontree.JsonList;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.hisp.dhis.jsontree.Validation.YesNo.NO;
import static org.hisp.dhis.jsontree.Assertions.assertValidationError;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests Validation of the @{@link Validation#minItems()} property.
 *
 * @author Jan Bernitt
 */
class JsonValidationMinItemsTest {

    public interface JsonMinItemsExampleA extends JsonObject {

        @Validation( minItems = 2 )
        default List<String> names() {
            return getArray( "names" ).stringValues();
        }
    }

    public interface JsonMinItemsExampleB extends JsonObject {

        @Validation( minItems = 3, required = NO )
        default JsonList<JsonInteger> points() {
            return getList( "points", JsonInteger.class );
        }
    }

    @Test
    void testMinItems_OK() {
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"names":["foo", "bar"]}""" ).validate( JsonMinItemsExampleA.class ) );

        assertDoesNotThrow( () -> JsonMixed.of( """
            {"points":[1,2,3]}""" ).validate( JsonMinItemsExampleB.class ) );
    }

    @Test
    void testMinItems_Undefined() {
        assertValidationError( "{}", JsonMinItemsExampleA.class, Rule.REQUIRED, "names" );

        assertValidationError( """
            {"points":[]}""", JsonMinItemsExampleB.class, Rule.MIN_ITEMS, 3, 0 );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"points":null}""" ).validate( JsonMinItemsExampleB.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {}""" ).validate( JsonMinItemsExampleB.class ) );
    }

    @Test
    void testMinItems_TooFew() {
        assertValidationError( """
            {"names":["hey"]}""", JsonMinItemsExampleA.class, Rule.MIN_ITEMS, 2, 1 );
        assertValidationError( """
            {"points":[]}""", JsonMinItemsExampleB.class, Rule.MIN_ITEMS, 3, 0 );
        assertValidationError( """
            {"points":[1,2]}""", JsonMinItemsExampleB.class, Rule.MIN_ITEMS, 3, 2 );
    }

    @Test
    void testMinItems_WrongType() {
        assertValidationError( """
            {"names":true}""", JsonMinItemsExampleA.class, Rule.TYPE, Set.of( NodeType.ARRAY ), NodeType.BOOLEAN );
        assertValidationError( """
            {"points":true}""", JsonMinItemsExampleB.class, Rule.TYPE, Set.of( NodeType.ARRAY ), NodeType.BOOLEAN );
    }
}
