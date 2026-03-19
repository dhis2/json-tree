# JURL: JSON in the URL Specification

## Introduction

JURL is a notation for encoding JSON-like data in URL query parameters. It is designed to be compact and URL-safe while retaining the expressiveness of JSON. This specification defines the syntax and semantics of JURL.

## Overview

JURL supports the same data types as JSON: objects, arrays, strings, numbers, booleans, and null. However, the syntax differs in several ways to accommodate URL constraints and improve brevity.

## Character Set and URL Safety

JURL strings and unquoted identifiers may only contain characters that are safe for inclusion in a URL query parameter value. 
Safe in this context means the URL encoder will not misunderstand them as having special semantics. 
The following characters are considered **URL-safe** and may appear literally in quoted strings:

- Letters: `A-Z` `a-z`
- Digits: `0-9`
- Space (` `)
- Punctuation: `' ( ) * , - . / ? @ : ; ! $ _ ~`

(Note: Space is allowed raw because URL encoders typically convert it to `+` and back.)

For unquoted strings and object member names, a more restricted set is used: the URL unreserved characters plus `@`:

- Letters: `A-Z` `a-z`
- Digits: `0-9`
- Hyphen (`-`), period (`.`), underscore (`_`), tilde (`~`), at sign (`@`)

## Syntax

### Values

A JURL value can be one of:

- object
- array
- string
- number
- boolean
- null

Boolean values may be represented as `true` or `false`, or their single-character shortcuts `t` and `f`.  
Null may be represented as `null` or `n`.

### Objects

An object is a comma-separated list of key-value pairs enclosed in parentheses `(` and `)`. Each pair consists of a key (an unquoted string) followed by a colon `:` and an optional value. If the value is omitted, it is interpreted as null.

**Syntax:**

```bnf
<object>  ::= "(" [ <members> ] [ "," ] ")"
<members> ::= <member> ( "," <member> )* 
<member>  ::= <unqouted-string> ":" [ <value> ]
<value>   ::= <object> | <array> | <number> | <string> | <boolean> | <null>
<boolean> ::= "true" | "false" | "t" | "f"
<null>    ::= "null" | "n" 
``` 
`<unqouted-string>`, `<string>`, `<number>` and `<array>` are detailed in later sections.

**Examples:**

- `()` is an empty array, not an object. (Empty object is not representable; use `null` instead.)
- `(a:1)` represents `{"a":1}`
- `(a:)` represents `{"a":null}`
- `(a:1,b:2,)` is equivalent to `(a:1,b:2)` (trailing comma ignored)
- `(a:1,,)` is invalid because two commas in a row would imply a missing member name.

### Arrays

An array is a comma-separated list of values enclosed in parentheses `(` and `)`. Values may be omitted, in which case they are interpreted as null. A trailing comma is ignored.

**Syntax:**

```
<array>    ::= "(" [ <elements> ] [ "," ] ")"
<elements> ::= <value> ( "," <value> )* 
```

The interpretation of omitted values and commas follows these rules:

- A comma that is **not at the end** indicates a separator between elements. If there is no value before it (i.e., it is the first character after `(` or after another comma), that missing value is null.
- A comma that is immediately before the closing `)` is a **trailing comma** and is ignored. It does not create a null element after it.
- If the array contains only a single comma (i.e., `(,)`), it is treated as a trailing comma and ignored, resulting in an empty array.

**Examples:**

- `()` represents `[]`
- `(,)` represents `[]` (single comma ignored)
- `(1,)` represents `[1]` (trailing comma ignored)
- `(,1)` represents `[null,1]`
- `(1,,2)` represents `[1,null,2]`
- `(,,)` represents `[null,null]` (first comma leading, second trailing)
- `(1,2,)` represents `[1,2]`

### Strings

Strings can be represented in two ways:

- **Quoted strings**: enclosed in single quotes `'`. Inside quotes, any URL-safe character may appear literally. Characters that are not URL-safe must be escaped.
- **Unquoted strings**: a sequence of URL unreserved characters or `@`. Unquoted strings are allowed only where a string value is expected and must not contain any characters that would be ambiguous with delimiters.

**Syntax:**

```
<string>          ::= "'" ( <string-char> )* "'" | <unquoted-string>
<unquoted-string> ::= ( <safe-char> )+
<safe-char>       ::= <letter> | <digit> | <separator>
<string-char>     ::= <letter> | <digit> | <separator> | <symbol> | <escape>
<letter>          ::= "a".."z" | "A".."Z"
<digit>           ::= "0".."9"
<separator>       ::= "-" | "." | "_" | "@"
<symbol>          ::= "(" | ")" | "*" | "," | ":" | ";" | "?" | "!" | "$" | "/"
<escape>          ::= "~" ( <mnemonic> | <hex-digit> <hex-digit> )
<hex-digit>       ::= digit | "A".."F"
<mnemonic>        ::= "a" | "b" | "c" | "d" | "e" | "g" | "h" | "p" | "q" | "s" 
                    | "~"
                    | "J" | "L" | "M" | "O" | "P" | "Q" | "U" 
```

The escape mechanism uses a tilde `~` followed by either a mnemonic letter or two hexadecimal digits. 
The following mnemonics are defined:

| Character | Mnemonic (memorable) | Mnemonic (ASCII+42) |
|---------|----------------------|---------------------|
| `&`     | `~a`                 | `~P`                |
| `=`     | `~e`                 | `~g`                |
| `+`     | `~p`                 | `~U`                |
| `#`     | `~h`                 | `~M`                |
| `%`     | `~c`                 | `~O`                |
| `'`     | `~q`                 | `~Q`                |
| `"`     | `~d`                 | `~L`                |
| space   | `~s`                 | `~J`                |
| `~`     | `~~`                 | (none)              |
| \\      | `~b`                 | (none)              |

Additionally, any character can be represented by its hexadecimal ASCII value using `~XX` where `XX` are two hexadecimal digits (e.g., `~20` for space). This is analogous to URL percent-encoding.

**Notes:**

- In quoted strings, the single quote character itself must be escaped as `~q` or `~Q`.
- The backslash must be escaped as `~b`.
- The tilde itself is escaped as `~~`.

**Examples:**

- `'hello world'` → `"hello world"`
- `hello` (unquoted) → `"hello"`
- `'~q'` → `"'"` (a string containing a single quote)
- `'~20'` → `" "` (space via hex)
- `'~a'` → `"&"`
- `'~d'` → `'"'`

### Numbers

Numbers follow a similar syntax to JSON but with some relaxations:

- No leading plus sign is allowed.
- A decimal point may appear at the beginning or end of the number (e.g., `.5` or `5.`), but must have at least one digit before or after.
- Scientific notation using `e` or `E` is allowed, with an optional minus sign on the exponent (plus sign is not allowed).

**Syntax:**

```
<number>   ::= [ "-" ] ( <int> | <float> ) [ <exponent> ]
<int>      ::= ( <digit> )+
<float>    ::= "." ( <digit> )+ | ( <digit> )+ "." [ ( <digit> )* ]
<exponent> ::= ( "e" | "E" ) [ "-" ] ( <digit> )+
<digit>    ::= "0".."9"
```

**Examples:**

- `0`, `123`, `-42`
- `.5`, `-.5`, `5.`, `5.0`
- `1e10`, `1E-5`, `-1.2e3` (valid), `-1.2e+3` (invalid, plus not allowed)

### Booleans and Null

- Boolean `true` can be written as `true` or `t`.
- Boolean `false` can be written as `false` or `f`.
- Null can be written as `null` or `n`.

These tokens are case-sensitive and must be in lowercase.

### Whitespace

Whitespace (spaces, tabs, newlines) is **not permitted** anywhere outside of quoted strings. In quoted strings, spaces are allowed as raw characters.

## Differences from JSON

- Objects and arrays both use round brackets `()` instead of `{}` and `[]`.
- Object member names are unquoted and limited to URL unreserved characters plus `@`.
- Strings may be unquoted if they consist only of URL unreserved characters plus `@`.
- Strings use single quotes `'` instead of double quotes.
- Numbers allow leading or trailing decimal points.
- Scientific notation does not allow a plus sign in the exponent.
- Boolean and null shortcuts: `t`, `f`, `n`.
- Whitespace is not allowed outside strings.
- Empty parentheses `()` denote an empty array; empty object is not representable (use `null` as a placeholder).
- Commas have special rules for omitted values and trailing commas (see below).

## Handling of Omitted Values and Dangling Commas

JURL allows omitting null values in arrays and object members to reduce verbosity. This interacts with the allowance of trailing commas, which are ignored. The following examples illustrate the behavior:

### Arrays

| JURL     | JSON Equivalent | Explanation                                                              |
|----------|-----------------|--------------------------------------------------------------------------|
| `()`     | `[]`            | Empty array                                                              |
| `(,)`    | `[]`            | Single comma is trailing, ignored                                        |
| `(1,)`   | `[1]`           | Trailing comma ignored                                                   |
| `(,1)`   | `[null,1]`      | Leading comma indicates null before 1                                    |
| `(1,,2)` | `[1,null,2]`    | Two commas in a row: null in between                                     |
| `(,,)`   | `[null,null]`   | First comma leading (null before), second trailing (ignored) → two nulls |
| `(1,2,)` | `[1,2]`         | Trailing comma ignored                                                   |

### Objects

| JURL       | JSON Equivalent    | Explanation                                             |
|------------|--------------------|---------------------------------------------------------|
| `()`       | `[]` (not object)  | Empty parentheses are always an array                   |
| `(a:)`     | `{"a":null}`       | Colon with no value means null                          |
| `(a:1,)`   | `{"a":1}`          | Trailing comma ignored                                  |
| `(a:,b:2)` | `{"a":null,"b":2}` | First member has null value                             |
| `(a:1,b:)` | `{"a":1,"b":null}` | Second member has null value                            |
| `(a:1,,)`  | Invalid            | Two commas in a row would require a missing member name |

## Escaping Examples

- `'~q'` → `"'"` (single quote)
- `'~d'` → `'"'` (double quote)
- `'~s'` → `" "` (space)
- `'~a'` → `"&"`
- `'~~'` → `"~"`
- `'~b'` → `"\\"`
- `'~20'` → `" "` (space via hex)
- `'hello~20world'` → `"hello world"`

## Implementation Notes

Boolean literals, null literals and numbers all qualify to also be unquoted strings.
This ambiguity must be handled by a parser to translate the character sequence of `true`, `false` and `null`
to their JSON equivalent (no string). If a character sequence can be a number is shall be converted
to a JSON number (no string). The shorthands for true, false and null however are not to be changed
and remain the exact character sequence given in the input, so they must be translated to a JSON string. 

## Security Considerations

JURL is intended for use in URLs, so it should be properly encoded when placed in a query parameter. 
The JURL string itself should be percent-encoded according to URL standards. 

## Examples

Here are some complete JURL examples and their JSON equivalents:

- `(name:John,age:30)` → `{"name":"John","age":30}`
- `(1,2,3)` → `[1,2,3]`
- `(,1,,2)` → `[null,1,null,2]`
- `(a:1,b:,c:3,)` → `{"a":1,"b":null,"c":3}`
- `'hello world'` → `"hello world"`
- `hello` → `"hello"`
- `t` → `true`
- `n` → `null`
- `(mixed:(1,2),flag:t)` → `{"mixed":[1,2],"flag":true}`

* * *

This specification is intended to guide implementations of JURL encoders and decoders.