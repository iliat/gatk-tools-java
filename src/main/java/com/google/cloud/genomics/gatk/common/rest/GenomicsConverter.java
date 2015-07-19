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
package com.google.cloud.genomics.gatk.common.rest;

import com.google.api.services.genomics.model.CigarUnit;
import com.google.api.services.genomics.model.Read;
import com.google.api.services.genomics.model.ReadGroup;
import com.google.api.services.genomics.model.ReadGroupProgram;
import com.google.api.services.genomics.model.ReadGroupSet;
import com.google.api.services.genomics.model.Reference;
import com.google.cloud.genomics.gatk.common.GenomicsConverterBase;
import com.google.common.collect.Lists;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMProgramRecord;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SAMTextHeaderCodec;
import htsjdk.samtools.SamFileHeaderMerger;
import htsjdk.samtools.TagValueAndUnsignedArrayFlag;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.StringLineReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Genomics REST Api based implementation.
 */
public class GenomicsConverter 
  extends GenomicsConverterBase<Read, ReadGroupSet, Reference> {
  
  @Override
  public SAMRecord makeSAMRecord(Read read, SAMFileHeader header) {
    SAMRecord record = new SAMRecord(header);
    if (read.getFragmentName() != null) {
      record.setReadName(read.getFragmentName());
    }
    if (read.getReadGroupId() != null) {
      record.setAttribute("RG" ,read.getReadGroupId());
    }
    // Set flags, as advised in http://google-genomics.readthedocs.org/en/latest/migrating_tips.html
    int flags = 0;

    final boolean paired = (read.getNumberReads() != null && 
        read.getNumberReads() == 2);
    flags += paired ? 1 : 0 ;// read_paired
    flags += (read.getProperPlacement() != null &&
        read.getProperPlacement()) ? 2 : 0; // read_proper_pair
    final boolean unmapped = (read.getAlignment() == null || 
        read.getAlignment().getPosition() == null || 
        read.getAlignment().getPosition().getPosition() == null);
    flags += unmapped ? 4 : 0;  // read_unmapped
    flags += ((read.getNextMatePosition() == null || 
        read.getNextMatePosition().getPosition() == null)) ? 8 : 0; // mate_unmapped
    flags += (read.getAlignment() != null && 
        read.getAlignment().getPosition() != null && 
        Boolean.TRUE.equals(read.getAlignment().getPosition().getReverseStrand())) ? 16 : 0 ; // read_reverse_strand
    flags += (read.getNextMatePosition() != null &&
    	Boolean.TRUE.equals(read.getNextMatePosition().getReverseStrand())) ? 32 : 0;  // mate_reverse_strand
    flags += (read.getReadNumber() != null && 
        read.getReadNumber() == 0) ? 64 : 0; // first_in_pair
    flags += (read.getReadNumber() != null && 
        read.getReadNumber() == 1) ? 128 : 0;  // second_in_pair
    flags += (read.getSecondaryAlignment() != null 
        && Boolean.TRUE.equals(read.getSecondaryAlignment())) ? 256 : 0; // secondary_alignment
    flags += (read.getFailedVendorQualityChecks() != null &&
    	Boolean.TRUE.equals(read.getFailedVendorQualityChecks())) ? 512 : 0;// failed_quality_check
    flags += (read.getDuplicateFragment() != null && 
    	Boolean.TRUE.equals(read.getDuplicateFragment())) ? 1024 : 0; // duplicate_read
    flags += (read.getSupplementaryAlignment() != null &&
    	Boolean.TRUE.equals(read.getSupplementaryAlignment())) ? 2048 : 0; //supplementary_alignment
    record.setFlags(flags);
    
    String referenceName = null;
    Long alignmentStart = null;
    if (read.getAlignment() != null) {
      if (read.getAlignment().getPosition() != null ) {
        referenceName = read.getAlignment().getPosition().getReferenceName();
        if (referenceName != null) {
          record.setReferenceName(referenceName);
        }
        alignmentStart = read.getAlignment().getPosition().getPosition();
        if (alignmentStart != null) {
          // API positions are 0-based and SAMRecord is 1-based.
          record.setAlignmentStart(alignmentStart.intValue() + 1);
        }
      }
      Integer mappingQuality = read.getAlignment().getMappingQuality();
      if (mappingQuality != null) {
        record.setMappingQuality(mappingQuality);
      }
      
      List<CigarUnit> cigar = read.getAlignment().getCigar();
      if (cigar != null && cigar.size() > 0) {
        StringBuffer cigarString = new StringBuffer(cigar.size());

        for (CigarUnit unit : cigar) {
          cigarString.append(String.valueOf(unit.getOperationLength()));
          cigarString.append(CIGAR_OPERATIONS.get(unit.getOperation()));
        }
        record.setCigarString(cigarString.toString());
      }
    }

    if (read.getNextMatePosition() != null) {
      String mateReferenceName = read.getNextMatePosition().getReferenceName();
      if (mateReferenceName != null) {
        record.setMateReferenceName(mateReferenceName);
      }
      Long matePosition = read.getNextMatePosition().getPosition();
      if (matePosition != null) {
        // API positions are 0-based and SAMRecord is 1-based.
        record.setMateAlignmentStart(matePosition.intValue() + 1);
      }
    } 
    
    if (read.getFragmentLength() != null) {
      record.setInferredInsertSize(read.getFragmentLength());
    }
    if (read.getAlignedSequence() != null) {
      record.setReadString(read.getAlignedSequence());
    }
    
    List<Integer> baseQuality = read.getAlignedQuality();
    if (baseQuality != null && baseQuality.size() > 0) {
      byte[] qualityArray = new byte[baseQuality.size()];
      int idx = 0;
      for (Integer i : baseQuality) {
        qualityArray[idx++] = i.byteValue();
      }
      record.setBaseQualities(qualityArray);
    }

    Map<String, List<String>> tags = read.getInfo();
    if (tags != null) {
      for (String tag : tags.keySet()) {
        List<String> values = tags.get(tag);
        if (values != null) {
          for (String value : values) {
              Object attrValue = textTagCodec.decode(
                  tag + ":" + getTagType(tag) + ":" + value)
                  .getValue();
                  if (attrValue instanceof TagValueAndUnsignedArrayFlag) {
                    record.setUnsignedArrayAttribute(tag, 
                        ((TagValueAndUnsignedArrayFlag)attrValue).value);
                  } else {
                    record.setAttribute(tag, attrValue);
                  }
          }
        }
      }
    }

    return record;
  }

  /**
   * Generates a SAMFileHeader from a ReadGroupSet and Reference metadata
   */
  @Override
  public SAMFileHeader makeSAMFileHeader(ReadGroupSet readGroupSet,
      List<Reference> references) {
    List<SAMFileHeader> samHeaders = new ArrayList<SAMFileHeader>(2);
    SAMFileHeader samHeader = new SAMFileHeader();
    samHeaders.add(samHeader);
    
    // Reads are always returned in coordinate order form the API.
    samHeader.setSortOrder(SAMFileHeader.SortOrder.coordinate);
    
    if (references != null && references.size() > 0) {
      SAMSequenceDictionary dict = new SAMSequenceDictionary();
      for (Reference reference : references) {
        if (reference.getName() != null && reference.getLength() != null) {
          SAMSequenceRecord sequence = new SAMSequenceRecord(reference.getName(), 
              reference.getLength().intValue());
          dict.addSequence(sequence);
        }
      }
      samHeader.setSequenceDictionary(dict);
    }
    
    List<SAMProgramRecord> programs = null;
    if (readGroupSet.getReadGroups() != null && readGroupSet.getReadGroups().size() > 0) {
      List<SAMReadGroupRecord> readgroups = Lists.newArrayList();
      for (ReadGroup RG : readGroupSet.getReadGroups()) {
        if (RG.getId() != null && RG.getName() != null) {
          String readGroupName = RG.getName();
          if (readGroupName == null|| readGroupName.isEmpty()) {
            // We have to set the name to something, so if for some reason the proper
            // SAM tag for name was missing, we will use the generated id.
            readGroupName = RG.getId();
          }
          SAMReadGroupRecord readgroup = new SAMReadGroupRecord(readGroupName);
          if (RG.getDescription() != null) {
            readgroup.setDescription(RG.getDescription());
          }
          if (RG.getPredictedInsertSize() != null) {
            readgroup.setPredictedMedianInsertSize(RG.getPredictedInsertSize());
          }
          if (RG.getSampleId() != null) {
            readgroup.setSample(RG.getSampleId());
          }
          if (RG.getExperiment() != null) {
            if (RG.getExperiment().getLibraryId() != null) {
              readgroup.setLibrary(RG.getExperiment().getLibraryId());
            }
            if (RG.getExperiment().getSequencingCenter() != null) {
              readgroup.setSequencingCenter(RG.getExperiment().getSequencingCenter());
            }
            if (RG.getExperiment().getInstrumentModel() != null) {
              readgroup.setPlatform(RG.getExperiment().getInstrumentModel());
            }
            if (RG.getExperiment().getPlatformUnit() != null) {
              readgroup.setPlatformUnit(RG.getExperiment().getPlatformUnit());
            }
          }
          readgroups.add(readgroup);
        }
        if (RG.getPrograms() != null && RG.getPrograms().size() > 0) {
          if (programs == null) {
            programs = Lists.newArrayList();
          }
          for (ReadGroupProgram PG : RG.getPrograms()) {
            SAMProgramRecord program = new SAMProgramRecord(PG.getId());
            if (PG.getCommandLine() != null) {
              program.setCommandLine(PG.getCommandLine());
            }
            if (PG.getName() != null) {
              program.setProgramName(PG.getName());
            }
            if (PG.getPrevProgramId() != null) {
              program.setPreviousProgramGroupId(PG.getPrevProgramId());
            }
            if (PG.getVersion() != null) {
              program.setProgramVersion(PG.getVersion());
            }
            programs.add(program);
          }
        }
      }
      samHeader.setReadGroups(readgroups);
      if (programs != null) {
        samHeader.setProgramRecords(programs);
      }
    }
    
    // If BAM file is imported with non standard reference, the SQ tags
    // are preserved in the info key/value array.
    // Attempt to read them form there.
    if (references == null || references.size() <= 0) {
      @SuppressWarnings("unchecked")
      Map<String, List<String>> tags = 
          (Map<String, List<String>>)readGroupSet.get("info");
      if (tags != null) {
        LOG.info("Getting @SQ header data from readgroupset info");
        StringBuffer buf = new StringBuffer();
        for (String tag : tags.keySet()) {
          if (!tag.startsWith(HEADER_SAM_TAG_INFO_KEY_PREFIX)) {
            continue;
          }
          final String headerName = tag.substring(HEADER_SAM_TAG_INFO_KEY_PREFIX.length());
          List<String> values = tags.get(tag);
          if (values == null) {
            continue;
          }
          for (String value : values) {
            buf.append(headerName);
            buf.append("\t");
            buf.append(value);
            buf.append("\r\n");
          }
          final String headerString = buf.toString();
          final SAMTextHeaderCodec codec = new SAMTextHeaderCodec();
          codec.setValidationStringency(ValidationStringency.STRICT);
          final SAMFileHeader parsedHeader = codec.decode(
              new StringLineReader(headerString), null);
          samHeaders.add(parsedHeader);
        }
      }
    }
    
    final SAMFileHeader finalHeader = 
        (new SamFileHeaderMerger(
            SAMFileHeader.SortOrder.coordinate, samHeaders, true))
        .getMergedHeader();
    
    return finalHeader;
  }  
}
