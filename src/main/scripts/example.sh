#!/bin/bash

if [ "$1" = "grpc" ]
then
MAVEN_OPTS="-Xbootclasspath/p:./lib/alpn-boot-7.1.3.v20150130.jar" \
mvn exec:java \
 -Dexec.mainClass=com.google.cloud.genomics.gatk.htsjdk.SamReaderExample \
 -Dsamjdk.custom_reader=https://www.googleapis.com/genomics,com.google.cloud.genomics.gatk.htsjdk.GA4GHReaderFactory \
 -Dga4gh.client_secrets=../client_secrets.json \
 -Dga4gh.using_grpc=true
else
mvn exec:java \
 -Dexec.mainClass=com.google.cloud.genomics.gatk.htsjdk.SamReaderExample \
 -Dsamjdk.custom_reader=https://www.googleapis.com/genomics,com.google.cloud.genomics.gatk.htsjdk.GA4GHReaderFactory \
 -Dga4gh.client_secrets=../client_secrets.json \
 -Dga4gh.using_grpc=false
fi

 


