/*
 * Copyright (c) 2004-2022, University of Oslo
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * The {@link JsonTypedAccessStore} is a registry for accessor functions.
 * <p>
 * These come in a simple form {@link JsonTypedAccessor} for non-generic types, and a generic form
 * {@link JsonGenericTypedAccessor} for more complicated types.
 * <p>
 * Conceptually accessors are the POJO "mappers" of this library. Just that instead of using POJOs data structures are
 * defined by their "getters" in an interface. The accessors then bridge the gap between the view the generic JSON tree
 * provided in form of {@link JsonValue}s and the non-JSON java types returned by getters.
 * <p>
 * While they "map" values this mapping takes place on access only. Everything before that is just as virtual (a view)
 * as the {@link JsonValue} tree.
 * <p>
 * One could also think of accessors as "automatic" implementation of an abstract method as if it became a default
 * method in an interface. The "implementation" here is derived from the return type of the method. Each accessor knows
 * how to access and map to a particular java tye. The store then contains the set of known java target types and their
 * way to access them given a {@link JsonValue} tree.
 *
 * @author Jan Bernitt
 * @since 0.4
 */
public interface JsonTypedAccessStore {

    /**
     * A function that given a parent object knows how to access a value at a certain path as a certain type.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    interface JsonGenericTypedAccessor<T> {

        /**
         * Accesses value at the path as the target type.
         *
         * @param from  the parent object containing the value at path
         * @param path  path to access the value
         * @param to    fully generic target type for the value of type {@code T}
         * @param store in case further conversions are needed
         * @return the value at path converted to target type
         */
        T access( JsonObject from, String path, Type to, JsonTypedAccessStore store );
    }

    /**
     * A simplified version of a {@link JsonGenericTypedAccessor} that only requires a parent and a path.
     * <p>
     * This usually is sufficient for simple Java types like strings, numbers and so forth.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    interface JsonTypedAccessor<T> extends JsonGenericTypedAccessor<T> {

        /**
         * Accesses value the path as the type implicitly assumed by this accessor.
         *
         * @param from the parent object containing that value at path
         * @param path path to access the value
         * @return the value at path with the type implicitly assumed by this accessor.
         */
        T access( JsonObject from, String path );

        @Override
        default T access( JsonObject from, String path, Type to, JsonTypedAccessStore store ) {
            return access( from, path );
        }
    }

    /**
     * Yields the accessor to use for the provided raw type.
     *
     * @param type the target type of access, if generic {@link Type} is a {@link ParameterizedType} this should be the
     *             raw type
     * @param <T>  type of the value provided by the returned accessor
     * @return the accessor to use, or {@code null} if no accessor is known for the given type
     */
    <T> JsonGenericTypedAccessor<T> accessor( Class<T> type );

}
