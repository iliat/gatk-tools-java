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

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;

import java.util.List;

/**
 * Provides reads data in the from of SAMRecords and SAMFileHeader, by wrapping
 * an existing source of Read and HeaderSection data and doing the conversion using
 * GenomicsConverter.
 */
public interface ReadIteratorResource<Read, ReadGroupSet, Reference> {
  public ReadGroupSet getReadGroupSet();
  
  public void setReadGroupSet(ReadGroupSet readGroupSet);
  
  public List<Reference> getReferences();
  
  public void setReferences(List<Reference> references);
  
  public Iterable<Read> getIterable();
  
  public void setIterable(Iterable<Read> iterable);
  
  public SAMFileHeader getSAMFileHeader();
  
  public Iterable<SAMRecord> getSAMRecordIterable();
}
