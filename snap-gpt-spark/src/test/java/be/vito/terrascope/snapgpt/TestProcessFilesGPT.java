package be.vito.terrascope.snapgpt;

import com.google.gson.Gson;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;

public class TestProcessFilesGPT {

    static{
        Properties properties = System.getProperties();
        properties.setProperty("snap.dataio.bigtiff.tiling.height", "256");
        properties.setProperty("snap.dataio.bigtiff.tiling.width", "256");
        properties.setProperty("snap.jai.defaultTileSize", "256");
        properties.setProperty("snap.jai.tileCacheSize", "1024");
        properties.setProperty("snap.gpf.useFileTileCache", "false");
        properties.setProperty("snap.parallelism", "2");


    }

    private String testProduct = "/data/MTDA/CGS_S2/CGS_S2_FAPAR/2018/06/18/S2A_20180618T101021Z_32TPP_CGS_V102_000/S2A_20180618T101021Z_32TPP_FAPAR_V102/10M/S2A_20180618T101021Z_32TPP_FAPAR_10M_V102.tif";

    @Test
    public void testSimpleGraph() throws URISyntaxException {
        String gptXML = getAbsolutePath("simple_test.xml");
        setupSpark();
        ProcessFilesGPT.main(new String[]{"-gpt", gptXML,"-output-dir","/tmp",testProduct});
    }

    @Test
    public void testSimpleGraphSTACInput() throws URISyntaxException, IOException {
        Path tempDirWithPrefix = Files.createTempDirectory("snapsparktest");
        String gptXML = getAbsolutePath("simple_test.xml");
        String jsonConfig = getAbsolutePath("minimal_input.json");
        setupSpark();
        ProcessFilesGPT.main(new String[]{"-gpt", gptXML,"-output-dir",tempDirWithPrefix.toString(),"-stac-input",jsonConfig});
        assertTrue(Files.exists(tempDirWithPrefix.resolve("S1A_IW_GRDH_SIGMA0_DV_20180930T054051_ASCENDING_59_3299_V001.tif")));
    }

    @Test
    public void testSimpleGraphMultipleInputs() throws URISyntaxException, IOException {
        Path tempDirWithPrefix = Files.createTempDirectory("snapsparktest");
        String gptXML = getAbsolutePath("multiple_input_test.xml");
        String jsonConfig = getAbsolutePath("multiple_inputs.json");
        setupSpark();
        ProcessFilesGPT.main(new String[]{"-gpt", gptXML,"-output-dir",tempDirWithPrefix.toString(),"-stac-input",jsonConfig});
        assertTrue(Files.exists(tempDirWithPrefix.resolve("S1A_IW_GRDH_SIGMA0_DV_20180930T054051_ASCENDING_59_3299_V001.dim")));
    }

    @Test
    public void testSimpleGraphOtherOutputformat() throws URISyntaxException, IOException {
        Path tempDirWithPrefix = Files.createTempDirectory("snapsparktest");
        String gptXML = getAbsolutePath("simple_test.xml");
        setupSpark();
        ProcessFilesGPT.main(new String[]{"-gpt", gptXML,"-output-dir",tempDirWithPrefix.toString(),"-format","BEAM-DIMAP",testProduct});
        assertTrue(tempDirWithPrefix.resolve("S2A_20180618T101021Z_32TPP_FAPAR_10M_V102.tif.data").toFile().exists());
    }

    @Test
    public void testErrorHandling() throws URISyntaxException, IOException {
        String gptXML = getAbsolutePath("simple_test.xml");
        setupSpark();
        try {

            ProcessFilesGPT.main(new String[]{"-gpt", gptXML, "-output-dir", "/tmp", "-format", "BEAM-DIMAP", "doesntExist.tif"});
            Assert.fail();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            Path path = Paths.get("/tmp/doesntExist.tif.FAILED");
            Assert.assertTrue(Files.exists(path));
            Files.delete(path);
            path = Paths.get("/tmp/doesntExist.tif.FAILED.0");
            Assert.assertTrue(Files.exists(path));
            Files.delete(path);
        }
    }

    private static SparkContext setupSpark() {
        return SparkContext.getOrCreate(new SparkConf(true).setAppName(TestProcessFilesGPT.class.getName()).setMaster("local[1,5]"));
    }

    @Ignore
    @Test
    public void testIdepix() throws URISyntaxException {
        Properties props = System.getProperties();
        props.setProperty("snap.dataio.gdal.creationoptions", "COMPRESS=DEFLATE;TILED=TRUE");
        props.setProperty("snap.jai.defaultTileSize", "256");
        props.setProperty("snap.jai.tileCacheSize", "1024");

        String gptXML = getAbsolutePath("idepix.xml");
        setupSpark();
        ProcessFilesGPT.main(new String[]{"-format", "GDAL-GTiff-WRITER","-gpt", gptXML,"-output-dir","/tmp","/data/MTDA/CGS_S2/CGS_S2_L1C/2020/08/07/S2B_MSIL1C_20200807T084559_N0209_R107_T33MZS_20200807T110241/S2B_MSIL1C_20200807T084559_N0209_R107_T33MZS_20200807T110241.SAFE/MTD_MSIL1C.xml"});
    }

    @Test
    public void testSimpleGraphNoTempFile() throws URISyntaxException {
        String gptXML = getAbsolutePath("simple_test.xml");
        setupSpark();
        ProcessFilesGPT.main(new String[]{"-noTempFile","-gpt", gptXML,"-output-dir","/tmp",testProduct});
    }

    @Test
    public void testPostProcessing() throws URISyntaxException, IOException {
        String gptXML = getAbsolutePath("simple_test.xml");
        String postProcessor = getAbsolutePath("triggerPostProcessing.sh");
        setupSpark();
        ProcessFilesGPT.main(new String[]{"-postprocess",postProcessor,"-noTempFile","-gpt", gptXML,"-output-dir","/tmp",testProduct});
    }

    @Test
    public void testPostProcessingNoSpark() throws URISyntaxException, IOException, InterruptedException {
        String postProcessor = getAbsolutePath("triggerPostProcessing.sh");
        ProcessFilesGPT.doPostProcess(postProcessor, Paths.get(postProcessor),null);
    }

    private String getAbsolutePath(String classPathFile) throws URISyntaxException {
        return Paths.get(Thread.currentThread().getContextClassLoader().getResource(classPathFile).toURI()).toAbsolutePath().toString();
    }

    @Test
    public void testConfigFileParsing() throws URISyntaxException {
        List<STACProduct> result = ProcessFilesGPT.parseSTACInputFile(getAbsolutePath("input_config.json"));
        STACProduct product = result.get(0);
        assertDecodedProduct(product);

        assertEquals("MultiPolygon",((Map<String,Object>)product.geometry).get("type"));

    }

    private void assertDecodedProduct(STACProduct product) {
        assertNotNull(product);
        assertNotNull(product.id);
        assertNotNull(product.geometry);

        STACProduct source_grd = product.inputs.get("source_GRD");
        assertNotNull(source_grd);
        assertNotNull(source_grd.assets.get("vito_filename"));

        Gson gson = new Gson();
        String jsonString = gson.toJson(product);
        System.out.println("jsonString = " + jsonString);
    }

    @Test
    public void testConfigFileParsingWKT() throws URISyntaxException {
        List<STACProduct> result = ProcessFilesGPT.parseSTACInputFile(getAbsolutePath("input_config_wkt.json"));
        STACProduct product = result.get(0);
        assertDecodedProduct(product);
        assertTrue(product.geometry instanceof String);

    }

}
