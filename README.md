# json-tree

**json-tree** is a zero-dependency Java JSON library for large payloads you process dynamically.
It lazily parses to a virtual tree, keeps memory close to the source text,
and gives you RFC 9535-style query, JSON Patch, diff, and schema validation
in one small package—without Jackson’s object graph or multiple separate dependencies.


[![Maven Central](https://img.shields.io/maven-central/v/org.hisp.dhis/json-tree)](https://search.maven.org/artifact/org.hisp.dhis/json-tree)
[![Javadoc](https://img.shields.io/badge/javadoc-latest-blue)](https://javadoc.io/doc/org.hisp.dhis/json-tree)
[![License](https://img.shields.io/github/license/dhis2/json-tree)](https://github.com/dhis2/json-tree/blob/main/LICENSE)

---

## Why json-tree?

`json-tree` started from a performance problem in a test suite: a JSON-heavy
integration test was taking far too long because path-based lookups were
evaluated repeatedly against a large document.

The `json-tree` lib was the answer: keep the source text, represent each node as
a path + offset, and materialize nothing until someone asks for a value.

Three things follow from that design:

- **Lazy.** Untouched regions are skipped, not parsed. Memory stays close to the
  size of the source string, not to an object graph derived from it.
- **Zero dependencies.** Parsing, querying, patching, diffing, and validation are
  all in-tree. Nothing to exclude, nothing to audit in addition.
- **One artifact.** The jobs you'd normally assemble from Jackson + JsonPath +
  json-patch + a validator are in one small package and integrate without conversion.

[![Memory: max payload at fixed heap](https://img.shields.io/badge/benchmark-pending-lightgrey)](https://github.com/dhis2/json-tree#benchmarks)

<!--
  BENCHMARK PLACEHOLDER — fill with the -Xmx max-payload chart.
  One bar chart: largest input each library can process at -Xmx64m.
  Caption: "At 64 MB heap, Jackson's tree model tops out at ~X MB;
  json-tree handles ~Y MB." Reproducible harness: [link].
-->

---

## Quick example

```java
    String json = """
        {
          "id": "abc",
          "name": "Ada",
          "roles": ["admin", "user"]
        }""";

    JsonObject root = JsonMixed.of(json);

    String id = root.get("id").stringValue();   // "abc"
    int roleCount = root.get("roles").size();   // 2
```

Navigation never throws. `root.get("a").get("b").get("c")` returns an *undefined*
node rather than `null` or an exception, and distinguishes three states that
`Map`-based APIs usually collapse: **missing**, **present but null**, and
**present with a value**. Validation is eager only when you ask for it:

```java
    JsonMixed root = JsonMixed.of(json).getDeclaration();
    // fail-fast on malformed input
    // as soon as any value is accessed
    // which is equivalent to finding the end of a JSON node in accessed the path
```

Typed access uses interfaces over the tree, so no annotations or reflection pass
is needed:

```java
    interface User extends JsonObject {
        String id();
        String name();
    }
    User user = root.as(User.class);
```

---

## When to use it — and when not to

| Use `json-tree` when…                                                  | Reach for something else when…                                                          |
|------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| You parse **large payloads** to process them dynamically               | You need **POJO databind** with annotations (use Jackson or Gson)                       |
| You want to allow **alternative input formats** conveniently           | You only allow **singular input format** (maps well onto a POJO)                        |
| Memory or allocation churn matters (classic server)                    | You think of unused memory as lost potential (cloud deploy)                             | 
| You stream process large files that **still fit in memory**            | You stream **arbitrarily large** files (use a streaming parser)                         |
| You want to **assert qualities of output** conveniently                | You want to **assert exact output** (e.g. via POJO mapping with Jackson)                |
| You want **full control** over validation and error messages           | You are satisfied **tool dependent** mapping error messages                             |
| You want **zero dependencies** or a small audit surface                | You need the full **Jackson ecosystem** (modules, integrations)                         |
| You need seamless integration **query + patch + diff + validate**      | You need **strict** RFC 6902 sequential patch semantics or **full** RFC 9535 compliance |
| You are in a **cold-start-sensitive** or dependency-averse environment | You need to interoperate with existing Jackson/Gson object models                       |

`json-tree` is a process/query/transform tool for the jobs the popular libraries do badly or require multiple dependencies to do.

---

## What's in the box

- **[Rich lazy virtual tree API](docs/json-api-overview.md)** — `JsonMixed`, `JsonObject`, `JsonArray`, and friends
- **[Navigation](docs/json-paths.md)** — never-throwing `get()`, three-state existence (`missing` / `null` / value)
- **[Search](docs/json-search.md)** — RFC 9535-*inspired* path queries
- **[Patch](docs/json-search.md)** — JSON Patch (RFC 6902-*based*, order-independent by design
- **[Diff](docs/json-diff.md)** — structural comparison of two documents
- **[Validation](docs/json-validation.md)** — annotation-on-interface schema style
- **Dialects** — RFC 8259 plus a [JSON5*](docs/spec-json5-star.md) dialect and `NaN`/`Infinity` handling
- **Zero dependencies**, small codebase

Also included, less central: [text templating](docs/spec-text-templating.md), [JURL notation](docs/spec-jurl.md), [input expressions](docs/spec-input-expressions.md).

---

## Installation

```xml
<dependency>
    <groupId>org.hisp.dhis</groupId>
    <artifactId>json-tree</artifactId>
    <version>1.13.0</version>
</dependency>
```

Get the [full into →](docs/intro-1-getting-started.md)

---

## Learn more

- **[Lazy parsing, in depth](docs/intro-2-lazy-parsing.md)** — how the virtual tree works and why it's small
- **[Coming from Jackson or Gson](docs/coming-from-jackson.md)** — side-by-side equivalents
- **[Conformance](docs/rfc-reference.md)** — what standards we support, what we deviate from, and why
- **[Javadoc](https://javadoc.io/doc/org.hisp.dhis/json-tree)**

## License

[BSD 3-Clause License](LICENSE)