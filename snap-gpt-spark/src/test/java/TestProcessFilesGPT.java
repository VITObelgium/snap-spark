import be.vito.terrascope.snapgpt.ProcessFilesGPT;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.junit.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

public class TestProcessFilesGPT {

    @Test
    public void testSimpleGraph() throws URISyntaxException {
        String gptXML = Paths.get(Thread.currentThread().getContextClassLoader().getResource("simple_test.xml").toURI()).toAbsolutePath().toString();
        SparkContext.getOrCreate(new SparkConf(true).setAppName(TestProcessFilesGPT.class.getName()).setMaster("local[1]"));
        ProcessFilesGPT.main(new String[]{"-gpt", gptXML,"-output-dir","/tmp","/home/driesj/alldata/CGS_S2_FAPAR/2017/06/05/S2A_20170605T105303Z_31UES_FAPAR_V100/S2A_20170605T105303Z_31UES_FAPAR_10M_V100.tif"});

    }

    @Test
    public void testSimpleGraphNoTempFile() throws URISyntaxException {
        String gptXML = Paths.get(Thread.currentThread().getContextClassLoader().getResource("simple_test.xml").toURI()).toAbsolutePath().toString();
        SparkContext.getOrCreate(new SparkConf(true).setAppName(TestProcessFilesGPT.class.getName()).setMaster("local[1]"));
        ProcessFilesGPT.main(new String[]{"-noTempFile","-gpt", gptXML,"-output-dir","/tmp","/home/driesj/alldata/CGS_S2_FAPAR/2017/06/05/S2A_20170605T105303Z_31UES_FAPAR_V100/S2A_20170605T105303Z_31UES_FAPAR_10M_V100.tif"});

    }
}
