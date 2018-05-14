## Sitemap Analzyer

After creating sitemaps we need a tool to go through and check each individual url within the map to see if it is a valid url. It is much better to blacklist a url from the sitemap (not include it in the first place) than to publish bad urls. This tool will take a sitemap, make http calls to each url within the map, and then reports results from those http calls. The report will include some summary statists, but also a list of all urls that failed and the reason why it failed.

The Sitemap Analyzer has two independent parts:

* sitemap-analyzer.clj
* sitemap-tester.clj

Todo: These names are not very descriptive of that they do. A more appropriate name for the sitemap-analyzer may be sitemap-processor since this set of functions is basically making https calls and storing the results of those calls. The sitemap-tester.clj would be better named 'sitemap-analyzer.clj' since it is actually taking the results, checking for failures, and reporting those results.

Todo: We are not sure how this tooling will be deployed or even exactly how it will be used. Since we do not know these specifics, many elements of the design where left in a state of that requires manual inspection and manipulation of the source (i.e. setting partition size, and results directory names in global vars). These params should be able to be set by the user via a cli for example.

### sitemap-analyzer.clj

The sitemap-analyzer.clj provides a function  (process-sitemap-directory <path-to-sitemap>) that takes one argument: a path that points to a saved sitemap directory.  The path will be used to in a call to (file-seq (clojure.java.io/file path-to-sitemap) which will attempt to coerce its argument into a collection of files that represent the sitemap. 

The sitemap-analyzer will then attempt to filter out all files except for the xml files that contain the site urls. This collection of xml files is then processed one-by-one. In order to impose some throttling for the http requests we partition the urls for each xml file. Currently this partition size is set by a var partition-size in the sitemap-analyzer.clj. If we send requests too frequently then we will be blocked by our cloud provider's load balancer (or something like that).

Once calling (process-sitemap-directory <path-to-sitemap>) with a path to a valid sitemap then the analyzer will begin processing the sitemap. The process should be documneted to the user via std out. Lately, the sitemap analysis takes about an hour with partition sizes of 100 urls each.

After the sitemap has been processed, a new directory should be created. Where this directory is created is based on the var 'results-base-directory'. Currently it is set to 'sitemap_results' which means a new directory intitled 'sitemap_results' will created in the root directory of the project that this code lives in. This needs to be updated and is not ideal, but works at this time.

The results directory should have a structure similar to:
```
 sitemap_results/ 
 |--- <sitemap-name>/
 |    |---  <todays-date>/    
 |    |     |--- <sitemap-results>.edn
 |    |     |--- <other-sitemap-results>.edn
 |    |     .          
 |    |     .
 |    |     .
```
Several results should be able to be saved into the same directory since the results are stored in directories named by date.

After the results are stored as .edn we can now move one to the second part.

### sitemap-tester.clj

The sitemap-tester is responsible for analyzing and reporting desired results from a processed sitemap (once again, the naming could be better). 

The sitemap-tester can be started by calling (analyze-sitemap <path-to-sitemap>) where the param <path-to-sitemap> is a string url that points to the directory where processed sitemap results are stored. The url needs to be a path to the folder containing the .edn files: i.e. "sitemap_results/<sitemap_name>/<todays-date>/"

The sitemap-tester will then analyze the results and create a new folder in the same directory called "analysis". The analysis directory will have a structure of:
```
 analysis/
 |--- summary.edn
 |--- failing_urls/
 |    |--- no-index-failures.edn
 |    |--- non-200-failures.edn
```
The summary.edn will contain summary statistics: number of tests run, number of test failures, and failures by type (no-index or non-200 response).

The failing_urls directory will contain actual failing urls for the no-index failure and non-200-response failures      


