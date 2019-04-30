(ns lemondronor.socialaircraft.acinfo
  (:require
   [cljs-http.client :as http]
   [cljs.core.async :refer [chan <! put! go close!]]
   [lemondronor.socialaircraft.util :as util]
   ["puppeteer" :as puppeteer])
  (:require-macros [lemondronor.socialaircraft.logging :as logging]))

(logging/deflog "acinfo" logger)


(defn get-profile-photo-airport-data& [icao reg]
  (info "Making airport-data.com request for icao:%s reg:%s" icao reg)
  (let [ch (chan)]
    (go
      (let [params (cond-> {:n 1}
                     icao (merge {:m icao})
                     reg (merge {:r reg}))
            response (<! (http/get "http://www.airport-data.com/api/ac_thumb.json"
                                   {:query-params params}))
            thumbnail-url (get-in response [:body :data 0 :image])]
        (info "airport-data.com response %s" response)
        (if (and (= (:status response) 200) thumbnail-url)
          (put! ch {:thumbnail-url thumbnail-url})
          (close! ch))))
    ch))
