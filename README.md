# ThreadSpawner

Program that spawns Java processes in parallel, by executing a .jar file with certain parameters. The purpose is to parallelize bisimulation equivalence checking between a set of timed automata, in a everyone-vs-everyone round robin fashion.

# Options

`ThreadSpawner` understands the following options:

* `-E jarpath` Path to the Equivalance checking algorithm .jar implementation. It must accept two arguments, two filepaths that point to the input automata. Required.
* `-F path` Path to the folder that contains the automata .xml files. Required.
* `-o pathfile` Path to output file, where the equivalences will be registered once they are determined. The dafault path is `~/output.txt`.
* `-l pathfile` Path to log file to append generated logging. The dafault log file is `~/log.txt`.
* `-d string` Sets the description to be written in the logs of that particular execution. Useful to register what exactly was tried, like "ccn-like automata with 3 threads fixed".
* `--FixedThreads number` Specifies a fixed thread pool size of `number`.
* `--ModulateThreads lowerBound LowerBound` Lets the program regulate the size of the thread pool dinamically, trying to keep memory usage percentage between the integers `lowerBound` and `upperBound`. For obvious reasons, both values must be between 0 and 100.
* `--CalculateFixedThreads` Asks the program to determine the best size of a fixed thread pool based on the machine available memory. This is the default behavior.
* `--ModulationPeriod secs` How often should the program try to regulate the thread pool size, each secs seconds. Only applies with the `--ModulateThreads` policy. The default value is 5 seconds.
* `--MemoryUsagePerProcess, -m Mb` How many megabytes of memory are the processes estimated to use at their peak. Used with the `--CalculateFixedThreads` policy to divide available memory between memory usage per process and guess how many processes can be carried at once. The default value is 3072mb.



 