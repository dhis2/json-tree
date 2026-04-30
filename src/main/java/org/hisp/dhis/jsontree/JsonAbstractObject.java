package org.hisp.dhis.jsontree;

import static org.hisp.dhis.jsontree.Validation.NodeType.OBJECT;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.hisp.dhis.jsontree.internal.TerminalOp;
import org.hisp.dhis.jsontree.validation.JsonValidator;

/**
 * An "abstract" type that is expected to be backed by a JSON object.
 *
 * @since 0.11
 */
@Validation.Ignore
@Validation(type = OBJECT)
public interface JsonAbstractObject<E extends JsonValue> extends JsonAbstractCollection {

  /**
   * @param key property to access
   * @return value at the provided property
   * @since 1.9
   */
  E get(Text key);

  /**
   * A typed variant of {@link JsonObject#get(CharSequence)}, equivalent to {@link
   * JsonObject#get(CharSequence, Class)} where 2nd parameter is the type parameter E.
   *
   * @see #get(Text)
   */
  default E get(CharSequence key) {
    return get(Text.of(key));
  }

  /**
   * @param name of the object property
   * @return true if this node exist and has the property of the given name
   * @throws JsonTreeException in case this value exists but is not an JSON object
   * @since 1.9
   */
  @TerminalOp(canBeUndefined = true, mustBeObject = true)
  default boolean has(CharSequence name) {
    JsonNode node = nodeIfExists();
    return node != null && node().isMember(name);
  }

  /**
   * Test an object for property names.
   *
   * @param names a set of property names that should exist
   * @return true if this object has (at least) all the given names
   * @throws JsonTreeException in case this value exists but is not an JSON object
   */
  @TerminalOp(canBeUndefined = true, mustBeObject = true)
  default boolean has(CharSequence... names) {
    JsonNode node = nodeIfExists();
    if (node == null) return false;
    for (CharSequence name : names) if (!node.isMember(name)) return false;
    return true;
  }

  /**
   * Test an object for property names.
   *
   * @param names a set of property names that should exist
   * @return true if this object has (at least) all the given names
   * @throws JsonTreeException in case this value exists but is not an JSON object
   * @since 0.10
   */
  @TerminalOp(canBeUndefined = true, mustBeObject = true)
  default boolean has(Collection<? extends CharSequence> names) {
    JsonNode node = nodeIfExists();
    if (node == null) return false;
    for (CharSequence name : names) if (!node.isMember(name)) return false;
    return true;
  }

  /**
   * @param key the key or name to look up
   * @return true, if the provided key is defined, false otherwise
   * @since 0.11
   */
  @TerminalOp(canBeUndefined = true)
  default boolean containsKey(CharSequence key) {
    return !isUndefined(key);
  }

  /**
   * OBS! This does not require this node to be an object node.
   *
   * @param name name of the object member
   * @return true if this object does not have a member of the provided name
   * @since 0.11
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isUndefined(CharSequence name) {
    return get(name).isUndefined();
  }

  /**
   * Test if the object property is defined which includes being defined JSON {@code null}.
   *
   * @param name name of the object member
   * @return true if this object has a member of the provided name
   * @since 1.1
   */
  @TerminalOp(canBeUndefined = true)
  default boolean exists(CharSequence name) {
    return get(name).exists();
  }

  /**
   * Stream of the raw JSON object member names in order of declaration.
   * If keys re-occur in the JSON (duplicates) they also re-occur in the stream.
   * Use {@link Stream#distinct()} on the result to de-duplicate when needed.
   *
   * @return The keys of this map.
   * @throws JsonTreeException in case this node does exist but is not an object node
   * @see #names()
   * @since 0.11 (as Stream)
   */
  @TerminalOp(canBeUndefined = true, mustBeObject = true)
  default Streamable.Sized<Text> keys() {
    if (isUndefined() || isEmpty()) return Streamable.empty();
    return node().keys();
  }

  /**
   * @return a stream of map/object entries in order of their declaration.
   * @throws JsonTreeException in case this node does exist but is not an object node
   * @since 0.11
   */
  @TerminalOp(canBeUndefined = true, mustBeObject = true)
  default Streamable.Sized<E> entries() {
    if (isUndefined() || isEmpty()) return Streamable.empty();
    return node().keys().map(this::get);
  }

  /**
   * Lists raw JSON object member names in order of declaration.
   * If keys re-occur in the JSON (duplicates) they also re-occur in the list.
   *
   * @return The list of object member names in the order they were defined.
   * @throws JsonTreeException in case this node does exist but is not an object node
   * @see #keys()
   */
  @TerminalOp(canBeUndefined = true, mustBeObject = true)
  default List<String> names() {
    return keys().map(Text::toString).toList();
  }

  /**
   * @return a stream of the absolute paths of the map/object members in oder of their declaration
   * @throws JsonTreeException in case this node does exist but is not an object node
   * @since 1.2
   */
  @TerminalOp(canBeUndefined = true, mustBeObject = true)
  default Stream<JsonPath> paths() {
    return isUndefined() || isEmpty() ? Stream.empty() : node().paths().stream();
  }

  /**
   * @param action call with each entry in the map/object in order of their declaration
   * @throws JsonTreeException in case this node does exist but is not an object node
   * @since 0.10
   */
  @TerminalOp(canBeUndefined = true, mustBeObject = true)
  default void forEach(BiConsumer<Text, ? super E> action) {
    // need to use keys() + get(key) because of wrapper type E is not accessible otherwise
    // and to preserve the path of the values
    keys().forEach(key -> action.accept(key, get(key)));
  }

  /**
   * @param action called for each value in the map/object in order of their declaration
   * @throws JsonTreeException in case this node does exist but is not an object node
   * @since 0.11
   */
  @TerminalOp(canBeUndefined = true, mustBeObject = true)
  default void forEachValue(Consumer<E> action) {
    keys().forEach(name -> action.accept(get(name)));
  }

  /**
   * @see #validate(Class, Validation.Mode, Rule...)
   * @since 0.11
   **/
  default void validate(Class<?> schema, Rule... rules) {
    validate(schema, Validation.Mode.FAIL_ALL, rules);
  }

  /**
   * @param schema the schema to validate against
   * @param mode check, fail fast or give a breakdown of all issues?
   * @param rules optional set of {@link Rule}s to check, empty includes all
   * @return Validation result if {@link Validation.Mode} is probing (or if
   *     there were no errors)
   * @throws JsonSchemaException in case this value does not match the given schema
   * @throws IllegalArgumentException in case the given schema is not an interface
   * @since 1.9
   */
  @TerminalOp(canBeUndefined = true, mustBeObject = true)
  default Validation.Result validate(Class<?> schema, Validation.Mode mode, Rule... rules) {
    return JsonValidator.validate(this, schema, mode, rules);
  }
}
