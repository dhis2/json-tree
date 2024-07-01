# JUON

JUON is short for JS URL Object Notation.
Think of it as JSON values for URLs. 
That means the syntax does not use characters that are not safe to use in URLs.

It is not only inspired by JSON but designed to be easily
translatable to JSON. For that, it shares the same types: 
_object_, _array_, _string_, _number_, _boolean_ and _null_.

Where possible the JSON syntax was kept in JUON.
This should help both translate but also remember the syntax for everyone
already familiar with JSON.

The key syntax differences are:

* object and arrays both use round brackets `(...)`
* strings are quoted in single upticks `'...'` 
* positive numbers or positive exponents must not use `+` (omit it)
* object member names are not quoted
* object member names can only use `A-Z a-z 0-9 - . _ @`
* object member names must start with a letter or `@`
* empty array is `()`, empty object cannot be expressed (approximate with `null`)
* as strings are quoted with single upticks `"` does not need escaping 
  but instead `'` is escaped as `\'`

Furthermore, JUON is more "lenient" than JSON both to be more user-friendly and 
to allow shorter URLs though the use of shorthands and omissions:

* `null` can be encoded as the empty string or shortened to `n`
* `true` can be shortened to `t`
* `false` can be shortened to `f`
* numbers can start with `.` (= `0.`)
* numbers can end with `.` (= `.0`)

In summary, JUON can express everything JSON can, except the empty object `{}`.
Some characters in strings obviously will need URL encoding to be allowed in URLs.

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
In JUON the equivalent Java `String` would be (formatting whitespace removed): 
```
(name:'Freddy',age:30,car:null,addresses:((street:'Elm Street',zip:1428,city:'Springwood',invoice:true)))
```
This could be further shortened by using shorthands and omitting `null` values:
```
(name:'Freddy',age:30,car:,addresses:((street:'Elm Street',zip:1428,city:'Springwood',invoice:t)))
```
In a URL parameter the entire value would be URL encoded, resulting in:
```
(name:'Freddy',age:30,car:,addresses:((street:'Elm+Street',zip:1428,city:'Springwood',invoice:t)))
```
(Note: the `+` could also be `%20`)