"use strict";

module.exports = function leftPadLite(value, width, ch) {
  let result = String(value);
  const pad = ch || " ";
  while (result.length < width) {
    result = pad + result;
  }
  return result;
};
