
gatk-tools-java [![Build Status](https://img.shields.io/travis/googlegenomics/gatk-tools-java.svg?style=flat)](https://travis-ci.org/googlegenomics/gatk-tools-java) [![Coverage Status](https://img.shields.io/coveralls/googlegenomics/gatk-tools-java.svg?style=flat)](https://coveralls.io/r/googlegenomics/gatk-tools-java)
===============
Tools for using Picard and GATK with Genomics API.

- Common classes for getting Reads from GA4GH Genomics API and
exposing them as SAMRecord "Iterable" resource.


- Implementation of a custom reader that can be plugged into [Picard](http://broadinstitute.github.io/picard/) tools
to handle reading of the input data specified via a url and coming from GA4GH API.
Works both with REST and GRPC (faster) implementation of [Google Genomics API](https://cloud.google.com/genomics/v1beta2/reference/).


- A set of [shell scripts](https://github.com/googlegenomics/gatk-tools-java/tree/master/src/main/scripts) that demonstrate how to run Picard
tools with Ga4GH custom reader.


- Requires [HTSJDK](https://github.com/samtools/htsjdk) v.1.128 and [Picard](https://github.com/broadinstitute/picard) v.1.133 and later.

To build this package: ```mvn compile package```
    
This command produces 3 files:
##### gatk-tools-java-[ver]-SNAPSHOT.jar

A small JAR with just the classes from this package, needs to be run with mvn run,
see [example.sh](https://github.com/googlegenomics/gatk-tools-java/blob/master/src/main/scripts/example.sh) script that demonstrates how to use classes in this package to get SAMRecords from GA4GH API.
You can run the example like this:

```gatk-tools-java$ src/main/scripts/example.sh```

##### gatk-tools-java-[ver]-SNAPSHOT-jar-with-dependencies.jar
A large jar with ALL dependencies in it.
This file is suitable for injecting a custom reader into a regularly built Picard
distribution, without recompiling it.

You will need to download and build Picard tools, see insrtuctions [here](http://broadinstitute.github.io/picard/).

See [view_sam_file.sh](https://github.com/googlegenomics/gatk-tools-java/blob/master/src/main/scripts/view_sam_file.sh) and [run_picard.sh](https://github.com/googlegenomics/gatk-tools-java/blob/master/src/main/scripts/run_picard.sh) scripts for examples of usage.
You can run the example like this:

```gatk-tools-java$ src/main/scripts/view_sam_file.sh```


##### gatk-tools-java-[ver]-SNAPSHOT-minimized.jar
JAR with dependencies suitable for compiling together with Picard tools.
See [instructions on building Picard with gatk-tools-java](https://github.com/broadinstitute/picard/blob/master/README.md).

With Picard tools built this way you can specify GA4GH urls as INPUT parameters
and do not have to use -Dsamjdk.custom_reader.

You should be able to run Picard tool like so:

```
picard$ java -jar dist/picard.jar ViewSam \
INPUT=https://www.googleapis.com/genomics/v1beta2/readgroupsets/CK256frpGBD44IWHwLP22R4/ \
GA4GH_CLIENT_SECRETS=../client_secrets.json
```
