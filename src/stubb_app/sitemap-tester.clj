(ns stubb-app.sitemap-analyzer-test
  (:require [clojure.test :as t]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def processed-sitemap-url "/Users/rpierce/Workspaces/Clojure_Workspace/stubb_app/sitemap_results/csi-rent/5-4-2018")
(def processed-sitemap-root (atom nil))
(def processed-sitemap (atom nil))
(def summary-data (atom {:tests-run 0 :failures {:no-index 0 :non-200 0}}))
(def failing-urls (atom {:no-index #{} :non-200 #{}}))

(defn read-sitemap-results
  [file]
  (with-open [r (java.io.PushbackReader. (io/reader file))]
    (binding [*read-eval* false]
      (edn/read r))))

(defn load-processed-sitemap
  []
  (let [sitemap-root (io/file processed-sitemap-url)
        edn-files (filter (fn [f]
                            (let [{is-file? :file 
                                   file-name :name} (bean f)]
                              (and is-file?
                                   (str/ends-with? file-name ".edn"))))
                          (file-seq sitemap-root))
        sitemap-results (map read-sitemap-results edn-files)]
    (reset! processed-sitemap-root sitemap-root)
    (reset! processed-sitemap sitemap-results)))

(defn report-builder
  [test-result]
  (t/with-test-out
    (let [{:keys [type expected actual message]} test-result
          results-count (first t/*testing-contexts*)]
      (println (str "the results count is: " results-count)))))

(defn process-url-test
  [url-test]
  (let [{:keys [url status error robots-meta]} url-test
        num-tests-run (get @summary-data :tests-run)
        num-no-index-failures (get-in @summary-data [:failures :no-index])
        num-non-200-failures (get-in @summary-data [:failures :non-200])
        failing-non-200-urls (get @failing-urls :non-200)
        failing-no-index-urls (get @failing-urls :no-index)]
    (swap! summary-data assoc :tests-run (inc num-tests-run))
    (if (not= 200 status)
      (do
        (swap! summary-data assoc-in [:failures :non-200] (inc num-non-200-failures))
        (swap! failing-urls assoc :non-200 (conj failing-non-200-urls url)))
      (if (contains? robots-meta "noindex")
        (do
          (swap! summary-data assoc-in [:failures :no-index] (inc num-no-index-failures))
          (swap! failing-urls assoc :no-index (conj failing-no-index-urls url)))))))

(defn process-submap
  [submap]
  (let [{:keys [meta results]} submap]
    ;(for [url-test results]
     ; (process-url-test url-test))
    (doall (map process-url-test results))
    (println "Finished processing submap <get-name-from-meta>")))

(defn write-failing-non-200-urls
  [out-file]
  (let [non-200-data {:failing-non-200-urls (get @failing-urls :non-200)}]
    (with-open [w (io/writer out-file)]
      (binding [*out* w]
        (pr non-200-data)))))

(defn write-failing-no-index-urls
  [out-file]
  (let [no-index-data {:failing-no-index-urls (get @failing-urls :no-index)}]
    (with-open [w (io/writer out-file)]
      (binding [*out* w]
        (pr no-index-data)))))

(defn write-summary-stats
  [out-file]
  (let [no-index-failures (get-in @summary-data [:failures :no-index])
        non-200-failures (get-in @summary-data [:failures :non-200])
        summary-data {:tests-run (get @summary-data :tests-run)
                      :test-failures (+ no-index-failures non-200-failures)
                      :failures-by-type {:no-index no-index-failures
                                         :non-200 non-200-failures}}]
    (with-open [w (io/writer out-file)]
      (binding [*out* w]
        (pr summary-data)))))

(defn persist-analysis-data
  []
  (let [analysis-directory (io/file @processed-sitemap-root "analysis")
        failing-url-directory (io/file analysis-directory "failing_urls")
        _ (io/make-parents failing-url-directory "no-index")
        summary-file (io/file analysis-directory "summary.edn")
        no-index-file (io/file failing-url-directory "no-index-failures.edn")
        non-200-file (io/file failing-url-directory "non-200-failures.edn")]
    (write-summary-stats summary-file)
    (write-failing-no-index-urls no-index-file)
    (write-failing-non-200-urls non-200-file)))

(defn analyze-sitemap
  []
  (load-processed-sitemap)
  (doall (map process-submap @processed-sitemap))
  (persist-analysis-data))
