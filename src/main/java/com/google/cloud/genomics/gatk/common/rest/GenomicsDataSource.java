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
package com.google.cloud.genomics.gatk.common.rest;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.genomics.Genomics;
import com.google.api.services.genomics.model.Read;
import com.google.api.services.genomics.model.ReadGroup;
import com.google.api.services.genomics.model.ReadGroupSet;
import com.google.api.services.genomics.model.Reference;
import com.google.api.services.genomics.model.ReferenceSet;
import com.google.api.services.genomics.model.SearchReadsRequest;
import com.google.cloud.genomics.gatk.common.GenomicsDataSourceBase;
import com.google.cloud.genomics.utils.Paginator;
import com.google.cloud.genomics.utils.Paginator.ShardBoundary;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Manages Genomics REST Api initialization and provides Read iterator based
 * resources via Reads.search API invocations. 
 */
public class GenomicsDataSource 
    extends GenomicsDataSourceBase<Read, ReadGroupSet, Reference> {
 
  /** Genomics API stub used for regular API access */ 
  private Genomics api;
 
  public GenomicsDataSource(String rootUrl, 
      String clientSecretsFilename, 
      String apiKey,
      boolean noLocalServer) {
    super(rootUrl, clientSecretsFilename, apiKey, noLocalServer);
  }
  
  private Genomics getApi() throws GeneralSecurityException, IOException {
    if (api == null) {
      api = initGenomicsApi();
    }
    return api;
  }
    
  private Genomics initGenomicsApi() throws GeneralSecurityException, IOException {
    LOG.info("Initializing Genomics API for " + rootUrl);
    checkParamsForAuth(AUTH_REQUIREMENTS.NONE); 
    if (!clientSecretsFilename.isEmpty() || !apiKey.isEmpty()) {
      if (!clientSecretsFilename.isEmpty()) {
        LOG.info("Using client secrets file " + clientSecretsFilename);
        return getFactory().fromClientSecretsFile(new File(clientSecretsFilename));
      } else {
        LOG.info("Using API key");
        return getFactory().fromApiKey(apiKey);
      }
    } else {
      final Genomics.Builder builder = new Genomics
          .Builder(
              GoogleNetHttpTransport.newTrustedTransport(),
              JacksonFactory.getDefaultInstance(),
              new HttpRequestInitializer() {
                @Override public void initialize(HttpRequest httpRequest) throws IOException {
                  httpRequest.setReadTimeout(20000);
                  httpRequest.setConnectTimeout(20000);
                }
              })
          .setApplicationName("genomics_java_client")
          .setRootUrl(rootUrl)
          .setServicePath("/");
        return builder.build();
    }
  }
    
  @Override
  public ReadIteratorResource getReads(String readsetId, 
      String sequenceName, int sequenceStart, int sequenceEnd) 
          throws IOException, GeneralSecurityException {
    LOG.info("Getting readset " + readsetId + ", sequence " + sequenceName + 
        ", start=" + sequenceStart + ", end=" + sequenceEnd);    
    final Genomics stub = getApi();

    try {
      ReadGroupSet readGroupSet = stub.readgroupsets().get(readsetId).execute();
      String datasetId = readGroupSet.getDatasetId();
      LOG.info("Found readset " + readsetId + ", dataset " + datasetId);
      
      final Map<String, Reference> references = getReferences(readGroupSet);
      final Reference reference = references.get(sequenceName);
      if (reference != null) {
          LOG.info("Reference for sequence name " + sequenceName + " is found, length="
              + String.valueOf(reference.getLength()));
      } else {
        LOG.warning("Reference for sequence name " + sequenceName + " not found");
      }
      LOG.info("Searching for reads in sequence " + sequenceName + " " + 
          String.valueOf(sequenceStart) + "-" + String.valueOf(sequenceEnd));
      com.google.cloud.genomics.gatk.common.UnmappedReads<Read> unmappedReads = null;
      if (sequenceName.isEmpty()) {
        unmappedReads = getUnmappedMatesOfMappedReads(readsetId); 
      }
      Paginator.Reads searchReads = Paginator.Reads.create(stub, ShardBoundary.OVERLAPS);
      SearchReadsRequest readRequest = new SearchReadsRequest()
        .setReadGroupSetIds(Arrays.asList(readsetId))
        .setReferenceName(sequenceName)
        .setPageSize(2048);
      if (sequenceStart != 0) {
        readRequest.setStart(Long.valueOf(sequenceStart));
      }
      if (sequenceEnd != 0) {
        readRequest.setEnd(Long.valueOf(sequenceEnd));
      }
      Iterable<Read> reads = searchReads.search(readRequest); 
      
      return new ReadIteratorResource(readGroupSet, 
          Lists.newArrayList(references.values()), unmappedReads, reads);
    } catch (GoogleJsonResponseException ex) {
      LOG.warning("Genomics API call failure: " + ex.getMessage());
      if (ex.getDetails() == null) {
        throw ex;
      }
      throw new IOException(ex.getDetails().getMessage());
    }
  }
  
  /**
   * Collect a list of references mentioned in this Readgroupset and get their meta data.
   * @throws GeneralSecurityException 
   * @throws IOException 
   */
  private Map<String, Reference> getReferences(ReadGroupSet readGroupSet) 
      throws IOException, GeneralSecurityException {
    Set<String> referenceSetIds = Sets.newHashSet();
    if (readGroupSet.getReferenceSetId() != null) {
      LOG.info("Found reference set from read group set " + 
          readGroupSet.getReferenceSetId());
      referenceSetIds.add(readGroupSet.getReferenceSetId());
    }
    if (readGroupSet.getReadGroups() != null) {
      LOG.info("Found read groups");
      for (ReadGroup readGroup : readGroupSet.getReadGroups()) {
        if (readGroup.getReferenceSetId() != null) {
          LOG.info("Found reference set from read group: " + 
              readGroup.getReferenceSetId());
          referenceSetIds.add(readGroup.getReferenceSetId());
        }
      }
    }
    
    Map<String, Reference> references = Maps.newHashMap();
    for (String referenceSetId : referenceSetIds) {
      LOG.info("Getting reference set " + referenceSetId);
      ReferenceSet referenceSet = getApi().referencesets().get(referenceSetId).execute();
      if (referenceSet == null || referenceSet.getReferenceIds() == null) {
        continue;
      }
      for (String referenceId : referenceSet.getReferenceIds()) {
        LOG.fine("Getting reference  " + referenceId);
        Reference reference = getApi().references().get(referenceId).execute();
        if (reference.getName() != null) {
          references.put(reference.getName(), reference);
          LOG.fine("Adding reference  " + reference.getName());
        }
      }
    }
    return references;
  }

  @Override
  protected UnmappedReads createUnmappedReads() {
    return new UnmappedReads();
  }

  @Override
  protected Iterable<Read> getUnmappedReadsIterator(String readsetId) throws GeneralSecurityException, IOException {
    final Paginator.Reads searchUnmappedReads = 
        Paginator.Reads.create(getApi(), ShardBoundary.OVERLAPS);
    final SearchReadsRequest unmappedReadRequest = new SearchReadsRequest()
      .setReadGroupSetIds(Arrays.asList(readsetId))
      .setReferenceName("*");
    final Iterable<Read> unmappedReadsIterable = 
        searchUnmappedReads.search(unmappedReadRequest);
    return unmappedReadsIterable;
  }
  
  @Override
  public void close() {
    api = null;
  }
}
