package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonStreamingTest {

    // ---------------------------------------------------------------------
    // Correctness: SKIP returns every element/member, no caching involved
    // ---------------------------------------------------------------------

    @Test
    void testElements_Skip() {
        JsonNode array = JsonMixed.of( "[1, 2, 3, 4, 5]" ).node();
        List<Integer> values = new ArrayList<>();
        array.elements( JsonNode.Index.SKIP ).forEach( n -> values.add( n.intValue() ) );
        assertEquals( List.of( 1, 2, 3, 4, 5 ), values );
    }

    @Test
    void testMembers_Skip() {
        JsonNode obj = JsonMixed.of( """
            {"a": 1, "b": 2, "c": 3}""" ).node();
        List<Integer> values = new ArrayList<>();
        obj.members( JsonNode.Index.SKIP ).forEach( n -> values.add( n.intValue() ) );
        assertEquals( List.of( 1, 2, 3 ), values );
    }

    @Test
    void testElements_Skip_NestedContainers() {
        JsonNode array = JsonMixed.of( """
            [{"id": 1}, {"id": 2}, {"id": 3}]""" ).node();
        List<Integer> ids = new ArrayList<>();
        array.elements( JsonNode.Index.SKIP )
            .forEach( n -> ids.add( n.member( Text.of("id") ).intValue() ) );
        assertEquals( List.of( 1, 2, 3 ), ids );
    }

    // ---------------------------------------------------------------------
    // Cache semantics: AUTO / CHECK / ADD / SKIP interact with the cache
    // ---------------------------------------------------------------------

    @Test
    void testAuto_CachesContainers_NotPrimitives() {
        // container elements: same instance on repeated AUTO access
        JsonNode containers = JsonMixed.of( """
            [{"x": 1}, {"x": 2}]""" ).node();
        JsonNode first  = containers.elements().iterator().next();
        JsonNode second = containers.elements().iterator().next();
        assertSame( first, second );

        // primitive elements: fresh instance each time
        JsonNode primitives = JsonMixed.of( "[1, 2, 3]" ).node();
        JsonNode p1 = primitives.elements().iterator().next();
        JsonNode p2 = primitives.elements().iterator().next();
        assertNotSame( p1, p2 );
    }

    @Test
    void testCheck_ReusesCacheWithoutGrowing() {
        JsonNode containers = JsonMixed.of( """
            [{"x": 1}, {"x": 2}, {"x": 3}]""" ).node();

        // warm the cache for element 0 with AUTO
        JsonNode cached = containers.elements().iterator().next();

        // CHECK finds the same instance
        JsonNode fromCheck = containers.elements( JsonNode.Index.CHECK ).iterator().next();
        assertSame( cached, fromCheck );

        // SKIP never looks at the cache: fresh node
        JsonNode fromSkip = containers.elements( JsonNode.Index.SKIP ).iterator().next();
        assertNotSame( cached, fromSkip );

        // ADD would return the same instance as the AUTO warm-up (additive)
        JsonNode fromAdd = containers.elements( JsonNode.Index.ADD ).iterator().next();
        assertSame( cached, fromAdd );
    }

    @Test
    void testAdd_ForcesCachingOfPrimitives() {
        JsonNode primitives = JsonMixed.of( "[1, 2, 3]" ).node();

        // first ADD access caches the node
        JsonNode first = primitives.elements( JsonNode.Index.ADD ).iterator().next();
        // second ADD access finds it in the cache
        JsonNode second = primitives.elements( JsonNode.Index.ADD ).iterator().next();
        assertSame( first, second );

        // SKIP keeps producing fresh instances even after caching
        JsonNode fromSkip = primitives.elements( JsonNode.Index.SKIP ).iterator().next();
        assertNotSame( first, fromSkip );
    }

    // ---------------------------------------------------------------------
    // Duplicate keys: caching modes observe the first occurrence only
    // ---------------------------------------------------------------------

    @Test
    void testMembers_DuplicateKeys_SkipVsAdd() {
        JsonNode dup = JsonMixed.of( """
            {"a": 1, "a": 2}""" ).node();

        // SKIP: both occurrences as declared
        List<String> skipped = new ArrayList<>();
        dup.members( JsonNode.Index.SKIP )
            .forEach( m -> skipped.add( m.getDeclaration().toString() ) );
        assertEquals( List.of( "1", "2" ), skipped );

        // ADD caches the first occurrence; iterating yields the cached node
        // again for the second key
        List<String> added = new ArrayList<>();
        dup.members( JsonNode.Index.ADD )
            .forEach( m -> added.add( m.getDeclaration().toString() ) );
        assertEquals( List.of( "1", "1" ), added );
    }

    @Test
    void testMembers_DuplicateKeys_AutoPrimitivesShowBoth() {
        // AUTO does not cache primitives, so no "first occurrence wins"
        // filtering happens during iteration
        JsonNode dup = JsonMixed.of( """
            {"a": 1, "a": 2}""" ).node();
        List<String> values = new ArrayList<>();
        dup.members().forEach( m -> values.add( m.getDeclaration().toString() ) );
        assertEquals( List.of( "1", "2" ), values );
    }

    @Test
    void testIndexResolution_TreeDefaultAffectsAuto() {
        // tree SKIP: an AUTO operation (no explicit Index) resolves to SKIP
        JsonNode tree = JsonNode.of( """
            [{"x": 1}, {"x": 2}]""", null, JsonNode.Index.SKIP );
        JsonNode first  = tree.elements().iterator().next();
        JsonNode second = tree.elements().iterator().next();
        assertNotSame( first, second );
    }

    @Test
    void testIndexResolution_ExplicitAddWinsOverSkipTree() {
        // tree SKIP: an explicit ADD at the operation level overrides the tree
        JsonNode tree = JsonNode.of( """
            [{"x": 1}, {"x": 2}]""", null, JsonNode.Index.SKIP );
        JsonNode first  = tree.elements( JsonNode.Index.ADD ).iterator().next();
        JsonNode second = tree.elements( JsonNode.Index.ADD ).iterator().next();
        assertSame( first, second );
    }

    @Test
    void testIndexResolution_AutoSkipFollowsTreeWhenTreeIsAdd() {
        // tree ADD: AUTO_SKIP (the tool preference) defers to ADD
        JsonNode tree = JsonNode.of( """
            [{"x": 1}, {"x": 2}]""", null, JsonNode.Index.ADD );
        JsonNode first  = tree.elements( JsonNode.Index.AUTO_SKIP ).iterator().next();
        JsonNode second = tree.elements( JsonNode.Index.AUTO_SKIP ).iterator().next();
        assertSame( first, second );
    }

    @Test
    void testIndexResolution_AutoSkipIsSkipWhenTreeIsAuto() {
        // tree AUTO: AUTO_SKIP resolves to SKIP (the tool's default preference)
        JsonNode tree = JsonNode.of( """
            [{"x": 1}, {"x": 2}]""", null, JsonNode.Index.AUTO );
        JsonNode first  = tree.elements( JsonNode.Index.AUTO_SKIP ).iterator().next();
        JsonNode second = tree.elements( JsonNode.Index.AUTO_SKIP ).iterator().next();
        assertNotSame( first, second );
    }
}