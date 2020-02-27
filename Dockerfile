FROM anapsix/alpine-java:jdk8
MAINTAINER Joesan <https://github.com/joesan>

ENV SBT_VERSION 0.13.15
ENV CHECKSUM 18b106d09b2874f2a538c6e1f6b20c565885b2a8051428bd6d630fb92c1c0f96

ENV APP_NAME plant-simulator
ENV PROJECT_HOME /usr/src

RUN mkdir -p $PROJECT_HOME/$APP_NAME

# Copy the jar file
COPY ./target/scala-*/plant-simulator-*.jar $PROJECT_HOME/$APP_NAME

# Copy the database file
COPY .plant-simulator.mv.db $PROJECT_HOME/$APP_NAME

# Run the application
CMD ["$PROJECT_HOME/$APP_NAME java -Denv=dev -jar plant-simulator-*.jar"]
