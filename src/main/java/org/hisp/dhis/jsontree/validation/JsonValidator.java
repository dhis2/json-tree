package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonSchemaException;
import org.hisp.dhis.jsontree.JsonSchemaException.Info;
import org.hisp.dhis.jsontree.JsonValue;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.Error;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Comparator.comparing;

/**
 * @author Jan Bernitt
 * @since 0.11
 */
public final class JsonValidator {

    public static void validate( JsonValue value, Class<? extends JsonValue> schema ) {
        ObjectValidator validator = ObjectValidator.getInstance( schema );
        List<Error> errors = new ArrayList<>();
        for ( Map.Entry<String, Validation.Validator> e : validator.properties().entrySet() ) {
            JsonValue property = value.asObject().get( e.getKey() );
            e.getValue().validate( property.as( JsonMixed.class ), errors::add );
        }
        if ( !errors.isEmpty() ) throw new JsonSchemaException( "%d errors".formatted( errors.size() ),
            new Info( value, schema, errors ) );
    }
}
