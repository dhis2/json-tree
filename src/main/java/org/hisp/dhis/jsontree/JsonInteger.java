package org.hisp.dhis.jsontree;

import static org.hisp.dhis.jsontree.Validation.NodeType.INTEGER;

/**
 * For numbers that always should be integers.
 * <p>
 * The main reason this exists is to benefit from more exact type validation.
 *
 * @author Jan Bernitt
 * @since 0.11
 */
@Validation( type = INTEGER )
@Validation.Ignore
public interface JsonInteger extends JsonNumber {
}
