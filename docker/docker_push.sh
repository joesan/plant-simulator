#!/usr/bin/env bash
#if [ $TRAVIS_BRANCH == "master" ]; then
if [ -n "$TRAVIS_TAG"  ]; then
  docker build . -t $DOCKER_APP_NAME -f docker/Dockerfile;
  docker images;

  echo "$DOCKER_REGISTRY_PASSWORD" | docker login -u "$DOCKER_REGISTRY_USERNAME" --password-stdin docker.io
  echo "Successfully logged into Docker hub <<hub.docker.com>>"

  # Tag & push image for tag $TRAVIS_TAG
  echo "Tag image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL with tag $TRAVIS_TAG";
  docker tag $DOCKER_APP_NAME $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME:$TRAVIS_TAG;
  echo "Push image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL with tag $TRAVIS_TAG";
  docker push $DOCKER_REGISTRY_USERNAME/$DOCKER_APP_NAME:$TRAVIS_TAG;
  echo "Successfully tagged and pushed image $DOCKER_APP_NAME to repository $DOCKER_REGISTRY_URL with tag $TRAVIS_TAG"

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