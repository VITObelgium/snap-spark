package be.vito.terrascope.snapgpt;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.*;
import javax.media.jai.JAI;
import javax.media.jai.TileCache;

import be.vito.pidclient.LoggingFactory;
import be.vito.pidclient.ProcessLog;
import com.bc.ceres.binding.dom.DefaultDomElement;
import com.bc.ceres.binding.dom.DomElement;
import com.bc.ceres.core.PrintWriterProgressMonitor;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import org.apache.commons.io.IOUtils;
import org.apache.spark.SparkContext;
import org.apache.spark.TaskContext;
import org.apache.spark.api.java.JavaSparkContext;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.common.ReadOp;
import org.esa.snap.core.gpf.common.WriteOp;
import org.esa.snap.core.gpf.graph.*;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.engine_utilities.gpf.ProcessTimeMonitor;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;


/**
 * A Spark Job to run a Sentinel Toolbox GPT workflow on a number of files in parallel.
 */
public class ProcessFilesGPT implements Serializable {


    @Parameter(description = "List of input files to process. Input files will be processed in parallel.",required = false)
    private List<String> files = new ArrayList<>();

    @Parameter(names = {"-stac-input"}, description = "Json file specifying the files to process, as an array of STAC metadata.", required = false)
    private String inputConfigFile = null;

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

    @Parameter(names = {"-postprocess"}, description = "Postprocessing script file name.", required = false)
    private String postProcessor = null;


    private transient Graph graph;

    private Graph loadGraph(){
        try (FileReader f = new FileReader(getXml())){
            return GraphIO.read(f, new HashMap<>());

        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Could not find graph file: " + getXml());
        } catch (IOException e) {
            throw new IllegalArgumentException("Error while reading graph: " + getXml(), e);
        } catch (GraphException e) {
            throw new IllegalArgumentException("Error while reading graph: " + getXml(), e);
        }
    }

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

        System.out.println("This SNAP workflow file will be applied: " + getXml());
        System.out.println("Output directory: " + outputLocation);

        ProcessLog pidLogger = null;
        List<String> resultFiles = new ArrayList<String>();

        try{
            if (files.isEmpty()) {

                pidLogger = LoggingFactory.logger(null, "CGS_S1_GRD_SIGMA0_L1", null, sparkContext.sc());
                pidLogger.procStarted();

                if (inputConfigFile == null) {
                    throw new IllegalArgumentException("Either specify a list of files to process, or specify the '-stac-input' parameter.");
                }
                List<STACProduct> stacProducts = parseSTACInputFile(inputConfigFile);
                resultFiles.addAll( sparkContext.parallelize(stacProducts,stacProducts.size()).map( product -> {
                    if (product.inputs.isEmpty()) {
                        throw new IllegalArgumentException("No input products found in the metadata. These should be defined by the 'inputs' property.");
                    }

                    List<File> inputFiles = new ArrayList<>();
                    for (STACProduct sourceProduct : product.inputs.values()) {
                        if (sourceProduct == null) {
                            throw new IllegalArgumentException("No source product found in the input metadata. ");
                        }
                        Map<String, String> vitoFilenameAsset = sourceProduct.assets.get("vito_filename");
                        if (vitoFilenameAsset == null) {
                            throw new IllegalArgumentException("The source product did not specify a local 'vito_filename'. Product ID: " + sourceProduct.id);
                        }
                        String href = vitoFilenameAsset.get("href");
                        if (href == null) {
                            throw new IllegalArgumentException("The vito_filename asset does not contain a href property.");
                        }
                        File inputFile = new File(href);

                        inputFiles.add(inputFile);
                    }

                    Gson gson = new Gson();
                    String jsonString = gson.toJson(product);
                    Files.write(Paths.get(outputLocation, product.id + ".json"), jsonString.getBytes());
                    return processFile(inputFiles, product.id, true);
                }).collect());

                pidLogger.procStopped(0);
            }else{
                System.out.println("This Spark job will process the following files: " + files);
                resultFiles.addAll(sparkContext.parallelize(files, files.size()).map(this::processFile).collect());
            }
            List<String> failed = resultFiles.stream().filter(filename -> filename.endsWith("FAILED")).collect(toList());
            if (!failed.isEmpty()) {
                throw new RuntimeException("Processing failed for these files:\n" + String.join("\n", failed));
            }
        }
        catch(Exception ex){
            if(pidLogger != null){
                pidLogger.procStopped(1, ex.getMessage());
            }
            throw ex;
        }

    }

    static List<STACProduct> parseSTACInputFile(String inputConfigFile) {
        Path inputPath = Paths.get(inputConfigFile);

        Gson gson = new Gson();
        try {
            JsonReader reader = new JsonReader(new FileReader(inputPath.toFile()));
            Type type = new TypeToken<List<STACProduct>>(){}.getType();
            List<STACProduct> myMap = gson.fromJson(reader, type);
            return myMap;
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("STAC input file does not exist: " + inputConfigFile ,e);

        }
    }

    private String processFile(String inputFile) throws IOException, GraphException, InterruptedException {
        File input = new File(inputFile);
        return this.processFile(singletonList(input), input.getName(), false);
    }

    private String processFile(List<File> inputFiles, final String outputName, boolean enablePidLogging) throws IOException, GraphException, InterruptedException {
        Path failedFile = Paths.get(outputLocation, outputName + ".FAILED");
        if (Files.exists(failedFile)) {
            Path failedAttemptFile = null;
            TaskContext taskContext = TaskContext.get();
            if (taskContext != null) {
                failedAttemptFile = Paths.get(outputLocation, outputName + ".FAILED." + (taskContext.attemptNumber()-1));
            }else{
                failedAttemptFile = Paths.get(outputLocation, outputName + ".FAILED.previous");
            }
            //yarn launches multiple attempts, so file may already exist
            Files.move(failedFile, failedAttemptFile,StandardCopyOption.REPLACE_EXISTING);
        }
        ProcessLog pidLogger = null;
        if (enablePidLogging) {
            pidLogger = LoggingFactory.logger(outputName, "CGS_S1_GRD_SIGMA0_L1", null);
            for (File f : inputFiles) {
                pidLogger.addFile(f.getAbsolutePath(), "input");
            }
            pidLogger.procStarted();
        }

        Path startedFile = Paths.get(outputLocation, outputName + ".PROCESSING");
        if (Files.notExists(startedFile)) {
            Files.createFile(startedFile);
        }
        Path logFile = Paths.get(outputLocation, outputName + ".log");
        Handler fh = new FileHandler(logFile.toFile().getAbsolutePath());
        fh.setLevel (Level.ALL);
        fh.setFormatter(new SimpleFormatter());

        Logger logger = Logger.getLogger("org.esa");
        logger.setUseParentHandlers(true);
        logger.addHandler (fh);

        logger.setLevel (Level.ALL);


        try {
            if (postProcessor != null) {
                if (!Paths.get(postProcessor).toAbsolutePath().toFile().exists()) {
                    throw new IllegalArgumentException("Can not find post processor script: " + postProcessor + ". This should be an absolute path, or available in the working directory.");
                }
                postProcessor = Paths.get(postProcessor).toAbsolutePath().toString();
                if (!Paths.get(postProcessor).toAbsolutePath().toFile().canExecute()) {
                    throw new IllegalArgumentException("The postprocessing script is not executable: " + Paths.get(postProcessor).toAbsolutePath().toString());
                }
            }

            ProcessTimeMonitor timeMonitor = new ProcessTimeMonitor();
            timeMonitor.start();
            SystemUtils.init3rdPartyLibs(ProcessFilesGPT.class);
            System.err.println("SNAP Application Data Dir: " + SystemUtils.getApplicationDataDir());
            System.err.println("SNAP Auxiliary Data Dir: " + SystemUtils.getAuxDataPath());
            System.err.println("SNAP Cache Dir: " + SystemUtils.getCacheDir());
            SystemUtils.LOG.info("SNAP Cache Dir: " + SystemUtils.getCacheDir());
            System.err.println("Processing files: " + inputFiles.stream().map(File::getName).collect(joining(", ")));
            System.err.println("Processing workflow: " + getXml());
            System.err.println("Output location: " + outputLocation);
            if (enablePidLogging) {
                pidLogger.addTrace("Processing workflow: " + getXml());
                pidLogger.addTrace("Output location: " + outputLocation);
            }
            if (postProcessor != null) {
                SystemUtils.LOG.info("Post processing executable: " + postProcessor);
                if (enablePidLogging) {
                    pidLogger.addTrace("Post processing executable: " + postProcessor);
                }
            }
            if (pidLogger != null) {
                pidLogger.writeLog();
            }


            File outputFile = new File(outputLocation, outputName);
            File finalOutput = outputFile;
            if (useStagingDirectory) {
                Path tempDir = Files.createTempDirectory(Paths.get(".").toAbsolutePath(), "snapoutput");
                outputFile = tempDir.resolve(outputName).toFile();
            }
            setIO(inputFiles, outputFile, formatName);
            new GraphProcessor().executeGraph(this.getGraph(), new PrintWriterProgressMonitor(System.out){
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

            //try to release memory
            TileCache tileCache = JAI.getDefaultInstance().getTileCache();
            if (tileCache != null) {
                tileCache.flush();
            }
            System.gc();
            SystemUtils.LOG.info(" time: " + ProcessTimeMonitor.formatDuration(duration) + " (" + duration + " s)");
            SystemUtils.LOG.info( "SNAP processing graph output directory contains these files: " );
            Path tempOutputDir = outputFile.toPath().getParent();
            Files.list(tempOutputDir)
                    .map(Path::toString)
                    .forEach(SystemUtils.LOG::info);

            if (postProcessor != null) {
                if (enablePidLogging) {
                    pidLogger.addTrace("Starting postprocessing.");
                    pidLogger.writeLog();
                }
                doPostProcess(postProcessor, outputFile.toPath(),logFile,pidLogger);
            }

            if (useStagingDirectory) {
                SystemUtils.LOG.info("Copying file to final destination: " + finalOutput.toString());
                Path finalOutputPath = Paths.get(outputLocation);
                Files.walk(tempOutputDir).filter(path -> !path.equals(tempOutputDir)).forEach(path -> {
                    try {
                        Files.copy(path, finalOutputPath.resolve(tempOutputDir.relativize(path)), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            fh.flush();
            fh.close();
            Path doneFile = Paths.get(outputLocation, outputName + ".DONE");
            Files.move(logFile, doneFile,StandardCopyOption.REPLACE_EXISTING);
            if(pidLogger != null)
                pidLogger.procStopped(0);
            return doneFile.toString();
        }catch(Throwable t){
            SystemUtils.LOG.log(Level.SEVERE,t.getLocalizedMessage(),t);
            fh.flush();
            fh.close();
            Files.move(logFile,failedFile,StandardCopyOption.REPLACE_EXISTING);
            if(pidLogger != null)
                pidLogger.procStopped(1, t.getMessage());
            TaskContext taskContext = TaskContext.get();
            if (taskContext != null) {
                if (taskContext.attemptNumber() < 3) {
                    throw t;
                }
            }else{
                throw t;
            }
            return failedFile.toString();
        }finally {
            if (Files.exists(startedFile)) {
                Files.delete(startedFile);
            }

        }
    }

    private void setIO(List<File> srcFiles, File tgtFile, String format) {
        String readOperatorAlias = OperatorSpi.getOperatorAlias(ReadOp.class);
        List<Node> readerNodes = findNodes(this.getGraph(), readOperatorAlias);
        // set file parameter for as many Read nodes we have a source file
        for (int i = 0; i < Math.min(srcFiles.size(),  readerNodes.size()); i++) {
        	Node readerNode = readerNodes.get(i);
        	DomElement param = new DefaultDomElement("parameters");
        	param.createChild("file").setValue(srcFiles.get(i).getAbsolutePath());
            readerNode.setConfiguration(param);
        }

        String writeOperatorAlias = OperatorSpi.getOperatorAlias(WriteOp.class);
        Node writerNode = findNode(this.getGraph(), writeOperatorAlias);
        if (writerNode != null && tgtFile != null) {
            DomElement origParam = writerNode.getConfiguration();
            origParam.getChild("file").setValue(tgtFile.getAbsolutePath());
            if (format != null) {
                origParam.getChild("formatName").setValue(format);
            }
        }

    }

    private static List<Node> findNodes(Graph graph, String alias) {
    	List<Node> nodes = new ArrayList<>();
    	for (Node n : graph.getNodes()) {
    		if (n.getOperatorName().equals(alias)) {
    			nodes.add(n);
    		}
    	}
    	return nodes;
    }

    private static Node findNode(Graph graph, String alias) {
        for (Node n : graph.getNodes()) {
            if (n.getOperatorName().equals(alias)) {
                return n;
            }
        }

        return null;
    }

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
        this.graph = loadGraph();
    }

    protected Graph getGraph() {
        if (graph == null) {
            this.graph = this.loadGraph();
        }
        return graph;
    }

    private static class IOThreadHandler extends Thread {
        private InputStream inputStream;
        private final ProcessLog pidLogger;

        IOThreadHandler(InputStream inputStream, ProcessLog pidLogger) {
            this.inputStream = inputStream;
            this.pidLogger = pidLogger;
        }

        public void run() {
            Scanner br = null;
            try {
                br = new Scanner(new InputStreamReader(inputStream));
                String line = null;
                while (br.hasNextLine()) {
                    line = br.nextLine();
                    SystemUtils.LOG.info(line);
                    if (pidLogger != null) {
                        pidLogger.addTrace(line);
                        pidLogger.writeLog();
                    }
                }
            }  finally {
                br.close();
            }
        }

    }

    static void doPostProcess(String postProcessor, Path outputFile, Path logFile, ProcessLog pidLogger) throws IOException, InterruptedException {

        ProcessBuilder builder = new ProcessBuilder().command(postProcessor, outputFile.toString()).directory(outputFile.getParent().toFile());
        builder.environment().put("PYTHONUNBUFFERED", "1");

        SystemUtils.LOG.log(Level.INFO, "Starting post processing: ");
        SystemUtils.LOG.log(Level.INFO,String.join(" ", builder.command()));
        Process process = builder.start();

        IOThreadHandler outputHandler = new IOThreadHandler(process.getInputStream(),pidLogger);
        outputHandler.start();

        process.waitFor();

        List<String> errors = IOUtils.readLines(process.getErrorStream());
        for (String error : errors) {
            SystemUtils.LOG.severe(error);
        }
        process.destroy();
        outputHandler.join();
        if (process.exitValue() != 0) {
            throw new RuntimeException("Postprocessing failed: " + process.exitValue());
        }


    }
}
