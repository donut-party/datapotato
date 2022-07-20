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
          :opt-un [::relations ::constraints ::spec]))

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

(s/def ::query-term.new
  (s/or :count ::count
        :query-opts ::query-opts))

(s/def ::query-term
  (s/or :query-term.orig ::query-term.orig
        :query-term.new ::query-term.new))

(s/def ::query
  (s/map-of ::ent-type (s/coll-of ::query-term)))

;; db specs
(s/def ::ent-db
  (s/keys :req-un [::schema]))


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
  [ent-db ent-name _constraint]
  (->> (ent-schema ent-db ent-name)
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
  (let [query-term      (lat/attr data ent-name :query-term)
        query-term-type (first (s/conform ::query-term query-term))]
    (case query-term-type
      :query-term.orig (second query-term)
      :query-term.new  query-term)))

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
  [ent-db ent relation-attr]
  (-> ent-db
      (ent-schema ent)
      (get-in [:constraints relation-attr])))

(defn coll-relation-attr?
  "Given a db, ent, and relation-attr, determines whether the relation is
  a coll attr."
  [ent-db ent relation-attr]
  (contains? (ent-relation-constraints ent-db ent relation-attr) :coll))

(s/fdef coll-relation-attr?
  :args (s/cat :ent-db ::ent-db :ent-name ::ent-name :ent-attr ::ent-attr)
  :ret boolean?)

(defn uniq-relation-attr?
  "Given a db, ent, and relation-attr, determines whether the relation is
  a uniq attr."
  [ent-db ent relation-attr]
  (contains? (ent-relation-constraints ent-db ent relation-attr) :uniq))

(s/fdef uniq-relation-attr?
  :args (s/cat :ent-db ::ent-db :ent-name ::ent-name :ent-attr ::ent-attr)
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
  (let [{:keys [refs bind]}        (and query-term
                                        (s/conform ::query-opts (second query-term)))
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
  [ent-db ent-name relation-attr query-term]
  (let [coll-attr?                      (coll-relation-attr? ent-db ent-name relation-attr)
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
  [{:keys [schema data] :as ent-db} ent-name relation-attr related-ent-type query-term]
  (let [{:keys [qr-constraint qr-type qr-term bind]} (conformed-query-opts query-term relation-attr)]

    (validate-related-ents-query ent-db ent-name relation-attr query-term)

    (b/cond (= qr-constraint :omit) []
            (= qr-type :count)  (mapv (partial numeric-node-name schema related-ent-type) (range qr-term))
            (= qr-type :ent-names)  qr-term
            (= qr-type :ent-name)   [qr-term]
            :let [bn (get bind related-ent-type)]
            bn   [bn]

            :let [has-bound-descendants? (bound-descendants? ent-db bind related-ent-type)
                  uniq?                  (uniq-relation-attr? ent-db ent-name relation-attr)
                  ent-index              (lat/attr data ent-name :index)]
            (and has-bound-descendants? uniq?) [(bound-relation-attr-name ent-db ent-name related-ent-type ent-index)]
            has-bound-descendants?             [(bound-relation-attr-name ent-db ent-name related-ent-type 0)]
            uniq?                              [(numeric-node-name schema related-ent-type ent-index)]
            related-ent-type                   [(default-node-name ent-db related-ent-type)]
            :else                              [])))

(defn query-relation
  "Returns the conformed relation for an ent's relation-attr. Handles
  polymorphic relations."
  [ent-db ent-name relation-attr]
  (let [{:keys [relations ref-types]} (ent-schema ent-db ent-name)
        [relation-type relation]      (s/conform ::relation (relation-attr relations))
        ent-query-opts                (query-opts ent-db ent-name)]
    (case relation-type
      :monotype-relation    relation
      :polymorphic-relation (let [polymorphic-type-choice (or (get-in ent-query-opts [:ref-types relation-attr])
                                                              (relation-attr ref-types))
                                  polymorphic-relation    (->> relation
                                                               (map second)
                                                               (some #(and (= (:ent-type %) polymorphic-type-choice) %)))]
                              (when-not polymorphic-relation
                                (throw (ex-info "Could not determine polymorphic relation. Specify relation type under :ref-type key of query-opts, or specify default value in schema."
                                                {:relation-attr  relation-attr
                                                 :ent-name       ent-name
                                                 :ent-query-opts ent-query-opts})))
                              polymorphic-relation))))

(s/fdef query-relation
  :args (s/cat :ent-db ::ent-db :ent-name ::ent-name :relation-attr ::ent-attr)
  :ret ::conformed-relation)

(defn add-related-ents
  [ent-db ent-name query-term]
  (let [relation-schema (:relations (ent-schema ent-db ent-name))]
    (reduce (fn [db relation-attr]
              (let [related-ent-type (:ent-type (query-relation db ent-name relation-attr))]
                (reduce (fn [db related-ent]
                          (-> db
                              (update :ref-ents conj [related-ent
                                                      related-ent-type
                                                      (if-let [query-bindings (get-in query-term [1 :bind])]
                                                        [:_ {:bind query-bindings}]
                                                        [:_])])
                              (update :data add-edge-with-id ent-name related-ent relation-attr)))
                        db
                        (related-ents db ent-name relation-attr related-ent-type query-term))))
            ent-db
            (keys relation-schema))))

(defn add-ent
  "Add an ent, and its related ents, to the ent-db"
  [{:keys [data] :as ent-db} ent-name ent-type query-term]
  ;; don't try to add an ent if it's already been added
  (let [ent-name  (if (= ent-name :_) (incrementing-node-name ent-db ent-type) ent-name)]
    ;; check both that the node exists and that it has the type
    ;; attribute: it's possible for the node to be added as an edge in
    ;; `add-related-ents`, without all the additional attributes below
    ;; to be added
    ;;
    ;; this prevents the attributes added below from being overwritten
    (if (and ((lg/nodes data) ent-name)
             (lat/attr data ent-name :type))
      ent-db
      (-> ent-db
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
  [ent-db ent-type num-ents query-term]
  (loop [db ent-db
         n  num-ents]
    (if (zero? n)
      db
      (recur (add-ent db (incrementing-node-name db ent-type) ent-type query-term)
             (dec n)))))

(defn conform-query-term-orig
  [conformed-query-term]
  (let [[ent-id-type ent-id] (:ent-id conformed-query-term)]
    (case ent-id-type
      :count      {:count      ent-id
                   :ent-name :_}
      :ent-name {:count      1
                 :ent-name ent-id})))

(defn conform-query-term-new
  [[query-term-type query-term]]
  (case query-term-type
    :count      {:count    query-term
                 :ent-name :_}
    :query-opts (-> query-term
                    (update :count #(or % 1))
                    (update :ent-name #(or % :_)))))

(defn conform-query-term
  [query-term]
  (let [[query-term-version conformed-query-term]
        (s/conform ::query-term query-term)

        {:keys [count ent-name] :as query-opts}
        (case query-term-version
          :query-term.orig (conform-query-term-orig conformed-query-term)
          :query-term.new  (conform-query-term-new conformed-query-term))]
    (when (and (> count 1)
               (not= :_ ent-name))
      (throw (ex-info "You can't specify both :ent-name and a :count > 1 in a query term" {:query-term query-term})))

    query-opts))

(defn add-ent-type-query
  "A query is composed of ent-type-queries, where each ent-type-query
  specifies the ents that should be created for that type. This
  function adds the ents for an ent-type-query."
  [ent-db ent-type-query ent-type]
  (reduce (fn [db query-term]
            ;; top-level meta is used to track which ents are
            ;; specified explicitly in a query

            (let [query-term             (with-meta query-term {:top-level true})
                  {:keys [count ent-name]} (conform-query-term query-term)]
              (if (> count 1)
                (add-n-ents db ent-type count query-term)
                (add-ent db ent-name ent-type query-term))))
          ent-db
          ent-type-query))

(defn add-ref-ents
  "Ents are added in two stages: first, all ents that are declared in
  the query are added. During that process, we keep track of ref-ents,
  ents which are automatically generated in order to satisfy
  relations. This function adds those ref ents if an ent of the same
  name doesn't exist already."
  [ent-db]
  (loop [{:keys [ref-ents] :as ent-db} ent-db]
    (if (empty? ref-ents)
      ent-db
      (recur (reduce (fn [db [ent-name ent-type query-term]]
                       (add-ent db ent-name ent-type query-term))
                     (assoc ent-db :ref-ents [])
                     ref-ents)))))

(defn init-db
  [{:keys [schema] :as ent-db} query]
  (let [rg (relation-graph schema)]
    (-> ent-db
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
  [{:keys [schema] :as ent-db} query]
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

  (throw-invalid-spec "db" ::ent-db ent-db)
  (throw-invalid-spec "query" ::query query)
  ;; end validations

  (let [ent-db (init-db ent-db query)]
    (->> (reduce (fn [db ent-type]
                   (if-let [ent-type-query (ent-type query)]
                     (add-ent-type-query db ent-type-query ent-type)
                     db))
                 ent-db
                 (:types ent-db))
         (add-ref-ents))))

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
  [ent-db ent-name related-ent relation-attr]
  (and (contains? (relation-attrs ent-db ent-name related-ent) relation-attr)
       related-ent))

(defn related-ents-by-attr
  "All ents related to ent via relation-attr"
  [{:keys [data] :as ent-db} ent-name relation-attr]
  (let [related-ents (lg/successors data ent-name)]
    (if (coll-relation-attr? ent-db ent-name relation-attr)
      (->> related-ents
           (map #(ent-related-by-attr? ent-db ent-name % relation-attr))
           (filter identity))
      (some #(ent-related-by-attr? ent-db ent-name % relation-attr)
            related-ents))))

(defn referenced-ent-attrs
  "seq of [referenced-ent relation-attr]"
  [{:keys [data] :as ent-db} ent-name]
  (for [referenced-ent (sort-by #(lat/attr data % :index) (lg/successors data ent-name))
        relation-attr  (relation-attrs ent-db ent-name referenced-ent)]
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
  [{:keys [data] :as ent-db} required-attrs]
  (assoc ent-db :data
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
  [ent-db]
  (topsort-ents (prune-required ent-db (required-attrs ent-db))))

(defn sort-ents
  "Attempts to topsort ents. If that's not possible (cycles present),
  uses `sort-by-required` to resolve ordering of ents in cycle"
  [ent-db]
  (let [sorted (or (seq (topsort-ents ent-db))
                   (sort-by-required ent-db))]
    (when (and (empty? sorted) (not-empty (ents ent-db)))
      (throw (ex-info "Can't sort ents: check for cycles in ent type relations. If a cycle is present, use the :required constraint to indicate ordering."
                      {})))
    sorted))

(defn visit-fn-data
  "When a visit fn is called, it's passed this map as its second argument"
  [ent-db ent visit-key]
  (let [attrs  (ent-attrs ent-db ent)
        q-opts (query-opts ent-db ent)
        base   {:ent-name         ent
                :attrs            attrs
                :visit-val        (visit-key attrs)
                :visit-key        visit-key
                :query-opts       q-opts
                :visit-query-opts (visit-key q-opts)
                :schema-opts      (visit-key (ent-schema ent-db ent))}]
    (merge attrs base)))

(defn visit-ents
  "Perform `visit-fns` on ents, storing return value as a graph
  attribute under `visit-key`"
  ([ent-db visit-key visit-fns]
   (visit-ents ent-db visit-key visit-fns (sort-ents ent-db)))
  ([ent-db visit-key visit-fns ents]
   (let [visit-fns (if (sequential? visit-fns) visit-fns [visit-fns])]
     (reduce (fn [ent-db [visit-fn ent]]
               (update ent-db :data lat/add-attr ent visit-key (visit-fn ent-db (visit-fn-data ent-db ent visit-key))))
             ent-db
             (for [visit-fn visit-fns ent ents] [visit-fn ent])))))

(defn visit-ents-once
  "Like `visit-ents` but doesn't call `visit-fn` if the ent already
  has a `visit-key` attribute"
  ([ent-db visit-key visit-fns]
   (visit-ents-once ent-db visit-key visit-fns (sort-ents ent-db)))
  ([ent-db visit-key visit-fns ents]
   (let [skip-ents (->> ents
                        (filter (fn [ent]
                                  (let [ent-attrs (get-in ent-db [:data :attrs ent])]
                                    (contains? ent-attrs visit-key))))
                        (set))
         visit-fns (if (sequential? visit-fns) visit-fns [visit-fns])]
     (visit-ents ent-db
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
  ([ent-db attr] (attr-map ent-db attr (ents ent-db)))
  ([{:keys [data]} attr ents]
   (->> ents
        (reduce (fn [m ent] (assoc m ent (lat/attr data ent attr)))
                {})
        (into (sorted-map)))))

(defn query-ents
  "Get seq of nodes that are explicitly defined in the query"
  [{:keys [data]}]
  (->> (:attrs data)
       (filter (fn [[_ent-name attrs]] (:top-level (meta (:query-term attrs)))))
       (map first)))

(defn ents-by-type
  "Given a db, returns a map of ent-type to a set of entities of that
  type. Optionally pass in a seq of the ents that should be included."
  ([ent-db] (ents-by-type ent-db (ents ent-db)))
  ([ent-db ents]
   (reduce-kv (fn [m k v] (update m v (fnil conj #{}) k))
              {}
              (select-keys (attr-map ent-db :ent-type) ents))))

(s/fdef ents-by-type
  :args (s/cat :ent-db ::ent-db :ent-names (s/? (s/coll-of ::ent-name)))
  :ret (s/map-of ::ent-type (s/coll-of ::ent-name)))

(defn ent-relations
  "Given a db and an ent, returns a map of relation attr to ent-name."
  [ent-db ent]
  (let [relations (get-in ent-db [:data :attrs ent :loom.attr/edge-attrs])]
    (apply merge-with
           set/union
           {}
           (for [[ref-ent {:keys [relation-attrs]}] relations
                 relation-attr relation-attrs]
             {relation-attr (if (coll-relation-attr? ent-db ent relation-attr)
                              #{ref-ent} ref-ent)}))))

(s/fdef ent-relations
  :args (s/cat :ent-db ::ent-db :ent-name ::ent-name)
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
  ([ent-db]
   (all-ent-relations ent-db (ents ent-db)))
  ([ent-db ents]
   (reduce-kv (fn [ents-by-type ent-type ents]
                (assoc ents-by-type ent-type
                       (into {}
                             (map (fn [ent]
                                    [ent (ent-relations ent-db ent)]))
                             ents)))
              {}
              (ents-by-type ent-db ents))))

(s/fdef all-ent-relations
  :args (s/cat :ent-db ::ent-db :ent-names (s/? (s/coll-of ::ent-name)))
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

(defn omit-relation?
  [ent-db ent-name reference-key]
  (-> ent-db
      (query-opts ent-name)
      (get-in [:refs reference-key])
      omit?))

(defn reset-relations
  "Generated data generates values agnostic of any schema constraints that may be
  present. This function updates values in the generated data to match up with
  constraints. First, it will remove any dummy ID's for a `:coll` relation.
  Next, it will remove any dummy ID's generated for an `:omit` relation. The
  updated ent-data map will be returned."
  [ent-db {:keys [ent-name visit-val]}]
  (let [coll-attrs (relation-attrs-with-constraint ent-db ent-name :coll)]
    (into {}
          (comp (map (fn [[k v]] (if (coll-attrs k) [k []] [k v])))
                (map (fn [[k v]] (when-not (omit-relation? ent-db ent-name k) [k v]))))
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
  [ent-db {:keys [ent-name visit-key visit-val]}]
  (let [{:keys [constraints]} (ent-schema ent-db ent-name)
        skip-keys             (::overwritten (meta visit-val) #{})]
    (->> (referenced-ent-attrs ent-db ent-name)
         (filter (comp (complement skip-keys) second))
         (reduce (fn [ent-data [referenced-ent relation-attr]]
                   (assoc-referenced-val ent-data
                                         relation-attr
                                         (get-in (ent-attr ent-db referenced-ent visit-key)
                                                 (:path (query-relation ent-db ent-name relation-attr)))
                                         constraints))
                 visit-val))))

(defn merge-overwrites
  "Overwrites generated data with what's found in schema-opts or
  visit-query-opts."
  [_ent-db {:keys [visit-val visit-query-opts schema-opts]}]
  (let [schema-overwrites (:overwrites schema-opts)
        merged (cond-> visit-val
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

(defn generate*
  "Use a generator to generate data for each ent"
  [ent-db]
  (let [base-generator (get-in ent-db [:generate :generator])
        visiting-fn    (wrap-generate-visiting-fn
                        (fn [db {:keys [ent-name visit-query-opts]}]
                          (let [ent-schema-generate       (generate-visit-key (ent-schema db ent-name))
                                visit-query-opts-generate (generate-visit-key visit-query-opts)
                                schema                    (or (:schema visit-query-opts-generate)
                                                              (:schema ent-schema-generate))
                                generator                 (or (:generator visit-query-opts-generate)
                                                              (:generator ent-schema-generate)
                                                              base-generator)]
                            (when-not generator
                              (throw (ex-info "No generator specified. Try adding [:generate :generator] to db" {})))
                            (when-not schema
                              (throw (ex-info "No generate schema provided" {})))
                            (generator schema))))]
    (visit-ents-once ent-db generate-visit-key visiting-fn)))

(defn generate
  [ent-db query]
  (-> ent-db
      (add-ents query)
      (generate*)))

(defn generate-attr-map
  [ent-db query]
  (-> ent-db
      (generate query)
      (attr-map generate-visit-key)))

;;---
;; fixtures
;;---

(def ^:const fixtures-visit-key :fixtures)
(def ^:dynamic *connection*)
(def ^:dynamic *ent-db*)

(defn wrap-incremental-insert-visiting-fn
  "Takes generated data stored as an attributed under `source-key` and inserts it
  using `inserting-visiting-fn`.

  Respects overwrites and ensures that referenced vals are assoc'd in before
  inserting. Useful when dealing with db-generated ids."
  [source-key inserting-visiting-fn]
  (fn incremental-insert-visiting-fn [ent-db opts]
    (reduce (fn [visit-val visiting-fn]
              (visiting-fn ent-db (assoc opts :visit-val visit-val)))
            (source-key opts)
            [merge-overwrites
             assoc-referenced-vals
             inserting-visiting-fn])))

(defn insert-fixtures*
  [{{:keys [perform-insert]} fixtures-visit-key
    :as                      ent-db}]
  (let [connection (or (get-in ent-db [fixtures-visit-key :connection])
                       (when-let [get-connection (get-in ent-db [fixtures-visit-key :get-connection])]
                         (get-connection)))]
    (visit-ents-once (assoc-in ent-db [fixtures-visit-key :connection] connection)
                     fixtures-visit-key
                     (wrap-incremental-insert-visiting-fn generate-visit-key perform-insert))))

(defn insert-fixtures
  [ent-db query]
  (-> ent-db
      (add-ents query)
      generate*
      insert-fixtures*
      (attr-map fixtures-visit-key)))

(defmacro with-fixtures
  [ent-db & body]
  `(let [ent-db# ~ent-db]
     (with-open [conn# (or (get-in ent-db# [:fixtures :connection])
                           (when-let [get-connection# (get-in ent-db# [:fixtures :get-connection])]
                             (get-connection# ent-db#)))]
       (binding [*connection* conn#]
         (when-let [setup# (get-in ent-db# [:fixtures :setup])]
           (setup# conn#))
         (try
           (binding [*ent-db* (assoc-in ent-db# [:fixtures :connection] conn#)]
             ~@body)
           (finally (when-let [teardown# (get-in ent-db# [:fixtures :teardown])]
                      (teardown# conn#))))))))
