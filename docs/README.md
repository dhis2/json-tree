# Documentation structure

The root [`README.md`](../README.md) is intentionally brief. Anything longer
than a quick start lives here.

| File                      | Purpose                                                              |
|---------------------------|----------------------------------------------------------------------|
| `index.md`                | Landing page / table of contents                                     |
| `getting-started.md`      | Coordinates + first working example                                  |
| `lazy-parsing.md`         | Virtual tree and lazy parsing concepts                               |
| `java-api.md`             | Building a tree from Java types                                      |
| `json-api.md`             | Building a tree from JSON                                            |
| `tree-navigation.md`      | Navigating a JSON tree                                               |
| `query-api.md`            | Find nodes by pattern                                                |
| `null-and-undefined.md`   | `exists` / `isUndefined` / `isNull` semantics                        |
| `user-types.md`           | Extending the tree with user `interface`s                            |
| `validation.md`           | `@Validation` / `@Required` and schema checks                        |
| `diff.md`                 | `JsonDiff` and comparison modes                                      |
| `json-patch.md`           | `JsonPatch` patching JSON via operations                             |
| `rfc-reference.md`        | Standards implemented, and how the library deviates                  |
| `api-reference.md`        | Exhaustive method/type index                                         |
| `json5-star-reference.md` | JSON5* syntax: what is and isn't accepted, and how it is transformed |

When adding a feature, add a section to the most specific page above and link
to it from `index.md`. Only add a line to the root `README.md` if the feature
changes the quick start.