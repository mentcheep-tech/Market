# GitHub Actions Android build

This project builds the PolymarketClient debug APK with GitHub Actions.

## Setup

Put the workflow at:

    .github/workflows/android-build.yml

The repository must contain the normal Android Gradle project files, including:

    gradlew
    gradlew.bat
    gradle/wrapper/
    settings.gradle or settings.gradle.kts
    build.gradle or build.gradle.kts
    app/

The workflow intentionally uses the repository's Gradle wrapper rather than installing
an arbitrary Gradle version.

Java 17 is used explicitly because this project previously encountered a Gradle/Java
version mismatch.

## Running a build

In GitHub:

Actions -> Build PolymarketClient APK -> Run workflow

The resulting APK will be attached under the workflow run's Artifacts section as:

    PolymarketClient-debug-apk

## Automatic builds

A build also runs when code is pushed to `main` or `master`.
