package com.google.cloud.genomics.gatk.htsjdk;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Example of HTSJDK SamReader and SamReaderFactory class usage.
 * Illustrates how to plug in a custom SamReaderFactory in order to consume
 * data from ga4gh urls.
 * 
 * To run this we need to specify the custom reader factory for HTSJDK and set
 * client_secrets file path for GenomGenomics API:
 * -Dsamjdk.custom_reader=https://www.googleapis.com/genomics,com.google.cloud.genomics.gatk.htsjdk.GA4GHReaderFactory 
 * -Dga4gh.client_secrets=<path to client_secrets.json>
 */
public class SamReaderExample {
  static String GA4GH_URL = 
      "https://www.googleapis.com/genomics/v1beta/readsets/CLqN8Z3sDRCwgrmdkOXjn_sB/*/";
  
  public static void main(String[] args) {  
    try {
      SamReaderFactory factory =  SamReaderFactory.makeDefault();
      
      // If it was a file, we would open like so:
      // factory.open(new File("~/testdata/htsjdk/samtools/uncompressed.sam"));
      // For API access we use SamInputResource constructed from a URL:
      SamReader reader = factory.open(SamInputResource.of(new URL(GA4GH_URL)));
      
      for (final SAMRecord samRecord : reader) {
        System.err.println(samRecord);
      }
    
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
  }
}
