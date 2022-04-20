<img src="https://user-images.githubusercontent.com/12676521/164160624-a6dc70ed-e35f-4570-890e-391248f922c4.svg" alt="Owlbear Logo" title="Owlbear" align="right" width="250px" />

# Owlbear (👷 WIP)

A Visual Studio Code extension to support paredit-like features for HTML and TS!

## Dev

### Clojure

Some parts of this extension will involve ClojureScript code. This section will detail how to get a running ClojureScript REPL in which said Cljs code can be loaded and played with.

Make sure you're using VS Code and have the [Calva](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.calva) extension installed.

To start a ClojureScript REPL:

1. `cmd + shift + p`
2. search for `Calva: Start a Project REPL and Connect...`
3. Select the aforementioned command
4. Select `shadow-cljs`
5. Select `:lib` checkbox then OK
6. Open a new terminal and run `npm run cljs-repl`
7. Voila! Your code will now be re-compiled after every change. Try evaluating some code!

[This video](https://i.gyazo.com/8ff378ec542fc9a76410fb5b936c2773.mp4) is a demonstration of the steps above.
