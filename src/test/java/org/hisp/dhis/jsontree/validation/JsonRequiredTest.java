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
package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.JsonPathException;
import org.hisp.dhis.jsontree.JsonTreeException;
import org.hisp.dhis.jsontree.Required;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.hisp.dhis.jsontree.validation.Assertions.assertValidationError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link Required} annotation feature.
 *
 * @author Jan Bernitt
 */
class JsonRequiredTest {

    public interface JsonFoo extends JsonObject {

        @Required
        default String getBar() {
            return getString( "bar" ).string();
        }
    }

    public interface JsonEntry extends JsonObject {

        @Required
        default String getKey() {
            return getString( "key" ).string();
        }

        @Required
        default Number getValue() {
            return getNumber( "value" ).number();
        }
    }

    public interface JsonRoot extends JsonObject {

        @Required
        default JsonFoo getA() {
            return get( "a", JsonFoo.class );
        }

        @Required
        default JsonFoo getB() {
            return get( "b", JsonFoo.class );
        }
    }

    @Test
    void testIsA() {
        Assertions.assertTrue( JsonMixed.ofNonStandard( "{'bar':'x'}" ).isA( JsonFoo.class ) );
        JsonMixed val = JsonMixed.ofNonStandard( "{'key':'x', 'value': 1}" );
        JsonEntry e = val.as( JsonEntry.class );
        assertTrue( val.isA( JsonEntry.class ) );
        JsonMixed both = JsonMixed.ofNonStandard( "{'key':'x', 'value': 1, 'bar':'y'}" );
        assertTrue( both.isA( JsonFoo.class ) );
        assertTrue( both.isA( JsonEntry.class ) );
    }

    @Test
    void testIsA_MissingMember() {
        assertFalse( JsonMixed.ofNonStandard( "{'bar':'x'}" ).isA( JsonEntry.class ) );
        assertFalse( JsonMixed.ofNonStandard( "{'key':'x', 'value': 1}" ).isA( JsonFoo.class ) );
    }

    @Test
    void testIsA_WrongNodeType() {
        assertFalse( JsonMixed.ofNonStandard( "{'bar':true}" ).isA( JsonFoo.class ) );
        assertFalse( JsonMixed.ofNonStandard( "{'key':'x', 'value': '1'}" ).isA( JsonEntry.class ) );
    }

    @Test
    void testIsA_NotAnObject() {
        assertFalse( JsonMixed.of( "[]" ).isA( JsonEntry.class ) );
        assertFalse( JsonMixed.ofNonStandard( "'test'" ).isA( JsonEntry.class ) );
        assertFalse( JsonMixed.of( "12" ).isA( JsonEntry.class ) );
        assertFalse( JsonMixed.of( "false" ).isA( JsonEntry.class ) );
        assertFalse( JsonMixed.of( "null" ).isA( JsonEntry.class ) );
    }

    @Test
    void testAsObject() {
        assertAsObject( JsonFoo.class, "{'bar': 'yes'}" );
        assertAsObject( JsonEntry.class, "{'key':'x', 'value': 42}" );
        String both = "{'key':'x', 'value': 1, 'bar':'y'}";
        assertAsObject( JsonFoo.class, both );
        assertAsObject( JsonEntry.class, both );
    }

    @Test
    void testAsObject_NotAnObjectArray() {
        JsonMixed obj = JsonMixed.of( "[]" );
        JsonTreeException ex = assertThrowsExactly( JsonTreeException.class, () -> obj.asA( JsonFoo.class ) );
        assertEquals( "Value $ JsonFoo node is not an object but a ARRAY", ex.getMessage() );
    }

    @Test
    void testAsObject_NotAnObjectString() {
        JsonMixed obj = JsonMixed.of( "\"nop\"" );
        JsonTreeException ex = assertThrowsExactly( JsonTreeException.class, () -> obj.asA( JsonFoo.class ) );
        assertEquals( "Value $ JsonFoo node is not an object but a STRING", ex.getMessage() );
    }

    @Test
    void testAsObject_NotAnObjectNumber() {
        JsonMixed obj = JsonMixed.of( "13" );
        JsonTreeException ex = assertThrowsExactly( JsonTreeException.class, () -> obj.asA( JsonFoo.class ) );
        assertEquals( "Value $ JsonFoo node is not an object but a NUMBER", ex.getMessage() );
    }

    @Test
    void testAsObject_NotAnObjectBoolean() {
        JsonMixed obj = JsonMixed.of( "true" );
        JsonTreeException ex = assertThrowsExactly( JsonTreeException.class, () -> obj.asA( JsonFoo.class ) );
        assertEquals( "Value $ JsonFoo node is not an object but a BOOLEAN", ex.getMessage() );
    }

    @Test
    void testAsObject_NotAnObjectNull() {
        JsonMixed obj = JsonMixed.of( "null" );
        JsonTreeException ex = assertThrowsExactly( JsonTreeException.class, () -> obj.asA( JsonFoo.class ) );
        assertEquals( "Value $ JsonFoo node is not an object but a NULL", ex.getMessage() );
    }

    @Test
    void testAsObject_NotAnObjectUndefined() {
        JsonObject obj = JsonMixed.of( "{}" ).getObject( "x" );
        JsonPathException ex = assertThrowsExactly( JsonPathException.class, () -> obj.asA( JsonFoo.class ) );
        assertEquals( "Value $.x JsonFoo node does not exist", ex.getMessage() );
    }

    @Test
    void testAsObject_MissingMemberUndefined() {
        String json = """
            {"b":{"bar":""}}""";
        Validation.Error error = assertValidationError( json, JsonRoot.class, Rule.REQUIRED, "a" );
        assertEquals( "$.a", error.path() );
    }

    @Test
    void testAsObject_MissingMemberRecursive() {
        String json = """
            {"a": {}, "b":{"bar":""}}""";
        Validation.Error error = assertValidationError( json, JsonRoot.class, Rule.REQUIRED, "bar" );
        assertEquals( "$.a.bar", error.path() );
    }

    @Test
    void testAsObject_NotAnObjectRecursive() {
        String json = """
            {"a": [], "b":{"bar":""}}""";
        JsonMixed obj = JsonMixed.of( json );
        assertValidationError( json, JsonRoot.class, Rule.TYPE, Set.of( NodeType.OBJECT ), NodeType.ARRAY );
    }

    private static void assertAsObject( Class<? extends JsonObject> of, String actualJson ) {
        JsonObject obj = JsonMixed.ofNonStandard( actualJson ).asA( of );
        assertNotNull( obj );
        assertTrue( of.isInstance( obj ) );
    }
}
