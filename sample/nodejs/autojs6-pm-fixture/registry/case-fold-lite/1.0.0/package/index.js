"use strict";

module.exports = function caseFoldLite(value) {
  return String(value).normalize("NFKD").toLowerCase();
};
