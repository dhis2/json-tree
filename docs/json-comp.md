# Comparing JSON values — equivalence and identity

`JsonValue` has two boolean comparison methods:

- **`equivalentTo(other)`** — same information, ignoring how it was formatted
  or encoded
- **`identicalTo(other)`** — same information *and* same encoding where
  encoding matters (number representation, object member order)

Both are `@TerminalOp(canBeUndefined = true)` — they don't throw when either
value is undefined, and both are symmetric: `a.equivalentTo(b)` returns the
same as `b.equivalentTo(a)`, and likewise for `identicalTo`. They differ from
[diff](json-diff.md), which reports *what* differs and requires both sides to
exist.

## Contains same information - `equivalentTo`

`equivalentTo` answers: *do these two trees carry the same information?*

**Ignored:**

- whitespace (in any position)
- the order of object members
- the exact way a number is written (`1` and `1.0` are equivalent)
- escaped vs literal characters inside a string, when the decoded value is
  the same

**Respected:**

- JSON type — an object is never equivalent to an array or a string
- array element order — arrays are compared positionally
- the distinction between `null` and undefined — a missing field is not
  equivalent to a field whose value is `null`

Examples:

```java
    JsonMixed a = JsonMixed.of( "{\"a\": 1, \"b\": 2}" );
    JsonMixed b = JsonMixed.of( "{\"b\": 2, \"a\": 1}" );
    a.equivalentTo( b );   // true — same members, different order

    JsonMixed c = JsonMixed.of( "1" );
    JsonMixed d = JsonMixed.of( "1.0" );
    c.equivalentTo( d );   // true — same numeric value

    JsonMixed e = JsonMixed.of( "[1, 2]" );
    JsonMixed f = JsonMixed.of( "[2, 1]" );
    e.equivalentTo( f );   // false — arrays are compared positionally

    JsonMixed g = JsonMixed.of( "{\"x\": 1}" );
    JsonMixed missing = g.get( "missing" );
    g.equivalentTo( missing );   // false — one exists, the other doesn't
```

## Exact JSON but ignore formatting - `identicalTo`

`identicalTo` is stricter. It requires everything `equivalentTo` requires,
plus:

- numbers compared by their raw text — so `1` and `1.0` differ, and `1e0`
  differs from `1`
- object members compared in source order — `{"a":1,"b":2}` and `{"b":2,"a":1}`
  differ

Whitespace differences are still ignored — they're outside the values.

```java
    JsonMixed a = JsonMixed.of( "{\"a\": 1, \"b\": 2}" );
    JsonMixed b = JsonMixed.of( "{ \"a\": 1, \"b\": 2 }" );
    a.identicalTo( b );   // true — whitespace only

    JsonMixed c = JsonMixed.of( "{\"a\": 1, \"b\": 2}" );
    JsonMixed d = JsonMixed.of( "{\"b\": 2, \"a\": 1}" );
    c.identicalTo( d );   // false — member order differs

    JsonMixed e = JsonMixed.of( "1" );
    JsonMixed f = JsonMixed.of( "1.0" );
    e.identicalTo( f );   // false — number representation differs
```

## How they relate

`identicalTo` implies `equivalentTo`: every pair that is identical is also
equivalent. The reverse does not hold — anything that differs only in number
representation or object member order is equivalent but not identical.

For most "same document?" checks, reach for `equivalentTo`. Use `identicalTo`
when the exact form matters — round-trip verification, content-addressed
caching, or reproducing a source document byte-for-byte.

## Summary

| Pair                                        | `equivalentTo` | `identicalTo` |
|---------------------------------------------|:--------------:|:-------------:|
| `1` vs `1`                                  |       ✓        |       ✓       |
| `1` vs `1.0`                                |       ✓        |       ✗       |
| `true` vs `true`                            |       ✓        |       ✓       |
| `"a"` vs `"a"`                              |       ✓        |       ✓       |
| `null` vs `null`                            |       ✓        |       ✓       |
| `null` vs undefined                         |       ✗        |       ✗       |
| undefined vs undefined                      |       ✓        |       ✓       |
| `[1, 2]` vs `[1, 2]`                        |       ✓        |       ✓       |
| `[1, 2]` vs `[2, 1]`                        |       ✗        |       ✗       |
| `[1, 2]` vs `[1.0, 2.0]`                    |       ✓        |       ✗       |
| `{"a": 1}` vs `{"a": 1}`                    |       ✓        |       ✓       |
| `{"a": 1, "b": 2}` vs `{"b": 2, "a": 1}`    |       ✓        |       ✗       |
| `{"a": 1}` vs `{"a": 1.0}`                  |       ✓        |       ✗       |
| `{"a": 1}` vs `{"b": 1}`                    |       ✗        |       ✗       |
| `9007199254740993` vs `9007199254740992`    |       ✗        |       ✗       |
| `9007199254740993` vs `9007199254740993.00` |       ✓        |       ✗       |

## Known limitations

- **Float precision.** `equivalentTo` compares non-integer numbers as Java
  `double`s. Pairs of values that round to the same `double` — either because
  they exceed 2<sup>53</sup> in magnitude, or because of floating-point
  rounding at any scale — compare equal even when their decimal
  representations differ. When the exact source form matters, use
  `identicalTo`, which compares raw text.

- **Large integers vs scientific notation.** Integer-valued numbers are
  compared by their normalized digit text, so `9007199254740993` and
  `9007199254740992` are correctly distinguished, and `9007199254740993` and
  `9007199254740993.00` are correctly equivalent. But if one side is in
  scientific notation (`9.007199254740993e15`), it falls back to `double`
  comparison and may round to the other side. This is a residual edge case;
  it doesn't affect integers written in plain decimal form.

- **Signs on integers.** `equivalentTo` compares integer values by their
  normalized digit text, so `+42`, `42`, `42.00`, and `042` all compare
  equal. `+` never appears in a JSON number, so this is defensive rather
  than essential — but it means the comparison behaves consistently for any
  numeric text a caller might construct.

- **String escapes.** Two strings with the same decoded value but different
  escapes (e.g. `"a\u0062"` vs `"ab"`) compare identical, because string
  comparison works on the decoded value. If the exact source form of a string
  matters, compare `getDeclaration()` values directly.

## Related

- [Comparing JSON values (diff)](json-diff.md) — structural comparison that
  reports *where* two trees differ, not just whether they do
- [Null, undefined and existence](intro-5-null-and-undefined.md) — the
  `exists` / `isUndefined` / `isNull` distinction that both comparisons
  respect
- [JSON API overview](json-api-overview.md)