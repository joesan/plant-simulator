#!/usr/bin/env bash
if [ $TRAVIS_BRANCH == "master" ]; then
  sbt docker:publishLocal;
  docker login -u $DOCKER_REGISTRY_USERNAME -p $DOCKER_REGISTRY_PASSWORD $DOCKER_REGISTRY_URL;
  docker images;
  echo "Pushing image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL";
  # We wait for 5 minutes until we opt out
  docker push $DOCKER_TAG_NAME;
fi
