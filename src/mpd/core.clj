(ns mpd.core
  (:require [aleph.tcp :as tcp]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [gloss.core :as gloss]
            [gloss.io :as io]
            [manifold.deferred :as d]
            [manifold.stream :as s]))

(def fr (gloss/compile-frame (gloss/string :utf-8 :delimiters ["\n"])))

(defn wrap-duplex-stream
  [frame s]
  (let [out (s/stream)]
    (s/connect
      (s/map #(io/encode frame %) out)
      s)
    (s/splice
      out
      (io/decode-stream s frame))))

(defn client
  [host port]
  (d/chain (tcp/client {:host host :port port})
           #(wrap-duplex-stream fr %)))

(defn connect
  [host port]
  (let [c @(client host port)
        msg @(s/take! c)]
    (if (clojure.string/starts-with? msg "OK MPD")
      c
      (do
        (.close c)
        (throw (Exception. (str "Unable to connect: " msg)))))))

(defn search
  [client search-type message]
  (s/put! client (str "search " search-type " \"" message "\""))
  (let [split-fn (fn [s] (-> s (clojure.string/split #": " 2) (update 0 ->kebab-case-keyword)))
        result   (s/stream)]
    (future
      (loop [[k v] (split-fn @(s/take! client))
             acc {}]
        (if (= :ok k)
          (do
            (if (seq acc) (s/put! result acc))
            (.close result))
          (if (= :file k)
            (do
              (if (seq acc) (s/put! result acc))
              (recur (split-fn @(s/take! client)) {k v}))
            (recur (split-fn @(s/take! client)) (assoc acc k v))))))
    result))

(defn clear
  [client]
  (s/put! client "clear")

  (let [result (s/stream)]
    (future
      @(s/take! client)
      (.close result))
    result))

(defn add
  [client uri]
  (s/put! client (str "add \"" uri "\""))

  (let [result (s/stream)]
    (future
      @(s/take! client)
      (.close result))
    result))

(defn play
  [client]
  (s/put! client "play")

  (let [result (s/stream)]
    (future
      @(s/take! client)
      (.close result))
    result))

(defn stop
  [client]
  (s/put! client "stop")

  (let [result (s/stream)]
    (future
      @(s/take! client)
      (.close result))
    result))

(comment

  (def c (connect "192.168.0.2" 6600))
  (def sr (search c "album" "highway 61 revisited bob dylan"))
  @(s/take! sr)
  (def sr (clear c))
  @(s/take! sr)
  (.close c)

  )
