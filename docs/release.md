# Release Process

This document describes the steps required to cut a new release of this project.

## Prerequisites

- You have push access to the `main` branch and permission to create releases.
- Your local clone is up-to-date with `origin/main`.

## Steps

#### 1. Bump the version in `pom.xml`

Edit `pom.xml` and drop the `-SNAPSHOT` suffix from the version, so it reflects the
final release version.

```xml
    <version>1.?.?</version>
```

#### 2. Update the version in the documentation

Update the Maven coordinates shown in the following files so they match the new
release version:

- `README.md`
- `docs/getting-started.md`

#### 3. Create a pull request

Commit the changes on a branch and open a pull request against `main`.

#### 4. Merge the pull request and verify the publication

Merge the pull request into `main`, then confirm that the GitHub Actions workflow
triggered by the merge has successfully published the artifact to Maven Central.

> **Do not proceed until the publication to Maven Central has succeeded.**

#### 5. Create the release tag

Once the artifact is published, tag the release commit on `main` using the format
`v{major}.{minor}.{patch}` (for example `v1.2.3`).

Use an **annotated** tag, so it carries a message and metadata:

```shell
    git tag -a v1.2.3 -m "Release v1.2.3"
```

**Note:** If you need to tag an older commit rather than `HEAD`, append the commit
 SHA to the command:

```shell
git tag -a v1.2.3 -m "Release v1.2.3" <commit-sha>
```

#### 6. Push the tag to GitHub

Push **only the tag you just created** — avoid `git push --tags`, which would push
every local tag:

```shell
    git push origin v1.2.3
```

#### 7. Create the GitHub release

1. Go to **[Releases](https://github.com/dhis2/json-tree/releases) → Draft a new release** in the GitHub UI.
2. Select the tag you just pushed (for example `v1.2.3`).
3. Set the **previous tag** as the comparison base.
4. Use GitHub's **"Generate release notes"** button to auto-create the description
   from the diff between the two tags.
5. Review the generated list and **remove entries that are not relevant to users**,
   such as build dependency bumps or internal Maven issues.
6. Publish the release.
