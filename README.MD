# Sentinel Toolbox on Spark

The aim of this project is to easily run a SNAP workflow, saved to XML, on a 
number of input files.

It is already deployed and tested on the PROBA-V MEP distributed processing cluster.


## Quickstart

The project consists of two main modules:

* snap-bundle: combines Sentinel Toolboxes and their dependencies into a single jar
* snap-gpt-spark: An Apache Spark application that applies a Snap XML workflow to multiple input files in parallel.

Build the jars with Maven:

`mvn package`

Create a zip with snap config files:
`cd snap-gpt-spark/etc`
`zip etc.zip *`

Now we can submit our application on a Spark cluster using Spark-submit.

```
spark-submit --master yarn \
--name Snap-GPT \
--conf spark.yarn.executor.memoryOverhead=2048 \
--deploy-mode cluster \
--executor-memory=12G \
--conf spark.memory.fraction=0.01 \
--jars snap-bundle/target/snap-bundle/snap-all-6.0.2.jar \
--packages com.beust:jcommander:1.72 \
--files my_workflow.xml \
--archives hdfs:///workflows-dev/snap/etc.zip#etc \
--class be.vito.terrascope.snapgpt.ProcessFilesGPT \" \
snap-gpt-spark/target/snap-gpt-spark-1.0-SNAPSHOT.jar \
-format GeoTIFF-BigTIFF \
-gpt my_workflow.xml \
-output-dir /snap/output \
/data/MTDA/CGS_S1/CGS_S1_GRD_L1/IW/HR/DV/2018/06/20/S1A_IW_GRDH_1SDV_20180620T054148_20180620T054213_022436_026E08_6AAB/S1A_IW_GRDH_1SDV_20180620T054148_20180620T054213_022436_026E08_6AAB.zip

```

## Configuring SNAP
SNAP configuration parameters can be configured in snap-gpt-spark/etc, or by 
passing them on to the Spark executors, for example:

`--conf spark.executor.extraJavaOptions="-Dsnap.dataio.bigtiff.tiling.height=256"`