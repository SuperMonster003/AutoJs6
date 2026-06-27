"use strict";

const leftPad = require("left-pad-lite");
const slugify = require("slugify-lite");

console.log([
  "npm-ci-pure-js",
  leftPad("7", 3, "0"),
  slugify("AutoJs6 Safe Node"),
].join(":"));
