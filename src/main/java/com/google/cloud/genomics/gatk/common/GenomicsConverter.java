/*
 * Copyright (C) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

// This file differs from GenomicsConverter in the utils-java package 
// ( com.google.cloud.genomics.utils) and it depends on HTSJDK 1.118.
// utils-java can not yet be moved to depend on HTSJDK due to pending
// work (https://github.com/samtools/htsjdk/pull/55).
// Once this completes and utils-java is updated this file can be removed. 
package com.google.cloud.genomics.gatk.common;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;

import java.util.List;

/**
 * Converts between genomics data representations by the Cloud Genomics API
 * and that of Picard Tools.
 */
public interface GenomicsConverter<Read, ReadGroupSet, Reference> {
  public SAMRecord makeSAMRecord(Read read, SAMFileHeader header);

  public SAMRecord makeSAMRecord(Read read, 
      ReadGroupSet readGroupSet, List<Reference> references, 
      boolean forceSetMatePositionToThisPosition);

  public SAMRecord makeSAMRecord(Read read, 
      boolean forceSetMatePositionToThisPosition);

  public SAMFileHeader makeSAMFileHeader(ReadGroupSet readGroupSet,
      List<Reference> references);
}
