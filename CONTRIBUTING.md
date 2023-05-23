# Contributing and Maintaining this Project

## Java Version
* Version 0.5.0 and prior require Java 11.
* Version 0.6.0 and newer require Java 17.

## Code Formatting
The repository contains the IDEA configuration for its code formatting.
This should be automatically used when importing the project into IDEA.
Before committing use _Reformat Code_ either only on the changes files or on
all files.

## Deploy to Maven Repository

This library can be deployed as an artefact to the Sonatype OSS Maven repository.
The deployment happens automatically when a PR is merged.
Therefore, it is important to make sure that the current version is either a
snapshot version or is bumped to the next snapshot version directly after
the release PR has been made with a stable version.