package org.hisp.dhis.jsontree.schema;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonNodeType;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.JsonValue;
import org.hisp.dhis.jsontree.Validation.NodeType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;

public class Validation {

    enum Type {
        TYPE, ENUM,

        // string values
        MIN_LENGTH, MAX_LENGTH, PATTERN,

        // number values
        MINIMUM, MAXIMUM, EXCLUSIVE_MINIMUM, EXCLUSIVE_MAXIMUM, MULTIPLE_OF,

        // array values
        MIN_ITEMS, MAX_ITEMS, UNIQUE_ITEMS, MIN_CONTAINS, MAX_CONTAINS,

        // object values
        MIN_PROPERTIES, MAX_PROPERTIES, REQUIRED, DEPENDENT_REQUIRED
    }

    record Error(Type type, JsonMixed value, List<Object> args) {

        public static Error of( Type type, JsonMixed value, Object... args ) {
            return new Error( type, value, List.of( args ) );
        }
    }

    /**
     * Value validation
     */
    interface Validator {

        /**
         * Adds an error to the provided callback in case the provided value is not valid according to this check.
         *
         * @param value the value to check
         * @param addError callback to add errors
         */
        void validate( JsonMixed value, Consumer<Error> addError );
    }

    /**
     * Used when no further checks are done but a non-null {@link Validator} instance is needed.
     */
    private record Valid() implements Validator {

        @Override public void validate( JsonMixed value, Consumer<Error> addError ) {
            // nothing to do
        }
    }

    private record All(List<Validator> validators) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            validators.forEach( v -> v.validate( value, addError ) );
        }
    }

    private record TypeSwitch(Map<NodeType, Validator> anyOf) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            JsonNodeType type = value.node().getType();
            Validator forType = switch ( type ) {
                case OBJECT -> anyOf.get( NodeType.OBJECT );
                case ARRAY -> anyOf.get( NodeType.ARRAY );
                case STRING -> anyOf.get( NodeType.STRING );
                case BOOLEAN -> anyOf.get( NodeType.BOOLEAN );
                case NULL -> anyOf.get( NodeType.NULL );
                case NUMBER -> anyOf.get( NodeType.NUMBER );
            };
            if (forType == null && type == JsonNodeType.NUMBER && value.number().doubleValue() %1d == 0d)
                forType = anyOf.get( NodeType.INTEGER );
            if (forType == null) {
                addError.accept( Error.of( Type.TYPE, value, anyOf.keySet() ) );
            } else {
                forType.validate( value, addError );
            }
        }
    }

    private record TypeEach(Validator each) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (value.isObject()) value.asMap( JsonMixed.class ).forEach( (k,v) -> each.validate( v, addError ));
            if (value.isArray()) value.asList( JsonMixed.class ).forEach( e -> each.validate( e, addError ) );
        }
    }

    private record EnumJson(List<String> constants) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (!constants.contains( value.node().getDeclaration() ))
                addError.accept( Error.of( Type.ENUM, value, constants ) );
        }
    }

    private record EnumString(List<String> constants) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (value.isString() && !constants.contains( value.string() ))
                addError.accept( Error.of( Type.ENUM, value, constants ) );
        }
    }

    /*
    string values
     */

    private record MinLength(int limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (value.isString() && value.string().length() < limit)
                addError.accept( Error.of( Type.MIN_LENGTH, value, limit ) );
        }
    }

    private record MaxLength(int limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (value.isString() && value.string().length() > limit)
                addError.accept( Error.of( Type.MAX_LENGTH, value, limit ) );
        }
    }

    private record Pattern(String regex) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (value.isString() && !value.string().matches( regex ))
                addError.accept( Error.of( Type.PATTERN, value, regex ) );
        }
    }


    /*
    number values
     */

    private record Minimum(double limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isNumber() && value.number().doubleValue() < limit )
                addError.accept( Error.of( Type.MINIMUM, value, limit ) );
        }
    }

    private record Maximum(double limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (value.isNumber() && value.number().doubleValue() > limit)
                addError.accept( Error.of( Type.MAXIMUM, value, limit ) );
        }
    }

    private record ExclusiveMinimum(double limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isNumber() && value.number().doubleValue() <= limit )
                addError.accept( Error.of( Type.EXCLUSIVE_MINIMUM, value, limit ) );
        }
    }

    private record ExclusiveMaximum(double limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (value.isNumber() && value.number().doubleValue() >= limit)
                addError.accept( Error.of( Type.EXCLUSIVE_MAXIMUM, value, limit ) );
        }
    }

    private record MultipleOf(double n) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (value.isNumber() && value.number().doubleValue() % n > 0d)
                addError.accept( Error.of( Type.MULTIPLE_OF, value, n ) );
        }
    }

    /*
    array values
     */

    private record MinItems(int count) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (value.isArray() && value.size() < count )
                addError.accept( Error.of(Type.MIN_ITEMS, value, count) );
        }
    }

    private record MaxItems(int count) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (value.isArray() && value.size() > count )
                addError.accept( Error.of( Type.MAX_ITEMS, value, count ) );
        }
    }

    private record UniqueItems() implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (value.isArray()) {
                List<String> elementsAsJson = value.asList( JsonValue.class ).toList( v -> v.node().getDeclaration() );
                for (int i = 0; i < elementsAsJson.size(); i++) {
                    int j = elementsAsJson.lastIndexOf( elementsAsJson.get( i ) );
                    if ( j != i)
                        addError.accept( Error.of( Type.UNIQUE_ITEMS, value, elementsAsJson.get( i ), i, j ) );
                }
            }
        }
    }

    /*
    object values
     */

    private record MinProperties(int limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (value.isObject() && value.size() < limit)
                addError.accept( Error.of( Type.MIN_PROPERTIES, value, limit ) );
        }
    }

    private record MaxProperties(int limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (value.isObject() && value.size() > limit)
                addError.accept( Error.of( Type.MAX_PROPERTIES, value, limit ) );
        }
    }

    private record Required(List<String> properties) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (value.isObject() && !value.has( properties ))
                addError.accept( Error.of( Type.REQUIRED, value, properties ) );
        }
    }

    private record DependentRequired(Map<String, List<String>> properties) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if (value.isObject()) {
                properties.forEach( (provided, required) -> {
                    if (value.get( provided ).exists() && !value.has( required ))
                        addError.accept( Error.of( Type.DEPENDENT_REQUIRED, value, provided, required ) );
                } );
            }
        }
    }

    private record Entry(Class<? extends JsonValue> type, Map<String, Validator> properties) {}

    private static final Map<Class<? extends JsonValue>, Entry> VALIDATORS_BY_TYPE = new ConcurrentSkipListMap<>(
        Comparator.comparing( Class::getCanonicalName ));

    public static Entry of(Class<? extends JsonValue> type) {
        return VALIDATORS_BY_TYPE.computeIfAbsent( type, Validation::ofInternal );
    }

    private static Entry ofInternal(Class<? extends JsonValue> type) {
        if ( JsonObject.class.isAssignableFrom( type ) ) {

        }
        return null;
    }
}
