# Musical Universes of Discourse

<img src="http://s30.postimg.org/6gdpkl2g1/piglet_color.png" alt="MUD painting" title="MUD" align="right" />

MUD is a layer over Overtone which focuses on immediacy of expression and creation.

Overtone is extremely powerful at the cost of verbosity. Live coding requires quite a lot of code.
Snippet expansion can help alleviate but it does hinder the range of expression and clutters the visual
experience for the audience reading along with the code.

## Install

Add in your project.clj file:

```clojure
[mud "0.1.0-SNAPSHOT"]
```

## Usage

## Timing

Everything you need to drive synths at beats

```clojure
(use '[mud.core])
(require '[mud.timing :as time])

;;set the rate
(ctl time/root-s :rate 8.)

;;Set timing controls on a Synth

(defsynth seqer
"Plays a single channel audio buffer."
[buf 0 rate 1 out-bus 0 beat-num 0 pattern 0  num-steps 8 
 beat-bus (:count time/main-beat)     ;; Our beat count
 beat-trg-bus (:beat time/main-beat)  ;; Our beat trigger
 amp 0.7
 rate-start 0.1
 rate-limit 0.9]
(let [cnt      (in:kr beat-bus)
      rander (mod cnt 1)
      beat-trg (in:kr beat-trg-bus)
      bar-trg  (and (buf-rd:kr 1 pattern cnt)
                    (= beat-num (mod cnt num-steps))
                    beat-trg)
      vol      (set-reset-ff bar-trg)]
  (out out-bus (* vol amp (scaled-play-buf :num-channels 1 :buf-num buf :rate (t-rand:kr rate-start rate-limit rander) :trigger bar-trg)))))


(defonce kick-seq (buffer 256))
(doall (map #(seqer :beat-num %1 :pattern kick-seq :num-steps 8 :buf (freesound-sample 194114)) (range 0 8)))

(pattern! [1 0 0 1 0 0 0]
          [1 0 0 1 0 0 0]
          [1 0 0 1 0 1 0])
```

## Callback on beats

We can define callbacks in Clojure land firing on various beat events.

```clojure
(defonce pulse-s (freesound-sample 194114))

;;Every 256 beat trigger sample
(on-beat-trigger 256 #(mono-player pulse-s))

;;Dividing beats into 32 parts play sample at 31st beat
(sample-trigger 31 32 #(mono-player pulse-s))

;;Dividing beats into 32 parts play sample at 0 beat
(sample-trigger 0 32 #(mono-player pulse-s))

;;Fire once on beat 0 dividing beats into 128 parts and then remove callback.
(one-time-beat-trigger 0 128 #(mono-player pulse-s))

;;Remove all registered triggers
(remove-all-sample-triggers)
(remove-all-beat-triggers)
 ```

### Chords

Use a single synth/inst def to play chords.

```clojure
(use '[mud.core])
(use '[mud.chords])

(def singing-chord-g
  (chord-synth general-purpose-assembly 3 :amp 0.0 :noise-level 0.05 :beat-trg-bus (:beat time/beat-1th) :beat-bus (:count time/beat-1th) :attack 0.1 :release 0.1))

(:bufs   singing-chord-g) ;; Access all the bufs of a chord.
(:synths singing-chord-g) ;; Access all the running synths of a chord.

(chord-pattern! singing-chord-g [[:C3 :E3 :F3]])

(ctl (:synths singing-chord-group-g) :amp 0.2)
```

### Fast composition of Chords and degrees

Make it easier to write a score, when switching of scales/chords

```clojure
(degrees-seq [:f3 1 6 1 4 :F2 1 1 :C3 1 1]) ;;Lowercase -> minor
                                            ;;Uppercase -> major

;;b first inversion, c second inversion, etc.
;; :sus4*2 is a shortcut for [:sus4 :sus4]
(chords-seq [:F3 1 :6b :1c 4 :sus4 :sus4*2])

;;Repeat a group of chords n times. Group is surronded by `[]`
;; Also pass a string rather than an array.
(chords-seq "F3 [1 6b 1c 4 sus4]*8 sus4*2")
```

### Patterns

Writing patterns to buffers.

```clojure
(use '[mud.core])

(defonce notes-buf (buffer 128))

;;Write a pattern immediately
(pattern! notes-buf (degrees [5 3 7] :minor :F3)) ;; Degrees
(pattern! notes-buf [:A5 :A3 :A7])                ;; Notes

;;Write a pattern on a beat one time (based on main-beat by default)
(one-time-beat-trigger 0 128 #(pattern! notes-buf (degrees [5 3 7] :minor :F3)))

;;Keep writing a pattern every nth beat.
(pattern-repeat! 8 notes-buf #(degrees (shuffle [5 3 7]) :minor :F3))
```

## Goals

* Its centered around using Supercollider for timing rather than Java.
* Buffers are the default way to express patterns.
* Keep it Clojure, support Clojures fns to manipulate patterns.

## Thanks

* Elise Huard for the artwork

## License

Copyright © 2014-2015 Joseph Wilk

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
