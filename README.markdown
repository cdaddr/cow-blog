# cow-blog 0.2.0
by [Brian Carper](http://briancarper.net/)

This is a(nother) complete rewrite of my blog engine, written in Clojure using Compojure and PostgreSQL.

Version 0.1.0 of this code used Tokyo Cabinet.  I ran it that way successfully for a year in hobby-production, but I don't advise you to do the same.  There were issues with thread-safety and stability and it was a huge hack / proof of concept.  Use it at your own risk.

Version 0.2.0 currently depends upon a highly experimental ORM-ish library called [Oyako](http://github.com/briancarper/oyako) which I'm building at the same time I build this blog engine.

## Purpose

This code runs my hobby-website.  Its purpose is to teach me how to write webapps in Clojure, and to have fun while doing so.

The intended audience for this code is a knowledgeable Clojure programmer.  User-friendliness is almost entirely lacking.  Users posting comments might get semi-helpful error messages when (not if) something breaks, but as an admin, you'll get stacktraces.

Get the picture?  I wouldn't use version even 0.2.0 of this code for anything you make money from.  But it might be fun to play with.

## Features

* Post tags, categories, comments
* Archives, with gratuitous tag cloud
* Markdown
* Gravatars
* RSS
* Lame spam filter
* Add/edit/delete posts/tags/categories via admin interface

# Getting started

Clone this git repo, then cd into the directory and:

    lein deps

Then, from a REPL or Emacs:

    user> (require 'blog.server)
    user> (blog.server/start)

Use `blog.db/create-user` to create an admin user, or you'll never be able to do anything.

# Deploying

Right now I deploy this by running Emacs in SLIME.  This is less than ideal.  Doing it WAR-style is a possibility in the future.  (Patches welcome.)

I use Apache to proxy to a running Jetty server.  Documentation possibly forthcoming, or read [here](http://briancarper.net/blog/deploying-clojure-websites) for an out-of-date writeup of my general strategy.

## Bugs

Bugs are a certainty.

For bug reports, feedback, or suggestions, please open an issue on github.

## LICENSE

See the `LICENSE` file.

## Changelog

* June 20, 2010 - Rewrite again?  No more Tokyo Cabinet.  Now uses Postgres.  Cows still missing.

* October 22, 2009 - Rewrite from scratch.  No more CRUD.  Tokyo Cabinet.  Removed cows.

* April 12, 2009 - Updated to work on Compojure's bleeding-edge "ring" branch.  Complete rewrite of the CRUD library.  Overhauled mostly everything.

* March 27, 2009 - Initial release.
