# API reference

## Entry points

- `Json.of(...)` — build a tree from a Java value.
- `Json.array(...)` / `Json.object(...)` — builder-style composition.
- `JsonMixed.of(String)` — wrap a JSON document.
- `JsonValue.of(String)` — same, typed as the bottom type.
- `Json5.of(...)` — wrap a JSON5 document (JSON5 is normalized to JSON).

## Basic types

`JsonBoolean`, `JsonNumber`, `JsonInteger`, `JsonString`, `JsonArray`,
`JsonObject` — plus `JsonMixed` (top type) and `JsonValue` (bottom type).

The library has both a top and a bottom type which allows convenient working
with nodes that can take multiple forms.

## Accessors
Some of the key methods to work with the virtual tree API:

- `exists()`, `isUndefined()`, `isNull()` — see
  [Null, undefined and existence](null-and-undefined.md).
- `isRoot()`, `path()` — path in tree checks and access
- `isNumber()`, `isInteger()`, `isNaN()`, `isInfinity()` — number-value type checks
- `isString()`, , `isBoolean()`, `isSimple()`, `type()`, more value-level type checks
- `string()`, `text()`, `charValue()` — text-value access
- `integer()`, `number()` — value-level access (+ primitives `intValue()`, ...)
- `bool()`, `booleanValue()` — boolean-value access
- `get(key)`, `entries()` — object-key access (+ many more typed and utility methods)
- `get(int)`, `values()` — array-index access (+ many more typed and utility methods including primitive streams)

Furthermore:

- The library seamlessly supports `NaN`, and `Infinity`+`-Infinity` handling.
  In JSON text they use a string but in Java we can receive them as `double`.
- Primitive streams of JSON arrays do not cause wrappers and are directly mapped from the JSON input.
- Both `JsonArray` and `JsonObject` are `Iterable` for their elements.
- Any `JsonValue` is also a JDK `Map.Entry` with the key being their `path()` tail segment 
  which coincides with the key (property name) in their parent object.

## Casting and validation

While the virtual tree allows to view nodes as any shape we can
test and assert specific shapes given as their Java interface type.

- `as(Class<T>)` — view as a user type.
- `validate(Class<T>)` — check against a user type.
- `isA(Class<T>)` / `asA(Class<T>)` — checking and asserting shape conformity 
- `@Validation`, `@Required`, `Rule` — see [Validation](validation.md).

## Diff

JSON trees can be compared to find the differences. 
This is exceptionally useful in test assertions or validating input.

- `diff(JsonValue)` / `diff(JsonValue, JsonDiff.Mode)` —
  see [Comparing JSON values](diff.md).
- `JsonDiff.Mode`, `@JsonDiff.AnyOrder`, `@JsonDiff.AnyAdditional`.

## Related

- [Java API](java-api.md)
- [JSON API](json-api.md)