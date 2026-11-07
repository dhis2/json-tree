# Text Templating

`TextTemplate` processes text line by line. Every line is either a **directive line** or a **content line**; a line is never both.

The design was motivated by SQL templating, which led to four central properties:

- **Templates can be authored as valid SQL** — directives are ordinary SQL comment lines, so the unprocessed template parses as SQL. A template can be written so that its raw form runs unmodified (all blocks concatenated); the processed form may differ.
- **Freedom in content formatting** — because directives occupy whole lines, content can be formatted freely.
- **No understanding of the content** — the templating does not require any knowledge about the content to work correctly.
- **Single pass over template text** — lines are visited once in order, and output is appended to a result buffer as the scan proceeds. `@reorder` groups are the one place emission is deferred: block bodies are collected, sorted by key, and emitted at the group's position. Reorder bodies are re-visited to emit them, so caller callbacks may be invoked more than once per body.

While designed with SQL in mind, `TextTemplate` is useful for many text-templating purposes since the directive vocabulary is universal.

> [!Note]
> The examples below use `--` as the directive-line marker (the SQL case). The marker is caller-supplied: `isDirectiveLine(prefix)` implements the default detection, matching `[indent]prefix[indent]@[alphanumeric]+(whitespace|EOF)`. The same prefix is used for the embedded directive in the compact form `-- @end --@name`.

---

## Overview

| Directive  | Form                                | Purpose                                                                  |
|------------|-------------------------------------|--------------------------------------------------------------------------|
| `@if`      | `@if <expr>` … `@end`               | emit the block only when the expression is true                          |
| `@else`    | … `@else` …                         | alternative branch of the enclosing `@if`                                |
| `@end`     | … `@end`                            | close the innermost open block                                           |
| `@insert`  | `@insert :name`                     | substitute text registered for the name                                  |
| `@replace` | `@replace <expr> = <text>` … `@end` | replace the block with text when the expression is true                  |
| `@replace` | `@replace :name` … `@end`           | replace the block with a substitute registered for the name              |
| `@reorder` | `@reorder :name` … `@end`           | position the block by a caller-supplied sort key for a cluster of blocks |

Block directives (`@if`, `@replace`, `@reorder`) all close with `@end`. Blocks nest.

A **name** is a `:` followed by one or more alphanumeric characters (letters and digits, no underscore or other punctuation). Names refer to caller-supplied condition results, insert texts, or reorder keys.

**Only an argumentless directive may be followed by another directive on the same line.** `@end` and `@else` take no argument, so only they may appear before a trailing directive; the trailing directive may itself take arguments. For example:

    -- @end --@if :b

is semantically identical to:

    -- @end
    -- @if :b

The space between the first directive and the trailing one is required (`-- @end--@if` is not a directive line). This compact form is convenient for sibling chains.

**Directive lines never contribute their own content.** `@insert` and `@replace` explicitly replace or remove their opening line; all other directive lines are simply omitted. Content lines are copied verbatim, including their line terminators.

---

## @if

Opens a block that is emitted only when its condition holds:

    -- @if :name
    Hello
    -- @end

If the condition is true, the block is emitted; otherwise its contents are skipped. Nested blocks compose: an inner block is emitted only when both its condition and all enclosing `@if` conditions are true.

**Conditions**

`@if` and `@replace` accept compound conditions:

    -- @if :a && :b          conjunction
    -- @if :a || :b          disjunction
    -- @if :a && :b || :c    standard precedence: (a && b) || c

`&&` binds tighter than `||`. There is no parenthesized form; nest `@if` blocks to group.

Every term must be `:name`. A line like `-- @if a && b` is rejected.

---

## @else

`@else` closes the current `@if` branch and opens its alternative branch:

    -- @if :flag
    A
    -- @else
    B
    -- @end

Exactly one branch is emitted. `@else` takes no argument. At most one `@else` per `@if`; a second is an error.

`@else` binds to the nearest enclosing `@if` — the same dangling-`else` rule as in most languages. If the innermost open block is `@replace` or `@reorder`, `@else` is an error.

    -- @if :outer
    -- @if :inner
    A
    -- @else
    B
    -- @end
    -- @end

Here the `@else` binds to the inner `@if`. To place `@else` on an outer `@if` after closing an inner `@if`, use the compact form:

    -- @if :outer
    -- @if :a
    A
    -- @end --@else
    E
    -- @end

Because the inner `@if` is closed by the preceding `-- @end`, the trailing `-- @else` binds to the outer `@if`.

---

## @end

Closes the innermost open block. Every `@if`, `@replace`, and `@reorder` must be closed by `@end`.

`@end` takes no argument but may be followed by a trailing directive on the same line (see §Overview), which is processed as if written on the next line:

    -- @if :a
    A
    -- @end --@if :b
    B
    -- @end --@if :c
    C
    -- @end

Any directive may follow `-- @end `, including `@else`, `@replace`, and `@reorder`. This allows sibling chains and switching branches of an outer block.

Text after `-- @end ` that is not a directive is emitted on its own line (with the leading space retained). For example, `-- @end some note` emits `" some note\n"` when that position is reached. Avoid trailing non-directive text after `@end` unless this is intended.

---

## @insert

Replaces its own line with registered text:

    SELECT
      -- @insert :fields
    FROM x

If text is registered for `:fields`, it replaces the whole directive line, including its line terminator. If no text is registered or the registered text is empty, the line is removed entirely.

`@insert` opens no block and takes no matching `@end`.

---

## @replace

Opens a block that is either replaced or kept, depending on a condition:

    SELECT
    -- @replace :counting = count(*)
      a, b, c
    -- @end
    FROM x

The choice is two-way:

- **Condition true** — the block body (between the opening directive and `@end`) is discarded; the opening line is replaced by the replacement text.
- **Condition false** — the opening line is removed; the block body is emitted normally.

The condition is an expression as for `@if`. Replacement text has two forms:

    -- @replace :counting = count(*)     inline replacement
    -- @replace :counting                lookup by name (as @insert)

When inline text follows `=`, it is emitted verbatim (surrounding whitespace trimmed). 
When no inline text is given, the replacement is looked up by name. 
If no text is registered for the name, the opening line is removed and the body is discarded — useful for making a whole block disappear.

The lookup form accepts only a single `:name`; using `&&` or `||` without an inline `= <text>` is an error. 
Complex conditions only apply to the inline form (`@replace <expr> = <text>`) as `:name` doubles as the
test and named insert when doing lookup by name. 

The body is processed in both branches. 
In the true branch, output is suppressed but inner directives (their structure and arguments) are still validated. 
In the false branch, nested blocks are processed normally with the current emission state.

`@replace :x =` (no text after the `=`) replaces the body with nothing and is equivalent to 
`@replace :x` when an empty text is registered for `x`. The `=` form does not fall back to a lookup.

---

## @reorder

Opens a block whose position is dictated by a caller-supplied ordering rather than by the template:

    ORDER BY
    -- @reorder :name
    x.name
    -- @end
    -- @reorder :created
    x.created DESC
    -- @end

Consecutive `@reorder` blocks form a **group**. A group continues as long as the next line, at the same nesting level, is another `@reorder` directive. A compact chain `-- @end --@reorder :name` also continues the group. Any other line — a blank line, a plain comment, an unrelated directive — ends the group.

When a group is emitted, its blocks are sorted by the sort key assigned to each block's name, with ties broken by template order. How keys are assigned, and what happens to unregistered names, is the caller's concern. Two common conventions: unregistered names sort to the end (so presence is signalled by assigning a position and absence by not assigning one), or all names must be registered. The language does not mandate either.

The body of each `@reorder` block is subject to normal directive processing, so `@if`, `@insert`, and further `@reorder` groups work inside a block:

    -- @reorder :created
    x.created
    -- @insert :direction
    -- @end

This lets a block be either a complete fragment (`x.created DESC`) or a template-driven one whose content depends on inputs (`x.created` followed by an inserted direction).

> [!Note]
> Reorder bodies are processed twice: once to locate the matching `@end` (with emission suppressed), and again to emit them in sorted order. Caller callbacks may therefore be invoked more than once per body. They should be idempotent or memoized. Nested `@reorder` multiplies this (each nested group also performs its own collect + emit passes).

`@reorder` takes no argument beyond its name.