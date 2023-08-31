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
package org.hisp.dhis.jsontree;

import static org.hisp.dhis.jsontree.JsonSchema.NodeType.BOOLEAN;

/**
 * Represents a boolean JSON node.
 *
 * @author Jan Bernitt
 */
@Validation( type = BOOLEAN )
public interface JsonBoolean extends JsonPrimitive {

    /**
     * @return boolean value of the property or {@code null} when this property is undefined or defined as JSON
     * {@code null}.
     */
    Boolean bool();

    /**
     * @param orDefault to use if this node is undefined or defined null
     * @return the boolean value of this node or the default if it is undefined or null
     * @throws JsonTreeException in case this node exist but is not a boolean node (or null)
     */
    default boolean booleanValue( boolean orDefault ) {
        return isUndefined() ? orDefault : booleanValue();
    }

    /**
     * Same as {@link #bool()} except that this throws an {@link JsonPathException} in case the value is not defined.
     *
     * @return true of false, nothing else
     * @throws JsonPathException when this value is not defined in the JSON content or defined JSON {@code null}.
     */
    default boolean booleanValue() {
        return mapNonNull( bool(), Boolean::booleanValue );
    }
}
