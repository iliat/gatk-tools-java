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

import com.google.api.services.genomics.model.Read;
import com.google.api.services.genomics.model.ReadGroupSet;
import com.google.api.services.genomics.model.Reference;
import com.google.cloud.genomics.gatk.common.ReadIteratorResourceBase;
import com.google.cloud.genomics.gatk.common.UnmappedReads;

import java.util.List;

/**
 * Genomics REST Api based implementation.
 */
public class ReadIteratorResource extends ReadIteratorResourceBase<Read, ReadGroupSet, Reference> {
  public ReadIteratorResource(ReadGroupSet readGroupSet, List<Reference> references,
      UnmappedReads<Read> unmappedReads, 
      Iterable<Read> iterable) {
    super(readGroupSet, references, unmappedReads, iterable, 
        new GenomicsConverter());
  }
  @Override
  protected String getReferenceNameFromRead(Read read) {
    return read.getAlignment().getPosition().getReferenceName();
  }
}
