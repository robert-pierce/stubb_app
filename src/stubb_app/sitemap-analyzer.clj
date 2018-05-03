(ns stubb_app.sitemap_analyzer
  (:require [org.httpkit.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hickory.core :as html]
            [hickory.select :as hs]
            [clojure.data.xml :as xml]
            [clojure.data.json :as json]
            [clojure.test :as t]))

(def num-tests-run (atom 0))
(def num-failures (atom 0))
(def results-directory (atom nil))
(def partition-size 100)
(def results-base-directory "sitemap_results")


(defn inspect-dom-for-noindex
  [root]
  (let [selection (hs/select (hs/and (hs/tag :meta) (hs/attr :name #{"robots"}))
                             root)]
    (some-> selection
            first
            :attrs
            :content
            (str/split #",")
            (->> (into #{} (map str/trim))))))

(defn process-submap-partition
  [urls]
  (let [futures (doall (map (juxt identity http/get) urls))]
    (for [[url response] futures]
      (do 
        (let [ {:keys [body status headers error opts]} @response
              err (if (nil? error) "{}" (:message (bean error)))]
          (if (empty? body)
            {:url url
             :status status
             :error err
             :robots-meta #{}}
            {:url url
             :status status
             :error err
             :robots-meta (inspect-dom-for-noindex (html/as-hickory (html/parse body)))}))))))

(defn process-sitemap-urls
  [urls]
  (let [url-partitions (partition-all partition-size urls)]
    (flatten (reduce (fn [accum partition]
                       (conj accum (doall (process-submap-partition partition))))
                     () url-partitions))))

(defn extract-xml-files
  [sitemap-directory]
  (filter (fn [f]
            (let [{is-file? :file 
                   file-name :name} (bean f)]
              (and is-file?
                   (not= "sitemap.xml" file-name)
                   (str/ends-with? file-name ".xml"))))
          sitemap-directory))

(defn update-error-stats
  [err-msg]
  (if (contains? failures err-msg)
    (assoc failures err-msg (inc (get failures err-msg)))
    (assoc failures err-msg 1)))

(defn report-builder
  [result]
  (t/with-test-out
    (let [{:keys [type expected actual message]} result
          url (first t/*testing-contexts*)]
      (if (= type :fail)
        (do
          (swap! num-failures inc)
          (update-error-stats message)
          (println  (str "  " url))
          (println "    Failure reason: " message)
          ;; these counters are here only for debug and dev purposes, will print this at the end of all results
          ;; (println "      Total Tests run [" @num-tests-run "] Total failures [" @num-failures "]")
          )))))


(defn report-results
  [result]
  (with-redefs [t/report report-builder]
    (let [{:keys [status error url robots-meta]} result]
      (swap! num-tests-run inc)
      (t/testing url
        (t/is (= 200 status) (str "Non-200 Status: " (if (nil? status) error status)))
        (t/is (not (contains? robots-meta "noindex")) "Contains Robots-NoIndex")))))

(defn get-date-string
  []
  (let [time (bean (java.time.LocalDate/now))
        {month :monthValue  day :dayOfMonth year :year} time]
    (str month "-" day "-" year)))

(defn persist-results
  [results xml-file]
  (let [file-name (-> xml-file bean :parentFile bean :name) 
        _ (io/make-parents @results-directory file-name)
        out-file (io/file @results-directory (str file-name ".edn"))]
    (println (bean out-file))
    (with-open [w (io/writer out-file)]
      (binding [*out* w]
        (pr results)))))

(defn process-xml-sitemap
  [xml-file]
  (let [urls (->> (xml/parse (io/reader xml-file))
                  :content
                  (mapcat :content)
                  (mapcat :content))
        file-name (-> xml-file
                      bean
                      :parentFile
                      bean
                      :name)
        curr-num-tests-run  @num-tests-run
        curr-num-failures @num-failures]
    (println "Processing sub-map for" file-name " ..." )    
    ;;(doall (map report-results (process-sitemap-urls urls)))
    (persist-results {:results (reduce conj #{}  (doall (process-sitemap-urls urls)))} xml-file)
    ;; (println (str "Total tests run in " file-name " ["(- @num-tests-run curr-num-tests-run)
                  ;; "]. Total failures in " file-name " [" (- @num-failures curr-num-failures) "]"))
    ))

(defn init
  []
  (reset! results-directory (io/file results-base-directory (get-date-string)))
  (reset! num-tests-run 0)
  (reset! num-failures 0))

(defn process-sitemap-directory
  [path-to-sitemap]
  (let [sitemap-directory (file-seq (io/file path-to-sitemap))
        xml-files (extract-xml-files sitemap-directory)
        currTime (System/currentTimeMillis)]
    (init)
    (println "Processing Sitemap ...")
    (doall (map process-xml-sitemap xml-files))
    (println "")
    (println "Sitemap analysis complete.")
    (println (str "Total number of test run [" @num-tests-run "]. Total number of failures [" @num-failures "]."))
    (println (str "Total elapsed time " (- (System/currentTimeMillis) currTime)))))
