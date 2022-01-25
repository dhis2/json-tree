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

import java.io.PrintStream;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.hisp.dhis.jsontree.JsonBuilder.JsonArrayBuilder;
import org.hisp.dhis.jsontree.JsonBuilder.JsonObjectBuilder;

/**
 * An "append only" {@link JsonBuilder} implementation that can be used with a
 * {@link PrintStream} or a {@link StringBuilder}.
 *
 * @author Jan Bernitt
 */
public class JsonAppender implements JsonBuilder, JsonObjectBuilder, JsonArrayBuilder
{

    public interface CharConsumer
    {

        void accept( char c );
    }

    private final Consumer<CharSequence> appendStr;

    private final CharConsumer appendChar;

    private final Supplier<String> toStr;

    private final boolean[] addedByLevel = new boolean[128];

    private int level = 0;

    public JsonAppender( PrintStream out )
    {
        this( out::append, out::append, () -> null );
    }

    public JsonAppender( StringBuilder out )
    {
        this( out::append, out::append, out::toString );
    }

    public JsonAppender( Consumer<CharSequence> appendStr, CharConsumer appendChar, Supplier<String> toStr )
    {
        this.appendStr = appendStr;
        this.appendChar = appendChar;
        this.toStr = toStr;
    }

    private void append( char c )
    {
        appendChar.accept( c );
    }

    private void append( CharSequence str )
    {
        appendStr.accept( str );
    }

    private void appendCommaWhenNeeded()
    {
        if ( !addedByLevel[level] )
        {
            addedByLevel[level] = true;
        }
        else
        {
            append( ',' );
        }
    }

    private void appendEscaped( CharSequence str )
    {
        str.chars().forEachOrdered( c -> {
            if ( c == '"' || c == '\\' || c <= ' ' )
            {
                appendChar.accept( '\\' );
            }
            appendChar.accept( (char) c );
        } );
    }

    private void beginLevel( char c )
    {
        append( c );
        addedByLevel[++level] = false;
    }

    private void endLevel( char c )
    {
        append( c );
        level--;
    }

    @Override
    public JsonNode toObject( Consumer<JsonObjectBuilder> obj )
    {
        beginLevel( '{' );
        obj.accept( this );
        endLevel( '}' );
        return toNode();

    }

    @Override
    public JsonNode toArray( Consumer<JsonArrayBuilder> arr )
    {
        beginLevel( '[' );
        arr.accept( this );
        endLevel( ']' );
        return toNode();
    }

    private JsonNode toNode()
    {
        String json = toStr.get();
        return json == null ? null : new JsonDocument( json ).get( "$" );
    }

    /*
     * JsonObjectBuilder
     */

    private JsonObjectBuilder addRawMember( String name, CharSequence rawValue )
    {
        appendCommaWhenNeeded();
        append( '"' );
        append( name );
        append( '"' );
        append( ':' );
        append( rawValue );
        return this;
    }

    @Override
    public JsonObjectBuilder addMember( String name, JsonNode value )
    {
        return addRawMember( name, value.getDeclaration() );
    }

    @Override
    public JsonObjectBuilder addBoolean( String name, boolean value )
    {
        return addRawMember( name, value ? "true" : "false" );
    }

    @Override
    public JsonObjectBuilder addBoolean( String name, Boolean value )
    {
        return addRawMember( name, value == null ? "null" : value ? "true" : "false" );
    }

    @Override
    public JsonObjectBuilder addNumber( String name, int value )
    {
        return addRawMember( name, String.valueOf( value ) );
    }

    @Override
    public JsonObjectBuilder addNumber( String name, long value )
    {
        return addRawMember( name, String.valueOf( value ) );
    }

    @Override
    public JsonObjectBuilder addNumber( String name, double value )
    {
        return addRawMember( name, String.valueOf( value ) );
    }

    @Override
    public JsonObjectBuilder addNumber( String name, Number value )
    {
        return addRawMember( name, value == null ? "null" : value.toString() );
    }

    @Override
    public JsonObjectBuilder addString( String name, String value )
    {
        appendCommaWhenNeeded();
        append( '"' );
        append( name );
        append( "\":\"" );
        appendEscaped( value );
        append( '"' );
        return this;
    }

    @Override
    public JsonObjectBuilder addArray( String name, Consumer<JsonArrayBuilder> value )
    {
        appendCommaWhenNeeded();
        append( '"' );
        append( name );
        append( '"' );
        append( ':' );
        beginLevel( '[' );
        value.accept( this );
        endLevel( ']' );
        return this;
    }

    @Override
    public JsonObjectBuilder addObject( String name, Consumer<JsonObjectBuilder> value )
    {
        appendCommaWhenNeeded();
        append( '"' );
        append( name );
        append( '"' );
        append( ':' );
        beginLevel( '{' );
        value.accept( this );
        endLevel( '}' );
        return this;
    }

    @Override
    public <K, V> JsonObjectBuilder addObject( String name, Map<K, V> value,
        TriConsumer<JsonObjectBuilder, ? super K, ? super V> toMember )
    {
        if ( name == null )
        {
            value.forEach( ( k, v ) -> toMember.accept( this, k, v ) );
            return this;
        }
        appendCommaWhenNeeded();
        append( '"' );
        append( name );
        append( '"' );
        append( ':' );
        beginLevel( '{' );
        value.forEach( ( k, v ) -> toMember.accept( this, k, v ) );
        endLevel( '}' );
        return this;
    }

    @Override
    public JsonObjectBuilder addMember( String name, Object pojo, JsonMapper mapper )
    {
        mapper.addTo( this, null, pojo );
        return this;
    }

    /*
     * JsonArrayBuilder
     */

    private JsonArrayBuilder addRawElement( CharSequence rawValue )
    {
        appendCommaWhenNeeded();
        appendStr.accept( rawValue );
        return this;
    }

    @Override
    public JsonArrayBuilder addElement( JsonNode value )
    {
        return addRawElement( value.getDeclaration() );
    }

    @Override
    public JsonArrayBuilder addBoolean( boolean value )
    {
        return addRawElement( value ? "true" : "false" );
    }

    @Override
    public JsonArrayBuilder addBoolean( Boolean value )
    {
        return addRawElement( value == null ? "null" : value ? "true" : "false" );
    }

    @Override
    public JsonArrayBuilder addNumber( int value )
    {
        return addRawElement( String.valueOf( value ) );
    }

    @Override
    public JsonArrayBuilder addNumber( long value )
    {
        return addRawElement( String.valueOf( value ) );
    }

    @Override
    public JsonArrayBuilder addNumber( double value )
    {
        return addRawElement( String.valueOf( value ) );
    }

    @Override
    public JsonArrayBuilder addNumber( Number value )
    {
        return addRawElement( value == null ? "null" : value.toString() );
    }

    @Override
    public JsonArrayBuilder addString( String value )
    {
        appendCommaWhenNeeded();
        append( '"' );
        appendEscaped( value );
        append( '"' );
        return this;
    }

    @Override
    public JsonArrayBuilder addArray( Consumer<JsonArrayBuilder> value )
    {
        appendCommaWhenNeeded();
        beginLevel( '[' );
        value.accept( this );
        endLevel( ']' );
        return this;
    }

    @Override
    public <E> JsonArrayBuilder addArray( Stream<E> values, BiConsumer<JsonArrayBuilder, ? super E> toElement )
    {
        appendCommaWhenNeeded();
        beginLevel( '[' );
        values.forEachOrdered( v -> toElement.accept( this, v ) );
        endLevel( ']' );
        return this;
    }

    @Override
    public JsonArrayBuilder addObject( Consumer<JsonObjectBuilder> value )
    {
        appendCommaWhenNeeded();
        beginLevel( '{' );
        value.accept( this );
        endLevel( '}' );
        return this;
    }

    @Override
    public <K, V> JsonObjectBuilder addObject( Map<K, V> value,
        TriConsumer<JsonObjectBuilder, ? super K, ? super V> toMember )
    {
        appendCommaWhenNeeded();
        beginLevel( '{' );
        value.forEach( ( k, v ) -> toMember.accept( this, k, v ) );
        endLevel( '}' );
        return this;
    }

    @Override
    public JsonArrayBuilder addElement( Object value, JsonMapper mapper )
    {
        mapper.addTo( this, value );
        return this;
    }

}
