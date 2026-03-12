package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class JsonSerializationTest {

    @Test
    void testSerializationWithProxy() throws IOException, ClassNotFoundException {
        JsonNode rootT0 = JsonNode.of( "{ \"key\": \"value\" }" );
        JsonNode valueT0 = rootT0.get( ".key" );
        JsonMixed rootT0Api = rootT0.lift( JsonAccess.GLOBAL ).as( JsonMixed.class );
        JsonValue valueT0Api = rootT0Api.get( "key" );

        JsonNode rootT1 = serialisationRoundTrip( rootT0 );
        JsonNode valueT1 = serialisationRoundTrip( valueT0 );
        JsonMixed rootT1Api = serialisationRoundTrip( rootT0Api );
        JsonValue valueT1Api = serialisationRoundTrip( valueT0Api );

        assertEquals(rootT0.getDeclaration(), rootT1.getDeclaration(),
                "The JSON should be identical");
        assertEquals( valueT0, valueT1 );
        assertEquals( valueT0.getParent(), valueT1.getParent() );

        assertEquals( rootT0Api, rootT1Api );
        assertEquals( rootT0Api.get( "key" ), rootT1Api.get( "key" ) );
        assertEquals( valueT0Api, valueT1Api );

        assertNotSame(rootT0, rootT1,
                "Deserialized object should be a new instance");
    }

    @SuppressWarnings( "unchecked" )
    private static <T> T serialisationRoundTrip( T before )
        throws IOException, ClassNotFoundException {
        byte[] serializedData;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject( before );
            serializedData = baos.toByteArray();
        }
        T after;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(serializedData);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            after = (T) ois.readObject();
        }
        return after;
    }
}