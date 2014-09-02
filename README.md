gatk-tools-java
===============
Tools for using Picard and GATK with Genomics API.

- Common classes for getting Reads from GA4GH Genomics API and
exposing them as SAMRecord "Iterable" resource.

- Ga4GHPicardRunner wrapper around Picard tools that allows for INPUTS into 
Picard tools to be ga4gh:// urls.

Build/Run:
No Maven setup yet, builds and runs in Eclipse.

Arguments for running:
--client_secrets_filename=<path to client_secrets.json> 
-path=<path to Picard tool jars>
-tool=ValidateSamFile.jar 
INPUT=ga4gh://www.googleapis.com/genomics/v1beta/reads/16801540936334623823/CLqN8Z3sDRCwgrmdkOXjn_sB/*/



