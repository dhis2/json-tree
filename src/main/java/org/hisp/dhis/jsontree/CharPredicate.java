package org.hisp.dhis.jsontree;

/**
 * Test if a character satisfies a test.
 *
 * @author Jan Bernitt
 * @since 1.13
 */
public interface CharPredicate {

  /**
   * @param ch the character to test
   * @return true if the given character satisfies this test, false otherwise
   */
  boolean test(char ch);
}
