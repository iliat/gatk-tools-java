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

import java.util.HashMap;
import java.util.Map;

/**
 * Creates GenomicsApiDataSource objects, one per each root url
 * (e.g. https://www.googleapis.com/genomics/v1beta2).
 * Allows configuring settings such as client secrets file on a per 
 * root url basis.
 * This class is abstract and is later specialized for API vs. GRPC.
 */
public abstract class GenomicsDataSourceFactory<Read, ReadGroupSet, Reference> {
  /**
   * Settings required for initializing GenomicsApiDataSource
   */
  public static class Settings {
    public Settings() {
      clientSecretsFile = "";
      noLocalServer = false;
    }
    public Settings(String clientSecretsFile, String apiKey, boolean noLocalServer) {
      this.clientSecretsFile = clientSecretsFile;
      this.apiKey = apiKey;
      this.noLocalServer = noLocalServer;
    }
    public String clientSecretsFile;
    public String apiKey;
    public boolean noLocalServer;
  }
  
  /**
   * A pair of settings and the corresponding initialized data source.
   */
  private static class Data<Read, ReadGroupSet, Reference> {
    public Data(Settings settings, GenomicsDataSource<Read, ReadGroupSet, Reference> dataSource) {
      this.settings = settings;
      this.dataSource = dataSource;
    }
    public Settings settings;
    public GenomicsDataSource<Read, ReadGroupSet, Reference> dataSource;
  }
  
  private Map<String, Data<Read, ReadGroupSet, Reference>> dataSources = 
      new HashMap<String, Data<Read, ReadGroupSet, Reference>>();
  
  /**
   * Sets the settings for a given root url, that will be used for creating
   * the data source. Has no effect if the data source has already been created.
   */
  public void configure(String rootUrl, Settings settings) {
    Data<Read, ReadGroupSet, Reference> data = dataSources.get(rootUrl);
    if (data == null) {
      data = new Data<Read, ReadGroupSet, Reference>(settings, null);
      dataSources.put(rootUrl, data);
    } else {
      data.settings = settings;
    }
  }
 
  /**
   * Lazily creates and returns the data source for a given root url.
   */
  public GenomicsDataSource<Read, ReadGroupSet, Reference> get(String rootUrl) {
    Data<Read, ReadGroupSet, Reference> data = dataSources.get(rootUrl);
    if (data == null) {
      data = new Data<Read, ReadGroupSet, Reference>(new Settings(), null);
      dataSources.put(rootUrl, data);
    }
    if (data.dataSource == null) {
      data.dataSource = makeDataSource(rootUrl, data.settings);
    }
    return data.dataSource;
  }
  
  protected abstract GenomicsDataSource<Read, ReadGroupSet, Reference> makeDataSource(
      String rootUrl, Settings settings);
}
