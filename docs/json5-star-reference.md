# JSON5‑ish Notation Specification

This library implements a lightweight variant of JSON5 coined JSON5*.  
The syntax is designed so that any valid document can be transformed 
into standard JSON **without inserting characters**.
This ensures low overhead while offering convenient JSON5‑like syntax.

---

## Differences to JSON5 

The following JSON5 features are **not** supported:

- **Unquoted object keys** – all keys must be quoted.
- **Hexadecimal numbers** – only decimal numbers are allowed.
- **Numbers with a leading decimal point** (e.g., `.5`).
- **Numbers with a trailing decimal point** (e.g., `5.`).
- **Whitespace characters other than** space (` `), tab (`\t`), line feed (`\n`), and carriage return (`\r`).
 
The following JSON5 features **are** supported with special transformation rules:

- **Trailing commas** in arrays and objects.
- **Comments**.
- **Single‑quoted strings** (in addition to double‑quoted JSON strings).
- **The literals `NaN`, `Infinity`, `-Infinity`** under specific conditions.

---

## Syntax Rules

### 1. Whitespace

Only the following whitespace characters are allowed (JSON standard):

| Character | Code Point | Name          |
|-----------|------------|---------------|
| space     | U+0020     | space         |
| `\t`      | U+0009     | horizontal tab|
| `\n`      | U+000A     | line feed     |
| `\r`      | U+000D     | carriage return|

No other whitespace may appear.

### 2. Strings

Strings may be written in two ways:

- **Double‑quoted strings**: Follow the JSON string syntax exactly.  
  These are already valid JSON and are passed through unchanged.
- **Single‑quoted strings**: Follow the same escape rules as JSON, but delimited by `'`.

When a single‑quoted string is transformed to JSON, the following substitutions are applied **without inserting any characters**:

| Input (inside single‑quoted string) | Output (inside double‑quoted JSON) | Explanation |
|--------------------------------------|-------------------------------------|-------------|
| `"` (unescaped double quote)         | `'` (single quote)                 | Prevents the need to insert a backslash escape. |
| `\"` (escaped double quote)          | `\"` (escaped double quote)        | Already escaped; remains as a double quote in JSON. |
| Any other content                    | unchanged                          | All other characters (including `\n`, `\t`, etc.) remain as in JSON. |

After these substitutions, the outer single quotes are replaced with double quotes to produce a standard JSON string.
Note that single quotes do not allow to escape the single quote with `\'`.

#### Examples

| Input (single‑quoted) | Transformed JSON String |
|------------------------|-------------------------|
| `'Hello "world"'`      | `"Hello 'world'"`       |
| `'She said \"Hi\"'`    | `"She said \"Hi\""`     |
| `'Line1\nLine2'`       | `"Line1\nLine2"`        |

### 3. Objects

- Keys **must** be quoted.
- Keys may be written with **either** single quotes (`'`) or double quotes (`"`).  
  Single‑quoted keys undergo the same string transformation as single‑quoted string values.
- Unquoted keys are **not** permitted.
- Objects **may** have a **trailing comma**

### 4. Numbers

Numbers follow **JSON number syntax** exactly:

- No leading `+`.
- No hexadecimal.
- No leading or trailing decimal point.
- No `NaN`, `Infinity`, `-Infinity` literals unless the special rules below apply.

These literals are allowed only when they can be transformed into
the equivalent JSON **string** (`"NaN"`, `"Infinity"`, `"-Infinity"`) **without inserting characters**.  
The transformation may delete whitespace or move a comma.

**Allowed contexts:**

- The literal must have space to the left
- The literal must have space to the right or a comma that can be shifted right 1
  taking the position of a space

#### Examples

| Input                         | Transformed JSON (after processing)   |
|-------------------------------|---------------------------------------|
| `[1, NaN, 2]`                 | `[1,"NaN",2]`                         |
| `{ 'value': NaN }`            | `{ "value":"NaN"}`                    |
| `[ NaN ]`                     | `["NaN"]`                             |
| `[1, Infinity, 2]`            | `[1,"Infinity",2]`                    |
| `[ -Infinity, 1 ]`            | `["-Infinity",1 ]`                    |
| `[NaN]` (no whitespace)       | Not allowed (no space left and right) |
| `[1, NaN]`                    | Not allowed (no space right)          |

Note that while this does change the node to a JSON string the `JsonNode` and `JsonValue` API
do handle this transparently and accessing it via `doubleValue` will yield the
expected `double`.

### 6. Arrays

- Arrays follow JSON syntax, with one addition: **trailing commas are allowed**.
- A trailing comma after the last element will be **blanked** with a space during transformation.

```json5
[1, 2, 3,]
```

### 7. Standard Literals

The literals `true`, `false`, `null` are identical in JSON and JSON5 
and no special treatment has to be considered.

