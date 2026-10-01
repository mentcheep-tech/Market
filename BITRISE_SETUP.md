# PolymarketClient — Bitrise setup

## 1. Add the project to Bitrise

Go to Bitrise and add the Git repository containing PolymarketClient.

Bitrise's Android project scanner looks for `gradlew` and the Android Gradle
plugin. The project location should therefore be the directory containing
`gradlew`.

## 2. Add this configuration

Copy `bitrise.yml` into the root of the repository:

    bitrise.yml

The workflow is named:

    build_apk

It is configured to:

1. Clone the repository.
2. Use Java 17.
3. Install missing Android SDK components.
4. Build the `app` module's `debug` variant.
5. Upload the resulting APK to Bitrise artifacts.

## 3. Run it

In Bitrise:

    PolymarketClient
      -> Workflows
      -> build_apk
      -> Start/Schedule Build

When the build finishes, open the build's Artifacts section and download the
generated APK.

## 4. If Bitrise asks about project location

Use the directory containing:

    gradlew

For a normal Android project this is the repository root.

## 5. Important

Do not commit CLOB private keys, API secrets, or wallet private keys into
`bitrise.yml` or the repository.

Those should be configured later as Bitrise Secrets if/when we add live
execution.

## 6. Why Java 17

This project previously hit a Gradle build failure because Gradle was being
run with Java 11. This workflow explicitly selects Java 17.

## 7. If the generated Bitrise workflow differs

Bitrise can generate a default Android `build_apk` workflow automatically.
The project-specific configuration above is intentionally simpler because our
current goal is only:

    source -> debug APK -> downloadable artifact

We can add signing, release builds, tests, and deployment later.
