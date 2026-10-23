# Java API — building a tree from Java types

Creating a (virtual) tree from Java types is handled by the `Json` API.

```java
    JsonString  s  = Json.of( "hello" ); // note: not quoted
    JsonBoolean b  = Json.of( true );
    JsonInteger i  = Json.of( 42 );
    JsonNumber  d  = Json.of( 42.01d );
    JsonArray   a  = Json.array( Json::of, List.of( 1, 2, 3 ) );
    JsonObject  o  = Json.object( Json::of, Map.of( "a", 4, "b", 2 ) );
```

## Builder API

Arrays and objects can also be composed in various ways using a builder API.

```java
    JsonArray a = Json.array( arr -> arr.addNumber( 1 ).addString( "yes" ) );
    a.toJson();   // [1,"yes"]

    JsonObject o = Json.object( obj -> obj.addNumber( "x", 1 ).addNumber( "y", 5 ) );
    o.toJson();   // {"x":1,"y":5}

    JsonObject o2 = Json.object( obj -> obj.addArray( "points",
            arr -> arr.addNumbers( IntStream.range( 1, 10 ) ) ) );
    o2.toJson();  // {"points":[1,2,3,4,5,6,7,8,9]}
```

## Related

- [JSON from text](intro-3-json-from-text)
- [Null, undefined and existence](intro-5-null-and-undefined)
- [Extending with user types](intro-6-user-types)