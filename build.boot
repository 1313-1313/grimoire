(set-env!
 :source-paths   #{"src/cljs" "src/clj"}
 :resource-paths #{"resources"}
 :dependencies '[[adzerk/boot-cljs "2.1.4" :scope "test"]
                 [adzerk/boot-reload "0.5.2" :scope "test"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.946"]
                 
                 [environ "1.1.0"]
                 [boot-environ "1.1.0"]
      
                 [org.danielsz/system "0.4.1"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 
                 [ring "1.6.2"]
                 [ring/ring-defaults "0.3.1"]
                 [compojure "1.6.0"]
                                
                 [http-kit "2.2.0"]
                 [com.taoensso/sente "1.12.0"]

                 [zcaudate/hara.time "2.8.1"]
                 [im.chit/hara.io.scheduler "2.5.10"]

                 [rum "0.10.8"]
                 [org.roman01la/citrus "3.0.1"]

                 [chronoid "0.1.1"]
                 [mantra "0.6.0"]

                 [metosin/potpuri "0.5.1"]
                 ])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-reload    :refer [reload]]
 '[holy-grail.systems :refer [dev-system prod-system]]
 '[environ.boot :refer [environ]]
 '[system.boot :refer [system run]])

(deftask dev
  "Run a restartable system in the Repl"
  []
  (comp
   (environ :env {:http-port "3000"})
   (watch :verbose true)
   (system :sys #'dev-system :auto true :files ["handler.clj"])
   (reload)
   (cljs)
   (repl :server true :port 44444)))

(deftask dev-run
  "Run a dev system from the command line"
  []
  (comp
   (environ :env {:http-port "3000"})
   (cljs)
   (run :main-namespace "holy-grail.core" :arguments [#'dev-system])
   (wait)))

(deftask prod-run
  "Run a prod system from the command line"
  []
  (comp
   (environ :env {:http-port "8008"
                  :repl-port "8009"})
   (cljs :optimizations :advanced)
   (run :main-namespace "holy-grail.core" :arguments [#'prod-system])
   (wait)))


