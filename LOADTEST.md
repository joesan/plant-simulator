## Load Testing the Application

I have included some possibilities with which you can load test the application. The idea is to create as many PowerPlant's as we want in the database and use this database to start the application. For convenience, I have created a script with which you can generate as many data as you want. 

The script db-generator.sh can be found [here](https://github.com/joesan/database-projects/tree/master/power-plant-simulator/h2). Have a look at the README.md on that link to know how you could generate the random massive data set.

For even more convenience, I have created alredy generated and pre-populated about 100,000 PowerPlant's in the H2 database file [plant-sim-load-test.mv.db](https://github.com/joesan/plant-simulator/blob/master/plant-sim-load-test.mv.db). All you need to do is just to run the application using this file as the database. So just issue the following command:

```
sbt -Denv=loadtest run
```

After you do this, I assure you that you will have fun! Ok if that fun was not enough, let us have even more fun. Let us dispatch all of the PowerPlants at once. To do this I have prepared a simple script that can send dispatch commands. 

```
0. Make sure that the application is up and running and make sure that you have a WebSocket connection open
   To open a command line WebSocket client, you can do the following:
   npm install -g wscat
   wscat -c ws://localhost:9000/plantsim/powerplant/events
1. Open another terminal and navigate to the project rool folder
2. Call the dispatch script - This script will burst dispatch commands to the set of PowerPlant's
   ./dispatch.sh
3. Make sure that you have some fun doing this!   
```

Dispatching all the PowerPlant's would mean that we will spin up those manxy actors as many PowerPlant's we have and the system will be responsive even after that. This is the guarantee you get when you build your system using the [reactive manifesto](https://www.reactivemanifesto.org/)

If you open the WebSocket client from a browser (for example the Chrome WebSocket client, you will notice that the browser freezes because it cannot handle the burst of messages coming from the server when you dispatch all of the PowerPlant's. This problem could be mitigated by something called [backpressure](https://github.com/ReactiveX/RxJava/wiki/Backpressure)
