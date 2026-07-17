FROM gradle:8.9-jdk17 AS build
WORKDIR /workspace
COPY backend/src ./src
RUN printf '%s\n' \
  'pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }' \
  'dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { mavenCentral() } }' \
  'rootProject.name = "farm-guardian-backend"' \
  > settings.gradle.kts
RUN printf '%s\n' \
  'plugins {' \
  '    kotlin("jvm") version "2.0.21"' \
  '    kotlin("plugin.serialization") version "2.0.21"' \
  '    application' \
  '}' \
  '' \
  'application { mainClass.set("com.farmguardian.backend.MainKt") }' \
  'kotlin { jvmToolchain(17) }' \
  '' \
  'dependencies {' \
  '    implementation("io.ktor:ktor-server-core-jvm:3.0.1")' \
  '    implementation("io.ktor:ktor-server-netty-jvm:3.0.1")' \
  '    implementation("io.ktor:ktor-server-websockets-jvm:3.0.1")' \
  '    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.0.1")' \
  '    implementation("ch.qos.logback:logback-classic:1.5.12")' \
  '    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")' \
  '}' \
  > build.gradle.kts
RUN gradle installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/install/farm-guardian-backend/ ./
EXPOSE 10000
CMD ["./bin/farm-guardian-backend"]
