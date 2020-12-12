#!/usr/bin/env bash
if [ -n "$TRAVIS_TAG"  ]; then
  git clone https://${GH_REPO}
  cd "${PLANT_SIMULATOR_DEPLOYMENT_REPO_NAME}"
  rm deployment-version.txt    # Remove the file
  touch deployment-version.txt # Add the file
  echo plant-simulator.version=${TRAVIS_BUILD_NUMBER} >> deployment-version.txt
  # Install yq (https://github.com/mikefarah/yq) for inline yaml editing
  sudo wget https://github.com/mikefarah/yq/releases/download/3.4.1/yq_linux_amd64 -O /usr/bin/yq
  sudo chmod +x /usr/bin/yq
  yq write --inplace helm-k8s/values.yaml 'app.version' ${TRAVIS_BUILD_NUMBER}
  git config user.email "travis@travis.org"
  git config user.name ${USER} # this email and name can be set anything you like
  git add .
  git commit --allow-empty -m "deployment plant-simulator tagged with version $TRAVIS_BUILD_NUMBER"
  git push https://${GITHUB_API_KEY}@${GH_REPO} HEAD:master
else
  echo "Skipping writing image tag release version to plant-simulator-deployment"
fi