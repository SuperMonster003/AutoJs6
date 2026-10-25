// Calls of the modules of roadmap P3.5, third group (tts, recorder, media, sensors), appended to bench/calls_prelude.js by
// device_bench.py --calls tts. They reach the modules of the script module layer (with its switch off they reached the old
// augment ones until P5.2). "ok" cases only read state (nothing is spoken); the "fail" cases fail with an argument error
// before the TTS service is called, compared with the "ref" cases of the prelude.
time("ok tts.getMaxInputLength", () => { try { tts.getMaxInputLength(); } catch (e) { } });
time("ok tts.isSpeaking", () => { try { tts.isSpeaking(); } catch (e) { } });
time("ok recorder", () => { try { recorder("aj6-bench"); } catch (e) { } });
time("ok recorder.save", () => { try { recorder.save("aj6-bench-save", 1); } catch (e) { } });
time("ok media.isMusicPlaying", () => { try { media.isMusicPlaying(); } catch (e) { } });
time("fail tts-arity", () => { try { tts(); } catch (e) { } });
time("fail tts.speak-range", () => { try { tts.speak(""); } catch (e) { } });
time("fail tts.silence-range", () => { try { tts.silence(-1); } catch (e) { } });
time("fail tts.ready-type", () => { try { tts.ready(1); } catch (e) { } });
time("fail tts.speak-options", () => { try { tts.speak("a", 5); } catch (e) { } });
time("fail recorder-arity", () => { try { recorder(1, 2, 3); } catch (e) { } });
