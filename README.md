# ![plant-simulator-ui](logo.png)

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/996bef52feb148039c61f0db9cff9830)](https://www.codacy.com/app/joesan/plant-simulator?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=joesan/plant-simulator&amp;utm_campaign=Badge_Grade)
[![codecov](https://codecov.io/gh/joesan/plant-simulator/branch/master/graph/badge.svg)](https://codecov.io/gh/joesan/plant-simulator)
[![Build Status](https://travis-ci.org/joesan/plant-simulator.svg?branch=master)](https://travis-ci.org/joesan/plant-simulator)
[![CII Best Practices](https://bestpractices.coreinfrastructure.org/projects/1185/badge)](https://bestpractices.coreinfrastructure.org/projects/1185)
[![Github All Releases](https://img.shields.io/github/downloads/joesan/plant-simulator/total.svg)]()
[![Powered By](https://img.shields.io/badge/poweredBy-Scala-brightgreen.svg)]()

##

# plant-simulator [![Open Source](https://img.shields.io/badge/Open%20Source-100%25-yellowgreen.svg)](https://github.com/ellerbrock/open-source-badges/)
A simple simulator application that simulates the running of a power producing plant. The application works by exposing several API's that can be used to get the status of a PowerPlant.

For simplicity, this simulator assumes that there are essentially two types of power producing
plants:

1. A PowerPlant that is steerable by turning it on or off (ex., battery)
2. A PowerPlant that is steerable by ramping it up (ex., generators, gas turbines, CHP units)

We also assume that all the units that we deal here are in kw for power and in seconds for time.

```diff
-ALERT: We do not steer Nuclear Power Plants - We stay away from them!
```

## Getting Started 

This project is completely OpenSource, so feel free to explore and if you have better ideas, please let me know or even better please contribute!

First some background: 
Normally in a PowerPlant, there are several power producing units. Each one of these power producing units could be turned on which means or in other words, it can be ramped up. The other way around should also be possible which means I can be a power consuming unit in a PowerPlant and this power consuming unit could be seen as a load to which I can send a singal so that I consume power from the grid.

This simulation does exactly that. I can register either a power producing unit or a power consuming unit with this simulation application. When registering these units, I specify how much power each one has and how much power I can ramp up or ramp down. I then expose an API with which I can communicate with the simulator. 

The API documentation can be found [here](https://github.com/joesan/plant-simulator/wiki)

These instructions below will get you a copy of the project up and running on your local machine for development and testing purposes. 

See [deployment](#Deployment) for notes on how to deploy the project on a live system.

### Prerequisites

```
1. Install Java Version 8
2. Install Scala Version 2.11.8
3. Install IntelliJ - Latest community edition and then install the latest Scala plugin available
```

### Installing

Follow the steps below to import the project into IntelliJ

```
1. Clone the project from: 
   git clone https://github.com/joesan/plant-simulator.git
   
2. Fire up IntelliJ and import the project
   
3. If you are opening IntelliJ for the first time, set up the Scala library in IntelliJ
```

### Running tests

You have the option here to either run tests from IntelliJ or from the command line

To run tests from the command line, do the following:

```
1. Open a terminal and navigate to the project root folder 
   
2. Issue the following command:
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
NOTE: For simplicity, there is local H2 database setup (plant-simulator.mv.db). The connection details 
are to be found under conf/application.dev.conf
   
You can comfortably run the application using the following command (No WiFi, no Network required)
sbt -Denv=dev run
```

```
0. We need a database that is up and running. Please have a look at
   this project that contains the setup script for the database:
   https://github.com/joesan/database-projects/tree/master/power-plant-simulator
   
   Once you have the database up and running, configure the database credentials in the 
   application.xxx.conf file (xxx stands for test or dev or prod)
   
1. Open a terminal and navigate to the project root folder 
   
2. Issue one of the following commands to run:
   To run against a default (application.conf)
   sbt run
   
   To run against the test environment
   sbt -Denv=test run
   
   To run in production mode (application.prod.conf):
   sbt -Denv=prod -Dlogger.resource=logger-prod.xml run

3. Navigate to the following url on your favorite browser:
   http://localhost:9000
   
4. To do something meaningful with the application, have a look at the [documentation](https://github.com/joesan/plant-  simulator/wiki) for more information on how to call the API's!
   
``` 

To visualize the application and to do some real work with it, have a look [here](https://github.com/joesan/plant-simulator-ui) - Currently under development

#### To run as a Docker container

```
1. Makue sure you have Docker installed on your host system 
   
2. Issue one of the following commands to build the docker image:
   sbt docker:publishLocal
   
   The above command would have built the image and push it to your local
   docker registry (on your host system)
   
3. Issue the following command to run the container:
   TODO - Document
   
4. Navigate to the following url on your favorite browser:
   
   
   [TODO] Add the Swagger API Docs and put the URL here
   
```

## Deployment

[[TODO]] Add additional notes about how to deploy this on a live system

## Tools Used

* [SBT](http://www.scala-sbt.org/) - Scala Build Tool

* [Travis CI](https://travis-ci.com/) - Hosted Continuous Integration

## Contributing [![contributions welcome](https://img.shields.io/badge/contributions-welcome-brightgreen.svg?style=flat)](https://github.com/joesan/plant-simulator/issues)

For more information on how to contribute, have a look [here](https://github.com/joesan/plant-simulator/blob/master/CONTRIBUTING.md)

## Authors / Maintainers

* *Joesan*           - [Joesan @ GitHub](https://github.com/joesan/)

See also the list of [contributors](https://github.com/joesan/plant-simulator/graphs/contributors) who helped.

## License [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

The whole project is licensed under Apache License, Version 2.0. See [LICENSE.txt](./LICENSE.txt).

## Acknowledgments

* To everybody that helped in this project
* The [Monix library](https://monix.io/)

##
<sup>[![forthebadge](http://forthebadge.com/images/badges/powered-by-electricity.svg)](http://forthebadge.com)</sup>
<sup>[![forthebadge](http://forthebadge.com/images/badges/built-with-grammas-recipe.svg)](http://forthebadge.com)</sup>
<sup>[![forthebadge](http://forthebadge.com/images/badges/fuck-it-ship-it.svg)](http://forthebadge.com)</sup>
