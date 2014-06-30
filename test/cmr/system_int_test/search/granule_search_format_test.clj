(ns cmr.system-int-test.search.granule-search-format-test
  "Integration tests for searching granules in csv format"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.url-helper :as url]
            [clj-http.client :as client]
            [cmr.umm.core :as umm]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1"))

(comment
(ingest/reset)
(ingest/create-provider "CMR_PROV1")
)

(deftest search-granules-in-xml-metadata
  ;; TODO we can add additional formats here later such as iso
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection))
        coll2 (d/ingest "CMR_PROV1" (dc/collection))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "g1"}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll2 {:granule-ur "g2"}))
        all-granules [gran1 gran2]]
    (index/refresh-elastic-index)
    (testing "echo10"
      (d/assert-metadata-results-match
        :echo10 all-granules
        (search/find-metadata :granule :echo10 {}))
      (d/assert-metadata-results-match
        :echo10 [gran1]
        (search/find-metadata :granule :echo10 {:granule-ur "g1"})))

    (testing "invalid format"
      (is (= {:errors ["The mime type [application/echo11+xml] is not supported."],
              :status 400}
             (search/get-search-failure-data
               (search/find-concepts-in-format
                 "application/echo11+xml" :granule {})))))

    (testing "reference XML"
      (let [refs (search/find-refs :granule {:granule-ur "g1"})
            location (:location (first (:refs refs)))]

        (is (d/refs-match? [gran1] refs))
        (testing "Location allows granule native format retrieval"
          (let [response (client/get location
                                     {:accept :xml
                                      :connection-manager (url/conn-mgr)})]
            (is (= (umm/umm->xml gran1 :echo10) (:body response)))))))))


(deftest search-granule-csv
    (let [ru1 (dc/related-url "GET DATA" "http://example.com")
          coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
          coll2 (d/ingest "CMR_PROV1" (dc/collection {}))
          gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule1"
                                                         :beginning-date-time "2010-01-01T12:00:00Z"
                                                         :ending-date-time "2010-01-11T12:00:00Z"
                                                         :producer-gran-id "Granule #1"
                                                         :day-night "DAY"
                                                         :size 100
                                                         :cloud-cover 50
                                                         :related-urls [ru1]}))
          gran2 (d/ingest "CMR_PROV1" (dg/granule coll2 {:granule-ur "Granule2"
                                                         :beginning-date-time "2011-01-01T12:00:00Z"
                                                         :ending-date-time "2011-01-11T12:00:00Z"
                                                         :producer-gran-id "Granule #2"
                                                         :day-night "NIGHT"
                                                         :size 80
                                                         :cloud-cover 30}))]

      (index/refresh-elastic-index)

      (testing "search granule in csv format."
        (let [response (search/find-grans-csv :granule {:granule-ur "Granule1"})]
          (is (= 200 (:status response)))
          (is (= (str "Granule UR,Producer Granule ID,Start Time,End Time,Online Access URLs,Browse URLs,Cloud Cover,Day/Night,Size\n"
                      "Granule1,Granule #1,2010-01-01T12:00:00Z,2010-01-11T12:00:00Z,http://example.com,50.0,DAY,100.0\n")
                 (:body response))))
        (let [response (search/find-grans-csv :granule {})]
          (is (= 200 (:status response)))
          (is (= (str "Granule UR,Producer Granule ID,Start Time,End Time,Online Access URLs,Browse URLs,Cloud Cover,Day/Night,Size\n"
                      "Granule1,Granule #1,2010-01-01T12:00:00Z,2010-01-11T12:00:00Z,http://example.com,50.0,DAY,100.0\n"
                      "Granule2,Granule #2,2011-01-01T12:00:00Z,2011-01-11T12:00:00Z,,30.0,NIGHT,80.0\n")
                 (:body response)))))))

