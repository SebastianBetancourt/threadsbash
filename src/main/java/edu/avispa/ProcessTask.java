package edu.avispa;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class ProcessTask implements Runnable {

    private final String automatonA;
    private final String automatonB;
    private final int thTry;

    ProcessTask(String automatonA, String automatonB, int thTry) {
        this.automatonA = automatonA;
        this.automatonB = automatonB;
        this.thTry = thTry;
    }

    public void run() {
        Logger logger = MainThread.logger;
        final String comparisonId = this.automatonA.concat("-").concat(automatonB).concat("-" + thTry);

        String bisimilarity = "";

        if(MainThread.transitivityInference){
            Class classOfA = Class.getClassOf(automatonA);
            boolean alreadyEquivalent = classOfA.equivalent.contains(automatonB);
            boolean alreadyNotEquivalent = classOfA.notEquivalent.contains(automatonB);
            if(alreadyEquivalent || alreadyNotEquivalent){
                if(alreadyEquivalent){
                    bisimilarity = "BISIM TRUE";
                    MainThread.bisimilarList.add(automatonA.concat("<->").concat(automatonB));
                    logger.output(automatonA, automatonB, "INFERRED BY TRANSITIVITY", MainThread.bisimilarList.size(),
                            String.join("  ", MainThread.bisimilarList));
                } else if (alreadyNotEquivalent) {
                    bisimilarity = "BISIM FALSE";
                    logger.output(automatonA, automatonB, "INFERRED BY TRANSITIVITY");
                }
                logger.log("PROCESS INFERRED", comparisonId, bisimilarity, getTotalProgress());
                MainThread.remainingComparisons.countDown();
                MainThread.inferredComparisons.incrementAndGet();
                return;
            }
        }
        try {
            logger.output(this.automatonA.concat(" ").concat(automatonB));

            String[] cmd = { "java",
                    //"-Xmx3072m",
                    "-Djava.library.path=PATH/x64_linux-gnu",
                    "-XX:ErrorFile=/dev/null",
                    "-cp",
                    MainThread.equivalenceProgram.getAbsolutePath(),
                    "main.Main",
                    MainThread.automataFolder.getAbsolutePath().concat(File.separator).concat(this.automatonA),
                    MainThread.automataFolder.getAbsolutePath().concat(File.separator).concat(this.automatonB), };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectError(new File("/dev/null"));
            File outputBuffer = File.createTempFile(comparisonId, ".comparisonoutput.temp");
            pb.redirectOutput(outputBuffer);
            Process p = pb.start();

            long start = System.currentTimeMillis();

            logger.log("PROCESS START", comparisonId);
            MainThread.startedProcesses.incrementAndGet();
            if(!p.waitFor( MainThread.maximumTimePerComparison,TimeUnit.MILLISECONDS)){
                logger.log("PROCESS TIMEOUT", comparisonId, thTry + " try", getTotalProgress());
                MainThread.remainingComparisons.countDown();
                MainThread.timedOutComparisons.incrementAndGet();
                MainThread.errorProcesses.incrementAndGet();
                outputBuffer.delete();
                return;
            }

            BufferedReader output = new BufferedReader(new FileReader(outputBuffer));

            String line = null;

            while ((line = output.readLine()) != null) {
                if (line.startsWith("Result of bisimulation check:")) {
                    long now = System.currentTimeMillis();
                    long elapsedTime = now - start;
                        Class classOfB =  Class.getClassOf(automatonB);
                        Class classOfA = Class.getClassOf(automatonA);

                    if (line.contains("Result of bisimulation check: true")) {
                        bisimilarity = "BISIM TRUE";
                        MainThread.bisimilarList.add(automatonA.concat("<->").concat(automatonB));
                        logger.output(automatonA, automatonB, elapsedTime, MainThread.bisimilarList.size(),
                                String.join("  ", MainThread.bisimilarList));
                        classOfA.equivalent.addAll(classOfB.equivalent);
                        classOfA.notEquivalent.addAll(classOfB.notEquivalent);
                        if(!classOfA.equals(classOfB)){
                            Class.equivalentClasses.remove(classOfB);
                        }
                    } else if (line.startsWith("Result of bisimulation check: false")) {
                        bisimilarity = "BISIM FALSE";
                        logger.output(automatonA, automatonB, elapsedTime);

                        classOfA.notEquivalent.addAll(classOfB.equivalent);
                        classOfB.notEquivalent.addAll(classOfA.equivalent);
                    }
                    outputBuffer.delete();
                    logger.log("PROCESS SUCCESSFUL", comparisonId, bisimilarity, getTotalProgress(),
                            (elapsedTime / 1000.0) + "s");
                    MainThread.computedComparisons.incrementAndGet();
                    MainThread.succesfulProcesses.incrementAndGet();
                }
            }

            int exitValue = p.exitValue();
            String retryOrSkip = "";
            if (exitValue != 0) {
                MainThread.modulateDown();
                long now = System.currentTimeMillis();
                long elapsedTime = now - start;
                if (thTry < MainThread.tries) {
                    MainThread.executor.submit(new ProcessTask(automatonA, automatonB, thTry + 1));
                    retryOrSkip = "REQUEUEING";
                } else {
                    retryOrSkip = "SKIPPED";
                    MainThread.skippedComparisons.incrementAndGet();
                }
                logger.log("PROCESS ERROR", comparisonId, "EXIT VALUE " + exitValue, retryOrSkip, thTry + " try", getTotalProgress(),
                (elapsedTime / 1000.0) + "s");
                outputBuffer.delete();
                MainThread.errorProcesses.incrementAndGet();
            }
            if (exitValue == 0 || retryOrSkip == "SKIPPED") {
                MainThread.remainingComparisons.countDown();
            }
            outputBuffer.delete();
             output.close();

        } catch (Exception e) {
            logger.log("ERROR", comparisonId,
                    e.getLocalizedMessage());
            MainThread.remainingComparisons.countDown();
            e.printStackTrace();
        }

    }

    private String getTotalProgress(){
        return (MainThread.todoComparisons - MainThread.remainingComparisons.getCount() + 1) + "/"
        + MainThread.todoComparisons;
    }
}
