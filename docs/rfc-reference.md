# Standards and RFCs

This page lists the standards the library implements, and where its behaviour
deviates from them. Deviations are intentional — they trade strict conformance
for a more uniform and easier-to-use API.

## JSON — [RFC 8259](https://datatracker.ietf.org/doc/html/rfc8259)

The library is generally compatible with RFC 8259, with the following
specific behaviours:

- **Duplicate keys**: consistently returns the entry encountered first. (This
  is not necessarily the first if doing advanced iteration from some mid-point.)
- **Member ordering**: always reflects the order in the JSON source.
- **Leading zeros**: are permitted.
- **Trailing commas**: are permitted.
- **Unpaired surrogates**: cause errors.
- **BOM**: is skipped.

The leading-zeros and trailing-commas allowances are extensions — strict
RFC 8259 rejects both.

**Path notation — non-conformance:** The empty path (`JsonPath.SELF`) means
*self* / *root* and cannot be used to select an object property with an empty
name. The alternative bracketed notation `{}` — that is, `{<property>}` —
selects the property with an empty name. A consequence is that property names
beginning with `{` are read as path notation rather than as literal names.
See [Navigating the tree](json-paths.md) for the path notation itself.

## JSONPath — [RFC 9535](https://datatracker.ietf.org/doc/html/rfc9535)

`JsonSelector` (see [Querying the tree](json-search.md)) is inspired by RFC 9535.
Most matchers behave as in the standard. The deviations are:

- **Filters**: only Java `Predicate` expressions are supported, not the
  standard's expression language.
- **Recursive descent**: the `..` semantics differ slightly from the
  standard.
- **Type matcher**: the library adds a non-standard `[?(:type)]` matcher for
  filtering by JSON node type.

## JSON Schema Validation — [Draft 2020-12](https://json-schema.org/draft/2020-12/json-schema-validation)

The library implements only the
[Vocabulary for Structural Validation](https://json-schema.org/draft/2020-12/json-schema-validation#name-a-vocabulary-for-structural)
part of the specification. Because the schema is declared via annotations on
property methods rather than in a JSON schema document, every validation is
declared on the property it affects — not on its parent (as `required` is in
the spec). See [Validation reference](json-validation-reference.md) for the full
list.

**Unsupported:** `minContains` and `maxContains` are not supported.

**Non-conformance — `required` and `dependentRequired`:** neither is satisfied
by a property that is defined `null`, unless also used with `acceptNull()`.

**Extension — `dependentRequired`:** this library's variant supports more
types of conditions than the specification, and therefore uses a slightly
different declaration syntax.

## JSON Pointer — [RFC 6901](https://datatracker.ietf.org/doc/html/rfc6901)

**Clarification:** in the context of JSON Patch, any path segment containing
letters is considered a property name. For example, `/1e0` is not an array
index in exponential notation, but the object property named `"1e0"`.

## JSON Patch — [RFC 6902](https://datatracker.ietf.org/doc/html/rfc6902)

The operation set of RFC 6902 (`add`, `remove`, `replace`, `move`, `copy`,
`test`) is supported, with extensions and deviations described below. The
feature itself is documented in [JSON Patch](json-patch.md); this section
summarises conformance.

**Extensions:**

- Two additional operations: `spread` (bulk insert of array elements or
  object members at one path) and `swap` (exchange two values in place).
- Additional aliases: `insert` for `add`, `delete` for `remove`, plus short
  forms `+`, `-`, `~`, `>`, `?`.
- An array-of-segments path syntax alongside RFC 6901 JSON Pointer strings.
  Array segments support negative indexes, ranges, enumerations, and
  conditions. See [JSON Patch](json-patch.md) for the segment types.
- An optional `test` field on any single-target operation, giving
  compare-and-swap semantics without a separate operation.

**Deviations:**

- **Execution model**: order-independent. A patch is a *set* of operations,
  all resolved against the original target before any edit is applied. Any
  permutation of the same operations produces the same result. This is the
  single biggest difference from RFC 6902 and the cause of most of the
  smaller deviations below.
- **Index semantics**: array indexes, ranges, and conditions all refer to the
  original array, not the current state. A `move` rightward within an array
  uses the pre-removal position rather than the shortened post-removal one.
- **Clash detection**: two operations writing to the same path, or to a path
  and its ancestor, are rejected up front. There is no last-write-wins.
- **`remove` on a missing target**: no-op. RFC 6902 raises an error.
- **`replace` on a missing object member**: adds the member. RFC 6902 raises
  an error.
- **`insert` into a missing object parent**: auto-creates the intermediate
  objects. RFC 6902 does not.

**What works identically:** any patch using only JSON Pointer paths where no
path is a prefix of another. That covers the overwhelming majority of
real-world RFC 6902 patches — the deviations only surface when array mutation
ordering matters, or when a patch deliberately relies on sequential behaviour.

## JSON Merge Patch — [RFC 7386](https://datatracker.ietf.org/doc/html/rfc7386)

`JsonValue.overlay(JsonValue)` (see [Masking and overlaying](json-mask-overlay.md))
implements RFC 7386 merge-patch semantics. The default behaviour is
conformant: object sources recurse when the target is also an object, `null`
members remove, everything else replaces, and members of the target not
mentioned in the source are kept.

**Extension — `OverlayNull.REPLACE`:** RFC 7386 has no way to express "set this
member to `null`" — a `null` in the merge patch always removes the member. The
optional `OverlayNull.REPLACE` mode adds this, treating a `null` member as data
rather than a deletion marker. The default (`OverlayNull.REMOVE`) matches the
RFC.

**Clarification — empty objects:** an empty object in the merge patch is a
no-op, not a "clear". RFC 7386 iterates the *source*'s members, and `{}` has
none. To clear a subtree, use a `null` member or mask the path (see
[Masking and overlaying](json-mask-overlay.md)).

**Clarification — top-level non-object sources:** a non-object top-level source
(array, scalar, `null`) replaces the whole document. Object-merge only applies
when both source and target are objects. This is RFC-conformant but easy to
miss when reading "merge" in the name.

## JSON5 — [official specification](https://json5.github.io/json5-spec/)

JSON5 is not an IETF RFC. It is a community specification — *The JSON5 Data
Interchange Format*, version 1.0.0 (March 2018) — describing a proposed
extension to JSON. The library implements a restricted variant of it, called
JSON5*, documented separately in [JSON5* notation](spec-json5-star.md).

The design goal of JSON5* is that any accepted document can be converted to
standard JSON **without inserting characters** — only deleting or replacing
characters in place. That keeps the parser's offsets valid against the original
source text, and preserves the lazy-parsing and memory characteristics described
in [Lazy parsing](intro-2-lazy-parsing.md). Every restriction below follows from that
constraint.

**JSON5 features not supported:**

- Unquoted object keys. All keys must be quoted.
- Hexadecimal numbers.
- Numbers with a leading decimal point (`.5`) or a trailing decimal point
  (`5.`).
- Whitespace other than space, tab, line feed and carriage return.

**JSON5 features supported, with in-place transformation:**

- Trailing commas in arrays and objects — blanked to a space.
- Comments.
- Single-quoted strings. Inside them, an unescaped `"` is rewritten to `'`,
  and `\"` is left as-is, so the outer quotes can be swapped without growing
  the text. Escaping `'` with `\'` is not supported.
- The literals `NaN`, `Infinity` and `-Infinity`, but only where the
  surrounding whitespace leaves room to turn the literal into the JSON string
  `"NaN"` / `"Infinity"` / `"-Infinity"` in place. `[NaN]` is rejected;
  `[ NaN ]` is accepted. The `JsonNode` and `JsonValue` APIs handle this
  transparently — `doubleValue()` on such a node returns the number, not the
  string.

## Related

- [Navigating the tree](json-paths.md) — `JsonPath`
- [Querying the tree](json-search.md) — `JsonSelector` and RFC 9535
- [Validation reference](json-validation-reference.md) — JSON Schema vocabulary
- [JSON Patch](json-patch.md) — the patch feature in detail
- [Masking and overlaying](json-mask-overlay.md) — `mask` and `overlay` (RFC 7386)
- [JSON5* notation](spec-json5-star.md) — the JSON5 variant