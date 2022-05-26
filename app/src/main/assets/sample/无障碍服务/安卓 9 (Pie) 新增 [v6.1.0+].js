// noinspection BadExpressionStatementJS,JSXNamespaceValidation,JSXNamespaceValidation

'ui';

let config = {
    textSize: 15,
    textMargin: 8,
    requiredApi: 28,
    methodNames: ['lockScreen', 'takeScreenshot'],
};

ui.layout(<vertical gravity="center">
    <text id="requiredApi" gravity="center" marginBottom="{{config.textMargin}}" />
    <text id="currentApi" gravity="center" marginBottom="{{config.textMargin}}" />
    <vertical id="methods" />
</vertical>);

ui['requiredApi'].setText(`Required API Level: ${config.requiredApi}`);
ui['requiredApi'].setTextSize(config.textSize);

ui['currentApi'].setText(`Current API Level: ${device.sdkInt}`);
ui['currentApi'].setTextSize(config.textSize);

config.methodNames.forEach((methodName) => {
    let view = ui.inflate(<button id="btn" />);
    view['btn'].setText(`${methodName}()`);
    view['btn'].setTextSize(config.textSize);
    view['btn'].setTransformationMethod(null);
    view['btn'].on('click', () => toastLog(`${methodName}(): ${automator[methodName]()}`, 'short', 'forcible'));
    ui['methods'].addView(view);
});