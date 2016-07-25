#!/bin/bash

if [ "$1" = "grpc" ]
then
# Use this for Java 7
# MAVEN_OPTS="-Xbootclasspath/p:./lib/alpn-boot-7.1.3.v20150130.jar" \
# Use this for Java 8
MAVEN_OPTS="-Xbootclasspath/p:./lib/alpn-boot-8.1.9.v20160720.jar" \
mvn exec:java \
 -Dexec.mainClass=com.google.cloud.genomics.gatk.htsjdk.SamReaderExample \
 -Dsamjdk.custom_reader=https://genomics.googleapis.com,com.google.cloud.genomics.gatk.htsjdk.GA4GHReaderFactory \
 -Dga4gh.client_secrets=../client_secrets.json \
 -Dga4gh.using_grpc=true
else
mvn exec:java \
 -Dexec.mainClass=com.google.cloud.genomics.gatk.htsjdk.SamReaderExample \
 -Dsamjdk.custom_reader=https://genomics.googleapis.com,com.google.cloud.genomics.gatk.htsjdk.GA4GHReaderFactory \
 -Dga4gh.client_secrets=../client_secrets.json \
 -Dga4gh.using_grpc=false
fi

 


