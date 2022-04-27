(ns owlbear.ts.parse.rules
  (:require [oops.core :refer [oget ocall]]
            [owlbear.parse.rules :as obpr]
            [owlbear.utilities :as obu]))

(def jsx-closing-element "jsx_closing_element")
(def jsx-element "jsx_element")
(def ts-export-statement "export_statement")
(def jsx-expression "jsx_expression")
(def jsx-fragment "jsx_fragment")
(def jsx-opening-element "jsx_opening_element")
(def jsx-self-closing-element "jsx_self_closing_element")
(def jsx-text "jsx_text")
(def ts-abstract-class-declaration "abstract_class_declaration")
(def ts-arguments "arguments")
(def ts-array "array")
(def ts-assignment-expression "assignment_expression")
(def ts-binary-expression "binary_expression")
(def ts-call-expression "call_expression")
(def ts-class-body "class_body")
(def ts-class-declaration "class_declaration")
(def ts-comment "comment")
(def ts-comment-block "comment_block")
(def ts-error "ERROR")
(def ts-expression-statement "expression_statement")
(def ts-for-statement "for_statement")
(def ts-for-in-statement "for_in_statement")
(def ts-function-declaration "function_declaration")
(def ts-identifier "identifier")
(def ts-import-statement "import_statement")
(def ts-incomplete-pair "incomplete_pair")
(def ts-incomplete-property-signature "incomplete_property_signature")
(def ts-interface-declaration "interface_declaration")
(def ts-labeled-statement "labeled_statement")
(def ts-lexical-declaration "lexical_declaration")
(def ts-new-expression "new_expression")
(def ts-number "number")
(def ts-object "object")
(def ts-object-type "object_type")
(def ts-pair "pair")
(def ts-parenthesized-expression "parenthesized_expression")
(def ts-property-identifier "property_identifier")
(def ts-property-signature "property_signature")
(def ts-public-field-definition "public_field_definition")
(def ts-regex "regex")
(def ts-return-statement "return_statement")
(def ts-spread-element "spread_element")
(def ts-string "string")
(def ts-statement-block "statement_block")
(def ts-structural-body "structural_body")
(def ts-syntax "syntax")
(def ts-template-string "template_string")
(def ts-template-substitution "template_substitution")
(def ts-type-alias-declaration "type_alias_declaration")
(def ts-type-annotation "type_annotation")
(def ts-update-expression "update_expression")
(def ts-while-statement "while_statement")

(defn subject-node
  "Returns the given `node`
   if edit operations can be run from within the node 
   i.e. the node doing the slurping or barfing"
  [node]
  (let [node-type (obu/noget+ node :?type)]
    (cond (contains? #{jsx-element
                       jsx-expression
                       jsx-fragment
                       ts-arguments
                       ts-array
                       ts-class-body
                       ts-comment-block
                       ts-object
                       ts-object-type
                       ts-statement-block
                       ts-structural-body
                       ts-string
                       ts-template-string
                       ts-template-substitution}
                     node-type) node
          (= node-type ts-interface-declaration) (ocall node :?childForFieldName "body")
          :else nil)))

(defn subject-container
  "Given a [subject] `node`, 
   returns a container node for the given `node` if applicable 
   else returns the given `node` 
   e.g. for `const a = () => {return \" \";};` the statement block 
   is the subject node and the lexical declaration, while not 
   a subject node itself, is the subject-container node"
  [node]
  (when (subject-node node)
    (or (->> node
             obpr/node->ancestors
             reverse
             (some (fn [parent]
                     ;; When not a subject node and not the root node
                     (when (and (not (subject-node parent))
                                (not= (obu/noget+ parent :?id)
                                      (obu/noget+ parent :?tree.?rootNode.?id)))
                       parent))))
        node)))

(defn object-node
  "Returns the given `node` 
   if edit operations can be run against the node 
   i.e. the node being slurped or barfed"
  [node]
  (or (let [node-type (obu/noget+ node :?type)]
        (cond (contains? #{jsx-self-closing-element
                           jsx-text
                           ts-abstract-class-declaration
                           ts-assignment-expression
                           ts-binary-expression
                           ts-call-expression
                           ts-class-declaration
                           ts-comment
                           ts-error
                           ts-export-statement
                           ts-expression-statement
                           ts-for-statement
                           ts-for-in-statement
                           ts-function-declaration
                           ts-identifier
                           ts-import-statement
                           ts-incomplete-pair
                           ts-incomplete-property-signature
                           ts-interface-declaration
                           ts-labeled-statement
                           ts-lexical-declaration
                           ts-new-expression
                           ts-number
                           ts-object
                           ts-parenthesized-expression
                           ts-property-identifier
                           ts-public-field-definition
                           ts-regex
                           ts-return-statement
                           ts-spread-element
                           ts-string
                           ts-template-string
                           ts-type-alias-declaration
                           ts-type-annotation
                           ts-update-expression
                           ts-while-statement}
                         node-type) node
              (= node-type ts-pair) (ocall node :?childForFieldName "value")
              (= node-type ts-property-signature) (ocall node :?childForFieldName "type")
              :else nil))
      (subject-node node)))

(defn node->current-subject-nodes
  "Given a `node` and an `offset`, 
   returns a lazy seq of all the subject nodes containing that offset"
  [node offset]
  {:pre [(<= 0 offset)]}
  (some-> node
          obpr/flatten-children
          (->> (filter subject-node))
          (obpr/filter-current-nodes offset)))

(defn node->current-object-nodes
  "Given a `node` and an `offset`, 
   returns a lazy seq of all the object nodes containing that offset"
  [node offset]
  {:pre [(<= 0 offset)]}
  (some-> node
          obpr/flatten-children
          (->> (filter object-node))
          (obpr/filter-current-nodes offset)))

(defn next-forward-object-node [node]
  (obpr/some-forward-sibling-node object-node (subject-container node)))

(defn node->current-forward-object-ctx
  "Given a `node` and character `offset`, 
   returns a map of the deepest node (containing the `offset`)
   with a forward sibling object context"
  [node offset]
  {:pre [(<= 0 offset)]}
  (some->> (node->current-subject-nodes node offset)
           (keep (fn [current-node]
                   (when-let [forward-object-node (next-forward-object-node current-node)]
                     {:forward-object-node forward-object-node
                      :current-node current-node})))
           last))

(defn node->child-object-nodes [node]
  (when node
    (keep object-node (oget node :?children))))

(defn node->current-last-child-object-ctx
  "Given a `node` and character `offset`, 
   returns a map containing the deepest
   node containing the offset with object-node children (`current-node`) 
   and the last child-object node of that `current-node` (`last-child-object-node`)"
  [node offset]
  {:pre [(<= 0 offset)]}
  (some->> (node->current-subject-nodes node offset)
           (keep (fn [current-node]
                   (when-let [last-child-object-node (last (node->child-object-nodes current-node))]
                     {:last-child-object-node last-child-object-node
                      :current-node current-node})))
           last))

(defn node->first-child
  "Given a node, 
   returns the end node for that node if available"
  [node]
  (last (obu/noget+ node :?children)))

(defn node->last-child
  "Given a node, 
   returns the start node for that node if available"
  [node]
  (first (obu/noget+ node :?children)))