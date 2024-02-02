package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonReaderTest {

    @Test
    void testReader_Mixed() {
        String json = """
            {"key": "😆"}""";
        assertEquals( json, JsonMixed.of( new StringReader( json ) ).toJson() );
    }

    @Test
    void testReader_Value() {
        String json = """
            {"key": "😆"}""";
        assertEquals( json, JsonValue.of( new StringReader( json ) ).toJson() );
    }

    @Test
    void testFile_Mixed() {
        String json = """
            { "min": -1.0e+28, "max": 1.0e+28 }""";
        assertEquals( json, JsonMixed.of( Path.of("src/test/resources/suite/y_object_extreme_numbers.json") ).toJson() );
    }

    @Test
    void testFile_Value() {
        String json = """
            { "min": -1.0e+28, "max": 1.0e+28 }""";
        assertEquals( json, JsonValue.of( Path.of("src/test/resources/suite/y_object_extreme_numbers.json") ).toJson() );
    }
}
