(ns mud.core
  (:use [overtone.live])
  (:require [mud.timing :as time]
            [overtone.sc.machinery.server.comms :refer [with-server-self-sync server-sync]]))

(def overtime-default-sleep 200)

(defn- arg-count [f]
  {:pre [(instance? clojure.lang.AFunction f)]}
  (-> f class .getDeclaredMethods first .getParameterTypes alength))

(defn ctl-global-clock [rate] (ctl time/root-s :rate rate))

(defn ctl-time
  "Quickly set the timing rate on a synth."
  [node rate]
  (cond
   (and (map? node) (:synths node)) (ctl-time (:synths node) rate)
   (sequential? node) (doseq [n node] (ctl-time n rate))
   :else
    (ctl node :beat-trg-bus (:beat rate) :beat-bus (:count rate))))

(defn pattern!
  "Fill a buffer repeating pattern if required.
   Supports integers or notes which will be converted to midi notes"
  [buf & lists]
  (if (and (= 1 (count lists)) (var? (first lists)))
    (apply pattern! buf (var-get (first lists)))
    (buffer-write! buf (take (buffer-size buf) (cycle (map #(if (keyword? %) (note %) %) (flatten lists)))))))

(defn pattern-at!
  "Exactly as `pattern!` but only writes on a beat."
  [buf beat n & lists]
  (on-trigger
   (:trig-id beat)
   (fn [b]
     (when (= 0.0 (mod b n))
       (apply pattern! (concat [buf] lists))
       (remove-event-handler ::pattern-writer))) ::pattern-writer))

(defn pattern-repeat!
  [buf beat n & lists]
  (on-trigger
   (:trig-id beat)
   (fn [b]
     (when (= 0.0 (mod b n))
       (apply pattern! (concat [buf] (map apply lists)))
       )) ::pattern-repeat-writer))

;;(remove-event-handler ::pattern-writer)

(defn pattern-seq!
  "Fill a buffer repeating pattern if required. Support expressing patterns with `x` and `o`.
     For example: `oooxxoo`"
  [buf & lists]
  (let [buf-lists (map (fn [list] (if (string? list)
                                    (map #(Integer/parseInt %)
                                         (-> list
                                             (clojure.string/replace #"o" "0")
                                             (clojure.string/replace #"x" "1")
                                             (clojure.string/split #"")))
                                    list))
                       lists)]
          (pattern! buf buf-lists)))

(defn n-overtime!
  "For a synth instance node over time change val of `field` to end"
  ([node field start end] (n-overtime! node field start end 0.01))
  ([node field start end rate]
     (cond
      (and (map? node) (:synths node)) (n-overtime! (:synths node) field start end rate)

      (sequential? node) (doseq [n node] (n-overtime! n field start end rate))

      :else
      (letfn [(change-fn [val]  (if (< end start)
                                  (if (< (- val rate) end)
                                    end
                                    (- val rate))
                                  (if (> (+ val rate) end)
                                    end
                                    (+ val rate))))]
        (future
          (loop [val start]
            (Thread/sleep overtime-default-sleep)
            (ctl node field val)
            (when (not= val end)
              (recur (change-fn val)))))))))

(defn overtime!
  "Change an atom or a sequence of atoms to a `target` value at some `rate` of change.
   If `rate` is a func it will be called per atom value (so they may all have different
   targets.

   Example:
   (overtime! [(atom 0.1) (atom 0.3)] #(rand 1.0))
  "
  ([thing target] (overtime! thing target 0.1))
  ([thing target rate]
     (let [things (if-not (vector? thing) [thing] thing)
           things-and-targets (map vector things (repeatedly #(if (fn? target) (target) target)))]
       (doseq [[thing target] things-and-targets]
         (letfn [(change-fn [val]  (if (< target @thing)
                                     (if (< (- val rate) target)
                                       target
                                       (- val rate))
                                     (if (> (+ val rate) target)
                                       target
                                       (+ val rate))))]
           (future (loop []
                     (Thread/sleep overtime-default-sleep)
                     (swap! thing change-fn)
                     (when (not= @thing target) (recur)))))))))

(defn fadein
  ([node]     (fadein node 1.0 0.1))
  ([node amp] (fadein node 1.0 0.1))
  ([node amp rate] (n-overtime! node :amp 0 amp rate)))
(defn fadeout
  ([node] (fadeout node 0.1))
  ([node rate]
     (n-overtime! node :amp 1 0 rate)))

(defn fadeout-master
  ([] (fadeout-master 1))
  ([current] (n-overtime! (foundation-output-group) :master-volume current 0 0.05)))

(def _ nil)
(defn degrees
  "Convert degrees into pitches. If degree > 7 will automatically move to the next
   octave degree."
  ([ds]   (degrees ds :major :A3))
  ([ds n] (degrees ds :major n))
  ([ds scale n]
      (let [root (note n)]
        (map (fn [degree]
               (if degree
                 (+ root (degree->interval degree scale))
                 0)) ds))))

(defn degrees-seq
  "A conciser way to express cross root degrees
  Examples:
  (degrees-seq [:F4 1 3 1 4 :C3 6 7 :F3 7 7] :minor)
  (degrees-seq [:F4 1314 :C3 67 :F3 77] :minor)

  (degrees-seq [:F4 1 3 1 4 :C3 6 7 :F3 7 7]) ;; Uppercase == major
  (degrees-seq [:f4 1 3 1 4])                 ;; Lowercase == minor`
  (degrees-seq \"F3 1314 C2 141456\")"
  [notes & [scale]]
  (if (string? notes)
    (recur (->> (clojure.string/replace notes #"\s+" "")
                (re-seq #"(\D\d|\d)")
                (map last))
           scale)
    (mapcat
     (fn [[[root] score]]
       (let [scale (or scale (if (Character/isUpperCase (get (name root) 0)) :major :minor))]
         (degrees score scale root)))
     (partition 2 (partition-by
                   #(or (keyword? %1) (re-find #"(?i)^[ABCDEFG]" (str %1)))
                   (reduce (fn [accum pattern]
                             (cond
                              (and (integer? pattern) (> pattern 9))
                              (concat accum
                                      (map #(Integer/parseInt (str %1))
                                           (remove clojure.string/blank?
                                                   (clojure.string/split (str pattern) #""))))

                              (and (string? pattern) (re-find #"^[1-9]" pattern))
                              (concat accum [(Integer/parseInt pattern)])

                              :else
                              (concat accum [pattern])))
                           [] notes))))))

(def _beat-trig-idx_ (atom []))

(defn one-time-beat-trigger
  [beat beats func]
  (on-trigger (:trig-id time/main-beat)
              (fn [b] (when (= beat  (int (mod b beats)))
                       (remove-event-handler ::one-time-beat-trigger)
                       (func)))
              ::one-time-beat-trigger))

(defn on-beat-trigger [beat func]
  (let [trigger-name (str "on-beat-trigger-" (gensym))
        func-arg-count (arg-count func)]
    (swap! _beat-trig-idx_ concat [trigger-name])
    (on-trigger (:trig-id time/main-beat)
                (fn [b] (when (= 0.0 (mod b beat))
                         (if (= 0 func-arg-count)
                           (func)
                           (func b)))) trigger-name)
    trigger-name))

(defn remove-beat-trigger [id]
  (reset! _beat-trig-idx_ (remove #(= %1 id) @_beat-trig-idx_))
  (remove-event-handler id))

(defn remove-all-beat-triggers []
  (doseq [trigger-name @_beat-trig-idx_]
    (remove-event-handler trigger-name))
  (reset! _beat-trig-idx_ []))

(defn randomly-trigger
  ([change-fn] (randomly-trigger change-fn 0.5 8))
  ([chance at-beat change-fn]
      (def random-counter (atom 0))
      (on-trigger (:trig-id time/beat-1th)
                  (fn [& _]
                    (swap! random-counter inc)
                    (when (and (= 0 (mod @random-counter at-beat))
                               (> (rand) chance)) (change-fn)))
                  ::beat-picker)))

(def _sample-trig-idx_ (atom []))
(defn sample-trigger
  ([pattern sample-fn]
     (sample-trigger
      (->> pattern
           (map vector (range) pattern)
           (filter (fn [[idx beat]] (= beat 1)))
           (map first))
      (count pattern) sample-fn))
  ([start size sample-fn]
     (let [start (if (sequential? start) start [start])
           trigger-name (str "sample-trigger-" (gensym))
           fn-arg-count (arg-count sample-fn)]
       (swap! _sample-trig-idx_ concat [trigger-name])
       (on-trigger (:trig-id time/beat-1th)
                   (fn [b]
                     (when-let [beat (int (mod b size))]
                       (when (some #{beat} start)
                         (if (= 0 fn-arg-count)
                           (sample-fn)
                           (sample-fn b)))))
                   trigger-name)
       trigger-name)))

(comment
  (def kick-s (freesound 131336))
  (sample-trigger [1 0 0 0 1 0 0 0
                   1 0 0 0 1 0 0 0
                   1 0 0 0 1 0 0 0
                   1 0 0 0 1 1 1 1] (fn [beat]
                                      (println beat)
                                      (kick-s)))
  (remove-all-sample-triggers)
)

(defn remove-all-sample-triggers []
  (doseq [trigger-name @_sample-trig-idx_]
    (remove-event-handler trigger-name))
  (reset! _sample-trig-idx_ []))

(defn stutter [rate]
  (future
    (do
      (ctl time/root-s :rate (- 0 rate))
      (Thread/sleep 300)
      (ctl time/root-s :rate rate))))

(defn pause-time [] (ctl time/root-s :rate 0))
(defn play-time [rate] (ctl time/root-s :rate rate))

(defn note-at-octave [note octave] (keyword (str (name note) octave)))

(defmacro defasynth
 "Behaves the same as Overtone's `defsynth` except it supports array arguments.
  When passed an array of floats/int/doubles it will create a buffer and use these as its default values.
  Within the synth the buffer will only be referable by its id. We still have to use buffer functions to
  access its contents.

  For each Synth definition we keep a reference of the id of the buffers that have been allocated.

  An example:
  ````
   (defasynth sin-ly [notes [30 30 30 30 30] amp 1 beat-bus 0]
     (let [cnt (in:kr beat-bus)
           note (buf-rd:kr 1 notes cnt)
           freq (midicps note)
           src (sin-osc freq)]
       (out 0 (pan2 (* amp src)))))

   (def siz (sin-ly :beat-bus (:count time/main-beat)))

   (actl siz :notes [50 50 60 75 75] :amp 0.2)
   (actl siz :amp 1)
   ```"
 [s-name & s-form]
 (let [param-pairs (partition 2 (first s-form))
       buf-args (filter (fn [[_ v]] (and (vector? v) (every? number? v))) param-pairs)
       buf-args (map (fn [[k v]]
                       (let [b (buffer (count v))]
                         (buffer-write! b v)
                         [k (:id b)]))
                     buf-args)
       buf-args-map (into {} buf-args)

       new-args (vec (mapcat (fn [[k v]]
                               (if-let [buf-id (k buf-args-map)]
                                 [k buf-id]
                                 [k v]))
                             param-pairs))
       tail-form (concat '(do) (rest s-form))
       buf-ids (map (fn [[k v]] [(keyword k) v]) buf-args)]

   `(do
      (defsynth ~s-name ~new-args ~tail-form)
      ;;Redefine synth with out buffer ids
      (def ~s-name (assoc-in ~s-name [:sdef :buffers] ~@buf-ids)))))

(defn actl
 "Behaves the same as Overtone's `ctl`.
  It supports implictly writing to buffers if a key matches
  that of a buffer defined for the synth this node is created from.

  An example:
  ```
  (actl siz :notes [50 50 60 75 75] :amp 0.2)
  ```
 "
 [node & args]
 (let [{array-args true args false} (group-by (fn [[_ v]] (vector? v)) (partition 2 args))]
   (doseq [[buf-name buf-val] array-args]
     (when-let [[_ buf-id] (some (fn [[k v]]
                                   (when (= (str (name buf-name)) (str (name k))) [k v]))
                                 (partition 2 (-> node :sdef :buffers)))]
       (apply snd "/b_setn" buf-id 0 (count buf-val) (map float buf-val))))
   (when (seq args) (apply ctl [node] (flatten args))))
 node)

(defn afree
 "Free all buffers held by this synth"
 [synth]
 (doseq [[_ buf-id] (partition 2 (-> synth :sdef :buffers))]
   (with-server-self-sync (fn [uid]
                            (snd "/b_free" buf-id)
                            (server-sync uid))
     (str "whilst freeing audio buffer " (with-out-str (pr buf-id))))))

(comment "Usage"
 (defasynth sin-ly [notes [30 30 30 30 30] amp 1 freq 300 beat-bus 0 amp 1]
   (let [cnt (in:kr beat-bus)
         note (buf-rd:kr 1 notes cnt)
         freq (midicps note)
         src (sin-osc freq)]
     (out 0 (pan2 (* amp src)))))

 (def siz (sin-ly :beat-bus (:count time/main-beat)))

 (actl siz :notes [50 50 60 75 75] :amp 0.2)
 (actl siz :amp 1)

 (-> sin-ly :sdef :buffers second)

 (def tester (buffer 5))

 (ctl time/root-s :rate 2)
 (buffer-write! tester [50 50 60 65 65])
 (ctl siz :notes tester)

 (afree sin-ly)

 (stop))

(defmacro defbufs [size vars]
 (let [vs (map (fn [v] `(defonce ~v (overtone.live/buffer ~size))) vars)]
   `(do ~@vs)))

(comment
 (macroexpand
  '(defbufs 10 [repl electric])
  ))

(defn full-stop []
  (remove-all-beat-triggers)
  (remove-all-sample-triggers)
  (stop))

(defn spread 
  "Euclidean distribution for beats"
  ([num-accents size] (spread num-accents size 0))
  ([num-accents size rotate]
    (map #(< (* %1 (mod num-accents size)) num-accents) (range 0 size))))
