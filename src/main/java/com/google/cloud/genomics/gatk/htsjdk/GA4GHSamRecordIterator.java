package com.google.cloud.genomics.gatk.htsjdk;

import com.google.cloud.genomics.gatk.common.GenomicsApiDataSource;
import com.google.cloud.genomics.gatk.common.ReadIteratorResource;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;

import java.util.Iterator;
import java.util.logging.Logger;

/**
 * Wraps iterators provided from Genomics API and implements
 * HTSJDK's SAMRecordIterator.
 * Iterates over data returned from the API and when needed
 * re-queries the API for more data.
 * Since the API always return *overalpping* reads and SAMRecordIterator
 * supports contained and start-at queries, this class filters reads
 * returned from the API to make sure they conform to the requested intervals.
 */
public class GA4GHSamRecordIterator implements SAMRecordIterator{
  private static final Logger LOG = Logger.getLogger(GA4GHSamRecordIterator.class.getName());
  
  Iterator<SAMRecord> iterator;
  GenomicsApiDataSource dataSource;
  GA4GHQueryInterval[] intervals;
  String readSetId;
  int intervalIndex = -1;
  boolean hasNext;
  SAMRecord nextRead;
  SAMFileHeader header;
  
  public GA4GHSamRecordIterator(GenomicsApiDataSource dataSource,
      String readSetId,
      GA4GHQueryInterval[] intervals) {
    this.dataSource = dataSource;
    this.readSetId = readSetId;
    this.intervals = intervals;
    seekMatchingRead();
  }
  
  /** Returns true when we truly reached the end of all requested data */
  boolean isAtEnd() {
    return intervals == null || intervals.length == 0 ||  
        intervalIndex >= intervals.length;
  }
  
  /** Returns the current interval being processed or null if we have reached the end */
  GA4GHQueryInterval currentInterval() {
    if (isAtEnd()) {
      return null;
    }
    return intervals[intervalIndex];
  }
  
  /** Re-queries the API for the next interval */
  ReadIteratorResource queryNextInterval() {
    if (!isAtEnd()) {
      intervalIndex++;
    }
    if (isAtEnd()) {
      return null;
    }
    return queryForInterval(currentInterval());
  }
  
  /** Queries the API for an interval and returns the iterator resource, or null if failed */
  ReadIteratorResource queryForInterval(GA4GHQueryInterval interval) {
    try {
      return dataSource.getReadsFromGenomicsApi(readSetId, interval.getSequence(),
          interval.getStart(), interval.getEnd());
    } catch (Exception ex) {
      LOG.warning("Error getting data for interval " + ex.toString());
    }
    return null;
  }
  
  /**
   * Ensures next returned read will match the currently requested interval.
   * Since the API always returns overlapping reads we might need to skip some
   * reads if the interval asks for "included" or "starts at" types.
   * Also deals with the case of iterator being at an end and needing to query
   * for the next interval.
   */
  void seekMatchingRead()  {
    while (!isAtEnd()) {
      if (iterator == null || !iterator.hasNext()) {
        LOG.info("Getting next interval from the API");
        // We have hit an end (or this is first time) so we need to go fish
        // to the API.
        ReadIteratorResource resource = queryNextInterval();
        if (resource != null) {
          LOG.info("Got next interval from the API");
          header = resource.getSAMFileHeader();
          iterator = resource.getSAMRecordIterable().iterator();
        } else {
          LOG.info("Failed to get next interval from the API");
          header = null;
          iterator = null;
        }
      } else {
        nextRead = iterator.next();
        if (currentInterval().matches(nextRead)) {
          return; // Happy case, otherwise we keep spinning in the loop.
        } else {
          LOG.info("Skipping non matching read");
        }
      }
    }
  }
 
  
  @Override
  public void close() {
    this.iterator = null;
    this.dataSource = null;
    this.intervalIndex = intervals.length;
  }

  @Override
  public boolean hasNext() {
    return !isAtEnd();
  }

  @Override
  public SAMRecord next() {
    SAMRecord retVal = nextRead;
    seekMatchingRead();
    return retVal;
  }

  @Override
  public void remove() {
    // Not implemented
  }

  @Override
  public SAMRecordIterator assertSorted(SortOrder sortOrder) {
    // TODO(iliat): implement this properly. This code never checks anything.
    return this;
  }
  
  public SAMFileHeader getFileHeader() {
    return header;
  }
}
