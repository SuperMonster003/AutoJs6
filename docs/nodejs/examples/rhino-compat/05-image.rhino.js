var image = images.read("template.png");
var template = images.clip(image, 0, 0, 20, 20);
var point = images.findImage(image, template, 0, 0, 200, 200, 0.9);
log(JSON.stringify(point));
images.recycle(template);
images.recycle(image);
