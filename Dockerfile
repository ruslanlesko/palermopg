FROM maven:3.6-openjdk-11-slim as build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN ["mvn", "package"]
RUN ["jlink", \
    "--no-header-files", "--no-man-pages", "--compress=2", \
    "--add-modules", "java.base,java.xml,java.scripting,java.desktop,java.management,java.naming", \
    "--output", "/app/slimjre"]

FROM debian:10.7-slim
COPY --from=build /app/slimjre /slimjre
COPY --from=build /app/target/core-1.16.2-fat.jar /app/target/core.jar
EXPOSE 8081
ENTRYPOINT ["/slimjre/bin/java", "-jar", "/app/target/core.jar"]