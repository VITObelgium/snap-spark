package be.vito.terrascope.snapgpt;

import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestProcessFilesGPT {

    private String testProduct = "/data/MTDA/CGS_S2/CGS_S2_FAPAR/2018/06/18/S2A_20180618T101021Z_32TPP_CGS_V102_000/S2A_20180618T101021Z_32TPP_FAPAR_V102/10M/S2A_20180618T101021Z_32TPP_FAPAR_10M_V102.tif";

    @Test
    public void testSimpleGraph() throws URISyntaxException {
        String gptXML = getAbsolutePath("simple_test.xml");
        SparkContext.getOrCreate(new SparkConf(true).setAppName(TestProcessFilesGPT.class.getName()).setMaster("local[1]"));
        ProcessFilesGPT.main(new String[]{"-gpt", gptXML,"-output-dir","/tmp",testProduct});
    }

    @Test
    public void testSimpleGraphSTACInput() throws URISyntaxException {
        String gptXML = getAbsolutePath("simple_test.xml");
        String jsonConfig = getAbsolutePath("minimal_input.json");
        SparkContext.getOrCreate(new SparkConf(true).setAppName(TestProcessFilesGPT.class.getName()).setMaster("local[1]"));
        ProcessFilesGPT.main(new String[]{"-gpt", gptXML,"-output-dir","/tmp","-stac-input",jsonConfig});
        assertTrue(Files.exists(Paths.get("/tmp","S1A_IW_GRDH_SIGMA0_DV_20180930T054051_ASCENDING_59_3299_V001.tif")));
    }

    @Test
    public void testSimpleGraphOtherOutputformat() throws URISyntaxException {
        String gptXML = getAbsolutePath("simple_test.xml");
        SparkContext.getOrCreate(new SparkConf(true).setAppName(TestProcessFilesGPT.class.getName()).setMaster("local[1]"));
        ProcessFilesGPT.main(new String[]{"-gpt", gptXML,"-output-dir","/tmp","-format","BEAM-DIMAP",testProduct});
    }

    @Test
    public void testErrorHandling() throws URISyntaxException, IOException {
        String gptXML = getAbsolutePath("simple_test.xml");
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
        String gptXML = getAbsolutePath("simple_test.xml");
        SparkContext.getOrCreate(new SparkConf(true).setAppName(TestProcessFilesGPT.class.getName()).setMaster("local[1]"));
        ProcessFilesGPT.main(new String[]{"-noTempFile","-gpt", gptXML,"-output-dir","/tmp",testProduct});
    }

    @Test
    public void testPostProcessing() throws URISyntaxException, IOException {
        String gptXML = getAbsolutePath("simple_test.xml");
        String postProcessor = getAbsolutePath("postprocess.py");
        SparkContext.getOrCreate(new SparkConf(true).setAppName(TestProcessFilesGPT.class.getName()).setMaster("local[1]"));
        ProcessFilesGPT.main(new String[]{"-postprocess",postProcessor,"-noTempFile","-gpt", gptXML,"-output-dir","/tmp",testProduct});
    }

    private String getAbsolutePath(String classPathFile) throws URISyntaxException {
        return Paths.get(Thread.currentThread().getContextClassLoader().getResource(classPathFile).toURI()).toAbsolutePath().toString();
    }

    @Test
    public void testConfigFileParsing() throws URISyntaxException {
        List<STACProduct> result = ProcessFilesGPT.parseSTACInputFile(getAbsolutePath("input_config.json"));
        STACProduct product = result.get(0);
        assertNotNull(product);
        assertNotNull(product.id);
        STACProduct source_grd = product.inputs.get("source_GRD");
        assertNotNull(source_grd);
        assertNotNull(source_grd.assets.get("vito_filename"));

    }

}
