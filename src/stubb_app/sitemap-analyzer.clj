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
        (let [{:keys [body status headers error opts]} @response
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

(defn persist-results
  [results xml-file]
  (let [file-name (-> xml-file bean :parentFile bean :name) 
        _ (io/make-parents @results-directory file-name)
        out-file (io/file @results-directory (str file-name ".edn"))]
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
        curr-time (System/currentTimeMillis)]
    (println "  Processing sub-map for" file-name "..." )    
    (persist-results 
     {:results (reduce conj #{} (doall (process-sitemap-urls urls)))} 
     xml-file)
    (println (str "    Finished processing sub-map for" file-name))
    (println (str "      Processed [" (count urls) "] urls"))
    (println (str "      Time to process: [" (- (System/currentTimeMillis) curr-time) "] milliseconds"))))

(defn extract-xml-files
  [sitemap-directory]
  (filter (fn [f]
            (let [{is-file? :file 
                   file-name :name} (bean f)]
              (and is-file?
                   (not= "sitemap.xml" file-name)
                   (str/ends-with? file-name ".xml"))))
          sitemap-directory))

(defn get-date-string
  []
  (let [time (bean (java.time.LocalDate/now))
        {month :monthValue  day :dayOfMonth year :year} time]
    (str month "-" day "-" year)))

(defn init
  [site-name]
  (let [directory (io/file (str results-base-directory "/" site-name) (get-date-string))]
    (reset! results-directory directory)
    (println "The results for this sitemap will be saved in: " (-> directory bean :canonicalPath)))
  (reset! num-tests-run 0)
  (reset! num-failures 0))

(defn process-sitemap-directory
  [path-to-sitemap]
  (let [sitemap-directory (file-seq (io/file path-to-sitemap))
        site-name (-> sitemap-directory first bean :name)
        xml-files (extract-xml-files sitemap-directory)
        currTime (System/currentTimeMillis)]
    (init site-name)
    (println "Processing Sitemap ...")
    (doall (map process-xml-sitemap xml-files))
    (println "")
    (println "Sitemap Processed ")
    (println (str "Total elapsed time " (- (System/currentTimeMillis) currTime) " milliseconds"))))
