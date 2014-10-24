gatk-tools-java
===============
Tools for using Picard and GATK with Genomics API.

- Common classes for getting Reads from GA4GH Genomics API and
exposing them as SAMRecord "Iterable" resource. 
These will be used for subsequent work on enabling HTSJDK to use APIs data as 
input.

- Ga4GHPicardRunner wrapper around Picard tools that allows for INPUTS into 
Picard tools to be ga4gh:// urls.

Build/Run:
No Maven setup yet, builds and runs in Eclipse.

Arguments for running:
--client_secrets_filename=<path to client_secrets.json> 
-path=<path to Picard tool jars>
-tool=ValidateSamFile.jar 
INPUT=`ga4gh://www.googleapis.com/genomics/v1beta/readsets/<readset>/<sequence>/`
E.g. `ga4gh://www.googleapis.com/genomics/v1beta/readsets/CLqN8Z3sDRCwgrmdkOXjn_sB/*/`

Current limitations:
(These will be removed in subsequent versions)
- Supports only a single input so will not work for tools expecting 
multiple INPUT parameters.
- Supports only SAM format, not using any indexing (BAM/BAI) so may not be
suitable for more complex tools.
