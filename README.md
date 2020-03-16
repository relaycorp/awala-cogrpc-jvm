# CogRPC Binding for the JVM

Kotlin JVM library implementing the [Relaynet CogRPC binding](https://specs.relaynet.link/RS-008).

## Development

This project uses [Gradle](https://gradle.org/), so the only system dependency is a Java JDK. To install the project along with its dependencies, run `./gradlew build` (or `gradlew.bat build` on Windows).

Additional Gradle tasks include:

- `test`: Runs the unit test suite.
- `dokka`: Generates the API documentation.
- `publish`: Publishes the library to the local Maven repository on `build/repository`.
