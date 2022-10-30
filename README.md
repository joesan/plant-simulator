# ![plant-simulator-ui](images/logo.png)

[![Codacy Badge](https://app.codacy.com/project/badge/Grade/7a7484b6cdf248a8b8e34aedb4e4433e)](https://www.codacy.com/gh/joesan/plant-simulator/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=joesan/plant-simulator&amp;utm_campaign=Badge_Grade)
[![codecov](https://codecov.io/gh/joesan/plant-simulator/branch/master/graph/badge.svg)](https://codecov.io/gh/joesan/plant-simulator)
[![Build and Deploy](https://github.com/joesan/plant-simulator/actions/workflows/main.yml/badge.svg)](https://github.com/joesan/plant-simulator/actions/workflows/main.yml)
![Contributors](https://img.shields.io/github/contributors/joesan/plant-simulator.svg?style=for-the-badge)
[![CII Best Practices](https://bestpractices.coreinfrastructure.org/projects/1185/badge)](https://bestpractices.coreinfrastructure.org/projects/1185)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

# plant-simulator (a.k.a Digital Twin a.k.a Virtual Power Plant command & control)

Simulation of De-centralized command and control of electricty producing & consuming power plant units that can be remotely steered and operated at scale!

## Getting Started 

For some background information on the project, please have a look at the Wiki documentation [here](https://github.com/joesan/plant-simulator/wiki)

For the impatient you, I have a version up and running on Heroku [here](https://plant-simulator-ui.herokuapp.com/)! Please be soft on it as it is running on just 512MB RAM! But you will not be dissapointed! Give it a try! [Here is the API](https://github.com/joesan/plant-simulator/wiki/API-Documentation) that you can use to test the Command and Control of the Power Plant's

Here is what the operation of a PowerPlant would look like! A constant command and control of the PowerPlant would end up showing you the following graph! In the graph below, you will see that the PowerPlant which was operating at its base power was asked to RampUp for some time and it did that. By RampUp, I mean that the PowerPlant can start operating and inject power into the electrical grid (if connected). The plant-simulator app monitored the RampUp closely and if I plot the values I end up getting this beautiful graph below!

![PowerPlant Command and Control](https://github.com/joesan/plant-simulator/blob/master/images/streaming_telemetry.png)

With that little picture above, I hope you are interested to give the application a try! The instructions given below will get you a copy of the project up and running on your local machine for development and testing purposes. 

### Prerequisites

```
1. Install Java Version 8
2. Install Scala Version 2.12.4
3. Install IntelliJ - Latest community edition and then install the latest Scala plugin available
```

### Setting up the codebase locally

Follow the steps below to import the project into IntelliJ

```
1. Clone the project from: 
   git clone https://github.com/joesan/plant-simulator.git
   
2. Fire up IntelliJ and import the project
   
3. If you are opening IntelliJ for the first time, set up the Scala library in IntelliJ accordingly
```

### Database Support

Though the application is multi relational database compliant, currently only MySQL and H2 databases are supported, the reason being that the [set up scripts](https://github.com/joesan/database-projects/tree/master/power-plant-simulator) are only avaiable for MySQL and H2. If you need support for other relational databases, please feel free to contribute. You only need to add the set up scripts and include the JDBC driver dependency to build.sbt. Everything else works out of the box!

You have to make sure to set up your database and configure the database credentials and url in one of the applicatiox.xxxx.conf files (where xxxx stands for either test or prod)

### Load Testing

This is a serious application which is ready to take a beating. Have a look [here](https://github.com/joesan/plant-simulator/blob/master/loadtest/LOADTEST.md) of what it could do!

### Running tests

You have the option here to either run tests from IntelliJ or from the command line

To run tests from the command line, do the following:

```
1. Open a terminal and navigate to the project root folder 
   
2. Issue the following command: [Tests are run against an in memory H2 database and uses the application.test.conf]
   sbt clean test
```
To run any specific tests from within IntelliJ, simply right click the test that you wish you
run and click Run

### Running the application

This application is built as a web application using the Play framework. We have two options to run the application:

* Run as a standalone jar
* Run as a Docker container

#### To run as a standalone jar, do the following

```diff
+For simplicity, there is local H2 database setup (plant-simulator.mv.db). The connection details 
+are to be found under conf/application.dev.conf
   
+You can comfortably run the application using the following command (No WiFi, no Network required)
-sbt -Denv=dev run
```

If you want to run the application against a MySQL database, follow the instructione below:

```
0. First, we need a database that is up and running. Please have a look at
   this project that contains the setup script for the database (Supported databases are MySQL and H2):
   https://github.com/joesan/database-projects/tree/master/power-plant-simulator
   
   Make sure to have a running instance of your database server and to run the setup scripts.
   
   Once you have the database up and running, configure the database credentials in the 
   application.xxxx.conf file (xxxx stands for test or prod)
   
1. Open a terminal and navigate to the project root folder 
   
2. Issue one of the following commands to run:
   To run against a default (application.conf)
   sbt run
   
   To run against the qa environment
   sbt -Denv=qa run
   
   To run in production mode (application.prod.conf):
   sbt -Denv=prod -Dlogger.resource=logger-prod.xml run

3. Navigate to the following url on your favorite browser:
   http://localhost:9000
   
4. To do something meaningful with the application, have a look at the [documentation!](https://github.com/joesan/plant-simulator/wiki/API-Documentation) for more information on how to call the API's!
   
``` 

To visualize the application and to do some real work with it, have a look [here](https://github.com/joesan/plant-simulator-ui) - Currently under development

Alternatively, you could visualize the app under the following URL:

`https://plant-simulator-ui.herokuapp.com/` - More information could be found [here](https://github.com/joesan/plant-simulator/wiki/API-Documentation)

#### To run as a Docker container

```
1. Make sure you have Docker installed on your host system 
   
2. Issue one of the following commands to build the docker image:
   sbt docker:publishLocal
   
   The above command would have built the image and push it to your local
   docker registry (on your host system)
   
   Alternatively, you could also pull the pre-built Docker image from my repo
   https://hub.docker.com/repository/docker/joesan/plant-simulator
   
3. Issue the following command to run the container:
   docker run -i -p 9000:9000 joesan/plant-simulator
   
   Note that I have a database file included in the container, so no need to set your database separately!
   
4. Follow the [API documentation!](https://github.com/joesan/plant-simulator/wiki/API-Documentation) to play with it
   
```

#### To run as a Docker container in a Kubernetes runtime

```
1. Makue sure you have Docker & Minikube installed on your host system (I assume you are on a Mac)
   
2. Build the Docker image by navigating to the project root folder and run the following command
   For example., on my machine it is
   
   Joes-MacBook-Pro:plant-simulator joesan$ pwd
   /Users/joesan/Projects/Private/scala-projects/plant-simulator
   Joes-MacBook-Pro:plant-simulator joesan$ docker build -t plant-simulator .
   
   The above command would have built the image and push it to your local
   docker registry (on your host system). Next you got to tag this image using the
   following command:
   
   Joes-MacBook-Pro:plant-simulator joesan$ docker tag plant-simulator joesan/plant-simulator
   
   Alternatively, you could also pull the latest pre-built Docker image from my repo
   https://hub.docker.com/repository/docker/joesan/plant-simulator
   
3. Now issue the following command to deploy the application to Minikube:
   Joes-MacBook-Pro:plant-simulator joesan$ kubectl create -f kubernetes/plant-simulator-deployment.yml
   
4. Issue the following command to find out the IP address of the application:
   Joes-MacBook-Pro:~ joesan$ minikube service list
   |----------------------|---------------------------|-----------------------------|-----|
   |      NAMESPACE       |           NAME            |         TARGET PORT         | URL |
   |----------------------|---------------------------|-----------------------------|-----|
   | default              | kubernetes                | No node port                |
   | default              | plant-simulator-service   | http://192.168.99.100:32224 |
   | demo                 | podinfo                   | No node port                |
   | flux                 | memcached                 | No node port                |
   | kube-system          | kube-dns                  | No node port                |
   | kubernetes-dashboard | dashboard-metrics-scraper | No node port                |
   | kubernetes-dashboard | kubernetes-dashboard      | No node port                |
   |----------------------|---------------------------|-----------------------------|-----|

   Use the IP address and follow the [API documentation!](https://github.com/joesan/plant-simulator/wiki/API-Documentation) to    play with it!
   
```

## Deployment

There are two options when it comes to deployment. 

1. I have a pre-deployed version on Heroku. More details can be found [here!](https://github.com/joesan/plant-simulator/wiki/API-Documentation)

2. You can deploy this yourself on your kubernetes cluster. Have a look [here!](https://github.com/joesan/plant-simulator-deployment)

## Automated Deployment / Continuous Integration

I added the possibility to do automated deployments using Travis CI after success. So here is what I did to get this happen:

1. Create an Auth token from GitHub - Follow the instructions from [here](https://docs.github.com/en/free-pro-team@latest/github/authenticating-to-github/creating-a-personal-access-token)

2. Set the credentials as environment variables in Travis CI. For example., in this case I had to set these 2 environment variables

   ```
   GH_REPO             github.com/joesan/plant-simulator-deployment.git // The project where we want to commit a version number
   GH_API_KEY          ***********  // The GitHub Auth token
   ```
3. Have a look at .travis.yml in the docker_push section on how to push a git commit with the version number. We use [yq](https://github.com/mikefarah/yq) for      inline editing of YAML files. So upon successful build, we write the version for the latest build (TRAVIS_BUILD_NUMBER) into the plant-simulator-deployment        project

4. Once Travis commits a version number (here only tags) to the plant-simulator-deployment project where the Kubernetes deployment files are 
   located, the CI / CD pipeline for that project gets triggered. Head over to [plant-simulator-deployment](https://github.com/joesan/plant-simulator-deployment) 
   project to find out more

## Tools Used

* [SBT](http://www.scala-sbt.org/) - Scala Build Tool

* [GitHub Actions](https://docs.github.com/en/actions) - Hosted Continuous Integration

* [Mergify](https://mergify.io/) - Automatically merge pull requests

* [Scala-Steward](https://github.com/scala-steward-org/scala-steward) - Keep dependencies up-to-date (The best your project can have)

## Contributing [![contributions welcome](https://img.shields.io/badge/contributions-welcome-brightgreen.svg?style=flat)](https://github.com/joesan/plant-simulator/issues)

For more information on how to contribute, have a look [here](https://github.com/joesan/plant-simulator/blob/master/CONTRIBUTING.md)

## Releases

We use tag's for doing releases. To tag a release do the following:

1. Commit pending changes into the master branch and push the master branch into Git

2. Run the following commands (Make sure to adjust the SemVer appropriately):

```
git tag -a v2.2.2 -m "Your comments" // Create annotated tag

git push origin --tags               // Push annotated tag
```

For more information, see the releases page [here](https://github.com/joesan/plant-simulator/releases)

## Authors / Maintainers

* *Joesan*           - [Joesan @ GitHub](https://github.com/joesan/)
* *Scala Steward*    - [Scala Steward @ GitHub](https://github.com/scala-steward-org/scala-steward)

See also the list of [contributors](https://github.com/joesan/plant-simulator/graphs/contributors) who helped.

## License [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

The whole project is licensed under Apache License, Version 2.0. See [LICENSE.txt](./LICENSE.txt).

## Acknowledgments

* To everybody that helped in this project
* The [Monix library](https://monix.io/)

## Improvements ##

Have a look here on what a much better distributed architecture would look like:

https://github.com/joesan/plant-simulator/wiki/Application-Architecture

Keep visiting for updates!

[![HitCount](http://hits.dwyl.com/joesan/plant-simulator.svg)](http://hits.dwyl.com/joesan/plant-simulator)

##
<sup>[![forthebadge](http://forthebadge.com/images/badges/powered-by-electricity.svg)](http://forthebadge.com)</sup>
<sup>[![forthebadge](http://forthebadge.com/images/badges/built-with-grammas-recipe.svg)](http://forthebadge.com)</sup>
<sup>[![forthebadge](http://forthebadge.com/images/badges/fuck-it-ship-it.svg)](http://forthebadge.com)</sup>
