(ns mud.core
  (:use overtone.live))

(defn pattern!
  "Fill a buffer repeating pattern if required.
   Supports integers or notes which will be converted to midi notes"
  [buf & lists]
  (buffer-write! buf (take (buffer-size buf) (cycle (map #(if (keyword? %) (note %) %) (flatten lists))))))

(defn pattern-at!
  "Exactly as `pattern!` but only writes on a beat."
  [buf beat n & lists]
  (on-trigger
   (:trig-id beat)
   (fn [b]
     (when (= 0.0 (mod b n))
       (apply pattern! (concat [buf] lists))
       (remove-event-handler ::pattern-writer))) ::pattern-writer))
