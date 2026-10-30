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
| `.indexes(i, j, ...)`      | union of indexes                                 |
| `.any()`                   | all children (object members or array elements)  |
| `.descendants()`           | this node and every node below it                |
| `.flatten()`               | one level of array nesting expanded              |
| `.slice(start, end, step)` | array slice                                      |
| `.type(JsonNodeType.X)`    | keep only nodes of that JSON type                |
| `.filter(node -> ...)`     | keep only nodes matching a `Predicate<JsonNode>` |
| `.find(node -> ...)`       | descendants that match a `Predicate<JsonMixed>`  |

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

`.*` is accepted as an alias for `[*]` and canonicalizes to it on `toString()`.

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

### Entry points at a glance

`JsonSelectable` exposes eleven methods; one is abstract, the rest are default
methods layered on top of it.

| Method                                                | Returns       | Purpose                                                      |
|-------------------------------------------------------|---------------|--------------------------------------------------------------|
| `query(selector, matches)`                            | `void`        | the essential method; hand each match to a `Matches<T>` sink |
| `query(selector)`                                     | `Stream<T>`   | all matches as a stream                                      |
| `query(selector, limit)`                              | `Stream<T>`   | up to `limit` matches as a stream                            |
| `queryCount(selector)`                                | `int`         | number of matches                                            |
| `queryCount(selector, limit)`                         | `int`         | number of matches, capped at `limit`                         |
| `queryExists(selector)`                               | `boolean`     | `true` if at least one match                                 |
| `queryFirst(selector)`                                | `Optional<T>` | first match, if any                                          |
| `queryReduce(selector, extract, init, reduce)`        | `V`           | fold all matches into one value                              |
| `queryReduce(selector, limit, extract, init, reduce)` | `V`           | fold matches, capped at `limit`                              |
| `queryMap(selector, mapper)`                          | `List<R>`     | transform each match; null results are skipped               |
| `queryTop(n, selector, score)`                        | `Stream<T>`   | highest-scoring `n` matches, descending                      |

The `query(selector, matches)` form is what all of the above use underneath —
it takes a `Matches<T>` sink instead of returning a `Stream`, which avoids
allocating a result collection when you only need a count, an existence check,
or a running aggregate. Reach for it directly when you want a custom
aggregation.

## Transforming matches

`queryMap(selector, mapper)` runs a selector and applies a mapper to each
match, returning the results as a list. It's the transformation counterpart
to `query(...)`:

```java
    JsonMixed root = JsonMixed.of( """
        {
            "people": [
                { "first": "James", "age": 30 },
                { "first": "Jacob", "age": 25 },
                { "first": "Jayden", "age": 41 }
            ]
        }""" );

    List<String> firsts = root.queryMap(
        JsonSelector.of( "$.people[*]" ),
        p -> p.getString( "first" ).string() );
    // [James, Jacob, Jayden]
```

The mapper can return any type — a scalar, a constructed `JsonObject` or
`JsonArray`, another list, or a domain type. This is where the shape of the
result is decided: instead of encoding the output shape in the selector, you
compose it in Java.

**Constructed output**

```java
    List<JsonObject> slimmed = root.queryMap(
        JsonSelector.of( "$.people[*]" ),
        p -> Json.object( o -> o
            .addString( "name", p.getString( "first" ).string() )
            .addNumber( "age",  p.getNumber( "age" ).integer() ) ) );
    // [{name: James, age: 30}, {name: Jacob, age: 25}, {name: Jayden, age: 41}]
```

If the list should become a JSON document rather than stay a Java `List`,
feed the results into a builder:

```java
    JsonArray materialised = Json.array( arr ->
        root.queryMap( JsonSelector.of( "$.people[*]" ),
            p -> p.getString( "first" ).string() )
        .forEach( arr::addString ) );
    // ["James","Jacob","Jayden"]
```

**Nested results**

Languages like JMESPath distinguish a *nested* projection
(`reservations[*].instances[*].state`, one array per reservation) from a
*flattened* projection (`reservations[].instances[].state`, one array
overall). Both are expressible with `queryMap` — the choice is whether you
nest the calls or let the selector flatten.

```java
    JsonMixed fleet = JsonMixed.of( """
        {
            "reservations": [
                { "instances": [ { "state": "running" }, { "state": "stopped" } ] },
                { "instances": [ { "state": "terminated" }, { "state": "running" } ] }
            ]
        }""" );
```

Nested — one list per outer match:

```java
    List<List<String>> nested = fleet.queryMap(
        JsonSelector.of( "$.reservations[*]" ),
        res -> res.queryMap(
            JsonSelector.of( "@.instances[*]" ),         // @ = self, not $
            inst -> inst.getString( "state" ).string() ) );
    // [[running, stopped], [terminated, running]]
```

Note `@` in the inner selector: it anchors the query at the current match,
not at the document root. Using `$` there would re-enter the whole document
for every outer match.

Flat — a single list overall:

```java
    List<String> flat = fleet.queryMap(
        JsonSelector.of( "$.reservations[*].instances[*]" ),
        inst -> inst.getString( "state" ).string() );
    // [running, stopped, terminated, running]
```

`[*]` at each level maps to `.any()` and visits every instance once, so the
result is flat by construction. When the arrays you're iterating might
themselves contain arrays, use `.flatten()` where JMESPath would use `[]`:

```java
    JsonMixed grid = JsonMixed.of( "{\"matrix\": [[1,2],[3,4]]}" );

    List<Integer> numbers = grid.queryMap(
        JsonSelector.of( "$.matrix" ).flatten(),          // flatten one level
        n -> n.as( JsonNumber.class ).integer() );
    // [1, 2, 3, 4]
```

Without `.flatten()`, the same selector would match the two inner arrays and
the mapper would be called twice.

**Null handling**

A mapper that returns `null` causes that match to be skipped; the returned
list never contains `null`s. This matches the projection semantics of
JMESPath and keeps nulls out of downstream code, but means the result size
is not necessarily equal to the number of matches.

```java
    JsonMixed mixed = JsonMixed.of( """
        {"people": [
            {"name": "James"},
            {"nickname": "JJ"},
            {"name": "Jacob"}
        ]}""" );

    List<String> names = mixed.queryMap(
        JsonSelector.of( "$.people[*]" ),
        p -> p.getString( "name" ).isUndefined()
            ? null
            : p.getString( "name" ).string() );
    // [James, Jacob]  — the middle entry produced null and was skipped
```

When `null` values are meaningful in the output, use the stream form.
`Stream.toList()` rejects nulls, so use `Collectors.toList()`:

```java
    List<String> withNulls = mixed
        .query( JsonSelector.of( "$.people[*]" ) )
        .map( p -> p.getString( "name" ).isUndefined()
            ? null
            : p.getString( "name" ).string() )
        .collect( Collectors.toList() );
    // [James, null, Jacob]
```

## Related

- [Navigating the tree](json-paths.md) — `JsonPath`, the fixed-location
  counterpart to `JsonSelector`.
- [API reference](json-api-overview.md)