#!/usr/bin/env bash
if [ $TRAVIS_BRANCH == "master" ]; then
  echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_REGISTRY_USERNAME" --password-stdin;
  docker build -t $DOCKER_APP_NAME .;
  docker images;
  docker tag $DOCKER_APP_NAME $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME:latest;
  echo "Pushing image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL";
  # We wait for 5 minutes until we opt out
  docker push $DOCKER_TAG_NAME;
  docker push $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME:latest;
fi
