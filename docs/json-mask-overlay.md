# Masking and overlaying

Two conveniences built on top of `patch` for common bulk-edit shapes:

- **`mask`** — removes a set of paths and everything under them. A shape-preserving
  blackout: what isn't masked is unchanged.
- **`overlay`** — paints a source JSON on top of the receiver using
  [RFC 7386](https://datatracker.ietf.org/doc/html/rfc7386) merge-patch semantics.
  Object sources merge recursively; everything else replaces.

Both are thin wrappers: `mask` de-sugars to a set of `remove` operations, `overlay`
to a set of `remove` / `replace` / `insert` operations. All the guarantees of
`patch` — clash detection, order-independence, single-pass application, the
original left unchanged — hold for both.

If you need anything beyond "remove these paths" or "merge this document", reach
for [`patch`](json-patch.md) directly.

---

## Masking

`mask(JsonPath...)` removes the given paths. Removing a path removes the whole
subtree rooted at it.

```java
    JsonMixed user = JsonMixed.of( """
        {
          "id": 42,
          "name": "Alice",
          "password": "secret",
          "contact": {
            "email": "alice@example.com",
            "phone": "+49-30-1234"
          }
        }""" );

    JsonMixed safe = user.mask(
        JsonPath.of( "password" ),
        JsonPath.of( "contact", "email" ) );
    // {"id": 42, "name": "Alice", "contact": { "phone": "+49-30-1234" }}
```

Missing paths are no-ops — masking a path that doesn't exist leaves the document
unchanged. This matches `remove`'s forgiving semantics: "ensure this is gone"
is satisfied by absence.

### Masking from a subtree

Paths are **relative** to the node `mask` is called on, not absolute from the
root:

```java
    JsonMixed contact = user.get( "contact" );
    contact.mask( JsonPath.of( "phone" ) );
    // {"email": "alice@example.com"}
```

`mask` returns the masked **subtree**, not the masked root. If you want the whole
document with a nested member removed, call `mask` on the root with the full
relative path.

### Selecting what to mask

Instead of listing paths, you can pass a `JsonSelector` and mask every match.
Matches outside the node's subtree are ignored.

```java
    JsonMixed masked = user.mask( JsonSelector.of( "@.contact.*" ) );
    // removes both email and phone
```

The result depends on what the selector matched:

| Selector matches                        | Result                              |
|-----------------------------------------|-------------------------------------|
| Descendant(s) of the node               | node, with those subtrees removed   |
| The node itself                         | JSON `null` (see below)             |
| A strict ancestor of the node           | undefined — the container is gone   |
| Only siblings or unrelated nodes        | node, unchanged                     |

The SELF and ancestor cases deserve a closer look:

- **`mask(SELF)`** — "remove everything" — yields JSON `null`, not an empty
  document. There's no empty-document state in JSON; `null` is the natural
  stand-in for "the whole thing was cleared."
- **Ancestor removed** — if the selector matches a strict ancestor of the node
  `mask` was called on, the node ceases to exist. The result is `undefined`, not
  `null` — "the container is gone" is a different signal from "the container is
  null."

### Overlapping paths are minimized, not rejected

`patch` rejects operations whose claimed paths overlap. `mask` deliberately
relaxes that — its intent is "these paths and everything under them are gone,"
so redundancy is tolerated rather than treated as a clash:

```java
    user.mask(
        JsonPath.of( "contact" ),
        JsonPath.of( "contact", "email" ),
        JsonPath.of( "contact" ) );       // three paths, one effect
    // equivalent to user.mask( JsonPath.of( "contact" ) )
```

Duplicates are deduplicated; descendants of a kept path are dropped (removing the
ancestor already removes the descendant). Any input permutation produces the same
result.

### Primitives

Calling `mask` on a primitive with any non-SELF path is a no-op — a number or
string has no descendants, so there's nothing to remove:

```java
    JsonMixed.of( "5" ).mask( JsonPath.of( "a" ) );      // → 5
    JsonMixed.of( "5" ).mask( JsonPath.SELF );           // → null
```

---

## Overlaying

`overlay(JsonValue)` merges a source value into this one using RFC 7386. The
source is "painted on top of" the receiver; what the source doesn't mention is
kept.

```java
    JsonMixed current = JsonMixed.of( """
        {
          "name": "Alice",
          "age": 30,
          "contact": {
            "email": "alice@example.com",
            "phone": "+49-30-1234"
          }
        }""" );

    JsonMixed merged = current.overlay( JsonMixed.of( """
        {
          "age": 31,
          "contact": { "phone": "+49-30-5678" },
          "city": "Berlin"
        }""" ) );
    // {
    //   "name": "Alice",
    //   "age": 31,
    //   "contact": { "email": "alice@example.com", "phone": "+49-30-5678" },
    //   "city": "Berlin"
    // }
```

The rules, in RFC order:

- **Object in source, object in receiver** — recurse. Members merge.
- **`null` member in source** — removes that member from the receiver.
- **Anything else** — replace: scalar, array, object-over-scalar, object-over-missing.
- **Extra receiver members** — kept, unless the source names them.

### `null` removes, by default

```java
    current.overlay( JsonMixed.of( "{\"age\": null}" ) );
    // removes "age"
```

This is RFC 7386's mechanism for deletion within a merge. When `null` is
*meaningful data* rather than a deletion marker, use the `OverlayNull.REPLACE`
mode:

```java
    current.overlay( JsonMixed.of( "{\"age\": null}" ), OverlayNull.REPLACE );
    // sets "age" to null; the member stays
```

At the top level, this distinction doesn't apply — a top-level `null` source
always replaces the whole document with `null`. There's no enclosing member to
remove.

### Top-level non-object sources

Per RFC 7386, a non-object source replaces the whole document regardless of
what the receiver is:

```java
    current.overlay( JsonMixed.of( "5" ) );           // → 5
    current.overlay( JsonMixed.of( "[1,2,3]" ) );     // → [1,2,3]
    current.overlay( JsonMixed.of( "null" ) );        // → null
```

This is easy to miss when reading "merge" in the name — merging only happens
when **both** sides are objects. Anything else is a full replacement.

### Arrays are atomic

Array sources replace, they don't merge element-wise:

```java
    JsonMixed.of( "{\"tags\": [1, 2, 3]}" )
        .overlay( JsonMixed.of( "{\"tags\": [9]}" ) );
    // {"tags": [9]}  — not [9, 2, 3]
```

Element-wise array merge has no standard semantics (align by index? by length?
by value?), so RFC 7386 avoids it. If you need to manipulate arrays, `patch`
with explicit `insert` / `remove` / `replace` operations is the right tool.

### Empty objects are a no-op

```java
    current.overlay( JsonMixed.of( "{\"contact\": {}}" ) );
    // "contact" unchanged
```

Counterintuitive at first glance — "the source says `contact` should be empty" —
but correct by RFC's algorithm: merge iterates the **source**'s members, and `{}`
has none. To clear a subtree, mask it (`mask(JsonPath.of("contact"))`) or use a
`null` member (`{"contact": null}`).

### Undefined and null sources

An undefined source is a no-op — there's nothing to overlay:

```java
    current.overlay( current.get( "missing" ) );    // unchanged
```

A Java `null` throws `NullPointerException` — the argument contract requires a
real `JsonValue`.

---

## How they complement each other

`mask` and `overlay` express the two halves of a delta:

- **`mask`** — "these paths should be *gone*." The client asserts absence;
  missing paths are silently OK.
- **`overlay`** — "these paths should have *this* content." The source
  describes the target state for the paths it mentions.

Composing them expresses most of the interesting "bring this document to a
target shape" operations without needing a diff/patch cycle:

```java
    JsonMixed desired = current
        .mask( JsonPath.of( "password" ), JsonPath.of( "internal" ) )
        .overlay( JsonMixed.of( """
            { "name": "Bob", "status": "active" }""" ) );
```

Remove sensitive fields, then overwrite the authoritative ones. Each step is an
independent `patch` call; each returns a new tree; the original is unchanged
throughout.

### Overlay + mask consistency

The two operations use the same underlying machinery, so they compose
predictably. In particular, `overlay` with a `null` member is equivalent to
`mask` on the same path:

```java
    current.overlay( JsonMixed.of( "{\"age\": null}" ) );
    // ≡ current.mask( JsonPath.of( "age" ) )
```

Both produce the same `remove` operation under the hood. If you find yourself
choosing between them, `mask` reads better when you're explicitly saying "delete
this path"; `overlay` reads better when the deletion is one entry among many
data changes described by a source document.

## Related

- [JSON Patch](json-patch.md) — the underlying operation API, if you need
  finer control than `mask` or `overlay` provide
- [Querying the tree](json-search.md) — `JsonSelector`, for path selection
  that feeds `mask`
- [Comparing JSON values](json-comp.md) — `equivalentTo` and `identicalTo`, the
  natural way to verify that a mask or overlay produced the expected result