/*
 * Copyright (c) 2004-2022, University of Oslo
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

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link JsonTypedAccessStore} implementation {@link JsonTypedAccess} by using it via {@link JsonVirtualTree}
 * (it is the default implementation).
 *
 * @author Jan Bernitt
 */
class JsonTypedAccessTest {

    interface PrimitivesBean extends JsonObject {

        int aInt();

        Integer aBigInteger();

        int aInt( int v );

        Integer aBigInteger( Integer v );

        long aLong();

        Long aBigLong();

        long aLong( long v );

        Long aBigLong( Long v );

        float aFloat();

        Float aBigFloat();

        float aFloat( float v );

        Float aBigFloat( Float v );

        double aDouble();

        Double aBigDouble();

        double aDouble( double v );

        Double aBigDouble( Double v );

        boolean aBoolean();

        Boolean aBigBoolean();

        boolean aBoolean( boolean v );

        Boolean aBigBoolean( Boolean v );

        char aChar();

        Character aBigCharacter();

        char aChar( char v );

        Character aBigCharacter( Character v );

        String aString();

        String aString( String v );

        TextStyle aEnum();

        TextStyle aEnum( TextStyle v );
    }

    @Test
    void testAccess_Integer() {
        PrimitivesBean obj = JsonMixed.ofNonStandard( "{'aInt':42, 'aBigInteger': -13}" ).as( PrimitivesBean.class );
        assertEquals( 42, obj.aInt() );
        assertEquals( 42, obj.aInt( 8 ) );
        assertEquals( Integer.valueOf( -13 ), obj.aBigInteger() );
        assertEquals( Integer.valueOf( -13 ), obj.aBigInteger( 22 ) );
    }

    @Test
    void testAccess_IntegerNonExistent() {
        PrimitivesBean obj = JsonMixed.of( "{}" ).as( PrimitivesBean.class );
        assertThrowsExactly( JsonPathException.class, obj::aInt );
        assertEquals( 8, obj.aInt( 8 ) );
        assertNull( obj.aBigInteger() );
        assertEquals( Integer.valueOf( 22 ), obj.aBigInteger( 22 ) );
    }

    @Test
    void testAccess_Long() {
        PrimitivesBean obj = JsonMixed.ofNonStandard( "{'aLong':42, 'aBigLong': -13}" ).as( PrimitivesBean.class );
        assertEquals( 42L, obj.aLong() );
        assertEquals( 42L, obj.aLong( 8 ) );
        assertEquals( Long.valueOf( -13 ), obj.aBigLong() );
        assertEquals( Long.valueOf( -13 ), obj.aBigLong( 22L ) );
    }

    @Test
    void testAccess_LongNonExistent() {
        PrimitivesBean obj = JsonMixed.of( "{}" ).as( PrimitivesBean.class );
        assertThrowsExactly( JsonPathException.class, obj::aLong );
        assertEquals( 8L, obj.aLong( 8L ) );
        assertNull( obj.aBigLong() );
        assertEquals( Long.valueOf( 22 ), obj.aBigLong( 22L ) );
    }

    @Test
    void testAccess_Float() {
        PrimitivesBean obj = JsonMixed.ofNonStandard( "{'aFloat':4.2, 'aBigFloat': -1.3}" ).as( PrimitivesBean.class );
        assertEquals( 4.2f, obj.aFloat(), 0.01f );
        assertEquals( 4.2f, obj.aFloat( 8f ), 0.01f );
        assertEquals( Float.valueOf( -1.3f ), obj.aBigFloat() );
        assertEquals( Float.valueOf( -1.3f ), obj.aBigFloat( 22f ) );
    }

    @Test
    void testAccess_FloatNonExistent() {
        PrimitivesBean obj = JsonMixed.of( "{}" ).as( PrimitivesBean.class );
        assertThrowsExactly( JsonPathException.class, obj::aFloat );
        assertEquals( 8f, obj.aFloat( 8f ), 0.01f );
        assertNull( obj.aBigFloat() );
        assertEquals( Float.valueOf( 22f ), obj.aBigFloat( 22f ) );
    }

    @Test
    void testAccess_Double() {
        PrimitivesBean obj = JsonMixed.ofNonStandard( "{'aDouble':4.2, 'aBigDouble': -1.3}" )
            .as( PrimitivesBean.class );
        assertEquals( 4.2d, obj.aDouble(), 0.01d );
        assertEquals( 4.2d, obj.aDouble( 8d ), 0.01d );
        assertEquals( Double.valueOf( -1.3d ), obj.aBigDouble() );
        assertEquals( Double.valueOf( -1.3d ), obj.aBigDouble( 22d ) );
    }

    @Test
    void testAccess_DoubleNonExistent() {
        PrimitivesBean obj = JsonMixed.of( "{}" ).as( PrimitivesBean.class );
        assertThrowsExactly( JsonPathException.class, obj::aDouble );
        assertEquals( 8d, obj.aDouble( 8d ), 0.01d );
        assertNull( obj.aBigDouble() );
        assertEquals( Double.valueOf( 22d ), obj.aBigDouble( 22d ) );
    }

    @Test
    void testAccess_Char() {
        PrimitivesBean obj = JsonMixed.ofNonStandard( "{'aChar':'a', 'aBigCharacter': 'B'}" )
            .as( PrimitivesBean.class );
        assertEquals( 'a', obj.aChar() );
        assertEquals( 'a', obj.aChar( '8' ) );
        assertEquals( Character.valueOf( 'B' ), obj.aBigCharacter() );
        assertEquals( Character.valueOf( 'B' ), obj.aBigCharacter( 'X' ) );
    }

    @Test
    void testAccess_CharNonExistent() {
        PrimitivesBean obj = JsonMixed.of( "{}" ).as( PrimitivesBean.class );
        assertThrowsExactly( JsonPathException.class, obj::aChar );
        assertEquals( 'x', obj.aChar( 'x' ) );
        assertNull( obj.aBigCharacter() );
        assertEquals( Character.valueOf( 'Y' ), obj.aBigCharacter( 'Y' ) );
    }

    @Test
    void testAccess_Boolean() {
        PrimitivesBean obj = JsonMixed.ofNonStandard( "{'aBoolean':true, 'aBigBoolean': true}" )
            .as( PrimitivesBean.class );
        assertTrue( obj.aBoolean() );
        assertTrue( obj.aBoolean( false ) );
        assertEquals( Boolean.TRUE, obj.aBigBoolean() );
        assertEquals( Boolean.TRUE, obj.aBigBoolean( false ) );
    }

    @Test
    void testAccess_BooleanNonExistent() {
        PrimitivesBean obj = JsonMixed.of( "{}" ).as( PrimitivesBean.class );
        assertThrowsExactly( JsonPathException.class, obj::aBoolean );
        assertTrue( obj.aBoolean( true ) );
        assertNull( obj.aBigBoolean() );
        assertEquals( Boolean.TRUE, obj.aBigBoolean( true ) );
    }

    @Test
    void testAccess_String() {
        PrimitivesBean obj = JsonMixed.ofNonStandard( "{'aString': 'hello'}" ).as( PrimitivesBean.class );
        assertEquals( "hello", obj.aString() );
        assertEquals( "hello", obj.aString( "x" ) );
    }

    @Test
    void testAccess_StringNonExistent() {
        PrimitivesBean obj = JsonMixed.of( "{}" ).as( PrimitivesBean.class );
        assertNull( obj.aString() );
        assertEquals( "x", obj.aString( "x" ) );
    }

    @Test
    void testAccess_Enum() {
        PrimitivesBean obj = JsonMixed.ofNonStandard( "{'aEnum': 'FULL'}" ).as( PrimitivesBean.class );
        assertEquals( TextStyle.FULL, obj.aEnum() );
        assertEquals( TextStyle.FULL, obj.aEnum( TextStyle.SHORT ) );
    }

    @Test
    void testAccess_EnumNonExistent() {
        PrimitivesBean obj = JsonMixed.of( "{}" ).as( PrimitivesBean.class );
        assertNull( obj.aEnum() );
        assertEquals( TextStyle.SHORT, obj.aEnum( TextStyle.SHORT ) );
    }

    interface NestedBean extends JsonObject {

        int a();

        NestedBean getB();

        JsonList<NestedBean> list();
    }

    @Test
    void testAccess_ExtendedObject() {
        NestedBean obj = JsonMixed.ofNonStandard( "{'a':1, 'b': {'a':2}}" ).as( NestedBean.class );
        assertEquals( 1, obj.a() );
        assertEquals( 2, obj.getB().a() );
    }

    @Test
    void testAccess_ExtendedObjectList() {
        NestedBean obj = JsonMixed.ofNonStandard( "{'list': [{'a':3}, {'a':4, 'list': [{'a':5}]}]}" )
            .as( NestedBean.class );
        assertEquals( List.of( 3, 4 ),
            obj.list().viewAsList( e -> e.getNumber( "a" ) ).toList( JsonNumber::intValue ) );
        assertEquals( 5, obj.list().get( 1 ).list().get( 0 ).a() );
    }

    interface DateBean extends JsonObject {

        JsonDate aNode();

        LocalDateTime aLocalDateTime();

        Date aDate();
    }

    @Test
    void testAccess_DateNode() {
        DateBean obj = JsonMixed.ofNonStandard( "{'aNode':'2000-01-01T00:00'}" ).as( DateBean.class );
        assertEquals( LocalDate.of( 2000, 1, 1 ).atStartOfDay(), obj.aNode().date() );
    }

    @Test
    void testAccess_DateLocalDateTime() {
        DateBean obj = JsonMixed.ofNonStandard( "{'aLocalDateTime':'2000-01-01T00:00'}" ).as( DateBean.class );
        assertEquals( LocalDate.of( 2000, 1, 1 ).atStartOfDay(), obj.aLocalDateTime() );
    }

    @Test
    void testAccess_Date() {
        DateBean obj = JsonMixed.ofNonStandard( "{'aDate':'2000-01-01T00:00'}" ).as( DateBean.class );
        Date expected = Date.from( LocalDate.of( 2000, 1, 1 ).atStartOfDay().toInstant( ZoneOffset.UTC ) );
        assertEquals( expected, obj.aDate() );
    }

    @Test
    void testAccess_DateTimestamp() {
        DateBean obj = JsonMixed.ofNonStandard( "{'aDate':946684800000}" ).as( DateBean.class );
        Date expected = Date.from( LocalDate.of( 2000, 1, 1 ).atStartOfDay().toInstant( ZoneOffset.UTC ) );
        assertEquals( expected, obj.aDate() );
    }

    interface ListBean extends JsonObject {

        List<String> names();

        List<String> names( List<String> v );

        List<Integer> ages();

        List<List<Boolean>> flags();

        List<ListBean> recursive();

        Iterable<Integer> numbers();
    }

    @Test
    void testAccess_ListNull() {
        assertNull( JsonMixed.of( "{}" ).as( ListBean.class ).names() );
        assertNull( JsonMixed.ofNonStandard( "{'names':null}" ).as( ListBean.class ).names() );
        assertEquals( List.of(), JsonMixed.of( "{}" ).as( ListBean.class ).names( List.of() ) );
    }

    @Test
    void testAccess_ListEmpty() {
        assertEquals( List.of(), JsonMixed.ofNonStandard( "{'names':[]}" ).as( ListBean.class ).names() );
    }

    @Test
    void testAccess_ListString() {
        assertEquals( List.of( "foo", "bar" ),
            JsonMixed.ofNonStandard( "{'names':['foo','bar']}" ).as( ListBean.class ).names() );
    }

    @Test
    void testAccess_ListInteger() {
        assertEquals( List.of( 1, 2, 3 ), JsonMixed.ofNonStandard( "{'ages':[1,2,3]}" ).as( ListBean.class ).ages() );
    }

    @Test
    void testAccess_ListListBoolean() {
        ListBean obj = JsonMixed.ofNonStandard( "{'flags':[[true, false],[true]]}" ).as( ListBean.class );
        assertEquals( List.of( List.of( true, false ), List.of( true ) ), obj.flags() );
    }

    @Test
    void testAccess_ListExtendedObject() {
        //language=json
        String json = """
            {"ages":[1,2,3],
            "flags":[[true, false],[true]],
            "recursive": [{"names": ["x","y"]}]
            }""";
        ListBean obj = JsonMixed.of( json ).as( ListBean.class );
        assertEquals( 1, obj.recursive().size() );
        assertEquals( List.of( "x", "y" ), obj.recursive().get( 0 ).names() );
    }

    @Test
    void testAccess_IteratorIsList() {
        ListBean obj = JsonMixed.ofNonStandard( "{'numbers':[1,2,3]}" ).as( ListBean.class );
        assertEquals( List.of( 1, 2, 3 ), obj.numbers() );
    }

    interface SetBean extends JsonObject {

        Set<Integer> ages();

        Set<Integer> ages( Set<Integer> v );

        Set<Set<TextStyle>> styles();

        Set<SetBean> recursive();
    }

    @Test
    void testAccess_SetNull() {
        assertNull( JsonMixed.of( "{}" ).as( SetBean.class ).ages() );
        assertNull( JsonMixed.ofNonStandard( "{'ages':null}" ).as( SetBean.class ).ages() );
        assertEquals( Set.of(), JsonMixed.of( "{}" ).as( SetBean.class ).ages( Set.of() ) );
    }

    @Test
    void testAccess_SetEmpty() {
        assertEquals( Set.of(), JsonMixed.ofNonStandard( "{'ages':[]}" ).as( SetBean.class ).ages() );
    }

    @Test
    void testAccess_SetInteger() {
        assertEquals( Set.of( 1, 2, 3 ), JsonMixed.ofNonStandard( "{'ages':[1,2,3,3]}" ).as( SetBean.class ).ages() );
    }

    @Test
    void testAccess_SetSetEnum() {
        SetBean obj = JsonMixed.ofNonStandard( "{'styles':[['FULL', 'SHORT'], ['NARROW']]}" ).as( SetBean.class );
        assertEquals( Set.of( Set.of( TextStyle.FULL, TextStyle.SHORT ), Set.of( TextStyle.NARROW ) ), obj.styles() );
    }

    interface MapBean extends JsonObject {

        Map<String, TextStyle> styles();

        Map<String, TextStyle> styles( Map<String, TextStyle> v );

        Map<String, Map<String, String>> messages();

        Map<String, Character> digits();

        Map<TextStyle, List<String>> argsByType();

        Map<Integer, MapBean> recursive();
    }

    @Test
    void testAccess_MapNull() {
        assertNull( JsonMixed.of( "{}" ).as( MapBean.class ).styles() );
        assertNull( JsonMixed.ofNonStandard( "{'styles':null}" ).as( MapBean.class ).styles() );
        assertEquals( Map.of(), JsonMixed.of( "{}" ).as( MapBean.class ).styles( Map.of() ) );
    }

    @Test
    void testAccess_MapEmpty() {
        assertEquals( Map.of(), JsonMixed.ofNonStandard( "{'styles':[]}" ).as( MapBean.class ).styles() );
    }

    @Test
    void testAccess_MapEnumValues() {
        MapBean obj = JsonMixed.ofNonStandard( "{'styles': {'a': 'FULL', 'b': 'SHORT'}}" ).as( MapBean.class );
        assertEquals( Map.of( "a", TextStyle.FULL, "b", TextStyle.SHORT ), obj.styles() );
    }

    @Test
    void testAccess_MapMapStringValues() {
        MapBean obj = JsonMixed.ofNonStandard( "{'messages': {'a':{'hello':'world'}, 'b':{}}}" ).as( MapBean.class );
        assertEquals( Map.of( "a", Map.of( "hello", "world" ), "b", Map.of() ), obj.messages() );
    }

    @Test
    void testAccess_MapCharacterValues() {
        MapBean obj = JsonMixed.ofNonStandard( "{'digits':{'A':'1', 'B':'2'}}" ).as( MapBean.class );
        assertEquals( Map.of( "A", '1', "B", '2' ), obj.digits() );
    }

    @Test
    void testAccess_MapEnumKeys() {
        MapBean obj = JsonMixed.ofNonStandard( "{'argsByType': {'FULL': ['hey', 'ho'], 'SHORT': ['lets', 'go']}}" )
            .as( MapBean.class );
        assertEquals( Map.of( TextStyle.FULL, List.of( "hey", "ho" ), TextStyle.SHORT, List.of( "lets", "go" ) ),
            obj.argsByType() );
    }

    @Test
    void testAccess_MapExtendedObjectValues() {
        //language=json
        String json = """
            {"recursive":{"1":{}, "2":{"recursive":{"3":{"digits":{"a":"A"}}}}}}""";
        MapBean obj = JsonMixed.of( json ).as( MapBean.class );
        assertEquals( 2, obj.recursive().size() );
        assertTrue( obj.recursive().get( 1 ).isEmpty() );
        MapBean two = obj.recursive().get( 2 );
        assertEquals( 1, two.size() );
        assertEquals( Map.of( "a", 'A' ), two.recursive().get( 3 ).digits() );
    }

    interface StreamBean extends JsonValue {

        Stream<Integer> numbers();

        Stream<List<String>> lists();

        Iterator<String> names();
    }

    @Test
    void testAccess_StreamOfNumbers() {
        StreamBean obj = JsonMixed.ofNonStandard( "{'numbers':[1,2,3]}" ).as( StreamBean.class );
        assertEquals( Stream.of( 1, 2, 3 ).collect( toList() ), obj.numbers().collect( toList() ) );
    }

    @Test
    void testAccess_StreamOfListOfStrings() {
        StreamBean obj = JsonMixed.ofNonStandard( "{'lists':[['a','b'],['1','2']]}" ).as( StreamBean.class );
        assertEquals( List.of( List.of( "a", "b" ), List.of( "1", "2" ) ), obj.lists().collect( toList() ) );
    }

    @Test
    void testAccess_IteratorOfStrings() {
        StreamBean obj = JsonMixed.ofNonStandard( "{'names': ['Tom', 'Mary']}" ).as( StreamBean.class );
        List<String> actual = new ArrayList<>();
        obj.names().forEachRemaining( actual::add );
        assertEquals( List.of( "Tom", "Mary" ), actual );
    }

    interface OptionalBean extends JsonValue {

        Optional<String> maybeString();

        Optional<List<String>> maybeList();
    }

    @Test
    void testAccess_OptionalEmpty() {
        assertEquals( Optional.empty(), JsonMixed.of( "{}" ).as( OptionalBean.class ).maybeString() );
        assertEquals( Optional.empty(),
            JsonMixed.ofNonStandard( "{'maybeString':null}" ).as( OptionalBean.class ).maybeString() );
    }

    @Test
    void testAccess_OptionalString() {
        OptionalBean obj = JsonMixed.ofNonStandard( "{'maybeString':'hello'}" ).as( OptionalBean.class );
        assertEquals( "hello", obj.maybeString().orElse( "not" ) );
    }

    @Test
    void testAccess_OptionalListString() {
        OptionalBean obj = JsonMixed.ofNonStandard( "{'maybeList':['hello']}" ).as( OptionalBean.class );
        assertEquals( List.of( "hello" ), obj.maybeList().orElse( List.of() ) );
    }

    @Test
    void testAccess_ListsAreCached() {
        //language=json
        String json = """
            {
            "names":["John", "Paul", "Ringo"],
            "ages":[1,2,3],
            "flags":[[true],[false]]
            }""";
        ListBean obj = JsonMixed.of( json ).as( ListBean.class );

        assertFalse( obj.isAccessCached() );
        assertNotSame( obj.names(), obj.names() );
        ListBean cached = obj.withAccessCached().as( ListBean.class );
        assertTrue( cached.isAccessCached() );
        assertSame( cached.names(), cached.names() );
        assertEquals( List.of( "John", "Paul", "Ringo" ), cached.names() );
        // some more tests of the same
        assertSame( cached.ages(), cached.ages() );
        assertSame( cached.flags(), cached.flags() );
        assertSame( cached.flags().get( 0 ), cached.flags().get( 0 ) );
    }

    @Test
    void testAccess_ObjectsAreCached() {
        SetBean obj = JsonMixed.ofNonStandard( "{'recursive': [{'ages':[1,2,3]}]}" ).withAccessCached()
            .as( SetBean.class );
        assertTrue( obj.isAccessCached() );
        assertSame( obj.recursive().iterator().next().ages(), obj.recursive().iterator().next().ages() );

        NestedBean obj2 = JsonMixed.ofNonStandard( "{'list':[{'b':{},'a':5}]}" ).withAccessCached()
            .as( NestedBean.class );
        assertSame( obj2.list(), obj2.list() );
        assertTrue( obj2.isAccessCached() );
        assertSame( obj2.list().get( 0 ).getB(), obj2.list().get( 0 ).getB() );
        assertTrue( obj2.list().get( 0 ).isAccessCached() );
    }

    @Test
    void testAccess_StreamsAreNeverCached() {
        StreamBean obj = JsonMixed.ofNonStandard( "{'numbers':[1,2,3], 'names':['Tim', 'Tom']}" ).withAccessCached()
            .as( StreamBean.class );

        assertTrue( obj.isAccessCached() );
        assertNotSame( obj.numbers(), obj.numbers() );
        assertNotSame( obj.names(), obj.names() );
    }

    interface UncachedBean extends JsonPrimitive {

        JsonNumber n();

        Long time();

        UncachedBean next();

        JsonList<UncachedBean> list();

        List<UncachedBean> list2();
    }

    @Test
    void testAccess_PrimitivesAreNeverCached() {
        //language=json
        String json = """
            {"n":42, "time": 123456789, "next":{"n":2}, "list":[{"n":3}], "list2":[{"n":4}]}""";
        UncachedBean obj = JsonMixed.of( json ).withAccessCached().as( UncachedBean.class );

        assertTrue( obj.isAccessCached() );
        assertNotSame( obj.n(), obj.n() );
        assertNotSame( obj.time(), obj.time() );
        assertNotSame( obj.next(), obj.next() );

        // in contrast:
        assertSame( obj.list(), obj.list() );
        // JsonList is still lazy => not cached
        assertNotSame( obj.list().get( 0 ), obj.list().get( 0 ) );

        assertSame( obj.list2(), obj.list2() );
        // List is not lazy, elements are part of the cached List
        assertSame( obj.list2().get( 0 ), obj.list2().get( 0 ) );
    }
}
