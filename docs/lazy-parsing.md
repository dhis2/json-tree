# Lazy parsing and the virtual tree

## The two ideas

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
cheap to extend the tree with user types (see [User types](user-types.md))
without paying for eager conversion.

## Related

- [Null, undefined and existence](null-and-undefined.md)
- [Java API](java-api.md)
- [JSON API](json-api.md)