import be.vito.terrascope.snapgpt.ProcessFilesGPT;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestProcessFilesGPT {

    private String testProduct = "/data/MTDA/CGS_S2/CGS_S2_FAPAR/2018/06/18/S2A_20180618T101021Z_32TPP_CGS_V102_000/S2A_20180618T101021Z_32TPP_FAPAR_V102/10M/S2A_20180618T101021Z_32TPP_FAPAR_10M_V102.tif";

    @Test
    public void testSimpleGraph() throws URISyntaxException {
        String gptXML = Paths.get(Thread.currentThread().getContextClassLoader().getResource("simple_test.xml").toURI()).toAbsolutePath().toString();
        SparkContext.getOrCreate(new SparkConf(true).setAppName(TestProcessFilesGPT.class.getName()).setMaster("local[1]"));
        ProcessFilesGPT.main(new String[]{"-gpt", gptXML,"-output-dir","/tmp",testProduct});
    }

    @Test
    public void testSimpleGraphOtherOutputformat() throws URISyntaxException {
        String gptXML = Paths.get(Thread.currentThread().getContextClassLoader().getResource("simple_test.xml").toURI()).toAbsolutePath().toString();
        SparkContext.getOrCreate(new SparkConf(true).setAppName(TestProcessFilesGPT.class.getName()).setMaster("local[1]"));
        ProcessFilesGPT.main(new String[]{"-gpt", gptXML,"-output-dir","/tmp","-format","BEAM-DIMAP",testProduct});
    }

    @Test
    public void testErrorHandling() throws URISyntaxException, IOException {
        String gptXML = Paths.get(Thread.currentThread().getContextClassLoader().getResource("simple_test.xml").toURI()).toAbsolutePath().toString();
        SparkContext.getOrCreate(new SparkConf(true).setAppName(TestProcessFilesGPT.class.getName()).setMaster("local[1]"));
        try {

            ProcessFilesGPT.main(new String[]{"-gpt", gptXML, "-output-dir", "/tmp", "-format", "BEAM-DIMAP", "doesntExist.tif"});
            Assert.fail();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            Path path = Paths.get("/tmp/doesntExist.tif.FAILED");
            Assert.assertTrue(Files.exists(path));
            Files.delete(path);
        }
    }


    @Test
    public void testSimpleGraphNoTempFile() throws URISyntaxException {
        String gptXML = Paths.get(Thread.currentThread().getContextClassLoader().getResource("simple_test.xml").toURI()).toAbsolutePath().toString();
        SparkContext.getOrCreate(new SparkConf(true).setAppName(TestProcessFilesGPT.class.getName()).setMaster("local[1]"));
        ProcessFilesGPT.main(new String[]{"-noTempFile","-gpt", gptXML,"-output-dir","/tmp",testProduct});
    }

    @Test
    public void testPostProcessing() throws URISyntaxException, IOException {
        String gptXML = Paths.get(Thread.currentThread().getContextClassLoader().getResource("simple_test.xml").toURI()).toAbsolutePath().toString();
        String postProcessor = Paths.get(Thread.currentThread().getContextClassLoader().getResource("postprocess.py").toURI()).toAbsolutePath().toString();
        SparkContext.getOrCreate(new SparkConf(true).setAppName(TestProcessFilesGPT.class.getName()).setMaster("local[1]"));
        ProcessFilesGPT.main(new String[]{"-postprocess",postProcessor,"-noTempFile","-gpt", gptXML,"-output-dir","/tmp",testProduct});
    }
}
