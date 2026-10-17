async function exerciseAdvancedImages(images, input) {
  function ok(value,label){if(!value)throw Error(label);}
  function eq(a,b,label){if(JSON.stringify(a)!==JSON.stringify(b))throw Error(label+': '+JSON.stringify(a)+' != '+JSON.stringify(b));}
  async function rejects(action){try{await action();}catch(error){return error;}throw Error('Expected rejection');}
  const held=[];const keep=async promise=>{const value=await promise;if(value&&value.id)held.push(value);return value;};
  const report={mode:input.mode,kind:input.kind};
  try {
    const source=await keep(images.readImage(input.path));
    if(input.kind==='filters') {
      await images.initOpenCvIfNeeded();eq(images.getSize(source),{width:80,height:60},'size');
      eq(await images.isGrayscale(source),false,'color image');
      const gray=await keep(images.grayscale(source));eq(await images.isGrayscale(gray),true,'gray');
      const range=await keep(images.inRange(source,'#ff0000','#ff0000'));eq(await images.pixel(range,10,10),-1,'range selected');eq(await images.pixel(range,50,10),-16777216,'range rejected');
      const interval=await keep(images.interval(source,'#00ff00',0));eq(await images.pixel(interval,50,10),-1,'interval');
      const adaptive=await keep(images.adaptiveThreshold(source,255,'MEAN_C','BINARY',3,2));ok(await images.isGrayscale(adaptive),'adaptive grayscale');
      const bilateral=await keep(images.bilateralFilter(source,{d:5,sigmaColor:30,sigmaSpace:20}));eq(images.getSize(bilateral),images.getSize(source),'bilateral size');
      const alpha=await keep(images.fromBase64(input.alpha));const alphaFiltered=await keep(images.bilateralFilter(alpha,{d:3}));
      eq(await images.pixel(alphaFiltered,2,2),-2130771968,'bilateral straight alpha');eq(await images.getMeanColor(alpha),-2130771968,'mean straight alpha');
      const alphaMask=await keep(images.inRange(alpha,'#80ff0000','#80ff0000'));eq(await images.pixel(alphaMask,2,2),-1,'range straight alpha');report.alpha=-2130771968;
      const rgb=await keep(images.cvtColor(source,'RGBA2RGB'));const hsv=await keep(images.cvtColor(rgb,'RGB2HSV'));
      const restored=await keep(images.cvtColor(await keep(images.cvtColor(hsv,'HSV2RGB')),'RGB2RGBA'));
      eq(await images.mse(source,restored),0,'retained channel round trip');
      const copy=await keep(images.copy(source));eq(await images.isEqual(source,copy),true,'equality');
      for(const metric of ['psnr','ssim','mssim','hist','mse','ncc']){const value=await images[metric](source,copy);ok(typeof value==='number'&&Number.isFinite(value),metric);report[metric]=value;}
      eq(report.mse,0,'MSE');ok(report.psnr>300,'PSNR');ok(Math.abs(report.ssim-1)<.00001,'SSIM');ok(Math.abs(report.mssim-1)<.00001,'MSSIM');
      eq(await images.getSimilarity(source,copy,{metric:'pnsr'}),report.psnr,'metric alias');
      eq(await images.countPointsByColor(source,'#ff0000',{threshold:0}),1200,'red count');
      eq(await images.getMeanColor(source),-8355712,'mean opaque RGB');
      eq(await images.detectMultiColors(source,'#ff0000',[[40,0,'#00ff00'],[0,30,'#0000ff']],10,10,{threshold:0}),true,'multi detection');
      eq(await images.detectsMultiColors(source,'#ff0000',[[80,0,'#00ff00']],10,10,{threshold:0}),false,'out of bounds offset');
      const points=await images.findPointsByColors(source,'#ff0000',[[40,0,'#00ff00']],{threshold:0});eq(points.length,1200,'multi count');
      eq(await images.findPointByColorExactly(source,'#ff0000'),{x:0,y:0},'exact alias');
      eq(await images.findColorInRegion(source,'#ff0000',3,2,10,10,0),{x:3,y:2},'region alias');
      eq(images.buildRegion(source,[2,3]),{x:2,y:3,width:78,height:57},'region dimensions');eq(images.__buildRegion([],undefined,2),null,'missing dimensions');
      const joined=await keep(images.concat(source,source,'bottom'));eq(images.getSize(joined),{width:80,height:120},'concat');
      const small=await keep(images.downsample(source,20,15));eq(images.getSize(small),{width:20,height:15},'downsample');
      const bytes=await images.compressToBytes(source,'png',100);ok(bytes instanceof Uint8Array,'encoded bytes');
      if(input.mode!=='bun')ok(Buffer.isBuffer(bytes),'Node Buffer');
      const fromBytes=await keep(images.downsample(bytes,20,15));eq(images.getSize(fromBytes),{width:20,height:15},'byte downsample');
      const compressed=await keep(images.compress(source,'jpeg',85));eq(images.getSize(compressed),images.getSize(source),'compressed image');
      await rejects(()=>images.cvtColor(source,'NOT_A_COLOR_CODE'));await rejects(()=>images.adaptiveThreshold(source,{blockSize:2}));
      const frame=new images.ObjectFrame({x:0,y:0},{x:8,y:0},{x:0,y:6},{x:8,y:6});eq(frame.center,{x:4,y:3},'frame constructor');ok(frame.summary().includes('width=8'),'frame methods');
      report.pointCount=points.length;
    } else if(input.kind==='features') {
      const object=await keep(images.clip(source,60,50,240,180));
      for(const method of ['SIFT','ORB']) {
        const sceneFeatures=await keep(images.detectAndComputeFeatures(source,{method,region:[30,20,360,300],scale:.75,maxFeatures:700}));
        const objectFeatures=await keep(images.detectAndComputeFeatures(object,{method,maxFeatures:700}));
        ok(sceneFeatures.count>10&&objectFeatures.count>10,'feature count '+method);
        const frame=await images.matchFeatures(sceneFeatures,objectFeatures,{threshold:method==='ORB'?.85:.75,minInliers:6});
        ok(frame&&frame.inliers>=6,'feature match '+method);ok(frame instanceof images.ObjectFrame,'frame class '+method);
        ok(Math.abs(frame.topLeft.x-60)<6&&Math.abs(frame.topLeft.y-50)<6,'ROI/scale coordinates '+JSON.stringify(frame.topLeft));
        ok(frame.width>230&&frame.width<250&&frame.height>170&&frame.height<190,'frame dimensions');
        report[method]={count:sceneFeatures.count,inliers:frame.inliers,topLeft:frame.topLeft};
        await images.recycle(sceneFeatures,objectFeatures);ok(images.isRecycled(sceneFeatures,objectFeatures),'feature recycle');
        await rejects(()=>images.matchFeatures(sceneFeatures,objectFeatures));
      }
      const direct=await images.matchFeatures(source,object,{method:'ORB',maxFeatures:700,drawMatches:input.output});ok(direct&&direct.inliers>=6,'direct image matching');
      const blank=await keep(images.threshold(source,{threshold:255}));eq(await images.matchFeatures(source,blank,{method:'ORB',maxFeatures:500}),null,'no features means no match');
      const matrix=await keep(images.imread(input.path));eq(matrix.channels,3,'imread BGR');
      const converted=await keep(images.matToImage(matrix));eq(await images.pixel(converted,5,5),await images.pixel(source,5,5),'Mat RGB round trip');
      await images.recycle(matrix);ok(images.isRecycled(matrix),'Mat recycle');await rejects(()=>images.matToImage(matrix));
      const independent=await keep(images.cvtColor(converted,'RGB2RGBA'));eq(await images.pixel(independent,5,5),await images.pixel(source,5,5),'independent Mat lifetime');
      const loaded=await keep(images.load(input.url));const loadedAsync=await keep(images.loadAsync(input.url));
      eq(images.getSize(loaded),images.getSize(source),'URL load');eq(await images.pixel(loadedAsync,5,5),await images.pixel(source,5,5),'URL alias');
      eq(await images.load(input.url+'/invalid'),null,'invalid image null');
      const found=await images.findPointByImage(source,object,{threshold:.99});ok(found&&Math.abs(found.x-60)<=1&&Math.abs(found.y-50)<=1,'template alias');
      const inRegion=await images.findImageInRegion(source,object,40,30,300,240,.99);ok(inRegion&&Math.abs(inRegion.x-60)<=1&&Math.abs(inRegion.y-50)<=1,'template region');
    } else if(input.kind==='quantization') {
      const result=await images.quantize(source,{maxColors:16,maxQuality:90,speed:8,preserveAlpha:true});
      eq(result.bytes.length,result.size,'quantized byte count');ok(result.bytes instanceof Uint8Array,'quantized byte type');
      if(input.mode!=='bun')ok(Buffer.isBuffer(result.bytes),'quantized Node Buffer');
      eq(Array.from(result.bytes.slice(0,4)),[137,80,78,71],'PNG signature');eq(result.bytes[25],3,'indexed PNG');
      ok(result.quality>=0&&result.quality<=100&&result.quantizationError>=0&&result.peakWorkingMemoryBytes>=0,'quantization metrics');
      const decoded=await keep(images.fromBytes(result.bytes));eq(images.getSize(decoded),images.getSize(source),'quantized dimensions');
      const output=await images.quantizeToFile(source,input.output,{maxColors:16,maxQuality:90});ok(output.size>0&&output.path===input.output,'file metadata');
      const bytes=await images.compressToBytes(source,'png',{maxColors:16,maxQuality:90});eq(bytes[25],3,'compression options');
      const compressed=await keep(images.compress(source));eq(images.getSize(compressed),images.getSize(source),'default PNG compression');
      await rejects(()=>images.quantize(source,{maxPixels:1}));
      await rejects(()=>images.quantizeToFile(source,input.denied,{maxColors:2,minQuality:100,maxQuality:100}));
      eq(await images.isGrayscale(source),false,'source remains usable');
      Object.assign(report,{size:result.size,quality:result.quality,quantizationError:result.quantizationError,peakWorkingMemoryBytes:result.peakWorkingMemoryBytes,fileSize:output.size});
    } else if(input.kind==='circles') {
      const circles=await images.findCircles(source,{dp:1,minDst:35,param1:100,param2:18,minRadius:28,maxRadius:36});
      ok(circles.some(c=>Math.abs(c.x-80)<4&&Math.abs(c.y-70)<4&&Math.abs(c.radius-32)<4),'circle geometry '+JSON.stringify(circles));report.circles=circles;
    }
    return report;
  } finally { for(const value of held.reverse()) await images.recycle(value); }
}
