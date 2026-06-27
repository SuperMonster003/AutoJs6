"use strict";

exports.layout = function layout() {
  return {
    type: "Column",
    children: [
      { type: "Text", id: "title", text: "Plugin UI" },
      { type: "Button", id: "close", text: "Close" }
    ]
  };
};
