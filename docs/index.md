# json-tree documentation

Start here.

## Learn

- [Getting started](intro-1-getting-started.md) — coordinates and a first example.
- [Lazy parsing and the virtual tree](intro-2-lazy-parsing.md) — what "virtual" and "lazy" mean here.
- [JSON API](intro-3-json-from-text.md) — build a tree from a JSON document.
- [Java API](intro-4-json-from-java.md) — build a tree from Java types.
- [Null, undefined and existence](intro-5-null-and-undefined.md) — the three-state model.
- [User types as JSON tree](intro-6-user-types.md) — extend the tree with `interface`s.
- [Performance and streaming](intro-7-json-performance.md) — choosing an `Index` mode
    for large-container iteration.
 
## Discover

- [API overview](json-api-overview.md) — most used API methods overview.
- [Navigating the tree](json-paths.md) — paths as a uniform way to drill down.
- [Querying the tree](json-search.md) — finding nodes using matchers.
- [Validation](json-validation.md) — check input against a target schema.
- [Equivalence and identity](json-comp.md) — comparing two trees for sameness.
- [Comparing JSON values (diff)](json-diff.md) — structural comparison of two trees.
- [Modification](json-patch.md) — bulk patching JSON using patch operations 
- [Mask & Overlay](json-mask-overlay.md) — masking JSON or patch merge with another JSON value

## Reference

- [Standards and RFCs](rfc-reference.md) — implementations and deviations.
- [JSON5* notation](json5-star-reference.md) — the JSON5 variant accepted by the parser.
- [JURL notation](jurl-reference.md) — JURL = JSON in the URL; a notation to encode JSON data in URL parameters
- [Input Expressions](input-expressions-reference.md) — a RegEx alternative that is simple, safe and fast

APIs in depth

- [Validation reference](json-validation-reference.md) — every validation attribute
  and its semantics.

## Maintainers

- [Release Process](release.md) — how a release is made