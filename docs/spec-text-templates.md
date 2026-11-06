# Text Templating

`TextTemplate` uses _**directives**_, which are lines with "instructions" placed in a template text. 
Each line is either a directive or otherwise part of the content of the template.
A line is never mixed, both content and instruction.

The design was motivated by SQL templating which lead to four central properties:

- **Templates can be valid SQL** — directives can be ordinary SQL comments, so a template can be pasted into any SQL console and run unmodified.
- **Freedom in content formatting** — because directives mark whole-line boundaries, the content can be formatted freely.
- **No understanding of the content** — the templating does not require any knowledge about the content to work correctly.  
- **Single Pass Processing** — The result can be produced by doing a simple pass over the template coping slices into a result text buffer making this templating very efficient.

While being designed with SQL in mind `TextTemplate`s are useful and adjustable for many text templating purposes
as the directives vocabulary is universal.

> [!Note]
> The examples in this document will use `--` before each directive as a marker (again going back to the SQL use case). 
> This marker is just an example, in practice a directive line detection predicate is passed to the template
> to customize this for other use cases.

---

## Overview

The directive vocabulary:

| Directive  | Form                             | Purpose                                                                               |
|------------|----------------------------------|---------------------------------------------------------------------------------------|
| `@if`      | `@if <expr>` … `@end`            | emit the enclosed block only when the expression is true                              |
| `@else`    | … `@else` …                      | alternative branch of the innermost `@if`                                             |
| `@end`     | … `@end`                         | close the innermost open block                                                        |
| `@insert`  | `@insert :name`                  | substitute with text registered for the name                                          |
| `@replace` | `@replace :cond [text]` … `@end` | replace the block with text when the condition is true                                |
| `@reorder` | `@reorder :name` … `@end`        | position the block by a caller-supplied sort key for the cluster of `@reorder` blocks |

The three block directives (`@if`, `@replace`, `@reorder`) all close with the same `@end` marker.
Blocks can contain nested directives.
Only directives without arguments (`@end`, `@else`) may be followed by another directive on the same line.
Otherwise, each directive must be on its own line. Directive instruction cannot be split into multiple lines.

For directives with arguments the first argument is always a name (colon followed by 1 or more alphanumeric characters)
The name refers to the condition to trigger the directive effect:

    -- @if :expr
    -- @insert :substitude
    -- @replace :cond with
    -- @replace :cond
    -- @reorder :id

`@replace` can be used with the substitute text hard-coded after the condition,
or (when given without substitute text) it does a lookup for the text 
similar to `@insert` for the condition name.

## @if

Opens a block that is emitted only when its condition holds.

    -- @if :name
    Hello
    -- @end

The block is emitted if the named condition resolves to true; 
otherwise every line between the opening directive and its matching `@end` is discarded. 
Nested blocks compose: an inner block's content is emitted only when both the outer and inner conditions are true. 

`@if` allows to test multiple conditions combined with logic operators `&&` and `||`:

    -- @if :a && :b          conjunction: both a and b must be true
    -- @if :a || :b          disjunction: either a or b must be true
    -- @if :a && :b || :c    standard precedence: (a && b) || c

Standard precedence applies: `&&` binds tighter than `||`.
There is no parenthesis syntax; to group a compound condition, nest `@if` blocks.

## @else

Inverts emission within the innermost open block.

    -- @if :flag
    A
    -- @else
    B
    -- @end

Exactly one of the two branches is emitted. `@else` must be bare (no argument). 
At most one `@else` may occur per block; a second is an error.

The `@else` refers to the innermost open block. This means in:

    -- @if :outer
    -- @if :inner
    A
    -- @else
    B
    -- @end
    -- @end

the `@else` binds to the inner `@if`, not the outer one. 
Sibling `@if` blocks use `--@end --@if` (see `@end`) rather than trying to use `@else` between them.

## @end

Closes the innermost open block.

    -- @if :name
    ...
    -- @end

Every `@if`, `@replace`, and `@reorder` must be closed by `@end`. 
`@end` has no arguments but can be followed by a trailing directive:

    -- @end --@if :b

This closes the current block and resumes at the embedded directive, as if it had been written on the next line:

    -- @end
    -- @if :b

The two forms are semantically identical. 
The compact form is convenient for sibling chains where a series of `@if` blocks sit next to each other:

    -- @if :a
    A
    -- @end --@if :b
    B
    -- @end --@if :c
    C
    -- @end

Any directive can follow `--@`, not just `@if`:

    -- @end --@else
    -- @end --@reorder :created

which read as `@end` followed by `@else` (applying to an outer `@if`) or `@reorder :created` on the next line.

## @insert

Substitutes text at the position of the directive line.

    SELECT
      -- @insert :fields
    FROM x

If text is registered for `fields`, it is emitted in place of the directive line. 
If no text is registered, or the registered text is empty, the line is removed entirely. 
This gives a simple way to compose SQL from fragments that may or may not be present.

`@insert` opens no block and takes no matching `@end`.

## @replace

Opens a block that is either *replaced* (with replacement text) or *kept* (with its body emitted), depending on a condition.

    SELECT
    -- @replace :counting count(*)
      a, b, c
    -- @end
    FROM x

The choice is two-way:

- **Condition true** — the block body (everything between the opening directive and `@end`) is discarded; the opening directive line is replaced by the replacement text.
- **Condition false** — the opening directive line is removed; the block body is emitted normally.

Two forms of replacement text exist:

    -- @replace :counting count(*)       inline replacement on the opening line
    -- @replace :counting                falls back to @insert-style lookup

When no inline text follows the condition, the text is looked up exactly as for `@insert`. If no text is registered, the opening line is removed and the body is discarded — a useful way to make a whole block disappear.

The body is subject to normal directive processing in both branches. In the true branch, emission is switched off so nested blocks still consume their own `@end` correctly, while any directives inside the body are still validated. In the false branch, nested blocks are processed normally with the current emission state.

## @reorder

Opens a block whose position is dictated by a caller-supplied ordering rather than by the template.

    ORDER BY
    -- @reorder :name
    x.name
    -- @end
    -- @reorder :created
    x.created DESC
    -- @end

Consecutive `@reorder` blocks form a **group**. 
A group ends at the first line (after its `@end`) that is not itself a `@reorder` directive. 
When the group is emitted, its blocks are sorted by the sort key assigned to each block's name, 
with ties broken by template order.

How sort keys are assigned and what happens to unregistered names is the caller's concern. 
A common convention is that an unregistered name sorts to the end 
(useful when the caller signals *presence* by assigning a position and *absence* by not assigning one); 
another is that all names must be registered. The language does not mandate either.

The body of each `@reorder` block is subject to normal directive processing, 
so filters and inserts inside a reorder block work as expected:

    -- @reorder :created
    x.created
    -- @insert :direction
    -- @end

This lets a block be either a complete fragment (`x.created DESC`) or 
a template-driven one whose content depends on inputs (`x.created` followed by a piece of inserted text).

`@reorder` takes no additional argument beyond its name.

## Binding a `TextTemplate`

The templating is unaware of any context.
This context is provided through four caller-supplied callbacks when constructing the `TextTemplate` instance:

- a predicate that decides whether a line is a directive line (note helper `isDirectiveLine` with prefix marker) 
- a predicate `ifs(name)` that resolves a named `@if` condition to a boolean,
- a function `inserts(name)` that resolves a named `@insert` to optional text,
- a function `reorders(name)` that assigns a sort key to a named `@reorder` block.