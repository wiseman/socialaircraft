(ns lemondronor.socialaircraft.db
  (:require
   [camel-snake-kebab.core :as csk]
   [cljs.core.async :as async]
   [clojure.string :as string]
   [com.stuartsierra.component :as component]
   [goog.object :as gobject]
   [lemondronor.socialaircraft.util :as util]
   ["fs" :as fs]
   ["sqlite3" :as sqlite3]))


(def ^:private setup-sql "
  CREATE TABLE aircraft (
    icao TEXT NOT NULL PRIMARY KEY,
    last_post_time TEXT
  );
  CREATE TABLE authentication (
    username TEXT NOT NULL PRIMARY KEY,
    password TEXT,
    access_token TEXT
  ")

(defn ^:private init-db [db-conn]
  (println "Initializing database...")
  (.serialize
   db-conn
   (fn []
     (.run db-conn "DROP TABLE IF EXISTS aircraft")
     (.run db-conn "DROP TABLE IF EXISTS authentication")
     (.run db-conn
           setup-sql
           (fn []
             (println "Database initialized.")
             (.close db-conn))))))


;; Turns this:
;;
;; {:icao "DEADBF" :last_post_time "2019-04-15T22:17:41.778-00:00"}
;;
;; into this:
;;
;; {:icao "DEADBF" :last-post-time #inst "2019-04-15T22:17:41.778-00:00"}

(defn- parse-record [rec]
  (let [new-rec (reduce (fn [m k]
                          (assoc m (-> k csk/->kebab-case keyword) (gobject/get rec k)))
                        {}
                        (js-keys rec))]
    (if (:last-post-time new-rec)
      (assoc new-rec :last-post-time (js/Date. (:last-post-time new-rec)))
      new-rec)))


;; Given a list (a, b, c, ...), returns a string "(?, ?, ?, ...)". For
;; use with SQL queries using the IN operator.

(defn- in-operator-helper [values]
  (str "(" (string/join ", " (repeat (count values) "?")) ")"))


(defn get-aircraft&
  "Fetches DB records for the specified aircraft.

  Given a collection of ICAOs, returns a channel that will be sent the
  collection of corresponding records."
  [database icaos]
  (println "Looking for" (count icaos) "aircraft in database")
  (let [{:keys [conn]} database
        sql (str "SELECT * from aircraft where icao in "
                 (in-operator-helper icaos))
        chan (async/chan)]
    (apply (.bind (.-all conn) conn)
           sql
           (concat icaos
                   (list
                    (fn [err rows]
                      (when err
                        (println "Error in get-aircraft&:" err))
                      (println "Loaded" (count rows) "aircraft from database")
                      (async/put! chan (util/index-by :icao (map parse-record rows)))))))
    chan))


(defn record-post
  "Records the fact that we made a post in the DB."
  [database icao]

  (let [{:keys [conn]} database]
    (.run
     conn
     (str "INSERT INTO aircraft (icao, last_post_time) "
          "VALUES (?, ?) "
          "ON CONFLICT (icao) "
          "DO UPDATE SET last_post_time = excluded.last_post_time")
     icao
     (.toISOString (js/Date.)))))


(defn record-social-access-token [database icao token]
  (let [{:keys [conn]} database]
    (.run
     conn
     (str "INSERT INTO aircraft (icao, social_access_token) "
          "VALUES (?, ?) "
          "ON CONFLICT (icao) "
          "DO UPDATE SET social_access_token = excluded.social_access_token")
     icao
     token)))


(defrecord Database [db-path
                     conn]
  component/Lifecycle
  (start [this]
    (if conn
      this
      (do
        ;; Create and initialize database if it doesn't exist.
        (when (not (fs/existsSync db-path))
          (let [db-conn (sqlite3/Database. db-path)]
            (.serialize
             db-conn
             #(init-db db-conn)
             #(.close db-conn))))
        (assoc this :conn (sqlite3/Database. db-path)))))
  (stop [this]
    (if conn
      (do (.close conn)
          (assoc this :conn nil)))))


(defn new-database [config]
  (let [db-path (get-in config [:database :path] "socialaircraft.db")]
    (map->Database {:db-path db-path})))
