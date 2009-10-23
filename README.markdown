# cow-blog
by [Brian Carper](http://briancarper.net/)

Now featuring thread safety!

This is a complete rewrite of my blog engine written in Clojure using Compojure and Tokyo Cabinet.  Previous versions of this code were terrible and you should not use them.

## Purpose

This is intended as a working proof-of-concept of a simple website using Compojure.  This is a clean rewrite that's close (but not identical) to the code I have been using to run my blog for the past year.

You should **NOT** expect to unpack this code and fire it up and have it work.  This code is meant largely as a learning tool or HOWTO for writing a web app in Clojure.  You should read the code carefully and understand it.  It's only ~700 lines of code and it shouldn't be hard to read through.

Some effort has been made to make this a bit secure but **not much**.  Read and understand (and fix) this code before deploying it publicly.  Use at your own risk.

## Features

* Post tags, categories, comments
* Markdown
* Gravatars
* RSS
* Add/edit posts via admin interface

## Dependencies

* [Clojure](http://github.com/richhickey/clojure), [clojure-contrib](http://github.com/richhickey/clojure-contrib), [Compojure](http://github.com/weavejester/compojure) (duh).

* [Tokyo Cabinet](http://1978th.net/tokyocabinet/).  You must install the C library (compile it yourself, it's straightforward), then install the Java bindings.  Then you have to tell Java where to find everything via `$CLASSPATH` and possibly `LD_LIBRARY_PATH`.  Follow the [directions](http://1978th.net/tokyocabinet/javadoc/) closely.

* [Rhino javascript engine](http://www.mozilla.org/rhino/).  Download and install it, all you need is `js.jar`.

* JQuery and some JQuery plugins (included in `/public/js`).

* [Showdown](http://attacklab.net/showdown/) JS Markdown library (included in `/deps`); I have slightly edited showdown to include a few extra features and integrate better with my blog.  Vanilla Showdown will likely not work.

## TODO

These features are very simple to implement (like, one or two functions each) but I haven't bothered yet; they will show up in he next version.

* Documentation!
* Better stylesheet.
* Editing comments, deleting posts/comments (you can do this from the REPL in the meantime)
* Spam filtering
* Archives
* Tag cloud

## Getting started

Make sure Tokyo Cabinet, Showdown, Compojure, and all of the files in `/blog` are on your $CLASSPATH.

Then edit `blog/config.clj`.  Then run this in a REPL:

    (.start blog.server/blog-server)

Visit http://localhost:8080 in a browser.
    
A file `posts.db` will be created to store your data.  Start reading `server.clj` to see what's what.

## Bugs

Bugs are a certainty.

For bug reports, feedback, or suggestions, please email me at brian@briancarper.net or open an issue on github.

## LICENSE

See the `LICENSE` file.

## Changelog

* October 22, 2009 - Rewrite from scratch.  No more CRUD.  Tokyo Cabinet.  Removed cows.

* April 12, 2009 - Updated to work on Compojure's bleeding-edge "ring" branch.  Complete rewrite of the CRUD library.  Overhauled mostly everything.

* March 27, 2009 - Initial release.
