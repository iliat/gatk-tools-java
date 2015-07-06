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
package com.google.cloud.genomics.gatk.common.grpc;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.util.Utils;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.cloud.genomics.gatk.common.GenomicsDataSourceBase;
import com.google.cloud.genomics.grpc.Channels;
import com.google.cloud.genomics.utils.GenomicsFactory.OfflineAuth;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.genomics.v1.GetReadGroupSetRequest;
import com.google.genomics.v1.GetReferenceRequest;
import com.google.genomics.v1.GetReferenceSetRequest;
import com.google.genomics.v1.Read;
import com.google.genomics.v1.ReadGroup;
import com.google.genomics.v1.ReadGroupSet;
import com.google.genomics.v1.ReadServiceV1Grpc;
import com.google.genomics.v1.ReadServiceV1Grpc.ReadServiceV1BlockingStub;
import com.google.genomics.v1.Reference;
import com.google.genomics.v1.ReferenceServiceV1Grpc;
import com.google.genomics.v1.ReferenceServiceV1Grpc.ReferenceServiceV1BlockingStub;
import com.google.genomics.v1.ReferenceSet;
import com.google.genomics.v1.StreamReadsRequest;
import com.google.genomics.v1.StreamReadsResponse;
import com.google.genomics.v1.StreamingReadServiceGrpc;
import com.google.genomics.v1.StreamingReadServiceGrpc.StreamingReadServiceBlockingStub;

import io.grpc.Channel;
import io.grpc.ChannelImpl;
import io.grpc.ClientInterceptors;
import io.grpc.auth.ClientAuthInterceptor;
import io.grpc.transport.netty.GrpcSslContexts;
import io.grpc.transport.netty.NegotiationType;
import io.grpc.transport.netty.NettyChannelBuilder;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * Manages Genomics Api initialization and provides Read iterator based
 * resources via Reads.search API invocations. 
 */
public class GenomicsDataSource 
  extends GenomicsDataSourceBase<Read, ReadGroupSet, Reference> {
  /** gRPC channel used for faster access to Genomics API */
  private Channel channel;
  private ChannelImpl channelImpl;
 
  public GenomicsDataSource(String rootUrl, 
      String clientSecretsFilename, 
      String apiKey,
      boolean noLocalServer) {
    super(rootUrl, clientSecretsFilename, apiKey, noLocalServer);
  }
    
  private Channel getChannel() throws FileNotFoundException, IOException, GeneralSecurityException {
    if (channel == null ) {
      channel = initGenomicsChannel();
    }
    return channel;
  }

  private Channel initGenomicsChannel() throws FileNotFoundException, IOException, GeneralSecurityException {
    checkParamsForAuth(AUTH_REQUIREMENTS.CLIENT_SECRETS_ONLY); 
    final GoogleClientSecrets secrets = GoogleClientSecrets.load(
        Utils.getDefaultJsonFactory(), 
        new FileReader(clientSecretsFilename));
    final OfflineAuth auth = getFactory()
        .getOfflineAuthFromClientSecretsFile(clientSecretsFilename);
    final UserCredentials userCredentials = new UserCredentials(
        secrets.getInstalled().getClientId(),
        secrets.getInstalled().getClientSecret(),
        auth.refreshToken);
    
 // Java 8's implementation of GCM ciphers is extremely slow. Therefore we disable
    // them here.
    List<String> defaultCiphers =
        GrpcSslContexts.forClient().ciphers(null).build().cipherSuites();
    List<String> performantCiphers = new ArrayList<>();
    for (String cipher : defaultCiphers) {
      if (!cipher.contains("GCM")) {
        performantCiphers.add(cipher);
      }
    }
    
    channelImpl = NettyChannelBuilder.forAddress("genomics.googleapis.com", 443)
        .negotiationType(NegotiationType.TLS)
        .streamWindowSize(1000000)
        .sslContext(GrpcSslContexts.forClient().ciphers(performantCiphers).build())
        .build();
    /*userCredentials = userCredentials.createScoped(
        Arrays.asList("https://www.googleapis.com/auth/genomics"));*/
    ClientAuthInterceptor interceptor = new ClientAuthInterceptor(userCredentials,
        Executors.newSingleThreadExecutor());
    return ClientInterceptors.intercept(channelImpl, interceptor); 
  }

 
  @Override
  public ReadIteratorResource getReads(
      String readsetId, 
      String sequenceName, int sequenceStart, int sequenceEnd) 
          throws IOException, GeneralSecurityException {
    LOG.info("Getting readset from GRPC:" + readsetId + ", sequence " + sequenceName + 
        ", start=" + sequenceStart + ", end=" + sequenceEnd);
    final Channel channel = getChannel();
    
    ReadServiceV1BlockingStub readStub = ReadServiceV1Grpc.newBlockingStub(channel);
    GetReadGroupSetRequest getReadGroupSetRequest = GetReadGroupSetRequest
        .newBuilder()
        .setReadGroupSetId(readsetId)
        .build();

    ReadGroupSet readGroupSet = readStub.getReadGroupSet(getReadGroupSetRequest);
    String datasetId = readGroupSet.getDatasetId();
    LOG.info("Found readset " + readsetId + ", dataset " + datasetId);
    
    final Map<String, Reference> references = 
        getReferences(readGroupSet);
    
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
    
    StreamingReadServiceBlockingStub streamingReadStub = 
        StreamingReadServiceGrpc.newBlockingStub(getChannel());
    StreamReadsRequest.Builder streamReadsRequestBuilder = StreamReadsRequest.newBuilder()
        .setReadGroupSetId(readsetId)
        .setReferenceName(sequenceName);
    if (sequenceStart != 0) {
      streamReadsRequestBuilder.setStart(Long.valueOf(sequenceStart));
    }
    if (sequenceEnd != 0) {
      streamReadsRequestBuilder.setEnd(Long.valueOf(sequenceEnd));
    }
    final StreamReadsRequest streamReadRequest = streamReadsRequestBuilder.build();
    final Iterable<Read> reads = streamReadsResponseToReadsIterator(
        streamingReadStub.streamReads(streamReadRequest), sequenceEnd);
    return new ReadIteratorResource(readGroupSet, 
        Lists.newArrayList(references.values()), unmappedReads, reads);

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
    if (readGroupSet.getReadGroupsCount() > 0) {
      LOG.info("Found read groups");
      for (ReadGroup readGroup : readGroupSet.getReadGroupsList()) {
        if (readGroup.getReferenceSetId() != null) {
          LOG.info("Found reference set from read group: " + 
              readGroup.getReferenceSetId());
          referenceSetIds.add(readGroup.getReferenceSetId());
        }
      }
    }
    
    ReferenceServiceV1BlockingStub referenceSetStub = 
        ReferenceServiceV1Grpc.newBlockingStub(getChannel());
        
    Map<String, Reference> references = Maps.newHashMap();
    for (String referenceSetId : referenceSetIds) {
      LOG.info("Getting reference set " + referenceSetId);
      GetReferenceSetRequest getReferenceSetRequest = GetReferenceSetRequest
          .newBuilder().setReferenceSetId(referenceSetId).build();
      ReferenceSet referenceSet = 
          referenceSetStub.getReferenceSet(getReferenceSetRequest);
      if (referenceSet == null || referenceSet.getReferenceIdsCount() == 0) {
        continue;
      }
      for (String referenceId : referenceSet.getReferenceIdsList()) {
        LOG.fine("Getting reference  " + referenceId);
        GetReferenceRequest getReferenceRequest = GetReferenceRequest
            .newBuilder().setReferenceId(referenceId).build();
        Reference reference = referenceSetStub.getReference(getReferenceRequest);
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
  
  public static class ReadResponsesIterator implements Iterator<Read> {
    Iterator<StreamReadsResponse> responses;
    Iterator<Read> readsFromCurrentResponse;
    Read nextRead;
    int endPos;
    
    public ReadResponsesIterator(Iterator<StreamReadsResponse> responses, int endPos) {
      this.responses = responses;
      this.endPos = endPos;
      nextRead = peek();
    }
    
    Read peek() {
      // This is a loop in order to handle the case
      // of response with no alignments in them.
      // We either exit it when we have non empty readsFromCurrentResponse
      // or bail out if there are no more responses.
      while (readsFromCurrentResponse == null || 
          !readsFromCurrentResponse.hasNext()) {
        if (responses.hasNext()) {
          readsFromCurrentResponse = 
              responses.next().getAlignmentsList().iterator();
        } else {
          return null;
        }
      }
      return readsFromCurrentResponse.next();
    }
    
    @Override
    public boolean hasNext() {
      if (nextRead != null && endPos > 0) {
        if (nextRead.getAlignment() != null && 
            nextRead.getAlignment().getPosition() != null) {
          //LOG.info("READ AT: " + 
          //  nextRead.getAlignment().getPosition().getPosition());
          if (nextRead.getAlignment().getPosition().getPosition() > endPos) {
            LOG.info("Iteration has passed beyond the end position: " + endPos +
                ", current read is at " + 
                nextRead.getAlignment().getPosition().getPosition());
            return false;
          }
        }
      }
      return nextRead != null;
    }

    @Override
    public Read next() {
      final Read readToReturn = nextRead;
      nextRead = peek();
      return readToReturn;
    }

    @Override
    public void remove() {
      // Not implemented
    }
    
  }
  private Iterable<Read> streamReadsResponseToReadsIterator(
      final Iterator<StreamReadsResponse> responseIterator, final int endPos) {
    return new Iterable<Read>() {
      @Override
      public Iterator<Read> iterator() {
        return new ReadResponsesIterator(responseIterator, endPos);
      }
    };
  }
  
  @Override
  protected Iterable<Read> getUnmappedReadsIterator(String readsetId) 
      throws GeneralSecurityException, IOException {
    StreamingReadServiceBlockingStub streamingReadStub = 
        StreamingReadServiceGrpc.newBlockingStub(getChannel());
    final StreamReadsRequest streamReadRequest = StreamReadsRequest.newBuilder()
        .setReadGroupSetId(readsetId)
        .setReferenceName("*")
        .build();
    return streamReadsResponseToReadsIterator(
        streamingReadStub.streamReads(streamReadRequest), 0);
  }
  
  @Override
  public void close() {
    if (channelImpl != null ) {
      channelImpl.shutdown();
      channelImpl = null;
    }
    channel = null;
  }
}
