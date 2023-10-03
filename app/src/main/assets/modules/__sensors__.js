/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Sensors}
 */
module.exports = function (scriptRuntime, scope) {
    let _ = {
        Sensors: (/* @IIFE */ () => {
            /**
             * @extends Internal.Sensors
             */
            const Sensors = function () {
                /* Empty body. */
            };

            Sensors.prototype = {
                constructor: Sensors,
            };

            Object.setPrototypeOf(Sensors.prototype, scriptRuntime.sensors);

            return Sensors;
        })(),
    };

    /**
     * @type {Internal.Sensors}
     */
    const sensors = new _.Sensors();

    return sensors;
};