FROM maven:3.6-openjdk-11-slim as build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn package

FROM openjdk:11-jre-slim
COPY --from=build /app/target/core-1.11-fat.jar /app/target/core.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar"]
CMD ["/app/target/core.jar"]