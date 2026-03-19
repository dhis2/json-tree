# JURL

JURL is short for _JSON in the URL_.

JURL is a notation that that can be used in the URL query position to describe JSON data
with most features of JSON being supported. 

Data that can be expressed in JURL is a strict subset of the data that can be expressed in JSON.
JURL shares types with JSON: _object_, _array_, _string_, _number_, _boolean_ and _null_.

JURL is designed to feel familiar and easy to comprehend in text form.

The key differences are:

* objects and arrays both use round brackets `(...)`
* objects and arrays can have dangling `,` (ignored)
* object member names are not quoted
* object member names can only use `A-Z a-z 0-9 - . _ @` (URL Unreserved - `~` + `@`)
* a `,` immediately followed by another `,` in an object or array sequence is equivalent to `,null,`
* strings use single quotes `'...'` 
* positive numbers or positive exponents must not use `+` (omit it)
* strings can be unquoted if they only use `A-Z a-z 0-9 - . _ @` (null/true/false auto-conversion)
* empty array is `()`, empty object cannot be expressed (approximate with `null`)
* JURL does not permit whitespace anywhere except space in string values

Furthermore, JURL is more "lenient" than JSON both to be more user-friendly and 
to allow shorter URLs though the use of shorthands and omissions:

* `null` can be encoded as the empty string or shortened to `n`
* `true` can be shortened to `t`
* `false` can be shortened to `f`
* numbers can start or end with the decimal point `.`

The flexibility in JURL is deliberate to allow for easier integration.
Unfortunately this means it is not as easy to parse.

In summary, JURL can express everything JSON can, except the empty object `{}`.
That value of string needs escaping as detailed below.

## Escaping
Escaping and encoding are easily confused in the context of a notation syntax that is
meant to be embedded into a URL query section. To clarify: 
* **Encoding** is the process 
performed by the browser to express a character that has special semantics in a URL as a "plain" character.
* **Escaping** is the process taking place before that where a JSON value is transcribed to
a JURL value so that when the value is given to the URL encoder it is not misunderstood
and wrongly changed.

In JSON any unicode character can occur in an object member name or string value.
Some might be JSON escaped but that is not relevant here. 

In a URL only very specific characters may occur plain (A-Z a-z 0-9 `-` `.` `_` `~`).
The so called sub‑delimiters may occur plain but might have special semantics
depending on where they occur. In a URL query parameter position where a JURL
value would occur `!` `$` `*` `;` `/` `?` `@` can be used plain, as they have no special semantic in that position,
and `&` `=` `+` `#` `%` `"` ` ` (space) and \\ (backslash) cannot occur plain and must
be escaped by JURL to not be confused with the role in an URL.

Characters outside the set of all mentioned characters must always be URL encoded.
But this is performed by the URL encoder, JURL does not need to escape them and leave them "as in".

**Overview**

| Category             | Characters                  | Action                                          |
|----------------------|-----------------------------|-------------------------------------------------|
| JURL syntax          | `(` `)` `'` `,` `:`         | Used as‑is (all sub‑delimiters)                 |
| Unreserved           | A-Z a-z 0-9 `-` `.` `_` `~` | Always safe (but `~` => `~~`)                   |
| Other sub‑delimiters | `!` `$` `*` `;`             | Safe – leave plain                              |
| Also allowed         | `/` `?` `@`                 | Safe – leave plain                              |
| Reserved in query    | `&` `=` `+`                 | Must be escaped (`~a`, `~e`, `~p` or hex `~XX`) |
| Fragment/percent     | `#` `%`                     | Must be escaped (`~h`, `~c` or hex)             |
| Space                | ` ` (space)                 | Must be escaped (`~s` or `~20` or `+`)          |
| Problematic          | `"` \\                      | Must be escaped (`~d`/`~22`, `~b`/`~5C`)        |

**Mnemonics** 

(`~X` shorthands for characters that need escaping in string literals)

| Characters         | Mnemonic 1 | Mnemonic 2 (ASCII+42) |
|--------------------|------------|-----------------------|
| `&` (ampersand)    | `~a`       | `~P`                  |
| `=` (equals)       | `~e`       | `~g`                  |
| `+` (plus)         | `~p`       | `~U`                  |
| `#` (hash)         | `~h`       | `~M`                  |
| `%` (percent)      | `~c`       | `~O`                  |
| `'` (quote)        | `~q`       | `~Q`                  |
| `"` (double quote) | `~d`       | `~L`                  |
| ` ` (space)        | `~s`       | `~J`                  | 
| `~` (tilde itself) | `~~`       | -                     |
| \\ (backslash)     | `~b`       | -                     |

The 1st mnemonic is easy to remember by name,
the 2nd is easy to compute by adding 42 to the character's ASCII value.
Tilde and backslash do not have a 2nd mnemonic and must always use the 1st.
Encoding space with `~s` is one option, using `+` (URL semantics) is another.

**Hexadecimal**
Alternatively any ASCII range character can be encoded as hexadecimal value 
using `~XX`. `XX` is the ASCII value as hex number using **upper-case** for
A-F only to not collide with mnemonics.

This syntax for example used to escape the characters that also need escaping in JSON
as there is no shorthand (mnemonic) for these.

The `~XX` notation is the escaping equivalent to `%XX` encoding performed by the URL encoder.


## Example
Here is a JSON example:
```json
{
  "name": "Freddy",
  "age": 30,
  "car": null,
  "addresses": [{
    "street": "Elm Street",
    "zip": 1428,
    "city": "Springwood",
    "invoice": true
  }]
}
```
In JURL the equivalent Java `String` would be (formatting whitespace removed):
```
(name:'Freddy',age:30,car:null,addresses:((street:'Elm Street',zip:1428,city:'Springwood',invoice:true)))
```
This could be further shortened by using shorthands and omitting `null` values:
```
(name:'Freddy',age:30,car:,addresses:((street:'Elm Street',zip:1428,city:'Springwood',invoice:t)))
```
In a URL parameter the entire value would be URL encoded (space => `+`), resulting in:
```
(name:'Freddy',age:30,car:,addresses:((street:'Elm+Street',zip:1428,city:'Springwood',invoice:t)))
```
(Note: the `+` could also be `%20`)


