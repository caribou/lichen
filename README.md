# lichen

A service for caching and retrieving images.

## Usage

Add this to your project.clj:

`[caribou/lichen "0.6.14"]`

Resize a single image:

```clj
(use 'lichen.image)
(resize-file "before.png" "after.png" {:width 200})
```

Start an image resizing service (in project.clj):

```clj
:ring {:handler lichen.core/app :init lichen.core/init :port 33333}
```

Then at the command line:

`> lein ring server`

## License

Copyright © 2012 Instrument/Caribou

Distributed under the Eclipse Public License, the same as Clojure.
