# UI leak walk: OPD2413 (API 35), host debug 5835, 2026-10-22 10:50

- Command: `py -3.12 -I -u tools/memory/ui_leak_walk.py -s 63751f63 --reps 3 --tag after-part2 --shots --pages plugin-center,log,tasks,settings,settings-theme,settings-shortcut-backup,settings-transfer,settings-trash,settings-editor,settings-codegen,settings-release,settings-about,settings-developer,settings-memory`
- LeakCanary: enabled in the developer options; automatic heap dumps on during the walk (was false)
- Offline documentation plugin: enabled for the walk (was disabled)

| page | rounds | watched | retained | application leaks | library leaks | result |
| --- | --- | --- | --- | --- | --- | --- |
| plugin-center | 3 | 22 | 0 | 0 | 0 | PASS |
| log | 3 | 15 | 0 | 0 | 0 | PASS |
| tasks | 3 | 12 | 0 | 0 | 0 | PASS |
| settings | 3 | 20 | 0 | 0 | 0 | PASS |
| settings-theme | 3 | 30 | 0 | 0 | 0 | PASS |
| settings-shortcut-backup | 3 | 40 | 0 | 0 | 0 | PASS |
| settings-transfer | 3 | 40 | 0 | 0 | 0 | PASS |
| settings-trash | 3 | 30 | 0 | 0 | 0 | PASS |
| settings-editor | 3 | 20 | 0 | 0 | 0 | PASS |
| settings-codegen | 3 | 15 | 0 | 0 | 0 | PASS |
| settings-release | 3 | 15 | 0 | 0 | 0 | PASS |
| settings-about | 3 | 15 | 0 | 0 | 0 | PASS |
| settings-developer | 3 | 25 | 0 | 0 | 0 | PASS |
| settings-memory | 3 | 35 | 0 | 0 | 0 | PASS |
