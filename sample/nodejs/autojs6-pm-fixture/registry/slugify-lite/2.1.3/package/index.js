"use strict";

const fold = require("case-fold-lite");

module.exports = function slugifyLite(value) {
  return fold(value)
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
};
