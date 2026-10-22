# Getting started

## Installation

Coordinates: [`org.hisp.dhis:json-tree`](https://search.maven.org/artifact/org.hisp.dhis/json-tree).

```xml
<dependency>
    <groupId>org.hisp.dhis</groupId>
    <artifactId>json-tree</artifactId>
    <version>1.10.0</version>
</dependency>
```

## First example

Build a virtual tree from Java values, then read a value out of it:

```java
    JsonString  s = Json.of( "hello" );   // note: no quotes
    JsonInteger i = Json.of( 42 );

    JsonObject o = Json.object( obj ->
        obj.addNumber( "x", 1 ).addNumber( "y", 5 ) );
    o.toJson();   // {"x":1,"y":5}
```

Or wrap an existing JSON document and navigate it:

```java
    JsonObject obj = JsonMixed.of( """
        {
            "key": "points",
            "value": [1,2,3]
        }
        """ );

    obj.getString( "key" ).string();   // "points"
```

## Next steps

- [Lazy parsing and the virtual tree](lazy-parsing.md)
- [Java API](java-api.md)
- [JSON API](json-api.md)