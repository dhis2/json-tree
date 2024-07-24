package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonBuilder.JsonArrayBuilder;
import org.hisp.dhis.jsontree.JsonBuilder.JsonObjectBuilder;
import org.hisp.dhis.jsontree.internal.Surly;

import java.util.function.Consumer;

/**
 * Builder for JUON
 *
 * @author Jan Bernitt
 * @since 1.3
 */
final class JuonAppender implements JsonObjectBuilder, JsonArrayBuilder {

    private final Juon.Format format;
    private final StringBuilder out = new StringBuilder();
    private final boolean[] hasChildrenAtLevel = new boolean[128];

    private int level = 0;

    JuonAppender( Juon.Format format ) {
        this.format = format;
    }

    @Override public String toString() {
        return out.toString();
    }

    static String toJuon( Juon.Format format,  JsonNode value ) {
        JuonAppender bld = new JuonAppender(format);
        bld.addElement( value );
        return bld.out.toString();
    }

    @Override public JsonObjectBuilder addMember( String name, JsonNode value ) {
        JsonNodeType type = value.getType();
        if ( type == JsonNodeType.NULL )
            return addRawMember( name,  format.nullsInObjects().value );
        return switch ( type ) {
            case OBJECT -> addObject( name, obj -> value.members().forEach( e -> obj.addMember( e.getKey(), e.getValue() ) ) );
            case ARRAY -> addArray( name, arr -> value.elements().forEach( arr::addElement ) );
            case STRING -> addString( name, (String)value.value() );
            case NUMBER -> addNumber( name, (Number) value.value() );
            case BOOLEAN -> addBoolean( name, (Boolean) value.value() );
            case NULL -> addBoolean( name, null );
        };
    }

    @Override public JsonObjectBuilder addBoolean( String name, boolean value ) {
        String raw = String.valueOf( value );
        return addRawMember( name, format.booleanShorthands() ? raw.substring( 0, 1 ) : raw );
    }

    @Override public JsonObjectBuilder addBoolean( String name, Boolean value ) {
        if (value == null) return addRawMember( name,  format.nullsInObjects().value );
        return addBoolean( name, value.booleanValue() );
    }

    @Override public JsonObjectBuilder addNumber( String name, int value ) {
        return addRawMember( name, String.valueOf( value ) );
    }

    @Override public JsonObjectBuilder addNumber( String name, long value ) {
        return addRawMember( name, String.valueOf( value ) );
    }

    @Override public JsonObjectBuilder addNumber( String name, double value ) {
        JsonBuilder.checkValid( value );
        return addRawMember( name, String.valueOf( value ) );
    }

    @Override public JsonObjectBuilder addNumber( String name, Number value ) {
        if (value == null) return addRawMember( name,  format.nullsInObjects().value );
        JsonBuilder.checkValid( value );
        return addRawMember( name, String.valueOf( value ) );
    }

    @Override public JsonObjectBuilder addString( String name, String value ) {
        if (value == null) return addRawMember( name,  format.nullsInObjects().value );
        addMember( name );
        appendEscaped( value );
        return this;
    }

    @Override public JsonObjectBuilder addArray( String name, Consumer<JsonArrayBuilder> arr ) {
        addMember( name );
        addArray( arr );
        return this;
    }

    @Override public JsonObjectBuilder addObject( String name, Consumer<JsonObjectBuilder> obj ) {
        addMember( name );
        addObject( obj );
        return this;
    }

    private void addMember( String name ) {
        appendCommaWhenNeeded();
        append( name );
        append( ':' );
    }

    private JsonObjectBuilder addRawMember(String name, CharSequence rawValue ) {
        if (rawValue == null) return this;
        addMember( name );
        append( rawValue );
        return this;
    }

    private JsonArrayBuilder addRawElement( CharSequence rawValue ) {
        appendCommaWhenNeeded();
        if (rawValue != null && !rawValue.isEmpty())
            append( rawValue );
        return this;
    }

    @Override public JsonArrayBuilder addElement( JsonNode value ) {
        JsonNodeType type = value.getType();
        if ( type == JsonNodeType.NULL )
            return addRawElement( format.nullsInArrays().value );
        return switch ( type ) {
            case OBJECT -> addObject(obj -> value.members().forEach( e -> obj.addMember( e.getKey(), e.getValue() ) ) );
            case ARRAY -> addArray(  arr -> value.elements().forEach( arr::addElement ) );
            case STRING -> addString(  (String)value.value() );
            case NUMBER -> addNumber( (Number) value.value() );
            case BOOLEAN -> addBoolean( (Boolean) value.value() );
            case NULL -> addBoolean( null );
        };
    }

    @Override public JsonArrayBuilder addBoolean( boolean value ) {
        String raw = String.valueOf( value );
        return addRawElement( format.booleanShorthands() ? raw.substring( 0, 1 ) : raw );
    }

    @Override public JsonArrayBuilder addBoolean( Boolean value ) {
        if (value == null) return addRawElement( format.nullsInArrays().value );
        return addBoolean( value.booleanValue() );
    }

    @Override public JsonArrayBuilder addNumber( int value ) {
        return addRawElement( String.valueOf( value ) );
    }

    @Override public JsonArrayBuilder addNumber( long value ) {
        return addRawElement( String.valueOf( value ) );
    }

    @Override public JsonArrayBuilder addNumber( double value ) {
        JsonBuilder.checkValid( value );
        return addRawElement( String.valueOf( value ) );
    }

    @Override public JsonArrayBuilder addNumber( Number value ) {
        if (value == null) return addRawElement( format.nullsInArrays().value );
        JsonBuilder.checkValid( value );
        return addRawElement( value.toString() );
    }

    @Override public JsonArrayBuilder addString( String value ) {
        if (value == null) return addRawElement( format.nullsInArrays().value );
        appendCommaWhenNeeded();
        appendEscaped( value );
        return this;
    }

    @Override public JsonArrayBuilder addArray( Consumer<JsonArrayBuilder> arr ) {
        beginLevel();
        arr.accept( this );
        endLevel();
        return this;
    }

    @Override public JsonArrayBuilder addObject( Consumer<JsonObjectBuilder> obj ) {
        int l0 = out.length();
        beginLevel();
        obj.accept( this );
        endLevel();
        if ( out.length() == l0 + 2) {
            out.setLength( l0 ); // undo object
            append( "null" ); // approximate the empty object with null
        }
        return this;
    }

    private void beginLevel() {
        append( '(' );
        hasChildrenAtLevel[++level] = false;
    }

    private void endLevel() {
        level--;
        append( ')' );
    }

    private void appendCommaWhenNeeded() {
        if ( !hasChildrenAtLevel[level] ) {
            hasChildrenAtLevel[level] = true;
        } else {
            append( ',' );
        }
    }

    private void append(CharSequence str) {
        out.append( str );
    }

    private void append(char c) {
        out.append( c );
    }

    void appendEscaped(@Surly CharSequence str ) {
        append( '\'' );
        str.chars().forEachOrdered( c -> {
            if ( c == '\'' || c == '\\' || c < ' ' ) {
                append( '\\' );
            }
            append( (char) c );
        } );
        append( '\'' );
    }
}
