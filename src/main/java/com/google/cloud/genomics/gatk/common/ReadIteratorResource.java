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

import com.google.api.services.genomics.model.HeaderSection;
import com.google.api.services.genomics.model.Read;
import com.google.cloud.genomics.pipelines.utils.GenomicsConverter;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;

import java.util.Iterator;

/**
 * Provides reads data in the from of SAMRecords and SAMFileHeader, by wrapping
 * an existing source of Read and HeaderSection data and doing the conversion using
 * GenomicsConverter.
 */
public class ReadIteratorResource {
  private HeaderSection header;
  private Iterable<Read> iterable;
  
  public ReadIteratorResource(HeaderSection header, Iterable<Read> iterable) {
    super();
    this.header = header;
    this.iterable = iterable;
  }
  
  public HeaderSection getHeader() {
    return header;
  }
  
  public void setHeader(HeaderSection header) {
    this.header = header;
  }
  
  public Iterable<Read> getIterable() {
    return iterable;
  }
  
  public void setIterable(Iterable<Read> iterable) {
    this.iterable = iterable;
  }
  
  public SAMFileHeader getSAMFileHeader() {
    return GenomicsConverter.makeSAMFileHeader(getHeader());
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
