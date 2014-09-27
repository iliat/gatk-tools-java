package com.google.cloud.genomics.gatk.htsjdk;

import htsjdk.samtools.CustomReaderFactory;
import htsjdk.samtools.SamReader;

import java.net.URL;
import java.util.logging.Logger;
/**
 * HTSJDK CustomReaderFactory implementation.
 * Returns a SamReader that reads data from GA4GH API.
 */
public class GA4GHReaderFactory implements CustomReaderFactory.ICustomReaderFactory {
  private static final Logger LOG = Logger.getLogger(GA4GHReaderFactory.class.getName());
  
  @Override
  public SamReader open(URL url) {
    try {
      return new GA4GHSamReader(url);
    } catch (Exception ex) {
      LOG.warning("Error creating SamReader " + ex.toString());
      return null;
    }
  }

}
