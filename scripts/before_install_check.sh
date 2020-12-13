#!/bin/bash -e
if ! git diff --name-only $TRAVIS_COMMIT_RANGE | grep -qvE '(.md)|(.png)|(.pdf)|(.jpg)|(.jpeg)|(.html)|^(LICENSE)|^(docs)'
then
  echo "Only doc files were updated, not running the CI."
  exit
fi