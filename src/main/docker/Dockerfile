####
# Before building the docker image run:
#
# mvn package -Pnative -Dnative-image.docker-build=true
#
# Then, build the image with:
#
# docker build -f src/main/docker/Dockerfile -t quarkus/core .
#
# Then run the container using:
#
# docker run -i --rm -p 8080:8080 quarkus/core
#
###
FROM openjdk:11-slim
WORKDIR /work/
COPY /target/ /work/
EXPOSE 8080
CMD ["java", "-jar", "core-1.0-SNAPSHOT-runner.jar"]