# Querying the tree

`JsonPath` says *where a node is*. `JsonSelector` says *what to look for*: a
path-like pattern where each segment is a matcher — key, index, wildcard,
descendants, slice, type, or an arbitrary `Predicate`. Because the matcher is
an interface, selectors can be composed programmatically as well as parsed from
a string.

`JsonSelectable` (which both `JsonValue` and `JsonNode` inherit) is the API 
for *running* a selector query against a node and its subtree. 
Every other method — `queryCount`, `queryExists`, `queryFirst`, `queryReduce`, 
`queryTop` — is a thin convenience wrapper around one essential method:

```java
    void query(JsonSelector selector, Matches<T> matches);
```

`Matches` is a consumer collecting the matches found.

## Building a selector

Fluent building blocks, all defined on `JsonSelector`:

| Method                     | Meaning                                          |
|----------------------------|--------------------------------------------------|
| `.key("name")`             | object member with that name                     |
| `.index(i)`                | array element at that index                      |
| `.indexes(i, j, …)`        | union of indexes                                 |
| `.any()`                   | all children (object members or array elements)  |
| `.descendants()`           | this node and every node below it                |
| `.slice(start, end, step)` | array slice                                      |
| `.type(JsonNodeType.X)`    | keep only nodes of that JSON type                |
| `.filter(node -> …)`       | keep only nodes matching a `Predicate<JsonNode>` |
| `.find(node -> …)`         | descendants that match a `Predicate<JsonMixed>`  |

Two anchors to start building a selector from:

- `JsonSelector.$` — start from the document root (whatever node `query` is called on
  jumps up to the root first).
- `JsonSelector.AT` — the current node (used as a start point for relative
  selectors, e.g. inside filters).

Note that `type` and `filter` narrow the current match set; 
they don't iterate children. To select all numbers anywhere under `$`, write
`$.any().type(NUMBER)` or `$.descendants().type(NUMBER)` — not
`$.type(NUMBER)`, which only checks the root node itself.

## String form

`JsonSelector.of(String)` parses the subset of RFC 9535 that maps cleanly onto
the fluent API: keys, indexes, unions, slices, wildcards, descendants, and
`[?(:type)]` type filters. Filters expressed as `Predicate` are
*programmatic only* — the parser doesn't evaluate expressions.

```java
    JsonSelector s = JsonSelector.of( "$.people[0].name" );
    JsonSelector t = JsonSelector.of( "$.people[*]" );
    JsonSelector u = JsonSelector.of( "$.." ).type( JsonNodeType.STRING );
```

Descendants (`..`) accept either the RFC short form (`$..name`) or the
bracketed form (`$..['name']`); both normalise to the same selector. Type
filters use the RFC 9535 shape `[?(:type)]`.

Type filters come in two shapes. Inside brackets — `$[?(:number)]` — this is
the RFC 9535 child selector: apply the filter to each child of the current
node, equivalent to `$.any().type(NUMBER)`. The non-bracketed top-level form
`$?(:number)` is our own extension: it narrows the current node without
navigating. Only the bracketed form is RFC-conform; the top-level form
exists so that the fluent `.type(...)` matcher has a round-trippable string
shape.

Anything past the common cases is easier to build fluently than to fight the
parser — the two forms produce the same `JsonSelector` and can be mixed.

## Running a selector

Given this document:

```java
    JsonMixed root = JsonMixed.of( """
        {
            "team": "compilers",
            "people": [
                { "name": "Ada",   "age": 36 },
                { "name": "Grace", "age": 85 },
                { "name": "Alan",  "age": 41 }
            ]
        }""" );
```

Count matches, check existence, grab the first one:

```java
    int  n      = root.queryCount( JsonSelector.of( "$.people[*]" ) );       // 3
    boolean any = root.queryExists( JsonSelector.of( "$..['name']" ) );      // true

    String first = root.queryFirst( JsonSelector.of( "$.people[0].name" ) )
        .map( node -> node.as( JsonString.class ).string() )
        .orElse( null );                                                    // "Ada"
```

Aggregate over matches without collecting them:

```java
    int totalAge = root.queryReduce(
        JsonSelector.of( "$.people[*].age" ),
        node -> node.as( JsonNumber.class ).integer(),
        0,
        Integer::sum );
```

Rank matches with a scoring function:

```java
    root.queryTop( 2,
        JsonSelector.of( "$.people[*]" ),
        node -> node.getNumber( "age" ).integer() )              // oldest first
        .map( node -> node.getString( "name" ).string() )
        .toList();                                              // [Grace, Alan]
```

The `query(selector, matches)` form is what all of the above use underneath —
it takes a `Matches<T>` sink instead of returning a `Stream`, which avoids
allocating a result collection when you only need a count, an existence check,
or a running aggregate. Reach for it directly when you want a custom
aggregation.

## Related

- [Navigating the tree](tree-navigation.md) — `JsonPath`, the fixed-location
  counterpart to `JsonSelector`.
- [API reference](api-reference.md)