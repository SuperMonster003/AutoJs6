* [Object.values()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Object/values)

   ```javascript
   Object.values({name: 'Max', age: 4}); // ['max', 4]
   ```

* [Array.prototype.includes()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Array/includes)

   ```javascript
   [10, 20, NaN].includes(20); // true
   ```

* [BigInt](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/BigInt)

   ```javascript
   typeof 567n === 'bigint'; // true
   ```

* [模板字符串](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Template_literals)

   ```javascript
   `Lucky number: ${(Math.random() * 100).toFixed(0)}`
   ```

* [求幂运算符](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Operators/Exponentiation)

   ```javascript
   9 ** 2 === 81; // true
   ```

* [简短对象属性](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Operators/Object_initializer)

   ```javascript
   let a = 7, b = 4, o = {a, b};
   o.a - o.b === 3; // true
   ```

* [生成器函数](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Generator)

   ```javascript
   let gen = function* () { yield 39 };
   gen().next().value === 39; // true
   ```

* [参数尾逗号](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Trailing_commas)

   ```javascript
   Math.max(9, 6, 3,) === 9; // true
   ```

* [Map](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Map) / [Set](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Set) / [WeakMap](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/WeakMap) / [WeakSet](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/WeakSet)

   ```javascript
   new Map().set('n', 2).set('n', 6).get('n'); // 6
   ```

* [Object.setPrototypeOf](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Object/setPrototypeOf)

   ```javascript
   Object.setPrototypeOf({}, {n: 6}).n; // 6
   ```

* [Array.from](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Array/from) / [Array.of](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Array/of)

   ```javascript
   Array.from('123'); // ['1', '2', '3']
   Array.from('123', Number); // [1, 2, 3]
   
   Array.of('hello', 'world'); // ['hello', 'world']
   ```

* [Array.prototype.fill](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Array/fill) / [copyWithin](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Array/copyWithin)

   ```javascript
   let a = Array(4).fill('x'); // ['x', 'x', 'x', 'x']
   a.fill('y', 3); // ['x', 'x', 'x', 'y']
   a.fill('z', 0, 2); // ['z', 'z', 'x', 'y']
   
   [1, 2, 3, 4, 5].copyWithin(-2, -3, -1); // [1, 2, 3, 3, 4]
   [].copyWithin.call(new Int32Array([1, 2, 3]), 0, 2, 3); // [3, 2, 3]
   ```

* [Array.prototype.find](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Array/find) / [findIndex](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Array/findIndex)

   ```javascript
   let a = [5, 12, 8, 130, 44];
   a.find(n => n > 10); // 12
   a.findIndex(n => n > 10); // 1
   ```

* [Array.prototype.keys](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Array/keys) / [values](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Array/values) / [entries](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Array/entries)

   ```javascript
   let a = [1,,,4];
   a.length; // 4
   
   Object.keys(a); // ['0', '3']
   Array.from(a.keys()); // [0, 1, 2, 3]
   
   Object.values(a); // [1, 4]
   Array.from(a.values()); // [1, undefined, undefined, 4]
   
   Object.entries(a); // [['0', 1], ['3', 4]]
   Array.from(a.entries()); // [[0, 1], [1, undefined], [2, undefined], [3, 4]]
   ```

* [Number.EPSILON](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Number/EPSILON)

   ```javascript
   let x = 0.1;
   let y = 0.2;
   let z = 0.3;
   x + y - z === 0; // false
   Math.abs(x + y - z) < Number.EPSILON; // true
   ```

* [Math.clz32](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Math/clz32) / [sign](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Math/sign) / [log2](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Math/log2) / [acosh](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Math/acosh) / [asinh](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Math/asinh) / [atanh](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Math/atanh) / [fround](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Math/fround)

* [Object.seal](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Object/seal) / [isSealed](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Object/isSealed)

   ```javascript
   let o = {};
   Object.isSealed(o); // false
   Object.preventExtensions(o);
   Object.isSealed(o); // true
   o.a = 1;
   o.a; // /undefined
   ```

* [Object.freeze](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Object/freeze) / [isFrozen](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Object/isFrozen)

   ```javascript
   let o = {a: 1, b: 2};
   Object.isFrozen(o); // false
   delete o.a; // true
   o; // {b: 2}
   Object.freeze(o);
   Object.isFrozen(o); // true
   delete o.b; // false
   o; // {b: 2}
   o.c = 3;
   o; // {b: 2}
   ```

* [Object.preventExtensions](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Object/preventExtensions) / [isExtensible](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Object/isExtensible)

   ```javascript
   let o = {};
   Object.isExtensible(o) // true
   Object.preventExtensions(o);
   Object.isExtensible(o) // false
   o.a = 1;
   o; // {}
   ```

* [Object.entries](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Object/entries) / [fromEntries](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Object/fromEntries)

   ```javascript
   let o = {a: 1, b: 2};
   Array.from(Object.entries(o)); // [['a', 1], ['b', 2]]
   
   let e = [['a', 1], ['b', 2]];
   Object.fromEntries(e); // {a: 1, b: 2}
   ```

* [String.prototype.padStart](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/String/padStart) / [padEnd](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/String/padEnd)

   ```javascript
   let h = '9';
   let m = '30';
   let s = '7';
   let pad = o => o.padStart(2, '0');
   `${pad(h)}:${pad(m)}:${pad(s)}`; // '09:30:07'
   
   pad = o => o.padEnd(2, '_');
   `${pad(h)}:${pad(m)}:${pad(s)}`; // '9_:30:7_'
   ```