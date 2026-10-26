# Performance and streaming

The virtual tree is lazy not just when you navigate but also when you iterate:
`JsonNode.elements(...)` and `JsonNode.members(...)` accept a `JsonNode.Index`
mode that controls whether visited nodes are added to the tree's internal
cache. Choosing the right mode is the main lever for controlling memory usage
when iterating or streaming large containers.

## The default: `AUTO`

With no argument, both methods use `AUTO`. In this mode:

- **Simple nodes** (numbers, strings, booleans, `null`) are created fresh each
  time and not cached — they're cheap to reproduce.
- **Container nodes** (objects, arrays) are added to the cache. This makes
  path-based access through them faster afterwards, because the parser can
  start from the nearest cached ancestor instead of walking from the root.

For typical walk-the-tree usage this is the right default: you pay a small
memory cost for containers and get much faster repeated access.

## Streaming: `SKIP`

If you're doing a single pass over a large array or object — processing each
element and never looking it up again — `AUTO` is doing unnecessary work.
Every container element lands in the cache and stays alive as long as the tree
does. For a million-element array, that's a million cached nodes you'll never
use again.

`SKIP` tells the iteration not to cache anything:

```java
    JsonNode items = root.node().member( "items" );
    for ( JsonNode item : items.elements( JsonNode.Index.SKIP ) ) {
        process( item );
    }
```

Each node returned is fresh and independent of the cache. Once the loop moves
past a node, nothing holds a reference to it — it becomes eligible for garbage
collection. The internal `nodesByPath` map stays the same size whether you
iterate ten elements or ten million.

This is what makes the API genuinely stream-capable on containers in the MB
range and beyond: memory during the loop is dominated by the source text, not
by the number of elements seen.

The same mode is available on the typed level: `JsonObject.entries(Index)` and
`JsonArray.elements(Index)` (where applicable) forward to the raw node API
with the same semantics.

## The full menu

| Mode        | Caches simple nodes? | Caches container nodes? | Typical use                                       |
|-------------|:--------------------:|:-----------------------:|---------------------------------------------------|
| `AUTO`      |          no          |           yes           | default; reusable navigation                      |
| `SKIP`      |          no          |           no            | one-pass streaming of large containers            |
| `AUTO_SKIP` |          no          |           no            | same as `SKIP`; used internally by the query API  |
| `CHECK`     |          no          |   reuse existing only   | benefit from an existing cache without growing it |
| `ADD`       |         yes          |           yes           | force caching, e.g. for warm-up or diagnostics    |

`CHECK` reads from the cache but never writes to it: if a node at the current
path is already cached, you get that same instance; otherwise a fresh
uncached node is created. `ADD` is the opposite: it caches everything,
including simple nodes, and forces caching even if the tree's default would
have skipped them.

## Setting the default at construction

The `Index` passed to `elements(...)` / `members(...)` overrides the tree's
behaviour for that one iteration. But the tree itself carries a default,
set at construction time — and `AUTO` (the mode used when you pass no
argument) means "do whatever the tree's default says".

The constructor that exposes this is `JsonNode.of(json, onGet, Index)`, with
`Path` / `byte[]` variants that take the same third argument:

```java
    JsonNode tree = JsonNode.of( json, null, JsonNode.Index.SKIP );
    // elements()/members() without an Index argument now behave as SKIP
```

The `JsonMixed.of(json)` and `JsonValue.of(json)` entry points don't expose
this parameter — they always build the underlying `JsonNode` with `AUTO`. To
set a different default, build the `JsonNode` yourself and lift it:

```java
    JsonMixed tree = JsonNode.of( json, null, JsonNode.Index.SKIP )
        .lift( JsonAccess.GLOBAL );
```

The lifted value shares the underlying tree, so the default carries through.

### Why it matters for tooling

Several operations on the higher-level APIs — `validate(...)`, `diff(...)`,
`patch(...)`, and the query API behind `JsonSelector` — traverse the tree
using `AUTO` or `AUTO_SKIP` internally. They don't take an `Index` argument,
so the tree-level default is the only lever you have over their caching
behaviour.

`JsonNode.Index.resolve(Index treeLevel)` defines exactly how each
operation-level mode interacts with the tree-level setting:

| Tree default ↓ / Operation → | `SKIP` | `CHECK` | `ADD` | `AUTO`  | `AUTO_SKIP` |
|------------------------------|:------:|:-------:|:-----:|:-------:|:-----------:|
| `AUTO`                       | `SKIP` | `CHECK` | `ADD` | `AUTO`  |   `SKIP`    |
| `SKIP`                       | `SKIP` | `CHECK` | `ADD` | `SKIP`  |   `SKIP`    |
| `CHECK`                      | `SKIP` | `CHECK` | `ADD` | `CHECK` |   `CHECK`   |
| `ADD`                        | `SKIP` | `CHECK` | `ADD` |  `ADD`  |    `ADD`    |

Two rules summarise the table:

- **An explicit `SKIP`, `CHECK`, or `ADD` at the operation level always wins.**
  These modes mean what they say.
- **`AUTO` follows the tree.** `AUTO_SKIP` — the mode tooling uses — resolves
  to `SKIP` when the tree is `AUTO` and otherwise follows the tree. In other
  words, tools prefer to skip but defer to what the tree's author chose.

### Practical defaults

- **Read a large document once, no need to keep nodes around.** Construct with
  `JsonNode.Index.SKIP`. `validate`, `diff`, `query`, and other tooling APIs
  will then run without accumulating any cached nodes beyond what you access
  explicitly.
- **Repeated random access on the same tree.** Construct with `ADD`. Tooling
  calls (which prefer `AUTO_SKIP`) will then also cache aggressively, on the
  assumption that you'll benefit from it.
- **Balanced.** Construct with `CHECK`: reads from the cache but never grows
  it. Rarely the right default; useful for a bounded memory budget on a tree
  that has already been warmed.
- **Default behaviour.** `AUTO`, matching the constructors that don't expose
  the parameter. Reasonable when the access pattern is mixed.

## Trade-offs

**No reuse across passes.** A node obtained via `SKIP` is not in the cache, so
if you access the same element again later, a new node is created for it. For
a single pass this doesn't matter. For workloads that alternate between
iteration and lookup, `AUTO` (or `CHECK` once warm) is usually better.

**Duplicate keys behave differently.** In a JSON object with duplicate member
names, iterating with a caching mode (or after that key has been looked up
before) observes only the *first* occurrence each time the key re-occurs —
subsequent occurrences are skipped during iteration. With `SKIP`, every
occurrence is observed in document order. If your data has duplicate keys and
you need to see all of them, use `SKIP`.

**The query API already uses this.** `JsonSelector` traverses descendant
nodes in `AUTO_SKIP` mode, so a query doesn't pollute the cache with
intermediate match candidates. Combined with a `SKIP` tree default, that
means queries run without any caching side effects at all. See
[Setting the default at construction](#setting-the-default-at-construction).

## Related

- [Extending with user types](intro-6-user-types.md)
- [Querying the tree](json-search.md) — `JsonSelector`, which uses skip-mode
  traversal internally
- [Lazy parsing and the virtual tree](intro-2-lazy-parsing.md) — the offsets model
  underneath