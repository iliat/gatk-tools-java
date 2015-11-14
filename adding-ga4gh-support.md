# Adding GA4GH support to HTSJDK based tools: Picard and others.

This library provides GA4GH based implementation of HTSJDK common interfaces for getting reads data, such as SamReader.
Any code that uses SamReader can now plug in GA4GH implementation and acquire ability to get data from the cloud as opposed to just files.

This document describes:
* HTSJDK support for custom reader factories
* How to use this support to inject GA4GH API support without modifying your tool code
* Modifications we made to Picard common code to make it easy to add GA4GH API support to a Picard tool.

After reading this you should be able to help us convert more Picard tools and also add GA4GH support to any other tool based on HTSJDK SamReader/SamReaderFactory intefraces.

## HTSJDK support for custom reader factories

The main interface for getting Reads data from any data source is [SamReader](https://github.com/samtools/htsjdk/blob/master/src/java/htsjdk/samtools/SamReader.java).
SamReaders are created by [SamReaderFactory](https://github.com/samtools/htsjdk/blob/master/src/java/htsjdk/samtools/SamReaderFactory.java) class, specifically in *open* function that takes a [SamInputResource](https://github.com/samtools/htsjdk/blob/master/src/java/htsjdk/samtools/SamInputResource.java) (a wrapper around input description) and returns a SamReader.

We have added [support for injecting custom factories](https://github.com/samtools/htsjdk/blob/master/src/java/htsjdk/samtools/CustomReaderFactory.java) that are able to produce SamReaders for input sources not natively supported in HTSJDK.

If a custom reader factory is injected it has a first crack at examining the input resource and if it decides it can handle the input it creates a SamReader and returns it.
For GA4GH support the custom factory for the SamReader that wraps the API access is packaged in the [gatk-tools-java](https://github.com/googlegenomics/gatk-tools-java) library (see [GA4GHReaderFactory](https://github.com/googlegenomics/gatk-tools-java/blob/master/src/main/java/com/google/cloud/genomics/gatk/htsjdk/GA4GHReaderFactory.java) and [GA4GHSamReader](https://github.com/googlegenomics/gatk-tools-java/blob/master/src/main/java/com/google/cloud/genomics/gatk/htsjdk/GA4GHSamReader.java) classes).

A custom factory is injected via a custom_reader property by specifying:
* A URL prefix denoting inputs this factory can handle 
(e.g. https://www.googleapis.com/genomics)
* A class name for the custom factory
* An optional path to a JAR file with custom reader factory implementation (can skip if the class in in the main JAR of your app)

Combining all of this together, here's what you need to add to a command line of a tool that uses HTSJDK to make it read from GA4GH urls:

```
-Dsamjdk.custom_reader=https://www.googleapis.com/genomics,\
com.google.cloud.genomics.gatk.htsjdk.GA4GHReaderFactory,\
gatk-tools-java-1.1-SNAPSHOT-jar-with-dependencies.jar
```

### Additional parameters to make GA4GH APIs work
To make the authentication in GA4GH Apis work we need to pass a path to client_secrets.json file to the library.
This is done via another property: 

```
-Dga4gh.client_secrets=<path to client_secrets.json file>
```

### GRPC support 
To support a faster API implementation via gRPC (as opposed to REST) you need to specify
```
-Dga4gh.using_grpc=true
```

You also need to add an ALPN jar to the class path like so:
```
-Xbootclasspath/p:../gatk-tools-java/lib/alpn-boot-8.1.3.v20150130.jar
```

or for Java 7 use 
```
-Xbootclasspath/p:../gatk-tools-java/lib/alpn-boot-7.1.3.v20150130.jar
```

This implementation is ~10 times faster.

### URL format

The INPUT urls are of the form **https://<GA4GH provider>/readgroupsets/<readgroupset id>[/sequence][/start-end]**.

E.g.
```
https://www.googleapis.com/genomics/v1beta2/readgroupsets/CK256frpGBD44IWHwLP22R4/

OR 

https://www.googleapis.com/genomics/v1beta2/readgroupsets/CMvnhpKTFhD3he72j4KZuyc/chr17/41196311-42677499
```

### Input validation
The default input validation code in [IOUtil](https://github.com/samtools/htsjdk/blob/master/src/java/htsjdk/samtools/util/IOUtil.java) assumes it always works with files but we have modified it to skip readability validation for urls.

## Putting it all together in a command line
Lets put all of this together: if you have a tool that uses HTSJDK and reads the data from a resource specified by INPUT parameter, here's what you need to do on the command line to make it work with GA4GH

```
java -jar \
-Dsamjdk.custom_reader=https://www.googleapis.com/genomics,\
com.google.cloud.genomics.gatk.htsjdk.GA4GHReaderFactory,\
<path to gatk-tools-java.jar> \
-Dga4gh.client_secrets=<path to client_secrets.json file> \
INPUT=https://www.googleapis.com/genomics/v1beta2/readgroupsets/CMvnhpKTFhD3he72j4KZuyc/chr17/41196311-42677499
```
or for using gRPC

```
java -jar \
-Xbootclasspath/p:<path to alpn-boot-8.1.3.v20150130.jar>
-Dsamjdk.custom_reader=https://www.googleapis.com/genomics,\
com.google.cloud.genomics.gatk.htsjdk.GA4GHReaderFactory,\
<path to gatk-tools-java.jar> \
-Dga4gh.client_secrets=<path to client_secrets.json file> \
-Dga4gh.using_grpc=true \
INPUT=https://www.googleapis.com/genomics/v1beta2/readgroupsets/CMvnhpKTFhD3he72j4KZuyc/chr17/41196311-42677499
```

## Picard support
This command line injection above is cumbersome and you need to drag the gatk-tools-java.jar around, so for Picard we made extra effort
to include the support directly in Picard JAR and add factory injection code directly to Picard in the [CommandLineProgram](https://github.com/broadinstitute/picard/blob/master/src/java/picard/cmdline/CommandLineProgram.java) class.

You need to create a special build of Picard to have gatk-tools-java support included, see [instructions on building Picard with gatk-tools-java](https://github.com/broadinstitute/picard/blob/master/README.md).

With this support all you have to do on the command line is specify the url and ```-Dga4gh.client_secrets parameters```, no need to use samjdk.custom_reader property.

### Per tool changes to add GA4GH support
In addition to generic code changes there are small changes that needs to be made on a per-tool basis.

Most of the tools specify INPUT parameter as type File and do validation and Reader factory initialization assuming they are working with a file. 

We need to make the following changes:
* Change the parameter type from File to String (to allow for urls)
* Change calls to  ```IOUtil.assertFileIsReadable(INPUT)``` to ```IOUtil.assertInputIsValid(INPUT)``` that is able to handle both urls and files.
* Change calls to ```SamReaderFactory.open(INPUT)``` to ```SamReaderFactory.open(SamInputResource.of(INPUT))``` that is able both urls and file.

For example see [this pull request]( https://github.com/broadinstitute/picard/pull/147/files?diff=split)

We have changes to ViewSamFile, MarkDuplicates and some other tools.

Please help us convert more tools in a similar manner.


