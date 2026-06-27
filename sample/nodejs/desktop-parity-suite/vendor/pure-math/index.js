"use strict";

exports.sum = function sum(values) {
  return values.reduce((total, value) => total + value, 0);
};
