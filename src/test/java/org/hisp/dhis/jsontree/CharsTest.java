package org.hisp.dhis.jsontree;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the {@link Chars} utility, mainly the file IO and {@link java.nio.charset.Charset} decoding.
 */
class CharsTest {

    @TempDir
    private Path tempDir;

    //language=json
    private static final String UNICODE_EXAMPLE = """
            {
                "greetings": [
                    "Hello World",
                    "你好，世界",
                    "Привет, мир",
                    "🌍🌎🌏",
                    "Γειά σου Κόσμε",
                    "नमस्ते दुनिया",
                    "こんにちは世界",
                    "안녕하세요 세계"
                ]
            }""";

    //language=json
    private static final String ISO_EXAMPLE = """
            {
                "greetings": [
                    "Hello World",
                    "Hallo Welt"
                ]
            }""";

    @Test
    void testFrom_NoBOM() throws IOException {
        Path file = tempDir.resolve("without_bom.json");

        Files.writeString(file, UNICODE_EXAMPLE, UTF_8, StandardOpenOption.CREATE);

        assertContentEquals( UNICODE_EXAMPLE, Chars.from(file, UTF_8));
    }

    @Test
    void testFrom_BOM() throws IOException {
        Path file = tempDir.resolve("with_bom.json");
        writeContentWithBOM( file );

        assertContentEquals( UNICODE_EXAMPLE, Chars.from(file, UTF_8));
    }

    @Test
    void testFrom_ISO() throws IOException {
        Path file = tempDir.resolve("with_bom.json");

        Files.writeString(file, ISO_EXAMPLE, ISO_8859_1, StandardOpenOption.CREATE);

        assertContentEquals( ISO_EXAMPLE, Chars.from(file, ISO_8859_1));
    }

    @Test
    void testTextOf_BOM() throws IOException {
        Path file = tempDir.resolve("with_bom.json");
        writeContentWithBOM( file );

        assertEquals( UNICODE_EXAMPLE, Text.of(file, UTF_8).toString());
    }

    private static void writeContentWithBOM( Path file )
        throws IOException {
        byte[] contentBytes = CharsTest.UNICODE_EXAMPLE.getBytes( UTF_8);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] bytesWithBom = new byte[bom.length + contentBytes.length];
        System.arraycopy(bom, 0, bytesWithBom, 0, bom.length);
        System.arraycopy(contentBytes, 0, bytesWithBom, bom.length, contentBytes.length);
        Files.write( file, bytesWithBom, StandardOpenOption.CREATE);
    }

    private static void assertContentEquals(String expected, char[] actual) {
        String actualStr = new String( actual );
        // trim is used because the method we use might add spaces at the end
        // which for JSON is fine
        assertEquals( expected, actualStr.trim());
        // lets also check the JSON part
        JsonObject expectedGreetings = JsonMixed.of( expected );
        JsonObject actualGreetings = JsonMixed.of( Text.of( actual, 0, actual.length ) );
        for (int i = 0; i < 8; i++)
            assertEquals( expectedGreetings.getArray( "greetings" ).getString( i ).text(),
                actualGreetings.getArray( "greetings" ).getString( i ).text() );
    }

}