# Input Expressions

An Input Expression is a pattern matching expression language 
designed for checking short, "simple" values such as 
numbers, identifiers, dates, codes or URLs. 

The language is **linear‑time**, strict left ro right matching 
and straight forward to implement **allocation‑free**.
It focuses on readability, simplicity, predictability and safety.

An Input Expression always looks for "what you want" (whitelist-y), and so it has

- no OR
- no "not in" character sets
- no backtracking

---

## Overview

A pattern is given using the following building blocks:

- a **character set** `[...]` (match 1 input character against a set of possible characters),
- a **sequence of sets** `|...|` (match a sequence of input characters against a sequence of sets of characters),
- a **repeat** `?`(0-1), `*`(0-*), `+`(1-*) greedy repeat the subsequence unit, 
- a **scan** `~` (skip-until) or `~~` (skip-match-until) non‑greedy skip until match for terminal unit,
- a **group** `(...)` used to create a compound unit.

Everything else is a literal character. 
Which characters are literal and which are codes is detailed below for each mode.

Most notably literal characters include (almost) all punctuation marks which is handy, 
as these tend to occur in the type of values this wants to match.

> [!NOTE]
> Note that the lack of an OR construct is mitigated in the implementation `InputExpression`
> by considering a list of multiple `Pattern`s to be alternative formats for the value.
> For example, a floating point number where digits must only be present before 
> or after the decimal point is given by 1 pattern for a number enforcing digits before 
> and 1 pattern enforcing digits after the decimal point.

---
## Syntax

Interpretation occurs in 3 modes:

- main mode: Initial mode, or in other words mode outside of character sets `[…]` and sequences `|…|`
- character set mode: inside `[…]`
- sequence mode: inside `|…|`

> [!NOTE]
> Note that apart from having modes the language does not allow nesting. 
> This means within any of `(…)`, `[…]` and `|…|` these no further grouping is possible. 
> This is a limitation chosen for simplicity of implementation. Implementation can and may choose to
> allow nesting of groups to increase expressiveness. Such an extension would not change the semantics
> of the language, just make it harder to skip a unit/block as nesting must be considered.

### Main-Mode

| Code    | Description                                                                                           | Characters                      |
|---------|-------------------------------------------------------------------------------------------------------|---------------------------------|
| `#`     | digit (shorthand for `[d]` or `[#]`                                                                   | `0`‑`9`                         |
| `@`     | identifier (letter, digit, underscore, hyphen; = `[i]`)                                               | `A`‑`Z` `a`‑`z` `0`‑`9` `_` `-` |
| `[…]`   | matches a single character that belongs to any of the listed sets                                     |                                 | 
| `\|…\|` | matches a sequence where each character belongs to the corresponding set                              |                                 |
| `?…`    | subsequent unit occurs 0 or 1 time (optional)                                                         |                                 |
| `*…`    | subsequent unit occurs 0 or more times (**greedy**)                                                   |                                 |
| `+…`    | subsequent unit occurs 1 or more times (**greedy**)                                                   |                                 |
| 1-`9…`  | subsequent unit occurs exactly 9 times (digit is max repetition)                                      |                                 |
| 1-`9?…` | subsequent unit occurs 0 to 9 times (digit is max repetition)                                         |                                 |
| `~…`    | skip forward (**non‑greedy**) until a match for subsequent unit is found                              |                                 |
| `~~……`  | repeat directly subsequent unit zero or more times (**non‑greedy**), to find unit directly after that |                                 |
| `(…)`   | groups a sequence of codes so that repeat and scan apply to the whole group                           |                                 |
| `{…}…`  | adds a numeric bound to the unit following the bound `{…}`                                            |                                 | 
| ...     | any other character is matched literally                                                              | + => `+`, é => `é`, ...         |

- **Escaping:** There is no escaping notation or code; to match op-code characters literally use a `|…|` or `[…]`
- **Unit:** Repeats and scans apply to a "unit", which is a `(…)`, `[…]` or `|…|` block or any other character individually (be it an op-code or literal match)  
- Parentheses group a sequence of codes into a single unit. Its sole function is to apply repeats or scans to a composite pattern.

**Examples:**  
- `3#` matches exactly three digits.  
- `*|ab|` matches zero or more repetitions of the two‑character sequence `ab`.  
- `2?@` matches at most two identifier characters.
- `~#` skips to the first digit and consumes it.  
- `~~(foo)(bar)` matches zero or more `ab` until `xy` is found (e.g., `foofoobar`).

> [!TIP]
> Repetitions in Input Expressions are written in the order we describe a pattern in natural language; 
> for example, "two digits followed by 3 upper case letters" is `2#3[u]` (`2` digits `#`, `3` upper case letters `[u]`).

---


### Character Sets: `[…]`-Mode

`[{code1}{code2}…]` matches a **single** character that belongs to **any** of the listed sets.

| Code | Description                                                | Characters                       |
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
| a-z* | lower letters not listed above are reserved for future use | -                                | 
| A-Z  | matches the given character case-insensitive               | A => `A`,`a`; B => `B`, `b`, ... |
| 0-9  | literally 0-9 (just to clarify)                            | `0`..`9`                         | 
| ...  | any other character is matched literally                   | + => `+`, é => `é`, ...          |

- Listing multiple sets composes a larger set; for example, `[u&]` => upper case letters and `&`
- To match `]` literally it must be the first character in the set, e.g. `[]uld]`
- The semantics of all **letter** codes are shared with `|…|` sequences

---

### Sequence of Sets: `|…|`-Mode

`|{code1}{code2}…|` matches a **sequence** of characters where the **first** character must belong to 
the character set given by `{code1}`, the second belong to set given by `{code2}`, etc. 

Note that a sequence always has a 1:1 length relation to the input it matches.

| Code | Description                                                | Characters                       |
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
| a-z* | lower letters not listed above are reserved for future use | -                                | 
| `?`  | any character (unicode BMP range)                          | U+0000 to U+FFFF                 |
| `#`  | digit (alias for `d`)                                      | `0`‑`9`                          |
| `@`  | literally @ (just to clarify)                              | `@`                              |
| A-Z  | matches the given character case-insensitive               | A => `A`,`a`; B => `B`, `b`, ... |
| `0`  | literally 0 (just to clarify)                              | `0`                              |
| 1-9* | the digit is the numerically largest digit                 | e.g. `3` => `0`-`3`              |
| ...  | any other character is matched literally                   | + => `+`, é => `é`, ...          |

- A `|…|` sequence must not be empty. 
- The semantics of all **letter** codes are shared with `[…]` sets
- To match `|` literally the sequence is interrupted and `[|]` is used to match the bar.
- Multiple **digits** (`0`‑`9`) in a row, match not each digit being in bounds but the entire sequence of digits
being in bounds. For example, `|234|` is not 0-2 followed by 0-3 followed by 0-4 but a 3-digit number 0-234.   
- **Digit sequences:** consecutive digits represent a **numeric upper bound** rather than individual digit bound.
- To match digits with individual upper bounds, separate the digits in multiple `|…|`. 

**Examples**
- `|dd|` matches exactly two digits (e.g., `12`).
- `|ua|` matches an uppercase letter followed by an alphanumeric character.
- `|123|` matches a three‑digit number ≤ 123.
- `|1||2|` matches a digit ≤ 1 followed by a digit ≤ 2.

> [!NOTE] 
> **Implementation note:** Numeric comparison is restricted to Java `long` range (not including the largest negative number).
> Any numeric sequence given exceeding this range will simply overflow and behave accordingly
> with a compromised numerical comparison. 

### Numeric Bounds `{…}`
Any unit can be preceded by a numeric bounds check. The check applies to the input section that was matched by
subsequent unit. Numeric bounds don't themselves consume input, they just add a constraint to the matched input.

`{n}` adds an upper bound of `n` (n being any integer number). 
`{n-m}` adds a range bound for the input to be between n and m (n and m being any integer number).
The upper bound can be open `{n-}`. This also allows to combine the bound with a `|…|` which might
already enforce an upper bound.

**Examples**

An IPv4 address can be `|255.255.255.255|`. This works giving each part 0-255 range, but mandates each part to have
3 digits. To match `0.0.0.0` as valid, the pattern can be adjusted to `{255}(#2?#).{255}(#2?#).{255}(#2?#).{255}(#2?#)`.

A simple date pattern may be `|2099-12-31|` which is fine except it does allow 0 for month and day, 
and the year can be any 4-digit number until year 2099. 
A refined pattern could be `{1900-}|2099|-{1-}|12|-{1-}|31|`. 
Now year is within 1900-2099, month between 1 and 12 and day between 1 and 31.
If in addition single digit days and month should be permitted the pattern could be refined to
`{1900-}|2099|-{1-12}(#?#)-{1-31}(#?#)`. If in addition a 2 digit year is accepted the pattern becomes
`{1900-2099}(2#2?#)-{1-12}(#?#)-{1-31}(#?#)`.
---

## Performance and Safety

Input Expressions are designed to be **safe** and **fast**:

- Matching runs in **linear time** relative to the pattern length and/or input length.
- No backtracking that could cause exponential blow‑up.
- No recursion could cause stack overflow
- No dynamic heap memory allocation during matching
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
