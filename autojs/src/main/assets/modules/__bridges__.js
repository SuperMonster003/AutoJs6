let _ = {
    // @Hint by SuperMonster003 on May 5, 2022.
    //  ! Duplicate of global.isJavaObject as module global has not been imported.
    isJavaObject(o) {
        if (o !== null && typeof o === 'object') {
            if (typeof o.getClass === 'function') {
                try {
                    return o.getClass() instanceof java.lang.Class;
                } catch (_) {
                    // Ignored.
                }
            }
        }
        return false;
    },
};

module.exports = {
    toArray(iterable) {
        let iterator = iterable.iterator();
        let arr = [];
        while (iterator.hasNext()) {
            arr.push(iterator.next());
        }
        return arr;
    },
    asArray(list) {
        let arr = [];
        for (let i = 0; i < list.size(); i += 1) {
            arr.push(list.get(i));
        }
        for (let key in list) {
            if (typeof key !== 'number') {
                let v = list[key];
                arr[key] = typeof v === 'function' ? v.bind(list) : v;
            }
        }
        return arr;
    },
    toString(o) {
        return String(o);
    },
    call(func, target, args) {
        return func.apply(target, args.map(o => _.isJavaObject(o) ? util.unwrapJavaObject(o) : o));
    },
};