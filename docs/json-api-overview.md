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
  [Null, undefined and existence](intro-5-null-and-undefined.md).
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

## Entry points by operation

The library splits into two APIs — the *virtual* API (`JsonValue` /
`JsonMixed` and user types) and the *actual* tree API (`JsonNode`). Each
operation has an entry point in one or both. The full explanation of the
split is in [Lazy parsing and the virtual tree](intro-2-lazy-parsing.md);
this section is the index.

| Operation              | `JsonValue` / `JsonMixed`                                                                                                                                 | `JsonNode`                                                                                                                                                                  | Supporting types                                                             |
|------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| **Parse from text**    | `JsonMixed.of(CharSequence)`, `JsonValue.of(CharSequence)`                                                                                                | `JsonNode.of(CharSequence[, GetListener[, Index]])`, plus `Path` and `byte[]` variants                                                                                      | `JsonPath`, `JsonNode.GetListener`, `JsonNode.Index`                         |
| **Generate from Java** | `Json.of(...)`, `Json.array(...)`, `Json.object(...)`                                                                                                     | —                                                                                                                                                                           | `JsonBuilder`                                                                |
| **Read back**          | `toJson()` (returns `String`); type-specific accessors (`string()`, `intValue()`, …)                                                                      | `getDeclaration()` (returns `Text`), `textValue()`, `toString()`                                                                                                            | `Text`                                                                       |
| **Navigate**           | `get(...)`, `getString(...)`, `getNumber(...)`, `getArray(...)`, `getObject(...)`, … — never throw; missing values come back as an undefined virtual node | `get(...)`, `member(...)`, `element(...)` — throw `JsonPathException` when missing. `getIfExists(...)`, `memberIfExists(...)`, `elementIfExists(...)` return `null` instead | `JsonPath`, `Text`                                                           |
| **Stream / iterate**   | `values(Index)`, `entries(Index)` (typed level)                                                                                                           | `elements(Index)`, `members(Index)`, `values()`                                                                                                                             | `JsonNode.Index`                                                             |
| **Search**             | `query(...)`, `queryCount(...)`, `queryExists(...)`, `queryFirst(...)`, `queryReduce(...)`, `queryTop(...)`                                               | same, inherited from `JsonSelectable`                                                                                                                                       | `JsonSelector`, `JsonSelector.Matches`                                       |
| **Patch**              | `patch(JsonPatch)`                                                                                                                                        | `patch(List<? extends JsonNodeOp>)`; conveniences `replaceWith`, `addMembers`, `removeMembers`, `addElements`, `putElements`, `removeElements`                              | `JsonPatch`, `JsonNodeOp`                                                    |
| **Compare**            | `equivalentTo(JsonValue)`, `identicalTo(JsonValue)`                                                                                                       | —                                                                                                                                                                           | —                                                                            |
| **Diff**               | `diff(JsonValue[, JsonDiff.Mode])`                                                                                                                        | —                                                                                                                                                                           | `JsonDiff`, `JsonDiff.Mode`, `@JsonDiff.AnyOrder`, `@JsonDiff.AnyAdditional` |
| **Validate**           | `validate(Class)`, `isA(Class, Rule...)`, `asA(Class, Rule...)`                                                                                           | —                                                                                                                                                                           | `@Validation`, `@Required`, `Validation.Rule`                                |

Two entry points sit between the two APIs rather than in one of them:

- **Convert.** `JsonValue.node()` gives the underlying `JsonNode`;
  `JsonNode.lift(JsonAccessors)` re-wraps a node as a `JsonMixed` view that
  shares the same tree. (`JsonMixed.of(node)` also works but creates an
  independent tree.)
- **Extract.** `JsonNode.extract()` produces a new, independent tree with the
  node as its root — the way to hand off a subtree without keeping the parent
  document alive.

Each operation is documented in depth on its own page:

- [Querying the tree](json-search.md) — selectors, matchers, query entry points
- [JSON Patch](json-patch.md) — operation types, path expressions, RFC 6902 comparison
- [Comparing JSON values](json-diff.md) — modes, per-property opt-in, difference format
- [Validation](intro-8-validation.md) — annotation-based validation
- [Performance and streaming](intro-7-json-performance.md) — `Index` modes and tree defaults


## Query

- `JsonSelector` — `$`, `AT`, `of(String)`, `of(JsonNodeType)`, `of(Text)`,
  `of(int)`, `of(int[])`, `of(int, int[, int])`, `of(Predicate)`, `ofAny()`,
  `ofDescendants()`.
- Fluent: `.key`, `.index`, `.indexes`, `.any`, `.descendants`, `.slice`,
  `.type`, `.filter`, `.find`.
- `JsonSelectable` — `query(selector, matches)`, `query`, `queryCount`,
  `queryExists`, `queryFirst`, `queryReduce`, `queryTop`.
- See [Querying the tree](json-search.md).
 

## Validation

While the virtual tree allows to view nodes as any shape we can
test and assert specific shapes given as their Java interface type.

- `as(Class<T>)` — view as type (not checked, always works).
- `isA(Class<T>)` / `asA(Class<T>)` — checking and asserting shape conformity.
- `validate(Class<T>)` — check against a user type.
- `@Validation`, `@Required`, `Rule` — see [Validation](json-validation.md). 

Full attribute list and semantics: see [Validation reference](json-validation-reference.md).

## Diff

JSON can be compared to find the differences between two trees. 
This is exceptionally useful in test assertions or input validation.

- `diff(JsonValue)` / `diff(JsonValue, JsonDiff.Mode)` —
  see [Comparing JSON values](json-diff.md).
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

- [Java API](intro-4-json-from-java.md)
- [JSON API](intro-3-json-from-text.md)
- [JSON Patch](json-patch.md) — the RFC 6902-based patch feature.