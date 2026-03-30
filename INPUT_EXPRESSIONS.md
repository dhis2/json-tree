# Input Expressions (InpEx)

An Input Expression is a pattern matching expression language 
designed for checking short, "simple" values such as 
numbers, identifiers, dates, codes or URLs. 

The language is **linear‑time**, strict left ro right matching 
and straight forward to implement **allocation‑free**.
It focuses on readability, simplicity, predictability and safety.

Input expression always look for "what you want" (whitelist-y), and so it has

- no OR
- no "not in" character sets
- no backtracking

---

## Overview

A pattern is a sequence of *units*. Each unit is either:

- a **literal character** (except special characters `# @ [ ] | ? * + ~ ( )`),
- a **character set** (mnemonic like `#` for digit, `@` for name‑character, etc.),
- a **set of character sets** `[...]` (to match 1 input character),
- a **sequence of sets** `|...|` (to match 1 input character for each set in the sequence),
- a **repeat** applied to a unit (e.g., `?{unit}`, `+{unit}`, `3{unit}`),
- a **scan** (non‑greedy skip) `~{unit}` or `~~{unit1}{unit2}`,
- a **group** `(...)` used to create a compound unit.

Everything else is a literal character.

Most notably this includes (almost) all punctuation marks which is handy, 
as these tend to occur in the type of values this wants to match.

Note that the lack of an OR construct is mitigated in the implementation `Pattern`
by considering a list of multiple patterns to be alternative formats for the value.
For example, a floating point number can be difficult with optional digits before 
and after decimal point. This simply becomes 2 patterns for each case.

---

## Syntax Summary

| Syntax              | Type           | Description                                                                                  |
|---------------------|----------------|----------------------------------------------------------------------------------------------|
| `d`                 | _set_          | any digit (`0`‑`9`) (alias)                                                                  |
| `u`                 | _set_          | any uppercase letter (`A`‑`Z`)                                                               |
| `l`                 | _set_          | any lowercase letter (`a`‑`z`)                                                               |
| `c`                 | _set_          | any letter (`A`‑`Z`, `a`‑`z`)                                                                |
| `x`                 | _set_          | any hexadecimal digit (`A`‑`F`, `a`‑`f`, `0`‑`9`)                                            |
| `a`                 | _set_          | any alphanumeric (`A`‑`Z`, `a`‑`z`, `0`‑`9`)                                                 |
| `s`                 | _set_          | sign (`+` or `-`)                                                                            |
| `#`                 | _set_*, _unit_ | any digit (`0`‑`9`)                                                                          |
| `@`                 | _set_*, _unit_ | any *name* character (`A`‑`Z`, `a`‑`z`, `0`‑`9`, `_`, `-`)                                   |
| `[{set1}{set2}…]`   | _unit_         | matches a single character that belongs to any of the listed sets                            |
| `\|{set1}{set2}…\|` | _unit_         | matches a sequence where each character belongs to the corresponding set (length must match) |
| `?{unit}`           | _unit_         | _unit_ occurs 0 or 1 time (optional)                                                         |
| `*{unit}`           | _unit_         | _unit_ occurs 0 or more times (greedy)                                                       |
| `+{unit}`           | _unit_         | _unit_ occurs 1 or more times (greedy)                                                       |
| `9{unit}`           | _unit_         | _unit_ occurs exactly n times (n = 1‑9, 9 in this example)                                   |
| `9?{unit}`          | _unit_         | _unit_ occurs 0‑n times (n = 1‑9, 9 in this example)                                                            |
| `~{unit}`           | _unit_         | skip forward (non‑greedy) until the _unit_ matches (the _unit_ is consumed)                  |
| `~~{unit1}{unit2}`  | _unit_         | repeat _unit1_ zero or more times, then _unit2_ (non‑greedy) – “repeat until”                |
| `(…)`               | _unit_         | groups a sequence of units so that repeats/scans apply to the whole group                    |
| `<literal>`         | _unit_         | any other character matches itself literally                                                 |

`#` and `@` have their set semantics in `|…|` but are literally in `[…]`.

---

## Building Blocks

### Literals
Any character that is **not** a special symbol matches itself exactly.  
Special symbols are: `# @ [ ] | ? * + ~ ( )` and digits when they appear as repeat counts.

**"Escaping"**

There is no escaping syntax, but most of the special characters can be matched literally, 
by placing them inside a `|…|` or `[…]`.

Naturally `|` must be "escaped" in `[|]` and `[` and `]` in `|[|`/ `|]|`.
`#` and `@` are only _sets_ in `|…|` so they must be "escaped" in `[#]` / `[@]` 

---

### Character Sets 

//TODO for each environment make a table for character (ranges) and what they mean instead

Input expressions build on pre-defined named character sets.

| Code | Set Description                                            | Characters                       |
|------|------------------------------------------------------------|----------------------------------|
| `s`  | sign                                                       | `+` `-`                          |
| `b`  | binary digit                                               | `0`,`1`                          |
| `d`  | digit                                                      | `0`‑`9`                          |
| `u`  | uppercase letter                                           | `A`‑`Z`                          |
| `l`  | lowercase letter                                           | `a`‑`z`                          |
| `c`  | letter (upper or lower)                                    | `A`‑`Z` `a`‑`z`                  |
| `a`  | alphanumeric (letter or digit)                             | `A`‑`Z` `a`‑`z` `0`‑`9`          |
| `x`  | hexadecimal digit                                          | `0`‑`9` `A`‑`F` `a`‑`f`          |
| `i`  | identifier (letter, digit, underscore, hyphen)             | `A`‑`Z` `a`‑`z` `0`‑`9` `_` `-`  |
| `#`  | digit (alias for `d`; in `\|…\|` and outside `[…]`)        | `0`‑`9`                          |
| `@`  | identifier (alias for `i`; in `\|…\|` and outside `[…]`)   | `A`‑`Z` `a`‑`z` `0`‑`9` `_` `-`  |
| a-z* | lower letters not listed above are reserved for future use | -                                | 
| A-Z  | matches the given character case-insensitive               | A => `A`,`a`; B => `B`, `b`, ... |
| ...  | any other character is matched literally                   | + => `+`, é => `é`, ...          |

Larger sets can be composed using `[…]`; for example, `[ud]` are upper case letters and digits,
`[d.]` are digits and decimal points.

In `[…]` to match `]` literally it must be the first character.
In `|…|` `|` is always the end of the sequence. 
To match it literally the sequence is interrupted and `[|]` is used to match the bar. 

---

### Set‑of‑Sets: `[…]`

`[ set1 set2 … ]` matches a **single** character that belongs to **any** of the listed sets.

**Example:**  
`[#u]` matches a digit **or** an uppercase letter.

Sets can be given as mnemonics (`#`, `@`, `u`, …) or as literal characters (which match exactly that character).  
If you need to match a literal `]`, put it inside the brackets: `[]]` matches `]`.

---

### Sequence of Sets: `|…|`

`|{set1}{set2}…|` matches a **sequence** of characters where the **first** character must belong to `{set1}`, 
the second to `{set2}`, etc. The length of the sequence is fixed to the number of sets inside the bars.
Note that this also implies that a set of length n always matches input of length n.

**Example:**  
`|dd|` matches exactly two digits (e.g., `12`).  
`|ua|` matches an uppercase letter followed by an alphanumeric character.

If a set is given as a **digit** (`0`‑`9`), it behaves as a *numeric limit* when placed consecutively with other digits. 
See **Numeric Sequences** below.

Literal characters inside `|…|` match themselves exactly.  
If you need to match a literal `|`, place it inside a set: `[|]`.

---

### Repeats

A repeat applies to the *immediately following unit*. 

The unit can be:

* a single mnemonic set `#` or `@`, 
* a set‑of‑sets `[…]`, 
* a sequence `|…|`, 
* a group `(…)`, 
* or a literal character.

| Repeat syntax | Meaning                               |
|---------------|---------------------------------------|
| `?{unit}`     | 0 or 1 times (optional)               |
| `*{unit}`     | 0 or more times (greedy)              |
| `+{unit}`     | 1 or more times (greedy)              |
| `n{unit}`     | exactly n times (n = 1‑9)             |
| `n?{unit}`    | 0 to n times (n = 1‑9)                |

**Examples:**  
`3#` matches exactly three digits.  
`*|ab|` matches zero or more repetitions of the two‑character sequence `ab`.  
`2?@` matches at most two name characters (zero, one, or two).  
`+?` is not allowed – the `?` after a repeat changes the meaning to “0‑n”; 
the repeat itself does not have a separate non‑greedy modifier.

**Greediness:** Repeats are *greedy*: they match as many repetitions as possible (up to the given max) 
until the input no longer matches. Unlike RegEx there is no connection to what follows the repeated unit.

**Zero‑length units:** A unit should never be of zero length, but if a repeat makes zero progress
(but is considered a match nonetheless) the repetition will end as if no further occurrence was found.

Repeating something up to n-times where n is > 9 must be done by repeating the repeated pattern,
for example `5#5#` to do 10 repetitions of `#`.

---

### Scans (Non‑greedy Skip)

Scans let you skip forward in the input until a certain unit matches.

- `~{unit}` – skip any number of characters (including none) until `{unit}` matches. The `{unit}` itself is consumed.
- `~~{unit1}{unit2}` – match `{unit1}` zero or more times (non‑greedy), then match `{unit2}`. 
  This is the non-greedy equivalent of `(*{unit1}){unit2}`, it ensures that `{unit1}` is repeated only as needed to find `{unit2}`. It is useful for “repeat until” patterns.

Both forms are **non‑greedy**: the minimal number of characters are skipped / repeated to satisfy the match.

**Examples:**  
`~#` skips to the first digit and consumes it.  
`~~(ab)(xy)` matches zero or more `ab` until `xy` is found (e.g., `ababxy`).

---

### Groups: `(…)`

Parentheses group a sequence of units into a single unit. This allows you to apply repeats or scans to a composite pattern.

**Example:**  
`+(ab)` matches one or more repetitions of the literal two‑character sequence `ab`.  
`~(##)` skips until two digits appear consecutively.

Groups **do not nest**. The notation is intentionally flat to keep implementation simple. 
If you need nested grouping, you can often restructure the pattern using separate units.

---

## Numeric Sequences

Inside a sequence `|…|`, consecutive digits represent a **numeric limit** rather than individual digit sets.

For example:  
`|12|` does **not** mean “digit 1 followed by digit 2”. Instead, it means “a two‑digit number whose numeric value is ≤ 12”.  
`|123|` means “a three‑digit number ≤ 123”.

This works only when digits appear consecutively with no other sets between them. 
The length of the digit sequence determines how many digits the input must have, 
and the numeric comparison is performed after parsing the input digits.

If you want individual digit limits, separate the digits:  
`|1||2|` means a digit ≤ 1 followed by a digit ≤ 2.

**Implementation note:** Numeric comparison is performed using the entire sequence of digits; 
leading zeros are allowed and count toward the number length.
Numeric comparison is restricted to Java `long` range (not including the largest negative number).
Any numeric sequence given exceeding this range will simply overflow and behave accordingly
with a compromised numerical comparison. 

---

## Performance and Safety

Input Expressions are designed to be **safe** and **fast**:

- Matching runs in **linear time** relative to the pattern length and input length.
- No recursion or backtracking that could cause exponential blow‑up.
- No dynamic memory allocation during matching (the pattern is processed as a `char[]` and input as a `CharSequence`).
- The implementation guards against infinite loops when a repeat would match zero characters repeatedly.

These properties make it suitable for validating user input in contexts where resource usage must be predictable.

---

## Examples

| Pattern      | Matches                            | Does not match                           |
|--------------|------------------------------------|------------------------------------------|
| `3#`         | `123`, `000`                       | `12` (too few), `1234` (too many)        |
| `*#`         | `` (empty), `1`, `12`, `123`, …    | any non‑digit                            |
| `+#`         | `1`, `12`, `123`, …                | `` (empty)                               |
| `[#u]`       | `5`, `A`, `Z`                      | `a` (lowercase), `*`                     |
| `u#`         | `A1`, `B2`                         | `1A` (wrong order)                       |               |
| `12`         | `02`, `12`                         | `13` (too high), `123` (too many digits) |
| `~#`         | `abc1` → matches `1` (skips `abc`) | `abc` (no digit)                         |
| `~~(ab)(xy)` | `xy`, `abxy`, `ababxy`             | `axy` (if not `xy` it must match `ab`)   |
| `+@`         | `abc`, `a_b`, `x-9`                | `a.b` (dot not allowed)                  |
| `2?#`        | ``, `1`, `12`                      | `123` (too many)                         |
| `3?@`        | ``, `a`, `ab`, `abc`               | `abcd` (too many)                        |

Common simple value inputs and their patterns:

| Value                | Input Expression Pattern                                                      | Examples                                    | Description                                                                                                 |
|----------------------|-------------------------------------------------------------------------------|---------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| Integer              | `?\|s\|+#` or `?[+-]+#` or `?[s]+#`                                           | `42`, `-7`, `+123`, `0`                     | Optional sign, one or more digits.                                                                          |
| Decimal              | `?[+-]+#?(.*#)` + `?[+-]*#.+#`                                                | `0.5`, `-3.`, `+.7`, `123.456`, `.5`        | At least one digit before or after the decimal point; includes integers.                                    |
| +Scientific Notation | add `?([E]?[+-]+#)`                                                           | `1.23e-4`, `-0.5E+2`, `3.0e0`               | Mantissa with at least one digit after decimal, followed by `e` or `E`, optional sign, and exponent digits. |
| ISO Date             | `\|2099-12-31\|`                                                              | `2023-12-25`, `1900-01-01`                  | Four‑digit year (0‑2099), two‑digit month (0‑12), two‑digit day (0‑31). No calendar validation.             |
| Time (24h)           | `\|23:59\|`, `\|23:59:59\|` or flexible `\|23:59\|?(\|:59\|)`                 | `14:30`, `08:05:22`                         | Two‑digit hour (00‑23), minute (00‑59), and optional seconds (00‑59).                                       |
| UUID                 | `8[x]-4[x]-4[x]-4[x]-8[x]4[x]` or `\|xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\| ` | `123e4567-e89b-12d3-a456-426614174000`      | Standard 36‑character UUID: 8‑4‑4‑4‑12 hex digits, separated by hyphens.                                    |
| US Phone             | `?(\|(###)\|) 3#-4#`                                                          | `(123) 456-7890`, `456-7890`                | Area code in parentheses, optional; then three digits, hyphen, four digits.                                 |
| IPv4 Address         | `\|255.255.255.255\|` (3 digits each only) `#2?#.#2?#.#2?#.#2#` (1-3 digits)  | `192.168.1.1`, `0.0.0.0`, `255.255.255.255` | Four numbers 0‑255 separated by dots. Each `0-255` matches 3 digits numerically 0-255.                      |
| MAC Address          | `\|xx-xx-xx-xx-xx-xx\|` or `2[x]:2[x]:2[x]:2[x]:2[x]:2[x]`                    | `00:1A:2B:3C:4D:5E`, `00-1A-2B-3C-4D-5E`    | Six groups of two hex digits, separated by colon or hyphen.                                                 |
| Currency (USD)       | `$?-#+*(,3#)?(.2#)`                                                           | `$1,234.56`, `$0.99`, `$-12.34`             | Dollar sign, optional sign, digits, optional comma, optional decimal part.                                  |
| US Zip Code          | `5#?(-4#)`                                                                    | `12345`, `12345-6789`                       | Five digits, optionally hyphen and four digits.                                                             |

Note in the example of a decimal numbers that allow decimal point without digits before or after
this is solved using 2 patterns (+) and matching that either of them matches.

---

This specification describes the pattern language as implemented in the `Pattern` class of the [DHIS2 JSON Tree library](https://github.com/dhis2/json-tree).
