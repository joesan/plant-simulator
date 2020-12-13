#!/bin/bash -e
#if [ $TRAVIS_BRANCH == "master" ]; then
if [ -n "$TRAVIS_TAG"  ]; then
  docker build . -t $DOCKER_APP_NAME -f docker/Dockerfile;
  docker tag $DOCKER_APP_NAME $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME:$TRAVIS_TAG;

  docker images;

  echo "Travis tag is **************** $TRAVIS_TAG"
  echo "$DOCKER_REGISTRY_PASSWORD" | docker login -u "$DOCKER_REGISTRY_USERNAME" --password-stdin docker.io
  echo "Successfully logged into Docker hub <<hub.docker.com>>"

  echo "Pushing image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL with tag $TRAVIS_TAG";
  docker push $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME:$TRAVIS_TAG;

  echo "Pushing image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL with tag latest";
  docker tag $DOCKER_APP_NAME $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME;
  docker push $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME;
else
  echo "Not a Tag, so not pushing anything to Docker Hub!"
fi