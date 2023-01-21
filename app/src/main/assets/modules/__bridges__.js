( /* @ModuleIIFE */ () => {

    let _ = {
        unwrapIfNeeded(o) {
            return isJavaObject(o) ? unwrapJavaObject(o) : o;
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
            return func.apply(target, args.map(_.unwrapIfNeeded));
        },
        toPrimitive(o) {
            return _.unwrapIfNeeded(o);
        },
    };

})();