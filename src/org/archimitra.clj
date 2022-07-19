(ns org.archimitra
  (:require [com.biffweb :as biff]
            [org.archimitra.feat.app :as app]
            [org.archimitra.feat.auth :as auth]
            [org.archimitra.feat.home :as home]
            [org.archimitra.feat.worker :as worker]
            [org.archimitra.schema :refer [malli-opts]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [ring.middleware.anti-forgery :as anti-forgery]
            [nrepl.cmdline :as nrepl-cmd]))

(def features
  [app/features
   auth/features
   home/features
   worker/features])

(def routes [["" {:middleware [anti-forgery/wrap-anti-forgery
                               biff/wrap-anti-forgery-websockets
                               biff/wrap-render-rum]}
              (map :routes features)]
             (map :api-routes features)])

(def handler (-> (biff/reitit-handler {:routes routes})
                 (biff/wrap-inner-defaults {})))

(defn on-tx [sys tx]
  (let [sys (biff/assoc-db sys)]
    (doseq [{:keys [on-tx]} features
            :when on-tx]
      (on-tx sys tx))))

(def tasks (->> features
                (mapcat :tasks)
                (map #(update % :task comp biff/assoc-db))))

(def static-pages (apply biff/safe-merge (map :static features)))

(defn generate-assets! [sys]
  (when (:org.archimitra/enable-web sys)
    (biff/export-rum static-pages "target/resources/public")
    (->> (file-seq (io/file "target/resources/public"))
         (filter (fn [file]
                   (and (.isFile file)
                        (biff/elapsed? (java.util.Date. (.lastModified file))
                                       :now
                                       30
                                       :seconds)
                        (str/ends-with? (.getPath file) ".html"))))
         (run! (fn [f]
                 (log/info "deleting" f)
                 (io/delete-file f))))
    (biff/sh "bin/tailwindcss"
             "-c" "resources/tailwind.config.js"
             "-i" "resources/tailwind.css"
             "-o" "target/resources/public/css/main.css"
             "--minify")
    (log/info "CSS done")))

(defn on-save [sys]
  (biff/eval-files! sys)
  (generate-assets! sys)
  (test/run-all-tests #"org.archimitra.test.*"))

(defn start []
  (biff/start-system
    {:org.archimitra/chat-clients (atom #{})
     :biff/after-refresh `start
     :biff/handler #'handler
     :biff/malli-opts #'malli-opts
     :biff.hawk/on-save #'on-save
     :biff.xtdb/on-tx #'on-tx
     :biff.chime/tasks tasks
     :biff/config "config.edn"
     :biff/components [biff/use-config
                       biff/use-random-default-secrets
                       biff/use-xt
                       biff/use-tx-listener
                       (biff/use-when
                         :org.archimitra/enable-web
                         biff/use-outer-default-middleware
                         biff/use-jetty)
                       (biff/use-when
                         :org.archimitra/enable-worker
                         biff/use-chime)
                       (biff/use-when
                         :org.archimitra/enable-hawk
                         biff/use-hawk)]})
  (generate-assets! @biff/system)
  (log/info "Go to" (:biff/base-url @biff/system)))

(defn -main [& args]
  (start)
  (apply nrepl-cmd/-main args))
