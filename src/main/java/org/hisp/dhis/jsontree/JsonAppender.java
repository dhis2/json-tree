/*
 * Copyright (c) 2004-2021, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonBuilder.JsonArrayBuilder;
import org.hisp.dhis.jsontree.JsonBuilder.JsonObjectBuilder;

import java.io.PrintStream;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * An "append only" {@link JsonBuilder} implementation that can be used with a {@link PrintStream} or a
 * {@link StringBuilder}.
 *
 * @author Jan Bernitt
 */
final class JsonAppender implements JsonBuilder, JsonObjectBuilder, JsonArrayBuilder {

    public interface CharConsumer {

        void accept( char c );
    }

    private final PrettyPrint config;
    private final boolean indent;
    private final String indent1;
    private final String colon;

    private final Consumer<CharSequence> appendStr;
    private final CharConsumer appendChar;
    private final Supplier<String> toStr;
    private final boolean[] hasChildrenAtLevel = new boolean[128];

    private int level = 0;
    private String indentLevel = "";

    public JsonAppender( PrettyPrint config, PrintStream out ) {
        this( config, out::append, out::append, () -> null );
    }

    public JsonAppender( PrettyPrint config, StringBuilder out ) {
        this( config, out::append, out::append, out::toString );
    }

    private JsonAppender( PrettyPrint config, Consumer<CharSequence> appendStr, CharConsumer appendChar,
        Supplier<String> toStr ) {
        this.config = config;
        this.indent = config.indentSpaces() > 0 || config.indentTabs() > 0;
        this.indent1 = "\t".repeat( config.indentTabs() ) + " ".repeat( config.indentSpaces() );
        this.colon = config.spaceAfterColon() ? ": " : ":";
        this.appendStr = appendStr;
        this.appendChar = appendChar;
        this.toStr = toStr;
    }

    private void append( char c ) {
        appendChar.accept( c );
    }

    private void append( CharSequence str ) {
        appendStr.accept( str );
    }

    private void appendCommaWhenNeeded() {
        if ( !hasChildrenAtLevel[level] ) {
            hasChildrenAtLevel[level] = true;
        } else {
            append( ',' );
        }
        if ( indent ) append( indentLevel );
    }

    private void appendEscaped( CharSequence str ) {
        str.chars().forEachOrdered( c -> {
            if ( c == '"' || c == '\\' || c < ' ' ) {
                appendChar.accept( '\\' );
            }
            appendChar.accept( (char) c );
        } );
    }

    private void beginLevel( char c ) {
        append( c );
        hasChildrenAtLevel[++level] = false;
        indentLevel = "\n" + indent1.repeat( level );
    }

    private void endLevel( char c ) {
        level--;
        indentLevel = "\n" + indent1.repeat( level );
        if ( indent && hasChildrenAtLevel[level + 1] ) append( indentLevel );
        append( c );
    }

    @Override
    public JsonNode toObject( Consumer<JsonObjectBuilder> obj ) {
        beginLevel( '{' );
        obj.accept( this );
        endLevel( '}' );
        return toNode();

    }

    @Override
    public JsonNode toArray( Consumer<JsonArrayBuilder> arr ) {
        beginLevel( '[' );
        arr.accept( this );
        endLevel( ']' );
        return toNode();
    }

    private JsonNode toNode() {
        String json = toStr.get();
        return json == null ? null : JsonNode.of( json );
    }

    /*
     * JsonObjectBuilder
     */

    private JsonObjectBuilder addRawMember( String name, CharSequence rawValue ) {
        appendCommaWhenNeeded();
        append( '"' );
        append( name );
        append( '"' );
        append( colon );
        append( rawValue );
        return this;
    }

    @Override
    public JsonObjectBuilder addMember( String name, JsonNode value ) {
        JsonNodeType type = value.getType();
        if ( config.excludeNullMembers() && type == JsonNodeType.NULL )
            return this;
        if ( config.retainOriginalDeclaration() || type.isSimple() )
            return addRawMember( name, value.getDeclaration() );
        return switch ( type ) {
            case OBJECT ->
                addObject( name, obj -> value.members().forEach( e -> obj.addMember( e.getKey(), e.getValue() ) ) );
            case ARRAY -> addArray( name, arr -> value.elements().forEach( arr::addElement ) );
            case NUMBER -> addNumber( name, (Number) value.value() );
            case STRING -> addString( name, (String) value.value() );
            case BOOLEAN -> addBoolean( name, (Boolean) value.value() );
            case NULL -> addBoolean( name, null );
        };
    }

    @Override
    public JsonObjectBuilder addBoolean( String name, boolean value ) {
        return addRawMember( name, value ? "true" : "false" );
    }

    @Override
    public JsonObjectBuilder addBoolean( String name, Boolean value ) {
        if ( value == null && config.excludeNullMembers() ) return this;
        return addRawMember( name, value == null ? "null" : value ? "true" : "false" );
    }

    @Override
    public JsonObjectBuilder addNumber( String name, int value ) {
        return addRawMember( name, String.valueOf( value ) );
    }

    @Override
    public JsonObjectBuilder addNumber( String name, long value ) {
        return addRawMember( name, String.valueOf( value ) );
    }

    @Override
    public JsonObjectBuilder addNumber( String name, double value ) {
        return addRawMember( name, String.valueOf( value ) );
    }

    @Override
    public JsonObjectBuilder addNumber( String name, Number value ) {
        if ( value == null && config.excludeNullMembers() ) return this;
        return addRawMember( name, value == null ? "null" : value.toString() );
    }

    @Override
    public JsonObjectBuilder addString( String name, String value ) {
        if ( value == null && config.excludeNullMembers() ) return this;
        if ( value == null ) return addRawMember( name, "null" );
        appendCommaWhenNeeded();
        append( '"' );
        append( name );
        append( '"' );
        append( colon );
        append( '"' );
        appendEscaped( value );
        append( '"' );
        return this;
    }

    @Override
    public JsonObjectBuilder addArray( String name, Consumer<JsonArrayBuilder> value ) {
        appendCommaWhenNeeded();
        append( '"' );
        append( name );
        append( '"' );
        append( colon );
        beginLevel( '[' );
        value.accept( this );
        endLevel( ']' );
        return this;
    }

    @Override
    public JsonObjectBuilder addObject( String name, Consumer<JsonObjectBuilder> value ) {
        appendCommaWhenNeeded();
        append( '"' );
        append( name );
        append( '"' );
        append( colon );
        beginLevel( '{' );
        value.accept( this );
        endLevel( '}' );
        return this;
    }

    @Override
    public <K, V> JsonObjectBuilder addObject( String name, Iterable<Map.Entry<K, V>> value,
        TriConsumer<JsonObjectBuilder, ? super K, ? super V> toMember ) {
        if ( name == null ) {
            value.forEach( e -> toMember.accept( this, e.getKey(), e.getValue() ) );
            return this;
        }
        appendCommaWhenNeeded();
        append( '"' );
        append( name );
        append( '"' );
        append( colon );
        beginLevel( '{' );
        value.forEach( e -> toMember.accept( this, e.getKey(), e.getValue() ) );
        endLevel( '}' );
        return this;
    }

    @Override
    public JsonObjectBuilder addMember( String name, Object pojo, JsonMapper mapper ) {
        mapper.addTo( this, name, pojo );
        return this;
    }

    /*
     * JsonArrayBuilder
     */

    private JsonArrayBuilder addRawElement( CharSequence rawValue ) {
        appendCommaWhenNeeded();
        appendStr.accept( rawValue );
        return this;
    }

    @Override
    public JsonArrayBuilder addElement( JsonNode value ) {
        JsonNodeType type = value.getType();
        if ( config.retainOriginalDeclaration() || type.isSimple() )
            return addRawElement( value.getDeclaration() );
        return switch ( type ) {
            case OBJECT ->
                addObject( obj -> value.members().forEach( e -> obj.addMember( e.getKey(), e.getValue() ) ) );
            case ARRAY -> addArray( arr -> value.elements().forEach( arr::addElement ) );
            case NUMBER -> addNumber( (Number) value.value() );
            case STRING -> addString( (String) value.value() );
            case BOOLEAN -> addBoolean( (Boolean) value.value() );
            case NULL -> addRawElement( "null" );
        };
    }

    @Override
    public JsonArrayBuilder addBoolean( boolean value ) {
        return addRawElement( value ? "true" : "false" );
    }

    @Override
    public JsonArrayBuilder addBoolean( Boolean value ) {
        return addRawElement( value == null ? "null" : value ? "true" : "false" );
    }

    @Override
    public JsonArrayBuilder addNumber( int value ) {
        return addRawElement( String.valueOf( value ) );
    }

    @Override
    public JsonArrayBuilder addNumber( long value ) {
        return addRawElement( String.valueOf( value ) );
    }

    @Override
    public JsonArrayBuilder addNumber( double value ) {
        return addRawElement( String.valueOf( value ) );
    }

    @Override
    public JsonArrayBuilder addNumber( Number value ) {
        return addRawElement( value == null ? "null" : value.toString() );
    }

    @Override
    public JsonArrayBuilder addString( String value ) {
        appendCommaWhenNeeded();
        append( '"' );
        appendEscaped( value );
        append( '"' );
        return this;
    }

    @Override
    public JsonArrayBuilder addArray( Consumer<JsonArrayBuilder> value ) {
        appendCommaWhenNeeded();
        beginLevel( '[' );
        value.accept( this );
        endLevel( ']' );
        return this;
    }

    @Override
    public <E> JsonArrayBuilder addArray( Stream<E> values, BiConsumer<JsonArrayBuilder, ? super E> toElement ) {
        appendCommaWhenNeeded();
        beginLevel( '[' );
        values.forEachOrdered( v -> toElement.accept( this, v ) );
        endLevel( ']' );
        return this;
    }

    @Override
    public <E> JsonArrayBuilder addElements( Stream<E> values, BiConsumer<JsonArrayBuilder, ? super E> toElement ) {
        values.forEachOrdered( v -> toElement.accept( this, v ) );
        return this;
    }

    @Override
    public JsonArrayBuilder addObject( Consumer<JsonObjectBuilder> value ) {
        appendCommaWhenNeeded();
        beginLevel( '{' );
        value.accept( this );
        endLevel( '}' );
        return this;
    }

    @Override
    public <K, V> JsonObjectBuilder addObject( Map<K, V> value,
        TriConsumer<JsonObjectBuilder, ? super K, ? super V> toMember ) {
        appendCommaWhenNeeded();
        beginLevel( '{' );
        value.forEach( ( k, v ) -> toMember.accept( this, k, v ) );
        endLevel( '}' );
        return this;
    }

    @Override
    public JsonArrayBuilder addElement( Object value, JsonMapper mapper ) {
        mapper.addTo( this, value );
        return this;
    }

}
