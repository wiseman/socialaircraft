(ns lemondronor.socialaircraft.db
  (:require [cljs.core.async :as async]
            [clojure.string :as string]
            [camel-snake-kebab.core :as csk]
            [goog.object :as gobject]
            [lemondronor.socialaircraft.util :as util]
            ["fs" :as fs]
            ["sqlite3" :as sqlite3]))

(defonce db_ (atom nil))

(def db-path "socialaircraft.db")
(def db-conn (sqlite3/Database. db-path))

(def setup-sql "
  CREATE TABLE aircraft (
    icao TEXT NOT NULL PRIMARY KEY,
    last_post_time TEXT
  );
  ")

(defn db-exists? []
  (fs/existsSync db-path))

(defn init-db []
  (println "Initializing database...")
  (.serialize
   db-conn
   (fn []
     (.run db-conn "DROP TABLE IF EXISTS aircraft")
     (.run db-conn
           setup-sql
           (fn []
             (println "Database initialized.")
             (.close db-conn))))))

(defn in-operator-helper [values]
  (str "(" (string/join ", " (repeat (count values) "?")) ")"))


(defn parse-record [rec]
  (reduce (fn [m k]
            (assoc m (-> k csk/->kebab-case keyword) (gobject/get rec k)))
          {}
          (js-keys rec)))

(def serialize-record [rec]


(defn load-all-aircraft& []
  (println "Loading aircraft from database...")
  (let [chan (async/chan)
        sql "select * from aircraft"
        cb (fn [err rows]
             (when err
               (println "Error in get-all-aircraft:" err))
             (println "Loaded" (count rows) "aircraft from database.")
             (let [result (util/index-by :icao (map parse-record rows))]
               (async/put! chan result)))]
    (.all db-conn sql cb)
    chan))

(defn get-all-aircraft& []
  (if-let [db @db_]
    (let [chan (async/chan)]
      (println "Using cache")
      (async/put! chan db)
      chan)
    (load-all-aircraft&)))


  (.run
   db-conn
   (str "INSERT INTO aircraft (icao, last_post_time) "
        "VALUES (?, ?) "
        "ON CONFLICT (icao) "
        "DO UPDATE SET last_post_time = excluded.last_post_time")
   (:icao post)
   (.toISOString (js/Date.))))

(defn record-post [post]
  (let [icao (:icao post)
        new-rec {:icao icao :last-post-time (js/Date.)}]
    (if (@db_ icao)
      (insert-record new-rec)
      (update-record new-rec))
    (swap! db_ assoc icao new-rec)))
