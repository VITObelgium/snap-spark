#!/bin/bash
###
# Runs a Sentinel Toolbox workflow on Spark
###
spark-submit --master yarn \
--deploy-mode cluster \
--executor-memory=24G \
--conf spark.shuffle.service.enabled=true --conf spark.dynamicAllocation.enabled=true \
--jars hdfs:///workflows-dev/snap/snap-all-6.0.2.jar \
--packages com.beust:jcommander:1.72 \
--files /data/projects/Terrascope/snap/S1_GRD_Processing_toSigma0.xml \
--archives hdfs:///workflows-dev/snap/etc.zip#etc \
--class be.vito.terrascope.snapgpt.ProcessFilesGPT \
--conf spark.executor.extraJavaOptions=-Dsnap.userdir=. \
hdfs:///workflows-dev/snap/snap-gpt-spark-1.0-SNAPSHOT.jar \
-gpt S1_GRD_Processing_toSigma0.xml \
-output-dir /data/users/Private/driesj/terrascope/S1/ \
/data/MTDA/CGS_S1/CGS_S1_GRD_L1/IW/HR/DV/2018/06/20/S1A_IW_GRDH_1SDV_20180620T054148_20180620T054213_022436_026E08_6AAB/S1A_IW_GRDH_1SDV_20180620T054148_20180620T054213_022436_026E08_6AAB.zip