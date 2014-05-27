# MUD - Musical Universes of Discourse

#### Why?

Overtone is extremely powerful at the cost of verbosity. Live coding requires quite a lot of code.
Snippet expansion can help alleviate but it does hinder the range of expression and clutters the visual
experience for the audience reading along with the code.

#### What?

Focus on immediacy of expression and creation.
MUD is a layer over Overtone to make live composition more power and immediate.

* Its centered around using Supercollider for timing rather than Java.
* Buffers are the way to communicate.

## Examples

```clojure
(use 'mud.core)

(defonce notes-buf (buffer 128))

;;Write a pattern immediately. Supports automatic conversion of degrees or notes.
(pat notes-buf [5 3 7] :minor :F3) ;; Degrees
(pat notes-buf [:A5 :A3 :A7])      ;; Notes
(pat notes-buf [300 400 600])      ;; Frequencies

;;Write a pattern on a beat one time (based on main-beat by default)
(pat-at 8 notes-buf [5 3 7] :minor :F3)

;;Keep writing a pattern every beat.
(pat-repeat 8 notes-buf #(degrees (shuffle [5 3 7]) :minor :F3))

;;Create a sequencer which specifies samples as well as on/offs
(defonce drums (seqer [kick-s _ kick-s _ snare-s [kick-s snare-s]]))
```

## License

Copyright © 2014 Joseph Wilk

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
