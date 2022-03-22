const fs = require("fs");

const WasmParser = require("web-tree-sitter");
WasmParser.init()
  .then(() => WasmParser.Language.load("./resources/tree-sitter-html.wasm"))
  .then((Html) => {
    const htmlParser = new WasmParser();
    htmlParser.setLanguage(Html);
    const htmlTree = htmlParser.parse("<div><!DOCTYPE html></div>");
    console.log(htmlTree.rootNode.children[0].children[1].type);
  });

// const ob = require("./out/cljs/owlbear");
// ob.htmlInit("./resources/tree-sitter-html.wasm").then(
//   () => {
//     ob.htmlRaise(sourceHtmlCode, 47112);
//   }
// );

const Parser = require("tree-sitter");
const Tsx = require("tree-sitter-typescript").tsx;

const parser = new Parser();
parser.setLanguage(Tsx);
parser.parse(fs.readFileSync("./test-data/hello-world.jsx", "utf-8"));
