#!/usr/bin/env bash

echo
echo "--------------------------------"
echo "GH_REPO              = $GH_REPO"
echo "DEPLOYMENT_REPO_NAME = $DEPLOYMENT_REPO_NAME"
echo "--------------------------------"
echo

if [ -n "$GITHUB_REF"  ]; then
  git clone "$GH_REPO"
  cd "$DEPLOYMENT_REPO_NAME" || exit
  # create a new branch with the TRAVIS_TAG as the name of the branch
  git checkout -b feature-$GITHUB_REF
  rm deployment-version.txt    # Remove the file
  touch deployment-version.txt # Add the file
  echo plant-simulator.version=$GITHUB_REF >> deployment-version.txt
  # Install yq (https://github.com/mikefarah/yq) for inline yaml editing
  sudo wget https://github.com/mikefarah/yq/releases/download/3.4.1/yq_linux_amd64 -O /usr/bin/yq
  sudo chmod +x /usr/bin/yq
  yq write --inplace helm-k8s/values.yaml 'app.version' $GITHUB_REF
  git config user.email "travis@travis.org"
  git config user.name $USER # this email and name can be set anything you like
  git add .
  git commit --allow-empty -m "deployment plant-simulator tagged with version $GITHUB_REF"
  git push https://$GITHUB_API_KEY@$GH_REPO feature-$GITHUB_REF
else
  echo "Skipping writing image tag release version to plant-simulator-deployment"
fi