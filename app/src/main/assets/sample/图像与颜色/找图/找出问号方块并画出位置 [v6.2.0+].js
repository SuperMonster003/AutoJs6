let imgSuperMario = images.read('./super_mario.jpg');
let imgBlock = images.read('./block.png');
let points = images.matchTemplate(imgSuperMario, imgBlock, { threshold: 0.8 }).points;

toastLog(points);

let paint = new Paint();
colors.setPaintColor(paint, '#2196F3');

let canvas = new Canvas(imgSuperMario);
points.forEach(point => canvas.drawRect(point.x, point.y, point.x + imgBlock.width, point.y + imgBlock.height, paint));

let image = canvas.toImage();
images.save(image, '/sdcard/tmp.png');

app.viewFile('/sdcard/tmp.png');

imgSuperMario.recycle();
imgBlock.recycle();
image.recycle();
