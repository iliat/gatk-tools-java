#!/bin/bash
# Runs Picard tool specified on the command line, using GA4GH custom reader
# for getting the data from url based INPUTs.
# E.g. run_picard.sh ViewSam INPUT=<url>.
# Assumes directory structure where gatk-tools-java and picard repos reside
# in the same folder and client_secrets is in the same folder:
# .../...
#          /gatk-tools-java
#          /picard
#          /client_secrets.json
# If your setup is different, please modify paths below.
GATK_TOOLS_JAVA_JAR=$(readlink -f `dirname $0`/../../../target/gatk-tools-java-*-SNAPSHOT-jar-with-dependencies.jar)
CLIENT_SECRETS=$(readlink -f `dirname $0`/../../../../client_secrets.json)
PICARD_JAR=$(readlink -f `dirname $0`/../../../../picard/build/libs/picard*all.jar)
ALPN_JAR=$(readlink -f `dirname $0`/../../../lib/alpn-boot-8.1.9.v20160720.jar)
echo Running Picard from $PICARD_JAR
echo Using gatk-tools-java from $GATK_TOOLS_JAVA_JAR
echo Using client_secrets from $CLIENT_SECRETS
echo Using ALPN from $ALPN_JAR

java -jar \
-Xbootclasspath/p:$ALPN_JAR:$GATK_TOOLS_JAVA_JAR \
-Dsamjdk.custom_reader=https://genomics.googleapis.com,\
com.google.cloud.genomics.gatk.htsjdk.GA4GHReaderFactory,\
$GATK_TOOLS_JAVA_JAR \
-Dga4gh.client_secrets=$CLIENT_SECRETS \
-Dga4gh.using_grpc=true \
$PICARD_JAR \
"$@" \
VERBOSITY=DEBUG QUIET=false