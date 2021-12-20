(ns owlbear.grammar
  (:require
   [antlr4 :as a4]
   [owlbear-grammar :as obg]
   [owlbear.utilities :as obu]
   [oops.core :refer [oget oget+ ocall]]
   [clojure.string :as string]))

(def rule-index-map
  {:html-document 0
   :html-elements 2
   :html-element  3})

(def token-type-map
  {:tag-open 10
   :tag-close 12
   :tag-name 16
   :tag-slash 14})

(defn src->html-document-ctx
  "Given a src string, returns an HTML document parser tree"
  [src]
  (let [chars (a4/InputStream. (str src))
        lexer (obg/HTMLLexer. chars)
        token-stream (a4/CommonTokenStream. lexer)
        parser (obg/HTMLParser. token-stream)
        _ (set! (.-buildParseTrees parser) true)]
    (.htmlDocument parser)))


(defn html-element-ctx?
  "Given a context, 
   returns true if the context is an HTML element context"
  [ctx]
  (and (some? ctx)
       (= (:html-element rule-index-map) (oget ctx :?ruleIndex))))

(defn html-elements-ctx?
  "Given a context, 
   returns true if the context is an HTML elements context"
  [ctx]
  (and (some? ctx)
       (= (:html-elements rule-index-map) (oget ctx :?ruleIndex))))

(defn tag-name-ctx?
  "Given a context, 
   returns true if the context is an HTML tag name"
  [ctx]
  (and (some? ctx)
       (= (:tag-name token-type-map) (oget ctx :?symbol.?type))))

(defn tag-name-ctx
  "Given a context, 
   returns the context if tag-name-ctx? returns true"
  [ctx]
  (when (tag-name-ctx? ctx)
    ctx))

(defn tag-open-bracket-ctx?
  "Given a context, 
   returns true if the context is an HTML tag open angular bracket e.g. <"
  [ctx]
  (and (some? ctx)
       (= (:tag-open token-type-map) (oget ctx :?symbol.?type))))

(defn tag-open-bracket-ctx
  "Given a context, 
   returns the context if tag-open-bracket-ctx? returns true"
  [ctx]
  (and (tag-open-bracket-ctx? ctx) ctx))

(defn tag-close-bracket-ctx?
  "Given a context, 
   returns true if the context is an HTML tag close angular bracket e.g. >"
  [ctx]
  (and (some? ctx)
       (= (:tag-close token-type-map) (oget ctx :?symbol.?type))))

(defn tag-close-bracket-ctx
  "Given a context, 
   returns the context if tag-close-bracket-ctx? returns true"
  [ctx]
  (and (tag-close-bracket-ctx? ctx) ctx))

(defn html-element-ctx
  "Given a context, 
   returns the context if html-element-ctx? returns true or
   returns the first HTML element context if the given context is an HTML elements context"
  [ctx]
  (cond
    (html-element-ctx? ctx) ctx
    (html-elements-ctx? ctx) (oget ctx :children.0)
    :else nil))

(defn filter-html-elements-ctxs
  "Given a list of contexts, 
   returns a filtered sequence of only the HTML element contexts"
  [ctxs]
  (filter
   (fn [ctx]
     (when (html-element-ctx? ctx)
       ctx))
   ctxs))

(defn ctx->seq
  "Given a context, 
   returns a flattened sequence of all of the context's children"
  [ctx]
  (tree-seq #(oget % :?children) #(oget % :children) ctx))

(defn ctx->html-elements-ctxs
  "Given a context, 
   returns a flattened sequence of only the HTML elements in the context"
  [ctx]
  (filter-html-elements-ctxs (ctx->seq ctx)))

(defn range-in-html-element-ctx?
  "Given an HTML element context, a start offset, and a stop offset,
   returns true if the HTML element context is within the given range (inclusive)"
  ([ctx start]
   (range-in-html-element-ctx? ctx start start))
  ([ctx start stop]
   (and start
        stop
        (html-element-ctx? ctx)
        (let [ctx-start (oget ctx :?start.?start)
              ctx-stop (oget ctx :?stop.?stop)]
          (and
           ctx-start
           ctx-stop
           (apply <=
                  (concat [ctx-start]
                          (range start (inc stop))
                          [ctx-stop])))))))

(defn root-html-element-ctx?
  "Given an HTML element context, 
   returns true if the context is a (tree) root HTML element context"
  [ctx]
  (html-elements-ctx? (oget ctx :?parentCtx)))

(defn forward-sibling-html-element-ctx?
  "Given an HTML element context and a second HTML element context, 
   returns true if the second context is positionally ahead of the first context"
  [ctx forward-ctx]
  (and (or (html-element-ctx? ctx) (html-elements-ctx? ctx))
       (or (html-element-ctx? forward-ctx) (html-elements-ctx? forward-ctx))
       (< (oget ctx :?stop.?stop) (oget forward-ctx :?start.?start))))

(defn forward-sibling-html-element-ctx
  "Calls forward-sibling-html-element-ctx? and returns the second context 
   if forward-sibling-html-element-ctx? returns true"
  [ctx forward-ctx]
  (when (forward-sibling-html-element-ctx? ctx forward-ctx)
    forward-ctx))

(defn next-sibling-html-element-ctx
  "Given a context (and optionally a parent context), 
   returns the next sibling HTML element context"
  ([ctx]
   (when-let [parent-ctx (oget+ ctx (if (root-html-element-ctx? ctx)
                                      :?parentCtx.?parentCtx
                                      :?parentCtx))]
     (next-sibling-html-element-ctx ctx parent-ctx)))
  ([ctx parent-ctx]
   (some
    (comp html-element-ctx (partial forward-sibling-html-element-ctx ctx))
    (oget parent-ctx :?children))))

(defn start-tag
  "Given an HTML element context, 
   returns a map representing the HTML element's start tag, 
   containing a vector of tag contexts (`:contexts`), 
   a string tag name (`:tag-name`), 
   a start index position of the contexts (`:start-index`), 
   a stop index position of the contexts (`:stop-index`), 
   a tag start offset (`:start-offset`), 
   and a tag stop offset (`:stop-offset`)"
  [ctx]
  (when-let [ctx-children (oget ctx :?children)]
    (let [[tag-open-bracket-index
           tag-close-bracket-index] (keep-indexed
                                     (fn [index ctx]
                                       (when (or (tag-open-bracket-ctx? ctx)
                                                 (tag-close-bracket-ctx? ctx))
                                         index))
                                     ctx-children)]
      (when (and (number? tag-open-bracket-index) (number? tag-close-bracket-index))
        (let [[tag-open-bracket-ctx*
               :as tag-ctxs] (subvec (vec ctx-children)
                                     tag-open-bracket-index
                                     (inc tag-close-bracket-index))
              tag-close-bracket-ctx* (last tag-ctxs)]
          {:contexts tag-ctxs
           :tag-name (some-> (some tag-name-ctx tag-ctxs) (ocall :?getText))
           :start-index tag-open-bracket-index
           :stop-index tag-close-bracket-index
           :start-offset (oget tag-open-bracket-ctx* :?symbol.?start)
           :stop-offset (oget tag-close-bracket-ctx* :?symbol.?stop)})))))

(defn end-tag
  "Given an HTML element context, 
   returns a map representing the HTML element's end tag, 
   containing a vector of tag contexts (`:contexts`), 
   a string expected tag name based on the start tag's name (`:expected-tag-name`), 
   a string actual tag name (`:tag-name`), 
   a start index position of the contexts (`:start-index`), 
   a stop index position of the contexts (`:stop-index`), 
   a tag start offset (`:start-offset`), 
   and a tag stop offset (`:stop-offset`)"
  [ctx]
  (when-let [ctx-children (oget ctx :?children)]
    (when-let [{:keys [tag-name]
                start-tag-stop-index :stop-index} (start-tag ctx)]
      (let [[tag-open-bracket-index
             tag-close-bracket-index] (keep-indexed
                                       (fn [index ctx]
                                         (when (and (> index start-tag-stop-index)
                                                    (or (tag-open-bracket-ctx? ctx)
                                                        (tag-close-bracket-ctx? ctx)))
                                           index))
                                       ctx-children)]
        (when (and (number? tag-open-bracket-index) (number? tag-close-bracket-index))
          (let [[tag-open-bracket-ctx*
                 :as tag-ctxs] (subvec (vec ctx-children)
                                       tag-open-bracket-index
                                       (inc tag-close-bracket-index))
                tag-close-bracket-ctx* (last tag-ctxs)]
            {:contexts tag-ctxs
             :expected-tag-name tag-name
             :tag-name (some-> (some tag-name-ctx tag-ctxs) (ocall :?getText))
             :start-index tag-open-bracket-index
             :stop-index tag-close-bracket-index
             :start-offset (oget tag-open-bracket-ctx* :?symbol.?start)
             :stop-offset (oget tag-close-bracket-ctx* :?symbol.?stop)}))))))

(defn current-html-element-ctxs
  "Given a context and a character offset, 
   returns a lazy sequence of the HTML element contexts containing the given offset"
  [ctx offset]
  (filter #(range-in-html-element-ctx? % offset) (ctx->html-elements-ctxs ctx)))

(defn src-with-cursor-symbol->html-element-ctx-map
  "Given a src string (and optionally a string), 
   return the HTML element context at the cursor symbol"
  ([src]
   (src-with-cursor-symbol->html-element-ctx-map src "📍"))
  ([src cursor-symbol]
   (let [cursor-symbol-start-offset (string/index-of src cursor-symbol)
         cursor-symbol-length (count cursor-symbol)
         cursor-symbol-stop-offset (+ cursor-symbol-start-offset cursor-symbol-length)
         actual-cursor-symbol-offset (- cursor-symbol-start-offset cursor-symbol-length)
         src-without-cursor-symbol (obu/str-remove src cursor-symbol-start-offset cursor-symbol-stop-offset)
         html-doc-ctx (src->html-document-ctx src-without-cursor-symbol)
         current-html-element-ctx (last (current-html-element-ctxs html-doc-ctx actual-cursor-symbol-offset))]
     {:current-ctx current-html-element-ctx
      :src-without-cursor-symbol src-without-cursor-symbol
      :cursor-offset actual-cursor-symbol-offset})))