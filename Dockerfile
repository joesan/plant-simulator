FROM anapsix/alpine-java:jdk8
MAINTAINER Joesan <https://github.com/joesan>

# If needed to run bash commands enable this line below!
# RUN apk add --no-cache bash

ENV SBT_VERSION 0.13.15
ENV CHECKSUM 18b106d09b2874f2a538c6e1f6b20c565885b2a8051428bd6d630fb92c1c0f96

ENV APP_NAME plant-simulator
ENV PROJECT_HOME /opt/apps
ENV PROJECT_DIR $PROJECT_HOME/$APP_NAME

RUN mkdir -p $PROJECT_HOME/$APP_NAME

# Change to the project directory
WORKDIR $PROJECT_DIR

# Copy the jar file
COPY ./target/scala-*/plant-simulator-*.jar $PROJECT_DIR

# Copy the database file
COPY plant-simulator.mv.db $PROJECT_DIR

# Run the application
CMD java -Denv=dev -jar plant-simulator-*.jar
