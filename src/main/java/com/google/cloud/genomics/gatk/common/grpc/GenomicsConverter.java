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
package com.google.cloud.genomics.gatk.common.grpc;

import com.google.cloud.genomics.gatk.common.GenomicsConverterBase;
import com.google.common.collect.Lists;
import com.google.genomics.v1.CigarUnit;
import com.google.genomics.v1.Read;
import com.google.genomics.v1.ReadGroup;
import com.google.genomics.v1.ReadGroup.Program;
import com.google.genomics.v1.ReadGroupSet;
import com.google.genomics.v1.Reference;
import com.google.protobuf.ListValue;
import com.google.protobuf.Value;

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
 * GRPC based implementation.
 */
public class GenomicsConverter 
  extends GenomicsConverterBase<Read, ReadGroupSet, Reference> {
  
  @Override
  public SAMRecord makeSAMRecord(Read read, SAMFileHeader header) {
    SAMRecord record = new SAMRecord(header);
    if (read.getFragmentName() != null && !read.getFragmentName().isEmpty()) {
      record.setReadName(read.getFragmentName());
    }
    if (read.getReadGroupId() != null && !read.getReadGroupId().isEmpty()) {
      record.setAttribute("RG" ,read.getReadGroupId());
    }
    // Set flags, as advised in http://google-genomics.readthedocs.org/en/latest/migrating_tips.html
    int flags = 0;

    final boolean paired = read.getNumberReads() == 2;
    flags += paired ? 1 : 0 ;// read_paired
    flags += (read.getProperPlacement()) ? 2 : 0; // read_proper_pair
    final boolean unmapped = (read.getAlignment() == null || 
        read.getAlignment().getPosition() == null || 
        read.getAlignment().getPosition().getPosition() == 0);
    flags += unmapped ? 4 : 0;  // read_unmapped
    flags += ((read.getNextMatePosition() == null || 
        read.getNextMatePosition().getPosition() == 0)) ? 8 : 0; // mate_unmapped
    flags += (read.getAlignment() != null && 
        read.getAlignment().getPosition() != null && 
        read.getAlignment().getPosition().getReverseStrand()) ? 16 : 0 ; // read_reverse_strand
    flags += (read.getNextMatePosition() != null &&
    	read.getNextMatePosition().getReverseStrand()) ? 32 : 0;  // mate_reverse_strand
    flags += (read.getReadNumber() == 0) ? 64 : 0; // first_in_pair
    flags += (read.getReadNumber() == 1) ? 128 : 0;  // second_in_pair
    flags += (read.getSecondaryAlignment()) ? 256 : 0; // secondary_alignment
    flags += (read.getFailedVendorQualityChecks()) ? 512 : 0;// failed_quality_check
    flags += (read.getDuplicateFragment()) ? 1024 : 0; // duplicate_read
    flags += (read.getSupplementaryAlignment()) ? 2048 : 0; //supplementary_alignment
    record.setFlags(flags);
    
    String referenceName = null;
    Long alignmentStart = null;
    if (read.getAlignment() != null) {
      if (read.getAlignment().getPosition() != null ) {
        referenceName = read.getAlignment().getPosition().getReferenceName();
        if (referenceName != null && !referenceName.isEmpty()) {
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
      
      List<CigarUnit> cigar = read.getAlignment().getCigarList();
      if (cigar != null && cigar.size() > 0) {
        StringBuffer cigarString = new StringBuffer(cigar.size());

        for (CigarUnit unit : cigar) {
          cigarString.append(String.valueOf(unit.getOperationLength()));
          cigarString.append(CIGAR_OPERATIONS.get(unit.getOperation().toString()));
        }
        record.setCigarString(cigarString.toString());
      }
    }

    if (read.getNextMatePosition() != null) {
      String mateReferenceName = read.getNextMatePosition().getReferenceName();
      if (mateReferenceName != null && !mateReferenceName.isEmpty()) {
        record.setMateReferenceName(mateReferenceName);
      }
      Long matePosition = read.getNextMatePosition().getPosition();
      if (matePosition != null) {
        // API positions are 0-based and SAMRecord is 1-based.
        record.setMateAlignmentStart(matePosition.intValue() + 1);
      }
    } 
    
    record.setInferredInsertSize(read.getFragmentLength());
    
    if (read.getAlignedSequence() != null) {
      record.setReadString(read.getAlignedSequence());
    }
    
    List<Integer> baseQuality = read.getAlignedQualityList();
    if (baseQuality != null && baseQuality.size() > 0) {
      byte[] qualityArray = new byte[baseQuality.size()];
      int idx = 0;
      for (Integer i : baseQuality) {
        qualityArray[idx++] = i.byteValue();
      }
      record.setBaseQualities(qualityArray);
    }

    Map<String, ListValue> tags = read.getInfo();
    if (tags != null) {
      for (String tag : tags.keySet()) {
        ListValue values = tags.get(tag);
        if (values != null) {
          for (Value value : values.getValuesList()) {
              Object attrValue = textTagCodec.decode(
                  tag + ":" + getTagType(tag) + ":" + value.getStringValue())
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
        if (reference.getName() != null && reference.getLength() != 0) {
          SAMSequenceRecord sequence = new SAMSequenceRecord(reference.getName(), 
              (int)reference.getLength());
          dict.addSequence(sequence);
        }
      }
      samHeader.setSequenceDictionary(dict);
    }
    
    List<SAMProgramRecord> programs = null;
    if (readGroupSet.getReadGroupsCount() != 0) {
      List<SAMReadGroupRecord> readgroups = Lists.newArrayList();
      for (ReadGroup RG : readGroupSet.getReadGroupsList()) {
        if (RG.getId() != null && RG.getName() != null) {
          String readGroupName = RG.getName();
          if (readGroupName == null || readGroupName.isEmpty()) {
            // We have to set the name to something, so if for some reason the proper
            // SAM tag for name was missing, we will use the generated id.
            readGroupName = RG.getId();
          }
          SAMReadGroupRecord readgroup = new SAMReadGroupRecord(readGroupName);
          if (RG.getDescription() != null && !RG.getDescription().isEmpty()) {
            readgroup.setDescription(RG.getDescription());
          }
   
          readgroup.setPredictedMedianInsertSize(RG.getPredictedInsertSize());
          
          if (RG.getSampleId() != null) {
            readgroup.setSample(RG.getSampleId());
          }
          if (RG.getExperiment() != null) {
            if (RG.getExperiment().getLibraryId() != null && !RG.getExperiment().getLibraryId().isEmpty()) {
              readgroup.setLibrary(RG.getExperiment().getLibraryId());
            }
            if (RG.getExperiment().getSequencingCenter() != null && !RG.getExperiment().getSequencingCenter().isEmpty()) {
              readgroup.setSequencingCenter(RG.getExperiment().getSequencingCenter());
            }
            if (RG.getExperiment().getInstrumentModel() != null && !RG.getExperiment().getInstrumentModel().isEmpty()) {
              readgroup.setPlatform(RG.getExperiment().getInstrumentModel());
            }
            if (RG.getExperiment().getPlatformUnit() != null && !RG.getExperiment().getPlatformUnit().isEmpty()) {
              readgroup.setPlatformUnit(RG.getExperiment().getPlatformUnit());
            }
          }
          readgroups.add(readgroup);
        }
        if (RG.getProgramsCount() > 0) {
          if (programs == null) {
            programs = Lists.newArrayList();
          }
          for (Program PG : RG.getProgramsList()) {
            SAMProgramRecord program = new SAMProgramRecord(PG.getId());
            if (PG.getCommandLine() != null && !PG.getCommandLine().isEmpty()) {
              program.setCommandLine(PG.getCommandLine());
            }
            if (PG.getName() != null  && !PG.getName().isEmpty()) {
              program.setProgramName(PG.getName());
            }
            if (PG.getPrevProgramId() != null  && !PG.getPrevProgramId().isEmpty()) {
              program.setPreviousProgramGroupId(PG.getPrevProgramId());
            }
            if (PG.getVersion() != null  && !PG.getVersion().isEmpty()) {
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
      Map<String, ListValue> tags = readGroupSet.getInfo();
      if (tags != null) {
        LOG.info("Getting @SQ header data from readgroupset info");
        StringBuffer buf = new StringBuffer();
        for (String tag : tags.keySet()) {
          if (!tag.startsWith(HEADER_SAM_TAG_INFO_KEY_PREFIX)) {
            continue;
          }
          final String headerName = tag.substring(HEADER_SAM_TAG_INFO_KEY_PREFIX.length());
          ListValue values = tags.get(tag);
          if (values == null) {
            continue;
          }
          for (Value value : values.getValuesList()) {
            buf.append(headerName);
            buf.append("\t");
            buf.append(value.getStringValue());
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
