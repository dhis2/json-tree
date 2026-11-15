# json-tree

**json-tree** is a zero-dependency Java JSON library that supports dynamic processing of large payloads.
It lazily parses to a virtual tree, keeps memory close to the source text,
and gives you RFC 9535-style query, JSON Patch, diff, and schema validation
in one small package—without Jackson’s object graph or multiple separate dependencies.


[![Maven Central](https://img.shields.io/maven-central/v/org.hisp.dhis/json-tree)](https://search.maven.org/artifact/org.hisp.dhis/json-tree)
[![Javadoc](https://img.shields.io/badge/javadoc-latest-blue)](https://javadoc.io/doc/org.hisp.dhis/json-tree)
[![License](https://img.shields.io/github/license/dhis2/json-tree)](https://github.com/dhis2/json-tree/blob/main/LICENSE)

**[Javadoc](https://javadoc.io/doc/org.hisp.dhis/json-tree)** - 
**[Intro](docs/intro-1-getting-started.md)** - 
**[Standards and RFCs](docs/rfc-reference.md)**



## What's in the box

- **[Rich lazy virtual tree API](docs/json-api-overview.md)** — `JsonMixed`, `JsonObject`, `JsonArray`, and friends
- **[Navigation](docs/json-paths.md)** — never-throwing `get()`, three-state existence (`missing` / `null` / value)
- **[Search](docs/json-search.md)** — RFC 9535-*inspired* path queries
- **[Patch](docs/json-search.md)** — JSON Patch RFC 6902-*based*, order-independent ops by design
- **[Diff](docs/json-diff.md)** — structural comparison of two documents
- **[Validation](docs/json-validation.md)** — annotation-on-interface schema style
- **Dialects** — RFC 8259 plus a [JSON5*](docs/spec-json5-star.md) dialect and `NaN`/`Infinity` handling
- **Zero dependencies**, small codebase

See [Standards and RFCs](docs/rfc-reference.md) for full details on RFC compliance and extensions.

Also included, less central: [text templating](docs/spec-text-templating.md), [JURL notation](docs/spec-jurl.md), [input expressions](docs/spec-input-expressions.md).


## What are you looking for?

| Here you get                                             | Look elsewhere for                      |
|----------------------------------------------------------|-----------------------------------------|
| **a tree** with **user types**                           | **POJO databind** with annotations      |
| **control** over details                                 | a **magic** Java ~ JSON transformer     |
| **zero dependencies** and a small audit surface          | **a large ecosystem** with many options |
| **batteries included** (query + patch + diff + validate) | a core with an **addon system**         |
| **a pragmatic take** on RFC standards                    | **strict** or **full** RFC compliance   | 


## Quick example

```java
    String json = """
        {
          "id": "abc",
          "name": "Ada",
          "roles": ["admin", "user"]
        }""";

    JsonObject root = JsonMixed.of(json);

    String id = root.get("id").string();      // "abc"
    int roleCount = root.get("roles").size(); // 2
```

Navigation never throws. `root.get("a").get("b").get("c")` returns an *undefined*
node rather than `null` or an exception, and distinguishes three states that
`Map`-based APIs usually collapse: **missing**, **present but null**, and
**present with a value**. 

However, on value access (a terminal operation) errors throw eagerly.

Data can use typed access as interfaces on top of the tree, 
so no annotations or reflection pass is needed:

```java
    interface User extends JsonObject {
        String id();
        String name();
    }
    User user = root.as(User.class);
```
`default`-methods can be used to implement custom access logic 
based on the generic tree API inherited from the base-type.

## Installation

```xml
<dependency>
    <groupId>org.hisp.dhis</groupId>
    <artifactId>json-tree</artifactId>
    <version>1.14.0</version>
</dependency>
```

Get the [full into →](docs/intro-1-getting-started.md)


## License

[BSD 3-Clause License](LICENSE)