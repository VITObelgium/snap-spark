package be.vito.terrascope.snapgpt;

import com.bc.ceres.core.PrintWriterProgressMonitor;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaSparkContext;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.engine_utilities.gpf.ProcessTimeMonitor;
import org.esa.snap.engine_utilities.util.TestUtils;
import org.esa.snap.graphbuilder.rcp.dialogs.support.GPFProcessor;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;


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

        sparkContext.parallelize(files).foreach( file -> {
            ProcessTimeMonitor timeMonitor = new ProcessTimeMonitor();
            timeMonitor.start();
            System.err.println("SNAP Application Data Dir: " +SystemUtils.getApplicationDataDir());
            System.err.println("SNAP Auxiliary Data Dir: " +SystemUtils.getAuxDataPath());
            System.err.println("SNAP Cache Dir: " +SystemUtils.getCacheDir());

            final GPFProcessor proc = new GPFProcessor(new File(xml));
            File inputFile = new File(file);
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
            proc.executeGraph(new PrintWriterProgressMonitor(System.out));

            final long duration = timeMonitor.stop();

            TestUtils.log.info(" time: " + ProcessTimeMonitor.formatDuration(duration) + " (" + duration + " s)");
            if (useStagingDirectory) {
                TestUtils.log.info("Copying file to final destination: " + finalOutput.toString());
                Files.copy(outputFile.toPath(), finalOutput.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
        });
    }
}
