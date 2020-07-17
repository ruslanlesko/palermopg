FROM openjdk:11-slim
WORKDIR /work/
COPY /target/ /work/
EXPOSE 8081
CMD ["java", "-jar", "core-1.6-fat.jar"]