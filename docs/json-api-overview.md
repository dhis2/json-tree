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
  [Null, undefined and existence](intro-5-null-and-undefined).
- `isRoot()`, `path()` — path in tree checks and access
- `isNumber()`, `isInteger()`, `isNaN()`, `isInfinity()` — number-value type checks
- `isString()`, , `isBoolean()`, `isSimple()`, `type()`, more value-level type checks
- `string()`, `text()`, `charValue()` — text-value access
- `integer()`, `number()` — value-level access (+ primitives `intValue()`, ...)
- `bool()`, `booleanValue()` — boolean-value access
- `get(key)`, `entries()` — object-key access (+ many more typed and utility methods)
- `get(int)`, `values()` — array-index access (+ many more typed and utility methods including primitive streams)

Noteworthy:

- The library seamlessly supports `NaN`, and `Infinity`+`-Infinity` handling.
  In JSON text they use a string but in Java we can receive them as `double`.
- Primitive streams of JSON arrays do not cause wrappers and are directly mapped from the JSON input.
- Both `JsonArray` and `JsonObject` are `Iterable` for their elements.
- Any `JsonValue` is also a JDK `Map.Entry` with the key being their `path()` tail segment 
  which coincides with the key (property name) in their parent object.

## Query

- `JsonSelector` — `$`, `AT`, `of(String)`, `of(JsonNodeType)`, `of(Text)`,
  `of(int)`, `of(int[])`, `of(int, int[, int])`, `of(Predicate)`, `ofAny()`,
  `ofDescendants()`.
- Fluent: `.key`, `.index`, `.indexes`, `.any`, `.descendants`, `.slice`,
  `.type`, `.filter`, `.find`.
- `JsonSelectable` — `query(selector, matches)`, `query`, `queryCount`,
  `queryExists`, `queryFirst`, `queryReduce`, `queryTop`.
- See [Querying the tree](json-search).
 

## Validation

While the virtual tree allows to view nodes as any shape we can
test and assert specific shapes given as their Java interface type.

- `as(Class<T>)` — view as type (not checked, always works).
- `isA(Class<T>)` / `asA(Class<T>)` — checking and asserting shape conformity.
- `validate(Class<T>)` — check against a user type.
- `@Validation`, `@Required`, `Rule` — see [Validation](json-validation). 

Full attribute list and semantics: see [Validation reference](json-validation-reference).

## Diff

JSON can be compared to find the differences between two trees. 
This is exceptionally useful in test assertions or input validation.

- `diff(JsonValue)` / `diff(JsonValue, JsonDiff.Mode)` —
  see [Comparing JSON values](json-diff).
- `JsonDiff.Mode`, `@JsonDiff.AnyOrder`, `@JsonDiff.AnyAdditional`.

## Patch

Patch do bulk manipulation via change operations resulting in a new patched JSON text.

- `JsonValue.patch(JsonPatch)` — apply a patch to a node.
- `JsonPatch` — the patch document; obtainable via `JsonMixed.of(...).as(JsonPatch.class)`.
- Operation names and aliases, path segment types, and the `test` field are
  covered in [JSON Patch](json-patch.md).

See also [Standards and RFCs](rfc-reference.md#json-patch--rfc-6902) for
conformance notes.

## Related

- [Java API](intro-4-json-from-java)
- [JSON API](intro-3-json-from-text)
- [JSON Patch](json-patch.md) — the RFC 6902-based patch feature.