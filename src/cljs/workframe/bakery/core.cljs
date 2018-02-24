(ns workframe.bakery.core
  (:require-macros [workframe.bakery.macros :refer [read-json]])
  (:require [reagent.core :as reagent]
            [reagent.ratom :refer [reaction]]
            [goog.string :as gstring]
            goog.string.format))

;;;; Helpers

(def format-usd
  (let [formatter (new (.-NumberFormat js/Intl)
                       "en-US"
                       #js {"style" "currency"
                            "currency" "USD"})]
    (fn [cents]
      (.format formatter (/ cents 100)))))

;;;; External data

(def data
  "Reads product data into memory. Also transforms floating point prices
  into cents to protect against rounding errors"
  (map
   (fn [product]
     (-> product
         (update-in [:price]
                    (fn [price] (int (* price 100))))
         (update-in [:bulkPricing]
                    (fn [bulkPricing]
                      (when (not (nil? bulkPricing))
                        (update-in bulkPricing [:totalPrice]
                                   (fn [totalPrice] (int (* totalPrice 100)))))))))
   (:treats (read-json "data.json"))))

;;;; State + state transitions

(def state
  "Global state atom for the app w/ initial data"
  (reagent/atom {:products/by-id (into (sorted-map)
                                 (map (fn [product] [(:id product) product]) data))
           :cart/orders-by-id {}
           :cart/orders []}))

(defmulti transition
  "Multi-method taking messages and deriving state transitions.
  Must be given dereferenced atom"
  (fn [state t message] t))

(defmethod transition :default [state _ _] state)

(defmethod transition :cart/add-order
  [state _ order]
  (let [{:keys [quantity product-id]} order]
    (if (get-in state [:cart/orders-by-id product-id])      
      (update-in state [:cart/orders-by-id product-id :quantity] + quantity)
      (-> state
          (update-in [:cart/orders-by-id] assoc
                     product-id
                     {:product-id product-id
                      :quantity quantity})
          (update-in [:cart/orders] conj product-id)))))

(defmethod transition :cart/checkout
  [state _ _]
  (let [messages ["As if you could afford this"
                  "Few too many calories, don't you think?"
                  "Reality check: you're buying donuts online"
                  "Uh, excuse me? We're closed"
                  "Why don't you finally make something out of yourself?\nYou're truly a disappointment, my son"]
        message (nth messages (rand-int (count messages)))]
    (.alert js/window message)
    state))

(defn dispatch!
  "Provides interface for components to initiate state transitions"
  ([t]
   (dispatch! t nil))
  ([t payload]
   (swap! state transition t payload)))

;;;; Computed state

(def get-products
  (reaction
   (let [s @state]
     (vals (:products/by-id s)))))

(def get-products-in-cart
  (reaction
   (let [s @state] ;; Would a cursor here increase perf?
     (map #(get-in s [:products/by-id %]) (:cart/orders s)))))

(defn get-price
  [product quantity]
  (let [{:keys [price]} product]    
    (if-let [bulk-pricing (:bulkPricing product)]
      (let [bulk-quantity (:amount bulk-pricing)
            bulk-price (:totalPrice bulk-pricing)]
        (+
         (* bulk-price (quot quantity bulk-quantity))
         (* price (mod quantity bulk-quantity))))
      (* price quantity))))

(def get-cart
  (reaction
   (let [s @state]
     (map #(let [product (get-in s [:products/by-id %])
                 order (get-in s [:cart/orders-by-id %])
                 quantity (:quantity order)]
             (assoc order
                    :product product
                    :price (get-price product quantity)))
          (:cart/orders s)))))

(def get-total-price
  (reaction
   (let [cart @get-cart]
     (reduce
      (fn [acc order] (+ acc (:price order)))
      0
      cart))))

;;;; Components

(defn product-snippet
  "Displays simple information about a product and provides an
   interface to add the product to the cart"
  [product]
  (let [{image-url :imageURL name :name price :price bulk-pricing :bulkPricing} product]
    [:div {:className ["product-snippet pa3 flex flex-column flex-row-l"]}
     [:div {:className "product-snippet__image self-center"}
      [:img {:className "db" :src image-url}]]
     [:div {:className "product-snippet__details dib flex flex-column justify-between self-stretch pa3"}
      [:div {:className ["product-snippet__details__name mb2"]}
       name]
      [:div {:className ["product-snippet__details__pricing mb2"]}
       (str      
        (format-usd price)
        (when (not (nil? bulk-pricing))
          (gstring/format
           " (or %s for %d)"
           (format-usd (:totalPrice bulk-pricing))
           (:amount bulk-pricing))))]
      [:button {:className ["btn btn--secondary product-snippet__add-cart pa1"]
                :onClick #(dispatch! :cart/add-order {:product-id (:id product)
                                                     :quantity 1})}
       "Add to Cart"]]]))

(defn product-list
  []
  (let [products @get-products]
    [:ul {:className ["product-list list ma0 pa0"]}
     (doall (map (fn [product] [:li {:className ["product-list__item"]
                                    :key (:id product)}
                               (product-snippet product)])
                 products))]))

(defn cart-item-list
  [cart]
  (let [total-price @get-total-price]
    (into
     [:ul {:className "cart-item-list list pa0 ma0 mb3"}]
     (conj (vec (map (fn [{:keys [quantity price product]}]
                       [:li {:className ["cart-item-list__item dib flex justify-between pa1"]
                             :key (:id product)}
                        [:div {:className "cart-item-list__name-col"}
                         (gstring/format "%s x %d" (:name product) quantity)]
                        [:div {:className "cart-item-list__price-col"}] (format-usd price)])
                     cart))
           [:li {:className "cart-item-list__item cart-item-list__item--total dub flex justify-between pa1"
                 :key "total"}
            [:span "Total"]
            [:span
             (format-usd total-price)]]))))

(defn cart
  []
  (let [cart @get-cart
        cart-empty (empty? cart)]
    [:div {:className "cart mw6-ns center pa4"}
     (if (not cart-empty)
       (cart-item-list cart)
       [:span "Cart empty"])
     [:button {:className ["btn btn--primary cart__checkout db pa2 mt3"]
               :disabled cart-empty
               :onClick #(dispatch! :cart/checkout)}
      "Checkout"]]))

(defn app
  []
  [:div {:className ["container"]}
   [:header]
   [:main {:className "main flex flex-column-reverse flex-row-ns mw9 center"}
    [:div {:className "product-list-container pa4"}
     (product-list)]
    [:div {:className "cart-container pa4"}
     (cart)]]
   [:footer]])

;;;; Entrypoint

(defn main []
  (reagent/render [app] (.querySelector js/document "#app")))
