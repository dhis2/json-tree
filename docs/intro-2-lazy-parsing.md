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