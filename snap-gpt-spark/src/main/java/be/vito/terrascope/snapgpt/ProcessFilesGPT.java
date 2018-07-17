package be.vito.terrascope.snapgpt;

import com.bc.ceres.core.PrintWriterProgressMonitor;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaSparkContext;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.engine_utilities.gpf.ProcessTimeMonitor;
import org.esa.snap.graphbuilder.rcp.dialogs.support.GPFProcessor;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;


/**
 * A Spark Job to run a Sentinel Toolbox GPT workflow on a number of files in parallel.
 */
public class ProcessFilesGPT implements Serializable {


    @Parameter(description = "Files", required = true)
    private List<String> files = new ArrayList<>();

    @Parameter(names = {"-gpt"}, description = "SNAP GPT XML file.", required = true)
    private String xml = "S1_GRD_Processing_toSigma0.xml";

    @Parameter(names = {"-output-dir"}, description = "Output directory.", required = true)
    private String outputLocation = null;

    @Parameter(names = {"-format"}, description = "SNAP format name, by default, the format from the gpt file will be used.", required = false)
    private String formatName = null;
    

    @Parameter(names = {"-noTempFile"}, description = "Do not use a temporary staging directory before writing to the actual output location.", required = false)
    private boolean useStagingDirectory = true;

    @Parameter(names = {"-output-regex"}, description = "Regex to apply to input to get output.")
    private String outputRegex = null;

    public static void main(String[] args) {
        ProcessFilesGPT processor = new ProcessFilesGPT();
        JCommander.newBuilder()
                .addObject(processor)
                .build()
                .parse(args);
        processor.run();

    }

    private void run() {
        JavaSparkContext sparkContext = JavaSparkContext.fromSparkContext(SparkContext.getOrCreate());
        sparkContext.setLogLevel("WARN");

        sparkContext.parallelize(files).foreach( file -> {

            File inputFile = new File(file);
            Path startedFile = Paths.get(outputLocation, inputFile.getName() + ".PROCESSING");
            if (Files.notExists(startedFile)) {
                Files.createFile(startedFile);
            }
            Path logFile = Paths.get(outputLocation, inputFile.getName() + ".log");
            Handler fh = new FileHandler(logFile.toFile().getAbsolutePath());
            fh.setLevel (Level.ALL);
            fh.setFormatter(new SimpleFormatter());

            Logger logger = Logger.getLogger("org.esa");
            logger.setUseParentHandlers(true);
            logger.addHandler (fh);

            logger.setLevel (Level.ALL);

            try {

                ProcessTimeMonitor timeMonitor = new ProcessTimeMonitor();
                timeMonitor.start();
                SystemUtils.init3rdPartyLibs(ProcessFilesGPT.class);
                System.err.println("SNAP Application Data Dir: " + SystemUtils.getApplicationDataDir());
                System.err.println("SNAP Auxiliary Data Dir: " + SystemUtils.getAuxDataPath());
                System.err.println("SNAP Cache Dir: " + SystemUtils.getCacheDir());
                SystemUtils.LOG.info("SNAP Cache Dir: " + SystemUtils.getCacheDir());
                System.err.println("Processing file: " + file);
                System.err.println("Processing workflow: " + xml);
                System.err.println("Output location: " + outputLocation);

                final GPFProcessor proc = new GPFProcessor(new File(xml));

                File outputFile = new File(outputLocation, inputFile.toPath().getFileName().toString());
                File finalOutput = outputFile;
                if (useStagingDirectory) {
                    Path tempDir = Paths.get("GPT_TEMPORARY_OUTPUT");
                    if (!tempDir.toFile().exists()) {
                        Files.createDirectory(tempDir);
                    }
                    outputFile = tempDir.resolve(inputFile.toPath().getFileName().toString()).toFile();
                }
                proc.setIO(inputFile, outputFile, formatName);
                proc.executeGraph(new PrintWriterProgressMonitor(System.out){
                    @Override
                    protected void printStartMessage(PrintWriter pw) {
                        super.printStartMessage(pw);
                        SystemUtils.LOG.info("Started " + this.getMessage());
                    }

                    @Override
                    protected void printWorkedMessage(PrintWriter pw) {
                        super.printWorkedMessage(pw);
                    }

                    @Override
                    protected void printMinorWorkedMessage(PrintWriter pw) {
                        super.printMinorWorkedMessage(pw);
                    }

                    @Override
                    protected void printDoneMessage(PrintWriter pw) {
                        super.printDoneMessage(pw);
                        SystemUtils.LOG.info("Done " + this.getMessage());
                    }
                });

                final long duration = timeMonitor.stop();

                SystemUtils.LOG.info(" time: " + ProcessTimeMonitor.formatDuration(duration) + " (" + duration + " s)");
                if (useStagingDirectory) {
                    SystemUtils.LOG.info("Copying file to final destination: " + finalOutput.toString());
                    Files.list(outputFile.toPath().getParent()).forEach(path -> {
                        try {
                            Files.copy(path,Paths.get(outputLocation,path.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
                fh.flush();
                fh.close();
                Files.copy(logFile, Paths.get(outputLocation, inputFile.getName() + ".DONE"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            }catch(Throwable t){
                SystemUtils.LOG.log(Level.SEVERE,t.getLocalizedMessage(),t);
                fh.flush();
                fh.close();
                Path failedFile = Paths.get(outputLocation, inputFile.getName() + ".FAILED");
                Files.copy(logFile,failedFile,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
                throw t;
            }finally {
                if (Files.exists(startedFile)) {
                    Files.delete(startedFile);
                }

            }
        });
    }
}
