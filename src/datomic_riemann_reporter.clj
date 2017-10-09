(ns datomic-riemann-reporter
  (:require [riemann.client :as riemann]
            [clojure.string :as s]
            [environ.core :as environ]))

(def ^:dynamic *client* nil)

(defn client []
  (if *client*
    *client*
    (if (and (environ/env :riemann-host) (environ/env :riemann-port))
      (let [initialized (riemann/tcp-client :host (environ/env :riemann-host) :port (Long/parseLong (environ/env :riemann-port)))]
        (println (str "event=starting-riemann-client host=" (environ/env :riemann-host) " port=" (environ/env :riemann-port) " tags=" (environ/env :riemann-extra-tags)))
        (alter-var-root #'*client* (constantly initialized))
        initialized)
      (do
        (println (str "warn=not-starting-riemann-client host=" (environ/env :riemann-host) " port=" (environ/env :riemann-port) " tags=" (environ/env :riemann-extra-tags)))
        nil))))

(defn add-additional-environment-tags [tags]
  (if-let [extra-tags (environ/env :riemann-extra-tags)]
    (into [] (concat tags (s/split extra-tags #",")))
    tags))

(defn send-event [event]
  (if-let [actual-client (client)]
    (riemann/send-event
      actual-client
      (merge event
             {:tags (add-additional-environment-tags ["datomic"])}))
    (prn (assoc event
                :riemann-reporter-error :no-client
                :riemann-reporter (client)
                :riemann-host (environ/env :riemann-host)
                :riemann-port (environ/env :riemann-port)))))

(defn report-datomic-metrics-to-riemann [metrics]
  (doseq [[metric-name value] metrics]
    (if (map? value)
      (doseq [[sub-metric-name sub-metric-value] value]
        (send-event
          {:service (str "datomic " (name metric-name) " " (name sub-metric-name))
           :metric sub-metric-value
           :state "ok"
           :ttl 600}))
      (send-event
        {:service (str "datomic " (name metric-name))
         :metric value
         :state "ok"
         :ttl 600}))))
