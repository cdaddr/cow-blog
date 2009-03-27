# cow-blog
by [Brian Carper](http://briancarper.net)

For bug reports and feedback, please email me at brian@briancarper.net.

This is a blog engine written in Clojure using Compojure.  For a live running version see my site [http://briancarper.net](http://briancarper.net).

Are you looking for a feature-full stable and tested blog engine that's easy to set up and run?  Then this is not for you!

Are you looking for a Lisp-driven sparsely-documented cludged-together hobby experiment running on a bleeding-edge quickly-evolving platform that's nevertheless tons of fun to play with?  Keep on reading!

## Features

* Categories, tags, blog posts and static "pages", tag cloud, archives by date and by number of comments, RSS feeds (overall, per-category, per-tag).

* Markdown live-update previews for posting comments, via Showdown.

* Gravatar avatars for comment posters.

* Lightweight (~2000 lines of code total, including all HTML templates).

* HTML templating via Compojure's html library; not a single tag of manually-written HTML needed.

* Via my CRUD library, all blog data is stored in Clojure refs.  All data reads are done from the refs without touching the DB, which is (probably) fast and is more elegant and Lispy than writing SQL queries.  All updates (edit/create/delete) are transparently done in the in-memory refs and in the DB.

* Easily extensible.

* Free.

* Cows.

## Dependencies

* Latest bleeding-edge [clojure](http://clojure.org/), post lazy-branch at least.

* Latest bleeding-eddge [clojure-contrib](http://code.google.com/p/clojure-contrib/).

* Tested with MySQL (you'll need the [JDBC connector for MySQL](http://dev.mysql.com/usingmysql/java/)).  May or may not work with other databases via JDBC; known NOT to work with SQLite3.

* [Compojure STABLE 0.1 tag](http://github.com/weavejester/compojure/downloads) and its deps.  Compojure has recently been branched to include *ring*; this will not yet work with that bleeding edge version.

* [Fast-MD5](http://www.twmacinta.com/myjava/fast_md5.php)  (included in /deps)

* [Rhino javascript engine](http://www.mozilla.org/rhino/)  (included in /deps)

* Showdown (included in the `/static-blog/js`); I have slightly edited showdown to include a few extra features and integrate better with my blog.  Vanilla showdown will likely not work.

* Lots of 3rd-party Javascript and CSS libraries, included.

## Installation

1. I hope you know what you're doing.  You're not going to be able to drop this into a folder and have it run.  It will require some tweaking.

2. Put all the dependencies in your Java CLASSPATH.

3. Make a database.

4. Edit `/net.briancarper.blog.config` to reflect your setup.

5. Set up an admin user via `net.briancarper.blog.db/add-user`.

6. Possibly edit `net.briancarper.blog.server` to adjust routing and set up the server port and address.

7. Start Clojure.  If running this on a server, I recommend starting GNU screen and running Clojure (or Clojure inside SLIME/Emacs) inside that, so you can keep it running when you log off the server.

8. Load `net.briancarper.blog.server.clj`, and run `(net.briancarper.blog.server/go)`.  If all goes well, it should be running.

9. Unless you really like cows, might want to edit the CSS a bit.

10. If you want to use Apache to forward traffic to your Compojure-running Jetty, look up `mod_proxy`.  It generally requires a few lines like this in your Apache configs:

        ProxyPass / http://localhost:8080/
        ProxyPassReverse / http://localhost:8080/

11. Start fixing bugs!

        
## Tests

Unit tests are included for a few of the libraries; more are in progress.  Run tests at your own risk and **read them before you run them**!  They are set up to use a MySQL table, and repeatedly wipe it out and rebuild it for the tests.

## TODO

1. Port to the bleeding-edge ring branch of Compojure.

2. There's no web interface to do certain things like add or edit categories; you must do it from the REPL.

3. There's even less of an interface for other things, like deleting or editing users.  Use the CRUD commands or do it in the DB manually and re-init everything after.

4. HTML generation is way slower than it could be.  There are HTML libraries that compile HTML rather than constantly re-generate it.  Might be nice to use those one day.

5. MIME type detection for statically served text files are currently not working.  Text files are served as binary downloads instead of displayed in-browser.

6. The CRUD library is a work in progress and I'm uncertain it's entirely thread-safe under heavy load.  Fun times.

7. The whole Rails "flash" thing I've got here kind of sucks.  Compojure's bleeding edge has something better, I'll use that after porting.

8. Init from DB requires two passes to pick up parent posts.  Blech.

9. The search feature is currently terrible and brute-force and should be rewritten.

10. Lots of stuff could be refactored to be more elegant and less brute-force.

11. More error checking and more graceful recovery would be nice, especially for things like adding or editing posts and comments via the web interface, where I largely assume you know what you're doing.  It's currently possible to break things by e.g. including tags with funky characters in them.

12. More security would be nice too.

13. And more documentation.

14. There are cows hard-coded all over the place in here.  Maybe I should remove those.
