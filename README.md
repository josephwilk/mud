# Musical Universes of Discourse

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

### Chords

Use a single synth/inst def to play chords.

```clojure
(def singing-chord-g
  (chord-synth general-purpose-assembly 3 :amp 0.0 :noise-level 0.05 :beat-trg-bus (:beat time/beat-1th) :beat-bus (:count time/beat-1th) :attack 0.1 :release 0.1))
  
(:bufs singing-chord-g) ;; Access all the bufs of a chord.
(:synths singing-chord-g) ;; Access all the running synths of a chord.

(chord-pattern! singing-chord-g [[:C3 :E3 :F3]]

(chord-ctl singing-chord-group-g :amp 0.2)
```

### Patterns

Writing patterns to buffers. Using many different musical notations.

```clojure
(use 'mud.core)

(defonce notes-buf (buffer 128))

;;Write a pattern immediately. Supports automatic conversion of degrees or notes.
(pattern! notes-buf [5 3 7] :minor :F3) ;; Degrees
(pattern! notes-buf [:A5 :A3 :A7])      ;; Notes
(pattern! notes-buf [300 400 600])      ;; Frequencies


;;Write a pattern on a beat one time (based on main-beat by default)
(pattern-at! 8 notes-buf [5 3 7] :minor :F3)

;;Keep writing a pattern every nth beat.
(pattern-repeat! 8 notes-buf #(degrees (shuffle [5 3 7]) :minor :F3))
```

## Goals

* Its centered around using Supercollider for timing rather than Java.
* Buffers are the default way to express patterns.
* Keep it Clojure, support Clojures fns to manipulate patterns.


## License

Copyright © 2014 Joseph Wilk

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
