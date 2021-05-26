def deployable_branches = ["master"]

node ('jenkinsslave3.vgt.vito.be') {
    stage('Build and Test') {
        sh "rm -rf *"
        sh "rm -rf .git/"
        checkout scm
        rel_version = getMavenVersion()
        build()
    }

    if(["master","develop"].contains(env.BRANCH_NAME)) {
        stage('Deploy to Dev') {
            //milestone ensures that previous builds that have reached this point are aborted
            milestone()
            deploy("/workflows-dev/snap/",rel_version)
        }


    }
}




if(deployable_branches.contains(env.BRANCH_NAME)){
    stage('Input'){
        milestone()
        input "Release build ${rel_version}?"
        milestone()
    }

    node('jenkinsslave3.vgt.vito.be'){
        stage('Releasing'){
            rel_version = getReleaseVersion()
            withMavenEnv(["JAVA_OPTS=-Xmx1536m -Xms512m","HADOOP_CONF_DIR=/etc/hadoop/conf/"]){
                sh "mvn versions:use-releases -DgenerateBackupPoms=false -DfailIfNotReplaced=true"
                echo "Removing SNAPSHOT from version for release"
                sh "mvn versions:set -DgenerateBackupPoms=false -DnewVersion=${rel_version}"
            }
            echo "releasing version ${rel_version}"
            build(tests = false)

            deploy("/workflows/snap/",rel_version)

            withMavenEnv(["JAVA_OPTS=-Xmx1536m -Xms512m","HADOOP_CONF_DIR=/etc/hadoop/conf/"]){
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'BobDeBouwer', usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD']]) {
                    sh "git commit -a -m 'Set version v${rel_version} in pom for release'"
                    sh "git tag -a v${rel_version} -m 'version ${rel_version}'"
                    sh "git checkout ${env.BRANCH_NAME}"
                    new_version = updateMavenVersion()
                    sh "mvn versions:set -DgenerateBackupPoms=false -DnewVersion=${new_version}"
                    sh "git commit -a -m 'Raise version in pom to ${new_version}'"
                    sh "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@git.vito.be/scm/biggeo/snap-spark.git ${env.BRANCH_NAME}"
                    sh "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@git.vito.be/scm/biggeo/snap-spark.git v${rel_version}"
                }
            }



        }
    }
}

void deploy(hdfs_dir="/workflows/snap/",rel_version){
    sh "hdfs dfs -copyFromLocal -f snap-bundle/target/snap-bundle/snap-all-*.jar "+hdfs_dir
    sh "hdfs dfs -copyFromLocal -f snap-gpt-spark/target/snap-gpt-spark-${rel_version}.jar " + hdfs_dir
    dir("snap-gpt-spark/etc") {
        sh "zip etc.zip *"
        sh "hdfs dfs -copyFromLocal -f etc.zip " + hdfs_dir
    }
    dir("snap-gpt-spark/auxdata") {
        sh "mkdir -p dem/egm96"
        sh "curl -o dem/egm96/ww15mgh_b.zip http://step.esa.int/auxdata/dem/egm96/ww15mgh_b.zip"
        sh "mkdir -p watermask"
        sh "curl -o watermask/50m.zip http://step.esa.int/auxdata/watermask/images/50m.zip"
        sh "curl -o watermask/150m.zip http://step.esa.int/auxdata/watermask/images/150m.zip"
        sh "curl -o watermask/GC_water_mask.zip http://step.esa.int/auxdata/watermask/images/GC_water_mask.zip"
        sh "curl -o watermask/MODIS_north_water_mask.zip http://step.esa.int/auxdata/watermask/images/MODIS_north_water_mask.zip"
        sh "curl -o watermask/MODIS_south_water_mask.zip http://step.esa.int/auxdata/watermask/images/MODIS_south_water_mask.zip"
        sh "mkdir -p openjpeg/8.0.2"
        sh "curl -o openjpeg/8.0.2/libopenjp2.so https://raw.githubusercontent.com/senbox-org/snap-engine/8.0.2/lib-openjpeg/src/main/resources/auxdata/openjpeg/openjpeg-2.1.0-linux64/bin/libopenjp2.so"
        sh "curl -o openjpeg/8.0.2/opj_compress https://raw.githubusercontent.com/senbox-org/snap-engine/8.0.2/lib-openjpeg/src/main/resources/auxdata/openjpeg/openjpeg-2.1.0-linux64/bin/opj_compress"
        sh "curl -o openjpeg/8.0.2/opj_decompress https://raw.githubusercontent.com/senbox-org/snap-engine/8.0.2/lib-openjpeg/src/main/resources/auxdata/openjpeg/openjpeg-2.1.0-linux64/bin/opj_decompress"
        sh "curl -o openjpeg/8.0.2/opj_dump https://raw.githubusercontent.com/senbox-org/snap-engine/8.0.2/lib-openjpeg/src/main/resources/auxdata/openjpeg/openjpeg-2.1.0-linux64/bin/opj_dump"

        sh "mkdir -p auxdata/gdal/gdal-3-0-X-jni"
        sh "curl -o auxdata/gdal/libenvironment-variables.so https://raw.githubusercontent.com/senbox-org/s2tbx/8.0.2/lib-gdal/src/main/resources/auxdata/gdal/Linux/x64/libenvironment-variables.so"
        sh "curl -o auxdata/gdal/gdal-3-0-X-jni/gdaljni.zip https://raw.githubusercontent.com/senbox-org/s2tbx/8.0.2/lib-gdal/src/main/resources/auxdata/gdal/Linux/x64/gdal-3-0-X-jni.zip"
        sh "cd auxdata/gdal/gdal-3-0-X-jni && unzip gdaljni.zip && rm gdaljni.zip"

        sh "mkdir -p auxdata/gdal/gdal-2-3-X-jni"
        sh "curl -o auxdata/gdal/gdal-2-3-X-jni/gdaljni.zip https://raw.githubusercontent.com/senbox-org/s2tbx/8.0.2/lib-gdal/src/main/resources/auxdata/gdal/Linux/x64/gdal-2-3-X-jni.zip"
        sh "cd auxdata/gdal/gdal-2-3-X-jni && unzip gdaljni.zip && rm gdaljni.zip"

        sh "zip -r auxdata.zip *"
        sh "hdfs dfs -copyFromLocal -f auxdata.zip ${hdfs_dir}"
    }
}

String getMavenVersion() {
    pom = readMavenPom file: 'pom.xml'
    version = pom.version
    if (version == null){
        version = pom.parent.version
    }
    return version
}

String getReleaseVersion() {
    pom = readMavenPom file: 'pom.xml'
    version = pom.version
    if (version == null){
        version = pom.parent.version
    }
    v = version.tokenize('.-') //List['1','0','0','SNAPSHOT']
    v_releasable = v[0] + '.' + v[1] + '.' + v[2] // 1.0.0
    pom.version = v_releasable
    return v_releasable
}

String updateMavenVersion(){
    pom = readMavenPom file: 'pom.xml'
    version = pom.version //1.0-SNAPSHOT
    v = version.tokenize('.-') //List['1','0'] Snapshot has already been removed by getMavenVersion but needs to be readded
    v_major = v[0].toInteger() // 1
    v_minor = v[1].toInteger() // 0
    v_hotfix = v[2].toInteger()

    v_hotfix += 1
    v = (v_major + '.' + v_minor + '.' + v_hotfix).toString()
    v_snapshot = (v_major + '.' + v_minor + '.' + v_hotfix + '-SNAPSHOT').toString()

    return v_snapshot
}

void build(tests = true){
    def publishable_branches = ["master","develop"]
    String jdktool = tool name: "OpenJDK 8 Centos7", type: 'hudson.model.JDK'
    List jdkEnv = ["PATH+JDK=${jdktool}/bin", "JAVA_HOME=${jdktool}","HADOOP_CONF_DIR=/etc/hadoop/conf/"]
    withEnv(jdkEnv) {
        sh 'echo $JAVA_HOME'
        def server = Artifactory.server('vitoartifactory')
        def rtMaven = Artifactory.newMavenBuild()
        rtMaven.deployer server: server, releaseRepo: 'libs-release-public', snapshotRepo: 'libs-snapshot-public'
        rtMaven.tool = 'Maven 3.5.0'
        def goals = '-P default clean install surefire:test@forked-jvm'
        if (!tests) {
            goals += ' -DskipTests'
        }
        rtMaven.deployer.deployArtifacts = publishable_branches.contains(env.BRANCH_NAME)
        //use '--projects StatisticsMapReduce' in 'goals' to build specific module
        try {
            buildInfo = rtMaven.run pom: 'pom.xml', goals: goals
            try{
                if(rtMaven.deployer.deployArtifacts )
                    server.publishBuildInfo buildInfo
            }catch(e){
                print e.message
            }
        }catch(err){
            mail body: "project build error is here: ${env.BUILD_URL}" ,
                    from: 'Jenkins@vgt.vito.be',
                    replyTo: 'no-reply@vgt.vito.be',
                    subject: 'Snap-spark build failed',
                    to: 'jeroen.dries@vito.be,dirk.daems@vito.be'
            throw err
        }
        finally {
            if (tests) {
                junit '**/target/surefire-reports/*.xml'
            }
        }
    }
}

void withMavenEnv(List envVars = [], def body) {
    String mvntool = tool name: "Maven 3.5.0", type: 'hudson.tasks.Maven$MavenInstallation'
    String jdktool = tool name: "OpenJDK 8 Centos7", type: 'hudson.model.JDK'

    List mvnEnv = ["PATH+MVN=${mvntool}/bin", "PATH+JDK=${jdktool}/bin", "JAVA_HOME=${jdktool}", "MAVEN_HOME=${mvntool}"]

    mvnEnv.addAll(envVars)
    withEnv(mvnEnv) {
        body.call()
    }
}
