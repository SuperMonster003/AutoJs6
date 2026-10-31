# Gallery session replacement - 2026-10-31

This batch (api-coverage Q197 (b)) reproduces and fixes the gallery calls that got no answer while a new session replaced
the one on screen. The cause is in how the host starts the session Activity; the fix is in the host, changes no contract and
needs no release of 3-Maple Image. No release, registry update or remote push was performed.

## What the report was

The device comparison of the plugin family (runtime-plugin-family-2026-10-31.md) saw gallery.pick~ and gallery.edit~ time out
after 45 s in Rhino and in a Node Worker. The 45 s are `CASE_TIMEOUT_MS` of device_plugin_family.py, not a limit of the
product. A case that waits longer ends after 60 s with `TIMEOUT`: `SESSION_CLAIM_TIMEOUT_MS`, the time the plugin gives an
Activity to claim a session. The new viewer's token never reached an Activity.

The report placed the failure right after a viewer closed. That step is not the condition: the session Activity of the closed
viewer finishes 41 to 62 ms before the next start. The window is the replacement itself, where that margin is 6 to 29 ms.

## Cause

`GalleryActivityLauncher` started the session Activity with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP`, on the
assumption written in its comment that an Activity still on top would read the new token in `onNewIntent`.
`GallerySessionActivity` of 3-Maple Image has no `onNewIntent` and reads its token in `onCreate` only. Two paths then lose
the token:

1. Replacement, intermittent. The plugin answers `openViewer` and finishes the previous UI asynchronously on its main thread:
   first the child Activity, then the session Activity, 2 to 12 ms apart. A host start that lands between the two finds that
   session Activity on top of its task and delivers the intent to it. The system creates no Activity, and the old one finishes
   in the same millisecond.
2. A viewer left to the user, always. After `session.close({finish: false})` the viewer and its session Activity stay in their
   task. `NEW_TASK` finds that task by component and the intent matches its root, so the start only brings the task to the
   front. Every further session waits for the claim timeout while the old viewer is on screen.

## Evidence

OnePlus 63751f63 (OPD2413, ColorOS, Android 15, API 35), 3-Maple Image 3.4.0 (198), Node debug 356, Bun debug 335, window
animations at 0.5. Script: tools/api-coverage/device_gallery_replace.py. A round is a viewer, a picker replaced by a viewer
and an editor replaced by a viewer, so 20 rounds are 60 steps and 40 replacements. A left step keeps a viewer open with
`{finish: false}` and opens the next one. Each case waits 75 s, so a lost session ends with the plugin's own answer.

Before the fix (host at HEAD 5d582fdd1 without the fix; Rhino on the device's earlier 5935, which has the same gallery core):

| Form | Replacement steps | Lost | Left steps | Lost |
| --- | --- | --- | --- | --- |
| Rhino | 80 (40 with 2500 ms, 40 with 300 ms) | 1 (r18.pick~) | 5 | 5 |
| Node main | 40 | 0 | 1 | 1 |
| Node Worker | 40 | 0 | 1 | 1 |
| Bun main | 40 | 1 (r7.edit~) | 1 | 1 |
| Bun Worker | 40 | 1 (r15.pick~) | 1 | 1 |

Three replacements of 240 were lost. All three show the same lines, for example Bun Worker:

```
00:57:32.805 wm_finish_activity  GalleryPickerActivity             (the plugin finishes the child)
00:57:32.817 wm_new_intent       GallerySessionActivity 55267352   (the host's start reaches the old instance)
00:57:32.817 START u0 ... flg=0x30000000                           (NEW_TASK | SINGLE_TOP)
00:57:32.817 wm_finish_activity  GallerySessionActivity 55267352   (the old instance finishes)
```

`wm_new_intent` for the plugin appears only in these three failures. The session then closes with `timeout` about 60 s after
the replacement, and the next session works: nothing is stuck but the lost token. In a left step the second `START` has neither
`wm_create_activity` nor `wm_new_intent`, and three starts in a row landed in the same task 7735.

Ruled out: background activity limits (the log says the start is allowed through `SYSTEM_ALERT_WINDOW`), `onUnbind` of the
plugin service (it would answer with a `close` event at once, not stay silent) and the bridges (Rhino fails the same way). In
one earlier run the second left step in a row ended with the 75 s case timeout instead of `TIMEOUT`; three repetitions in a
row answered `TIMEOUT` as expected, so that once is unexplained. It came from a lost token, which the fix removes.

The fix was first tried with a replacement delay of 500 ms in Rhino, a Node main script and a Node Worker: 62 / 62 each, left
steps open in 75 to 92 ms. Two of those starts landed in the window, and each now creates its own Activity:

```
00:05:16.026 wm_finish_activity  GalleryPickerActivity
00:05:16.032 wm_create_activity  GallerySessionActivity 4779298 in task 7962   (the new session)
00:05:16.032 START u0 ... flg=0x18000000                                       (NEW_TASK | MULTIPLE_TASK)
00:05:16.032 wm_finish_activity  GallerySessionActivity 178529778 in task 7961 (the old instance)
```

After the fix (the same HEAD with the fix, replacement delay 2500 ms):

| Form | Steps | Lost | Slowest replacing viewer | Slowest left step |
| --- | --- | --- | --- | --- |
| Rhino | 62 / 62 | 0 | 99 ms | 92 ms |
| Node main | 62 / 62 | 0 | 159 ms | 94 ms |
| Node Worker | 62 / 62 | 0 | 161 ms | 91 ms |
| Bun main | 62 / 62 | 0 | 221 ms | 103 ms |
| Bun Worker | 62 / 62 | 0 | 228 ms | 108 ms |

That is 200 replacements and 10 left steps without a lost session. All 520 sessions started with `0x18000000` and created
their own Activity, and the plugin saw no `wm_new_intent`. In three of these starts the old instance was still finishing when
the new one was created, the timing that lost the token before the fix.

The passes alone would be weak evidence for the intermittent path: at 3 lost of 240, 200 replacements without a loss could
happen by chance about one time in twelve. What closes it is that the five starts in the window (two in the 500 ms run, three
here) created their own Activity, and that no start asks the system to reuse one. The left steps fail every time before the
fix and passed all 16 times after it (6 in the 500 ms run, 10 here).

## Fix

`GalleryActivityLauncher.launchFlags`: `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK` and no `SINGLE_TOP`. Every
session gets a new task and a new Activity, as it already did when no race happened, and the Activity of an earlier session
finishes on its own. The contract does not change; protocol section "Sessions" now states this. Plugins need no change,
and an `onNewIntent` in a plugin would never run.

A viewer left to the user now stays in its task under the new session, and that task is excluded from Recents. Before the fix
that case failed with `TIMEOUT` after 60 s.

Unit test: GalleryActivityLauncherTest (2 cases). The host's unit tests have no Android runtime, so the flags are a pure
function; the behavior is covered by the device script. In one full-load build, GalleryLateOpeningTest
(aSessionOpenedAfterTheTimeoutReleasesItsBindingEvenThoughItsCloseCannotBeSent) failed in 0.25 s, shorter than its own 400 ms
sleep: its 100 ms timeout fired at `delay(1)`, before the lease existed. It passes on its own (0.48 s) and does not touch this
change.

## Running it

```
py -3.12 -I tools/api-coverage/device_gallery_replace.py run --serial SERIAL --foreground --rounds 20 --left 2 --log PREFIX --report FILE
```

The Node runs need the permissions and the Bun runs the grants of device_plugin_family.py (`grant --set on --only ...`). A bare
Bun script is limited to 60 s, so the script runs Bun as a project with `bun.timeoutMs`. Steps that fail for a lost token take
60 s each.

## Device state afterwards

OnePlus 63751f63 is back on what it had before this batch, checked by md5: host 5935 (b958b46599fe2c7188b58a84a1705553), Node
340 (648d7ac693f6902528f6a280de41e906), 3-Maple Image (8fed17c5839aaa7ff560c7406a5126e6), Batch Rename
(814cff596957d573c04211dfb30a7ba1), Bun Runtime not installed, the four script control grants absent. 3-Macaw Speech is not part
of this record: another session reinstalled it during this batch, and it stays as that session left it.
