# JSON Patch

## Quick Reference
To patch means to apply a set of modification operations to a JSON document, 
producing a new value with the original left unchanged.

The entry point to this bulk-editing API is `JsonValue.patch`. 

Unlike [RFC 6902](https://datatracker.ietf.org/doc/html/rfc6902), a patch here is **not** a sequence of mutations. 
It is a **set** of operations, all of which reference the target *before* any change is applied.

By design this results in:

- **Order of operations does not matter.** Any permutation of the same operations produces the same result.
- **Clashes are detected up front.** Two operations that would compete for the same path are rejected before any edit is attempted.
- **Single-pass application.** The result can and is produced by copying slices of the source text with no intermediate JSON tree ever materialized.

If two operations write to the same path — or to a path and its ancestor — the patch is rejected. 
There is no "last write wins", no silent override, no guessing what the client meant.

### Demo

```java
	JsonMixed target = JsonMixed.of("""
	    {"name": "Alice", "tags": ["a", "b"]}""");

	JsonPatch patch = JsonMixed.of("""
	    [
	      {"op": "replace", "path": ["name"],  "value": "Bob"},
	      {"op": "insert",  "path": ["tags", -0], "value": "c"}
	    ]""").as(JsonPatch.class);

	JsonMixed result = target.patch(patch);
	// = {"name": "Bob", "tags": ["a", "b", "c"]}
```

`-0` means "append to array". Negative indexes count from the end of the *original* array, not the current one.

### Operations

| op        | aliases       | purpose                  | notes                                                                             |
|-----------|---------------|--------------------------|-----------------------------------------------------------------------------------|
| `insert`  | `add`, `+`    | Insert a value           | On existing object member: replaces value. On array index: inserts *before* index |
| `remove`  | `delete`, `-` | Delete a value           | No-op if the target doesn't exist                                                 |
| `replace` |               | Replace a value          | Missing object key → adds member; missing array slot → error                      |
| `spread`  | `~`           | Insert multiple elements | Array: splice elements at index. Object: merge members                            |
| `move`    | `>`           | Move a value             | Source removed, target inserted                                                   |
| `copy`    |               | Copy a value             | Source unchanged                                                                  |
| `swap`    |               | Exchange two values      | Both must exist                                                                   |
| `test`    | `?`           | Assert a precondition    | Test path(s) for being defined or equal to a value, details below                 |

RFC 6902 aliases are accepted: `add` → `insert`, `delete` → `remove`. 

### Paths

Every operation carries a `path` (plus `from` / `to` where relevant). 
Two equivalent forms:

* `[...]`:  array of path segments; no escaping needed, advanced selectors
* `"..."`: a _JSON Pointer_ string (RFC 6901) — for RFC 6902 compatibility

```json
	{"op": "remove", "path": ["users", 0, "roles", 2]}
```
```json
	{"op": "remove", "path": "/users/0/roles/2"}
```

Inside a path array, a segment encodes targeting intent by its JSON shape:

| type                            | shape             | targets                      | meaning                                    |
|---------------------------------|-------------------|------------------------------|--------------------------------------------|
| _key_                           | `"foo"`           | object member                | object member (literal key)                |
| _index_                         | `5`               | array index                  | array index                                |
| _index from end_                | `-1`              | array index                  | index from end (`-0` = append)             |
| _index range_                   | `[[0, 2]]`        | array index range            | inclusive range                            |
| _index enumeration_ `[0, 2, 4]` | array indexes     | scattered indexes            |                                            |
| _index condition_ `["=", 3]`    | array index       | first element equal to value |                                            |
| _index condition shorthand_     | `[true]`, `["a"]` | array index                  | shorthand for `["=", true]`, `["=", "a"]`  |
| _index object condition_        | `[{"id": "x"}]`   | array of objects index       | first object element with matching members |

Note:
* Ranges and enumerations are only permitted at the end of the path (as they may result in multiple targets).
* Conditional index selection always resolves to a single index and may appear anywhere.
* Index numbers in any shape can be negative to resolve from the end (`-0` is the equivalent to JSON Pointer's append marker `-`) 
* Indexes, ranges, and conditions all resolve against the original target — a condition doesn't see values inserted by another operation in the same patch.
* Object conditions can have multiple key:value pairs (all must match)
* Shorthand condition cannot be used to match numbers as `[42]` is an index enumeration of just 42 (=> `["=" 42]`)

### Preconditions

Any op that writes a single value accepts an optional `test` field:

```json
	{"op": "replace", "path": ["balance"], "value": 100, "test": 50}
```

The write happens **only if** `/balance` currently equals `50`. Otherwise, the entire patch throws. 
This makes the operation a compare-and-swap — a natural guard against concurrent modification and a standard idempotency primitive.

The equivalent standalone `test` operation is:

```json
	{"op": "test", "path": ["balance"], "value": 50}
```

Using a standalone `test` operation has 2 benefits that are not accessible via the `test` field:
1. it may target multiple values
2. it can omit the `value` to test if the target is **defined** (present and not JSON `null`)

All tests are evaluated **before** any mutation is performed.

### Caveats

When `replace` targets a range or enumeration, the value's shape determines how it's applied:

| shape of `value`   | effect                                                |
|--------------------|-------------------------------------------------------|
| scalar             | broadcast — every target gets this value              |
| array, same length | aligned position-by-position                          |
| array, shorter     | aligned; excess targets removed                       |
| array, longer      | **ranges only** — excess values spread past the end   |
| array, longer      | **enumerations** — error (no coherent "past the end") |

To collapse several targets into one, use an array-of-one: `Replace([1:3], [99])` shrinks elements 1..3 to a single `99`.

**Replace - duplicate keys.** When replacing an object member that is a duplicate key the operation throws unless the key is the first member in which case it removes the first occurence of the key.

**Insert - no array parent auto-created.** `insert` into a missing object parent creates the parent. `insert` into a missing array parent throws — the array must exist first.

**Single-target ops reject multi-target paths.** `insert`, `spread`, `move`, `copy`, `swap` and the CAS `test` field reject ranges and enumerations.

### RFC 6902 Compatability

| aspect                          | RFC 6902                        | This library                          |
|---------------------------------|---------------------------------|---------------------------------------|
| Execution                       | sequential                      | order-independent set                 |
| Path syntax                     | JSON Pointer only               | JSON Pointer **or** array of segments |
| Index semantics                 | current state                   | original state                        |
| Clash detection                 | implicit (failure at apply)     | explicit (validated up front)         |
| `remove` on missing             | error                           | no-op                                 |
| `replace` on missing object key | error                           | adds member                           |
| `add` on existing object key    | replaces                        | replaces (same)                       |
| Array `add` past end            | error                           | error (except `size` = append)        |
| `move` rightward in array       | sequential (post-removal index) | original (pre-removal index)          |
| `test` numeric equality         | numeric                         | structural (`1` ≈ `1.0`)              |
| Auto-create object parents      | no                              | yes                                   |

**What works identically:** any patch using only JSON Pointer paths where no path is a prefix of another. That's the overwhelming majority of real-world RFC 6902 patches — the differences only surface when array mutation ordering matters, or when a patch deliberately relies on sequential behavior.

**How application differs:** clash detection up front, order-independence, single-pass application with predictable memory. There is no way to write a silently-wrong patch — either it applies correctly or it throws.

## Spec

This section documents the semantics of path expressions and operations in
detail. It assumes you have read the Quick Reference and are looking for the
precise behavior of a particular path form or operation.

### Path Expressions

A path is either an **array of segments** or a **JSON Pointer string**. The
array form is the primary form; the string form exists for RFC 6902
compatibility. Both forms can be used freely within one patch, but each
individual path is one or the other.

Each segment in the array form is interpreted by its JSON shape. There are
seven distinct segment types.

#### Object member — string

```json
	["users", "name"]
```

Addresses an object member by its literal key. No escaping is needed — keys can
contain `.`, `[`, `"`, and so on without any encoding. This is one
advantage of the array form over JSON Pointer strings.

#### Array index — integer

```json
	["items", 2]
```

Addresses an array element positionally, 0-based. A non-negative index refers
to an existing element position in the **original** array.

#### Index from end — negative integer

```json
	["items", -1]
```

Counts from the end of the array. `-1` is the last element, `-2` the
second-to-last, and so on. `-0` is the position **one past** the last element —
the append slot.

The distinction between `0` and `-0` matters:

```json
	["items", 0]    // first element position
	["items", -0]   // append slot, after the last element
```

Both resolve against the original array's length. In a patch that both removes
and inserts, `-0` always refers to the original array's tail. Underflow (a
negative index that resolves below 0) is an error.

#### Array range — `[[from, to]]`
Shape: an array of 2 integers within an array: 

```json
	["items", [[1, 3]]]
```

A contiguous span of array elements, both bounds **inclusive**. Resolves to
multiple paths, one per element in the range. Both bounds may be negative.

A range that resolves to nothing — because both bounds are past the end, or
because the parent array is empty — produces no targets.

#### Array enumeration — `[i1, i2, ...]`
Shape: an array of integers of any length 

```json
	["items", [0, 2, 4]]
```

A set of specific array indexes. Resolves to multiple paths, one per listed
index. Elements may be negative. An empty array `[]` is a valid enumeration
with no entries.

Enumerations are the way to address a scattered selection of elements — the
conceptual equivalent of a range but without the contiguous constraint.

#### Object condition — `[{"key": value, ...}]`
Shape: an array of one object
```json
	["users", [{"id": "alice", "status": "active"}]]
```

Selects the **first element** of an array of objects whose members
structurally match all of the given key-value pairs. Additional members on the
target element are ignored. Multiple key-value pairs narrow the match — all
must be satisfied.

If no element matches, the condition resolves to no path. For `remove` this
is a no-op; for `replace` it's an error, since the client's assumption about
the target is stale.

Object conditions are the primary tool for **set-semantic** manipulation of
arrays — addressing elements by their logical identity rather than by
position.

#### Value condition — `["=", value]` and shorthand
Shape: an array with first element `"="` and any second value to compare with

Shorthand shape: just an array of the value to compare with (for non-numeric primitives)

```json
	["items", ["=", 42]]
	["items", ["=", "hello"]]
```

Selects the first element of an array that is structurally equal to the given
value. Works on arrays of any element type — scalars, objects, arrays.

For non-numeric primitives values a shorthand is available: 

```json
	["items", [true]]     // same as ["=", true]
	["items", ["hello"]]  // same as ["=", "hello"]
	["items", [null]]     // same as ["=", null]
```

Numbers do not have a shorthand — `[42]` is an enumeration of the single index
42, not a value condition for 42. Use `["=", 42]` explicitly.

### Path Caveats

#### Multi-target and tail-only constraint

Ranges and enumerations potentially resolve to multiple paths. 
To keep targets linear ranges and enumerations must be
the **last** segment of their path.

```json
	["items", [[0, 2]], "name"]     // error
	["items", [0, 2], "name"]       // error
```

Conditions resolve to a **single** index and may appear anywhere:

```json
	["users", [{"id": "alice"}], "email"]     // ok
	["users", [{"id": "alice"}], "roles", 0]  // ok
```

#### Resolution against the original

Every path, index, range, and condition resolves against the target **before
any edit is applied**. A condition does not see values inserted by another
operation in the same patch, and an index does not shift because an earlier
operation removed an element.

```json
	[
	  {"op": "remove", "path": ["items", 0]},
	  {"op": "replace", "path": ["items", 1], "value": "x"}
	]
```

On an `items` array of `["a", "b", "c"]`, this removes `"a"` and replaces `"b"` with `"x"`, 
giving `["x", "c"]`. The `replace` at index 1 refers to the original `"b"`, not the
post-removal `"c"`. This is what makes the patch order-independent.

#### JSON Pointer paths

The string form follows RFC 6901:

- `/` separates segments
- `~1` escapes `/`, `~0` escapes `~`
- `-` on an array parent means "append position" (equivalent to `-0`)
- `""` (empty string) means the root

```json
  {"op": "remove", "path": "/items/2"}
  {"op": "insert", "path": "/items/-", "value": "new"}
```  

Only single-target addressing is possible in this form — no ranges,
enumerations, or conditions. It is the standard form for interoperability with
RFC 6902.

#### Guards via conditions

Conditions let a patch express "do X to *this* element" rather than "do X to
element at index N" — the correct primitive when the array's shape isn't known
in advance:

```json
	{"op": "replace", "path": ["users", [{"id": "alice"}], "role"], "value": "admin"}
```

This targets the `role` member of whichever user has `id` equal to `"alice"`,
regardless of their position. If no such user exists, the patch fails — the
client's assumption about the target was wrong.

Combine a condition with the `test` field to make the whole operation
conditional:

```json
	{"op": "replace",
	 "path": ["users", [{"id": "alice"}], "role"],
	 "test": "guest",
	 "value": "admin"}
```

Now the promotion to `admin` only happens if alice is currently a `guest`.
This is a compare-and-swap on the role member.

### Operations

Every operation is one of eight types. This section describes each operation's
semantics precisely.

#### The `insert`-Operation

Adds a value.

- **Object parent, existing member**: the member's value is replaced.
- **Object parent, missing member**: the member is appended.
- **Array parent**: the value is inserted **before** the addressed index,
  shifting later elements right. `-0` appends at the end.
- **Missing object parents are auto-created.** `["a", "b", "c"]` on `{}`
  produces `{"a": {"b": {"c": value}}}`.
- **Missing array parents are not auto-created.** The array must exist.

Insertion-before-index is what distinguishes `insert` from `replace` on
arrays:

```json
	{"op": "insert", "path": ["items", 1], "value": "x"}
	// "items" array ["a", "b", "c"] → ["a", "x", "b", "c"]

	{"op": "replace", "path": ["items", 1], "value": "x"}
	// "items" array ["a", "b", "c"] → ["a", "x", "c"]
```

#### The `remove`-Operation

Deletes the value at the path.

- **No-op if the path doesn't exist.**
- **Multiple targets** from a range or enumeration remove each resolved
  target.

The no-op behavior is deliberate: `remove` expresses "ensure this is gone,"
and absence already satisfies that. Combined with conditions, this lets a
patch safely attempt cleanup without requiring the caller to check existence
first. A `test` can be used to explicitly require existence or value equality. 

#### The `replace`-Operation

Replaces the value at the path.

- **Existing target**: value at path is replaced.
- **Missing object key**: the member is added (same as `insert`).
- **Missing array position**: error.
- **Multiple targets** from a range or enumeration: see Multi-target Replace
  below.

The dual behavior on objects is a deliberate convergence with `insert`: a
client that knows "this key should have this value" doesn't need to branch on
whether the key currently exists. It also makes `replace` composable with
`remove` — the combination `remove + insert` on the same path is precisely
what `replace` represents. Client use it to express their intent of 
replacing a target (as `remove + insert` on the same target is an error
as is unclear whether the same target is deliberate or accidental). 


#### The `spread`-Operation

Bulk-inserts from a structured value.

- **Array parent, array value**: the `value`'s elements are spliced in before
  the addressed index.
- **Object parent, object value**: the `value`'s members are merged in — existing
  keys are replaced, new keys are appended.

```json
  {"op": "spread", "path": ["items", 1], "value": [10, 20, 30]}
  // "items" array ["a", "b", "c"] → ["a", 10, 20, 30, "b", "c"]
```

The value must be an array or an object. Scalar values are rejected.

`spread` is the way to insert multiple elements at a single position.
Multiple `insert` operations at the same index would clash — they claim the
same target path. `spread` claims one path and inserts many elements at it.
This is again to be explicit about intent as it is unclear if 
multiple inserts on the same target is deliberate or accidental. 

Missing object parents are auto-created, same as `insert`.

#### The `move` Operation

Removes the value at `from` and inserts it at `to` (or `path`; see the RFC
6902 fallback below).

- **No-op if `from` doesn't exist.**
- **No-op if `from` equals `to`.**
- **Both paths resolve against the original target.**

For arrays, `move` rightward within the same array is subtle:

```json
	{"op": "move", "from": ["items", 0], "to": ["items", 2]}
	// "items" array ["a", "b", "c"] → ["b", "a", "c"]
```

The `to` index refers to the original array's slot 2 (before `"c"`), not the
shortened post-removal array's slot 2. To move an element past others, address
the position one past the target, or use `-0` to append. See the
"Compared to RFC 6902" section for how this differs from sequential semantics.

#### The `copy`-Operation

Inserts a copy of the value at `from` at `to` (or `path`; see the RFC
6902 fallback below).

- **No-op if `from` doesn't exist.**
- **Source is unchanged.**
- **Value is read from the original target** — the snapshot is taken before
  any edit is applied.

Because the read is against the original, `copy` composes predictably with
other operations that modify the source path:

```json
	[
	  {"op": "copy",    "from": ["a"], "to": ["b"]},
	  {"op": "replace", "path": ["a"], "value": 99}
	]
```

produces `{"a": 99, "b": originalA}` — `b` gets the pre-patch value of `a`.

#### The `swap`-Operation

Exchanges the values at `from` and `to` (or `path`; see the RFC
6902 fallback below).

- **No-op if either side is missing.**
- **No-op if `from` equals `to`.**
- **Values may be of any shape and at any depth.**

```json
  {"op": "swap", "from": ["a"], "to": ["b"]}
  // {"a": 1, "b": 2} → {"a": 2, "b": 1}
```

#### The `test`-Operation

Asserts a precondition without modifying anything.

A standalone `test` operation passes under one of three modes:

- **`value` omitted**: the target path is **defined** — present and not JSON
  `null`.
- **`value` is `null`**: the target path is exactly JSON `null`.
- **`value` is defined**: the target is structurally equal to the value.

The three modes let a client distinguish "is there anything here" from "is
there specifically a null or specific value here":

```json
	{"op": "test", "path": ["a"]}                 // defined (not null, not missing)
	{"op": "test", "path": ["a"], "value": null}  // exactly null
	{"op": "test", "path": ["a"], "value": 42}    // exactly 42
```

Multiple targets (from a range or enumeration) each must pass.

#### The `test` Field

Any operation that writes a single value accepts an optional `test` field:

```json
	{"op": "replace", "path": ["balance"], "test": 50, "value": 100}
```

This is the same check as a standalone `test` at the same path with the
same value. The write only proceeds if the test passes; otherwise the whole
patch throws.

The `test` field is allowed on `insert`, `remove`, `replace`, `move`, and
`copy`. It is rejected on `test` (redundant), `spread` (no single target), and
`swap` (two targets, ambiguous).

The idiomatic use is a compare-and-swap style change:

```json
	{"op": "replace", "path": ["status"], "test": "draft", "value": "published"}
```

Applying this patch twice: the first application transitions the state, the
second fails the test and produces no change. Combined with handling
`JsonPatchTestException`, this gives idempotency without any external
coordination.

### Operation Caveats

#### Multi-target Replace

When `replace` targets a range or enumeration, the resolved paths form a set
of values to update. The shape of `value` determines how they're matched.

**Scalar value** — broadcast to every target:

```json
	{"op": "replace", "path": ["flags", [[0, 3]]], "value": false}
```

Sets every flag in the range to `false`.

**Array value, same length** — aligned position-by-position:

```json
	{"op": "replace", "path": ["items", [[1, 3]]], "value": [10, 20, 30]}
	// replaces elements 1, 2, 3 with 10, 20, 30 respectively
```

**Array value, shorter than targets** — aligned, with excess targets removed:

```json
	{"op": "replace", "path": ["items", [[1, 3]]], "value": [10]}
	// replaces element 1 with 10, removes elements 2 and 3
```

This is the compact form for **shrinking** a contiguous span into a shorter
sequence, and the reason scalar broadcasts rather than collapses — collapsing
many targets into one is expressed by array-of-one, not by scalar.

**Array value, longer than targets** — this depends on the target type:

- For a **range**, the excess values are inserted after the last target,
  extending the span:

```json
	{"op": "replace", "path": ["items", [[1, 2]]], "value": [10, 20, 30, 40]}
	// replaces 1 and 2 with 10 and 20, then inserts 30 and 40 after them
```

- For an **enumeration**, this is rejected. A scattered selection has no
  coherent "past the end" position, so the operation would be ambiguous.

**Collapsing multiple targets into one** uses array-of-one:

```json
	{"op": "replace", "path": ["items", [[1, 3]]], "value": [99]}
	// three original elements become one `99`
```

#### Two-phase execution

A patch runs in two phases:

1. **All tests run first.** Every standalone `test` and every `test` field is
   evaluated against the original target. Any failure aborts the entire patch
   before any edit is performed.

2. **All mutations are applied.** Each mutation is translated to text edits
   against the original; The output is composed though edits in a single pass.

The consequence: regardless of where the `test` appears in the operation list
a patch with a failing test is a read-only operation where no work has been 
performed to write the patched result.

#### Set-semantic array manipulation

The combination of conditions and the operations above gives a set-based view
of arrays: elements can be addressed by their identity, not their position.

**Remove an element by identity:**

```json
	{"op": "remove", "path": ["users", [{"id": "alice"}]]}
```

No-op if alice isn't present.

**Replace a member of an element by identity:**

```json
	{"op": "replace", "path": ["users", [{"id": "alice"}], "role"], "value": "admin"}
```

Error if alice isn't present — the client's assumption about the array's
contents is stale.

**Conditionally promote only if the current state matches:**

```json
	{"op": "replace",
	 "path": ["users", [{"id": "alice"}], "role"],
	 "test": "tester",
	 "value": "admin"}
```

Fails the whole patch if alice's current role isn't `"tester"`.

**Remove multiple specific elements in one patch:**

```json
	[
	  {"op": "remove", "path": ["users", [{"id": "alice"}]]},
	  {"op": "remove", "path": ["users", [{"id": "bob"}]]}
	]
```

Both operations resolve independently against the original array, so neither
shifts the other. Each is a no-op if its target isn't present. The claim
checker verifies that the two resolved indexes don't overlap, 
otherwise the patch would be rejected.

This pattern is the reason patches are order-independent: the client
describes *what* they want changed in terms of the data's identity, and the
implementation works out *where* those changes land.