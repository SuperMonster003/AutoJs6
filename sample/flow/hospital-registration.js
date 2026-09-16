// Requires the Flow extensions in AutoJs6 6.8.0 from this change.
// Keep the hospital's current accessibility layout consistent with the compass paths below.

// let aimProfession = '修复专业';
// let aimDepartment = '修复科';
let aimProfession = '牙体牙髓专业';
let aimDepartment = '牙体牙髓科';

let campus = '魏公村总院区';
let unavailable = /每天.午..?时起至.+凌晨..?时止/;
let noSchedule = /暂无医生排班/;
let doctorStatus = /^(去挂号|已约满)$/;
let periods = /[上下]午/;
let stable = { timeout: 8000, interval: 100, stableFor: 500 };
let titles = ['医师', '住院医师', '主治医师', '副主任医师', '主任医师', '知名专家']
    .sort((a, b) => b.length - a.length);
let titleWeights = { '医师': 0, '住院医师': 0, '主治医师': 1, '副主任医师': 3, '主任医师': 4, '知名专家': 2 };
let nameWeights = { '普通': 0 };

let bookingFlow = waitAsync('北京大学口腔医院', 10000)
    .clickIfExists('稍后再看', { timeout: 400 })
    .waitThenClick(/^[明当]日挂号$/, 5000)
    .waitThenClick(campus, 5000)
    .waitThenClick('选择', 5000)
    .whenPresent(/阅读并同意/, () =>
        flow.toggle(className('CheckBox'), true, { timeout: 800 })
            .waitThenClick('确定', 2000),
    { timeout: 800 })
    .repeatUntil(refreshDepartmentsOnce, aimProfession, {
        timeout: 30000,
        maxAttempts: 6,
        interval: 300,
    })
    .waitThenClick(aimProfession, 5000)
    .waitThenClick(aimDepartment, 5000)
    .findAny([doctorStatus, noSchedule], { timeout: 8000 })
    .then(result => {
        if (!result) throw Error('等待医生列表超时');
        if (result.index === 1) throw Error('暂无医生排班');
        return waitForStable(doctorStatus, {
            ...stable,
            resultType: '[]',
            snapshot: nodes => nodes.map(doctorFields),
        });
    })
    .then(selectProperDoctor)
    .wait('截至取号时间', 8000)
    .waitForStable(periods, {
        ...stable,
        resultType: '[]',
        snapshot: nodes => nodes.map(w => [w.content(), detect(w, 's>1', 'content')]),
    })
    .then(selectProperTime)
    .waitThenClick({ content: '立即挂号', enabled: true }, 5000)
    .waitThenClick({ content: '确定', enabled: true }, 5000)
    .then(() => console.log('已提交挂号确认, 请以页面反馈为准'))
    .catch(error => {
        toast(error.message || String(error));
        console.error(error);
    });

// To cancel while the script is running: bookingFlow.cancel().

function refreshDepartmentsOnce() {
    return flow.findAny([aimProfession, unavailable, noSchedule], { timeout: 5000 })
        .then(state => {
            if (!state) throw Error('未识别到专业列表或可刷新的页面状态');
            if (state.index === 0) return state.node;
            return flow.smartClick(state.index === 1 ? '切换院区' : '即将放号', { timeout: 2000 })
                .waitForStable(campus, { ...stable, timeout: 5000 })
                .smartClick()
                .whenPresent(/加载中/, () => waitForGone(/加载中/, {
                    timeout: 8000, interval: 100, stableFor: 300,
                }), { timeout: 300 })
                .findAny([aimProfession, unavailable, noSchedule], { timeout: 5000 })
                .then(result => {
                    if (!result) throw Error('切换院区后未出现专业列表或结果提示');
                    return result;
                });
        });
}

function doctorFields(w) {
    return [
        w.content(),
        detect(w, 's<3', 'content') || '',
        detect(w, 's<2', 'content') || '',
        detect(w, 's<1', 'content') || '',
    ];
}

function numberFrom(text, pattern) {
    let match = String(text || '').match(pattern);
    return match ? Number(match[1]) : NaN;
}

function readDoctor(w) {
    let [status, label, remainText, feeText] = doctorFields(w);
    let remain = numberFrom(remainText, /余号[^\d]*(\d+)/);
    let expense = numberFrom(feeText, /挂号费[^\d]*(\d+(?:\.\d+)?)/);
    if (status !== '去挂号' || !label || !(remain > 0) || !(expense > 0)) return null;
    let title = titles.find(value => label.endsWith(value)) || null;
    let name = title ? label.slice(0, -title.length) : label;
    let weight = (titleWeights[title] ?? 1) * 100
        + (nameWeights[name] ?? 0) * 20
        + Math.log2(remain + 1) * 2
        - expense / 100;
    return { label, name, title, expense, remain, weight: Math.round(weight * 10) / 10 };
}

function selectProperDoctor(nodes) {
    let results = nodes.map(readDoctor).filter(Boolean).sort((a, b) => b.weight - a.weight);
    if (!results.length) throw Error('无号源');
    console.log('results', results.map(o => [o.name, o.title, `¥${o.expense}`, `余号: ${o.remain}`]));
    let best = results[0];
    // Reacquire a matching available row after ranking; do not reuse an old node.
    return flow.smartClick({
        content: '去挂号',
        filter: w => {
            let current = readDoctor(w);
            return current && current.label === best.label && current.expense === best.expense;
        },
    }, { timeout: 2000, verify: '截至取号时间', verifyTimeout: 8000 });
}

function readTime(w) {
    return {
        label: w.content(),
        remain: numberFrom(detect(w, 's>1', 'content'), /余号[^\d]*(\d+)/),
    };
}

function selectProperTime(nodes) {
    let results = nodes.map(readTime).filter(o => o.remain > 0).sort((a, b) => b.remain - a.remain);
    if (!results.length) throw Error('无可选时段');
    let best = results[0];
    return flow.smartClick({
        content: periods,
        filter: w => {
            let current = readTime(w);
            return current.label === best.label && current.remain > 0;
        },
    }, { timeout: 2000 });
}
