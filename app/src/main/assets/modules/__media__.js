/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Media}
 */
module.exports = function (scriptRuntime, scope) {
    let _ = {
        Media: (/* @IIFE */ () => {
            /**
             * @extends Internal.Media
             */
            const Media = function () {
                /* Empty body. */
            };

            Media.prototype = {
                constructor: Media,
            };

            Object.setPrototypeOf(Media.prototype, scriptRuntime.media);

            return Media;
        })(),
    };

    /**
     * @type {Internal.Media}
     */
    const media = new _.Media();

    return media;
};