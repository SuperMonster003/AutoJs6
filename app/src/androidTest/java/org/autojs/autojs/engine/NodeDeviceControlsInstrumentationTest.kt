package org.autojs.autojs.engine

import org.junit.Assert.assertTrue
import org.junit.Test

class NodeDeviceControlsInstrumentationTest {
    @Test fun nestedWorkersReadRealHostDeviceInfoThroughParentBridge() {
        val result = NodePluginTestHarness.runPluginScript(
            """
            (async () => {
              const assert = require('node:assert/strict'), {Worker} = require('node:worker_threads');
              const expected = (await require('device').info()).build;
              const leaf = "const wt=require('node:worker_threads'); require('autojs6:bridge').callAutoJs('device','info',[]).then(info=>wt.parentPort.postMessage(info.build),error=>{setImmediate(()=>{throw error;});});";
              const parent = "const wt=require('node:worker_threads'); const child=new wt.Worker(" + JSON.stringify(leaf) + ",{eval:true}); child.once('message',value=>wt.parentPort.postMessage(value)); child.once('error',error=>{throw error;});";
              const worker = new Worker(parent, {eval:true});
              try {
                const actual = await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);});
                assert.equal(actual.model, expected.model);
                assert.equal(actual.sdkInt, expected.sdkInt);
                assert.ok(actual.model.length > 0 && actual.sdkInt > 0);
                console.log('m23.worker.host.device=PASS');
              } finally { await worker.terminate(); }
            })().catch(error=>{console.error(error.stack);process.exitCode=1;});
            """.trimIndent(), "worker-host-device.cjs", timeoutMs = 20_000,
            workerThreadsEnabled = true,
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest
                .fromDeclaredCapabilities(listOf("device")).toJson()),
        )
        NodePluginTestHarness.assertSucceeded("nested worker host device bridge", result)
        assertTrue(result.stdout, result.stdout.contains("m23.worker.host.device=PASS"))
    }

    @Test fun readsDeviceFieldsControlsAudioAndKeepsScreenOnForFiveSeconds() {
        val result = NodePluginTestHarness.runPluginScript(
            """
            (async () => {
              const assert = require('node:assert/strict'), device = require('device'), media = require('media');
              const numeric = ['Width', 'Height', 'Density', 'DensityDpi', 'Brightness', 'BrightnessMode', 'Battery', 'SdkInt', 'TotalMem', 'AvailMem'];
              for (const field of numeric) {
                const value = await device['get' + field]();
                assert.equal(typeof value, 'number', field); assert.ok(Number.isFinite(value), field);
                assert.ok(value >= 0, field + '=' + value);
              }
              for (const field of ['Release', 'Model', 'Brand', 'Product', 'Board', 'Bootloader', 'Hardware', 'Fingerprint', 'BuildId']) {
                const value = await device['get' + field]();
                assert.equal(typeof value, 'string', field); assert.ok(value.length > 0, field);
                const key = field[0].toLowerCase() + field.slice(1);
                assert.equal(device[key], value, key + ' startup property');
              }
              assert.equal(typeof await device.isCharging(), 'boolean');
              assert.ok(device.width > 0 && device.height > 0 && device.densityDpi > 0);
              const info = await device.info();
              assert.equal(info.schema, 'autojs6-bridge-device-info-v1');
              assert.ok(info.memory.totalMem >= info.memory.availMem && info.memory.availMem > 0);
              for (const key of ['imei', 'androidId', 'serial', 'macAddress']) {
                assert.equal(device[key], undefined); assert.equal(info.build[key], undefined);
              }
              for (const [method, value] of [['setBrightness', 256], ['setBrightnessMode', 2], ['keepScreenOn', 0]]) {
                await assert.rejects(device[method](value), error => Boolean(error.message));
              }
              if (info.screen.brightness >= 0 && info.screen.brightness <= 255) {
                if (info.screen.canWriteSettings) {
                  await device.setBrightness(info.screen.brightness);
                  await device.setBrightnessMode(info.screen.brightnessMode);
                } else {
                  await assert.rejects(device.setBrightness(info.screen.brightness), error => error.category === 'permission-denied');
                  await assert.rejects(device.setBrightnessMode(info.screen.brightnessMode), error => error.category === 'permission-denied');
                }
              }
              for (const name of ['Music', 'Notification', 'Alarm']) {
                const current = await device['get' + name + 'Volume']();
                const max = await device['get' + name + 'MaxVolume']();
                assert.ok(Number.isInteger(current) && current <= max);
                try {
                  await device['set' + name + 'Volume'](current);
                  assert.equal(await device['get' + name + 'Volume'](), current);
                } catch (error) { assert.equal(error.category, 'permission-denied'); }
              }
              await assert.rejects(media.setAudioStreamVolume('music', 99999), error => Boolean(error.message));
              await device.keepScreenOn(5000);
              try {
                assert.equal(await device.isScreenOn(), true);
                await new Promise(resolve => setTimeout(resolve, 4500));
                assert.equal(await device.isScreenOn(), true);
              } finally { await device.cancelKeepingAwake(); }
              // Termination must also release an acquired lock without an explicit cancel.
              await device.keepScreenOn(30000);
              console.log('m15.device=PASS model=' + info.build.model + ' memory=' + info.memory.totalMem + ' canWriteSettings=' + info.screen.canWriteSettings);
            })().catch(error => { console.error(error.stack); process.exitCode = 1; });
            """.trimIndent(), "device-controls.cjs", timeoutMs = 20_000,
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest
                .fromDeclaredCapabilities(listOf("device", "device.power", "media", "media.audio")).toJson()),
        )
        NodePluginTestHarness.assertSucceeded("device controls", result)
        assertTrue(result.stdout, result.stdout.contains("m15.device=PASS"))
        println(result.stdout)
    }

    @Test fun undeclaredPowerControlIsRejectedBeforeDispatch() {
        val result = NodePluginTestHarness.runPluginScript(
            """
            (async () => {
              const assert = require('node:assert/strict'), device = require('device');
              assert.equal(typeof (await device.info()).build.model, 'string');
              await assert.rejects(device.keepScreenOn(5000), error => error.code === 'ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED');
              console.log('m15.device.permission=PASS');
            })().catch(error => { console.error(error.stack); process.exitCode = 1; });
            """.trimIndent(), "device-power-permission.cjs",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest
                .fromDeclaredCapabilities(listOf("device")).toJson()),
        )
        NodePluginTestHarness.assertSucceeded("device power declaration", result)
    }
}
