#!/usr/bin/env bash
#if [ $TRAVIS_BRANCH == "master" ]; then
if [ -n "$TRAVIS_TAG"  ]; then
  git clone https://${GH_REPO}
  cd "${PLANT_SIMULATOR_DEPLOYMENT_REPO_NAME}"
  # create a new branch with the TRAVIS_TAG as the name of the branch
  git checkout -b feature-$TRAVIS_TAG
  rm deployment-version.txt    # Remove the file
  touch deployment-version.txt # Add the file
  echo plant-simulator.version=${TRAVIS_TAG} >> deployment-version.txt
  # Install yq (https://github.com/mikefarah/yq) for inline yaml editing
  sudo wget https://github.com/mikefarah/yq/releases/download/3.4.1/yq_linux_amd64 -O /usr/bin/yq
  sudo chmod +x /usr/bin/yq
  yq write --inplace helm-k8s/values.yaml 'app.version' ${TRAVIS_TAG}
  git config user.email "travis@travis.org"
  git config user.name ${USER} # this email and name can be set anything you like
  git add .
  git commit --allow-empty -m "deployment plant-simulator tagged with version $TRAVIS_TAG"
  git push https://${GITHUB_API_KEY}@${GH_REPO} feature-$TRAVIS_TAG
else
  echo "Skipping writing image tag release version to plant-simulator-deployment"
fi