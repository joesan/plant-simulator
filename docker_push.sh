#!/usr/bin/env bash
if [ $TRAVIS_BRANCH == "master" ]; then
  echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_REGISTRY_USERNAME" --password-stdin;
  docker build -t $DOCKER_APP_NAME .;
  docker images;
  docker tag $DOCKER_APP_NAME $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME:$TRAVIS_BUILD_NUMBER;
  echo "Pushing image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL";
  docker push $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME:$TRAVIS_BUILD_NUMBER;
  docker tag $DOCKER_APP_NAME $DOCKER_USER/$DOCKER_APP_NAME:latest;
  docker push $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME:latest;
fi