package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextTest {

    @Test
    void testOf_IndexCache() {
        for (int i = 0; i < 1000; i++) {
            assertEquals( String.valueOf( i ), Text.of( i ).toString() );
        }
    }

    @Test
    void testOf_IndexFallback() {
        for (int i : List.of(234567, 78943)) {
            assertEquals( String.valueOf( i ), Text.of( i ).toString() );
        }
    }

    @Test
    void testHashCode_Indexes() {
        Set<Integer> hashes = new HashSet<>(1000);
        for (int i = 0; i < 1000; i++) {
            hashes.add( Text.of( i ).hashCode() );
        }
        assertEquals( 1000, hashes.size() );
    }
}
