package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * Can be implemented by objects that have a {@link Text} form.
 *
 * @author Jan Bernitt
 * @since 1.9
 */
@FunctionalInterface
public interface Textual {

  /**
   * @return the {@link Object#toString()} equivalent for {@link Text}, never null
   */
  @NotNull
  Text textValue();
}
