package edu.avispa;

import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import oshi.hardware.GlobalMemory;

public class ProcessTask implements Runnable {

    private final ArrayList<String> bisimilarList;
    private final File pathFolder;
    private final String automatonA;
    private final String automatonB;
    private final File saveFile;
    private final File saveFileLog;
    private final File equivalenceProgram;
    private final AtomicInteger succesfulComparisons;
    private final int totalComparisons;
    CountDownLatch latch;

    private FileWriter fw;
    private FileWriter fwLog;
    private GlobalMemory memory;

    ProcessTask(int totalComparisons,
            AtomicInteger succesfulComparisons,
            ArrayList<String> bisimilarList,
            File pathFolder,
            String automatonA,
            String automatonB,
            File saveFile,
            File saveFileLog,
            GlobalMemory memory,
            File equivalenceProgram,
            CountDownLatch latch) throws IOException {
        this.totalComparisons = totalComparisons;
        this.succesfulComparisons = succesfulComparisons;
        this.bisimilarList = bisimilarList;
        this.pathFolder = pathFolder;
        this.automatonA = automatonA;
        this.automatonB = automatonB;
        this.saveFile = saveFile;
        this.saveFileLog = saveFileLog;
        this.memory = memory;
        this.equivalenceProgram = equivalenceProgram;
        this.latch = latch;
    }

    public synchronized void writeFile(String[] write) {
        try {
            for (String w : write) {
                this.fw.write(w);
                this.fw.write(",");
            }
            this.fw.write("\n");
            this.fw.flush();
        } catch (IOException e) {
            writeFileLog(write);
        }

    }

    public synchronized void writeFileLog(String[] write) {
        try {
            for (String w : write) {
                this.fwLog.write(w);
                this.fwLog.write(",");
            }
            this.fwLog.write("\n");
            this.fwLog.flush();

        } catch (IOException e) {
            System.out.println(e.toString());
            for (String w : write) {
                System.out.print(w);
            }
            System.out.println();
        }

    }

    public void run() {

        try {
            this.fw = new FileWriter(saveFile, true);
            this.fwLog = new FileWriter(saveFileLog, true);
            String[] logWriter = new String[1];
            logWriter[0] = this.automatonA.concat(" ").concat(automatonB);

            writeFile(logWriter);

            String[] cmd = { "java",
                    "-Djava.library.path=PATH/x64_linux-gnu",
                    "-cp",
                    this.equivalenceProgram.getAbsolutePath(),
                    "main.Main",
                    this.pathFolder.getAbsolutePath().concat(File.separator).concat(this.automatonA),
                    this.pathFolder.getAbsolutePath().concat(File.separator).concat(this.automatonB), };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectError(new File("/dev/null"));
            // pb.redirectErrorStream(true);
            Process p = pb.start();

            long start = System.currentTimeMillis();

            writeFileLog(new String[] { "PROCESS START", String.valueOf(start), automatonA, automatonB });
            String progress = (totalComparisons - latch.getCount() + 1) + "/" + totalComparisons;

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = null;

            stdInput.readLine();
            stdInput.readLine();
            stdInput.readLine();
            stdInput.readLine();
            while ((line = stdInput.readLine()) != null) {
                if (line.startsWith("Result of bisimulation check:")) {
                    long end = System.currentTimeMillis();
                    String elapsedTime = String.valueOf(end - start);
                    String[] outWrite = {};
                    String bisimilarity = "";
                    if (line.contains("Result of bisimulation check: true")) {
                        outWrite = new String[5];
                        outWrite[0] = automatonA;
                        outWrite[1] = automatonB;
                        outWrite[2] = elapsedTime;
                        outWrite[3] = Integer.toString(this.bisimilarList.size());
                        outWrite[4] = String.join("  ", this.bisimilarList);

                        this.bisimilarList.add(automatonA.concat("<->").concat(automatonB));
                        bisimilarity = "BISIM TRUE";
                    } else if (line.startsWith("Result of bisimulation check: false")) {
                        outWrite = new String[3];
                        outWrite[0] = automatonA;
                        outWrite[1] = automatonB;
                        outWrite[2] = elapsedTime;
                        bisimilarity = "BISIM FALSE";
                    }
                    writeFile(outWrite);
                    start = end;

                    writeFileLog(new String[] { "PROCESS DONE", String.valueOf(end), automatonA, automatonB,
                            bisimilarity, progress, elapsedTime });
                    succesfulComparisons.incrementAndGet();
                }
            }

            p.waitFor();
            int exitValue = p.exitValue();
            if (exitValue != 0) {
                long end = System.currentTimeMillis();
                String elapsedTime = String.valueOf(end - start);
                double usedMemory = 100 - (100 * memory.getAvailable() / memory.getTotal());
                writeFileLog(new String[] { "PROCESS ERROR", String.valueOf(end), automatonA, automatonB,
                        "EXIT VALUE " + exitValue, "USED MEMORY " + usedMemory + "%", progress, elapsedTime });
            }
            stdInput.close();
            this.latch.countDown();

            this.fwLog.close();
            this.fw.close();
        } catch (Exception e) {
            writeFileLog(new String[] { "ERROR", String.valueOf(System.currentTimeMillis()), automatonA, automatonB,
                    e.getLocalizedMessage() });
            this.latch.countDown();
            e.printStackTrace();
        }

    }

}
