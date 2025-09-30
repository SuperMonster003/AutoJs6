// 忽略不支持的传感器, 即使有传感器不支持也不抛出异常
sensors.ignoresUnsupportedSensor = true;

sensors.on('unsupported_sensor', function (sensorName, sensorType) {
    log('不支持的传感器: %s 类型: %d', sensorName, sensorType);
});

// 加速度传感器
sensors.register('accelerometer').on('change', (event, ax, ay, az) => {
    log('x 方向加速度: %d\ny 方向加速度: %d\nz 方向加速度: %d', ax, ay, az);
});
// 方向传感器
sensors.register('orientation').on('change', (event, dx, dy, dz) => {
    log('绕 x 轴转过角度: %d\n 绕 y 轴转过角度: %d\n 绕 z 轴转过角度: %d', dx, dy, dz);
});
// 陀螺仪传感器
sensors.register('gyroscope').on('change', (event, wx, wy, wz) => {
    log('绕 x 轴角速度: %d\n 绕 y 轴角速度: %d\n 绕 z 轴角速度: %d', wx, wy, wz);
});
// 磁场传感器
sensors.register('magnetic_field').on('change', (event, bx, by, bz) => {
    log('x 方向磁场强度: %d\ny 方向磁场强度: %d\nz 方向磁场强度: %d', bx, by, bz);
});
// 重力传感器
sensors.register('magnetic_field').on('change', (event, gx, gy, gz) => {
    log('x 方向重力: %d\ny 方向重力: %d\nz 方向重力: %d', gx, gy, gz);
});
// 线性加速度传感器
sensors.register('linear_acceleration').on('change', (event, ax, ay, az) => {
    log('x 方向线性加速度: %d\ny 方向线性加速度: %d\nz 方向线性加速度: %d', ax, ay, az);
});
// 温度传感器
sensors.register('ambient_temperature').on('change', (event, t) => {
    log('当前温度: %d', t);
});
// 光线传感器
sensors.register('light').on('change', (event, l) => {
    log('当前光的强度: %d', l);
});
// 压力传感器
sensors.register('pressure').on('change', (event, p) => {
    log('当前压力: %d', p);
});
// 距离传感器
sensors.register('proximity').on('change', (event, d) => {
    log('当前距离: %d', d);
});
// 湿度传感器
sensors.register('relative_humidity').on('change', (event, rh) => {
    log('当前相对湿度: %d', rh);
});

// 30 秒后退出程序
setTimeout(exit, 30 * 1000);
