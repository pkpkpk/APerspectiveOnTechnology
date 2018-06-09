(ns cube.core
  (:require-macros [cube.core :as mac])
  (:require [neo.core :as neo]
            [neo.vec.mat4 :as mat4]
            [neo.vec.vec3 :as vec3]
            [neo.vec.euler :as euler]
            [neo.vec.quaternion :as quat]
            [neo.util :refer [applyEpsilon]]
            [neo.math :include-macros true :refer [pi -pi]]
            [neo.styles :as neo-styles :refer [xfstyles]]
            [goog.dom :as gdom]
            [reagent.core :as r]))

(defn update-state!
  ([component f]
   (swap! (r/state-atom component) f))
  ([component f arg]
   (swap! (r/state-atom component) f arg))
  ([component f arg0 arg1]
   (swap! (r/state-atom component) f arg0 arg1))
  ([component f arg0 arg1 arg2]
   (swap! (r/state-atom component) f arg0 arg1 arg2))
  ([component f arg0 arg1 arg2 arg3]
   (swap! (r/state-atom component) f arg0 arg1 arg2 arg3))
  ([component f arg0 arg1 arg2 arg3 & arg-rest]
   (reset! (r/state-atom component) (apply f (r/state component) arg0 arg1 arg2 arg3 arg-rest))))

(defn rotate-cube [cube next-rotation]
  (let [{:keys [rotating?] {rotation :rotation} :xfs, cube? :cube?} (r/state cube)]
    (when (and cube? (not rotating?))
      (neo/tween! rotation
        {:from @rotation
         :to next-rotation
         :duration 500
         :curve :ease-in
         :onStart #(update-state! cube assoc-in [:rotating?] true)
         :onFinish (fn []
                     (update-state! cube assoc-in [:rotating?] false)
                     (applyEpsilon @rotation))}))))

(defn rotateOnWorldAxis [cube [x y z] angle]
  (let [{{rotation :rotation} :xfs} (r/state cube)
        axis #js[x y z]
        q (quat/quaternion)
        _(quat/normalize @rotation)
        _(quat/setFromAxisAngle q axis angle)
        next-rotation (quat/premult (quat/qclone @rotation) q)]
    (rotate-cube cube next-rotation)))

(defn center-side [cube [x y z :as eye]]
  (let [{:keys [up mat] {rotation :rotation} :xfs} (r/state cube)
        _(quat/normalize @rotation)
        _(mat4/lookAt mat (into-array eye) (vec3/vec3 0 0 1) up)]
    (rotate-cube cube (quat/mat4->quat mat))))

(defonce cube (atom nil))

(defn get-rotation [] (-> @cube r/state :xfs :rotation deref))
(defn get-direction [] (-> @cube r/state :direction ))

(def rotation-delta (quat/quaternion 1 1 1 185))

(defn alter-delta []
  (let [x (dec (* (rand) 2))
        y (dec (* (rand) 2))
        z (dec (* (rand) 2))]
    (quat/setFromValues rotation-delta x y z 185)))

(defonce idling? (atom nil))

(defonce idle
  (fn []
    (let [rotation (get-in (r/state @cube) [:xfs :rotation])]
      (quat/mult @rotation rotation-delta)
      (.forceUpdate @cube))))

(defn start-idle []
  (when-not @idling?
    (neo/every-tick idle)
    (reset! idling? true)))

(defn cancel-idle []
  (neo/cancel-every-tick idle)
  (reset! idling? false))

(defn toggle-idle [] (if ^boolean @idling? (cancel-idle) (start-idle)))

(defn center [] (cancel-idle) (center-side  @cube [0 0 1]) )
(defn up [] (rotateOnWorldAxis @cube [1 0 0] (/ pi 2)))
(defn down [] (rotateOnWorldAxis @cube [1 0 0] (/ pi -2)))
(defn left [] (rotateOnWorldAxis @cube [0 1 0] (/ pi -2)))
(defn right [] (rotateOnWorldAxis @cube [0 1 0] (/ pi 2)))
(defn cw [] (rotateOnWorldAxis @cube [0 0 1] (/ pi 2)))
(defn counter-cw [] (rotateOnWorldAxis @cube [0 0 1] (/ pi -2)))

(defn zoom-in
  "make content visible"
  [this]
  (let [{:keys [blur scale opacity content-scale w h]}(get (r/state this) :xfs)
        {{:keys [toggle-cube scale-factor]} :parent size :size} (r/props this)]
    (toggle-cube)
    (neo/tween! content-scale
                {:from 0.8
                 :to 1
                 :duration 500
                 :curve :ease-in})
    (neo/tween! scale
                {:from 1
                 :to scale-factor
                 :duration 500
                 :curve :ease-in})
    (neo/tween! blur
                {:to 0
                 :duration 500
                 :curve :ease-in})
    (neo/tween! opacity
                {:to 0
                 :duration 500
                 :curve :ease-in})))

(defn zoom-out "fuzz out content and return to cube state" [this]
  (let [{:keys [blur scale opacity content-scale]}(get (r/state this) :xfs)
        {{:keys [toggle-cube scale-factor]} :parent up :up} (r/props this)]
    (toggle-cube)
    (neo/tween! scale
                {:from scale-factor
                 :to 1
                 :duration 500
                 :curve :linear})
    (neo/tween! content-scale
                {:from 1
                 :to 0.8
                 :duration 500
                 :curve :linear})
    (neo/tween! blur
                {:to 2.5
                 :duration 500
                 :curve :linear})
    (neo/tween! opacity
                {:to 1
                 :duration 500
                 ; :onFinish #(start-idle)
                 :curve :linear})))

(defn toggle-scale [this]
  (if ^boolean (get-in (r/props this) [:parent :cube?])
    (zoom-in this)
    (zoom-out this)))

(def side-onClick
  (let [next-rotation (quat/quaternion)]
    (fn [this]
      (let [{{:keys [cube? rotation toggle-cube mat]} :parent up :up} (r/props this)]
        (when @idling?
          (cancel-idle))
        (if (up (vec (array-seq @rotation)))
          (toggle-scale this)
          (let [u (first up)]
            (quat/setFromValues next-rotation (nth u 0) (nth u 1) (nth u 2) (nth u 3))
            (quat/normalize next-rotation)
            (neo/tween! rotation
                        {:from @rotation
                         :to next-rotation
                         :duration 500
                         :curve :ease-in
                         :onFinish #(toggle-scale this)})))))))


(defn Side [& arg]
  (r/create-class
   {:getInitialState
    (fn [this]
      (let [{[w h d :as size] :size} (r/props this)]
        {:xfs {:scale (neo/tweener 1)
               :blur (neo/tweener 2.5)
               :opacity (neo/tweener 1)
               :content-scale (neo/tweener 0.8)}}))
    :componentWillMount
    (fn [this]
      (neo/register-owner this (get (r/state this) :xfs)))
    :componentWillUnmount
    (fn [this] (neo/deregister-owner this))
    :reagent-render
    (fn [{:keys [key styles transform label html up] [w h d :as size] :size,
         {:keys [cube?]} :parent :as props}]
      (let [this (r/current-component)
            {{:keys [blur content-scale scale opacity]} :xfs} (r/state this)
            cube-xf (mat4/->CSS (mat4/scale! (mat4/mclone transform) @scale))
            content-xf (mat4/->CSS (mat4/scale @content-scale @content-scale @content-scale))
            color "lime"]
        [:div {:id key
               :onClick #(side-onClick this)
               :style (merge neo.styles/styles styles
                             {:width (str w "px")
                              :height (str h "px")
                              :border (str "solid 1px " color)
                              :backfaceVisibility "hidden"
                              :WebkitTransform cube-xf
                              :transform cube-xf})}
         [:span {:style {:position "absolute"
                         :color color
                         :top "50%"
                         :left "50%"
                         :WebkitTransform "translate(-50%,-50%)"
                         :transform "translate(-50%,-50%)"
                         :fontSize "50px"
                         :opacity @opacity}} label]
         [:div {:style {:WebkitTransform content-xf
                        :transform content-xf
                        :filter (str "blur(" @blur "px)")
                        :overflow (if cube? "hidden" "auto")
                        :backfaceVisibility (if cube? "visible" "hidden")
                        :width "100%"
                        :height "100%"}
                :dangerouslySetInnerHTML {:__html html}}]]))}))

(defn side->xf
  [key [w h depth :as size] root-size global-xf]
  (case key
    :front
    (let [local (mat4/translate 0 0 (/ depth 2))]
      (mat4/mult global-xf local local))
    :back
    (let [local (mat4/pre-translate! (mat4/rotateX! (mat4/rotateZ pi) pi) 0 0 (/ depth (- 2)))]
      (mat4/mult global-xf local local))
    :top
    (let [local (mat4/pre-translate! (mat4/rotateX (/ pi 2)) 0 (/ (- h) 2) 0)]
      (mat4/mult global-xf local local))
    :left
    (let [local (mat4/pre-translate! (mat4/rotateY (/ -pi 2)) (/ (- w) 2) 0 0)]
      (mat4/mult global-xf local local))
    :right
    (let [local (mat4/pre-translate! (mat4/rotateY (/ pi 2)) (/ w 2) 0 0)]
      (mat4/mult global-xf local local))
    :bottom
    (let [local (-> (mat4/rotateX (/ pi (- 2)))
                    (mat4/rotateZ! pi)
                    (mat4/pre-translate! 0 (/ h 2) 0))]
      (mat4/mult global-xf local local))

    (throw (js/Error. (str "unrecognized cube key " key)))))

(def ^{:doc "directions for reorienting a face when expanding"}
  key->up
  (let [sin45 (js/Math.sin (/ pi 4))]
    (hash-map
      :front #{[0 0 0 1] [0 0 0 -1]}
      :left #{[0 sin45 0 sin45] [0 (- sin45) 0 (- sin45)]}
      :right #{[0 (- sin45) 0 sin45] [0 sin45 0 (- sin45)]}
      :back #{[0 -1 0 0] [0 1 0 0]}
      :top #{[(- sin45) 0 0 sin45] [sin45 0 0 (- sin45)]}
      :bottom #{[0 sin45 sin45 0] [0 (- sin45) (- sin45) 0]})))

(def key->dir
  {:front [0 0 1]
   :back [0 0 -1]
   :left [1 0 1]
   :right [-1 0 1]
   :top [0 1 0]
   :bottom [0 1 0]})

(defn cube-sides [cubes {:keys [root-size rotation]
                         [w h depth :as size] :cube-size
                         :as parent-data}]
  (let [center-xf (mat4/relative-xf {:psize root-size
                                     :size size
                                     :align [0.5 0.5]
                                     :origin :center})
        global-xf (mat4/mult center-xf (mat4/quat->mat4 @rotation))]
    (into []
      (map
        (fn [{key :key :as cube}]
          (assoc cube
                 :size size
                 :up (key->up key)
                 :direction (key->dir key)
                 :parent parent-data
                 :transform (side->xf key size root-size global-xf))))
      cubes)))

(defn Cube [cube-data {:keys [root-size cube-size] :as dimensions}]
  (let []
    (r/create-class
     {:getInitialState
      (fn [this]
        (let [origin (vec3/vec3 0 0 1)
              direction (vec3/vec3 0 0 1)
              rotation (quat/quaternion 0 0 0 1)
              _(.onChange rotation #(applyEpsilon (vec3/applyQuaternion origin rotation direction)))]
          {:rotating? false
           :mat (mat4/ident)
           :direction direction
           :up (vec3/vec3 0 1 0)
           :xfs {:rotation (neo/tweener rotation)}
           :cube? true
           :toggle-cube #(update-state! this update-in [:cube?] not)}))
      :componentWillMount
      (fn [this] (neo/register-owner this (:xfs (r/state this))))
      :componentWillUnmount
      (fn [this]
        (cancel-idle)
        (neo/deregister-owner this))
      :componentDidMount
      (fn [this]
        (reset! cube this)
        (start-idle)
        (neo/start!))
      :reagent-render
      (fn [& args]
        (let [this (r/current-component)
              {{rotation :rotation} :xfs, cube? :cube?} (r/state this)
              parent-data (merge dimensions (r/state this) {:rotation rotation})]
          [:div {:style {:perspective "1000"}}
           (for [s (cube-sides cube-data parent-data)]
             [Side s])]))})))

(defn button [{:keys [onClick label left]}]
  [:button {:onClick onClick
            :style {:width "50px"
                    :height "25px"
                    :color "black"}} label])

(defn RootView [cube-data [w h :as root-size]]
  (let [side-length (* .25 w)
        scale-factor 4
        cube-size [side-length side-length side-length]
        dimensions {:root-size root-size
                    :cube-size [side-length side-length side-length]
                    :scale-factor 2}]
    [:div {:style {:backgroundColor "#1e1e1e"
                   :color "white"
                   :height (str h "px")
                   :width (str w "px")}}
     [Cube cube-data dimensions]
     [:div {:style {}}
      [:div
       (button {:onClick counter-cw  :label "🔄"})
       (button {:onClick up :label "⬆️" :left "50%"})
       (button {:onClick cw  :label "🔃"})]
      [:div {}
       (button {:onClick left :label "⬅️"})
       (button {:onClick center :label "O"})
       (button {:onClick right :label "➡️"})]
      [:div {:style {:position "relative" :left "50px"}}
       (button {:onClick down :label "⬇️"})]]
     [:button {:onClick toggle-idle
               :style {:color "black"}} "toggle-idle"]]))

(when-let [node (gdom/getElement "app")]
  (let [root-size (neo/get-vp)]
    (r/render-component (RootView (mac/data) root-size) node)))
