function installAutoJs6DisplayCapture(key, options) {
  "use strict";
  if (!isSecureContext || !navigator.mediaDevices) throw new DOMException("A secure media context is required", "NotAllowedError");
  if (location.origin !== new URL(options.origin).origin) throw new DOMException("Display capture origin does not match", "NotAllowedError");
  if (typeof navigator.mediaDevices.getDisplayMedia === "function") throw new DOMException("A native getDisplayMedia implementation is already present", "NotSupportedError");
  let closed = false, serial = 0, request = null, current = null;
  const messages = [];
  function exception(message, name = "NotAllowedError") { return new DOMException(message, name); }
  function end(value, error, notify = true) {
    if (!value || value.closed) return;
    value.closed = true;
    for (const [track, stop] of value.tracks) { stop(); if (error) track.dispatchEvent(new Event("ended")); }
    if (value.driver) value.driver.stop();
    value.tracks.clear(); value.image = null;
    if (value.reject) value.reject(error || exception("Screen sharing stopped", "AbortError"));
    value.resolve = value.reject = null;
    if (request === value) request = null;
    if (current === value) current = null;
    if (notify) messages.push({ stop: value.id });
  }
  function bound(value, fallback) {
    if (value === undefined) return fallback;
    if (typeof value === "object" && value !== null) value = value.max === undefined ? value.ideal : value.max;
    if (value === undefined) return fallback;
    if (typeof value !== "number" || !Number.isFinite(value) || value <= 0) throw new TypeError("Display dimensions and frameRate must be positive numbers");
    return Math.min(value, fallback);
  }
  function noExactOrMin(value) {
    if (!value || typeof value !== "object") return;
    if ("exact" in value || "min" in value) throw new TypeError("getDisplayMedia does not accept min/exact constraints");
    Object.values(value).forEach(noExactOrMin);
  }
  function getDisplayMedia(constraints = {}) {
    try {
      if (closed) throw exception("Display capture is disabled", "InvalidStateError");
      if (!navigator.userActivation || !navigator.userActivation.isActive || document.hidden) throw exception("A user gesture in the visible page is required", "InvalidStateError");
      if (!constraints || typeof constraints !== "object" || constraints.video === false) throw new TypeError("getDisplayMedia requires video");
      if (constraints.video !== undefined && constraints.video !== true && typeof constraints.video !== "object") throw new TypeError("Invalid video constraints");
      const video = constraints.video || {}; noExactOrMin(video);
      const config = { maxWidth: Math.max(1, Math.floor(bound(video.width, options.maxWidth))),
        maxHeight: Math.max(1, Math.floor(bound(video.height, options.maxHeight))), frameRate: bound(video.frameRate, options.frameRate) };
      if (request || current) throw exception("Stop the current display stream before requesting another", "InvalidStateError");
      return new Promise((resolve, reject) => {
        request = { id: String(++serial), resolve, reject, tracks: new Map(), closed: false, loading: false, config };
        messages.push({ request: request.id, ...config });
      });
    } catch (error) { return Promise.reject(error); }
  }
  function trackOwner(value, track) {
    const stop = track.stop.bind(track), clone = track.clone.bind(track);
    value.tracks.set(track, stop);
    track.stop = () => { stop(); value.tracks.delete(track); if (!value.tracks.size) end(value, null); };
    track.clone = () => trackOwner(value, clone());
    return track;
  }
  const bridge = {
    poll() {
      if (closed || location.origin !== new URL(options.origin).origin) return { disabled: true };
      return messages.shift() || { frame: current && !current.loading ? current.id : null };
    },
    accept(id, width, height) {
      const value = request;
      if (!value || value.id !== id || value.closed) return false;
      const canvas = document.createElement("canvas"); canvas.width = width; canvas.height = height;
      const driver = canvas.captureStream(0).getVideoTracks()[0], track = driver.clone();
      const stream = new MediaStream([track]); value.driver = driver;
      track.contentHint = "detail";
      value.canvas = canvas; value.stream = stream; value.track = trackOwner(value, track);
      current = value; request = null;
      return true;
    },
    frame(id, data) {
      const value = current;
      if (!value || value.id !== id || value.closed || value.loading) return false;
      value.loading = true;
      const image = new Image(); value.image = image;
      image.onload = () => {
        if (value.closed) return;
        const canvas = value.canvas, context = canvas.getContext("2d"), scale = Math.min(canvas.width / image.width, canvas.height / image.height);
        context.fillStyle = "black"; context.fillRect(0, 0, canvas.width, canvas.height);
        const width = image.width * scale, height = image.height * scale;
        context.drawImage(image, (canvas.width - width) / 2, (canvas.height - height) / 2, width, height);
        value.driver.requestFrame(); value.loading = false; value.image = null;
        if (value.resolve) { value.resolve(value.stream); value.resolve = value.reject = null; }
      };
      image.onerror = () => end(value, exception("Screen frame decoding failed", "NotReadableError"));
      image.src = "data:image/jpeg;base64," + data;
      return true;
    },
    reject(id, message, name) {
      const value = request && request.id === id ? request : current && current.id === id ? current : null;
      end(value, exception(message, name), false); return true;
    },
    close() {
      if (closed) return;
      closed = true; end(request, exception("Display capture disabled", "AbortError"), false);
      end(current, exception("Display capture ended", "AbortError"), false); messages.length = 0;
      if (navigator.mediaDevices.getDisplayMedia === getDisplayMedia) delete navigator.mediaDevices.getDisplayMedia;
      delete window[key];
    },
  };
  Object.defineProperty(navigator.mediaDevices, "getDisplayMedia", { configurable: true, value: getDisplayMedia });
  Object.defineProperty(window, key, { configurable: true, value: bridge });
  return { installed: true, source: "android-media-projection", audio: false };
}
