# Navigating the tree

Every node in the tree has a `JsonPath` — an ordered sequence of `Text` segments
from the root. A segment is either an object member name or an array index; the
two are indistinguishable in the path. Navigation is just
chaining one more segment onto the current path; So the virtual tree API is
largely just a nice API to drill down to a specific path.
The path is only ever resolved to a node if a terminal operation is called.

## One level at a time

For a `JsonObject`, ask by name. For a `JsonArray`, ask by index. Both return a
node of the requested type, and both just chain one more segment onto the path
and give back an API to continue navigating from there on.

```java
    JsonObject root = JsonMixed.of( """
        {
            "name": "John",
            "points": [10, 20, 30]
        }""" );

    JsonMixed name = root.get( "name" );                 // segment "name"
    JsonMixed p20  = root.getArray( "points" ).get( 1 ); // segment "1"
```

Typed convenience helpers (`getString`, `getNumber`, `getArray`, `getObject`,
`getBoolean`, `getList`, `getMap`, …) do the same thing with a narrower return
type.

## Several levels in one go

`get(CharSequence, …)` accepts a `String` that already contains path syntax. If
the string contains `.`, `{` or `[` it is first parsed into a `JsonPath` and
applied in a single step. If it contains no syntax, it behaves exactly like a
plain member name.

```java
    JsonObject root = JsonMixed.of( """
        {
            "address": { "city": "Oslo" },
            "points": [10, 20, 30]
        }""" );

    String city = root.get( ".address.city" ).string();             // dotted
    String city2 = root.get( "{address}{city}" ).string();          // braced
    int    p20   = root.get( ".points[1]" ).asNumber().intValue();  // indexed
```

The three notations:

| Notation     | Meaning                                                             |
|--------------|---------------------------------------------------------------------|
| `.property`  | object member, dotted                                               |
| `{property}` | object member, braced — needed when the name itself contains syntax |
| `[index]`    | array index                                                         |

An opening bracket with no matching close, or a `[…]` that does not contain an
integer, is read literally as part of the previous segment's name. That's
deliberate: ordinary property names work without any escaping, and you only
reach for `{…}` when you really have to.

## Building a `JsonPath` explicitly

For anything non-trivial, build the path up front and pass it in one call.
`JsonPath` is immutable and cheap to chain.

```java
    JsonPath path = JsonPath.of( "address", "city" );   
    // => equivalent to JsonPath.of(".address.city")

    JsonMixed city = root.get( path );                  

    JsonPath firstPoint = JsonPath.of( ".points" ).chain( 1 ); 
    // => .points[1] (segments are "points" + "1"
```

`JsonPath.of(CharSequence...)` chains segments, `JsonPath.of(int)` creates an
array-index path, and `JsonPath.escape(Text)` escapes a plain name that
contains syntax characters.

## The current path

Every node knows where it is. `node.path()` returns its `JsonPath`,
`path.length()` its depth, `path.segments()` its segments, and
`path.parentPath()` the path one level up. Useful when you want to log or
compare positions rather than values.

## Related

- [Lazy parsing and the virtual tree](intro-2-lazy-parsing)
- [JSON API](intro-3-json-from-text)
- [API reference](json-api-overview)