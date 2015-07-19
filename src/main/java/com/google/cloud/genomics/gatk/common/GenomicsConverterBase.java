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


import com.google.api.services.genomics.model.Read;
import com.google.api.services.genomics.model.ReadGroupSet;
import com.google.api.services.genomics.model.Reference;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.TextTagCodec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A utility class for converting between genomics data representations by the Cloud Genomics API
 * and that of Picard Tools
 * 
 * Notes: Conversion is not perfect, information WILL be lost!
 *        HTSJDK formats get very mad about passing nulls, so lots of null checks. Genomics API 
 *        classes however are fine with nulls so no checks need to be done.
 * 
 * Currently supported conversions:
 *      Read <-> SAMRecord
 *      HeaderSection <-> SAMFileHeader
 */
public abstract class GenomicsConverterBase<Read, ReadGroupSet, Reference> 
  implements GenomicsConverter<Read, ReadGroupSet, Reference> {
  protected static final Logger LOG = Logger.getLogger(GenomicsConverter.class.getName());
  
  // Prefix for SAM tag in the info array of ReadGroupSet.
  protected static final String HEADER_SAM_TAG_INFO_KEY_PREFIX = "SAM:";
  /** 
   * Standard tags defined in SAM spec. and their types.
   * See http://samtools.github.io/hts-specs/SAMv1.pdf, section 1.5.
   */
  protected static Map<String, String> SAM_TAGS;
  
  /**
   * Map form CIGAR operations as represented in the API to standard SAM ones.
   */
  protected static Map<String, String> CIGAR_OPERATIONS;
  
  static {
    SAM_TAGS = new HashMap<String,String>();
    SAM_TAGS.put("AM", "i");
    SAM_TAGS.put("AS", "i");
    SAM_TAGS.put("BC", "Z");
    SAM_TAGS.put("BQ", "Z");
    SAM_TAGS.put("CC", "Z");
    SAM_TAGS.put("CM", "i");
    SAM_TAGS.put("CO", "Z");
    SAM_TAGS.put("CP", "i");
    SAM_TAGS.put("CQ", "Z");
    SAM_TAGS.put("CS", "Z");
    SAM_TAGS.put("CT", "Z");
    SAM_TAGS.put("E2", "Z");
    SAM_TAGS.put("FI", "i");
    SAM_TAGS.put("FS", "Z");
    SAM_TAGS.put("FZ", "B");
    SAM_TAGS.put("H0", "i");
    SAM_TAGS.put("H1", "i");
    SAM_TAGS.put("H2", "i");
    SAM_TAGS.put("HI", "i");
    SAM_TAGS.put("IH", "i");
    SAM_TAGS.put("LB", "Z");
    SAM_TAGS.put("MC", "Z");
    SAM_TAGS.put("MD", "Z");
    SAM_TAGS.put("MQ", "i");
    SAM_TAGS.put("NH", "i");
    SAM_TAGS.put("NM", "i");
    SAM_TAGS.put("OQ", "Z");
    SAM_TAGS.put("OP", "i");
    SAM_TAGS.put("OC", "Z");
    SAM_TAGS.put("PG", "Z");
    SAM_TAGS.put("PQ", "i");
    SAM_TAGS.put("PT", "Z");
    SAM_TAGS.put("PU", "Z");
    SAM_TAGS.put("QT", "Z");
    SAM_TAGS.put("Q2", "Z");
    SAM_TAGS.put("R2", "Z");
    SAM_TAGS.put("RG", "Z");
    SAM_TAGS.put("RT", "Z");
    SAM_TAGS.put("SA", "Z");
    SAM_TAGS.put("SM", "i");
    SAM_TAGS.put("TC", "i");
    SAM_TAGS.put("U2", "Z");
    SAM_TAGS.put("UQ", "i");
    
    SAM_TAGS.put("MF", "i");
    SAM_TAGS.put("Aq", "i");
    
    CIGAR_OPERATIONS = new HashMap<String, String>();
    CIGAR_OPERATIONS.put("ALIGNMENT_MATCH","M");
    CIGAR_OPERATIONS.put("CLIP_HARD", "H");
    CIGAR_OPERATIONS.put("CLIP_SOFT","S");
    CIGAR_OPERATIONS.put("DELETE", "D");
    CIGAR_OPERATIONS.put("INSERT", "I");
    CIGAR_OPERATIONS.put("PAD", "P");
    CIGAR_OPERATIONS.put("SEQUENCE_MATCH", "=");
    CIGAR_OPERATIONS.put("SEQUENCE_MISMATCH", "X");
    CIGAR_OPERATIONS.put("SKIP", "N");
  }
  
  /** Returns SAM Tag type. If not a known tag - defaults to "Z". */
  public static String getTagType(String tagName) {
    final String result = SAM_TAGS.get(tagName);
    return result != null ? result : "Z";
  }
   
  /** Codec used for converting know SAM tags and their values, from strings to Objects */
  protected static TextTagCodec textTagCodec = new TextTagCodec();
  
 
  @Override
  public SAMRecord makeSAMRecord(Read read, 
      ReadGroupSet readGroupSet, List<Reference> references, 
      boolean forceSetMatePositionToThisPosition) {
    return makeSAMRecord(read, makeSAMFileHeader(readGroupSet, references));
  }

  @Override
  public SAMRecord makeSAMRecord(Read read, 
      boolean forceSetMatePositionToThisPosition) {
    return makeSAMRecord(read, new SAMFileHeader());
  }

  @Override
  public abstract SAMFileHeader makeSAMFileHeader(ReadGroupSet readGroupSet,
      List<Reference> references);
  
  @Override
  public abstract SAMRecord makeSAMRecord(Read read, SAMFileHeader header);
}
