# ThreadSpawner

Program that spawns Java processes in parallel, by executing a .jar file with certain parameters. The purpose is to parallelize bisimulation equivalence checking between a set of timed automata, in a everyone-vs-everyone round robin fashion.

## Compilation

Run 

```
mvn clean compile package -f "./pom.xml"
```

And let Maven compile classes, pull dependencies, and package the project in a `.jar` file that will be placed in `./target/`.

## Usage
```
usage: ThreadSpawner [-h] -E JARPATH -F PATH [-o PATHFILE] [-l PATHFILE] [-d STRING] 
                    [--ModulationPeriod SECS] [--MemoryUsagePerProcess MB]
                    [--FixedThreads NUMBER | --ModulateThreads UPPERBOUND LOWERBOUND 
                    | --CalculateFixedThreads]

-E JARPATH                              Path to the Equivalance checking algorithm .jar implementation. 
                                        It must accept two arguments, two filepaths that point to the 
                                        input automata. Required.
-F PATH                                 Path to the folder that contains the automata .xml files. Required.
-o OUTFOLDER                            Folder path to create an output file, where the equivalences 
                                        will be registered once they are determined. The default path is 
                                        ~.
-l LOGFOLDER                            Folder path to create a log file. The default path is ~.
-d STRING                               Sets the description to be written in the logs of that particular 
                                        execution. Useful to register what exactly was tried, like "ccn-like
                                        automata with 3 threads fixed".
--FixedThreads NUMBER                   Specifies a fixed thread pool size of number.
--ModulateThreads LOWERBOUND UPPERBOUND Lets the program regulate the size of the thread pool dinamically, 
                                        trying to keep memory usage percentage between the integers 
                                        lowerBound and upperBound. For obvious reasons, both values must be 
                                        between 0 and 100.
--CalculateFixedThreads                 Asks the program to determine the best size of a fixed thread pool 
                                        based on the available memory of the machine. This is the default 
                                        behavior.
--ModulationPeriod SECS                 How often should the program try to regulate the thread pool size, 
                                        each SECS seconds. Only applies with the --ModulateThreads policy. 
                                        The default value is 5 seconds.
-m, --MemoryUsagePerProcess MB          How many megabytes of memory are the processes estimated to use 
                                        at their peak. Used with the --CalculateFixedThreads policy to divide 
                                        available memory between memory usage per process and guess how many 
                                        processes can be carried at once. The default value is 3072mb.
```