package edu.avispa;

import java.io.Closeable;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class Logger implements Closeable{
    private FileWriter log;
    private FileWriter output;

    Logger(Path logFolder, Path outputFolder){
        String fileId = ZonedDateTime.now(ZoneOffset.of("-5")).format(DateTimeFormatter.ofPattern("ddHHmm"));
        try {
            String logPath = logFolder+"/log_"+fileId+".csv";
            String outputPath = outputFolder+"/output_"+fileId+".csv";
            this.log = new FileWriter(logPath, true);
            this.output = new FileWriter(outputPath, true);
            System.out.println("output to: "+outputPath+", logging to: "+logPath);
            log.write("timestamp,memory usage%,event,\n");
        } catch (IOException e) {
            e.printStackTrace();
        }        
    }

    public void log(Object... logEntry){
        write(log, logEntry);
    }

    public void output(Object... logEntry){
        write(output, logEntry);
    }

    private synchronized void write(FileWriter w, Object[] entry) {
        String timestamp = ZonedDateTime.now(ZoneOffset.of("-5")).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        String memory = String.valueOf(MainThread.usedMemory);
        try {
            w.write(timestamp+","+memory+",");
            for (Object s : entry) {
                w.write(s.toString()+",");
            }
            w.write("\n");
            w.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close(){
        try {
            log.close();
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
