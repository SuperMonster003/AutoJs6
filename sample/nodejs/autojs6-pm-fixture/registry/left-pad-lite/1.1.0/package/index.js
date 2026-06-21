"use strict";

module.exports = function leftPadLite(value, width, fill) {
  const text = String(value);
  const pad = String(fill || " ");
  if (text.length >= width) {
    return text;
  }
  return pad.repeat(width).slice(0, width - text.length) + text;
};
