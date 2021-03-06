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

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Manages auth. and communications initializations and provides iteration over Reads.
 */
public interface GenomicsDataSource<Read, ReadGroupSet, Reference> {  
  public ReadIteratorResource<Read, ReadGroupSet, Reference> getReads(GA4GHUrl url) 
       throws IOException, GeneralSecurityException;
  public ReadIteratorResource<Read, ReadGroupSet, Reference> getReads(String readsetId, 
      String sequenceName, int sequenceStart, int sequenceEnd) throws IOException, GeneralSecurityException;
  void close();
}
