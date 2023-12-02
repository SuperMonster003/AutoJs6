// noinspection BadExpressionStatementJS,JSXNamespaceValidation

'ui';

let _ = {
    colors: {
        inputText: colors.rgba('#000000DE'),
        sampleText: colors.rgba('#00000091'),
        splitLine: colors.rgba('#00000055'),
    },
    textSize: 16,
    inputHint: '输入要检索的内容',
    sampleTitle: '检索示例',
    samples: ['30', 'R', 'Android 11', 'oreo', 'pie', 'cookie', 'nougat', 'jelly bean',
        '7.1.1', '7.1', '8.0', '8', '2022', '2022 Mar', '2022 March', '2022 3', '2022/3',
        '2022/03', 'Mar 2022', 'March 2022', 'Mar, 2022', 'March, 2022', '2022 Mar 07',
        '2022 Mar 7', '2022 March 7', '2022 3 7', '2022/3/7', '2022/3/07', '2022/03/07',
        'Mar 7 2022', 'March 7 2022', 'Mar 7, 2022', 'March 7, 2022'],
    infoKeys: ['versionCode', 'apiLevel', 'platformVersion', 'releaseName', 'internalCodename', 'releaseDate'],
};

let $ = {
    show() {
        this.initUiLayout();
        this.addSampleText();
        this.addTextChangedListener();
    },
    initUiLayout() {
        ui.layout(<vertical focusable="true" clickable="true" gravity="top" marginTop="10">
            <vertical marginTop="16">
                <input id="input" lines="1" layout_weight="1" gravity="center"/>
            </vertical>
            <scroll>
                <vertical margin="0 16" id="info"/>
            </scroll>
        </vertical>);

        ui['input'].setHint(_.inputHint);
        ui['input'].setTextColor(_.colors.inputText);
        ui['input'].setTextSize(_.textSize);
        // to disable multi-line feature
        ui['input'].setInputType(InputType.TYPE_CLASS_TEXT);
    },
    addSampleText() {
        let view = ui.inflate(<vertical>
            <text id="text" size="16" gravity="center"/>
        </vertical>);

        view.text.setText(`${_.sampleTitle}:\n\n${_.samples.join('\n')}`);
        view.text.setTextColor(_.colors.sampleText);

        ui['info'].addView(view);
    },
    addTextChangedListener() {
        ui['input'].addTextChangedListener(new TextWatcher({
            beforeTextChanged: () => void 0,
            onTextChanged: () => void 0,
            afterTextChanged: this.afterTextChanged.bind(this),
        }));
    },
    afterTextChanged(inputText) {
        ui['info'].removeAllViews();

        if (inputText.length() > 0) {
            this.parseInputAndSetView(inputText);
        } else {
            this.addSampleText();
        }
    },
    parseInputAndSetView(inputText) {
        let result = util.versionCodes.search(inputText.toString());
        let infos = Array.isArray(result) ? result : result ? [result] : [];
        infos.forEach((info) => {
            this.setInfoView(info);
            this.setSplitLineView();
        });
    },
    setInfoView(info) {
        _.infoKeys.forEach((key) => {
            let infoView = ui.inflate(<vertical>
                <text id="text" marginBottom="10" gravity="center"/>
            </vertical>);

            // e.g. versionCode -> Version Code
            // e.g. platformVersion -> Platform Version
            let prop = key.replace(/^([a-z]+)(([A-Z][a-z]+)*)$/, ($, $1, $2) => /* @AXR */ (
                StringUtils.toUpperCaseFirst($1) + $2.replace(/[A-Z](?=[a-z])/g, ' $&')
            ));
            infoView['text'].setText(`${prop}: ${info[key]}`);
            infoView['text'].setTextSize(_.textSize);

            ui['info'].addView(infoView);
        });
    },
    setSplitLineView() {
        let splitLineView = ui.inflate(<vertical>
            <vertical id="line" height="2" margin="0 12"/>
        </vertical>);

        splitLineView['line'].setBackgroundColor(_.colors.splitLine);

        ui['info'].addView(splitLineView);
    },
};

$.show();