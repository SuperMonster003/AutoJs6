console.log(`屏幕宽度: ${device.width}
屏幕高度: ${device.height}
buildId: ${device.buildId}
主板: ${device.board}
制造商: ${device.brand}
型号: ${device.model}
产品名称: ${device.product}
bootloader版本: ${device.bootloader}
硬件名称: ${device.hardware}
唯一标识码: ${device.fingerprint}
IMEI: ${device.getIMEI('suppress_warnings')}
AndroidId: ${device.getAndroidId()}
MAC: ${device.getMacAddress()}
API: ${device.sdkInt}
电量: ${device.getBattery()}`);

console.launch();