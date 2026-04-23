
# v1.9 `json-tree` Highlights 

A presentation about what happened in version 1.9

----

## Features 🎉

* `.to(Class)` API (JSON to Java)
* `.query(JsonSelector)` API (finding matching nodes RFC-9535-ish)
* `InputExpression`s (RegEx-ish pattern matching but "safe" and "cheap")
* `JsonPointer` support via `JsonPath` (RFC-6901)
* `Json5` input support (JSON5-ish)
* `Jurl` = JSON in the URL (a JSON notation that is "URL-safe")
* `JsonNode` + `JsonValue` with small footprint `Serializable` support
* `JsonString`-`JsonNumber` + `JsonString`-`JsonBoolean` duality

---- 

## Specified Edge-cases

* a full JSON node validation will occur at the latest when `JsonNode#endIndex` is called
* duplicate `keys()` in objects are observed
* duplicate key `entries()` are observed with the value of the **first** occurrence 
  when nodes `Index` cache is effective, otherwise they are observed as defined in JSON

----

## Validation



----

## Performance 🏃

`Text` goodness

* `Text`-views (a rich `CharSequence` API instead of `String`s)
* essential APIs all are `Text`-based (avoids lots of array copying)
* JSON string as lazy `Text`-views (if no escaping was used)
* O(1) cached numeric `char[]` for array indexes => `Text`
* O(1) "is numeric" for cache backed numeric `Text`
* O(1) lookup cached short name `Text`s (for loops with string properties)
* O(1) hashing for `Text` (main component in `JsonNode` nodes map)

Slim `JsonNode`s
 
* more compact `JsonNode` implementations (2 refs + 2 ints per node)
* `JsonNode` + `JsonValue` as `Map.Entry`s (no need for `Map.entry`)

Avoid or defer work (fewer indirections, more control)

* `Streamable` provides rich API with minimal overhead
* Nodes `Index` control for "looping"
* `JsonPath` as chain (no alloc parent access, O(1) append)
* once resolved a `JsonNode` is remembered in `JsonValue`
* JSON numbers as lazy `TextualNumber`-views


----

## That escalated quickly... 

The origins of `Text`...

https://github.com/dhis2/json-tree/blob/ce68c5acafe49ad0262967404415adca839e0f88/src/main/java/org/hisp/dhis/jsontree/JsonTree.java#L589

_A few moments later..._

https://github.com/dhis2/json-tree/pull/86

----

## Performance - `Text`-views

from
```
              {"key":"value", "key2":42, ...}
                |
                v  
   new String("key") ---> new byte[] { ... }
```
to
```
              {"key":"value", "key2":42, ...}
                ^  ^
                |  |
 new Text.Slice(s, e)
```