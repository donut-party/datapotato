(ns donut.datapotato.core
  (:require
   [better-cond.core :as b]
   [clojure.data :as data]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   #?(:bb      [loom.alg-generic :as lgen]
      :default [loom.alg :as la])
   [loom.attr :as lat]
   [loom.derived :as ld]
   [loom.graph :as lg]
   [medley.core :as medley]))

;; specifies that a ref ent should *not* be automatically generated
(defn omit? [x] (= x ::omit))

;; ent type is specified in the schema, e.g. in this:
;; {:user {:prefix :u}}
;; `:user` is the ent-type
(s/def ::ent-type keyword?)

;; every ent has an ent-name. ent name's are used for query references
;; and to manage the auto-generation of ents to ensure that the same
;; ent isn't generated twice.
(s/def ::ent-name (s/and keyword? (complement omit?)))

;; an ent-attr roughly corresponds to a field in a database
(s/def ::ent-attr keyword?)

;; the clojure.spec spec for a type
(s/def ::spec (s/or :keyword (s/and keyword? namespace)
                    :spec    s/spec?))


;; -----------------
;; -----------------
;; fixture and generate specs
;; -----------------
;; fixture and generation behavior can be configured at the potato-db level and at
;; the ent-type-schema level.

;; db specs
(s/def :datapotato.fixtures/insert fn?)
(s/def :datapotato.fixtures/get-connection fn?)
(s/def :datapotato.fixtures/setup fn?)
(s/def :datapotato/fixtures
  (s/keys :opt-un [:datapotato.fixtures/get-connection
                   :datapotato.fixtures/insert
                   :datapotato.fixtures/setup]))

(s/def :datapotato.generate/generator any?)
(s/def :datapotato.generate/schema any?)

(s/def :datapotato/generate
  (s/keys :opt-un [:datapotato.generate/generator
                   :datapotato.generate/schema]))

;; -----------------
;; -----------------
;; schema specs
;; -----------------

;; The schema defines entity types, their specs, and their
;; relationships to each other


;; relations
;; -----------------

;; About relations:
;; In these comments I'll use capital letters like A and B to refer to
;; types.

;; A relation is the description of how ents of type A reference ents
;; of type B. For example, in this:
;;
;; {:user {}
;;  :todo {:relations {:created-by-id [:user :id]}}}
;;
;; a Todo's `:created-by-id` key references a User, such that
;; :created-by-id should equal the User's `:id`
;;
;; /end About relations


;; In the following schema:
;;
;; {:user {}
;;  :todo {:relations {:created-by-id [:user :id]}}}
;;
;; the relation-path is `[:user :id]`
(s/def ::relation-path (s/+ any?))

;; Used to validate the return value of the function `query-relation`
(s/def ::conformed-relation
  (s/map-of :ent-type ::ent-type
            :path ::relation-path))


{:watch {:relations {:watched-id #{[:topic-category :id]
                                   [:topic :id]}}}}

;; in monotype relations, there is only one type of referred ent. most
;; relations are monotype relations. example:
;;
;; {:topic {:relations {:topic-category-id [:topic-category :id]}}}
;;
;; `[:topic-category-id :id]` is a monotype relation
(s/def ::monotype-relation
  (s/cat :ent-type ::ent-type
         :path ::relation-path))


;; In this example:
;; {:watch {:relations {:watched-id #{[:topic-category :id]
;;                                    [:topic :id]}}}}
;; the set `#{[:topic-category :id] [:topic :id]}` is a polymorphic relation
(s/def ::polymorphic-relation
  (s/coll-of ::relation))

(s/def ::relation
  (s/or :monotype-relation ::monotype-relation
        :polymorphic-relation ::polymorphic-relation))

(s/def ::relations
  (s/map-of ::ent-attr ::relation))


;; constraints
;; -----------------

;; This isn't used for validation, just for documentation
(s/def ::core-constraints
  #{:coll :uniq :required})

(s/def ::constraint keyword?)

(s/def ::constraints
  (s/map-of ::ent-attr (s/coll-of ::constraint)))


;; schema
;; -----------------

;; the prefix is used for ent auto-generation to uniquely identify
;; ents. Given this schema:
;;
;; {:user {:prefix :u}}
;;
;; the query `{:user [[2]]}` would produce ents with `ent-name` `:u0`
;; and `:u1`
(s/def ::prefix keyword?)

(s/def ::ent-type-schema
  (s/keys :req-un [::prefix]
          :opt-un [::relations ::constraints ::spec :datapotato/fixtures :datapotato/generate]))

(s/def ::schema
  (s/map-of ::ent-type ::ent-type-schema))

;; -----------------
;; -----------------
;; query specs
;; -----------------

;; query refs / relations
;; -----------------

;; how many ents to create
(s/def ::count pos-int?)

(s/def ::coll-query-relations
  (s/or :ent-names (s/coll-of ::ent-name)
        :count ::count))

(s/def ::unary-query-relations
  (s/or :ent-name ::ent-name))

;; by default, datapotato uses a default naming system to determine
;; which ents to refer to. you can specify a `:refs` value to override
;; the default names.
(s/def ::refs
  (s/map-of ::ent-attr (s/or :coll  ::coll-query-relations
                             :unary ::unary-query-relations
                             :omit  omit?)))

;; other query opts
;; -----------------

(s/def ::ref-types
  (s/map-of ::ent-attr ::ent-type))

;; all references to ents of type `:ent-type` should have name
;; `:ent-name` throughout the entire subgraph
(s/def ::bind
  (s/map-of ::ent-type ::ent-name))

(s/def ::query-opts
  (s/keys :opt-un [::refs
                   ::ref-types
                   ::bind
                   ::count
                   ::ent-name]))

(s/def ::ent-id
  (s/or :count ::count
        :ent-name  ::ent-name))

;; queries
;; -----------------

;; examples:
;; generate 5 of a thing
;; [[5]]
;;
;; generate 1 of a thing, don't generate the ent corresponding to created-by-id:
;; [[1 {:refs {:created-by-id ::omit}}]]
(s/def ::query-term.orig
  (s/cat :ent-id ::ent-id
         :query-opts (s/? ::query-opts)))

(s/def ::query-term.new ::query-opts)

(s/def ::query-term
  (s/or :query-term.orig ::query-term.orig
        :query-term.new ::query-term.new))

(s/def ::query
  (s/map-of ::ent-type (s/coll-of ::query-term)))

;; -----------------
;; -----------------
;; potato-db specs
;; -----------------

(s/def ::potato-db
  (s/keys :req-un [::schema]
          :opt-un [:datapotato/fixtures
                   :datapotato/generate]))

;; -----------------
;; -----------------
;; building / updating db
;; -----------------

;; utilities
;; -----------------
(defn ent-schema
  "Given an ent name, return the schema of its corresponding type"
  [{:keys [schema data]} ent-name]
  (get schema (lat/attr data ent-name :ent-type)))

(defn relation-attrs-with-constraint
  "Given an ent name, return all relation attributes which include the constraint."
  [potato-db ent-name _constraint]
  (->> (ent-schema potato-db ent-name)
       :constraints
       (medley/filter-vals (fn [attr-constraints] (contains? attr-constraints :coll)))
       keys
       set))

(defn ent-attrs
  [{:keys [data]} ent-name]
  (get-in data [:attrs ent-name]))

(defn ent-attr
  [{:keys [data]} ent-name attr]
  (get-in data [:attrs ent-name attr]))

(defn query-opts
  [{:keys [data]} ent-name]
  (lat/attr data ent-name :query-term))

(defn relation-graph
  "A graph of the type dependencies in a schema. If entities of type
  `:project` reference an entity of type `:user` via `:owner-id`, then
  this will return a graph where the `:project` node connects to the
  `:user` node"
  [schema]
  (->> schema
       (medley/map-vals (fn [v] (->> v :relations vals (map first) set)))
       (lg/digraph)))

;; ent naming
;; -----------------

(defn ent-index
  "Used to keep track of entity's insertion order in graph relative to
  other entities of the same type."
  [g ent-type]
  (count (lg/successors g ent-type)))

(defn numeric-node-name
  "Template for generating a node name"
  [schema ent-type index]
  (let [prefix (get-in schema [ent-type :prefix])]
    (keyword (str (name prefix) index))))

(defn default-node-name
  "Whenever datapotato needs to create a node that's not manually
  named, it uses this to generate the default name."
  [{:keys [schema]} ent-type]
  (numeric-node-name schema ent-type 0))

(defn incrementing-node-name
  "A template for creating distinct node names."
  [{:keys [data schema]} ent-type]
  (numeric-node-name schema ent-type (ent-index data ent-type)))

;; bound naming
;; -----------------

(defn bound-descendants?
  "Check whether `query-relations` contains bindings that apply to any
  descendants of `related-ent-type`"
  [{:keys [relation-graph]} query-bindings related-ent-type]
  (not-empty (set/intersection (disj (set (lg/nodes (ld/subgraph-reachable-from relation-graph related-ent-type))) related-ent-type)
                               (set (keys query-bindings)))))

(defn bound-relation-attr-name-source
  [ent-name]
  (-> ent-name
      name
      (str/replace #"-\d+$" "")
      (str/replace #".*-bound-" "")
      (str/replace #"\d+$" "")))

(defn bound-relation-attr-name
  "Template for when a binding necessitates you add a new entity"
  [{:keys [schema]} ent-name related-ent-type index]
  (let [{:keys [prefix]} (related-ent-type schema)]
    (keyword (str (name prefix) "-bound-" (bound-relation-attr-name-source ent-name) "-" index))))

;; related ents
;; -----------------
(defn ent-relation-constraints
  [potato-db ent relation-attr]
  (-> potato-db
      (ent-schema ent)
      (get-in [:constraints relation-attr])))

(defn coll-relation-attr?
  "Given a db, ent, and relation-attr, determines whether the relation is
  a coll attr."
  [potato-db ent relation-attr]
  (contains? (ent-relation-constraints potato-db ent relation-attr) :coll))

(s/fdef coll-relation-attr?
  :args (s/cat :potato-db ::potato-db :ent-name ::ent-name :ent-attr ::ent-attr)
  :ret boolean?)

(defn uniq-relation-attr?
  "Given a db, ent, and relation-attr, determines whether the relation is
  a uniq attr."
  [potato-db ent relation-attr]
  (contains? (ent-relation-constraints potato-db ent relation-attr) :uniq))

(s/fdef uniq-relation-attr?
  :args (s/cat :potato-db ::potato-db :ent-name ::ent-name :ent-attr ::ent-attr)
  :ret boolean?)

(defn add-edge-with-id
  "When indicating :ent-a references :ent-b, include a
  `:relation-attrs` graph attribute that includes the attributes via
  which `:ent-a` references `:ent-b`.

  For example, if the `:project` named `:p0` has an `:owner-id` and
  `:updated-by-id` that both reference the `:user` named `:u0`, then
  the edge from `:p0` to `:u0` will if a `:relation-attrs` attribute
  with value `#{:owner-id :updated-by-id}`.

  This can be used e.g. to set the values for `:owner-id` and
  `:updated-by-id`."
  [g ent-name related-ent-name id]
  (let [ids (lat/attr g ent-name related-ent-name :relation-attrs)]
    (-> g
        (lg/add-edges [ent-name related-ent-name])
        (lat/add-attr ent-name related-ent-name :relation-attrs (conj (or ids #{}) id)))))

(defn conformed-query-opts
  "These conformed query opts allow us to 1) validate the query term and
  2) dispatch on what kind of query was supplied.

  This is one of the most complicated parts of SM because users can
  supply different types of values for the `:refs` key of a query:

  1. an ent-name for unary relations          (type: `:ent-name`)
  2. a vector of ent-names for coll relations (type: `:ent-names`)
  3. a number for coll relations              (type: `:count`)

  These types are captured by the `::refs` specs, and the specs it
  composes. The type for the supplied query-term is returned as
  `:qr-type`.

  Each of these qr-types corresponds to a constraint of either `:coll`
  or `:unary`. The constraint is returned as `:qr-constraint`.

  Conforming the query opts provides the `qr-type` and `qr-constraint`
  so that dependent functions can dispatch on these values."
  [query-term relation-attr]
  (let [{:keys [refs bind]}        (s/conform ::query-opts query-term)
        [qr-constraint qr-details] (relation-attr refs)]
    (if (= qr-constraint :omit)
      {:qr-constraint :omit}
      {:bind          bind
       :qr-constraint qr-constraint
       :qr-type       (first qr-details)
       :qr-term       (second qr-details)})))

(defn validate-related-ents-query
  "Check that the refs value supplied in a query is a collection if the
  relation type is collection, or a keyword if the relation type is
  unary. If the reference is omit, no further validation is required."
  [potato-db ent-name relation-attr query-term]
  (let [coll-attr?                      (coll-relation-attr? potato-db ent-name relation-attr)
        {:keys [qr-constraint qr-term]} (conformed-query-opts query-term relation-attr)]
    (cond (or (nil? qr-constraint) (= :omit qr-constraint)) nil ;; noop

          (and coll-attr? (not= qr-constraint :coll))
          (throw (ex-info "Query-relations for coll attrs must be a number or vector"
                          {:spec-data (s/explain-data ::coll-query-relations qr-term)}))

          (and (not coll-attr?) (not= qr-constraint :unary))
          (throw (ex-info "Query-relations for unary attrs must be a keyword"
                          {:spec-data (s/explain-data ::unary-query-relations qr-term)})))))

(defn related-ents
  "Returns all related ents for an ent's relation-attr"
  [{:keys [schema data] :as potato-db} ent-name relation-attr related-ent-type query-term]
  (let [{:keys [qr-constraint qr-type qr-term bind]} (conformed-query-opts query-term relation-attr)]

    (validate-related-ents-query potato-db ent-name relation-attr query-term)

    (b/cond (= qr-constraint :omit) []
            (= qr-type :count)      (mapv (partial numeric-node-name schema related-ent-type) (range qr-term))
            (= qr-type :ent-names)  qr-term
            (= qr-type :ent-name)   [qr-term]
            :let [bn (get bind related-ent-type)]
            bn   [bn]

            :let [has-bound-descendants? (bound-descendants? potato-db bind related-ent-type)
                  uniq?                  (uniq-relation-attr? potato-db ent-name relation-attr)
                  ent-index              (lat/attr data ent-name :index)]
            (and has-bound-descendants? uniq?) [(bound-relation-attr-name potato-db ent-name related-ent-type ent-index)]
            has-bound-descendants?             [(bound-relation-attr-name potato-db ent-name related-ent-type 0)]
            uniq?                              [(numeric-node-name schema related-ent-type ent-index)]
            related-ent-type                   [(default-node-name potato-db related-ent-type)]
            :else                              [])))

(defn query-relation
  "Returns the conformed relation for an ent's relation-attr. Handles
  polymorphic relations."
  [potato-db ent-name relation-attr]
  (let [{:keys [relations ref-types]} (ent-schema potato-db ent-name)
        [relation-type relation]      (s/conform ::relation (relation-attr relations))
        ent-query-opts                (query-opts potato-db ent-name)]
    (case relation-type
      :monotype-relation    relation
      :polymorphic-relation (let [polymorphic-type-choice (or (get-in ent-query-opts [:ref-types relation-attr])
                                                              (relation-attr ref-types))
                                  polymorphic-relation    (->> relation
                                                               (map second)
                                                               (some #(and (= (:ent-type %) polymorphic-type-choice) %)))]
                              (when-not polymorphic-relation
                                (throw (ex-info "Could not determine polymorphic relation. Specify relation type under the :ref-types key of query-opts, or specify default value in schema."
                                                {:relation-attr  relation-attr
                                                 :ent-name       ent-name
                                                 :ent-query-opts ent-query-opts})))
                              polymorphic-relation))))

(s/fdef query-relation
  :args (s/cat :potato-db ::potato-db :ent-name ::ent-name :relation-attr ::ent-attr)
  :ret ::conformed-relation)

(defn add-related-ents
  [potato-db ent-name query-term]
  (let [relation-attrs    (keys (:relations (ent-schema potato-db ent-name)))
        attr-related-ents (for [relation-attr    relation-attrs
                                related-ent-type [(:ent-type (query-relation potato-db ent-name relation-attr))]
                                related-ent      (related-ents potato-db ent-name relation-attr related-ent-type query-term)]
                            [relation-attr related-ent-type related-ent])
        ent-bindings      (if-let [query-bindings (:bind query-term)]
                            {:ent-name :_ :bind query-bindings}
                            {:ent-name :_})]
    (reduce (fn [db [relation-attr related-ent-type related-ent]]
              (-> db
                  (update :ref-ents conj [related-ent related-ent-type ent-bindings])
                  (update :data add-edge-with-id ent-name related-ent relation-attr)))
            potato-db
            attr-related-ents)))

(defn add-ent
  "Add an ent, and its related ents, to the potato-db"
  [{:keys [data] :as potato-db} ent-name ent-type query-term]
  ;; don't try to add an ent if it's already been added
  (let [ent-name (if (= ent-name :_) (incrementing-node-name potato-db ent-type) ent-name)]
    ;; check both that the node exists and that it has the type
    ;; attribute: it's possible for the node to be added as an edge in
    ;; `add-related-ents`, without all the additional attributes below
    ;; to be added
    ;;
    ;; this prevents the attributes added below from being overwritten
    (if (and ((lg/nodes data) ent-name)
             (lat/attr data ent-name :type))
      potato-db
      (-> potato-db
          (update :data (fn [data]
                          (-> data
                              (lg/add-edges [ent-type ent-name])
                              (lat/add-attr ent-type :type :ent-type)
                              (lat/add-attr ent-name :type :ent)
                              (lat/add-attr ent-name :index (ent-index data ent-type))
                              (lat/add-attr ent-name :ent-type ent-type)
                              (lat/add-attr ent-name :query-term query-term))))
          (add-related-ents ent-name query-term)))))

(defn add-n-ents
  "Used when a query is something like [3]"
  [potato-db ent-type num-ents query-term]
  (loop [db potato-db
         n  num-ents]
    (if (zero? n)
      db
      (recur (add-ent db (incrementing-node-name db ent-type) ent-type query-term)
             (dec n)))))

(defn normalize-query-term-orig
  "normalizes query terms written with specmonstah syntax"
  [{:keys [ent-id query-opts]}]
  (let [[ent-id-type ent-id-val] ent-id
        query-opts               (s/unform ::query-opts query-opts)]
    (case ent-id-type
      :ent-name (merge {:ent-name ent-id-val
                        :count    1}
                       query-opts)
      :count    (merge {:ent-name :_
                        :count    ent-id-val}
                       query-opts))))

(defn normalize-query-term
  "Query terms can take a few different forms. This converts the query term to the
  standard form used internally and fills in default values."
  [query-term]
  (let [[query-term-version conformed-query-term] (s/conform ::query-term query-term)
        normalized (case query-term-version
                     :query-term.orig (normalize-query-term-orig conformed-query-term)
                     :query-term.new  (merge {:ent-name :_ :count 1} query-term))]
    (when (and (> (:count normalized) 1)
               (not= :_ (:ent-name normalized)))
      (throw (ex-info "You can't specify both :ent-name and a :count > 1 in a query term" {:query-term query-term})))
    normalized))

(defn add-ent-type-query
  "A query is composed of ent-type-queries, where each ent-type-query
  specifies the ents that should be created for that type. This
  function adds the ents for an ent-type-query."
  [potato-db ent-type-query ent-type]
  (reduce (fn [db query-term]
            ;; top-level meta is used to track which ents are
            ;; specified explicitly in a query

            (let [query-term               (with-meta query-term {:top-level true})
                  {:keys [count ent-name]} query-term]
              (if (> count 1)
                (add-n-ents db ent-type count query-term)
                (add-ent db ent-name ent-type query-term))))
          potato-db
          ent-type-query))

(defn add-ref-ents
  "Ents are added in two stages: first, all ents that are declared in
  the query are added. During that process, we keep track of ref-ents,
  ents which are automatically generated in order to satisfy
  relations. This function adds those ref ents if an ent of the same
  name doesn't exist already."
  [potato-db]
  (loop [{:keys [ref-ents] :as potato-db} potato-db]
    (if (empty? ref-ents)
      potato-db
      (recur (reduce (fn [db [ent-name ent-type query-term]]
                       (add-ent db ent-name ent-type query-term))
                     (assoc potato-db :ref-ents [])
                     ref-ents)))))

(defn init-db
  [{:keys [schema] :as potato-db} query]
  (let [rg (relation-graph schema)]
    (-> potato-db
        (update :data #(or % (lg/digraph)))
        (update :queries conj query)
        (assoc :relation-graph rg
               :types (set (keys schema))
               :ref-ents []))))

(defn throw-invalid-spec
  [arg-name spec data]
  (when-not (s/valid? spec data)
    (throw (ex-info (str arg-name " is invalid") {::s/explain-data (s/explain-data spec data)}))))

(defn identical-prefixes
  "Schemas are invalid if two types have the same prefix. This checks
  that."
  [schema]
  (->> (medley/map-vals :prefix schema)
       (reduce-kv (fn [grouping ent-type prefix]
                    (update grouping prefix (fn [x] (conj (or x #{}) ent-type))))
                  {})
       (medley/filter-vals #(> (count %) 1))))

(defn invalid-schema-relations
  "Relations that reference nonexistent types"
  [schema]
  (set/difference (->> schema
                       vals
                       ;; TODO clean this up
                       (map (comp (fn [relation-paths]
                                    (map (fn [relation-path]
                                           (if (set? relation-path)
                                             (map first relation-path)
                                             (first relation-path)))
                                         relation-paths))
                                  vals
                                  :relations))
                       flatten
                       set)
                  (set (keys schema))))

(defn invalid-constraints
  "Constraints that reference nonexistent relation attrs"
  [schema]
  (->> schema
       (medley/map-vals (fn [ent-schema]
                          (set/difference (set (keys (:constraints ent-schema)))
                                          (set (keys (:relations ent-schema))))))
       (medley/filter-vals not-empty)))

(defn add-ents
  "Produce a new db with an ent graph that contains all ents specified
  by query"
  [potato-db query]
  (let [{:keys [schema] :as potato-db} (or (:potato-db (meta potato-db)) potato-db)]
    ;; validations
    (let [isr (invalid-schema-relations schema)]
      (assert (empty? isr)
              (str "Your schema relations reference nonexistent types: " isr)))

    (let [prefix-dupes (identical-prefixes schema)]
      (assert (empty? prefix-dupes)
              (str "You have used the same prefix for multiple entity types: " prefix-dupes)))

    (let [ic (invalid-constraints schema)]
      (assert (empty? ic)
              (str "Schema constraints reference nonexistent relation attrs: " ic)))

    (let [diff (set/difference (set (keys query)) (set (keys schema)))]
      (assert (empty? diff)
              (str "The following ent types are in your query but aren't defined in your schema: " diff)))

    (throw-invalid-spec "db" ::potato-db potato-db)
    (throw-invalid-spec "query" ::query query)
    ;; end validations

    (let [normalized-query (medley/map-vals (fn [query-terms] (mapv normalize-query-term query-terms))
                                            query)
          potato-db        (init-db potato-db normalized-query)]
      (->> (:types potato-db)
           (reduce (fn [db ent-type]
                     (if-let [ent-type-query (ent-type normalized-query)]
                       (add-ent-type-query db ent-type-query ent-type)
                       db))
                   potato-db)
           (add-ref-ents)))))

;; -----------------
;; visiting
;; -----------------

(defn ents
  "returns all ents in the ent db"
  [{:keys [data]}]
  (lg/nodes (ld/nodes-filtered-by #(= (lat/attr data % :type) :ent) data)))

(defn relation-attrs
  "Given an ent A and an ent it references B, return the set of attrs
  by which A references B."
  [{:keys [data]} ent-name referenced-ent]
  (lat/attr data ent-name referenced-ent :relation-attrs))

(defn ent-related-by-attr?
  "Is ent A related to ent B by the given relation-attr?"
  [potato-db ent-name related-ent relation-attr]
  (and (contains? (relation-attrs potato-db ent-name related-ent) relation-attr)
       related-ent))

(defn related-ents-by-attr
  "All ents related to ent via relation-attr"
  [{:keys [data] :as potato-db} ent-name relation-attr]
  (let [related-ents (lg/successors data ent-name)]
    (if (coll-relation-attr? potato-db ent-name relation-attr)
      (->> related-ents
           (map #(ent-related-by-attr? potato-db ent-name % relation-attr))
           (filter identity))
      (some #(ent-related-by-attr? potato-db ent-name % relation-attr)
            related-ents))))

(defn referenced-ent-attrs
  "seq of [referenced-ent relation-attr]"
  [{:keys [data] :as potato-db} ent-name]
  (for [referenced-ent (sort-by #(lat/attr data % :index) (lg/successors data ent-name))
        relation-attr  (relation-attrs potato-db ent-name referenced-ent)]
    [referenced-ent relation-attr]))

#?(:bb
   ;; Copied from la/topsort since bb can't load the loom.alg ns
   (defn topsort
     "Topological sort of a directed acyclic graph (DAG). Returns nil if
      g contains any cycles."
     ([g]
      (loop [seen #{}
             result ()
             [n & ns] (seq (lg/nodes g))]
        (if-not n
          result
          (if (seen n)
            (recur seen result ns)
            (when-let [cresult (lgen/topsort-component
                                (lg/successors g) n seen seen)]
              (recur (into seen cresult) (concat cresult result) ns))))))
     ([g start]
      (lgen/topsort-component (lg/successors g) start))))

(defn topsort-ents
  [{:keys [data]}]
  (reverse (#?(:bb topsort :default la/topsort) (ld/nodes-filtered-by #(= (lat/attr data % :type) :ent) data))))

(defn required-attrs
  "Returns a map of `{:ent-type #{:required-attr-1 :required-attr-2}}`.
  Used to handle cycles."
  [{:keys [schema]}]
  (->> (for [[ent-type ent-schema]   schema
             [attr-name constraints] (:constraints ent-schema)
             :when                   (contains? constraints :required)]
         [ent-type #{attr-name}])
       (group-by first)
       (medley/map-vals (fn [xs] (->> xs (map second) (apply set/union))))))

(defn prune-required
  "Updates ent graph, removing edges going from a 'required' ent to the
  'requiring' ent, thus eliminating cycles."
  [{:keys [data] :as potato-db} required-attrs]
  (assoc potato-db :data
         (reduce-kv (fn [g ent-type attrs]
                      (reduce (fn [g requiring-ent]
                                (reduce (fn [g required-ent]
                                          (if (seq (set/intersection attrs (lat/attr g requiring-ent required-ent :relation-attrs)))
                                            (lg/remove-edges g [required-ent requiring-ent])
                                            g))
                                        g
                                        (lg/successors g requiring-ent)))
                              g
                              (lg/successors g ent-type)))
                    data
                    required-attrs)))

(defn sort-by-required
  [potato-db]
  (topsort-ents (prune-required potato-db (required-attrs potato-db))))

(defn sort-ents
  "Attempts to topsort ents. If that's not possible (cycles present),
  uses `sort-by-required` to resolve ordering of ents in cycle"
  [potato-db]
  (let [sorted (or (seq (topsort-ents potato-db))
                   (sort-by-required potato-db))]
    (when (and (empty? sorted) (not-empty (ents potato-db)))
      (throw (ex-info "Can't sort ents: check for cycles in ent type relations. If a cycle is present, use the :required constraint to indicate ordering."
                      {})))
    sorted))

(defn visit-fn-data
  "When a visit fn is called, it's passed this map as its second argument"
  [potato-db ent visit-key]
  (let [attrs  (ent-attrs potato-db ent)
        q-opts (query-opts potato-db ent)
        base   {:ent-name         ent
                :attrs            attrs
                :visit-val        (visit-key attrs)
                :visit-key        visit-key
                :query-opts       q-opts
                :visit-query-opts (visit-key q-opts)
                :schema-opts      (visit-key (ent-schema potato-db ent))}]
    (merge attrs base)))

(defn visit-ents
  "Perform `visit-fns` on ents, storing return value as a graph
  attribute under `visit-key`"
  ([potato-db visit-key visit-fns]
   (visit-ents potato-db visit-key visit-fns (sort-ents potato-db)))
  ([potato-db visit-key visit-fns ents]
   (let [visit-fns (if (sequential? visit-fns) visit-fns [visit-fns])]
     (reduce (fn [potato-db [visit-fn ent]]
               (update potato-db :data lat/add-attr ent visit-key (visit-fn potato-db (visit-fn-data potato-db ent visit-key))))
             potato-db
             (for [visit-fn visit-fns ent ents] [visit-fn ent])))))

(defn visit-ents-once
  "Like `visit-ents` but doesn't call `visit-fn` if the ent already
  has a `visit-key` attribute"
  ([potato-db visit-key visit-fns]
   (visit-ents-once potato-db visit-key visit-fns (sort-ents potato-db)))
  ([potato-db visit-key visit-fns ents]
   (let [skip-ents (->> ents
                        (filter (fn [ent]
                                  (let [ent-attrs (get-in potato-db [:data :attrs ent])]
                                    (contains? ent-attrs visit-key))))
                        (set))
         visit-fns (if (sequential? visit-fns) visit-fns [visit-fns])]
     (visit-ents potato-db
                 visit-key
                 (mapv (fn [visit-fn]
                         (fn [db {:keys [ent-name visit-val] :as visit-data}]
                           (if (skip-ents ent-name)
                             visit-val
                             (visit-fn db visit-data))))
                       visit-fns)
                 ents))))


;; -----------------
;; views
;; -----------------

;; convenience functions for getting projections of the ent db,
;; considering the ent db has a lot of loom bookkeeping

(defn attr-map
  "Produce a map where each key is a node and its value is a graph
  attr on that node"
  ([potato-db attr] (attr-map potato-db attr (ents potato-db)))
  ([{:keys [data] :as potato-db} attr ents]
   (with-meta (->> ents
                   (reduce (fn [m ent] (assoc m ent (lat/attr data ent attr)))
                           {})
                   (into (sorted-map)))
     {:potato-db potato-db})))

(defn query-ents
  "Get seq of nodes that are explicitly defined in the query"
  [{:keys [data]}]
  (->> (:attrs data)
       (filter (fn [[_ent-name attrs]] (:top-level (meta (:query-term attrs)))))
       (map first)))

(defn ents-by-type
  "Given a db, returns a map of ent-type to a set of entities of that
  type. Optionally pass in a seq of the ents that should be included."
  ([potato-db] (ents-by-type potato-db (ents potato-db)))
  ([potato-db ents]
   (reduce-kv (fn [m k v] (update m v (fnil conj #{}) k))
              {}
              (select-keys (attr-map potato-db :ent-type) ents))))

(s/fdef ents-by-type
  :args (s/cat :potato-db ::potato-db :ent-names (s/? (s/coll-of ::ent-name)))
  :ret (s/map-of ::ent-type (s/coll-of ::ent-name)))

(defn ent-relations
  "Given a db and an ent, returns a map of relation attr to ent-name."
  [potato-db ent]
  (let [relations (get-in potato-db [:data :attrs ent :loom.attr/edge-attrs])]
    (apply merge-with
           set/union
           {}
           (for [[ref-ent {:keys [relation-attrs]}] relations
                 relation-attr                      relation-attrs]
             {relation-attr (if (coll-relation-attr? potato-db ent relation-attr)
                              #{ref-ent} ref-ent)}))))

(s/fdef ent-relations
  :args (s/cat :potato-db ::potato-db :ent-name ::ent-name)
  :ret  (s/map-of ::ent-attr (s/or :unary ::ent-name
                                   :coll (s/coll-of ::ent-name))))

(defn all-ent-relations
  "Given a db, returns a map of ent-type to map of entity relations.

  An example return value is:
  {:patient {:p0 {:created-by :u0
                  :updated-by :u1}
             :p1 {:created-by :u0
                  :updated-by :u2}}
   :user {:u0 {:friends-with :u0}}}"
  ([potato-db]
   (all-ent-relations potato-db (ents potato-db)))
  ([potato-db ents]
   (reduce-kv (fn [ents-by-type ent-type ents]
                (assoc ents-by-type ent-type
                       (into {}
                             (map (fn [ent]
                                    [ent (ent-relations potato-db ent)]))
                             ents)))
              {}
              (ents-by-type potato-db ents))))

(s/fdef all-ent-relations
  :args (s/cat :potato-db ::potato-db :ent-names (s/? (s/coll-of ::ent-name)))
  :ret  (s/map-of ::ent-type
                  (s/map-of ::ent-name
                            (s/map-of ::ent-attr ::ent-name))))

#?(:bb
   nil
   :clj
   (do
     (require '[loom.io :as lio])
     (defn view
       "View with loom"
       [{:keys [data]}]
       (lio/view data))))

;; -----------------
;; visiting w/ referenced vals
;; -----------------

;; These helpers are here mainly for handling generated data. When generating
;; data you need to do a little extra work to make sure that some generated
;; values are replaced by a referent. That is, if a Book entity's :author_id
;; refers to an Author entity's :id, then you want to replace the generated
;; :author_id with the author's :id.

(defn omit-relation?
  [potato-db ent-name reference-key]
  (-> potato-db
      (query-opts ent-name)
      (get-in [:refs reference-key])
      omit?))

(defn reset-relations
  "Generated data generates values agnostic of any schema constraints that may be
  present. This function updates values in the generated data to match up with
  constraints. First, it will remove any dummy ID's for a `:coll` relation.
  Next, it will remove any dummy ID's generated for an `:omit` relation. The
  updated ent-data map will be returned."
  [potato-db {:keys [ent-name visit-val]}]
  (let [coll-attrs (relation-attrs-with-constraint potato-db ent-name :coll)]
    (into {}
          (comp (map (fn [[k v]] (if (coll-attrs k) [k []] [k v])))
                (map (fn [[k v]] (when-not (omit-relation? potato-db ent-name k) [k v]))))
          visit-val)))

(defn assoc-referenced-val
  "Look up related ent's attr value and assoc with parent ent
  attr. `:coll` relations will add value to a vector."
  [ent-data relation-attr relation-val constraints]
  (if (contains? (relation-attr constraints) :coll)
    (update ent-data relation-attr #((fnil conj []) % relation-val))
    (assoc ent-data relation-attr relation-val)))

(defn assoc-referenced-vals
  "A visiting function that sets referenced values.

  Given a schema like
  {:user {}
   :post {:relations {:created-by [:user :id]}}}

  a :post's `:created-by` key gets set to the `:id` of the :user it references."
  [potato-db {:keys [ent-name visit-key visit-val]}]
  (let [{:keys [constraints]} (ent-schema potato-db ent-name)
        skip-keys             (::overwritten (meta visit-val) #{})]
    (->> (referenced-ent-attrs potato-db ent-name)
         (filter (comp (complement skip-keys) second))
         (reduce (fn [ent-data [referenced-ent relation-attr]]
                   (assoc-referenced-val ent-data
                                         relation-attr
                                         (get-in (ent-attr potato-db referenced-ent visit-key)
                                                 (:path (query-relation potato-db ent-name relation-attr)))
                                         constraints))
                 visit-val))))

(defn merge-overwrites
  "Overwrites generated data with what's found in schema-opts or
  visit-query-opts."
  [_potato-db {:keys [visit-val visit-query-opts schema-opts]}]
  (let [schema-overwrites (:overwrites schema-opts)
        merged            (cond-> visit-val
                            ;; the schema can include vals to merge into each ent
                            (fn? schema-overwrites)  schema-overwrites
                            (map? schema-overwrites) (merge schema-overwrites)

                            ;; visit query opts can also specify merge vals
                            (fn? visit-query-opts)  visit-query-opts
                            (map? visit-query-opts) (merge visit-query-opts))
        changed-keys (->> (data/diff visit-val merged)
                          (take 2)
                          (map keys)
                          (apply into)
                          (set))]
    (with-meta merged {::overwritten changed-keys})))

;;---
;; generating
;;---

(defn wrap-generate-visiting-fn
  "Useful when writing visiting fns where data generated for ent A needs to be
  referenced by ent B."
  [data-generating-visiting-fn]
  [data-generating-visiting-fn
   reset-relations
   merge-overwrites
   assoc-referenced-vals])

(def ^:const generate-visit-key :generate)

;; The functions below introduce some conventions to make it easier to get
;; started with generating data. The conventions also make data generation
;; pluggable, so that you can use either clojure.spec or malli and potentially
;; other generators.

(defn generate*
  "Use a generator to generate data for each ent. The generator can be specified
  in three places (in order of descending preference):

  - the visit query
  - an entity's schema
  - the potato-db

  for example:

  (datapotato.core/generate
    {:schema {:todo {:generate {:generator second-highest-precedence}}} ;; <= generator specified in entity schema
     :generate {:generator lowest-precedence}}                          ;; <= generator specified in potato-db
    {:todo [{:generate {:generator highest-precedence-generator}}]})    ;; <= generator specified in visit query
  "
  [potato-db]
  (visit-ents-once potato-db
                   generate-visit-key
                   (wrap-generate-visiting-fn
                    (fn generate-visiting-fn [db {:keys [ent-name visit-query-opts ent-type]}]
                      (let [ent-schema-generate       (generate-visit-key (ent-schema db ent-name))
                            visit-query-opts-generate (generate-visit-key visit-query-opts)
                            schema                    (or (:schema visit-query-opts-generate)
                                                          (:schema ent-schema-generate))
                            generator                 (or (:generator visit-query-opts-generate)
                                                          (:generator ent-schema-generate)
                                                          (get-in potato-db [generate-visit-key :generator]))]
                        (when-not generator
                          (throw (ex-info "No generator specified. Try adding [:generate :generator] to potato-db" {})))
                        (when-not schema
                          (throw (ex-info (str "No generate schema provided. Add under [:schema "
                                               ent-type
                                               " :generate :schema]")
                                          {:ent-name ent-name
                                           :ent-type ent-type})))
                        (generator schema))))))

(defn generate
  [potato-db query]
  (-> potato-db
      (add-ents query)
      (generate*)))

(defn generate-attr-map
  "Generates data and returns a map of {ent-id generated-data} rather than the
  entire potato-db"
  [potato-db query]
  (-> potato-db
      (generate query)
      (attr-map generate-visit-key)))

;;---
;; fixtures
;;---

(def ^:const fixtures-visit-key :fixtures)
(def ^:const insert-key :insert)
(def ^:dynamic *connection*)
(def ^:dynamic *potato-db*)

(defn wrap-incremental-insert-visiting-fn
  "Takes generated data stored as an attributed under `source-key` and inserts it
  using `inserting-visiting-fn`.

  Respects overwrites and ensures that referenced vals are assoc'd in before
  inserting. Useful when dealing with db-generated ids."
  [source-key inserting-visiting-fn]
  (fn incremental-insert-visiting-fn [potato-db opts]
    (reduce (fn [visit-val visiting-fn]
              (visiting-fn potato-db (assoc opts :visit-val visit-val)))
            (source-key opts)
            [reset-relations
             merge-overwrites
             assoc-referenced-vals
             inserting-visiting-fn])))

(defn insert-fixtures*
  [potato-db]
  (let [connection (or (get-in potato-db [fixtures-visit-key :connection])
                       (when-let [get-connection (get-in potato-db [fixtures-visit-key :get-connection])]
                         (get-connection potato-db)))]
    (visit-ents-once (assoc-in potato-db [fixtures-visit-key :connection] connection)
                     fixtures-visit-key
                     (wrap-incremental-insert-visiting-fn
                      generate-visit-key
                      (fn insert-visiting-fn [potato-db {:keys [ent-name visit-query-opts] :as visit-data}]
                        (let [insert (or (get-in visit-query-opts [fixtures-visit-key insert-key])
                                         (get-in (ent-schema potato-db ent-name) [fixtures-visit-key insert-key])
                                         (get-in potato-db [fixtures-visit-key insert-key]))]
                          (when-not insert
                            (throw (ex-info "No insert function specified. Try adding [:fixtures :insert] to potato-db" {})))
                          (insert potato-db visit-data)))))))

(defn insert-fixtures
  ([query]
   (insert-fixtures *potato-db* query))
  ([potato-db query]
   (-> potato-db
       (generate query)
       insert-fixtures*
       (attr-map fixtures-visit-key))))

(defmacro with-fixtures
  "Preferred way to work with fixtures in tests. Handles setup and teardown."
  [potato-db & body]
  `(let [potato-db#      ~potato-db
         get-connection# (get-in potato-db# [:fixtures :get-connection])
         connection#     (and get-connection# (get-connection# potato-db#))
         potato-db#      (assoc-in potato-db# [:fixtures :connection] connection#)]
     (binding [*connection* connection#
               *potato-db*  potato-db#]
       (when-let [setup# (get-in potato-db# [:fixtures :setup])]
         (setup# potato-db#))
       (try
         ~@body
         (finally
           (when-let [teardown# (get-in potato-db# [:fixtures :teardown])]
             (teardown# potato-db#))
           (when-let [close-connection# (get-in potato-db# [:fixtures :close-connection])]
             (close-connection# *connection*)))))))
