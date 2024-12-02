console.show();

console.verbose(`Text from \`console.verbose\``);
console.log(`Text from \`console.log\``);
console.info(`Text from \`console.info\``);
console.warn(`Text from \`console.warn\``);
console.error(`Text from \`console.error\``);
try {
    console.assert(false, `Text from \`console.assert\``);
} catch (e) {
    /* Ignored. */
}