package com.google.cloud.genomics.gatk.common;

import java.util.ArrayList;

/**
 * In-memory container for unmapped reads, so we can inject them
 * at the right positions to satisfy Picard tools expectations of the order,
 * which are violated by the current API implementation.
 * See https://github.com/ga4gh/schemas/issues/224
 * SAM format *best practice* (not requirement), states:
 * "For a unmapped paired-end or mate-pair read whose mate is mapped, the unmapped read should have RNAME and POS identical to its mate."
 * But the API returns pairs where an unmapped mate has no alignment
 * and references its mapped mate and so fails this condition.
 * We fix this by reading all unmapped reads and injecting them right after their mapped mates
 * as we iterate.
 * This is NOT feasible if the number of unmapped reads is very large.
 * We detect this condition and if that happens we will output such unmapped
 * reads with flags changed to make it look like they are not paired.
 * Since most of the tools do precious little with unmapped reads we hope
 * we can get away with this.
 */
public interface UnmappedReads<Read> {
  public boolean isUnmappedMateOfMappedRead(Read read);
  
  public boolean isMappedMateOfUnmappedRead(Read read);
  
  /**
   * Checks and adds the read if we need to remember it for injection.
   * Returns true if the read was added.
   */
  public boolean maybeAddRead(Read read);
  
  /**
   * Checks if the passed read has unmapped mates that need to be injected and
   * if so - returns them. The returned list is sorted by read number to
   * handle the case of multi-read fragments.
   */
  public ArrayList<Read> getUnmappedMates(Read read);
  
  public long getReadCount();
}
