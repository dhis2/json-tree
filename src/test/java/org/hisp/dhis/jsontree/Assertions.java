package org.hisp.dhis.jsontree;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

public class Assertions {

    public static Validation.Error assertValidationError( String actualJson,
        Class<?> schema,
        Validation.Rule expected, Object... args ) {
        return assertValidationError( JsonMixed.of( actualJson ), schema, expected, args );
    }

    public static Validation.Error assertValidationError( JsonObject actual,
        Class<?> schema,
        Validation.Rule expected, Object... args ) {
        JsonSchemaException ex = assertThrowsExactly( JsonSchemaException.class, () -> actual.validate( schema ),
            "expected an error of type " + expected );
        List<Validation.Error> errors = ex.getInfo().errors();
        assertEquals( 1, errors.size(),
            () -> "unexpected number of errors: "+String.join( "", errors.stream().map( Validation.Error::toString ).toList() ) );
        Validation.Error error = errors.get( 0 );
        assertEquals( expected, error.rule(), () -> "unexpected error type: "+error );
        List<Object> actualArgs = textToString( error.args() );
        assertEquals( List.of( args ), actualArgs, () -> "unexpected error arguments: "+error );
        return error;
    }

    private static List<Object> textToString( List<Object> args ) {
        return args.stream().map( Assertions::textToString ).toList();
    }

    private static Object textToString( Object arg ) {
        if (arg instanceof Text t) return t.toString();
        if (arg instanceof Set<?> s) return s.stream().map( Assertions::textToString ).collect( Collectors.toSet());
        if (arg instanceof List<?> l) return l.stream().map( Assertions::textToString ).toList();
        if (arg instanceof Map<?,?> m) return m.entrySet().stream().collect( Collectors.toMap(
            e -> textToString( e.getKey() ), e -> textToString( e.getValue() )  ) );
        return arg;
    }
}
