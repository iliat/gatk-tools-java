/*
Copyright 2015 Google Inc. All rights reserved.

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
package com.google.cloud.genomics.gatk.common.grpc;


import com.google.cloud.genomics.gatk.common.GenomicsDataSourceFactory;
import com.google.genomics.v1.Read;
import com.google.genomics.v1.ReadGroupSet;
import com.google.genomics.v1.Reference;

/**
 * Genomics GRPC Api based implementation.
 */
public class GenomicsDataSourceFactoryGrpc
  extends GenomicsDataSourceFactory<Read, ReadGroupSet, Reference> {

  @Override
  protected GenomicsDataSource makeDataSource(
      String rootUrl, Settings settings) {
        return new GenomicsDataSource(rootUrl, settings.clientSecretsFile,
            settings.apiKey, settings.noLocalServer);
  }
}
