package org.hisp.dhis.jsontree.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonPath;
import org.hisp.dhis.jsontree.JsonPathException;
import org.hisp.dhis.jsontree.JsonSchemaException;
import org.hisp.dhis.jsontree.JsonSchemaException.Info;
import org.hisp.dhis.jsontree.JsonTreeException;
import org.hisp.dhis.jsontree.JsonValue;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.Error;

/**
 * @author Jan Bernitt
 * @since 0.11
 */
public final class JsonValidator {

    public static void validate( JsonValue value, Class<?> schema ) {
        validate( value, schema, Set.of() );
    }

    public static void validate( JsonValue value, Class<?> schema, Validation.Rule... rules ) {
        validate( value, schema, Set.of( rules ) );
    }

    public static void validate( JsonValue value, Class<?> schema, Set<Validation.Rule> rules ) {
        if ( !value.exists() )
            throw new JsonPathException( value.path(),
                String.format( "Value at path `%s` is not a %s object as it does not exist",
                    value.path(), schema.getSimpleName() ) );
        if ( !value.isObject() ) {
            throw new JsonTreeException(
                String.format( "Value at path `%s` is not a %s object but a %s",
                    value.path(), schema.getSimpleName(), value.type() ) );
        }
        ObjectValidator validator = ObjectValidator.of( schema );
        if (validator.properties().isEmpty()) return;

        List<Error> errors = new ArrayList<>();

        //TODO add a fail-fast (1st error) mode
        //TODO strict types vs convertable types mode

        for ( Map.Entry<JsonPath, Validation.Validator> e : validator.properties().entrySet() ) {
            JsonMixed property = value.asObject().get( e.getKey(), JsonMixed.class );
            e.getValue().validate( property, errors::add );
        }
        if ( !rules.isEmpty() )
            errors = errors.stream().filter( e -> rules.contains( e.rule() ) ).toList();
        if ( !errors.isEmpty() ) throw new JsonSchemaException( "%d errors".formatted( errors.size() ),
            new Info( value, schema, errors ) );
    }

    //TODO a publicly accessible way to get the JSON Schema validation description (JSON) for a schema class
    // and also for classes used as values like UID to get what the validation is on these in isolation for OpenAPI

    //TODO a mode or setting that allows to skip validation on parts that are about stream processing
    // like Stream<X> or Iterator<X> types where the validation would rack the benefits
    // of stream processing the items
}
