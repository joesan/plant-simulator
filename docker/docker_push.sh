#!/usr/bin/env bash

# exit when any command fails
set -e

echo "Pushing image to Docker Hub"
echo "--------------------------------"
echo "DOCKER_APP_NAME          = $DOCKER_APP_NAME"
echo "DOCKER_REGISTRY_USERNAME = $DOCKER_REGISTRY_USERNAME"
echo "DOCKER_REGISTRY_URL      = $DOCKER_REGISTRY_URL"
echo "RELEASE TAG VERSION      = $RELEASE_VERSION"
echo "--------------------------------"
echo

if [ -n "$RELEASE_VERSION"  ]; then
  docker build . -t $DOCKER_APP_NAME -f docker/Dockerfile;
  docker images;

  echo "Attempting log in to $DOCKER_REGISTRY_URL"
  echo "$DOCKER_REGISTRY_PASSWORD" | docker login -u "$DOCKER_REGISTRY_USERNAME" --password-stdin docker.io
  echo "Successfully logged into Docker hub $DOCKER_REGISTRY_URL"

  # Tag & push image for tag $TRAVIS_TAG
  echo "Tagging image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL with tag $RELEASE_VERSION";
  docker tag $DOCKER_APP_NAME $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME:$RELEASE_VERSION;
  echo "Pushing image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL with tag $RELEASE_VERSION";
  docker push $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME:$RELEASE_VERSION;
  echo "Successfully tagged and pushed image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL with tag $RELEASE_VERSION"

  # Tag & push image for tag latest
  echo "Tag image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL with tag latest";
  docker tag $DOCKER_APP_NAME $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME;
  echo "Push image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL with tag latest";
  docker push $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME;
  echo "Successfully tagged and pushed image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL with tag latest"

  docker logout
  echo "Logged out of docker"
else
  echo "Not a Tag, so not pushing anything to Docker Hub!"
fi