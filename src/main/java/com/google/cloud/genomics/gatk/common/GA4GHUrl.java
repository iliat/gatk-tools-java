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

import java.net.URISyntaxException;

/**
 * Represents a GA4GH reads resource as a URL in the form of
 * ga4gh://<base api path>/reads/<dataset>/<readset>/<sequence>/[start-end],
 * e.g. ga4gh://www.googleapis.com/genomics/v1beta/reads/16801540936334623823/CLqN8Z3sDRDQldHJ_rTS9VE/1/
 */
public class GA4GHUrl {
  public GA4GHUrl(String rootUrl,
      String dataset,
      String readset,
      String sequence,
      int rangeStart,
      int rangeEnd) {
    super();
    this.rootUrl = rootUrl;
    this.dataset = dataset;
    this.readset = readset;
    this.sequence = sequence;
    this.rangeStart = rangeStart;
    this.rangeEnd = rangeEnd;
  }

  int rangeStart = 0;
  int rangeEnd = 0;
  String rootUrl = "";
  String dataset = "";
  String readset = "";
  String sequence = "";
  
  private static String READS_PATH_COMPONENT = "/reads/";
  private static String GA4GH_SCHEMA_PREFIX = "ga4gh://";
  
  public static boolean isGA4GHUrl(String url) {
    return url.toLowerCase().startsWith(GA4GH_SCHEMA_PREFIX);
  }
  
  public GA4GHUrl() {
    
  }
  
  public GA4GHUrl(String input) throws URISyntaxException {
    if (!isGA4GHUrl(input)) {
      throw new URISyntaxException(input, "Schema is not ga4gh");
    }
    int pos = input.indexOf(READS_PATH_COMPONENT);
    if (pos < 0) {
      throw new URISyntaxException(input, "Can not find /reads/ path componentt");
    }
    rootUrl = input.substring(0, pos).replace(GA4GH_SCHEMA_PREFIX, "https://");
    String readsPath = input.substring(pos);
    String[] pathComponents = readsPath.split("/");
    if (pathComponents.length < 5) {
      throw new URISyntaxException(input,
          "Expecting /reads/dataset/readset/sequence/[range], got " + 
          readsPath);
    }
    dataset = pathComponents[2];
    readset = pathComponents[3];
    sequence = pathComponents[4];
    
    if (pathComponents.length > 5) {
      String [] range = pathComponents[5].split("-");
      if (range.length == 2) {
        rangeStart = Integer.parseInt(range[0]);
        rangeEnd = Integer.parseInt(range[1]);
      } else {
        throw new URISyntaxException(input,
            "Expecting last component to be <start>-<end>, got " + 
                readsPath);
      }
    }
  }

  /**
   * @return the rangeStart
   */
  public int getRangeStart() {
    return rangeStart;
  }

  /**
   * @param rangeStart the rangeStart to set
   */
  public void setRangeStart(int rangeStart) {
    this.rangeStart = rangeStart;
  }

  /**
   * @return the rangeEnd
   */
  public int getRangeEnd() {
    return rangeEnd;
  }

  /**
   * @param rangeEnd the rangeEnd to set
   */
  public void setRangeEnd(int rangeEnd) {
    this.rangeEnd = rangeEnd;
  }

  /**
   * @return the rootUrl
   */
  public String getRootUrl() {
    return rootUrl;
  }

  /**
   * @param rootUrl the rootUrl to set
   */
  public void setRootUrl(String rootUrl) {
    this.rootUrl = rootUrl;
  }

  /**
   * @return the dataset
   */
  public String getDataset() {
    return dataset;
  }

  /**
   * @param dataset the dataset to set
   */
  public void setDataset(String dataset) {
    this.dataset = dataset;
  }

  /**
   * @return the readset
   */
  public String getReadset() {
    return readset;
  }

  /**
   * @param readset the readset to set
   */
  public void setReadset(String readset) {
    this.readset = readset;
  }

  /**
   * @return the sequence
   */
  public String getSequence() {
    return sequence;
  }

  /**
   * @param sequence the sequence to set
   */
  public void setSequence(String sequence) {
    this.sequence = sequence;
  }
}
