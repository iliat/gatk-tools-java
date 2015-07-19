package com.google.cloud.genomics.gatk.common.grpc;

import com.google.cloud.genomics.gatk.common.UnmappedReadsBase;
import com.google.genomics.v1.Position;
import com.google.genomics.v1.Read;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Genomics GRPC Api based implementation.
 */
public class UnmappedReads extends UnmappedReadsBase<Read> { 
  @Override
  public boolean isUnmappedMateOfMappedRead(Read read) {
    final boolean paired = read.getNumberReads() >= 2;
    if (!paired) {
      return false;
    }
    final boolean unmapped = (read.getAlignment() == null || 
        read.getAlignment().getPosition() == null || 
        read.getAlignment().getPosition().getPosition() == 0);
    if (!unmapped) {
      return false;
    }
    final Position matePosition = read.getNextMatePosition();
    if  (matePosition == null) {
      return false;
    }
    if (read.getFragmentName() == null) {
      return false;
    }
    if (matePosition.getReferenceName() != null && matePosition.getPosition() != 0) {
      return true;
    }
    return false;
  }
  
  @Override
  public boolean isMappedMateOfUnmappedRead(Read read) {
    return read.getNumberReads() > 0 && 
        (read.getNextMatePosition() == null || 
         read.getNextMatePosition().getPosition() == 0);
  }
  
  /**
   * Checks and adds the read if we need to remember it for injection.
   * Returns true if the read was added.
   */
  @Override
  public boolean maybeAddRead(Read read) {
    if (!isUnmappedMateOfMappedRead(read)) {
      return false;
    }
    final String reference = read.getNextMatePosition().getReferenceName();
    String key = getReadKey(read);
    Map<String, ArrayList<Read>> reads = unmappedReads.get(reference);
    if (reads == null) {
      reads = new HashMap<String, ArrayList<Read>>();
      unmappedReads.put(reference, reads);
    }
    ArrayList<Read> mates = reads.get(key);
    if (mates == null) {
      mates = new ArrayList<Read>();
      reads.put(key, mates);
    }
    if (getReadCount() < MAX_READS) {
      mates.add(read);
      readCount++;
      return true;
    } else {
      LOG.warning("Reached the limit of in-memory unmapped mates for injection.");
    }
    return false;
  }
  
  /**
   * Checks if the passed read has unmapped mates that need to be injected and
   * if so - returns them. The returned list is sorted by read number to
   * handle the case of multi-read fragments.
   */
  @Override
  public ArrayList<Read> getUnmappedMates(Read read) {
    if (read.getNumberReads() < 2 ||
        (read.getNextMatePosition() != null && 
        read.getNextMatePosition().getPosition() != 0) ||
        read.getAlignment() == null ||
        read.getAlignment().getPosition() == null ||
        read.getAlignment().getPosition().getReferenceName() == null ||
        read.getFragmentName() == null) {
      return null;
    }
    final String reference = read.getAlignment().getPosition().getReferenceName();
    final String key = getReadKey(read);
    
    Map<String, ArrayList<Read>> reads = unmappedReads.get(reference);
    if (reads != null) {
      final ArrayList<Read> mates = reads.get(key);
      if (mates != null && mates.size() > 1) {
        Collections.sort(mates, matesComparator);
      }
      return mates;
    }
    return null;
  }
  
  private static String getReadKey(Read read) {
    return read.getFragmentName();
  }
  
  private static Comparator<Read> matesComparator = new Comparator<Read>() {
    @Override
    public int compare(Read r1, Read r2) {
        return r1.getReadNumber() - r2.getReadNumber();
    }
  };
}
