# json-tree

[![main](https://github.com/dhis2/json-tree/actions/workflows/main.yml/badge.svg?branch=main)](https://github.com/dhis2/json-tree/actions/workflows/main.yml)

A lazily parsed JSON virtual tree library for Java.

*Virtual* here means it represents the "wanted" or "expected" tree structure.
On leaf value access it is checked against the actual tree structure underneath,
so navigation is painless and checks only happen when values are resolved
(extracted) from the actual tree.

*Lazily parsed* means only the parts of the underlying tree that are used during
navigation get parsed into nodes which remember offsets ("pointers") into the
original JSON input. Untouched parts are skipped over until they are used. This
makes for a fast, memory-efficient implementation that is convenient to query
and can be extended with user types, handling inputs in the MB range without
problems.

## Quick start

```java
    // parsing JSON text
    JsonArray numbers = JsonMixed.of("[1,2,3]");
    int v1 = numbers.get(1).intValue(); // = 2
    int[] values = numbers.intValues().toArray(); // = [1,2,3]

    // programmatic Java => JSON
    JsonString  s = Json.of( "hello" );   // note: no quotes
    JsonInteger i = Json.of( 42 );

    // programmatic dynamic composition Java => JSON
    JsonObject root = Json.object( obj ->
        obj.addNumber( "x", 1 ).addNumber( "y", 5 ) );
    root.toJson();   // {"x":1,"y":5}
```

## Documentation

- [Getting started](docs/intro-1-getting-started) — install and first example
- [Lazy parsing and the virtual tree](docs/intro-2-lazy-parsing) — the idea behind the library
- [Java API](docs/intro-4-json-from-java) — build a tree from Java types
- [JSON API](docs/intro-3-json-from-text) — work from a JSON document
- [Null, undefined and existence](docs/intro-5-null-and-undefined) — the subtle states
- [User types as JSON tree](docs/intro-6-user-types) — extend the tree with your own types
- [Validation](docs/json-validation) — check input against a target schema
- [Comparing JSON values (diff)](docs/json-diff) — structural comparison
- [API reference](docs/json-api-overview) — types and methods at a glance
- [Standards and RFCs](docs/rfc-reference.md) — conformance and deviations

## Maven

Coordinates: 
- [`org.hisp.dhis:json-tree`](https://search.maven.org/artifact/org.hisp.dhis/json-tree).
- [Sonartype-Central](https://central.sonatype.com/artifact/org.hisp.dhis/json-tree)

```xml
<dependency>
    <groupId>org.hisp.dhis</groupId>
    <artifactId>json-tree</artifactId>
    <version>1.10.0</version>
</dependency>
```

## License

[BSD 3-Clause License](LICENSE)