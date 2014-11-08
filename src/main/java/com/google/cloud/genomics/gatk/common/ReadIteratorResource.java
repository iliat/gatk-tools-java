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

import com.google.api.services.genomics.model.Read;
import com.google.api.services.genomics.model.ReadGroupSet;
import com.google.api.services.genomics.model.Reference;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;

import java.util.Iterator;
import java.util.List;

/**
 * Provides reads data in the from of SAMRecords and SAMFileHeader, by wrapping
 * an existing source of Read and HeaderSection data and doing the conversion using
 * GenomicsConverter.
 */
public class ReadIteratorResource {
  private ReadGroupSet readGroupSet;
  private List<Reference> references;
  private Iterable<Read> iterable;
  
  public ReadIteratorResource(ReadGroupSet readGroupSet, List<Reference> references,
      Iterable<Read> iterable) {
    super();
    this.readGroupSet = readGroupSet;
    this.references = references;
    this.iterable = iterable;
  }

  public ReadGroupSet getReadGroupSet() {
    return readGroupSet;
  }
  
  public void setReadGroupSet(ReadGroupSet readGroupSet) {
    this.readGroupSet = readGroupSet;
  }
  
  public List<Reference> getReferences() {
    return references;
  }

  public void setReferences(List<Reference> references) {
    this.references = references;
  }
  
  public Iterable<Read> getIterable() {
    return iterable;
  }
  
  public void setIterable(Iterable<Read> iterable) {
    this.iterable = iterable;
  }
  
  public SAMFileHeader getSAMFileHeader() {
    return GenomicsConverter.makeSAMFileHeader(getReadGroupSet(), getReferences());
  }
  
  public Iterable<SAMRecord> getSAMRecordIterable() {
    final Iterator<Read> readIterator = getIterable().iterator();
    final SAMFileHeader header = getSAMFileHeader();
    return new Iterable<SAMRecord>() {
      @Override
      public Iterator<SAMRecord> iterator() {
        return new Iterator<SAMRecord>() {

          @Override
          public boolean hasNext() {
            return readIterator.hasNext();
          }

          @Override
          public SAMRecord next() {
            return GenomicsConverter.makeSAMRecord(readIterator.next(), 
                header);
          }

          @Override
          public void remove() {
            readIterator.remove(); 
          }
        };
      }
    };
  }
}
