# Theme color, gallery, batch rename and speech analysis in Node and Bun - 2026-10-31

This development batch (api-coverage P3.5 / P4.5, part A, Q80 - Q82 (a), Q114) brings four families to Node main threads,
Node Workers and Bun: colors.theme, gallery (3-Maple Image), files.batchRename (Batch Rename) and the speech analysis
members (speech.diarize, detectLanguage, tag and speech.speaker, 3-Macaw Speech). No release, registry update or remote
push was performed.

## Delivered behavior

- Three new host bridge modules, each behind one capability: Node declares `theme`, `gallery` and `batch_rename` in
  node.permissions, Bun has the grants theme.control, gallery.control and batchrename.control, off by default.
- gallery and batch_rename use execution-owned operation clients (`RuntimeOperationClients`): open, start, poll, cancel and
  close stay short requests, so a picker or an editor can wait for the user without holding a transport request. Events
  drain in fragments that fit the Node and Bun JSON transports.
- A gallery viewer is an EventEmitter session with open, page, error and a terminal close event. The execution stays alive
  until close; when the script ends the viewer stays with the user and a waiting picker or editor ends.
- The Node facades are the Bun facades (`__autojs6_create_plugin_family`, copied by sync-plugin-family.mjs), and colors.theme
  lives in the shared colors core, so the argument rules, error classes and error codes are Rhino's in all forms.
- Packaged applications cannot reach 3-Maple Image and Batch Rename: both plugins require the signature permission
  org.autojs.permission.PLUGIN and have no packaged entry. The same holds for Rhino in a packaged application (Q196).

## Device evidence

OnePlus 63751f63 (OPD2413, Android 15, API 35), host debug 5935 built from 4eedcfc5e plus 7b01dc338's sources, Node debug 356,
Bun debug 335, 3-Maple Image 198, Batch Rename 49, 3-Macaw Speech 165 with the speaker, segmentation and language models the
device already held. Script: tools/api-coverage/device_plugin_family.py, 78 cases, one body for all runtimes.

| Run | Rhino | Node main | Node Worker | Bun main | Bun Worker |
| --- | --- | --- | --- | --- | --- |
| Compare, first | 77 / 78 (gallery.pick~ timed out) | 77 + that case differs | 77 + that case differs | 77 + that case differs | 77 + that case differs |
| Compare, second | 78 / 78 | 78 / 78 | 77 / 78 (gallery.edit~ timed out) | 78 / 78 | 78 / 78 |
| Denied (nothing declared or granted) | - | 20 / 20 | 20 / 20 | 20 / 20 | 20 / 20 |

The two timeouts are one intermittent failure, seen in Rhino as well: a picker or editor is replaced by a new viewer right
after the previous viewer closed, and the case gets no answer within 45 s (Rhino gallery.pick~ 2 of 9 tries, Node Worker
gallery.edit~ 1 of 5 tries). Repeating gallery.viewAsync~, gallery.pick~ and gallery.edit~ three times gave 8 / 9 in Rhino and
9 / 9 in a Node Worker. The cause is not diagnosed; because Rhino shows it, it does not come from the
Node or Bun bridges (Q197).

The speech cases reach the plugin in every form: the model answers (diarize, speaker.identify) have the same keys, and
the error codes on the 0.1 s fixture are the same as Rhino's (AUDIO_TOO_SHORT, SPEAKER_NOT_FOUND, SESSION_CLOSED). No case
plays sound.

Packages, grants and md5 were restored: host b958b46599fe2c7188b58a84a1705553, Node 340 648d7ac693f6902528f6a280de41e906, Bun not
installed, 3-Maple Image 8fed17c5839aaa7ff560c7406a5126e6, Batch Rename 814cff596957d573c04211dfb30a7ba1, 3-Macaw Speech
d62c19ad3b375d3d91df14f025966058; the four grants are absent. The preset aj6-p45 stays in Batch Rename on that device: no
script API removes a preset, and reinstalling the plugin would also drop its settings.
