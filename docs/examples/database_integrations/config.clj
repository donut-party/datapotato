(def potato-db
  {:schema   {:user          {:prefix   :u
                              :generate {:schema User}
                              :fixtures {:insert custom-insert}}
              :dream-journal {:prefix   :dj
                              :generate {:schema DreamJournal}}
              :entry         {:prefix   :e
                              :generate {:schema Entry}}}
   :fixtures {:insert donut.datapotato.datomic/insert}})


(dc/with-fixtures
  (dc/insert {:entry [{:fixtures {:insert custom-dream-journal-insert}}]}))
