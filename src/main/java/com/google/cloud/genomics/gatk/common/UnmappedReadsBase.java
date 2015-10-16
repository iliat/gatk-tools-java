package com.google.cloud.genomics.gatk.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Base class for UnmappedReads implementation.
 * Most of the logic is models specific and so is housed in Api and gRPC 
 * specific descendants of this class.
 */
public abstract class UnmappedReadsBase<Read> implements UnmappedReads<Read> {
  protected static final Logger LOG = Logger.getLogger(UnmappedReads.class.getName());
  
  /**
   * Maximum number of reads we are prepared to keep in memory.
   * If this number is exceeded, we will switch to the mode of ignoring
   * unmapped mate pairs.
   */
  protected static final long MAX_READS = 100000000;
  
  @Override
  public long getReadCount() {
    return readCount;
  }
  
  protected Map<String, Map<String, ArrayList<Read>>> unmappedReads = 
      new HashMap<String, Map<String, ArrayList<Read>>>();
  
  protected long readCount = 0;
}
