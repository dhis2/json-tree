# Comparing JSON values (diff)

**Looking for a simple "are these the same?" check?** If you only need a
boolean answer, see [Equivalence and identity](json-comp.md). This page is
about `diff`, which produces a list of *where* two trees differ. That's the
right tool when the "no" answer needs to explain itself, but it's more work
than necessary when a yes or no will do.

---

When asserting that the JSON returned by a service contains the expected
information, a usual challenge is that the comparison cannot rely on
particulars such as:

- the whitespace (formatting)
- the order of properties in objects
- the order of elements in an array that is a "set"
- the presence of additional object properties
- the exact way numbers are formatted

`JsonValue.diff(JsonValue)` compares two trees structurally and reports where
they differ, ignoring formatting. A `JsonDiff` with an empty `differences()`
list means the two trees are equal under the chosen mode.

## Quick example

```java
    JsonValue expected = JsonValue.of( "[1,2,3]" );
    JsonValue actual   = JsonValue.of( "[1,5,3]" );

    JsonDiff diff = expected.diff( actual );
    diff.differences();   // [!= $.1: 2 <> 5]
```

The list is empty when the trees are equal. Otherwise, each `Difference` names
the path where the two sides diverge and shows the expected value next to the
actual value.

## What a difference looks like

Every `Difference` renders as `<TYPE> <PATH>: <EXPECTED> <> <ACTUAL>`, where `<TYPE>`
is a two-character symbol:

| Symbol | `JsonDiff.Type`  | Meaning                                                            |
|--------|------------------|--------------------------------------------------------------------|
| `!=`   | `NEQ`            | the leaf values differ, or the node types differ                   |
| `--`   | `LESS`           | the actual tree is missing a node the expected tree has            |
| `++`   | `MORE`           | the actual tree has a node the expected tree does not              |
| `>>`   | `SORT`           | object members in a different order; only with strict object order |

For `--` and `++`, `?` stands for the missing side.

Concrete outputs, all taken from the test suite:

    != $: 7 <> 4                       // same type, different value
    != $: null <> []                   // type mismatch at the root
    != $.a.1: 2 <> 5                   // array element
    -- $.2: 678 <> ?                   // actual array is missing an element
    ++ $.2: ? <> 678                   // actual array has an extra element
    ++ $.paga: ? <> 1                  // actual object has an extra member
    -- $.page: 1 <> ?                  // actual object is missing a member

`>>` is the one exception to the "same path on both sides" rule — for a
reordered object it shows both paths as `$expectedPath/$actualPath`. It only
appears when object ordering is strict, which none of the built-in modes
use; see the next section.

## Modes

`JsonDiff.Mode` controls three aspects of the comparison: array order, object
order, and number equality. Three presets cover the common cases:

| Mode      | Arrays                   | Objects                  | Numbers |
|-----------|--------------------------|--------------------------|---------|
| `DEFAULT` | strict order, no extras  | any order, no extras     | numeric |
| `STRICT`  | strict order, no extras  | strict order, no extras  | textual |
| `LENIENT` | any order, any extras    | any order, any extras    | numeric |

The same reordered array under two modes:

```java
    JsonDiff d1 = JsonValue.of( "[1,2,3]" )
      .diff( JsonValue.of( "[1,3,2]" ) );
    d1.differences();
    // [!= $.1: 2 <> 3, != $.2: 3 <> 2]        (DEFAULT — arrays are strict)

    JsonDiff d2 = JsonValue.of( "[1,2,3]" )
        .diff( JsonValue.of( "[1,3,2]" ), JsonDiff.Mode.LENIENT );
    d2.differences();
    // []                                       (LENIENT — arrays are any-order)
```

Number equality differs between `DEFAULT` and `STRICT`: under `DEFAULT`
(`NUMERIC`), `4` and `4.0` are equal; under `STRICT` (`TEXTUAL`), they are not,
because the underlying text differs.

The presets are starting points. `Mode` also exposes fluent builders —
`anyOrder()`, `anyAdditional()`, `arrays(...)`, `objects(...)`,
`numbers(...)` — to change one aspect without giving up the rest of a preset:

```java
    JsonDiff.Mode m = JsonDiff.Mode.STRICT
        .anyOrder()                                // arrays + objects any-order
        .numbers( JsonDiff.Equivalence.NUMERIC );  // numeric equality
```

`STRICT.anyOrder()` is a common choice when order doesn't matter but extra
elements should still be rejected — any reordering passes, but an additional
array element is still reported as `++`.

## Per-property opt-in

Modes apply to the entire comparison. When only specific properties should be
lenient, annotate their `default` methods with `@JsonDiff.AnyOrder` and
`@JsonDiff.AnyAdditional`.

Without an annotation, reordered array elements are reported as differences:

```java
    interface MyObject extends JsonObject {

        default JsonArray tags() {
            return getArray( "tags" );
        }
    }

    MyObject expected = JsonValue.of( """
        {"tags": ["hello", "intro"]}""" ).as( MyObject.class );
    MyObject actual = JsonValue.of( """
        {"tags": ["intro", "hello"]}""" ).as( MyObject.class );

    expected.diff( actual ).differences();
    // [!= $.tags.0: "hello" <> "intro",
    //  != $.tags.1: "intro" <> "hello"]
```

Adding `@AnyOrder` to the property makes the reorder acceptable:

```java
    interface MyObject extends JsonObject {

        @JsonDiff.AnyOrder
        default JsonArray tags() {
            return getArray( "tags" );
        }
    }

    // ... same expected and actual as above ...
    expected.diff( actual ).differences();   // []
```

Annotations override the mode rather than fighting it. A property annotated
`@JsonDiff.AnyOrder` is compared any-order even under `DEFAULT`; a property
annotated `@JsonDiff.AnyOrder(value = false)` is compared strict even under
`LENIENT`. `@JsonDiff.AnyAdditional` works the same way for extra elements or
members.

## Related

- [Equivalence and identity](json-comp.md) — the boolean
  `equivalentTo` / `identicalTo` comparison, when you just want true or
  false
- [User types as JSON tree](intro-6-user-types.md)
- [Validation](json-validation.md)