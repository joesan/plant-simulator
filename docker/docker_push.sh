#!/usr/bin/env bash
if [ $TRAVIS_BRANCH == "master" ]; then
  docker build -t $DOCKER_APP_NAME .;
  docker images;
  docker tag $DOCKER_APP_NAME $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME:$TRAVIS_BUILD_NUMBER;

  echo "$DOCKER_REGISTRY_PASSWORD" | docker login -u "$DOCKER_REGISTRY_USERNAME" --password-stdin docker.io
  echo "Successfully logged into Docker hub <<hub.docker.com>>"

  echo "Pushing image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL with tag $TRAVIS_BUILD_NUMBER"; 
  docker push $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME:$TRAVIS_BUILD_NUMBER;

  echo "Pushing image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL with tag latest";
  docker tag $DOCKER_APP_NAME $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME;
  docker push $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME;
fi