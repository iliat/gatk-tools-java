/*
Copyright 2014 Google Inc. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.google.cloud.genomics.gatk.common;

import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.extensions.java6.auth.oauth2.GooglePromptReceiver;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.genomics.Genomics;
import com.google.api.services.genomics.GenomicsScopes;
import com.google.api.services.genomics.model.Dataset;
import com.google.api.services.genomics.model.HeaderSection;
import com.google.api.services.genomics.model.Read;
import com.google.api.services.genomics.model.Readset;
import com.google.api.services.genomics.model.SearchReadsRequest;
import com.google.cloud.genomics.utils.GenomicsFactory;
import com.google.cloud.genomics.utils.Paginator;
import com.google.common.base.Suppliers;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Manages Genomics Api initialization and provides Read iterator based
 * resources via Reads.search API invocations. 
 */
public class GenomicsApiDataSource {
  private static final Logger LOG = Logger.getLogger(GenomicsApiDataSource.class.getName());
  private static List<String> SCOPES = Arrays.asList(GenomicsScopes.GENOMICS);
  
  private String clientSecretsFilename;
  private boolean noLocalServer;
  private String rootUrl;
  
  /** Genomics API stub */
  private Genomics api = null;
 
  public GenomicsApiDataSource(String rootUrl, 
      String clientSecretsFilename, 
      boolean noLocalServer) {
    super();
    this.clientSecretsFilename = clientSecretsFilename;
    this.noLocalServer = noLocalServer;
    this.rootUrl = rootUrl;
  }
  
  private Genomics getApi() throws GeneralSecurityException, IOException {
    if (api == null) {
      api = initGenomicsApi();
    }
    return api;
  }
  
  private Genomics initGenomicsApi() throws GeneralSecurityException, IOException {
    File clientSecrets = new File(clientSecretsFilename);
    if (!clientSecrets.exists()) {
      throw new IOException(
          "Client secrets file " + clientSecretsFilename + " does not exist."
          + " Visit https://developers.google.com/genomics to learn how"
          + " to install a client_secrets.json file.  If you have installed a client_secrets.json"
          + " in a specific location, use --client_secrets_filename <path>/client_secrets.json.");
    }
    
    VerificationCodeReceiver receiver = noLocalServer ? 
        new GooglePromptReceiver() : new LocalServerReceiver();

    GenomicsFactory genomicsFactory = GenomicsFactory
        .builder("genomics_java_client")
        .setScopes(SCOPES)
        .setUserName("user" + SCOPES.size())
        .setVerificationCodeReceiver(Suppliers.ofInstance(receiver))
        .setRootUrl(rootUrl)
        .setServicePath("/")
        .build();

    return genomicsFactory.fromClientSecretsFile(clientSecrets);
  }
  
  public ReadIteratorResource getReadsFromGenomicsApi(GA4GHUrl url) 
       throws IOException, GeneralSecurityException {
    return getReadsFromGenomicsApi(url.getReadset(), 
        url.getSequence(), url.getRangeStart(), url.getRangeEnd());
  }
  
  public ReadIteratorResource getReadsFromGenomicsApi(String readsetId, 
      String sequenceName, int sequenceStart, int sequenceEnd) 
          throws IOException, GeneralSecurityException {
    final Genomics stub = getApi();
    // TODO(iliat): implement API retries and using access key for public
    // datasets
    try {
      Readset readset = stub.readsets().get(readsetId).execute();
      String datasetId = readset.getDatasetId();
      LOG.info("Found readset " + readsetId + ", dataset " + datasetId);
      
      List<HeaderSection> headers = readset.getFileData();
      if (headers == null || headers.size() == 0) {
        throw new IOException("No headers found in the readset " + readsetId);
      }
      if (headers.size() > 1) {
        throw new IOException("Multiple (" + String.valueOf(headers.size()) + 
            ") headers found in the readset " + readsetId);
      }
      HeaderSection headerSection = headers.get(0);
      if (headerSection == null) {
        throw new IOException("Invalid header section in readset " + readsetId);
      }
      
      LOG.info("Searching for reads in sequence " + sequenceName + 
          String.valueOf(sequenceStart) + "-" + String.valueOf(sequenceEnd));
      Paginator.Reads searchReads = Paginator.Reads.create(stub);
      SearchReadsRequest readRequest = new SearchReadsRequest()
        .setReadsetIds(Arrays.asList(readsetId))
        .setSequenceName(sequenceName);
      if (sequenceStart != 0) {
        readRequest.setSequenceStart(BigInteger.valueOf(sequenceStart));
      }
      if (sequenceEnd != 0) {
        readRequest.setSequenceEnd(BigInteger.valueOf(sequenceEnd));
      }
      Iterable<Read> reads = searchReads.search(readRequest); 
      return new ReadIteratorResource(headerSection, reads);
    } catch (GoogleJsonResponseException ex) {
      LOG.warning("Genomics API call failure: " + ex.getMessage());
      if (ex.getDetails() == null) {
        throw ex;
      }
      throw new IOException(ex.getDetails().getMessage());
    }
  }
}
