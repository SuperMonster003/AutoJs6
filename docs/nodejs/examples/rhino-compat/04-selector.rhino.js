var ok = text("OK").descContains("Confirm").clickable();
var node = ok.findOne(3000);
if (node) {
  node.click();
}
