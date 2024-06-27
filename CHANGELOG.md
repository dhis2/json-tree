# ChangeLog

## v1.1 - Bulk Modification APIs - [Unreleased]

> [!Note]
> ### Major Features 
> * **Added**: [JSON Patch](https://jsonpatch.com/) support; `JsonValue#patch` (`JsonPatch`, `JsonPointer`) 
> * **Added**: bulk modification API: `JsonNode#patch` + `JsonNodeOperation`
> * **Added**: `@Validation#acceptNull()`, `null` value satisfies required property

> [!Tip]
> ### Minor API Improvements
> * **Added**: JSON value test for same information `JsonValue#equivalentTo`
> * **Added**: JSON value test for same definition (ignoring formatting) `JsonValue#identicalTo`
> * **Added**: `JsonAbstractObject#exists(String)` test if object member exists
> * **Changed**: `JsonNode#equals` and `JsonNode#hashCode` are now based on the json input


> [!Warning]
> ### Breaking Changes 
> * **Changed**: `JsonNode#getPath` returns a `JsonPath` (`String` before)
> * **Changed**: `JsonNode#keys` returns paths with escaping when needed

> [!Caution]
> ### Bugfixes


## v1.0 Matured APIs - January 2024
Unfortunately no detailed changelog was maintained prior to version 1.0.
  
The following is a recollection from memory on major improvements in versions
close the 1.0 release.

> [!Note]
> ### Major Features
> * **Added**: [JSON Schema Validation](https://json-schema.org/) support; 
>   `JsonAbstractObject#validate` and `JsonAbstractArray#validateEach` + 
>   `@Validation` and `@Required`
> 