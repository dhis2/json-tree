# Lazy parsing and the virtual tree

## Two ideas

**Virtual.** A `Json*` instance describes the "wanted" or "expected" structure
of the tree. It's not a copy of the data — it's a view that is checked against
the actual tree underneath only when a value is resolved (extracted) from it.
Navigation is painless, and the type-check work only happens for values you
actually touch.

**Lazy.** Only the parts of the underlying tree that are used during navigation
get parsed into nodes. Each node remembers offsets ("pointers") into the
original JSON input. Untouched parts of the tree are skipped over until they
are used.

## Why that matters

Because the underlying input is never expanded into a tree of intermediate
objects, memory stays close to the size of the source string. In practice this
lets `json-tree` comfortably handle inputs in the MB range, and it makes it
cheap to extend the tree with user types (see [User types](intro-6-user-types))
without paying for eager conversion.

Already convinced? Then you can skip to [Two APIs](#two-apis) or
[JSON from text](intro-3-json-from-text) now. 

## What you get

Grouped by theme — these are the characteristics that distinguish `json-tree`
from other JSON libraries.

**Cheap until you touch it**

- *No object graph.* Parsed nodes are tiny (a path and a start offset); memory
  is dominated by the source text, not by the number of values it contains.
- *Pay for what you use.* Values are resolved on access. If you only need a
  header field out of a 10 MB payload, only the bytes up to that field are
  walked.
- *Streaming iteration.* `elements(Index)` and `entries(Index)` support a
  skip-mode that keeps the internal cache flat across a million-element loop.
  See [Performance and streaming](intro-7-json-performance.md).
- *Zero-copy text.* `Text` exposes string content as views into the source
  `char[]`, not copies. Sub-strings from navigation don't allocate.

**Ergonomic API**

- *Object-graph ergonomics without the object graph.* `JsonObject`,
  `JsonArray`, `JsonNumber`, `JsonString`, `JsonBoolean` compose like a normal
  Java model, but the raw JSON text is always available via `getDeclaration()`
  or `textValue()` when text-level access matters (error messages, logging,
  forwarding without re-serialising).
- *Navigation never throws.* `root.get("a").get("b").get("c")` returns an
  undefined node if any step is missing, so “is this defined?” chains don't
  need to exist. Only terminal accessors like `.string()` or `.intValue()`
  throw when the value isn't there:

    ```java
    int port = root.get( "config" ).get( "port" ).intValue();
    ```

- *Three explicit states.* “Missing”, “present but `null`”, and “present with
  a value” are distinguishable (`exists()`, `isUndefined()`, `isNull()`)
  instead of collapsing to a single `null` ambiguity.
- *User types are interfaces.* Extend the tree with domain types by writing
  `interface JsonUser extends JsonObject { ... }`. No annotations to describe
  the mapping, no reflection pass, no converter class — abstract methods map
  by name, `default` methods get full control when needed.

**Batteries included**

- Parse, search, generate, patch, diff, and validate — all in one library,
  all sharing the same lazy parser and node model. No round-tripping through
  third-party models to move between them.
- *Search.* RFC 9535-inspired selectors with an extensible `Matcher` SPI;
  write your own matcher if the built-ins don't fit. See
  [Querying the tree](json-search.md).
- *Patch.* RFC 6902-based, but order-independent — a patch is a *set* of
  operations, all resolved against the original target. Clashes are detected
  up front, application is single-pass. See [JSON Patch](json-patch.md).
- *Diff.* Configurable modes (arrays/objects/number equality) plus
  per-property overrides via `@JsonDiff.AnyOrder` / `@AnyAdditional`. See
  [Comparing JSON values](json-diff.md).
- *Validation.* JSON Schema structural vocabulary via annotations on the
  property methods themselves — no separate schema document, no string name
  references. See [Validation](intro-8-validation.md).

**Small and standalone**

- Zero dependencies.
- Small codebase: the work is done by `JsonTree` (the parser) and
  `JsonVirtualTree` (the virtual layer) plus a set of interfaces. Little
  classloading before you're productive.
- No service loader, no reflection on startup, no annotation processor.

**Compatible and pragmatic**

- Generally RFC 8259 compatible (deviations documented in
  [Standards and specifications](rfc-reference.md)).
- JSON5* dialect accepts trailing commas, comments, and single-quoted strings
  — but only where the input can be rewritten to strict JSON *without
  inserting characters*, preserving the offset model. See
  [JSON5* notation](json5-star-reference.md).
- Special `double` values that aren't valid JSON numbers (`NaN`, `Infinity`)
  are readable transparently — either via the JSON5* route or by treating
  the JSON string as a number transparently via `doubleValue()`.

## Trade-offs

Deliberate design choices, not oversights — worth knowing before you commit.

- **Not a standard API.** Nothing else will read code written against
  `JsonObject` as if it were Jackson or Gson. The API is small and learnable,
  but it's the library's own.
- **Interfaces, not POJOs.** Typed access requires defining an interface. This
  is what buys the laziness and the extensibility, but it's not the “annotate a
  data class” workflow some callers expect.
- **Lazy means errors surface at access time.** A malformed document doesn't
  throw on parse; it throws when the malformed region is reached. This is a
  deliberate trade (see the parser javadoc), but it means “fail fast on load”
  has to be done explicitly (e.g. `root.node().getDeclaration()` for eager
  validation of the whole document).
- **RFC conformance is a compromise.** “Generally compatible” plus documented
  deviations is the honest description — read
  [Standards and specifications](rfc-reference.md) before relying on edge-case
  behaviour.
- **The virtual/actual distinction is a concept to learn.** Once it clicks,
  most of the API makes sense; until then, `JsonValue` vs `JsonNode` can feel
  like two names for the same thing. The table in the next section is here
  for exactly that reason.

## Two APIs
The user facing API `JsonValue`+`JsonMixed` (+user types) is for convenience
when working with JSON data; the actual tree API `JsonNode` is for driving
the lazy parsing when needed under the hood. 

| `JsonValue` API                                                       | `JsonNode` API                                             |
|-----------------------------------------------------------------------|------------------------------------------------------------|
| `JsonValue#node()` =>                                                 | <= `JsonValue.of(node)`                                    |
| `JsonMixed#node()` =>                                                 | <= `JsonMixed.of(node)`                                    |
| **virtual** 👻  (how we view it)                                      | **actual** ☠️ (what is actually in the JSON text)          |
| high level abstraction                                                | low level abstraction                                      |
| the JSON assumed/expected                                             | the actual JSON                                            |
| navigation without requiring existence (no exceptions)                | navigation demands existence (otherwise throws exceptions) |
| extendable tree API                                                   | non-extendable tree API                                    |
| `JsonObject`, `JsonArray`, `JsonNumber`, `JsonString`, `JsonBoolean`  | `JsonNode` + `JsonNodeType`                                |
| `JsonValue` (API all above have in common; bottom type)               | `JsonNode`                                                 |
| `JsonMixed` (API of all above together; top type)                     | `JsonNode`                                                 |
| read-only (+ projections)                                             | immutable (transformations to new trees)                   |
| extendable by user types                                              | build in mapping to JDK types                              |
| Implemented by `JsonVirtualTree`                                      | Implemented by `JsonTree`                                  |

## Related

- [JSON from text](intro-3-json-from-text)
- [JSON from Java](intro-4-json-from-java)
- [Null, undefined and existence](intro-5-null-and-undefined)
- [Lazyness, Performance and Streaming](intro-7-json-performance.md)