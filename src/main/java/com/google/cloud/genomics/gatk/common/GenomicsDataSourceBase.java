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
import com.google.cloud.genomics.utils.GenomicsFactory;
import com.google.common.base.Suppliers;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.logging.Logger;

/**
 * Manages Genomics Api initialization and provides Read iterator based
 * resources via Reads.search API invocations. 
 */
public abstract class GenomicsDataSourceBase<Read, ReadGroupSet, Reference> 
  implements GenomicsDataSource<Read, ReadGroupSet, Reference>{
  protected static final Logger LOG = Logger.getLogger(GenomicsDataSourceBase.class.getName());
  
  protected String clientSecretsFilename;
  protected String apiKey;
  protected boolean noLocalServer;
  protected String rootUrl;
  
  /** Genomics Factory that wraps auth details. */
  protected GenomicsFactory factory;
 
  public GenomicsDataSourceBase(String rootUrl, 
      String clientSecretsFilename, 
      String apiKey,
      boolean noLocalServer) {
    super();
    this.clientSecretsFilename = clientSecretsFilename;
    this.apiKey = apiKey;
    this.noLocalServer = noLocalServer;
    this.rootUrl = rootUrl;
  }
  
  protected GenomicsFactory getFactory() {
    if (factory == null) {
      factory = initGenomicsFactory();
    }
    return factory;
  }
  
  protected GenomicsFactory initGenomicsFactory() {
    // Remove any path component from the root url - the code expects
    // e.g. https://genomics.googleapis.com
    URL url = null;
    try {
      url = new URL(rootUrl);
    } catch (MalformedURLException e) {
      // Will not set url
    }
    GenomicsFactory.Builder builder = GenomicsFactory
        .builder("genomics_java_client");
    if (url != null) {
      String rootUrlString = url.getProtocol() + "://" + url.getHost();
      LOG.info("Initializing genomics factory with root url " + rootUrlString);
      builder.setRootUrl(rootUrlString);
    }
    return builder.build();
  }
 
  static final String CLIENT_SECRETS_INSTRUCTIONS = 
      "\nVisit https://cloud.google.com/genomics/install-genomics-tools#authenticate to learn how" +
      " to install a client_secrets.json file.  If you have installed a client_secrets.json" +
      " in a specific location, use -Dga4gh.client_secrets=<path>/client_secrets.json." +
      "\nAn API key can be specified via -Dga4gh.api_key=<key>.\n";
   
  protected enum AUTH_REQUIREMENTS {
    NONE,
    API_OR_CLIENT_SECRETS,
    CLIENT_SECRETS_ONLY
  }
  /**
   * Checks that one of API key or client secrets is specified and valid.
   * Depending on the use case no auth. might be allowed but some use cases
   * require it and some only work with client_secrets (and not API key).
   */
  protected void checkParamsForAuth(AUTH_REQUIREMENTS required) throws IOException {
    if (!clientSecretsFilename.isEmpty()) {
      File clientSecrets = new File(clientSecretsFilename);
      if (!clientSecrets.exists()) {
        throw new IOException(
            "Client secrets file " + clientSecretsFilename + " does not exist." +
            CLIENT_SECRETS_INSTRUCTIONS);   
      }
    } else {
     if (required == AUTH_REQUIREMENTS.CLIENT_SECRETS_ONLY) {
       throw new IOException(
           "Client secrets file has to be specified (API key can not be used)." 
               + CLIENT_SECRETS_INSTRUCTIONS); 
     }
     if (apiKey.isEmpty() && required != AUTH_REQUIREMENTS.NONE) {
       throw new IOException(
           "Either an API key or a client secrets file have to be specified." 
               + CLIENT_SECRETS_INSTRUCTIONS);
     }
    }
  }
    
  @Override
  public ReadIteratorResource<Read, ReadGroupSet, Reference> getReads(GA4GHUrl url) 
      throws IOException, GeneralSecurityException {
    LOG.info("Getting reads from " + url);
    return getReads(url.getReadset(), 
        url.getSequence(), url.getRangeStart(), url.getRangeEnd());
  }
  
  /**
   * Gets unmapped mates so we can inject them besides their mapped pairs.
   * @throws GeneralSecurityException 
   * @throws IOException
   */
  protected UnmappedReads<Read> getUnmappedMatesOfMappedReads(String readsetId) 
      throws GeneralSecurityException, IOException {
    LOG.info("Collecting unmapped mates of mapped reads for injection");
    final Iterable<Read> unmappedReadsIterable = getUnmappedReadsIterator(readsetId); 
    final UnmappedReads<Read> unmappedReads = createUnmappedReads();
    for (Read read : unmappedReadsIterable) {
      unmappedReads.maybeAddRead(read);
    }
    LOG.info("Finished collecting unmapped mates of mapped reads: " + 
        unmappedReads.getReadCount() + " found.");
    return unmappedReads;
  }
    
  public abstract ReadIteratorResource<Read, ReadGroupSet, Reference> getReads(String readsetId, 
      String sequenceName, int sequenceStart, int sequenceEnd) throws IOException, GeneralSecurityException;
    
  protected abstract UnmappedReads<Read> createUnmappedReads();
  protected abstract Iterable<Read> getUnmappedReadsIterator(String readsetId) throws GeneralSecurityException, IOException;
}
