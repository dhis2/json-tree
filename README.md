# json-tree

[![main](https://github.com/dhis2/json-tree/actions/workflows/main.yml/badge.svg?branch=main)](https://github.com/dhis2/json-tree/actions/workflows/main.yml)

A lazily parsed JSON object tree library

## Virtual vs Actual Tree

A `JsonResponse` is a **virtual** tree of `JsonValue` nodes:

* `JsonObject`
* `JsonArray`
* `JsonNumber`
* `JsonString`
* `JsonBoolean`
* `JsonNull`

Underlying implementation of the actual JSON document is the `JsonDocument`
which exposes all node types as `JsonNode`.

| `JsonValue` API | `JsonNode` API |
| --------------- | -------------- |
| `JsonValue#node()` =>  | <= `JsonValue.of(node)` |
| **virtual** 👻  | **actual** ☠️   |
| high level abstraction | low level abstraction |
| what we expect we got back | what we actually got back |
| navigation without requiring existence (no exceptions) | navigation demands existence (otherwise throws exceptions) |
| extendable tree API | non-extendable tree API |

"Casting" to Node Types

* `JsonValue   as(Class<T extends JsonValue> type)`
* `JsonList<T> asList(Class<T extends JsonValue> type)`
* `JsonMap<V>  asMap(Class<V extends JsonValue> type)`
* and more of similar kind...

Tree Navigation

* `JsonValue  get(String path)`
* `JsonObject getObject(String path)`
* `JsonArray  getArray(String path)`
* `JsonNumber getNumber(String path)`
* `JsonBoolean getBoolean(String path)`
* `JsonValue  get(int index)`
* and more of similar kind...

Drilling down...

    JsonString str=root.getObject("a").getArray("b").getString(3)
    // or
    JsonString str=root.getString("a.b[3]")

Traversing **actual** tree to find nodes

* `JsonValue#node()`: from virtual to actual JSON tree 🗱
* `JsonObect#find`: return first matching `JsonValue`
* `JsonNode#visit`: visit matching nodes
* `JsonNode#find`: return first matching `JsonNode`
* `JsonNode#count`: count matching nodes

Assertions using standard JUnit asserts

    JsonObject settings = GET("/me/settings").content(HttpStatus.OK);
    
    assertTrue(settings.isObject());
    assertFalse(settings.isEmpty());
    assertTrue(settings.get("keyMessageSmsNotification").exists());
    assertEquals("en",settings.getString("keyUiLocale").string());

Virtual tree: no exceptions before assert 🤩

## Custom Node Types

The `JsonValue` API can easily be extended with user defined types by simply declaring an interface with `default`
methods.

```java
public interface JsonPasswordValidation extends JsonObject
{
    default boolean isValidPassword()
    {
        return getBoolean( "isValidPassword" ).booleanValue();
    }

    default String getErrorMessage()
    {
        return getString( "errorMessage" ).string();
    }
}
```

Use `as` or `asObject` to "cast" a `JsonValue` to a custom type:

    JsonValue jsonValue = fetchPasswordValidation();
    JsonPasswordValidation result = jsonValue.as(JsonPasswordValidation.class );
    assertTrue(result.isValidPassword());
    assertNull(result.getErrorMessage());

Asserting a Schema or Structure

    assertTrue(settings.isA(JsonPasswordValidation.class));

Recursively checks that all primitive members or members annotated with `@Expected` as defined by the `JsonSettings`
interface are present.

When `asObject` is used instead of `as` the structural check is done implicitly and fails with an exception should the
actual JSON not match the expectations defined by the provided user defined interface.

## Building or Streaming JSON

The `JsonBuilder` API is used to:

* create a `JsonNode` document (and from there a JSON `String`)
* directly stream JSON to an output stream while it is built

While it is an "append-only" API to support true streaming its use of lambda function arguments allows to compose the
output in a convenient and flexible way.

Ad-hoc creation of `JsonNode`:

    JsonNode obj = JsonBuilder.createObject(
        obj -> obj.addString( "name","value" ));
    
    String json = obj.getDeclaration();

Directly writing JSON to an output stream:

    JsonBuilder.streamObject( out, 
        obj -> obj.addString( "name", "value" ) );

## Manipulating JSON Trees

The `JsonNode` trees are effectively immutable tree structures. However, they
can be "manipulated" returning a new changed tree root.

To replace any type of node with a different node use `JsonNode#replaceWith`.
The new JSON `String` provided is expected to be valid JSON. To add members to
an object node `JsonNode#addMember` is used, again expecting value name and JSON
value. Both return a new changed root.

If only a subtree of an existing tree should be extracted
use `JsonNode#extract();`

**OBS!** This technique should be used with care it each changes produces a new
tree that needs to be parsed again.

## Searching JSON Trees

As the `JsonValue` represents a virtual or expected tree the API to search this
tree is quite limited as any search has to build upon the `JsonNode` API which
reflects the actual tree.

For convenience the subtree of a `JsonObject` can be searched for an object node
that satisfies a particular shape and test `Predicate`:

    JsonObject match = root.find(JsonPasswordValidation.class, obj -> true);

The above example finds the first object in the subtree that successfully can
be "cast" to a `JsonPasswordValidation`
object, meaning it has all its `@Expected` members (same as
calling `obj.isA(JsonPasswordValidation.class)` on each potential object in the
subtree).

    JsonObject match = root.find(JsonPasswordValidation.class, 
        validation -> !validation.isValidPassword());

In the second example the search is extended by an additional `Predicate`. Now
the `match` is the first `JsonPasswordValidation` which has an invalid password.

The `JsonObject#find` method builds upon the `JsonNode` API which offers a
broader range of search capabilities.

* `JsonNode#find`: find first match that satisfy both a
  particular `JsonNodeType` and a `Predicate<JsonNode>`
* `JsonNode#count`: count all matches that satisfy both a
  particular `JsonNodeType` and `Predicate<JsonNode>`
* `JsonNode#visit`: fully generic subtree visitor in form of
  a `Consumer<JsonNode>`

The downside of the `JsonNode` search API is that dealing with `JsonNode` is
more cumbersome and can throw exceptions. Therefore, it can make sense to
implement specific search methods similar to `JsonObject#find` in types that
extend `JsonObject`.

Searching for an element in a `JsonList` can be done using a number of methods:

* `containsAll`: check if the list contains a subset
* `contains`: check at least one matching element is contained
* `containsUnique`: check that one and only one matching element is contained
* `count`: count elements matching a `Predicate` test
* `first`: find the first matching element

These can be used with `JsonArray` as any array can be treated as list
using `array.asList(JsonValue.class)`.

Another way to test qualities of a `JsonList` is to use one of its `toList`
methods to turn it into a `java.util.List`. The assumption is that the values in
that target list should not be instances of `JsonValue` but "usual" java types.
Therefore, any of the `toList` methods accepts a `Function` to transform
the `JsonValue` elements into some java type. At the point of conversion the
virtual tree is accessing the actual tree which means exceptions are possible.
To mitigate this risk there are different variations of the `toList` method:

* `toList(Function)`: map and expect all source values to exist, otherwise
  throws exception
* `toList(Function, Object)`: map and use provided default value in
  case source element does not exist
* `toListOfElementsThatExists(Function)`: map and skip those source elements
  of the list that do not exist

A reason for elements to not exist in an existing array are view
transformations. For example if the original elements are objects and a member
is extracted for the view not all object might have this member.

## View-Transform JSON Trees

While the actual `JsonNode` tree is immutable and never changed for any
virtual `JsonValue` tree layered on top of it the virtual tree can be virtually
transformed. This means looking at the same tree through a lens that maps the
affected level to some other way to look at it. Usually this is used to 
"de-objectify" elements or members by mapping to one of their members.

Available view transformations are:

* `JsonArray#viewAsList`: maps all array elements resulting in a list view
* `JsonList#viewAsList`: maps all list elements resulting in a list view
* `JsonMap#viewAsMap`: maps all map members resulting in a map view
* `JsonObject#viewAsObject`: maps all object member values resulting in an
  object view
* `JsonMultiMap#viewAsMultiMap`: maps all values of the multimap lists
  returning in a multimap view

Given the list `[{"x":1},{"x":2}]` the view 

    array.viewAsList(e -> e.asObject().getNumber("x"))

would result in an effective value of `[1,2]`. Again, the underlying tree is not
changed by this, just the view on top of it is transformed.

Such transformation can be especially useful in combination with `toList` to
extract a list of the values that can be compared to an expected list. 